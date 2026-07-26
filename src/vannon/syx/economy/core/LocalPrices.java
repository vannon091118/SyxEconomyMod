package vannon.syx.economy.core;

import game.boosting.BOOSTABLE_O;
import game.time.TIME;
import game.faction.FACTIONS;
import init.race.RACES;
import init.race.Race;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.resources.ResG;
import init.resources.ResGDrink;
import init.resources.ResGEat;
import init.type.HCLASS;
import init.type.HCLASSES;
import init.type.NEEDS;
import java.util.HashMap;
import settlement.main.SETT;
import settlement.room.service.food.canteen.ROOM_CANTEEN;
import settlement.room.service.food.eatery.ROOM_EATERY;
import settlement.room.service.food.tavern.EconomyTavernAccess;
import settlement.room.service.food.tavern.ROOM_TAVERN;
import settlement.stats.POP;
import settlement.stats.STATS;
import settlement.stats.equip.WearableResource;
import snake2d.util.sets.LIST;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.Roster;

public final class LocalPrices {
    private static long cachedFoodStock = -1L;
    private static int lastRefreshTick = -9999;
    private static final HashMap<WearableResource, Long> demandCache = new HashMap();
    private static int lastGoodsRefresh = -9999;
    private static int cachedFoodBasket = -1;
    private static int cachedDrinkBasket = -1;
    private static long cachedDrinkStock = -1L;
    private static int lastDrinkRefresh = -9999;

    public static double foodDays() {
        try {
            double d = STATS.FOOD().FOOD_DAYS.data().getD(null) * (double)STATS.FOOD().FOOD_DAYS.dataDivider();
            if (d > 24.0) { d = 24.0; }
            if (d < 0.0) { d = 0.0; }
            return d;
        } catch (RuntimeException e) {
            if (!foodDaysFailed) { foodDaysFailed = true; EventLog.log("SEAM", "LocalPrices.foodDays(): STATS.FOOD().FOOD_DAYS failed — " + e.getClass().getSimpleName()); }
            return EconConfig.targetFoodDays;
        }
    }
    private static boolean foodDaysFailed = false;

    public static double drinkDays(int tick) {
        if (SETT.ROOMS() == null) {
            return EconConfig.targetDrinkDays;
        }
        try {
            double needed = 0.0;
            for (int ci = 0; ci < HCLASSES.ALL().size(); ++ci) {
                HCLASS c = (HCLASS)HCLASSES.ALL().get(ci);
                if (!c.player) continue;
                for (int ri = 0; ri < RACES.all().size(); ++ri) {
                    Race r = (Race)RACES.all().get(ri);
                    needed += NEEDS.TYPES().THIRST.rate.get((BOOSTABLE_O)c.get(r)) * (double)POP.physical((HCLASS)c, (Race)r) * STATS.FOOD().DRINK.decree().get(c, r);
                }
            }
            if (needed <= 0.0) {
                return EconConfig.targetDrinkDays;
            }
            return (double)LocalPrices.drinkStock(tick) / needed;
        } catch (RuntimeException e) {
            if (!drinkDaysFailed) { drinkDaysFailed = true; EventLog.log("SEAM", "LocalPrices.drinkDays(): thirst calculation failed — " + e.getClass().getSimpleName()); }
            return EconConfig.targetDrinkDays;
        }
    }
    private static boolean drinkDaysFailed = false;

    public static long goodsDemand(WearableResource w, Roster roster, int tick) {
        Long cached;
        int scarcityThreshold = (int)(EconConfig.scarcityRefreshDays * TIME.secondsPerDay());
        if (Math.abs(tick - lastGoodsRefresh) >= scarcityThreshold) {
            demandCache.clear();
            lastGoodsRefresh = tick;
        }
        if ((cached = demandCache.get(w)) != null) {
            return cached;
        }
        long demand = 0L;
        for (int i = 0; i < roster.size(); ++i) {
            demand += (long)w.target(roster.get(i).indu());
        }
        demandCache.put(w, demand);
        return demand;
    }

