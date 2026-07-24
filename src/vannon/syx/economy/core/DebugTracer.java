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
    }

    /**
     * Trace only every Nth frame. Convenience combining {@link #every(int)} and {@link #trace(byte, String)}.
     */
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
     * Dump all buffered events to the game log via {@code LOG.ln()}.
     * Events are oldest-first. Buffer is NOT cleared — old events are
     * overwritten by new ones in ring-buffer fashion.
     */
    public static void dump() {
        if (count == 0) {
            LOG.ln("[TRACE] buffer empty");
            return;
        }
        int start = count < CAP ? 0 : (pos & (CAP - 1));
        LOG.ln("[TRACE] === " + count + " events (frame=" + frame + ") ===");
        for (int i = 0; i < count; i++) {
            int idx = (start + i) & (CAP - 1);
            LOG.ln("[TRACE] " + catName(catBuf[idx]) + " f=" + tickBuf[idx] + " " + msgBuf[idx]);
        }
        LOG.ln("[TRACE] === END ===");
    }

    /** Number of events currently in the buffer. */
    public static int size() { return count; }

    // ─── Internal ──────────────────────────────────────────────────────────

    private static String catName(byte cat) {
        if (cat >= 0 && cat < CAT_NAMES.length) return CAT_NAMES[cat];
        return "???";
    }
    private static final String[] CAT_NAMES = {"INTR", "VIEW", "SCRP", "ECON", "SYS "};
}
