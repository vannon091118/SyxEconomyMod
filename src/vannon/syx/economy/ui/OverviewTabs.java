package vannon.syx.economy.ui;

import init.sprite.UI.UI;
import java.util.ArrayList;
import java.util.List;
import settlement.entity.humanoid.Humanoid;
import snake2d.util.color.COLOR;
import util.gui.misc.GStat;
import util.gui.misc.GText;
import vannon.syx.economy.core.ChartPanel;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.EconomySim;

/** Tabs for the {@link WindowOverview}. */
public final class OverviewTabs {

    private OverviewTabs() {}

    /**
     * Single source of truth for card labels, used both as the master-banner
     * subtitle source AND as each card title. Keeping one array prevents the
     * banner from mentioning a subsystem the card row doesn't actually show.
     */
    private static final String[] CARD_LABELS = {
        "Stadtkasse",       // index 0 — also rendered as "Stadtkasse: X denari"
        "Heute bezahlt",    // index 1 — short, avoids overflow on 800×600
        "Einnahmen",        // index 2
        "Staatslager",      // index 3
        "Wohnungen",        // index 4
        "Bevölkerung"       // index 5
    };

    /**
     * 3-Sekunden-Dashboard.
     * <p>Layout (von oben nach unten):
     * <ol>
     *   <li>Master-Banner: ein einziger Indikator "Ist alles in Ordnung?" — Farbe ist
     *       das Schlimmste aller Unter-Systeme. Bei mehreren gleich-schlechten Karten
     *       werden alle namentlich genannt.</li>
     *   <li>2×3 Karten-Grid: eine Karte pro Spielerfrage. Jede Karte hat Ampel-Punkt +
     *       Wert + Trend-Pfeil.</li>
     *   <li>Treasury-Chart: optischer Verlauf der letzten In-Game-Tage.</li>
     * </ol>
     * <p>Grundregel: jede sichtbare Zahl steht neben einer Ampel, jeder Zustand heißt
     * in Spieler-Sprache. Erst nach dem ersten Render werden Trend-Pfeile angezeigt.
     */
    public static final class DashboardTab implements EconTab {
        private final EconomySim sim;
        private final ChartPanel treasuryChart;
        private final GStat treasuryStat;
        private int lastChartW = -1, lastChartH = -1;
        private int lastChartX = -1, lastChartY = -1;

        // Per-card previous-snapshot cache for trend computation. Not persisted —
        // the player only sees "vs last time we looked", and after Load the cache
        // resets to first-render seeding so we never invent a delta that isn't real.
        private long prevTreasury;
        private long prevTaxesCollected;
        private long prevLastIncomePaid;
        private long prevLastIncomeDue;
        private int  prevLastWorkersPaid;
        private int  prevEvictions;
        private int  prevPopulation;
        private int  prevOwnedCount;
        private boolean firstRender = true;

        public DashboardTab(EconomySim sim) {
            this.sim = sim;
            this.treasuryChart = new ChartPanel();
            this.treasuryChart.add(sim.treasuryHistory(), 1.0, COLOR.WHITE100, "Treasury");
            this.treasuryStat = new GStat(UI.FONT().M) {
                @Override public void update(GText text) {
                    text.clear().add(CompactNumber.format(sim.treasury()));
                }
            };
        }

        @Override public CharSequence title() { return "Übersicht"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}
        @Override public boolean drawsOwnHeader() { return true; }

