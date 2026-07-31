package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.time.TIME;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import settlement.entity.humanoid.Humanoid;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomInstance;
import settlement.stats.Induvidual;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.rnd.RND;
import vannon.syx.economy.adapter.AdapterDispatcher;
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.ISyxAI;
import vannon.syx.economy.adapter.ISyxNpc;
import vannon.syx.economy.adapter.ISyxBoosting;
import vannon.syx.economy.adapter.ISyxDiplomacy;
import vannon.syx.economy.adapter.ISyxTransport;
import vannon.syx.economy.adapter.ISyxWarehouse;

/**
 * Main economy simulation class. After Spluck-TECHD-01 extraction,
 * delegates heavy logic to:
 * <ul>
 *   <li>{@link EconomySaveLoad} — save/load, TAG constants</li>
 *   <li>{@link EconomyAuditEngine} — audit, demography, heir-finding, opinion monitoring, flow prices</li>
 *   <li>{@link EconomyTickOrchestrator} — update() phases 7-11</li>
 * </ul>
 *
 * Fields are package-private so extracted classes in the same package can access them directly.
 */
public final class EconomySim {

    /** Save-format version constant (delegated to EconomySaveLoad for internals). */
    public static final int CHUNKED_VERSION = EconomySaveLoad.CHUNKED_VERSION;
    static final int ECON_INDICATOR_INTERVAL = 60;

    // ── Subsystem fields (package-private for extracted class access) ──

    final Wallets wallets = new Wallets();
    final Roster roster = new Roster();
    final RandomPairSource randomPairs = new RandomPairSource();
    final ProximityPairSource proximityPairs = new ProximityPairSource();
    final Histogram histogram = new Histogram();
    final WealthStats stats = new WealthStats();
    final Wages wages = new Wages();
    final FirmLedger firmLedger = new FirmLedger();
    final WorkplaceDefaults workplaceDefaults = new WorkplaceDefaults();
    final MaintenanceMarket maintenanceMarket = new MaintenanceMarket();
    final LaborMarket laborMarket = new LaborMarket();
    final Taxes taxes = new Taxes();
    final Purchases purchases = new Purchases();
    final GrainDole grainDole = new GrainDole();
    final Fiscal fiscal = new Fiscal();
    final ReligionMarket religionMarket = new ReligionMarket();
    final Liturgy liturgy = new Liturgy();
    final CorveeController corveeController = new CorveeController();
    final DebtBondage debtBondage = new DebtBondage();
    final OddjobMarket oddjobMarket = new OddjobMarket();
    final MilitaryPayroll militaryPayroll = new MilitaryPayroll();
    final StateWageMarket stateWages = new StateWageMarket();
    final ISyxTransport transportAdapter;
    final TransportMarket transportMarket;
    final HandoutRelief handoutRelief = new HandoutRelief();
    final ProductionSubsidies productionSubsidies = new ProductionSubsidies();
    final FlowMeter flowMeter = new FlowMeter();
    final FlowPrices flowPrices = new FlowPrices();
    final vannon.syx.economy.core.io.IOGraph ioGraph = new vannon.syx.economy.core.io.IOGraph();
    final vannon.syx.economy.core.io.IOMatrix ioMatrix = new vannon.syx.economy.core.io.IOMatrix(0); // lazy-resized on first compute
    final EconTutorialController tutorial = new EconTutorialController();
    final ScarcitySignal scarcitySignal = new ScarcitySignal();
    final ISyxWarehouse warehouseAdapter;
    final StateWarehouses stateWarehouses;
    final ISyxAI aiAdapter;
    final EconIndicators econIndicators = new EconIndicators();
    final AccessAutomation accessAutomation = new AccessAutomation();
    final RenderCaches renderCaches = new RenderCaches();
    final EconProgression progression;
    final ISyxBoosting boostingAdapter;
    final ISyxDiplomacy diplomacyAdapter;
    ISyxNpc npcAdapter;
    final DebtDiplomacyBuffer debtDiplomacyBuffer;
    int econIndicatorTickCounter = 0;
    final ConstructionHoardController constructionHoardController;
    final WarehouseMarket warehouseMarket;
    final Escrow escrow = new Escrow(this.wallets);
    final CrimeTheftConsumer theftConsumer = new CrimeTheftConsumer(this.wallets);
    final AffordabilityGate affordabilityGate = new AffordabilityGate(this.escrow, this.flowPrices, this.grainDole);
    final FoodPlanController foodPlanController;
    final PurchasePlanController purchasePlanController;
    final ServiceMarket serviceMarket = new ServiceMarket();
    final ServicePlanController servicePlanController;
    final HousingMarket housingMarket = new HousingMarket();
    final ForeignTradeLedger foreignTradeLedger = new ForeignTradeLedger();
    private static volatile EconomySim active = null;
    int ticks = 0;
    final ReentryGuard updateGuard = new ReentryGuard("EconomySim.update()");
    final SimpleHistory treasuryHistory = new SimpleHistory(60);
    final SimpleHistory giniHistory = new SimpleHistory(60);
    double encounterCarry = 0.0;
    long seedSupply = 0L;
    long imported = 0L;
    long exported = 0L;
    long escheated = 0L;
    long guildIncomePaid = 0L;
    long taxesCollected = 0L;
    long spent = 0L;
    long religionTaxCollected = 0L;
    long liturgyCollected = 0L;
    long warehouseTaxCollected = 0L;
    long wagesPaid = 0L;
    long housingRentCollected = 0L;
    final PropertyMarketController propertyMarket;
    long roundingDrift = 0L;
    int deaths = 0;
    int emigrations = 0;
    int inherited = 0;
    int heirless = 0;
    long reportedAuditDelta = 0L;
    int lastTaxSeason = -1;
    int starvationRiskCount = 0;
    public int starvationRiskCount() { return this.starvationRiskCount; }
    final PairSource.PairConsumer exchange = (a, b) -> {
        if ((double) RND.rFloat() >= EconConfig.alpha) return;
        int ma = this.wallets.spendable(a);
        int mb = this.wallets.spendable(b);
        int newA = ExchangeKernel.yardSale(ma, mb, this.wallets.lambda(a), this.wallets.lambda(b), RND.rFloat());
        this.wallets.applyExchange(a, b, newA);
    };

