package vannon.syx.economy.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sprint v0.13.103+ — 5-Tier-Staircase + Staatsbestand-Demand-Floor Tests.
 *
 * <p>Pin: CARPENTER bei leerem MOEBEL-Lager laeuft mit 100% maxEmp, bei vollem
 * Lager mit 10%. Staatsbestand-Override (stock < minCoverage) hebt Tier-0 auf
 * 100% workers, auch wenn die Staircase sonst weniger liefert.</p>
 *
 * <p>JUnit 5.9+ entfernte assertEquals(double, double, double). Tests nutzen
 * assertTrue mit manueller Delta.</p>
 */
class FirmStaircaseTest {

    private static final double DELTA = 1e-6;

    private boolean savedStaircaseEnabled;
    private boolean savedStaatsbestandEnabled;
    private double savedStaatsbestandMinCoverage;
    private double[] savedTiers;
    private double[] savedFractions;

    @BeforeEach
    void saveConfig() {
        savedStaircaseEnabled = EconConfig.firmStaircaseEnabled;
        savedStaatsbestandEnabled = EconConfig.firmStaatsbestandEnabled;
        savedStaatsbestandMinCoverage = EconConfig.firmStaatsbestandMinCoverage;
        savedTiers = EconConfig.firmStaircaseCoverageTiers;
        savedFractions = EconConfig.firmStaircaseWorkerFractions;
    }

    @AfterEach
    void restoreConfig() {
        EconConfig.firmStaircaseEnabled = savedStaircaseEnabled;
        EconConfig.firmStaatsbestandEnabled = savedStaatsbestandEnabled;
        EconConfig.firmStaatsbestandMinCoverage = savedStaatsbestandMinCoverage;
        EconConfig.firmStaircaseCoverageTiers = savedTiers;
        EconConfig.firmStaircaseWorkerFractions = savedFractions;
    }

    private static void assertIntApprox(int expected, int actual, String msg) {
        assertTrue(Math.abs(expected - actual) <= 1,
                msg + " (expected=" + expected + ", actual=" + actual + ")");
    }

    // ── getTier ────────────────────────────────────────────────────────

    @Test
    void getTier_zero_coverage_returns_tier0() {
        EconConfig.firmStaircaseCoverageTiers = new double[]{0.0, 0.30, 0.70, 1.00, 2.00};
        assertTrue(FirmStaircase.getTier(0.0) == 0, "Tier 0 bei coverage=0");
    }

    @Test
    void getTier_negative_coverage_clamped_to_tier0() {
        EconConfig.firmStaircaseCoverageTiers = new double[]{0.0, 0.30, 0.70, 1.00, 2.00};
        assertTrue(FirmStaircase.getTier(-1.0) == 0, "Negative coverage → Tier 0");
    }

    @Test
    void getTier_between_breakpoints() {
        EconConfig.firmStaircaseCoverageTiers = new double[]{0.0, 0.30, 0.70, 1.00, 2.00};
        assertTrue(FirmStaircase.getTier(0.20) == 1, "0<coverage≤0.30 → Tier 1");
        assertTrue(FirmStaircase.getTier(0.50) == 2, "0.30<coverage≤0.70 → Tier 2");
        assertTrue(FirmStaircase.getTier(0.85) == 3, "0.70<coverage≤1.00 → Tier 3");
        assertTrue(FirmStaircase.getTier(1.50) == 4, "1.00<coverage≤2.00 → Tier 4");
    }

    @Test
    void getTier_above_last_tier_returns_last() {
        EconConfig.firmStaircaseCoverageTiers = new double[]{0.0, 0.30, 0.70, 1.00, 2.00};
        assertTrue(FirmStaircase.getTier(5.0) == 4, "coverage > alle Tiers → letzter Tier");
    }

    // ── scaleMax ───────────────────────────────────────────────────────

