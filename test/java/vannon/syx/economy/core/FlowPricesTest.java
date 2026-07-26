package vannon.syx.economy.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Sprint 9 — 7-1b: Unit-Test-Coverage für die static math-Methoden in FlowPrices.
 * Fokus: effectiveCoverage(), scarcityMultiplier(), localPrice(), targetStock().
 * Inklusive D-001 (foodPriceCapMultiplier) und D-004 (supplyPerDay=0 → stock ignoriert).
 */
class FlowPricesTest {

    // ─── targetStock ──────────────────────────────────────────────────

    @Test
    void targetStock_normalCase() {
        double stock = FlowPrices.targetStock(10.0, 6.0);
        assertEquals(60.0, stock, 1e-9);
    }

    @Test
    void targetStock_zeroDemand_returnsZero() {
        assertEquals(0.0, FlowPrices.targetStock(0.0, 6.0), 1e-9);
    }

    @Test
    void targetStock_zeroCoverageDays_returnsZero() {
        assertEquals(0.0, FlowPrices.targetStock(10.0, 0.0), 1e-9);
    }

    @Test
    void targetStock_negativeDemand_returnsZero() {
        assertEquals(0.0, FlowPrices.targetStock(-1.0, 6.0), 1e-9);
    }

    @Test
    void targetStock_negativeCoverageDays_returnsZero() {
        assertEquals(0.0, FlowPrices.targetStock(10.0, -1.0), 1e-9);
    }

    // ─── effectiveCoverage ────────────────────────────────────────────

    @Test
    void effectiveCoverage_atTargetStock_returnsOne() {
        // stock=60, supply=demand=10 → projected=60+1*(0)=60, target=10*6=60 → 1.0
        double cov = FlowPrices.effectiveCoverage(60.0, 10.0, 10.0, 6.0, 1.0);
        assertEquals(1.0, cov, 1e-9);
    }

    @Test
    void effectiveCoverage_aboveTarget() {
        // stock=120, supply=demand=10 → projected=120, target=60 → 2.0
        double cov = FlowPrices.effectiveCoverage(120.0, 10.0, 10.0, 6.0, 1.0);
        assertEquals(2.0, cov, 1e-9);
    }

    @Test
    void effectiveCoverage_belowTarget() {
        // stock=30, supply=demand=10 → projected=30, target=60 → 0.5
        double cov = FlowPrices.effectiveCoverage(30.0, 10.0, 10.0, 6.0, 1.0);
        assertEquals(0.5, cov, 1e-9);
    }

    @Test
    void effectiveCoverage_zeroTarget_returnsOne() {
        double cov = FlowPrices.effectiveCoverage(100.0, 10.0, 0.0, 6.0, 1.0);
        assertEquals(1.0, cov, 1e-9);
    }

    @Test
    void effectiveCoverage_negativeProjected_returnsZero() {
        // stock=0, supply=0, demand=10 → projected=0+1*(-10)=-10 → coverage=0
        double cov = FlowPrices.effectiveCoverage(0.0, 0.0, 10.0, 6.0, 1.0);
        assertEquals(0.0, cov, 1e-9);
    }

    @Test
    void effectiveCoverage_supplyAboveDemand_increasesProjected() {
        double cov = FlowPrices.effectiveCoverage(0.0, 20.0, 10.0, 6.0, 1.0);
        // projected=0+1*(20-10)=10, target=60 → 10/60=0.167
        assertTrue(cov > 0.0 && cov < 1.0);
    }

    // ─── D-004: stock ignored when supplyPerDay ≤ 0 ──────────────────

    @Test
    void d004_stockIgnoredWhenNoProduction() {
        // stock=414, supply=0, demand=41.93 → old: projected=414-41.93=372, cov≈8.7
        // new: effectiveStock=0, projected=0-41.93=-41.93, cov=0
        double cov = FlowPrices.effectiveCoverage(414.0, 0.0, 41.93, 1.0, 1.0);
        assertEquals(0.0, cov, 1e-9,
                "D-004: stock must be ignored when supplyPerDay=0");
    }

    @Test
    void d004_stockCountedWhenProductionActive() {
        // supply>0 → stock counts normally
        double cov = FlowPrices.effectiveCoverage(414.0, 10.0, 41.93, 1.0, 1.0);
        // projected=414+1*(10-41.93)=382.07, target=41.93 → 9.11
        assertTrue(cov > 1.0, "stock should count when supplyPerDay > 0");
    }

    @Test
    void d004_zeroSupply_negativeProjected_resultsInZeroCoverage() {
        // Exact _WOOD scenario from diagnostics
        double cov = FlowPrices.effectiveCoverage(414.0, 0.0, 41.93, 1.0, 1.0);
        assertEquals(0.0, cov, 1e-9);
    }

