package vannon.syx.economy.core;

import init.constant.C;
import init.sprite.UI.UI;
import settlement.entity.humanoid.Humanoid;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomySim;
import util.gui.misc.GText;
import view.main.VIEW;

public final class SubjectJob {
    private final GText text;

    public SubjectJob() {
        this.text = new GText(UI.FONT().S, 32);
    }

    public void render(Renderer r, float ds) {
        if (!EconConfig.windowEnabled) {
            return;
        }
        EconomySim sim = EconomySim.active();
        if (sim == null) {
            return;
        }
        if (VIEW.s() == null) {
            return;
        }
        Humanoid h = VIEW.s().ui.subjects.current();
        if (h == null || h.isRemoved()) {
            return;
        }
        // Right-side mirror of SubjectWallet
        int screenW = C.WIDTH();
        int x = screenW - 170;
        int y = 56;

        COLOR.WHITE15.render((SPRITE_RENDERER)r, x, x + 150, y, y + 26);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x, x + 150, y, y + 1);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x, x + 150, y + 25, y + 26);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x, x + 1, y, y + 26);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x + 149, x + 150, y, y + 26);

        String jobName = "Kein Job";
        // Job lookup is API-dependent; show placeholder for now
        // Full job integration can be added when the engine API is confirmed

        this.text.clear().add(jobName);
        this.text.color(COLOR.WHITE200);
        this.text.render((SPRITE_RENDERER)r, x + 10, x + 150, y + 2, y + 24);
    }
}