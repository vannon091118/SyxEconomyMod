package vannon.syx.economy.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import settlement.room.infra.stockpile.StockpileInstance;
import vannon.syx.economy.adapter.ISyxWarehouse;

/**
 * Isolation tests for {@link WarehouseMarket}.
 *
 * <p>These tests exercise the stateful bookkeeping layer of the market without
 * touching the Songs of Syx engine. Methods that enumerate rooms/resources
 * (e.g. {@code buy()}) need a running engine and are therefore excluded from
 * this suite.</p>
 */
class WarehouseMarketIsolationTest {

    private StateWarehouses state;
    private FlowPrices prices;
    private WarehouseMarket market;

    @BeforeEach
    void setUp() {
        ISyxWarehouse warehouseAdapter = new ISyxWarehouse() {
            public boolean isStoringLockAvailable() { return false; }
            public void setStoring(StockpileInstance g, boolean l) {}
        };
        state = new StateWarehouses(warehouseAdapter);
        prices = new FlowPrices();
        market = new WarehouseMarket(state, prices);
    }

    @Test
    void construction_createsNonNullMarket() {
        assertNotNull(market);
    }

    @Test
    void initialState_lastBoughtAndSoldAreZero() {
        assertEquals(0L, market.lastBought());
        assertEquals(0L, market.lastSold());
        assertEquals(0L, market.lastConstructionPaid());
        assertEquals(0L, market.lastExportBought());
    }

    @Test
    void initialState_unitCountersAreZero() {
        assertEquals(0, market.lastUnitsBought());
        assertEquals(0, market.lastUnitsSold());
    }

    @Test
    void beginPurchases_resetsPurchaseTrackers() {
        // The only public way to mutate lastBought without the engine is beginPurchases().
        market.beginPurchases();
        assertEquals(0L, market.lastBought());
        assertEquals(0, market.lastUnitsBought());
    }

    @Test
    void beginTick_doesNotCrash_andResetsSaleTrackers() {
        assertDoesNotThrow(() -> market.beginTick());
        assertEquals(0L, market.lastSold());
        assertEquals(0, market.lastUnitsSold());
    }

    @Test
    void clear_resetsAllTrackers() {
        market.clear();
        assertEquals(0L, market.lastBought());
        assertEquals(0L, market.lastSold());
        assertEquals(0, market.lastUnitsBought());
        assertEquals(0, market.lastUnitsSold());
        assertEquals(0L, market.lastTaxed());
    }

    @Test
    void crownUnits_nullResource_returnsZero() {
        assertEquals(0L, market.crownUnits(null));
    }

}
