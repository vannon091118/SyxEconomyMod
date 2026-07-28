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
import vannon.syx.economy.adapter.ISyxBoosting;

/**
 * T-COV-2 — Pure-Helper + Save/Load + v32→v33-Migration Tests für {@link EconProgression}.
 *
 * <p>Engine-gekoppelte Pfade ({@link EconProgression#pollBuildings}, {@link EconProgression#checkAdvance},
 * {@link EconProgression#registerAdminBooster}) bleiben ungetestet; sie brauchen
 * {@code SETT.ROOMS()} für die Gebäude-Zählung und den {@link game.boosting.Boostable}-Pfad.
 * Mockito-Inject ist Sprint T-COV-9 vorbehalten.</p>
 *
 * <p>Was hier geprüft wird:</p>
 * <ul>
 *   <li>{@link EconProgression.Stage#fromLevel} — Round-Trip aller 5 Stufen + Out-of-Range.</li>
 *   <li>{@link EconProgression.Stage#next} — Aufstieg + IMPERIUM-Boundary.</li>
 *   <li>{@link EconProgression#save}/{@link EconProgression#load} — v33-Format Roundtrip.</li>
 *   <li>v32→v33-Migration: alte Saves mit level=2 (WOHLSTAND) werden korrekt zu level=3 (WOHLSTAND+INDUSTRIE-Shift),
 *       level=3 (IMPERIUM) zu level=4 (IMPERIUM).</li>
 *   <li>Milestone-Erhalt über Save/Load.</li>
 *   <li>{@link EconProgression#adminBoostActive} default false.</li>
 * </ul>
 */
class EconProgressionTest {

    /** AutoCloseable wrapper. */
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

    private static final class NullBoostingAdapter implements ISyxBoosting {
        @Override public boolean isAdminBoosterAvailable() { return false; }
        @Override public game.boosting.Boostable getAdminBoostable() { return null; }
    }

