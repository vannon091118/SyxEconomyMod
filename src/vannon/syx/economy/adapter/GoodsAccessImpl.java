package vannon.syx.economy.adapter;

import game.faction.FACTIONS;
import game.faction.trade.ResourcePrices;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.trade.TR;
import init.trade.TRADABLE;
import settlement.main.SETT;
import settlement.room.infra.stockpile.StockpileTally;
import settlement.trade.PBuyer;
import settlement.trade.PSeller;
import settlement.trade.SettTrade;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.LIST;
import vannon.syx.economy.core.EngineLevers;
import vannon.syx.economy.core.LoggingAdapter;
import vannon.syx.economy.adapter.seam.BypassGate;
import vannon.syx.economy.adapter.seam.ClassResolver;
import vannon.syx.economy.adapter.seam.FieldAccessor;
import vannon.syx.economy.adapter.seam.MethodAccessor;

import java.lang.invoke.MethodHandles;
import java.util.List;

/**
 * GoodsAccessImpl — IGoodsAccess Implementation.
 *
 * <p>Zugriff auf SettTrade, StockpileTally, ResourcePrices via BypassGate / ClassResolver.
 * Für V71.44 validiert.</p>
 */
public final class GoodsAccessImpl implements IGoodsAccess {

    // ─── BypassGate Access ──────────────────────────────────────
    private final Object stockpileTallyInstance;
    private final Object tradeInstance;
    private final boolean available;

    private final ClassResolver classResolver;

    public GoodsAccessImpl() {
        Object sti = null;
        Object ti = null;
        boolean stiFailed = false;
        boolean tiFailed = false;

        this.classResolver = new ClassResolver(settlement.room.infra.stockpile.StockpileTally.class.getClassLoader());

        // StockpileTally
        BypassGate tallyGate = new BypassGate("GoodsAccessImpl-StockpileTally", MethodHandles.lookup());
        ClassResolver tallyResolver = tallyGate.classResolver(settlement.room.infra.stockpile.StockpileTally.class.getClassLoader());
        try {
            Class<?> stClass = tallyResolver.resolve("settlement.room.infra.stockpile.StockpileTally");
            // Try instance field first
            FieldAccessor.RefField<Object> instanceField = tallyGate.refField(stClass, "instance", Object.class);
            if (tallyGate.isAvailable()) {
                sti = instanceField.getStatic();
            }
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("GOODS", "INIT", LoggingAdapter.Severity.ERROR,
                    "init_tally_error", t.getMessage(), "");
            stiFailed = true;
        }

        // SettTrade
        BypassGate tradeGate = new BypassGate("GoodsAccessImpl-SettTrade", MethodHandles.lookup());
        ClassResolver tradeResolver = tradeGate.classResolver(settlement.trade.SettTrade.class.getClassLoader());
        try {
            Class<?> stClass = tradeResolver.resolve("settlement.trade.SettTrade");
            FieldAccessor.RefField<Object> instanceField = tradeGate.refField(stClass, "instance", Object.class);
            if (tradeGate.isAvailable()) {
                ti = instanceField.getStatic();
            }
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("GOODS", "INIT", LoggingAdapter.Severity.ERROR,
                    "init_setttrade_error", t.getMessage(), "");
            tiFailed = true;
        }

        this.stockpileTallyInstance = sti;
        this.tradeInstance = ti;
        this.available = !stiFailed && !tiFailed && sti != null && ti != null;
    }

    // ─── Helper ────────────────────────────────────────────────────

    private int tradableIndex(TRADABLE t) {
        if (t == null) return -1;
        try {
            return t.index();
        } catch (Throwable e) {
            return -1;
        }
    }

    // ─── isAvailable ────────────────────────────────────────────
    @Override
    public boolean isAvailable() {
        return EngineLevers.goodsAccessEnabled && available;
    }

    // ─── Prices ───────────────────────────────────────────────────

