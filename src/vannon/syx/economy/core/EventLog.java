package vannon.syx.economy.core;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import snake2d.util.rnd.RND;

public class EventLog {
    private static PrintWriter writer;
    private static final double SAMPLE_RATE = 0.10; // 10% covering log
    private static final int MAX_RECENT_EVENTS = 100;
    private static final List<EventEntry> recentEvents = Collections.synchronizedList(new ArrayList<>());
    private static final java.util.Map<String, Long> lastLogTimes = java.util.Collections.synchronizedMap(new java.util.HashMap<>());
    private static final long TREND_COOLDOWN_MS = 300_000L; // 5 minutes
    
    public static final class EventEntry {
        public final String category;
        public final String message;
        public final String timestamp;

        public EventEntry(String category, String message, String timestamp) {
            this.category = category;
            this.message = message;
            this.timestamp = timestamp;
        }
    }

    public static void init() {
        try {
            // Writes to the game's working directory
            writer = new PrintWriter(new BufferedWriter(new FileWriter("economy_events.log", true)));
            log("SYSTEM", "EventLog initialized. Sampling rate for high-frequency events: " + (int)(SAMPLE_RATE * 100) + "%");
        } catch (IOException e) {
            System.err.println("[ECON] EventLog write failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
    
    public static void log(String category, String message) {
        synchronized (recentEvents) {
            if ("TREND".equals(category)) {
                String key = category + ":" + message;
                long now = System.currentTimeMillis();
                Long last = lastLogTimes.get(key);
                if (last != null && (now - last) < TREND_COOLDOWN_MS) {
                    return;
                }
                lastLogTimes.put(key, now);
            }
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            EventEntry entry = new EventEntry(category, message, timestamp);
            
            recentEvents.add(entry);
            if (recentEvents.size() > MAX_RECENT_EVENTS) {
                recentEvents.remove(0);
            }

            if (writer != null && EconConfig.debugPriceLogging) {
                String fullTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                writer.println("[" + fullTime + "] [" + category + "] " + message);
                writer.flush();
            }
            // Sprint 6.4 — Unified CSV-Bridge: parallel-write to DebugCsv.
            // Additive only, Original-EventLog-Output bleibt bestehen.
            LoggingAdapter.csvTrace(
                    "SYSTEM".equals(category) ? LoggingAdapter.Category.SYSTEM : category,
                    LoggingAdapter.Subsystem.ECON,
                    LoggingAdapter.Severity.INFO,
                    "eventlog_" + category,
                    "1",
                    message);
        }
    }
    
    public static void logSampled(String category, String message) {
        if (RND.rFloat() <= SAMPLE_RATE) {
            log(category, message);
        }
    }

    public static List<EventEntry> getRecentEvents() {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents);
        }
    }

    public static void clearRecentEvents() {
        recentEvents.clear();
        lastLogTimes.clear();
    }

    public static void close() {
        if (writer != null) {
            log("SYSTEM", "EventLog closed.");
            writer.close();
            writer = null;
        }
    }
}
