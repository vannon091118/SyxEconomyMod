package vannon.syx.economy.benchmark;

/**
 * Pure-Java config holder for {@link SyxBenchmarkHarness}.
 *
 * <p>Loaded from JVM system properties so the harness is fully driven from the command line
 * (e.g. {@code -Dbench.enabled=true -Dbench.seed=42 -Dbench.runDays=180}). Defaults match
 * Sprint v0.13.108 benchmark conventions: seed=42, runDays=180 (half-year), log every 1 day,
 * CSV to {@code ./bench-baseline-out.csv}, snapshot reference to {@code ./bench-baseline.save},
 * {@code headless=true} (exit on cutoff).</p>
 *
 * <p>Has <b>no dependency</b> on vanilla Songs-of-Syx APIs and can be unit-tested in
 * absolute isolation (pure JVM, no Engine boot).</p>
 */
public final class BenchConfig {

    public final long seed;
    public final int runDays;
    public final int logEveryNDays;
    public final String csvPath;
    public final String snapshotPath;
    public final boolean headless;

    public BenchConfig(long seed, int runDays, int logEveryNDays,
                       String csvPath, String snapshotPath, boolean headless) {
        this.seed = seed;
        this.runDays = runDays;
        this.logEveryNDays = logEveryNDays;
        this.csvPath = csvPath;
        this.snapshotPath = snapshotPath;
        this.headless = headless;
    }

    /** Library defaults. Kept stable across runs so committed expected CSVs are reproducible. */
    public static BenchConfig defaults() {
        return new BenchConfig(42L, 180, 1,
            "./bench-baseline-out.csv",
            "./bench-baseline.save",
            true);
    }

    /**
     * Read from JVM system properties with safe fall-through to {@link #defaults()}.
     * Bogus inputs (e.g. {@code -Dbench.seed=hello}) silently fall back rather than crash.
     */
    public static BenchConfig loadOrDefault() {
        BenchConfig d = defaults();
        long seed = parseLong(System.getProperty("bench.seed"), d.seed);
        int runDays = parseInt(System.getProperty("bench.runDays"), d.runDays);
        int logEveryNDays = Math.max(1,
            parseInt(System.getProperty("bench.logEveryNDays"), d.logEveryNDays));
        String csvPath = System.getProperty("bench.csvPath", d.csvPath);
        String snapshotPath = System.getProperty("bench.snapshotPath", d.snapshotPath);
        boolean headless = !"false".equalsIgnoreCase(System.getProperty("bench.headless", "true"));
        return new BenchConfig(seed, runDays, logEveryNDays, csvPath, snapshotPath, headless);
    }

    private static long parseLong(String s, long fallback) {
        if (s == null) return fallback;
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static int parseInt(String s, int fallback) {
        if (s == null) return fallback;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BenchConfig)) return false;
        BenchConfig b = (BenchConfig) o;
        return this.seed == b.seed
            && this.runDays == b.runDays
            && this.logEveryNDays == b.logEveryNDays
            && this.headless == b.headless
            && this.csvPath.equals(b.csvPath)
            && this.snapshotPath.equals(b.snapshotPath);
    }

    @Override
    public int hashCode() {
        int h = Long.hashCode(this.seed);
        h = 31 * h + this.runDays;
        h = 31 * h + this.logEveryNDays;
        h = 31 * h + this.csvPath.hashCode();
        h = 31 * h + this.snapshotPath.hashCode();
        h = 31 * h + (this.headless ? 1 : 0);
        return h;
    }

    @Override
    public String toString() {
        return "BenchConfig{seed=" + seed + ", runDays=" + runDays
            + ", logEveryNDays=" + logEveryNDays
            + ", csvPath=" + csvPath + ", snapshotPath=" + snapshotPath
            + ", headless=" + headless + "}";
    }
}
