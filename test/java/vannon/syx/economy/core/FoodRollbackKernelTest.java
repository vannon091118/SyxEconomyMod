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
        assertEquals(100, r[1], "slot 1 gets 150*200/300 = 100 (residual goes here)");
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
}
