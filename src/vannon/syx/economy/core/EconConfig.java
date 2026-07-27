package vannon.syx.economy.core;

public final class EconConfig {

    /**
     * Active locale for UI strings. {@code "de"} (default) uses EconTexts German constants.
     * {@code "en"} switches to LocaleStrings English translations where available.
     * Future: read from game engine's {@code SETT.INFO().language} on startup.
     */
    public static String locale = "de";
    
    /** Range clamping utility for configuration integers. */
    public static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    /** Range clamping utility for configuration doubles. */
    public static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    /** Migration assumption used when converting old tick-based refresh constants
     *  to day-based values. Songs of Syx uses ~300 ticks per in-game day. */
    public static final double DEFAULT_TICKS_PER_DAY = 300.0;

    /**
     * Plan-Amendment 2: Hard-Cap für den Wage-Bonus, der aus der künftigen
     * Affinitäts-Achse (Citizen-State, Phase 5a/5b) resultiert. Erfahrene Veteranen
     * dürfen realitätsnah mehr Lohn akzeptieren (Markt-Spannung), aber der Bonus
     * darf nicht mit profitElasticity=6.0, guildSurplusShare=0.25, oder zukünftigen
     * Lohn-Modifiern kumulativ über 15 % über Basislohn eskalieren. 1.15 = max +15 %.
     * Default stabil; nur Opt-in-Anhebung falls Phase-6-Diagnostik zeigt dass die
     * Wage-Drift im Real-Game < 10 % bleibt.
     */
    public static double affinityWageBonusMax = 1.15;

    /**
     * Plan-Amendment 4: Nested-Config-Klasse für Affinitäts-Flags. Wenn Phase 5a/5b
     * shipped, werden die Affinitäts-relevante Felder (Decay-Rate, Wachstumsfaktor,
     * Veteran-Tier-Floor, Wage-Bonus-Cap, Max-Konvergenz-Distanz) aus dem Root-
     * EconConfig hierher migriert. Bis dahin ist diese Klasse strukturell leer —
     * wir etablieren die Klasse früh, damit zukünftige Migrationen keinen großen
     * Reorg-Diff erzeugen.
     */
    static class AffinConfig {
        // Vorbereitet für Task 7 (Phase 5b, Plan-Doc) — aktuell leer, Platzhalter.
    }

    /** Basis-Startguthaben (WOHLSTAND). Tatsächlicher Wert skaliert mit Wirtschaftsstufe. */
    public static int initialWallet = 5000;
    /** Basis-Immigrantenguthaben. Ebenfalls stage-skaliert via {@link #effectiveImmigrantWallet()}. */
    public static int immigrantWallet = 1000;
    public static int newbornWallet = 0;

    private static final int WALLET_SUBSISTENZ = 200;
    private static final int WALLET_HANDEL     = 500;
    private static final int WALLET_INDUSTRIE  = 2000;
    private static final int WALLET_WOHLSTAND  = 5000;

    /**
     * Stage-gated initial wallet — verhindert 1M Seed-Geld ohne Produktion.
     * Skalierung: SUBSISTENZ→200, HANDEL→500, INDUSTRIE→2000, WOHLSTAND→5000.
     * Fallback (kein Sim/progression): 200 (Subsistenz = sicherster Default).
     */
    public static int effectiveInitialWallet() {
        EconomySim sim = EconomySim.active();
        int base;
        if (sim == null || sim.progression() == null) {
            base = WALLET_SUBSISTENZ;
        } else {
            base = switch (sim.progression().stage) {
                case SUBSISTENZ -> WALLET_SUBSISTENZ;
                case HANDEL     -> WALLET_HANDEL;
                case INDUSTRIE  -> WALLET_INDUSTRIE;
                case WOHLSTAND  -> WALLET_WOHLSTAND;
                case IMPERIUM   -> WALLET_WOHLSTAND; // Deckel: max 5000
            };
        }
        // Early-Settler-Buff: Pop < 50 → +300 D extra Kaufkraft
        if (earlySettlerBuffEnabled && population < earlySettlerPopThreshold) {
            base += earlySettlerWalletBonus;
        }
        return base;
    }