    public EconTutorialController tutorial() { return tutorial; }

    // ── Public accessors ───────────────────────────────────────────

    public FlowMeter flowMeter() { return this.flowMeter; }
    public FlowPrices flowPrices() { return this.flowPrices; }
    public vannon.syx.economy.core.io.IOGraph ioGraph() { return this.ioGraph; }
    public vannon.syx.economy.core.io.IOMatrix ioMatrix() { return this.ioMatrix; }
    public GrainDole grainDole() { return this.grainDole; }
    public Purchases purchases() { return this.purchases; }
    public AffordabilityGate affordabilityGate() { return this.affordabilityGate; }
    public Wages wages() { return this.wages; }
    public FirmLedger firmLedger() { return this.firmLedger; }
    public WarehouseMarket warehouseMarket() { return this.warehouseMarket; }
    public StateWarehouses stateWarehouses() { return this.stateWarehouses; }
    public LaborMarket laborMarket() { return this.laborMarket; }
    public ScarcitySignal scarcitySignal() { return this.scarcitySignal; }
    public Taxes taxes() { return this.taxes; }
    public Fiscal fiscal() { return this.fiscal; }
    public ReligionMarket religionMarket() { return this.religionMarket; }
    public Liturgy liturgy() { return this.liturgy; }
    public long religionTaxCollected() { return this.religionTaxCollected; }
    public long liturgyCollected() { return this.liturgyCollected; }
    public double corveeDraftFractionLast() { return this.corveeController.lastDraftFraction(); }
    public DebtBondage debtBondage() { return this.debtBondage; }
    public OddjobMarket oddjobMarket() { return this.oddjobMarket; }
    public MilitaryPayroll militaryPayroll() { return this.militaryPayroll; }
    public StateWageMarket stateWages() { return this.stateWages; }
    public TransportMarket transportMarket() { return this.transportMarket; }
    public ProductionSubsidies productionSubsidies() { return this.productionSubsidies; }

    public double taxPain(Induvidual indu) {
        double effectiveRate = this.wallets.lastTaxRate(indu);
        double ref = EconConfig.taxPainReference;
        double free = EconConfig.taxPainFreeRate;
        if (ref <= free) return 0.0;
        double pain = (effectiveRate - free) / (ref - free);
        return Math.max(0.0, Math.min(1.0, pain));
    }

