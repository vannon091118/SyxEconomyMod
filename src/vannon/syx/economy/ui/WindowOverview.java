package vannon.syx.economy.ui;

import init.sprite.UI.UI;
import snake2d.util.gui.GuiSection;
import snake2d.util.misc.ACTION;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.gui.misc.GText;
import util.gui.panel.GPanel;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.EconIndicators;
import vannon.syx.economy.core.EconProgression;
import vannon.syx.economy.core.EconSnapshot;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.WealthStats;
import snake2d.util.color.COLOR;

/**
 * Übersicht-Fenster: Dashboard, Demografie, Berater.
 */
public final class WindowOverview extends EconWindowBase {

    private static final TabContent[] TABS = {
        new DashboardTab(),
        new DemographicsTab(),
        new AdvisorTab()
    };

    public WindowOverview(EconomySim sim) {
        super(sim);
    }

    @Override
    protected CharSequence title() {
        return "Uebersicht";
    }

    @Override
    protected int panelWidth() { return 780; }

    @Override
    protected TabContent[] tabs() { return TABS; }

    // ─── Tab 1: Dashboard ────────────────────────────────────────────

    private static final class DashboardTab implements TabContent {
        @Override public CharSequence title() { return "Dashboard"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            WealthStats stats = sim.stats();
            EconProgression prog = sim.progression();
            EconIndicators ind = sim.econIndicators();
            long treasury = sim.treasury();

            boolean hasPop = stats.people > 0;

            // KPI row 1
            addKpi(content, x, y, UI.icons().m.coins, "Staatskasse", CompactNumber.format(treasury) + " D",
                !hasPop ? GCOLOR.T().INACTIVE : treasury >= 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
            addKpi(content, x + 240, y, UI.icons().m.citizen, "Bevölkerung", String.valueOf(stats.people),
                hasPop ? GCOLOR.T().NORMAL : GCOLOR.T().INACTIVE);
            addKpi(content, x + 480, y, "Stufe", prog.stage.displayName, GCOLOR.T().NORMAL);
            y += 50;

            // KPI row 2
            addKpi(content, x, y, UI.icons().m.heart, "Gini",
                hasPop ? String.format("%.3f", stats.gini) : "N/A",
                !hasPop ? GCOLOR.T().INACTIVE : stats.gini > 0.40 ? GCOLOR.UI().BAD.normal : stats.gini > 0.35 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().GOOD.normal);
            addKpi(content, x + 240, y, "Median", CompactNumber.format(stats.median) + " D",
                !hasPop ? GCOLOR.T().INACTIVE : stats.median > 100 ? GCOLOR.UI().GOOD.normal : stats.median > 30 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().BAD.normal);
            addKpi(content, x + 480, y, UI.icons().m.pickaxe, "Lohn/Tag", CompactNumber.format((long)sim.laborMarket().meanWage()) + " D",
                !hasPop ? GCOLOR.T().INACTIVE : sim.laborMarket().meanWage() > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
            y += 60;

            // Ampel (traffic lights) — ASCII text indicators, no unicode dots that might render as '?'
            GText ampelHeader = new GText(UI.FONT().M, 256);
            ampelHeader.set("--- AMPANZEIGE ---");
            ampelHeader.lablify();
            content.add(ampelHeader, x, y);
            y += 24;

            addTrafficLight(content, x, y, "Finanzen",
                !hasPop ? -1 : treasury > 10000 ? 2 : treasury > 0 ? 1 : 0);
            addTrendArrow(content, x + 160, y, ind, "treasuryCurrent");
            addTrafficLight(content, x + 240, y, "Gleichheit",
                !hasPop ? -1 : stats.gini < 0.30 ? 2 : stats.gini < 0.40 ? 1 : 0);
            addTrendArrow(content, x + 400, y, ind, "gini");
            addTrafficLight(content, x + 480, y, "Wachstum",
                !hasPop ? -1 : !ind.isTreasuryDeclining() ? 2 : treasury > -5000 ? 1 : 0);
            y += 30;

            addTrafficLight(content, x, y, "Arbeit",
                !hasPop ? -1 : ind.isWagesFalling() ? 0 : sim.firmLedger().lastWorkersUnpaid() == 0 ? 2 : 1);
            addTrafficLight(content, x + 240, y, "Versorgung",
                !hasPop ? -1 : !ind.isFurnishingCrisis() ? 2 : 1);

            // Status text
            y += 40;
            String statusText = buildStatusText(ind, stats, treasury);
            boolean hasWarnings = !allClear(ind) || !hasPop || treasury < 0;
            GText status = new GText(UI.FONT().M, 512);
            status.set(statusText);
            status.color(!hasPop ? GCOLOR.T().INACTIVE : hasWarnings ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().GOOD.normal);
            content.add(status, x, y);

            // History chart — ASCII vertical bars for treasury timeline
            y += 26;
            if (ind.count() > 0) {
                GText histHeader = new GText(UI.FONT().S, 256);
                histHeader.set("--- Kassen-Historie ---");
                histHeader.lablify();
                content.add(histHeader, x, y);
                y += 16;

                int maxBars = Math.min(ind.count(), 20);
                long maxVal = 1;
                for (int i = 0; i < maxBars; i++) {
                    EconSnapshot s = ind.get(i);
                    if (s != null && s.treasuryCurrent > maxVal) maxVal = s.treasuryCurrent;
                }

                for (int i = 0; i < maxBars; i++) {
                    EconSnapshot s = ind.get(i);
                    int level = 0;
                    if (s != null && maxVal > 0) {
                        level = (int)((s.treasuryCurrent * 5) / maxVal);
                        if (level < 0) level = 0;
                        if (level > 5) level = 5;
                    }
                    StringBuilder barStr = new StringBuilder();
                    for (int row = 0; row < 5; row++) {
                        barStr.append(row >= (5 - level) ? '#' : '.');
                    }

                    GText bar = new GText(UI.FONT().S, 32);
                    bar.set(barStr.toString());
                    bar.color(level > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
                    content.add(bar, x + i * 14, y);
                }
                y += 18;

                GText chartLabel = new GText(UI.FONT().S, 256);
                chartLabel.set(CompactNumber.format(maxVal) + " D max — " + maxBars + " Tage");
                chartLabel.color(GCOLOR.T().INACTIVE);
                content.add(chartLabel, x, y);
            }
        }
    }

    // ─── Tab 2: Demographics ─────────────────────────────────────────

    private static final class DemographicsTab implements TabContent {
        @Override public CharSequence title() { return "Demografie"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            WealthStats stats = sim.stats();

            // Stats header
            addKpi(content, x, y, "Siedler", String.valueOf(stats.people), GCOLOR.T().NORMAL);
            addKpi(content, x + 240, y, "Min", CompactNumber.format(stats.min) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 480, y, "Max", CompactNumber.format(stats.max) + " D", GCOLOR.T().NORMAL);
            y += 40;

            addKpi(content, x, y, "Median", CompactNumber.format(stats.median) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 240, y, "Mittel", CompactNumber.format((long)stats.mean) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 480, y, "Gini",
                stats.people > 0 ? String.format("%.3f", stats.gini) : "N/A",
                stats.people > 0 ? (stats.gini > 0.35 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal) : GCOLOR.T().INACTIVE);
            y += 50;

            // Wealth distribution histogram (text-based)
            GText histLabel = new GText(UI.FONT().M, 256);
            histLabel.set("--- Vermögensverteilung ---");
            histLabel.lablify();
            content.add(histLabel, x, y);
            y += 24;

            if (stats.people > 0 && stats.tallest > 0) {
                int barMaxW = 300;
                for (int i = 0; i < WealthStats.BUCKETS && y < 450; i++) {
                    int from = i * stats.bucketWidth;
                    int to = (i + 1) * stats.bucketWidth;
                    int count = stats.histogram[i];
                    int barW = (int)((long)count * barMaxW / stats.tallest);

                    // Bucket label
                    GText lbl = new GText(UI.FONT().S, 64);
                    lbl.set(CompactNumber.format(from) + "-" + CompactNumber.format(to));
                    lbl.color(GCOLOR.T().NORMAL);
                    content.add(lbl, x, y);

                    GText bar = new GText(UI.FONT().S, 256);
                    StringBuilder sb = new StringBuilder();
                    for (int b = 0; b < Math.min(barW / 6, 50); b++) sb.append('#');
                    bar.set(sb.toString());
                    bar.color(count > stats.tallest / 2 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().GOOD.normal);
                    content.add(bar, x + 100, y);

                    GText cnt = new GText(UI.FONT().S, 32);
                    cnt.set(String.valueOf(count));
                    cnt.color(GCOLOR.T().NORMAL);
                    content.add(cnt, x + 100 + barW + 8, y);

                    y += 16;
                }
            } else {
                GText empty = new GText(UI.FONT().M, 128);
                empty.set("Keine Siedler");
                empty.color(GCOLOR.T().NORMAL);
                content.add(empty, x, y);
            }

            // Housing info
            y += 20;
            GText housing = new GText(UI.FONT().M, 256);
            housing.set("Mieteinnahmen: " + CompactNumber.format(sim.housingMarket().lastRentCollected()) + " D  |  Zwangsraeumungen: " + sim.housingMarket().lastEvictions());
            housing.color(GCOLOR.T().NORMAL);
            content.add(housing, x, y);
        }
    }

    // ─── Tab 3: Advisor ──────────────────────────────────────────────

    private static final class AdvisorTab implements TabContent {
        @Override public CharSequence title() { return "Berater"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            EconProgression prog = sim.progression();
            WealthStats stats = sim.stats();
            EconIndicators ind = sim.econIndicators();
            EconSnapshot snap = ind.latest();

            // Stage info
            addKpi(content, x, y, "Stufe", prog.stage.displayName, GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, "Tage in Stufe", String.valueOf(prog.stageDays), GCOLOR.T().NORMAL);
            y += 40;

            addKpi(content, x, y, "Bevölkerung", String.valueOf(stats.people), GCOLOR.T().NORMAL);
            addKpi(content, x + 240, y, "Unbezahlte", String.valueOf(sim.firmLedger().lastWorkersUnpaid()),
                sim.firmLedger().lastWorkersUnpaid() > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
            addKpi(content, x + 480, y, "Tote", String.valueOf(sim.deaths()), GCOLOR.T().NORMAL);
            y += 40;

            addKpi(content, x, y, "Ausgewandert", String.valueOf(sim.emigrations()),
                sim.emigrations() > 0 ? GCOLOR.UI().SOSO.normal : GCOLOR.T().NORMAL);
            addKpi(content, x + 240, y, "Erben", String.valueOf(sim.inherited()), GCOLOR.T().NORMAL);
            addKpi(content, x + 480, y, "Erblos", String.valueOf(sim.heirless()), GCOLOR.T().NORMAL);
            y += 50;

            // Milestones
            GText msHeader = new GText(UI.FONT().M, 256);
            msHeader.set("--- Meilensteine ---");
            msHeader.lablify();
            content.add(msHeader, x, y);
            y += 24;

            addMilestoneIcon(content, x, y, "Erstes Lagerhaus", prog.msFirstStockpile); y += 16;
            addMilestoneIcon(content, x, y, "Erster Export", prog.msFirstExport); y += 16;
            addMilestoneIcon(content, x, y, "Taverne/Markt", prog.msFirstTavern || prog.msFirstMarket); y += 16;
            addMilestoneIcon(content, x, y, "Stabile Loehne (100d)", prog.msStableWages); y += 16;
            addMilestoneIcon(content, x, y, "Erster Tempel", prog.msFirstTemple); y += 16;
            addMilestoneIcon(content, x, y, "Erstes Labor", prog.msFirstLaboratory); y += 16;
            addMilestoneIcon(content, x, y, "Erste Botschaft", prog.msFirstEmbassy); y += 24;

            // Next stage requirements
            GText nextHeader = new GText(UI.FONT().M, 256);
            nextHeader.set("--- Nächste Stufe ---");
            nextHeader.lablify();
            content.add(nextHeader, x, y);
            y += 24;

            String nextReqs = nextStageReqs(prog, stats);
            GText reqs = new GText(UI.FONT().M, 512);
            reqs.set(nextReqs);
            reqs.color(GCOLOR.UI().SOSO.normal);
            content.add(reqs, x, y);

            // Trend snapshot
            if (snap != null) {
                y += 30;
                GText trends = new GText(UI.FONT().M, 512);
                trends.set("Lohn: " + CompactNumber.format((long)snap.actualMeanWage) + "  Gini: " + String.format("%.3f", snap.gini) + "  Kasse: " + CompactNumber.format(snap.treasuryCurrent));
                trends.color(GCOLOR.T().NORMAL);
                content.add(trends, x, y);
            }

            // ── "Was soll ich heute tun?" priority advisor ──────────
            y += 28;
            long treasuryAdv = sim.treasury();
            String advice = buildAdvice(sim, stats, ind, treasuryAdv);
            GText adviceHeader = new GText(UI.FONT().M, 256);
            adviceHeader.set("--- Was soll ich heute tun? ---");
            adviceHeader.lablify();
            content.add(adviceHeader, x, y);
            y += 20;

            GText adviceText = new GText(UI.FONT().M, 512);
            adviceText.set(advice);
            adviceText.color(GCOLOR.UI().SOSO.normal);
            content.add(adviceText, x, y);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private static void addTrafficLight(GuiSection section, int x, int y, String label, int state) {
        // state: -1=gray, 0=red, 1=yellow, 2=green
        // Fixed-width 5-char block bar with varying density + color
        String bar;
        COLOR barColor;
        switch (state) {                case 2:  bar = "#####"; barColor = GCOLOR.UI().GOOD.normal; break;
                case 1:  bar = "###--"; barColor = GCOLOR.UI().SOSO.normal; break;
                case 0:  bar = "#----"; barColor = GCOLOR.UI().BAD.normal; break;
                default: bar = "-----"; barColor = GCOLOR.T().INACTIVE; break;
        }

        GText barText = new GText(UI.FONT().M, 64);
        barText.set(bar);
        barText.color(barColor);
        section.add(barText, x, y);

        GText lbl = new GText(UI.FONT().M, 128);
        lbl.set(label);
        lbl.color(state >= 0 ? GCOLOR.T().NORMAL : GCOLOR.T().INACTIVE);
        section.add(lbl, x + 65, y);
    }

    private static void addMilestoneIcon(GuiSection section, int x, int y, String label, boolean achieved) {
        if (achieved) {
            section.add(UI.icons().s.allRight, x, y + 2);
        } else {
            section.add(UI.icons().s.cancel, x, y + 2);
        }
        GText ms = new GText(UI.FONT().M, 256);
        ms.set(label);
        ms.color(achieved ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
        section.add(ms, x + 20, y);
    }

    private static boolean allClear(EconIndicators ind) {
        return !ind.isInequalityRising() && !ind.isWagesFalling()
            && !ind.isTreasuryDeclining() && !ind.isEmigrationSpike()
            && !ind.isFurnishingCrisis();
    }

    private static String buildStatusText(EconIndicators ind, WealthStats stats, long treasury) {
        if (stats.people == 0) {
            return "Keine Bevoelkerung erfasst — Wirtschaftsdaten noch nicht verfuegbar.";
        }
        StringBuilder sb = new StringBuilder();
        if (treasury < 0) sb.append("Staatskasse negativ! ");
        if (ind.isInequalityRising()) sb.append("Gini steigt! ");
        if (ind.isWagesFalling()) sb.append("Loehne sinken! ");
        if (ind.isTreasuryDeclining()) sb.append("Einnahmen ruecklaeufig! ");
        if (ind.isEmigrationSpike()) sb.append("Abwanderung! ");
        if (ind.isFurnishingCrisis()) sb.append("Einrichtungskrise! ");
        if (sb.length() == 0) {
            return "Wirtschaft stabil — keine Warnungen.";
        }
        return sb.toString().trim();
    }

    private static String nextStageReqs(EconProgression prog, WealthStats stats) {
        switch (prog.stage) {
            case SUBSISTENZ:
                return "50 Siedler, Lagerhaus, 3d Nahrung, 30 Tage in Stufe";
            case HANDEL:
                return "100 Siedler, Export, Lohn>50, Taverne/Markt";
            case INDUSTRIE:
                return "150 Siedler, Labor, Bibliothek, Militaer, 30 Tage";
            case WOHLSTAND:
                return "200 Siedler, 100d ohne Insolvenz, Gini<0.35 30d, Export>10K";
            default:
                return "Maximale Stufe erreicht!";
        }
    }

    /** Priority-based advisor: scans the current state and returns the single most
     *  important action the player should take. Modeled after the old AdvisorTab. */
    private static String buildAdvice(EconomySim sim, WealthStats stats, EconIndicators ind, long treasury) {
        if (stats.people == 0) {
            return "Baue Haeuser und ziehe Siedler an, um die Wirtschaft zu starten.";
        }

        // Priority 1: Unpaid workers → wages too low or treasury empty
        int unpaid = sim.firmLedger().lastWorkersUnpaid();
        if (unpaid > 0 && treasury <= 0) {
            return "Kasse leer! Exportiere Ressourcen oder erhoehe Steuern, um " + unpaid + " unbezahlte Arbeiter zu bezahlen.";
        }
        if (unpaid > stats.people / 4) {
            return "Krise: " + unpaid + " Arbeiter unbezahlt! Sofort Export starten oder Lohn senken (Staat → Lager).";
        }

        // Priority 2: Evictions
        long evictions = sim.housingMarket().lastEvictions();
        if (evictions > 3) {
            return evictions + " Zwangsraeumungen! Mieteinnahmen: " + CompactNumber.format(sim.housingMarket().lastRentCollected()) + " D. Mehr Wohnungen bauen oder Mietzuschuss erhoehen.";
        }

        // Priority 3: Treasury crisis
        if (treasury < -10000) {
            return "Schuldenkrise! Staat muss dringend sparen: Steuern aktivieren, Loehne senken, Ressourcen exportieren.";
        }
        if (treasury < 0) {
            return "Kasse im Minus. Export starten (Lager → Nur verkaufen) oder Steuern erhoehen.";
        }

        // Priority 4: Empty state warehouses
        int whCount = sim.stateWarehouses().ownedCount();
        if (whCount == 0) {
            return "Keine Staatslager gebaut. Ein Lagerhaus als Staat uebernehmen (Rechtsklick → Staatlich).";
        }

        // Priority 5: Furnishing crisis
        if (ind.isFurnishingCrisis()) {
            return "Einrichtungskrise! Holzproduktion erhoehen oder Holz am Markt zukaufen.";
        }

        // Priority 6: Emigration
        if (ind.isEmigrationSpike()) {
            return "Abwanderung! Loehne und Wohnqualitaet verbessern, um Buerger zu halten.";
        }

        // Priority 7: No production
        if (sim.firmLedger().firmFinancialSnapshots().isEmpty()) {
            return "Keine produzierenden Betriebe. Baue Werkstaetten (Holzfaeller, Baecker, Schneider).";
        }

        // OK
        return "Alles im Lot. Weiter Bevoelkerung und Produktion ausbauen.";
    }

    /** Render a trend arrow using vanilla icons next to a traffic light indicator.
     *  Compares the latest snapshot with the second-to-last one. */
    private static void addTrendArrow(GuiSection section, int x, int y, EconIndicators ind, String field) {
        if (ind.count() < 2) return;
        EconSnapshot latest = ind.latest();
        EconSnapshot prev = ind.get(ind.count() - 2);
        if (latest == null || prev == null) return;

        double curr = getSnapshotField(latest, field);
        double past = getSnapshotField(prev, field);

        double delta = curr - past;
        if (Math.abs(delta) < 0.001) {
            section.add(UI.icons().s.arrow_right, x, y + 2);
        } else if (delta > 0) {
            section.add(UI.icons().s.arrowUp, x, y + 2);
        } else {
            section.add(UI.icons().s.arrowDown, x, y + 2);
        }
    }

    private static double getSnapshotField(EconSnapshot snap, String field) {
        switch (field) {
            case "treasuryCurrent": return snap.treasuryCurrent;
            case "gini": return snap.gini;
            default: return 0;
        }
    }
}
