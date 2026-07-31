package vannon.syx.economy.core;

import settlement.stats.STATS;

/**
 * Sammelt alle Wirtschaftsdaten in einem Frame.
 * Erstellt alle 60 Ticks von EconIndicators.
 */
public final class EconSnapshot {
    // Bevölkerung
    public final int people;
    public final int deaths;
    public final int emigrations;
    public final int inherited;
    public final int heirless;
    
    // Wohlstand
    public final long totalMoney;
    public final int median;
    public final double mean;
    public final double gini;
    public final int maxWealth;
    
    // Beschäftigung
    public final long incomeDue;
    public final long incomePaid;
    public final int workersUnpaid;
    public final double meanWage;
    public final double actualMeanWage;  // Sprint v0.13.140+: now market wage from laborMarket(), not actual paid
    
    // Fiskal
    public final long headTax;
    public final long marketReceipts;
    public final long rationOut;
    public final long creditsTax;
    public final long creditsTrade;
    public final long creditsMisc;
    
    // Ressourcen (Arrays)
    public final double[] supplyPerDay;
    public final double[] demandPerDay;
    public final double[] stock;
    
    // Warenhandel
    public final long warehouseBought;
    public final long warehouseSold;
    public final int unitsBought;
    public final int unitsSold;
    
    // Advisor-Trenddaten
    public final int foodBasketPrice;
    public final double unpaidRatio;
    public final double wageShare;

    // v1.7.1 — Makro-Indikatoren (GUI-Audit)
    public final long treasuryCurrent;
    public final double foodDays;
    public final double battleThreat;

    /**
     * Erstellt einen Snapshot aus dem aktuellen EconomySim-Zustand.
     */
    public EconSnapshot(EconomySim sim) {
        // Bevölkerung
        this.people = sim.roster().size();
        this.deaths = sim.deaths();
        this.emigrations = sim.emigrations();
        this.inherited = sim.inherited();
        this.heirless = sim.heirless();
        
        // Wohlstand
        this.totalMoney = sim.wallets().circulating();
        this.median = sim.stats().median;
        this.mean = sim.stats().mean;
        this.gini = sim.stats().gini;
        this.maxWealth = sim.stats().max;
        
        // Beschäftigung
        this.incomeDue = sim.firmLedger().lastIncomeDue();
        this.incomePaid = sim.firmLedger().lastIncomePaid();
        this.workersUnpaid = sim.firmLedger().lastWorkersUnpaid();
        this.meanWage = sim.laborMarket().meanWage();
        this.actualMeanWage = sim.laborMarket().meanWage();
        
        // Fiskal
        this.headTax = sim.fiscal().headTaxCollected();
        this.marketReceipts = sim.fiscal().marketReceipts();
        this.rationOut = sim.fiscal().rationOut();
        this.creditsTax = sim.fiscal().creditsTax();
        this.creditsTrade = sim.fiscal().creditsTrade();
        this.creditsMisc = sim.fiscal().creditsMisc();
        
        // Ressourcen-Arrays füllen
        FlowMeter.Snapshot flow = sim.flowMeter().snapshot();
        int n = flow.size();
        this.supplyPerDay = new double[n];
        this.demandPerDay = new double[n];
        this.stock = new double[n];
        for (int i = 0; i < n; i++) {
            this.supplyPerDay[i] = flow.supplyPerDay(i);
            this.demandPerDay[i] = flow.demandPerDay(i);
            this.stock[i] = flow.stock(i);
        }
        
        // Warenhandel
        this.warehouseBought = sim.warehouseMarket().lastBought();
        this.warehouseSold = sim.warehouseMarket().lastSold();
        this.unitsBought = sim.warehouseMarket().lastUnitsBought();
        this.unitsSold = sim.warehouseMarket().lastUnitsSold();
        
        // Advisor-Trenddaten
        int foodPrice = LocalPrices.flowFoodBasketPrice();
        if (foodPrice <= 0) {
            foodPrice = LocalPrices.foodBasketPrice(sim.ticks());
        }
        this.foodBasketPrice = foodPrice;
        this.unpaidRatio = (double) this.workersUnpaid / (double) Math.max(1, this.people);
        this.wageShare = this.totalMoney > 0L ? (double) this.incomePaid / (double) this.totalMoney : 0.0;

        // v1.7.1 — Makro-Indikatoren
        this.treasuryCurrent = sim.treasury();
        this.foodDays = LocalPrices.foodDays();

        // v1.7.2 — Militärökonomie STATS.BATTLE()
        // dataDivider()-Hinweis: BATTLE().WAR.data().getD() liefert laut Vanilla-Engine-Konvention
        // bereits einen normierten Wert im Bereich [0,1] — kein dataDivider()-Teiler nötig
        // (anders als FOOD_DAYS, das durch dataDivider() geteilt wird, s. LocalPrices.java:41).
        // WICHTIG: battleThreat ist derzeit ein totes Feld (kein UI, kein Indicator, kein System
        // referenziert es). Vor dem Anschließen: dataDivider()-Annahme mit Vanilla-Source verifizieren.
        double threat = 0.0;
        try {
            if (STATS.BATTLE() != null && STATS.BATTLE().WAR != null && STATS.BATTLE().WAR.data() != null) {
                threat = STATS.BATTLE().WAR.data().getD(null);
            }
        } catch (RuntimeException t) {
            // v1.7.3-Fix: War leerer Catch-Block ohne SEAM-Log — einzige Reflection-Stelle
            // im ganzen Mod ohne EventLog-Hook, entgegen README-Garantie "kein lautloser Crash".
            EventLog.log("BATTLE_STATS_ERROR", "STATS.BATTLE() reflection failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        this.battleThreat = threat;
    }
}
