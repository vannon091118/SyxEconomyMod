package vannon.syx.economy.adapter;

import init.type.NEED;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.room.main.RoomInstance;
import settlement.stats.service.StatService;
import java.util.function.Consumer;

/**
 * Humanoid-Zugriff — Arbeitsstatistiken, Hunger, Bedürfnisse, Religion,
 * Sklaverei, Service-Erfüllung, Sozialhierarchie und AI-Plan-Management.
 *
 * <p>Teil des EngineMirror. Jeder Zugriff wird über {@code EngineLevers}
 * getogglet und via {@code LoggingAdapter} geloggt.</p>
 *
 * <p>Alle Methoden verwenden direkte Compilezeit-Links auf die Vanilla-Engine
 * (NEEDS, STATS, HTYPES, HPoll, RELIGIONS). Nur AI-Plan-Klassen sind
 * package-private und werden via {@code ClassResolver} aufgelöst.</p>
 *
 * <p>Version: V71.44. Bei Vanilla-Update V72 müssen die Compilezeit-Links
 * geprüft werden — jeder Zugriff kann einzeln per EngineLevers abgeschaltet
 * werden.</p>
 */
public interface IHumanoidAccess {

    /**
     * @return true wenn die Humanoid-Engine verfügbar ist
     *         (SETT.ENTITIES() != null).
     */
    boolean isAvailable();

    // ═══ Resident Enumeration ═════════════════════════════════
    // Sprint v0.13.129+ResidentImportFix: Aggregat- und Iterations-API
    // für Subsysteme die "alle Bewohner" brauchen (Census-View,
    // Demographie-Walk, Cleaning-Pass). Vorher nur insel-query API
    // (Humanoid → property); ohne Enumeration konnten Census-Code-Pfade
    // nicht durchlaufen was zu "Bewohner werden nicht erkannt" führte.

    /**
     * Gesamtzahl aller residenten Humanoid-Instanzen (alle Klassen,
     * alle Rassen, alle Sub-Types wie SLAVE/CHILD etc.).
     *
     * <p>Vanilla-Pfad: {@code SETT.ENTITIES().humans().size()} oder
     * Equivalent (z. B. STATS.POP().POP.data CITIZEN + SLAVE + ...).</p>
     *
     * <p>Stub-Pfad: {@code MockWorldState.citizenCount} (zero-sum
     * pairwise-lottery population).</p>
     *
     * @return Anzahl Bewohner, 0 wenn Humanoid-Engine nicht verfügbar.
     */
    int getResidentCount();

    /**
     * Iteriert über alle residenten Humanoid-Instanzen. Visitor-Pattern
     * damit's speichereffizient bleibt und kein Vanilla-Iterable durch
     * den Mod-Kern geleakt wird.
     *
     * <p>Vanilla-Pfad: {@code SETT.ENTITIES().humans().forEach(action)}.</p>
     *
     * <p>Stub-Pfad: no-op Consumer oder iterator über
     * {@code MockWorldState}-Mock-Instanzen.</p>
     *
     * @param action Visitors-Operation die pro Bewohner aufgerufen wird.
     */
    void forEachResident(Consumer<Humanoid> action);

    // ═══ Employment & Labor ═════════════════════════════════

    /**
     * Prüft ob der Humanoid gerade arbeitet.
     * Nutzt {@code HPoll.Handler.works()}.
     *
     * @param humanoid der zu prüfende Humanoid
     * @return true wenn er gerade arbeitet
     */
    boolean isWorking(Humanoid humanoid);

    /**
     * Prüft ob der Humanoid arbeitsfähig ist.
     * {@code hType.isWorks() && clas() != SLAVE}.
     *
     * @param humanoid der zu prüfende Humanoid
     * @return true wenn arbeitsfähig
     */
    boolean isEmployableWorker(Humanoid humanoid);

    /**
     * Prüft ob der Humanoid ein arbeitsfähiger Arbeitsloser ist.
     * {@code isEmployableWorker && employedRoom == null}.
     *
     * @param humanoid der zu prüfende Humanoid
     * @return true wenn arbeitslos und arbeitsfähig
     */
    boolean isSurplusLaborer(Humanoid humanoid);

    /**
     * Liefert den Arbeitsraum des Humanoids.
     * {@code (RoomInstance) STATS.WORK().EMPLOYED.get(indu)}.
     *
     * @param humanoid der zu prüfende Humanoid
     * @return der Arbeitsraum oder null
     */
    RoomInstance getEmployedRoom(Humanoid humanoid);

    // ═══ Hunger & Needs ═════════════════════════════════════

    /**
     * Liefert den rohen Hunger-Wert (0–100+).
     * {@code NEEDS.TYPES().HUNGER.stat().stat().indu().get(indu)}.
     *
     * @param humanoid der zu prüfende Humanoid
     * @return roher Hunger-Wert
     */
    int getHungerRaw(Humanoid humanoid);

