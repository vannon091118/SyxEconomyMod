package vannon.syx.economy.core;

import init.constant.C;
import init.sprite.SPRITES;
import init.sprite.UI.UI;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.gui.GuiSection;
import snake2d.util.misc.ACTION;
import util.colors.GCOLOR;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.misc.GText;
import vannon.syx.economy.ui.EconWindowBase;

/**
 * Vanilla-konforme HUD-Buttons: GButt.ButtPanel mit echten Icons
 * in der Naehe der Top-Leiste. Kein Eigen-Render, kein manuelles Hover.
 *
 * Da VIEW.java keinen oeffentlichen UIPanelTop-Zugriff hat, positioniert
 * EconHud die Buttons selbst — aber MIT den echten Vanilla-ButtPanel-
 * Komponenten (automatischer Hover/Selected/Border/Sound).
 */
public final class EconHud {

    private final GuiSection section = new GuiSection();
    private final GText versionLabel;
    private static final int BTN_DIM = 36;
    private static final int BTN_GAP = 2;

    public EconHud(EconWindowBase overview, EconWindowBase economy,
                   EconWindowBase state, EconWindowBase quickview) {

        GButt.ButtPanel ovBtn = new GButt.ButtPanel(SPRITES.icons().l.gov);
        ovBtn.clickActionSet(new ACTION() { @Override public void exe() { overview.toggle(); } });
        ovBtn.hoverInfoSet("Wirtschafts-Uebersicht (Numpad +)");
        ovBtn.setDim(BTN_DIM, 48);

        GButt.ButtPanel ecBtn = new GButt.ButtPanel(SPRITES.icons().m.coins);
        ecBtn.clickActionSet(new ACTION() { @Override public void exe() { economy.toggle(); } });
        ecBtn.hoverInfoSet("Wirtschaftsfenster (Numpad -)");
        ecBtn.setDim(BTN_DIM, 48);

        GButt.ButtPanel stBtn = new GButt.ButtPanel(SPRITES.icons().m.admin);
        stBtn.clickActionSet(new ACTION() { @Override public void exe() { state.toggle(); } });
        stBtn.hoverInfoSet("Staatsfenster (Numpad *)");
        stBtn.setDim(BTN_DIM, 48);

        GButt.ButtPanel qvBtn = new GButt.ButtPanel(SPRITES.icons().m.cog);
        qvBtn.clickActionSet(new ACTION() { @Override public void exe() { quickview.toggle(); } });
        qvBtn.hoverInfoSet("Quickview (Numpad 0)");
        qvBtn.setDim(BTN_DIM, 48);

        section.add(ovBtn, 0, 0);
        section.add(ecBtn, BTN_DIM + BTN_GAP, 0);
        section.add(stBtn, 2 * (BTN_DIM + BTN_GAP), 0);
        section.add(qvBtn, 3 * (BTN_DIM + BTN_GAP), 0);

        // Version label — build identity anchor
        versionLabel = new GText(UI.FONT().S, 16);
        versionLabel.set(BuildStamp.FULL_ID);
        versionLabel.color(GCOLOR.T().INACTIVE);
        section.add(versionLabel, 0, BTN_DIM + 6);
    }

    /** Called once to position the button strip.
     *  Positions ~200px from the right screen edge, clear of vanilla icons. */
    public void initPosition() {
        section.body().moveX2(C.WIDTH() - 200);
        section.body().moveY1(2);
    }

    public void render(Renderer r, float ds) {
        SPRITE_RENDERER sr = (SPRITE_RENDERER) r;
        section.render(sr, ds);
    }

    public void pollHover(COORDINATE mCoo, GBox tooltipText) {
        section.hover(mCoo);
    }

    public void pollClick(MButt button) {
        if (button == MButt.LEFT) {
            section.click();
        }
    }

    public void pollHoverTimer(GBox text) {
        section.hoverInfoGet(text);
    }
}
