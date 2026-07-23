package vannon.syx.economy.core;

import init.sprite.UI.UI;
import settlement.entity.humanoid.Humanoid;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomySim;
import util.gui.misc.GText;
import view.main.VIEW;

public final class SubjectWallet {
    private static final int X = 16;
    private static final int Y = 56;
    private static final int W = 150;
    private static final int H = 26;
    private final GText text;

    public SubjectWallet() {
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
        int money = sim.wallets().moneyOf(h.indu());
        if (money < 0) {
            return;
        }
        COLOR.WHITE15.render((SPRITE_RENDERER)r, 16, 166, 56, 82);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, 16, 166, 56, 57);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, 16, 166, 81, 82);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, 16, 17, 56, 82);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, 165, 166, 56, 82);
        UI.icons().s.money.render((SPRITE_RENDERER)r, 22, 61);
        this.text.clear().add((CharSequence)("" + money)).add((CharSequence)" denari");
        this.text.color(COLOR.WHITE200);
        this.text.render((SPRITE_RENDERER)r, 42, 166, 63, 82);
    }
}