    @Test
    void scaleMax_empty_lager_full_capacity() {
        // CARPENTER-Use-Case: MOEBEL-Lager leer → Stock-Demand-0
        EconConfig.firmStaircaseCoverageTiers = new double[]{0.0, 0.30, 0.70, 1.00, 2.00};
        EconConfig.firmStaircaseWorkerFractions = new double[]{1.00, 0.70, 0.30, 0.10, 0.05};
        EconConfig.firmStaatsbestandEnabled = false;
        assertIntApprox(45, FirmStaircase.scaleMax(45, 0.0), "Lager leer (coverage=0) → 100% maxEmp");
    }

    @Test
    void scaleMax_surplus_drops_to_5percent() {
        EconConfig.firmStaircaseCoverageTiers = new double[]{0.0, 0.30, 0.70, 1.00, 2.00};
        EconConfig.firmStaircaseWorkerFractions = new double[]{1.00, 0.70, 0.30, 0.10, 0.05};
        EconConfig.firmStaatsbestandEnabled = false;
        assertIntApprox(2, FirmStaircase.scaleMax(45, 1.50), "Surplus (coverage>1.0) → 5% (~2 Worker)");
    }

    @Test
    void scaleMax_one_day_supply_drops_to_10percent() {
        EconConfig.firmStaircaseCoverageTiers = new double[]{0.0, 0.30, 0.70, 1.00, 2.00};
        EconConfig.firmStaircaseWorkerFractions = new double[]{1.00, 0.70, 0.30, 0.10, 0.05};
        EconConfig.firmStaatsbestandEnabled = false;
        assertIntApprox(5, FirmStaircase.scaleMax(45, 0.85), "0.70<coverage≤1.0 → 10% (~5 Worker)");
    }

    @Test
    void scaleMax_comfortable_drops_to_30percent() {
        EconConfig.firmStaircaseCoverageTiers = new double[]{0.0, 0.30, 0.70, 1.00, 2.00};
        EconConfig.firmStaircaseWorkerFractions = new double[]{1.00, 0.70, 0.30, 0.10, 0.05};
        EconConfig.firmStaatsbestandEnabled = false;
        assertIntApprox(14, FirmStaircase.scaleMax(45, 0.50), "0.30<coverage≤0.70 → 30% (~14 Worker)");
    }

    @Test
    void scaleMax_just_below_threshold_drops_to_70percent() {
        EconConfig.firmStaircaseCoverageTiers = new double[]{0.0, 0.30, 0.70, 1.00, 2.00};
        EconConfig.firmStaircaseWorkerFractions = new double[]{1.00, 0.70, 0.30, 0.10, 0.05};
        EconConfig.firmStaatsbestandEnabled = false;
        assertIntApprox(32, FirmStaircase.scaleMax(45, 0.20), "coverage≤0.30 → 70% (~32 Worker)");
    }

    @Test
    void scaleMax_zero_capacity_returns_zero() {
        EconConfig.firmStaircaseCoverageTiers = new double[]{0.0, 0.30, 0.70, 1.00, 2.00};
        EconConfig.firmStaircaseWorkerFractions = new double[]{1.00, 0.70, 0.30, 0.10, 0.05};
        assertTrue(FirmStaircase.scaleMax(0, 0.5) == 0, "maxCapacity=0 → 0 (kein Crash)");
    }

    @Test
    void scaleMax_disabled_returns_maxCapacity() {
        EconConfig.firmStaircaseEnabled = false;
        assertTrue(FirmStaircase.scaleMax(45, 0.0) == 45, "Disabled → maxCapacity unverändert");
    }

    @Test
    void scaleMax_very_small_capacity_floor_at_one() {
        // maxCapacity=3, coverage=2.0 → 3*0.05=0.15 → Math.round(0.15)=0 → Math.max(1, 0)=1
        EconConfig.firmStaircaseCoverageTiers = new double[]{0.0, 0.30, 0.70, 1.00, 2.00};
        EconConfig.firmStaircaseWorkerFractions = new double[]{1.00, 0.70, 0.30, 0.10, 0.05};
        EconConfig.firmStaatsbestandEnabled = false;
        assertTrue(FirmStaircase.scaleMax(3, 2.0) >= 1, "min 1 Worker auch bei kleinem Blueprint");
    }

