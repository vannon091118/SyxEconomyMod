package vannon.syx.economy.adapter;

import settlement.room.infra.stockpile.StockpileInstance;

/**
 * Fallback für {@link ISyxWarehouse}, wenn die Vanilla-Reflection auf
 * {@code storingSet(boolean)} fehlgeschlagen hat.
 *
 * <p>{@link #setStoring} ist ein No-Op: die Staatslager-Sperre wird rein
 * über das Pricing-Lock-Pattern in {@code StateWarehouses} abgebildet
 * (Sell-Preis über Markt-Preis → Merchant-Kauf-Through unmöglich). Das ist
 * semantisch schwächer als die Vanilla-Sperre, hält aber die Ökonomie
 * konsistent (kein Merchant-Drain des Staatsvermögens).</p>
 *
 * <p>Wird via
 * {@code ISyxWarehouse wh = new VanillaWarehouseAdapter();
 * if (!wh.isStoringLockAvailable()) wh = new FallbackWarehouseAdapter();}
 * aktiviert.</p>
 */
public final class FallbackWarehouseAdapter implements ISyxWarehouse {

    @Override
    public boolean isStoringLockAvailable() {
        return false;
    }

    @Override
    public void setStoring(StockpileInstance granary, boolean locked) {
        // No-op: Pricing-Lock in StateWarehouses übernimmt die Rolle.
    }
}
