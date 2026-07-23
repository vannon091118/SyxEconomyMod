package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.time.TIME;
import init.resources.RESOURCE;
import init.type.HCLASSES;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.room.main.employment.RoomEmploymentIns;
import settlement.stats.STATS;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomicRoles;
import vannon.syx.economy.core.EngineSeams;
import vannon.syx.economy.core.FirmEconomyKernel;
import vannon.syx.economy.core.FlowMeter;
import vannon.syx.economy.core.FlowPrices;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.StateWarehouses;
import vannon.syx.economy.core.Wallets;

public final class FirmLedger {
    private final Map<RoomInstance, FirmState> firms = new IdentityHashMap<RoomInstance, FirmState>();
    private final Map<String, BlueprintState> blueprints = new HashMap<String, BlueprintState>();
    private final Map<RoomBlueprintImp, Double> serviceRevenue = new IdentityHashMap<RoomBlueprintImp, Double>();
    private final Map<RoomBlueprintImp, Double> stateWageMarginal = new IdentityHashMap<RoomBlueprintImp, Double>();
    /**
     * Phase 5e — Per-Room Operating-Mode für die Pause-vs-Operating-Cost-Choice-UI.
     * PRODUCE = vanilla. PAUSED = kein Output, keine Operating-Cost. MOTHBALLED =
     * kein Output, 0.3× Cost. Default PRODUCE; UI-Toggle in EconomyWindow setzt
     * auf PAUSED/MOTHBALLED. Save/Load nicht in diesem Patch — fällt nach Load auf
     * PRODUCE zurück, wird mit Phase-4.7-Chunk-Migration erfasst.
     */
    private final Map<RoomInstance, EconConfig.RoomOperatingMode> opModes = new IdentityHashMap<RoomInstance, EconConfig.RoomOperatingMode>();
    private StateWarehouses stateWarehouses;
    private int lastSizingTick = -1073741824;
    private long lastIncomeDue;
    private long lastIncomePaid;
    private int lastWorkersPaid;
    private int lastWorkersUnpaid;
    private double meanPositiveMarginal;
    private int lastFurnitureDumpTick = -1;

    /**
     * v0.1.3 (Phase-4.7-Blocker #8): Register all reference-keyed maps with the
     * {@link IdentityMapRegistry} so they are explicitly cleared on Save/Load
     * instead of silently losing data. IncomeCarry per firm, service-revenue and
     * state-wage-marginal per blueprint cannot survive a reference-identity shift
     * (Java IdentityHashMap looks up by {@code ==}); clearing them is the
     * intermediate fix until full Long-Key migration in Phase-4.7.
     */
    public FirmLedger() {
        IdentityMapRegistry.register("FirmLedger", "firms", firms);
        IdentityMapRegistry.register("FirmLedger", "serviceRevenue", serviceRevenue);
        IdentityMapRegistry.register("FirmLedger", "stateWageMarginal", stateWageMarginal);
        // Phase 5e: opModes mit clearOnLoad-Cover — nach Save/Load fallen alle Modi auf
        // PRODUCE (Default). Save/Load selbst kommt mit Phase-4.7-Chunk-Migration.
        IdentityMapRegistry.register("FirmLedger", "opModes", opModes);
    }
    public long lastIncomeDue() {
        return this.lastIncomeDue;
    }

    public long lastIncomePaid() {
        return this.lastIncomePaid;
    }

    public int lastWorkersPaid() {
        return this.lastWorkersPaid;
    }

    public int lastWorkersUnpaid() {
        return this.lastWorkersUnpaid;
    }

    public double meanPositiveMarginal() {
        return this.meanPositiveMarginal;
    }


    void setStateWarehouses(StateWarehouses stateWarehouses) {
        this.stateWarehouses = stateWarehouses;
    }

    private boolean stateWarehouse(RoomInstance room) {
        return room instanceof StockpileInstance && this.stateWarehouses != null && this.stateWarehouses.isStateOwned(room);
    }

    private boolean excludedFromMarketSizing(RoomInstance room) {
        return room == null || EconomicRoles.excludedFromMarketSizing((RoomBlueprintImp)room.blueprintI()) || this.stateWarehouse(room);
    }

    public double profitPerDay(RoomBlueprintImp blueprint) {
        BlueprintState state = blueprint == null ? null : this.blueprints.get(blueprint.key);
        return state == null ? 0.0 : state.profit;
    }

    public double marginalSurplus(RoomBlueprintImp blueprint) {
        BlueprintState state = blueprint == null ? null : this.blueprints.get(blueprint.key);
        return state == null ? 0.0 : state.marginal;
    }

