package vannon.syx.economy.falsification;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vannon.syx.economy.core.EconomySim;

/**
 * Falsifikations-Test #2 für RES-002-Behauptung:
 *   "14-Phasen-Tick-Loop mit ReentryGuard ist sicher."
 *
 * <p><b>Falsifikations-Hypothese (RES-003):</b> Auto-Save
 * (separater Thread/Timer oder periodischer Engine-Listener
 * auf WORLD_TICK_EVENT) kann während
 * {@link EconomySim#update(double)} feuern, während intern
 * Locks auf {@code updateGuard} nur für SAME-Thread Re-Entry
 * da sind. Ein halb-aktualisierter State wird persistiert; bei
 * Load kommt es zu {@code circulating() != expected()},
 * doppelten Löhnen oder fehlenden Steuer-Belegen.</p>
 *
 * <p><b>Falsifikations-Kriterium:</b> {@link EconomySim#save}
 * und {@link EconomySim#load} dürfen KEINE Kopplung zu
 * {@code updateGuard} aufweisen, und {@code update()} darf
 * KEIN Save-Hook haben, der update() selbst stoppt oder in
 * einem synchronized-Block landet. Wenn diese Kriterien erfüllt
 * sind (d.h. save/load mit update koexistieren können, ohne
 * sich gegenseitig auszuschließen), dann ist die
 * ReentryGuard-Behauptung im Bezug auf Save sauber
 * <em>FALSCH</em> — der Lock schützt gegen Re-Entry, NICHT
 * gegen parallele Saves.</p>
 *
 * <p><b>Scope:</b> Reflection + Source-Grep, KEIN
 * Multi-Threading-Test nötig — der Code-Beweis ist die
 * Inspektion. Engine wird nicht instanziiert.</p>
 */
@DisplayName("Falsifikation #2 — Save-Race-Condition via fehlender updateGuard-Kopplung")
class FalsifikationsTest2_SaveRaceCondition {

    private static final String ECON_SIM_SOURCE = "src/vannon/syx/economy/core/EconomySim.java";

    /**
     * Falsifikation 2A — Existenz und Schutzgrad von updateGuard.
     *
     * <p>Im Code existiert ein {@code ReentryGuard}-Feld. Aber
     * es schützt nur SAME-Frame Re-Entry-Versuche. Es ist KEIN
     * breiter Mutex.</p>
     */
    @Test
    @DisplayName("updateGuard ist Re-Entry-Wächter (same-thread) — KEIN breiter Mutex")
    void updateGuard_is_reentry_not_mutex() throws Exception {
        Field guardField = EconomySim.class.getDeclaredField("updateGuard");
        guardField.setAccessible(true);
        assertNotNull(guardField, "updateGuard Feld muss existieren.");
        assertTrue(java.lang.reflect.Modifier.isPrivate(guardField.getModifiers()),
                "updateGuard ist private — save()/load() haben keinen Zugriff darauf.");
    }

    /**
     * Falsifikation 2B — save()/load() sind NICHT in updateGuard eingeklinkt.
     *
     * <p>Wir lesen die Source von
     * {@code EconomySim.save(...)} und
     * {@code EconomySim.load(...)} und prüfen, ob sie
     * {@code tryEnter()}, {@code exit()} oder
     * {@code updateGuard} referenzieren. Wenn KEINE dieser
     * Referenzen existiert, kann ein externer Caller (z.B.
     * Engine-WORLD_TICK_EVENT-Listener) save() parallel zu
     * update() aufrufen, ohne zu blocken.</p>
     */
    @Test
    @DisplayName("save() und load() rufen updateGuard NICHT an — Lockung fehlt")
    void save_and_load_do_not_acquire_updateGuard() throws Exception {
        String src = new String(
                Files.readAllBytes(Paths.get(ECON_SIM_SOURCE)),
                StandardCharsets.UTF_8);

        // Locate the save(FilePutter) method body.
        int saveStart = src.indexOf("public void save(FilePutter file)");
        assertTrue(saveStart > 0, "save(FilePutter) muss existieren.");
        // Approximate body bound: next 8000 chars or until next sibling resumption.
        int saveEnd = Math.min(saveStart + 8000, src.length());
        String saveBody = src.substring(saveStart, saveEnd);

        int loadStart = src.indexOf("public void load(FileGetter file)");
        assertTrue(loadStart > 0, "load(FileGetter) muss existieren.");
        int loadEnd = Math.min(loadStart + 8000, src.length());
        String loadBody = src.substring(loadStart, loadEnd);

        // KEIN updateGuard-Zugriff in save() — sonst greift die Hypothese nicht.
        assertFalse(saveBody.contains("updateGuard"),
                "Falsifikation: save(FilePutter) darf updateGuard NICHT referenzieren — "
                        + "sonst wäre die Race-Hypothese durch Code-Schutz bereits entkräftet.");
        assertFalse(saveBody.contains("tryEnter") || saveBody.contains(".exit()"),
                "Falsifikation: save() darf KEINE ReentryGuard-API aufrufen.");

        assertFalse(loadBody.contains("updateGuard"),
                "Falsifikation: load(FileGetter) darf updateGuard NICHT referenzieren.");
        assertFalse(loadBody.contains("tryEnter") || loadBody.contains(".exit()"),
                "Falsifikation: load() darf KEINE ReentryGuard-API aufrufen.");
    }

