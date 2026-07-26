package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FResources;
import game.time.TIME;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.resources.ResG;
import init.resources.ResGEat;
import init.trade.TRADABLE;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import settlement.main.SETT;
import settlement.room.industry.module.Industry;
import settlement.room.industry.module.IndustryResource;
import settlement.room.industry.module.ROOM_PRODUCER_INSTANCE;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.room.service.food.canteen.ROOM_CANTEEN;
import settlement.room.service.food.eatery.ROOM_EATERY;
import util.statistics.HISTORY_COLLECTION;

public final class FlowMeter {
    private final Map<RoomInstance, FirmState> firms = new IdentityHashMap<RoomInstance, FirmState>();

    /**
     * v0.1.3 (Phase-4.7-Blocker #8): Register the firm-tracking map so that
     * it is cleared on Save/Load. The vanilla flow-meter rebuilds firms on
     * every tick from {@code SETT.ROOMS()} anyway, so clearing is harmless;
     * the alternative is silent null-lookups after the engine recreates
     * {@code RoomInstance} objects.
     */
    public FlowMeter() {
        IdentityMapRegistry.register("FlowMeter", "firms", firms);
    }
    private double[] supply = new double[0];
    private double[] targetSupply = new double[0]; // T5 (B-001): Blueprint-Intent. Echte Gap-Berechnung erfordert Engine-Blueprint-API (Closed — ROADMAP.md T5, Commit c1964d2).
    private double[] firmInputs = new double[0];
    private double[] householdConsumption = new double[0];
    private double[] demand = new double[0];
    private double[] constructionDemand = new double[0];
    private double[] stock = new double[0];
    private double[] lastStock = new double[0];
    private double[] stockChange = new double[0];
    private int[] lastGlobalProduced = new int[0];
    private int[] producerlessProduced = new int[0];
    private boolean globalProducedInitialized;
    private boolean stockInitialized;

