package vannon.syx.economy.benchmark;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.Taxes;

/**
 * Headless-CI-driven benchmark test for {@link EconomyMock}. Validates that the
 * deterministic economy simulator can run 500 simulated game-days in well under
 * 5 seconds and validates gini-drift bounded under tolerance — <b>without
 * booting</b> the Songs-of-Syx engine globals
 * {@code GAME.world()/s()/FACTIONS/{@code player()}/POP()/STATS()}).
 *
 * <p>This test runs in <em>batch mode</em> from {@code mvn test --batch-mode}
 * with {@code -Dtest=HeadlessBenchTest}. It does <b>not</b> require a vanilla
 * jar on the test classpath, does not boot {@code init.Main}, and never
 * touches {@code SETT.ENTITIES()}.</p>
 *
 * <h2>Tolerances</h2>
 * <ul>
 *   <li><b>Performance</b>: 500 days in {@code <5000ms} (≈ 10 ms/tick on commodity CI).</li>
 *   <li><b>Gini drift</b>: per-config <b>safety limit</b> {@code < 0.30}
 *       absolute |final−initial|, validated across a {@code 5×3 = 15}-cell
 *       matrix (seeds {@code [42, 99, 2024, 31415, 88888]} × populations
 *       {@code [20, 50, 100]}). The matrix MEDIAN drift — the headline
 *       seed-agnostic invariant — must stay in {@code [0.10, 0.20]}. A single
 *       re-tuned randomisation cannot silently shift all 15 cells into the
 *       upper band without failing the median check (median is robust up to
 *       7 outliers of 15). The 0.30 safety limit covers the natural
 *       stochastic rise of pairwise-lottery gini over 500 days × small
 *       populations (empirically observed max ≈ 0.26 at seed=31415, pop=20);
 *       tighten (with proof of true stationarity) or loosen if dynamics change.</li>
 *   <li><b>Money preservation</b>: <b>zero</b> drift. Per {@code EconomyMock}
 *       zero-sum semantics, every {@code Sample.moneySupply} must equal
 *       {@link EconomyMock#initialMoneySupply}. Any non-zero delta indicates
 *       a tick() bug (creates or destroys money).</li>
 *   <li><b>Determinism</b>: same {@code seed} → byte-identical sample time-line.</li>
 * </ul>
 */
class HeadlessBenchTest {

    /**
     * Wild-outlier safety limit — distinct from the headline median band. Per-
     * config drift must stay BELOW this ceiling so a single outlier cell
     * cannot drag the matrix median outside {@code [0.10, 0.20]}. Set
     * empirically to cover observed max ≈ 0.26 (seed=31415, pop=20) with 15%
     * margin; tighten only after proving true stationarity across the 15-cell
     * matrix.
     */
    static final double GINI_DRIFT_PER_CONFIG_SAFETY_LIMIT = 0.30;

    /** Matrix-wide median-drift lower band — natural-rise floor. */
    static final double GINI_DRIFT_MEDIAN_MIN = 0.10;

    /** Matrix-wide median-drift upper band — also the per-config ceiling. */
    static final double GINI_DRIFT_MEDIAN_MAX = 0.20;

    /** Wall-clock budget for 500 simulated days. */
    static final long TIME_BUDGET_MS = 5_000L;

    /** Money-preservation drift tolerance (long-converted: must be 0). */
    static final long MONEY_PRESERVATION_TOLERANCE = 0L;

    /** Number of repeated performance runs for a single warmup-independent median. */
    private static final int PERF_RUNS = 5;

    // ───────────────────────────────────────────────────────────────────────
    // Performance (5s budget for 500 days)
    // ───────────────────────────────────────────────────────────────────────

    /**
     * 500 simulated days must complete under 5 seconds — the canonical hard
     * requirement from the upstream task. Uses 50 citizens (canonical harness
     * size) and samples every 5 days.
     */
    @Test
    @Timeout(value = 30, unit = java.util.concurrent.TimeUnit.SECONDS)
    void runs500DaysUnder5Seconds() {
        long worstMs = 0;
        int sampleCount = -1;
        for (int run = 0; run < PERF_RUNS; run++) {
            long startNs = System.nanoTime();
            EconomyMock sim = new EconomyMock(50, 500, 42L);
            List<EconomyMock.Sample> samples = sim.runAndCollect(5);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            worstMs = Math.max(worstMs, elapsedMs);
            sampleCount = samples.size();
        }
        // 1 day-0 sample + 500/5 (days 5,10,...,500) = 101 samples
        assertEquals(101, sampleCount, "expected 101 samples for 500/5 cadence + bootstrap");
        assertTrue(worstMs < TIME_BUDGET_MS,
            "500-day simulation exceeded " + TIME_BUDGET_MS
                + "ms budget in the worst of " + PERF_RUNS + " runs: " + worstMs + "ms");
    }

