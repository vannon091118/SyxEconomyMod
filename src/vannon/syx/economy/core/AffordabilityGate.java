package vannon.syx.economy.core;

import init.race.RACES;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.resources.ResGDrink;
import init.resources.ResGEat;
import init.type.HCLASSES;
import init.type.HTYPES;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import settlement.entity.humanoid.Humanoid;
import settlement.room.main.RoomInstance;
import settlement.stats.equip.WearableResource;
import snake2d.util.sets.LIST;
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.IHumanoidAccess;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EngineSeams;
import vannon.syx.economy.core.Escrow;
import vannon.syx.economy.core.FlowPrices;
import vannon.syx.economy.core.FoodGateKernel;
import vannon.syx.economy.core.GrainDole;
import vannon.syx.economy.core.LocalPrices;
import vannon.syx.economy.core.PolityPriceAnchor;
import vannon.syx.economy.core.Roster;

public final class AffordabilityGate {
    private final Escrow escrow;
    private final FlowPrices prices;
    private final GrainDole grainDole;
    private final Map<Humanoid, ArrayDeque<Integer>> settledMeals = new IdentityHashMap<Humanoid, ArrayDeque<Integer>>();
    private final Map<Humanoid, ArrayDeque<Integer>> settledDrinks = new IdentityHashMap<Humanoid, ArrayDeque<Integer>>();
    private final Map<Humanoid, ArrayDeque<Integer>> settledGoods = new IdentityHashMap<Humanoid, ArrayDeque<Integer>>();
    private final Map<Humanoid, Humanoid> foodPayers = new IdentityHashMap<Humanoid, Humanoid>();
    private int lastFoodBundleQuote;
    private int lastFoodBundleUnits;
    private SettlementSink sink = SettlementSink.NONE;

    public AffordabilityGate(Escrow escrow, FlowPrices prices, GrainDole grainDole) {
        this.escrow = escrow;
        this.prices = prices;
        this.grainDole = grainDole;
        // Phase-4.7 (Task 2): register all 4 Humanoid-keyed IdentityHashMaps for clearOnLoad.
        // Maps are per-tick transient (rebuilt via computeIfAbsent); clearOnLoad
        // is defensive against silent identity-loss after engine RoomInstance reload.
        IdentityMapRegistry.register("AffordabilityGate", "settledMeals", settledMeals);
        IdentityMapRegistry.register("AffordabilityGate", "settledDrinks", settledDrinks);
        IdentityMapRegistry.register("AffordabilityGate", "settledGoods", settledGoods);
        IdentityMapRegistry.register("AffordabilityGate", "foodPayers", foodPayers);
    }

    public void setSettlementSink(SettlementSink sink) {
        this.sink = sink == null ? SettlementSink.NONE : sink;
    }

    public boolean affordFirmInputs(FlowMeter.FirmSnapshot snapshot, double profit, FlowPrices prices) {
        if (!EconConfig.firmInputGateEnabled) {
            return true;
        }
        if (snapshot == null || snapshot.inputCount() == 0) {
            return true;
        }
        if (profit >= 0.0) {
            return true;
        }
        double expectedCost = 0.0;
        for (int i = 0; i < snapshot.inputCount(); ++i) {
            RESOURCE input = snapshot.inputResource(i);
            double rate = snapshot.inputPerDay(i);
            if (input == null || rate <= 0.0) {
                continue;
            }
            double price = prices.ready() ? prices.price(input.index()) : (double) PolityPriceAnchor.priceOf(input);
            expectedCost += rate * price;
        }
        return expectedCost <= 0.0;
    }

    public int lastFoodBundleQuote() {
        return this.lastFoodBundleQuote;
    }

    public int lastFoodBundleUnits() {
        return this.lastFoodBundleUnits;
    }

