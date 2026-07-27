package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.faction.FResources;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.trade.TRADABLE;
import java.util.Map;
import settlement.main.SETT;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.RoomInstance;
import snake2d.LOG;
import util.statistics.HISTORY_COLLECTION;
import vannon.syx.economy.core.warehouse.market.MarketSharedState;

/**
 * T-105 AutoProcurementEngine — extracted from WarehouseMarket as part of Sprint M-1.
 *
 * <p>Handles construction-material and export procurement. Delegates sale
 * distribution to {@link WholesaleEngine#distributeSaleDetailed} (fixing the
 * T-102 tracking divergence where old WM copies were used).</p>
 */
public final class AutoProcurementEngine {

    private final MarketSharedState sharedState;
    private final StateWarehouses state;
    private final FlowPrices prices;
    private final WholesaleEngine wholesale;

    private int[] lastConstruction = new int[0];
    private boolean constructionInitialized;
    long lastConstructionPaid;
    private int[] lastExport = new int[0];
    private boolean exportInitialized;
    long lastExportBought;

    public AutoProcurementEngine(MarketSharedState sharedState, StateWarehouses state, FlowPrices prices, WholesaleEngine wholesale) {
        this.sharedState = sharedState;
        this.state = state;
        this.prices = prices;
        this.wholesale = wholesale;
    }

    public void clear() {
        this.lastConstruction = new int[0];
        this.constructionInitialized = false;
        this.lastConstructionPaid = 0L;
        this.lastExport = new int[0];
        this.exportInitialized = false;
        this.lastExportBought = 0L;
    }

    // ── Construction procurement ────────────────────────────────

    public int[] observeConstructionWithdrawals() {
        int goods = RESOURCES.ALL().size();
        int[] consumed = new int[goods];
        if (this.lastConstruction.length != goods) {
            this.lastConstruction = new int[goods];
            this.constructionInitialized = false;
        }
        HISTORY_COLLECTION out = FACTIONS.player().res().out(FResources.RTYPE.CONSTRUCTION);
        for (int i = 0; i < goods; ++i) {
            RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(i);
            TRADABLE tradable = resource.tr();
            int current = out.get((Object)tradable);
            if (!this.constructionInitialized) {
                this.lastConstruction[i] = current;
                continue;
            }
            consumed[i] = FlowMeter.exactCounterDelta(current, this.lastConstruction[i], out.history((Object)tradable).get(1));
            this.lastConstruction[i] = current;
        }
        this.constructionInitialized = true;
        return consumed;
    }

    public long buyConstructionMaterials(int[] withdrawals, int[] stateWithdrawals, Roster roster, Wallets wallets, FirmLedger ledger) {
        if (!EconConfig.warehouseMarketEnabled || !this.prices.ready()) return 0L;
        this.lastConstructionPaid = 0L;
        int goods = RESOURCES.ALL().size();
        long paid = 0L;
        long remainingBudget = EconConfig.maxAutoBuySpendPerTick;
        for (int i = 0; i < goods; ++i) {
            int consumed;
            int n = consumed = withdrawals != null && i < withdrawals.length ? withdrawals[i] : 0;
            if (consumed <= 0) continue;
            RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(i);
            int stateConsumed = stateConstructionUnits(consumed, stateWithdrawals != null && i < stateWithdrawals.length ? stateWithdrawals[i] : 0);
            consumeStateConstructionTitle(resource, stateConsumed);
            int marketConsumed = consumed - stateConsumed;
            int price = this.prices.priceRoundedUp(i);
            if (price <= 0 || marketConsumed <= 0) continue;
            long normalCost = (long)marketConsumed * (long)price;
            long premiumCost = EconConfig.autoProcureConstruction
                    ? (long)Math.ceil((double)normalCost * EconConfig.autoProcurePremiumMultiplier)
                    : normalCost;
            long budgetedCost = EconConfig.autoProcureConstruction && premiumCost <= remainingBudget
                    ? premiumCost
                    : normalCost;
            int[] quantities = new int[goods];
            quantities[i] = marketConsumed;
            WarehouseMarket.SaleDistribution distribution = this.wholesale.distributeSaleDetailed(quantities, (int)Math.min(Integer.MAX_VALUE, budgetedCost), roster, wallets, ledger, true, true);
            int credited = distribution.credited();
            if (credited <= 0) continue;
            if (EconConfig.autoProcureConstruction && budgetedCost == premiumCost) {
                remainingBudget -= Math.min(premiumCost, (long)credited);
            }
            FACTIONS.player().credits().inc((double)(-credited), FCredits.CTYPE.MISC);
            paid += (long)credited;
        }
        this.lastConstructionPaid = paid;
        return paid;
    }