    public static double scarcity(double perCapita, double target) {
        if (target <= 0.0) {
            return 1.0;
        }
        double m = EconConfig.scarcityMaxMultiple;
        double w = EconConfig.scarcitySteepness;
        if (m <= 1.0 || w <= 0.0) {
            return 1.0;
        }
        double x = Math.log(perCapita / target);
        return Math.pow(m, -Math.tanh(x / w));
    }

    public static long foodStock(int tick) {
        int foodThreshold = (int)(EconConfig.scarcityRefreshDays * TIME.secondsPerDay());
        if (cachedFoodStock >= 0L && Math.abs(tick - lastRefreshTick) < foodThreshold) {
            return cachedFoodStock;
        }
        lastRefreshTick = tick;
        long total = 0L;
        if (SETT.ROOMS() != null) {
            LIST edibles = RESOURCES.EDI().all();
            for (int i = 0; i < edibles.size(); ++i) {
                RESOURCE res = ((ResGEat)edibles.get((int)i)).resource;
                total += (long)SETT.ROOMS().STOCKPILE.tally().amountTotal(res);
            }
            LIST eateries = SETT.ROOMS().EATERIES;
            for (int i = 0; i < eateries.size(); ++i) {
                total += ((ROOM_EATERY)eateries.get(i)).totalFood();
            }
            LIST canteens = SETT.ROOMS().CANTEENS;
            for (int i = 0; i < canteens.size(); ++i) {
                total += ((ROOM_CANTEEN)canteens.get(i)).totalFood();
            }
        }
        cachedFoodStock = total;
        return total;
    }

    private static int basketPrice(long[] stocks, RESOURCE[] res, int fallback) {
        long amount = 0L;
        long weighted = 0L;
        for (int i = 0; i < res.length; ++i) {
            int p;
            if (stocks[i] <= 0L || (p = FACTIONS.PRICE().get(res[i].tr())) <= 0) continue;
            weighted += (long)p * stocks[i];
            amount += stocks[i];
        }
        if (amount == 0L) {
            return fallback;
        }
        return (int)(weighted / amount);
    }

    private static int flowBasketPrice(long[] stocks, RESOURCE[] resources) {
        double result;
        EconomySim sim = EconomySim.active();
        if (sim == null || !sim.flowPrices().ready()) {
            return 0;
        }
        double amount = 0.0;
        double weighted = 0.0;
        double fallback = 0.0;
        int fallbackCount = 0;
        for (int i = 0; i < resources.length; ++i) {
            double price = sim.flowPrices().price(resources[i].index());
            if (!(price > 0.0)) continue;
            fallback += price;
            ++fallbackCount;
            if (stocks[i] <= 0L) continue;
            amount += (double)stocks[i];
            weighted += (double)stocks[i] * price;
        }
        double d = result = amount > 0.0 ? weighted / amount : fallback / (double)Math.max(1, fallbackCount);
        if (!(result > 0.0)) {
            return 0;
        }
        return result >= 2.147483647E9 ? Integer.MAX_VALUE : (int)Math.ceil(result);
    }

    public static int flowFoodBasketPrice() {
        if (SETT.ROOMS() == null) {
            return 0;
        }
        LIST edibles = RESOURCES.EDI().all();
        RESOURCE[] resources = new RESOURCE[edibles.size()];
        long[] stocks = new long[edibles.size()];
        for (int i = 0; i < edibles.size(); ++i) {
            ResGEat edible = (ResGEat)edibles.get(i);
            resources[i] = edible.resource;
            stocks[i] = SETT.ROOMS().STOCKPILE.tally().amountTotal(edible.resource);
            for (ROOM_EATERY eatery : SETT.ROOMS().EATERIES) {
                int n = i;
                stocks[n] = stocks[n] + eatery.amount((ResG)edible);
            }
            for (ROOM_CANTEEN canteen : SETT.ROOMS().CANTEENS) {
                int n = i;
                stocks[n] = stocks[n] + canteen.amount((ResG)edible);
            }
        }
        return LocalPrices.flowBasketPrice(stocks, resources);
    }