    private boolean origAdminBoostActive;
    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        // P1-Fix (Reviewer-Note): Single @BeforeEach instead of two — JUnit 5 does
        // not guarantee execution order across multiple @BeforeEach methods, and
        // the previous `// NOSONAR second wins for setup` comment was factually
        // wrong.
        origAdminBoostActive = EconProgression.adminBoostActive;
        tempFile = Files.createTempFile("econ-prog-test-", ".syx");
    }

    @AfterEach
    void tearDown() throws IOException {
        EconProgression.adminBoostActive = origAdminBoostActive;
        if (tempFile != null) Files.deleteIfExists(tempFile);
    }

    // ── Stage.fromLevel() ────────────────────────────────────────────────

    @Test
    void stageFromLevel_allFiveLevels() {
        assertEquals(EconProgression.Stage.SUBSISTENZ, EconProgression.Stage.fromLevel(0));
        assertEquals(EconProgression.Stage.HANDEL, EconProgression.Stage.fromLevel(1));
        assertEquals(EconProgression.Stage.INDUSTRIE, EconProgression.Stage.fromLevel(2));
        assertEquals(EconProgression.Stage.WOHLSTAND, EconProgression.Stage.fromLevel(3));
        assertEquals(EconProgression.Stage.IMPERIUM, EconProgression.Stage.fromLevel(4));
    }

    @Test
    void stageFromLevel_negative_fallsBackToSubsistenz() {
        assertEquals(EconProgression.Stage.SUBSISTENZ, EconProgression.Stage.fromLevel(-5));
    }

    @Test
    void stageFromLevel_outOfRange_high_fallsBackToSubsistenz() {
        // Stage.fromLevel has no level=5; falls back to SUBSISTENZ.
        assertEquals(EconProgression.Stage.SUBSISTENZ, EconProgression.Stage.fromLevel(42));
    }

    // ── Stage.next() — Aufstieg + IMPERIUM-Boundary ──────────────────────

    @Test
    void stageNext_promotesSubsistenzToHandel() {
        assertEquals(EconProgression.Stage.HANDEL, EconProgression.Stage.SUBSISTENZ.next());
    }

    @Test
    void stageNext_promotesHandelToIndustrie() {
        assertEquals(EconProgression.Stage.INDUSTRIE, EconProgression.Stage.HANDEL.next());
    }

    @Test
    void stageNext_promotesIndustrieToWohlstand() {
        assertEquals(EconProgression.Stage.WOHLSTAND, EconProgression.Stage.INDUSTRIE.next());
    }

    @Test
    void stageNext_promotesWohlstandToImperium() {
        assertEquals(EconProgression.Stage.IMPERIUM, EconProgression.Stage.WOHLSTAND.next());
    }

    @Test
    void stageNext_imperiumStaysAtImperium() {
        // Boundary: kein next() über IMPERIUM hinaus.
        assertEquals(EconProgression.Stage.IMPERIUM, EconProgression.Stage.IMPERIUM.next());
    }

    @Test
    void stage_allHaveNonNullDisplayNames() {
        // displayName is a public final *field* on the enum, not a method.
        for (EconProgression.Stage s : EconProgression.Stage.values()) {
            assertNotNull(s.displayName,
                    "Stage " + s.name() + " must have a displayName field");
            assertFalse(s.displayName.isEmpty(),
                    "Stage " + s.name() + " displayName must not be empty");
        }
    }

    // ── Default-Field State ──────────────────────────────────────────────

    @Test
    void fresh_defaultStageIsSubsistenz() {
        EconProgression p = new EconProgression(new NullBoostingAdapter());
        assertEquals(EconProgression.Stage.SUBSISTENZ, p.stage);
        assertEquals(0, p.stageDays);
        assertFalse(p.msFirstStockpile);
        assertFalse(p.msFirstExport);
        assertFalse(EconProgression.adminBoostActive);
    }

    // ── Save/Load — v33 Roundtrip ───────────────────────────────────────

    @Test
    void saveLoad_v33_roundtrip() throws IOException {
        EconProgression source = new EconProgression(new NullBoostingAdapter());
        source.stage = EconProgression.Stage.WOHLSTAND;
        source.stageDays = 42;
        source.cumulativeWagesPaid = 1_500_000L;
        source.cumulativeExportValue = 250_000L;
        source.daysSinceInsolvency = 37;
        source.daysLowGini = 12;
        source.daysVeryLowGini = 3;
        source.msFirstStockpile = true;
        source.msFirstExport = true;
        source.msFirstTavern = true;
        source.msFirstMarket = true;
        source.msFirstTemple = false;
        source.msFirstLaboratory = true;
        source.msFirstMilitary = false;
        source.msFirstEmbassy = false;
        source.msStableWages = true;
        source.statusLowInequality = true;

        FilePutter putter = new FilePutter(tempFile, 256);
        source.save(putter);
        putter.save();

        EconProgression target = new EconProgression(new NullBoostingAdapter());
        try (AutoCloseableGetter handle = new AutoCloseableGetter(tempFile)) {
            target.load(handle.getter);
        }

        assertEquals(EconProgression.Stage.WOHLSTAND, target.stage);
        assertEquals(42, target.stageDays);
        assertEquals(1_500_000L, target.cumulativeWagesPaid);
        assertEquals(250_000L, target.cumulativeExportValue);
        assertEquals(37, target.daysSinceInsolvency);
        assertEquals(12, target.daysLowGini);
        assertEquals(3, target.daysVeryLowGini);
        assertTrue(target.msFirstStockpile);
        assertTrue(target.msFirstExport);
        assertTrue(target.msFirstTavern);
        assertTrue(target.msFirstMarket);
        assertFalse(target.msFirstTemple);
        assertTrue(target.msFirstLaboratory);
        assertFalse(target.msFirstMilitary);
        assertFalse(target.msFirstEmbassy);
        assertTrue(target.msStableWages);
        assertTrue(target.statusLowInequality);
    }

    @Test
    void save_writesVersionHeader_33() throws IOException {
        EconProgression p = new EconProgression(new NullBoostingAdapter());
        FilePutter putter = new FilePutter(tempFile, 256);
        p.save(putter);
        putter.save();

        try (AutoCloseableGetter handle = new AutoCloseableGetter(tempFile)) {
            int version = handle.getter.i();
            assertEquals(EconProgression.SAVE_VERSION, version, "first int must be SAVE_VERSION (33)");
            int stageLevel = handle.getter.i();
            assertEquals(0, stageLevel, "fresh instance = SUBSISTENZ level=0");
        }
    }

    // ── v32 → v33 Migration ─────────────────────────────────────────────

    @Test
    void load_legacyV32_levelZero_mapsToSubsistenz() throws IOException {
        // v32-Save: direkt der stage.level als erstes int.
        writeLegacySave(0, 100, 50, 0, 0, 0, false, false, false, false,
                false, false, false, false, false, false);
        EconProgression target = new EconProgression(new NullBoostingAdapter());
        try (AutoCloseableGetter handle = new AutoCloseableGetter(tempFile)) {
            target.load(handle.getter);
        }
        assertEquals(EconProgression.Stage.SUBSISTENZ, target.stage);
    }

    @Test
    void load_legacyV32_levelOne_mapsToHandel() throws IOException {
        writeLegacySave(1, 100, 50, 0, 0, 0, false, false, false, false,
                false, false, false, false, false, false);
        EconProgression target = new EconProgression(new NullBoostingAdapter());
        try (AutoCloseableGetter handle = new AutoCloseableGetter(tempFile)) {
            target.load(handle.getter);
        }
        assertEquals(EconProgression.Stage.HANDEL, target.stage);
    }

    @Test
    void load_legacyV32_levelTwo_shiftsToIndustrie() throws IOException {
        // Migration: alt level=2 war WOHLSTAND. Mit INDUSTRIE-Einfügung +
        // 1 → level=3 = neues WOHLSTAND.
        writeLegacySave(2, 100, 50, 0, 0, 0, false, false, false, false,
                false, false, false, false, false, false);
        EconProgression target = new EconProgression(new NullBoostingAdapter());
        try (AutoCloseableGetter handle = new AutoCloseableGetter(tempFile)) {
            target.load(handle.getter);
        }
        assertEquals(EconProgression.Stage.WOHLSTAND, target.stage);
    }

    @Test
    void load_legacyV32_levelThree_shiftsToImperium() throws IOException {
        // Migration: alt level=3 war IMPERIUM. +1 → level=4 = neues IMPERIUM.
        writeLegacySave(3, 100, 50, 0, 0, 0, false, false, false, false,
                false, false, false, false, false, false);
        EconProgression target = new EconProgression(new NullBoostingAdapter());
        try (AutoCloseableGetter handle = new AutoCloseableGetter(tempFile)) {
            target.load(handle.getter);
        }
        assertEquals(EconProgression.Stage.IMPERIUM, target.stage);
    }

    /**
     * Schreibt einen v32-Save-Strom direkt auf die Platte. Reihenfolge exakt wie
     * im damaligen {@code EconProgression.save()} VOR der v33-Änderung:
     * int stage.level, int stageDays, long cumulativeWagesPaid, long cumulativeExportValue,
     * int daysSinceInsolvency, int daysLowGini, int daysVeryLowGini, bool x9 (milestones+status).
     */
    private void writeLegacySave(int stageLevel, long cumWages, long cumExport,
                                 int daysSinceInsolvency, int daysLowGini, int daysVeryLowGini,
                                 boolean msStock, boolean msExport, boolean msTavern, boolean msMarket,
                                 boolean msTemple, boolean msLab, boolean msMil, boolean msEmb,
                                 boolean stableWages, boolean lowIneq) throws IOException {
        FilePutter putter = new FilePutter(tempFile, 256);
        putter.i(stageLevel);
        putter.i(15); // stageDays
        putter.l(cumWages);
        putter.l(cumExport);
        putter.i(daysSinceInsolvency);
        putter.i(daysLowGini);
        putter.i(daysVeryLowGini);
        putter.bool(msStock);
        putter.bool(msExport);
        putter.bool(msTavern);
        putter.bool(msMarket);
        putter.bool(msTemple);
        putter.bool(msLab);
        putter.bool(msMil);
        putter.bool(msEmb);
        putter.bool(stableWages);
        putter.bool(lowIneq);
        putter.save();
    }
}