        @Override
        public void render(EconContext ctx, int yStart) {
            // Pull a single render-pass snapshot — cheaper than hammering getters
            // in every draw call and keeps trend deltas stable per frame.
            long treasury       = sim.treasury();
            long taxesCollected = sim.taxesCollected() + sim.marketReceipts() + sim.guildIncomePaid();
            long lastIncomePaid = sim.firmLedger().lastIncomePaid();
            long lastIncomeDue  = sim.firmLedger().lastIncomeDue();
            int  workersPaid    = sim.firmLedger().lastWorkersPaid();
            int  workersUnpaid  = sim.firmLedger().lastWorkersUnpaid();
            int  stateOwned     = sim.stateWarehouses().ownedCount();
            int  stateTotal     = sim.cachedStateWarehouses().size();
            int  evictions      = sim.housingMarket().lastEvictions();
            int  population     = sim.roster().size();

            // ─── Compute per-card status (player-question grouping) ───
            long incomeOutgoing = sim.spent();
            boolean deficit = incomeOutgoing > taxesCollected;

            COLOR treasuryStatus;
            if (treasury < 1000L) treasuryStatus = EconStyle.BAD;
            else if (treasury < 5000L) treasuryStatus = EconStyle.OKAY;
            else treasuryStatus = EconStyle.GOOD;

            COLOR wagesStatus;
            if (lastIncomePaid >= lastIncomeDue && lastIncomeDue > 0L) wagesStatus = EconStyle.GOOD;
            else if (lastIncomePaid > 0L) wagesStatus = EconStyle.OKAY;
            else if (lastIncomeDue > 0L) wagesStatus = EconStyle.BAD;
            else wagesStatus = EconStyle.NA;

            COLOR incomeStatus;
            if (deficit) incomeStatus = EconStyle.BAD;
            else if (taxesCollected <= 0L) incomeStatus = EconStyle.NA;
            else incomeStatus = EconStyle.GOOD;

            COLOR warehousesStatus;
            if (stateOwned == 0) warehousesStatus = EconStyle.BAD;
            else if (stateOwned < stateTotal / 2) warehousesStatus = EconStyle.OKAY;
            else warehousesStatus = EconStyle.GOOD;

            COLOR housingStatus;
            if (evictions > 0) housingStatus = EconStyle.BAD;
            else housingStatus = EconStyle.GOOD;

            COLOR populationStatus = population > 5 ? EconStyle.GOOD : (population > 0 ? EconStyle.OKAY : EconStyle.BAD);

            // ─── Roll-up: worst status wins, surfaces ALL ties ───
            // Iterate cards[] AND labels[] in lockstep. If multiple cards share the
            // worst severity, the banner names all of them so the player knows what
            // to investigate. Use >= so ties are reported. Skip the append when
            // code == codeGood so all-OK doesn't list six subsystem names.
            // Inline integer codes (must be compile-time constants if used in a
            // switch-case later — we use if-else here for that reason).
            final int CODE_BAD  = 2;
            final int CODE_OKAY = 1;
            final int CODE_NA   = 0;
            final int CODE_GOOD = -1;
            COLOR[] cards = {treasuryStatus, wagesStatus, incomeStatus, warehousesStatus, housingStatus, populationStatus};
            int worst = CODE_GOOD;
            int worstCount = 0;
            StringBuilder worstNames = new StringBuilder();
            for (int i = 0; i < cards.length; i++) {
                int code = (cards[i] == EconStyle.BAD) ? CODE_BAD
                         : (cards[i] == EconStyle.OKAY) ? CODE_OKAY
                         : (cards[i] == EconStyle.NA) ? CODE_NA : CODE_GOOD;
                if (code > CODE_GOOD && code >= worst) {
                    if (code > worst) { worstNames.setLength(0); worst = code; worstCount = 0; }
                    if (worstNames.length() > 0) worstNames.append(" · ");
                    worstNames.append(CARD_LABELS[i]);
                    worstCount++;
                }
            }
            // Singular/plural safe phrasing — drop the verb so neither case breaks.
            COLOR masterStatus;
            String bannerTitle;
            String bannerSub;
            if (worst == CODE_BAD) {
                masterStatus = EconStyle.BAD;
                bannerTitle = "Achtung: " + worstNames + " rot";
                bannerSub   = "Sofort prüfen: " + worstNames;
            } else if (worst == CODE_OKAY) {
                masterStatus = EconStyle.OKAY;
                bannerTitle = "Stadt läuft — " + worstNames + " beobachten";
                bannerSub   = "Keine Krise, aber: " + worstNames;
            } else if (worst == CODE_NA) {
                masterStatus = EconStyle.OKAY;
                bannerTitle = "Stadt im Aufbau";
                bannerSub   = "Wirtschaft dreht sich noch ein — schau später wieder rein.";
            } else {
                masterStatus = EconStyle.GOOD;
                bannerTitle = "Alles in Ordnung";
                bannerSub   = "Keine Sorge. Arbeit läuft.";
            }

            // ─── Layout pass ───
            int pad = EconStyle.PAD;
            int x = ctx.windowX + pad;
            int contentW = ctx.windowW - pad * 2;
            int y = yStart;

            DashboardWidgets.masterBanner(ctx, x, y, contentW,
                                            masterStatus, bannerTitle, bannerSub);
            y += EconStyle.BANNER_H + EconStyle.GAP_Y;

            int cardW = EconStyle.cardWidth(contentW, 2);
            int cardH = EconStyle.CARD_H;

            // ─── Trend deltas — null on first render so we never lie about "vs yesterday" ───
            String trendTreasuryGlyph;
            String trendWagesGlyph;
            String trendIncomeGlyph;
            String trendWarehousesGlyph;
            String trendHousingGlyph;
            String trendPopulationGlyph;
            String trendTreasuryDelta;
            String trendWagesDelta;
            String trendIncomeDelta;
            String trendHousingDelta;
            String trendPopulationDelta;
            if (firstRender) {
                trendTreasuryGlyph = trendWagesGlyph = trendIncomeGlyph = null;
                trendWarehousesGlyph = trendHousingGlyph = trendPopulationGlyph = null;
                trendTreasuryDelta = trendWagesDelta = trendIncomeDelta = null;
                trendHousingDelta = trendPopulationDelta = null;
            } else {
                trendTreasuryGlyph = DashboardWidgets.trendFor(treasury, prevTreasury, 50.0, false);
                trendWagesGlyph    = DashboardWidgets.trendFor(lastIncomePaid, prevLastIncomePaid, 1.0, false);
                trendIncomeGlyph   = DashboardWidgets.trendFor(taxesCollected, prevTaxesCollected, 50.0, false);
                trendWarehousesGlyph = stateTotal > 0
                    ? DashboardWidgets.trendFor(stateOwned, prevOwnedCount, 0.5, false) : EconStyle.ARROW_FLAT;
                trendHousingGlyph    = DashboardWidgets.trendFor(evictions, prevEvictions, 0.5, true);
                trendPopulationGlyph = DashboardWidgets.trendFor(population, prevPopulation, 0.5, false);

                trendTreasuryDelta = (trendTreasuryGlyph == null || EconStyle.ARROW_FLAT.equals(trendTreasuryGlyph)) ? null :
                                     (((treasury - prevTreasury) > 0 ? "+" : "") + CompactNumber.format(treasury - prevTreasury));
                trendWagesDelta = (lastIncomePaid != prevLastIncomePaid)
                                  ? ((lastIncomePaid - prevLastIncomePaid > 0 ? "+" : "") + CompactNumber.format(lastIncomePaid - prevLastIncomePaid))
                                  : null;
                trendIncomeDelta = (trendIncomeGlyph == null || EconStyle.ARROW_FLAT.equals(trendIncomeGlyph)) ? null :
                                   (((taxesCollected - prevTaxesCollected) > 0 ? "+" : "") + CompactNumber.format(taxesCollected - prevTaxesCollected));
                long hDelta = (long) evictions - (long) prevEvictions;
                trendHousingDelta = (evictions != prevEvictions)
                    ? (hDelta > 0 ? "+" : "") + hDelta : null;
                long pDelta = (long) population - (long) prevPopulation;
                trendPopulationDelta = (population != prevPopulation)
                    ? (pDelta > 0 ? "+" : "") + pDelta : null;
            }

            // ─── Card 1: Stadtkasse ───
            DashboardWidgets.card(ctx, x, y, cardW, cardH,
                treasuryStatus,
                CARD_LABELS[0],
                CompactNumber.format(treasury) + " denari",
                treasury < 1000L ? "So bald wie möglich auffüllen."
                                 : (treasury < 5000L ? "Kasse wird eng." : "Kasse stabil."),
                trendTreasuryGlyph,
                DashboardWidgets.trendColor(trendTreasuryGlyph),
                trendTreasuryDelta);
            // ─── Card 2: Heute bezahlt ───
            DashboardWidgets.card(ctx, x + cardW + EconStyle.GAP_X, y, cardW, cardH,
                wagesStatus,
                CARD_LABELS[1],
                workersPaid + (workersUnpaid > 0 ? " von " + (workersPaid + workersUnpaid) : ""),
                workersUnpaid > 0
                    ? workersUnpaid + " Beschäftigte haben heute keinen Lohn bekommen."
                    : "Alle Beschäftigten wurden bezahlt.",
                trendWagesGlyph,
                DashboardWidgets.trendColor(trendWagesGlyph),
                trendWagesDelta);
            y += cardH + EconStyle.GAP_Y;

            // ─── Card 3: Einnahmen heute ───
            DashboardWidgets.card(ctx, x, y, cardW, cardH,
                incomeStatus,
                CARD_LABELS[2],
                CompactNumber.format(taxesCollected),
                deficit ? "Ausgaben übersteigen Einnahmen." : "Einnahmen decken Ausgaben.",
                trendIncomeGlyph,
                DashboardWidgets.trendColor(trendIncomeGlyph),
                trendIncomeDelta);
            // ─── Card 4: Staatslager ───
            DashboardWidgets.card(ctx, x + cardW + EconStyle.GAP_X, y, cardW, cardH,
                warehousesStatus,
                CARD_LABELS[3],
                stateOwned + " / " + stateTotal,
                stateOwned == 0 ? "Staat hat keine eigenen Lager."
                                : (stateOwned < stateTotal / 2 ? "Staatliche Lager knapp."
                                                                : "Genügend staatliche Lager vorhanden."),
                trendWarehousesGlyph,
                DashboardWidgets.trendColor(trendWarehousesGlyph),
                null);
            y += cardH + EconStyle.GAP_Y;

            // ─── Card 5: Wohnungen ───
            DashboardWidgets.card(ctx, x, y, cardW, cardH,
                housingStatus,
                CARD_LABELS[4],
                evictions > 0 ? (evictions + " Räumungen") : "Stabil",
                evictions > 0
                    ? "Miete nicht bezahlt — Bewohner wurden ausgewiesen."
                    : "Keine Räumungen in letzter Zeit.",
                trendHousingGlyph,
                DashboardWidgets.trendColor(trendHousingGlyph),
                trendHousingDelta);
            // ─── Card 6: Bevölkerung ───
            DashboardWidgets.card(ctx, x + cardW + EconStyle.GAP_X, y, cardW, cardH,
                populationStatus,
                CARD_LABELS[5],
                population + " Bürger",
                population == 0 ? "Stadt ist leer — Siedler warten."
                                : (population < 10 ? "Stadt wächst noch."
                                                   : "Stadt ist bewohnt."),
                trendPopulationGlyph,
                DashboardWidgets.trendColor(trendPopulationGlyph),
                trendPopulationDelta);
            y += cardH + EconStyle.GAP_Y;

            // Push trend-cache for next frame in one block. firstRender gate is
            // already applied above — the seed here is identical to the regular
            // push, so we just write the snapshot once.
            prevTreasury       = treasury;
            prevTaxesCollected = taxesCollected;
            prevLastIncomePaid = lastIncomePaid;
            prevLastIncomeDue  = lastIncomeDue;
            prevLastWorkersPaid = workersPaid;
            prevEvictions      = evictions;
            prevPopulation     = population;
            prevOwnedCount     = stateOwned;
            firstRender = false;

            // ─── Treasury chart safety: skip if no remaining height ───
            int remainingH = ctx.windowY + ctx.windowH - y - pad;
            if (remainingH >= 16) {
                int chartH = Math.min(80, remainingH);
                int chartW = contentW;
                if (chartW != lastChartW || chartH != lastChartH) {
                    this.treasuryChart.body().setDim(chartW, chartH);
                    lastChartW = chartW;
                    lastChartH = chartH;
                }
                if (x != lastChartX || y != lastChartY) {
                    this.treasuryChart.body().moveX1Y1(x, y);
                    lastChartX = x;
                    lastChartY = y;
                }
                this.treasuryChart.render(ctx.renderer, ctx.ds, false);
            }
        }
    }