    /** Stage-gated immigrant wallet — gleiche Skalierung wie initial, aber anteilig (1/5). */
    public static int effectiveImmigrantWallet() {
        // Sprint 6 — expliziter null-Guard: effectiveInitialWallet() gibt im
        // EconomySim.active() == null Pfad WALLET_SUBSISTENZ=200 zurueck. Wir
        // duplizieren den Guard-Aufruf hier, damit audit-sim-logic.sh die
        // explizite Intention im Method-Body sieht und kein implizites
        // Delegations-Trust entsteht.
        EconomySim sim = EconomySim.active();
        if (sim == null || sim.progression() == null) {
            return Math.max(50, WALLET_SUBSISTENZ / 5);
        }
        return Math.max(50, effectiveInitialWallet() / 5);
    }
    public static boolean escheatToPlayerTreasury = true;
    public static int maxHeirSearchDepth = 64;
    public static boolean wealthAffectsHappiness = true;
    public static double happinessAtPoorest = 0.75;
    public static double happinessAtRichest = 1.25;
    public static double relativeWealthMedians = 2.0;
    public static double medianRefreshDays = 30.0 / DEFAULT_TICKS_PER_DAY;
    
    // Gini → Loyalty coupling
    public static boolean giniAffectsLoyalty = true;
    public static double loyaltyAtMaxGini = 0.85; // 15% loyalty penalty at max inequality
    public static boolean wagesEnabled = true;
    // v1.7.0 intended: 150→50 as part of coordinated balance package
    // (startingTreasury 100k→200k, corveeDraftPercent 50→20).
    // v1.7.2: Regression fix — value had stayed at 150 while sibling
    // values were deployed.
    // v1.7.3-Fix: Die 13 anderen Wage-Konstanten (militaryTrainingWagePerDay etc.)
    // standen ebenfalls noch auf 150 UND wurden in resetLaborDefaults() aktiv
    // auf 150 zurückgesetzt — der Bug reparierte sich selbst.
    // Alle 13 jetzt auf 50 aligniert, consistent mit defaultWage.
    public static int defaultWage = 50;
    public static int startingTreasury = 200000;
    public static int wageMax = 1000;
    public static int wageStep = 5;
    public static int militaryTrainingWagePerDay = 50;
    public static int exportDepotWagePerDay = 50;
    public static int haulerWagePerDay = 50;
    public static int armySupplyWagePerDay = 50;
    public static int laboratoryWagePerDay = 50;
    public static int libraryWagePerDay = 50;
    public static int embassyWagePerDay = 50;
    public static int waterWagePerDay = 50;
    public static int cannibalWagePerDay = 50;
    public static int policeWagePerDay = 50;
    public static int guardWagePerDay = 50;
    public static int stockadeWagePerDay = 50;
    public static int prisonWagePerDay = 50;
    public static boolean transportFeeEnabled = true;
    public static final int DEFAULT_TRANSPORT_FEE_PER_100_TILE_DAY = 5;
    public static int transportFeePer100TileDay = 5;
    public static boolean laborMarketEnabled = true;
    public static int laborNeutralPriority = 10;
    public static double laborRefreshDays = 50.0 / DEFAULT_TICKS_PER_DAY;
    public static int laborFrictionPoints = 2;
    public static boolean firmLedgerEnabled = true;
    public static boolean firmSizingEnabled = true;
    /** Minimum workers a firm will request when it first appears. 0 used to cause a
     * cold-start death spiral: no workers assigned → no output → profit ≤ 0 → idle.
     * 1 is enough to bootstrap production while still letting the hill-climber size. */
    public static int minimumWorkersPerWorkplace = 1;
    /** Share of firm profit paid out to workers. 0.0 kept all surplus inside the
     * firm, driving Gini toward 1.0 because only capital owners/carpenters captured
     * value. 0.25 returns a quarter to labor first; tune upward if Gini stays high. */
    public static double guildSurplusShare = 0.25;
    /** Mindest-Profit pro Arbeiter, bevor Gewinn an Arbeitnehmer ausgeschüttet wird.
     * Verhindert, dass Subsistenz-Betriebe (Bäckerei mit 187 D/Tag, Holzfäller mit
     * <1 D/Tag) durch die Surplus-Auszahlung insolvent werden. Nur der Profit über
     * diesem Sockel wird mit guildSurplusShare an die Arbeiter verteilt. */
    public static double guildSurplusMinProfitPerWorker = 10.0;
    public static double profitElasticity = 6.0;
    public static int firmSizingHillclimbStep = 1;
    public static double firmSizingHysteresis = 1.0;
    public static double firmSizingRefreshDays = 3600.0 / DEFAULT_TICKS_PER_DAY;
    public static boolean warehouseMarketEnabled = true;
    public static boolean stateWarehousesEnabled = true;
    public static boolean warehouseAutoTuneEnabled = true;
    public static boolean warehouseAutoHireEnabled = true;
    public static int stateWarehouseWage = 50;
    public static int statePriceMax = 10000;
    public static final int DEFAULT_CROWN_MARKET_PRICE = 75;
    public static int warehouseTaxPercent = 0;
    public static boolean autoProcureConstruction = true;
    public static double autoProcurePremiumMultiplier = 1.5;
    public static int maxAutoBuySpendPerTick = 5000;
    public static boolean constructionHoardingEnabled = true;
    public static boolean maintenanceMarketEnabled = true;
    public static double maintenanceRefreshDays = 50.0 / DEFAULT_TICKS_PER_DAY;
    public static int maintenanceBidBase = 20;
    public static int maintenanceWorkplacesPerJanitor = 2;
    public static int productionSubsidyMax = 1000;
    /**
     * v0.1.3-Fix (Cheat-Loop):
     * Default-Flip von {@code false} → {@code true}.     * <p>Hintergrund: Mit {@code foodAffordabilityGateEnabled=false} UND
     * {@code handoutWalletAmount=400} (Phase-4.6-Stand) gab es einen stillen
     * Geld-Drucker: Buerger bekamen pro Saison 400 D Handout, assen aber umsonst
     * (Food-Gate aus). Kein Sink, kein Verlust — 200 Buerger x 400 D = 80.000 D
     * reine Geldschoepfung pro Saison, Gini driftete unkontrolliert Richtung 1.0
     * (in Playtests: Gini 0.95 bei Treasury -900M als sichtbares Symptom).</p>
     *
     * <p>Phase 5h (2026-07-24) hat zwei Hebel gesetzt: (1) dieser Default-Flip auf
     * {@code true}, (2) {@link #handoutWalletAmount} wurde von 400 auf 50 gesenkt.
     * Die Cap-50 wirkt als Notfallreserve ohne Drucker-Mechanismus, und
     * {@code tools/food-dole-cheat-check.sh} misst die Equity-Drift
     * (Σ_GrainDoleSpend / Σ_TotalSupply) als CI-Gate — bei >5 % exit 1.</p>
     *
     <p>Jetzt Default-true: Food kostet wieder Geld, Hunger = echter Druck,
     * Handout = Notfallreserve statt Cashflow-Trick. UI-Slider im Advisor-Tab
     * bleibt erhalten, Spieler koennen es bei Bedarf bewusst ausschalten
     * (z. B. fuer Test-Saves oder spezielle Krisen-Szenarien).</p>
     */
    public static boolean foodAffordabilityGateEnabled = true;
    public static boolean consumptionGateEnabled = true;
    // All major economic subsystems are enabled by default as of v1.3.0.
    // Values below are intentionally conservative so the economy stays
    // playable while every system is actually felt. Disable only as a fallback.
    public static boolean firmInputGateEnabled = true;
    public static int gateRoundingMargin = 1;
    public static boolean serviceMarketEnabled = true;
    public static double serviceUtilTarget = 0.8;
    public static double servicePriceUp = 0.2;
    public static double servicePriceDown = 0.08;
    public static double serviceBidWealthWeight = 0.25;
    public static int serviceBasePrice = 20;
    public static int servicePriceMin = 1;
    public static int servicePriceMax = 2000;
    public static int perHeadTax = 0;
    public static double marketTaxRate = 0.05;
    public static int doleWealthThreshold = 500;
    /** Citizens with net worth below this amount are exempt from the per-head tax.
     * Decoupled from {@link #doleWealthThreshold} so tax policy and grain-dole
     * eligibility can be tuned independently. */
    public static int perHeadTaxExemptionThreshold = 500;
    public static int doleHeadcap = 100;
    public static int doleHeadcapBase = 100; // Base value stored separately; doleHeadcap is modified at runtime by trend consequences
    public static boolean handoutToWallet = true;
    /**
     * Phase 5h (Pre-Phase-6 Blocker): Handout-Cap pro Bürger auf 50 Denari gesenkt.
     * Mit dem alten Wert 400 + {@link #foodAffordabilityGateEnabled}=true (oder false!)
     * entstand eine stille Geldschöpfung: ~80k Denari/Jahr bei 200 Bürgern (40 %
     * der startingTreasury=200k) ohne wirtschaftliche Gegenleistung. 50 lässt einen
     * echten Notfall-Puffer für Bürger ohne Geld und Nahrung (Feuer, Krieg) ohne
     * den Drucker-Mechanismus freizugeben. Ratio wird durch
     * {@code tools/food-dole-cheat-check.sh} gegen den Live-Snapshot geprüft
     * (Σ_GrainDoleSpend / Σ_TotalSupply > 5 % → CI-Rot).
     */
    public static int handoutWalletAmount = 50;
    public static boolean oddjobWageEnabled = true;
    public static boolean oddjobAutoTuneEnabled = true;
    public static final int DEFAULT_ODDJOB_WAGE_PER_TASK = 3;
    public static final int ODDJOB_WAGE_MAX = 250;
    public static int oddjobWagePerTask = 3;

