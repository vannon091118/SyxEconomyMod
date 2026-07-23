package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.time.TIME;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import snake2d.util.sets.LIST;
import settlement.stats.Induvidual;
import settlement.stats.STATS;
import snake2d.LOG;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.rnd.RND;
import vannon.syx.economy.core.AffordabilityGate;
import vannon.syx.economy.core.AuditKernel;
import vannon.syx.economy.core.ConstructionHoardController;
import vannon.syx.economy.core.CorveeController;
import vannon.syx.economy.core.DebtBondage;
import vannon.syx.economy.core.DebtDiplomacyBuffer;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.Escrow;
import vannon.syx.economy.core.ExchangeKernel;
import vannon.syx.economy.core.FirmLedger;
import vannon.syx.economy.core.Fiscal;
import vannon.syx.economy.core.FlowMeter;
import vannon.syx.economy.core.FlowPrices;
import vannon.syx.economy.core.FoodPlanController;
import vannon.syx.economy.core.GrainDole;
import vannon.syx.economy.core.HandoutRelief;
import vannon.syx.economy.core.Histogram;
import vannon.syx.economy.core.LaborMarket;
import vannon.syx.economy.core.Liturgy;
import vannon.syx.economy.core.LocalPrices;
import vannon.syx.economy.core.MaintenanceMarket;
import vannon.syx.economy.core.MilitaryPayroll;
import vannon.syx.economy.core.OddjobMarket;
import vannon.syx.economy.core.PairSource;
import vannon.syx.economy.core.PolityPriceAnchor;
import vannon.syx.economy.core.ProductionSubsidies;
import vannon.syx.economy.core.ProximityPairSource;
import vannon.syx.economy.core.PurchasePlanController;
import vannon.syx.economy.core.Purchases;
import vannon.syx.economy.core.RandomPairSource;
import vannon.syx.economy.core.ReligionMarket;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.ServiceMarket;
import vannon.syx.economy.core.ServicePlanController;
import vannon.syx.economy.core.StateWageMarket;
import vannon.syx.economy.adapter.FallbackBoostingAdapter;
import vannon.syx.economy.adapter.FallbackDiplomacyAdapter;
import vannon.syx.economy.adapter.FallbackTransportAdapter;
import vannon.syx.economy.adapter.FallbackWarehouseAdapter;
import vannon.syx.economy.adapter.ISyxAI;
import vannon.syx.economy.adapter.ISyxBoosting;
import vannon.syx.economy.adapter.ISyxDiplomacy;
import vannon.syx.economy.adapter.ISyxTransport;
import vannon.syx.economy.adapter.ISyxWarehouse;
import vannon.syx.economy.adapter.VanillaAIAdapter;
import vannon.syx.economy.adapter.VanillaBoostingAdapter;
import vannon.syx.economy.adapter.VanillaDiplomacyAdapter;
import vannon.syx.economy.adapter.VanillaDiplomacyAdapterMH;
import vannon.syx.economy.adapter.VanillaTransportAdapter;
import vannon.syx.economy.adapter.VanillaTransportAdapterMH;
import vannon.syx.economy.adapter.VanillaWarehouseAdapter;
import vannon.syx.economy.adapter.VanillaWarehouseAdapterMH;
import vannon.syx.economy.core.StateWarehouses;
import vannon.syx.economy.core.Taxes;
import vannon.syx.economy.core.TransportMarket;
import vannon.syx.economy.core.Wages;
import vannon.syx.economy.core.Wallets;
import vannon.syx.economy.core.WarehouseMarket;
import vannon.syx.economy.core.WealthStats;
import vannon.syx.economy.core.WorkplaceDefaults;

public final class EconomySim {
    // Save-format version that enables the chunked layout below.
    // v33: Stage.INDUSTRIE eingefügt; EconProgression schreibt jetzt Version-Header.
    public static final int CHUNKED_VERSION = 33;

    // Magic prefix that immediately precedes the first chunk. Helps detect corruption
    // and distinguishes a chunked save from a legacy ordered stream.
    private static final int CHUNK_MAGIC = 0xEC0FEC0F;

    // Chunk tags for the new save format. Tags are arbitrary positive ints.
    private static final int TAG_CORE_SCALARS = 1;
    private static final int TAG_ECON_CONFIG = 2;
    private static final int TAG_WAGES = 3;
    private static final int TAG_TAXES = 4;
    private static final int TAG_FISCAL = 5;
    private static final int TAG_LABOR_MARKET = 6;
    private static final int TAG_MAINTENANCE_MARKET = 7;
    private static final int TAG_GRAIN_DOLE = 8;
    private static final int TAG_RELIGION_MARKET = 9;
    private static final int TAG_LITURGY = 10;
    private static final int TAG_DEBT_BONDAGE = 11;
    private static final int TAG_MILITARY_PAYROLL = 12;
    private static final int TAG_PRODUCTION_SUBSIDIES = 13;
    private static final int TAG_STATE_WAREHOUSES = 14;
    private static final int TAG_WAREHOUSE_MARKET = 15;
    private static final int TAG_STATE_WAGES = 16;
    private static final int TAG_PROGRESSION = 17;
    private static final int TAG_CORVEE = 18;
    private static final int TAG_HOUSING = 19;
    private static final int TAG_FOREIGN_TRADE_LEDGER = 20;
    private static final int TAG_END = 0x7FFFFFFF;

    private final Wallets wallets = new Wallets();
    private final Roster roster = new Roster();
    private final RandomPairSource randomPairs = new RandomPairSource();
    private final ProximityPairSource proximityPairs = new ProximityPairSource();
    private final Histogram histogram = new Histogram();
    private final WealthStats stats = new WealthStats();
    private final Wages wages = new Wages();
    private final FirmLedger firmLedger = new FirmLedger();
    private final WorkplaceDefaults workplaceDefaults = new WorkplaceDefaults();
    private final MaintenanceMarket maintenanceMarket = new MaintenanceMarket();
    // DebtDiplomacyBuffer & EconProgression: werden im Konstruktor mit Adaptern
    // gebaut — Field-Initializer kann das nicht, weil Adapter davor erzeugt werden
    // müssen. Field-Declarations unten.
    private final LaborMarket laborMarket = new LaborMarket();
    private final Taxes taxes = new Taxes();
    private final Purchases purchases = new Purchases();
    private final GrainDole grainDole = new GrainDole();
    private final Fiscal fiscal = new Fiscal();
    private final ReligionMarket religionMarket = new ReligionMarket();
    private final Liturgy liturgy = new Liturgy();
    private final CorveeController corveeController = new CorveeController();
    private final DebtBondage debtBondage = new DebtBondage();
    private final OddjobMarket oddjobMarket = new OddjobMarket();
    private final MilitaryPayroll militaryPayroll = new MilitaryPayroll();
    private final StateWageMarket stateWages = new StateWageMarket();
    private final ISyxTransport transportAdapter;
    private final TransportMarket transportMarket;
    private final HandoutRelief handoutRelief = new HandoutRelief();
    private final ProductionSubsidies productionSubsidies = new ProductionSubsidies();
    private final FlowMeter flowMeter = new FlowMeter();
    private final FlowPrices flowPrices = new FlowPrices();
    private final ScarcitySignal scarcitySignal = new ScarcitySignal();
    private final ISyxWarehouse warehouseAdapter;
    private final StateWarehouses stateWarehouses;
    private final ISyxAI aiAdapter;
    private final EconIndicators econIndicators = new EconIndicators();
    private final AccessAutomation accessAutomation = new AccessAutomation();
    // Phase 4.4 + 4.5: EconProgression + DebtDiplomacyBuffer werden mit Adaptern
    // konstruiert. Field-Init kann das nicht — also constructor-assigned nach Aufbau
    // der Adapter (gleiche Workaround-Technik wie für ConstructionHoardController).
    private final EconProgression progression;
    private final ISyxBoosting boostingAdapter;
    private final ISyxDiplomacy diplomacyAdapter;
    private final DebtDiplomacyBuffer debtDiplomacyBuffer;
    private int econIndicatorTickCounter = 0;
    private static final int ECON_INDICATOR_INTERVAL = 60;
    // Phase 4: stateWarehouses is built in the constructor after the warehouse
    // adapter is initialized. These two consumers are therefore constructed in
    // the constructor body, not via field initializers, so the diamond reads
    // a fully-initialized stateWarehouses reference.
    private final ConstructionHoardController constructionHoardController;
    private final WarehouseMarket warehouseMarket;
    private final Escrow escrow = new Escrow(this.wallets);
    private final AffordabilityGate affordabilityGate = new AffordabilityGate(this.escrow, this.flowPrices, this.grainDole);
    private final FoodPlanController foodPlanController = new FoodPlanController(this.affordabilityGate);
    private final PurchasePlanController purchasePlanController = new PurchasePlanController(this.affordabilityGate);
    private final ServiceMarket serviceMarket = new ServiceMarket();
    private final ServicePlanController servicePlanController = new ServicePlanController(this.serviceMarket, this.fiscal, this.firmLedger);
    private final HousingMarket housingMarket = new HousingMarket();
    private final ForeignTradeLedger foreignTradeLedger = new ForeignTradeLedger();
    private static volatile EconomySim active = null;
    private volatile Humanoid cachedRichestCitizen;
    private volatile List<StockpileInstance> cachedStateWarehouses = Collections.emptyList();
    private volatile List<RoomBlueprintImp> cachedWorkplaces = Collections.emptyList();
    private volatile List<RESOURCE> cachedAllResources = Collections.emptyList();
    private int ticks = 0;
    private long lastUpdateTick = -1L;
    private final SimpleHistory treasuryHistory = new SimpleHistory(60);
    private final SimpleHistory giniHistory = new SimpleHistory(60);
    private double encounterCarry = 0.0;
    private long seedSupply = 0L;
    private long imported = 0L;
    private long exported = 0L;
    private long escheated = 0L;
    private long guildIncomePaid = 0L;
    private long taxesCollected = 0L;
    private long spent = 0L;
    private long religionTaxCollected = 0L;
    private long liturgyCollected = 0L;
    private long warehouseTaxCollected = 0L;
    private long wagesPaid = 0L;
    private long housingRentCollected = 0L;
    private long propertySalesCollected = 0L;
    private long propertyDividendsPaid = 0L;
    private int lastPropertySeason = -1;
    private long roundingDrift = 0L;
    private int deaths = 0;
    private int emigrations = 0;
    private int inherited = 0;
    private int heirless = 0;
    private long reportedAuditDelta = 0L;
    private int lastTaxSeason = -1;
    private final PairSource.PairConsumer exchange = (a, b) -> {
        if ((double)RND.rFloat() >= EconConfig.alpha) {
            return;
        }
        int ma = this.wallets.spendable(a);
        int mb = this.wallets.spendable(b);
        int newA = ExchangeKernel.yardSale(ma, mb, this.wallets.lambda(a), this.wallets.lambda(b), RND.rFloat());
        this.wallets.applyExchange(a, b, newA);
    };

