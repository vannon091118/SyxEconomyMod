package vannon.syx.economy.benchmark;

import init.resources.RESOURCES;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.FlowPrices;

/**
 * Sampling interface used by {@link BenchmarkInstance} to read economy state every simulated
 * day. Two implementations:
 * <ul>
 *   <li>{@link FromSim} — production default; reads from a running EconomySim.</li>
 *   <li>{@link Constant} — test stub used by {@code mvn test} without booting the
 *       Songs-of-Syx engine — feeds scripted values per day.</li>
 * </ul>
 *
 * <p>Decoupling the metrics behind this interface means batch-mode tests can verify the
 * entire read-sample-write-cutoff path in pure JVM without any engine state.</p>
 */
public interface EconomyMetrics {

    double gini();

    long moneySupply();

    int medianPrice();

    /** Production: reads from {@link EconomySim#active()}. Null at any layer → 0. */
    final class FromSim implements EconomyMetrics {

        @Override
        public double gini() {
            EconomySim sim = EconomySim.active();
            return sim == null ? 0.0 : sim.stats().gini;
        }

        @Override
        public long moneySupply() {
            EconomySim sim = EconomySim.active();
            if (sim == null) return 0L;
            // Treasury + sum of citizens' money (stats().total). Excludes flow counters
            // because BudgetSupply is best represented by the static pool (wallet+state).
            return sim.treasury() + (long) sim.stats().total;
        }

        @Override
        public int medianPrice() {
            EconomySim sim = EconomySim.active();
            if (sim == null) return 0;
            FlowPrices fp = sim.flowPrices();
            if (fp == null || !fp.ready()) return 0;
            // RESOURCES.ALL() returns snake2d.util.sets.LIST which is non-generic-infriendly;
            // iterate by index — we never call methods on individual RESOURCE instances.
            int size = RESOURCES.ALL().size();
            List<Integer> prices = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                int p = fp.priceRoundedUp(i);
                if (p > 0) prices.add(p);
            }
            if (prices.isEmpty()) return 0;
            Collections.sort(prices);
            return prices.get(prices.size() / 2);
        }
    }

    /** Test stub —calls a script function so {@code mvn test} can drive synthetic days. */
    final class Constant implements EconomyMetrics {
        private final double gini;
        private final long money;
        private final int median;

        public Constant(double gini, long money, int median) {
            this.gini = gini;
            this.money = money;
            this.median = median;
        }

        @Override public double gini() { return this.gini; }
        @Override public long moneySupply() { return this.money; }
        @Override public int medianPrice() { return this.median; }
    }
}
