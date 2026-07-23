package vannon.syx.economy.core;

public final class FirmEconomyKernel {
    public static double profit(double[] outputRates, double[] outputPrices, double[] inputRates, double[] inputPrices) {
        if (outputRates.length != outputPrices.length || inputRates.length != inputPrices.length) {
            throw new IllegalArgumentException("rate and price vectors must have equal lengths");
        }
        double revenue = FirmEconomyKernel.dotNonNegative(outputRates, outputPrices);
        double cost = FirmEconomyKernel.dotNonNegative(inputRates, inputPrices);
        return revenue - cost;
    }

    public static double marginal(double profitAtN, double profitAtNMinusOne) {
        if (!Double.isFinite(profitAtN) || !Double.isFinite(profitAtNMinusOne)) {
            return 0.0;
        }
        return profitAtN - profitAtNMinusOne;
    }

    public static boolean shouldIdle(double profit, double hysteresis) {
        return Double.isFinite(profit) && profit < -Math.max(0.0, hysteresis);
    }

    public static int priority(double marginal, double meanPositiveMarginal, int neutral, double elasticity, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max");
        }
        if (!(marginal > 0.0) || !Double.isFinite(marginal)) {
            return min;
        }
        if (!(meanPositiveMarginal > 0.0) || !Double.isFinite(meanPositiveMarginal)) {
            return FirmEconomyKernel.clamp(neutral, min, max);
        }
        double raw = (double)neutral + elasticity * Math.log(marginal / meanPositiveMarginal);
        if (!Double.isFinite(raw)) {
            return raw > 0.0 ? max : min;
        }
        return FirmEconomyKernel.clamp((int)Math.round(raw), min, max);
    }

    public static int[] split(int amount, int workers) {
        if (amount < 0 || workers < 0) {
            throw new IllegalArgumentException("negative split input");
        }
        if (workers == 0) {
            return new int[0];
        }
        int[] result = new int[workers];
        int each = amount / workers;
        int remainder = amount % workers;
        for (int i = 0; i < workers; ++i) {
            result[i] = each + (i < remainder ? 1 : 0);
        }
        return result;
    }

    public static HillResult hillStep(HillState previous, int observedTarget, double observedProfit, int maxTarget, int step, double hysteresis) {
        return FirmEconomyKernel.hillStep(previous, observedTarget, observedProfit, 0, maxTarget, step, hysteresis);
    }

    public static HillResult hillStep(HillState previous, int observedTarget, double observedProfit, int minTarget, int maxTarget, int step, double hysteresis) {
        if (maxTarget < 0) {
            throw new IllegalArgumentException("negative max target");
        }
        minTarget = FirmEconomyKernel.clamp(minTarget, 0, maxTarget);
        step = Math.max(1, step);
        hysteresis = Math.max(0.0, hysteresis);
        observedTarget = FirmEconomyKernel.clamp(observedTarget, minTarget, maxTarget);
        if (!Double.isFinite(observedProfit)) {
            observedProfit = -1.7976931348623157E308;
        }
        if (previous == null || !previous.initialized()) {
            int direction = observedProfit < 0.0 && observedTarget > minTarget ? -1 : (observedTarget < maxTarget ? 1 : -1);
            int probe = FirmEconomyKernel.neighbour(observedTarget, direction, step, minTarget, maxTarget);
            return new HillResult(new HillState(observedTarget, observedProfit, direction, true), probe, 0.0);
        }
        int bestTarget = previous.bestTarget();
        double bestProfit = previous.bestProfit();
        int direction = previous.direction() == 0 ? 1 : Integer.signum(previous.direction());
        double marginal = 0.0;
        if (observedTarget != bestTarget) {
            int distance = observedTarget - bestTarget;
            marginal = (observedProfit - bestProfit) / (double)distance;
            if (observedProfit > bestProfit + hysteresis) {
                bestTarget = observedTarget;
                bestProfit = observedProfit;
                direction = Integer.signum(distance);
            } else {
                direction = -Integer.signum(distance);
            }
        } else if (observedProfit > bestProfit) {
            bestProfit = observedProfit;
        }
        int probe = FirmEconomyKernel.neighbour(bestTarget, direction, step, minTarget, maxTarget);
        if (probe == bestTarget) {
            direction = -direction;
            probe = FirmEconomyKernel.neighbour(bestTarget, direction, step, minTarget, maxTarget);
        }
        return new HillResult(new HillState(bestTarget, bestProfit, direction, true), probe, marginal);
    }

    private static int neighbour(int target, int direction, int step, int min, int max) {
        return FirmEconomyKernel.clamp(target + Integer.signum(direction) * step, min, max);
    }

    private static double dotNonNegative(double[] quantities, double[] prices) {
        double sum = 0.0;
        for (int i = 0; i < quantities.length; ++i) {
            double q = quantities[i];
            double p = prices[i];
            if (!Double.isFinite(q) || !Double.isFinite(p) || !(q > 0.0) || !(p > 0.0)) continue;
            sum += q * p;
        }
        return sum;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private FirmEconomyKernel() {
    }

    public record HillState(int bestTarget, double bestProfit, int direction, boolean initialized) {
    }

    public record HillResult(HillState state, int nextTarget, double observedSlope) {
    }
}

