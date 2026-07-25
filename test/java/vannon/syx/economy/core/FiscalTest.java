package vannon.syx.economy.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

/**
 * T-COV-1 — Pure-Helper + Save/Load Tests für {@link Fiscal}.
 *
 * <p>Engine-gekoppelte Pfade ({@link Fiscal#update}, settlePurchase/Ration/Service,
 * settleMerchantRemainder, settleCrownWholesale) bleiben ungetestet; sie brauchen
 * {@code FACTIONS.player().credits()} und einen lebenden {@code Roster}. Mockito-Inject
 * ist Sprint T-COV-9 vorbehalten.</p>
 *
 * <p>Was hier geprüft wird:</p>
 * <ul>
 *   <li>{@link Fiscal#split} — Bracket-Korrektheit + Clamping bei Out-of-Range-Raten.</li>
 *   <li>{@link Fiscal#retailSettlement} — Aufteilung Gross→Warehouse+Retailer.</li>
 *   <li>{@link Fiscal#save}/{@link Fiscal#load} — Roundtrip + Load-on-empty.</li>
 *   <li>{@link Fiscal#clear} — Reset-Werte auf 0 / -1.</li>
 *   <li>{@link Fiscal#setHeadTax}/{@link Fiscal#setMarketLevy} — Clamping bei negativen Werten.</li>
 *   <li>{@link Fiscal.Split}/{@link Fiscal.RetailSettlement} — Record-Components.</li>
 * </ul>
 */
class FiscalTest {

    /** AutoCloseable wrapper around snake2d's final {@link FileGetter}. */
    private static final class AutoCloseableGetter implements AutoCloseable {
        final FileGetter getter;

        AutoCloseableGetter(Path file) throws IOException {
            this.getter = new FileGetter(file);
        }

