package vannon.syx.economy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für {@link FirmEconomyKernel} — Firmen-Buchhaltung + Hillclimber.
 *
 * <p>Läuft ohne Spiel-Engine; alle Funktionen sind pure.</p>
 */
class FirmEconomyKernelTest {

    /* ── profit() ───────────────────────────────────────────────────── */

    @Test
    void profit_revenueMinusCost() {
        double[] outputs = {10.0, 5.0};
        double[] prices  = {20.0, 30.0};
        double[] inputs  = {3.0, 2.0};
        double[] ip      = {5.0, 7.0};
        // revenue = 10*20 + 5*30 = 350; cost = 3*5 + 2*7 = 29; profit = 321
        assertEquals(321.0, FirmEconomyKernel.profit(outputs, prices, inputs, ip), 1e-9);
    }

    @Test
    void profit_negativeComponentsAreIgnored() {
        // Negative output rate or non-positive price contributes 0 to revenue.
        double[] outputs = {-1.0, 5.0};
        double[] prices  = {20.0, 30.0};
        double[] inputs  = {3.0};
        double[] ip      = {5.0};
        // revenue = 0 (neg rate) + 5*30=150 → cost 15 → profit 135
        assertEquals(135.0, FirmEconomyKernel.profit(outputs, prices, inputs, ip), 1e-9);
    }

