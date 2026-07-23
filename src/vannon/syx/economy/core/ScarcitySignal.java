package vannon.syx.economy.core;

import java.util.Arrays;

/**
 * Tracks per-resource scarcity (0.0 = abundant, 1.0 = extreme shortage)
 * from FlowMeter.Snapshot. The signal combines two indicators:
 *
 * 1. stockChangePerDay — consistently negative means the stockpile is draining.
 * 2. low absolute stock relative to demand — zero stock with positive demand
 *    means the resource is effectively gone.
 *
 * The signal is exponentially smoothed so transient spikes don't cause
 * wild price/priority oscillations.
 */
public final class ScarcitySignal {
    private double[] signal = new double[0];
    private final double smoothingWindow = 3.0; // days

    /**
     * Recompute scarcity signals from the latest flow snapshot.
     * @param meter   current FlowMeter snapshot
     * @param dtDays  elapsed game-days since last call
     */
    public void update(FlowMeter.Snapshot meter, double dtDays) {
        int goods = meter.size();
        ensureCapacity(goods);
        double blend = dtDays > 0.0 ? (1.0 - Math.exp(-dtDays / smoothingWindow)) : 1.0;

        for (int good = 0; good < goods; ++good) {
            double stock = meter.stock(good);
            double demand = meter.demandPerDay(good);
            double stockChange = meter.stockChangePerDay(good);
            double firmInputs = meter.firmInputsPerDay(good);

            // Component 1: stock is draining (negative change = scarcity pressure)
            double drainPressure = 0.0;
            if (stockChange < 0.0 && demand > 0.0) {
                // How many days until stock hits zero at current drain rate?
                double drainRate = Math.abs(stockChange);
                double daysLeft = stock / Math.max(1.0, drainRate);
                drainPressure = Math.max(0.0, Math.min(1.0, 1.0 - (daysLeft / EconConfig.targetFoodDays)));
            }

            // Component 2: absolute stock is critically low
            double stockPressure = 0.0;
            if (stock <= 0.0 && demand > 0.0) {
                stockPressure = 1.0;
            } else if (demand > 0.0) {
                double coverageDays = stock / demand;
                double targetDays = EconConfig.targetFoodDays;
                if (targetDays > 0.0) {
                    stockPressure = Math.max(0.0, Math.min(1.0, 1.0 - (coverageDays / targetDays)));
                }
            }

            // Component 3: firm demand is rising (more firms want this input)
            double firmPressure = 0.0;
            if (firmInputs > 0.0 && demand > 0.0) {
                // If firm inputs dominate total demand, scarcity is structural
                double firmShare = firmInputs / Math.max(1.0, demand);
                firmPressure = Math.max(0.0, Math.min(1.0, firmShare));
            }

            // Combine: drain pressure is the strongest signal, stock/firm are secondary
            double raw = 0.5 * drainPressure + 0.3 * stockPressure + 0.2 * firmPressure;

            // Exponential smoothing
            this.signal[good] = FlowMeter.smooth(this.signal[good], raw, blend);
        }
    }

    /** Returns scarcity signal for a resource [0.0 = none ... 1.0 = extreme]. */
    public double get(int good) {
        if (good < 0 || good >= this.signal.length) {
            return 0.0;
        }
        return this.signal[good];
    }

    /** Returns the full signal array (defensive copy). */
    public double[] snapshot() {
        return Arrays.copyOf(this.signal, this.signal.length);
    }

    public void clear() {
        Arrays.fill(this.signal, 0.0);
    }

    private void ensureCapacity(int goods) {
        if (this.signal.length == goods) {
            return;
        }
        double[] old = this.signal;
        this.signal = new double[goods];
        // Preserve accumulated signal for indices that still exist.
        // Resource counts rarely change, but when they do (mod updates),
        // losing all smoothing history creates a visible discontinuity.
        int keep = Math.min(old.length, goods);
        System.arraycopy(old, 0, this.signal, 0, keep);
    }
}
