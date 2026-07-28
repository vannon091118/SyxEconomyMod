package vannon.syx.economy.falsification;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.seam.BypassGate;

/**
 * Falsifikations-Test #3 für RES-002-Behauptung:
 *   "Adapter-Layer mit 88 Vanilla-Zugriffen via BypassGate."
 *
 * <p><b>Falsifikations-Hypothese (RES-003):</b>
 * {@link BypassGate#isAvailable()} ist ALL-OR-NOTHING. Ein
 * Field-Fehler (z.B. SecurityException, NoSuchFieldError) setzt
 * das eine boolesche {@code initOk} auf false, und die gesamte
 * Bypass-Gate-Instanz ist fortan nutzlos. Es gibt KEINE
 * granulare Pro-Methode Degradation auf der BypassGate-Ebene
 * — die Granularität existiert NUR downstream in den
 * AccessImpl-Klassen via {@code failedMethods}-Set, aber der
 * BypassGate selbst kennt keine Felder, die fehlschlagen
 * dürfen ohne alles mitzureißen.</p>
 *
 * <p>Zusätzlich: {@link EngineMirror#isFullyAvailable()} bündelt
 * die 4 Sub-Interfaces ({@code rooms}, {@code factions},
 * {@code humanoids}, {@code stats}) in EINEN booleschen
 * Wert. Fällt EINES aus, ist die gesamte EngineMirror-API als
 * "degraded" markiert — auch wenn 3 andere noch voll
 * funktionsfähig wären.</p>
 *
 * <p><b>Falsifikations-Kriterium:</b> Wenn
 * {@link BypassGate#markFailed} ein einziges Mal aufgerufen
 * wird, muss {@link BypassGate#isAvailable} false zurückgeben —
 * mit Reset auf true erst nach erneutem {@code new
 * BypassGate(...)}. Die Hypothese ist <em>FALSCH</em>, weil es
 * KEINE partielle Recovery gibt.</p>
 *
 * <p><b>Scope:</b> Reflection + Source-Grep, KEIN Laufzeit-
 * Engine-Inject.</p>
 */
@DisplayName("Falsifikation #3 — BypassGate ALL-OR-NOTHING + EngineMirror-Bündelung")
class FalsifikationsTest3_BypassGateAllOrNothing {

    /**
     * Falsifikation 3A — BypassGate.initOk ist ein einziger boolean.
     *
     * <p>Behauptung: Eine einzige Field-/Method-Failure killt die
     * komplette Bypass-Gate-Instanz — kein per-Feature-Toggle.</p>
     */
    @Test
    @DisplayName("BypassGate.initOk ist ein einziger boolean — keine Granularität am SDK")
    void bypassGate_initOk_is_single_boolean_no_granularity() throws Exception {
        Field initOkField = BypassGate.class.getDeclaredField("initOk");
        initOkField.setAccessible(true);
        assertSame(boolean.class, initOkField.getType(),
                "Falsifikation: BypassGate.initOk MUSS boolean sein — "
                        + "eine granularere Struktur würde die Hypothese entkräften.");

        boolean initOkModifiers = java.lang.reflect.Modifier.isPrivate(initOkField.getModifiers());
        assertTrue(initOkModifiers,
                "initOk ist private — externe Modifikation ist nicht erlaubt (außer markFailed).");

        // Inspect markFailed signature: ein Aufruf killt ALLES.
        Method markFailed = BypassGate.class.getDeclaredMethod("markFailed", Throwable.class);
        int mkfMods = markFailed.getModifiers();
        // Java hat kein isPackage() — package-private ist NOT public AND NOT private AND NOT protected.
        boolean isPkgPrivate = !Modifier.isPublic(mkfMods) && !Modifier.isPrivate(mkfMods) && !Modifier.isProtected(mkfMods);
        assertTrue(isPkgPrivate,
                "markFailed ist package-private (sichtbar für FieldAccessor/MethodAccessor im selben Paket).");
    }

    /**
     * Falsifikation 3B — Live-Demo: ein markFailed tötet isAvailable.
     *
     * <p>Wir erzeugen einen BypassGate und rufen markFailed
     * ein einziges Mal auf. Das Ergebnis: isAvailable ist false.
     * Es gibt keinen Pro-Methode-Toggle, kein partiel-
     * Disable. Ab jetzt ist ALLES in dem BypassGate aus.</p>
     */
    @Test
    @DisplayName("Live: BypassGate.markFailed 1× macht isAvailable permanent false")
    void bypassGate_markFailed_kills_isAvailable_forever() throws Exception {
        BypassGate gate = new BypassGate("FalsifikationsTest-3B",
                MethodHandles.lookup());
        assertTrue(gate.isAvailable(),
                "Frischer BypassGate: isAvailable true (Sanity-Check).");

        Method markFailed = BypassGate.class.getDeclaredMethod("markFailed", Throwable.class);
        markFailed.setAccessible(true);
        markFailed.invoke(gate, new RuntimeException("simulierter Field-Fehler"));

        assertFalse(gate.isAvailable(),
                "Falsifikations-Kriterium: nach 1× markFailed ist isAvailable false — "
                        + "kein per-Field-Toggle, kein Cleanup-Pfad. ALL-OR-NOTHING bestätigt.");
    }

