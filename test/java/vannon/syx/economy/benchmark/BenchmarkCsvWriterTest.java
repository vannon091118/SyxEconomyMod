package vannon.syx.economy.benchmark;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pure-JVM test for {@link BenchmarkCsvWriter}. Verifies the 4-column
 * schema (day,gini,money_supply,median_price) and flush-per-row behaviour.
 */
class BenchmarkCsvWriterTest {

    @Test
    void writesHeader_thenRowsExact(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("out.csv");
        BenchmarkCsvWriter w = new BenchmarkCsvWriter(out);
        w.append(0, 0.0, 0L, 0);
        w.append(1, 0.1234, 1500L, 75);
        w.append(2, 0.9876, 999L, 14);
        w.close();

        List<String> lines = Files.readAllLines(out);
        assertEquals(4, lines.size());
        assertEquals(BenchmarkCsvWriter.HEADER, lines.get(0));
        assertEquals("0,0.0000,0,0", lines.get(1));
        assertEquals("1,0.1234,1500,75", lines.get(2));
        assertEquals("2,0.9876,999,14", lines.get(3));
    }

    @Test
    void rowsCounterIncrementsByOne(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("r.csv");
        BenchmarkCsvWriter w = new BenchmarkCsvWriter(out);
        assertEquals(0, w.rows());
        w.append(5, 0.5, 100L, 50);
        w.append(6, 0.6, 200L, 60);
        assertEquals(2, w.rows());
        w.close();
        assertEquals(2, w.rows()); // stable after close
    }

    @Test
    void doubleClose_isSafe(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("d.csv");
        BenchmarkCsvWriter w = new BenchmarkCsvWriter(out);
        w.append(1, 0.1, 10L, 10);
        w.close();
        // second close must not throw
        w.close();
    }

    @Test
    void appendAfterClose_throws(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("e.csv");
        BenchmarkCsvWriter w = new BenchmarkCsvWriter(out);
        w.close();
        assertThrows(IOException.class, () -> w.append(2, 0.2, 20L, 20));
    }

    @Test
    void truncateOnRecreate(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("t.csv");
        BenchmarkCsvWriter w = new BenchmarkCsvWriter(out);
        w.append(1, 0.1, 10L, 10);
        w.close();

        BenchmarkCsvWriter w2 = new BenchmarkCsvWriter(out);
        w2.append(2, 0.2, 20L, 20);
        w2.close();

        List<String> lines = Files.readAllLines(out);
        assertEquals(2, lines.size(), "recreate must overwrite, not append");
        assertEquals(BenchmarkCsvWriter.HEADER, lines.get(0));
        assertEquals("2,0.2000,20,20", lines.get(1));
    }
}
