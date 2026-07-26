package vannon.syx.economy.core;

import snake2d.LOG;

/**
 * Zero-allocation ring-buffer event tracer for debugging Interrupter lifecycle,
 * View transitions, Script hooks, and Economy state changes.
 *
 * <p>Opt-in via {@link EconConfig#debugTracing}. Disabled by default.
 * Export buffered events via {@link #dump()} to the game log.
 *
 * <p>Performance characteristics:
 * <ul>
 *   <li>Ring buffer of 8192 pre-allocated slots — no GC pressure after warmup</li>
 *   <li>{@link #every(int)} sampling for high-frequency events (render, update)</li>
 *   <li>Single-threaded — all callbacks run on the game's main thread</li>
 *   <li>Disabled-by-default guard at every call site</li>
 * </ul>
 *
 * <p>Categories:
 * <ul>
 *   <li>{@link #INTR} — Interrupter callbacks (show, hide, hover, click, render, update)</li>
 *   <li>{@link #VIEW} — Game/View-level events (activate, deactivate, save, load)</li>
 *   <li>{@link #SCRP} — Script lifecycle (init, createInstance)</li>
 *   <li>{@link #ECON} — Economy state changes (toggle, config, stage)</li>
 *   <li>{@link #SYS}  — System/infra (startup, shutdown)</li>
 * </ul>
 */
public final class DebugTracer {

    // ─── Categories ────────────────────────────────────────────────────────
    public static final byte INTR = 0;
    public static final byte VIEW = 1;
    public static final byte SCRP = 2;
    public static final byte ECON = 3;
    public static final byte SYS  = 4;

    // ─── Ring buffer ───────────────────────────────────────────────────────
    private static final int CAP = 8192;
    private static final long[]   tickBuf = new long[CAP];
    private static final byte[]   catBuf  = new byte[CAP];
    private static final String[] msgBuf  = new String[CAP];
    private static int pos;
    private static int count;

    // ─── Frame counter for sampling ────────────────────────────────────────
    private static long frame;

    private DebugTracer() {}

    // ─── Public API ────────────────────────────────────────────────────────

    /** Guard: all trace() calls are no-ops unless this returns true. */
    public static boolean on() {
        return EconConfig.debugTracing;
    }

    /**
     * Increment the frame counter. Call once per {@code InstanceScript.update()}.
     * Used by {@link #every(int)} for rate-limited sampling.
     */
    public static void tick() {
        if (EconConfig.debugTracing) frame++;
    }

    /**
     * Returns true every Nth frame. Use for sampling high-frequency events
     * like render() or update() without filling the buffer instantly.
     */
    public static boolean every(int n) {
        return EconConfig.debugTracing && frame % n == 0;
    }

    /**
     * Trace a single event into the ring buffer.
     * @param cat  one of {@link #INTR}, {@link #VIEW}, {@link #SCRP}, {@link #ECON}, {@link #SYS}
     * @param msg  human-readable description (keep short — no allocations in caller)
     */
    public static void trace(byte cat, String msg) {
        if (!EconConfig.debugTracing) return;
        int idx = pos & (CAP - 1);  // bitmask safe for negative overflow, CAP is power of 2
        tickBuf[idx] = frame;
        catBuf[idx]  = cat;
        msgBuf[idx]  = msg;
        pos++;
        if (count < CAP) count++;
        // Sprint 6.4 — Unified CSV-Bridge: parallel-write to DebugCsv für
        // konsolidierte Debug-Analyse. Additive only, kein Refactor.
        LoggingAdapter.csvTrace(
                cat == INTR ? LoggingAdapter.Category.INTR : LoggingAdapter.Category.TRACE,
                LoggingAdapter.Subsystem.SEAM,
                LoggingAdapter.Severity.DEBUG,
                "trace_" + catName(cat),
                String.valueOf(frame),
                msg);
    }

    /**
     * Trace only every Nth frame. Convenience combining {@link #every(int)} and {@link #trace(byte, String)}.
     * <p><b>Performance note:</b> the {@code msg} argument is evaluated BEFORE the sampling check.
     * For hot-path callers (render, update), wrap with {@code if (DebugTracer.on())} to avoid
     * string allocation when tracing is disabled.</p>
     */
    public static void traceEvery(int n, byte cat, String msg) {
        if (every(n)) trace(cat, msg);
    }

