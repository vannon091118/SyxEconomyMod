package vannon.syx.economy.core;

import game.faction.FACTIONS;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.infra.stockpile.StockpileTally;
import settlement.room.main.RoomInstance;
import vannon.syx.economy.core.warehouse.market.MarketSharedState;

/**
 * T-103 CrownTitleEngine — extracted from WarehouseMarket as part of Sprint M-1.
 *
 * <p>Handles all crown-title operations: producerless-output accumulation,
 * crown-goods purchasing (buyCheaperCrownGoods, buyRemainingCrownGoods), and
 * ownerless-retail-claim waiving. Operates on shared state and the
 * {@link WholesaleEngine} for tracking-field updates.</p>
 */
public final class CrownTitleEngine {

    private final MarketSharedState sharedState;
    private final StateWarehouses state;
    private final FlowPrices prices;
    private final WholesaleEngine wholesale;

    public CrownTitleEngine(MarketSharedState sharedState, StateWarehouses state, FlowPrices prices, WholesaleEngine wholesale) {
        this.sharedState = sharedState;
        this.state = state;
        this.prices = prices;
        this.wholesale = wholesale;
    }

    // ── Public API ──────────────────────────────────────────────

    public void recordProducerlessOutput(FlowMeter meter) {
        ensureCrownCapacity();
        for (int resource = 0; resource < this.sharedState.crownUnits.length; ++resource) {
            int units = meter.producerlessProducedSinceLastSample(resource);
            if (units <= 0) continue;
            this.sharedState.crownUnits[resource] = Math.min(Long.MAX_VALUE, this.sharedState.crownUnits[resource] + (long)units);
        }
    }

    public long crownUnits(RESOURCE resource) {
        if (resource == null) return 0L;
        ensureCrownCapacity();
        return Math.max(0L, this.sharedState.crownUnits[resource.index()]);
    }

    public long buyCheaperCrownGoods(Roster roster, Wallets wallets) {
        return buyStoredCrownGoods(roster, wallets, true);
    }

    public long buyRemainingCrownGoods(Roster roster, Wallets wallets) {
        return buyStoredCrownGoods(roster, wallets, false);
    }

    public WarehouseMarket.OwnerlessRetailClaims waiveOwnerlessRetailClaims(int[] soldQuantities, int[] wholesaleByResource) {
        int[] payable;
        int[] nArray = payable = soldQuantities == null ? new int[]{} : (int[])soldQuantities.clone();
        if (soldQuantities == null || wholesaleByResource == null) {
            return new WarehouseMarket.OwnerlessRetailClaims(0, payable);
        }
        ensureCrownCapacity();
        long waived = 0L;
        for (int resource = 0; resource < payable.length && resource < this.sharedState.abandonedUnits.length; ++resource) {
            int units;
            int sold = Math.max(0, payable[resource]);
            if (sold <= 0 || this.sharedState.abandonedUnits[resource] <= 0L || (units = (int)Math.min((long)sold, Math.min(this.sharedState.crownUnits[resource], this.sharedState.abandonedUnits[resource]))) <= 0) continue;
            int resourceDue = resource < wholesaleByResource.length ? Math.max(0, wholesaleByResource[resource]) : 0;
            waived = WarehouseMarket.safeMoneyAdd(waived, WarehouseMarket.proportionalValue(resourceDue, units, sold));
            int n = resource;
            payable[n] = payable[n] - units;
            int n2 = resource;
            this.sharedState.crownUnits[n2] = this.sharedState.crownUnits[n2] - (long)units;
            int n3 = resource;
            this.sharedState.abandonedUnits[n3] = this.sharedState.abandonedUnits[n3] - (long)units;
        }
        return new WarehouseMarket.OwnerlessRetailClaims((int)Math.min(Integer.MAX_VALUE, waived), payable);
    }

    // ── Private core ────────────────────────────────────────────

    void ensureCrownCapacity() {
        int goods = RESOURCES.ALL().size();
        if (this.sharedState.crownUnits.length != goods) {
            this.sharedState.crownUnits = Arrays.copyOf(this.sharedState.crownUnits, goods);
        }
        if (this.sharedState.abandonedUnits.length != goods) {
            this.sharedState.abandonedUnits = Arrays.copyOf(this.sharedState.abandonedUnits, goods);
        }
    }

