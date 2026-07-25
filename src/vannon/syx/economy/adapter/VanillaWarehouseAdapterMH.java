package vannon.syx.economy.adapter;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import settlement.room.infra.stockpile.StockpileInstance;
import vannon.syx.economy.core.EventLog;

/**
 * Forward-kompatible, optimierte Variante des {@link VanillaWarehouseAdapter}: MethodHandle
 * statt {@link java.lang.reflect.Method#invoke(Object, Object...)}.
 *
 * <p>MethodHandle.invokeExact() eliminiert Boxing/Array-Allokation des
 * Reflection-Invoke und wird vom C2 zu einem direkten vtable-Call
 * optimiert. Erwarteter Speedup: 3–5× auf JDK 21+.</p>
 *
 * <p>Noch geringe Gesamtauswirkung wegen niedriger Aufruffrequenz
 * (nur pro Lager-Toggle), aber kostenlos mitzunehmen.</p>
 */
public final class VanillaWarehouseAdapterMH implements ISyxWarehouse {

    private static final String STORING_SET_METHOD = "storingSet";

    private MethodHandle storingSetHandle;
    private boolean available;
    private boolean initFailedLogged;
    private boolean runtimeFailedLogged;

    public VanillaWarehouseAdapterMH() {
        this.storingSetHandle = null;
        this.available = false;
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandles.Lookup priv = MethodHandles.privateLookupIn(StockpileInstance.class, lookup);
            this.storingSetHandle = priv.findVirtual(StockpileInstance.class, STORING_SET_METHOD,
                    MethodType.methodType(void.class, boolean.class));
            this.available = true;
            EventLog.log("SEAM", "VanillaWarehouseAdapterMH: READY (storingSet, MethodHandle)");
        } catch (Throwable t) {
            this.available = false;
            if (!this.initFailedLogged) {
                this.initFailedLogged = true;
                EventLog.log("SEAM", "VanillaWarehouseAdapterMH init failed — "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + ". Staatslager-Sperre ist nur Pricing-Lock.");
            }
        }
    }

    @Override
    public boolean isStoringLockAvailable() {
        return this.available;
    }

    @Override
    public void setStoring(StockpileInstance granary, boolean locked) {
        if (!this.available || granary == null || this.storingSetHandle == null) {
            return;
        }
        try {
            this.storingSetHandle.invokeExact(granary, locked);
        } catch (Throwable t) {
            this.available = false;
            if (!this.runtimeFailedLogged) {
                this.runtimeFailedLogged = true;
                EventLog.log("SEAM", "VanillaWarehouseAdapterMH runtime invoke failed — "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + ". Deaktiviere physikalische Sperre dauerhaft.");
            }
        }
    }
}
