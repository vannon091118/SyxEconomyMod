package vannon.syx.economy.core;

public final class FoodGateKernel {

    /**
     * Computes the quoted/ceiled total price of a cart.
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>Lines with non-positive quantity, non-positive price, NaN or infinite
     *       prices are ignored.</li>
     *   <li>Each remaining line contributes {@code ceil(quantity * unitPrice)} to
     *       the running total.</li>
     *   <li>If any single line value is {@code >= Integer.MAX_VALUE}, that line
     *       contributes exactly {@code Integer.MAX_VALUE}.</li>
     *   <li>As soon as the running total reaches {@code Integer.MAX_VALUE}, the
     *       method returns {@code Math.max(0, quote)} immediately.</li>
     *   <li>Otherwise the result is {@code min(total, max(0, quote))}.</li>
     * </ul>
     */
    public static int bill(int[] quantities, double[] unitPrices, int quote) {
        if (quantities.length != unitPrices.length) {
            throw new IllegalArgumentException("quantity and price vectors must match");
        }
        long total = 0L;
        for (int i = 0; i < quantities.length; ++i) {
            if (quantities[i] <= 0 || !(unitPrices[i] > 0.0) || !Double.isFinite(unitPrices[i])) {
                continue;
            }
            double line = (double) quantities[i] * unitPrices[i];
            long addition = line >= (double) Integer.MAX_VALUE
                    ? (long) Integer.MAX_VALUE
                    : (long) Math.ceil(line);
            total += addition;
            if (total >= (long) Integer.MAX_VALUE) {
                return Math.max(0, quote);
            }
        }
        return (int) Math.min(total, (long) Math.max(0, quote));
    }

    private FoodGateKernel() {
    }
}