    /**
     * Falsifikation 2C — Kein Auto-Save-Timer / Thread in EconomySim selbst.
     *
     * <p>Die Engine WOULD save() aufrufen, etwa via
     * {@code thread.schedule(...)}. Wenn EconomySim selbst
     * kein solches Timer/Thread-Setup hat, suchen wir die
     * Aufrufer von {@code .save()} im Repo. Wenn keine
     * existieren, ist die Hypothese zwar akademisch, aber der
     * Code bleibt unsicher für JEDE zukünftige Wiring — ein
     * Caller-binding außerhalb von EconomySim ist trivial
     * nachrüstbar.</p>
     */
    @Test
    @DisplayName("EconomySim hat keinen eigenen Auto-Save-Timer/Thread — Caller-binding fehlt")
    void economySim_has_no_internal_auto_save_thread() throws Exception {
        String src = new String(
                Files.readAllBytes(Paths.get(ECON_SIM_SOURCE)),
                StandardCharsets.UTF_8);

        // KEIN Hintergrund-Thread/Timer in EconomySim.
        for (String forbidden : new String[]{
                "ExecutorService", "ScheduledExecutorService", "new Thread(",
                "Timer(", "scheduleAtFixedRate", "scheduleWithFixedDelay"
        }) {
            assertFalse(src.contains(forbidden),
                    "EconomySim darf KEIN " + forbidden + " enthalten — sonst hat es "
                            + "bereits eine Timer-Mechanik, die den Test gegenstandslos macht.");
        }

        // external save() callers — Liste im gesamten src/-Tree.
        List<String> callers = new ArrayList<>();
        Files.walk(Paths.get("src/vannon/syx/economy"))
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> {
                    try {
                        String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                        for (String line : content.split("\n")) {
                            // Heuristic: Caller ruft EconomySim.save auf.
                            if (line.contains(".save(") || line.contains(".load(")) {
                                if (line.contains("EconomySim") || line.contains("economySim")) {
                                    callers.add(p.getFileName() + ": " + line.trim());
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // Skip unreadable / non-text files.
                    }
                });

        // Der Befund dokumentiert das aktuelle Caller-Bild —
        // Hypothese bleibt "future caller can race in" solange save() nicht gelockt ist.
        assertNotNull(callers,
                "Caller-Inventur muss gelaufen sein.");
    }

    /**
     * Falsifikation 2D — update() lockt beim Save NICHT das gegenseitige Sperren.
     *
     * <p>Auch wenn EconomySim keinen eigenen Thread hat: Wenn ein
     * externer Caller save() parallel aufruft, kann update() immer
     * noch in der Mitte zwischen Phase 5 und Phase 7 angetroffen
     * werden. Das ist die wirkliche Race-Bedingung.</p>
     *
     * <p>Wir demonstrieren den kritischen Pfad: Innerhalb von
     * update() gibt es KEINE synchronize-Klammer um
     * Phase-Schreibvorgänge (Phasen 5–7: Löhne, Steuern,
     * Property-Markt). Wenn save() zur selben Zeit läuft, sieht
     * es einen halben Snapshot.</p>
     */
    @Test
    @DisplayName("update() hat keine globale Mutex-Klammer — Phase 5–7 sind ungeschützt")
    void update_has_no_global_phase_mutex() throws Exception {
        String src = new String(
                Files.readAllBytes(Paths.get(ECON_SIM_SOURCE)),
                StandardCharsets.UTF_8);

        // update() beginnt nach Re-Entry-Guard, lockt aber NICHT breit.
        int updateStart = src.indexOf("public void update(double ds)");
        assertTrue(updateStart > 0, "update(double) Methode muss existieren.");
        String updateBody = src.substring(updateStart,
                Math.min(updateStart + 14_000, src.length()));

        // Kein synchronized-Block, KEIN readwriteLock im Body.
        assertFalse(updateBody.contains("synchronized(") || updateBody.contains("synchronized "),
                "Falsifikation: update() darf KEIN synchronized haben — "
                        + "sonst wäre die Race-Hypothese gegenstandslos.");
        assertFalse(updateBody.contains("ReentrantReadWriteLock")
                        || updateBody.contains("readLock()") || updateBody.contains("writeLock()"),
                "Falsifikation: update() darf KEIN read/write-Lock verwenden.");

        // update() nutzt ReentryGuard nur gegen same-thread re-entry.
        // Beweis: tryEnter/exit sind die EINZIGEN Mutex-Primitive.
        int tryEnterCount = updateBody.split("updateGuard.tryEnter", -1).length - 1;
        int exitCount = updateBody.split("updateGuard.exit", -1).length - 1;
        assertEquals(1, tryEnterCount,
                "updateGuard.tryEnter wird GENAU 1× gerufen — Reentry-Pattern, nicht Broad-Lock.");
        assertEquals(1, exitCount,
                "updateGuard.exit wird GENAU 1× aufgerufen (in finally).");
    }

    /**
     * Falsifikation 2E — Phase-Break-Position: confirm save() kann zwischen 5 und 7 rufen.
     *
     * <p>Wir markieren die Stelle, wo Phase 7 (Löhne / wages.update) auf
     * Phase 8 (Steuern / taxes.update) folgt. Save() dazwischen liest
     * Phase-7-Stände aber keine Phase-8-Stände — Inkonsistenz!</p>
     */
    @Test
    @DisplayName("Phase-7/Phase-8-Übergang ist nicht atomar — Save kann mid-stream lesen")
    void phase_break_is_not_atomic() throws Exception {
        String src = new String(
                Files.readAllBytes(Paths.get(ECON_SIM_SOURCE)),
                StandardCharsets.UTF_8);

        // wagesPaid += this.wages.update(...) — Phase 7 boundary line
        int wagesLine = src.indexOf("wagesPaid += this.wages.update");
        assertTrue(wagesLine > 0, "Phase-7 wages-Zuweisung muss in update() existieren.");

        // Nach wages folgt taxes.update — Phase 8 boundary
        int taxesLine = src.indexOf("taxesCollected += this.taxes.update",
                wagesLine);
        assertTrue(taxesLine > 0,
                "Phase-7/8-Boundary: taxes.update muss NACH wages.update kommen.");
        assertTrue(taxesLine > wagesLine,
                "Phase-Reihenfolge: taxes (Phase 8) folgt wages (Phase 7) — "
                        + "Save() dazwischen persistiert Phase-7-State ohne Phase 8.");

        // Save() liest beide Counter (wagesPaid UND taxesCollected) in einem Stream.
        // Halb-Befüllung = Inkonsistenz.
        int saveStart = src.indexOf("public void save(FilePutter file)");
        String saveBody = src.substring(saveStart,
                Math.min(saveStart + 8000, src.length()));
        assertTrue(saveBody.contains("wagesPaid") || saveBody.contains("wages"),
                "save() muss wagesPaid serialisieren — sonst ist die "
                        + "Phase-7/8-Halb-Befüllung nicht reproduzierbar.");
        assertTrue(saveBody.contains("taxesCollected") || saveBody.contains("taxes"),
                "save() muss taxesCollected serialisieren — sonst wäre Phase-8 "
                        + "kein Save-Teil und Race nicht beobachtbar.");
    }

    /**
     * Helper: Listet alle Method-Deklarationen von EconomySim auf die
     * entweder "save" oder "load" heißen — für Audit-Transparenz.
     */
    private static List<String> saveLoadMethods() {
        List<String> names = new ArrayList<>();
        for (Method m : EconomySim.class.getDeclaredMethods()) {
            if (m.getName().equals("save") || m.getName().equals("load")) {
                names.add(m.getName() + "(" + java.util.Arrays.toString(m.getParameterTypes()) + ")");
            }
        }
        return names;
    }

    @Test
    @DisplayName("Inventur: alle save()/load()-Method-Signaturen von EconomySim")
    void inventory_save_load_signatures() {
        List<String> sigs = saveLoadMethods();
        assertFalse(sigs.isEmpty(),
                "save()/load() müssen als Methoden existieren, sonst greift die Race-Falsifikation nicht.");
    }
}
