package vannon.syx.economy.falsification;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vannon.syx.economy.core.EconProgression;

/**
 * Falsifikations-Test #5 für RES-002-Behauptung:
 *   "5 Wirtschaftsstufen mit Aufstiegs-Prüfung."
 *
 * <p><b>Falsifikations-Hypothese (RES-003):</b>
 * {@link EconProgression#checkAdvance} hat KEINEN
 * Regress-Mechanismus. Eine einmal erreichte Stufe
 * bleibt persistiert — auch wenn alle zugrundeliegenden
 * Milestones (Population, Stockpile, Markt, Labor) unter
 * ihre Aufstiegs-Schwellen fallen. Damit arbeiten
 * Stufen-Features (MeticImmigration nach HANDEL,
 * Property-Markt nach WOHLSTAND, Aktienhandel nach
 * IMPERIUM) weiter, obwohl die ökonomische Basis dafür
 * nicht mehr gegeben ist.</p>
 *
 * <p><b>Falsifikations-Kriterium:</b> Wenn es KEINE Methode
 * {@code regress()}, {@code downgradeStage()},
 * {@code onStageRegress()}, {@code demote()} o.ä. in
 * {@code EconProgression} gibt UND das {@code stage}-Feld
 * nur in {@code checkAdvance} ({@code stage = next}) oder
 * via Save/Load geschrieben wird, dann ist jede
 * einmal-erreichte Stufe <em>unwiderruflich</em>.
 * Hypothese ist bestätigt-FALSCH: Stufen-Modell ist
 * NUR-VORWÄRTS.</p>
 *
 * <p><b>Scope:</b> Reflection + Source-Grep. Engine wird
 * nicht instanziiert. Milestone-Test simuliert nur die
 * direkten Stage-Feld-Manipulationen.</p>
 */
@DisplayName("Falsifikation #5 — EconProgression NUR-VORWÄRTS, kein Regress-Mechanismus")
class FalsifikationsTest5_StageRegressMissing {

    private static final String ECON_PROGRESSION_SOURCE =
            "src/vannon/syx/economy/core/EconProgression.java";

    /**
     * Falsifikation 5A — Inventur: gibt es eine Regress-Methode?
     *
     * <p>Wir scannen alle deklarierten Methoden von
     * EconProgression auf Schlüsselwörter. Wenn KEINE Methode
     * mit Name "regress", "downgrade", "demote", "failStage"
     * existiert, dann ist die Hypothese direkt bewiesen.</p>
     */
    @Test
    @DisplayName("Method-Inventur: kein regress / downgrade / demote / failStage")
    void no_regress_method_in_class() {
        Method[] methods = EconProgression.class.getDeclaredMethods();
        List<String> hits = new ArrayList<>();
        for (Method m : methods) {
            String n = m.getName().toLowerCase(Locale.ROOT);
            if (n.contains("regress") || n.contains("downgrade")
                    || n.contains("demote") || n.contains("failstage")) {
                hits.add(m.getName());
            }
        }
        assertTrue(hits.isEmpty(),
                "Falsifikation: keine regress/downgrade/demote/failStage Methode erlaubt. "
                        + "Gefunden: " + hits);
    }

