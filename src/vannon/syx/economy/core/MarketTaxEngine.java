package vannon.syx.economy.core;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.RoomInstance;
import vannon.syx.economy.core.warehouse.market.MarketSharedState;

/**
 * T-107 MarketTaxEngine — extracted from WarehouseMarket as part of Sprint M-1.
 *
 * <p>Handles per-season inventory taxation of warehouse merchants. Taxes are
 * collected as a percentage of inventory value from non-state-owned stockpiles.</p>
 */
public final class MarketTaxEngine {

    private final MarketSharedState sharedState;
    private final StateWarehouses state;
    private final FlowPrices prices;

    private int lastTaxSeason = -1;
    long lastTaxed;
    int lastTaxPayers;

    public MarketTaxEngine(MarketSharedState sharedState, StateWarehouses state, FlowPrices prices) {
        this.sharedState = sharedState;
        this.state = state;
        this.prices = prices;
    }

    public void clear() {
        this.lastTaxSeason = -1;
        this.lastTaxed = 0L;
        this.lastTaxPayers = 0;
    }

    public long taxInventory(Roster roster, Wallets wallets, FirmLedger ledger) {
        int season = game.time.TIME.seasons().bitsSinceStart();
        if (this.lastTaxSeason == -1) {
            this.lastTaxSeason = season;
            return 0L;
        }
        if (season == this.lastTaxSeason) return 0L;
        this.lastTaxSeason = season;
        this.lastTaxed = 0L;
        this.lastTaxPayers = 0;
        int percent = Math.max(0, Math.min(100, EconConfig.warehouseTaxPercent));
        if (percent <= 0 || !this.prices.ready() || settlement.main.SETT.ROOMS() == null) return 0L;
        long collected = 0L;
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            java.util.ArrayList<settlement.entity.humanoid.Humanoid> merchants;
            long due;
            long value;
            StockpileInstance warehouse = (StockpileInstance)settlement.main.SETT.ROOMS().STOCKPILE.getInstance(i);
            if (warehouse == null || !warehouse.exists() || warehouse.employees() == null || this.state.isStateOwned((RoomInstance)warehouse) || (value = inventoryValue(warehouse)) <= 0L || (due = value * (long)percent / 100L) <= 0L || (merchants = WarehouseMarket.staff(roster, (RoomInstance)warehouse)).isEmpty()) continue;
            int bill = (int)Math.min(Integer.MAX_VALUE, due);
            int[] shares = FirmEconomyKernel.split(bill, merchants.size());
            int warehousePaid = 0;
            for (int m = 0; m < shares.length; ++m) {
                int paid = Math.min(shares[m], wallets.spendable(merchants.get(m)));
                if (paid <= 0) continue;
                wallets.add(merchants.get(m), -paid);
                collected += (long)paid;
                warehousePaid += paid;
                ++this.lastTaxPayers;
            }
            if (warehousePaid <= 0 || ledger == null) continue;
            ledger.recordFirmCost((RoomInstance)warehouse, warehousePaid);
        }
        if (collected > 0L) {
            game.faction.FACTIONS.player().credits().inc((double)collected, game.faction.FCredits.CTYPE.TAX);
        }
        this.lastTaxed = collected;
        return collected;
    }

    private long inventoryValue(StockpileInstance warehouse) {
        WarehouseMarket.Book[] shelf = this.sharedState.books.get(warehouse);
        if (shelf == null) return 0L;
        long value = 0L;
        for (int i = 0; i < shelf.length && i < RESOURCES.ALL().size(); ++i) {
            WarehouseMarket.Book book = shelf[i];
            if (book == null || book.unitsHeld <= 0 || book.stateOwned || (value += (long)book.unitsHeld * (long)this.prices.priceRoundedUp(i)) >= 0L) continue;
            return Long.MAX_VALUE;
        }
        return value;
    }
}