    public FlowMeter flowMeter() {
        return this.flowMeter;
    }

    public FlowPrices flowPrices() {
        return this.flowPrices;
    }

    public GrainDole grainDole() {
        return this.grainDole;
    }

    public Purchases purchases() {
        return this.purchases;
    }

    public AffordabilityGate affordabilityGate() {
        return this.affordabilityGate;
    }

    public Wages wages() {
        return this.wages;
    }

    public FirmLedger firmLedger() {
        return this.firmLedger;
    }

    public WarehouseMarket warehouseMarket() {
        return this.warehouseMarket;
    }

    public StateWarehouses stateWarehouses() {
        return this.stateWarehouses;
    }

    public LaborMarket laborMarket() {
        return this.laborMarket;
    }

    /** v1.7.2 Ticket 1: ScarcitySignal für LaborMarket-Prioritäts-UI. */
    public ScarcitySignal scarcitySignal() {
        return this.scarcitySignal;
    }

    public Taxes taxes() {
        return this.taxes;
    }

    public Fiscal fiscal() {
        return this.fiscal;
    }

    public ReligionMarket religionMarket() {
        return this.religionMarket;
    }

    public Liturgy liturgy() {
        return this.liturgy;
    }

    public long religionTaxCollected() {
        return this.religionTaxCollected;
    }

    public long liturgyCollected() {
        return this.liturgyCollected;
    }

    public double corveeDraftFractionLast() {
        return this.corveeController.lastDraftFraction();
    }

    public DebtBondage debtBondage() {
        return this.debtBondage;
    }

    public OddjobMarket oddjobMarket() {
        return this.oddjobMarket;
    }

    public MilitaryPayroll militaryPayroll() {
        return this.militaryPayroll;
    }

    public StateWageMarket stateWages() {
        return this.stateWages;
    }

    public TransportMarket transportMarket() {
        return this.transportMarket;
    }

    public ProductionSubsidies productionSubsidies() {
        return this.productionSubsidies;
    }

    private void settleTaxSeason() {
        int season = TIME.seasons().bitsSinceStart();
        if (this.lastTaxSeason == -1) {
            this.lastTaxSeason = season;
            return;
        }
        if (season == this.lastTaxSeason) {
            return;
        }
        this.lastTaxSeason = season;
        this.wallets.settleTaxResentment();
    }

    public double taxPain(Induvidual indu) {
        double effectiveRate = this.wallets.lastTaxRate(indu);
        double ref = EconConfig.taxPainReference;
        double free = EconConfig.taxPainFreeRate;
        if (ref <= free) {
            return 0.0;
        }
        double pain = (effectiveRate - free) / (ref - free);
        if (pain < 0.0) {
            return 0.0;
        }
        if (pain > 1.0) {
            return 1.0;
        }
        return pain;
    }

    public int ticks() {
        return this.ticks;
    }

    /** Current treasury balance (vanilla denari, floor-rounded). */
    public long treasury() {
        return (long) Math.floor(FACTIONS.player().credits().credits());
    }

    private void credit(Humanoid h, int amount) {
        this.wallets.add(h, amount);
    }

    public Roster roster() {
        return this.roster;
    }

    public Wallets wallets() {
        return this.wallets;
    }

    public Humanoid cachedRichestCitizen() {
        return this.cachedRichestCitizen;
    }

    public List<StockpileInstance> cachedStateWarehouses() {
        return this.cachedStateWarehouses;
    }

    public List<RoomBlueprintImp> cachedWorkplaces() {
        return this.cachedWorkplaces;
    }

    public List<RESOURCE> cachedAllResources() {
        return this.cachedAllResources;
    }

    public static EconomySim active() {
        return active;
    }

    public EconIndicators econIndicators() {
        return this.econIndicators;
    }

    public EconProgression progression() {
        return this.progression;
    }

    public DebtDiplomacyBuffer debtDiplomacyBuffer() {
        return this.debtDiplomacyBuffer;
    }

    public ISyxAI aiAdapter() {
        return this.aiAdapter;
    }

    public HousingMarket housingMarket() {
        return this.housingMarket;
    }

    /** Foreign-faction trade-flow aggregator (Phase 5d, Task 9). */
    public ForeignTradeLedger foreignTradeLedger() {
        return this.foreignTradeLedger;
    }

    public long propertySalesCollected() {
        return this.propertySalesCollected;
    }

    public long propertyDividendsPaid() {
        return this.propertyDividendsPaid;
    }