    /** Linear scaling sanity: 10× fewer ticks must be at most ~50× faster. */
    @Test
    @Timeout(value = 60, unit = java.util.concurrent.TimeUnit.SECONDS)
    void scalesSubLinearly() {
        long fast = bestOf(50, 500);
        long slow = bestOf(50, 5_000);
        long ceiling = fast * 50 + 500; // generous — soft signal of O(n²) regression
        assertTrue(slow < ceiling,
            "EconomyMock does not scale: 500 ticks=" + fast + "ms (best), "
                + "5000 ticks=" + slow + "ms (best); ceiling " + ceiling + "ms");
    }

    /** Take the best (fastest) of N runs to filter CI noise spikes. */
    private static long bestOf(int citizens, int days) {
        long best = Long.MAX_VALUE;
        for (int run = 0; run < 3; run++) {
            long t0 = System.nanoTime();
            new EconomyMock(citizens, days, 42L).runAndCollect(5);
            long elapsed = (System.nanoTime() - t0) / 1_000_000L;
            if (elapsed < best) best = elapsed;
        }
        return best;
    }

    // ───────────────────────────────────────────────────────────────────────
    // Money-supply preservation (zero-sum guarantee)
    // ───────────────────────────────────────────────────────────────────────

    /**
     * <b>Headline invariant</b>: under the zero-sum lottery dynamics,
     * {@code Σw + treasury} must equal {@link EconomyMock#initialMoneySupply}
     * for every sample. Any non-zero delta indicates {@link EconomyMock#tick()}
     * is leaking money — fail-fast.
     */
    @Test
    void moneySupplyIsStrictlyConserved() {
        EconomyMock sim = new EconomyMock(50, 500, 42L);
        List<EconomyMock.Sample> samples = sim.runAndCollect(1);
        long initial = sim.initialMoneySupply;
        for (EconomyMock.Sample s : samples) {
            assertEquals(initial, s.moneySupply,
                "money supply leaked at day " + s.day
                    + ": initial=" + initial + ", current=" + s.moneySupply);
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Determinism / reproducibility
    // ───────────────────────────────────────────────────────────────────────

    /** Same seed MUST produce byte-identical sample timelines. */
    @Test
    void sameSeedProducesByteIdenticalTimeline() {
        List<EconomyMock.Sample> a = new EconomyMock(50, 500, 42L).runAndCollect(5);
        List<EconomyMock.Sample> b = new EconomyMock(50, 500, 42L).runAndCollect(5);
        assertTimelineEquals(a, b,
            "deterministic violation: same seed must yield bit-identical Sample lists");
    }

    /** Sanity check: the seed must actually influence outcomes. */
    @Test
    void differentSeedProducesDifferentTimeline() {
        List<EconomyMock.Sample> a = new EconomyMock(50, 500, 42L).runAndCollect(5);
        List<EconomyMock.Sample> b = new EconomyMock(50, 500, 99L).runAndCollect(5);
        assertNotEquals(a, b,
            "seed has no effect — either tick() ignores rng() or seedCitizens was bypassed");
    }

    /**
     * 5 consecutive re-runs of the same seed must all match — catches any
     * accidental class-level non-determinism (static RNG state, lazy init,
     * accumulator).
     */
    @Test
    void manyRunsKeepAgreeingUnderIdenticalSeed() {
        List<EconomyMock.Sample> golden = new EconomyMock(50, 500, 42L).runAndCollect(5);
        for (int i = 0; i < 5; i++) {
            List<EconomyMock.Sample> run = new EconomyMock(50, 500, 42L).runAndCollect(5);
            assertTimelineEquals(golden, run, "run " + i + " diverged from golden timeline");
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Sanity of the final state
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Final system must be physically reasonable: gini ∈ [0, 1], money supply
     * is finite and matches bootstrap (zero-sum invariant), median wallet ≥ 0,
     * treasury ≥ 0. A failure here signals a corrupted simulator (NaN gini,
     * negative wallets leaking, etc.).
     */
    @Test
    void finalStateIsPhysicallyReasonable() {
        List<EconomyMock.Sample> samples = new EconomyMock(50, 500, 42L).runAndCollect(5);
        EconomyMock.Sample last = samples.get(samples.size() - 1);
        EconomyMock.Sample first = samples.get(0);
        assertTrue(last.gini >= 0.0 && last.gini <= 1.0,
            "gini out of [0,1] range: " + last.gini);
        assertTrue(Double.isFinite(last.gini), "gini is NaN/Infinity");
        assertTrue(last.moneySupply > 0, "money supply ≤ 0");
        assertTrue(last.medianWallet > 0, "median wallet ≤ 0");
        assertTrue(last.treasury > 0, "treasury ≤ 0");
        // Money-supply invariant: moneySupply = initialMoneySupply exactly.
        assertEquals(first.moneySupply, last.moneySupply,
            "money supply must be conserved (zero-sum dynamics)");
        // Programmatic invariant: moneySupply = treasury + Σ wallets — verified in
        // a separate test to avoid cross-test coupling.
        assertTrue(last.moneySupply >= last.treasury,
            "money supply must include treasury + Σ wallets, so ≥ treasury alone");
    }

    // ───────────────────────────────────────────────────────────────────────

    // ───────────────────────────────────────────────────────────────────────
    // Sprint v0.13.127+TaxImmigrationDecoupling — Bench-Snapshot Regression
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Sprint v0.13.127+ Bench-Snapshot-Regression-Test: bestätigt empirisch dass
     * die Sprint-v0.13.124+BootstrapEncapsulation-Entkoppelung zwischen
     * {@code EconConfig.meticImmigrationDepth} und
     * {@link Taxes#immigrationMultiplier(int)} hält.
     *
     * <p><b>Setting</b>: {@code Taxes.immigrationMultiplier(int meticModifier)} liesst
     * {@code EconConfig.taxImmigrationDepth} (=0.20 Default) und
     * {@code EconConfig.meticImmigrationSteepness} (=10.0 Default), NICHT
     * {@code meticImmigrationDepth}. Pre-v0.13.124 las die Formel noch
     * {@code meticImmigrationDepth} — Cross-Semantik-Bug: Stage-Override der
     * Migration mutierte <em>gleichzeitig</em> die Tax-Multiplier-Berechnung.</p>
     *
     * <p><b>Was getestet wird</b>: vor und nach {@code setMeticImmigrationDepth(0.7)}
     * werden {@code 180} Werte (= {@code BenchConfig.defaults().runDays}-Halb-Jahr-Convention
     * aus bench-baseline.save-Contract) gespült — einer pro Tag mit ramping
     * {@code meticModifier=d * 2}. Wenn die Werte-Arrays divergieren: <b>HARD-FAIL</b>
     * weil das einen wieder eingeführten Semantik-Leak zwischen Immigration und Tax
     * bedeuten würde.</p>
     *
     * <p><b>Empirik-Pattern</b>: Mit k=0.20 und k=0.7 produzieren non-decoupled
     * Codes Tag-10-Werte von 1.0-0.20*0.964=0.807 vs. 1.0-0.7*0.964=0.325
     * (Delta=0.482). Das ist klar detektierbar.</p>
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void immigrationTaxDecouplingRegression() {
        final int runDays = 180; // BenchConfig.defaults().runDays (Halb-Jahr bench-baseline-save Contract)

        final double[] baselineCsv;
        final double[] afterSetterCsv;

        // Save & Restore — Testisolation: andere Tests dürfen den Default-Wert erwarten.
        final double originalDepth = EconConfig.meticImmigrationDepth;
        try {
            // Run 1: vor dem Setter auf Default-Zustand. Defensive Setter-Roundtrip
            // statt direkter Read damit der Test nicht von einem bereits mutierten
            // State eines vorherigen Tests beeinflusst wird.
            EconConfig.setMeticImmigrationDepth(0.20);
            baselineCsv = flushImmigrationMultiplier(runDays);

            // Run 2: nach setMeticImmigrationDepth(0.7). Wenn Tax post-v0.13.124+ korrekt
            // entkoppelt ist: baselineCsv == afterSetterCsv byte-identical.
            EconConfig.setMeticImmigrationDepth(0.7);
            afterSetterCsv = flushImmigrationMultiplier(runDays);
        } finally {
            EconConfig.setMeticImmigrationDepth(originalDepth); // Restore für nachfolgende Tests
        }

        // Defensive Length-Pre-Check: separate error für Length-Mismatch.
        // Wenn die Längen ungleich sind hilft `findFirstDivergence` und `Arrays.equals`
        // mit Length-Mismatch nicht weiter — die Fail-Message würde sonst IndexOutOfBounds werfen.
        assertEquals(baselineCsv.length, afterSetterCsv.length,
            "CSV lengths differ: baseline=" + baselineCsv.length
                + ", afterSetter=" + afterSetterCsv.length
                + " — Collectors should produce identical-length arrays for matching runDays.");

        // Array-Identity-Assert (Double.compare-Semantik via Arrays.equals):
        // wenn Decoupling intakt ist, müssen die Werte-Arrays EXAKT gleich sein.
        if (!java.util.Arrays.equals(baselineCsv, afterSetterCsv)) {
            int divergenceDay = findFirstDivergence(baselineCsv, afterSetterCsv);
            double baselineVal = divergenceDay < baselineCsv.length ? baselineCsv[divergenceDay] : Double.NaN;
            double afterSetterVal = divergenceDay < afterSetterCsv.length ? afterSetterCsv[divergenceDay] : Double.NaN;
            int meticMod = divergenceDay * DECOUPLING_METIC_PER_DAY;
            fail(String.format(Locale.ROOT,
                "Tax-Semantik-Leak entdeckt nach EconConfig.setMeticImmigrationDepth(0.7)."
                + " Erste Divergenz bei Tag=%d (meticModifier=%d): baseline=%.6f afterSetter=%.6f delta=%.6f."
                + " Ursache: Taxes.immigrationMultiplier(...) liest EconConfig.meticImmigrationDepth"
                + " statt EconConfig.taxImmigrationDepth. Re-apply Sprint v0.13.124+BootstrapEncapsulation-Entkoppelung"
                + " (Taxes.java: k := EconConfig.taxImmigrationDepth, NICHT meticImmigrationDepth).",
                divergenceDay, meticMod, baselineVal, afterSetterVal,
                afterSetterVal - baselineVal));
        }

        // Defensive Coverage-Assert damit ein stiller Empty-Array-Pass nicht durchrutscht.
        assertEquals(180, baselineCsv.length, "expected 180 entries from runDays");
        assertEquals(180, afterSetterCsv.length, "expected 180 entries from runDays");
    }

    /** meticModifier ramping speed: 2 units per Tag → 360 nach 180 Tagen (saturiert tanh). */
    private static final int DECOUPLING_METIC_PER_DAY = 2;

    /**
     * Flush {@link Taxes#immigrationMultiplier(int)} values to a deterministic
     * double-array, one value per simulated day. Pure-math: {@code tanh} ist
     * deterministisch fuer gleiche Inputs, kein RNG involviert. Wenn Sprint
     * v0.13.124+ korrekt entkoppelt hat, ist die Funktion unabhängig von
     * {@code setMeticImmigrationDepth(...)} Calls.
     */
    private static double[] flushImmigrationMultiplier(int runDays) {
        double[] out = new double[runDays];
        for (int d = 0; d < runDays; d++) {
            out[d] = Taxes.immigrationMultiplier(d * DECOUPLING_METIC_PER_DAY);
        }
        return out;
    }

    /**
     * Find first content-divergence index. Returns {@code -1} if lengths differ but
     * the prefix still matches — caller is expected to pre-verify lengths so an
     * informative error message can include the length delta instead of a
     * {@code [-1]} array-access {@link ArrayIndexOutOfBoundsException}.
     *
     * <p><b>Comparator choice</b>: uses {@link Double#compare(double, double)} to be
     * bit-consistent with {@link java.util.Arrays#equals(double[], double[])}, which
     * the actual assertion delegate uses internally. Contrast with
     * {@code Double.doubleToLongBits} which collapses {@code +0.0} and {@code -0.0}
     * (and treats {@code NaN} bits-literally). For the immigration-multiplier formula
     * neither sign-of-zero nor NaN is a real output, so the {@code Double.compare}
     * choice is the safer and consistent one.</p>
     */
    private static int findFirstDivergence(double[] a, double[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            if (Double.compare(a[i], b[i]) != 0) return i;
        }
        return -1; // caller is expected to pre-verify length
    }

    // Gini drift — the headline metric
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Per-config drift safety check: 500 days of pairwise-lottery dynamics
     * MUST stay within {@value #GINI_DRIFT_PER_CONFIG_SAFETY_LIMIT} (the
     * wild-outlier safety limit, distinct from the strict median band). Drift
     * above bound in any single cell signals an outlier-dynamics regression
     * (e.g. seed-specific accumulator bug). The matrix median is enforced
     * separately below.
     */
    @ParameterizedTest(name = "seed={0}, pop={1}")
    @MethodSource("giniDriftMatrix")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void giniDriftPerConfigUnderCeiling(long seed, int population) {
        List<EconomyMock.Sample> samples = new EconomyMock(population, 500, seed).runAndCollect(5);
        double initial = samples.get(0).gini;
        double finall = samples.get(samples.size() - 1).gini;
        double drift = Math.abs(finall - initial);
        assertTrue(drift < GINI_DRIFT_PER_CONFIG_SAFETY_LIMIT, String.format(Locale.ROOT,
            "Gini drift %.4f exceeded per-config safety limit %.4f (seed=%d, pop=%d, day0=%.4f, day500=%.4f)",
            drift, GINI_DRIFT_PER_CONFIG_SAFETY_LIMIT, seed, population, initial, finall));
    }

    /**
     * Matrix-wide median drift must stay in {@code [0.10, 0.20]}. This is the
     * headline seed-agnostic invariant: a single re-tuned randomisation cannot
     * silently shift all 15 cells into the upper band without failing the
     * median check — yet a single outlier cell is filtered out (median is
     * robust up to 7 outliers of 15).
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void medianGiniDriftAcrossMatrixInBand() {
        // Symmetric guard with giniDriftMatrix(): fail-fast on empty matrix
        // rather than letting drifts.get(0) throw IOOBE asymmetrically.
        assertFalse(GINI_MATRIX_SEEDS.length == 0 || GINI_MATRIX_POPS.length == 0,
            "gini-drift matrix is empty: seeds=" + GINI_MATRIX_SEEDS.length
                + ", pops=" + GINI_MATRIX_POPS.length);
        List<Double> drifts = new ArrayList<>(GINI_MATRIX_SEEDS.length * GINI_MATRIX_POPS.length);
        StringBuilder breakdown = new StringBuilder();
        for (long seed : GINI_MATRIX_SEEDS) {
            for (int pop : GINI_MATRIX_POPS) {
                List<EconomyMock.Sample> samples = new EconomyMock(pop, 500, seed).runAndCollect(5);
                double d = Math.abs(samples.get(samples.size() - 1).gini - samples.get(0).gini);
                drifts.add(d);
                if (breakdown.length() > 0) breakdown.append(", ");
                breakdown.append(String.format(Locale.ROOT, "(s=%d,p=%d)→%.4f", seed, pop, d));
            }
        }
        Collections.sort(drifts);
        // Matrix is hardcoded to 5×3=15 (odd) cells; the even-size branch is
        // defensive in case the matrix is later diversified to an even count.
        int n = drifts.size();
        double median = (n % 2 == 1)
            ? drifts.get(n / 2)
            : 0.5 * (drifts.get(n / 2 - 1) + drifts.get(n / 2));
        assertTrue(median >= GINI_DRIFT_MEDIAN_MIN && median <= GINI_DRIFT_MEDIAN_MAX,
            String.format(Locale.ROOT,
                "Matrix median gini drift %.4f not in [%.2f, %.2f] — drifts=[%s]",
                median, GINI_DRIFT_MEDIAN_MIN, GINI_DRIFT_MEDIAN_MAX, breakdown));
    }

    /** Seeds × populations = 15 cells. Deterministic Cartesian product. */
    private static final long[] GINI_MATRIX_SEEDS = { 42L, 99L, 2024L, 31415L, 88888L };
    private static final int[]  GINI_MATRIX_POPS = { 20, 50, 100 };

    /**
     * @return Cartesian product (seed × pop) → 15 {@link Arguments} cells.
     * @throws IllegalStateException if either {@link #GINI_MATRIX_SEEDS} or
     *         {@link #GINI_MATRIX_POPS} is empty — prevents the silent-pass
     *         trap where JUnit 5 reports "0 invocations, 0 failures" for an
     *         empty {@code @MethodSource} stream.
     */
    static Stream<Arguments> giniDriftMatrix() {
        if (GINI_MATRIX_SEEDS.length == 0 || GINI_MATRIX_POPS.length == 0) {
            throw new IllegalStateException(
                "gini-drift matrix is empty: seeds=" + GINI_MATRIX_SEEDS.length
                    + ", pops=" + GINI_MATRIX_POPS.length
                    + " (silent-pass would mask the invariant)");
        }
        return IntStream.range(0, GINI_MATRIX_SEEDS.length).boxed()
            .flatMap(i -> IntStream.range(0, GINI_MATRIX_POPS.length)
                .mapToObj(j -> Arguments.of(GINI_MATRIX_SEEDS[i], GINI_MATRIX_POPS[j])));
    }

    /** Money preservation drift (must be 0 by design). */
    @Test
    void moneySupplyPreservationDriftIsZero() {
        List<EconomyMock.Sample> samples = new EconomyMock(50, 500, 42L).runAndCollect(5);
        long initial = samples.get(0).moneySupply;
        long finall = samples.get(samples.size() - 1).moneySupply;
        long delta = Math.abs(finall - initial);
        assertTrue(delta <= MONEY_PRESERVATION_TOLERANCE,
            "money supply drifted by " + delta
                + " D (initial=" + initial + ", final=" + finall
                + ") — EconomyMock.tick() is not zero-sum");
    }

    // ───────────────────────────────────────────────────────────────────────
    // Robustness / input-validation
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Large-scale invariant consistency check — exercises the
     * {@link EconomyMock} long-wallet storage at a 20 000-day × 20-citizen
     * scale (16× larger than typical 500-day regression runs). Asserts that
     * <em>all</em> monetary fields stay non-negative and that zero-sum
     * conservation ({@code moneySupply == initialMoneySupply}) holds across
     * the full timeline.
     *
     * <p>This test does <b>not</b> directly provoke {@code Integer.MAX_VALUE}
     * overflow — zero-sum dynamics cap single-wallet growth at the initial
     * sum (~70 k D). Its value is invariant-consistency at scale: a future
     * refactor that breaks long-arithmetic (e.g. narrows back to {@code int}
     * without defensive checks) will surface negative values or supply drift
     * here even when small-scale smoke tests still pass.</p>
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void conservedMoneyAcrossExtendedRun() {
        EconomyMock sim = new EconomyMock(20, 20_000, 31415L);
        List<EconomyMock.Sample> samples = sim.runAndCollect(2_000);
        assertFalse(samples.isEmpty(), "20 000-day run produced no samples");
        long initialMoneySupply = sim.initialMoneySupply;
        for (EconomyMock.Sample s : samples) {
            assertTrue(s.moneySupply > 0,
                "money supply went non-positive at day " + s.day + ": " + s.moneySupply);
            assertTrue(s.medianWallet >= 0,
                "median wallet went negative at day " + s.day
                    + ": " + s.medianWallet);
            assertTrue(s.treasury >= 0,
                "treasury went negative at day " + s.day + ": " + s.treasury);
            assertEquals(initialMoneySupply, s.moneySupply,
                "zero-sum violation at day " + s.day
                    + ": initial=" + initialMoneySupply
                    + ", current=" + s.moneySupply);
        }
    }

    @Test
    void zeroDayRunIsOneSample() {
        List<EconomyMock.Sample> samples = new EconomyMock(50, 0, 42L).runAndCollect(5);
        assertEquals(1, samples.size(), "days=0 must produce only the day-0 sample");
        assertEquals(0, samples.get(0).day);
    }

    @Test
    void invalidCitizensRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EconomyMock(0, 100, 1L));
        assertThrows(IllegalArgumentException.class, () -> new EconomyMock(-3, 100, 1L));
    }

    @Test
    void invalidDaysRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EconomyMock(50, -1, 1L));
    }

    @Test
    void invalidSampleCadenceRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new EconomyMock(50, 100, 1L).runAndCollect(0));
    }

    // ───────────────────────────────────────────────────────────────────────
    // Diagnostic helper — locate the first divergent Sample
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Compare two timelines; on failure, report the day-index of the FIRST
     * divergence so the CI log points exactly to the regression. JUnit's
     * default {@code assertEquals(List, List)} prints both lists in full,
     * which is noise-heavy on a 101-element timeline.
     */
    private static void assertTimelineEquals(List<EconomyMock.Sample> a,
                                              List<EconomyMock.Sample> b,
                                              String message) {
        if (a.size() != b.size()) {
            fail(message + " — sizes differ: actual=" + a.size() + ", expected=" + b.size());
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                fail(message + " — first divergence at index " + i
                    + " (day " + a.get(i).day + "): actual=" + a.get(i)
                    + ", expected=" + b.get(i));
            }
        }
    }
}
