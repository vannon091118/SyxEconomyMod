package vannon.syx.economy.ui;

import snake2d.util.color.COLOR;
import util.colors.GCOLOR;
import vannon.syx.economy.core.FlowPrices;
import java.util.Arrays;

/**
 * Sprint v0.13.104+M-UI-1 — Shared KPI display helpers. SeverityClassifier +
 * Color-Helper für 16 Tabs, dedupliziert Color-Decisions die ursprünglich
 * in WindowQuickview.build() und WindowEconomy.PricesTab
 * 1:1 dupliziert waren.
 *
 * <p>DRY-Refactor: ~70 LOC Duplikation entfernt, Single Source-of-Truth für
 * Severity-Schwellwerte. Spieler bekommt konsistente Ampel-Logik in Quickview
 * UND PricesTab UND zukünftigen Tabs (Lever-Discovery, Heatmap).</p>
 *
 * <p>Rule-15 konform: keine {@code static final}-Init mit Engine-Singletons.
 * Alle Methoden arbeiten auf bereits aufgelösten Objekten (FlowPrices-Referenz,
 * long/double-Primitive). Sanctioniert auch außerhalb der Init-Hook-Pfade.</p>
 *
 * <p>God-Class-Guard Rule 14: ~150 SLOC, 0 fields, 4 public methods — bleibt
 * weit unter allen Hard-Blocks. KpiSection ist KEIN Window* (Regel 6 exempt),
 * aber unkritisch klein.</p>
 */
public final class KpiSection {

    private KpiSection() {}

    // ─── SeverityClassifier (Sprint v0.13.104+M-UI-1) ────────────────
    // Konsistente Schwellwerte zwischen PricesTab-Status-Badge und zukünftigen
    // Tabs (Lever-Discovery, Heatmap). Coverage-basierte 4-Stufen-Klassifikation:
    // CRITICAL (Mangel) → LOW (knapp) → OK → SURPLUS (Überschuss).
    //
    // Schwellwerte (fix, an Price-Tab gekoppelt):
    //   CRITICAL: coverage < 0.3      → BAD-Rot
    //   LOW:      0.3 ≤ coverage < 0.7 → SOSO-Gelb
    //   OK:       0.7 ≤ coverage ≤ 3.0 → GOOD-Grün
    //   SURPLUS:  coverage > 3.0       → GOOD-Grün
    //
    // Preis-Logik in PricesTab:alter Code war äquivalent (coverage < 0.3/0.7/3.0),
    // also 1:1 übernommen — keine Verhaltens-Änderung, nur Deduplizierung.

    public enum Severity {
        CRITICAL, LOW, OK, SURPLUS;

        /** Coverage → Severity. Sprint M-UI-1.1 Polishing Pathological-Values-Policy:
         *  NaN und +Infinity → OK (Division-durch-0 oder "sehr grosser Stock" sind
         *  Daten-Stale und sollen keine False-Positive-CRITICAL-Alarme auslösen).
         *  -Infinity, negative finite, exakt 0.0 → CRITICAL (echte State-Bugs
         *  oder Out-of-Stock sollen laut sichtbar sein statt still unterdrückt).
         *  Vorherige Implementation hatte `!Double.isFinite()` was -Infinity
         *  fälschlicherweise als OK maskierte (Bug, Test-classify_negative_infinity_returns_critical
         *  schlug fehl). */
        public static Severity classify(double coverage) {
            if (Double.isNaN(coverage) || coverage == Double.POSITIVE_INFINITY) return OK;
            if (coverage < 0.3) return CRITICAL;
            if (coverage < 0.7) return LOW;
            if (coverage > 3.0) return SURPLUS;
            return OK;
        }

        /** True wenn Spieler eingreifen sollte (Mangel oder knapp). */
        public boolean isProblem() {
            return this == CRITICAL || this == LOW;
        }

        /** Kurz-Badge für Tabellen-Spalte. */
        public String badge() {
            switch (this) {
                case CRITICAL: return "MANGL";
                case LOW:      return "knapp";
                case OK:       return "ok";
                case SURPLUS:  return "UEBERSCH.";
                default:       return "?";
            }
        }
    }