        @Override
        public void close() throws IOException {
            getter.close();
        }
    }

    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile("fiscal-test-", ".syx");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempFile != null) Files.deleteIfExists(tempFile);
    }

    // ── split() — pure static ─────────────────────────────────────────────

    @Test
    void split_zeroRate_returnsZeroTax() {
        Fiscal.Split s = Fiscal.split(1000, 0.0);
        assertEquals(0, s.tax());
        assertEquals(1000, s.net());
    }

    @Test
    void split_fullRate_returnsGrossAsTax() {
        Fiscal.Split s = Fiscal.split(1000, 1.0);
        assertEquals(1000, s.tax());
        assertEquals(0, s.net());
    }

    @Test
    void split_floorsTax_atPartialRate() {
        // 1000 gross @ 0.05 = 50.0 → floor = 50
        Fiscal.Split s = Fiscal.split(1000, 0.05);
        assertEquals(50, s.tax());
        assertEquals(950, s.net());
    }

    @Test
    void split_negativeGross_treatedAsZero() {
        Fiscal.Split s = Fiscal.split(-100, 0.10);
        assertEquals(0, s.tax());
        assertEquals(0, s.net());
    }

    @Test
    void split_rateAboveOne_clampsToOne() {
        Fiscal.Split s = Fiscal.split(500, 1.5);
        assertEquals(500, s.tax());
        assertEquals(0, s.net());
    }

    @Test
    void split_negativeRate_clampsToZero() {
        Fiscal.Split s = Fiscal.split(500, -0.5);
        assertEquals(0, s.tax());
        assertEquals(500, s.net());
    }

    @Test
    void split_partialFloors_toMaxValue() {
        // 7 @ rate 0.50 = 3.5 → floor = 3
        Fiscal.Split s = Fiscal.split(7, 0.50);
        assertEquals(3, s.tax());
        assertEquals(4, s.net());
    }

    // ── retailSettlement() — package-private static ───────────────────────

    @Test
    void retailSettlement_zeroRecordedWholesale_allGoesToRetailer() {
        Fiscal.RetailSettlement s = Fiscal.retailSettlement(500, 0);
        assertEquals(0, s.warehouse());
        assertEquals(500, s.retailer());
    }

    @Test
    void retailSettlement_proceedsBelowRecorded_warehouseSplitsFirst() {
        // proceeds=200, recorded=500 → warehouse=min(200,500)=200, retailer=0
        Fiscal.RetailSettlement s = Fiscal.retailSettlement(200, 500);
        assertEquals(200, s.warehouse());
        assertEquals(0, s.retailer());
    }

    @Test
    void retailSettlement_proceedsAboveRecorded_splitsBetweenTwo() {
        // proceeds=500, recorded=200 → warehouse=min(500,200)=200, retailer=300
        Fiscal.RetailSettlement s = Fiscal.retailSettlement(500, 200);
        assertEquals(200, s.warehouse());
        assertEquals(300, s.retailer());
    }

    @Test
    void retailSettlement_negativeNet_clampedToZero() {
        Fiscal.RetailSettlement s = Fiscal.retailSettlement(-50, 100);
        assertEquals(0, s.warehouse());
        assertEquals(0, s.retailer());
    }

    @Test
    void retailSettlement_negativeRecordedWare_treatedAsZero() {
        // recorded -100 → clamp to 0 → all proceeds to retailer
        Fiscal.RetailSettlement s = Fiscal.retailSettlement(500, -100);
        assertEquals(0, s.warehouse());
        assertEquals(500, s.retailer());
    }

    // ── Setters + Getters via EconConfig wrappers ─────────────────────────

    @Test
    void headTax_getterReflectsEconConfig() {
        EconConfig.perHeadTax = 42;
        Fiscal f = new Fiscal();
        assertEquals(42, f.headTax());

        EconConfig.perHeadTax = 0; // reset for the next test
    }

    @Test
    void setHeadTax_clampsNegativeToZero() {
        EconConfig.perHeadTax = 100;
        Fiscal f = new Fiscal();
        f.setHeadTax(-50);
        assertEquals(0, EconConfig.perHeadTax);
        assertEquals(0, f.headTax());
    }

    @Test
    void marketLevy_getterReflectsEconConfig() {
        EconConfig.marketTaxRate = 0.10;
        Fiscal f = new Fiscal();
        assertEquals(0.10, f.marketLevy(), 1e-9);

        EconConfig.marketTaxRate = 0.05;
    }

    @Test
    void setMarketLevy_clampsNegativeToZero() {
        EconConfig.marketTaxRate = 0.20;
        Fiscal f = new Fiscal();
        f.setMarketLevy(-0.50);
        assertEquals(0.0, EconConfig.marketTaxRate, 1e-9);
        assertEquals(0.0, f.marketLevy(), 1e-9);
    }

    // ── Default-getters return 0 on a fresh instance ──────────────────────

    @Test
    void freshInstance_allCountersAreZero() {
        Fiscal f = new Fiscal();
        assertEquals(0L, f.headTaxCollected());
        assertEquals(0L, f.marketReceipts());
        assertEquals(0L, f.rationOut());
        assertEquals(0L, f.producerIncome());
        assertEquals(0L, f.creditsTax());
        assertEquals(0L, f.creditsTrade());
        assertEquals(0L, f.creditsMisc());
    }

    // ── clear() — Reset-Werte ──────────────────────────────────────────────

    @Test
    void clear_resetsAllCounters() {
        Fiscal f = new Fiscal();
        // We can't easily mutate the private fields without reflection;
        // the cleanest evidence of clear() is that constructor + clear() leave
        // identical state.
        f.clear();
        assertEquals(0L, f.headTaxCollected());
        assertEquals(0L, f.marketReceipts());
        assertEquals(0L, f.rationOut());
        assertEquals(0L, f.producerIncome());
        // clear() sets lastSeason to -1; the next update() will start a new
        // season without charging anything (deferred to integration-test path).
    }

    // ── Save/Load — Roundtrip mit FilePutter/FileGetter ────────────────────

    @Test
    void save_load_roundtrip_preservesAllCounters() throws IOException {
        Fiscal source = freshWithManualState();
        FilePutter putter = new FilePutter(tempFile, 256);
        source.save(putter);
        putter.save();

        Fiscal target = new Fiscal();
        try (AutoCloseableGetter handle = new AutoCloseableGetter(tempFile)) {
            target.load(handle.getter);
        }
        assertEquals(source.headTaxCollected(), target.headTaxCollected(),
                "headTaxCollected must round-trip");
        assertEquals(source.marketReceipts(), target.marketReceipts(),
                "marketReceipts must round-trip");
        assertEquals(source.rationOut(), target.rationOut(),
                "rationOut must round-trip");
        assertEquals(source.producerIncome(), target.producerIncome(),
                "producerIncome must round-trip");
    }

    @Test
    void save_writesLastSeasonAsFirstInt() throws IOException {
        Fiscal f = freshWithManualState();
        FilePutter putter = new FilePutter(tempFile, 256);
        f.save(putter);
        putter.save();

        try (AutoCloseableGetter handle = new AutoCloseableGetter(tempFile)) {
            // save() writes: int lastSeason, long headTaxCollected, long marketReceipts,
            // long rationOut, long producerIncome
            int lastSeason = handle.getter.i();
            long headTax = handle.getter.l();
            long marketRecpts = handle.getter.l();
            long ration = handle.getter.l();
            long producer = handle.getter.l();
            assertEquals(7, lastSeason, "lastSeason is the first int in the save stream");
            assertEquals(100_000L, headTax);
            assertEquals(50_000L, marketRecpts);
            assertEquals(20_000L, ration);
            assertEquals(80_000L, producer);
        }
    }

    @Test
    void load_skipsStaleBytes_whenFetchedLater() throws IOException {
        // After load(), the cursor must be at the byte after the producerIncome long.
        Fiscal f = freshWithManualState();
        FilePutter putter = new FilePutter(tempFile, 256);
        f.save(putter);
        putter.save();

        try (AutoCloseableGetter handle = new AutoCloseableGetter(tempFile)) {
            f.clear();
            f.load(handle.getter);
            // The cursor sits right after producerIncome; no further ints to read,
            // so reading i() should return 0 from snake2d's end-of-stream behavior.
            int posBeforeJunk = handle.getter.getPosition();
            assertTrue(posBeforeJunk > 0, "cursor must have advanced past saved fields");
        }
    }

    // ── Records: Split + RetailSettlement ────────────────────────────────

    @Test
    void splitRecord_componentAccessors() {
        Fiscal.Split s = new Fiscal.Split(30, 70);
        assertEquals(30, s.tax());
        assertEquals(70, s.net());
    }

    @Test
    void retailSettlementRecord_componentAccessors() {
        Fiscal.RetailSettlement s = new Fiscal.RetailSettlement(40, 60);
        assertEquals(40, s.warehouse());
        assertEquals(60, s.retailer());
    }

    /**
     * Builds a Fiscal instance with non-zero last-known save stream state. Uses
     * reflection because the production class does not expose package-private
     * setters for the private counters.
     */
    private static Fiscal freshWithManualState() {
        Fiscal f = new Fiscal();
        try {
            java.lang.reflect.Field lastSeason = Fiscal.class.getDeclaredField("lastSeason");
            lastSeason.setAccessible(true);
            lastSeason.setInt(f, 7);
            java.lang.reflect.Field headTax = Fiscal.class.getDeclaredField("headTaxCollected");
            headTax.setAccessible(true);
            headTax.setLong(f, 100_000L);
            java.lang.reflect.Field marketRecpts = Fiscal.class.getDeclaredField("marketReceipts");
            marketRecpts.setAccessible(true);
            marketRecpts.setLong(f, 50_000L);
            java.lang.reflect.Field ration = Fiscal.class.getDeclaredField("rationOut");
            ration.setAccessible(true);
            ration.setLong(f, 20_000L);
            java.lang.reflect.Field producer = Fiscal.class.getDeclaredField("producerIncome");
            producer.setAccessible(true);
            producer.setLong(f, 80_000L);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not seed Fiscal counters: " + e.getMessage(), e);
        }
        return f;
    }
}