    /**
     * Phase 2: Property market tick (per season).
     * - Checks if citizens can afford to buy their state-owned homes.
     * - Accrues and pays out firm dividends to shareholders.
     */
    private void updatePropertyMarket() {
        if (!EconConfig.propertyMarketEnabled) return;
        int season = TIME.seasons().bitsSinceStart();
        if (this.lastPropertySeason == -1) {
            this.lastPropertySeason = season;
            return;
        }
        if (season == this.lastPropertySeason) return;
        this.lastPropertySeason = season;

        PropertyLedger ledger = this.housingMarket.ledger();

        // 0. Remove entries for demolished rooms (monotonically growing map fix)
        ledger.cleanupGoneRooms();

        // 1. Home purchase: check state-owned homes for occupants who can afford them.
        if (EconConfig.homePurchaseEnabled && SETT.ROOMS() != null) {
            // Check houses
            if (SETT.ROOMS().HOME != null && SETT.ROOMS().HOME.service != null) {
                int tw = SETT.TWIDTH;
                int th = SETT.THEIGHT;
                for (int ty = 0; ty < th; ++ty) {
                    for (int tx = 0; tx < tw; ++tx) {
                        settlement.room.home.house.HomeInstance home = SETT.ROOMS().HOME.service.get(tx, ty);
                        if (home == null || home.occupants() <= 0) continue;
                        PropertyLedger.Entry e = ledger.get(home, "HOME");
                        if (e == null || !e.isStateOwned()) continue;
                        for (int oi = 0; oi < home.occupants(); ++oi) {
                            Humanoid occupant = home.occupant(oi);
                            if (occupant == null || occupant.isRemoved()) continue;
                        long price = ledger.buyHome(occupant, (settlement.room.home.HOME) home, this.wallets);
                        if (price > 0L) {
                            this.propertySalesCollected += price;
                            break; // one buyer per season per home
                            }
                        }
                    }
                }
            }
            // Check chambers
            if (SETT.ROOMS().CHAMBER != null) {
                for (int i = 0; i < SETT.ROOMS().CHAMBER.instancesSize(); ++i) {
                    settlement.room.home.chamber.ChamberInstance chamber = SETT.ROOMS().CHAMBER.getInstance(i);
                    if (chamber == null || !chamber.exists() || chamber.occupants() <= 0) continue;
                    PropertyLedger.Entry e = ledger.get(chamber, "CHAMBER");
                    if (e == null || !e.isStateOwned()) continue;
                    for (int oi = 0; oi < chamber.occupants(); ++oi) {
                        Humanoid occupant = chamber.occupant(oi);
                        if (occupant == null || occupant.isRemoved()) continue;
                        long price = ledger.buyHome(occupant, (settlement.room.home.HOME) chamber, this.wallets);
                        if (price > 0L) {
                            this.propertySalesCollected += price;
                            break;
                        }
                    }
                }
            }
        }

        // 2. Reclaim property from dead/emigrated citizens
        this.reclaimOrphanedProperty();

        // 3. Firm share purchases and dividend cycle
        if (EconConfig.workplaceSharesEnabled) {
            // Accrue this season's dividends (per-firm, not aggregate)
            ledger.accrueDividends(this.firmLedger, SETT.ROOMS().imps());
            // Pay out accumulated dividends
            this.propertyDividendsPaid += ledger.payDividends(this.wallets, this.roster, season);

            // Check if wealthy citizens want to buy firm shares
            for (int i = 0; i < this.roster.size(); ++i) {
                Humanoid citizen = this.roster.get(i);
                int wealth = this.wallets.spendable(citizen);
                double firmMult = EconConfig.citizenClassesEnabled ? this.wallets.classOf(citizen).firmBuyThresholdMultiplier : 1.0;
                if (firmMult >= 999.0) continue; // class never buys firm shares
                if (wealth < (int)((double)EconConfig.initialWallet * 5.0 * firmMult)) continue; // class-specific threshold
                // Try to buy shares in firms — iterate all instances, not just the first
                firmLoop:
                for (settlement.room.main.RoomBlueprintImp bp : this.cachedWorkplaces) {
                    if (!(bp instanceof settlement.room.main.RoomBlueprintIns)) continue;
                    settlement.room.main.RoomBlueprintIns<?> ins = (settlement.room.main.RoomBlueprintIns<?>) bp;
                    for (int j = 0; j < ins.instancesSize(); ++j) {
                        RoomInstance room = (RoomInstance) ins.getInstance(j);
                        if (room == null || !room.exists()) continue;
                        PropertyLedger.Entry e = ledger.get(room);
                        if (e == null || e.shares >= 100) continue;
                        int maxShares = ledger.maxSharesForCitizen((long) citizen.id());
                        if (maxShares <= 0) continue;
                        int available = 100 - e.shares;
                        int toBuy = Math.min(maxShares, Math.min(available, 10)); // buy 10% at a time
                        if (toBuy <= 0) continue;
                        long sharePrice = ledger.buyShares(citizen, room, toBuy, this.wallets, this.firmLedger);
                        if (sharePrice > 0L) {
                            this.propertySalesCollected += sharePrice;
                            break firmLoop; // one purchase per citizen per season
                        }
                    }
                }
            }
        }
    }

    public EconomySim() {
        active = this;
        ISyxTransport tx;
        if (EconConfig.useMethodHandleAdapters) {
            tx = new VanillaTransportAdapterMH();
        } else {
            tx = new VanillaTransportAdapter();
        }
        if (!tx.isDistanceAvailable()) {
            tx = new FallbackTransportAdapter();
        }
        this.transportAdapter = tx;
        this.transportMarket = new TransportMarket(this.transportAdapter);
        ISyxWarehouse wh;
        if (EconConfig.useMethodHandleAdapters) {
            wh = new VanillaWarehouseAdapterMH();
        } else {
            wh = new VanillaWarehouseAdapter();
        }
        if (!wh.isStoringLockAvailable()) {
            wh = new FallbackWarehouseAdapter();
        }
        this.warehouseAdapter = wh;
        this.stateWarehouses = new StateWarehouses(this.warehouseAdapter);
        this.constructionHoardController = new ConstructionHoardController(this.stateWarehouses);
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
        // Phase 4.4: ISyxBoosting erst NACH warehouseInit aufbauen — Booster braucht
        // Engine-Ready-State. Dann EconProgression mit Adapter konstruieren.
        ISyxBoosting boosting = new VanillaBoostingAdapter();
        if (!boosting.isAdminBoosterAvailable()) {
            boosting = new FallbackBoostingAdapter();
        }
        this.boostingAdapter = boosting;
        this.progression = new EconProgression(this.boostingAdapter);
        // Phase 4.5: ISyxDiplomacy + DebtDiplomacyBuffer.
        ISyxDiplomacy diplomacy;
        if (EconConfig.useMethodHandleAdapters) {
            diplomacy = new VanillaDiplomacyAdapterMH();
        } else {
            diplomacy = new VanillaDiplomacyAdapter();
        }
        if (!diplomacy.isAvailable()) {
            diplomacy = new FallbackDiplomacyAdapter();
        }
        this.diplomacyAdapter = diplomacy;
        this.debtDiplomacyBuffer = new DebtDiplomacyBuffer(this.diplomacyAdapter);
        // Initialer Update-Tick, damit Puffer-State gleich mit Engine-Initialisierung synchron ist.
        this.debtDiplomacyBuffer.update();
        // Phase 4 Step 5.6: ISyxAI in EconomySim statt EngineSeams — konsolidiert
        // alle Adapter unter einer Instanz für konsistente Migration der Aufrufer.
        this.aiAdapter = new VanillaAIAdapter();
    }