    @Override
    public int getWorldPrice(TRADABLE tradable) {
        if (!EngineLevers.goodsAccessEnabled) return 0;
        if (tradable == null) return 0;
        try {
            // ResourcePrices is a class with static instance via FACTIONS.PRICE()
            ResourcePrices prices = FACTIONS.PRICE();
            if (prices != null) {
                int v = prices.get(tradable);
                LoggingAdapter.csvTrace("GOODS", "GET", LoggingAdapter.Severity.DEBUG,
                        "getWorldPrice", String.valueOf(v), tradable.key());
                return v;
            }
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("GOODS", "GET", LoggingAdapter.Severity.ERROR,
                    "getWorldPrice_error", t.getMessage(), tradable != null ? tradable.key() : "null");
        }
        return 0;
    }

    @Override
    public double getLocalPrice(TRADABLE tradable) {
        if (!EngineLevers.goodsAccessEnabled) return 0;
        // Local price not directly exposed - return world price
        return getWorldPrice(tradable);
    }

    @Override
    public int getAnchorPrice(TRADABLE tradable) {
        if (!EngineLevers.goodsAccessEnabled) return 0;
        // Anchor price not directly exposed
        return getWorldPrice(tradable);
    }

    @Override
    public double getScarcityMultiplier(TRADABLE tradable) {
        if (!EngineLevers.goodsAccessEnabled) return 1.0;
        return 1.0;
    }

    @Override
    public double getEffectiveCoverage(TRADABLE tradable) {
        if (!EngineLevers.goodsAccessEnabled) return 1.0;
        return 1.0;
    }

    @Override
    public boolean isPriceCapped(TRADABLE tradable) {
        if (!EngineLevers.goodsAccessEnabled) return false;
        return false;
    }

    // ─── Production / Consumption ───────────────────────────────

    @Override
    public double getDailyProduction(TRADABLE tradable) {
        if (!EngineLevers.goodsAccessEnabled) return 0;
        // Not directly exposed in vanilla V71
        return 0;
    }

    @Override
    public double getDailyConsumption(TRADABLE tradable) {
        if (!EngineLevers.goodsAccessEnabled) return 0;
        // Not directly exposed in vanilla V71
        return 0;
    }

    @Override
    public double getNetDailyFlow(TRADABLE tradable) {
        return getDailyProduction(tradable) - getDailyConsumption(tradable);
    }

    @Override
    public int getProducerCount(TRADABLE tradable) {
        return 0;
    }

    @Override
    public int getConsumerCount(TRADABLE tradable) {
        return 0;
    }

    // ─── Stockpiles ──────────────────────────────────────────────

    @Override
    public long getTotalStockpileAmount(RESOURCE res) {
        if (!EngineLevers.goodsAccessEnabled || stockpileTallyInstance == null) return 0;
        try {
            // StockpileTally has tally() or data() method
            Object tally = classResolver.invokeMethod(stockpileTallyInstance, "tally");
            if (tally == null) {
                tally = classResolver.invokeMethod(stockpileTallyInstance, "data");
            }
            if (tally != null) {
                Object amount = classResolver.invokeMethod(tally, "amountTotal", res);
                if (amount instanceof Number n) {
                    long v = n.longValue();
                    LoggingAdapter.csvTrace("GOODS", "GET", LoggingAdapter.Severity.DEBUG,
                            "getTotalStockpileAmount", String.valueOf(v), res != null ? res.key() : "null");
                    return v;
                }
                // Try get(res)
                amount = classResolver.invokeMethod(tally, "get", res);
                if (amount instanceof Number n) {
                    long v = n.longValue();
                    LoggingAdapter.csvTrace("GOODS", "GET", LoggingAdapter.Severity.DEBUG,
                            "getTotalStockpileAmount", String.valueOf(v), res != null ? res.key() : "null");
                    return v;
                }
            }
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("GOODS", "GET", LoggingAdapter.Severity.ERROR,
                    "getTotalStockpileAmount_error", t.getMessage(), "");
        }
        return 0;
    }

