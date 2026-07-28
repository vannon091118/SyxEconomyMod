package vannon.syx.economy.core;

import game.time.TIME;

/**
 * LoggingAdapter — bridges the existing loggers (EventLog, DebugTracer,
 * DiagnosticExporter) into the unified CSV debug format.
 *
 * <p>Sprint 6.4 — additive only. Each existing logger keeps its own output
 * path (in-game event log, ring-buffer, day-level rebalance CSVs). This
 * adapter adds a parallel write into {@link DebugCsv} so a user can open
 * <em>one</em> CSV file and see all debug events aligned by tick.</p>
 *
 * <p>Usage from existing loggers:
 * <pre>{@code
 *   LoggingAdapter.csvTrace("ADAPTER", "seam", "WARN",
 *       "init_failed", cause.getMessage(), note);
 * }</pre>
 *
 * <p>Tick/day are sampled once per call to avoid duplicate reads. The
 * adapter never throws — it forwards everything to {@link DebugCsv#write}
 * which itself is best-effort.</p>
 */
public final class LoggingAdapter {

    private LoggingAdapter() {}

    /**
     * Read current engine tick. Returns 0 if engine is not initialised yet.
     * Provides a safe path for adapters that log before EconomySim ticks.
     * Uses EconomySim.active().ticks() since this codebase does not expose
     * a snake2d.CORE.tick() method (the engine tick-counter is private to
     * the EconomySim kernel).
     */
    public static long currentTick() {
        try {
            EconomySim sim = EconomySim.active();
            if (sim == null) return 0L;
            return (long) sim.ticks();
        } catch (Throwable t) {
            return 0L;
        }
    }

    /**
     * Read current game-day (fractional). Returns 0.0 if engine is not
     * initialised yet. Defaults to 300 ticks-per-day (Songs of Syx V71)
     * when {@link game.time.TIME} is not yet bootstrapped.
     */
    public static double currentDay() {
        try {
            long t = currentTick();
            long spd;
            try {
                spd = (long) TIME.secondsPerDay();
            } catch (Throwable t2) {
                spd = 300L; // Songs of Syx V71 default
            }
            if (spd <= 0) spd = 300L;
            return (double) t / (double) spd;
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /**
     * Convenience wrapper for {@link DebugCsv#write} that pulls tick + day
     * from the engine automatically. Safe to call from any thread.
     */
    public static void csvTrace(String category, String subsystem,
                                 String severity, String key,
                                 String value, String note) {
        try {
            DebugCsv.write(
                    currentTick(),
                    currentDay(),
                    category,
                    subsystem,
                    severity,
                    key,
                    value == null ? "" : value,
                    note == null ? "" : note);
        } catch (Throwable t) {
            // Logging must NEVER crash the caller.
        }
    }

    /**
     * Standard category constants — adopters should prefer these over free
     * strings so downstream pandas filters stay consistent.
     */
    public static final class Category {
        public static final String TRACE = "TRACE";
        public static final String REBALANCE = "REBALANCE";
        public static final String ADAPTER = "ADAPTER";
        public static final String SNAPSHOT = "SNAPSHOT";
        public static final String INTR = "INTR";
        public static final String DIAG = "DIAG";
        public static final String SYSTEM = "SYSTEM";
        private Category() {}
    }

    public static final class Subsystem {
        public static final String FIRM = "FIRM";
        public static final String FOOD = "FOOD";
        public static final String ECON = "ECON";
        public static final String ROOM = "ROOM";
        public static final String SEAM = "SEAM";
        public static final String WAGE = "WAGE";
        public static final String HOUSING = "HOUSING";
        private Subsystem() {}
    }

    public static final class Severity {
        public static final String DEBUG = "DEBUG";
        public static final String INFO = "INFO";
        public static final String WARN = "WARN";
        public static final String ERROR = "ERROR";
        public static final String FATAL = "FATAL";
        private Severity() {}
    }
}
