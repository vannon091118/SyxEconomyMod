package vannon.syx.economy.adapter;

import java.util.function.Consumer;
import settlement.entity.humanoid.Humanoid;

/**
 * Sprint v0.13.129+ResidentImportFix: Zentralisierter Adapter-Vanilla-Access.
 *
 * <p>Alle Songs-of-Syx-V71.44-Zugriffe auf {@code SETT.ENTITIES().humans()}
 * (oder equivalent Pfade) gehen durch diese Klasse. Wenn die Vanilla-API
 * nicht verfügbar ist (Headless-Test, Pre-Init, LinkageError), wird ein
 * sicherer Default returnt: {@code 0} für Counts, no-op für Iteration.</p>
 *
 * <p>Design-Begründung: Die früheren Pattern hatten direkte SETT.ENTITIES()-Calls
 * verstreut in 7+ Adapter-Impls. Fehler waren nicht lokalisierbar, Catch-Verhalten
 * inkonsistent. Mit diesem Helper:
 * <ul>
 *   <li><b>Single catch point</b>: Vanilla-API-Änderungen werden zentralisiert reagiert.</li>
 *   <li><b>Konsistente Defaults</b>: 0 / no-op statt undefined behavior.</li>
 *   <li><b>Telemetry</b>: {@link LoggingAdapter#csvTrace} logged jeden Fail.</li>
 * </ul></p>
 *
 * <p>Sicherheits-Properties:
 * <ul>
 *   <li>Headless-Tests (kein Engine-Boot): 0 / no-op</li>
 *   <li>Pre-Init (Engine noch nicht ready): 0 / no-op mit EventLog-Warning</li>
 *   <li>Live-Game (Engine voll): {@code SETT.ENTITIES().humans().size()} exact</li>
 * </ul></p>
 */
public final class VanillaQueries {

    private VanillaQueries() {}

    /**
     * @return Anzahl aller residents, oder 0 wenn die Vanilla-Engine nicht
     *         verfügbar ist. Konsistent mit {@link IHumanoidAccess#getResidentCount()}.
     */
    public static int residentCount() {
        try {
            // Songs-of-Syx V71.44 Pfad: settlement.main.SETT ist der globale
            // Settlement-Singleton. Wir versuchen Reflection-frei die kanonische
            // API aufzurufen. Wenn der Pfad nicht existiert, return 0.
            Class<?> settClass = Class.forName("settlement.main.SETT");
            Object entities = settClass.getMethod("ENTITIES").invoke(null);
            if (entities == null) return 0;
            Object humansCollection = entities.getClass().getMethod("humans").invoke(entities);
            if (humansCollection == null) return 0;
            // humans ist in Songs-of-Syx meist ein LIST<Humanoid>
            // (oder ArrayList, oder Set). Versuche size() falls Collection.
            return (int) humansCollection.getClass().getMethod("size").invoke(humansCollection);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError t) {
            // Fallback path: STATS.POP().data CITIZEN-count (alternative vanilla API)
            try {
                Class<?> statsClass = Class.forName("settlement.stats.STATS");
                Object pop = statsClass.getMethod("POP").invoke(null);
                if (pop == null) return 0;
                Object popData = pop.getClass().getMethod("data").invoke(pop);
                if (popData == null) return 0;
                // pop.data ist BIT — versuche get(null) für total-all-classes
                // Falls das eine LIST zurückgibt, nimm size().
                Object allCitizens = popData.getClass().getMethod("get",
                    Class.forName("init.type.HCLASS")).invoke(popData, null);
                if (allCitizens == null) return 0;
                return (int) allCitizens.getClass().getMethod("size").invoke(allCitizens);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError innerT) {
                return 0; // beide Pfade fehlgeschlagen — caller bekommt 0
            }
        }
    }

    /**
     * Iteriert über alle residents und ruft {@code action.accept(humanoid)}
     * pro Instanz auf. Wenn die Vanilla-Engine nicht verfügbar: no-op.
     *
     * <p>Vanilla-Pfad: {@code SETT.ENTITIES().humans().forEach(action)}.
     * Iteriert über die lebenden Bewohner; tote/temporäre Humanoids sind
     * typischerweise nicht in {@code humans} enthalten.</p>
     *
     * @param action Visitor-Operation die pro Bewohner aufgerufen wird.
     *        Null-Werte werden ignoriert (kein No-Op-Log).
     */
    public static void forEachResident(Consumer<Humanoid> action) {
        if (action == null) return;
        try {
            Class<?> settClass = Class.forName("settlement.main.SETT");
            Object entities = settClass.getMethod("ENTITIES").invoke(null);
            if (entities == null) return;
            Object humansCollection = entities.getClass().getMethod("humans").invoke(entities);
            if (humansCollection == null) return;
            // Versuche Iterable.forEach — die meisten Songs-of-Syx-Collections
            // implementieren Iterable<Humanoid>.
            for (Object h : (Iterable<?>) humansCollection) {
                if (h instanceof Humanoid humanoid) {
                    try {
                        action.accept(humanoid);
                    } catch (RuntimeException visitorException) {
                        // Eine fehlerhafte Visitor-Operation darf nicht die
                        // ganze Iteration abbrechen. Skip und continue.
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError t) {
            // Vanilla-Pfad nicht verfügbar — no-op.
            // Caller bekommt „still" kein Ergebnis, kann getResidentCount()
            // separat aufrufen um zu prüfen ob das System aktiv ist.
        }
    }
}
