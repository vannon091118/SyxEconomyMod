package vannon.syx.economy.ui;

import init.constant.C;
import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.Rec;
import snake2d.util.gui.GuiSection;
import snake2d.util.misc.ACTION;
import snake2d.util.sprite.SPRITE;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.gui.misc.GText;
import util.gui.panel.GPanel;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.StateWarehouses;
import vannon.syx.economy.core.WealthStats;

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
        b.moveX1Y1(C.WIDTH() - 380 - 360, 152); // below minimap
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
        addKpi(content, x, y, UI.icons().m.coins, "Staatskasse",
            CompactNumber.format(treasury) + " D",
            KpiSection.colorForTreasury(treasury, hasPop));
        y += 38;

        // Population & Gini
        addKpi(content, x, y, UI.icons().m.citizen, "Bevoelkerung",
            String.valueOf(stats.people), hasPop ? GCOLOR.T().NORMAL : GCOLOR.T().INACTIVE);
        addKpi(content, x + 170, y, UI.icons().m.heart, "Gini",
            hasPop ? String.format("%.3f", stats.gini) : "N/A",
            KpiSection.colorForGini(stats.gini, hasPop));
        y += 38;

        // Median & Wage
        addKpi(content, x, y, "Median",
            CompactNumber.format(stats.median) + " D",
            !hasPop ? GCOLOR.T().INACTIVE : GCOLOR.T().NORMAL);
        addKpi(content, x + 170, y, UI.icons().m.pickaxe, "Lohn/Tag",
            CompactNumber.format((long)sim.laborMarket().meanWage()) + " D",
            KpiSection.colorForWage(sim.laborMarket().meanWage(), hasPop));
        y += 38;

        // Stage
        addKpi(content, x, y, "Stufe",
            sim.progression().stage.displayName, GCOLOR.T().NORMAL);
        addKpi(content, x + 170, y, UI.icons().m.skull, "Unbezahlte",
            String.valueOf(sim.firmLedger().lastWorkersUnpaid()),
            KpiSection.colorForUnpaid(sim.firmLedger().lastWorkersUnpaid()));
        y += 38;

        // Deaths / Emigrations
        addKpi(content, x, y, "Tote/Ausgewandert",
            sim.deaths() + " / " + sim.emigrations(),
            KpiSection.colorForEmigration(sim.emigrations()));
        y += 38;

        // Trade mode buttons — ButtPanel with render override for
        // dynamic selected state, plus text labels so each mode is clearly distinguishable.
        GText modeLabel = new GText(UI.FONT().M, FONTW_KPI);
        modeLabel.set("Lager-Modus:");
        modeLabel.lablify();
        content.add(modeLabel, x, y);
        y += 20;

        GButt.ButtPanel normal = new GButt.ButtPanel("Normal", 110) {
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

        GButt.ButtPanel buy = new GButt.ButtPanel("Kaufen", 110) {
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
        content.add(buy, x + 120, y);

        GButt.ButtPanel sell = new GButt.ButtPanel("Verkaufen", 110) {
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
        content.add(sell, x + 240, y);
        y += 36;

        // Warehouse stats
        GText whLabel = new GText(UI.FONT().M, FONTW_HDR);
        whLabel.set("Lager: " + wh.ownedCount() + " staatlich");
        whLabel.color(GCOLOR.T().NORMAL);
        content.add(whLabel, x, y);
        y += 20;

        GText whStats = new GText(UI.FONT().S, FONTW_HDR);
        whStats.set("Gekauft: " + CompactNumber.format(wh.lastBought()) + "  Verkauft: " + CompactNumber.format(wh.lastSold()));
        whStats.color(GCOLOR.T().NORMAL);
        content.add(whStats, x, y);
        y += 30;

        GText fiscalLabel = new GText(UI.FONT().S, FONTW_HDR);
        fiscalLabel.set("Steuern: " + CompactNumber.format(sim.fiscal().headTaxCollected()) + "  Markt: " + CompactNumber.format(sim.fiscal().marketReceipts()));
        fiscalLabel.color(GCOLOR.T().NORMAL);
        content.add(fiscalLabel, x, y);
        y += 20;

        GText wagesLabel = new GText(UI.FONT().S, FONTW_HDR);
        wagesLabel.set("Loehne: " + CompactNumber.format(sim.wagesPaid()) + "  Rationen: " + CompactNumber.format(sim.fiscal().rationOut()));
        wagesLabel.color(GCOLOR.T().NORMAL);
        content.add(wagesLabel, x, y);

        // Window switcher buttons
        y += 36;
        GText switchLabel = new GText(UI.FONT().S, FONTW_KPI);
        switchLabel.set("Fenster:");
        switchLabel.color(GCOLOR.T().INACTIVE);
        content.add(switchLabel, x, y);
        y += 16;

        if (winOverview() != null) {
            GButt.ButtPanel ovBtn = new GButt.ButtPanel("Uebersicht", 120);
            ovBtn.clickActionSet(new ACTION() {
                @Override public void exe() { winOverview().toggle(); }
            });
            content.add(ovBtn, x, y);
        }
        if (winEconomy() != null) {
            GButt.ButtPanel ecBtn = new GButt.ButtPanel("Wirtschaft", 120);
            ecBtn.clickActionSet(new ACTION() {
                @Override public void exe() { winEconomy().toggle(); }
            });
            content.add(ecBtn, x + 130, y);
        }
        if (winState() != null) {
            GButt.ButtPanel stBtn = new GButt.ButtPanel("Staat", 100);
            stBtn.clickActionSet(new ACTION() {
                @Override public void exe() { winState().toggle(); }
            });
            content.add(stBtn, x + 260, y);
        }
    }

    // Sprint v0.13.117+ UI-Endredaktion — renderSidePanelContent ENTFERNT (0 Caller, no-op).
    // Sprint v0.13.116+ Hotfix hatte Body disabled wegen Regression "Schwarzbild+grüne Schrift",
    // die privaten addKpiSidePanel-Helper waren bereits in e667436 entfernt. Sprint v0.13.117+
    // schliesst das Thema komplett: public no-op API ohne Aufrufer ist dead code per agents.md.
    // Folge-Sprint fuer proper Side-Panel-Rebuild (neu mit Layout-Migration v0.13.117+ falls noetig)
    // bleibt Sprint v0.13.126+ WindowQuickview Side-Panel proper Rebuild.
}
