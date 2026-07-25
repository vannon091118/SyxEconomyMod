package vannon.syx.economy.ui;

import init.constant.C;
import init.sprite.UI.UI;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.Rec;
import snake2d.util.gui.GuiSection;
import snake2d.util.misc.ACTION;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.gui.misc.GText;
import util.gui.panel.GPanel;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.StateWarehouses;
import vannon.syx.economy.core.WealthStats;

/**
 * Kompaktes Control-Panel mit den wichtigsten KPIs und schnellen Aktionen.
 * Numpad 0 oeffnet/schliesst.
 */
public final class WindowQuickview extends EconWindowBase {

    public WindowQuickview(EconomySim sim) {
        super(sim);
    }

    @Override
    protected CharSequence title() {
        return "Quickview";
    }

    @Override
    protected int panelWidth() { return 360; }

    @Override
    protected int panelHeight() { return 480; }

    @Override
    protected void position(GuiSection root) {
        Rec b = (Rec) root.body();
        b.moveX1Y1(C.WIDTH() - 380 - 360, 80);
    }

    @Override
    protected void build(GPanel background, GuiSection content) {
        background.setDim(panelWidth(), panelHeight());

        WealthStats stats = sim.stats();
        StateWarehouses wh = sim.stateWarehouses();
        long treasury = sim.treasury();
        boolean hasPop = stats.people > 0;
        int x = 16;
        int y = 8;

        // Treasury
        addKpi(content, x, y, "Staatskasse",
            CompactNumber.format(treasury) + " D",
            !hasPop ? GCOLOR.T().INACTIVE : treasury >= 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
        y += 38;

        // Population & Gini
        addKpi(content, x, y, "Bevölkerung",
            String.valueOf(stats.people), hasPop ? GCOLOR.T().NORMAL : GCOLOR.T().INACTIVE);
        addKpi(content, x + 170, y, "Gini",
            hasPop ? String.format("%.3f", stats.gini) : "N/A",
            !hasPop ? GCOLOR.T().INACTIVE : stats.gini > 0.40 ? GCOLOR.UI().BAD.normal : stats.gini > 0.35 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().GOOD.normal);
        y += 38;

        // Median & Wage
        addKpi(content, x, y, "Median",
            CompactNumber.format(stats.median) + " D",
            !hasPop ? GCOLOR.T().INACTIVE : GCOLOR.T().NORMAL);
        addKpi(content, x + 170, y, "Lohn/Tag",
            CompactNumber.format((long)sim.laborMarket().meanWage()) + " D",
            !hasPop ? GCOLOR.T().INACTIVE : sim.laborMarket().meanWage() > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
        y += 38;

        // Stage
        addKpi(content, x, y, "Stufe",
            sim.progression().stage.displayName, GCOLOR.T().NORMAL);
        addKpi(content, x + 170, y, "Unbezahlte",
            String.valueOf(sim.firmLedger().lastWorkersUnpaid()),
            sim.firmLedger().lastWorkersUnpaid() > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
        y += 38;

        // Deaths / Emigrations
        addKpi(content, x, y, "Tote/Ausgewandert",
            sim.deaths() + " / " + sim.emigrations(),
            sim.emigrations() > 3 ? GCOLOR.UI().SOSO.normal : GCOLOR.T().NORMAL);
        y += 38;

        // Trade mode buttons — ButtPanel with render override (like WindowState) for
        // dynamic selected state, plus text labels so each mode is clearly distinguishable.
        GText modeLabel = new GText(UI.FONT().M, 128);
        modeLabel.set("Lager-Modus:");
        modeLabel.lablify();
        content.add(modeLabel, x, y);
        y += 20;

        GButt.ButtPanel normal = new GButt.ButtPanel("Normal", 90) {
            @Override protected void render(snake2d.SPRITE_RENDERER r, float ds,
                                              boolean isActive, boolean isSelected, boolean isHovered) {
                selectedSet(wh.tradeMode() == StateWarehouses.TradeMode.NORMAL);
                super.render(r, ds, isActive, isSelected, isHovered);
            }
        };
        normal.clickActionSet(new ACTION() {
            @Override public void exe() { wh.setTradeMode(StateWarehouses.TradeMode.NORMAL); }
        });
        normal.hoverInfoSet("Normal handeln");
        content.add(normal, x, y);

        GButt.ButtPanel buy = new GButt.ButtPanel("Kaufen", 90) {
            @Override protected void render(snake2d.SPRITE_RENDERER r, float ds,
                                              boolean isActive, boolean isSelected, boolean isHovered) {
                selectedSet(wh.tradeMode() == StateWarehouses.TradeMode.BUY_ONLY);
                super.render(r, ds, isActive, isSelected, isHovered);
            }
        };
        buy.clickActionSet(new ACTION() {
            @Override public void exe() { wh.setTradeMode(StateWarehouses.TradeMode.BUY_ONLY); }
        });
        buy.hoverInfoSet("Nur einkaufen");
        content.add(buy, x + 100, y);

        GButt.ButtPanel sell = new GButt.ButtPanel("Verkaufen", 90) {
            @Override protected void render(snake2d.SPRITE_RENDERER r, float ds,
                                              boolean isActive, boolean isSelected, boolean isHovered) {
                selectedSet(wh.tradeMode() == StateWarehouses.TradeMode.SELL_ONLY);
                super.render(r, ds, isActive, isSelected, isHovered);
            }
        };
        sell.clickActionSet(new ACTION() {
            @Override public void exe() { wh.setTradeMode(StateWarehouses.TradeMode.SELL_ONLY); }
        });
        sell.hoverInfoSet("Nur verkaufen");
        content.add(sell, x + 200, y);
        y += 36;

        // Warehouse stats
        GText whLabel = new GText(UI.FONT().M, 256);
        whLabel.set("Lager: " + wh.ownedCount() + " staatlich");
        whLabel.color(GCOLOR.T().NORMAL);
        content.add(whLabel, x, y);
        y += 20;

        GText whStats = new GText(UI.FONT().S, 256);
        whStats.set("Gekauft: " + CompactNumber.format(wh.lastBought()) + "  Verkauft: " + CompactNumber.format(wh.lastSold()));
        whStats.color(GCOLOR.T().NORMAL);
        content.add(whStats, x, y);
        y += 30;

        GText fiscalLabel = new GText(UI.FONT().S, 256);
        fiscalLabel.set("Steuern: " + CompactNumber.format(sim.fiscal().headTaxCollected()) + "  Markt: " + CompactNumber.format(sim.fiscal().marketReceipts()));
        fiscalLabel.color(GCOLOR.T().NORMAL);
        content.add(fiscalLabel, x, y);
        y += 20;

        GText wagesLabel = new GText(UI.FONT().S, 256);
        wagesLabel.set("Loehne: " + CompactNumber.format(sim.wagesPaid()) + "  Rationen: " + CompactNumber.format(sim.fiscal().rationOut()));
        wagesLabel.color(GCOLOR.T().NORMAL);
        content.add(wagesLabel, x, y);

        // Window switcher buttons
        y += 36;
        GText switchLabel = new GText(UI.FONT().S, 128);
        switchLabel.set("Fenster:");
        switchLabel.color(GCOLOR.T().INACTIVE);
        content.add(switchLabel, x, y);
        y += 16;

        if (winOverview() != null) {
            GButt.ButtPanel ovBtn = new GButt.ButtPanel("Uebersicht", 100);
            ovBtn.clickActionSet(new ACTION() {
                @Override public void exe() { winOverview().toggle(); }
            });
            content.add(ovBtn, x, y);
        }
        if (winEconomy() != null) {
            GButt.ButtPanel ecBtn = new GButt.ButtPanel("Wirtschaft", 100);
            ecBtn.clickActionSet(new ACTION() {
                @Override public void exe() { winEconomy().toggle(); }
            });
            content.add(ecBtn, x + 110, y);
        }
        if (winState() != null) {
            GButt.ButtPanel stBtn = new GButt.ButtPanel("Staat", 100);
            stBtn.clickActionSet(new ACTION() {
                @Override public void exe() { winState().toggle(); }
            });
            content.add(stBtn, x + 220, y);
        }
    }
}