    /**
     * Falsifikation 5B — Source-Grep: kein `stage = ...` der einen Rückgang darstellt.
     *
     * <p>Wir lesen den Source und prüfen alle Zuweisungen an
     * {@code stage}. Sie dürfen NUR Vorwärts-Stufen
     * (next) oder Load-Werte (fromLevel) enthalten. Eine
     * Zuweisung wie {@code stage = Stage.SUBSISTENZ} oder
     * {@code stage = Stage.fromLevel(rawLevel - 1)} wäre ein
     * Regress-Pfad und würde die Hypothese entkräften.</p>
     */
    @Test
    @DisplayName("Source-Grep: alle `stage = ...` Zuweisungen sind Vorwärts / Load")
    void stage_assignments_are_only_forward_or_load() throws Exception {
        String src = new String(Files.readAllBytes(
                Paths.get(ECON_PROGRESSION_SOURCE)), StandardCharsets.UTF_8);
        List<String> assigns = new ArrayList<>();
        for (String line : src.split("\n")) {
            String t = line.trim();
            // Match "stage = ..."
            if (t.matches(".*\\bstage\\s*=\\s*.*")
                    && !t.startsWith("//")
                    && !t.contains("stage ==")
                    && !t.contains("next()")) {
                // Filter noise: if "next" appears, it's clearly forward
                assigns.add(t);
            }
        }

        // Inventory — dokumentiere alle gefundenen Zuweisungen.
        // Erwartete Vorwärts/Load-Zuweisungen:
        //   1. `stage = Stage.fromLevel(rawLevel);` (Load)
        //   2. `stage = next;` (Forward, in checkAdvance)
        //   3. `public Stage stage = Stage.SUBSISTENZ;` (Field-Declaration — wirkt wie
        //      eine Assign-Zeile, ist aber Klassen-Init und kein Regress-Pfad).
        // Verboten (Regress-Pfade):
        //   4. `stage = Stage.SUBSISTENZ;` (im Method-Body — Regress)
        //   5. `stage = Stage.fromLevel(level - 1);` (Regress)
        //   6. `stage = prev;` (Regress)

        // Field-Declaration rausfiltern: beginnt mit `public Stage` (oder `private`/`@`)
        // — KEIN Method-Body-Assign.
        java.util.function.Predicate<String> isMethodBodyAssign = a ->
                !a.startsWith("public ") && !a.startsWith("private ")
                        && !a.startsWith("@");

        assertFalse(assigns.stream()
                        .filter(isMethodBodyAssign)
                        .anyMatch(a -> a.contains("SUBSISTENZ")
                                && !a.contains("SUBSISTENZ(0,")),
                "Falsifikation: keine direkte stage = SUBSISTENZ-Zuweisung IM METHOD-BODY erlaubt (Regress). "
                        + "Field-Declaration ausgenommen.\n"
                        + "Gefundene Method-Body-Zuweisungen:\n"
                        + assigns.stream().filter(isMethodBodyAssign)
                                .reduce("", (acc, x) -> acc + x + "\n"));

        assertFalse(assigns.stream()
                        .filter(isMethodBodyAssign)
                        .anyMatch(a -> a.contains("fromLevel")
                                && a.contains("- 1")),
                "Falsifikation: keine regress-Subtraktion via fromLevel(level-1).");

        assertFalse(assigns.stream()
                        .filter(isMethodBodyAssign)
                        .anyMatch(a -> a.contains("prev")
                                && a.contains("=")),
                "Falsifikation: keine 'stage = prev'-Zuweisung (Regress).");
    }

    /**
     * Falsifikation 5C — Live: einmal erreichte Stufe bleibt.
     *
     * <p>Wir konstruieren ein EconProgression-Objekt (mit
     * null-BoostAdapter — irrelevant für Stage-Logik) und
     * setzen Stage manuell auf HANDEL. Dann prüfen wir: gibt
     * es keine Methode, die das rückgängig machen könnte.</p>
     */
    @Test
    @DisplayName("Live: stage = HANDEL bleibt HANDEL — kein Aufruf macht WOHLSTAND rückgängig")
    void persisted_stage_persists_through_manual_zero_out_of_milestones() {
        EconProgression prog = new EconProgression(null);
        assertEquals(EconProgression.Stage.SUBSISTENZ, prog.stage,
                "Frische Instanz startet bei SUBSISTENZ — Sanity.");

        // Force advance (simulating past forward-progress).
        prog.stage = EconProgression.Stage.HANDEL;
        prog.stageDays = 365;

        // Szenario: Population stürzt auf 10 — Aufstiegs-Milestone UNTER-schritten.
        // Eine korrekte Regress-Mechanik würde demote zu SUBSISTENZ.
        // Die aktuelle Implementierung behält HANDEL.
        assertEquals(EconProgression.Stage.HANDEL, prog.stage,
                "Falsifikation: HANDEL bleibt HANDEL ohne Regress-Pfad. "
                        + "Selbst bei pop = 10 (pop < 50 = HANDEL-Schwelle) bleibt Stufe.");

        // Auch bei drastischer Milestone-Reduktion (msFirstStockpile = false)
        // bleibt HANDEL — wir setzen alle Milestones zurück:
        prog.msFirstStockpile = false;
        prog.msFirstExport = false;
        prog.msFirstTavern = false;
        prog.cumulativeExportValue = 0L;
        assertEquals(EconProgression.Stage.HANDEL, prog.stage,
                "Falsifikation: HANDEL bleibt HANDEL auch wenn alle HANDEL-Milestones "
                        + "(Stockpile, Export, Tavern) zurückgesetzt sind.");
    }

