package vannon.syx.economy.core;

public final class WarehouseKernel {
    public static int[] contributions(int[] spendable, int cost) {
        if (cost < 0) {
            throw new IllegalArgumentException("negative cost");
        }
        int[] result = new int[spendable.length];
        if (cost == 0 || spendable.length == 0) {
            return result;
        }
        long capital = 0L;
        for (int value : spendable) {
            capital += (long)Math.max(0, value);
        }
        if (capital <= 0L) {
            return result;
        }
        long raise = Math.min((long)cost, capital);
        return WarehouseKernel.largestRemainder(spendable, raise);
    }

    public static double[] redistributeStakes(double[] stakes, boolean[] employed) {
        if (stakes.length != employed.length) {
            throw new IllegalArgumentException("stake and employment vectors must match");
        }
        double[] result = new double[stakes.length];
        double surrendered = 0.0;
        int workers = 0;
        for (int i = 0; i < stakes.length; ++i) {
            double stake;
            double d = stake = Double.isFinite(stakes[i]) ? Math.max(0.0, stakes[i]) : 0.0;
            if (employed[i]) {
                result[i] = stake;
                ++workers;
                continue;
            }
            surrendered += stake;
        }
        if (workers <= 0 || !(surrendered > 0.0)) {
            return result;
        }
        double share = surrendered / (double)workers;
        for (int i = 0; i < result.length; ++i) {
            if (!employed[i]) continue;
            int n = i;
            result[n] = result[n] + share;
        }
        return result;
    }

    public static int affordableUnits(int[] spendable, int unitPrice, int offered) {
        if (unitPrice <= 0 || offered <= 0) {
            return 0;
        }
        long capital = 0L;
        for (int value : spendable) {
            capital += (long)Math.max(0, value);
        }
        return (int)Math.min((long)offered, capital / (long)unitPrice);
    }

    public static int[] payouts(double[] stakes, boolean[] living, int proceeds) {
        if (stakes.length != living.length) {
            throw new IllegalArgumentException("stake and living vectors must match");
        }
        if (proceeds < 0) {
            throw new IllegalArgumentException("negative proceeds");
        }
        int[] result = new int[stakes.length];
        if (proceeds == 0) {
            return result;
        }
        int[] weights = new int[stakes.length];
        boolean any = false;
        for (int i = 0; i < stakes.length; ++i) {
            double stake = living[i] && Double.isFinite(stakes[i]) ? Math.max(0.0, stakes[i]) : 0.0;
            weights[i] = (int)Math.min(Integer.MAX_VALUE, Math.round(stake * 1000.0));
            if (weights[i] <= 0) continue;
            any = true;
        }
        if (!any) {
            return result;
        }
        return WarehouseKernel.largestRemainder(weights, proceeds);
    }

    public static double decayStakes(double stake, int unitsSold, int unitsHeld) {
        if (unitsHeld <= 0 || unitsSold <= 0) {
            return Math.max(0.0, stake);
        }
        if (unitsSold >= unitsHeld) {
            return 0.0;
        }
        double remaining = stake * (1.0 - (double)unitsSold / (double)unitsHeld);
        return remaining > 0.0 && Double.isFinite(remaining) ? remaining : 0.0;
    }

    public static int backedUnits(int unitsSold, int unitsHeld) {
        if (unitsSold <= 0 || unitsHeld <= 0) {
            return 0;
        }
        return Math.min(unitsSold, unitsHeld);
    }

    private static int[] largestRemainder(int[] weights, long amount) {
        int[] result = new int[weights.length];
        long total = 0L;
        for (int weight : weights) {
            total += (long)Math.max(0, weight);
        }
        if (total <= 0L || amount <= 0L) {
            return result;
        }
        double[] fractions = new double[weights.length];
        long assigned = 0L;
        for (int i = 0; i < weights.length; ++i) {
            double exact = (double)amount * (double)Math.max(0, weights[i]) / (double)total;
            result[i] = (int)Math.floor(exact);
            fractions[i] = exact - (double)result[i];
            assigned += (long)result[i];
        }
        for (long left = amount - assigned; left > 0L; --left) {
            int best = -1;
            for (int i = 0; i < fractions.length; ++i) {
                if (weights[i] <= 0 || best >= 0 && !(fractions[i] > fractions[best])) continue;
                best = i;
            }
            if (best < 0) break;
            int n = best;
            result[n] = result[n] + 1;
            fractions[best] = -1.0;
        }
        return result;
    }

    private WarehouseKernel() {
    }
}

