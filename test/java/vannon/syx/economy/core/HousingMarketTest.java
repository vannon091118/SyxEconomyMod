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
 * T-COV-5 — Save/Load + State-Reset Tests für {@link HousingMarket}.
 *
 * <p>Engine-gekoppelte Pfade ({@link HousingMarket#update},
 * {@link HousingMarket#collectFromHomes}, {@link HousingMarket#collectFromChambers},
 * {@link HousingMarket#collectRent}, {@link HousingMarket#rentFor},
 * {@link HousingMarket#evict}) bleiben ungetestet; sie brauchen
 * {@code SETT.ROOMS()} und einen lebenden {@code Roster}+{@code Wallets}.
 * Mockito-Inject ist Sprint T-COV-9 vorbehalten.</p>
 *
 * <p>Was hier geprüft wird:</p>
 * <ul>
 *   <li>Default-Werte für lastRentCollected/Due/Evictions = 0.</li>
 *   <li>{@link HousingMarket#ledger} gibt die gleiche (memoized) PropertyLedger-Instanz.</li>
 *   <li>{@link HousingMarket#clear} setzt alle Counter zurück.</li>
 *   <li>{@link HousingMarket#save}/{@link HousingMarket#load} — Roundtrip.</li>
 * </ul>
 */
class HousingMarketTest {

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
        tempFile = Files.createTempFile("housing-test-", ".syx");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempFile != null) Files.deleteIfExists(tempFile);
    }

    // ── Default-Werte ────────────────────────────────────────────────

    @Test
    void fresh_lastCountersAreZero() {
        HousingMarket m = new HousingMarket();
        assertEquals(0L, m.lastRentCollected());
        assertEquals(0L, m.lastRentDue());
        assertEquals(0, m.lastEvictions());
    }

    @Test
    void ledger_isMemoizedOnSameInstance() {
        HousingMarket m = new HousingMarket();
        PropertyLedger a = m.ledger();
        PropertyLedger b = m.ledger();
        assertSame(a, b,
                "ledger() must return the same PropertyLedger instance every call");
        assertNotNull(a, "ledger() must never return null");
    }

    @Test
    void twoHousingMarkets_haveIndependentLedgers() {
        HousingMarket m1 = new HousingMarket();
        HousingMarket m2 = new HousingMarket();
        assertNotSame(m1.ledger(), m2.ledger(),
                "each HousingMarket owns its own PropertyLedger");
    }

    // ── clear() ──────────────────────────────────────────────────────

    @Test
    void clear_resetsAllCounters() {
        HousingMarket m = seedingCounters(new HousingMarket());
        m.clear();
        assertEquals(0L, m.lastRentCollected());
        assertEquals(0L, m.lastRentDue());
        assertEquals(0, m.lastEvictions());
        // The PropertyLedger must also be cleared.
        assertTrue(m.ledger().ownedBy(123L).isEmpty(),
                "ledger must be empty after clear()");
    }

    // ── Save/Load — Roundtrip ────────────────────────────────────────

    @Test
    void saveLoad_roundtrip_preservesCounters() throws IOException {
        HousingMarket source = seedingCounters(new HousingMarket());

        FilePutter putter = new FilePutter(tempFile, 256);
        source.save(putter);
        putter.save();

        HousingMarket target = new HousingMarket();
        try (AutoCloseableGetter handle = new AutoCloseableGetter(tempFile)) {
            target.load(handle.getter);
        }

        assertEquals(source.lastRentCollected(), target.lastRentCollected());
        assertEquals(source.lastRentDue(), target.lastRentDue());
        assertEquals(source.lastEvictions(), target.lastEvictions());
        // Counter ↔ ledger consistency: ledger() is freshly reconstructed
        // post-load, so its entries must be empty (we didn't seed the ledger).
        assertTrue(target.ledger().ownedBy(0L).isEmpty());
    }

    @Test
    void save_writesFieldsInExpectedOrder() throws IOException {
        HousingMarket source = seedingCounters(new HousingMarket());
        FilePutter putter = new FilePutter(tempFile, 256);
        source.save(putter);
        putter.save();

        try (AutoCloseableGetter handle = new AutoCloseableGetter(tempFile)) {
            // Order: int lastSeason (default -1), long lastRentCollected,
            // long lastRentDue, int lastEvictions, then PropertyLedger content.
            int lastSeason = handle.getter.i();
            long rentCollected = handle.getter.l();
            long rentDue = handle.getter.l();
            int evictions = handle.getter.i();
            assertEquals(-1, lastSeason, "fresh instance lastSeason must be -1");
            assertEquals(75_000L, rentCollected);
            assertEquals(100_000L, rentDue);
            assertEquals(4, evictions);
        }
    }

    // ── save/load roundtrip mit eigenem PropertyLedger-Inhalt ────────

    @Test
    void saveLoad_ledgerOwnershipSurvives() throws IOException {
        HousingMarket source = new HousingMarket();
        // Seed an entry in the ledger.
        PropertyLedger.Entry entry = source.ledger().get(15, 20, "HOME");
        entry.ownerId = 42L;
        entry.shares = 100;

        FilePutter putter = new FilePutter(tempFile, 256);
        source.save(putter);
        putter.save();

        HousingMarket target = new HousingMarket();
        try (AutoCloseableGetter handle = new AutoCloseableGetter(tempFile)) {
            target.load(handle.getter);
        }

        PropertyLedger.Entry e = target.ledger().get(15, 20, "HOME");
        assertEquals(42L, e.ownerId());
        assertEquals(100, e.shares());
        assertTrue(target.ledger().isHomeOwner(42L),
                "citizen 42 must remain a home-owner after load()");
    }

    // ── helpers ──────────────────────────────────────────────────────

    /** Seed the private counters via reflection so save()/load() has work to do. */
    private static HousingMarket seedingCounters(HousingMarket m) {
        try {
            java.lang.reflect.Field lastSeason = HousingMarket.class.getDeclaredField("lastSeason");
            lastSeason.setAccessible(true);
            lastSeason.setInt(m, -1);

            java.lang.reflect.Field collected = HousingMarket.class.getDeclaredField("lastRentCollected");
            collected.setAccessible(true);
            collected.setLong(m, 75_000L);

            java.lang.reflect.Field due = HousingMarket.class.getDeclaredField("lastRentDue");
            due.setAccessible(true);
            due.setLong(m, 100_000L);

            java.lang.reflect.Field evictions = HousingMarket.class.getDeclaredField("lastEvictions");
            evictions.setAccessible(true);
            evictions.setInt(m, 4);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return m;
    }
}