    public void update(double ds) {
        // Plan-Amendment 5: Re-Entry-Wächter. Ein doppelter update()-Call im selben Tick
        // würde Diagnose-Werte (auditSupply), Acceptor-State und Subsystem-IDs doppelt
        // zählen und damit roi-snapshot ersetzen ohne Realität. Wir nutzen die Vanilla-
        // Aufr ruffrequenz als Truth-of-One — wenn sie gebrochen ist, ist es ein Plugins-
        // API-Bruch oder eine Reflection-Re-Entry und gehört geloggt statt doppelt ausgeführt.
        if (this.lastUpdateTick == this.ticks) {
            throw new IllegalStateException(
                "[ECON] EconomySim.update() re-entry on tick=" + this.ticks
                + " — Vanilla-Loop hat denselben Tick zweimal angesprochen. Mod-State bleibt stabil, Call wird geworfen statt wiederholt."
            );
        }
        this.lastUpdateTick = this.ticks;
        this.debtDiplomacyBuffer.update();
        if (SETT.ENTITIES() == null) {
            return;
        }
        if (ds <= 0.0) {
            return;
        }
        this.roster.rebuild();
        this.wallets.clearPaidThisTick();
        if (this.roster.size() < 2) {
            this.updateRenderCaches();
            return;
        }
        ++this.ticks;
        // Treasury-Krisenprüfung NACH Game-State-Guards (SETT.ENTITIES != null, ds > 0, roster >= 2)
        // Übergibt 'this' für erzwungene Aktionen (Liquidation, Property-Markt, etc.)
        TreasuryCrisis.update(this.treasury(), this);
        this.workplaceDefaults.update();
        this.warehouseMarket.beginTick();
        this.stateWarehouses.beginTick();
        int[] constructionWithdrawals = this.warehouseMarket.observeConstructionWithdrawals();
        int[] stateConstructionWithdrawals = this.stateWarehouses.matchConstructionDeliveries(constructionWithdrawals);
        int[] exportWithdrawals = this.warehouseMarket.observeExportWithdrawals();
        this.flowMeter.sample(ds, EconConfig.flowSmoothingDays, this.stateWarehouses.withheldStock(stateConstructionWithdrawals), constructionWithdrawals);
        this.warehouseMarket.recordProducerlessOutput(this.flowMeter);
        if (EconConfig.stateWarehousesEnabled) {
            WarehouseAutomation.autoTune(this.stateWarehouses, this.flowPrices,
                    this.flowMeter.snapshot(), constructionWithdrawals, this.treasury());
        }
        if (EconConfig.flowPricingEnabled) {
            int refresh = Math.max(1, (int)(EconConfig.flowPriceRefreshDays * TIME.secondsPerDay()));
            if (!this.flowPrices.ready() || this.ticks % refresh == 0) {
                this.refreshFlowPrices();
            }
        }
        boolean seeding = !this.wallets.isSeeded();
        for (int i = 0; i < this.roster.size(); ++i) {
            int minted = this.wallets.touch(this.roster.get(i), this.ticks);
            if (seeding) {
                this.seedSupply += (long)minted;
                continue;
            }
            this.imported += (long)minted;
        }
        if (seeding) {
            this.wallets.markSeeded();
            this.seedTreasury();
            this.logSeed();
        }
        this.wallets.sweepDepartures(this.ticks, this::onDeparture);
        this.grainDole.update(this.roster, this.wallets);
        this.warehouseMarket.prune(this.roster);
        this.warehouseMarket.observeRetailDeliveries();
        this.foodPlanController.update(this.roster);
        this.purchasePlanController.update(this.roster);
        this.serviceMarket.refresh();
        this.servicePlanController.update(this.roster, this.wallets);
        if (EconConfig.constructionHoardingEnabled) {
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
        this.fiscal.settleMerchantRemainder((int)Math.min(Integer.MAX_VALUE, Math.max(0L, b2b.billed() - b2b.credited())));
        this.fiscal.settleCrownWholesale(this.warehouseMarket.buyRemainingCrownGoods(this.roster, this.wallets));
        FirmLedger.UpdateResult firmUpdate = this.firmLedger.update(this.roster, this.wallets, this.flowMeter, this.flowPrices, this.affordabilityGate, ds, this.ticks);
        this.guildIncomePaid += firmUpdate.paid();
        MaintenanceMarket.Settlement upkeep = this.maintenanceMarket.update(this.ticks, this.roster, this.wallets, this.firmLedger);
        this.fiscal.settleMerchantRemainder((int)Math.min(Integer.MAX_VALUE, Math.max(0L, upkeep.billed() - upkeep.credited())));
        this.guildIncomePaid += this.productionSubsidies.update(this.flowMeter, this.firmLedger, this.roster, this.wallets);
        this.guildIncomePaid += this.stateWages.update(ds, this.roster, this.wallets, this.firmLedger);
        this.wagesPaid += this.wages.update(this.roster, this.wallets);
        this.guildIncomePaid += this.transportMarket.update(ds / (double)TIME.secondsPerDay(), this.roster, this.wallets, this.firmLedger);
        this.guildIncomePaid += this.handoutRelief.update(this.roster, this.wallets);
        this.guildIncomePaid += this.stateWarehouses.payWages(this.roster, this.wallets);
        this.warehouseTaxCollected += this.warehouseMarket.taxInventory(this.roster, this.wallets, this.firmLedger);
        this.corveeController.update(this.roster);
        this.accessAutomation.update(this.flowMeter.snapshot(), this.ticks);
        // Scarcity→Priority: set signal + blueprint outputs BEFORE labor update
        // so the scarcity boost is active on the very first tick it's needed.
        this.laborMarket.setScarcitySignal(this.scarcitySignal);            this.laborMarket.update(this.firmLedger, this.ticks);
            this.stateWarehouses.updateEmploymentPriority(this.laborMarket.meanWage());
        OddjobAutomation.autoTune(this.roster, this.laborMarket);
        this.guildIncomePaid += this.oddjobMarket.update(this.roster, this.wallets);
        this.taxesCollected += this.taxes.update(this.roster, this.wallets);
        this.fiscal.update(this.roster, this.wallets);
        this.religionTaxCollected += this.religionMarket.update(this.roster, this.wallets);
        this.liturgyCollected += this.liturgy.update(this.roster, this.wallets);
        this.housingRentCollected += this.housingMarket.update(this.roster, this.wallets, this.firmLedger);
        this.updatePropertyMarket();
        this.settleTaxSeason();
        this.debtBondage.update(this.roster, this.wallets);
        this.spent += this.purchases.update(this.roster, this.wallets, this.affordabilityGate, this.ticks);
        long before = EconConfig.checkConservation ? this.totalLiving() : 0L;
        PairSource source = EconConfig.pairMode == EconConfig.PairMode.PROXIMITY ? this.proximityPairs : this.randomPairs;
        this.encounterCarry += EconConfig.encountersPerGameSecond * ds;
        int n = (int)this.encounterCarry;
        this.encounterCarry -= (double)n;
        if (n > 0) {
            source.encounters(this.roster, n, this.exchange);
        }
        if (EconConfig.checkConservation) {
            long after = this.totalLiving();
            if (before != after) {
                System.err.println("[ECON] KERNEL LEAK: " + before + " -> " + after + " (delta " + (after - before) + ") \u2014 the exchange is not conserving money");
            }
            this.auditSupply();
        }
        int medianRefresh = Math.max(1, (int)(EconConfig.medianRefreshDays * TIME.secondsPerDay()));
        if (EconConfig.medianRefreshDays > 0 && this.ticks % medianRefresh == 0) {
            this.stats.recompute(this.roster, this.wallets);
            // Reclassify citizens after wealth stats update (Phase 3)
            if (EconConfig.citizenClassesEnabled) {
                this.wallets.classifyAll(this.roster, this.stats, this.housingMarket.ledger());
            }
        }
        int dumpInterval = Math.max(1, (int)(EconConfig.dumpIntervalDays * TIME.secondsPerDay()));
        if (EconConfig.dumpIntervalDays > 0 && this.ticks % dumpInterval == 0) {
            this.histogram.dump(this.roster, this.wallets, this.ticks);
            this.logLedger();
        }

        // EconIndicators & EconProgression: Snapshot + Trend-Berechnung alle 60 Ticks.
        // v1.7.2-Fix: War zuvor im roster<2 Guard → lief nie bei echter Population.
        this.econIndicatorTickCounter++;
        if (this.econIndicatorTickCounter >= ECON_INDICATOR_INTERVAL) {
            this.econIndicatorTickCounter = 0;
            EconSnapshot snap = new EconSnapshot(this);
            this.econIndicators.update(snap);
            this.progression.update(snap);
            GiniConsequences.announceIfCrossed(snap, TIME.seasons().bitsSinceStart());
        }

        this.updateRenderCaches();

        // Push values into the dashboard histories once per in-game day.
        // This keeps the 60-slot charts meaningful over several game days
        // instead of filling within seconds of real time.
        if (this.ticks % (int)EconConfig.DEFAULT_TICKS_PER_DAY == 0) {
            this.treasuryHistory.push((double)this.treasury());
            this.giniHistory.push(this.stats.gini);
            // Rebalancing-CSV-Export (opt-in via EconConfig.diagnosticsExportEnabled).
            // Schreibt Makro- und Ressourcen-Zeitreihen in das Diagnostik-Verzeichnis
            // für die Offline-Analyse — Hauptaufgabe: Balancing-Entscheidungen
            // (Anker-Preise, Warehouse-Buy-Floor, Scarcity-Multiplier, Lohn-Niveaus).
            DiagnosticExporter.exportDay(this);
            // Foreign-Tag/Nacht-Transition: sample NPC credit snapshots, accumulate
            // positive deltas as the day's "foreign trade inflow" proxy. Driven at the
            // same boundary as the day-cadence history pushes above.
            // Plan-Amendment 3: Uses EconConfig.DEFAULT_TICKS_PER_DAY (300 = Vanille-Day-Ratio)
            // statt TIME.secondsPerDay() damit die Boundary-Definition aus EconConfig
            // ein einziges Source-of-Truth ist und Balance-Verschiebungen nicht die
            // Tag/Nacht-Rollover-Stelle verschieben.
            this.foreignTradeLedger.dailyTick(this.ticks);
        }
    }

    public SimpleHistory treasuryHistory() {
        return this.treasuryHistory;
    }

    public SimpleHistory giniHistory() {
        return this.giniHistory;
    }

    public WealthStats stats() {
        return this.stats;
    }

    private void updateRenderCaches() {
        // richest citizen
        Humanoid best = null;
        int most = -1;
        for (int i = 0; i < this.roster.size(); ++i) {
            Humanoid h = this.roster.get(i);
            int money = this.wallets.get(h);
            if (money > most) {
                most = money;
                best = h;
            }
        }
        this.cachedRichestCitizen = most > 0 ? best : null;

        // all resources (static, but cache reference to avoid repeated engine calls)
        LIST<RESOURCE> allResources = RESOURCES.ALL();
        ArrayList<RESOURCE> resourcesList = new ArrayList<>(allResources.size());
        for (RESOURCE resource : allResources) {
            resourcesList.add(resource);
        }
        this.cachedAllResources = resourcesList;

        // state-owned warehouses (state-owned first, then private)
        if (SETT.ROOMS() != null && SETT.ROOMS().STOCKPILE != null) {
            int stockpiles = EconProgression.reliableStockpileCount();
            ArrayList<StockpileInstance> ordered = new ArrayList<>(stockpiles);
            for (int i = 0; i < stockpiles; ++i) {
                StockpileInstance w = (StockpileInstance) SETT.ROOMS().STOCKPILE.getInstance(i);
                if (w != null && this.stateWarehouses.isStateOwned((RoomInstance) w)) {
                    ordered.add(w);
                }
            }
            for (int i = 0; i < stockpiles; ++i) {
                StockpileInstance w = (StockpileInstance) SETT.ROOMS().STOCKPILE.getInstance(i);
                if (w != null && !this.stateWarehouses.isStateOwned((RoomInstance) w)) {
                    ordered.add(w);
                }
            }
            this.cachedStateWarehouses = Collections.unmodifiableList(ordered);
        } else {
            this.cachedStateWarehouses = Collections.emptyList();
        }

        // workplaces with employment
        if (SETT.ROOMS() != null) {
            LIST<?> all = SETT.ROOMS().imps();
            ArrayList<RoomBlueprintImp> jobs = new ArrayList<>();
            for (int i = 0; i < all.size(); ++i) {
                RoomBlueprintImp b = (RoomBlueprintImp) all.get(i);
                if (b.employment() == null || !(b instanceof RoomBlueprintIns)) {
                    continue;
                }
                RoomBlueprintIns<?> workplace = (RoomBlueprintIns<?>) b;
                if (workplace.instancesSize() > 0) {
                    jobs.add(b);
                }
            }
            this.cachedWorkplaces = Collections.unmodifiableList(jobs);
        } else {
            this.cachedWorkplaces = Collections.emptyList();
        }
    }

    private void refreshFlowPrices() {
        int goods = RESOURCES.ALL().size();
        double[] anchors = new double[goods];
        double[] targets = new double[goods];
        for (int i = 0; i < goods; ++i) {
            RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(i);
            anchors[i] = PolityPriceAnchor.priceOf(resource);
            targets[i] = RESOURCES.EDI().is(resource) ? EconConfig.targetFoodDays : (RESOURCES.DRINKS().is(resource) ? EconConfig.targetDrinkDays : EconConfig.flowDefaultTargetCoverageDays);
        }
        // Update scarcity signal from the latest flow snapshot
        if (EconConfig.scarcityPriceBoost > 0.0 || EconConfig.scarcityLaborBoost > 0.0) {
            this.scarcitySignal.update(this.flowMeter.snapshot(), EconConfig.flowPriceRefreshDays);
            // Refresh blueprint→output mapping at the same cadence as the signal.
            // Building changes are rare; re-scanning every tick is wasteful.
            this.laborMarket.refreshBlueprintOutputs(this.flowMeter);
        }
        this.flowPrices.refresh(anchors, this.flowMeter.snapshot(), new FlowPrices.Parameters(targets, EconConfig.flowLookaheadDays, EconConfig.scarcityElasticityUp, EconConfig.scarcityElasticityDown, EconConfig.priceClampLo, EconConfig.priceClampHi, EconConfig.priceAbsoluteMax), this.scarcitySignal.snapshot());
        
        if (EconConfig.debugLoggingEnabled) {
            FlowMeter.Snapshot meter = this.flowMeter.snapshot();
            for (int i = 0; i < goods; ++i) {
                RESOURCE res = (RESOURCE)RESOURCES.ALL().get(i);
                LOG.ln("[ECON_DEBUG_PRICE] " + res.name + ": price=" + String.format("%.2f", this.flowPrices.price(i)) + " (anchor=" + anchors[i] + "), supply=" + String.format("%.1f", meter.supplyPerDay(i)) + ", demand=" + String.format("%.1f", meter.demandPerDay(i)) + ", stock=" + String.format("%.1f", meter.stock(i)) + ", cov=" + String.format("%.2f", this.flowPrices.coverage(i)));
            }
        }
    }

    public long seedSupply() {
        return this.seedSupply;
    }

    public long imported() {
        return this.imported;
    }

    public long exported() {
        return this.exported;
    }

    public long escheated() {
        return this.escheated;
    }

    public long wagesPaid() {
        return this.wagesPaid;
    }

    public long guildIncomePaid() {
        return this.guildIncomePaid;
    }

    public long taxesCollected() {
        return this.taxesCollected;
    }

    public long spent() {
        return this.spent;
    }

    public long marketReceipts() {
        return this.fiscal.marketReceipts();
    }

    public long headTaxCollected() {
        return this.fiscal.headTaxCollected();
    }

    public long rationOut() {
        return this.fiscal.rationOut();
    }

    public long roundingDrift() {
        return this.roundingDrift;
    }

    public int deaths() {
        return this.deaths;
    }

    public int emigrations() {
        return this.emigrations;
    }

    public int inherited() {
        return this.inherited;
    }

    public int heirless() {
        return this.heirless;
    }

    public double relativeWealth(Induvidual indu) {
        int m = this.wallets.moneyOf(indu);
        if (m < 0) {
            return 0.5;
        }
        int med = this.stats.median;
        if (med <= 0) {
            return 0.5;
        }
        double rel = (double)m / (EconConfig.relativeWealthMedians * (double)med);
        if (rel < 0.0) {
            rel = 0.0;
        }
        if (rel > 1.0) {
            rel = 1.0;
        }
        return rel;
    }

    private void onDeparture(int estate, int relRef, boolean emigrated) {
        if (emigrated) {
            ++this.emigrations;
            this.exported += (long)estate;
            return;
        }
        ++this.deaths;
        if (estate == 0) {
            return;
        }
        Humanoid heir = this.findHeir(relRef);
        if (heir != null) {
            ++this.inherited;
            this.credit(heir, estate);
            return;
        }
        ++this.heirless;
        this.escheated += (long)estate;
        if (EconConfig.escheatToPlayerTreasury) {
            FACTIONS.player().credits().inc((double)estate, FCredits.CTYPE.TAX);
        }
    }

    /**
     * Phase 2: Reclaim property from dead/emigrated citizens.
     * Called once per season inside updatePropertyMarket().
     */
    private void reclaimOrphanedProperty() {
        if (!EconConfig.propertyMarketEnabled) return;
        java.util.HashSet<Integer> alive = new java.util.HashSet<>();
        for (int i = 0; i < this.roster.size(); ++i) {
            alive.add(this.roster.get(i).id());
        }
        this.housingMarket.ledger().reclaimDeadOwners(alive);
    }

    // Phase 2 orphaned property reclamation moved into updatePropertyMarket().

    private Humanoid findHeir(int deadRef) {
        if (deadRef <= 0) {
            return null;
        }
        int found = 0;
        Humanoid chosen = null;
        block0: for (int i = 0; i < this.roster.size(); ++i) {
            Humanoid h = this.roster.get(i);
            int ref = STATS.REL().reference(h.indu());
            for (int d = 0; d < EconConfig.maxHeirSearchDepth && STATS.REL().hasParent(ref); ++d) {
                ref = STATS.REL().parentRef(ref);
                if (ref != deadRef) continue;
                if (RND.rInt((int)(++found)) != 0) continue block0;
                chosen = h;
                continue block0;
            }
        }
        return chosen;
    }

    public long auditDelta() {
        return AuditKernel.delta(this.totalLiving(), this.auditTerms());
    }

    private AuditKernel.Terms auditTerms() {
        return new AuditKernel.Terms(this.seedSupply, this.imported, this.guildIncomePaid + this.fiscal.rationOut(), this.roundingDrift, this.exported, this.escheated, this.taxesCollected, this.fiscal.headTaxCollected(), this.fiscal.marketReceipts(), this.spent, this.religionTaxCollected, this.liturgyCollected, this.warehouseTaxCollected, this.wagesPaid, this.housingRentCollected, this.propertySalesCollected, this.propertyDividendsPaid);
    }

    private void auditSupply() {
        long expected = AuditKernel.expected(this.auditTerms());
        long actual = this.totalLiving();
        long delta = actual - expected;
        if (delta != 0L) {
            if (Math.abs(delta) <= EconConfig.roundingDriftThreshold) {
                // Small residual — integer truncation noise. Absorb into drift so the
                // next audit cycle self-corrects. Large residuals still fire a mismatch.
                this.roundingDrift += delta;
            } else if (delta != this.reportedAuditDelta) {
                System.err.println("[ECON] SUPPLY MISMATCH: living=" + actual + " expected=" + expected + " (seed=" + this.seedSupply + " +imported=" + this.imported + " +treasuryIncome=" + this.guildIncomePaid + " +rationOut=" + this.fiscal.rationOut() + " +wagesPaid=" + this.wagesPaid + " +propertyDividends=" + this.propertyDividendsPaid + " -exported=" + this.exported + " -escheated=" + this.escheated + " -wealthTax=" + this.taxesCollected + " -headTax=" + this.fiscal.headTaxCollected() + " -market=" + this.fiscal.marketReceipts() + " -legacySpent=" + this.spent + " -religionTax=" + this.religionTaxCollected + " -liturgy=" + this.liturgyCollected + " -warehouseTax=" + this.warehouseTaxCollected + " -housingRent=" + this.housingRentCollected + " -propertySales=" + this.propertySalesCollected + " -roundingDrift=" + this.roundingDrift + ")");
            }
        }
        this.reportedAuditDelta = delta;
    }

    private long totalLiving() {
        return this.wallets.circulating();
    }

    private void seedTreasury() {
        int floor = EconConfig.startingTreasury;
        if (floor <= 0) {
            return;
        }
        double have = FACTIONS.player().credits().credits();
        if (have >= (double)floor) {
            return;
        }
        int topUp = (int)((double)floor - have);
        FACTIONS.player().credits().inc((double)topUp, FCredits.CTYPE.MISC);
        if (EconConfig.debugLoggingEnabled) {
            LOG.ln("[ECON] starting treasury topped up by " + topUp + " to " + floor + " \u2014 enough to make payroll while the city finds its feet.");
        }
    }

    private void logSeed() {
        int n = this.roster.size();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < n; ++i) {
            int m = this.wallets.get(this.roster.get(i));
            if (m < min) {
                min = m;
            }
            if (m <= max) continue;
            max = m;
        }
        LOG.ln("[ECON] seeded " + n + " settlers | min=" + min + " max=" + max + " supply=" + this.seedSupply + " | alpha=" + EconConfig.alpha + " mode=" + String.valueOf((Object)EconConfig.pairMode) + " encounters/gamesec=" + EconConfig.encountersPerGameSecond);
        if (min != max) {
            LOG.ln("[ECON] NOTE: wallets were not uniform at start \u2014 this save already holds economy data. Set EconConfig.resetWalletsOnLoad = true to start flat.");
        }
    }

