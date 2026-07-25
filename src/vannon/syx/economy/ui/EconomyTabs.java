package vannon.syx.economy.ui;

import init.resources.RESOURCE;
import init.sprite.UI.UI;
import java.util.List;
import snake2d.util.color.COLOR;
import util.gui.misc.GText;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.FlowMeter;
import vannon.syx.economy.core.FlowPrices;

/** Tabs for the {@link WindowEconomy}.
 *  Player-question translation: prices are "which goods are cheap / scarce",
 *  wages are "are workers getting paid", subsidies are "what is the state helping with". */
public final class EconomyTabs {

    private EconomyTabs() {}

    /** Player-question: "Welche Güter sind knapp und welche günstig?" */
    public static final class PricesTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);
        private int scroll = 0;

        public PricesTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Marktpreise"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            List<RESOURCE> resources = sim.cachedAllResources();
            FlowPrices prices = sim.flowPrices();
            FlowMeter.Snapshot flow = sim.flowMeter().snapshot();
            int rowH = 18;
            int headerH = 32;
            int viewportH = ctx.windowY + ctx.windowH - yStart - 20 - headerH;
            int visible = Math.max(1, viewportH / rowH);
            int maxScroll = Math.max(0, resources.size() - visible);
            scroll = EconWidgets.scrollbar(ctx, "prices", resources.size(), visible, scroll,
                ctx.windowX + ctx.windowW - 24, yStart + headerH, viewportH);

            // Section header (above the scrollable list, not inside the scroll)
            EconWidgets.text(ctx, "Preis pro Stück (aktueller Markt vs Anker):", ctx.windowX + 20, yStart, COLOR.WHITE150);

            int y = yStart + headerH;
            int x = ctx.windowX + 20;

            for (int i = scroll; i < resources.size() && i < scroll + visible; i++) {
                RESOURCE res = resources.get(i);
                int idx = res.index();
                int local = idx < flow.size() ? prices.priceRoundedUp(idx) : 0;
                double anchor = idx < flow.size() ? prices.anchor(idx) : 0.0;

                // Traffic light: cheap if price < anchor * 0.7, scarce if > anchor * 1.3
                COLOR rowCol;
                String word;
                if (anchor <= 0.0) {
                    rowCol = EconStyle.NA;
                    word = "(kein Anker)";
                } else {
                    double ratio = local / anchor;
                    if (ratio > 1.30) { rowCol = EconStyle.BAD; word = "knapp"; }
                    else if (ratio > 1.10) { rowCol = EconStyle.OKAY; word = "teuer"; }
                    else if (ratio < 0.70) { rowCol = EconStyle.OKAY; word = "günstig"; }
                    else { rowCol = EconStyle.GOOD; word = "normal"; }
                }
                // Status dot
                int dotSize = EconStyle.DOT_SIZE;
                int dotX = x;
                int dotY = y + 4;
                rowCol.render(ctx.renderer, dotX, dotX + dotSize, dotY, dotY + dotSize);

                text.clear();
                text.add(res.name).add("   ").add(CompactNumber.format(local))
                    .add("  (").add(word).add(")");
                text.color(COLOR.WHITE200);
                text.render(ctx.renderer, x + dotSize + 8, ctx.windowX + ctx.windowW - 32, y, y + rowH);
                y += rowH;
            }
        }
    }

    /** Player-question: "Werden meine Leute bezahlt und läuft die Wirtschaft?" */
    public static final class WagesFirmsTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);

        public WagesFirmsTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Gehälter"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            int x = ctx.windowX + 20;
            int y = yStart;

            long lastPaid = sim.firmLedger().lastIncomePaid();
            long lastDue  = sim.firmLedger().lastIncomeDue();
            int  paidCount   = sim.firmLedger().lastWorkersPaid();
            int  unpaidCount = sim.firmLedger().lastWorkersUnpaid();

            // Hero: workers paid
            COLOR statusCol;
            if (lastPaid >= lastDue && lastDue > 0L) statusCol = EconStyle.GOOD;
            else if (lastPaid > 0L) statusCol = EconStyle.OKAY;
            else if (lastDue > 0L) statusCol = EconStyle.BAD;
            else statusCol = EconStyle.NA;

            EconWidgets.text(ctx, "Wie viele Beschäftigte haben heute ihren Lohn bekommen?", x, y, COLOR.WHITE150);
            y += 18;
            text.clear();
            text.add(paidCount + (unpaidCount > 0 ? " von " + (paidCount + unpaidCount) : ""))
                .add(unpaidCount > 0 ? (" · " + unpaidCount + " ohne Lohn") : "");
            text.color(statusCol);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 22;

            EconWidgets.text(ctx, "Durchschnittlicher Lohn:", x, y, COLOR.WHITE150);
            y += 18;
            text.clear();
            text.add(CompactNumber.format((long)sim.laborMarket().meanWage())).add(" denari pro Tag");
            text.color(COLOR.WHITE200);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 22;

            // Zusammenfassung
            EconWidgets.text(ctx, "Wie viel Geld hat die Wirtschaft heute eingenommen?", x, y, COLOR.WHITE150);
            y += 18;
            COLOR summaryCol = lastPaid < lastDue ? EconStyle.BAD : COLOR.WHITE200;
            text.clear();
            text.add("Geplant: ").add(CompactNumber.format(lastDue)).add(" denari  ·  ")
                .add("Bezahlt: ").add(CompactNumber.format(lastPaid)).add(" denari");
            text.color(summaryCol);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
        }
    }

    /** Player-question: "Welche Waren unterstützt der Staat mit Subventionen?" */
    public static final class SubsidiesTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);
        private int scroll = 0;

        public SubsidiesTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Hilfen"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            List<RESOURCE> resources = sim.cachedAllResources();
            int rowH = 18;
            int headerH = 32;
            int viewportH = ctx.windowY + ctx.windowH - yStart - 20 - headerH;
            int visible = Math.max(1, viewportH / rowH);
            int maxScroll = Math.max(0, resources.size() - visible);
            scroll = EconWidgets.scrollbar(ctx, "subsidies", resources.size(), visible, scroll,
                ctx.windowX + ctx.windowW - 24, yStart + headerH, viewportH);

            int subsidisedCount = 0;
            for (RESOURCE r : resources) {
                if (sim.productionSubsidies().bounty(r) > 0) subsidisedCount++;
            }

            EconWidgets.text(ctx,
                "Staat fördert aktuell " + subsidisedCount + " von " + resources.size() + " Gütern.",
                ctx.windowX + 20, yStart, subsidisedCount > 0 ? COLOR.WHITE150 : EconStyle.NA);

            int y = yStart + headerH;
            int x = ctx.windowX + 20;

            for (int i = scroll; i < resources.size() && i < scroll + visible; i++) {
                RESOURCE res = resources.get(i);
                int bounty = sim.productionSubsidies().bounty(res);
                // Color the row by whether it's getting subsidies
                COLOR rowCol = bounty > 0 ? EconStyle.GOOD : EconStyle.NA;
                int dotSize = EconStyle.DOT_SIZE;
                int dotX = x;
                int dotY = y + 4;
                rowCol.render(ctx.renderer, dotX, dotX + dotSize, dotY, dotY + dotSize);

                text.clear();
                text.add(res.name).add("  ").add(CompactNumber.format(bounty))
                    .add(bounty > 0 ? " denari pro Stück" : "  (keine Förderung)");
                text.color(bounty > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
                text.render(ctx.renderer, x + dotSize + 8, ctx.windowX + ctx.windowW - 32, y, y + rowH);
                y += rowH;
            }
        }
    }
}
