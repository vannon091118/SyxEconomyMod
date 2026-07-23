package vannon.syx.economy.core;

import game.time.TIME;
import game.time.TIMECYCLE;
import util.statistics.HISTORY;

/**
 * Minimal fixed-size HISTORY implementation for use with GChart.
 * Stores a configurable number of recent double values and shifts them
 * forward whenever a new value is pushed. Values are not tied to a real
 * in-game TIMECYCLE; the TIME constant is only returned to satisfy the
 * HISTORY contract. History is rebuilt each session and not persisted.
 */
public final class SimpleHistory implements HISTORY {
    private final double[] data;

    public SimpleHistory(int records) {
        this.data = new double[Math.max(1, records)];
    }

    /** Push a new value into the history; older values move back one slot. */
    public void push(double value) {
        for (int i = this.data.length - 1; i > 0; --i) {
            this.data[i] = this.data[i - 1];
        }
        this.data[0] = value;
    }

    @Override
    public double getD(int fromZero) {
        int idx = Math.max(0, Math.min(this.data.length - 1, fromZero));
        return this.data[idx];
    }

    @Override
    public int historyRecords() {
        return this.data.length;
    }

    @Override
    public TIMECYCLE time() {
        return TIME.days();
    }
}
