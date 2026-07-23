package vannon.syx.economy.adapter;

import java.lang.reflect.Method;
import settlement.room.infra.stockpile.StockpileInstance;
import vannon.syx.economy.core.EventLog;

/**
 * V71.44-Adapter: ruft die private Methode {@code storingSet(boolean)} der
 * {@code StockpileInstance} per Reflection auf. Ein Fehlschlag führt zu
 * {@link FallbackWarehouseAdapter} (vom Aufrufer zu prüfen via
 * {@link #isStoringLockAvailable()}).
 *
 * <p>One-Shot-Guards verhindern EventLog-Spam. Runtime-Fehler setzen
 * {@code available} dauerhaft auf false, damit Folge-Aufrufe ohne
 * Reflection-Crash zum No-Op-Pfad wechseln.</p>
 */
public final class VanillaWarehouseAdapter implements ISyxWarehouse {

    private static final String STORING_SET_METHOD = "storingSet";

    private Method storingSetMethod;
    private boolean available;
    private boolean initFailedLogged;
    private boolean runtimeFailedLogged;

    public VanillaWarehouseAdapter() {
        this.storingSetMethod = null;
        this.available = false;
        try {
            this.storingSetMethod = StockpileInstance.class.getDeclaredMethod(STORING_SET_METHOD, boolean.class);
            this.storingSetMethod.setAccessible(true);
            this.available = true;
        } catch (Throwable t) {
            this.available = false;
            if (!this.initFailedLogged) {
                this.initFailedLogged = true;
                EventLog.log("SEAM", "VanillaWarehouseAdapter init failed — "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + ". Staatslager-Sperre ist nur Pricing-Lock (Merchants beachten Sell-Preis).");
            }
        }
    }

    @Override
    public boolean isStoringLockAvailable() {
        return this.available;
    }

    @Override
    public void setStoring(StockpileInstance granary, boolean locked) {
        if (!this.available || granary == null || this.storingSetMethod == null) {
            return;
        }
        try {
            this.storingSetMethod.invoke(granary, locked);
        } catch (Throwable t) {
            this.available = false;
            if (!this.runtimeFailedLogged) {
                this.runtimeFailedLogged = true;
                EventLog.log("SEAM", "VanillaWarehouseAdapter runtime invoke failed — "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + ". Deaktiviere physikalische Sperre dauerhaft.");
            }
        }
    }
}