    public void sample(double gameSecondsElapsed, double smoothingDays, long[] withheld) {
        this.sample(gameSecondsElapsed, smoothingDays, withheld, null);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    public void sample(double gameSecondsElapsed, double smoothingDays, long[] withheld, int[] constructionWithdrawals) {
        int good;
        if (SETT.ROOMS() == null || gameSecondsElapsed <= 0.0) {
            return;
        }
        int goods = RESOURCES.ALL().size();
        this.ensureCapacity(goods);
        double elapsedDays = gameSecondsElapsed / (double)TIME.secondsPerDay();
        if (!(elapsedDays > 0.0)) {
            return;
        }
        double window = smoothingDays > 0.0 ? smoothingDays : elapsedDays;
        double blend = 1.0 - Math.exp(-elapsedDays / window);
        double constructionWindow = EconConfig.constructionSmoothingDays > 0.0 ? EconConfig.constructionSmoothingDays : elapsedDays;
        double constructionBlend = 1.0 - Math.exp(-elapsedDays / constructionWindow);
        Arrays.fill(this.supply, 0.0);
        Arrays.fill(this.firmInputs, 0.0);
        int[] industryProduced = new int[goods];
        @SuppressWarnings({"rawtypes","unchecked"})
        Set seen = Collections.newSetFromMap(new IdentityHashMap());
        for (Industry industry : SETT.ROOMS().industries.all) {
            RoomBlueprintImp roomBlueprintImp = industry.blue;
            if (!(roomBlueprintImp instanceof RoomBlueprintIns)) continue;
            RoomBlueprintIns blueprint = (RoomBlueprintIns)roomBlueprintImp;
            for (int i = 0; i < blueprint.instancesSize(); ++i) {
                ROOM_PRODUCER_INSTANCE producer;
                RoomInstance room = blueprint.getInstance(i);
                if (!(room instanceof ROOM_PRODUCER_INSTANCE) || (producer = (ROOM_PRODUCER_INSTANCE)room).industry() != industry) continue;
                seen.add(room);
                FirmState state = this.firms.get(room);
                if (state == null || state.industry != industry) {
                    state = new FirmState(industry);
                    this.firms.put(room, state);
                }
                state.sample(producer, elapsedDays, blend);
                state.addTo(this.supply, this.firmInputs);
                state.addExactProduction(industryProduced);
            }
        }
        // T-005 (Phase 4.7/T-005): Catch-all für Producer-Räume deren Industry nicht in
        // SETT.ROOMS().industries.all registriert ist (Farms, Pastures, WORKSHOP_POTTERY,
        // …). Vor diesem Patch hatten solche Räume permanent total_output_value_per_day=0.00.
        // Wir teilen seen mit dem Industry-Loop oben — Doppel-Sampling ist unmöglich.
        for (RoomBlueprintIns<?> blueprint : EngineSeams.settRoomsIns()) {
            for (int i = 0; i < blueprint.instancesSize(); ++i) {
                RoomInstance room = blueprint.getInstance(i);
                if (!(room instanceof ROOM_PRODUCER_INSTANCE) || seen.contains(room)) continue;
                ROOM_PRODUCER_INSTANCE producer = (ROOM_PRODUCER_INSTANCE) room;
                Industry ind = producer.industry();
                if (ind == null || (ind.outs().size() == 0 && ind.ins().size() == 0)) continue;
                seen.add(room);
                FirmState state = this.firms.get(room);
                if (state == null || state.industry != ind) {
                    state = new FirmState(ind);
                    this.firms.put(room, state);
                }
                state.sample(producer, elapsedDays, blend);
                state.addTo(this.supply, this.firmInputs);
                state.addExactProduction(industryProduced);
            }
        }
        Iterator<RoomInstance> iterator = this.firms.keySet().iterator();
        while (iterator.hasNext()) {
            if (seen.contains(iterator.next())) continue;
            iterator.remove();
        }
        HISTORY_COLLECTION produced = FACTIONS.player().res().in(FResources.RTYPE.PRODUCED);
        for (good = 0; good < goods; ++good) {
            TRADABLE tradable = RESOURCES.ALL().get(good).tr();
            int current = produced.get((Object)tradable);
            int global = this.globalProducedInitialized ? FlowMeter.exactCounterDelta(current, this.lastGlobalProduced[good], produced.history((Object)tradable).get(1)) : 0;
            this.producerlessProduced[good] = FlowMeter.producerlessUnits(global, industryProduced[good]);
            this.lastGlobalProduced[good] = current;
        }
        this.globalProducedInitialized = true;
        for (good = 0; good < goods; ++good) {
            long locked = withheld != null && good < withheld.length ? Math.max(0L, withheld[good]) : 0L;
            double physical = Math.max(0L, FlowMeter.stockOf(RESOURCES.ALL().get(good)) - locked);
            int withdrawn = constructionWithdrawals != null && good < constructionWithdrawals.length ? Math.max(0, constructionWithdrawals[good]) : 0;
            this.stock[good] = FlowMeter.stockBeforeWithdrawal(physical, withdrawn);
            if (this.stockInitialized) {
                double instantChange = (this.stock[good] - this.lastStock[good]) / elapsedDays;
                this.stockChange[good] = FlowMeter.smooth(this.stockChange[good], instantChange, blend);
            } else {
                this.stockChange[good] = 0.0;
            }
            this.lastStock[good] = physical;
            this.householdConsumption[good] = Math.max(0.0, this.supply[good] - this.firmInputs[good] - this.stockChange[good]);
            double instantConstructionDemand = elapsedDays > 0.0 ? (double) withdrawn / elapsedDays : 0.0;
            this.constructionDemand[good] = FlowMeter.smooth(this.constructionDemand[good], instantConstructionDemand, constructionBlend);
            this.demand[good] = this.firmInputs[good] + this.householdConsumption[good] + this.constructionDemand[good];
        }
        this.stockInitialized = true;
    }

    static double stockBeforeWithdrawal(double physicalStock, int withdrawn) {
        double result = Math.max(0.0, physicalStock) + (double)Math.max(0, withdrawn);
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    public Snapshot snapshot() {
        return new Snapshot(this.supply, this.targetSupply, this.firmInputs, this.householdConsumption, this.demand, this.stock, this.stockChange);
    }

    public int producerlessProducedSinceLastSample(int resource) {
        return resource >= 0 && resource < this.producerlessProduced.length ? this.producerlessProduced[resource] : 0;
    }

    static int producerlessUnits(int globalProduced, int industryProduced) {
        return Math.max(0, globalProduced - Math.max(0, industryProduced));
    }

    public List<FirmSnapshot> firmSnapshots() {
        ArrayList<FirmSnapshot> result = new ArrayList<FirmSnapshot>(this.firms.size());
        for (Map.Entry<RoomInstance, FirmState> entry : this.firms.entrySet()) {
            FirmState state = entry.getValue();
            if (!state.initialized) continue;
            result.add(state.snapshot(entry.getKey()));
        }
        return List.copyOf(result);
    }

    public void clear() {
        this.firms.clear();
        Arrays.fill(this.supply, 0.0);
        Arrays.fill(this.targetSupply, 0.0); // T5 (B-001)
        Arrays.fill(this.firmInputs, 0.0);
        Arrays.fill(this.householdConsumption, 0.0);
        Arrays.fill(this.demand, 0.0);
        Arrays.fill(this.constructionDemand, 0.0);
        Arrays.fill(this.stock, 0.0);
        Arrays.fill(this.lastStock, 0.0);
        Arrays.fill(this.stockChange, 0.0);
        Arrays.fill(this.lastGlobalProduced, 0);
        Arrays.fill(this.producerlessProduced, 0);
        this.globalProducedInitialized = false;
        this.stockInitialized = false;
    }

    private void ensureCapacity(int goods) {
        if (this.supply.length == goods) {
            return;
        }
        this.supply = new double[goods];
        this.targetSupply = new double[goods]; // T5 (B-001)
        this.firmInputs = new double[goods];
        this.householdConsumption = new double[goods];
        this.demand = new double[goods];
        this.constructionDemand = new double[goods];
        this.stock = new double[goods];
        this.lastStock = new double[goods];
        this.stockChange = new double[goods];
        this.lastGlobalProduced = new int[goods];
        this.producerlessProduced = new int[goods];
        this.firms.clear();
        this.globalProducedInitialized = false;
        this.stockInitialized = false;
    }

    private static long stockOf(RESOURCE resource) {
        long total = SETT.ROOMS().STOCKPILE.tally().amountTotal(resource);
        if (!RESOURCES.EDI().is(resource)) {
            return total;
        }
        ResGEat edible = null;
        for (ResGEat candidate : RESOURCES.EDI().all()) {
            if (candidate.resource != resource) continue;
            edible = candidate;
            break;
        }
        if (edible == null) {
            return total;
        }
        for (ROOM_EATERY eatery : EngineSeams.settRoomsEateries()) {
            total += eatery.amount((ResG)edible);
        }
        for (ROOM_CANTEEN canteen : EngineSeams.settRoomsCanteens()) {
            total += canteen.amount((ResG)edible);
        }
        return total;
    }

    static double counterDelta(double current, double previous, double completedDay) {
        if (current >= previous) {
            return current - previous;
        }
        return Math.max(0.0, completedDay - previous) + Math.max(0.0, current);
    }

    static double smooth(double previous, double current, double blend) {
        if (!Double.isFinite(current)) {
            return previous;
        }
        double weight = Math.max(0.0, Math.min(1.0, blend));
        return previous + weight * (current - previous);
    }

    static int exactCounterDelta(int current, int previous, int completedPeriod) {
        long delta = current >= previous ? (long)current - (long)previous : Math.max(0L, (long)completedPeriod - (long)previous) + (long)Math.max(0, current);
        return delta >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)Math.max(0L, delta);
    }

    private static final class FirmState {
        private final Industry industry;
        private final double[] lastOutput;
        private final int[] lastProduced;
        private final int[] producedDelta;
        private final double[] lastInput;
        private final int[] lastConsumed;
        private final int[] consumedDelta;
        private final double[] outputRate;
        private final double[] inputRate;
        private boolean initialized;

        FirmState(Industry industry) {
            this.industry = industry;
            this.lastOutput = new double[industry.outs().size()];
            this.lastProduced = new int[this.lastOutput.length];
            this.producedDelta = new int[this.lastOutput.length];
            this.lastInput = new double[industry.ins().size()];
            this.lastConsumed = new int[this.lastInput.length];
            this.consumedDelta = new int[this.lastInput.length];
            this.outputRate = new double[this.lastOutput.length];
            this.inputRate = new double[this.lastInput.length];
        }

        void sample(ROOM_PRODUCER_INSTANCE firm, double elapsedDays, double blend) {
            double current;
            IndustryResource resource;
            int i;
            for (i = 0; i < this.lastOutput.length; ++i) {
                resource = this.industry.outs().get(i);
                current = resource.day.getD(firm);
                int produced = resource.year.get(firm);
                int n = this.producedDelta[i] = this.initialized ? FlowMeter.exactCounterDelta(produced, this.lastProduced[i], resource.yearPrev.get(firm)) : 0;
                if (this.initialized) {
                    // Physische Produktionsrate aus tatsächlich fertigen Einheiten ableiten,
                    // nicht aus der Tageskapazität, die auch Phantom-Fortschritt enthalten kann.
                    double physicalRate = (double) this.producedDelta[i] / elapsedDays;
                    this.outputRate[i] = FlowMeter.smooth(this.outputRate[i], physicalRate, blend);
                }
                this.lastOutput[i] = current;
                this.lastProduced[i] = produced;
            }
            for (i = 0; i < this.lastInput.length; ++i) {
                resource = this.industry.ins().get(i);
                current = resource.day.getD(firm);
                int consumed = resource.year.get(firm);
                int n = this.consumedDelta[i] = this.initialized ? FlowMeter.exactCounterDelta(consumed, this.lastConsumed[i], resource.yearPrev.get(firm)) : 0;
                if (this.initialized) {
                    double physicalRate = (double) this.consumedDelta[i] / elapsedDays;
                    this.inputRate[i] = FlowMeter.smooth(this.inputRate[i], physicalRate, blend);
                }
                this.lastInput[i] = current;
                this.lastConsumed[i] = consumed;
            }
            this.initialized = true;
        }

        void addTo(double[] aggregateOutput, double[] aggregateInput) {
            int i;
            for (i = 0; i < this.outputRate.length; ++i) {
                int n = this.industry.outs().get(i).resource.index();
                aggregateOutput[n] = aggregateOutput[n] + this.outputRate[i];
            }
            for (i = 0; i < this.inputRate.length; ++i) {
                int n = this.industry.ins().get(i).resource.index();
                aggregateInput[n] = aggregateInput[n] + this.inputRate[i];
            }
        }

        void addExactProduction(int[] aggregate) {
            for (int i = 0; i < this.producedDelta.length; ++i) {
                int resource = this.industry.outs().get(i).resource.index();
                aggregate[resource] = (int)Math.min(Integer.MAX_VALUE, (long)aggregate[resource] + (long)this.producedDelta[i]);
            }
        }

        FirmSnapshot snapshot(RoomInstance room) {
            int i;
            RESOURCE[] outputs = new RESOURCE[this.outputRate.length];
            RESOURCE[] inputs = new RESOURCE[this.inputRate.length];
            for (i = 0; i < outputs.length; ++i) {
                outputs[i] = this.industry.outs().get(i).resource;
            }
            for (i = 0; i < inputs.length; ++i) {
                inputs[i] = this.industry.ins().get(i).resource;
            }
            return new FirmSnapshot(room, outputs, this.outputRate, this.producedDelta, inputs, this.inputRate, this.consumedDelta);
        }
    }

    public static final class Snapshot {
        private final double[] supply;
        private final double[] targetSupply; // T5 (B-001)
        private final double[] firmInputs;
        private final double[] householdConsumption;
        private final double[] demand;
        private final double[] stock;
        private final double[] stockChange;

        Snapshot(double[] supply, double[] targetSupply, double[] firmInputs, double[] householdConsumption, double[] demand, double[] stock, double[] stockChange) {
            this.supply = supply.clone();
            this.targetSupply = targetSupply.clone();
            this.firmInputs = firmInputs.clone();
            this.householdConsumption = householdConsumption.clone();
            this.demand = demand.clone();
            this.stock = stock.clone();
            this.stockChange = stockChange.clone();
        }

        public int size() {
            return this.supply.length;
        }

        public double supplyPerDay(int good) {
            return this.supply[good];
        }

        /**
         * @deprecated (T5 B-001) Liefert aktuell identisch zu {@link #supplyPerDay(int)}.
         * Echte Intent-Gap-Berechnung (employeesNeeded / employeesActual) ist TODO
         * bis Engine-Blueprint-API verfuegbar ist. Wird in einer spaeteren Phase
         * durch Gap-bewusste Implementierung ersetzt.
         */
        @Deprecated
        public double targetSupplyPerDay(int good) {
            return good >= 0 && good < this.targetSupply.length ? this.targetSupply[good] : 0.0;
        }

        public double firmInputsPerDay(int good) {
            return this.firmInputs[good];
        }

        public double householdConsumptionPerDay(int good) {
            return this.householdConsumption[good];
        }

        public double demandPerDay(int good) {
            return this.demand[good];
        }

        public double stock(int good) {
            return this.stock[good];
        }

        public double stockChangePerDay(int good) {
            return this.stockChange[good];
        }
    }

    public static final class FirmSnapshot {
        private final RoomInstance room;
        private final RESOURCE[] outputs;
        private final double[] outputRates;
        private final int[] producedDelta;
        private final RESOURCE[] inputs;
        private final double[] inputRates;
        private final int[] consumedDelta;

        FirmSnapshot(RoomInstance room, RESOURCE[] outputs, double[] outputRates, int[] producedDelta, RESOURCE[] inputs, double[] inputRates, int[] consumedDelta) {
            this.room = room;
            this.outputs = outputs.clone();
            this.outputRates = outputRates.clone();
            this.producedDelta = producedDelta.clone();
            this.inputs = inputs.clone();
            this.inputRates = inputRates.clone();
            this.consumedDelta = consumedDelta.clone();
        }

        public RoomInstance room() {
            return this.room;
        }

        public int outputCount() {
            return this.outputs.length;
        }

        public RESOURCE outputResource(int index) {
            return this.outputs[index];
        }

        public double outputPerDay(int index) {
            return this.outputRates[index];
        }

        public int producedSinceLastSample(int index) {
            return this.producedDelta[index];
        }

        public int inputCount() {
            return this.inputs.length;
        }

        public RESOURCE inputResource(int index) {
            return this.inputs[index];
        }

        public double inputPerDay(int index) {
            return this.inputRates[index];
        }

        public int consumedSinceLastSample(int index) {
            return this.consumedDelta[index];
        }
    }
}