    public int ticks() { return this.ticks; }
    public long treasury() { return (long) Math.floor(FACTIONS.player().credits().credits()); }
    public Roster roster() { return this.roster; }
    public Wallets wallets() { return this.wallets; }
    public Humanoid cachedRichestCitizen() { return this.renderCaches.cachedRichestCitizen(); }
    public List<StockpileInstance> cachedStateWarehouses() { return this.renderCaches.cachedStateWarehouses(); }
    public List<RoomBlueprintImp> cachedWorkplaces() { return this.renderCaches.cachedWorkplaces(); }
    public List<RESOURCE> cachedAllResources() { return this.renderCaches.cachedAllResources(); }
    public static EconomySim active() { return active; }
    public EconIndicators econIndicators() { return this.econIndicators; }
    public EconProgression progression() { return this.progression; }
    public DebtDiplomacyBuffer debtDiplomacyBuffer() { return this.debtDiplomacyBuffer; }
    public ISyxAI aiAdapter() { return this.aiAdapter; }
    public HousingMarket housingMarket() { return this.housingMarket; }
    public ForeignTradeLedger foreignTradeLedger() { return this.foreignTradeLedger; }
    public long propertySalesCollected() { return this.propertyMarket.salesCollected(); }
    public long propertyDividendsPaid() { return this.propertyMarket.dividendsPaid(); }
    public SimpleHistory treasuryHistory() { return this.treasuryHistory; }
    public SimpleHistory giniHistory() { return this.giniHistory; }
    public WealthStats stats() { return this.stats; }

    // Delegated scalar accessors
    public long seedSupply() { return this.seedSupply; }
    public long imported() { return this.imported; }
    public long exported() { return this.exported; }
    public long escheated() { return this.escheated; }
    public long wagesPaid() { return this.wagesPaid; }
    public long guildIncomePaid() { return this.guildIncomePaid; }
    public long taxesCollected() { return this.taxesCollected; }
    public long spent() { return this.spent; }
    public long marketReceipts() { return this.fiscal.marketReceipts(); }
    public long headTaxCollected() { return this.fiscal.headTaxCollected(); }
    public long rationOut() { return this.fiscal.rationOut(); }
    public long roundingDrift() { return this.roundingDrift; }
    public int deaths() { return this.deaths; }
    public int emigrations() { return this.emigrations; }
    public int inherited() { return this.inherited; }
    public int heirless() { return this.heirless; }

    public double relativeWealth(Induvidual indu) {
        int m = this.wallets.moneyOf(indu);
        if (m < 0) return 0.5;
        int med = this.stats.median;
        if (med <= 0) return 0.5;
        double rel = (double) m / (EconConfig.relativeWealthMedians * (double) med);
        return Math.max(0.0, Math.min(1.0, rel));
    }

    // ── Debug / Cheat API ── extracted to EconomyDebugTools (Sprint E1)

    // ── Debug / Cheat API ── extracted to EconomyDebugTools (Sprint E1)

    // ── Constructors ───────────────────────────────────────────────

    public EconomySim() {
        this(AdapterDispatcher.build(), true);
    }

    private EconomySim(AdapterDispatcher.AdapterBundle bundle, boolean productionMode) {
        this(bundle.transport, bundle.warehouse, bundle.boosting,
             bundle.diplomacy, bundle.ai, productionMode);
        this.npcAdapter = bundle.npc;
    }

    EconomySim(ISyxTransport transportAdapter, ISyxWarehouse warehouseAdapter,
               ISyxBoosting boostingAdapter, ISyxDiplomacy diplomacyAdapter,
               ISyxAI aiAdapter) {
        this(transportAdapter, warehouseAdapter, boostingAdapter, diplomacyAdapter, aiAdapter, false);
    }

