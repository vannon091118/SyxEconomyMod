package vannon.syx.economy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für {@link FoodRollbackKernel#allocate} — proportional food-stock allocator.
 *
 * <p>Läuft ohne Spiel-Engine; pure Funktion über {@code int[]} und {@code boolean[]}.</p>
 */
class FoodRollbackKernelTest {

    private static int sum(int[] a) {
        int s = 0;
        for (int x : a) s += x;
        return s;
    }

    @Test
    void proportionalAllocation_preferredSlotsShareProportionally() {
        int[] stock = {100, 200, 50};
        boolean[] preferred = {true, true, false};
        int[] r = FoodRollbackKernel.allocate(150, stock, preferred);
        // Stock totals to 350; preferred totals to 300.
        // Proportional split among preferred: stock 100 → 150*100/300 = 50;
        //                                         stock 200 → 150*200/300 = 100.
        assertEquals(50, r[0], "slot 0 gets 150*100/300 = 50");
        assertEquals(100, r[1], "slot 1 gets 150*200/300 = 100 (exact division, no residual)");
        assertEquals(0, r[2], "non-preferred slot 2 gets nothing");
        assertEquals(150, sum(r), "result must sum to requested demand");
    }

    @Test
    void sufficientStock_preferredCapacityExceedsDemand_returnsDemandRespected() {
        // stock={50,80,30}, preferred={T,T,T}, demand=100, total stock=160
        // allocateCapped(100, ..., true):
        //   available = 160; target = min(100,160) = 100
        //   shares = round(100*50/160), round(100*80/160), round(100*30/160)
        //          = 31, 50, 18 → sum 99
        //   residual loop bumps the slot with the largest remainder (slot 2, rem=120) → 19
        // Result: [31, 50, 19], sum=100.
        int[] stock = {50, 80, 30};
        boolean[] preferred = {true, true, true};
        int[] r = FoodRollbackKernel.allocate(100, stock, preferred);
        assertEquals(31, r[0]);
        assertEquals(50, r[1]);
        assertEquals(19, r[2]);
        assertEquals(100, sum(r), "result must equal demand when stock suffices");
        assertTrue(r[0] <= stock[0] && r[1] <= stock[1] && r[2] <= stock[2],
            "result cannot exceed per-slot stock");
    }

    @Test
    void zeroDemand_returnsAllZeros() {
        int[] stock = {10, 20, 30};
        boolean[] preferred = {true, false, true};
        int[] r = FoodRollbackKernel.allocate(0, stock, preferred);
        assertEquals(0, r[0]);
        assertEquals(0, r[1]);
        assertEquals(0, r[2]);
    }

    @Test
    void exactStock_allSlotsConsumed() {
        int[] stock = {40, 60};
        boolean[] preferred = {true, true};
        int demand = 100;   // exactly equal to total stock
        int[] r = FoodRollbackKernel.allocate(demand, stock, preferred);
        assertEquals(40, r[0]);
        assertEquals(60, r[1]);
        assertEquals(100, sum(r), "exact stock → result equals total stock");
    }

    @Test
    void nonPreferredSlots_onlyFilledWhenPreferredExhausted() {
        int[] stock = {10, 20};
        boolean[] preferred = {true, false};
        // Demand 25: prefer slot 0 (10) + fallback slot 1 (15 pulled from 20).
        int[] r = FoodRollbackKernel.allocate(25, stock, preferred);
        assertEquals(10, r[0], "preferred slot 0 fully consumed first");
        assertEquals(15, r[1], "fallback slot 1 covers remaining demand");
        assertEquals(25, sum(r));
    }

    @Test
    void negativeDemand_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> FoodRollbackKernel.allocate(-1, new int[]{10}, new boolean[]{true}));
    }

    @Test
    void mismatchedArrays_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> FoodRollbackKernel.allocate(5, new int[]{1, 2}, new boolean[]{true}));
    }

    @Test
    void negativeStock_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> FoodRollbackKernel.allocate(5, new int[]{-1}, new boolean[]{true}));
    }

    @Test
    void demandWithNoSlots_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> FoodRollbackKernel.allocate(5, new int[0], new boolean[0]));
    }

    @Test
    void nonPreferredSlots_remainderDistributedByLargestRemainder() {
        // preferred slot 0 fully consumed (10). Remaining 15 pulled from
        // non-preferred slots 1 (stock 7) and 2 (stock 13), total 20.
        // Proportional shares: 15*7/20 = 5 (rem 105), 15*13/20 = 9 (rem 195).
        // Residual loop bumps slot 2 (larger remainder) → 10.
        int[] stock = {10, 7, 13};
        boolean[] preferred = {true, false, false};
        int[] r = FoodRollbackKernel.allocate(25, stock, preferred);
        assertEquals(10, r[0], "preferred slot fully consumed");
        assertEquals(5, r[1]);
        assertEquals(10, r[2], "slot 2 absorbs the residual unit");
        assertEquals(25, sum(r));
    }

    @Test
    void demandExceedsTotalStock_noPreferred_unboundedPath_allocatesProportionally() {
        // WARNING: This test documents the *current* behavior of the unbounded
        // allocator, not necessarily a desired invariant. When demand exceeds
        // total stock, the result can exceed the per-slot stock.
        // Total stock = 30, demand = 40. After both capped passes 30 units are
        // allocated; the unbounded path distributes the remaining 10.
        int[] stock = {10, 20};
        boolean[] preferred = {false, false};
        int[] r = FoodRollbackKernel.allocate(40, stock, preferred);
        // Unbounded weights are max(stock,1): 10 and 20.
        // Remaining 10 is split 10*10/30 = 3 (rem 100), 10*20/30 = 6 (rem 200);
        // residual bumps slot 1 → 7. Result: [13, 27], sum = 40.
        assertEquals(13, r[0]);
        assertEquals(27, r[1]);
        assertEquals(40, sum(r), "unbounded path returns full demand even when stock is insufficient");
    }

    @Test
    void demandExceedsTotalStock_withPreferred_unboundedPathUsesPreferredFallback() {
        // WARNING: This test documents the *current* behavior of the unbounded
        // allocator, not necessarily a desired invariant. When demand exceeds
        // total stock, the result can exceed the per-slot stock.
        // Total stock = 30, demand = 40. Preferred slot 0 gives 10,
        // non-preferred slot 1 gives 20, remaining 10 flows into the
        // unbounded allocator with fallback = preferred.
        int[] stock = {10, 20};
        boolean[] preferred = {true, false};
        int[] r = FoodRollbackKernel.allocate(40, stock, preferred);
        // Fallback is {true, false}; only slot 0 is eligible.
        // Weight = max(10,1) = 10; remaining 10 → 10*10/10 = 10 to slot 0.
        assertEquals(20, r[0], "preferred fallback receives the unbounded allocation");
        assertEquals(20, r[1]);
        assertEquals(40, sum(r));
    }
}
