package vannon.syx.economy.core;

import init.constant.C;
import init.sprite.SPRITES;
import init.sprite.UI.UI;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomySim;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.gui.misc.GText;
import util.gui.panel.GPanel;
import snake2d.util.misc.ACTION;
import util.gui.misc.GBox;
import vannon.syx.economy.ui.WindowOverview;
import vannon.syx.economy.ui.WindowEconomy;
import vannon.syx.economy.ui.WindowState;
import vannon.syx.economy.ui.WindowQuickview;

public final class EconHud {
    private final EconomySim economy;
    private final WindowOverview overview;
    private final WindowEconomy economyWindow;
    private final WindowState stateWindow;
    private final WindowQuickview quickview;
    private boolean hudShown = true;

    public EconHud(EconomySim economy, WindowOverview overview, WindowEconomy economyWindow,
                   WindowState stateWindow, WindowQuickview quickview) {
        this.economy = economy;
        this.overview = overview;
        this.economyWindow = economyWindow;
        this.stateWindow = stateWindow;
        this.quickview = quickview;
    }

    public void render(Renderer r, float ds) {
        if (!EconConfig.windowEnabled || !hudShown) {
            return;
        }
        if (economy == null) return;

        // Render HUD panel at fixed position top-right (but below minimap)
        int x = C.WIDTH() - 210;
        int y = 152; // below minimap (~148px)

        String[] texts = {
            "Finanzen: " + (economy.treasury() >= 0 ? "OK" : "ROT"),
            "Gleichheit: " + (economy.stats().gini < 0.35 ? "OK" : "WARN"),
            "Wachstum: " + (!economy.econIndicators().isTreasuryDeclining() ? "OK" : "WARN"),
            "Arbeit: " + (economy.firmLedger().lastWorkersUnpaid() == 0 ? "OK" : "WARN")
        };

        GText[] labels = new GText[4];
        for (int i = 0; i < 4; i++) {
            labels[i] = new GText(UI.FONT().S, 120);
            labels[i].set(texts[i]);
            COLOR c = texts[i].contains("OK") ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal;
            labels[i].color(c);
        }

        // Draw background
        COLOR.WHITE15.render((SPRITE_RENDERER)r, x, x + 200, y, y + 160);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x, x + 200, y, y + 10);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x, x + 200, y + 150, y + 160);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x, x + 10, y, y + 160);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x + 190, x + 200, y, y + 160);

        int lineY = y + 16;
        for (int i = 0; i < 4; i++) {
            labels[i].render((SPRITE_RENDERER)r, x + 10, lineY, 180, 30);
            lineY += 22;
        }
    }

    public void pollClick(snake2d.MButt button) {
        // Not used for now - button handling is via GButt in render
    }

    public void pollHover(snake2d.util.datatypes.COORDINATE mCoo, util.gui.misc.GBox text) {
        // Not used for now
    }

    public void toggle() {
        hudShown = !hudShown;
    }

    public boolean isShown() {
        return hudShown;
    }
}