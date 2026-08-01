package vannon.syx.economy.benchmark;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end driver test for {@link SyxBenchmarkHarness}. Verifies the full
 * <b>read-sample → write → cutoff</b> loop using {@link BenchmarkInstance#simulateDay(int)}
 * as a test seam so {@code mvn test --batch-mode} runs WITHOUT booting the
 * Songs-of-Syx engine.
 *
 * <p>This pattern matches the existing {@code EconomySimMockitoTest} split: pure
 * inheritance-free Mockito-free logic covered without mocks, mocking reserved
 * for engine-coupled branches.</p>
 */
class SyxBenchmarkHarnessBatchTest {

    @Test
    void forceInit_defaultsToFalse_whenNoJvmFlag() {
        // saves/restores bench.enabled
        String orig = System.getProperty("bench.enabled");
        try {
            System.clearProperty("bench.enabled");
            SyxBenchmarkHarness h = new SyxBenchmarkHarness();
            assertFalse(h.forceInit(), "forceInit() must require -Dbench.enabled=true");
        } finally {
            restore(orig);
        }
    }

    @Test
    void forceInit_returnsTrue_whenJvmFlagSet() {
        String orig = System.getProperty("bench.enabled");
        try {
            System.setProperty("bench.enabled", "true");
            SyxBenchmarkHarness h = new SyxBenchmarkHarness();
            assertTrue(h.forceInit());
        } finally {
            restore(orig);
        }
    }

    @Test
    void fullLoop_writesRowsAndReachesCutoff(@TempDir Path tmp) throws IOException {
        // arrange
        Path out = tmp.resolve("baseline.csv");
        BenchConfig cfg = new BenchConfig(
            7L, /*runDays*/5, /*logEveryNDays*/1,
            out.toString(),
            tmp.resolve("baseline.save").toString(),
            false /* headless=false so test doesn't System.exit */);
        BenchmarkCsvWriter csv = new BenchmarkCsvWriter(out);
        EconomyMetrics stub = new EconomyMetrics.Constant(0.42, 12345L, 88);

        BenchmarkInstance inst = new BenchmarkInstance(cfg, csv, stub);

        // act — drive days 0..5 (runDays=5), gain 6 rows then cutoff
        for (int d = 0; d <= cfg.runDays; d++) {
            int rowsAfter = inst.simulateDay(d);
            assertTrue(rowsAfter >= 0, "day " + d + " must write a row");
        }

        // assert
        assertTrue(inst.isCutoffDone(), "cutoff must fire when day >= runDays");
        assertEquals(cfg.runDays + 1, inst.rowsWritten(),
            "runDays=5 + 1 header sampled rows");
        assertEquals(cfg.runDays, inst.lastLoggedDay());

        List<String> lines = Files.readAllLines(out);
        assertEquals(cfg.runDays + 1 + 1, lines.size(),
            "header + runDays+1 rows = 7 lines");
        assertEquals(BenchmarkCsvWriter.HEADER, lines.get(0));
        // day 0..5 each written as a row
        for (int d = 0; d <= cfg.runDays; d++) {
            String expected = d + ",0.4200,12345,88";
            assertEquals(expected, lines.get(d + 1),
                "day " + d + " must produce \"" + expected + "\"");
        }
    }

    @Test
    void fullLoop_logEveryNDays_throttlesRows(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("throttled.csv");
        BenchConfig cfg = new BenchConfig(
            9L, /*runDays*/10, /*logEveryNDays*/3,
            out.toString(),
            tmp.resolve("t.save").toString(),
            false);
        BenchmarkCsvWriter csv = new BenchmarkCsvWriter(out);
        BenchmarkInstance inst = new BenchmarkInstance(cfg, csv,
            new EconomyMetrics.Constant(0.0, 0L, 0));

        for (int d = 0; d <= cfg.runDays; d++) inst.simulateDay(d);

        // logEveryNDays=3 → rows at days 0,3,6,9 = 4 rows + header
        assertEquals(4, inst.rowsWritten());
        assertTrue(inst.isCutoffDone());
    }

    @Test
    void simulateDay_afterCutoff_returnsMinusOne(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("postcutoff.csv");
        BenchConfig cfg = new BenchConfig(1L, 2, 1, out.toString(),
            tmp.resolve("p.save").toString(), false);
        BenchmarkInstance inst = new BenchmarkInstance(cfg,
            new BenchmarkCsvWriter(out),
            new EconomyMetrics.Constant(0.5, 100L, 50));
        inst.simulateDay(0);
        inst.simulateDay(2); // cutoff at runDays=2
        assertTrue(inst.isCutoffDone());

        int rowsAfterPostCutoffCall = inst.simulateDay(5);
        assertEquals(-1, rowsAfterPostCutoffCall,
            "after cutoff, simulateDay is no-op and returns -1");
    }

    private static void restore(String value) {
        if (value == null) System.clearProperty("bench.enabled");
        else System.setProperty("bench.enabled", value);
    }
}
