package vannon.syx.economy.core;

import java.io.IOException;
import script.SCRIPT;
import snake2d.CORE;
import snake2d.LOG;
import snake2d.MButt;
import snake2d.Renderer;
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
    private final WindowEconomy economyWindow;
    private final WindowState stateWindow;
    private final WindowQuickview quickview;
    private final SubjectWallet subjectWallet;
    private final SubjectJob subjectJob;
    private final EconHud econHud;
    /** Edge detection for hotkey polling (Hk.java pattern).
     *  GLFW key codes: 334 = Numpad +, 333 = Numpad -, 332 = Numpad *, 331 = Numpad /. */
    private boolean overviewWasDown;
    private boolean economyWasDown;
    private boolean stateWasDown;
    private boolean quickviewWasDown;
    private boolean dumpWasDown;
    private boolean escWasDown;
    /** View-change detection: compare VIEW.current() against last known class name. */
    private String lastViewClassName = "(startup)";

    InstanceScript() {
        EconConfig.init();
        EconConfig.resetLaborDefaults();
        this.economy = new EconomySim();
        this.overview = new WindowOverview(this.economy);
        this.economyWindow = new WindowEconomy(this.economy);
        this.stateWindow = new WindowState(this.economy);
        this.quickview = new WindowQuickview(this.economy);
        this.subjectWallet = new SubjectWallet();
        this.subjectJob = new SubjectJob();
        this.econHud = new EconHud(this.economy, this.overview, this.economyWindow, this.stateWindow, this.quickview);
        this.econHud.initPosition();
        EconWindowBase.setSiblings(this.overview, this.economyWindow, this.stateWindow);
        DebugTracer.trace(DebugTracer.SCRP, "InstanceScript created");
    }

    @Override
    public void save(FilePutter file) {
        DebugTracer.trace(DebugTracer.VIEW, "save " + file.path);
        LOG.ln("[ECONOMY MOD] Writing save game: " + String.valueOf(file.path));
        // DC-01: Summary-Buffer vor dem Save flushen — agent-lesbare
        // Change-Events statt 39.6M TRACE-Zeilen.
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
     *  Numpad + → Overview, Numpad - → Economy, Numpad * → State.
     *  Numpad 0 → Quickview (compact control panel).
     *  ESC → close all open windows.
     *  Clean switching: pressing a hotkey hides all other windows first. */
    private void pollHotkeys() {
        boolean add  = CORE.getInput().getKeyboard().isPressed(334); // Numpad +
        boolean sub  = CORE.getInput().getKeyboard().isPressed(333); // Numpad -
        boolean mul  = CORE.getInput().getKeyboard().isPressed(332); // Numpad *
        boolean num0 = CORE.getInput().getKeyboard().isPressed(320); // Numpad 0
        boolean esc  = CORE.getInput().getKeyboard().isPressed(256); // ESC

        if (add && !this.overviewWasDown) {
            switchTo(this.overview, this.economyWindow, this.stateWindow, this.quickview);
        } else if (sub && !this.economyWasDown) {
            switchTo(this.economyWindow, this.overview, this.stateWindow, this.quickview);
        } else if (mul && !this.stateWasDown) {
            switchTo(this.stateWindow, this.overview, this.economyWindow, this.quickview);
        } else if (num0 && !this.quickviewWasDown) {
            switchTo(this.quickview, this.overview, this.economyWindow, this.stateWindow);
        } else if (esc && !this.escWasDown) {
            closeAllWindows();
        }

        this.overviewWasDown = add;
        this.economyWasDown = sub;
        this.stateWasDown  = mul;
        this.escWasDown    = esc;
        this.quickviewWasDown = num0;
    }

    /** Close all open economy windows. */
    private void closeAllWindows() {
        if (overview.isShown()) overview.close();
        if (economyWindow.isShown()) economyWindow.close();
        if (stateWindow.isShown()) stateWindow.close();
        if (quickview.isShown()) quickview.close();
    }

    /** Numpad / (331) or regular / (47) → dump DebugTracer buffer to EventLog + file + stdout. */
    private void pollDumpHotkey() {
        // 331 = Numpad / (layout-independent), 47 = US '/' key
        boolean div = CORE.getInput().getKeyboard().isPressed(331)
                   || CORE.getInput().getKeyboard().isPressed(47);
        if (div && !this.dumpWasDown) {
            DebugTracer.trace(DebugTracer.SYS, "dump requested via hotkey");
            DebugTracer.dump();
        }
        this.dumpWasDown = div;
    }

    /** Toggle the target window, closing all others first if the target is not already shown. */
    private void switchTo(EconWindowBase target,
                          EconWindowBase... others) {
        if (target.isShown()) {
            target.toggle(); // close it
        } else {
            for (EconWindowBase other : others) {
                if (other.isShown()) {
                    other.toggle();
                }
            }
            target.toggle(); // open it
        }
    }

    @Override
    public void keyPush(KEYS keys) {
        // Hotkey handled via keyboard polling in update()
        // TopBar removed — use in-window nav buttons (Übersicht|Wirtschaft|Staat) or Numpad hotkeys.
    }

    @Override
    public void hoverTimer(double mouseTimer, GBox text) {
        // Trace only every 120th frame (matching render/hover sampling) to prevent
        // hoverTimer from filling the 8192-event buffer with idle noise.
        DebugTracer.traceEvery(120, DebugTracer.SCRP, "hoverTimer t=" + (long)mouseTimer);
        this.econHud.pollHoverTimer(text);
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