    private EconomySim(ISyxTransport transportAdapter, ISyxWarehouse warehouseAdapter,
                        ISyxBoosting boostingAdapter, ISyxDiplomacy diplomacyAdapter,
                        ISyxAI aiAdapter, boolean productionMode) {
        TreasuryCrisis.reset();
        AccessAutomation.reset();
        LocalPrices.reset();
        OddjobAutomation.reset();
        WarehouseAutomation.reset();
        GiniConsequences.reset();
        CitizenClass.reset();
        active = this;
        this.transportAdapter = transportAdapter;
        this.transportMarket = new TransportMarket(transportAdapter);
        this.warehouseAdapter = warehouseAdapter;
        this.stateWarehouses = new StateWarehouses(warehouseAdapter);
        this.constructionHoardController = productionMode ? new ConstructionHoardController(this.stateWarehouses) : null;
        this.warehouseMarket = new WarehouseMarket(this.stateWarehouses, this.flowPrices);
        this.firmLedger.setStateWarehouses(this.stateWarehouses);
        this.affordabilityGate.setSettlementSink(new AffordabilityGate.SettlementSink(){
            @Override
            public void purchase(Humanoid buyer, int[] resources, int gross, AffordabilityGate.Kind kind, RoomInstance seller) {
                EconomySim.this.fiscal.settlePurchase(buyer, resources, gross, kind, seller, EconomySim.this.roster, EconomySim.this.wallets, EconomySim.this.firmLedger, EconomySim.this.warehouseMarket);
            }
            @Override
            public void ration(Humanoid diner, int[] resources, int marketValue, RoomInstance seller) {
                EconomySim.this.fiscal.settleRation(diner, resources, marketValue, seller, EconomySim.this.roster, EconomySim.this.wallets, EconomySim.this.firmLedger, EconomySim.this.warehouseMarket);
            }
        });
        this.boostingAdapter = boostingAdapter;
        this.progression = new EconProgression(boostingAdapter);
        this.diplomacyAdapter = diplomacyAdapter;
        this.debtDiplomacyBuffer = new DebtDiplomacyBuffer(diplomacyAdapter);
        this.debtDiplomacyBuffer.update();
        this.aiAdapter = aiAdapter;
        this.purchasePlanController = productionMode ? new PurchasePlanController(this.affordabilityGate, aiAdapter) : null;
        this.foodPlanController = productionMode ? new FoodPlanController(this.affordabilityGate) : null;
        this.servicePlanController = productionMode ? new ServicePlanController(this.serviceMarket, this.fiscal, this.firmLedger) : null;
        this.propertyMarket = new PropertyMarketController(
            this.housingMarket, this.firmLedger, this.wallets, this.roster);
    }

    static void clearActive() {
        TreasuryCrisis.reset();
        AccessAutomation.reset();
        LocalPrices.reset();
        OddjobAutomation.reset();
        WarehouseAutomation.reset();
        GiniConsequences.reset();
        CitizenClass.reset();
        active = null;
    }

    // ── Main update loop (skeleton) ────────────────────────────────