    public Admission requestFood(Humanoid humanoid, int[] quantities) {
        int quote = this.foodBundleQuote(quantities);
        this.recordFoodQuote(quantities, quote);
        if (!EconConfig.foodAffordabilityGateEnabled) {
            return new Admission(true, 0, true);
        }
        if (this.freeRation(humanoid)) {
            EventLog.logSampled("CONSUMPTION", humanoid.title() + " received free food ration");
            return new Admission(true, 0, true);
        }
        if (quote <= 0) {
            return new Admission(true, 0, false);
        }
        Humanoid payer = AffordabilityGate.foodPayer(humanoid);
        if (payer == null || !this.escrow.reserve(payer, quote)) {
            EventLog.logSampled("LATENT_DEMAND", "Food purchase rejected for " + humanoid.title() + " (Quote: " + quote + ")");
            return new Admission(false, 0, false);
        }
        this.foodPayers.put(humanoid,
 payer);
        EventLog.logSampled("CONSUMPTION", humanoid.title() + " reserved food for " + quote);
        return new Admission(true, quote, false);
    }

    public Admission replaceFood(Humanoid humanoid, Admission previous, int[] quantities) {
        if (previous == null || !previous.admitted()) {
            return this.requestFood(humanoid, quantities);
        }
        if (previous.free() || !EconConfig.foodAffordabilityGateEnabled) {
            return previous;
        }
        int quote = this.foodBundleQuote(quantities);
        this.recordFoodQuote(quantities, quote);
        if (quote == previous.quote()) {
            return previous;
        }
        Humanoid payer = this.foodPayers.remove(humanoid)
;
        if (payer == null) {
            payer = AffordabilityGate.foodPayer(humanoid);
        }
        if (payer == null) {
            payer = humanoid;
        }
        if (previous.quote() > 0) {
            this.escrow.release(payer, previous.quote());
        }
        if (quote <= 0) {
            return new Admission(true, 0, false);
        }
        if (!this.escrow.reserve(payer, quote)) {
            return new Admission(false, 0, false);
        }
        this.foodPayers.put(humanoid,
 payer);
        return new Admission(true, quote, false);
    }

    public Admission requestDrink(Humanoid humanoid, int[] quantities) {
        int quote = this.drinkBundleQuote(quantities);
        if (!EconConfig.consumptionGateEnabled) {
            return new Admission(true, 0, true);
        }
        if (this.freeRation(humanoid)) {
            return new Admission(true, 0, true);
        }
        if (quote <= 0) {
            return new Admission(true, 0, false);
        }
        return this.escrow.reserve(humanoid, quote) ? new Admission(true, quote, false) : new Admission(false, 0, false);
    }

    public double[] drinkUnitPrices() {
        double[] unitPrices = new double[RESOURCES.DRINKS().all().size()];
        for (int i = 0; i < unitPrices.length; ++i) {
            RESOURCE resource = RESOURCES.DRINKS().all().get(i).resource;
            unitPrices[i] = this.prices.ready() ? this.prices.price(resource.index()) : (double)PolityPriceAnchor.priceOf(resource);
        }
        return unitPrices;
    }

    public int drinkBundleQuote(int[] quantities) {
        int priced = FoodGateKernel.bill(quantities, this.drinkUnitPrices(), Integer.MAX_VALUE);
        return priced <= 0 ? 0 : AffordabilityGate.safeAdd(priced, EconConfig.gateRoundingMargin);
    }

    public int settleDrink(Humanoid humanoid, Admission admission, int[] quantities, RoomInstance seller) {
        int bill;
        if (!admission.admitted()) {
            return 0;
        }
        int priced = FoodGateKernel.bill(quantities, this.drinkUnitPrices(), admission.free() ? Integer.MAX_VALUE : admission.quote());
        int n = bill = admission.free() ? 0 : priced;
        if (!admission.free()) {
            bill = this.escrow.settleOrCharge(humanoid, admission.quote(), bill);
        }
        if (bill > 0) {
            this.sink.purchase(humanoid, AffordabilityGate.drinkToResources(quantities), bill, Kind.DRINK, seller);
        }
        this.settledDrinks.computeIfAbsent(humanoid,
 ignored -> new ArrayDeque<Integer>()).addLast(bill);
        return bill;
    }

    private static int[] drinkToResources(int[] drinks) {
        int[] resources = new int[RESOURCES.ALL().size()];
        if (drinks == null) {
            return resources;
        }
        for (int i = 0; i < drinks.length && i < RESOURCES.DRINKS().all().size(); ++i) {
            resources[RESOURCES.DRINKS().all().get(i).resource.index()] = drinks[i];
        }
        return resources;
    }

