package vannon.syx.economy.core;

public final class FoodGateKernel {
    public static int bill(int[] quantities, double[] unitPrices, int quote) {
        if (quantities.length != unitPrices.length) {
            throw new IllegalArgumentException("quantity and price vectors must match");
        }
        long total = 0L;
        for (int i = 0; i < quantities.length; ++i) {
            double line;
            if (quantities[i] <= 0 || !(unitPrices[i] > 0.0) || !Double.isFinite(unitPrices[i]) || (total += (line = (double)quantities[i] * unitPrices[i]) >= 2.147483647E9 ? Integer.MAX_VALUE : (long)Math.ceil(line)) < Integer.MAX_VALUE) continue;
            return Math.max(0, quote);
        }
        return (int)Math.min(total, (long)Math.max(0, quote));
    }

    private FoodGateKernel() {
    }
}

