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
import vannon.syx.economy.ui.WindowState;

final class InstanceScript implements SCRIPT.SCRIPT_INSTANCE {

    private final EconomySim economy;
    private final WindowOverview overview;
    private final WindowEconomy economyWindow;
    private final WindowState stateWindow;
    private final SubjectWallet subjectWallet;
    /** Edge detection for hotkey polling (Hk.java pattern).
     *  GLFW key codes: 334 = Numpad +, 333 = Numpad -, 332 = Numpad *, 331 = Numpad /. */
    private boolean overviewWasDown;
    private boolean economyWasDown;
    private boolean stateWasDown;
    private boolean dumpWasDown;

    InstanceScript() {
        EconConfig.init();
        EconConfig.resetLaborDefaults();
        this.economy = new EconomySim();
        this.overview = new WindowOverview(this.economy);
        this.economyWindow = new WindowEconomy(this.economy);
        this.stateWindow = new WindowState(this.economy);
        this.subjectWallet = new SubjectWallet();
        DebugTracer.trace(DebugTracer.SCRP, "InstanceScript created");
    }

    @Override
    public void save(FilePutter file) {
        DebugTracer.trace(DebugTracer.VIEW, "save " + file.path);
        LOG.ln("[ECONOMY MOD] Writing save game: " + String.valueOf(file.path));
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
        pollHotkeys();
        pollDumpHotkey();
        DebugTracer.traceEvery(300, DebugTracer.SCRP, "update sample");
    }

    /** Hotkey polling with edge detection (Hk.java pattern from ListMenus mod).
     *  Numpad + → Overview, Numpad - → Economy, Numpad * → State.
     *  Clean switching: pressing a hotkey hides all other windows first. */
    private void pollHotkeys() {
        boolean add  = CORE.getInput().getKeyboard().isPressed(334); // Numpad +
        boolean sub  = CORE.getInput().getKeyboard().isPressed(333); // Numpad -
        boolean mul  = CORE.getInput().getKeyboard().isPressed(332); // Numpad *

        if (add && !this.overviewWasDown) {
            switchTo(this.overview, this.economyWindow, this.stateWindow);
        } else if (sub && !this.economyWasDown) {
            switchTo(this.economyWindow, this.overview, this.stateWindow);
        } else if (mul && !this.stateWasDown) {
            switchTo(this.stateWindow, this.overview, this.economyWindow);
        }

        this.overviewWasDown = add;
        this.economyWasDown = sub;
        this.stateWasDown  = mul;
    }

    /** Numpad / (GLFW_KEY_KP_DIVIDE = 331) → dump DebugTracer buffer to game log. */
    private void pollDumpHotkey() {
        boolean div = CORE.getInput().getKeyboard().isPressed(331);
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
        // Hotkey handled via keyboard polling in update() (Hk.java pattern).
        // keyPush kept as no-op to avoid double-trigger with polling.
    }

    @Override
    public void hoverTimer(double mouseTimer, GBox text) {
        // No-op: this mod does not provide a hover timer.
    }

    @Override
    public void render(Renderer renderer, float deltaSeconds) {
        DebugTracer.traceEvery(120, DebugTracer.SCRP, "render");
        this.subjectWallet.render(renderer, deltaSeconds);
    }

    @Override
    public void mouseClick(MButt button) {
        DebugTracer.trace(DebugTracer.SCRP, "mouseClick btn=" + button);
    }

    @Override
    public void hover(COORDINATE mCoo, boolean mouseHasMoved) {
        DebugTracer.traceEvery(120, DebugTracer.SCRP, "hover x=" + mCoo.x() + " y=" + mCoo.y());
    }

    @Override
    public boolean handleBrokenSavedState() {
        return true;
    }
}
