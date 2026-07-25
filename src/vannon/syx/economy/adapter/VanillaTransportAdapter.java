package vannon.syx.economy.adapter;

import java.lang.reflect.Field;
import settlement.room.infra.station.ROOM_STATION;
import settlement.room.infra.transport.ROOM_TRANSPORT;
import settlement.room.main.RoomInstance;
import vannon.syx.economy.core.EventLog;    /**
     * V71.44-Adapter: liest das interne {@code distance}-Feld (float) der
     * {@code TransportInstance} per Reflection. Field-Zugriff erfolgt einmalig
     * im Konstruktor; ein Fehlschlag führt zu {@link FallbackTransportAdapter}
     * (vom Aufrufer zu prüfen via {@link #isDistanceAvailable()}).
     *
     * <p>One-Shot-Guard im Konstruktor verhindert EventLog-Spam bei wiederholten
     * Initialisierungen. Runtime-Lese-Fehler setzen {@code distanceAvailable}
     * dauerhaft auf false und signalisieren zusätzlich via EventLog.</p>
     *
     * <p><b>V71.44-verifiziert:</b> {@code TransportInstance.distance} ist
     * {@code float} — NICHT {@code double}. {@link #getReflectedDistance}
     * nutzt daher {@link Field#getFloat} mit explizitem {@code (double)} Cast.</p>
     */
public final class VanillaTransportAdapter implements ISyxTransport {

    private static final String TRANSPORT_INSTANCE_CLASS = "settlement.room.infra.transport.TransportInstance";
    private static final String DISTANCE_FIELD = "distance";

    private static final ClassLoader GAME_CL;
    static {
        ClassLoader cl = RoomInstance.class.getClassLoader();
        GAME_CL = cl != null ? cl : ClassLoader.getSystemClassLoader();
    }

    private Field distanceField;
    private boolean distanceAvailable;
    private boolean initFailedLogged;
    private boolean runtimeFailedLogged;

    public VanillaTransportAdapter() {
        this.distanceField = null;
        this.distanceAvailable = false;
        try {
            Class<?> transportClass = Class.forName(TRANSPORT_INSTANCE_CLASS, true, GAME_CL);
            this.distanceField = transportClass.getDeclaredField(DISTANCE_FIELD);
            this.distanceField.setAccessible(true);
            this.distanceAvailable = true;
            EventLog.log("SEAM", "VanillaTransportAdapter: READY (distance-Feld)");
        } catch (Throwable t) {
            this.distanceAvailable = false;
            if (!this.initFailedLogged) {
                this.initFailedLogged = true;
                EventLog.log("SEAM", "VanillaTransportAdapter init failed — "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + ". Fallback auf geometrische Distanz.");
            }
        }
    }

    @Override
    public boolean isDistanceAvailable() {
        return this.distanceAvailable;
    }

    @Override
    public double getReflectedDistance(RoomInstance loadingStation) {
        if (!this.distanceAvailable || loadingStation == null || this.distanceField == null) {
            return -1.0;
        }
        // The vanilla field lives on TransportInstance, but ROOM_TRANSPORT.getInstance()
        // already returns TransportInstance. V71.44-verified: distance is float, NOT double.
        // Field.getFloat() + explicit (double) cast avoids IllegalArgumentException.
        try {
            return (double) this.distanceField.getFloat(loadingStation);
        } catch (Throwable t) {
            this.distanceAvailable = false;
            if (!this.runtimeFailedLogged) {
                this.runtimeFailedLogged = true;
                EventLog.log("SEAM", "VanillaTransportAdapter runtime read failed — "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + ". Aktiviere geometrischen Fallback.");
            }
            return -1.0;
        }
    }

    @Override
    public double getGeometricDistance(RoomInstance loadingStation, ROOM_STATION unloading) {
        if (loadingStation == null || unloading == null || unloading.instancesSize() == 0) {
            return 0.0;
        }
        int lx = loadingStation.mX();
        int ly = loadingStation.mY();
        double best = Double.MAX_VALUE;
        for (int i = 0; i < unloading.instancesSize(); ++i) {
            RoomInstance s = unloading.getInstance(i);
            if (s == null || !s.exists()) continue;
            double dx = (double) (s.mX() - lx);
            double dy = (double) (s.mY() - ly);
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d < best) best = d;
        }
        return best == Double.MAX_VALUE ? 0.0 : best;
    }
}