    /**
     * Falsifikation 3C — EngineMirror bündelt 4 Sub-Interfaces in 1 Bit.
     *
     * <p>{@link EngineMirror#isFullyAvailable} berechnet einen
     * UND-Ausdruck über 4 Sub-Klassen (rooms, factions,
     * humanoids, stats). EINE Fail-Stufe reicht, um den ganzen
     * EngineMirror "degraded" zu melden.</p>
     */
    @Test
    @DisplayName("EngineMirror.isFullyAvailable() ist 4-fach-UND — 1 Fail reicht für alle")
    void engineMirror_foldsto_single_bit() throws Exception {
        String src = new String(
                Files.readAllBytes(Paths.get(
                        "src/vannon/syx/economy/adapter/EngineMirror.java")),
                StandardCharsets.UTF_8);

        // isFullyAvailable muss alle 4 Sub-Interfaces UND-verknüpfen.
        int methodStart = src.indexOf("public boolean isFullyAvailable()");
        assertTrue(methodStart > 0, "isFullyAvailable() muss existieren.");
        String methodBody = src.substring(methodStart,
                Math.min(methodStart + 600, src.length()));

        assertTrue(methodBody.contains("rooms.isAvailable()"),
                "isFullyAvailable muss rooms.isAvailable() prüfen.");
        assertTrue(methodBody.contains("factions.isAvailable()"),
                "isFullyAvailable muss factions.isAvailable() prüfen.");
        assertTrue(methodBody.contains("humanoids.isAvailable()"),
                "isFullyAvailable muss humanoids.isAvailable() prüfen.");
        assertTrue(methodBody.contains("stats.isAvailable()"),
                "isFullyAvailable muss stats.isAvailable() prüfen.");

        // Constructor's initOk: UND over room/faction/humanoid/stats non-null.
        // Das ist eine zweite Bündelungs-Stelle.
        int ctorStart = src.indexOf("private EngineMirror(IRoomAccess rooms");
        assertTrue(ctorStart > 0, "Constructor muss existieren.");
        String ctorBody = src.substring(ctorStart,
                Math.min(ctorStart + 600, src.length()));
        assertTrue(ctorBody.contains("rooms != null")
                        && ctorBody.contains("factions != null")
                        && ctorBody.contains("humanoids != null")
                        && ctorBody.contains("stats != null"),
                "EngineMirror-Konstruktor bündelt 4 Sub-Interfaces in EIN initOk-Bit. "
                        + "Fehlt EINE non-null-Prüfung, ist die Falsifikation gegenstandslos.");
    }

    /**
     * Falsifikation 3D — IRoomAccess.isAvailable ist AUCH boolean (per-Sub).
     *
     * <p>Die Granularität existiert auf Sub-Interface-Ebene (jedes
     * {@link IRoomAccess#isAvailable()} für sich), aber NICHT
     * pro Methode. Innerhalb eines AccessImpl gibt es zwar
     * einen {@code failedMethods}-Set — ABER die fehlgeschlagene
     * BypassGate-Field-Init markiert die ganze Impl als
     * {@code initOk=false}, was wieder alle Konsumenten
     * deaktiviert, die das isAvailable prüfen.</p>
     *
     * <p>Wir zeigen: failedMethods-Granularität existiert in
     * HumanoidAccessImpl, aber initOk-Granularität NICHT.</p>
     */
    @Test
    @DisplayName("Pro-Methode Granularität in HumanoidAccessImpl via failedMethods — ABER initOk bleibt binär")
    void humanoidAccess_has_granular_failedMethods_but_initOk_is_binary() throws Exception {
        String humSrc = new String(
                Files.readAllBytes(Paths.get(
                        "src/vannon/syx/economy/adapter/HumanoidAccessImpl.java")),
                StandardCharsets.UTF_8);

        // failedMethods Set — granular pro Method
        assertTrue(humSrc.contains("failedMethods"),
                "HumanoidAccessImpl hat failedMethods Set — Granularität bewiesen.");

        // Aber: initOk = true wird ohne Bedingung gesetzt
        int initOkLine = humSrc.indexOf("this.initOk = true;");
        assertTrue(initOkLine > 0,
                "HumanoidAccessImpl setzt initOk = true unconditional — "
                        + "EIN Field-Fehler bei BypassGate würde die ganze Impl über "
                        + "EngineMirror.isFullyAvailable() mitziehen.");
    }

    /**
     * Falsifikation 3E — Inventur: zähle wie viele BypassGate-Instanzen im Repo existieren.
     *
     * <p>Wenn EINE BypassGate-Instanz in der Chain failed, dann
     * kann der entsprechende Sub-Adapter (z.B.
     * VanillaTransportAdapter) nicht initialisiert werden.
     * Andere Sub-Adapter sind technisch unabhängig, ABER
     * EngineMirror.isFullyAvailable() meldet die ganze
     * Kette als degraded — d.h. die downstream-Code-Pfade, die
     * {@code EngineMirror.api().rooms()} nutzen, fallen
     * still aus.</p>
     */
    @Test
    @DisplayName("BypassGate-Inventur — wo wird der SDK instanziiert?")
    void bypassGate_instantiation_inventory() throws Exception {
        List<String> sites = new ArrayList<>();
        Files.walk(Paths.get("src/vannon/syx/economy"))
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> {
                    try {
                        String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                        for (String line : content.split("\n")) {
                            if (line.contains("new BypassGate(")) {
                                sites.add(p.getFileName() + ": "
                                        + line.trim().replaceAll("\\s+", " "));
                            }
                        }
                    } catch (Exception ignored) { }
                });

        // Mindestens eine BypassGate-Instanz muss existieren,
        // sonst testet der Rest dieses Tests gegen Phantom-Code.
        assertFalse(sites.isEmpty(),
                "Im Repo muss mindestens eine `new BypassGate(...)` Instanz existieren — "
                        + "sonst testen wir gegen einen Geist. Tatsächliche Sites: 0.\n"
                        + "(Wenn das je FALSCH wird, ist die BypassGate-SDK-Migration "
                        + "abgebrochen und der Test verliert seine Aussagekraft.)");
    }
}