    private void consumeStateConstructionTitle(RESOURCE resource, int units) {
        if (resource == null || units <= 0) return;
        int left = units;
        for (Map.Entry<StockpileInstance, WarehouseMarket.Book[]> entry : this.sharedState.books.entrySet()) {
            WarehouseMarket.Book book;
            if (left <= 0 || !this.state.isStateOwned((RoomInstance)entry.getKey())) continue;
            WarehouseMarket.Book[] shelves = entry.getValue();
            if (resource.index() >= shelves.length || (book = shelves[resource.index()]) == null || book.unitsHeld <= 0) continue;
            book.stateOwned = true;
            int consumed = Math.min(left, book.unitsHeld);
            book.unitsHeld -= consumed;
            left -= consumed;
        }
    }

    static int stateConstructionUnits(int consumed, int deliveredByState) {
        if (consumed <= 0 || deliveredByState <= 0) return 0;
        return Math.min(consumed, deliveredByState);
    }

    // ── Export procurement ──────────────────────────────────────

    public int[] observeExportWithdrawals() {
        int goods = RESOURCES.ALL().size();
        int[] shipped = new int[goods];
        if (this.lastExport.length != goods) {
            this.lastExport = new int[goods];
            this.exportInitialized = false;
        }
        HISTORY_COLLECTION out = FACTIONS.player().res().out(FResources.RTYPE.TRADE);
        for (int i = 0; i < goods; ++i) {
            RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(i);
            TRADABLE tradable = resource.tr();
            int current = out.get((Object)tradable);
            if (!this.exportInitialized) {
                this.lastExport[i] = current;
                continue;
            }
            shipped[i] = FlowMeter.exactCounterDelta(current, this.lastExport[i], out.history((Object)tradable).get(1));
            this.lastExport[i] = current;
        }
        this.exportInitialized = true;
        return shipped;
    }

    public long buyExports(int[] shipments, Roster roster, Wallets wallets, FirmLedger ledger) {
        if (!EconConfig.warehouseMarketEnabled || !this.prices.ready()) return 0L;
        this.lastExportBought = 0L;
        int goods = RESOURCES.ALL().size();
        long paid = 0L;
        for (int i = 0; i < goods; ++i) {
            int shipped;
            int n = shipped = shipments != null && i < shipments.length ? shipments[i] : 0;
            if (shipped <= 0) continue;
            RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(i);
            int price = this.prices.priceRoundedUp(i);
            if (price <= 0) continue;
            int[] quantities = new int[goods];
            quantities[i] = shipped;
            WarehouseMarket.SaleDistribution distribution = this.wholesale.distributeSaleDetailed(quantities, (int)Math.min(Integer.MAX_VALUE, (long)shipped * (long)price), roster, wallets, ledger, true, true);
            int credited = distribution.credited();
            int free = Math.max(0, shipped - distribution.merchantUnits() - distribution.crownUnits() - distribution.directUnits());
            LOG.ln("[ECON] export settlement: " + resource.key + " units=" + shipped + " price=" + price + " warehouse=" + distribution.merchantUnits() + " crown=" + distribution.crownUnits() + " direct=" + distribution.directUnits() + " free=" + free + " paid=" + credited);
            if (credited <= 0) continue;
            FACTIONS.player().credits().inc((double)(-credited), FCredits.CTYPE.MISC);
            paid += (long)credited;
        }
        this.lastExportBought = paid;
        return paid;
    }
}