    // ── Staatsbestand ──────────────────────────────────────────────────

    @Test
    void staatsbestand_below_minCoverage_returns_critical() {
        EconConfig.firmStaatsbestandEnabled = true;
        EconConfig.firmStaatsbestandMinCoverage = 0.10;
        assertTrue(FirmStaircase.isStaatsbestandCritical(0.05), "5% < 10% → critical");
        assertTrue(FirmStaircase.isStaatsbestandCritical(0.0), "0% < 10% → critical");
    }

    @Test
    void staatsbestand_above_minCoverage_not_critical() {
        EconConfig.firmStaatsbestandEnabled = true;
        EconConfig.firmStaatsbestandMinCoverage = 0.10;
        assertFalse(FirmStaircase.isStaatsbestandCritical(0.10), "10% ist NICHT < 10%");
        assertFalse(FirmStaircase.isStaatsbestandCritical(0.50), "50% > 10% → nicht critical");
    }

    @Test
    void staatsbestand_disabled_never_critical() {
        EconConfig.firmStaatsbestandEnabled = false;
        EconConfig.firmStaatsbestandMinCoverage = 0.10;
        assertFalse(FirmStaircase.isStaatsbestandCritical(0.0), "Disabled → nie critical");
    }

    @Test
    void staatsbestand_negative_coverage_not_critical() {
        // Defensive: NaN/negative ist Datenbank-Fehler, kein State-Priority
        EconConfig.firmStaatsbestandEnabled = true;
        EconConfig.firmStaatsbestandMinCoverage = 0.10;
        assertFalse(FirmStaircase.isStaatsbestandCritical(-1.0), "Negative coverage → nicht critical");
        assertFalse(FirmStaircase.isStaatsbestandCritical(Double.NaN), "NaN → nicht critical");
    }

    @Test
    void staatsbestand_override_forces_full_capacity() {
        // CRITICAL: kritischer Bestand überschreibt Staircase
        EconConfig.firmStaircaseCoverageTiers = new double[]{0.0, 0.30, 0.70, 1.00, 2.00};
        EconConfig.firmStaircaseWorkerFractions = new double[]{1.00, 0.70, 0.30, 0.10, 0.05};
        EconConfig.firmStaatsbestandEnabled = true;
        EconConfig.firmStaatsbestandMinCoverage = 0.10;
        // coverage=0.05 (< 10% threshold) → State-Priority → 100% workers trotz Tier-0-Match
        assertTrue(FirmStaircase.scaleMax(45, 0.05) == 45,
                "Staatsbestand-Override: coverage < minCoverage → 100% maxEmp");
    }

    // ── Kombination ────────────────────────────────────────────────────

    @Test
    void full_lifecycle_empty_to_surplus() {
        EconConfig.firmStaircaseCoverageTiers = new double[]{0.0, 0.30, 0.70, 1.00, 2.00};
        EconConfig.firmStaircaseWorkerFractions = new double[]{1.00, 0.70, 0.30, 0.10, 0.05};
        EconConfig.firmStaatsbestandEnabled = false;
        // Day 1: leer → 45 Workers
        assertIntApprox(45, FirmStaircase.scaleMax(45, 0.00), "Tag 1: leer");
        // Tag 2: knapp → 32
        assertIntApprox(32, FirmStaircase.scaleMax(45, 0.20), "Tag 2: knapp");
        // Tag 5: komfortabel → 14
        assertIntApprox(14, FirmStaircase.scaleMax(45, 0.50), "Tag 5: komfortabel");
        // Tag 10: 1-Tage-Vorrat → 5
        assertIntApprox(5, FirmStaircase.scaleMax(45, 0.85), "Tag 10: 1-Tage-Vorrat");
        // Tag 30: Surplus → 2
        assertIntApprox(2, FirmStaircase.scaleMax(45, 1.50), "Tag 30: Surplus");
    }
}
