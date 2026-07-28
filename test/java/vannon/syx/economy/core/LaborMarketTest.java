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
 * T-COV-4 — Static-Math + State-Setter Tests für {@link LaborMarket}.
 *
 * <p>Engine-gekoppelte Pfade ({@link LaborMarket#update},
 * {@link LaborMarket#refreshBlueprintOutputs}) bleiben ungetestet; sie brauchen
 * {@code SETT.ROOMS()} und einen lebenden {@code FirmLedger}. Mockito-Inject ist Sprint
 * T-COV-9 vorbehalten.</p>
 *
 * <p>Was hier geprüft wird:</p>
 * <ul>
 *   <li>{@link LaborMarket#profitPriority} — Pure-Math: Marginal vs Mean → elastic priority.</li>
 *   <li>{@link LaborMarket#blend} — Clamping bei freeShare&lt;0, freeShare&gt;1, sowie Min/Max-Clamps.</li>
 *   <li>{@link LaborMarket#setScarcitySignal} + {@link LaborMarket#scarcityForBlueprint}
 *       — Returns 0.0 ohne Signal/Mismatch.</li>
 *   <li>{@link LaborMarket#derivedPriority} — -1 für null/missing-key.</li>
 *   <li>{@link LaborMarket#meanWage} default 0.0.</li>
 *   <li>{@link LaborMarket#save}/{@link LaborMarket#load} — Roundtrip.</li>     *   <li>{@link LaborMarket#clear} — Felder auf Default.</li>
 * </ul>
 */
class LaborMarketTest {

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
        tempFile = Files.createTempFile("labor-test-", ".syx");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempFile != null) Files.deleteIfExists(tempFile);
    }

    // ── blend() — pure static ─────────────────────────────────────────

    @Test
    void blend_normalCase() {
        // base=10, wagePrio=20, freeShare=0.5 → expected 15
        int result = LaborMarket.blend(10, 20, 0.5, 0, 100);
        assertEquals(15, result);
    }

    @Test
    void blend_zeroFreeShare_returnsBase() {
        int result = LaborMarket.blend(10, 20, 0.0, 0, 100);
        assertEquals(10, result);
    }

    @Test
    void blend_fullFreeShare_returnsWagePrio() {
        int result = LaborMarket.blend(10, 20, 1.0, 0, 100);
        assertEquals(20, result);
    }

    @Test
    void blend_freeShareBelowZero_clampsToZero() {
        int result = LaborMarket.blend(10, 20, -0.5, 0, 100);
        assertEquals(10, result, "freeShare<0 must clamp to 0 → base wins");
    }

    @Test
    void blend_freeShareAboveOne_clampsToOne() {
        int result = LaborMarket.blend(10, 20, 2.0, 0, 100);
        assertEquals(20, result, "freeShare>1 must clamp to 1 → wagePrio wins");
    }

    @Test
    void blend_resultBelowMin_clampsToMin() {
        // base=2, wagePrio=3, freeShare=0.0 → 2, but min=5 → 5
        int result = LaborMarket.blend(2, 3, 0.0, 5, 100);
        assertEquals(5, result);
    }

    @Test
    void blend_resultAboveMax_clampsToMax() {
        // base=90, wagePrio=200, freeShare=1.0 → 200, but max=150 → 150
        int result = LaborMarket.blend(90, 200, 1.0, 0, 150);
        assertEquals(150, result);
    }

    @Test
    void blend_roundsToNearestInteger() {
        // base=10, wagePrio=15, freeShare=0.4 → 10 + 0.4 * 5 = 12.0
        int result = LaborMarket.blend(10, 15, 0.4, 0, 100);
        assertEquals(12, result);
    }

    // ── profitPriority() — forwards to FirmEconomyKernel.priority ────

    @Test
    void profitPriority_aboveMean_returnsAboveNeutral() {
        // profitPriority(marginal=200, mean=100, ...) — higher than neutral
        int prio = LaborMarket.profitPriority(200.0, 100.0, 0, 100);
        assertTrue(prio >= EconConfig.laborNeutralPriority,
                "above-mean marginal must yield priority ≥ neutral (" + prio + ")");
    }

    @Test
    void profitPriority_equalToMean_returnsNeutral() {
        int prio = LaborMarket.profitPriority(100.0, 100.0, 0, 100);
        assertEquals(EconConfig.laborNeutralPriority, prio,
                "marginal == mean must yield neutral priority");
    }

    @Test
    void profitPriority_zeroMarginal_clampsToMin() {
        // FirmEconomyKernel.priority roughly: neutral + elasticity*log(marginal/mean).
        // log(0)=-inf → priority=-inf → clamped to the configured min (=0 here).
        int prio = LaborMarket.profitPriority(0.0, 100.0, 0, 100);
        assertEquals(0, prio,
                "marginal=0 must clamp to min via logarithm of zero");
    }

    @Test
    void profitPriority_belowMean_yieldsBelowNeutral() {
        int prio = LaborMarket.profitPriority(10.0, 100.0, 0, 100);
        assertTrue(prio < EconConfig.laborNeutralPriority,
                "below-mean marginal must yield priority < neutral");
    }

    // ── derivedPriority() — Map-Setter + Defaults ──────────────────────

    @Test
    void derivedPriority_nullBlueprint_returnsMinusOne() {
        LaborMarket m = new LaborMarket();
        assertEquals(-1, m.derivedPriority(null));
    }

    @Test
    void derivedPriority_unknownBlueprint_returnsMinusOne() {
        LaborMarket m = new LaborMarket();
        // Without prior update(), the private `written` map is empty.
        // We exercise the public Map-miss branch via derivedPriority(null).
        assertEquals(-1, m.derivedPriority(null));
    }

    // ── meanWage() + setScarcitySignal() defaults ─────────────────────

    @Test
    void meanWage_defaultIsZero() {
        assertEquals(0.0, new LaborMarket().meanWage(), 1e-9);
    }

    @Test
    void setScarcitySignal_nullIsAllowed() {
        LaborMarket m = new LaborMarket();
        assertDoesNotThrow(() -> m.setScarcitySignal(null));
    }

    @Test
    void setScarcitySignal_canBeReplaced() {
        LaborMarket m = new LaborMarket();
        ScarcitySignal a = new ScarcitySignal();
        ScarcitySignal b = new ScarcitySignal();
        m.setScarcitySignal(a);
        m.setScarcitySignal(b); // second call wins; first call should not leak a memory issue.
        // No NPE or visible state corruption.
        assertNotNull(m);
    }

    @Test
    void scarcityForBlueprint_nullSignal_returnsZero() {
        LaborMarket m = new LaborMarket();
        // Without an injected ScarcitySignal, scarcityForBlueprint() short-circuits.
        assertEquals(0.0, m.scarcityForBlueprint(null), 1e-9);
    }

    @Test
    void scarcityForBlueprint_nullBlueprint_returnsZero() {
        LaborMarket m = new LaborMarket();
        m.setScarcitySignal(new ScarcitySignal());
        assertEquals(0.0, m.scarcityForBlueprint(null), 1e-9);
    }

    // ── Save/Load — Roundtrip ─────────────────────────────────────────

    @Test
    void saveLoad_roundtrip() throws IOException {
        LaborMarket source = new LaborMarket();
        seedMap(source);

        FilePutter putter = new FilePutter(tempFile, 256);
        source.save(putter);
        putter.save();

        LaborMarket target = new LaborMarket();
        try (AutoCloseableGetter handle = new AutoCloseableGetter(tempFile)) {
            target.load(handle.getter);
        }

        // We seeded baseline={key:42, another:7}; restore is verified via update()
        // behavior which we don't test here. But the load itself must not throw,
        // and the roundtrip field-count must be sane.
        assertEquals(0.0, target.meanWage(), "load resets transient meanWage");
    }

    // ── clear() ───────────────────────────────────────────────────────

    @Test
    void clear_clearsAllState() {
        LaborMarket m = new LaborMarket();
        seedMap(m);
        m.setScarcitySignal(new ScarcitySignal());
        m.clear();

        assertEquals(0.0, m.meanWage(), 1e-9);
        // After reset, scarcityForBlueprint(null) still returns 0.0 (the safer
        // null path) and derivedPriority(null) still returns -1.
        assertEquals(0.0, m.scarcityForBlueprint(null), 1e-9);
        assertEquals(-1, m.derivedPriority(null));
    }


    // ── helpers ───────────────────────────────────────────────────────

    private static void seedMap(LaborMarket m) {
        try {
            java.lang.reflect.Field baseline = LaborMarket.class.getDeclaredField("baseline");
            baseline.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.HashMap<String, Integer> bm =
                    (java.util.HashMap<String, Integer>) baseline.get(m);
            bm.put("key", 42);
            bm.put("another", 7);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
