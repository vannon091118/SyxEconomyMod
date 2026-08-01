package vannon.syx.economy.headless;

import init.resources.RESOURCE;
import init.trade.TRADABLE;
import snake2d.util.sets.LIST;
import vannon.syx.economy.adapter.IGoodsAccess;

/**
 * Headless stub for {@link IGoodsAccess}. The stub world has no tradables,
 * no stockpiles, no import/export — every read returns the documented safe
 * default. The few "stat" methods (history arrays, active tradables) return
 * empty arrays / empty lists.
 *
 * <p>Cache invariant: snapshots returned by the {@code *History} family are
 * always zero-filled, never {@code null}. Callers can safely call
 * {@link java.util.Arrays#hashCode(double[])} on the result.</p>
 */
public final class StubGoodsAccess implements IGoodsAccess {

    private final MockWorldState state;

    public StubGoodsAccess(MockWorldState state) { this.state = state; }

    @Override public boolean isAvailable() { return true; }

    // ── Prices ──────────────────────────────────────────────
    @Override public int     getWorldPrice(TRADABLE t) { return 0; }
    @Override public double  getLocalPrice(TRADABLE t) { return 0.0; }
    @Override public int     getAnchorPrice(TRADABLE t) { return 0; }
    @Override public double  getScarcityMultiplier(TRADABLE t) { return 1.0; }
    @Override public double  getEffectiveCoverage(TRADABLE t) { return 1.0; }
    @Override public boolean isPriceCapped(TRADABLE t) { return false; }

    // ── Production / Consumption ────────────────────────────
    @Override public double  getDailyProduction(TRADABLE t) { return 0.0; }
    @Override public double  getDailyConsumption(TRADABLE t) { return 0.0; }
    @Override public double  getNetDailyFlow(TRADABLE t)      { return 0.0; }
    @Override public int     getProducerCount(TRADABLE t)    { return 0; }
    @Override public int     getConsumerCount(TRADABLE t)    { return 0; }

    // ── Stockpiles ──────────────────────────────────────────
    @Override public long getTotalStockpileAmount(RESOURCE r) { return 0L; }
    @Override public long getReservableStockpileAmount(RESOURCE r) { return 0L; }
    @Override public long getCrownStorageAmount(RESOURCE r) { return 0L; }
    @Override public long getPlayerStockpileAmount(TRADABLE t) { return 0L; }

    // ── Import / Export ─────────────────────────────────────
    @Override public boolean isImporting(TRADABLE t)       { return false; }
    @Override public boolean isExporting(TRADABLE t)       { return false; }
    @Override public int     getImportLimit(TRADABLE t)    { return 0; }
    @Override public int     getExportLimit(TRADABLE t)    { return 0; }
    @Override public double  getImportedYesterday(TRADABLE t) { return 0.0; }
    @Override public double  getExportedYesterday(TRADABLE t) { return 0.0; }
    @Override public double  getImportPrice(TRADABLE t)    { return 0.0; }
    @Override public double  getExportPrice(TRADABLE t)    { return 0.0; }

    // ── FlowPrices / Market Detail ──────────────────────────
    @Override public LIST<TRADABLE> getActiveTradables() {
        // Stub world has no tradables — null is acceptable per the interface
        // contract and keeps us from coupling to a snake2d concrete subclass
        // that may not exist on the test classpath.
        return null;
    }
    @Override public double[] getPriceHistory(TRADABLE t, int days) {
        return new double[days];
    }
    @Override public double[] getProductionHistory(TRADABLE t, int days) {
        return new double[days];
    }
    @Override public double[] getConsumptionHistory(TRADABLE t, int days) {
        return new double[days];
    }

    // ── Treasury / Trade Balance ───────────────────────────
    @Override public double getPlayerCredits()         { return state.treasury(); }
    @Override public double getDailyTradeBalance()     { return 0.0; }
    @Override public double getCumulativeTradeBalance(){ return 0.0; }
}
