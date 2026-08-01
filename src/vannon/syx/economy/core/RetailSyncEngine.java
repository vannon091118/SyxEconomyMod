package vannon.syx.economy.core;

import game.faction.FACTIONS;
import init.race.RACES;
import init.race.RaceResources;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.resources.ResGDrink;
import init.resources.ResGEat;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import settlement.main.SETT;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.room.service.food.canteen.EconomyCanteenAccess;
import settlement.room.service.food.canteen.ROOM_CANTEEN;
import settlement.room.service.food.eatery.ROOM_EATERY;
import settlement.room.service.food.eatery.RoomDistribution;
import settlement.room.service.food.tavern.ROOM_TAVERN;
import settlement.room.service.market.ROOM_MARKET;
import vannon.syx.economy.core.warehouse.market.MarketSharedState;

/**
 * T-104 RetailSyncEngine — extracted from WarehouseMarket as part of Sprint M-1.
 *
 * <p>Handles retail-delivery observation and wholesale-quote generation for
 * eateries, canteens, taverns, and markets. Operates on shared state and tracks
 * retail-book FIFO lots.</p>
 */
public final class RetailSyncEngine {

    private final MarketSharedState sharedState;
    private final FlowPrices prices;

    public RetailSyncEngine(MarketSharedState sharedState, FlowPrices prices) {
        this.sharedState = sharedState;
        this.prices = prices;
    }

    // ── Public API ──────────────────────────────────────────────

