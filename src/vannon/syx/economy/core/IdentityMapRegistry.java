package vannon.syx.economy.core;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * v0.1.3 (Phase-4.7-Blocker #8) — Save/Load-Schutznetz für Karten mit
 * Referenz-basierten Schlüsseln.
 *
 * <p>Wenn Songs of Syx lädt, instanziiert die Engine {@code RoomInstance},
 * {@code StockpileInstance}, {@code Induvidual}, {@code Humanoid},
 * {@code RoomBlueprintImp} neu. Reference-Equality ist danach weg, Lookups
 * liefern still {@code null}, Daten verschwinden ohne Fehler-Log.</p>
 *
 * <p>Die Registry zentralisiert Save/Load-Clear: jeder betroffene Map wird
 * einmal registriert; nach jedem Load ruft {@code EconomySim.load()} den
 * Hook und cleart die Maps mit stderr-Warnung — Datenverlust sichtbar
 * statt silent. Längerfristig (Phase 4.7): Migration auf
 * {@code HashMap<Long, X>} via {@link IdentityKeys}.</p>
 */
public final class IdentityMapRegistry {

    private static final CopyOnWriteArrayList<Registered> entries = new CopyOnWriteArrayList<>();

    private IdentityMapRegistry() {}

    public record Registered(String owner, String fieldName, Runnable clearer) {}

    /**
     * Registriert eine Map für Save/Load-Clear. Der konkrete Map-Typ ist
     * irrelevant ({@code IdentityHashMap}, {@code HashMap}, …); die Registry
     * hält nur den {@code clear}-Runnable.
     */
    public static void register(String owner, String fieldName, Map<?, ?> map) {
        if (map == null) return;
        entries.add(new Registered(owner, fieldName, map::clear));
    }

    /** Cleart alle registrierten Maps und loggt pro Eintrag eine Warnung. */
    public static void clearOnLoad(String triggerReason) {
        for (Registered r : entries) {
            try {
                r.clearer().run();
                System.err.println("[ECON] IdentityMapRegistry: cleared " + tag(r) + " on " + triggerReason);
            } catch (Throwable t) {
                System.err.println("[ECON] IdentityMapRegistry: failed to clear " + tag(r) + " — " + t.getMessage());
            }
        }
    }

    private static String tag(Registered r) {
        return r.owner() + "." + r.fieldName();
    }

    /** Test-Hooks. */
    public static int size() { return entries.size(); }
    public static void resetForTests() { entries.clear(); }

    /**
     * Save-Drift-Detection (Plan-Amendment 1): Liefert einen stabilen Hash über alle
     * registrierten Map-Identitäten (owner + fieldName + size). Wenn ein Save-File
     * denselben Hash vor und nach Load liefert, hat sich die Map-Set nicht verändert.
     * Eine Hash-Differenz ist ein Signal dass eine Map neu hinzugekommen (Register),
     * weggefallen oder umbenannt wurde — und damit ein Indikator dass Save/Load-Logic
     * veraltet ist oder eine neue Saveable ohne {@code clearOnLoad}-Cover gestartet
     * wurde (z.B. die zukünftige {@code CitizenStateTable} aus Phase 5a).
     *
     * <p><b>Wichtig:</b> Wir hashen NICHT die Map-Inhalte (keine Reflection, kein
     * Deep-Walk). Es bleibt ein Structural-Hash für Save-Format-Drift-Detection.</p>
     */
    public static int snapshotHash() {
        int combined = 0;
        for (Registered r : entries) {
            int ownerHash = r.owner().hashCode();
            int fieldHash = r.fieldName().hashCode();
            combined = 31 * combined + ownerHash * 31 + fieldHash;
        }
        return combined;
    }
}