    public Admission requestGoods(Humanoid humanoid, Roster roster, int tick) {
        int q;
        long quote = Math.max(0, EconConfig.gateRoundingMargin);
        LIST<?> all = RACES.res().all(humanoid.indu().popCL());
        for (int i = 0; i < all.size(); ++i) {
            WearableResource wearable = (WearableResource)all.get(i);
            int needed = Math.max(0, wearable.needed(humanoid.indu()));
            if ((quote += (long)needed * (long)LocalPrices.goodPrice(wearable, wearable.resource(humanoid.indu()), roster, tick)) < Integer.MAX_VALUE) continue;
            quote = Integer.MAX_VALUE;
            break;
        }
        return (q = (int)quote) == 0 || this.escrow.reserve(humanoid, q) ? new Admission(true, q, false) : new Admission(false, 0, false);
    }

    public double[] foodUnitPrices() {
        double[] unitPrices = new double[RESOURCES.EDI().all().size()];
        for (int i = 0; i < RESOURCES.EDI().all().size(); ++i) {
            RESOURCE resource = RESOURCES.EDI().all().get(i).resource;
            unitPrices[i] = this.prices.ready() ? this.prices.price(resource.index()) : (double)PolityPriceAnchor.priceOf(resource);
        }
        return unitPrices;
    }

    public int foodBundleQuote(int[] quantities) {
        int priced = FoodGateKernel.bill(quantities, this.foodUnitPrices(), Integer.MAX_VALUE);
        return priced <= 0 ? 0 : AffordabilityGate.safeAdd(priced, EconConfig.gateRoundingMargin);
    }

    private void recordFoodQuote(int[] quantities, int quote) {
        int units = 0;
        if (quantities != null) {
            for (int quantity : quantities) {
                units += Math.max(0, quantity);
            }
        }
        this.lastFoodBundleUnits = units;
        this.lastFoodBundleQuote = Math.max(0, quote);
    }

    public int settleFood(Humanoid humanoid, Admission admission, int[] quantities, RoomInstance seller) {
        Humanoid payer;
        if (!admission.admitted()) {
            return 0;
        }
        double[] unitPrices = new double[RESOURCES.EDI().all().size()];
        for (int i = 0; i < unitPrices.length; ++i) {
            RESOURCE resource = RESOURCES.EDI().all().get(i).resource;
            unitPrices[i] = this.prices.ready() ? this.prices.price(resource.index()) : (double)PolityPriceAnchor.priceOf(resource);
        }
        int priced = FoodGateKernel.bill(quantities, unitPrices, admission.free() ? Integer.MAX_VALUE : admission.quote());
        int bill = admission.free() ? 0 : priced;
        int[] resources = AffordabilityGate.edibleToResources(quantities);
        if (admission.free() && priced > 0) {
            this.grainDole.recordRation(humanoid, priced);
            this.sink.ration(humanoid, resources, priced, seller);
        }
        if ((payer = this.foodPayers.remove(humanoid)
) == null) {
            payer = AffordabilityGate.foodPayer(humanoid);
        }
        if (payer == null) {
            payer = humanoid;
        }
        if (!admission.free()) {
            bill = this.escrow.settleOrCharge(payer, admission.quote(), bill);
        }
        if (bill > 0) {
            this.sink.purchase(payer, resources, bill, Kind.FOOD, seller);
        }
        this.settledMeals.computeIfAbsent(humanoid,
 ignored -> new ArrayDeque<Integer>()).addLast(bill);
        return bill;
    }

    public int settleGoods(Humanoid humanoid, Admission admission, int bill, int[] resources, RoomInstance seller) {
        return this.settle(humanoid, admission, Math.max(0, bill), this.settledGoods, resources, Kind.GOODS, seller);
    }

    private int settle(Humanoid humanoid, Admission admission, int rawBill,Map<Humanoid, ArrayDeque<Integer>> queue, int[] resources
, Kind kind, RoomInstance seller) {
        if (!admission.admitted()) {
            return 0;
        }
        int bill = Math.min(admission.quote(), rawBill);
        bill = this.escrow.settleOrCharge(humanoid, admission.quote(), bill);
        if (bill > 0) {
            this.sink.purchase(humanoid, resources, bill, kind, seller);
        }
        queue.computeIfAbsent(humanoid,
 ignored -> new ArrayDeque<Integer>()).addLast(bill);
        return bill;
    }