    // ─── scarcityMultiplier ───────────────────────────────────────────

    @Test
    void scarcityMultiplier_atCoverageOne_returnsOne() {
        double mult = FlowPrices.scarcityMultiplier(1.0, 0.8, 1.375, 0.001, 100.0);
        assertEquals(1.0, mult, 1e-9);
    }

    @Test
    void scarcityMultiplier_belowCoverageOne_usesUpElasticity() {
        // coverage=0.5, upElasticity=0.8 → 0.5^(-0.8)=1.741
        double mult = FlowPrices.scarcityMultiplier(0.5, 0.8, 1.375, 0.001, 100.0);
        assertTrue(mult > 1.0, "price should increase when coverage < 1");
    }

    @Test
    void scarcityMultiplier_aboveCoverageOne_usesDownElasticity() {
        // coverage=2.0, downElasticity=1.375 → 2.0^(-1.375)=0.385
        double mult = FlowPrices.scarcityMultiplier(2.0, 0.8, 1.375, 0.001, 100.0);
        assertTrue(mult < 1.0, "price should decrease when coverage > 1");
    }

    @Test
    void scarcityMultiplier_clampedToPriceClampLo() {
        // Very high coverage → raw < clampLo
        double mult = FlowPrices.scarcityMultiplier(1000.0, 0.8, 1.375, 0.01, 100.0);
        assertEquals(0.01, mult, 1e-9);
    }

    @Test
    void scarcityMultiplier_coverageZero_returnsFiniteScarcitySignal() {
        // coverage=0 → floored to COVERAGE_FLOOR(0.005) → finite
        double mult = FlowPrices.scarcityMultiplier(0.0, 0.8, 1.375, 0.001, 100.0);
        assertTrue(mult > 1.0, "zero coverage should produce scarcity signal");
        assertTrue(mult < 100.0, "scarcity signal should be finite");
    }

    @Test
    void scarcityMultiplier_clampHiKicksIn_withHighElasticity() {
        // elasticity=5.0 → pow(0.005, -5) = 3.2e11 → clamped to 100
        double mult = FlowPrices.scarcityMultiplier(0.0, 5.0, 1.375, 0.001, 100.0);
        assertEquals(100.0, mult, 1e-9);
    }

    // ─── localPrice ───────────────────────────────────────────────────

    @Test
    void localPrice_normalCase() {
        double price = FlowPrices.localPrice(100.0, 1.0, 0.8, 1.375, 0.001, 100.0, 50000.0);
        assertEquals(100.0, price, 1e-9);
    }

    @Test
    void localPrice_zeroAnchor_returnsZero() {
        assertEquals(0.0, FlowPrices.localPrice(0.0, 1.0, 0.8, 1.375, 0.001, 100.0, 50000.0), 1e-9);
    }

    @Test
    void localPrice_negativeAnchor_returnsZero() {
        assertEquals(0.0, FlowPrices.localPrice(-1.0, 1.0, 0.8, 1.375, 0.001, 100.0, 50000.0), 1e-9);
    }

    @Test
    void localPrice_scarcityMultiplierApplied() {
        // coverage=0.5 → multiplier>1 → price>anchor
        double price = FlowPrices.localPrice(100.0, 0.5, 0.8, 1.375, 0.001, 100.0, 50000.0);
        assertTrue(price > 100.0, "scarcity should increase price");
    }

    @Test
    void localPrice_clampedByPriceAbsoluteMax() {
        // High scarcity → price would exceed 500 but clamped
        double price = FlowPrices.localPrice(1000.0, 0.005, 0.8, 1.375, 0.001, 100.0, 500.0);
        assertEquals(500.0, price, 1e-9);
    }

    @Test
    void localPrice_priceAbsoluteMaxZero_disablesCap() {
        // priceAbsoluteMax=0 → no cap applied
        double price = FlowPrices.localPrice(1000.0, 0.005, 0.8, 1.375, 0.001, 100.0, 0.0);
        assertTrue(price > 1000.0, "no cap when priceAbsoluteMax=0");
    }

    @Test
    void localPrice_infiniteAnchor_returnsZero() {
        assertEquals(0.0, FlowPrices.localPrice(Double.POSITIVE_INFINITY, 1.0, 0.8, 1.375, 0.001, 100.0, 50000.0), 1e-9);
    }

    @Test
    void localPrice_nanAnchor_returnsZero() {
        assertEquals(0.0, FlowPrices.localPrice(Double.NaN, 1.0, 0.8, 1.375, 0.001, 100.0, 50000.0), 1e-9);
    }
}