    private void logLedger() {
        LOG.ln("[ECON] supply: living=" + this.totalLiving() + " = seed " + this.seedSupply + " + imported " + this.imported + " + treasury-funded income " + this.guildIncomePaid + " + ration procurement " + this.fiscal.rationOut() + " - exported " + this.exported + " - escheated " + this.escheated + " - wealth taxes " + this.taxesCollected + " - head taxes " + this.fiscal.headTaxCollected() + " - market receipts " + this.fiscal.marketReceipts() + " - legacy spent " + this.spent + " - housing rent " + this.housingRentCollected + " - wages paid " + this.wagesPaid + " - religion tax " + this.religionTaxCollected + " - liturgy " + this.liturgyCollected + " - warehouse tax " + this.warehouseTaxCollected + " | drift=" + this.roundingDrift + " | deaths=" + this.deaths + " (inherited=" + this.inherited + ", heirless=" + this.heirless + ") emigrations=" + this.emigrations + " | current guild flow: paid " + this.firmLedger.lastIncomePaid() + "/" + this.firmLedger.lastIncomeDue() + (String)(this.firmLedger.lastWorkersUnpaid() > 0 ? " INSOLVENT (" + this.firmLedger.lastWorkersUnpaid() + " unpaid shares)" : ""));
        LOG.ln("[ECON] B2B: merchants bought " + this.warehouseMarket.lastUnitsBought() + " units for " + this.warehouseMarket.lastBought() + " | sold " + this.warehouseMarket.lastUnitsSold() + " for " + this.warehouseMarket.lastSold() + " | construction materials " + this.warehouseMarket.lastConstructionPaid() + " | export purchases " + this.warehouseMarket.lastExportBought() + " | stock levy " + this.warehouseMarket.lastTaxed() + (this.warehouseMarket.lastUnitsBought() == 0 ? "  <- NOBODY IS BUYING: no crates allocated, or merchants are broke" : ""));
    }

