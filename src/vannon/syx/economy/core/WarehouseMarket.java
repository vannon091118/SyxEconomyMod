package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.faction.FResources;
import game.time.TIME;
import init.race.RACES;
import init.race.RaceResources;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.resources.ResGDrink;
import init.resources.ResGEat;
import init.trade.TRADABLE;
import init.type.HCLASSES;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.misc.util.TILE_STORAGE;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.infra.stockpile.StockpileTally;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.room.service.food.canteen.EconomyCanteenAccess;
import settlement.room.service.food.canteen.ROOM_CANTEEN;
import settlement.room.service.food.eatery.ROOM_EATERY;
import settlement.room.service.food.eatery.RoomDistribution;
import settlement.room.service.food.tavern.ROOM_TAVERN;
import settlement.room.service.market.ROOM_MARKET;
import settlement.stats.STATS;
import settlement.thing.THINGS;
import settlement.thing.ThingsResources;
import snake2d.LOG;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.FirmEconomyKernel;
import vannon.syx.economy.core.FirmLedger;
import vannon.syx.economy.core.FlowMeter;
import vannon.syx.economy.core.FlowPrices;
import vannon.syx.economy.core.PolityPriceAnchor;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.StateWarehouses;
import vannon.syx.economy.core.Wallets;
import vannon.syx.economy.core.WarehouseKernel;
import vannon.syx.economy.core.warehouse.market.MarketSharedState;
import util.statistics.HISTORY_COLLECTION;

public final class WarehouseMarket implements Saveable {
    static final int FORMAT = 7;
    // Sprint M-1 / T-101: 11 reference-keyed data fields + scalar flags moved into
    // MarketSharedState. WarehouseMarket keeps tracking-stat fields (lastBought, etc.)
    // for now; those migrate into per-engine aggregations in T-102..T-108.
    private final MarketSharedState sharedState = new MarketSharedState();
    private final WholesaleEngine wholesale;
    private final StateWarehouses state;
    private final FlowPrices prices;
    private long lastBought;
    private long lastSold;
    private int lastUnitsBought;
    private int lastUnitsSold;
    private int lastTaxSeason = -1;
    private long lastTaxed;
    private int lastTaxPayers;
    private int[] lastConstruction = new int[0];
    private boolean constructionInitialized;
    private long lastConstructionPaid;
    private int[] lastExport = new int[0];
    private boolean exportInitialized;
    private long lastExportBought;

    static boolean supportsFormat(int version) {
        return version >= 1 && version <= 7;
    }

    public WarehouseMarket(StateWarehouses state, FlowPrices prices) {
        this.state = state;
        this.prices = prices;
        this.wholesale = new WholesaleEngine(sharedState, state, prices);
    }

    public long lastConstructionPaid() {
        return this.lastConstructionPaid;
    }

    public long lastExportBought() {
        return this.lastExportBought;
    }

    public long lastBought() {
        return this.wholesale.lastBought;
    }

    public long lastSold() {
        return this.wholesale.lastSold;
    }

    public int lastUnitsBought() {
        return this.wholesale.lastUnitsBought;
    }

    public int lastUnitsSold() {
        return this.wholesale.lastUnitsSold;
    }

    public long lastTaxed() {
        return this.lastTaxed;
    }

    public int lastTaxPayers() {
        return this.lastTaxPayers;
    }

    public void beginPurchases() {
        this.wholesale.beginPurchases();
    }

    public long buy(FlowMeter meter, FlowPrices prices, Roster roster, Wallets wallets, FirmLedger ledger) {
        return this.wholesale.buy(meter, prices, roster, wallets, ledger);
    }

    public void recordProducerlessOutput(FlowMeter meter) {
        this.ensureCrownCapacity();
        for (int resource = 0; resource < this.sharedState.crownUnits.length; ++resource) {
            int units = meter.producerlessProducedSinceLastSample(resource);
            if (units <= 0) continue;
            this.sharedState.crownUnits[resource] = Math.min(Long.MAX_VALUE, this.sharedState.crownUnits[resource] + (long)units);
        }
    }

    private void ensureCrownCapacity() {
        int goods = RESOURCES.ALL().size();
        if (this.sharedState.crownUnits.length != goods) {
            this.sharedState.crownUnits = Arrays.copyOf(this.sharedState.crownUnits, goods);
        }
        if (this.sharedState.abandonedUnits.length != goods) {
            this.sharedState.abandonedUnits = Arrays.copyOf(this.sharedState.abandonedUnits, goods);
        }
    }

    public long crownUnits(RESOURCE resource) {
        if (resource == null) {
            return 0L;
        }
        this.ensureCrownCapacity();
        return Math.max(0L, this.sharedState.crownUnits[resource.index()]);
    }

    public long buyCheaperCrownGoods(Roster roster, Wallets wallets) {
        return this.buyStoredCrownGoods(roster, wallets, true);
    }

    public long buyRemainingCrownGoods(Roster roster, Wallets wallets) {
        return this.buyStoredCrownGoods(roster, wallets, false);
    }