    public void cancelFood(Humanoid humanoid, Admission admission) {
        Humanoid payer = this.foodPayers.remove(humanoid)
;
        if (payer == null) {
            payer = AffordabilityGate.foodPayer(humanoid);
        }
        if (payer == null) {
            payer = humanoid;
        }
        if (admission != null && admission.quote() > 0) {
            this.escrow.release(payer, admission.quote());
        }
    }

    public void cancel(Humanoid humanoid, Admission admission) {
        if (admission != null && admission.quote() > 0) {
            this.escrow.release(humanoid, admission.quote());
        }
    }

    public int consumeSettledMeal(Humanoid individual)
 {
        return AffordabilityGate.consume(this.settledMeals, individual);
    }

    public int consumeSettledDrink(Humanoid individual)
 {
        return AffordabilityGate.consume(this.settledDrinks, individual);
    }

    public int consumeSettledGoods(Humanoid individual)
 {
        return AffordabilityGate.consume(this.settledGoods, individual);
    }

    public void recordScavengedMeal(Humanoid humanoid) {
        this.settledMeals.computeIfAbsent(humanoid,
 ignored -> new ArrayDeque<Integer>()).addLast(0);
    }

    private static int consume(Map<Humanoid, ArrayDeque<Integer>> queues, Humanoid individual)
 {
        ArrayDeque<Integer> queue = queues.get(individual);
        if (queue == null || queue.isEmpty()) {
            return -1;
        }
        int bill = queue.removeFirst();
        if (queue.isEmpty()) {
            queues.remove(individual);
        }
        return bill;
    }

    public void clear() {
        this.settledMeals.clear();
        this.settledDrinks.clear();
        this.settledGoods.clear();
        this.foodPayers.clear();
        this.lastFoodBundleQuote = 0;
        this.lastFoodBundleUnits = 0;
    }

    private boolean freeRation(Humanoid humanoid) {
        if (humanoid.indu().clas() == HCLASSES.SLAVE()) {
            return true;
        }
        if (humanoid.indu().hType() == HTYPES.CHILD_SLAVE()) {
            return true;
        }
        if (this.grainDole.isOnRoll(humanoid.indu())) {
            return true;
        }
        if (humanoid.indu().hType() == HTYPES.CHILD() && livingParent(humanoid) == null) {
            return true;
        }
        // Kein Blanko-Safety-Net mehr — nur GrainDole-Empfänger,
        // Sklaven und Waisen bekommen gratis Essen.
        // Bürger ohne Job müssen arbeiten oder vom GrainDole-System
        // erfasst werden (netWorth < doleWealthThreshold).
        return false;
    }

    private static Humanoid livingParent(Humanoid child) {
        IHumanoidAccess hum = EngineMirror.api() != null ? EngineMirror.api().humanoids() : null;
        return hum != null ? hum.getLivingParent(child) : EngineSeams.livingParent(child);
    }

    private static Humanoid foodPayer(Humanoid humanoid) {
        return humanoid.indu().hType() == HTYPES.CHILD() ? livingParent(humanoid) : humanoid;
    }

    private static int safeAdd(int a, int b) {
        long value = (long)a + (long)Math.max(0, b);
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)value;
    }

    private static int[] edibleToResources(int[] edible) {
        int[] resources = new int[RESOURCES.ALL().size()];
        for (int i = 0; i < edible.length && i < RESOURCES.EDI().all().size(); ++i) {
            resources[RESOURCES.EDI().all().get(i).resource.index()] = edible[i];
        }
        return resources;
    }

    public static interface SettlementSink {
        public static final SettlementSink NONE = new SettlementSink(){

            @Override
            public void purchase(Humanoid buyer, int[] resources, int gross, Kind kind, RoomInstance seller) {
            }

            @Override
            public void ration(Humanoid diner, int[] resources, int marketValue, RoomInstance seller) {
            }
        };

        public void purchase(Humanoid var1, int[] var2, int var3, Kind var4, RoomInstance var5);

        public void ration(Humanoid var1, int[] var2, int var3, RoomInstance var4);
    }

    public record Admission(boolean admitted, int quote, boolean free) {
    }

    public static enum Kind {
        FOOD,
        DRINK,
        GOODS;

    }
}