    public void save(FilePutter file) {
        // Wallets writes the global format version first.
        this.wallets.save(file);
        saveChunked(file);
    }

    private void saveChunked(FilePutter file) {
        int pos;
        file.i(CHUNK_MAGIC);

        // Core scalar state of EconomySim
        pos = ChunkedSave.startChunk(file, TAG_CORE_SCALARS);
        file.l(this.seedSupply);
        file.l(this.imported);
        file.l(this.exported);
        file.l(this.escheated);
        file.l(this.guildIncomePaid);
        file.l(this.taxesCollected);
        file.l(this.spent);
        file.l(this.roundingDrift);
        file.l(this.warehouseTaxCollected);
        file.l(this.religionTaxCollected);
        file.l(this.liturgyCollected);
        file.l(this.wagesPaid);
        file.i(this.deaths);
        file.i(this.emigrations);
        file.i(this.inherited);
        file.i(this.heirless);
        file.i(this.lastTaxSeason);
        file.i(this.ticks);
        file.i(this.econIndicatorTickCounter);
        file.d(this.encounterCarry);
        file.l(this.reportedAuditDelta);
        file.l(this.housingRentCollected);
        file.l(this.propertySalesCollected);
        file.l(this.propertyDividendsPaid);
        file.i(this.lastPropertySeason);
        ChunkedSave.endChunk(file, pos);

        // EconConfig values that are persisted per-save (not all config)
        pos = ChunkedSave.startChunk(file, TAG_ECON_CONFIG);
        file.bool(EconConfig.debtSlaveryEnabled);
        file.i(EconConfig.debtSlaveThreshold);
        file.bool(EconConfig.oddjobWageEnabled);
        file.i(EconConfig.oddjobWagePerTask);
        file.bool(EconConfig.transportFeeEnabled);
        file.i(EconConfig.transportFeePer100TileDay);
        file.i(EconConfig.perHeadTax);
        file.i((int)Math.round(EconConfig.marketTaxRate * 100.0));
        file.i(EconConfig.warehouseTaxPercent);
        file.bool(EconConfig.taxesEnabled);
        file.bool(EconConfig.religionTaxEnabled);
        file.bool(EconConfig.liturgyEnabled);
        file.i((int)Math.round(EconConfig.liturgyRate * 10000.0));
        file.i(EconConfig.liturgyHeadcount);
        file.i(EconConfig.liturgyIntervalSeasons);
        file.bool(EconConfig.autoProcureConstruction);
        file.i((int)Math.round(EconConfig.autoProcurePremiumMultiplier * 100.0));
        file.i(EconConfig.maxAutoBuySpendPerTick);
        file.bool(EconConfig.constructionHoardingEnabled);
        file.i((int)Math.round(EconConfig.constructionSmoothingDays * 100.0));
        file.bool(EconConfig.firmInputGateEnabled);
        ChunkedSave.endChunk(file, pos);

        // Subsystem chunks (only subsystems that actually persist state)
        saveSubsystemChunk(file, TAG_WAGES, this.wages);
        saveSubsystemChunk(file, TAG_TAXES, this.taxes);
        saveSubsystemChunk(file, TAG_FISCAL, this.fiscal);
        saveSubsystemChunk(file, TAG_LABOR_MARKET, this.laborMarket);
        saveSubsystemChunk(file, TAG_MAINTENANCE_MARKET, this.maintenanceMarket);
        saveSubsystemChunk(file, TAG_GRAIN_DOLE, this.grainDole);
        saveSubsystemChunk(file, TAG_RELIGION_MARKET, this.religionMarket);
        saveSubsystemChunk(file, TAG_LITURGY, this.liturgy);
        saveSubsystemChunk(file, TAG_DEBT_BONDAGE, this.debtBondage);
        saveSubsystemChunk(file, TAG_MILITARY_PAYROLL, this.militaryPayroll);
        saveSubsystemChunk(file, TAG_PRODUCTION_SUBSIDIES, this.productionSubsidies);
        saveSubsystemChunk(file, TAG_STATE_WAREHOUSES, this.stateWarehouses);
        saveSubsystemChunk(file, TAG_WAREHOUSE_MARKET, this.warehouseMarket);
        saveSubsystemChunk(file, TAG_PROGRESSION, this.progression);
        saveSubsystemChunk(file, TAG_HOUSING, this.housingMarket);
        saveSubsystemChunk(file, TAG_FOREIGN_TRADE_LEDGER, this.foreignTradeLedger);
        saveCorveeChunk(file);
        saveStateWagesChunk(file);

        // Explicit end marker. Reading stops here; anything after is ignored.
        int endPos = ChunkedSave.startChunk(file, TAG_END);
        ChunkedSave.endChunk(file, endPos);
    }

