package vannon.syx.economy.adapter;

import settlement.room.infra.station.ROOM_STATION;
import settlement.room.main.RoomInstance;

/**
 * Fallback für {@link ISyxTransport}, wenn die Vanilla-Reflection auf das
 * {@code distance}-Feld in {@code TransportInstance} fehlgeschlagen hat
 * (Spiel-Update, Game-API-Änderung).
 *
 * <p>{@link #getGeometricDistance} liefert hier immer 0.0 — der Aufrufer
 * ({@code TransportMarket}) überspringt dann die Station, was konsistent
 * mit dem Verhalten vor Phase 4 ist (kein Transport-Credit, keine
 * Distance-Aggregation).</p>
 *
 * <p>Wird via {@code new FallbackTransportAdapter()} oder — eleganter —
 * durch Pattern {@code ISyxTransport tx = new VanillaTransportAdapter();
 * if (!tx.isDistanceAvailable()) tx = new FallbackTransportAdapter();}
 * aktiviert.</p>
 */
public final class FallbackTransportAdapter implements ISyxTransport {

    @Override
    public boolean isDistanceAvailable() {
        return false;
    }

    @Override
    public double getReflectedDistance(RoomInstance loadingStation) {
        return -1.0;
    }

    @Override
    public double getGeometricDistance(RoomInstance loadingStation, ROOM_STATION unloading) {
        // Konservativ: keine Distanz → Aufrufer behandelt Station als inaktiv.
        return 0.0;
    }
}
