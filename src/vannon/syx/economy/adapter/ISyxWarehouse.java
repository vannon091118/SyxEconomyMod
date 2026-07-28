package vannon.syx.economy.adapter;

import settlement.room.infra.stockpile.StockpileInstance;

/**
 * Kapselt Reflection-Zugriff auf die private Methode {@code storingSet(boolean)}
 * der {@code StockpileInstance}. Wird vom Staat genutzt, um Lager beim
 * Horten physisch gegen neue Ein-Lieferungen zu sperren (kein "Merchant
 * kauft günstig von staatlichem Lager"-Effekt).
 *
 * <p>Bei einem Spiel-Update muss nur die Adapter-Implementierung
 * geprüft werden — nicht mehr der gesamte {@code StateWarehouses}.</p>
 */
public interface ISyxWarehouse {

    /**
     * @return true wenn {@code storingSet(boolean)} per Reflection verfügbar ist.
     *         false → Aufrufer muss auf den Pricing-Lock-Fallback zurückfallen.
     */
    boolean isStoringLockAvailable();

    /**
     * Setzt oder löst die physikalische Lager-Sperre.
     *
     * @param granary die zu schaltende Staatslager-Instanz
     * @param locked  true = Lager nimmt nichts mehr an, false = offen
     */
    void setStoring(StockpileInstance granary, boolean locked);
}