    public void recordServiceRevenue(RoomBlueprintImp blueprint, double amount) {
        if (blueprint != null && amount > 0.0 && Double.isFinite(amount)) {
            this.serviceRevenue.merge(blueprint, amount, Double::sum);
        }
    }

    public void recordStateWageMarginal(RoomBlueprintImp blueprint, double marginalPerDay) {
        if (blueprint == null) {
            return;
        }
        if (marginalPerDay > 0.0 && Double.isFinite(marginalPerDay)) {
            this.stateWageMarginal.put(blueprint, marginalPerDay);
        } else {
            this.stateWageMarginal.remove(blueprint);
        }
    }

    public void recordFirmRevenue(RoomInstance room, double amount) {
        if (room == null || room.employees() == null || EconomicRoles.excludedFromMarketAccounting((RoomBlueprintImp)room.blueprintI()) || !(amount > 0.0) || !Double.isFinite(amount)) {
            return;
        }
        FirmState state = this.firms.computeIfAbsent(room, ignored -> new FirmState());
        state.cashTracked = true;
        state.pendingCash += amount;
    }

    public void recordFirmCost(RoomInstance room, double amount) {
        if (room == null || room.employees() == null || EconomicRoles.excludedFromMarketAccounting((RoomBlueprintImp)room.blueprintI()) || !(amount > 0.0) || !Double.isFinite(amount)) {
            return;
        }
        FirmState state = this.firms.computeIfAbsent(room, ignored -> new FirmState());
        state.cashTracked = true;
        state.pendingCash -= amount;
    }

    /**
     * Phase 5e: Set operating-mode for a room. Caller (UI) decides PRODUCE / PAUSED / MOTHBALLED.
     * Setting PAUSED does NOT kick workers out — the empty-employee path inside
     * update() is what freezes operating-cost when paired with no workers.
     */
    public void setOperatingMode(RoomInstance room, EconConfig.RoomOperatingMode mode) {
        if (room == null || mode == null) return;
        opModes.put(room, mode);
    }

    public EconConfig.RoomOperatingMode getOperatingMode(RoomInstance room) {
        if (room == null) return EconConfig.RoomOperatingMode.PRODUCE;
        EconConfig.RoomOperatingMode mode = opModes.get(room);
        return mode == null ? EconConfig.RoomOperatingMode.PRODUCE : mode;
    }