    // ─── Phase 5e — Player-Agency Bundle (2026-07-24) ─────────────────────────
    /**
     * Cap-Ratio f\u00fcr Tagel\u00f6hner-Lohn: max 75 % von {@link #defaultWage} (also ~38 D
     * bei defaultWage=50). Verhindert dass Oddjob-Tasks den regul\u00e4ren Arbeiter-Lohn
     * \u00fcberbieten und damit Tagel\u00f6hner attraktiver machen als Festanstellung — Migration
     * zu XP-gebundenen Berufen (Phase 5a) w\u00fcrde sonst leerlaufen.
     */
    public static double oddjobWageCeilingRatio = 0.75;

    /**
     * Phase 5e hard-cap setter: clamps oddjob wage to the ceiling ratio.
     * This is the SINGLE choke-point for ALL writes to {@link #oddjobWagePerTask}.
     * Every slider, save/load, auto-tuner, and crisis path MUST route through here
     * — direct assignment to {@code oddjobWagePerTask} bypasses the cap and is a bug.
     *
     * @param wage desired wage; silently clamped to [0, defaultWage × oddjobWageCeilingRatio]
     */
    public static void setOddjobWage(int wage) {
        int ceiling = (int)(defaultWage * oddjobWageCeilingRatio);
        oddjobWagePerTask = clamp(wage, 0, ceiling);
    }

