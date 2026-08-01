package vannon.syx.economy.benchmark;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Pure-Java CSV writer for the 4-column benchmark schema:
 * <pre>
 *   day,gini,money_supply,median_price
 * </pre>
 *
 * <p>This class has <b>zero coupling</b> to vanilla Songs-of-Syx APIs and is therefore
 * fully exercisable by {@code mvn test --batch-mode} without booting the engine.
 * The driver loop ({@link BenchmarkInstance}) wraps this writer and supplies data per
 * simulated day; tests feed constant or scripted data via {@link EconomyMetrics.Constant}.</p>
 *
 * <p>Flush is per-row ({@link BufferedWriter#newLine()} on every {@link #append}) so a crash
 * mid-run still preserves the partial CSV — never lose data on hull-breach.</p>
 */
public final class BenchmarkCsvWriter implements AutoCloseable {

    /** Canonical 4-column header. Kept as a constant so tests can assert byte-for-byte. */
    public static final String HEADER = "day,gini,money_supply,median_price";

    private final BufferedWriter w;
    private int rowCount;
    private boolean closed;

    public BenchmarkCsvWriter(Path path) throws IOException {
        this.w = Files.newBufferedWriter(
            path,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING);
        this.w.write(HEADER);
        this.w.newLine();
        this.rowCount = 0;
        this.closed = false;
    }

    /**
     * Append one sample row.
     * @param day          simulated day index (0 = game start, runDays = cutoff)
     * @param gini         Gini coefficient of wealth [0.0..1.0], 2 decimals
     * @param moneySupply  aggregate denari across treasury + citizens' wallets
     * @param medianPrice  median resource price (rounded up) across RESOURCES.ALL
     */
    public synchronized void append(int day, double gini, long moneySupply, int medianPrice) throws IOException {
        if (closed) {
            throw new IOException("writer already closed");
        }
        // day is integer, gini is double with 4 decimals to keep regression-diff readable
        this.w.write(Integer.toString(day));
        this.w.write(',');
        this.w.write(String.format(java.util.Locale.ROOT, "%.4f", gini));
        this.w.write(',');
        this.w.write(Long.toString(moneySupply));
        this.w.write(',');
        this.w.write(Integer.toString(medianPrice));
        this.w.newLine();
        this.rowCount++;
    }

    /** Number of data rows written so far (excludes header). */
    public int rows() {
        return this.rowCount;
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        this.w.flush();
        this.w.close();
        this.closed = true;
    }
}
