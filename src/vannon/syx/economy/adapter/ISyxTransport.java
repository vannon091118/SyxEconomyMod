package vannon.syx.economy.adapter;

import settlement.room.infra.station.ROOM_STATION;
import settlement.room.main.RoomInstance;

/**
 * Kapselt Reflection-Zugriffe auf das interne {@code distance}-Feld der
 * LoadingStation ({@code TransportInstance}) in Vanilla V71.44.
 *
 * <p>Bei einem Spiel-Update muss nur die Adapter-Implementierung
 * geprüft werden — nicht mehr der gesamte {@code TransportMarket}.</p>
 */
public interface ISyxTransport {

    /**
     * @return true wenn das interne {@code distance}-Feld per Reflection
     *         erfolgreich aufgelöst wurde; false wenn der Fallback genutzt
     *         werden muss (z. B. weil die Vanilla-Klassenstruktur geändert wurde).
     */
    boolean isDistanceAvailable();

    /**
     * Echte (vom Spiel gepflegte) Transport-Distanz via Reflection.
     *
     * @param loadingStation die Verladestation ({@code RoomInstance}, wird
     *                       gegen {@code TransportInstance} gecastet)
     * @return die Distanz in Tiles oder {@code -1.0} wenn die Reflection
     *         fehlgeschlagen hat (Aufrufer soll dann auf geometrische
     *         Distanz zurückfallen).
     */
    double getReflectedDistance(RoomInstance loadingStation);

    /**
     * Geometrische Fallback-Distanz (Tile-Manhattan per Wurzel über
     * Tile-Koordinaten). Wird verwendet wenn Reflection nicht verfügbar
     * oder die echte Distanz nicht ermittelbar ist.
     */
    double getGeometricDistance(RoomInstance loadingStation, ROOM_STATION unloading);
}