    /**
     * Opt-in-Toggle: Wenn true, lehnt {@code Wages.setWage()} f\u00fcr private R\u00e4ume (nicht
     * state-funded public works) ab. Default false (Backward-Compat).
     */
    public static boolean stateFundedWageRegulationOnly = false;

    /**
     * Default-Operating-Mode f\u00fcr neu-gebaut/state-funded R\u00e4ume ohne Personal. PAUSED =
     * keine Produktion, keine Operating-Cost. Pro-Spieler-Wahl im UI \u00fcberschreibbar.
     */
    public static RoomOperatingMode stateRoomDefaultOpMode = RoomOperatingMode.PAUSED;

    /** Operating-Cost-Faktor f\u00fcr MOTHBALLED Mode (0.3 = 30 % Normal-Cost). */
    public static double mothballOperatingCostMultiplier = 0.3;

    /** Per-Room Operating-Mode-Enum. PRODUCE=vanilla, PAUSED=kein Output/Cost, MOTHBALLED=0.3\u00d7Cost. */
    public enum RoomOperatingMode { PRODUCE, PAUSED, MOTHBALLED }
    public static boolean religionTaxEnabled = true;
    // Default religion head tax: applied to newly discovered religions.
    public static int religionHeadTaxDefault = 5;
    public static int[] religionHeadTax = new int[]{religionHeadTaxDefault};
    public static boolean liturgyEnabled = true;
    public static double liturgyRate = 0.1;
    public static int liturgyHeadcount = 1;
    public static int liturgyIntervalSeasons = 1;
    public static boolean corveeEnabled = true;
    public static boolean[] corveeDays = new boolean[0];
    public static int corveeDraftPercent = 20;
    public static int corveeDraftMax = 9999;
    // Corvée scales with settlement size: no drafting below the threshold,
    // full configured percentage at/above the full-scale population.
    public static int corveePopThreshold = 100;
    public static int corveePopFullScale = 500;
    // Room key substrings that are exempt from corvée drafting (essentials).
    public static String[] corveeExemptRoomKeys = new String[]{"_WATERPUMP", "FARM", "ORCHARD", "PASTURE", "FISHING", "_WOOD", "_STONE", "MINE", "QUARRY"};
    public static boolean debtSlaveryEnabled = true;
    public static int debtSlaveThreshold = 5000;
    public static double unpaidHappiness = 0.6;
    public static boolean taxesEnabled = true;
    public static boolean disableVanillaInflation = true;
    public static boolean diplomacyDebtBufferEnabled = true;
    public static long diplomacyDebtThreshold = -100000000L;
    public static double taxHappinessAtFullRate = 0.5;
    public static double taxPainReference = 0.25;
    public static double taxPainFreeRate = 0.05;
    public static double meticImmigrationDepth = 0.35;
    public static double meticImmigrationSteepness = 10.0;
    public static boolean grainDoleEnabled = true;
    public static boolean grainDoleToSlaves = false;
    public static boolean militaryPayrollEnabled = true;
    public static boolean debugPriceLogging = true;
    public static boolean payWagesToSlaves = false;
    public static boolean housingMarketEnabled = true;
    public static int housingBaseRentPerTile = 1;
    public static int housingEvictionDebtThreshold = 100;
    // Grace seasons *after* rent debt reaches housingEvictionDebtThreshold before eviction.
    public static int housingGraceDays = 3;
    public static boolean chargeForGoods = true;
    public static double targetDrinkDays = 6.0;
    public static double scarcityMaxMultiple = 1.5;
    public static double scarcitySteepness = 1.0;
    public static double targetFoodDays = 6.0;
    public static final double FOOD_DAYS_MAX = 24.0;
    public static double targetGoodsCoverage = 1.0;
    public static double scarcityRefreshDays = 60.0 / DEFAULT_TICKS_PER_DAY;
    public static double purchasePollDays = 6.0 / DEFAULT_TICKS_PER_DAY;
    /** v1.7.2 Perf: Shard-Count fuer FoodPlanController + PurchasePlanController.
     *  Jeder Tick verarbeitet nur 1/shardCount des Rosters, aber ueber shardCount
     *  Ticks wird jeder Buerger einmal geprueft. 0 oder 1 = Sharding deaktiviert
     *  (voller Roster-Scan jeden Tick). */
    public static int planControllerShardCount = 4;
    public static boolean flowPricingEnabled = true;
    public static double flowSmoothingDays = 1.0;
    public static double constructionSmoothingDays = 5.0;
    public static double flowLookaheadDays = 1.0;
    public static double flowDefaultTargetCoverageDays = 1.0;
    public static double scarcityElasticityUp = 0.8;
    public static double scarcityElasticityDown = 1.375;
    public static double priceClampLo = 0.001;
    public static double priceClampHi = 100.0;
    /** v1.7.2-Fix: War 5000 → alle Ressourcen mit Bestand=0 landeten bei exakt 5K,
     *  unabhaengig vom Ankerpreis. Jetzt 50000 — 10x hoeher, Ressourcen bleiben
     *  unterscheidbar (Brot ~14K, Moebel ~99K gecappt auf 50K). priceClampHi=100
     *  begrenzt den Multiplikator bereits, 50000 ist reines Safety-Net. */
    /** D-001: Hard-Cap für Nahrungsmittel-Einzelpreise im FlowPrices-System.
     *  Verhindert dass food_basket_price bei Knappheit auf 70-85× Anker explodiert
     *  (Diagnose: BREAD 78→6248 bei scarcityMaxMultiple=1.5). 500 = ~6× durchschnittlicher
     *  Food-Anker — signalisiert Knappheit ohne Bürger bankrott zu machen.
     *  Greift NACH scarcityMultiplier + scarcityPriceBoost, VOR phaseFactor.
     *  Nur für essbare Ressourcen (RESOURCES.EDI().is(resource)).
     *  Anker-relativ (6×): BREAD (Anker=78) → max 468, LUXUS-FOOD (Anker=500) → max 3000.
     *  Verhindert Config-Design-Fehler: absoluter Cap (500) ist für teure Güter zu eng
     *  und für billige zu weit. Multiplikator skaliert mit dem Anker — Handelsdynamik bleibt.
     *  Setze 0.0 zum Deaktivieren. */
    public static double foodPriceCapMultiplier = 6.0;
    /** v1.7.2-Fix: War 5000 → alle Ressourcen mit Bestand=0 landeten bei exakt 5K,
     *  unabhaengig vom Ankerpreis. Jetzt 50000 — 10x hoeher, Ressourcen bleiben
     *  unterscheidbar (Brot ~14K, Moebel ~99K gecappt auf 50K). priceClampHi=100
     *  begrenzt den Multiplikator bereits, 50000 ist reines Safety-Net.
     *  D-001: foodPriceCapMultiplier=6.0 deckt Nahrungsmittel separat ab (anker-relativ). */
    public static double priceAbsoluteMax = 50000.0;
    public static double flowPriceRefreshDays = 60.0 / DEFAULT_TICKS_PER_DAY;
    public static boolean windowEnabled = true;
    public static boolean heterogeneousLambda = true;
    public static double lambdaMin = 0.0;
    public static double lambdaMax = 0.99;
    public static boolean resetWalletsOnLoad = false;
    public static double alpha = 0.002;
    public static double encountersPerGameSecond = 200.0;
    public static PairMode pairMode = PairMode.PROXIMITY;
    public static int proximityRadiusPx = 32;
    public static double dumpIntervalDays = 0.0;
    public static boolean checkConservation = true;
    // Max absolute delta that auditSupply() silently absorbs into roundingDrift.
    // Larger deltas still fire SUPPLY MISMATCH. Set to 0 to disable drift absorption.
    public static int roundingDriftThreshold = 20;
    // Scarcity→Price→Priority coupling (v1.4.0)
    // Multiply the FlowPrices coverage multiplier by (1 + signal * boost) when a resource is scarce.
    // 0.0 = no price boost. 0.5 = up to 50% price increase at max scarcity.
    public static double scarcityPriceBoost = 0.3;
    // Boost the effective marginal surplus for rooms producing a scarce resource.
    // 0.0 = no labour priority boost. 0.5 = up to 50% priority increase at max scarcity.
    public static double scarcityLaborBoost = 0.4;