    private long buyStoredCrownGoods(Roster roster, Wallets wallets, boolean beforePrivate) {
        if (!EconConfig.warehouseMarketEnabled || SETT.ROOMS() == null) return 0L;
        ensureCrownCapacity();
        StockpileTally tally = SETT.ROOMS().STOCKPILE.tally();
        long paidTotal = 0L;
        block0: for (int resourceIndex = 0; resourceIndex < RESOURCES.ALL().size(); ++resourceIndex) {
            int privatePrice;
            if (this.sharedState.crownUnits[resourceIndex] <= 0L) continue;
            RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(resourceIndex);
            int crownPrice = this.state.crownMarketPrice(resource);
            int n = privatePrice = this.prices.ready() ? this.prices.priceRoundedUp(resourceIndex) : Integer.MAX_VALUE;
            if (WarehouseMarket.crownBeforePrivate(crownPrice, privatePrice) != beforePrivate) continue;
            ArrayList<WarehouseMarket.CrownStorage> candidates = new ArrayList<WarehouseMarket.CrownStorage>();
            long physical = 0L;
            long warehouseTitle = 0L;
            for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
                StockpileInstance warehouse = (StockpileInstance)SETT.ROOMS().STOCKPILE.getInstance(i);
                if (warehouse == null || !warehouse.exists() || this.state.isStateOwned((RoomInstance)warehouse)) continue;
                int stored = Math.max(0, tally.amount.get(resource, warehouse));
                physical += (long)stored;
                WarehouseMarket.Book[] shelf = this.sharedState.books.get(warehouse);
                WarehouseMarket.Book existing = shelf != null && resourceIndex < shelf.length ? shelf[resourceIndex] : null;
                int titled = existing == null ? 0 : Math.max(0, existing.unitsHeld);
                warehouseTitle += (long)titled;
                int shortfall = Math.max(0, stored - titled);
                if (shortfall <= 0 || WarehouseMarket.staff(roster, (RoomInstance)warehouse).isEmpty()) continue;
                candidates.add(new WarehouseMarket.CrownStorage(warehouse, shortfall));
            }
            long directTitle = directClaimUnits(resourceIndex);
            int offered = WarehouseMarket.crownPurchasableUnits(physical, warehouseTitle, directTitle, this.sharedState.crownUnits[resourceIndex]);
            if (offered <= 0) continue;
            long directReserve = directTitle;
            int remaining = offered;
            for (WarehouseMarket.CrownStorage candidate : candidates) {
                WarehouseMarket.Purchase purchase;
                if (remaining <= 0) continue block0;
                int eligible = candidate.untitledUnits();
                int reserved = (int)Math.min((long)eligible, directReserve);
                directReserve -= (long)reserved;
                if ((eligible -= reserved) <= 0 || (purchase = purchaseCrown(candidate.warehouse(), resource, Math.min(remaining, eligible), crownPrice, roster, wallets)).units() <= 0) continue;
                remaining -= purchase.units();
                int n2 = resourceIndex;
                this.sharedState.crownUnits[n2] = this.sharedState.crownUnits[n2] - (long)purchase.units();
                this.sharedState.abandonedUnits[resourceIndex] = Math.max(0L, this.sharedState.abandonedUnits[resourceIndex] - (long)purchase.units());
                paidTotal += (long)purchase.paid();
                this.wholesale.lastUnitsBought += purchase.units();
                this.wholesale.lastBought += (long)purchase.paid();
                this.state.recordCrownMarketSale(purchase.paid(), purchase.units());
            }
        }
        return paidTotal;
    }

    private WarehouseMarket.Purchase purchaseCrown(StockpileInstance warehouse, RESOURCE resource, int offered, int price, Roster roster, Wallets wallets) {
        ArrayList<Humanoid> merchants = WarehouseMarket.staff(roster, (RoomInstance)warehouse);
        if (merchants.isEmpty() || offered <= 0) return WarehouseMarket.Purchase.NONE;
        WarehouseMarket.Book book = book(warehouse, resource);
        if (price <= 0) {
            for (Humanoid merchant : merchants) {
                book.stakes.merge(merchant.id(), 1.0, Double::sum);
            }
            book.unitsHeld = WarehouseMarket.safeAdd(book.unitsHeld, offered);
            return new WarehouseMarket.Purchase(offered, 0);
        }
        int[] spendable = new int[merchants.size()];
        for (int i = 0; i < merchants.size(); ++i) spendable[i] = wallets.spendable(merchants.get(i));
        int units = Math.min(WarehouseKernel.affordableUnits(spendable, price, offered), Integer.MAX_VALUE / price);
        if (units <= 0) return WarehouseMarket.Purchase.NONE;
        int cost = units * price;
        int[] paidIn = WarehouseKernel.contributions(spendable, cost);
        for (int i = 0; i < merchants.size(); ++i) {
            if (paidIn[i] <= 0) continue;
            wallets.add(merchants.get(i), -paidIn[i]);
            book.stakes.merge(merchants.get(i).id(), Double.valueOf(paidIn[i]), Double::sum);
        }
        book.capitalBasis += (double)cost;
        book.unitsHeld = WarehouseMarket.safeAdd(book.unitsHeld, units);
        return new WarehouseMarket.Purchase(units, cost);
    }

    private long directClaimUnits(int resourceIndex) {
        long total = 0L;
        ArrayList<WarehouseMarket.DirectClaim> claims = this.sharedState.directClaims.get(resourceIndex);
        if (claims == null) return 0L;
        for (WarehouseMarket.DirectClaim claim : claims) {
            if (claim.producer == null || !claim.producer.exists() || claim.unitsHeld <= 0) continue;
            total = Math.min(Long.MAX_VALUE, total + (long)claim.unitsHeld);
        }
        return total;
    }

    private WarehouseMarket.Book book(StockpileInstance warehouse, RESOURCE resource) {
        WarehouseMarket.Book[] shelf = this.sharedState.books.computeIfAbsent(warehouse, ignored -> new WarehouseMarket.Book[RESOURCES.ALL().size()]);
        if (shelf[resource.index()] == null) {
            shelf[resource.index()] = new WarehouseMarket.Book();
        }
        return shelf[resource.index()];
    }

    private int retailUnitPrice(int resourceIndex) {
        if (resourceIndex < 0 || resourceIndex >= RESOURCES.ALL().size()) return 0;
        RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(resourceIndex);
        if (this.prices.ready()) return Math.max(0, this.prices.priceRoundedUp(resourceIndex));
            int partnerPrice = PolityPriceAnchor.priceOf(resource);
            return Math.max(0, partnerPrice > 0 ? partnerPrice : FACTIONS.PRICE().get(resource.tr()));
    }
}
