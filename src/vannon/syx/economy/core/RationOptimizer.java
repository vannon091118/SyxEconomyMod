package vannon.syx.economy.core;

public final class RationOptimizer {
    public static Result optimize(int requested, int[] available, double[] unitPrices, boolean[] preferred) {
        if (available == null || unitPrices == null || preferred == null || available.length != unitPrices.length || available.length != preferred.length) {
            throw new IllegalArgumentException("availability, prices, and preferences must align");
        }
        int target = Math.max(0, requested);
        long totalAvailable = 0L;
        for (int amount : available) {
            if (amount < 0) {
                throw new IllegalArgumentException("availability must be non-negative");
            }
            totalAvailable += (long)amount;
        }
        if ((target = (int)Math.min((long)target, Math.min(Integer.MAX_VALUE, totalAvailable))) == 0) {
            return new Result(new int[available.length], 0, 0, 0L);
        }
        State[] states = new State[target + 1];
        states[0] = new State(0, 0L, new int[available.length]);
        for (int food = 0; food < available.length; ++food) {
            State[] next = new State[target + 1];
            for (int filled = 0; filled <= target; ++filled) {
                State base = states[filled];
                if (base == null) continue;
                int limit = Math.min(available[food], target - filled);
                for (int take = 0; take <= limit; ++take) {
                    int amount;
                    long cost;
                    int[] bundle = (int[])base.bundle.clone();
                    bundle[food] = take;
                    int preference = base.preferred + (preferred[food] ? take : 0);
                    State candidate = new State(preference, cost = base.cost + RationOptimizer.lineCost(take, unitPrices[food]), bundle);
                    if (!RationOptimizer.better(candidate, next[amount = filled + take])) continue;
                    next[amount] = candidate;
                }
            }
            states = next;
        }
        State best = states[target];
        if (best == null) {
            throw new IllegalStateException("ration optimizer lost a feasible bundle");
        }
        return new Result(best.bundle, target, best.preferred, best.cost);
    }

    private static boolean better(State candidate, State incumbent) {
        if (incumbent == null) {
            return true;
        }
        if (candidate.preferred != incumbent.preferred) {
            return candidate.preferred > incumbent.preferred;
        }
        return candidate.cost < incumbent.cost;
    }

    private static long lineCost(int quantity, double unitPrice) {
        if (quantity <= 0 || !(unitPrice > 0.0) || !Double.isFinite(unitPrice)) {
            return 0L;
        }
        double value = (double)quantity * unitPrice;
        return value >= 2.147483647E9 ? Integer.MAX_VALUE : (long)Math.ceil(value);
    }

    private RationOptimizer() {
    }

    public record Result(int[] bundle, int servings, int preferredServings, long cost) {

        public Result {
            bundle = bundle.clone();
        }

        @Override
        public int[] bundle() {
            return bundle.clone();
        }
    }

    private record State(int preferred, long cost, int[] bundle) {
    }
}

