package vannon.syx.economy.core;

import java.io.IOException;
import script.SCRIPT;
import snake2d.CORE;
import snake2d.LOG;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import util.gui.misc.GBox;
import view.keyboard.KEYS;
import vannon.syx.economy.ui.EconWindowBase;
import vannon.syx.economy.ui.WindowEconomy;
import vannon.syx.economy.ui.WindowOverview;
import vannon.syx.economy.ui.WindowQuickview;
import vannon.syx.economy.ui.WindowState;

final class InstanceScript implements SCRIPT.SCRIPT_INSTANCE {

    private final EconomySim economy;
    private final WindowOverview overview;
    private final WindowEconomy economyWin;
    private final WindowState stateWin;
    private final WindowQuickview quickviewWin;
    private final SubjectWallet subjectWallet;
    private final SubjectJob subjectJob;
    private final EconHud econHud;

    /** Edge detection for hotkey polling (Hk.java pattern).
     *  GLFW key codes: 334 = Numpad +, 333 = Numpad -, 332 = Numpad *, 320 = Numpad 0,
     *  331 = Numpad /, 256 = ESC.
     *  Letter-key fallback (Res-UI-HOTKEYS): 69=E, 79=O, 83=S, 81=Q, 47=/,
     *  damit Spieler ohne Numpad (Laptops, Compact-Tastaturen) die Mod-Fenster
     *  ebenfalls finden — die meisten haben keinen dedizierten Numpad-Block. */
    private boolean overviewWasDown;
    private boolean economyWasDown;
    private boolean stateWasDown;
    private boolean quickviewWasDown;
    private boolean dumpWasDown;
    private boolean escWasDown;
    // Letter-key edge states (Res-UI-HOTKEYS parallel)
    private boolean oWasDown;
    private boolean eWasDown;
    private boolean sWasDown;
    private boolean qWasDown;

    /** View-change detection: compare VIEW.current() against last known class name. */
    private String lastViewClassName = "(startup)";

    InstanceScript() {
        EconConfig.init();
        EconConfig.resetLaborDefaults();
        this.economy = new EconomySim();
        this.overview = new WindowOverview(this.economy);
        this.economyWin = new WindowEconomy(this.economy);
        this.stateWin = new WindowState(this.economy);
        this.quickviewWin = new WindowQuickview(this.economy);
        this.subjectWallet = new SubjectWallet();
        this.subjectJob = new SubjectJob();
        this.econHud = new EconHud(this.economy, this.overview, this.economyWin, this.stateWin, this.quickviewWin);
        EconWindowBase.setSiblings(this.overview, this.economyWin, this.stateWin, this.quickviewWin);

        DebugTracer.trace(DebugTracer.SCRP, "InstanceScript created (4 windows + 3 panels registered)");
    }

    @Override
    public void save(FilePutter file) {
        DebugTracer.trace(DebugTracer.VIEW, "save " + file.path);
        LOG.ln("[ECONOMY MOD] Writing save game: " + String.valueOf(file.path));
        DiagnosticExporter.flush(DiagnosticExporter.sessionSeed());
        this.economy.save(file);
    }

    @Override
    public void load(FileGetter file) throws IOException {
        DebugTracer.trace(DebugTracer.VIEW, "load " + file.path);
        LOG.ln("[ECONOMY MOD] Reading save game: " + String.valueOf(file.path));
        this.economy.load(file);
    }

    @Override
    public void update(double deltaSeconds) {
        DebugTracer.tick();
        this.economy.update(deltaSeconds);
        pollViewChange();
        pollHotkeys();
        pollDumpHotkey();
        DebugTracer.traceEvery(300, DebugTracer.SCRP, "update sample");
    }

    /** Detect View switches via polling (SCRIPT_INSTANCE has no activate/deactivate). */
    private void pollViewChange() {
        if (!DebugTracer.on()) return;
        view.main.VIEW.ViewSubSimple current = view.main.VIEW.current();
        if (current == null) return;
        String name = current.getClass().getSimpleName();
        if (!name.equals(this.lastViewClassName)) {
            DebugTracer.trace(DebugTracer.VIEW,
                "view_change: " + this.lastViewClassName + " -> " + name);
            this.lastViewClassName = name;
        }
    }

