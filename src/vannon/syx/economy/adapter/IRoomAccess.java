package vannon.syx.economy.adapter;

import init.resources.RESOURCE;
import settlement.entity.humanoid.Humanoid;
import settlement.room.home.chamber.ROOM_CHAMBER;
import settlement.room.home.house.ROOM_HOME;
import settlement.room.infra.janitor.ROOM_JANITOR;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.room.service.food.canteen.ROOM_CANTEEN;
import settlement.room.service.food.eatery.ROOM_EATERY;
import settlement.room.service.module.RoomService;
import snake2d.util.sets.LIST;

/**
 * EngineMirror Sub-Interface für alle Siedlungsraum-Zugriffe.
 *
 * <p>Bündelt die Zugriffe von {@link ISyxWarehouse}, {@link ISyxTransport}
 * und den room-bezogenen Vanilla-Engine-Zugriffen (ehem. {@code EngineSeams}-Klasse,
 * entfernt in Sprint v0.13.119+B-008-Phase-2) in eine einheitliche Fassade.
 * Jeder Zugriff wird über {@code EngineLevers} konfigurierbar und via
 * {@code LoggingAdapter} geloggt.</p>
 *
 * <p>Implementierung: {@link RoomAccessImpl}. B-008 in Sprint v0.13.119
 * abgeschlossen — alle direkten {@code EngineSeams}-Aufrufe sind migriert.</p>
 *
 * <p>Version-gebunden für Songs of Syx V71.44.</p>
 */
public interface IRoomAccess {

    // ─── Availability ───────────────────────────────────────

    /**
     * @return true wenn mindestens die kritischen BypassGate-Accessor
     *         erfolgreich initialisiert wurden. Consumer sollten diesen
     *         Check vor der Nutzung durchführen.
     */
    boolean isAvailable();

    // ─── Stockpile Read ─────────────────────────────────────

    /**
     * Lager-Füllstand als Verhältnis 0.0–1.0.
     * @param stockpile die zu lesende Lager-Instanz
     * @param res die Ressource (darf null sein → Gesamtfüllstand)
     * @return Füllstand oder 1.0 wenn nicht lesbar
     */
    double getStoredRatio(StockpileInstance stockpile, RESOURCE res);

    /**
     * Gesamte belegte Lagerkapazität als Verhältnis.
     * @return 0.0–1.0 oder 0.0 wenn nicht lesbar
     */
    double getUsedSpace(StockpileInstance stockpile);

    /**
     * Crate-Kapazität (alle Ressourcen).
     */
    int getCrateSize(StockpileInstance stockpile);

    /**
     * Crate-Kapazität für eine spezifische Ressource (berücksichtigt Limits).
     */
    int getCrateSize(StockpileInstance stockpile, RESOURCE res);

    /**
     * Anzahl der Crates im Stockpile.
     */
    int getTotalCrates(StockpileInstance stockpile);

    /**
     * Benutzer-Limit pro Ressource (0 = kein Limit).
     */
    int getSpecialAmount(StockpileInstance stockpile, RESOURCE res);

    /**
     * Verbleibende Transportkapazität für eine Ressource.
     */
    int getMoveCapacityAm(StockpileInstance stockpile, RESOURCE res);

    // ─── Stockpile Write ────────────────────────────────────

    /**
     * Physikalische Lager-Sperre setzen/lösen (via BypassGate).
     * @param granary die zu schaltende Staatslager-Instanz
     * @param locked true = Lager nimmt nichts mehr an
     */
    void setStoring(StockpileInstance granary, boolean locked);

    /**
     * Fetch-Status setzen (via BypassGate).
     * @param stockpile die zu schaltende Instanz
     * @param enabled true = Fetch aktiv
     */
    void setFetching(StockpileInstance stockpile, boolean enabled);