    private void saveSubsystemChunk(FilePutter file, int tag, Saveable saveable) {
        int pos = ChunkedSave.startChunk(file, tag);
        saveable.save(file);
        ChunkedSave.endChunk(file, pos);
    }

    private void saveStateWagesChunk(FilePutter file) {
        int pos = ChunkedSave.startChunk(file, TAG_STATE_WAGES);
        StateWageMarket.Entry[] entries = this.stateWages.entries();
        file.i(entries.length);
        for (StateWageMarket.Entry e : entries) {
            file.i(e.wage());
        }
        ChunkedSave.endChunk(file, pos);
    }

    private void saveCorveeChunk(FilePutter file) {
        int pos = ChunkedSave.startChunk(file, TAG_CORVEE);
        CorveeController.ensureSized();
        file.bool(EconConfig.corveeEnabled);
        file.i(EconConfig.corveeDraftPercent);
        file.i(EconConfig.corveeDraftMax);
        file.i(EconConfig.corveeDays.length);
        for (boolean on : EconConfig.corveeDays) {
            file.bool(on);
        }
        ChunkedSave.endChunk(file, pos);
    }

    public void load(FileGetter file) throws IOException {
        int version = this.wallets.load(file);
        if (version >= CHUNKED_VERSION) {
            loadChunked(file);
        } else {
            loadLegacy(file, version);
        }
        if (EconConfig.resetWalletsOnLoad) {
            this.resetEconomy();
            if (EconConfig.debugLoggingEnabled) {
                LOG.ln("[ECON] resetWalletsOnLoad=true -> wallets wiped; everyone will be re-seeded with " + EconConfig.initialWallet);
            }
        }
        // v0.1.3 (Phase-4.7-Blocker #8): Clear all registered IdentityHashMaps
        // after Save/Load, regardless of which load path (chunked/legacy) was used.
        // Songs of Syx re-instantiates RoomInstance, StockpileInstance, Induvidual,
        // Humanoid, RoomBlueprintImp — reference-equality is lost, IdentityHashMap
        // lookups would silently return null. The registry pre-emptively clears
        // each registered map and emits a stderr line so the data loss is visible
        // instead of silent. Maps will rebuild on the next tick's update() calls.
        IdentityMapRegistry.clearOnLoad("Load (version " + version + ")");
    }

    /**
     * Loads the EconomySim state from the new chunked save format.
     *
     * Each chunk is a self-contained byte range. After a chunk handler runs, the
     * file pointer is forced to the declared chunk boundary. This protects against
     * under-reads (a subsystem reading fewer bytes than it wrote). Over-reads
     * cannot be recovered automatically because the extra bytes have already been
     * consumed, so subsystem save/load implementations must stay byte-exact.
     */
    private void loadChunked(FileGetter file) throws IOException {
        int magic = file.i();
        if (magic != CHUNK_MAGIC) {
            throw new IOException("[ECON] expected chunked save magic 0x" + Integer.toHexString(CHUNK_MAGIC) + " but found 0x" + Integer.toHexString(magic));
        }

        boolean loadedCore = false;
        boolean loadedConfig = false;

        ChunkedSave.Header header;
        while ((header = ChunkedSave.readHeader(file)) != null) {
            int expectedEnd = header.dataPosition + header.length;
            try {
                switch (header.tag) {
                    case TAG_CORE_SCALARS:
                        this.seedSupply = file.l();
                        this.imported = file.l();
                        this.exported = file.l();
                        this.escheated = file.l();
                        this.guildIncomePaid = file.l();
                        this.taxesCollected = file.l();
                        this.spent = file.l();
                        this.roundingDrift = file.l();
                        this.warehouseTaxCollected = file.l();
                        this.religionTaxCollected = file.l();
                        this.liturgyCollected = file.l();
                        this.wagesPaid = expectedEnd - file.getPosition() >= 8 ? file.l() : 0L;
                        this.deaths = file.i();
                        this.emigrations = file.i();
                        this.inherited = file.i();
                        this.heirless = file.i();
                        this.lastTaxSeason = file.i();
                        this.ticks = file.i();
                        this.econIndicatorTickCounter = file.i();
                        this.encounterCarry = file.d();
                        this.reportedAuditDelta = file.l();
                        this.housingRentCollected = expectedEnd - file.getPosition() >= 8 ? file.l() : 0L;
                        this.propertySalesCollected = expectedEnd - file.getPosition() >= 8 ? file.l() : 0L;
                        this.propertyDividendsPaid = expectedEnd - file.getPosition() >= 8 ? file.l() : 0L;
                        this.lastPropertySeason = expectedEnd - file.getPosition() >= 4 ? file.i() : -1;
                        loadedCore = true;
                        break;
                    case TAG_ECON_CONFIG:
                        EconConfig.debtSlaveryEnabled = file.bool();
                        EconConfig.debtSlaveThreshold = file.i();
                        EconConfig.oddjobWageEnabled = file.bool();
                        EconConfig.setOddjobWage(file.i());
                        EconConfig.transportFeeEnabled = file.bool();
                        EconConfig.transportFeePer100TileDay = file.i();
                        EconConfig.perHeadTax = file.i();
                        EconConfig.marketTaxRate = (double)file.i() / 100.0;
                        EconConfig.warehouseTaxPercent = file.i();
                        EconConfig.taxesEnabled = file.bool();
                        EconConfig.religionTaxEnabled = file.bool();
                        EconConfig.liturgyEnabled = file.bool();
                        EconConfig.liturgyRate = (double)file.i() / 10000.0;
                        EconConfig.liturgyHeadcount = file.i();
                        EconConfig.liturgyIntervalSeasons = file.i();
                        EconConfig.autoProcureConstruction = file.bool();
                        EconConfig.autoProcurePremiumMultiplier = (double)file.i() / 100.0;
                        EconConfig.maxAutoBuySpendPerTick = file.i();
                        EconConfig.constructionHoardingEnabled = file.bool();
                        EconConfig.constructionSmoothingDays = (double)file.i() / 100.0;
                        EconConfig.firmInputGateEnabled = file.bool();
                        loadedConfig = true;
                        break;
                    case TAG_WAGES:
                        this.wages.load(file);
                        break;
                    case TAG_TAXES:
                        this.taxes.load(file);
                        break;
                    case TAG_FISCAL:
                        this.fiscal.load(file);
                        break;
                    case TAG_LABOR_MARKET:
                        this.laborMarket.load(file);
                        break;
                    case TAG_MAINTENANCE_MARKET:
                        this.maintenanceMarket.load(file);
                        break;
                    case TAG_GRAIN_DOLE:
                        this.grainDole.load(file);
                        break;
                    case TAG_RELIGION_MARKET:
                        this.religionMarket.load(file);
                        break;
                    case TAG_LITURGY:
                        this.liturgy.load(file);
                        break;
                    case TAG_DEBT_BONDAGE:
                        this.debtBondage.load(file);
                        break;
                    case TAG_MILITARY_PAYROLL:
                        this.militaryPayroll.load(file);
                        break;
                    case TAG_PRODUCTION_SUBSIDIES:
                        this.productionSubsidies.load(file);
                        break;
                    case TAG_STATE_WAREHOUSES:
                        this.stateWarehouses.load(file);
                        break;
                    case TAG_WAREHOUSE_MARKET:
                        this.warehouseMarket.load(file);
                        break;
                    case TAG_HOUSING:
                        this.housingMarket.load(file);
                        break;
                    case TAG_FOREIGN_TRADE_LEDGER:
                        this.foreignTradeLedger.load(file);
                        break;
                    case TAG_PROGRESSION:
                        this.progression.load(file);
                        break;
                    case TAG_CORVEE:
                        loadCorvee(file);
                        break;
                    case TAG_STATE_WAGES:
                        {
                            int stateWageCount = file.i();
                            StateWageMarket.Entry[] entries = this.stateWages.entries();
                            int toRead = Math.min(stateWageCount, entries.length);
                            for (int i = 0; i < toRead; ++i) {
                                entries[i].setWage(file.i());
                            }
                            for (int i = toRead; i < stateWageCount; ++i) {
                                file.i(); // discard extra entries
                            }
                        }
                        break;
                    case TAG_END:
                        // Explicit end-of-chunks marker. Stop reading; remaining data is ignored.
                        return;
                    default:
                        // Unknown chunk: skip and continue (forward compatibility)
                        if (EconConfig.debugLoggingEnabled) {
                            LOG.ln("[ECON] skipping unknown save chunk tag=" + header.tag + " length=" + header.length);
                        }
                        break;
                }
            } finally {
                // Ensure we always end up at the declared chunk boundary, even if a
                // subsystem read fewer fields than it wrote (e.g., after a format change).
                if (file.getPosition() != expectedEnd) {
                    System.err.println("[ECON] save chunk " + header.tag + " under-read; adjusting position from " + file.getPosition() + " to " + expectedEnd);
                    file.setPosition(expectedEnd);
                }
            }
        }

        if (!loadedCore) {
            System.err.println("[ECON] WARNING: chunked save missing TAG_CORE_SCALARS; state may be incomplete");
        }
        if (!loadedConfig) {
            System.err.println("[ECON] WARNING: chunked save missing TAG_ECON_CONFIG; config may be incomplete");
        }
    }

