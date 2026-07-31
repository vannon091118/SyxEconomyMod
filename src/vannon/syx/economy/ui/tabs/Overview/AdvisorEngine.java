package vannon.syx.economy.ui.tabs.Overview;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import vannon.syx.economy.core.EconIndicators;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.WealthStats;

import java.util.List;

/**
 * Sprint v0.13.105+/M-UI-2 — Causality-Layer Engine.
 *
 * <p>Ersetzt die einfache 7-fach If/Else-Kaskade in
 * {@code OverviewHelpers.buildAdvice} durch ein Triplet-Modell mit Top-3
 * Alternativen und Trade-off-Tabelle:</p>
 *
 * <ol>
 *   <li><b>Wahrscheinlichkeit p (0-100%)</b> — Hybrid: Base-Konstante pro
 *       Case ± Modifier je nach {@code EconSnapshot}-Schwere.</li>
 *   <li><b>Empfehlung A</b> — deterministische Top-1 Maßnahme (Hauptaktion).</li>
 *   <li><b>Alternativen [B, C]</b> — Top-2 Folgeoptionen aus
 *       {@link ActionLibrary} mit 4-spaltiger Trade-off-Tabelle
 *       (Cash / Loyalty / Production / Risk-Score).</li>
 * </ol>
 *
 * <p>Rule-15 konform: keine static-final Engine-Touches. Trade-off-Konstanten
 * in {@link ActionLibrary} sind hand-codiert, deterministisch, ohne Live-
 * Calculation.</p>
 *
 * <p>Rule-14 konform: ~190 SLOC (unter 600 warn).</p>
 */
public final class AdvisorEngine {

    private AdvisorEngine() {}

    // ─── Data-Models ─────────────────────────────────────────────────

    /** Trade-off-Spalten einer Alternative. Werte sind deterministisch in
     *  {@link ActionLibrary} definiert; keine Live-Calculation. */
    public record Alternative(
        String action,
        int cashDeltaPerDay,    // positive = Einnahme, negative = Kosten, 0 = neutral
        double loyaltyDelta,    // ±0.0..±0.20 Typical-Range, 0 = neutral
        double productionDelta, // ±0.0..±0.20 Typical-Range, 0 = neutral
        int riskScore           // 0..100, höher = riskanter (z.B. Lohnkürzung)
    ) {
        public String formatCash() {
            if (cashDeltaPerDay == 0) return "±0";
            return (cashDeltaPerDay > 0 ? "+" : "") + cashDeltaPerDay + " D/d";
        }
        public String formatLoyalty() {
            if (loyaltyDelta == 0) return "±0";
            return (loyaltyDelta > 0 ? "+" : "") + String.format("%.2f", loyaltyDelta);
        }
        public String formatProduction() {
            if (productionDelta == 0) return "±0";
            return (productionDelta > 0 ? "+" : "") + String.format("%.2f", productionDelta);
        }
        public String formatRisk() {
            return riskScore + "%";
        }
    }

    /** Triplet-Result der Advice-Cascade. */
    public record Advice(
        String recommendation,
        int probability,    // 0..100
        List<Alternative> alternatives
    ) {}

    // ─── Trade-off-Bibliothek ────────────────────────────────────────

    /** Hand-curated Trade-off-Konstanten für 9 typische Spieler-Maßnahmen.
     *  Werte sind deterministisch (kein Engine-Touch) und werden vom
     *  Sprint-Impl-Reviewer fixiert. */
    public enum ActionLibrary {
        TAX_RAISE_5PCT(new Alternative(
            "Steuer +5%", +300, -0.05, 0.00, 25)),
        TAX_RAISE_15PCT(new Alternative(
            "Steuer +15%", +800, -0.12, -0.05, 55)),
        EXPORT_SURPLUS(new Alternative(
            "Ueberschuss exportieren", +150, 0.00, -0.10, 10)),
        WAGE_CUT_25PCT(new Alternative(
            "Loehne -25%", +250, -0.18, -0.15, 65)),
        WAGE_TOPUP_10PCT(new Alternative(
            "Loehne +10%", -150, +0.10, +0.05, 15)),
        HOUSING_BONUS(new Alternative(
            "Mietzuschuss +20 D/Head", -100, +0.12, +0.05, 8)),
        BUILD_WORKSHOP(new Alternative(
            "Produktionsgebaeude bauen", -50, +0.05, +0.20, 20)),
        FOOD_SUBSIDY(new Alternative(
            "Nahrungssubvention", -150, +0.10, +0.10, 12)),
        WAIT_AND_SEE(new Alternative(
            "Abwarten, beobachten", 0, 0.00, 0.00, 0));