    @Test
    void profit_lengthMismatch_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> FirmEconomyKernel.profit(new double[]{1, 2}, new double[]{1.0},
                                              new double[]{1.0}, new double[]{1.0}));
    }

    /* ── marginal() ─────────────────────────────────────────────────── */

    @Test
    void marginal_diffBetweenProfits() {
        assertEquals(7.0, FirmEconomyKernel.marginal(50.0, 43.0), 1e-9);
    }

    @Test
    void marginal_nonFiniteInputs_returnZero() {
        assertEquals(0.0, FirmEconomyKernel.marginal(Double.NaN, 50.0), 1e-9);
        assertEquals(0.0, FirmEconomyKernel.marginal(50.0, Double.POSITIVE_INFINITY), 1e-9);
    }

    /* ── shouldIdle() ───────────────────────────────────────────────── */

    @Test
    void shouldIdle_belowHysteresisThreshold() {
        assertTrue(FirmEconomyKernel.shouldIdle(-30.0, 20.0));
    }

    @Test
    void shouldIdle_aboveHysteresisThreshold() {
        assertFalse(FirmEconomyKernel.shouldIdle(-5.0, 20.0));
    }

    @Test
    void shouldIdle_nonFiniteProfit_isFalse() {
        assertFalse(FirmEconomyKernel.shouldIdle(Double.NaN, 0.0));
    }

    /* ── priority() ─────────────────────────────────────────────────── */

    @Test
    void priority_zeroMarginal_returnsMin() {
        int result = FirmEconomyKernel.priority(0.0, 10.0, 50, 1.0, 30, 60);
        assertEquals(30, result, "non-positive marginal → min priority");
    }

    @Test
    void priority_negativeMarginal_returnsMin() {
        int result = FirmEconomyKernel.priority(-5.0, 10.0, 50, 1.0, 30, 60);
        assertEquals(30, result);
    }

    @Test
    void priority_zeroMean_returnsNeutralClamped() {
        int result = FirmEconomyKernel.priority(5.0, 0.0, 50, 1.0, 30, 60);
        assertEquals(50, result, "non-positive mean → neutral priority, clamped");
    }

    @Test
    void priority_dominantMarginal_boostsPriority() {
        // marginal = 100, mean = 10 → ratio 10 → elastic*log10(10) ≈ 1.0*2.30 ≈ 2.3
        int result = FirmEconomyKernel.priority(100.0, 10.0, 50, 1.0, 30, 70);
        assertTrue(result > 50, "dominant marginal yields higher priority; got " + result);
        assertTrue(result <= 70, "result must clamp to max");
    }

    @Test
    void priority_equalMarginalAndMean_returnsNeutral() {
        // ratio=1 → log(1)=0 → raw=neutral → result=neutral (rounded)
        int result = FirmEconomyKernel.priority(10.0, 10.0, 50, 1.0, 30, 70);
        assertEquals(50, result);
    }

    @Test
    void priority_minGreaterThanMax_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> FirmEconomyKernel.priority(1.0, 1.0, 50, 1.0, 60, 30));
    }

    /* ── split() ────────────────────────────────────────────────────── */

    @Test
    void split_evenAmount_evenWorkers() {
        int[] r = FirmEconomyKernel.split(100, 4);
        assertEquals(4, r.length);
        assertEquals(25, r[0]);
        assertEquals(25, r[1]);
        assertEquals(25, r[2]);
        assertEquals(25, r[3]);
    }

    @Test
    void split_unevenAmount_distributesRemainder() {
        int[] r = FirmEconomyKernel.split(10, 3);
        // total/workers=3, remainder=1 → first worker gets 4, others get 3.
        assertEquals(4, r[0], "first worker absorbs the remainder");
        assertEquals(3, r[1]);
        assertEquals(3, r[2]);
        assertEquals(10, r[0] + r[1] + r[2]);
    }

    @Test
    void split_zeroWorkers_returnsEmpty() {
        assertEquals(0, FirmEconomyKernel.split(100, 0).length);
    }

    @Test
    void split_zeroAmount_returnsAllZeros() {
        int[] r = FirmEconomyKernel.split(0, 5);
        assertEquals(5, r.length);
        for (int v : r) assertEquals(0, v);
    }

    @Test
    void split_negativeInputs_throws() {
        assertThrows(IllegalArgumentException.class, () -> FirmEconomyKernel.split(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> FirmEconomyKernel.split(1, -1));
    }

    /* ── hillStep() — Hillclimber state-machine ──────────────────────── */

    @Test
    void hillStep_uninitialised_negativeProfitAndRoomAboveMin_probesDown() {
        // observedProfit=-10.0 (<0) AND observedTarget=5 (>minTarget=0)
        //   → direction = -1 → probe = neighbour(5, -1, 1, 0, 10) = 4
        // state.bestTarget initialised to observedTarget = 5.
        FirmEconomyKernel.HillResult r =
            FirmEconomyKernel.hillStep(null, /*observed*/ 5, -10.0, /*max*/10, 1, 0.0);
        assertEquals(4, r.nextTarget(),
            "negative observed profit with room above min → probe downward");
        assertEquals(5, r.state().bestTarget(),
            "initial bestTarget starts at observedTarget");
        assertEquals(-10.0, r.state().bestProfit(), 1e-9);
        assertTrue(r.state().initialized());
    }

    @Test
    void hillStep_uninitialised_negativeProfitAtMinTarget_probesUp() {
        // observedProfit=-10.0 AND observedTarget=0 (=minTarget, not > minTarget)
        //   → observedTarget < maxTarget (10), so direction = +1 → probe = 1
        FirmEconomyKernel.HillResult r =
            FirmEconomyKernel.hillStep(null, 0, -10.0, 10, 1, 0.0);
        assertEquals(1, r.nextTarget(),
            "negative profit at minTarget → probe upward (can't go lower)");
        assertTrue(r.state().initialized());
    }

    @Test
    void hillStep_uninitialised_belowMaxAndNonNegativeProfit_stillGoesUp() {
        FirmEconomyKernel.HillResult r =
            FirmEconomyKernel.hillStep(null, 5, 100.0, 10, 1, 0.0);
        assertEquals(6, r.nextTarget());
    }

    @Test
    void hillStep_atMaxBoundary_firstStepGoesDown() {
        FirmEconomyKernel.HillResult r =
            FirmEconomyKernel.hillStep(null, 10, 100.0, 10, 1, 0.0);
        // observedTarget == max → direction = -1
        assertEquals(9, r.nextTarget(), "at max the first probe goes down");
    }

    @Test
    void hillStep_improvement_keepsDirectionAndProbe() {
        FirmEconomyKernel.HillState prev =
            new FirmEconomyKernel.HillState(5, 50.0, 1, true);
        FirmEconomyKernel.HillResult r =
            FirmEconomyKernel.hillStep(prev, 6, 55.0, 0, 10, 1, 0.0);
        // best target updates to 6, direction stays +1 → probe=7
        assertEquals(6, r.state().bestTarget(), "bestTarget moves toward improvement");
        assertTrue(r.state().bestProfit() >= 55.0);
        assertEquals(7, r.nextTarget());
    }

    @Test
    void hillStep_nonImprovement_reversesDirection() {
        FirmEconomyKernel.HillState prev =
            new FirmEconomyKernel.HillState(5, 50.0, 1, true);
        FirmEconomyKernel.HillResult r =
            FirmEconomyKernel.hillStep(prev, 6, 40.0, 0, 10, 1, 0.0);
        // distance = 6-5 = 1; direction = -1 → probe = 5-1 = 4
        assertEquals(4, r.nextTarget(), "non-improvement reverses direction");
    }

    @Test
    void hillStep_maxNegative_negativeMaxTarget_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> FirmEconomyKernel.hillStep(null, 0, 0.0, -1, 1, 0.0));
    }
}