    // —— Eigentum & Privatisierung (Phase 2, v1.5.0) —————————————————————
    // Master switch for the entire property market subsystem.
    public static boolean propertyMarketEnabled = false;
    // Citizens can buy their homes from the state when they have saved enough.
    public static boolean homePurchaseEnabled = false;
    // Home price = (annual rent) × this multiplier. 20 means ~20 years of rent.
    public static double homePriceMultiplier = 20.0;
    // Citizens can buy shares in production firms.
    public static boolean workplaceSharesEnabled = false;
    // Firm full price = (annual profit) × this multiplier.
    public static double firmPriceMultiplier = 12.0;
    // Maximum shares a citizen can own in a single firm (% of the firm).
    public static int maxSharesPerFirm = 50;
    // Progressive monopoly brake: each additional firm reduces max shares by this.
    public static int progressiveShareStep = 5;
    // Floor: no citizen can ever own less than this % of a firm.
    public static int minSharesPerFirm = 10;
    // Fraction of firm profit distributed to shareholders each season.
    public static double dividendRate = 0.30;
    // Happiness boost for home-owning citizens (additive to the wealth booster).
    public static double propertyHappinessBoost = 0.15;

    // —— Bürger-Diversifizierung (Phase 3, v1.6.0) —————————————————————
    // Enable wealth-based citizen classes that modify behavior.
    public static boolean citizenClassesEnabled = true;

