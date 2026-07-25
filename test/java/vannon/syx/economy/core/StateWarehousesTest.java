package vannon.syx.economy.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import settlement.room.infra.stockpile.StockpileInstance;
import vannon.syx.economy.adapter.ISyxWarehouse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für {@link StateWarehouses} TradeMode- und Liquidation-Logik.
 * Läuft ohne Spiel-Engine — nur reine Zustandsmaschine.
 */
class StateWarehousesTest {

    private StateWarehouses warehouses;

    @BeforeEach
    void setUp() {
        warehouses = new StateWarehouses(new ISyxWarehouse() {
            public boolean isStoringLockAvailable() { return false; }
            public void setStoring(StockpileInstance g, boolean l) {}
        });
    }

    @Test
    void defaultTradeModeIsNormal() {
        assertEquals(StateWarehouses.TradeMode.NORMAL, warehouses.tradeMode());
    }

    @Test
    void setTradeModeStoresValue() {
        warehouses.setTradeMode(StateWarehouses.TradeMode.BUY_ONLY);
        assertEquals(StateWarehouses.TradeMode.BUY_ONLY, warehouses.tradeMode());

        warehouses.setTradeMode(StateWarehouses.TradeMode.SELL_ONLY);
        assertEquals(StateWarehouses.TradeMode.SELL_ONLY, warehouses.tradeMode());

        warehouses.setTradeMode(StateWarehouses.TradeMode.NORMAL);
        assertEquals(StateWarehouses.TradeMode.NORMAL, warehouses.tradeMode());
    }

    @Test
    void nullTradeModeFallsBackToNormal() {
        warehouses.setTradeMode(null);
        assertEquals(StateWarehouses.TradeMode.NORMAL, warehouses.tradeMode());
    }

    @Test
    void allLiquidatingFollowsTradeMode() {
        assertFalse(warehouses.allLiquidating(), "NORMAL ohne Lager -> nicht liquidating");

        warehouses.setTradeMode(StateWarehouses.TradeMode.SELL_ONLY);
        assertTrue(warehouses.allLiquidating(), "SELL_ONLY -> allLiquidating true");

        warehouses.setTradeMode(StateWarehouses.TradeMode.BUY_ONLY);
        assertFalse(warehouses.allLiquidating(), "BUY_ONLY -> allLiquidating false");
    }

    @Test
    void setAllLiquidatingResetsTradeModeToNormal() {
        warehouses.setTradeMode(StateWarehouses.TradeMode.SELL_ONLY);
        warehouses.setAllLiquidating(false);
        assertEquals(StateWarehouses.TradeMode.NORMAL, warehouses.tradeMode());
        assertFalse(warehouses.allLiquidating());

        warehouses.setTradeMode(StateWarehouses.TradeMode.SELL_ONLY);
        warehouses.setAllLiquidating(true);
        assertEquals(StateWarehouses.TradeMode.NORMAL, warehouses.tradeMode());
        // With no owned warehouses allLiquidating() is false even after setAllLiquidating(true).
        assertFalse(warehouses.allLiquidating());
    }

    @Test
    void clearResetsTradeMode() {
        warehouses.setTradeMode(StateWarehouses.TradeMode.BUY_ONLY);
        warehouses.clear();
        assertEquals(StateWarehouses.TradeMode.NORMAL, warehouses.tradeMode());
    }
}
