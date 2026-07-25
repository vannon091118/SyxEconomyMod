package vannon.syx.economy.adapter;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import settlement.room.infra.station.ROOM_STATION;
import settlement.room.main.RoomInstance;
import vannon.syx.economy.core.EventLog;

/**
 * Forward-kompatible, optimierte Variante des {@link VanillaTransportAdapter}: VarHandle
 * statt {@link java.lang.reflect.Field#getFloat(Object)}.
 *
 * <p>VarHandle eliminiert die Reflection-Access-Check-Overhead pro Aufruf und
 * wird vom C2-JIT bis auf einen einzigen Mov-Instruktion optimiert.
 * Erwarteter Speedup: 3–5× auf JDK 21+.</p>
 *
 * <p>Fallback-Strategie unverändert: wenn die TransportInstance-Klasse oder
 * das distance-Feld nicht gefunden wird, fällt der Adapter auf geometrische
 * Distanz zurück.</p>
 *
 * <p>V71.44-verifiziert: TransportInstance.distance ist float (Field.getFloat).</p>
 */
public final class VanillaTransportAdapterMH implements ISyxTransport {

    private static final String TRANSPORT_INSTANCE_CLASS = "settlement.room.infra.transport.TransportInstance";
    private static final String DISTANCE_FIELD = "distance";

    private static final ClassLoader GAME_CL;
    static {
        ClassLoader cl = RoomInstance.class.getClassLoader();
        GAME_CL = cl != null ? cl : ClassLoader.getSystemClassLoader();
    }

    private VarHandle distanceHandle;
    private boolean distanceAvailable;
    private boolean initFailedLogged;
    private boolean runtimeFailedLogged;

    public VanillaTransportAdapterMH() {
        this.distanceHandle = null;
        this.distanceAvailable = false;
        try {
            Class<?> transportClass = Class.forName(TRANSPORT_INSTANCE_CLASS, true, GAME_CL);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            this.distanceHandle = MethodHandles.privateLookupIn(transportClass, lookup)
                    .findVarHandle(transportClass, DISTANCE_FIELD, float.class);
            this.distanceAvailable = true;
            EventLog.log("SEAM", "VanillaTransportAdapterMH: READY (distance-Feld, VarHandle)");
        } catch (Throwable t) {
            this.distanceAvailable = false;
            if (!this.initFailedLogged) {
                this.initFailedLogged = true;
                EventLog.log("SEAM", "VanillaTransportAdapterMH init failed — "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + ". Fallback auf geometrische Distanz.");
            }
        }
    }

    @Override
    public boolean isDistanceAvailable() {
        return this.distanceAvailable;
    }

    /**
     * VarHandle-basierter Read des distance-Felds.
     *
     * <p>Performance: VarHandle.get() ist ein single-implementation-native-call
     * ohne Access-Check (wurde beim Lookup geprüft). C2 kompiliert ihn zu
     * einem direkten Feld-Zugriff (1-2 CPU-Instruktionen vs. 15+ bei Reflection).</p>
     */
    @Override
    public double getReflectedDistance(RoomInstance loadingStation) {
        if (!this.distanceAvailable || loadingStation == null || this.distanceHandle == null) {
            return -1.0;
        }
        try {
            return (double) this.distanceHandle.get(loadingStation);
        } catch (Throwable t) {
            this.distanceAvailable = false;
            if (!this.runtimeFailedLogged) {
                this.runtimeFailedLogged = true;
                EventLog.log("SEAM", "VanillaTransportAdapterMH runtime read failed — "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + ". Aktiviere geometrischen Fallback.");
            }
            return -1.0;
        }
    }

    /**
     * Geometrische Distanzberechnung — identisch zu {@link VanillaTransportAdapter}.
     */
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