    /**
     * Citizens tab — was "Werte + Bürger".
     * Player-question translation:
     * <ul>
     *   <li>"Wie viel haben die Leute?" → Median (typischer Bürger) + Reichster.</li>
     *   <li>"Ist Geld gerecht verteilt?" → Gini-Koeffizient, mit Ampel.</li>
     * </ul>
     */
    public static final class CitizensTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);

        public CitizensTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Vermögen"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            int x = ctx.windowX + 20;
            int y = yStart;

            // stats().median/mean come back as double from WealthStats — keep
            // them double so CompactNumber.format never loses precision.
            double median = sim.stats().median;
            double mean   = sim.stats().mean;
            double gini   = sim.stats().gini;
            COLOR giniStatus = gini < 0.3 ? EconStyle.GOOD : (gini < 0.5 ? EconStyle.OKAY : EconStyle.BAD);

            int labelXEnd = x + 220;
            int valueX    = labelXEnd;

            text.clear();
            text.add("Typischer Bürger:");
            text.color(COLOR.WHITE150);
            text.render(ctx.renderer, x, labelXEnd, y, y + 18);
            text.clear();
            text.add(CompactNumber.format(median) + " denari");
            text.color(median > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
            text.render(ctx.renderer, valueX, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 22;

            text.clear();
            text.add("Durchschnitt:");
            text.color(COLOR.WHITE150);
            text.render(ctx.renderer, x, labelXEnd, y, y + 18);
            text.clear();
            text.add(CompactNumber.format(mean) + " denari");
            text.color(COLOR.WHITE200);
            text.render(ctx.renderer, valueX, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 22;

            int barX = valueX;
            int barW = Math.min(200, ctx.windowX + ctx.windowW - 20 - barX - 80);
            int barH = 14;
            text.clear();
            text.add("Verteilung: ");
            text.color(COLOR.WHITE150);
            text.render(ctx.renderer, x, labelXEnd, y, y + barH);
            COLOR.WHITE15.render(ctx.renderer, barX, barX + barW, y, y + barH);
            int fill = (int) Math.max(2, Math.min(barW, barW * gini));
            giniStatus.render(ctx.renderer, barX, barX + fill, y, y + barH);
            String giniWord = gini < 0.3 ? "gerecht" : (gini < 0.5 ? "ungleich" : "sehr ungleich");
            text.clear();
            text.add(String.format("%.2f · ", gini)).add(giniWord);
            text.color(giniStatus);
            int wordX = barX + barW + 6;
            text.render(ctx.renderer, wordX, ctx.windowX + ctx.windowW - 20, y, y + barH);
            y += 22;

            Humanoid richest = sim.cachedRichestCitizen();
            COLOR bg = richest == null ? EconStyle.OKAY : EconStyle.GOOD;
            text.clear();
            text.add("Reichster Bürger:");
            text.color(COLOR.WHITE150);
            text.render(ctx.renderer, x, labelXEnd, y, y + 18);
            text.clear();
            if (richest == null) {
                text.add("(noch niemand)");
            } else {
                text.add(richest.race().info.name).add(" · ")
                    .add(CompactNumber.format(sim.wallets().get(richest)))
                    .add(" denari");
            }
            text.color(bg);
            text.render(ctx.renderer, valueX, ctx.windowX + ctx.windowW - 20, y, y + 18);
        }
    }

    /**
     * Advisor tab — was the "Defizit vs Stabil" tab. Now answers:
     * "Was sollte ich heute tun?" — one sentence, no jargon.
     */
    public static final class AdvisorTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);

        public AdvisorTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Ratgeber"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            long t = sim.treasury();
            long in = sim.taxesCollected() + sim.marketReceipts() + sim.guildIncomePaid();
            long out = sim.spent() + sim.wagesPaid();
            int  evictions = sim.housingMarket().lastEvictions();
            int  unpaid   = sim.firmLedger().lastWorkersUnpaid();
            int  warehouses = sim.stateWarehouses().ownedCount();

            String advice;
            COLOR tone;
            if (out > in * 2) { advice = "Stadt gibt viel mehr aus als ein — Steuern erhöhen oder Ausgaben senken."; tone = EconStyle.BAD; }
            else if (t < 1000) { advice = "Kasse wird bald leer — königliche Hilfen oder Subventionen prüfen."; tone = EconStyle.BAD; }
            else if (unpaid > 0) { advice = unpaid + " Beschäftigte warten auf Lohn — Steuern oder Staatsausgaben anpassen."; tone = EconStyle.OKAY; }
            else if (evictions > 0) { advice = "Mieter werden ausgewiesen — Wohnungen oder Subventionen für Bürger prüfen."; tone = EconStyle.OKAY; }
            else if (warehouses == 0) { advice = "Staat hat keine eigenen Lager — für Krisenzeiten Lager bauen."; tone = EconStyle.OKAY; }
            else                    { advice = "Alles läuft ruhig. Kein Eingriff nötig."; tone = EconStyle.GOOD; }

            int x = ctx.windowX + 20;
            int y = yStart;
            EconWidgets.text(ctx, "Rat des Stadtökonoms:", x, y, COLOR.WHITE150);
            y += 18;
            text.clear();
            text.add(advice);
            text.color(tone);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 24;

            EconWidgets.text(ctx, "Heutige Zahlen im Überblick:", x, y, COLOR.WHITE150);
            y += 18;
            text.clear();
            text.add("Einnahmen ").add(CompactNumber.format(in)).add("  ·  ")
                .add("Ausgaben ").add(CompactNumber.format(out));
            text.color(in >= out ? EconStyle.GOOD : EconStyle.BAD);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
        }
    }
}