    public static int flowDrinkBasketPrice() {
        if (SETT.ROOMS() == null) {
            return 0;
        }
        LIST drinks = RESOURCES.DRINKS().all();
        RESOURCE[] resources = new RESOURCE[drinks.size()];
        long[] stocks = new long[drinks.size()];
        for (int i = 0; i < drinks.size(); ++i) {
            resources[i] = ((ResGDrink)drinks.get((int)i)).resource;
            stocks[i] = SETT.ROOMS().STOCKPILE.tally().amountTotal(resources[i]);
        }
        return LocalPrices.flowBasketPrice(stocks, resources);
    }

    public static int foodBasketPrice(int tick) {
        int basketThreshold = (int)(EconConfig.scarcityRefreshDays * TIME.secondsPerDay());
        if (cachedFoodBasket >= 0 && Math.abs(tick - lastRefreshTick) < basketThreshold) {
            return cachedFoodBasket;
        }
        int fallback = FACTIONS.PRICE().edible();
        if (SETT.ROOMS() == null) {
            return fallback;
        }
        LIST edibles = RESOURCES.EDI().all();
        RESOURCE[] res = new RESOURCE[edibles.size()];
        long[] stocks = new long[edibles.size()];
        LIST eateries = SETT.ROOMS().EATERIES;
        LIST canteens = SETT.ROOMS().CANTEENS;
        for (int i = 0; i < edibles.size(); ++i) {
            int j;
            ResGEat g = (ResGEat)edibles.get(i);
            res[i] = g.resource;
            long amount = SETT.ROOMS().STOCKPILE.tally().amountTotal(res[i]);
            for (j = 0; j < eateries.size(); ++j) {
                amount += ((ROOM_EATERY)eateries.get(j)).amount((ResG)g);
            }
            for (j = 0; j < canteens.size(); ++j) {
                amount += ((ROOM_CANTEEN)canteens.get(j)).amount((ResG)g);
            }
            stocks[i] = amount;
        }
        cachedFoodBasket = LocalPrices.basketPrice(stocks, res, fallback);
        return cachedFoodBasket;
    }

    public static int mealPrice(int population, int tick) {
        int flow;
        if (EconConfig.flowPricingEnabled && (flow = LocalPrices.flowFoodBasketPrice()) > 0) {
            // D-001: flowFoodBasketPrice nutzt FlowPrices — foodPriceAbsoluteMax
            // greift bereits via EconomySim.refreshFlowPrices() + enforceCap().
            // Zusätzlicher Hard-Clamp als Defense-in-Depth.
            if (EconConfig.foodPriceAbsoluteMax > 0.0 && flow > (int)EconConfig.foodPriceAbsoluteMax) {
                flow = (int)EconConfig.foodPriceAbsoluteMax;
            }
            return flow;
        }
        int world = LocalPrices.foodBasketPrice(tick);
        if (world <= 0) {
            return world;
        }
        double s = LocalPrices.scarcity(LocalPrices.foodDays(), EconConfig.targetFoodDays);
        int result = (int)Math.ceil((double)world * s);
        // D-001: Food-Price-Hard-Cap. Greift wenn flowPricingEnabled=false
        // und vanilla FACTIONS.PRICE().get() ungecappte Preise liefert.
        if (EconConfig.foodPriceAbsoluteMax > 0.0 && result > (int)EconConfig.foodPriceAbsoluteMax) {
            result = (int)EconConfig.foodPriceAbsoluteMax;
        }
        return result;
    }

    public static long drinkStock(int tick) {
        int drinkThreshold = (int)(EconConfig.scarcityRefreshDays * TIME.secondsPerDay());
        if (cachedDrinkStock >= 0L && Math.abs(tick - lastDrinkRefresh) < drinkThreshold) {
            return cachedDrinkStock;
        }
        lastDrinkRefresh = tick;
        long total = 0L;
        if (SETT.ROOMS() != null) {
            int i;
            LIST drinks = RESOURCES.DRINKS().all();
            for (i = 0; i < drinks.size(); ++i) {
                total += (long)SETT.ROOMS().STOCKPILE.tally().amountTotal(((ResGDrink)drinks.get((int)i)).resource);
            }
            for (i = 0; i < SETT.ROOMS().TAVERNS.size(); ++i) {
                total += EconomyTavernAccess.totalStock((ROOM_TAVERN)SETT.ROOMS().TAVERNS.get(i));
            }
        }
        cachedDrinkStock = total;
        return total;
    }