    /** Hotkey polling with edge detection (Hk.java pattern from ListMenus mod).
     *  NUM-ONLY by Design (Sprint v0.13.116+ Hotfix):
     *  Numpad + (334) → Overview/Advisor window.
     *  Numpad - (333) → Economy window.
     *  Numpad * (332) → State window.
     *  Numpad 0 (320) → Quickview window.
     *  Letter-key fallback gated by EconConfig.letterHotkeyFallbackEnabled.
     *    Default FALSE — wir haben NumPad gewaehlt UM Vanilla-Hotkey-Kollisionen zu
     *    vermeiden. E ist in songs-of-syx fuer Edge-Building/Interact reserviert
     *    und kollidiert sonst mit unserem Window-Open. Toggle ON nur fuer Laptop-
     *    Spieler ohne NumPad, mit Kollisionsrisiko.
     *  ESC (256) → close all economy windows.
     *  Clean switching: pressing a hotkey hides all other windows first. */
    private void pollHotkeys() {
        boolean add  = CORE.getInput().getKeyboard().isPressed(334); // Numpad +
        boolean sub  = CORE.getInput().getKeyboard().isPressed(333); // Numpad -
        boolean mul  = CORE.getInput().getKeyboard().isPressed(332); // Numpad *
        boolean num0 = CORE.getInput().getKeyboard().isPressed(320); // Numpad 0
        boolean esc  = CORE.getInput().getKeyboard().isPressed(256); // ESC

        // Sprint v0.13.116+ Hotfix — Letter-key fallback gated by config (default OFF).
        // ASCII-Codes funktionieren auf jeder Standard-Tastatur; NumPad nicht.
        // Bei OFF (Default) sind Letter-Keys = no-op, NumPad-only ist die Policy.
        boolean letters = EconConfig.letterHotkeyFallbackEnabled;
        boolean eKey = letters && CORE.getInput().getKeyboard().isPressed(69);  // E
        boolean oKey = letters && CORE.getInput().getKeyboard().isPressed(79);  // O
        boolean sKey = letters && CORE.getInput().getKeyboard().isPressed(83);  // S
        boolean qKey = letters && CORE.getInput().getKeyboard().isPressed(81);  // Q

        // Merge numpad + letter: rising-edge für jede Aktion getrennt erfasst,
        // sodass (Numpad+ ODER O) übersicht öffnet; das erste auslösende gewinnt.
        if ((add || oKey) && !this.overviewWasDown && !this.oWasDown) {
            if (overview.isShown()) {
                overview.close();
            } else {
                closeOthers(overview);
                overview.toggle();
            }
        } else if ((sub || eKey) && !this.economyWasDown && !this.eWasDown) {
            if (economyWin.isShown()) {
                economyWin.close();
            } else {
                closeOthers(economyWin);
                economyWin.toggle();
            }
        } else if ((mul || sKey) && !this.stateWasDown && !this.sWasDown) {
            if (stateWin.isShown()) {
                stateWin.close();
            } else {
                closeOthers(stateWin);
                stateWin.toggle();
            }
        } else if ((num0 || qKey) && !this.quickviewWasDown && !this.qWasDown) {
            if (quickviewWin.isShown()) {
                quickviewWin.close();
            } else {
                closeOthers(quickviewWin);
                quickviewWin.toggle();
            }
        } else if (esc && !this.escWasDown) {
            closeAllWindows();
        }

        this.overviewWasDown   = add;
        this.economyWasDown    = sub;
        this.stateWasDown      = mul;
        this.quickviewWasDown  = num0;
        this.escWasDown        = esc;
        // Letter-edge separat halten — sonst kollidieren Numpad+/O überlagert
        // den rising-edge und der Brief-Tasten-Bereich wird redundant.
        this.oWasDown = oKey;
        this.eWasDown = eKey;
        this.sWasDown = sKey;
        this.qWasDown = qKey;
    }

    /** Numpad / (331) or regular / (47) → dump DebugTracer buffer to EventLog + file + stdout. */
    private void pollDumpHotkey() {
        boolean div = CORE.getInput().getKeyboard().isPressed(331)
                   || CORE.getInput().getKeyboard().isPressed(47);
        if (div && !this.dumpWasDown) {
            DebugTracer.trace(DebugTracer.SYS, "dump requested via hotkey");
            DebugTracer.dump();
        }
        this.dumpWasDown = div;
    }

    /** Close all open economy windows. */
    private void closeAllWindows() {
        if (overview != null && overview.isShown()) overview.close();
        if (economyWin != null && economyWin.isShown()) economyWin.close();
        if (stateWin != null && stateWin.isShown()) stateWin.close();
        if (quickviewWin != null && quickviewWin.isShown()) quickviewWin.close();
    }

    /** Hide all windows except the one being shown. */
    private void closeOthers(EconWindowBase keep) {
        if (overview != null && overview != keep && overview.isShown()) overview.close();
        if (economyWin != null && economyWin != keep && economyWin.isShown()) economyWin.close();
        if (stateWin != null && stateWin != keep && stateWin.isShown()) stateWin.close();
        if (quickviewWin != null && quickviewWin != keep && quickviewWin.isShown()) quickviewWin.close();
    }

    @Override
    public void keyPush(KEYS keys) {
        // Hotkey handled via keyboard polling in update()
    }

    @Override
    public void hoverTimer(double mouseTimer, GBox text) {
        // Trace only every 120th frame (matching render/hover sampling) to prevent
        // hoverTimer from filling the 8192-event buffer with idle noise.
        DebugTracer.traceEvery(120, DebugTracer.SCRP, "hoverTimer t=" + (long)mouseTimer);
    }

    @Override
    public void render(Renderer renderer, float deltaSeconds) {
        DebugTracer.traceEvery(120, DebugTracer.SCRP, "render");
        this.subjectWallet.render(renderer, deltaSeconds);
        this.subjectJob.render(renderer, deltaSeconds);
        this.econHud.render(renderer, deltaSeconds);
    }

    @Override
    public void mouseClick(MButt button) {
        DebugTracer.trace(DebugTracer.SCRP, "mouseClick btn=" + button);
        this.econHud.pollClick(button);
    }

    @Override
    public void hover(COORDINATE mCoo, boolean mouseHasMoved) {
        DebugTracer.traceEvery(120, DebugTracer.SCRP, "hover x=" + mCoo.x() + " y=" + mCoo.y());
        this.econHud.pollHover(mCoo, null);
    }

    @Override
    public boolean handleBrokenSavedState() {
        return true;
    }
}