    // ─── Filter-Modi (Sprint v0.13.104+M-UI-1) ───────────────────────
    // 4 Filter-Chip-Modi für PricesTab. Default PROBLEM_ONLY wenn currentFilter
    // nicht gesetzt ist (wird in PricesTab statisch auf 1 initialisiert).

    public enum FilterMode {
        ALL,              // 0: alle Resources
        PROBLEM_ONLY,     // 1: nur Mangel+Knapp (CRITICAL+LOW) — Default
        SURPLUS_ONLY,     // 2: nur Überschuss (SURPLUS)
        CRITICAL_ONLY;    // 3: nur harter Mangel (CRITICAL)

        public boolean accepts(Severity sev) {
            switch (this) {
                case ALL:           return true;
                case PROBLEM_ONLY:  return sev.isProblem();
                case SURPLUS_ONLY:  return sev == Severity.SURPLUS;
                case CRITICAL_ONLY: return sev == Severity.CRITICAL;
                default:            return true;
            }
        }

        public String chipLabel() {
            switch (this) {
                case ALL:           return "Alle";
                case PROBLEM_ONLY:  return "Mangel+Knapp";
                case SURPLUS_ONLY:  return "Ueberschuss";
                case CRITICAL_ONLY: return "Nur Mangel";
                default:            return "?";
            }
        }
    }

    // ─── Color-Helper (DRY aus WindowQuickview extrahiert) ───────────
    // Spieler-Ampel-Logik konsistent: GOOD/SOSO/BAD/INACTIVE/NORMAL.

    public static COLOR colorForTreasury(long treasury, boolean hasPop) {
        if (!hasPop) return GCOLOR.T().INACTIVE;
        return treasury >= 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal;
    }

    public static COLOR colorForGini(double gini, boolean hasPop) {
        if (!hasPop) return GCOLOR.T().INACTIVE;
        if (gini > 0.40) return GCOLOR.UI().BAD.normal;
        if (gini > 0.35) return GCOLOR.UI().SOSO.normal;
        return GCOLOR.UI().GOOD.normal;
    }

    public static COLOR colorForMedian(int median, boolean hasPop) {
        if (!hasPop) return GCOLOR.T().INACTIVE;
        if (median > 100) return GCOLOR.UI().GOOD.normal;
        if (median > 30)  return GCOLOR.UI().SOSO.normal;
        return GCOLOR.UI().BAD.normal;
    }

    public static COLOR colorForWage(double wage, boolean hasPop) {
        if (!hasPop) return GCOLOR.T().INACTIVE;
        return wage > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal;
    }

    public static COLOR colorForUnpaid(int unpaid) {
        return unpaid > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal;
    }

    public static COLOR colorForEmigration(int emigration) {
        return emigration > 3 ? GCOLOR.UI().SOSO.normal : GCOLOR.T().NORMAL;
    }

    public static COLOR colorForSeverity(Severity sev) {
        switch (sev) {
            case CRITICAL: return GCOLOR.UI().BAD.normal;
            case LOW:      return GCOLOR.UI().SOSO.normal;
            case OK:       return GCOLOR.UI().GOOD.normal;
            case SURPLUS:  return GCOLOR.UI().GOOD.normal;
            default:       return GCOLOR.T().NORMAL;
        }
    }

    // ─── Sort-Helper (Sprint v0.13.104+M-UI-1) ───────────────────────
    // Severity-Sort: coverage ASC (kritischste zuerst). Liefert Index-Array
    // das ggf. vom Caller gefiltert wird. O(N log N), einmal pro Tab-build().
    //
    // Bei 100+ Resources (Songs of Syx realistisch) N log N ≈ 660 ops, läuft
    // einmal pro Tab-Open. Vernachlässigbar.

    public static int[] sortIndicesByCoverageAsc(FlowPrices fp, int total) {
        if (fp == null || total <= 0) return new int[0];
        Integer[] boxed = new Integer[total];
        for (int i = 0; i < total; i++) boxed[i] = i;
        Arrays.sort(boxed, (a, b) -> Double.compare(fp.coverage(a), fp.coverage(b)));
        int[] out = new int[total];
        for (int i = 0; i < total; i++) out[i] = boxed[i];
        return out;
    }
}