    public UpdateResult update(Roster roster, Wallets wallets, FlowMeter meter, FlowPrices prices, AffordabilityGate gate, double gameSeconds, int ticks) {
        boolean sizeNow;
        FirmLedger.restoreMilitaryCapacity();
        this.lastIncomeDue = 0L;
        this.lastIncomePaid = 0L;
        this.lastWorkersPaid = 0;
        this.lastWorkersUnpaid = 0;
        if (!EconConfig.firmLedgerEnabled || !prices.ready() || gameSeconds <= 0.0) {
            return new UpdateResult(0L);
        }
        double elapsedDays = gameSeconds / (double)TIME.secondsPerDay();
        if (!(elapsedDays > 0.0)) {
            return new UpdateResult(0L);
        }
        for (FirmState state : this.firms.values()) {
            state.physicalSeen = false;
            state.physicalProfit = 0.0;
            state.marketTracked = false;
            state.lastIncomeDueThisTick = 0;
            state.lastIncomePaidThisTick = 0;
            state.workersUnpaidThisTick = 0;
        }
        for (RoomBlueprintIns<?> blueprint : SETT.ROOMS().ins()) {
            for (int i = 0; i < blueprint.instancesSize(); ++i) {
                RoomInstance room = blueprint.getInstance(i);
                if (room == null || !room.exists() || room.employees() == null || room.employees().max() <= 0 || this.excludedFromMarketSizing(room)) continue;
                FirmState state = this.firms.computeIfAbsent(room, ignored -> new FirmState());
                state.marketTracked = true;
                if (!state.targetInitialized) {
                    state.marketTarget = FirmLedger.initialMarketTarget(room.employees().employed(), room.employees().max(), EconConfig.minimumWorkersPerWorkplace);
                    state.targetInitialized = true;
                }
                if (room.employees().hardTarget() == state.marketTarget) continue;
                EngineSeams.setFirmTarget(room, state.marketTarget);
            }
        }
        this.blueprints.clear();
        for (FlowMeter.FirmSnapshot snapshot : meter.firmSnapshots()) {
            RoomInstance room = snapshot.room();
            if (room == null || room.employees() == null || EconomicRoles.excludedFromMarketAccounting((RoomBlueprintImp)room.blueprintI())) continue;
            FirmState state = this.firms.computeIfAbsent(room, ignored -> new FirmState());
            state.setOutputs(snapshot);
            state.physicalSeen = true;
            state.physicalProfit = FirmLedger.value(snapshot, prices);
            state.totalOutputValue = snapshotOutputTotal(snapshot, prices);
            state.totalInputValue = snapshotInputTotal(snapshot, prices);
            if (Double.isFinite(state.physicalProfit)) continue;
            state.physicalProfit = 0.0;
        }
        double window = EconConfig.flowSmoothingDays > 0.0 ? EconConfig.flowSmoothingDays : elapsedDays;
        double blend = 1.0 - Math.exp(-elapsedDays / window);
        Iterator<Map.Entry<RoomInstance, FirmState>> iterator = this.firms.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RoomInstance, FirmState> entry = iterator.next();
            RoomInstance roomInstance = entry.getKey();
            FirmState state = entry.getValue();
            if (roomInstance == null || !roomInstance.exists() || roomInstance.employees() == null || EconomicRoles.excludedFromMarketAccounting((RoomBlueprintImp)roomInstance.blueprintI()) || this.stateWarehouse(roomInstance) || !state.physicalSeen && !state.cashTracked && !state.marketTracked) {
                iterator.remove();
                continue;
            }
            if (state.cashTracked) {
                double instant = state.pendingCash / elapsedDays;
                state.cashRate = FlowMeter.smooth(state.cashRate, instant, blend);
                state.pendingCash = 0.0;
            }
            if (roomInstance.employees().employed() == 0) {
                state.physicalProfit = 0.0;
                state.cashRate = 0.0;
                state.profit = 0.0;
                state.marginal = 0.0;
            } else {
                state.profit = state.physicalProfit + state.cashRate;
                if (state.profit <= 0.0) {
                    state.marginal = state.profit / (double)Math.max(1, roomInstance.employees().employed());
                } else if (state.marginal == 0.0 || !Double.isFinite(state.marginal)) {
                    state.marginal = state.profit / (double)Math.max(1, roomInstance.employees().employed());
                }
            }
            if (!this.excludedFromMarketSizing(roomInstance) && EconConfig.firmSizingEnabled && FirmEconomyKernel.shouldIdle(state.profit, EconConfig.firmSizingHysteresis)) {
                state.marketTarget = 0;
                state.hill = new FirmEconomyKernel.HillState(0, 0.0, 1, true);
                EngineSeams.setFirmTarget(roomInstance, 0);
            }
            RoomBlueprintIns<?> blueprint = roomInstance.blueprintI();
            BlueprintState aggregate = this.blueprints.computeIfAbsent(blueprint.key, ignored -> new BlueprintState());
            aggregate.profit += state.profit;
            aggregate.marginalNumerator += state.marginal * (double)Math.max(1, roomInstance.employees().employed());
            aggregate.marginalWeight += Math.max(1, roomInstance.employees().employed());
        }
        for (Map.Entry<RoomBlueprintImp, Double> entry : this.serviceRevenue.entrySet()) {
            RoomBlueprintImp roomBlueprintImp = (RoomBlueprintImp)entry.getKey();
            BlueprintState aggregate = this.blueprints.computeIfAbsent(roomBlueprintImp.key, ignored -> new BlueprintState());
            aggregate.profit += ((Double)entry.getValue()).doubleValue();
            int workers = 0;
            if (roomBlueprintImp instanceof RoomBlueprintIns) {
                RoomBlueprintIns<?> instances = (RoomBlueprintIns<?>)roomBlueprintImp;
                for (int i = 0; i < instances.instancesSize(); ++i) {
                    RoomEmploymentIns employment = instances.getInstance(i).employees();
                    if (employment == null) continue;
                    workers += employment.employed();
                }
            }
            aggregate.marginalNumerator += ((Double)entry.getValue()).doubleValue();
            aggregate.marginalWeight += Math.max(1, workers);
        }
        this.serviceRevenue.clear();
        this.applyStateWageMarginals();
        for (BlueprintState blueprintState : this.blueprints.values()) {
            blueprintState.marginal = (blueprintState.marginalWeight == 0 ? 0.0 : blueprintState.marginalNumerator / (double)blueprintState.marginalWeight) + blueprintState.stateWageMarginal;
        }
        this.recomputeMeanMarginal();
        boolean gateActive = EconConfig.firmInputGateEnabled && EconConfig.firmSizingEnabled && gate != null;
        Map<RoomInstance, FlowMeter.FirmSnapshot> snapshots = null;
        if (gateActive) {
            snapshots = new IdentityHashMap<RoomInstance, FlowMeter.FirmSnapshot>();
            for (FlowMeter.FirmSnapshot snapshot : meter.firmSnapshots()) {
                snapshots.put(snapshot.room(), snapshot);
            }
            for (Map.Entry<RoomInstance, FirmState> entry : this.firms.entrySet()) {
                RoomInstance room = entry.getKey();
                if (this.excludedFromMarketSizing(room)) continue;
                FirmState state = entry.getValue();
                if (!state.physicalSeen || state.hill == null) continue;
                FlowMeter.FirmSnapshot snap = snapshots.get(room);
                if (snap == null) continue;
                if (gate.affordFirmInputs(snap, state.profit, prices)) continue;
                state.marketTarget = 0;
                state.hill = new FirmEconomyKernel.HillState(0, 0.0, 1, true);
                if (room.employees() != null) {
                    EngineSeams.setFirmTarget(room, 0);
                }
            }
        }
        int sizingThreshold = Math.max(1, (int)(EconConfig.firmSizingRefreshDays * TIME.secondsPerDay()));
        boolean bl = sizeNow = EconConfig.firmSizingEnabled && ticks - this.lastSizingTick >= sizingThreshold;
        if (sizeNow) {
            this.lastSizingTick = ticks;
            for (Map.Entry<RoomInstance, FirmState> entry : this.firms.entrySet()) {
                RoomInstance room = entry.getKey();
                if (this.excludedFromMarketSizing(room)) continue;
                if (gateActive && snapshots != null) {
                    FlowMeter.FirmSnapshot snap = snapshots.get(room);
                    if (snap != null && !gate.affordFirmInputs(snap, entry.getValue().profit, prices)) {
                        continue;
                    }
                }
                this.size(room, entry.getValue());
            }
            this.recomputeBlueprintMarginals();
            this.recomputeMeanMarginal();
        }
        this.stateWageMarginal.clear();
        long l = 0L;
        long treasuryBudget = Math.max(0L, (long)Math.floor(FACTIONS.player().credits().credits()));
        for (Map.Entry<RoomInstance, FirmState> entry : this.firms.entrySet()) {
            int due;
            FirmState state = entry.getValue();
            if (!(state.profit > 0.0)) continue;
            ArrayList<Humanoid> workers = FirmLedger.freeWorkers(roster, entry.getKey());
            int workerCount = Math.max(1, workers.size());
            double excessProfit = state.profit - EconConfig.guildSurplusMinProfitPerWorker * (double) workerCount;
            if (excessProfit > 0.0) {
                state.incomeCarry += excessProfit * elapsedDays * Math.max(0.0, EconConfig.guildSurplusShare);
            }
            int n = due = state.incomeCarry >= 2.147483647E9 ? Integer.MAX_VALUE : (int)Math.floor(state.incomeCarry);
            if (due <= 0) continue;
            this.lastIncomeDue += (long)due;
            if (workers.isEmpty()) continue;
            int payable = (int)Math.min((long)due, Math.min(Integer.MAX_VALUE, treasuryBudget));
            treasuryBudget -= (long)payable;
            state.incomeCarry -= (double)payable;
            int[] shares = FirmEconomyKernel.split(payable, workers.size());
            int unpaidCount = payable < due ? workers.size() : 0;
            if (unpaidCount > 0) {
                this.lastWorkersUnpaid += unpaidCount;
            }
            state.lastIncomeDueThisTick = due;
            state.lastIncomePaidThisTick = payable;
            state.workersUnpaidThisTick = unpaidCount;
            for (int i = 0; i < workers.size(); ++i) {
                if (shares[i] <= 0) continue;
                Humanoid worker = workers.get(i);
                wallets.add(worker, shares[i]);
                wallets.markPaidThisTick(worker.indu());
                l += (long)shares[i];
                ++this.lastWorkersPaid;
            }
        }
        if (l > 0L) {
            FACTIONS.player().credits().inc((double)(-l), FCredits.CTYPE.MISC);
        }
        this.lastIncomePaid = l;
        if (EconConfig.debugFurnitureDump) dumpFurnitureDebug(meter, prices, ticks, gameSeconds);
        return new UpdateResult(l);
    }

    private static void restoreMilitaryCapacity() {
        if (SETT.ROOMS() == null) {
            return;
        }
        for (RoomBlueprintIns<?> blueprint : SETT.ROOMS().ins()) {
            if (!EconomicRoles.stateFundedMilitary((RoomBlueprintImp)blueprint)) continue;
            for (int i = 0; i < blueprint.instancesSize(); ++i) {
                RoomInstance room = blueprint.getInstance(i);
                if (room == null || !room.exists() || room.employees() == null || room.employees().hardTarget() == room.employees().max()) continue;
                room.employees().neededSet(room.employees().max());
            }
        }
    }

    private static double value(FlowMeter.FirmSnapshot snapshot, FlowPrices prices) {
        double[] outputRates = new double[snapshot.outputCount()];
        double[] outputPrices = new double[outputRates.length];
        for (int i = 0; i < outputRates.length; ++i) {
            outputRates[i] = snapshot.outputPerDay(i);
            outputPrices[i] = prices.price(snapshot.outputResource(i).index());
        }
        double[] inputRates = new double[snapshot.inputCount()];
        double[] inputPrices = new double[inputRates.length];
        for (int i = 0; i < inputRates.length; ++i) {
            inputRates[i] = snapshot.inputPerDay(i);
            inputPrices[i] = prices.price(snapshot.inputResource(i).index());
        }
        return FirmEconomyKernel.profit(outputRates, outputPrices, inputRates, inputPrices);
    }

    private static double snapshotOutputTotal(FlowMeter.FirmSnapshot snapshot, FlowPrices prices) {
        double total = 0.0;
        for (int i = 0; i < snapshot.outputCount(); ++i) {
            total += snapshot.outputPerDay(i) * prices.price(snapshot.outputResource(i).index());
        }
        return Double.isFinite(total) ? total : 0.0;
    }

    private static double snapshotInputTotal(FlowMeter.FirmSnapshot snapshot, FlowPrices prices) {
        double total = 0.0;
        for (int i = 0; i < snapshot.inputCount(); ++i) {
            total += snapshot.inputPerDay(i) * prices.price(snapshot.inputResource(i).index());
        }
        return Double.isFinite(total) ? total : 0.0;
    }

    private static ArrayList<Humanoid> freeWorkers(Roster roster, RoomInstance room) {
        ArrayList<Humanoid> result = new ArrayList<Humanoid>();
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid worker = roster.get(i);
            if (worker.indu().clas() == HCLASSES.SLAVE() || STATS.WORK().EMPLOYED.get(worker.indu()) != room) continue;
            result.add(worker);
        }
        return result;
    }

    public int distributeFirmRevenue(Roster roster, Wallets wallets, RoomInstance room, int amount) {
        if (room == null || EconomicRoles.excludedFromMarketAccounting((RoomBlueprintImp)room.blueprintI()) || amount <= 0) {
            return 0;
        }
        this.recordFirmRevenue(room, amount);
        ArrayList<Humanoid> workers = FirmLedger.freeWorkers(roster, room);
        if (workers.isEmpty()) {
            return 0;
        }
        int[] shares = FirmEconomyKernel.split(amount, workers.size());
        int credited = 0;
        for (int i = 0; i < shares.length; ++i) {
            if (shares[i] <= 0) continue;
            Humanoid worker = workers.get(i);
            wallets.add(worker, shares[i]);
            wallets.markPaidThisTick(worker.indu());
            credited += shares[i];
            ++this.lastWorkersPaid;
        }
        this.lastIncomePaid += (long)credited;
        return credited;
    }

    public int distributeServiceRevenue(Roster roster, Wallets wallets, RoomBlueprintImp blueprint, int amount) {
        return this.distributeServiceRevenue(roster, wallets, blueprint, amount, true);
    }

    public int distributeStateWage(Roster roster, Wallets wallets, RoomBlueprintImp blueprint, int amount) {
        return this.distributeServiceRevenue(roster, wallets, blueprint, amount, false);
    }

    private int distributeServiceRevenue(Roster roster, Wallets wallets, RoomBlueprintImp blueprint, int amount, boolean recordRevenue) {
        if (blueprint == null || amount <= 0) {
            return 0;
        }
        ArrayList<Humanoid> workers = new ArrayList<Humanoid>();
        for (int i = 0; i < roster.size(); ++i) {
            RoomInstance room;
            Humanoid worker = roster.get(i);
            if (worker.indu().clas() == HCLASSES.SLAVE() || (room = (RoomInstance)STATS.WORK().EMPLOYED.get(worker.indu())) == null || room.blueprintI() != blueprint) continue;
            workers.add(worker);
        }
        if (workers.isEmpty()) {
            return 0;
        }
        int[] shares = FirmEconomyKernel.split(amount, workers.size());
        int credited = 0;
        for (int i = 0; i < workers.size(); ++i) {
            if (shares[i] <= 0) continue;
            wallets.add((Humanoid)workers.get(i), shares[i]);
            credited += shares[i];
            ++this.lastWorkersPaid;
        }
        this.lastIncomePaid += (long)credited;
        if (recordRevenue && credited > 0) {
            this.recordServiceRevenue(blueprint, credited);
        }
        return credited;
    }

    private void size(RoomInstance room, FirmState state) {
        int minimum = Math.min(room.employees().max(), Math.max(0, EconConfig.minimumWorkersPerWorkplace));
        if (FirmEconomyKernel.shouldIdle(state.profit, EconConfig.firmSizingHysteresis)) {
            state.marketTarget = minimum;
            state.hill = new FirmEconomyKernel.HillState(minimum, 0.0, 1, true);
            EngineSeams.setFirmTarget(room, minimum);
            return;
        }
        int target = Math.max(minimum, state.marketTarget);
        if (Math.abs(room.employees().employed() - target) > 1) {
            return;
        }
        FirmEconomyKernel.HillState before = state.hill;
        int oldBest = before == null ? target : before.bestTarget();
        FirmEconomyKernel.HillResult result = FirmEconomyKernel.hillStep(before, target, state.profit, minimum, room.employees().max(), EconConfig.firmSizingHillclimbStep, EconConfig.firmSizingHysteresis);
        state.hill = result.state();
        if (target < oldBest && result.observedSlope() > 0.0) {
            state.marginal = result.observedSlope();
        } else if (result.observedSlope() != 0.0 && target > oldBest) {
            state.marginal = result.observedSlope();
        }
        state.marketTarget = result.nextTarget();
        EngineSeams.setFirmTarget(room, state.marketTarget);
    }

    static int initialMarketTarget(int employed, int maximum, int minimum) {
        if (maximum <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(maximum, Math.max(employed, minimum)));
    }

    private void recomputeBlueprintMarginals() {
        this.blueprints.clear();
        this.serviceRevenue.clear();
        for (Map.Entry<RoomInstance, FirmState> entry : this.firms.entrySet()) {
            RoomInstance room = entry.getKey();
            if (EconomicRoles.excludedFromMarketAccounting((RoomBlueprintImp)room.blueprintI())) continue;
            FirmState state = entry.getValue();
            BlueprintState aggregate = this.blueprints.computeIfAbsent(room.blueprintI().key, ignored -> new BlueprintState());
            int weight = Math.max(1, room.employees().employed());
            aggregate.profit += state.profit;
            aggregate.marginalNumerator += state.marginal * (double)weight;
            aggregate.marginalWeight += weight;
        }
        this.applyStateWageMarginals();
        for (BlueprintState state : this.blueprints.values()) {
            state.marginal = (state.marginalWeight == 0 ? 0.0 : state.marginalNumerator / (double)state.marginalWeight) + state.stateWageMarginal;
        }
    }

    private void applyStateWageMarginals() {
        for (Map.Entry<RoomBlueprintImp, Double> entry : this.stateWageMarginal.entrySet()) {
            BlueprintState aggregate = this.blueprints.computeIfAbsent(entry.getKey().key, ignored -> new BlueprintState());
            // Phase 5e: scale state-wage-marginal by the per-blueprint opMode average.
            // Per-room PAUSED → 0.0×, MOTHBALLED → 0.3×, PRODUCE → 1.0×. Non-state-funded
            // blueprints bleiben bei 1.0× (vanilla). Mehrere Räume desselben Blueprints
            // werden gemittelt.
            aggregate.stateWageMarginal += entry.getValue().doubleValue()
                    * this.effectiveOpModeCostScale(entry.getKey());
        }
    }

    /**
     * Phase 5e: Per-Blueprint Cost-Faktor für state-wage-marginal accumulation.
     * 1.0 für PRODUCE-Mode, 0.0 für ausschließlich PAUSED, mothballOperatingCostMultiplier
     * für MOTHBALLED. Mittelwert über alle Räume dieses Blueprints. Non-state-funded
     * blueprints: immer 1.0.
     */
    private double effectiveOpModeCostScale(RoomBlueprintImp blueprint) {
        if (blueprint == null || !EconomicRoles.stateFundedPublicWorks(blueprint)) {
            return 1.0;
        }
        int total = 0;
        double scaleSum = 0.0;
        for (Map.Entry<RoomInstance, FirmState> entry : this.firms.entrySet()) {
            RoomInstance roomInstance = entry.getKey();
            if (roomInstance == null || roomInstance.blueprintI() != blueprint) continue;
            ++total;
            EconConfig.RoomOperatingMode mode = opModes.getOrDefault(roomInstance, EconConfig.RoomOperatingMode.PRODUCE);
            switch (mode) {
                case PRODUCE:
                    scaleSum += 1.0;
                    break;
                case PAUSED:
                    scaleSum += 0.0;
                    break;
                case MOTHBALLED:
                    scaleSum += EconConfig.mothballOperatingCostMultiplier;
                    break;
            }
        }
        return total == 0 ? 1.0 : scaleSum / (double)total;
    }

    private void recomputeMeanMarginal() {
        double total = 0.0;
        int count = 0;
        for (BlueprintState state : this.blueprints.values()) {
            if (!(state.marginal > 0.0) || !Double.isFinite(state.marginal)) continue;
            total += state.marginal;
            ++count;
        }
        this.meanPositiveMarginal = count == 0 ? 0.0 : total / (double)count;
    }

    /**
     * Schreibt pro Tick eine Zeile pro Carpenter-Firma (oder Firma mit Möbel/Chair/Table-Output)
     * nach {@code ~/.local/share/songsofsyx/mods/SyxEconomyMod/diagnostics/furniture_debug.csv}.
     * Diagnostiziert ob employed_target > 0 ist und ob das out-Resource korrekt gemappt ist.
     * Aktiviert via {@link EconConfig#debugFurnitureDump}.
     */
    private void dumpFurnitureDebug(FlowMeter meter, FlowPrices prices, int ticks, double gameSeconds) {
        if (meter == null) return;
        int throttle = Math.max(1, EconConfig.debugFurnitureDumpEveryTicks);
        if (this.lastFurnitureDumpTick >= 0 && ticks - this.lastFurnitureDumpTick < throttle) return;
        this.lastFurnitureDumpTick = ticks;
        Map<RoomInstance, FlowMeter.FirmSnapshot> snapsByRoom = new IdentityHashMap<>();
        for (FlowMeter.FirmSnapshot s : meter.firmSnapshots()) {
            snapsByRoom.put(s.room(), s);
        }
        Path path = furnitureDumpPath();
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            boolean writeHeader = !Files.exists(path);
            try (BufferedWriter w = Files.newBufferedWriter(path,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (writeHeader) {
                    w.write("tick;day;bp_key;employed;max;hardTarget;marketTarget;physicalSeen;"
                            + "profit;marginal;incomeCarry;out_count;out0_name;out0_per_day;out0_producedDelta;"
                            + "in_count;in0_name;in0_per_day;in0_consumedDelta;note\n");
                }
                for (Map.Entry<RoomInstance, FirmState> entry : this.firms.entrySet()) {
                    RoomInstance room = entry.getKey();
                    if (room == null || !room.exists() || room.employees() == null) continue;
                    RoomBlueprintImp bp = room.blueprintI();
                    if (bp == null) continue;
                    if (EconomicRoles.excludedFromMarketAccounting(bp)) continue;
                    FlowMeter.FirmSnapshot snap = snapsByRoom.get(room);
                    if (snap == null || snap.outputCount() == 0) continue;
                    RESOURCE out = snap.outputResource(0);
                    if (out == null) continue;
                    String outKey = out.key != null ? out.key.toUpperCase(Locale.ROOT) : "?";
                    String bpKey = bp.key != null ? bp.key.toUpperCase(Locale.ROOT) : "?";
                    boolean isFurniture = outKey.contains("MOEBEL") || outKey.contains("FURNITURE")
                            || outKey.contains("CHAIR") || outKey.contains("STUHL")
                            || outKey.contains("TISCH") || outKey.contains("TABLE")
                            || bpKey.contains("ZIMMER") || bpKey.contains("CARPENT");
                    if (!isFurniture) continue;
                    FirmState s = entry.getValue();
                    int employed = room.employees().employed();
                    int maxEmp = room.employees().max();
                    int hardTarget = room.employees().hardTarget();
                    int marketTarget = s.targetInitialized ? s.marketTarget : 0;
                    double day = gameSeconds / Math.max(1.0, TIME.secondsPerDay());
                    String inKey = "";
                    double inRate = 0.0;
                    int inConsumed = 0;
                    if (snap.inputCount() > 0 && snap.inputResource(0) != null) {
                        inKey = snap.inputResource(0).key != null ? snap.inputResource(0).key : "";
                        inRate = snap.inputPerDay(0);
                        inConsumed = snap.consumedSinceLastSample(0);
                    }
                    int producedDelta = snap.producedSinceLastSample(0);
                    String note;
                    if (hardTarget == 0 || marketTarget == 0) {
                        note = "TARGET-ZERO";
                    } else if (s.profit <= 0.0) {
                        note = "PROFIT-NEGATIVE";
                    } else if (producedDelta == 0 && day > 5.0) {
                        note = "OUT-STUCK";
                    } else {
                        note = "OK";
                    }
                    w.write(String.format(Locale.ROOT,
                            "%d;%.3f;%s;%d;%d;%d;%d;%s;%.2f;%.2f;%.2f;%d;%s;%.4f;%d;%d;%s;%.4f;%d;%s%n",
                            ticks, day, bp.key, employed, maxEmp, hardTarget, marketTarget,
                            s.physicalSeen ? "true" : "false",
                            s.profit, s.marginal, s.incomeCarry,
                            snap.outputCount(), out.key, snap.outputPerDay(0), producedDelta,
                            snap.inputCount(), inKey, inRate, inConsumed, note));
                }
            }
        } catch (IOException e) {
            System.err.println("[ECON] furniture debug dump failed: " + e.getMessage());
        }
    }

    /**
     * Cross-platform Pfad-Resolver für {@code furniture_debug.csv}.
     * Linux/Mac: {@code $HOME/.local/share/songsofsyx/mods/SyxEconomyMod/diagnostics/...}
     * Windows: {@code %APPDATA%\songsofsyx\mods\SyxEconomyMod\diagnostics\...}
     */
    private static Path furnitureDumpPath() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            if (appdata == null || appdata.isEmpty()) {
                appdata = System.getProperty("user.home") + "\\AppData\\Roaming";
            }
            return Paths.get(appdata, "songsofsyx", "mods", "SyxEconomyMod", "diagnostics", "furniture_debug.csv");
        }
        return Paths.get(System.getProperty("user.home"),
                ".local", "share", "songsofsyx", "mods", "SyxEconomyMod", "diagnostics", "furniture_debug.csv");
    }

    public void clear() {
        this.firms.clear();
        this.lastFurnitureDumpTick = -1;
        this.blueprints.clear();
        this.serviceRevenue.clear();
        this.stateWageMarginal.clear();
        this.lastSizingTick = -1073741824;
        this.lastIncomePaid = 0L;
        this.lastIncomeDue = 0L;
        this.lastWorkersUnpaid = 0;
        this.lastWorkersPaid = 0;
        this.meanPositiveMarginal = 0.0;
    }

    private static final class BlueprintState {
        double profit;
        double marginalNumerator;
        int marginalWeight;
        double stateWageMarginal;
        double marginal;

        private BlueprintState() {
        }
    }

    private static final class FirmState {
        boolean physicalSeen;
        boolean marketTracked;
        boolean cashTracked;
        boolean targetInitialized;
        int marketTarget;
        double physicalProfit;
        double pendingCash;
        double cashRate;
        double profit;
        double marginal;
        double incomeCarry;
        RESOURCE[] outputs = new RESOURCE[0];
        double[] outputRates = new double[0];
        FirmEconomyKernel.HillState hill;
        double totalOutputValue;
        double totalInputValue;
        int lastIncomeDueThisTick;
        int lastIncomePaidThisTick;
        int workersUnpaidThisTick;

        private FirmState() {
        }

        void setOutputs(FlowMeter.FirmSnapshot snapshot) {
            this.outputs = new RESOURCE[snapshot.outputCount()];
            this.outputRates = new double[this.outputs.length];
            for (int i = 0; i < this.outputs.length; ++i) {
                this.outputs[i] = snapshot.outputResource(i);
                this.outputRates[i] = Math.max(0.0, snapshot.outputPerDay(i));
            }
        }
    }

    /**
     * Per-Firm financial snapshot for diagnostic CSV export.
     * Captures one tick of income distribution — shows which blueprints
     * are structurally unprofitable (profitPerDay ≤ 0) and which have
     * workers going unpaid despite positive book profit.
     * <p>
     * totalOutputValue and totalInputValue come from FlowMeter.firmSnapshots()
     * valued at current market prices via FlowPrices — these are the raw
     * throughput numbers that determine physical profitability.</p>
     */
    public record FirmFinancialSnapshot(
            String blueprint,
            int employees,
            int employedTarget,
            double profitPerDay,
            double marginalPerWorker,
            double incomeCarry,
            double totalOutputValuePerDay,
            double totalInputValuePerDay,
            int lastIncomeDue,
            int lastIncomePaid,
            int workersUnpaid
    ) {}

    /**
     * Returns per-firm financial data for diagnostic CSV export.
     * Only includes firms that have been physically sampled this tick
     * and are not excluded from market accounting.
     */
    public List<FirmFinancialSnapshot> firmFinancialSnapshots() {
        List<FirmFinancialSnapshot> result = new ArrayList<>(this.firms.size());
        for (Map.Entry<RoomInstance, FirmState> entry : this.firms.entrySet()) {
            RoomInstance room = entry.getKey();
            FirmState state = entry.getValue();
            if (room == null || !room.exists() || room.employees() == null) continue;
            if (EconomicRoles.excludedFromMarketAccounting((RoomBlueprintImp) room.blueprintI())) continue;
            if (!state.physicalSeen) continue;
            result.add(new FirmFinancialSnapshot(
                    room.blueprintI().key,
                    room.employees().employed(),
                    state.targetInitialized ? state.marketTarget : 0,
                    state.profit,
                    state.marginal,
                    state.incomeCarry,
                    state.totalOutputValue,
                    state.totalInputValue,
                    state.lastIncomeDueThisTick,
                    state.lastIncomePaidThisTick,
                    state.workersUnpaidThisTick
            ));
        }
        return Collections.unmodifiableList(result);
    }

    public record UpdateResult(long paid) {
    }
}

