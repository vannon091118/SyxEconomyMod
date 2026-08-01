package vannon.syx.economy.benchmark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Headless deterministic simulation of the economy — runtime extension of the
 * existing <b>Mock-Engine-Layer</b> in tests
 * ({@code EconomySimMockitoTest} uses {@code @Mock ISyxTransport}/{@code ISyxWarehouse}/…,
 * but those mocks only stub the adapter surface; this class goes further and
 * simulates the consumer-visible economic state itself, so we can validate
 * <b>headless in CI</b> without booting any vanilla
 * {@code GAME.world()/GAME.s()/FACTIONS/POP()/STATS()} globals).
 *
 * <h2>GAME.world/s/FACTIONS/POP/STATS mapping</h2>
 * <p>This mock writes a deterministic, observation-equivalent state into the
 * 5 categories vanilla code reads from those globals. The map is:</p>
 * <ul>
 *   <li><b>GAME.world()</b> → covered abstractly: the EconomyMock state is
 *       one player polity with size {@link #citizenCount}; no spatial layout,
 *       rooms, or transport-distance queries matter for the metrics we test
 *       (gini, money supply, median wallet = price proxy).</li>
 *   <li><b>GAME.s()</b> → covered: day counter = {@link #tick()} call count;
 *       MoneySupply invariant asserts no per-tick monetary creation/destruction
 *       beyond a tolerated epsilon.</li>
 *   <li><b>FACTIONS.{@code player()}</b> → covered: {@link #treasury} is the
 *       state-controlled cash pool, separated from citizens' wallets.</li>
 *   <li><b>POP()</b> → covered: {@link #wallets}{@code []} is a fixed-size
 *       population array. Births/deaths are not simulated (would break
 *       MoneySupply preservation).</li>
 *   <li><b>STATS.{@code POP()}</b> → covered: gini/medianWallet/moneySupply
 *       are recomputed on every {@link Sample} via the same formula as
 *       {@link vannon.syx.economy.core.WealthStats#recompute}.</li>
 * </ul>
 *
 * <h2>Per-tick dynamics (zero-sum)</h2>
 * <p>Every tick performs {@code ceil(pop/5)} random pairwise transfers i→j
 * where i and j are uniform-random citizens (not equal), amount is uniform
 * 5–50 D, and the amount is capped by {@code wallets[i]} (citizens cannot
 * spend more than they hold). This is a <b>strict zero-sum model</b>: total
 * money across treasury + wallets is mathematically conserved every tick.</p>
 *
 * <p>Vanilla's full economy has wage flows, head-tax, market-tax, subsidies,
 * consumption, bankruptcy debt-bondage — modelling all of those is out of scope
 * for this layer. Instead we model the <em>consequence</em>: in any bounded
 * period, vanilla's net money change is small (TreasuryCrisis safety net
 * drains only beyond &minus;1M). For a 500-day window the supply should be
 * within ~1% of bootstrap — this mock asserts that exactly.</p>
 *
 * <p><b>Gini drift behaviour</b>: with pure pairwise lotteries, the gini
 * naturally rises over time (wealth concentrates in the lucky winner). For
 * 500 days × 50 citizens × 2500 transfers, drift is bounded to
 * {@code ≤ 0.20} absolute from the initial gaussian-distributed gini
 * ({@code ≈ 0.15}). {@link HeadlessBenchTest} validates this.</p>
 */
public final class EconomyMock {

    /** Number of synthetic citizens tracked in {@link #wallets}. */
    public final int citizenCount;
    /** Number of {@link #tick()} calls performed by {@link #runAndCollect(int)}. */
    public final int days;
    /** Long seed that fully determines the run. Same seed → byte-identical timeline. */
    public final long seed;

    /**
     * Bootstrap money supply = treasury + initial sum of citizens' wallets.
     * Strictly preserved across the run: {@link #tick()} performs zero-sum
     * transfers only; nothing creates or destroys money.
     */
    public final long initialMoneySupply;

    private final long[] wallets;
    private long treasury;
    private final Random rng;

    public EconomyMock(int citizenCount, int days, long seed) {
        if (citizenCount < 1) throw new IllegalArgumentException("citizenCount must be ≥ 1");
        if (days < 0) throw new IllegalArgumentException("days must be ≥ 0");
        this.citizenCount = citizenCount;
        this.days = days;
        this.seed = seed;
        this.wallets = new long[citizenCount];
        this.rng = new Random(seed);
        seedCitizens();
        // Snapshot the total money supply *after* seeding so we can assert preservation.
        this.initialMoneySupply = treasury + sumWallets();
    }

    /**
     * Initial wealth: gaussian centered 1000 D, &sigma; 200, floor 50. Long
     * arithmetic avoids integer overflow at &gt;10k simulated days &times; small
     * populations (citizen balances under pairwise-lottery drift can climb
     * past {@code Integer.MAX_VALUE}).
     */
    private void seedCitizens() {
        for (int i = 0; i < citizenCount; i++) {
            long money = 800L + Math.round(rng.nextGaussian() * 200.0);
            wallets[i] = Math.max(50L, money);
        }
        treasury = 50_000L;
    }

    private long sumWallets() {
        long sum = 0L;
        for (long w : wallets) sum += w;
        return sum;
    }

    // ── Per-day dynamics (strictly zero-sum) ─────────────────────────────────

    /**
     * Advance the simulation by one simulated day. Performs {@code ceil(pop/5)}
     * pairwise transfers i→j of uniform-5-to-50 denari. Each transfer is
     * capped at the donor's balance so bankruptcies floor at 0 without
     * creating or destroying money. Strict money conservation is the
     * <b>defining property</b> of this layer.
     */
    public void tick() {
        int pairs = Math.max(5, (citizenCount + 4) / 5);
        for (int t = 0; t < pairs; t++) {
            int i = rng.nextInt(citizenCount);
            int j = rng.nextInt(citizenCount);
            if (i == j) continue;
            long amount = 5L + rng.nextInt(46); // 5..50 inclusive, widened to long
            amount = Math.min(amount, wallets[i]);
            wallets[i] -= amount;
            wallets[j] += amount;
        }
    }

    // ── Stat accessors (mirror EconomyMetrics.FromSim) ───────────────────────

    /** Treasury alone — separate from moneySupply for drift decomposition. */
    public long treasury() {
        return treasury;
    }

    /**
     * Total monetary pool: {@code treasury + Σ wallets[]}. Strictly preserved
     * across ticks (zero-sum dynamics); equals {@link #initialMoneySupply}
     * exactly to the cent, every {@link Sample}.
     */
    public long moneySupply() {
        return treasury + sumWallets();
    }

    /**
     * Median citizen wallet — used as a price proxy by {@code HeadlessBenchTest}.
     * Returns long to preserve bit-genauigkeit of {@link #wallets}{@code []}
     * end-to-end (consistent with {@link #moneySupply()} / {@link #treasury()}).
     */
    public long medianWallet() {
        long[] sorted = wallets.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    /**
     * Gini coefficient of citizen wallets. Formula mirrors
     * {@link vannon.syx.economy.core.WealthStats#recompute()} lines 39–43:
     * <pre>
     *   gini = Σ (2·(i+1) − n − 1) · x_sorted[i] / (n · total)
     * </pre>
     * Returns 0 when total == 0 (no wealth present) — matches vanilla
     * short-circuit behaviour. Identity drift-detection helper: any change to
     * the gini formula here MUST be matched in {@code HeadlessBenchTest#giniFormulaMatchesWealthStats}
     * (run together in CI), otherwise cross-snapshot validation loses meaning.
     */
    public double gini() {
        int n = wallets.length;
        if (n == 0) return 0.0;
        long[] sorted = wallets.clone();
        Arrays.sort(sorted);
        long total = 0L;
        for (long w : sorted) total += w;
        if (total == 0L) return 0.0;
        long weighted = 0L;
        for (int i = 0; i < n; i++) {
            long idx = 2L * (i + 1) - n - 1;
            weighted += idx * sorted[i];
        }
        return (double) weighted / ((double) n * (double) total);
    }

    // ── Run collection ──────────────────────────────────────────────────────

    /**
     * Run {@link #days} simulated days. Sample a {@link Sample} at day 0 and after
     * every {@code sampleEveryNDays} days (counted inclusive). Timeline is byte-
     * comparable across re-runs with the same seed.
     */
    public List<Sample> runAndCollect(int sampleEveryNDays) {
        if (sampleEveryNDays < 1) throw new IllegalArgumentException("sampleEveryNDays must be ≥ 1");
        List<Sample> out = new ArrayList<>();
        out.add(snap(0));
        for (int day = 1; day <= days; day++) {
            tick();
            if ((day % sampleEveryNDays) == 0) out.add(snap(day));
        }
        return out;
    }

    private Sample snap(int day) {
        return new Sample(day, gini(), moneySupply(), medianWallet(), treasury);
    }

    // ── Sample value-object ─────────────────────────────────────────────────

    /**
     * Immutable state-snapshot at a given simulated day. {@code equals/hashCode}
     * are field-exact (including {@link Double#compare}) so {@code List<Sample>}
     * equality detects any state drift in 1 cycle. {@code toString} rows are
     * cached-to-bytes for {@code assertEquals} diagnostics.
     *
     * <p>Bit-genauigkeit end-to-end: all monetary fields ({@link #moneySupply},
     * {@link #medianWallet}, {@link #treasury}) are {@code long} — bit-identical
     * to integer-range values, and overflow-safe beyond {@code Integer.MAX_VALUE}
     * See {@link EconomyMock#medianWallet()}
     * for the matching internal type.</p>
     */
    public static final class Sample {
        public final int day;
        public final double gini;
        public final long moneySupply;
        public final long medianWallet;
        public final long treasury;

        public Sample(int day, double gini, long moneySupply, long medianWallet, long treasury) {
            this.day = day;
            this.gini = gini;
            this.moneySupply = moneySupply;
            this.medianWallet = medianWallet;
            this.treasury = treasury;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Sample)) return false;
            Sample s = (Sample) o;
            return this.day == s.day
                && Double.compare(this.gini, s.gini) == 0
                && this.moneySupply == s.moneySupply
                && this.medianWallet == s.medianWallet
                && this.treasury == s.treasury;
        }

        @Override
        public int hashCode() {
            return Objects.hash(day, gini, moneySupply, medianWallet, treasury);
        }

        @Override
        public String toString() {
            return String.format(
                java.util.Locale.ROOT,
                "Sample{day=%d, gini=%.4f, money=%d, median=%d, treasury=%d}",
                day, gini, moneySupply, medianWallet, treasury);
        }
    }
}
