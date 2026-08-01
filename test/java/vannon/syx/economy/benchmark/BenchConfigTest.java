package vannon.syx.economy.benchmark;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Pure-JVM test for {@link BenchConfig}. No Engine coupling.
 * Drives {@code mvn test --batch-mode} without boooting Songs-of-Syx.
 */
class BenchConfigTest {

    @Test
    void defaults_haveSeed42_runDays180() {
        BenchConfig d = BenchConfig.defaults();
        assertEquals(42L, d.seed);
        assertEquals(180, d.runDays);
        assertEquals(1, d.logEveryNDays);
        assertTrue(d.csvPath.endsWith(".csv"));
        assertTrue(d.snapshotPath.endsWith(".save"));
        assertTrue(d.headless);
    }

    @Test
    void loadOrDefault_returnsDefaultsWhenNoProperties() {
        BenchConfig c = BenchConfig.loadOrDefault();
        assertEquals(BenchConfig.defaults(), c);
    }

    @Test
    void loadOrDefault_overridesFromSystemProperties() {
        // Snapshot all bench.* keys via String to avoid Long.getLong() autounbox-NPE.
        String[] keys = {"bench.seed", "bench.runDays", "bench.csvPath", "bench.headless"};
        String[] original = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            original[i] = System.getProperty(keys[i]);
        }
        try {
            System.setProperty("bench.seed", "1337");
            System.setProperty("bench.runDays", "60");
            System.setProperty("bench.csvPath", "/tmp/x.csv");
            System.setProperty("bench.headless", "false");

            BenchConfig c = BenchConfig.loadOrDefault();
            assertEquals(1337L, c.seed);
            assertEquals(60, c.runDays);
            assertEquals("/tmp/x.csv", c.csvPath);
            assertFalse(c.headless);
        } finally {
            restoreAll(keys, original);
        }
    }

    @Test
    void loadOrDefault_fallsBackOnBogusNumbers() {
        String orig = System.getProperty("bench.seed");
        try {
            System.setProperty("bench.seed", "not-a-number");
            BenchConfig c = BenchConfig.loadOrDefault();
            assertEquals(BenchConfig.defaults().seed, c.seed);
        } finally {
            restore("bench.seed", orig);
        }
    }

    @Test
    void loadEveryNDays_clampsToMinimumOne() {
        String orig = System.getProperty("bench.logEveryNDays");
        try {
            System.setProperty("bench.logEveryNDays", "0");
            BenchConfig c = BenchConfig.loadOrDefault();
            assertEquals(1, c.logEveryNDays, "logEveryNDays=0 must clamp to 1");
        } finally {
            restore("bench.logEveryNDays", orig);
        }
    }

    @Test
    void toString_includesAllFields() {
        String s = BenchConfig.defaults().toString();
        assertTrue(s.contains("runDays=180"));
        assertTrue(s.contains("seed=42"));
        assertTrue(s.contains("headless=true"));
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }

    private static void restoreAll(String[] keys, String[] original) {
        for (int i = 0; i < keys.length; i++) {
            restore(keys[i], original[i]);
        }
    }
}