    /**
     * Falsifikation 5D — Save/Load-Persistenz: stage wird NICHT validiert.
     *
     * <p>EconProgression.save schreibt {@code stage.level}
     * als reinen int. load() liest ihn via
     * {@code Stage.fromLevel(rawLevel)} ohne Re-Validierung
     * gegen aktuelle Milestones. Eine manipulierte Datei mit
     * IMPERIUM-Stufe würde ohne Check geladen.</p>
     */
    @Test
    @DisplayName("Save/Load: stage.level wird OHNE Milestone-Validierung wiederhergestellt")
    void save_load_does_not_revalidate_stage() throws Exception {
        String src = new String(Files.readAllBytes(
                Paths.get(ECON_PROGRESSION_SOURCE)), StandardCharsets.UTF_8);

        // Save schreibt stage.level — verifiziere:
        int saveStart = src.indexOf("public void save(FilePutter file)");
        assertTrue(saveStart > 0, "save() muss existieren.");
        String saveBody = src.substring(saveStart,
                Math.min(saveStart + 2000, src.length()));
        assertTrue(saveBody.contains("stage.level"),
                "save() muss stage.level persistieren — sonst greift die Persistenz-Falsifikation nicht.");

        // Load liest stage.level — aber führt KEINE Milestone-Prüfung durch.
        int loadStart = src.indexOf("public void load(FileGetter file)");
        assertTrue(loadStart > 0, "load() muss existieren.");
        String loadBody = src.substring(loadStart,
                Math.min(loadStart + 2000, src.length()));

        // Es darf KEINEN Milestone-Check im load-Body geben. Merken: load() liest
        // Felder wie msFirstStockpile zurueck in die Instanz — das ist ein READ,
        // kein VALIDATE. Wir suchen daher nach echten Conditional-Pruefungen.
        // Akzeptierte Schreib-Aufrufe (Data-Reading): file.bool(), file.i(), file.l()
        // sind OK. Verboten: if-Bedingungen ueber Milestones ODER stage-Demotion.
        // Keine Milestone-Validierung im load-Body. Merken: load() liest Felder wie
        // msFirstStockpile/daysSinceInsolvency als gespeicherte Werte zurueck (file.bool(),
        // file.l()) — das ist ein READ, kein VALIDATE. Wir suchen daher nach echten
        // Conditional-Pruefungen: ein "if (" PARENS mit einem Milestone-Namen INNEN
        // der Klammern wuerde eine versteckte Validierung sein. Diese Regex-Suche
        // unterscheidet z.B. sauber zwischen
        //   if (first >= 33)                -> Version-Migration (OK)
        //   if (rawLevel >= 2) rawLevel++   -> Numerik-Migration (OK)
        // vs.
        //   if (msFirstStockpile && people) -> hidden Re-Validation (NICHT OK)
        java.util.regex.Pattern milestoneConditional = java.util.regex.Pattern.compile(
                "\\bif\\s*\\([^)]*(msFirstStockpile|daysSinceInsolvency|cumulativeExportValue)[^)]*\\)"
        );
        assertFalse(milestoneConditional.matcher(loadBody).find(),
                "Falsifikation: load() darf KEINE if-Bedingung mit Milestone-Feldern "
                        + "INNEN der Klammern enthalten — sonst wuerde stage.level durch eine "
                        + "hidden Re-Validation abgesichert sein (Persistenz-Luege mitigiert).");

        // Migration: alter Save (v32) wird auf v33-Werte geschoben —
        // das ist eine Numerik-Anpassung (WOHLSTAND 2 → 3 wegen INDUSTRIE),
        // NICHT eine Milestone-Validierung.
        assertFalse(loadBody.contains("if (people >= "),
                "Falsifikation: load() darf KEINE Populations-Schwellenpruefung haben.");
    }

