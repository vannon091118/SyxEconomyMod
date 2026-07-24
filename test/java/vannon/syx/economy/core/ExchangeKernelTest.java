package vannon.syx.economy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für {@link ExchangeKernel} — split (gradual transfer) und yardSale (peer trade).
 *
 * <p>Läuft ohne Spiel-Engine; pure Funktionen. Math derives directly from
 * <pre>
 *   split:    newA = λA * moneyA + eps * ((1-λA) * moneyA + (1-λB) * moneyB)
 *   yardSale: stake = (moneyA≤moneyB ? (1-λA)*moneyA : (1-λB)*moneyB)
 *             newA   = (eps < 0.5) ? moneyA + stake : moneyA - stake
 * </pre>
 * Both clamp newA into [0, moneyA+moneyB].</p>
 */
class ExchangeKernelTest {

    /* ── split() — 3-arg variant (λA=λB=0) ──────────────────────────── */

    @Test
    void split_3arg_epsZero_returnsZero() {
        // pot = moneyA + moneyB, newA = 0*pot = 0
        assertEquals(0, ExchangeKernel.split(100, 200, 0.0));
    }

    @Test
    void split_3arg_epsHalf_evenSplit() {
        // pot = moneyA + moneyB = 200, newA = 0.5 * 200 = 100
        assertEquals(100, ExchangeKernel.split(50, 150, 0.5));
    }

    @Test
    void split_3arg_fullEps_returnsTotal() {
        // newA = 1.0 * (moneyA + moneyB) = total, clamped to total
        int total = 250;
        int newA = ExchangeKernel.split(100, 150, 1.0);
        assertEquals(total, newA, "eps=1 with λ=0 → newA grabs the whole pot");
    }

    @Test
    void split_zeroTotal_returnsZero() {
        assertEquals(0, ExchangeKernel.split(0, 0, 0.5));
        assertEquals(0, ExchangeKernel.split(0, 0, 0.0));
        assertEquals(0, ExchangeKernel.split(0, 0, 1.0));
    }

    /* ── split() — 5-arg variant ────────────────────────────────────── */

    @Test
    void split_5arg_epsZero_retentionActive() {
        // λA=0.5 → 50% retained from moneyA; pot=0 (λ=1 for both, so no contribution)
        // newA = 0.5*moneyA + 0*pot = 25
        int newA = ExchangeKernel.split(50, 100, 0.5, 0.5, 0.0);
        assertEquals(25, newA, "λA=0.5 with eps=0 keeps half of moneyA");
    }

    @Test
    void split_5arg_epsHalf_withRetention() {
        // λA=λB=0.5 → each keeps half, then eps=0.5 splits the leaked remainder evenly
        // leaked = 0.5*moneyA + 0.5*moneyB = 25 + 50 = 75; newA = 25 + 0.5*75 = 62
        int newA = ExchangeKernel.split(50, 100, 0.5, 0.5, 0.5);
        assertEquals(62, newA, "λ=0.5 retention + eps=0.5 fair split");
    }

    @Test
    void split_5arg_lambdaOneEpsZero_preservesMoneyA() {
        // λA=1 → moneyA fully retained; λB=1 → no leak; eps=0 → no drain from pot.
        // pot = (1-1)*moneyA + (1-1)*moneyB = 0
        // newA = 1*moneyA + 0*pot = moneyA
        int newA = ExchangeKernel.split(77, 999, 1.0, 1.0, 0.0);
        assertEquals(77, newA, "λ=1 with eps=0 → newA = moneyA unchanged");
    }

    @Test
    void split_5arg_clampedToZeroWhenNegative() {
        // Hyperspace defense: if math somehow yields negative newA → clamp to 0.
        int newA = ExchangeKernel.split(0, 1000, -1.0, -1.0, 1.0);
        assertTrue(newA >= 0, "newA must clamp to 0 for negative results");
    }

    @Test
    void split_5arg_clampedToTotalWhenOverflow() {
        // λA = -1000 → newA would explode; clamp to total.
        int total = 50;
        int newA = ExchangeKernel.split(0, 50, -1000.0, -1000.0, 0.5);
        assertTrue(newA <= total, "newA must clamp to total when over");
    }

    /* ── yardSale() — peer-to-peer exchange ─────────────────────────── */

    @Test
    void yardSale_moneyALower_epsLow_addsStake() {
        // moneyA=400 ≤ moneyB=600 → stake = (1-0)*400 = 400
        // eps=0.0 < 0.5 → newA = 400 + 400 = 800
        int newA = ExchangeKernel.yardSale(400, 600, 0.0, 0.0, 0.0);
        assertEquals(800, newA);
    }

