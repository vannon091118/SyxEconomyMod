package vannon.syx.economy.benchmark;

import java.io.IOException;
import java.nio.file.Paths;
import snake2d.LOG;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.gui.GuiSection;
import util.gui.misc.GBox;
import view.keyboard.KEYS;
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomySim;

/**
 * Singleton-style SCRIPT_INSTANCE driver for {@link SyxBenchmarkHarness}.
 *
 * <p>Each engine-tick the instance:</p>
 * <ol>
 *   <li>Reads {@link EconomySim#ticks()} and divides by {@link EconConfig#DEFAULT_TICKS_PER_DAY}</li>
 *   <li>If a new simulated day has elapsed (and at least {@code config.logEveryNDays} days have), samples metrics</li>
 *   <li>Appends a CSV row via {@link BenchmarkCsvWriter#append}</li>
 *   <li>When the simulated day reaches {@code config.runDays}, closes the CSV and (if headless) calls {@code System.exit(0)}.</li>
 * </ol>
 *
 * <p>The {@link EconomyMetrics} dependency is injected so the entire read-sample-write-cutoff
 * path can be exercised in {@code mvn test --batch-mode} without an Engine boot.</p>
 */
final class BenchmarkInstance implements script.SCRIPT.SCRIPT_INSTANCE {

    private final BenchConfig config;
    private final BenchmarkCsvWriter csv;
    private final EconomyMetrics metrics;
    private int lastLoggedDay = -1;
    private boolean cutoffDone = false;

    public BenchmarkInstance(BenchConfig config, BenchmarkCsvWriter csv, EconomyMetrics metrics) {
        this.config = config;
        this.csv = csv;
        this.metrics = metrics;
    }

    /**
     * Production factory — opens the configured CSV and wires
     * {@link EconomyMetrics.FromSim}.
     */
    public static BenchmarkInstance production(BenchConfig config) {
        BenchmarkCsvWriter csv;
        try {
            csv = new BenchmarkCsvWriter(Paths.get(config.csvPath));
        } catch (IOException e) {
            throw new RuntimeException("[BENCHMARK] cannot open " + config.csvPath, e);
        }
        return new BenchmarkInstance(config, csv, new EconomyMetrics.FromSim());
    }

    @Override
    public void update(double deltaSeconds) {
        if (cutoffDone) return;
        EconomySim sim = EconomySim.active();
        if (sim == null) return;
        EngineMirror m = EngineMirror.api();
        if (m == null || !m.isFullyAvailable()) return;

        int currentDay = (int) (((long) sim.ticks()) / (long) EconConfig.DEFAULT_TICKS_PER_DAY);
        sampleOrSkip(currentDay);
    }

    /**
     * Drives one row-write decision for the given simulated day. Same logic for
     * production update() and test seam — keeps both paths verified in {@code mvn test}
     * without engine boot. {@code package-private} so the test class can call it
     * directly with a synthetic day.
     */
    void sampleOrSkip(int currentDay) {
        if (cutoffDone) return;
        if (currentDay <= lastLoggedDay) return;
        boolean firstEver = (lastLoggedDay < 0);
        boolean dueForLog = firstEver
            || (currentDay - lastLoggedDay) >= config.logEveryNDays;

        // Row write fires only on dueForLog ticks; cutoff fires whenever
        // currentDay >= runDays regardless of whether this tick logged a row
        // (a throttled day still terminates the run when runDays is reached).
        if (dueForLog) {
            try {
                csv.append(currentDay,
                    metrics.gini(),
                    metrics.moneySupply(),
                    metrics.medianPrice());
                // Only advance on successful write — otherwise a write-failure streak
                // would silently degrade logEveryNDays throttling into per-tick writes.
                lastLoggedDay = currentDay;
            } catch (IOException e) {
                LOG.err("[BENCHMARK] CSV write failed: " + e.getMessage());
                // Continue to cutoff check — CSV failure must not leak the run open.
            }
        }

        if (currentDay >= config.runDays) {
            try { csv.close(); } catch (IOException ignored) {}
            cutoffDone = true;
            LOG.ln("[BENCHMARK] Reached runDays=" + config.runDays
                + " — CSV closed (" + csv.rows() + " rows).");
            if (config.headless) {
                System.out.println("[BENCHMARK] Headless cutoff → exit(0).");
                System.exit(0);
            }
        }
    }

    // ── No-op save/load (engine schema-versioning is owned by EconomySim) ──

    @Override
    public void save(FilePutter file) {
        // benchmark state is reconstructed from BenchConfig on every load — no per-save data
    }

    @Override
    public void load(FileGetter file) throws IOException {
        // see save()
    }

    // ── Hooks we don't use (default implementations match MainScript's InstanceScript) ──

    @Override
    public void render(Renderer renderer, float deltaSeconds) { /* no HUD overlay */ }

    @Override
    public void keyPush(KEYS keys) { /* no hotkeys */ }

    @Override
    public void hoverTimer(double mouseTimer, GBox text) { /* quiet */ }

    @Override
    public void mouseClick(MButt button) { /* no UI */ }

    @Override
    public void hover(COORDINATE mCoo, boolean mouseHasMoved) { /* no UI */ }

    @Override
    public boolean handleBrokenSavedState() {
        return true;
    }

    // ── Test seam: simulate a day-step without engine ──────────────────────────

    /**
     * Test helper (package-private). Drives the same sample-write-cutoff logic as
     * {@link #update(double)} but with an explicit currentDay. Used by
     * {@code SyxBenchmarkHarnessBatchTest} to verify the full loop "ohne Engine-Boot".
     */
    int simulateDay(int currentDay) throws IOException {
        // Return -1 ONLY when cutoff already fired on a previous call.
        // On the call that triggers cutoff, the row was written and we still
        // return the row count so the caller's loop can verify it.
        if (cutoffDone) return -1;
        sampleOrSkip(currentDay);
        return this.csv.rows();
    }

    int rowsWritten() { return csv.rows(); }
    boolean isCutoffDone() { return cutoffDone; }
    int lastLoggedDay() { return lastLoggedDay; }
}