    /**
     * Benutzer-Limit pro Ressource setzen (via BypassGate).
     * @param stockpile die zu schaltende Instanz
     * @param res die Ressource
     * @param amount das Limit (0 = kein Limit)
     */
    void setSpecialAmount(StockpileInstance stockpile, RESOURCE res, int amount);

    // ─── Transport Read ─────────────────────────────────────

    /**
     * Echte (vom Spiel gepflegte) Transport-Distanz.
     * @return Distanz in Tiles oder -1.0 wenn nicht lesbar
     */
    double getDistance(RoomInstance loadingStation);

    /**
     * Gesamt-Effizienz der Transportstation (0.0–1.0).
     */
    double getEfficiency(RoomInstance loadingStation);

    /**
     * Aktuelle Fetch-Zeit (geglättet, 0.0–1.0).
     */
    float getFetchTime(RoomInstance loadingStation);

    /**
     * Arbeiter pro Station (geglättet).
     */
    float getStationWorkers(RoomInstance loadingStation);

    /**
     * Station-Problem-Flag (true = Station hat ein Problem).
     */
    boolean hasStationProblem(RoomInstance loadingStation);

    /**
     * Aktuelle Transport-Ressource (null wenn keine zugewiesen).
     */
    RESOURCE getTransportResource(RoomInstance loadingStation);

    /**
     * Transport-Radius (raw byte-Wert).
     */
    byte getRadiusRaw(RoomInstance loadingStation);

    // ─── Transport Write ────────────────────────────────────

    /**
     * Transport-Radius setzen.
     */
    void setRadiusRaw(RoomInstance loadingStation, byte radius);

    // ─── Room Iteration ─────────────────────────────────────

    /**
     * @return true wenn SETT.ENTITIES() != null (Engine geladen).
     */
    boolean entitiesAvailable();

    /**
     * Alle Raum-Blueprints (Superklasse).
     */
    LIST<RoomBlueprintImp> getRoomImps();

    /**
     * Alle Raum-Blueprints (sub-typisiert).
     */
    LIST<RoomBlueprintIns<?>> getRoomIns();

    /**
     * Alle Gasthäuser.
     */
    LIST<ROOM_EATERY> getEateries();

    /**
     * Alle Kantinen.
     */
    LIST<ROOM_CANTEEN> getCanteens();

    /**
     * Wohnhäuser-Blueprint.
     */
    ROOM_HOME getHome();

    /**
     * Kammern-Blueprint.
     */
    ROOM_CHAMBER getChamber();

    /**
     * Hausmeister-Blueprint.
     */
    ROOM_JANITOR getJanitor();

    /**
     * Bahnhofs-Blueprint (Station). {@code SETT.ROOMS().STATION}.
     * @return die Station-Blueprint oder null wenn nicht verfügbar
     */
    Object getStation();

    /**
     * Gesamt-Lagerbestand in allen Bahnhöfen für eine Ressource.
     * @param res die Ressource
     * @return Gesamtmenge oder 0
     */
    long getStationTally(RESOURCE res);

    // ─── Service & Employment ───────────────────────────────

    /**
     * Service-Kapazität für einen Service-Raum.
     */
    ServiceCapacity getServiceCapacity(RoomService service);

    /**
     * Beschäftigungsziel für einen Raum setzen.
     * @param firm der Raum
     * @param target gewünschte Arbeiterzahl
     */
    void setFirmTarget(RoomInstance firm, int target);

    /**
     * Beschäftigungsraum eines Bürgers lesen.
     * @return der Raum oder null wenn nicht beschäftigt
     */
    RoomInstance getEmployedRoom(Humanoid humanoid);

    // ─── Inner Types ────────────────────────────────────────

    /**
     * Service-Kapazitäts-Record. Vor v0.13.119 als {@code EngineSeams.ServiceCapacity}
     * definiert; seit B-008-Phase-2 hier konsolidiert.
     */
    record ServiceCapacity(int total, int available, double utilisation) {
    }
}
