package vannon.syx.economy.core;

import init.constant.C;
import init.sprite.UI.UI;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import util.gui.misc.GText;
import util.gui.misc.GBox;
import vannon.syx.economy.ui.EconWindowBase;

/**
 * Minimal persistent HUD — 4 tiny window-switcher buttons at the top-right
 * of the screen. Rendered through InstanceScript lifecycle.
 * No counters, no clutter. Only 4 single-letter buttons: Ü W S Q.
 */
public final class EconHud {

    private final EconWindowBase overview;
    private final EconWindowBase economy;
    private final EconWindowBase state;
    private final EconWindowBase quickview;

    private static final int BTN_W = 44;
    private static final int BTN_H = 22;
    private static final int GAP = 4;
    private static final int TOTAL_W = 4 * BTN_W + 3 * GAP;
    private final int x0;
    private static final int Y = 8;

    private final GText[] labels;
    private int hoveredIdx = -1;

    public EconHud(EconWindowBase overview, EconWindowBase economy,
                    EconWindowBase state, EconWindowBase quickview) {
        this.overview = overview;
        this.economy = economy;
        this.state = state;
        this.quickview = quickview;

        x0 = C.WIDTH() - TOTAL_W - 340;

        labels = new GText[4];
        String[] names = {"Ü", "W", "S", "Q"};
        for (int i = 0; i < 4; i++) {
            labels[i] = new GText(UI.FONT().S, 16);
            labels[i].set(names[i]);
        }
    }

    public void render(Renderer r, float ds) {
        SPRITE_RENDERER sr = (SPRITE_RENDERER) r;
        for (int i = 0; i < 4; i++) {
            int bx = x0 + i * (BTN_W + GAP);
            boolean hov = (i == hoveredIdx);
            // Button background
            (hov ? COLOR.WHITE35 : COLOR.WHITE15).render(sr, bx, bx + BTN_W, Y, Y + BTN_H);
            // Border
            COLOR.WHITE35.render(sr, bx, bx + BTN_W, Y, Y + 1);
            COLOR.WHITE35.render(sr, bx, bx + BTN_W, Y + BTN_H - 1, Y + BTN_H);
            COLOR.WHITE35.render(sr, bx, bx + 1, Y, Y + BTN_H);
            COLOR.WHITE35.render(sr, bx + BTN_W - 1, bx + BTN_W, Y, Y + BTN_H);
            // Label
            labels[i].color(hov ? COLOR.WHITE200 : COLOR.WHITE100);
            labels[i].render(sr, bx + 4, bx + BTN_W - 4, Y + 3, Y + BTN_H - 3);
        }
    }

    private static final String[] TIPS = {"Uebersicht (Numpad +)", "Wirtschaft (Numpad -)",
                                          "Staat (Numpad *)", "Quickview (Numpad 0)"};
    private final GText tipText = new GText(UI.FONT().S, 128);

    public boolean pollHover(COORDINATE mCoo, GBox tooltipText) {
        hoveredIdx = -1;
        for (int i = 0; i < 4; i++) {
            int bx = x0 + i * (BTN_W + GAP);
            if (mCoo.x() >= bx && mCoo.x() <= bx + BTN_W
                && mCoo.y() >= Y && mCoo.y() <= Y + BTN_H) {
                hoveredIdx = i;
                return true;
            }
        }
        return false;
    }

    public boolean pollClick(MButt button) {
        if (button != MButt.LEFT || hoveredIdx < 0) return false;
        EconWindowBase[] wins = {overview, economy, state, quickview};
        if (hoveredIdx < wins.length && wins[hoveredIdx] != null) {
            wins[hoveredIdx].toggle();
        }
        return true;
    }

    public void pollHoverTimer(GBox text) {
        if (hoveredIdx >= 0) {
            tipText.clear().add(TIPS[hoveredIdx]);
            text.add(tipText);
        }
    }
}
