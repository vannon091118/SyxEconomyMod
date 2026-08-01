package vannon.syx.economy.benchmark;

import snake2d.LOG;
import snake2d.util.rnd.RND;
import script.SCRIPT;
import util.info.INFO;

/**
 * SCRIPT entry point for the in-game benchmark driver.
 *
 * <p>Lives in its own SCRIPT-class so it does not collide with the production
 * {@code MainScript} — Songs-of-Syx auto-loads every {@code script.SCRIPT} implementation
 * in the JAR, and {@link #forceInit()} here gates injection behind an explicit JVM flag
 * so a regular player never accidentally boots the harness.</p>
 *
 * <p>Lifecycle:</p>
 * <ol>
 *   <li>{@link #forceInit()} — JVM-flag gate. {@code -Dbench.enabled=true} activates.</li>
 *   <li>{@link #initBeforeGameCreated()} — loads {@link BenchConfig}, pins
 *       {@link RND#setSeed(long)} so worldgen is deterministic.</li>
 *   <li>{@link #createInstance()} — returns a {@link BenchmarkInstance} that the engine
 *       ticks every frame.</li>
 *   <li>BenchmarkInstance.update() samples the configured cadence, writes the CSV,
 *       and {@code System.exit(0)} when {@code runDays} is reached.</li>
 * </ol>
 *
 * <p>The {@code save} / {@code load} methods on both this class and the instance are
 * intentionally empty: benchmark state is reconstructed from {@link BenchConfig} on every
 * load (cheap and deterministic) and we deliberately keep Save chunks owned by
 * {@link vannon.syx.economy.core.EconomySim} only — this avoids version-skew if the
 * save format changes later.</p>
 */
public final class SyxBenchmarkHarness implements SCRIPT {

    private final INFO info = new INFO(
        "Syx Benchmark Harness",
        "In-game deterministic benchmark driver. Activated by -Dbench.enabled=true."
    );

    private BenchConfig config;

    @Override
    public CharSequence name() { return this.info.name; }

    @Override
    public CharSequence desc() { return this.info.desc; }

    @Override
    public boolean isSelectable() {
        return true;
    }

    /**
     * Gate. Defaults to {@code false} so vanilla players never see it.
     * @return true iff JVM was launched with {@code -Dbench.enabled=true}
     *         AND a {@code bench-config.json} or relevant cli flag was set.
     */
    @Override
    public boolean forceInit() {
        return Boolean.parseBoolean(System.getProperty("bench.enabled", "false"));
    }

    @Override
    public void initBeforeGameCreated() {
        this.config = BenchConfig.loadOrDefault();
        // Cast to int: snake2d.util.rnd.RND only exposes setSeed(int) in V71.44.
        // Default seed 42 fits; values > Integer.MAX_VALUE suffer precision loss —
        // documented in tools/bench-baseline-snapshot.md as a known limiter.
        RND.setSeed((int) this.config.seed);
        LOG.ln("[BENCHMARK] Harness active — " + this.config);
        System.out.println("[BENCHMARK] seed=" + this.config.seed
            + " runDays=" + this.config.runDays
            + " csv=" + this.config.csvPath);
    }

    @Override
    public void initBeforeGameInited() {
        // no extra sub-system registration — EconomySim active hooks are owned by MainScript
    }

    @Override
    public SCRIPT_INSTANCE createInstance() {
        if (this.config == null) {
            // initBeforeGameCreated may not have run yet under edge load-orders —
            // defensive default keeps the harness idempotent.
            this.config = BenchConfig.loadOrDefault();
        }
        return BenchmarkInstance.production(this.config);
    }

    // NOTE: handleBrokenSavedState() lives on SCRIPT_INSTANCE, not on SCRIPT, so we
    // override it only in BenchmarkInstance — keeps SCRIPT-class minimal.
}