    public void observeRetailDeliveries() {
        if (SETT.ROOMS() == null) return;
        Set<RoomInstance> live = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RoomBlueprintIns<?> blueprint : SETT.ROOMS().ins()) {
            if (!isRetailBlueprint(blueprint)) continue;
            for (int i = 0; i < blueprint.instancesSize(); ++i) {
                RoomInstance room2 = blueprint.getInstance(i);
                if (room2 == null || !room2.exists()) continue;
                live.add(room2);
                syncRetail(room2, null);
            }
        }
        this.sharedState.retailBooks.keySet().removeIf(room -> !live.contains(room));
    }

    public WarehouseMarket.RetailQuote retailWholesaleQuote(RoomInstance seller, int[] soldQuantities) {
        if (seller == null || soldQuantities == null || !isRetailBlueprint(seller.blueprintI())) {
            return new WarehouseMarket.RetailQuote(0, new int[RESOURCES.ALL().size()]);
        }
        return syncRetail(seller, soldQuantities);
    }

    // ── Private core ────────────────────────────────────────────

    private WarehouseMarket.RetailQuote syncRetail(RoomInstance room, int[] soldQuantities) {
        int[] current = retailStock(room);
        int[] byResource = new int[RESOURCES.ALL().size()];
        WarehouseMarket.RetailBook[] shelf = this.sharedState.retailBooks.computeIfAbsent(room, ignored -> new WarehouseMarket.RetailBook[RESOURCES.ALL().size()]);
        long wholesale = 0L;
        for (int resource = 0; resource < shelf.length; ++resource) {
            int sold = soldQuantities == null || resource >= soldQuantities.length ? 0 : Math.max(0, soldQuantities[resource]);
            int now = resource < current.length ? Math.max(0, current[resource]) : 0;
            WarehouseMarket.RetailBook book = shelf[resource];
            if (book == null && now == 0 && sold == 0) continue;
            if (book == null) {
                shelf[resource] = book = new WarehouseMarket.RetailBook();
            }
            int beforeSale = WarehouseMarket.safeAdd(now, sold);
            if (book.observedStock < 0) {
                appendRetailLot(book, beforeSale, retailUnitPrice(resource));
            } else {
                int change = beforeSale - book.observedStock;
                if (change > 0) {
                    appendRetailLot(book, change, retailUnitPrice(resource));
                } else if (change < 0) {
                    discardRetailUnits(book, -change);
                }
            }
            int missing = sold - retailUnits(book);
            if (missing > 0) {
                appendRetailLot(book, missing, retailUnitPrice(resource));
            }
            long resourceDue = consumeRetailUnits(book, sold);
            byResource[resource] = (int)Math.min(Integer.MAX_VALUE, resourceDue);
            wholesale = safeMoneyAdd(wholesale, resourceDue);
            book.observedStock = now;
        }
        return new WarehouseMarket.RetailQuote((int)Math.min(Integer.MAX_VALUE, wholesale), byResource);
    }

    private int retailUnitPrice(int resourceIndex) {
        if (resourceIndex < 0 || resourceIndex >= RESOURCES.ALL().size()) return 0;
        RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(resourceIndex);
        if (this.prices.ready()) return Math.max(0, this.prices.priceRoundedUp(resourceIndex));
            int partnerPrice = PolityPriceAnchor.priceOf(resource);
            return Math.max(0, partnerPrice > 0 ? partnerPrice : FACTIONS.PRICE().get(resource.tr()));
    }

    // ── Retail statics ──────────────────────────────────────────

    static void appendRetailLot(WarehouseMarket.RetailBook book, int units, int price) {
        if (book == null || units <= 0) return;
        WarehouseMarket.RetailLot last = book.lots.peekLast();
        if (last != null && last.unitPrice == Math.max(0, price)) {
            last.units = WarehouseMarket.safeAdd(last.units, units);
        } else {
            book.lots.addLast(new WarehouseMarket.RetailLot(units, price));
        }
    }

    static int retailUnits(WarehouseMarket.RetailBook book) {
        long units = 0L;
        if (book != null) {
            for (WarehouseMarket.RetailLot lot : book.lots) units += (long)Math.max(0, lot.units);
        }
        return (int)Math.min(Integer.MAX_VALUE, units);
    }

    static long consumeRetailUnits(WarehouseMarket.RetailBook book, int wanted) {
        long due = 0L;
        int remaining = Math.max(0, wanted);
        while (book != null && remaining > 0 && !book.lots.isEmpty()) {
            WarehouseMarket.RetailLot lot = book.lots.peekFirst();
            int units = Math.min(remaining, lot.units);
            due = safeMoneyAdd(due, (long)units * (long)lot.unitPrice);
            remaining -= units;
            lot.units -= units;
            if (lot.units > 0) continue;
            book.lots.removeFirst();
        }
        return due;
    }

    static void discardRetailUnits(WarehouseMarket.RetailBook book, int wanted) {
        int remaining = Math.max(0, wanted);
        while (book != null && remaining > 0 && !book.lots.isEmpty()) {
            WarehouseMarket.RetailLot lot = book.lots.peekFirst();
            int units = Math.min(remaining, lot.units);
            remaining -= units;
            lot.units -= units;
            if (lot.units > 0) continue;
            book.lots.removeFirst();
        }
    }

    static long safeMoneyAdd(long left, long right) {
        if (right <= 0L) return Math.max(0L, left);
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    static boolean isRetailBlueprint(Object blueprint) {
        return blueprint instanceof ROOM_EATERY || blueprint instanceof ROOM_CANTEEN || blueprint instanceof ROOM_TAVERN || blueprint instanceof ROOM_MARKET;
    }

    static int[] retailStock(RoomInstance room) {
        int[] stock;
        block7: {
            RoomDistribution.RoomDistributionIns distributed;
            block8: {
                block6: {
                    if (room == null) return new int[RESOURCES.ALL().size()];
                    if (room.blueprintI() instanceof ROOM_CANTEEN) {
                        return EconomyCanteenAccess.stock(room);
                    }
                    stock = new int[RESOURCES.ALL().size()];
                    if (!(room instanceof RoomDistribution.RoomDistributionIns)) return stock;
                    distributed = (RoomDistribution.RoomDistributionIns)room;
                    if (!(room.blueprintI() instanceof ROOM_EATERY)) break block6;
                    for (ResGEat food : RESOURCES.EDI().all()) {
                        stock[food.resource.index()] = clampStock(distributed.distributionNlueData().stored(food.resource).get(room));
                    }
                    break block7;
                }
                if (!(room.blueprintI() instanceof ROOM_TAVERN)) break block8;
                for (ResGDrink drink : RESOURCES.DRINKS().all()) {
                    stock[drink.resource.index()] = clampStock(distributed.distributionNlueData().stored(drink.resource).get(room));
                }
                break block7;
            }
            if (!(room.blueprintI() instanceof ROOM_MARKET)) break block7;
            for (RaceResources.RaceResource wearable : RACES.res().ALL) {
                stock[wearable.res.index()] = clampStock(distributed.distributionNlueData().stored(wearable.res).get(room));
            }
        }
        return stock;
    }

    static int clampStock(long stock) {
        return stock <= 0L ? 0 : (stock >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)stock);
    }
}
