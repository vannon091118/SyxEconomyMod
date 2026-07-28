package vannon.syx.economy.core;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.RoomInstance;

/**
 * Extracted from StateWarehouses (Sprint 7 Legacy-Drift-Reduktion):
 * pricing, trade mode, and liquidation logic for state-owned warehouses.
 *
 * <p>Static utility — all methods receive the StateWarehouses instance
 * for field access (package-private).</p>
 */
final class WarehouseTrade {

    private WarehouseTrade() {}

    // ── Pricing ────────────────────────────────────────────────────

    static int buyPrice(StateWarehouses sw, RESOURCE resource) {
        sw.ensureSized();
        return resource == null ? 0 : sw.buyPrice[resource.index()];
    }

    static void setBuyPrice(StateWarehouses sw, RESOURCE resource, int price) {
        if (resource == null) return;
        sw.ensureSized();
        sw.buyPrice[resource.index()] = clampPrice(price);
    }

    static int sellPrice(StateWarehouses sw, RESOURCE resource) {
        sw.ensureSized();
        return resource == null ? 0 : sw.sellPrice[resource.index()];
    }

    static void setSellPrice(StateWarehouses sw, RESOURCE resource, int price) {
        if (resource == null) return;
        sw.ensureSized();
        sw.sellPrice[resource.index()] = clampPrice(price);
    }

    static int crownMarketPrice(StateWarehouses sw, RESOURCE resource) {
        sw.ensureSized();
        return resource == null ? 75 : sw.crownMarketPrice[resource.index()];
    }

    static void setCrownMarketPrice(StateWarehouses sw, RESOURCE resource, int price) {
        if (resource == null) return;
        sw.ensureSized();
        sw.crownMarketPrice[resource.index()] = clampPrice(price);
    }

    static void standardizeAllPrices(StateWarehouses sw, FlowPrices prices) {
        if (prices == null) return;
        sw.ensureSized();
        for (RESOURCE r : RESOURCES.ALL()) {
            int idx = r.index();
            int anchor = (int) prices.anchor(idx);
            if (sw.tradeMode != StateWarehouses.TradeMode.SELL_ONLY) {
                sw.buyPrice[idx] = clampPrice((int) (anchor * 0.80));
            }
            if (sw.tradeMode != StateWarehouses.TradeMode.BUY_ONLY) {
                sw.sellPrice[idx] = clampPrice((int) (anchor * 1.10));
            }
        }
    }

    static boolean buysAt(StateWarehouses sw, RESOURCE resource, int marketPrice) {
        if (sw.tradeMode == StateWarehouses.TradeMode.SELL_ONLY) return false;
        int floor = buyPrice(sw, resource);
        return floor > 0 && marketPrice <= floor && resource != null
            && resource.index() < sw.hoardingBuyerFor.length
            && sw.hoardingBuyerFor[resource.index()];
    }

    static boolean sellsAt(StateWarehouses sw, RoomInstance warehouse, RESOURCE resource, int marketPrice) {
        if (sw.tradeMode == StateWarehouses.TradeMode.BUY_ONLY) return false;
        int floor = sellPrice(sw, resource);
        return floor > 0 && isLiquidating(sw, warehouse) && marketPrice >= floor;
    }

    static int clampPrice(int price) {
        return Math.max(0, Math.min(Math.max(0, EconConfig.statePriceMax), price));
    }

    // ── Trade mode ─────────────────────────────────────────────────

    static void setTradeMode(StateWarehouses sw, StateWarehouses.TradeMode mode) {
        sw.tradeMode = mode == null ? StateWarehouses.TradeMode.NORMAL : mode;
    }

    static StateWarehouses.TradeMode tradeMode(StateWarehouses sw) {
        return sw.tradeMode;
    }

    // ── Liquidation ────────────────────────────────────────────────

    static boolean isHoarding(StateWarehouses sw, RoomInstance warehouse) {
        if (!sw.isStateOwned(warehouse)) return false;
        if (sw.tradeMode == StateWarehouses.TradeMode.SELL_ONLY) return false;
        if (sw.tradeMode == StateWarehouses.TradeMode.BUY_ONLY) return true;
        return !sw.liquidating.contains(StateWarehouses.key(warehouse));
    }

    static boolean isLiquidating(StateWarehouses sw, RoomInstance warehouse) {
        if (!sw.isStateOwned(warehouse)) return false;
        if (sw.tradeMode == StateWarehouses.TradeMode.SELL_ONLY) return true;
        if (sw.tradeMode == StateWarehouses.TradeMode.BUY_ONLY) return false;
        return sw.liquidating.contains(StateWarehouses.key(warehouse));
    }

    static void setLiquidating(StateWarehouses sw, RoomInstance warehouse, boolean value) {
        if (warehouse == null) return;
        if (value) {
            sw.liquidating.add(StateWarehouses.key(warehouse));
        } else {
            sw.liquidating.remove(StateWarehouses.key(warehouse));
        }
    }

    static void toggleLiquidating(StateWarehouses sw, RoomInstance warehouse) {
        setLiquidating(sw, warehouse, !isLiquidating(sw, warehouse));
    }

    static void setAllLiquidating(StateWarehouses sw, boolean value) {
        if (value) {
            sw.liquidating.addAll(sw.owned);
        } else {
            sw.liquidating.clear();
        }
        sw.tradeMode = StateWarehouses.TradeMode.NORMAL;
    }

    static boolean allLiquidating(StateWarehouses sw) {
        if (sw.tradeMode == StateWarehouses.TradeMode.SELL_ONLY) return true;
        if (sw.tradeMode == StateWarehouses.TradeMode.BUY_ONLY) return false;
        return !sw.owned.isEmpty() && sw.liquidating.containsAll(sw.owned);
    }
}