    private long buyStoredCrownGoods(Roster roster, Wallets wallets, boolean beforePrivate) {
        if (!EconConfig.warehouseMarketEnabled || SETT.ROOMS() == null) {
            return 0L;
        }
        this.ensureCrownCapacity();
        StockpileTally tally = SETT.ROOMS().STOCKPILE.tally();
        long paidTotal = 0L;
        block0: for (int resourceIndex = 0; resourceIndex < RESOURCES.ALL().size(); ++resourceIndex) {
            int privatePrice;
            if (this.sharedState.crownUnits[resourceIndex] <= 0L) continue;
            RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(resourceIndex);
            int crownPrice = this.state.crownMarketPrice(resource);
            int n = privatePrice = this.prices.ready() ? this.prices.priceRoundedUp(resourceIndex) : Integer.MAX_VALUE;
            if (WarehouseMarket.crownBeforePrivate(crownPrice, privatePrice) != beforePrivate) continue;
            ArrayList<CrownStorage> candidates = new ArrayList<CrownStorage>();
            long physical = 0L;
            long warehouseTitle = 0L;
            for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
                StockpileInstance warehouse = (StockpileInstance)SETT.ROOMS().STOCKPILE.getInstance(i);
                if (warehouse == null || !warehouse.exists() || this.state.isStateOwned((RoomInstance)warehouse)) continue;
                int stored = Math.max(0, tally.amount.get(resource, warehouse));
                physical += (long)stored;
                Book[] shelf = this.sharedState.books.get(warehouse);
                Book existing = shelf != null && resourceIndex < shelf.length ? shelf[resourceIndex] : null;
                int titled = existing == null ? 0 : Math.max(0, existing.unitsHeld);
                warehouseTitle += (long)titled;
                int shortfall = Math.max(0, stored - titled);
                if (shortfall <= 0 || WarehouseMarket.staff(roster, (RoomInstance)warehouse).isEmpty()) continue;
                candidates.add(new CrownStorage(warehouse, shortfall));
            }
            long directTitle = this.directClaimUnits(resourceIndex);
            int offered = WarehouseMarket.crownPurchasableUnits(physical, warehouseTitle, directTitle, this.sharedState.crownUnits[resourceIndex]);
            if (offered <= 0) continue;
            long directReserve = directTitle;
            int remaining = offered;
            for (CrownStorage candidate : candidates) {
                Purchase purchase;
                if (remaining <= 0) continue block0;
                int eligible = candidate.untitledUnits();
                int reserved = (int)Math.min((long)eligible, directReserve);
                directReserve -= (long)reserved;
                if ((eligible -= reserved) <= 0 || (purchase = this.purchaseCrown(candidate.warehouse(), resource, Math.min(remaining, eligible), crownPrice, roster, wallets)).units() <= 0) continue;
                remaining -= purchase.units();
                int n2 = resourceIndex;
                this.sharedState.crownUnits[n2] = this.sharedState.crownUnits[n2] - (long)purchase.units();
                this.sharedState.abandonedUnits[resourceIndex] = Math.max(0L, this.sharedState.abandonedUnits[resourceIndex] - (long)purchase.units());
                paidTotal += (long)purchase.paid();
                this.lastUnitsBought += purchase.units();
                this.lastBought += (long)purchase.paid();
                this.state.recordCrownMarketSale(purchase.paid(), purchase.units());
            }
        }
        return paidTotal;
    }

    public static boolean crownBeforePrivate(int crownPrice, int privatePrice) {
        return Math.max(0, crownPrice) <= Math.max(0, privatePrice);
    }

    public static int crownPurchasableUnits(long physical, long warehouseTitle, long directTitle, long crownAvailable) {
        long untitled = Math.max(0L, physical - Math.max(0L, warehouseTitle) - Math.max(0L, directTitle));
        return (int)Math.min(Integer.MAX_VALUE, Math.min(Math.max(0L, crownAvailable), untitled));
    }

    private long directClaimUnits(int resourceIndex) {
        long total = 0L;
        ArrayList<DirectClaim> claims = this.sharedState.directClaims.get(resourceIndex);
        if (claims == null) {
            return 0L;
        }
        for (DirectClaim claim : claims) {
            if (claim.producer == null || !claim.producer.exists() || claim.unitsHeld <= 0) continue;
            total = Math.min(Long.MAX_VALUE, total + (long)claim.unitsHeld);
        }
        return total;
    }

    private Purchase purchaseCrown(StockpileInstance warehouse, RESOURCE resource, int offered, int price, Roster roster, Wallets wallets) {
        ArrayList<Humanoid> merchants = WarehouseMarket.staff(roster, (RoomInstance)warehouse);
        if (merchants.isEmpty() || offered <= 0) {
            return Purchase.NONE;
        }
        Book book = this.book(warehouse, resource);
        if (price <= 0) {
            for (Humanoid merchant : merchants) {
                book.stakes.merge(merchant.id(), 1.0, Double::sum);
            }
            book.unitsHeld = WarehouseMarket.safeAdd(book.unitsHeld, offered);
            return new Purchase(offered, 0);
        }
        int[] spendable = new int[merchants.size()];
        for (int i = 0; i < merchants.size(); ++i) {
            spendable[i] = wallets.spendable(merchants.get(i));
        }
        int units = Math.min(WarehouseKernel.affordableUnits(spendable, price, offered), Integer.MAX_VALUE / price);
        if (units <= 0) {
            return Purchase.NONE;
        }
        int cost = units * price;
        int[] paidIn = WarehouseKernel.contributions(spendable, cost);
        for (int i = 0; i < merchants.size(); ++i) {
            if (paidIn[i] <= 0) continue;
            wallets.add(merchants.get(i), -paidIn[i]);
            book.stakes.merge(merchants.get(i).id(), Double.valueOf(paidIn[i]), Double::sum);
        }
        book.capitalBasis += (double)cost;
        book.unitsHeld = WarehouseMarket.safeAdd(book.unitsHeld, units);
        return new Purchase(units, cost);
    }

    private long buyOutput(FlowMeter.FirmSnapshot firm, RESOURCE resource, int units, int price, Roster roster, Wallets wallets, FirmLedger ledger) {
        int remaining = units;
        long paid = 0L;
        if (EconConfig.stateWarehousesEnabled && this.state.buysAt(resource, price)) {
            Purchase bought = this.stateBuy(firm, resource, remaining, this.state.buyPrice(resource), roster, wallets, ledger);
            remaining -= bought.units();
            paid += (long)bought.paid();
        }
        if (remaining <= 0) {
            return paid;
        }
        ArrayList<StockpileInstance> buyers = this.dealers(resource, false, roster);
        if (buyers.isEmpty()) {
            this.recordDirectClaim(firm.room(), resource, remaining);
            return paid;
        }
        int[] weights = new int[buyers.size()];
        long totalWeight = 0L;
        for (int i = 0; i < buyers.size(); ++i) {
            weights[i] = WarehouseMarket.freeSpace(resource, buyers.get(i));
            totalWeight += (long)weights[i];
        }
        if (totalWeight <= 0L) {
            this.recordDirectClaim(firm.room(), resource, remaining);
            return paid;
        }
        int marketUnits = remaining;
        int assignedOffers = 0;
        for (int i = 0; i < buyers.size() && assignedOffers < marketUnits; ++i) {
            int offer = (int)((long)marketUnits * (long)weights[i] / totalWeight);
            if (i == buyers.size() - 1) {
                offer = marketUnits - assignedOffers;
            }
            if ((offer = Math.min(offer, marketUnits - assignedOffers)) <= 0) continue;
            assignedOffers += offer;
            Purchase purchase = this.purchase(buyers.get(i), firm, resource, offer, price, roster, wallets, ledger);
            remaining -= purchase.units();
            paid += (long)purchase.paid();
        }
        if (remaining > 0) {
            this.recordDirectClaim(firm.room(), resource, remaining);
        }
        return paid;
    }

    private Purchase stateBuy(FlowMeter.FirmSnapshot firm, RESOURCE resource, int offered, int price, Roster roster, Wallets wallets, FirmLedger ledger) {
        ArrayList<StockpileInstance> granaries = this.dealers(resource, true, roster);
        if (granaries.isEmpty()) {
            return Purchase.NONE;
        }
        long budget = Math.max(0L, (long)Math.floor(FACTIONS.player().credits().credits()));
        int affordable = (int)Math.min((long)offered, budget / (long)Math.max(1, price));
        if (affordable <= 0) {
            return Purchase.NONE;
        }
        int taken = 0;
        int paid = 0;
        for (StockpileInstance granary : granaries) {
            int room;
            if (taken >= affordable) break;
            if (!this.state.isHoarding((RoomInstance)granary) || (room = WarehouseMarket.freeSpace(resource, granary)) <= 0) continue;
            int units = Math.min(affordable - taken, room);
            int cost = units * price;
            int credited = ledger.distributeFirmRevenue(roster, wallets, firm.room(), cost);
            if (credited <= 0) continue;
            FACTIONS.player().credits().inc((double)(-credited), FCredits.CTYPE.MISC);
            this.book((StockpileInstance)granary, (RESOURCE)resource).unitsHeld += units;
            taken += units;
            paid += credited;
            this.state.recordPurchase(credited, units);
        }
        this.lastUnitsBought += taken;
        return new Purchase(taken, paid);
    }

    private Purchase purchase(StockpileInstance warehouse, FlowMeter.FirmSnapshot firm, RESOURCE resource, int offered, int price, Roster roster, Wallets wallets, FirmLedger ledger) {
        ArrayList<Humanoid> merchants = WarehouseMarket.staff(roster, (RoomInstance)warehouse);
        if (merchants.isEmpty()) {
            return Purchase.NONE;
        }
        int[] spendable = new int[merchants.size()];
        for (int i = 0; i < merchants.size(); ++i) {
            spendable[i] = wallets.spendable(merchants.get(i));
        }
        int units = WarehouseKernel.affordableUnits(spendable, price, offered);
        if (units <= 0) {
            return Purchase.NONE;
        }
        int cost = units * price;
        int credited = ledger.distributeFirmRevenue(roster, wallets, firm.room(), cost);
        if (credited <= 0) {
            return Purchase.NONE;
        }
        int[] paidIn = WarehouseKernel.contributions(spendable, credited);
        Book book = this.book(warehouse, resource);
        for (int i = 0; i < merchants.size(); ++i) {
            if (paidIn[i] <= 0) continue;
            wallets.add(merchants.get(i), -paidIn[i]);
            book.stakes.merge(merchants.get(i).id(), Double.valueOf(paidIn[i]), Double::sum);
        }
        book.capitalBasis += (double)credited;
        book.unitsHeld += units;
        this.lastUnitsBought += units;
        return new Purchase(units, credited);
    }

    public void settleSeizures(Roster roster, Wallets wallets) {
        if (SETT.ROOMS() == null) {
            return;
        }
        this.ensureCrownCapacity();
        for (Map.Entry<StockpileInstance, Book[]> entry : this.sharedState.books.entrySet()) {
            boolean nowState = this.state.isStateOwned((RoomInstance)entry.getKey());
            for (Book book : entry.getValue()) {
                if (book == null) continue;
                if (nowState && !book.stateOwned && !book.stakes.isEmpty()) {
                    for (Map.Entry<Integer, Double> stake : book.stakes.entrySet()) {
                        int owed;
                        Humanoid holder = WarehouseMarket.alive(roster, stake.getKey());
                        if (holder == null || (owed = (int)Math.min(Integer.MAX_VALUE, Math.round(Math.max(0.0, stake.getValue())))) <= 0) continue;
                        wallets.accrueTax(holder, owed);
                    }
                    book.stakes.clear();
                    book.capitalBasis = 0.0;
                }
                book.stateOwned = nowState;
            }
        }
    }

    public Settlement sellInputs(FlowMeter meter, FlowPrices prices, Roster roster, Wallets wallets, FirmLedger ledger) {
        return this.wholesale.sellInputs(meter, prices, roster, wallets, ledger);
    }

    private int marketTitledUnits(RESOURCE resource, int wanted, int marketPrice) {
        if (resource == null || wanted <= 0) {
            return 0;
        }
        int index = resource.index();
        long warehouseUnits = 0L;
        for (Map.Entry<StockpileInstance, Book[]> entry : this.sharedState.books.entrySet()) {
            Book book;
            Book[] shelf = entry.getValue();
            if (index >= shelf.length || (book = shelf[index]) == null || book.unitsHeld <= 0 || book.stateOwned && !this.state.sellsAt((RoomInstance)entry.getKey(), resource, marketPrice)) continue;
            warehouseUnits += (long)book.unitsHeld;
        }
        int titled = (int)Math.min((long)wanted, Math.min(Integer.MAX_VALUE, warehouseUnits));
        int remaining = wanted - titled;
        if (remaining <= 0) {
            return titled;
        }
        long producerUnits = 0L;
        ArrayList<DirectClaim> claims = this.sharedState.directClaims.get(index);
        if (claims != null) {
            for (DirectClaim claim : claims) {
                if (claim.producer == null || !claim.producer.exists() || claim.unitsHeld <= 0) continue;
                producerUnits += (long)claim.unitsHeld;
            }
        }
        return titled + (int)Math.min((long)remaining, Math.min(Integer.MAX_VALUE, producerUnits));
    }

    static long inputGross(int units, int marketUnits, int marketPrice, int crownPrice) {
        int total = Math.max(0, units);
        int market = Math.min(total, Math.max(0, marketUnits));
        int crown = total - market;
        long gross = (long)market * (long)Math.max(0, marketPrice) + (long)crown * (long)Math.max(0, crownPrice);
        return gross < 0L ? Long.MAX_VALUE : gross;
    }

    static int crownPricedUnits(int units, long crownAvailable, int marketTitleAvailable) {
        int total = Math.max(0, units);
        int trackedCrown = (int)Math.min((long)total, Math.max(0L, crownAvailable));
        int market = Math.min(total - trackedCrown, Math.max(0, marketTitleAvailable));
        return total - market;
    }

    public static int proportionalValue(int amount, long claimedValue, long totalValue) {
        if (amount <= 0 || claimedValue <= 0L || totalValue <= 0L) {
            return 0;
        }
        if (claimedValue >= totalValue) {
            return amount;
        }
        return BigInteger.valueOf(amount).multiply(BigInteger.valueOf(claimedValue)).divide(BigInteger.valueOf(totalValue)).intValue();
    }

    private static int charge(RoomInstance room, int amount, Roster roster, Wallets wallets) {
        if (room == null || amount <= 0) {
            return 0;
        }
        ArrayList<Humanoid> workers = WarehouseMarket.staff(roster, room);
        if (workers.isEmpty()) {
            return 0;
        }
        int[] shares = FirmEconomyKernel.split(amount, workers.size());
        int collected = 0;
        for (int i = 0; i < shares.length; ++i) {
            int due = Math.min(shares[i], wallets.spendable(workers.get(i)));
            if (due <= 0) continue;
            wallets.add(workers.get(i), -due);
            collected += due;
        }
        return collected;
    }

    public void observeRetailDeliveries() {
        if (SETT.ROOMS() == null) {
            return;
        }
        Set<RoomInstance> live = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RoomBlueprintIns<?> blueprint : SETT.ROOMS().ins()) {
            if (!WarehouseMarket.isRetailBlueprint(blueprint)) continue;
            for (int i = 0; i < blueprint.instancesSize(); ++i) {
                RoomInstance room2 = blueprint.getInstance(i);
                if (room2 == null || !room2.exists()) continue;
                live.add(room2);
                this.syncRetail(room2, null);
            }
        }
        this.sharedState.retailBooks.keySet().removeIf(room -> !live.contains(room));
    }

    public RetailQuote retailWholesaleQuote(RoomInstance seller, int[] soldQuantities) {
        if (seller == null || soldQuantities == null || !WarehouseMarket.isRetailBlueprint(seller.blueprintI())) {
            return new RetailQuote(0, new int[RESOURCES.ALL().size()]);
        }
        return this.syncRetail(seller, soldQuantities);
    }

    private RetailQuote syncRetail(RoomInstance room, int[] soldQuantities) {
        int[] current = WarehouseMarket.retailStock(room);
        int[] byResource = new int[RESOURCES.ALL().size()];
        RetailBook[] shelf = this.sharedState.retailBooks.computeIfAbsent(room, ignored -> new RetailBook[RESOURCES.ALL().size()]);
        long wholesale = 0L;
        for (int resource = 0; resource < shelf.length; ++resource) {
            int sold = soldQuantities == null || resource >= soldQuantities.length ? 0 : Math.max(0, soldQuantities[resource]);
            int now = resource < current.length ? Math.max(0, current[resource]) : 0;
            RetailBook book = shelf[resource];
            if (book == null && now == 0 && sold == 0) continue;
            if (book == null) {
                shelf[resource] = book = new RetailBook();
            }
            int beforeSale = WarehouseMarket.safeAdd(now, sold);
            if (book.observedStock < 0) {
                WarehouseMarket.appendRetailLot(book, beforeSale, this.retailUnitPrice(resource));
            } else {
                int change = beforeSale - book.observedStock;
                if (change > 0) {
                    WarehouseMarket.appendRetailLot(book, change, this.retailUnitPrice(resource));
                } else if (change < 0) {
                    WarehouseMarket.discardRetailUnits(book, -change);
                }
            }
            int missing = sold - WarehouseMarket.retailUnits(book);
            if (missing > 0) {
                WarehouseMarket.appendRetailLot(book, missing, this.retailUnitPrice(resource));
            }
            long resourceDue = WarehouseMarket.consumeRetailUnits(book, sold);
            byResource[resource] = (int)Math.min(Integer.MAX_VALUE, resourceDue);
            wholesale = WarehouseMarket.safeMoneyAdd(wholesale, resourceDue);
            book.observedStock = now;
        }
        return new RetailQuote((int)Math.min(Integer.MAX_VALUE, wholesale), byResource);
    }

    public OwnerlessRetailClaims waiveOwnerlessRetailClaims(int[] soldQuantities, int[] wholesaleByResource) {
        int[] payable;
        int[] nArray = payable = soldQuantities == null ? new int[]{} : (int[])soldQuantities.clone();
        if (soldQuantities == null || wholesaleByResource == null) {
            return new OwnerlessRetailClaims(0, payable);
        }
        this.ensureCrownCapacity();
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
        return new OwnerlessRetailClaims((int)Math.min(Integer.MAX_VALUE, waived), payable);
    }

    private int retailUnitPrice(int resourceIndex) {
        if (resourceIndex < 0 || resourceIndex >= RESOURCES.ALL().size()) {
            return 0;
        }
        RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(resourceIndex);
        return Math.max(0, this.prices.ready() ? this.prices.priceRoundedUp(resourceIndex) : PolityPriceAnchor.priceOf(resource));
    }

    private static void appendRetailLot(RetailBook book, int units, int price) {
        if (book == null || units <= 0) {
            return;
        }
        RetailLot last = book.lots.peekLast();
        if (last != null && last.unitPrice == Math.max(0, price)) {
            last.units = WarehouseMarket.safeAdd(last.units, units);
        } else {
            book.lots.addLast(new RetailLot(units, price));
        }
    }

    private static int retailUnits(RetailBook book) {
        long units = 0L;
        if (book != null) {
            for (RetailLot lot : book.lots) {
                units += (long)Math.max(0, lot.units);
            }
        }
        return (int)Math.min(Integer.MAX_VALUE, units);
    }

    private static long consumeRetailUnits(RetailBook book, int wanted) {
        long due = 0L;
        int remaining = Math.max(0, wanted);
        while (book != null && remaining > 0 && !book.lots.isEmpty()) {
            RetailLot lot = book.lots.peekFirst();
            int units = Math.min(remaining, lot.units);
            due = WarehouseMarket.safeMoneyAdd(due, (long)units * (long)lot.unitPrice);
            remaining -= units;
            lot.units -= units;
            if (lot.units > 0) continue;
            book.lots.removeFirst();
        }
        return due;
    }

    private static void discardRetailUnits(RetailBook book, int wanted) {
        int remaining = Math.max(0, wanted);
        while (book != null && remaining > 0 && !book.lots.isEmpty()) {
            RetailLot lot = book.lots.peekFirst();
            int units = Math.min(remaining, lot.units);
            remaining -= units;
            lot.units -= units;
            if (lot.units > 0) continue;
            book.lots.removeFirst();
        }
    }

    static long safeMoneyAdd(long left, long right) {
        if (right <= 0L) {
            return Math.max(0L, left);
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static boolean isRetailBlueprint(Object blueprint) {
        return blueprint instanceof ROOM_EATERY || blueprint instanceof ROOM_CANTEEN || blueprint instanceof ROOM_TAVERN || blueprint instanceof ROOM_MARKET;
    }

    private static int[] retailStock(RoomInstance room) {
        int[] stock;
        block7: {
            RoomDistribution.RoomDistributionIns distributed;
            block8: {
                block6: {
                    if (room == null) {
                        return new int[RESOURCES.ALL().size()];
                    }
                    if (room.blueprintI() instanceof ROOM_CANTEEN) {
                        return EconomyCanteenAccess.stock(room);
                    }
                    stock = new int[RESOURCES.ALL().size()];
                    if (!(room instanceof RoomDistribution.RoomDistributionIns)) {
                        return stock;
                    }
                    distributed = (RoomDistribution.RoomDistributionIns)room;
                    if (!(room.blueprintI() instanceof ROOM_EATERY)) break block6;
                    for (ResGEat food : RESOURCES.EDI().all()) {
                        stock[food.resource.index()] = WarehouseMarket.clampStock(distributed.distributionNlueData().stored(food.resource).get(room));
                    }
                    break block7;
                }
                if (!(room.blueprintI() instanceof ROOM_TAVERN)) break block8;
                for (ResGDrink drink : RESOURCES.DRINKS().all()) {
                    stock[drink.resource.index()] = WarehouseMarket.clampStock(distributed.distributionNlueData().stored(drink.resource).get(room));
                }
                break block7;
            }
            if (!(room.blueprintI() instanceof ROOM_MARKET)) break block7;
            for (RaceResources.RaceResource wearable : RACES.res().ALL) {
                stock[wearable.res.index()] = WarehouseMarket.clampStock(distributed.distributionNlueData().stored(wearable.res).get(room));
            }
        }
        return stock;
    }

    private static int clampStock(long stock) {
        return stock <= 0L ? 0 : (stock >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)stock);
    }

    public int distributeSale(int[] resourceQuantities, int amount, Roster roster, Wallets wallets, FirmLedger ledger) {
        return this.wholesale.distributeSale(resourceQuantities, amount, roster, wallets, ledger);
    }

    private SaleDistribution distributeSaleDetailed(int[] resourceQuantities, int amount, Roster roster, Wallets wallets, FirmLedger ledger, boolean crownFirst, boolean consumeCrownRemainder) {
        if (amount <= 0 || resourceQuantities == null) {
            return new SaleDistribution(0, 0, 0, 0);
        }
        int[] remaining = (int[])resourceQuantities.clone();
        MerchantDistribution merchants = this.distributeToMerchants(resourceQuantities, remaining, amount, roster, wallets, ledger);
        int credited = merchants.credited();
        int unclaimed = amount - merchants.claimed();
        if (unclaimed <= 0) {
            return new SaleDistribution(credited, merchants.claimedUnits(), 0, 0);
        }
        int crownClaimed = 0;
        if (crownFirst) {
            long demanded = WarehouseMarket.totalUnits(remaining);
            crownClaimed = this.consumeCrownTitle(remaining);
            if ((unclaimed -= WarehouseMarket.proportionalAmount(unclaimed, crownClaimed, demanded)) <= 0) {
                return new SaleDistribution(credited, merchants.claimedUnits(), 0, crownClaimed);
            }
        }
        DirectDistribution direct = this.distributeToDirectClaimants(remaining, unclaimed, roster, wallets, ledger);
        credited += direct.credited();
        if (!crownFirst && consumeCrownRemainder) {
            crownClaimed = this.consumeCrownTitle(remaining);
        }
        return new SaleDistribution(credited, merchants.claimedUnits(), direct.claimedUnits(), crownClaimed);
    }

    private int consumeCrownTitle(int[] remaining) {
        this.ensureCrownCapacity();
        long consumed = 0L;
        for (int resource = 0; resource < remaining.length && resource < this.sharedState.crownUnits.length; ++resource) {
            int wanted = Math.max(0, remaining[resource]);
            if (wanted <= 0 || this.sharedState.crownUnits[resource] <= 0L) continue;
            int units = WarehouseMarket.crownUnitsConsumed(this.sharedState.crownUnits[resource], wanted);
            int n = resource;
            this.sharedState.crownUnits[n] = this.sharedState.crownUnits[n] - (long)units;
            this.sharedState.abandonedUnits[resource] = Math.max(0L, this.sharedState.abandonedUnits[resource] - (long)units);
            int n2 = resource;
            remaining[n2] = remaining[n2] - units;
            consumed += (long)units;
        }
        return (int)Math.min(Integer.MAX_VALUE, consumed);
    }

    private int consumeCrownTitle(RESOURCE resource, int wanted) {
        if (resource == null || wanted <= 0) {
            return 0;
        }
        this.ensureCrownCapacity();
        int index = resource.index();
        int units = WarehouseMarket.crownUnitsConsumed(this.sharedState.crownUnits[index], wanted);
        int n = index;
        this.sharedState.crownUnits[n] = this.sharedState.crownUnits[n] - (long)units;
        this.sharedState.abandonedUnits[index] = Math.max(0L, this.sharedState.abandonedUnits[index] - (long)units);
        return units;
    }

    public static int crownUnitsConsumed(long available, int wanted) {
        if (available <= 0L || wanted <= 0) {
            return 0;
        }
        return (int)Math.min((long)wanted, Math.min(Integer.MAX_VALUE, available));
    }

    private static long totalUnits(int[] quantities) {
        long total = 0L;
        for (int quantity : quantities) {
            total += (long)Math.max(0, quantity);
        }
        return total;
    }

    private MerchantDistribution distributeToMerchants(int[] resourceQuantities, int[] remaining, int amount, Roster roster, Wallets wallets, FirmLedger ledger) {
        if (amount <= 0 || resourceQuantities == null) {
            return new MerchantDistribution(0, 0, 0);
        }
        if (!EconConfig.warehouseMarketEnabled) {
            return new MerchantDistribution(0, 0, 0);
        }
        ArrayList<WarehouseHolding> holders = new ArrayList<WarehouseHolding>();
        ArrayList<Integer> sold = new ArrayList<Integer>();
        long backed = 0L;
        long demanded = 0L;
        for (int index = 0; index < resourceQuantities.length; ++index) {
            int wanted = resourceQuantities[index];
            if (wanted <= 0) continue;
            demanded += (long)wanted;
            ArrayList<WarehouseHolding> shelf = new ArrayList<WarehouseHolding>();
            long held = 0L;
            RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(index);
            for (Map.Entry<StockpileInstance, Book[]> entry : this.sharedState.books.entrySet()) {
                Book book;
                Book[] shelves = entry.getValue();
                if (index >= shelves.length || (book = shelves[index]) == null || book.unitsHeld <= 0 || book.stateOwned && !this.state.sellsAt((RoomInstance)entry.getKey(), resource, this.prices.priceRoundedUp(index))) continue;
                shelf.add(new WarehouseHolding(entry.getKey(), book));
                held += (long)book.unitsHeld;
            }
            if (shelf.isEmpty()) continue;
            int claimable = (int)Math.min((long)wanted, held);
            int assigned = 0;
            for (int i = 0; i < shelf.size() && assigned < claimable; ++i) {
                WarehouseHolding holding = (WarehouseHolding)shelf.get(i);
                Book book = holding.book();
                int share = i == shelf.size() - 1 ? claimable - assigned : (int)((long)claimable * (long)book.unitsHeld / held);
                if ((share = Math.min(Math.min(share, book.unitsHeld), claimable - assigned)) <= 0) continue;
                assigned += share;
                holders.add(holding);
                sold.add(share);
            }
            backed += (long)assigned;
            remaining[index] = Math.max(0, remaining[index] - assigned);
        }
        if (backed <= 0L || demanded <= 0L) {
            return new MerchantDistribution(0, 0, 0);
        }
        int claimableAmount = (int)((long)amount * backed / demanded);
        if (claimableAmount <= 0) {
            return new MerchantDistribution(0, 0, 0);
        }
        int credited = 0;
        long assigned = 0L;
        for (int i = 0; i < holders.size(); ++i) {
            long share = i == holders.size() - 1 ? (long)claimableAmount - assigned : (long)claimableAmount * (long)((Integer)sold.get(i)).intValue() / backed;
            assigned += share;
            if (share <= 0L) continue;
            WarehouseHolding holding = (WarehouseHolding)holders.get(i);
            Book book = holding.book();
            boolean privateCapital = !book.stateOwned && !book.stakes.isEmpty();
            double capital = privateCapital ? WarehouseMarket.capitalForUnits(book, (Integer)sold.get(i)) : 0.0;
            int received = this.settle(book, (Integer)sold.get(i), (int)Math.min(Integer.MAX_VALUE, share), roster, wallets);
            credited += received;
            if (!privateCapital) continue;
            WarehouseMarket.recordMerchantProfit(ledger, holding.warehouse(), WarehouseMarket.realizedMerchantProfit(received, capital));
        }
        this.lastSold += (long)credited;
        return new MerchantDistribution(credited, claimableAmount, (int)backed);
    }

    private static double capitalForUnits(Book book, int unitsSold) {
        if (book == null || book.unitsHeld <= 0 || unitsSold <= 0) {
            return 0.0;
        }
        double capital = Double.isFinite(book.capitalBasis) ? Math.max(0.0, book.capitalBasis) : 0.0;
        return capital * (double)Math.min(book.unitsHeld, unitsSold) / (double)book.unitsHeld;
    }

    static double realizedMerchantProfit(double proceeds, double soldCapital) {
        if (!Double.isFinite(proceeds) || !Double.isFinite(soldCapital)) {
            return 0.0;
        }
        return proceeds - Math.max(0.0, soldCapital);
    }

    private static void recordMerchantProfit(FirmLedger ledger, StockpileInstance warehouse, double profit) {
        if (ledger == null || warehouse == null || !Double.isFinite(profit) || profit == 0.0) {
            return;
        }
        if (profit > 0.0) {
            ledger.recordFirmRevenue((RoomInstance)warehouse, profit);
        } else {
            ledger.recordFirmCost((RoomInstance)warehouse, -profit);
        }
    }

    private void recordDirectClaim(RoomInstance producer, RESOURCE resource, int units) {
        if (producer == null || resource == null || units <= 0) {
            return;
        }
        ArrayList<DirectClaim> claims = this.sharedState.directClaims.computeIfAbsent(resource.index(), ignored -> new ArrayList<DirectClaim>());
        for (DirectClaim claim : claims) {
            if (claim.producer != producer) continue;
            claim.unitsHeld = WarehouseMarket.safeAdd(claim.unitsHeld, units);
            return;
        }
        claims.add(new DirectClaim(producer, units));
    }

    private DirectDistribution distributeToDirectClaimants(int[] remaining, int amount, Roster roster, Wallets wallets, FirmLedger ledger) {
        long demanded = 0L;
        for (int units : remaining) {
            demanded += (long)Math.max(0, units);
        }
        if (demanded <= 0L || amount <= 0) {
            return new DirectDistribution(0, 0);
        }
        ArrayList<DirectSale> sales = new ArrayList<DirectSale>();
        long claimed = 0L;
        for (int resource = 0; resource < remaining.length; ++resource) {
            ArrayList<DirectClaim> claims;
            int wanted = remaining[resource];
            if (wanted <= 0 || (claims = this.sharedState.directClaims.get(resource)) == null) continue;
            Iterator<DirectClaim> iterator = claims.iterator();
            while (iterator.hasNext() && wanted > 0) {
                DirectClaim claim = iterator.next();
                if (claim.producer == null || !claim.producer.exists() || claim.unitsHeld <= 0) {
                    iterator.remove();
                    continue;
                }
                int units = Math.min(wanted, claim.unitsHeld);
                claim.unitsHeld -= units;
                wanted -= units;
                claimed += (long)units;
                sales.add(new DirectSale(claim.producer, units));
                if (claim.unitsHeld > 0) continue;
                iterator.remove();
            }
            remaining[resource] = wanted;
            if (!claims.isEmpty()) continue;
            this.sharedState.directClaims.remove(resource);
        }
        if (claimed <= 0L) {
            return new DirectDistribution(0, 0);
        }
        int claimableAmount = WarehouseMarket.proportionalAmount(amount, claimed, demanded);
        int credited = 0;
        long assigned = 0L;
        for (int i = 0; i < sales.size(); ++i) {
            DirectSale sale = (DirectSale)sales.get(i);
            long share = i == sales.size() - 1 ? (long)claimableAmount - assigned : (long)claimableAmount * (long)sale.units() / claimed;
            assigned += share;
            if (share <= 0L) continue;
            credited += ledger.distributeFirmRevenue(roster, wallets, sale.producer(), (int)Math.min(Integer.MAX_VALUE, share));
        }
        return new DirectDistribution(credited, (int)Math.min(Integer.MAX_VALUE, claimed));
    }

    static int proportionalAmount(int amount, long claimedUnits, long demandedUnits) {
        if (amount <= 0 || claimedUnits <= 0L || demandedUnits <= 0L) {
            return 0;
        }
        return (int)Math.min(Integer.MAX_VALUE, (long)amount * Math.min(claimedUnits, demandedUnits) / demandedUnits);
    }

    static int safeAdd(int left, int right) {
        return (int)Math.min(Integer.MAX_VALUE, (long)Math.max(0, left) + (long)Math.max(0, right));
    }

    static long safeAdd(long left, long right) {
        long base = Math.max(0L, left);
        long addition = Math.max(0L, right);
        return addition > Long.MAX_VALUE - base ? Long.MAX_VALUE : base + addition;
    }

    private int settle(Book book, int unitsSold, int proceeds, Roster roster, Wallets wallets) {
        if (book.stateOwned) {
            book.unitsHeld = Math.max(0, book.unitsHeld - unitsSold);
            this.lastUnitsSold += unitsSold;
            this.state.recordSale(proceeds, unitsSold);
            return 0;
        }
        ArrayList<Integer> holders = new ArrayList<Integer>(book.stakes.keySet());
        if (holders.isEmpty()) {
            book.unitsHeld = Math.max(0, book.unitsHeld - unitsSold);
            book.capitalBasis = 0.0;
            this.lastUnitsSold += unitsSold;
            return 0;
        }
        double[] stakes = new double[holders.size()];
        boolean[] living = new boolean[holders.size()];
        Humanoid[] people = new Humanoid[holders.size()];
        for (int i = 0; i < holders.size(); ++i) {
            stakes[i] = book.stakes.get(holders.get(i));
            people[i] = WarehouseMarket.alive(roster, holders.get(i));
            living[i] = people[i] != null;
        }
        int[] payouts = WarehouseKernel.payouts(stakes, living, proceeds);
        int credited = 0;
        for (int i = 0; i < payouts.length; ++i) {
            if (payouts[i] <= 0 || people[i] == null) continue;
            wallets.add(people[i], payouts[i]);
            credited += payouts[i];
        }
        int held = book.unitsHeld;
        double capitalHeld = book.capitalBasis;
        for (int i = 0; i < holders.size(); ++i) {
            double left = WarehouseKernel.decayStakes(stakes[i], unitsSold, held);
            if (left <= 0.0 || !living[i]) {
                book.stakes.remove(holders.get(i));
                continue;
            }
            book.stakes.put(holders.get(i), left);
        }
        book.unitsHeld = Math.max(0, held - unitsSold);
        book.capitalBasis = WarehouseKernel.decayStakes(capitalHeld, unitsSold, held);
        this.lastUnitsSold += unitsSold;
        return credited;
    }

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
        if (!EconConfig.warehouseMarketEnabled || !this.prices.ready()) {
            return 0L;
        }
        this.lastConstructionPaid = 0L;
        int goods = RESOURCES.ALL().size();
        long paid = 0L;
        long remainingBudget = EconConfig.maxAutoBuySpendPerTick;
        for (int i = 0; i < goods; ++i) {
            int consumed;
            int n = consumed = withdrawals != null && i < withdrawals.length ? withdrawals[i] : 0;
            if (consumed <= 0) continue;
            RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(i);
            int stateConsumed = WarehouseMarket.stateConstructionUnits(consumed, stateWithdrawals != null && i < stateWithdrawals.length ? stateWithdrawals[i] : 0);
            this.consumeStateConstructionTitle(resource, stateConsumed);
            int marketConsumed = consumed - stateConsumed;
            int price = this.prices.priceRoundedUp(i);
            if (price <= 0 || marketConsumed <= 0) {
                continue;
            }
            long normalCost = (long)marketConsumed * (long)price;
            long premiumCost = EconConfig.autoProcureConstruction
                    ? (long)Math.ceil((double)normalCost * EconConfig.autoProcurePremiumMultiplier)
                    : normalCost;
            long budgetedCost = EconConfig.autoProcureConstruction && premiumCost <= remainingBudget
                    ? premiumCost
                    : normalCost;
            int[] quantities = new int[goods];
            quantities[i] = marketConsumed;
            // TODO T-105: redirect to this.wholesale.distributeSaleDetailed() once
            // AutoProcurementEngine extracts — current call updates dead WM tracking fields
            SaleDistribution distribution = this.distributeSaleDetailed(quantities, (int)Math.min(Integer.MAX_VALUE, budgetedCost), roster, wallets, ledger, true, true);
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

    static int stateConstructionUnits(int consumed, int deliveredByState) {
        if (consumed <= 0 || deliveredByState <= 0) {
            return 0;
        }
        return Math.min(consumed, deliveredByState);
    }

    private void consumeStateConstructionTitle(RESOURCE resource, int units) {
        if (resource == null || units <= 0) {
            return;
        }
        int left = units;
        for (Map.Entry<StockpileInstance, Book[]> entry : this.sharedState.books.entrySet()) {
            Book book;
            if (left <= 0 || !this.state.isStateOwned((RoomInstance)entry.getKey())) continue;
            Book[] shelves = entry.getValue();
            if (resource.index() >= shelves.length || (book = shelves[resource.index()]) == null || book.unitsHeld <= 0) continue;
            book.stateOwned = true;
            int consumed = Math.min(left, book.unitsHeld);
            book.unitsHeld -= consumed;
            left -= consumed;
        }
    }

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
        if (!EconConfig.warehouseMarketEnabled || !this.prices.ready()) {
            return 0L;
        }
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
            // TODO T-105: redirect to this.wholesale.distributeSaleDetailed() once
            // AutoProcurementEngine extracts — current call updates dead WM tracking fields
            SaleDistribution distribution = this.distributeSaleDetailed(quantities, (int)Math.min(Integer.MAX_VALUE, (long)shipped * (long)price), roster, wallets, ledger, true, true);
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

    public long taxInventory(Roster roster, Wallets wallets, FirmLedger ledger) {
        int season = TIME.seasons().bitsSinceStart();
        if (this.lastTaxSeason == -1) {
            this.lastTaxSeason = season;
            return 0L;
        }
        if (season == this.lastTaxSeason) {
            return 0L;
        }
        this.lastTaxSeason = season;
        this.lastTaxed = 0L;
        this.lastTaxPayers = 0;
        int percent = Math.max(0, Math.min(100, EconConfig.warehouseTaxPercent));
        if (percent <= 0 || !this.prices.ready() || SETT.ROOMS() == null) {
            return 0L;
        }
        long collected = 0L;
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            ArrayList<Humanoid> merchants;
            long due;
            long value;
            StockpileInstance warehouse = (StockpileInstance)SETT.ROOMS().STOCKPILE.getInstance(i);
            if (warehouse == null || !warehouse.exists() || warehouse.employees() == null || this.state.isStateOwned((RoomInstance)warehouse) || (value = this.inventoryValue(warehouse)) <= 0L || (due = value * (long)percent / 100L) <= 0L || (merchants = WarehouseMarket.staff(roster, (RoomInstance)warehouse)).isEmpty()) continue;
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
            FACTIONS.player().credits().inc((double)collected, FCredits.CTYPE.TAX);
        }
        this.lastTaxed = collected;
        return collected;
    }

    private long inventoryValue(StockpileInstance warehouse) {
        Book[] shelf = this.sharedState.books.get(warehouse);
        if (shelf == null) {
            return 0L;
        }
        long value = 0L;
        for (int i = 0; i < shelf.length && i < RESOURCES.ALL().size(); ++i) {
            Book book = shelf[i];
            if (book == null || book.unitsHeld <= 0 || book.stateOwned || (value += (long)book.unitsHeld * (long)this.prices.priceRoundedUp(i)) >= 0L) continue;
            return Long.MAX_VALUE;
        }
        return value;
    }

    private ArrayList<StockpileInstance> dealers(RESOURCE resource, boolean stateOwned, Roster roster) {
        ArrayList<StockpileInstance> result = new ArrayList<StockpileInstance>();
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            StockpileInstance warehouse = (StockpileInstance)SETT.ROOMS().STOCKPILE.getInstance(i);
            if (warehouse == null || !warehouse.exists() || warehouse.employees() == null || !warehouse.crateMask.has(resource) || this.state.isStateOwned((RoomInstance)warehouse) != stateOwned || !stateOwned && WarehouseMarket.staff(roster, (RoomInstance)warehouse).isEmpty()) continue;
            result.add(warehouse);
        }
        return result;
    }

    private static int freeSpace(RESOURCE resource, StockpileInstance warehouse) {
        StockpileTally tally = SETT.ROOMS().STOCKPILE.tally();
        return Math.max(0, tally.space.get(resource, warehouse) - tally.amount.get(resource, warehouse));
    }

    public int stateStock(RESOURCE resource) {
        if (resource == null || SETT.ROOMS() == null) {
            return 0;
        }
        long total = 0L;
        StockpileTally tally = SETT.ROOMS().STOCKPILE.tally();
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            StockpileInstance warehouse = (StockpileInstance)SETT.ROOMS().STOCKPILE.getInstance(i);
            if (warehouse == null || !warehouse.exists() || !this.state.isStateOwned((RoomInstance)warehouse) || (total += (long)Math.max(0, tally.amount.get(resource, warehouse))) < Integer.MAX_VALUE) continue;
            return Integer.MAX_VALUE;
        }
        return (int)total;
    }

    static ArrayList<Humanoid> staff(Roster roster, RoomInstance room) {
        ArrayList<Humanoid> result = new ArrayList<Humanoid>();
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid worker = roster.get(i);
            if (worker.indu().clas() == HCLASSES.SLAVE() || STATS.WORK().EMPLOYED.get(worker.indu()) != room) continue;
            result.add(worker);
        }
        return result;
    }

    static Humanoid alive(Roster roster, int id) {
        for (int i = 0; i < roster.size(); ++i) {
            if (roster.get(i).id() != id) continue;
            return roster.get(i);
        }
        return null;
    }

    private Book book(StockpileInstance warehouse, RESOURCE resource) {
        Book[] shelf = this.sharedState.books.computeIfAbsent(warehouse, ignored -> new Book[RESOURCES.ALL().size()]);
        if (shelf[resource.index()] == null) {
            shelf[resource.index()] = new Book();
        }
        return shelf[resource.index()];
    }

    public void prune(Roster roster) {
        this.resolvePending();
        this.sharedState.books.keySet().removeIf(warehouse -> warehouse == null || !warehouse.exists());
        this.sharedState.intakeLocks.keySet().removeIf(warehouse -> warehouse == null || !warehouse.exists());
        this.sharedState.retailBooks.keySet().removeIf(room -> room == null || !room.exists());
        Iterator<Map.Entry<Integer, ArrayList<DirectClaim>>> resources = this.sharedState.directClaims.entrySet().iterator();
        while (resources.hasNext()) {
            ArrayList<DirectClaim> claims = resources.next().getValue();
            claims.removeIf(claim -> claim.producer == null || !claim.producer.exists() || claim.unitsHeld <= 0);
            if (!claims.isEmpty()) continue;
            resources.remove();
        }
        if (SETT.ROOMS() == null) {
            return;
        }
        this.ensureCrownCapacity();
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            StockpileInstance warehouse2 = (StockpileInstance)SETT.ROOMS().STOCKPILE.getInstance(i);
            if (warehouse2 == null || !warehouse2.exists()) continue;
            ArrayList<Humanoid> workers = WarehouseMarket.staff(roster, (RoomInstance)warehouse2);
            boolean stateOwned = this.state.isStateOwned((RoomInstance)warehouse2);
            if (stateOwned || !workers.isEmpty()) {
                this.unlockIntake(warehouse2);
            } else {
                this.lockIntake(warehouse2);
            }
            Book[] shelf = this.sharedState.books.get(warehouse2);
            if (shelf == null) continue;
            for (int resourceIndex = 0; resourceIndex < shelf.length; ++resourceIndex) {
                Book book = shelf[resourceIndex];
                if (book == null) continue;
                book.stateOwned = stateOwned;
                if (stateOwned) {
                    book.stakes.clear();
                    book.capitalBasis = 0.0;
                    continue;
                }
                if (workers.isEmpty()) {
                    if (resourceIndex < this.sharedState.crownUnits.length && book.unitsHeld > 0) {
                        this.sharedState.crownUnits[resourceIndex] = WarehouseMarket.safeAdd(this.sharedState.crownUnits[resourceIndex], (long)book.unitsHeld);
                    }
                    if (resourceIndex < this.sharedState.abandonedUnits.length && book.unitsHeld > 0) {
                        this.sharedState.abandonedUnits[resourceIndex] = WarehouseMarket.safeAdd(this.sharedState.abandonedUnits[resourceIndex], (long)book.unitsHeld);
                    }
                    book.unitsHeld = 0;
                    book.stakes.clear();
                    book.capitalBasis = 0.0;
                    continue;
                }
                WarehouseMarket.reconcileOwners(book, workers);
            }
        }
    }

    private static void reconcileOwners(Book book, ArrayList<Humanoid> workers) {
        if (book.stakes.isEmpty()) {
            return;
        }
        HashSet<Integer> employed = new HashSet<Integer>();
        for (Humanoid humanoid : workers) {
            employed.add(humanoid.id());
        }
        ArrayList<Integer> owners = new ArrayList<Integer>(book.stakes.keySet());
        for (Humanoid worker : workers) {
            if (book.stakes.containsKey(worker.id())) continue;
            owners.add(worker.id());
        }
        double[] dArray = new double[owners.size()];
        boolean[] stillEmployed = new boolean[owners.size()];
        for (int i = 0; i < owners.size(); ++i) {
            dArray[i] = book.stakes.getOrDefault(owners.get(i), 0.0);
            stillEmployed[i] = employed.contains(owners.get(i));
        }
        double[] reassigned = WarehouseKernel.redistributeStakes(dArray, stillEmployed);
        book.stakes.clear();
        for (int i = 0; i < owners.size(); ++i) {
            if (!(reassigned[i] > 0.0)) continue;
            book.stakes.put(owners.get(i), reassigned[i]);
        }
        if (book.stakes.isEmpty()) {
            book.capitalBasis = 0.0;
        }
    }

    private void lockIntake(StockpileInstance warehouse) {
        Map<Integer, Integer> locks = this.sharedState.intakeLocks.computeIfAbsent(warehouse, ignored -> new HashMap<Integer, Integer>());
        for (COORDINATE tile : warehouse.body()) {
            int free;
            TILE_STORAGE crate;
            if (!warehouse.is(tile) || (crate = warehouse.storage(tile.x(), tile.y())) == null || crate.resource() == null || (free = crate.storageReservable()) <= 0) continue;
            crate.storageReserve(free);
            locks.merge(WarehouseMarket.tileKey(tile.x(), tile.y()), free, Integer::sum);
        }
    }

    private void unlockIntake(StockpileInstance warehouse) {
        Map<Integer, Integer> locks = this.sharedState.intakeLocks.remove(warehouse);
        if (locks == null) {
            return;
        }
        for (Map.Entry<Integer, Integer> entry : locks.entrySet()) {
            int release;
            int key = entry.getKey();
            TILE_STORAGE crate = warehouse.storage(WarehouseMarket.tileX(key), WarehouseMarket.tileY(key));
            if (crate == null || (release = Math.min(Math.max(0, entry.getValue()), crate.storageReserved())) <= 0) continue;
            crate.storageUnreserve(release);
        }
    }

    private static int tileKey(int x, int y) {
        return x << 16 | y & 0xFFFF;
    }

    private static int tileX(int key) {
        return key >>> 16;
    }

    private static int tileY(int key) {
        return key & 0xFFFF;
    }

    private void resolvePending() {
        RESOURCE resource;
        StockpileInstance warehouse;
        if (this.sharedState.pending.isEmpty() && this.sharedState.pendingIntakeLocks.isEmpty() && this.sharedState.pendingDirectClaims.isEmpty() && this.sharedState.pendingRetailBooks.isEmpty() && !this.sharedState.inferCrownFromLoose || SETT.ROOMS() == null) {
            return;
        }
        for (PendingBook pendingBook : this.sharedState.pending) {
            warehouse = WarehouseMarket.warehouseAt(pendingBook.x, pendingBook.y);
            resource = WarehouseMarket.resource(pendingBook.resourceKey);
            if (warehouse == null || resource == null) continue;
            Book book = this.book(warehouse, resource);
            book.unitsHeld = pendingBook.unitsHeld;
            book.stakes.putAll(pendingBook.stakes);
            book.capitalBasis = pendingBook.capitalBasis;
        }
        this.sharedState.pending.clear();
        for (PendingIntakeLock pendingIntakeLock : this.sharedState.pendingIntakeLocks) {
            warehouse = WarehouseMarket.warehouseAt(pendingIntakeLock.x, pendingIntakeLock.y);
            if (warehouse == null) continue;
            this.sharedState.intakeLocks.put(warehouse, new HashMap<Integer, Integer>(pendingIntakeLock.tiles));
        }
        this.sharedState.pendingIntakeLocks.clear();
        for (PendingDirectClaim pendingDirectClaim : this.sharedState.pendingDirectClaims) {
            RoomInstance producer = WarehouseMarket.producerAt(pendingDirectClaim.x, pendingDirectClaim.y);
            resource = WarehouseMarket.resource(pendingDirectClaim.resourceKey);
            if (producer == null || resource == null || pendingDirectClaim.unitsHeld <= 0) continue;
            this.recordDirectClaim(producer, resource, pendingDirectClaim.unitsHeld);
        }
        this.sharedState.pendingDirectClaims.clear();
        for (PendingRetailBook pendingRetailBook : this.sharedState.pendingRetailBooks) {
            RoomInstance retailer = WarehouseMarket.producerAt(pendingRetailBook.x, pendingRetailBook.y);
            resource = WarehouseMarket.resource(pendingRetailBook.resourceKey);
            if (retailer == null || resource == null || !WarehouseMarket.isRetailBlueprint(retailer.blueprintI())) continue;
            RetailBook[] shelf = this.sharedState.retailBooks.computeIfAbsent(retailer, ignored -> new RetailBook[RESOURCES.ALL().size()]);
            RetailBook book = new RetailBook();
            book.observedStock = Math.max(0, pendingRetailBook.observedStock);
            for (RetailLot lot : pendingRetailBook.lots) {
                WarehouseMarket.appendRetailLot(book, lot.units, lot.unitPrice);
            }
            shelf[resource.index()] = book;
        }
        this.sharedState.pendingRetailBooks.clear();
        if (this.sharedState.inferCrownFromLoose) {
            this.inferCrownFromLoose();
            this.sharedState.inferCrownFromLoose = false;
        }
    }

    private void inferCrownFromLoose() {
        this.ensureCrownCapacity();
        long[] loose = new long[this.sharedState.crownUnits.length];
        for (Object tile : SETT.TILE_BOUNDS) {
            COORDINATE c = (COORDINATE) tile;
            THINGS.Thing thing = SETT.THINGS().getFirst(c.x(), c.y());
            while (thing != null) {
                ThingsResources.ScatteredResource pile;
                if (thing instanceof ThingsResources.ScatteredResource && !(pile = (ThingsResources.ScatteredResource) thing).isRemoved()) {
                    int resource = pile.resource().index();
                    if (resource >= 0 && resource < loose.length) {
                        loose[resource] = Math.min(Long.MAX_VALUE, loose[resource] + (long)Math.max(0, pile.amount()));
                    }
                }
                thing = thing.tileNext();
            }
        }
        long[] titled = new long[this.sharedState.crownUnits.length];
        for (Map.Entry<Integer, ArrayList<DirectClaim>> entry : this.sharedState.directClaims.entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() >= titled.length) continue;
            for (DirectClaim claim : entry.getValue()) {
                titled[entry.getKey()] = Math.min(Long.MAX_VALUE, titled[entry.getKey()] + (long)Math.max(0, claim.unitsHeld));
            }
        }
        for (Book[] bookArray : this.sharedState.books.values()) {
            for (int resource = 0; resource < bookArray.length && resource < titled.length; ++resource) {
                Book book = bookArray[resource];
                if (book == null || book.unitsHeld <= 0) continue;
                titled[resource] = Math.min(Long.MAX_VALUE, titled[resource] + (long)book.unitsHeld);
            }
        }
        for (int resource = 0; resource < this.sharedState.crownUnits.length; ++resource) {
            this.sharedState.crownUnits[resource] = Math.max(this.sharedState.crownUnits[resource], Math.max(0L, loose[resource] - titled[resource]));
        }
    }

    private static StockpileInstance warehouseAt(int x, int y) {
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            StockpileInstance warehouse = (StockpileInstance)SETT.ROOMS().STOCKPILE.getInstance(i);
            if (warehouse == null || !warehouse.exists() || warehouse.mX() != x || warehouse.mY() != y) continue;
            return warehouse;
        }
        return null;
    }

    private static RoomInstance producerAt(int x, int y) {
        for (RoomBlueprintIns blueprint : SETT.ROOMS().ins()) {
            for (int i = 0; i < blueprint.instancesSize(); ++i) {
                RoomInstance room = blueprint.getInstance(i);
                if (room == null || !room.exists() || room.mX() != x || room.mY() != y) continue;
                return room;
            }
        }
        return null;
    }

    private static RESOURCE resource(String key) {
        for (int i = 0; i < RESOURCES.ALL().size(); ++i) {
            RESOURCE candidate = (RESOURCE)RESOURCES.ALL().get(i);
            if (!candidate.key.equals(key)) continue;
            return candidate;
        }
        return null;
    }

    public void save(FilePutter file) {
        RetailBook[] shelf;
        this.resolvePending();
        file.i(7);
        file.i(this.lastTaxSeason);
        int count = 0;
        for (Book[] bookArray : this.sharedState.books.values()) {
            for (Book book : bookArray) {
                if (book == null || book.unitsHeld <= 0) continue;
                ++count;
            }
        }
        file.i(count);
        for (Map.Entry entry : this.sharedState.books.entrySet()) {
            StockpileInstance warehouse = (StockpileInstance)entry.getKey();
            if (warehouse == null || !warehouse.exists()) continue;
            for (int i = 0; i < ((Book[])entry.getValue()).length; ++i) {
                Book book = ((Book[])entry.getValue())[i];
                if (book == null || book.unitsHeld <= 0) continue;
                file.i(warehouse.mX());
                file.i(warehouse.mY());
                file.chars((CharSequence)((RESOURCE)RESOURCES.ALL().get((int)i)).key);
                file.i(book.unitsHeld);
                file.l(Math.round(Math.max(0.0, book.capitalBasis) * 100.0));
                file.i(book.stakes.size());
                for (Map.Entry entry2 : book.stakes.entrySet()) {
                    file.i(((Integer)entry2.getKey()).intValue());
                    file.l(Math.round((Double)entry2.getValue() * 100.0));
                }
            }
        }
        int lockCount = 0;
        for (Map.Entry<StockpileInstance, Map<Integer, Integer>> entry : this.sharedState.intakeLocks.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().exists() || entry.getValue().isEmpty()) continue;
            ++lockCount;
        }
        file.i(lockCount);
        for (Map.Entry<StockpileInstance, Map<Integer, Integer>> entry : this.sharedState.intakeLocks.entrySet()) {
            StockpileInstance stockpileInstance = entry.getKey();
            if (stockpileInstance == null || !stockpileInstance.exists() || entry.getValue().isEmpty()) continue;
            file.i(stockpileInstance.mX());
            file.i(stockpileInstance.mY());
            file.i(entry.getValue().size());
            for (Map.Entry<Integer, Integer> lock : entry.getValue().entrySet()) {
                file.i(lock.getKey());
                file.i(Math.max(0, lock.getValue()));
            }
        }
        int directClaimCount = 0;
        for (ArrayList<DirectClaim> claims : this.sharedState.directClaims.values()) {
            for (DirectClaim claim : claims) {
                if (claim.producer == null || !claim.producer.exists() || claim.unitsHeld <= 0) continue;
                ++directClaimCount;
            }
        }
        file.i(directClaimCount);
        for (Map.Entry<Integer, ArrayList<DirectClaim>> entry : this.sharedState.directClaims.entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() >= RESOURCES.ALL().size()) continue;
            String resourceKey = ((RESOURCE)RESOURCES.ALL().get((int)entry.getKey().intValue())).key;
            for (DirectClaim directClaim : entry.getValue()) {
                if (directClaim.producer == null || !directClaim.producer.exists() || directClaim.unitsHeld <= 0) continue;
                file.i(directClaim.producer.mX());
                file.i(directClaim.producer.mY());
                file.chars((CharSequence)resourceKey);
                file.i(directClaim.unitsHeld);
            }
        }
        this.ensureCrownCapacity();
        int crownCount = 0;
        for (long l : this.sharedState.crownUnits) {
            if (l <= 0L) continue;
            ++crownCount;
        }
        file.i(crownCount);
        for (int i = 0; i < this.sharedState.crownUnits.length; ++i) {
            if (this.sharedState.crownUnits[i] <= 0L) continue;
            file.chars((CharSequence)((RESOURCE)RESOURCES.ALL().get(i)).key);
            file.l(this.sharedState.crownUnits[i]);
        }
        int abandonedCount = 0;
        for (long l : this.sharedState.abandonedUnits) {
            if (l > 0L) {
                ++abandonedCount;
            }
        }
        file.i(abandonedCount);
        for (int resource = 0; resource < this.sharedState.abandonedUnits.length; ++resource) {
            if (this.sharedState.abandonedUnits[resource] <= 0L) continue;
            file.chars((CharSequence)((RESOURCE)RESOURCES.ALL().get((int)resource)).key);
            file.l(this.sharedState.abandonedUnits[resource]);
        }
        int retailCount = 0;
        for (Map.Entry<RoomInstance, RetailBook[]> entry : this.sharedState.retailBooks.entrySet()) {
            RoomInstance retailer = entry.getKey();
            if (retailer == null || !retailer.exists()) continue;
            for (RetailBook book : shelf = entry.getValue()) {
                if (book == null || book.observedStock <= 0 && book.lots.isEmpty()) continue;
                ++retailCount;
            }
        }
        file.i(retailCount);
        for (Map.Entry<RoomInstance, RetailBook[]> entry : this.sharedState.retailBooks.entrySet()) {
            RoomInstance retailer = entry.getKey();
            if (retailer == null || !retailer.exists()) continue;
            shelf = entry.getValue();
            for (int resourceIndex = 0; resourceIndex < shelf.length && resourceIndex < RESOURCES.ALL().size(); ++resourceIndex) {
                RetailBook book = shelf[resourceIndex];
                if (book == null || book.observedStock <= 0 && book.lots.isEmpty()) continue;
                file.i(retailer.mX());
                file.i(retailer.mY());
                file.chars((CharSequence)((RESOURCE)RESOURCES.ALL().get((int)resourceIndex)).key);
                file.i(Math.max(0, book.observedStock));
                file.i(book.lots.size());
                for (RetailLot lot : book.lots) {
                    file.i(Math.max(0, lot.units));
                    file.i(Math.max(0, lot.unitPrice));
                }
            }
        }
    }

    public void load(FileGetter file) throws IOException {
        int x;
        int i;
        this.clear();
        int version = file.i();
        if (!WarehouseMarket.supportsFormat(version)) {
            throw new IOException("unsupported warehouse book format " + version);
        }
        this.lastTaxSeason = version >= 2 ? file.i() : -1;
        int count = Math.max(0, file.i());
        for (int i2 = 0; i2 < count; ++i2) {
            int x2 = file.i();
            int y = file.i();
            String key = file.chars();
            int unitsHeld = file.i();
            double capitalBasis = version >= 6 ? (double)Math.max(0L, file.l()) / 100.0 : 0.0;
            int stakeCount = Math.max(0, file.i());
            HashMap<Integer, Double> stakes = new HashMap<Integer, Double>();
            for (int s = 0; s < stakeCount; ++s) {
                int id = file.i();
                double paidIn = (double)file.l() / 100.0;
                if (!(paidIn > 0.0)) continue;
                stakes.put(id, paidIn);
                if (version >= 6) continue;
                capitalBasis += paidIn;
            }
            this.sharedState.pending.add(new PendingBook(x2, y, key, unitsHeld, stakes, capitalBasis));
        }
        if (version >= 3) {
            int lockCount = Math.max(0, file.i());
            for (i = 0; i < lockCount; ++i) {
                x = file.i();
                int y = file.i();
                int tileCount = Math.max(0, file.i());
                HashMap<Integer, Integer> tiles = new HashMap<Integer, Integer>();
                for (int tile = 0; tile < tileCount; ++tile) {
                    int key = file.i();
                    int amount = Math.max(0, file.i());
                    if (amount <= 0) continue;
                    tiles.put(key, amount);
                }
                this.sharedState.pendingIntakeLocks.add(new PendingIntakeLock(x, y, tiles));
            }
        }
        if (version >= 4) {
            int directCount = Math.max(0, file.i());
            for (i = 0; i < directCount; ++i) {
                x = file.i();
                int y = file.i();
                String key = file.chars();
                int unitsHeld = Math.max(0, file.i());
                if (unitsHeld <= 0) continue;
                this.sharedState.pendingDirectClaims.add(new PendingDirectClaim(x, y, key, unitsHeld));
            }
        }
        boolean bl = this.sharedState.inferCrownFromLoose = version < 5;
        if (version >= 5) {
            this.ensureCrownCapacity();
            int crownCount = Math.max(0, file.i());
            for (i = 0; i < crownCount; ++i) {
                RESOURCE resource = WarehouseMarket.resource(file.chars());
                long units = Math.max(0L, file.l());
                if (resource == null) continue;
                this.sharedState.crownUnits[resource.index()] = units;
            }
        }
        if (version >= 7) {
            this.ensureCrownCapacity();
            int abandonedCount = Math.max(0, file.i());
            for (i = 0; i < abandonedCount; ++i) {
                RESOURCE resource = WarehouseMarket.resource(file.chars());
                long units = Math.max(0L, file.l());
                if (resource == null) continue;
                this.sharedState.abandonedUnits[resource.index()] = Math.min(units, this.sharedState.crownUnits[resource.index()]);
            }
            int retailCount = Math.max(0, file.i());
            for (int i3 = 0; i3 < retailCount; ++i3) {
                int x3 = file.i();
                int y = file.i();
                String key = file.chars();
                int observed = Math.max(0, file.i());
                int lotCount = Math.max(0, file.i());
                ArrayList<RetailLot> lots = new ArrayList<RetailLot>();
                for (int lot = 0; lot < lotCount; ++lot) {
                    int units = Math.max(0, file.i());
                    int price = Math.max(0, file.i());
                    if (units <= 0) continue;
                    lots.add(new RetailLot(units, price));
                }
                this.sharedState.pendingRetailBooks.add(new PendingRetailBook(x3, y, key, observed, lots));
            }
        }
    }

    public void beginTick() {
        this.wholesale.beginTick();
    }

    public void clear() {
        this.sharedState.books.clear();
        this.sharedState.pending.clear();
        this.sharedState.directClaims.clear();
        this.sharedState.pendingDirectClaims.clear();
        this.sharedState.retailBooks.clear();
        this.sharedState.pendingRetailBooks.clear();
        this.sharedState.crownUnits = new long[0];
        this.sharedState.abandonedUnits = new long[0];
        this.sharedState.inferCrownFromLoose = true;
        this.sharedState.intakeLocks.clear();
        this.sharedState.pendingIntakeLocks.clear();
        this.wholesale.clear();
        this.lastSold = 0L;
        this.lastBought = 0L;
        this.lastUnitsSold = 0;
        this.lastUnitsBought = 0;
        this.lastTaxSeason = -1;
        this.lastTaxed = 0L;
        this.lastTaxPayers = 0;
        this.lastConstruction = new int[0];
        this.constructionInitialized = false;
        this.lastConstructionPaid = 0L;
        this.lastExport = new int[0];
        this.exportInitialized = false;
        this.lastExportBought = 0L;
    }

    public static final class Book {
        int unitsHeld;
        double capitalBasis;
        final Map<Integer, Double> stakes = new HashMap<Integer, Double>();
        boolean stateOwned;

        Book() {
        }
    }

    public record CrownStorage(StockpileInstance warehouse, int untitledUnits) {
    }

    public record Purchase(int units, int paid) {
        public static final Purchase NONE = new Purchase(0, 0);
    }

    public static final class DirectClaim {
        final RoomInstance producer;
        int unitsHeld;

        DirectClaim(RoomInstance producer, int unitsHeld) {
            this.producer = producer;
            this.unitsHeld = unitsHeld;
        }
    }

    public record Settlement(long billed, long credited) {
    }

    public record SaleDistribution(int credited, int merchantUnits, int directUnits, int crownUnits) {
    }

    public record RetailQuote(int total, int[] byResource) {
        public RetailQuote {
            byResource = byResource == null ? new int[]{} : (int[])byResource.clone();
        }

        @Override
        public int[] byResource() {
            return byResource.clone();
        }
    }

    public static final class RetailBook {
        int observedStock = -1;
        final ArrayDeque<RetailLot> lots = new ArrayDeque();

        private RetailBook() {
        }
    }

    public record OwnerlessRetailClaims(int waivedValue, int[] payableQuantities) {
        public OwnerlessRetailClaims {
            payableQuantities = payableQuantities == null ? new int[]{} : (int[])payableQuantities.clone();
        }

        @Override
        public int[] payableQuantities() {
            return payableQuantities.clone();
        }
    }

    public static final class RetailLot {
        int units;
        final int unitPrice;

        RetailLot(int units, int unitPrice) {
            this.units = Math.max(0, units);
            this.unitPrice = Math.max(0, unitPrice);
        }
    }

    private record MerchantDistribution(int credited, int claimed, int claimedUnits) {
    }

    private record DirectDistribution(int credited, int claimedUnits) {
    }

    private record WarehouseHolding(StockpileInstance warehouse, Book book) {
    }

    private record DirectSale(RoomInstance producer, int units) {
    }

    public static final class PendingBook {
        final int x;
        final int y;
        final String resourceKey;
        final int unitsHeld;
        final Map<Integer, Double> stakes;
        final double capitalBasis;

        PendingBook(int x, int y, String resourceKey, int unitsHeld, Map<Integer, Double> stakes, double capitalBasis) {
            this.x = x;
            this.y = y;
            this.resourceKey = resourceKey;
            this.unitsHeld = unitsHeld;
            this.stakes = stakes;
            this.capitalBasis = capitalBasis;
        }
    }

    public static final class PendingIntakeLock {
        final int x;
        final int y;
        final Map<Integer, Integer> tiles;

        PendingIntakeLock(int x, int y, Map<Integer, Integer> tiles) {
            this.x = x;
            this.y = y;
            this.tiles = tiles;
        }
    }

    public static final class PendingDirectClaim {
        final int x;
        final int y;
        final String resourceKey;
        final int unitsHeld;

        PendingDirectClaim(int x, int y, String resourceKey, int unitsHeld) {
            this.x = x;
            this.y = y;
            this.resourceKey = resourceKey;
            this.unitsHeld = unitsHeld;
        }
    }

    public static final class PendingRetailBook {
        final int x;
        final int y;
        final String resourceKey;
        final int observedStock;
        final ArrayList<RetailLot> lots;

        PendingRetailBook(int x, int y, String resourceKey, int observedStock, ArrayList<RetailLot> lots) {
            this.x = x;
            this.y = y;
            this.resourceKey = resourceKey;
            this.observedStock = observedStock;
            this.lots = lots;
        }
    }
}