    // —— Poverty Pressure (v1.7.1) —————————————————————————————————————
    // Happiness penalty for unemployed citizens with low wealth.
    // Without this, citizens with foodAffordabilityGateEnabled=false
    // have no survival pressure to seek work.
    public static boolean povertyPressureEnabled = true;
    public static int povertyPressureWealthThreshold = 500;
    public static double povertyPressureHappinessMin = 0.5;

    public static boolean debugLoggingEnabled = true;

    /**
     * Opt-in: Aktiviert den {@link DebugTracer} Ring-Buffer.
     * Zeichnet ALLE Interrupter-Callbacks, View-Wechsel, Save/Load und
     * Economy-State-Changes auf (8.192 Events). Export via {@code DebugTracer.dump()}
     * oder Numpad / (Division) Hotkey in {@code InstanceScript}.
     * <p>Default: false — kein Overhead im Normalbetrieb.</p>
     */
    public static boolean debugTracing = true;

    /**
     * Schaltet den Rebalancing-Diagnostik-Export ein. Wenn aktiv, schreibt
     * {@link DiagnosticExporter} pro In-Game-Tag zwei CSV-Dateien
     * (Makro- und Ressourcen-Zeitreihen) in das Mod-Diagnostik-Verzeichnis
     * f\u00fcr die Offline-Analyse in Excel/pandas.
     *
     * <p>Default: false — Opt-in, um Datei-IO f\u00fcr Endnutzer zu vermeiden.</p>
     */
    public static boolean diagnosticsExportEnabled = false; // Public-Release-Default; per Debug-Tab aktivierbar

