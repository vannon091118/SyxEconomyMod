package vannon.syx.economy.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sprint v0.13.104+M-UI-1 — KpiSection SeverityClassifier + FilterMode Tests.
 *
 * <p>Unit-Tests ohne Engine-Mock: Severity/Filter-Logik ist pure data-classifier,
 * deterministisch, kein Mockito-Setup noetig. sortIndicesByCoverageAsc benoetigt
 * ein FlowPrices-Stub und bleibt an dieser Stelle ungetestet — wird mit Sprint
 * M-UI-3 (Test-Fixture fuer EngineMirror-Mock) folgen.</p>
 *
 * <p>Pattern-Guards (Sprint-Doku): Jede Schwellwert-Aenderung am SeverityClassifier
 * produziert hier mindestens einen Test-Bruch. Wer den Classifier ohne Test-Update
 * anfasst, schickt die Mod durch die CI-Rueckkopplung.</p>
 */
class KpiSectionTest {

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
}