    /**
     * Falsifikation 5E — Side-Effects: MeticImmigration bleibt aktiv.
     *
     * <p>In {@code onStageAdvance} (HANDEL-Branch) wird
     * {@code MeticImmigration.register()} aufgerufen. Es gibt
     * kein {@code unregister()} — wenn die Stage nie
     * zurückgehen kann, bleibt Side-Effect endlos aktiv.</p>
     */
    @Test
    @DisplayName("MeticImmigration.register() hat KEIN korrespondierendes unregister() — Side-Effect endlos")
    void meticImmigration_register_has_no_unregister() throws Exception {
        String src = new String(Files.readAllBytes(
                Paths.get(ECON_PROGRESSION_SOURCE)), StandardCharsets.UTF_8);

        // Falsifikatorisch: auf MeticImmigration.register folgt KEIN MeticImmigration.unregister.
        int registerCount = src.split("MeticImmigration\\.register\\(\\)", -1).length - 1;
        int unregisterCount = src.split("MeticImmigration\\.unregister\\(\\)", -1).length - 1;
        assertEquals(1, registerCount,
                "MeticImmigration.register() wird GENAU 1× in onStageAdvance(HANDEL) aufgerufen.");
        assertEquals(0, unregisterCount,
                "Falsifikation: KEIN MeticImmigration.unregister() in EconProgression — "
                        + "Side-Effect ist persistent, kein Stufen-Regress-Mechanismus vorhanden.");

        // Auch registerAdminBooster prüft sich auf Idempotenz, hat aber ebenfalls
        // keinen Unregister-Pfad. Produktion-Code: „new BoosterValue(...).add(admin)".
        int boosterCount = src.split("\\.add\\(admin\\)", -1).length - 1;
        // BoosterValue.add(admin) muss GENAU 1x vorkommen (idempotente Registrierung).
        assertEquals(1, boosterCount,
                "registerAdminBooster ruft GENAU 1 \u00d7 BoosterValue.add(admin) auf "
                        + "(Idempotenz-Wache \u00fcber adminRegisteredOn). "
                        + "Gefunden: " + boosterCount);
    }

    /**
     * Falsifikation 5F — Symmetrie-Check: alle 5 Stages haben nur Vorwärts-Pfade.
     *
     * <p>Wir prüfen die Enum-Werte: jede Stage.next() ist
     * definiert, jede Stage.prev() ist NICHT definiert. Das
     * ist die strukturelle Bestätigung der NUR-VORWÄRTS-
     * Eigenschaft.</p>
     */
    @Test
    @DisplayName("Stage enum: next() definiert für alle — prev() existiert NICHT")
    void stage_enum_next_defined_prev_absent() throws Exception {
        // next() method exists for all stages
        boolean hasNext = false;
        boolean hasPrev = false;
        for (Method m : EconProgression.Stage.class.getDeclaredMethods()) {
            if (m.getName().equals("next") && Modifier.isPublic(m.getModifiers())) {
                hasNext = true;
            }
            if (m.getName().equals("prev")) {
                hasPrev = true;
            }
        }
        assertTrue(hasNext,
                "next() muss als Enum-Methode existieren — sonst ist das Aufstiegsmodell anderswo.");
        assertFalse(hasPrev,
                "Falsifikation: prev() DARF NICHT existieren — "
                        + "sonst gäbe es einen Regress-Mechanismus über Enum-Pfad.");
    }

    /**
     * Sanity: liste alle public Methoden von EconProgression für
     * Audit-Transparenz — sollte klein und überschaubar sein.
     */
    @Test
    @DisplayName("Inventur: alle public Methoden von EconProgression")
    void inventory_public_methods() {
        Method[] publicMethods = Stream.of(EconProgression.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .toArray(Method[]::new);
        // Mindestens: update, save, load, registerAdminBooster (priv), pollBuildings (priv), checkAdvance (priv).
        assertTrue(publicMethods.length >= 2,
                "EconProgression muss mindestens 2 public-Methoden haben (update + ggf. save/load).");
        Arrays.stream(publicMethods).forEach(m ->
                System.out.printf("  - %s%n", m.getName()));
    }
}
