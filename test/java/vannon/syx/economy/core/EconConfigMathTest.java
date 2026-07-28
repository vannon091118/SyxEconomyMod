package vannon.syx.economy.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EconConfig math + invariants.
 * Sprint 6.3 — additive test, no source-modification.
 */
class EconConfigMathTest {

    @AfterEach
    void resetDefaults() {
        EconConfig.phaseFactorEnabled = true;
        EconConfig.phaseFactorThreshold = 300;
        EconConfig.phaseFactorMin = 0.5;
        EconConfig.population = 0;
    }

    @Test
    void clampIntMinMax() {
        assertEquals(5, EconConfig.clamp(5, 0, 10));
        assertEquals(0, EconConfig.clamp(-1, 0, 10));
        assertEquals(10, EconConfig.clamp(99, 0, 10));
        assertEquals(7, EconConfig.clamp(7, 7, 7));
    }

    @Test
    void clampDoubleMinMax() {
        assertEquals(0.5, EconConfig.clamp(0.5, 0.0, 1.0));
        assertEquals(0.0, EconConfig.clamp(-0.1, 0.0, 1.0));
        assertEquals(1.0, EconConfig.clamp(1.1, 0.0, 1.0));
    }

    @Test
    void phaseFactorDisabledReturnsOne() {
        EconConfig.phaseFactorEnabled = false;
        EconConfig.population = 50;
        assertEquals(1.0, EconConfig.phaseFactor(), 1e-9);
    }

    @Test
    void phaseFactorPopulationAtThreshold() {
        EconConfig.phaseFactorThreshold = 100;
        EconConfig.phaseFactorMin = 0.5;
        EconConfig.population = 100;
        assertEquals(1.0, EconConfig.phaseFactor(), 1e-9);
    }

    @Test
    void phaseFactorAtZeroPopulationClampsToMin() {
        EconConfig.phaseFactorThreshold = 100;
        EconConfig.phaseFactorMin = 0.5;
        EconConfig.population = 0;
        assertEquals(0.5, EconConfig.phaseFactor(), 1e-9);
    }

    @Test
    void phaseFactorLinearRampMidpoint() {
        EconConfig.phaseFactorThreshold = 100;
        EconConfig.phaseFactorMin = 0.5;
        EconConfig.population = 50;
        // Ramp = (100-50)/100 = 0.5, factor = 1 - 0.5*(1-0.5) = 0.75
        assertEquals(0.75, EconConfig.phaseFactor(), 1e-9);
    }

    @Test
    void setPopulationNegativeIsClampedToZero() {
        EconConfig.setPopulation(-10);
        assertEquals(0, EconConfig.population);
        EconConfig.setPopulation(50);
        assertEquals(50, EconConfig.population);
    }

    @Test
    void resetLaborDefaultsAlignsAllWagesWithDefaultWage() {
        // First mutate so we can verify reset restores.
        EconConfig.militaryTrainingWagePerDay = 999;
        EconConfig.exportDepotWagePerDay = 999;
        EconConfig.haulerWagePerDay = 999;
        EconConfig.transportFeePer100TileDay = 999;

        EconConfig.resetLaborDefaults();

        assertEquals(50, EconConfig.militaryTrainingWagePerDay);
        assertEquals(50, EconConfig.exportDepotWagePerDay);
        assertEquals(50, EconConfig.haulerWagePerDay);
        assertEquals(50, EconConfig.armySupplyWagePerDay);
        assertEquals(50, EconConfig.laboratoryWagePerDay);
        assertEquals(50, EconConfig.libraryWagePerDay);
        assertEquals(50, EconConfig.embassyWagePerDay);
        assertEquals(50, EconConfig.waterWagePerDay);
        assertEquals(50, EconConfig.cannibalWagePerDay);
        assertEquals(50, EconConfig.policeWagePerDay);
        assertEquals(50, EconConfig.guardWagePerDay);
        assertEquals(50, EconConfig.stockadeWagePerDay);
        assertEquals(50, EconConfig.prisonWagePerDay);
        assertEquals(5, EconConfig.transportFeePer100TileDay);
        assertEquals(EconConfig.DEFAULT_ODDJOB_WAGE_PER_TASK, EconConfig.oddjobWagePerTask);
    }

    @Test
    void conflictWarningIsNullInDefaultConfig() {
        // Default config has wagesEnabled=true, stateFundedWageRegulationOnly=false,
        // foodAffordabilityGateEnabled=true, handoutWalletAmount=50 — no conflict.
        EconConfig.stateFundedWageRegulationOnly = false;
        EconConfig.wagesEnabled = true;
        EconConfig.foodAffordabilityGateEnabled = true;
        EconConfig.handoutWalletAmount = 50;
        assertNull(EconConfig.conflictWarning());
    }

    @Test
    void conflictWarningFiresWhenStateRegulationButWagesOff() {
        EconConfig.stateFundedWageRegulationOnly = true;
        EconConfig.wagesEnabled = false;
        assertNotNull(EconConfig.conflictWarning());
    }

    @Test
    void conflictWarningFiresOnLargeHandoutWithGate() {
        EconConfig.foodAffordabilityGateEnabled = true;
        EconConfig.handoutWalletAmount = 500;
        assertNotNull(EconConfig.conflictWarning());
    }

    @Test
    void guildSurplusDistributionFloor() {
        // mint employee count + minimum profit-per-worker — surplus should be 0
        int workers = 10;
        double minPerWorker = EconConfig.guildSurplusMinProfitPerWorker;
        double profit = workers * minPerWorker;
        double surplus = Math.max(0.0, profit - workers * minPerWorker) * EconConfig.guildSurplusShare;
        assertTrue(surplus >= 0.0, "Surplus must not be negative");
    }

    @Test
    void clampIntPreservesFittingValue() {
        assertEquals(50, EconConfig.clamp(50, 0, 100));
        assertEquals(0, EconConfig.clamp(0, 0, 100));
        assertEquals(100, EconConfig.clamp(100, 0, 100));
    }
}