    /**
     * Dump all buffered events. Writes to:<br>
     * 1. EventLog (visible in-game via BooksTab event chronicle).
     * 2. Diagnostics file (persistent on disk).
     * 3. System.out (terminal / log file).
     * <p>Events are oldest-first. Buffer is NOT cleared.</p>
     * <p>If {@link EconConfig#debugTracing} is off, logs a hint to enable it first.</p>
     */
    public static void dump() {
        // Always provide in-game feedback via EventLog
        if (!EconConfig.debugTracing) {
            String hint = "Trace AUS — erst Debug-Tracing im Staat-Fenster aktivieren";
            EventLog.log("TRACE", hint);
            LOG.ln("[TRACE] " + hint);
            System.out.println("[TRACE] " + hint);
            return;
        }
        if (count == 0) {
            String msg = "Trace-Puffer leer — noch keine Events aufgezeichnet";
            EventLog.log("TRACE", msg);
            LOG.ln("[TRACE] " + msg);
            System.out.println("[TRACE] " + msg);
            return;
        }
        int start = count < CAP ? 0 : (pos & (CAP - 1));
        StringBuilder sb = new StringBuilder(count * 80);
        sb.append("[TRACE] === ").append(count).append(" events (frame=").append(frame).append(") ===\n");
        for (int i = 0; i < count; i++) {
            int idx = (start + i) & (CAP - 1);
            sb.append("[TRACE] ").append(catName(catBuf[idx]))
              .append(" f=").append(tickBuf[idx]).append(' ').append(msgBuf[idx]).append('\n');
        }
        sb.append("[TRACE] === END ===\n");
        String output = sb.toString();

        // 1. In-game: EventLog summary (visible in BooksTab)
        EventLog.log("TRACE", count + " Events gedumpt (frame=" + frame + ")");

        // 2. Persistent file
        String path = DiagnosticExporter.diagnosticDirectory()
                + java.io.File.separator + "trace_dump_" + System.nanoTime() + ".log";
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of(DiagnosticExporter.diagnosticDirectory()));
            java.nio.file.Files.writeString(java.nio.file.Path.of(path), output);
            EventLog.log("TRACE", "Geschrieben: " + path);
        } catch (java.io.IOException e) {
            EventLog.log("TRACE", "Datei-Schreibfehler: " + e.getMessage());
        }

        // 3. stdout (terminal / game log)
        LOG.ln("[TRACE] dumped " + count + " events to " + path);
        System.out.print(output);
    }

    /** Number of events currently in the buffer. */
    public static int size() { return count; }

    /**
     * Dump all buffered events to a file. Useful for crash-recovery:
     * the buffer survives in-memory and can be written to disk via
     * an {@code UncaughtExceptionHandler}.
     * @param absPath absolute path to the output file
     */
    public static void dumpToFile(String absPath) {
        if (count == 0) return;
        int start = count < CAP ? 0 : (pos & (CAP - 1));
        StringBuilder sb = new StringBuilder(count * 80);
        sb.append("=== ").append(count).append(" events (frame=").append(frame).append(") ===\n");
        for (int i = 0; i < count; i++) {
            int idx = (start + i) & (CAP - 1);
            sb.append(catName(catBuf[idx])).append(" f=").append(tickBuf[idx]).append(' ').append(msgBuf[idx]).append('\n');
        }
        sb.append("=== END ===\n");
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(absPath), sb.toString());
        } catch (java.io.IOException e) {
            LOG.ln("[TRACE] dumpToFile failed: " + e.getMessage());
        }
    }

    // ─── Internal ──────────────────────────────────────────────────────────

    private static String catName(byte cat) {
        if (cat >= 0 && cat < CAT_NAMES.length) return CAT_NAMES[cat];
        return "???";
    }
    private static final String[] CAT_NAMES = {"INTR", "VIEW", "SCRP", "ECON", "SYS "};
}