    private void loadLegacy(FileGetter file, int version) throws IOException {
        this.seedSupply = file.l();
        this.imported = file.l();
        this.exported = file.l();
        this.escheated = file.l();
        this.guildIncomePaid = file.l();
        this.wages.load(file);
        this.taxesCollected = file.l();
        this.taxes.load(file);
        this.spent = file.l();
        this.roundingDrift = file.l();
        this.grainDole.load(file);
        this.laborMarket.load(file);
        this.fiscal.load(file);
        this.maintenanceMarket.load(file);
        this.religionTaxCollected = file.l();
        this.liturgyCollected = file.l();
        this.religionMarket.load(file);
        this.liturgy.load(file);
        this.loadCorvee(file);
        EconConfig.debtSlaveryEnabled = file.bool();
        EconConfig.debtSlaveThreshold = file.i();
        this.debtBondage.load(file);
        EconConfig.oddjobWageEnabled = file.bool();
        EconConfig.setOddjobWage(file.i());
        if (version >= 20) {
            this.militaryPayroll.load(file);
            this.productionSubsidies.load(file);
        } else {
            this.militaryPayroll.clear();
            this.militaryPayroll.setWage(150);
            this.productionSubsidies.clear();
        }
        if (version >= 21) {
            this.stateWarehouses.load(file);
        } else {
            this.stateWarehouses.clear();
        }
        if (version >= 23) {
            this.warehouseMarket.load(file);
            this.warehouseTaxCollected = file.l();
        } else if (version >= 22) {
            this.warehouseMarket.load(file);
            this.warehouseTaxCollected = 0L;
        } else {
            this.warehouseMarket.clear();
        }
        if (version >= 25) {
            EconConfig.transportFeeEnabled = file.bool();
            EconConfig.transportFeePer100TileDay = file.i();
            for (StateWageMarket.Entry e : this.stateWages.entries()) {
                e.setWage(file.i());
            }
        }
        if (version >= 26) {
            EconConfig.perHeadTax = file.i();
            EconConfig.marketTaxRate = (double)file.i() / 100.0;
            EconConfig.warehouseTaxPercent = file.i();
            EconConfig.taxesEnabled = file.bool();
            EconConfig.religionTaxEnabled = file.bool();
            EconConfig.liturgyEnabled = file.bool();
            EconConfig.liturgyRate = (double)file.i() / 10000.0;
            EconConfig.liturgyHeadcount = file.i();
            EconConfig.liturgyIntervalSeasons = file.i();
        }

        // Legacy layout (v28/v29/v30): progression was written AFTER v29/v30 fields.
        if (version >= 29 && version <= 30) {
            EconConfig.autoProcureConstruction = file.bool();
            EconConfig.autoProcurePremiumMultiplier = (double)file.i() / 100.0;
            EconConfig.maxAutoBuySpendPerTick = file.i();
            if (version >= 30) {
                EconConfig.constructionHoardingEnabled = file.bool();
                EconConfig.constructionSmoothingDays = (double)file.i() / 100.0;
                EconConfig.firmInputGateEnabled = file.bool();
            }
        }
        // Version 28: EconProgression
        if (version >= 28) {
            progression.load(file);
        }
        // Modern layout (v31): progression is written first, then v29/v30 fields in order.
        if (version >= 31) {
            EconConfig.autoProcureConstruction = file.bool();
            EconConfig.autoProcurePremiumMultiplier = (double)file.i() / 100.0;
            EconConfig.maxAutoBuySpendPerTick = file.i();
            EconConfig.constructionHoardingEnabled = file.bool();
            EconConfig.constructionSmoothingDays = (double)file.i() / 100.0;
            EconConfig.firmInputGateEnabled = file.bool();
        }
    }

    private void resetEconomy() {
        this.wallets.reset();
        this.wages.reset();
        this.taxes.reset();
        this.purchases.reset();
        this.grainDole.reset();
        this.fiscal.clear();
        this.laborMarket.reset();
        this.firmLedger.clear();
        this.maintenanceMarket.clear();
        this.serviceMarket.clear();
        this.servicePlanController.clear();
        this.housingMarket.clear();
        this.affordabilityGate.clear();
        this.flowMeter.clear();
        this.flowPrices.clear();
        this.scarcitySignal.clear();
        this.warehouseMarket.clear();
        this.stateWarehouses.clear();
        this.religionMarket.clear();
        this.liturgy.clear();
        this.debtBondage.clear();
        this.oddjobMarket.clear();
        this.militaryPayroll.clear();
        this.stateWages.clear();
        this.transportMarket.clear();
        this.handoutRelief.clear();
        this.productionSubsidies.clear();
        LocalPrices.clearCache();
        this.escheated = 0L;
        this.exported = 0L;
        this.imported = 0L;
        this.seedSupply = 0L;
        this.spent = 0L;
        this.taxesCollected = 0L;
        this.guildIncomePaid = 0L;
        this.liturgyCollected = 0L;
        this.religionTaxCollected = 0L;
        this.warehouseTaxCollected = 0L;
        this.wagesPaid = 0L;
        this.housingRentCollected = 0L;
        this.propertySalesCollected = 0L;
        this.propertyDividendsPaid = 0L;
        this.lastPropertySeason = -1;
        this.lastTaxSeason = -1;
        this.roundingDrift = 0L;
        this.reportedAuditDelta = 0L;
    }

    private void saveCorvee(FilePutter file) {
        CorveeController.ensureSized();
        file.bool(EconConfig.corveeEnabled);
        file.i(EconConfig.corveeDraftPercent);
        file.i(EconConfig.corveeDraftMax);
        file.i(EconConfig.corveeDays.length);
        for (boolean on : EconConfig.corveeDays) {
            file.bool(on);
        }
    }

    private void loadCorvee(FileGetter file) throws IOException {
        EconConfig.corveeEnabled = file.bool();
        EconConfig.corveeDraftPercent = file.i();
        EconConfig.corveeDraftMax = file.i();
        int n = Math.max(0, file.i());
        boolean[] days = new boolean[n];
        for (int i = 0; i < n; ++i) {
            days[i] = file.bool();
        }
        EconConfig.corveeDays = days;
        CorveeController.ensureSized();
    }
}

