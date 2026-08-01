package vannon.syx.economy.headless;

import java.util.Arrays;
import java.util.Random;

/**
 * Test-only deterministic state container backing the 7 EngineMirror sub-interface
 * stub providers. Modeled after {@link vannon.syx.economy.benchmark.EconomyMock}:
 * zero-sum pairwise-lottery dynamics across {@code citizenCount} citizens and a
 * fixed-size treasury. Same seed yields the same byte-exact timeline.
 *
 * <h2>Comparison with EconomyMock</h2>
 * <p>{@link EconomyMock} stands alone — it owns its own RNG and ticks, returning
 * samples to {@code HeadlessBenchTest}. {@code MockWorldState} sits behind
 * 7 I*Access stubs — the stubs read {@code treasury()}, {@code population()},
 * {@code religionCount()}, etc., directly from this class; EconomySim.update()
 * then mutates the same treasury/citizen arrays via the EconomySim side of
 * the test harness. Two parallel worlds, both seeded from the same {@code seed}.</p>
 *
 * <h2>Snapshot semantics</h2>
 * <p>{@link #tick()} and {@link #simTick()} couple the zero-sum lottery with
 * the wall-clock simulation: every {@code simTick()} bumps the day counter and
 * performs {@code ceil(pop/5)} lottery pairs. Money supply (treasury + Σ wallets)
 * is strictly conserved across ticks.</p>
 */
public final class MockWorldState {

    public final int citizenCount;
    public final int days;
    public final long seed;

    public final long initialMoneySupply;

    private final int[] wallets;
    private long treasury;
    private final Random rng;
    private long day = 0;

    public MockWorldState(int citizenCount, int days, long seed) {
        if (citizenCount < 1) throw new IllegalArgumentException("citizenCount ≥ 1 required");
        if (days < 0) throw new IllegalArgumentException("days ≥ 0 required");
        this.citizenCount = citizenCount;
        this.days = days;
        this.seed = seed;
        this.wallets = new int[citizenCount];
        this.rng = new Random(seed);
        seedCitizens();
        this.initialMoneySupply = treasury + sumWallets();
    }

    private void seedCitizens() {
        for (int i = 0; i < citizenCount; i++) {
            int money = 800 + (int) Math.round(rng.nextGaussian() * 200);
            wallets[i] = Math.max(50, money);
        }
        treasury = 50_000L;
    }

    private long sumWallets() {
        long s = 0L;
        for (int w : wallets) s += w;
        return s;
    }

    /** Strict zero-sum lottery tick — does NOT advance {@link #day()}. */
    public void tick() {
        int pairs = Math.max(5, (citizenCount + 4) / 5);
        for (int t = 0; t < pairs; t++) {
            int i = rng.nextInt(citizenCount);
            int j = rng.nextInt(citizenCount);
            if (i == j) continue;
            int amount = 5 + rng.nextInt(46);
            amount = Math.min(amount, wallets[i]);
            wallets[i] -= amount;
            wallets[j] += amount;
        }
    }

    /** Advance wall-clock day AND perform {@link #tick()} once. */
    public void simTick() {
        tick();
        day++;
    }

    public long day() { return day; }

    public long treasury() { return treasury; }

    public int[] walletsSnapshot() { return wallets.clone(); }

    public long moneySupply() { return treasury + sumWallets(); }

    public int medianWallet() {
        int[] sorted = wallets.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    public double gini() {
        int n = wallets.length;
        if (n == 0) return 0.0;
        int[] sorted = wallets.clone();
        Arrays.sort(sorted);
        long total = 0L;
        for (int w : sorted) total += w;
        if (total == 0L) return 0.0;
        long weighted = 0L;
        for (int i = 0; i < n; i++) {
            weighted += (long) (2 * (i + 1) - n - 1) * (long) sorted[i];
        }
        return (double) weighted / ((double) n * (double) total);
    }
}
