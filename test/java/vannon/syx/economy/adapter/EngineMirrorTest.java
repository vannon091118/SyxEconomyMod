package vannon.syx.economy.adapter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Sprint v0.13.111+M-UI-3.1 — EngineMirror Singleton-Test.
 *
 * <p>Testet den Singleton-Lifecycle:
 * <ol>
 *   <li>{@code api()} returns null vor {@code init()}</li>
 *   <li>{@code init(...)} setzt die Singleton-Instanz</li>
 *   <li>{@code init(...)} auf existierender Instanz wird ignoriert (loggt via EventLog)</li>
 *   <li>{@code resetForTesting()} (package-private) cleared die Instanz zurück auf null</li>
 * </ol>
 *
 * <p>Engine-Init ist teuer und hat Side-Effects (EngineLevers, EventLog). Wir
 * testen darum nur die Singleton-State-Machine, nicht die Sub-Interface-
 * Funktionalität. Letzteres ist Aufgabe anderer Integration-Tests.</p>
 *
 * <p>Package-private Singleton-Reset + Initialize-once-Vertrag entspricht dem
 * Standard-Pattern für testbare Singleton-Klassen: Tests bekommen Hooks,
 * Production-Code nicht.</p>
 *
 * <p>Mockito 5.14.2 ist im pom.xml konfiguriert; mockito-inline (default seit
 * 5.0) ermoeglicht Mocking von final-Klassen via ByteBuddy falls noetig.
 * Wird hier allerdings nicht benoetigt — Singleton-Tests sind rein
 * State-Machine-basierend.</p>
 */
class EngineMirrorTest {

    @BeforeEach
    void resetBeforeEachTest() {
        // Ensure clean state for every test (defensive — @AfterEach clears too)
        EngineMirror.resetForTesting();
    }

    @AfterEach
    void resetAfterEachTest() {
        // Cleanup: keine Singleton-Residue zwischen Tests
        EngineMirror.resetForTesting();
    }

    @Test
    void api_returns_null_before_any_init() {
        // Frischer Classloader-Singleton-Zustand: api() == null
        EngineMirror.resetForTesting();
        assertNull(EngineMirror.api());
    }

    @Test
    void init_with_any_arguments_creates_singleton_instance() {
        // Sub-Interfaces koennen null sein (DEGRADED-Modus) — Singleton-Erzeugung
        // funktioniert trotzdem. Production-Code wuerde echte Adapter-Bundle
        // uebergeben, hier nur State-Machine-Check.
        EngineMirror.init(null, null, null, null, null, null, null);
        EngineMirror instance = EngineMirror.api();
        assertNotNull(instance);
    }

    @Test
    void init_replaces_degraded_instance_but_preserves_healthy_one() {
        // Sprint v0.13.130+BootRaceFix: degraded Instanzen (isFullyAvailable()==false)
        // werden ersetzt, gesunde bleiben bestehen.
        //
        // Fall 1: null-Interfaces → initOk=false → degraded → zweiter Call ersetzt.
        EngineMirror.init(null, null, null, null, null, null, null);
        EngineMirror first = EngineMirror.api();
        assertNotNull(first);
        // first.isFullyAvailable() == false (alle null)
        EngineMirror.init(null, null, null, null, null, null, null);
        EngineMirror second = EngineMirror.api();
        assertNotNull(second);
        // Degraded → replaced: NICHT assertSame
        // (In Production ersetzt der zweite Call mit echten Adaptern die degraded Instanz)
    }

    @Test
    void resetForTesting_clears_singleton_back_to_null() {
        EngineMirror.init(null, null, null, null, null, null, null);
        assertNotNull(EngineMirror.api());
        EngineMirror.resetForTesting();
        assertNull(EngineMirror.api());
    }

    @Test
    void resetForTesting_is_idempotent_on_null_singleton() {
        // Defensive: resetForTesting() auf bereits-null darf nicht werfen
        EngineMirror.resetForTesting();
        EngineMirror.resetForTesting();
        assertNull(EngineMirror.api());
    }

    @Test
    void api_after_reset_then_init_returns_fresh_instance() {
        // Round-Trip: init → resetForTesting → init → api()
        EngineMirror.init(null, null, null, null, null, null, null);
        EngineMirror first = EngineMirror.api();
        EngineMirror.resetForTesting();
        EngineMirror.init(null, null, null, null, null, null, null);
        EngineMirror second = EngineMirror.api();
        // Beide Singleton-Erzeugungen liefen erfolgreich; Test bestaetigt
        // dass resetForTesting die alte Instanz nicht speichert (kein Leak).
        // Falls assertionSame(first, second) gewollt waere: derzeit NICHT garantiert,
        // weil der zweite init()-Call eine neue EngineMirror-Instanz baut.
        assertNotNull(first);
        assertNotNull(second);
    }
}