    @Override
    public long getReservableStockpileAmount(RESOURCE res) {
        return 0;
    }

    @Override
    public long getCrownStorageAmount(RESOURCE res) {
        return 0;
    }

    @Override
    public long getPlayerStockpileAmount(TRADABLE tradable) {
        return 0;
    }

    // ─── Import / Export ────────────────────────────────────────

    @Override
    public boolean isImporting(TRADABLE tradable) {
        if (!EngineLevers.goodsAccessEnabled || tradeInstance == null) return false;
        try {
            if (tradeInstance instanceof SettTrade st) {
                PBuyer buyer = st.buyer(tradable);
                if (buyer != null) {
                    // Check if buyer is importing
                    Object imp = classResolver.invokeMethod(buyer, "importing");
                    if (imp instanceof Boolean b) {
                        LoggingAdapter.csvTrace("GOODS", "GET", LoggingAdapter.Severity.DEBUG,
                                "isImporting", String.valueOf(b), tradable.key());
                        return b;
                    }
                }
            }
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("GOODS", "GET", LoggingAdapter.Severity.ERROR,
                    "isImporting_error", t.getMessage(), "");
        }
        return false;
    }

    @Override
    public boolean isExporting(TRADABLE tradable) {
        if (!EngineLevers.goodsAccessEnabled || tradeInstance == null) return false;
        try {
            if (tradeInstance instanceof SettTrade st) {
                PSeller seller = st.seller(tradable);
                if (seller != null) {
                    Object exp = classResolver.invokeMethod(seller, "exporting");
                    if (exp instanceof Boolean b) {
                        LoggingAdapter.csvTrace("GOODS", "GET", LoggingAdapter.Severity.DEBUG,
                                "isExporting", String.valueOf(b), tradable.key());
                        return b;
                    }
                }
            }
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("GOODS", "GET", LoggingAdapter.Severity.ERROR,
                    "isExporting_error", t.getMessage(), "");
        }
        return false;
    }

    @Override
    public int getImportLimit(TRADABLE tradable) {
        return 0;
    }

    @Override
    public int getExportLimit(TRADABLE tradable) {
        return 0;
    }

    @Override
    public double getImportedYesterday(TRADABLE tradable) {
        return 0;
    }

    @Override
    public double getExportedYesterday(TRADABLE tradable) {
        return 0;
    }

    @Override
    public double getImportPrice(TRADABLE tradable) {
        return getWorldPrice(tradable);
    }

    @Override
    public double getExportPrice(TRADABLE tradable) {
        return getWorldPrice(tradable);
    }

    // ─── FlowPrices / Market Detail ─────────────────────────────

    @Override
    public LIST<TRADABLE> getActiveTradables() {
        if (!EngineLevers.goodsAccessEnabled) return new snake2d.util.sets.ArrayList<>(0);
        List<TRADABLE> active = new java.util.ArrayList<>();
        for (TRADABLE t : TR.ALL()) {
            if (t != null) {
                active.add(t);
            }
        }
        return new snake2d.util.sets.ArrayList<>(active);
    }

    @Override
    public double[] getPriceHistory(TRADABLE tradable, int days) {
        return new double[0];
    }

    @Override
    public double[] getProductionHistory(TRADABLE tradable, int days) {
        return new double[0];
    }

    @Override
    public double[] getConsumptionHistory(TRADABLE tradable, int days) {
        return new double[0];
    }

    // ─── Treasury / Trade Balance ────────────────────────────────

    @Override
    public double getPlayerCredits() {
        var api = EngineMirror.api();
        if (api != null && api.treasury() != null) {
            return api.treasury().getPlayerCredits();
        }
        return 0;
    }

    @Override
    public double getDailyTradeBalance() {
        return 0;
    }

    @Override
    public double getCumulativeTradeBalance() {
        return 0;
    }
}