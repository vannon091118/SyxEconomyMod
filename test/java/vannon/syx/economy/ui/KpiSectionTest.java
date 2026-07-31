package vannon.syx.economy.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vannon.syx.economy.core.FlowPrices;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Sprint v0.13.104+M-UI-1 — KpiSection SeverityClassifier + FilterMode Tests.
 * Sprint v0.13.111+M-UI-3.1 — Mockito-Test-Fixture für sortIndicesByCoverageAsc.
 *
 * <p>Sprint M-UI-1 lieferte 18 Pure-Logic-Tests (Severity-Classifier +
 * FilterMode.accepts + chipLabel + ordinal-Vertrag). Kein Engine-Mock noetig —
 * alle Schwellwert-Tests sind deterministisch ohne Engine-State.</p>
 *
 * <p>Sprint M-UI-3.1 erweitert um 5 Mockito-basierte Tests auf
 * {@link KpiSection#sortIndicesByCoverageAsc(FlowPrices, int)}. FlowPrices ist
 * eine {@code final}-Klasse, was Mockito 5.x's inline-mockmaker (default)
 * per ByteBuddy transparent macht. mockito-junit-jupiter 5.14.2 ist im
 * pom.xml als test-scope-Dependency eingebunden.</p>
 *
 * <p>Test-Strategien:</p>
 * <ul>
 *   <li>{@code @Mock FlowPrices} + {@code when(...).thenReturn(...)} für
 *       sorted-by-coverage Tests.</li>
 *   <li>{@code MockitoSettings(strictness=LENIENT)} weil einige Tests den
 *       @Mock nicht stubben (z.B. Null-Path-Tests).</li>
 * </ul>
 *
 * <p>Pattern-Guards: jede Schwellwert-Aenderung am Sort-Verhalten bricht
 * mindestens einen Test hier. CI-Feedback verhindert unbeaufsichtigte Sortier-
 * Regressionen in PricesTab.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KpiSectionTest {

    @Mock
    FlowPrices flowPrices;

    // ── SeverityClassifier.classify (Sprint M-UI-1) ─────────────────

    @Test
    void classify_zero_coverage_returns_critical() {
        assertEquals(KpiSection.Severity.CRITICAL, KpiSection.Severity.classify(0.0));
    }

    @Test
    void classify_at_low_threshold_inclusive_returns_low() {
        // Schwellwerte sind inklusiv (siehe Logic): 0.3 → LOW
        assertEquals(KpiSection.Severity.LOW, KpiSection.Severity.classify(0.3));
    }

    @Test
    void classify_just_below_low_threshold_returns_critical() {
        assertEquals(KpiSection.Severity.CRITICAL, KpiSection.Severity.classify(0.2999));
    }

    @Test
    void classify_middle_of_low_band_returns_low() {
        assertEquals(KpiSection.Severity.LOW, KpiSection.Severity.classify(0.5));
    }

    @Test
    void classify_at_ok_threshold_inclusive_returns_ok() {
        // 0.7 untere OK-Schwelle, 1.0 Mitte, 3.0 obere OK-Schwelle.
        assertEquals(KpiSection.Severity.OK, KpiSection.Severity.classify(0.7));
        assertEquals(KpiSection.Severity.OK, KpiSection.Severity.classify(1.0));
        assertEquals(KpiSection.Severity.OK, KpiSection.Severity.classify(3.0));
    }

    @Test
    void classify_just_above_surplus_threshold_returns_surplus() {
        assertEquals(KpiSection.Severity.SURPLUS, KpiSection.Severity.classify(3.0001));
    }

    @Test
    void classify_nan_returns_ok_data_stale() {
        // NaN/Infinite Coverage = Datenbank-Stale, default OK (nicht Alarm)
        assertEquals(KpiSection.Severity.OK, KpiSection.Severity.classify(Double.NaN));
    }

    @Test
    void classify_positive_infinity_returns_ok() {
        assertEquals(KpiSection.Severity.OK, KpiSection.Severity.classify(Double.POSITIVE_INFINITY));
    }

    @Test
    void classify_negative_infinity_returns_critical() {
        // Negative Werte sind Datenfehler, klassifiziert als CRITICAL
        // (Spieler sieht Alarm statt stummer Default-OK).
        assertEquals(KpiSection.Severity.CRITICAL, KpiSection.Severity.classify(Double.NEGATIVE_INFINITY));
    }

    @Test
    void classify_negative_finite_returns_critical() {
        assertEquals(KpiSection.Severity.CRITICAL, KpiSection.Severity.classify(-0.5));
    }

    // ── Severity.isProblem (Sprint M-UI-1) ───────────────────────────

    @Test
    void isProblem_only_critical_and_low() {
        assertTrue(KpiSection.Severity.CRITICAL.isProblem());
        assertTrue(KpiSection.Severity.LOW.isProblem());
        assertFalse(KpiSection.Severity.OK.isProblem());
        assertFalse(KpiSection.Severity.SURPLUS.isProblem());
    }

    // ── Severity.badge (Sprint M-UI-1) ──────────────────────────────

    @Test
    void badge_produces_baseline_strings() {
        // Label-String ist UI-verteilt (PricesTab Status-Spalte). Aenderung
        // bricht diese Test UND sichtbare Spalten-Beschriftung.
        assertEquals("MANGL", KpiSection.Severity.CRITICAL.badge());
        assertEquals("knapp", KpiSection.Severity.LOW.badge());
        assertEquals("ok", KpiSection.Severity.OK.badge());
        assertEquals("UEBERSCH.", KpiSection.Severity.SURPLUS.badge());
    }

    // ── FilterMode.accepts (Sprint M-UI-1) ───────────────────────────

    @Test
    void filtermode_all_accepts_every_severity() {
        for (KpiSection.Severity s : KpiSection.Severity.values()) {
            assertTrue(KpiSection.FilterMode.ALL.accepts(s),
                    "FilterMode.ALL muss " + s + " akzeptieren");
        }
    }

    @Test
    void filtermode_problem_only_accepts_critical_and_low_only() {
        assertTrue(KpiSection.FilterMode.PROBLEM_ONLY.accepts(KpiSection.Severity.CRITICAL));
        assertTrue(KpiSection.FilterMode.PROBLEM_ONLY.accepts(KpiSection.Severity.LOW));
        assertFalse(KpiSection.FilterMode.PROBLEM_ONLY.accepts(KpiSection.Severity.OK));
        assertFalse(KpiSection.FilterMode.PROBLEM_ONLY.accepts(KpiSection.Severity.SURPLUS));
    }

    @Test
    void filtermode_surplus_only_accepts_surplus_only() {
        assertFalse(KpiSection.FilterMode.SURPLUS_ONLY.accepts(KpiSection.Severity.CRITICAL));
        assertFalse(KpiSection.FilterMode.SURPLUS_ONLY.accepts(KpiSection.Severity.LOW));
        assertFalse(KpiSection.FilterMode.SURPLUS_ONLY.accepts(KpiSection.Severity.OK));
        assertTrue(KpiSection.FilterMode.SURPLUS_ONLY.accepts(KpiSection.Severity.SURPLUS));
    }

    @Test
    void filtermode_critical_only_accepts_critical_only() {
        assertTrue(KpiSection.FilterMode.CRITICAL_ONLY.accepts(KpiSection.Severity.CRITICAL));
        assertFalse(KpiSection.FilterMode.CRITICAL_ONLY.accepts(KpiSection.Severity.LOW));
        assertFalse(KpiSection.FilterMode.CRITICAL_ONLY.accepts(KpiSection.Severity.OK));
        assertFalse(KpiSection.FilterMode.CRITICAL_ONLY.accepts(KpiSection.Severity.SURPLUS));
    }

    // ── FilterMode.chipLabel (Sprint M-UI-1) ────────────────────────

    @Test
    void chiplabels_match_baseline_strings() {
        // chipLabel wird in PricesTab Filter-Chip-Bar genutzt; Aenderung
        // bricht sichtbare Chips UND diesen Test.
        assertEquals("Alle", KpiSection.FilterMode.ALL.chipLabel());
        assertEquals("Mangel+Knapp", KpiSection.FilterMode.PROBLEM_ONLY.chipLabel());
        assertEquals("Ueberschuss", KpiSection.FilterMode.SURPLUS_ONLY.chipLabel());
        assertEquals("Nur Mangel", KpiSection.FilterMode.CRITICAL_ONLY.chipLabel());
    }

    @Test
    void filtermode_enum_order_is_baseline_ordinal_contract() {
        // PricesTab.currentFilter ist ein int-Index in FilterMode.values().
        // Wer die enum-Reihenfolge aendert, verschiebt die Filter-Auswahl
        // der Spieler.ordinal()-Vertrag ist hart kodiert.
        assertEquals(0, KpiSection.FilterMode.ALL.ordinal());
        assertEquals(1, KpiSection.FilterMode.PROBLEM_ONLY.ordinal());
        assertEquals(2, KpiSection.FilterMode.SURPLUS_ONLY.ordinal());
        assertEquals(3, KpiSection.FilterMode.CRITICAL_ONLY.ordinal());
    }

    // ── sortIndicesByCoverageAsc (Sprint v0.13.111+M-UI-3.1) ──────────
    // Mockito @Mock FlowPrices (final class — handled by mockito-inline-5.x
    // ByteBuddy mockmaker). LENIENT strictness weil Null-Path-Tests den
    // @Mock gar nicht stubben.

    @Test
    void sort_null_flowPrices_returns_empty_array() {
        // Null-Safety-Guard: kein Crash auf null FlowPrices-Referenz
        int[] sorted = KpiSection.sortIndicesByCoverageAsc(null, 5);
        assertEquals(0, sorted.length);
    }

    @Test
    void sort_zero_or_negative_total_returns_empty_array() {
        // total<=0 → keine Sortierung noetig
        int[] sortedZero = KpiSection.sortIndicesByCoverageAsc(flowPrices, 0);
        assertEquals(0, sortedZero.length);

        int[] sortedNeg = KpiSection.sortIndicesByCoverageAsc(flowPrices, -3);
        assertEquals(0, sortedNeg.length);
    }

    @Test
    void sort_single_resource_returns_index_zero() {
        // Edge-Case: 1-element Liste muss trivial sortiert sein
        when(flowPrices.coverage(0)).thenReturn(0.5);
        int[] sorted = KpiSection.sortIndicesByCoverageAsc(flowPrices, 1);
        assertArrayEquals(new int[]{0}, sorted);
    }

    @Test
    void sort_ascending_by_coverage_returns_lowest_first() {
        // Haupt-Test: kritischste Resource (niedrigste Coverage) zuerst.
        // 5 Resources mit Coverages [0.9, 0.1, 0.5, 0.0, 1.2]:
        when(flowPrices.coverage(0)).thenReturn(0.9);
        when(flowPrices.coverage(1)).thenReturn(0.1);
        when(flowPrices.coverage(2)).thenReturn(0.5);
        when(flowPrices.coverage(3)).thenReturn(0.0);
        when(flowPrices.coverage(4)).thenReturn(1.2);
        int[] sorted = KpiSection.sortIndicesByCoverageAsc(flowPrices, 5);
        // Expected ascending coverage: idx 3 (0.0) > idx 1 (0.1) > idx 2 (0.5) > idx 0 (0.9) > idx 4 (1.2)
        assertArrayEquals(new int[]{3, 1, 2, 0, 4}, sorted);
    }

    @Test
    void sort_uniform_coverage_preserves_stable_order() {
        // Alle Coverage-Werte 0.0 (Mockito-Default fuer unstubbte double-Returns).
        // Arrays.sort ist stable seit Java 7: equal-keys behalten Input-Reihenfolge.
        int[] sorted = KpiSection.sortIndicesByCoverageAsc(flowPrices, 3);
        assertEquals(3, sorted.length);
        assertEquals(0, sorted[0]);
        assertEquals(1, sorted[1]);
        assertEquals(2, sorted[2]);
    }
}
