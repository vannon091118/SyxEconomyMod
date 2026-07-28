package vannon.syx.economy.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Unified CSV-based debug logging.
 * Sprint 6.4 — additive only, replaces no existing logger.
 *
 * <p>Single canonical schema:
 * <pre>
 *   tick;day;category;subsystem;severity;key;value;note
 * </pre>
 *
 * <p>This class is the SSOT for the unified debug-friendly format. Adopters
 * (EventLog, DebugTracer, DiagnosticExporter, custom debug hooks) call into
 * {@link #write} to publish entries. The file lives at
 * {@code ~/.local/share/songsofsyx/mods/SyxEconomyMod/diagnostics/debug.csv}
 * — same directory as existing diagnostic CSVs, so users can load it
 * alongside {@code rebalance_*.csv} in pandas.</p>
 *
 * <p>Opt-in: only writes when {@link EconConfig#debugTracing} is true, or
 * when {@link #forceEnabled()} flips it on explicitly (used by adapter init
 * tests). Default is disabled to keep IO off in release builds.</p>
 *
 * <p>Thread-safety: writes are dispatched through a single-thread daemon
 * executor, so callers may log from any thread without contention. The
 * writer flushes and closes after each push, so a crash mid-game yields at
 * most one truncated row, never a corrupt header.</p>
 */
public final class DebugCsv {

    /** Canonical header — DO NOT change without bumping schema version. */
    public static final String[] HEADER = {
            "tick", "day", "category", "subsystem", "severity",
            "key", "value", "note"
    };

    /** Schema version — printed in note on first row for parser migrations. */
    public static final String SCHEMA_VERSION = "v1";

    private static final Path DEBUG_CSV = Paths.get(
            System.getProperty("user.home"),
            ".local", "share", "songsofsyx", "mods", "SyxEconomyMod",
            "diagnostics", "debug.csv");

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "syx-debug-csv");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean headerWritten = false;
    private static volatile boolean forceEnabled = false;

    private DebugCsv() {}

    /** Test/CI hook: enable CSV-writing regardless of EconConfig debugTracing. */
    public static void forceEnabled() { forceEnabled = true; }

    /** Test/CI hook: re-disable CSV-writing. */
    public static void disableForce() { forceEnabled = false; }

    /**
     * Returns true if this CSV sink should consume an entry now.
     * Combines the global debugTracing flag with the override hook.
     */
    public static boolean isOn() {
        return forceEnabled || EconConfig.debugTracing;
    }

    /**
     * Schema-driven row formatter. Any changes to the canonical schema must
     * also update HEADER. Each argument is escaped per CSV rules (commas,
     * quotes, newlines wrapped in quotes; quotes doubled inside).
     *
     * @param tick      engine tick (long)
     * @param day       in-game day (fractional OK)
     * @param category  family label (TRACE, REBALANCE, ADAPTER, ...)
     * @param subsystem finer bucket (FIRM, FOOD, ECON, ROOM, SEAM, ...)
     * @param severity  DEBUG | INFO | WARN | ERROR | FATAL
     * @param key       short identifier for tooling
     * @param value     arbitrary numeric/text payload
     * @param note      free-form note (commas allowed)
     */
    public static String formatRow(long tick, double day, String category,
                                    String subsystem, String severity,
                                    String key, String value, String note) {
        StringBuilder s = new StringBuilder(160);
        s.append(tick).append(';')
         .append(String.format(java.util.Locale.ROOT, "%.3f", day)).append(';')
         .append(esc(category)).append(';')
         .append(esc(subsystem)).append(';')
         .append(esc(severity)).append(';')
         .append(esc(key)).append(';')
         .append(value == null ? "" : value).append(';')
         .append(esc(note));
        return s.toString();
    }

    /**
     * Submit a row to the CSV writer. No-op when {@link #isOn()} returns false.
     * Returns immediately; actual write happens on background thread.
     */
    public static void write(long tick, double day, String category,
                              String subsystem, String severity,
                              String key, String value, String note) {
        if (!isOn()) return;
        IO.submit(() -> doWrite(formatRow(tick, day, category, subsystem,
                severity, key, value, note)));
    }

    private static void doWrite(String row) {
        try {
            ensureDir();
            synchronized (DebugCsv.class) {
                if (!headerWritten) {
                    Files.writeString(DEBUG_CSV, String.join(";", HEADER) + "\n",
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    headerWritten = true;
                }
            }
            Files.writeString(DEBUG_CSV, row + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Best-effort logging — fall back to stderr once.
            System.err.println("[ECON] DebugCsv write failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void ensureDir() throws IOException {
        Path parent = DEBUG_CSV.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    /** CSV-escape: comma / quote / newline trigger quote-wrap, quotes are doubled. */
    public static String esc(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.indexOf(';') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /**
     * Public test-helper for the canonical schema: write a row synchronously
     * and return the formatted string. Used by tests + manual invocations.
     * Lock-acquired on the class object so the test-path and the async
     * write-path do not race on the same {@code headerWritten} flag.
     */
    public static String writeNow(long tick, double day, String category,
                                   String subsystem, String severity,
                                   String key, String value, String note) {
        String row = formatRow(tick, day, category, subsystem,
                severity, key, value, note);
        try (BufferedWriter w = Files.newBufferedWriter(DEBUG_CSV,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            synchronized (DebugCsv.class) {
                if (!headerWritten) {
                    w.write(String.join(";", HEADER));
                    w.newLine();
                    headerWritten = true;
                }
            }
            w.write(row);
            w.newLine();
        } catch (IOException e) {
            // ignore — test path; real write path also tolerates
        }
        return row;
    }

    /** Diagnostic accessor for tests. */
    public static String debugCsvPath() {
        return DEBUG_CSV.toString();
    }

    /** Test-only: reset header flag so a new file can be created. */
    public static void resetForTests() {
        synchronized (DebugCsv.class) {
            headerWritten = false;
        }
    }
}
