package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import init.race.RACES;
import init.type.NEEDS;
import java.util.HashMap;
import java.util.HashSet;
import game.time.TIME;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.Induvidual;
import settlement.stats.STATS;
import settlement.stats.equip.WearableResource;
import snake2d.util.sets.LIST;
import vannon.syx.economy.core.AffordabilityGate;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.EngineSeams;
import vannon.syx.economy.core.FoodRollback;
import vannon.syx.economy.core.LocalPrices;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class Purchases {
    private final HashMap<Induvidual, Integer> lastHunger = new HashMap<>();
    private final HashMap<Induvidual, int[]> lastOwned = new HashMap<>();
    private final HashMap<Induvidual, Integer> lastDrink = new HashMap<>();
    private long spentOnFood = 0L;
    private long spentOnDrink = 0L;
    private long spentOnGoods = 0L;
    private int meals = 0;
    private int drinks = 0;
    private int goodsBought = 0;
    private int pop = 0;
    private int tick = 0;
    private Roster roster = null;
    private static final int FOOD = 0;
    private static final int DRINK = 1;
    private static final int GOODS = 2;

    public long spentOnFood() {
        return this.spentOnFood;
    }

    public long spentOnDrink() {
        return this.spentOnDrink;
    }

    public long spentOnGoods() {
        return this.spentOnGoods;
    }

    public int meals() {
        return this.meals;
    }

    public int drinks() {
        return this.drinks;
    }

    public int goodsBought() {
        return this.goodsBought;
    }

    public long update(Roster roster, Wallets wallets, AffordabilityGate gate, int ticks) {
        if (!EconConfig.chargeForGoods) {
            return 0L;
        }
        int pollThreshold = Math.max(1, (int)(EconConfig.purchasePollDays * TIME.secondsPerDay()));
        if (EconConfig.purchasePollDays > 0 && ticks % pollThreshold != 0) {
            return 0L;
        }
        long collected = 0L;
        this.pop = roster.size();
        this.tick = ticks;
        this.roster = roster;
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid h = roster.get(i);
            collected += (long)this.chargeMeal(h, wallets, gate);
            collected += (long)this.chargeDrink(h, wallets);
            collected += (long)this.chargeGoods(h, wallets);
        }
        if (this.lastHunger.size() > roster.size() * 2 + 64) {
            this.prune(roster);
        }
        if (collected > 0L) {
            FACTIONS.player().credits().inc((double)collected, FCredits.CTYPE.TRADE);
        }
        return collected;
    }

    private int chargeMeal(Humanoid h, Wallets wallets, AffordabilityGate gate) {
        Induvidual indu = h.indu();
        int now = NEEDS.TYPES().HUNGER.stat().stat().indu().get(indu);
        Integer prevBox = this.lastHunger.put(indu, now);
        int exactMeals = 0;
        int exactPaid = 0;
        int bill = gate.consumeSettledMeal(h);
        while (bill >= 0) {
            ++exactMeals;
            exactPaid += bill;
            bill = gate.consumeSettledMeal(h);
        }
        if (exactMeals > 0) {
            this.meals += exactMeals;
            this.spentOnFood += (long)exactPaid;
            return 0;
        }
        if (prevBox == null) {
            return 0;
        }
        int prev = prevBox;
        if (now >= prev) {
            return 0;
        }
        if (EconConfig.foodAffordabilityGateEnabled) {
            int amount = FoodRollback.estimateUnitsEaten(h, prev, now);
            FoodRollback.restore(h, FoodRollback.nearestStallSnapshot(h), amount);
            EngineSeams.hungerRawSet(h, prev);
            this.lastHunger.put(indu, prev);
            return 0;
        }
        int price = LocalPrices.mealPrice(this.pop, this.tick);
        if (price <= 0) {
            return 0;
        }
        int total = price;
        ++this.meals;
        EconomySim sim = EconomySim.active();
        if (sim != null && sim.grainDole().isOnRoll(indu)) {
            sim.grainDole().recordDoledMeal(total);
            return 0;
        }
        return this.bill(h, wallets, total, 0);
    }

    private int chargeDrink(Humanoid h, Wallets wallets) {
        AffordabilityGate gate;
        Induvidual indu = h.indu();
        int now = STATS.FOOD().DRINK.indu().get(indu);
        int exact = 0;
        int exactCount = 0;
        EconomySim sim = EconomySim.active();
        AffordabilityGate affordabilityGate = gate = sim == null ? null : sim.affordabilityGate();
        if (gate != null) {
            int bill = gate.consumeSettledDrink(h);
            while (bill >= 0) {
                exact += bill;
                ++exactCount;
                bill = gate.consumeSettledDrink(h);
            }
        }
        if (exactCount > 0) {
            this.lastDrink.put(indu, now);
            this.drinks += exactCount;
            this.spentOnDrink += (long)exact;
            return 0;
        }
        Integer prevBox = this.lastDrink.put(indu, now);
        if (prevBox == null) {
            return 0;
        }
        int gained = now - prevBox;
        if (gained <= 0) {
            return 0;
        }
        int unit = LocalPrices.drinkPrice(this.pop, this.tick);
        if (unit <= 0) {
            return 0;
        }
        int total = unit;
        if (total <= 0) {
            return 0;
        }
        if (EconConfig.consumptionGateEnabled) {
            return 0;
        }
        ++this.drinks;
        return this.bill(h, wallets, total, 1);
    }

    private int chargeGoods(Humanoid h, Wallets wallets) {
        int i;
        int[] prev;
        AffordabilityGate gate;
        Induvidual indu = h.indu();
        LIST<?> all = RACES.res().all(indu.popCL());
        if (all == null || all.size() == 0) {
            return 0;
        }
        EconomySim sim = EconomySim.active();
        AffordabilityGate affordabilityGate = gate = sim == null ? null : sim.affordabilityGate();
        if (gate != null) {
            int exact = 0;
            int count = 0;
            int bill = gate.consumeSettledGoods(h);
            while (bill >= 0) {
                exact += bill;
                ++count;
                bill = gate.consumeSettledGoods(h);
            }
            if (count > 0) {
                int[] baseline = new int[all.size()];
                for (int i2 = 0; i2 < all.size(); ++i2) {
                    baseline[i2] = ((WearableResource)all.get(i2)).get(indu);
                }
                this.lastOwned.put(indu, baseline);
                this.spentOnGoods += (long)exact;
                this.goodsBought += count;
                return 0;
            }
        }
        if ((prev = this.lastOwned.get(indu)) == null || prev.length != all.size()) {
            prev = new int[all.size()];
            for (int i3 = 0; i3 < all.size(); ++i3) {
                prev[i3] = ((WearableResource)all.get(i3)).get(indu);
            }
            this.lastOwned.put(indu, prev);
            return 0;
        }
        int paid = 0;
        if (EconConfig.consumptionGateEnabled) {
            for (i = 0; i < all.size(); ++i) {
                prev[i] = ((WearableResource)all.get(i)).get(indu);
            }
            return 0;
        }
        for (i = 0; i < all.size(); ++i) {
            int cost;
            int unit;
            WearableResource w = (WearableResource)all.get(i);
            int now = w.get(indu);
            int gained = now - prev[i];
            prev[i] = now;
            if (gained <= 0 || (unit = LocalPrices.goodPrice(w, w.resource(indu), this.roster, this.tick)) <= 0 || (cost = unit * gained) <= 0) continue;
            this.goodsBought += gained;
            paid += this.bill(h, wallets, cost, 2);
        }
        return paid;
    }

    private int bill(Humanoid h, Wallets wallets, int price, int kind) {
        int paid = wallets.charge(h, price);
        switch (kind) {
            case 0: {
                this.spentOnFood += (long)paid;
                break;
            }
            case 1: {
                this.spentOnDrink += (long)paid;
                break;
            }
            default: {
                this.spentOnGoods += (long)paid;
            }
        }
        return paid;
    }

    private void prune(Roster roster) {
        HashSet<Induvidual> living = new HashSet<Induvidual>();
        for (int i = 0; i < roster.size(); ++i) {
            living.add(roster.get(i).indu());
        }
        this.lastHunger.keySet().retainAll(living);
        this.lastDrink.keySet().retainAll(living);
        this.lastOwned.keySet().retainAll(living);
    }

    public void reset() {
        this.lastHunger.clear();
        this.lastDrink.clear();
        this.lastOwned.clear();
        this.spentOnFood = 0L;
        this.spentOnDrink = 0L;
        this.drinks = 0;
        this.spentOnGoods = 0L;
        this.meals = 0;
        this.goodsBought = 0;
    }
}