        public final Alternative alt;
        ActionLibrary(Alternative alt) { this.alt = alt; }
    }

    // ─── Causality-Cascade (7 Priorities) ────────────────────────────

    /** Cascading advice logic — preserves Priority-Order from the old
     *  {@code OverviewHelpers.buildAdvice} (B-013, B-009, B-004, H8, B-010
     *  aware) but emits a Triplet with probability and trade-off alternatives.
     *
     *  <p>Wichtig: ändert KEINE Engine-State. Liest nur via
     *  {@code sim.*}-Methoden (Rule-15 safe).</p> */
    public static Advice buildAdvice(EconomySim sim, WealthStats stats, EconIndicators ind, long treasury) {
        // Case 0: Keine Bevölkerung (Bootstrap)
        if (stats.people == 0) {
            return new Advice(
                "Baue Haeuser und ziehe Siedler an, um die Wirtschaft zu starten.",
                95,
                List.of(ActionLibrary.HOUSING_BONUS.alt, ActionLibrary.WAIT_AND_SEE.alt, ActionLibrary.BUILD_WORKSHOP.alt));
        }

        int unpaid = sim.firmLedger().lastWorkersUnpaid();
        boolean severeScarcity = sim.scarcitySignal() != null && sim.scarcitySignal().maxScarcity() > 0.7;

        // Case 1: Unpaid + Kasse leer (CRITICAL, B-013 aware)
        if (unpaid > 0 && treasury <= 0) {
            int p = 60 + (treasury < -5000 ? 30 : 0) + (severeScarcity ? -10 : 0);
            String header = severeScarcity
                ? "Kasse leer bei Knappheit! Steuern erhoehen UND Produktionsgebaeude bauen — Export ist zu teuer."
                : "Kasse leer! Exportiere Ressourcen oder erhoehe Steuern sofort, um " + unpaid + " unbezahlte Arbeiter zu bezahlen.";
            return new Advice(header, p,
                List.of(ActionLibrary.EXPORT_SURPLUS.alt, ActionLibrary.TAX_RAISE_5PCT.alt, ActionLibrary.WAGE_CUT_25PCT.alt));
        }

        // Case 2: Unpaid > 25% (CRITICAL)
        if (unpaid > stats.people / 4) {
            int p = 50 + (severeScarcity ? -15 : 5);
            String header = severeScarcity
                ? "Krise: " + unpaid + " Arbeiter unbezahlt! Produktionsgebaeude bauen statt Export — Knappheit "
                    + String.format("%.0f", sim.scarcitySignal().maxScarcity() * 100) + "%."
                : "Krise: " + unpaid + " Arbeiter unbezahlt! Sofort Export starten oder Lohn senken (Staat → Lager).";
            return new Advice(header, p,
                List.of(ActionLibrary.EXPORT_SURPLUS.alt, ActionLibrary.WAGE_CUT_25PCT.alt, ActionLibrary.BUILD_WORKSHOP.alt));
        }

        // Case 3: Evictions (HOUSING)
        long evictions = sim.housingMarket().lastEvictions();
        if (evictions > 3) {
            int p = 70;
            String header = evictions + " Zwangsraeumungen! Mieteinnahmen: " + formatRent(sim.housingMarket().lastRentCollected())
                + " D. Mehr Wohnungen bauen oder Mietzuschuss erhoehen.";
            return new Advice(header, p,
                List.of(ActionLibrary.HOUSING_BONUS.alt, ActionLibrary.BUILD_WORKSHOP.alt, ActionLibrary.WAGE_TOPUP_10PCT.alt));
        }

        // Case 4: Treasury Crisis (-10K kritisch)
        if (treasury < -10000) {
            int p = 80 + (treasury < -50000 ? 10 : 0);
            return new Advice(
                "Schuldenkrise! Staat muss dringend sparen: Steuern aktivieren, Loehne senken, Ressourcen exportieren.",
                p,
                List.of(ActionLibrary.TAX_RAISE_15PCT.alt, ActionLibrary.WAGE_CUT_25PCT.alt, ActionLibrary.EXPORT_SURPLUS.alt));
        }

        // Case 5: Treasury im Minus (WARNING)
        if (treasury < 0) {
            int p = 65 + (Math.abs(treasury) > 5000 ? 10 : 0);
            return new Advice(
                "Kasse im Minus. Export starten (Lager → Nur verkaufen) oder Steuern erhoehen.",
                p,
                List.of(ActionLibrary.EXPORT_SURPLUS.alt, ActionLibrary.TAX_RAISE_5PCT.alt, ActionLibrary.FOOD_SUBSIDY.alt));
        }

        // Case 6: Empty State Warehouses
        int whCount = sim.stateWarehouses().ownedCount();
        if (whCount == 0) {
            int p = 75;
            return new Advice(
                "Keine Staatslager gebaut. Lagerhaus als Staat uebernehmen (Rechtsklick → Staatlich).",
                p,
                List.of(ActionLibrary.EXPORT_SURPLUS.alt, ActionLibrary.WAIT_AND_SEE.alt, ActionLibrary.BUILD_WORKSHOP.alt));
        }

        // Case 7: Furnishing Crisis (B-004 aware)
        if (ind.isFurnishingCrisis()) {
            int p = 70;
            return new Advice(
                "Einrichtungskrise! Holzproduktion erhoehen oder Holz am Markt zukaufen.",
                p,
                List.of(ActionLibrary.BUILD_WORKSHOP.alt, ActionLibrary.FOOD_SUBSIDY.alt, ActionLibrary.EXPORT_SURPLUS.alt));
        }

        // Case 8: Chain-Bottleneck (H8 IO-Analysis aware)
        if (sim.ioMatrix() != null && sim.ioMatrix().isValid()) {
            int worstChain = 0;
            String worstResource = "";
            for (int i = 0; i < sim.ioMatrix().size() && i < RESOURCES.ALL().size(); ++i) {
                if (sim.flowPrices().coverage(i) >= 0.5) continue;
                int affected = OverviewHelpers.countChainAffected(sim, i);
                if (affected > worstChain) {
                    worstChain = affected;
                    RESOURCE res = (RESOURCE) RESOURCES.ALL().get(i);
                    worstResource = res.name.toString();
                }
            }
            if (worstChain >= 3) {
                int p = 60;
                return new Advice(
                    "Ketten-Engpass: " + worstResource + "-Mangel wirkt sich ueber " + worstChain
                        + " Ressourcen in der Kette aus! Produktion hochfahren oder am Markt zukaufen.",
                    p,
                    List.of(ActionLibrary.BUILD_WORKSHOP.alt, ActionLibrary.EXPORT_SURPLUS.alt, ActionLibrary.FOOD_SUBSIDY.alt));
            }
        }

        // Case 9: Emigration (B-010 aware)
        if (ind.isEmigrationSpike()) {
            int p = 65;
            return new Advice(
                "Abwanderung! Loehne und Wohnqualitaet verbessern, um Buerger zu halten.",
                p,
                List.of(ActionLibrary.WAGE_TOPUP_10PCT.alt, ActionLibrary.HOUSING_BONUS.alt, ActionLibrary.FOOD_SUBSIDY.alt));
        }

        // Case 10: No Production (Bootstrapping)
        if (sim.firmLedger().firmFinancialSnapshots().isEmpty()) {
            int p = 80;
            return new Advice(
                "Keine produzierenden Betriebe. Baue Werkstaetten (Holzfaeller, Baecker, Schneider).",
                p,
                List.of(ActionLibrary.BUILD_WORKSHOP.alt, ActionLibrary.WAIT_AND_SEE.alt, ActionLibrary.HOUSING_BONUS.alt));
        }

        // Fail-Open: OK + Wachstumstipp (Sprint-Impl-Feature: Berater statt nur Krisenmanager)
        return new Advice(
            "Alles im Lot. Wachstumstipp: Baue deine Export-Infrastruktur weiter aus — Werkstaetten + Exporthandel.",
            95,
            List.of(ActionLibrary.BUILD_WORKSHOP.alt, ActionLibrary.EXPORT_SURPLUS.alt, ActionLibrary.HOUSING_BONUS.alt));
    }

    private static String formatRent(long rent) {
        // Plain formatter ohne CompactNumber-Abhängigkeit — hält AdvisorEngine Engine-Touch-frei.
        if (rent < 1000) return String.valueOf(rent);
        if (rent < 1000000) return String.valueOf(rent / 1000) + "k";
        return String.format("%.1fM", rent / 1000000.0);
    }
}