    @Test
    void yardSale_moneyALower_epsHigh_subtractsStake() {
        // eps=0.5 NOT < 0.5 → newA = moneyA - stake = 400 - 400 = 0
        int newA = ExchangeKernel.yardSale(400, 600, 0.0, 0.0, 0.5);
        assertEquals(0, newA, "eps≥0.5 → moneyA loses the stake");
    }

    @Test
    void yardSale_moneyAHigher_stakeDrawnFromB() {
        // moneyA=800 > moneyB=200 → stake = (1-0)*200 = 200
        // eps=0.0 < 0.5 → newA = 800 + 200 = 1000 (= total)
        int newA = ExchangeKernel.yardSale(800, 200, 0.0, 0.0, 0.0);
        assertEquals(1000, newA);
    }

    @Test
    void yardSale_moneyAHigher_epsHigh_subtractsStakeFromB() {
        // stake = 200, eps=0.5 → newA = 800 - 200 = 600
        int newA = ExchangeKernel.yardSale(800, 200, 0.0, 0.0, 0.5);
        assertEquals(600, newA);
    }

    @Test
    void yardSale_lambdaOne_noStakeTransferred() {
        // λA=λB=1 → stake = 0; newA = moneyA ± 0
        int newA = ExchangeKernel.yardSale(100, 200, 1.0, 1.0, 0.0);
        assertEquals(100, newA, "λ=1 → no stake, moneyA unchanged");
    }

    @Test
    void yardSale_neverNegative() {
        for (int moneyA : new int[]{0, 1, 10, 100}) {
            int newA = ExchangeKernel.yardSale(moneyA, 10000, 0.95, 0.95, 0.99);
            assertTrue(newA >= 0, "yardSale must never return negative, got " + newA);
        }
    }

    @Test
    void yardSale_neverExceedsTotal() {
        for (int moneyA : new int[]{0, 50, 500, 9999}) {
            int newA = ExchangeKernel.yardSale(moneyA, 1000, 1.0, 1.0, 0.0);
            int total = moneyA + 1000;
            assertTrue(newA <= total,
                "yardSale must not exceed combined pot, got newA=" + newA + " > " + total);
        }
    }

    @Test
    void yardSale_zeroBroke_returnsZero() {
        assertEquals(0, ExchangeKernel.yardSale(0, 0, 0.0, 0.0, 0.5));
    }

    @Test
    void yardSale_asymmetricLambda_aLower_retentionAUsed() {
        // moneyA=100 <= moneyB=200 → stake uses lambdaA.
        // lambdaA=0.0 → stake = (1-0)*100 = 100.
        assertEquals(200, ExchangeKernel.yardSale(100, 200, 0.0, 1.0, 0.0),
            "eps<0.5 adds stake");
        assertEquals(0, ExchangeKernel.yardSale(100, 200, 0.0, 1.0, 0.5),
            "eps>=0.5 subtracts stake");
    }

    @Test
    void yardSale_asymmetricLambda_bHigher_lambdaBPreventsStake() {
        // moneyA=300 > moneyB=100 → stake uses lambdaB.
        // lambdaB=1.0 → stake = (1-1)*100 = 0, so moneyA is unchanged.
        assertEquals(300, ExchangeKernel.yardSale(300, 100, 0.0, 1.0, 0.0),
            "lambdaB=1.0 removes B's stake");
        assertEquals(300, ExchangeKernel.yardSale(300, 100, 0.0, 1.0, 0.5),
            "lambdaB=1.0 removes B's stake even when eps>=0.5");
    }

    @Test
    void split_5arg_asymmetricLambda_mixedRetention() {
        // lambdaA=0, lambdaB=1 → A leaks fully, B retains fully.
        // pot = (1-0)*100 + (1-1)*50 = 100.
        // newA = 0*100 + 0.5*100 = 50.
        assertEquals(50, ExchangeKernel.split(100, 50, 0.0, 1.0, 0.5),
            "asymmetric retention must only leak from A");
    }

    @Test
    void split_5arg_truncatesTowardZero() {
        // λA=λB=0.5, moneyA=50, moneyB=100, eps=0.5.
        // leaked = 25 + 50 = 75; newA = 25 + 0.5*75 = 62.5 → (int) = 62.
        // This test documents the current truncation behavior. A future change
        // to Math.round() would break it and must therefore be intentional.
        assertEquals(62, ExchangeKernel.split(50, 100, 0.5, 0.5, 0.5),
            "split truncates fractional newA toward zero");
    }

    @Test
    void split_3arg_truncatesTowardZero() {
        // pot = 1 + 2 = 3; newA = 0.5 * 3 = 1.5 → (int) = 1.
        assertEquals(1, ExchangeKernel.split(1, 2, 0.5),
            "3-arg split truncates fractional newA toward zero");
    }
}