    /**
     * Schreibt pro Tick die Carpenter/Möbel-Firma-Diagnose nach
     * {@code ~/.local/share/songsofsyx/mods/SyxEconomyMod/diagnostics/furniture_debug.csv}.
     * Diagnostiziert ob employed_target &gt; 0 und ob das out-Resource richtig gemappt
     * ist. Spalten: tick, bp_key, employed, hardTarget, marketTarget, profit,
     * out_name, out_per_day, out_producedTotal, in_name, in_per_day.
     * <p>Default false — Opt-in wegen File-IO pro Tick.</p>
     */
    public static boolean debugFurnitureDump = true;

    /**
     * Throttle für {@link #debugFurnitureDump}: schreibt maximal alle N Ticks
     * eine Zeile (Default 50 ≈ 6 syscalls/Tag statt 300). Setze 1 für
     * lückenlosen Trace, 50 für Diagnose, 0 für deaktiviert.
     */
    public static int debugFurnitureDumpEveryTicks = 50;

    /** Initialization hook called once at mod startup. */
    public static void init() {}

    public static void resetLaborDefaults() {
        // v1.7.3-Fix: War 150 — hat den Balance-Fix von v1.7.0 bei jedem Reset
        // aktiv rückabgewickelt. Jetzt 50, konsistent mit defaultWage.
        militaryTrainingWagePerDay = 50;
        exportDepotWagePerDay = 50;
        haulerWagePerDay = 50;
        armySupplyWagePerDay = 50;
        laboratoryWagePerDay = 50;
        libraryWagePerDay = 50;
        embassyWagePerDay = 50;
        waterWagePerDay = 50;
        cannibalWagePerDay = 50;
        policeWagePerDay = 50;
        guardWagePerDay = 50;
        stockadeWagePerDay = 50;
        prisonWagePerDay = 50;
        transportFeeEnabled = true;
        transportFeePer100TileDay = 5;
        oddjobWageEnabled = true;
        setOddjobWage(DEFAULT_ODDJOB_WAGE_PER_TASK);
    }

