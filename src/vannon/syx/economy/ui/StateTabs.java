package vannon.syx.economy.ui;

import init.sprite.UI.UI;
import snake2d.util.color.COLOR;
import util.gui.misc.GText;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconTexts;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.StateWarehouses;

/** Tabs for the {@link WindowState}. */
public final class StateTabs {

    private StateTabs() {}

    public static final class WarehouseTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);

        public WarehouseTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Staatslager"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            StateWarehouses state = sim.stateWarehouses();
            int x = ctx.windowX + 20;
            int y = yStart;
            text.clear();
            text.add("State warehouses: ").add(state.ownedCount()).add(" / ").add(CompactNumber.format(sim.cachedStateWarehouses().size()));
            text.color(state.ownedCount() == 0 ? COLOR.REDISH : COLOR.WHITE150);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 28;

            // Trade mode buttons (NORMAL / BUY_ONLY / SELL_ONLY)
            StateWarehouses.TradeMode mode = state.tradeMode();
            if (EconWidgets.button(ctx, EconTexts.¤¤warehouseModeNormal, x, y, 80, 22) && mode != StateWarehouses.TradeMode.NORMAL) {
                state.setTradeMode(StateWarehouses.TradeMode.NORMAL);
            }
            if (EconWidgets.button(ctx, EconTexts.¤¤warehouseModeBuy, x + 85, y, 110, 22) && mode != StateWarehouses.TradeMode.BUY_ONLY) {
                state.setTradeMode(StateWarehouses.TradeMode.BUY_ONLY);
            }
            if (EconWidgets.button(ctx, EconTexts.¤¤warehouseModeSell, x + 200, y, 130, 22) && mode != StateWarehouses.TradeMode.SELL_ONLY) {
                state.setTradeMode(StateWarehouses.TradeMode.SELL_ONLY);
            }
            y += 28;

            // Standardize prices button
            if (EconWidgets.button(ctx, EconTexts.¤¤btnStandardize + " (80%/110%)", x, y, 240, 22)) {
                state.standardizeAllPrices(sim.flowPrices());
            }
            y += 28;

            int wage = state.wage();
            text.clear();
            text.add("Warehouse clerk wage: ").add(CompactNumber.format(wage));
            text.color(COLOR.WHITE150);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 24;
            int nextWage = EconWidgets.slider(ctx, "state_wage", x, y, wage, 0, EconConfig.wageMax, EconConfig.wageStep);
            if (nextWage != wage) {
                state.setWage(nextWage);
            }
            y += 30;

            boolean allLiq = state.allLiquidating();
            boolean nextAll = EconWidgets.toggle(ctx, "Liquidate all", allLiq, x, y);
            if (nextAll != allLiq) {
                state.setAllLiquidating(nextAll);
            }
        }
    }

    public static final class TaxesTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);

        public TaxesTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Steuern"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            int x = ctx.windowX + 20;
            int y = yStart;
            text.clear();
            text.add("Per-head tax: ").add(CompactNumber.format(EconConfig.perHeadTax)).add("  Market tax: ").add(String.format("%.0f%%", EconConfig.marketTaxRate * 100.0));
            text.color(COLOR.WHITE150);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 24;
            text.clear();
            text.add("Taxes enabled: ").add(EconConfig.taxesEnabled ? "yes" : "no");
            text.color(EconConfig.taxesEnabled ? COLOR.GREENISH : COLOR.WHITE100);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 24;
            EconConfig.taxesEnabled = EconWidgets.toggle(ctx, "Enable taxes", EconConfig.taxesEnabled, x, y);
        }
    }

    public static final class SocialTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);

        public SocialTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Soziales"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            int x = ctx.windowX + 20;
            int y = yStart;
            text.clear();
            text.add("Religion tax: ").add(EconConfig.religionTaxEnabled ? "on" : "off").add("  Liturgy: ").add(EconConfig.liturgyEnabled ? "on" : "off");
            text.color(COLOR.WHITE150);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 24;
            EconConfig.religionTaxEnabled = EconWidgets.toggle(ctx, "Religion tax", EconConfig.religionTaxEnabled, x, y);
            y += 22;
            EconConfig.liturgyEnabled = EconWidgets.toggle(ctx, "Liturgy", EconConfig.liturgyEnabled, x, y);
        }
    }
}
