package vannon.syx.economy.adapter;

import settlement.entity.humanoid.Humanoid;
import settlement.room.infra.station.ROOM_STATION;
import settlement.room.main.RoomInstance;
import vannon.syx.economy.adapter.seam.BypassGate;
import vannon.syx.economy.adapter.seam.ClassResolver;
import vannon.syx.economy.adapter.seam.FieldAccessor;
import vannon.syx.economy.core.EventLog;

/**
 * V71.44-Adapter powered by {@link BypassGate}: liest das interne
 * {@code distance}-Feld (float) der package-private
 * {@code TransportInstance} per VarHandle (primär) mit Reflection-Fallback.
 *
 * <p>{@link ClassResolver} löst die package-private Klasse mit dem
 * Game-ClassLoader ({@code Humanoid.class.getClassLoader()}) auf.</p>
 *
 * <p>Geometrische Distanzberechnung bleibt unverändert — reine Mathematik,
 * kein Engine-Zugriff.</p>
 */
public final class VanillaTransportAdapter implements ISyxTransport {

    private static final String TRANSPORT_INSTANCE_CLASS = "settlement.room.infra.transport.TransportInstance";
    private static final String DISTANCE_FIELD = "distance";

    private static final ClassLoader GAME_CL;
    static {
        ClassLoader cl = Humanoid.class.getClassLoader();
        GAME_CL = cl != null ? cl : ClassLoader.getSystemClassLoader();
    }

    private final FieldAccessor.FloatField distanceAccessor;
    private final boolean initOk;

    private boolean runtimeFailed;
    private boolean runtimeFailedLogged;

    public VanillaTransportAdapter() {
        BypassGate gate = new BypassGate("VanillaTransportAdapter");
        ClassResolver resolver = gate.classResolver(GAME_CL);

        FieldAccessor.FloatField dist = null;
        boolean ok = false;
        try {
            Class<?> tc = resolver.resolve(TRANSPORT_INSTANCE_CLASS);
            dist = gate.floatField(tc, DISTANCE_FIELD);
            ok = gate.isAvailable();
        } catch (Throwable t) {
            ok = false;
            EventLog.log("SEAM", "VanillaTransportAdapter init failed — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + ". Fallback auf geometrische Distanz.");
        }

        this.distanceAccessor = dist;
        this.initOk = ok;

        if (this.initOk) {
            EventLog.log("SEAM", "VanillaTransportAdapter: READY (distance-Feld via BypassGate)");
        }
    }

    @Override
    public boolean isDistanceAvailable() {
        return this.initOk && !this.runtimeFailed;
    }

    @Override
    public double getReflectedDistance(RoomInstance loadingStation) {
        if (!isDistanceAvailable() || loadingStation == null || distanceAccessor == null) {
            return -1.0;
        }
        try {
            return (double) distanceAccessor.get(loadingStation);
        } catch (Throwable t) {
            this.runtimeFailed = true;
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
