package vannon.syx.economy.core;

import java.util.Arrays;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class WealthStats {
    public int people = 0;
    public int min = 0;
    public int max = 0;
    public int median = 0;
    public long total = 0L;
    public double mean = 0.0;
    public double gini = 0.0;
    public static final int BUCKETS = 16;
    public final int[] histogram = new int[16];
    public int bucketWidth = 1;
    public int tallest = 1;
    private int[] scratch = new int[1024];

    public void recompute(Roster roster, Wallets wallets) {
        int i;
        int n;
        this.people = n = roster.size();
        if (n == 0) {
            return;
        }
        if (this.scratch.length < n) {
            this.scratch = new int[Math.max(n, this.scratch.length * 2)];
        }
        for (i = 0; i < n; ++i) {
            this.scratch[i] = wallets.get(roster.get(i));
        }
        Arrays.sort(this.scratch, 0, n);
        this.min = this.scratch[0];
        this.max = this.scratch[n - 1];
        this.median = this.scratch[n / 2];
        this.total = 0L;
        for (i = 0; i < n; ++i) {
            this.total += (long)this.scratch[i];
        }
        this.mean = (double)this.total / (double)n;
        if (this.total > 0L) {
            long weighted = 0L;
            for (int i2 = 0; i2 < n; ++i2) {
                weighted += (long)(2 * (i2 + 1) - n - 1) * (long)this.scratch[i2];
            }
            this.gini = (double)weighted / ((double)n * (double)this.total);
        } else {
            this.gini = 0.0;
        }
        this.bucketWidth = Math.max(1, this.max / 16 + 1);
        Arrays.fill(this.histogram, 0);
        for (int i3 = 0; i3 < n; ++i3) {
            int b = this.scratch[i3] / this.bucketWidth;
            if (b >= 16) {
                b = 15;
            }
            int n2 = b;
            this.histogram[n2] = this.histogram[n2] + 1;
        }
        this.tallest = 1;
        for (int c : this.histogram) {
            if (c <= this.tallest) continue;
            this.tallest = c;
        }
    }
}