    public void update(double ds) {
        tutorial.update(ds);
        EconConfig.setPopulation((int) Math.min(Integer.MAX_VALUE, EconomyAuditEngine.totalLiving(this)));
        if (!this.updateGuard.tryEnter()) return;
        try {
            this.debtDiplomacyBuffer.update();
            // Sprint v0.13.119+B-008-Phase-2: EngineSeams.entitiesAvailable()-Fallback entfernt.
            // Production-Kontext: AdapterDispatcher.build() initialisiert EngineMirror.api()
            // im selben EconomySim-Constructor; api() ist vor update() garantiert != null und
            // isFullyAvailable() == true (alle 7 Sub-Interfaces OK).
            // Test-Kontext: bei `new EconomySim(mock, mock, ...)` ohne AdapterDispatcher ist
            // api() == null → early-return statt NPE (Identisch zur alten Ternary-Semantik).
            // Strikt-Equivalent zur alten `rooms().entitiesAvailable()`-Prüfung + null-safety.
            EngineMirror m = EngineMirror.api();
            if (m == null || !m.isFullyAvailable()) return;
            if (ds <= 0.0) return;
            this.roster.rebuild();
            this.wallets.clearPaidThisTick();
            this.wallets.updateFatigue();
            if (this.roster.size() < 2) {
                this.renderCaches.update(this.roster, this.wallets, this.stateWarehouses);
                return;
            }
            ++this.ticks;

            // Treasury crisis dispatch
            CrisisDispatch.update(this.treasury(), this);
            this.workplaceDefaults.update();

            // ── Phases 1-6: Warehouses, flow, pricing, seeding ─────
            this.warehouseMarket.beginTick();
            this.stateWarehouses.beginTick();
            int[] constructionWithdrawals = this.warehouseMarket.observeConstructionWithdrawals();
            int[] stateConstructionWithdrawals = this.stateWarehouses.matchConstructionDeliveries(constructionWithdrawals);
            int[] exportWithdrawals = this.warehouseMarket.observeExportWithdrawals();
            this.flowMeter.sample(ds, EconConfig.flowSmoothingDays, this.stateWarehouses.withheldStock(stateConstructionWithdrawals), constructionWithdrawals);
            if (!this.ioGraph.isBuilt()) { this.ioGraph.build(); }
            // IO-Phase 2: Empirische IO-Matrix einmal pro Ingame-Tag berechnen.
            // Nutzt den gleichen Day-Boundary-Check wie der Rest des update()-Loops
            // (this.ticks % DEFAULT_TICKS_PER_DAY == 0), da ticks ein Frame-Counter
            // ist und KEIN Sekunden-Counter.
            if (this.ioGraph.isBuilt() && this.flowMeter.snapshot() != null
                    && this.ticks > 0 && this.ticks % (int) EconConfig.DEFAULT_TICKS_PER_DAY == 0) {
                if (this.ioMatrix.size() == 0) {
                    this.ioMatrix.resize(init.resources.RESOURCES.ALL().size());
                }
                this.ioMatrix.compute(this.flowMeter, this.ioGraph);
            }
            this.warehouseMarket.recordProducerlessOutput(this.flowMeter);
            if (EconConfig.stateWarehousesEnabled) {
                WarehouseAutomation.autoTune(this.stateWarehouses, this.flowPrices,
                    this.flowMeter.snapshot(), constructionWithdrawals, this.treasury());
            }
            if (EconConfig.flowPricingEnabled) {
                int refresh = Math.max(1, (int)(EconConfig.flowPriceRefreshDays * TIME.secondsPerDay()));
                if (!this.flowPrices.ready() || this.ticks % refresh == 0) {
                    EconomyAuditEngine.refreshFlowPrices(this);
                }
            }
            boolean seeding = !this.wallets.isSeeded();
            for (int i = 0; i < this.roster.size(); ++i) {
                int minted = this.wallets.touch(this.roster.get(i), this.ticks);
                if (seeding) { this.seedSupply += (long) minted; continue; }
                this.imported += (long) minted;
            }
            if (seeding) {
                this.wallets.markSeeded();
                EconomyAuditEngine.seedTreasury();
                EconomyAuditEngine.logSeed(this);
            }
            this.wallets.sweepDepartures(this.ticks,
                (estate, relRef, emigrated) -> EconomyAuditEngine.onDeparture(this, estate, relRef, emigrated));
            this.grainDole.update(this.roster, this.wallets);
            this.warehouseMarket.prune(this.roster);
            this.warehouseMarket.observeRetailDeliveries();
            this.foodPlanController.update(this.roster);
            if (this.purchasePlanController != null) this.purchasePlanController.update(this.roster);
            this.serviceMarket.refresh();
            this.servicePlanController.update(this.roster, this.wallets);
            if (EconConfig.constructionHoardingEnabled && this.constructionHoardController != null) {
                this.constructionHoardController.update(this.roster);
            }
            this.stateWarehouses.prune();
            this.warehouseMarket.beginPurchases();
            this.fiscal.settleCrownWholesale(this.warehouseMarket.buyCheaperCrownGoods(this.roster, this.wallets));
            this.warehouseMarket.buy(this.flowMeter, this.flowPrices, this.roster, this.wallets, this.firmLedger);
            this.guildIncomePaid += this.stateWarehouses.lastBought();
            this.guildIncomePaid += this.warehouseMarket.buyConstructionMaterials(constructionWithdrawals, stateConstructionWithdrawals, this.roster, this.wallets, this.firmLedger);
            this.guildIncomePaid += this.warehouseMarket.buyExports(exportWithdrawals, this.roster, this.wallets, this.firmLedger);
            this.warehouseMarket.settleSeizures(this.roster, this.wallets);
            WarehouseMarket.Settlement b2b = this.warehouseMarket.sellInputs(this.flowMeter, this.flowPrices, this.roster, this.wallets, this.firmLedger);
            this.fiscal.settleMerchantRemainder((int) Math.min(Integer.MAX_VALUE, Math.max(0L, b2b.billed() - b2b.credited())));
            this.fiscal.settleCrownWholesale(this.warehouseMarket.buyRemainingCrownGoods(this.roster, this.wallets));
            EconomyAuditEngine.updateDemography(this);

            // ── Phases 7-11: Delegated to tick orchestrator ─────────
            EconomyTickOrchestrator.tickPhases7To11(this, ds);
        } finally {
            this.updateGuard.exit();
        }
    }

    // ── Delegated methods ──────────────────────────────────────────

    public long auditDelta() { return EconomyAuditEngine.auditDelta(this); }

    // ── Reset ── delegated to EconomySaveLoad.resetAll() (Sprint E2)

    // ── Save / Load (delegated) ────────────────────────────────────

    public void save(FilePutter file) {
        EconomySaveLoad.save(this, file);
    }

    public void load(FileGetter file) throws IOException {
        EconomySaveLoad.load(this, file);
    }
}
