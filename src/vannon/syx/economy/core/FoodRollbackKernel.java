package vannon.syx.economy.core;

public final class FoodRollbackKernel {
    public static int[] allocate(int amount, int[] stock, boolean[] preferred) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (stock == null || preferred == null || stock.length != preferred.length) {
            throw new IllegalArgumentException("stock and preferred arrays must have equal lengths");
        }
        if (amount > 0 && stock.length == 0) {
            throw new IllegalArgumentException("cannot allocate food without resource slots");
        }
        int[] cleanStock = (int[])stock.clone();
        for (int i = 0; i < cleanStock.length; ++i) {
            if (cleanStock[i] >= 0) continue;
            throw new IllegalArgumentException("stock must be non-negative");
        }
        int[] result = new int[stock.length];
        int remaining = amount;
        remaining -= FoodRollbackKernel.allocateCapped(remaining, cleanStock, preferred, true, result);
        if ((remaining -= FoodRollbackKernel.allocateCapped(remaining, cleanStock, preferred, false, result)) > 0) {
            boolean anyPreferred = false;
            for (boolean value : preferred) {
                anyPreferred |= value;
            }
            boolean[] fallback = new boolean[preferred.length];
            for (int i = 0; i < fallback.length; ++i) {
                fallback[i] = anyPreferred ? preferred[i] : true;
            }
            FoodRollbackKernel.allocateUnbounded(remaining, cleanStock, fallback, result);
        }
        return result;
    }

    private static int allocateCapped(int requested, int[] stock, boolean[] preferred, boolean desiredPreference, int[] result) {
        if (requested <= 0) {
            return 0;
        }
        long available = 0L;
        for (int i = 0; i < stock.length; ++i) {
            if (preferred[i] != desiredPreference) continue;
            available += (long)stock[i];
        }
        int target = (int)Math.min((long)requested, available);
        if (target == 0) {
            return 0;
        }
        long[] remainder = new long[stock.length];
        int allocated = 0;
        for (int i = 0; i < stock.length; ++i) {
            if (preferred[i] != desiredPreference || stock[i] == 0) continue;
            long numerator = (long)target * (long)stock[i];
            int share = (int)(numerator / available);
            int n = i;
            result[n] = result[n] + share;
            allocated += share;
            remainder[i] = numerator % available;
        }
        while (allocated < target) {
            int best = -1;
            for (int i = 0; i < stock.length; ++i) {
                if (preferred[i] != desiredPreference || result[i] >= stock[i] || best >= 0 && remainder[i] <= remainder[best]) continue;
                best = i;
            }
            if (best < 0) {
                throw new IllegalStateException("capped allocation lost mass");
            }
            int n = best;
            result[n] = result[n] + 1;
            remainder[best] = -1L;
            ++allocated;
        }
        return target;
    }

    private static void allocateUnbounded(int amount, int[] stock, boolean[] eligible, int[] result) {
        long weightTotal = 0L;
        for (int i = 0; i < stock.length; ++i) {
            if (!eligible[i]) continue;
            weightTotal += (long)Math.max(stock[i], 1);
        }
        long[] remainder = new long[stock.length];
        int allocated = 0;
        for (int i = 0; i < stock.length; ++i) {
            if (!eligible[i]) continue;
            long numerator = (long)amount * (long)Math.max(stock[i], 1);
            int share = (int)(numerator / weightTotal);
            int n = i;
            result[n] = result[n] + share;
            allocated += share;
            remainder[i] = numerator % weightTotal;
        }
        while (allocated < amount) {
            int best = -1;
            for (int i = 0; i < stock.length; ++i) {
                if (!eligible[i] || best >= 0 && remainder[i] <= remainder[best]) continue;
                best = i;
            }
            int n = best;
            result[n] = result[n] + 1;
            remainder[best] = -1L;
            ++allocated;
        }
    }

    private FoodRollbackKernel() {
    }
}

