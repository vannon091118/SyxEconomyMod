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

final class InstanceScript implements SCRIPT.SCRIPT_INSTANCE {

    private final EconomySim economy;
    private final EconomyWindow window;
    private final SubjectWallet subjectWallet;
    /** Edge detection for hotkey polling (Hk.java pattern). */
    private boolean hotkeyWasDown;

    InstanceScript() {
        EconConfig.init();
        EconConfig.resetLaborDefaults();
        this.economy = new EconomySim();
        this.window = new EconomyWindow();
        this.subjectWallet = new SubjectWallet();
    }

    @Override
    public void save(FilePutter file) {
        LOG.ln("[ECONOMY MOD] Writing save game: " + String.valueOf(file.path));
        this.economy.save(file);
    }

    @Override
    public void load(FileGetter file) throws IOException {
        LOG.ln("[ECONOMY MOD] Reading save game: " + String.valueOf(file.path));
        this.economy.load(file);
    }

    @Override
    public void update(double deltaSeconds) {
        this.economy.update(deltaSeconds);
        // Hotkey polling (Hk.java pattern from ListMenus mod)
        // Key: numpad '+' (GLFW_KEY_KP_ADD = 334). 'E' war bereits durch Vanilla belegt.
        // Edge detection: only toggles on key-down edge, not while held
        boolean keyDown = CORE.getInput().getKeyboard().isPressed(334);
        if (keyDown && !this.hotkeyWasDown) {
            this.window.toggle();
        }
        this.hotkeyWasDown = keyDown;
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
        this.window.render(renderer, deltaSeconds);
        this.subjectWallet.render(renderer, deltaSeconds);
    }

    @Override
    public void mouseClick(MButt button) {
        this.window.click(button);
    }

    @Override
    public void hover(COORDINATE mCoo, boolean mouseHasMoved) {
        this.window.hover(mCoo);
    }

    @Override
    public boolean handleBrokenSavedState() {
        return true;
    }
}