    private EconConfig() {
    }

    /**
     * Returns a human-readable warning when two or more economy levers are set in a way
     * that can cancel each other out or produce unintended side effects. The check is
     * read-only; it never changes any config value.
     */
    public static String conflictWarning() {
        if (stateFundedWageRegulationOnly && !wagesEnabled) {
            return "stateFundedWageRegulationOnly braucht wagesEnabled=true";
        }
        if (foodAffordabilityGateEnabled && handoutWalletAmount > 200) {
            return "foodAffordabilityGate + Handout = doppelte Kosten";
        }
        return null;
    }

    public static enum PairMode {
        RANDOM,
        PROXIMITY;

    }

    // T6 (B-009): Hunger→Demographie Hook. hungerDeathThreshold: Engine-Hunger-Stat
    // (0=saettigend, 100=verhungernd) ab dem Wert geld-schaden + emigration-risk
    // ausgeloest wird. hungerDamageRate: D/tick Geld-Schaden bei Ueberschreitung.
    public static int hungerDeathThreshold = 80;
    public static int hungerDamageRate = 2;

    // T6-Final: Engine-Ticks pro Spiel-Tag. Songs-of-Syx hat basierend auf
    // Live-Daten (tick=179247/day=597.49) ca. 300 Sub-Ticks pro Spiel-Tag.
    // Drain-Operationen in EconomySim.updateDemography nutzen diese Konstante.
    public static int ticksPerGameDay = 300;

    // T8 (H8): phaseFactor fuer Early-Game-Preisdampfung. Wenn population < threshold,
    // werden Preise linear nach unten skaliert (factor in [phaseFactorMin, 1.0]).
    // HEBELKARTE markiert phaseFactor als "Kritisch" — fehlte auf P1-Liste.
    public static boolean phaseFactorEnabled = true;
    public static int phaseFactorThreshold = 300;
    public static double phaseFactorMin = 0.5;

    // ─── Early-Game Safeties (Livetest v0.13.67) ───────────────────────
    /** Grace Period: TreasuryCrisis feuert Tiers 1-4 nicht waehrend der ersten
     *  {@code treasuryGracePeriodTicks} Ticks (~1 Spieljahr = 365 Tage × 300 Ticks).
     *  Tier 5 (Safety-Net bei -5M) feuert IMMER — Grace schuetzt vor False-Alarm,
     *  nicht vor echtem Kollaps. */
    public static boolean treasuryGracePeriodEnabled = true;
    public static int treasuryGracePeriodTicks = 365 * 300; // 109.500

    /** Early-Settler-Buff: Buerger mit Pop < {@code earlySettlerPopThreshold}
     *  bekommen {@code earlySettlerWalletBonus} Denari extra auf ihr Start-Wallet.
     *  Verhindert dass die ersten 50 Siedler sofort verhungern weil noch kein
     *  Markt/Lager existiert. Kombiniert mit phaseFactor (0.5× Preise) fuer
     *  sanften Early-Game-Einstieg. */
    public static boolean earlySettlerBuffEnabled = true;
    public static int earlySettlerPopThreshold = 50;
    public static int earlySettlerWalletBonus = 300; // +300 D auf SUBSISTENZ=200 → effektiv 500 D

    // T8: Live-Population, gesetzt von EconomySim.update(). FlowPrices liest das.
    public static int population = 0;

    /**
     * T8: Berechnet den phaseFactor fuer die aktuelle population.
     * Linear: pop=0 -> phaseFactorMin, pop=threshold -> 1.0, pop>threshold -> 1.0.
     */
    public static double phaseFactor() {
        if (!phaseFactorEnabled) return 1.0;
        int pop = Math.max(0, population);
        if (pop >= phaseFactorThreshold) return 1.0;
        double ramp = (double)(phaseFactorThreshold - pop) / (double)phaseFactorThreshold;
        return Math.max(phaseFactorMin, 1.0 - ramp * (1.0 - phaseFactorMin));
    }

    public static void setPopulation(int pop) {
        population = Math.max(0, pop);
    }
}

