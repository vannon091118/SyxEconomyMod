package vannon.syx.economy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für {@link FoodGateKernel} — Warenkorb-Aggregation mit Quote-Cap.
 *
 * <p>Läuft ohne Spiel-Engine; pure Funktion.</p>
 */
class FoodGateKernelTest {

    @Test
    void bill_simpleCart_returnsSumCeiledAsInteger() {
        int[] quantities  = {2, 3, 1};
        double[] prices   = {5.0, 4.0, 7.5};
        int quote = 100;
        int expected = (int) Math.ceil(2*5.0) + (int) Math.ceil(3*4.0) + (int) Math.ceil(1*7.5);
        assertEquals(expected, FoodGateKernel.bill(quantities, prices, quote));
        assertEquals(10 + 12 + 8, expected, "5+5, 12, 7+1 — verify the benchmark");
    }

    @Test
    void bill_quoteCapsAtLowerValue() {
        int[] quantities = {100, 100};
        double[] prices  = {10.0, 10.0};
        // sum = 1000 + 1000 = 2000; quote = 100 → caps to 100.
        assertEquals(100, FoodGateKernel.bill(quantities, prices, 100));
    }

    @Test
    void bill_quantityZero_skipped() {
        int[] quantities = {0, 5};
        double[] prices  = {3.0, 4.0};
        assertEquals(20, FoodGateKernel.bill(quantities, prices, 1000),
            "line with qty=0 must not contribute to total");
    }

    @Test
    void bill_priceNonPositive_skipped() {
        int[] quantities = {5, 5};
        double[] prices  = {3.0, 0.0};
        assertEquals(15, FoodGateKernel.bill(quantities, prices, 1000),
            "line with price<=0 or NaN/Infinity must not contribute");
    }

    @Test
    void bill_priceNaN_skipped() {
        int[] quantities = {1, 1};
        double[] prices  = {Double.NaN, 5.0};
        assertEquals(5, FoodGateKernel.bill(quantities, prices, 1000));
    }

    @Test
    void bill_priceInfinite_skipped() {
        int[] quantities = {1, 1};
        double[] prices  = {Double.POSITIVE_INFINITY, 5.0};
        assertEquals(5, FoodGateKernel.bill(quantities, prices, 1000));
    }

    @Test
    void bill_emptyCart_returnsZero() {
        assertEquals(0, FoodGateKernel.bill(new int[0], new double[0], 9999));
    }

    @Test
    void bill_quoteZero_capsToZeroUnlessOverflow() {
        int[] quantities = {1};
        double[] prices  = {100.0};
        // Normal: sum=100, capped to 0.
        assertEquals(0, FoodGateKernel.bill(quantities, prices, 0));
    }

    @Test
    void bill_negativeQuote_returnsZero() {
        int[] quantities = {1};
        double[] prices  = {10.0};
        // Math.max(0, quote) on quote=-5 = 0 → result capped to 0
        assertEquals(0, FoodGateKernel.bill(quantities, prices, -5));
    }

    @Test
    void bill_mismatchedVectors_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> FoodGateKernel.bill(new int[]{1, 2}, new double[]{1.0}, 100));
    }

    @Test
    void bill_singleLineOverflow_returnsQuoteCappedAtMax() {
        // line = 1_000_000 * 3000.0 = 3.0e9 > Integer.MAX_VALUE.
        // Overflow branch caps the line at Integer.MAX_VALUE, total reaches MAX_VALUE,
        // and the method returns Math.max(0, quote).
        int[] quantities = {1_000_000};
        double[] prices  = {3000.0};
        assertEquals(500, FoodGateKernel.bill(quantities, prices, 500),
            "overflow path must return the quote cap");
    }

    @Test
    void bill_exactlyAtIntegerMaxBoundary_returnsQuote() {
        // line == 2.147483647E9 exactly. The branch uses >=, so this exact value
        // must trigger the overflow-capped return path.
        int[] quantities = {1};
        double[] prices  = {2_147_483_647.0};
        assertEquals(123, FoodGateKernel.bill(quantities, prices, 123),
            "exact Integer.MAX_VALUE boundary must trigger the overflow path");
    }

    @Test
    void bill_cumulativeTotalExceedsMax_returnsQuote() {
        // Each individual line (1.0e9) is below the 2.147...E9 single-line
        // threshold, but their sum exceeds Integer.MAX_VALUE. The overflow guard
        // must fire after the third line, returning the quote cap.
        int[] quantities = {1, 1, 1};
        double[] prices  = {1_000_000_000.0, 1_000_000_000.0, 1_000_000_000.0};
        assertEquals(777, FoodGateKernel.bill(quantities, prices, 777),
            "cumulative overflow must return the quote cap once total >= Integer.MAX_VALUE");
    }

    @Test
    void bill_cumulativeTotalExactlyMax_returnsQuote() {
        // 1_000_000_000 + 1_000_000_000 + 147_483_647 = Integer.MAX_VALUE.
        // The guard uses >=, so the exact boundary must also return the quote.
        int[] quantities = {1, 1, 1};
        double[] prices  = {1_000_000_000.0, 1_000_000_000.0, 147_483_647.0};
        assertEquals(888, FoodGateKernel.bill(quantities, prices, 888),
            "cumulative total exactly at Integer.MAX_VALUE must return the quote cap");
    }

    @Test
    void bill_mixedValidAndInvalidLines_skipsInvalidAndReturnsExactTotal() {
        // Regression guard for the skip-condition refactor:
        // line 0: 2 * 5.0 = 10
        // line 1: quantity 0 → skipped
        // line 2: price 0.0 → skipped
        // If || were accidentally changed to &&, invalid lines would no longer be skipped.
        int[] quantities = {2, 0, 5};
        double[] prices  = {5.0, 100.0, 0.0};
        assertEquals(10, FoodGateKernel.bill(quantities, prices, 1000),
            "only valid lines contribute to the total");
    }
}
