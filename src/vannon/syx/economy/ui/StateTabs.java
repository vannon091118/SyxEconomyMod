package vannon.syx.economy.ui;

import init.sprite.UI.UI;
import snake2d.util.color.COLOR;
import util.gui.misc.GText;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.StateWarehouses;

/** Tabs for the {@link WindowState}.
 *  Player-question translation: every label answers "was passiert wenn ich das ändere?",
 *  not "was steht im Config?". */
public final class StateTabs {

    private StateTabs() {}

    /** Player-question: "Wie verwaltet der Staat seine Vorräte?" */
    public static final class WarehouseTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);

        public WarehouseTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Lager"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            StateWarehouses state = sim.stateWarehouses();
            int x = ctx.windowX + 20;
            int y = yStart;

            // Quick-Status header in plain language
            int owned = state.ownedCount();
            int total = sim.cachedStateWarehouses().size();
            COLOR hdrCol = owned == 0 ? EconStyle.BAD
                         : (owned < total / 2 ? EconStyle.OKAY : EconStyle.GOOD);
            text.clear();
            text.add("Staat hat ").add(owned).add(" eigene Lager von ").add(total).add(" insgesamt");
            text.color(hdrCol);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 24;

            // Trade-Mode buttons with plain German captions
            EconWidgets.text(ctx, "Was soll der Staat am Markt tun?", x, y, COLOR.WHITE150);
            y += 18;
            StateWarehouses.TradeMode mode = state.tradeMode();
            int btnW = Math.min(110, (ctx.windowW - 40) / 3);
            int btnH = 22;
            if (EconWidgets.button(ctx, "Normal handeln", x, y, btnW, btnH)
                    && mode != StateWarehouses.TradeMode.NORMAL) {
                state.setTradeMode(StateWarehouses.TradeMode.NORMAL);
            }
            if (EconWidgets.button(ctx, "Nur einkaufen", x + btnW + 4, y, btnW, btnH)
                    && mode != StateWarehouses.TradeMode.BUY_ONLY) {
                state.setTradeMode(StateWarehouses.TradeMode.BUY_ONLY);
            }
            if (EconWidgets.button(ctx, "Nur verkaufen", x + (btnW + 4) * 2, y, btnW, btnH)
                    && mode != StateWarehouses.TradeMode.SELL_ONLY) {
                state.setTradeMode(StateWarehouses.TradeMode.SELL_ONLY);
            }
            y += 28;

            // Wage slider — label now says "Was zahlen wir Lagerarbeitern?"
            int wage = state.wage();
            EconWidgets.text(ctx, "Lohn für Lagerarbeiter:", x, y, COLOR.WHITE150);
            y += 18;
            text.clear();
            text.add(CompactNumber.format(wage)).add(" denari");
            text.color(COLOR.WHITE200);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 18;
            int nextWage = EconWidgets.slider(ctx, "state_wage", x, y, wage, 0, EconConfig.wageMax, EconConfig.wageStep);
            if (nextWage != wage) {
                state.setWage(nextWage);
            }
            y += 30;

            // Liquidation toggle — caption now says "Alle Lager räumen?"
            boolean allLiq = state.allLiquidating();
            boolean nextAll = EconWidgets.toggle(ctx, "Alle Lager räumen (Notfall)", allLiq, x, y);
            if (nextAll != allLiq) {
                state.setAllLiquidating(nextAll);
            }
            y += 24;

            EconWidgets.text(ctx, "Räumt Lagerbestände in die Stadtkasse — nur in Finanznot.", x, y, COLOR.WHITE100);
        }
    }

    /** Player-question: "Wie viel Geld nimmt der Staat ein?" */
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

            EconWidgets.text(ctx, "Was heute eingezogen wird:", x, y, COLOR.WHITE150);
            y += 18;

            long todayTotal = sim.taxesCollected() + sim.fiscal().headTaxCollected()
                            + sim.fiscal().marketReceipts() + sim.marketReceipts();
            COLOR totalCol = todayTotal > 0 ? EconStyle.GOOD : EconStyle.NA;
            text.clear();
            text.add(CompactNumber.format(todayTotal)).add(" denari heute");
            text.color(totalCol);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 24;

            EconWidgets.text(ctx, "Steuersätze:", x, y, COLOR.WHITE150);
            y += 18;
            text.clear();
            text.add("Kopfsteuer: ").add(CompactNumber.format(EconConfig.perHeadTax)).add(" denari pro Bürger  ·  ")
                .add("Marktsteuer: ").add(String.format("%.0f%%", EconConfig.marketTaxRate * 100.0));
            text.color(COLOR.WHITE150);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 24;

            EconWidgets.text(ctx, "Steuereinzug komplett anhalten?", x, y, COLOR.WHITE150);
            y += 18;
            EconConfig.taxesEnabled = EconWidgets.toggle(ctx, "Steuern einziehen", EconConfig.taxesEnabled, x, y);
            y += 22;
            EconWidgets.text(ctx, "Ausschalten = Bürger behalten alles, der Staat füllt seine Kasse nicht.", x, y, COLOR.WHITE100);
        }
    }

    /** Player-question: "Was tun Religion und Liturgie?" */
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

            EconWidgets.text(ctx, "Was passt heute:", x, y, COLOR.WHITE150);
            y += 18;
            EconWidgets.text(ctx, "Religion treibt Geld für Tempel und Glaube ein.", x, y, COLOR.WHITE100);
            y += 18;
            EconConfig.religionTaxEnabled = EconWidgets.toggle(ctx, "Religion-Steuer einziehen", EconConfig.religionTaxEnabled, x, y);
            y += 22;

            EconWidgets.text(ctx, "Liturgie sammelt regelmäßige Spenden — Stimmung der Bürger steigt.", x, y, COLOR.WHITE100);
            y += 18;
            EconConfig.liturgyEnabled = EconWidgets.toggle(ctx, "Liturgie abhalten", EconConfig.liturgyEnabled, x, y);
            y += 22;

            // Heute eingenommene Beträge
            EconWidgets.text(ctx, "Heute eingenommen:", x, y, COLOR.WHITE150);
            y += 18;
            text.clear();
            text.add("Religion-Steuer: ").add(CompactNumber.format(sim.religionTaxCollected())).add("  ·  ")
                .add("Liturgie: ").add(CompactNumber.format(sim.liturgyCollected()));
            text.color(sim.religionTaxCollected() + sim.liturgyCollected() > 0 ? EconStyle.GOOD : EconStyle.NA);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
        }
    }
}