    public static int drinkBasketPrice(int tick) {
        int drinkBasketThreshold = (int)(EconConfig.scarcityRefreshDays * TIME.secondsPerDay());
        if (cachedDrinkBasket >= 0 && Math.abs(tick - lastDrinkRefresh) < drinkBasketThreshold) {
            return cachedDrinkBasket;
        }
        if (SETT.ROOMS() == null) {
            return 0;
        }
        LIST drinks = RESOURCES.DRINKS().all();
        RESOURCE[] res = new RESOURCE[drinks.size()];
        long[] stocks = new long[drinks.size()];
        for (int i = 0; i < drinks.size(); ++i) {
            res[i] = ((ResGDrink)drinks.get((int)i)).resource;
            stocks[i] = SETT.ROOMS().STOCKPILE.tally().amountTotal(res[i]);
        }
        cachedDrinkBasket = LocalPrices.basketPrice(stocks, res, 0);
        return cachedDrinkBasket;
    }

    public static int drinkPrice(int population, int tick) {
        int flow;
        if (EconConfig.flowPricingEnabled && (flow = LocalPrices.flowDrinkBasketPrice()) > 0) {
            return flow;
        }
        int world = LocalPrices.drinkBasketPrice(tick);
        if (world <= 0) {
            return world;
        }
        double s = LocalPrices.scarcity(LocalPrices.drinkDays(tick), EconConfig.targetDrinkDays);
        return (int)Math.ceil((double)world * s);
    }

    public static double drinkScarcity(int tick) {
        return LocalPrices.scarcity(LocalPrices.drinkDays(tick), EconConfig.targetDrinkDays);
    }

    public static double foodScarcity(int population, int tick) {
        return LocalPrices.scarcity(LocalPrices.foodDays(), EconConfig.targetFoodDays);
    }

    public static int goodPrice(WearableResource w, RESOURCE res, Roster roster, int tick) {
        int flow;
        EconomySim sim;
        if (EconConfig.flowPricingEnabled && (sim = EconomySim.active()) != null && sim.flowPrices().ready() && (flow = sim.flowPrices().priceRoundedUp(res.index())) > 0) {
            return flow;
        }
        int world = FACTIONS.PRICE().get(res.tr());
        if (world <= 0) {
            return world;
        }
        if (SETT.ROOMS() == null) {
            return world;
        }
        long stock = SETT.ROOMS().STOCKPILE.tally().amountTotal(res);
        long demand = LocalPrices.goodsDemand(w, roster, tick);
        if (demand <= 0L) {
            return world;
        }
        double coverage = (double)stock / (double)demand;
        double s = LocalPrices.scarcity(coverage, EconConfig.targetGoodsCoverage);
        return (int)Math.ceil((double)world * s);
    }

    public static void clearCache() {
        cachedFoodStock = -1L;
        lastRefreshTick = -9999;
        cachedDrinkStock = -1L;
        lastDrinkRefresh = -9999;
        cachedFoodBasket = -1;
        cachedDrinkBasket = -1;
    }

    private LocalPrices() {
    }

    /**
     * T13: Session-Reset aller Caches + Failure-Flags. Wird via EconomySim.clearActive()
     * und im 6-arg privaten Ctor aufgerufen, damit ein geladenes Savegame nicht die
     * Cache-Werte der vorigen Session erbt. Pattern vgl. TreasuryCrisis.reset().
     */
    public static void reset() {
        cachedFoodStock = -1L;
        lastRefreshTick = -9999;
        lastGoodsRefresh = -9999;
        cachedFoodBasket = -1;
        cachedDrinkBasket = -1;
        cachedDrinkStock = -1L;
        lastDrinkRefresh = -9999;
        foodDaysFailed = false;
        drinkDaysFailed = false;
    }
}

