package vannon.syx.economy.adapter;

import settlement.room.infra.stockpile.StockpileInstance;
import vannon.syx.economy.adapter.seam.BypassGate;
import vannon.syx.economy.adapter.seam.MethodAccessor;
import vannon.syx.economy.core.EventLog;

import java.lang.invoke.MethodHandles;

/**
 * V71.44-Adapter powered by {@link BypassGate}: ruft die private Methode
 * {@code storingSet(boolean)} der {@code StockpileInstance} per MethodHandle
 * (primär) mit Reflection-Fallback auf.
 *
 * <p>Der BypassGate wählt automatisch die schnellste verfügbare Zugriffsstrategie
 * (MethodHandle auf JDK 21+, sonst Reflection). Kein manueller MH-Toggle nötig.</p>
 */
public final class VanillaWarehouseAdapter implements ISyxWarehouse {

    private static final String STORING_SET_METHOD = "storingSet";

    private final MethodAccessor.VoidMethod storingSet;
    private final boolean initOk;

    private boolean runtimeFailed;
    private boolean runtimeFailedLogged;

    public VanillaWarehouseAdapter() {
        BypassGate gate = new BypassGate("VanillaWarehouseAdapter", MethodHandles.lookup());
        MethodAccessor.VoidMethod method = null;
        boolean ok = false;
        try {
            method = gate.voidMethod(StockpileInstance.class, STORING_SET_METHOD, boolean.class);
            ok = gate.isAvailable();
        } catch (Throwable t) {
            ok = false;
            EventLog.log("SEAM", "VanillaWarehouseAdapter init failed — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + ". Staatslager-Sperre ist nur Pricing-Lock.");
        }

        this.storingSet = method;
        this.initOk = ok;

        if (this.initOk) {
            EventLog.log("SEAM", "VanillaWarehouseAdapter: READY (storingSet via BypassGate)");
        }
    }

    @Override
    public boolean isStoringLockAvailable() {
        return this.initOk && !this.runtimeFailed;
    }

    @Override
    public void setStoring(StockpileInstance granary, boolean locked) {
        if (!isStoringLockAvailable() || granary == null || storingSet == null) {
            return;
        }
        try {
            storingSet.invoke(granary, locked);
        } catch (Throwable t) {
            this.runtimeFailed = true;
            if (!this.runtimeFailedLogged) {
                this.runtimeFailedLogged = true;
                EventLog.log("SEAM", "VanillaWarehouseAdapter runtime invoke failed — "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + ". Deaktiviere physikalische Sperre dauerhaft.");
            }
        }
    }
}
