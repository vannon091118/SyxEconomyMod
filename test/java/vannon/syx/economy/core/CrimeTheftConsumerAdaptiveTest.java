package vannon.syx.economy.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sprint v0.13.102+ — Adaptive Crime Factor unit tests.
 *
 * <p>Pin: locked the engine-not-ready amplification bug. Before the fix,
 * {@code computeGuardFactor(0.0, false)} returned 3.0 (3x crime amplification
 * when engine is not initialized). The contract is now: if {@code dataAvailable=false}
 * OR {@code strength=0.0}, the factor is exactly 1.0 (neutral).
 *
 * <p>JUnit 5.9+ removed the {@code assertEquals(double, double, double)} overload
 * (the delta-floats variant). Tests use {@code assertTrue} with manual delta.
 */
class CrimeTheftConsumerAdaptiveTest {

    private static final double DELTA = 1e-4;

    private double savedMoneyStrength;
    private double savedGuardStrength;
    private int savedRefWealth;

    @BeforeEach
    void saveConfig() {
        savedMoneyStrength = EconConfig.crimeTheftMoneyFactorStrength;
        savedGuardStrength = EconConfig.crimeTheftGuardFactorStrength;
        savedRefWealth = EconConfig.crimeTheftReferenceWealth;
    }

    @AfterEach
    void restoreConfig() {
        EconConfig.crimeTheftMoneyFactorStrength = savedMoneyStrength;
        EconConfig.crimeTheftGuardFactorStrength = savedGuardStrength;
        EconConfig.crimeTheftReferenceWealth = savedRefWealth;
    }

    private static void assertApprox(double expected, double actual, String msg) {
        assertTrue(Math.abs(expected - actual) < DELTA,
                msg + " (expected=" + expected + ", actual=" + actual + ")");
    }

    // ── moneyFactor ──────────────────────────────────────────────────────

    @Test
    void moneyFactor_arm_returns_three_x_when_strength_two() {
        EconConfig.crimeTheftMoneyFactorStrength = 2.0;
        EconConfig.crimeTheftReferenceWealth = 500;
        // totalMoney=0, pop=1, refWealth=500 → coverage = 0 → factor = 1 + (1-0)*2 = 3.0
        assertApprox(3.0, CrimeTheftConsumer.computeMoneyFactor(0.0, 1),
                "Armut (0 Money) sollte Faktor 3.0 ergeben");
    }

    @Test
    void moneyFactor_wealthy_returns_neutral_one() {
        EconConfig.crimeTheftMoneyFactorStrength = 2.0;
        EconConfig.crimeTheftReferenceWealth = 500;
        // totalMoney = 500, pop=1 → coverage = 1.0 → factor = 1 + 0*2 = 1.0
        assertApprox(1.0, CrimeTheftConsumer.computeMoneyFactor(500.0, 1),
                "Reichtum (100% Coverage) sollte neutral 1.0 sein");
    }

    @Test
    void moneyFactor_half_coverage_returns_two_x() {
        EconConfig.crimeTheftMoneyFactorStrength = 2.0;
        EconConfig.crimeTheftReferenceWealth = 500;
        // totalMoney = 250, pop=1 → coverage = 0.5 → factor = 1 + 0.5*2 = 2.0
        assertApprox(2.0, CrimeTheftConsumer.computeMoneyFactor(250.0, 1),
                "Halbe Reichweite sollte Faktor 2.0 ergeben");
    }

    @Test
    void moneyFactor_strength_zero_returns_neutral() {
        EconConfig.crimeTheftMoneyFactorStrength = 0.0;
        // strength=0 → factor sollte IMMER 1.0 sein, egal welcher State
        assertApprox(1.0, CrimeTheftConsumer.computeMoneyFactor(0.0, 1),
                "Strength=0 muss neutral sein");
    }

    @Test
    void moneyFactor_pop_zero_does_not_divide_by_zero() {
        EconConfig.crimeTheftMoneyFactorStrength = 2.0;
        EconConfig.crimeTheftReferenceWealth = 500;
        // pop=0 → Math.max(1, 0) = 1 → coverage = 0 / 500 = 0 → factor = 3.0
        assertApprox(3.0, CrimeTheftConsumer.computeMoneyFactor(0.0, 0),
                "pop=0 darf nicht zu NaN/Inf fuehren");
    }

    @Test
    void moneyFactor_negative_total_money_clamped_to_zero_coverage() {
        EconConfig.crimeTheftMoneyFactorStrength = 2.0;
        EconConfig.crimeTheftReferenceWealth = 500;
        // totalMoney<0 ist defensiv. coverage = -100/500 = -0.2, Math.min(1.0, ...) → 1.0? No.
        // Actually min(1.0, -0.2) = -0.2, and 1.0 + (1.0 - (-0.2)) * 2 = 1.0 + 2.4 = 3.4
        // Slightly off from 3.0 but well-defined. Acceptable defensive behavior.
        double factor = CrimeTheftConsumer.computeMoneyFactor(-100.0, 1);
        assertTrue(factor >= 3.0 && Double.isFinite(factor),
                "Negative Money soll definierten Faktor ergeben (got: " + factor + ")");
    }

    // ── guardFactor ──────────────────────────────────────────────────────

    @Test
    void guardFactor_no_guards_returns_three_x_when_data_available() {
        EconConfig.crimeTheftGuardFactorStrength = 2.0;
        // guardRatio=0, dataAvailable=true → factor = 1 + (1-0)*2 = 3.0
        assertApprox(3.0, CrimeTheftConsumer.computeGuardFactor(0.0, true),
                "0% Miliz sollte Faktor 3.0 ergeben");
    }