    /**
     * Setzt den rohen Hunger-Wert.
     *
     * @param humanoid der zu verändernde Humanoid
     * @param value neuer Hunger-Wert
     */
    void setHungerRaw(Humanoid humanoid, int value);

    /**
     * Liefert die Priorität eines Need-Events für diesen Humanoid.
     * {@code NEED_E.stat().getPrio(humanoid)}.
     *
     * @param humanoid der zu prüfende Humanoid
     * @param need der Need-Typ (muss NEED_E sein)
     * @return Prioritätswert oder -1 bei Fehlern
     */
    int getEventNeedPriority(Humanoid humanoid, NEED need);

    // ═══ Service Fulfilment ═════════════════════════════════

    /**
     * Liefert den Service-Erfüllungsgrad (0.0–1.0).
     * {@code service.total().indu().getD(indu)}.
     *
     * @param humanoid der zu prüfende Humanoid
     * @param service der Service-Typ (z.B. Tavern-Service)
     * @return Erfüllungsgrad 0.0–1.0 oder -1.0 bei Fehler
     */
    double getServiceFulfilment(Humanoid humanoid, StatService service);

    // ═══ Social Hierarchy ═══════════════════════════════════

    /**
     * Liefert den lebenden Elternteil eines Kindes.
     * {@code STATS.REL().humanParent(child)}.
     *
     * @param child das Kind
     * @return der Elternteil oder null
     */
    Humanoid getLivingParent(Humanoid child);

    // ═══ Religion ════════════════════════════════════════════

    /**
     * Liefert den Religion-Index des Humanoids.
     * {@code STATS.RELIGION().getter.get(indu).religion.index()}.
     *
     * @param humanoid der zu prüfende Humanoid
     * @return Religion-Index oder -1
     */
    int getReligionIndexOf(Humanoid humanoid);

    /**
     * Konvertiert den Humanoid zu einer Religion.
     * {@code STATS.RELIGION().getter.set(indu, stat)}.
     *
     * @param humanoid der zu konvertierende Humanoid
     * @param religionIndex Index der Ziel-Religion
     */
    void convertTo(Humanoid humanoid, int religionIndex);

    // ═══ Slavery ════════════════════════════════════════════

    /**
     * Prüft ob der Humanoid versklavbar ist.
     * {@code hType == HTYPES.SUBJECT()}.
     *
     * @param humanoid der zu prüfende Humanoid
     * @return true wenn versklavbar
     */
    boolean isEnslaveablePleb(Humanoid humanoid);

    /**
     * Versklavt den Humanoid.
     * {@code hTypeSet(humanoid, HTYPES.SLAVE(), null, null)}.
     *
     * @param humanoid der zu versklavende Humanoid
     */
    void enslave(Humanoid humanoid);

    // ═══ AI Plan Management ═════════════════════════════════

    /**
     * Überschreibt den AI-Plan des Humanoids.
     * {@code AIManager.overwrite(humanoid, plan)}.
     *
     * <p>Verwende {@code PlanCatalog} um Plan-Instanzen zu erzeugen.</p>
     *
     * @param humanoid der zu verändernde Humanoid
     * @param plan der neue AI-Plan
     */
    void overwritePlan(Humanoid humanoid, AIPLAN plan);

    /**
     * Zugriff auf den PlanCatalog — Discovery- und Factory-Interface
     * für Vanilla AI-Plan-Klassen (alle package-private, via ClassResolver).
     *
     * @return der PlanCatalog oder null wenn nicht initialisiert
     */
    PlanCatalog planCatalog();

    /**
     * Discovery- und Factory-Interface für Vanilla AI-Plan-Klassen.
     *
     * <p>Alle 6+ AI-Plan-Klassen sind package-private in
     * {@code settlement.entity.humanoid.ai.work}. ClassResolver
     * löst sie zur Laufzeit auf. {@code create()} instantiiert
     * via Constructor(String key).</p>
     *
     * <p>Hinweis: BOOSTABLES-Zugriffe (CIVICS.GOV, BEHAVIOUR.LOYALTY etc.)
     * werden in {@code IStatsAccess} (A-04b) implementiert, da sie globale
     * Spielstatistiken sind und nicht pro-Humanoid. Die EngineLevers-Toggles
     * {@code boostingGovEnabled} etc. sind bereits vorhanden.</p>
     */
    interface PlanCatalog {

        /**
         * @return Anzahl der aufgelösten Plan-Klassen
         */
        int resolvedCount();

        /**
         * @return true wenn der ClassResolver funktioniert hat
         */
        boolean isAvailable();

        /**
         * Liefert eine aufgelöste Plan-Klasse oder null.
         *
         * @param simpleName Klassenname (z.B. "PlanOddjobber")
         * @return die Klasse oder null
         */
        Class<?> lookup(String simpleName);

        /**
         * Erzeugt eine Plan-Instanz via Constructor(String key).
         *
         * @param simpleName Klassenname
         * @param key Plan-Key (z.B. "WORK")
         * @return die Instanz oder null
         */
        AIPLAN create(String simpleName, String key);
    }
}