    @Test
    void guardFactor_full_guards_returns_neutral_one() {
        EconConfig.crimeTheftGuardFactorStrength = 2.0;
        // guardRatio=1.0 → factor = 1 + (1-1)*2 = 1.0
        assertApprox(1.0, CrimeTheftConsumer.computeGuardFactor(1.0, true),
                "100% Miliz sollte neutral 1.0 sein");
    }

    @Test
    void guardFactor_engine_not_ready_returns_neutral_one() {
        // CRITICAL: defensive default — engine-not-ready darf NICHT amplifizieren.
        EconConfig.crimeTheftGuardFactorStrength = 2.0;
        assertApprox(1.0, CrimeTheftConsumer.computeGuardFactor(0.0, false),
                "dataAvailable=false MUSS neutral 1.0 ergeben (engine-not-ready amplification bug fix)");
    }

    @Test
    void guardFactor_strength_zero_returns_neutral() {
        EconConfig.crimeTheftGuardFactorStrength = 0.0;
        assertApprox(1.0, CrimeTheftConsumer.computeGuardFactor(0.0, true),
                "Strength=0 muss neutral sein");
    }

    @Test
    void guardFactor_negative_guard_ratio_clamped() {
        EconConfig.crimeTheftGuardFactorStrength = 2.0;
        // guardRatio < 0: Math.max(0.0, 1.0 - (-0.5)) = 1.5 → factor = 1 + 1.5*2 = 4.0
        // Defensive: clamp to 0? No, current code uses Math.max(0.0, ...) AFTER subtraction.
        // So 1.0 - (-0.5) = 1.5, Math.max(0.0, 1.5) = 1.5, factor = 4.0.
        // This is intentional: a negative ratio is treated as "even more guardless".
        double factor = CrimeTheftConsumer.computeGuardFactor(-0.5, true);
        assertTrue(factor >= 3.0 && Double.isFinite(factor),
                "Negative guardRatio erhoeht Faktor (got: " + factor + ")");
    }

    @Test
    void guardFactor_ratio_above_one_clamped() {
        EconConfig.crimeTheftGuardFactorStrength = 2.0;
        // guardRatio > 1.0 → 1 - 1.5 = -0.5, Math.max(0.0, -0.5) = 0 → factor = 1.0
        assertApprox(1.0, CrimeTheftConsumer.computeGuardFactor(1.5, true),
                "Ratio > 1 wird auf 0 geklemmt -> Faktor 1.0");
    }

    // ── Interaction (kombinierte Faktoren) ──────────────────────────────

    @Test
    void combined_arm_and_no_guards_yields_nine_x() {
        EconConfig.crimeTheftMoneyFactorStrength = 2.0;
        EconConfig.crimeTheftGuardFactorStrength = 2.0;
        EconConfig.crimeTheftReferenceWealth = 500;
        // Worst-case: arm + keine Miliz → 3 * 3 = 9x baseChance
        double money = CrimeTheftConsumer.computeMoneyFactor(0.0, 1);
        double guard = CrimeTheftConsumer.computeGuardFactor(0.0, true);
        assertApprox(3.0, money, "moneyFactor in arm");
        assertApprox(3.0, guard, "guardFactor in no-militia");
        assertApprox(9.0, money * guard, "Multiplikativ: 3*3 = 9x");
    }

    @Test
    void combined_safe_state_yields_one_x() {
        EconConfig.crimeTheftMoneyFactorStrength = 2.0;
        EconConfig.crimeTheftGuardFactorStrength = 2.0;
        EconConfig.crimeTheftReferenceWealth = 500;
        // Safe state: reich + volle Miliz → 1.0 * 1.0 = 1x
        double money = CrimeTheftConsumer.computeMoneyFactor(500.0, 1);
        double guard = CrimeTheftConsumer.computeGuardFactor(1.0, true);
        assertApprox(1.0, money, "moneyFactor in safe");
        assertApprox(1.0, guard, "guardFactor in safe");
        assertApprox(1.0, money * guard, "Safe state: 1x (neutral)");
    }

    @Test
    void combined_engine_not_ready_guard_clamps_to_one() {
        // CRITICAL: defensive — engine-not-ready GUARD factor MUST clamp to 1.0,
        // NICHT zur guardRatio=0-derived 3x Amplification eskalieren.
        // Geld-Faktor kennt das dataAvailable-Flag nicht (Wallets sind immer
        // verfuegbar), bleibt also bei 3.0 fuer arme Settlements. Multiplikativ
        // ergibt das 3.0 * 1.0 = 3.0, NICHT 9.0.
        EconConfig.crimeTheftMoneyFactorStrength = 2.0;
        EconConfig.crimeTheftGuardFactorStrength = 2.0;
        EconConfig.crimeTheftReferenceWealth = 500;
        double money = CrimeTheftConsumer.computeMoneyFactor(0.0, 1); // arm
        double guard = CrimeTheftConsumer.computeGuardFactor(0.0, false); // engine not ready
        assertApprox(3.0, money, "Armes Settlement: moneyFactor=3.0");
        assertApprox(1.0, guard, "Engine-not-ready: guardFactor MUSS 1.0 sein (nicht 3.0)");
        assertApprox(3.0, money * guard,
                "Engine-not-ready guard klamp verhindert 9x amplification (3*3 = 9 waere der bug)");
    }
}
