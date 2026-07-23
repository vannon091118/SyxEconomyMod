package vannon.syx.economy.core;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für die 6-stufige TreasuryCrisis-Mechanik inkl. Hysterese.
 *
 * <p>Hysterese: Aktivierung bei strengeren Schwellen (−5K/−50K/−250K/−1M/−5M),
 * Recovery erst bei höheren Schwellen (>0/>−10K/>−50K/>−250K/>−1M).
 * Bürger spüren die Krise schneller als die Erholung — so kann die
 * Treasury nicht zwischen zwei Ticks hin- und herpendeln.</p>
 *
 * <p>Läuft ohne Spiel-Engine — EconConfig-Werte werden direkt gesetzt.
 * Kein SongsOfSyx.jar nötig.</p>
 */
class TreasuryCrisisTest {

    /* ── Originalwerte (Backup/Restore) ───────────────────────────────── */

    private int origDefaultWage;
    private int origPerHeadTax;
    private double origMarketTaxRate;
    private int origDoleHeadcap;
    private boolean origPropertyMarketEnabled;
    private boolean origCorveeEnabled;
    private boolean origGrainDoleEnabled;
    private boolean origDebtSlaveryEnabled;
    private boolean origWagesEnabled;
    private boolean origStateWarehousesEnabled;
    private int origProductionSubsidyMax;
    private boolean origTransportFeeEnabled;
    private boolean origHandoutToWallet;
    private int origHandoutWalletAmount;
    private boolean origAutoProcureConstruction;
    private double origLoyaltyAtMaxGini;

    @BeforeEach
    void resetAllState() {
        // EconConfig auf bekannte Defaults
        EconConfig.defaultWage       = 50;
        EconConfig.perHeadTax        = 0;
        EconConfig.marketTaxRate     = 0.05;
        EconConfig.stateWarehouseWage = 50;
        EconConfig.doleHeadcap       = 100;
        EconConfig.propertyMarketEnabled = false;
        EconConfig.homePurchaseEnabled   = false;
        EconConfig.corveeEnabled     = true;
        EconConfig.grainDoleEnabled  = true;
        EconConfig.debtSlaveryEnabled = true;
        EconConfig.wagesEnabled      = true;
        EconConfig.stateWarehousesEnabled = true;
        EconConfig.warehouseAutoTuneEnabled = true;
        EconConfig.autoProcureConstruction = true;
        EconConfig.productionSubsidyMax    = 1000;
        EconConfig.transportFeeEnabled     = true;
        EconConfig.handoutToWallet   = true;
        EconConfig.handoutWalletAmount = 400;
        EconConfig.laborMarketEnabled = true;
        EconConfig.firmLedgerEnabled  = true;
        EconConfig.serviceMarketEnabled = true;
        EconConfig.maintenanceMarketEnabled = true;
        EconConfig.consumptionGateEnabled = true;
        EconConfig.warehouseMarketEnabled = true;
        EconConfig.loyaltyAtMaxGini = 0.85;

        // Alle 15 Lohnkonstanten auf 50
        EconConfig.militaryTrainingWagePerDay = 50;
        EconConfig.exportDepotWagePerDay  = 50;
        EconConfig.haulerWagePerDay       = 50;
        EconConfig.armySupplyWagePerDay   = 50;
        EconConfig.laboratoryWagePerDay   = 50;
        EconConfig.libraryWagePerDay      = 50;
        EconConfig.embassyWagePerDay      = 50;
        EconConfig.waterWagePerDay        = 50;
        EconConfig.cannibalWagePerDay     = 50;
        EconConfig.policeWagePerDay       = 50;
        EconConfig.guardWagePerDay        = 50;
        EconConfig.stockadeWagePerDay     = 50;
        EconConfig.prisonWagePerDay       = 50;

        // TreasuryCrisis komplett zurücksetzen — Recovery von vorherigen Tests
        forceRecoveryToTier0();
    }

    /** Erzwingt vollständige Recovery auf Tier 0 (clean slate). */
    private static void forceRecoveryToTier0() {
        TreasuryCrisis.update(1_000_000L, null);
    }

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  Schwellen (Aktivierung)                                           */
    /* ═══════════════════════════════════════════════════════════════════ */

    @Test
    void classify_aboveZero_isTier0() {
        updateTo(100_000L);
        assertEquals(0, TreasuryCrisis.activeTier());
    }

    @Test
    void classify_atMinus3k_isTier0() {
        updateTo(-3_000L);
        assertEquals(0, TreasuryCrisis.activeTier(), "−3K > −5K → Tier 0");
    }

    @Test
    void classify_atMinus5k_isTier1() {
        updateTo(-5_000L);
        assertEquals(1, TreasuryCrisis.activeTier());
    }

    @Test
    void classify_atMinus50k_isTier2() {
        updateTo(-50_000L);
        assertEquals(2, TreasuryCrisis.activeTier());
    }

    @Test
    void classify_atMinus250k_isTier3() {
        updateTo(-250_000L);
        assertEquals(3, TreasuryCrisis.activeTier());
    }

    @Test
    void classify_atMinus1M_isTier4() {
        updateTo(-1_000_000L);
        assertEquals(4, TreasuryCrisis.activeTier());
    }

    @Test
    void classify_atMinus5M_isTier5() {
        updateTo(-5_000_000L);
        assertEquals(5, TreasuryCrisis.activeTier());
    }

    @Test
    void classify_atMinus900M_isTier5_hardFloor() {
        updateTo(-900_000_000L);
        assertEquals(5, TreasuryCrisis.activeTier());
        assertTrue(TreasuryCrisis.isHardFloor());
    }

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  Hysterese — Recovery erst oberhalb der Recovery-Schwelle          */
    /* ═══════════════════════════════════════════════════════════════════ */

    @Test
    void hysteresis_tier1RecoversAboveZero() {
        updateTo(-5_000L);
        assertEquals(1, TreasuryCrisis.activeTier());
        updateTo(1L);
        assertEquals(0, TreasuryCrisis.activeTier(), "Tier 1 Recovery > 0");
    }

    @Test
    void hysteresis_tier1staysActiveAtMinus1k() {
        updateTo(-5_000L);
        assertEquals(1, TreasuryCrisis.activeTier());
        // −1K → classify()=0, aber classifyRecovery()=1 (weil −1K ≤ 0=TIER1_RECOVERY)
        // → Hält Tier 1 (noch nicht über Recovery-Schwelle)
        updateTo(-1_000L);
        assertEquals(1, TreasuryCrisis.activeTier(), "−1K: classify=0 aber Recovery=1 → Tier 1 bleibt");
    }

    @Test
    void hysteresis_tier2recoversAboveMinus10k() {
        updateTo(-50_000L);
        assertEquals(2, TreasuryCrisis.activeTier());
        // −9K: classify=1, classifyRecovery=1 → recoveryTier=1 < activeTier=2 → deactivate to 1
        updateTo(-9_000L);
        assertEquals(1, TreasuryCrisis.activeTier(), "−9K > −10K → Tier 1 (Recovery von Tier 2)");
    }

    @Test
    void hysteresis_tier2staysAtMinus20k() {
        updateTo(-50_000L);
        assertEquals(2, TreasuryCrisis.activeTier());
        updateTo(-20_000L); // classify=1, classifyRecovery=2 (weil −20K ≤ −10K)
        assertEquals(2, TreasuryCrisis.activeTier(), "−20K: Recovery noch nicht erreicht → Tier 2 bleibt");
    }

    @Test
    void hysteresis_tier3recoversAboveMinus50k() {
        updateTo(-250_000L);
        assertEquals(3, TreasuryCrisis.activeTier());
        // −40K: classify=1 (weil ≤−5K), classifyRecovery=2 (weil ≤−10K)
        // activeTier=3, recoveryTier=2 < 3 → deactivateTiers(2)
        updateTo(-40_000L);
        assertEquals(2, TreasuryCrisis.activeTier(), "−40K: classify=1, classifyRecovery=2 → Tier 2");
    }

    @Test
    void hysteresis_tier4recoversAboveMinus250k() {
        updateTo(-1_000_000L);
        assertEquals(4, TreasuryCrisis.activeTier());
        // −200K: classify=2 (weil ≤−50K), classifyRecovery=3 (weil ≤−50K, aber >−250K)
        // activeTier=4, recoveryTier=3 < 4 → deactivateTiers(3)
        updateTo(-200_000L);
        assertEquals(3, TreasuryCrisis.activeTier(), "−200K: classify=2, classifyRecovery=3 → Tier 3");
    }

    @Test
    void hysteresis_tier5recoversAboveMinus1M() {
        updateTo(-5_000_000L);
        assertEquals(5, TreasuryCrisis.activeTier());
        updateTo(-500_000L); // classify=3, classifyRecovery=4 (weil −500K ≤ −250K? nein, −500K ≤ −1M? nein, ≤ −250K? ja!)
        // classify(-500K): ≤ −5M? no, ≤ −1M? no, ≤ −250K? YES → classify=3
        // classifyRecovery(-500K): ≤ −1M? no, ≤ −250K? YES → classifyRecovery=4? 
        // Wait: TIER4_RECOVERY = -250K. classifyRecovery: if (treasury <= -1M) 5, if (treasury <= -250K) 4, if (treasury <= -50K) 3...
        // -500K <= -250K = true → classifyRecovery = 4. activeTier=5, recoveryTier=4 < 5 → deactivate to 4.
        assertEquals(4, TreasuryCrisis.activeTier(), "−500K: classify=3, classifyRecovery=4 → Tier 4");
    }

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  Eskalation                                                        */
    /* ═══════════════════════════════════════════════════════════════════ */

    @Test
    void escalation_tier0toTier5_disablesSystems() {
        updateTo(-10_000_000L);
        assertEquals(5, TreasuryCrisis.activeTier());

        // Tier 4 Maßnahmen
        assertFalse(EconConfig.corveeEnabled, "Tier 4 → Corvée deaktiviert");
        assertFalse(EconConfig.grainDoleEnabled, "Tier 4 → GrainDole deaktiviert");
        assertFalse(EconConfig.debtSlaveryEnabled, "Tier 4 → Schuldknechtschaft deaktiviert");
        assertFalse(EconConfig.wagesEnabled, "Tier 4 → Wages deaktiviert");

        // Tier 5 Maßnahmen
        assertFalse(EconConfig.stateWarehousesEnabled, "Tier 5 → StateWarehouses deaktiviert");
        assertFalse(EconConfig.laborMarketEnabled, "Tier 5 → LaborMarket deaktiviert");
    }

    @Test
    void escalation_tier1disablesNonEssentialSpending() {
        updateTo(-10_000L);
        assertEquals(1, TreasuryCrisis.activeTier());

        assertEquals(0, EconConfig.productionSubsidyMax, "Subsidies auf 0");
        assertFalse(EconConfig.transportFeeEnabled, "TransportFee deaktiviert");
        assertFalse(EconConfig.handoutToWallet, "Handout deaktiviert");
        assertFalse(EconConfig.warehouseAutoTuneEnabled, "WarehouseAutoTune deaktiviert");
        assertFalse(EconConfig.autoProcureConstruction, "AutoProcure deaktiviert");
    }

    @Test
    void tier2_halvesAllWages() {
        updateTo(-60_000L);
        assertEquals(2, TreasuryCrisis.activeTier());

        assertEquals(25, EconConfig.defaultWage, "defaultWage halbiert");
        assertEquals(25, EconConfig.stateWarehouseWage, "stateWarehouseWage halbiert");
        assertEquals(25, EconConfig.militaryTrainingWagePerDay, "alle 13 state wages halbiert");
        assertEquals(0.20, EconConfig.marketTaxRate, 0.001, "Marktsteuer 0.05+0.15");
        assertEquals(50, EconConfig.doleHeadcap, "DoleHeadcap halbiert");
    }

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  savedPerHeadTax Regression (Bugfix)                               */
    /* ═══════════════════════════════════════════════════════════════════ */

    @Test
    void savedPerHeadTax_neverZero_afterTier4Recovery() {
        EconConfig.perHeadTax = 100;
        updateTo(-2_000_000L);
        assertEquals(4, TreasuryCrisis.activeTier());
        assertEquals(500, EconConfig.perHeadTax, "Tier 4 → Kopfsteuer auf 500");

        updateTo(1_000_000L);
        assertEquals(0, TreasuryCrisis.activeTier());
        assertEquals(100, EconConfig.perHeadTax,
                "perHeadTax restored to 100, NOT 0");
    }

    @Test
    void savedPerHeadTax_neverZero_Tier2thenTier4thenRecovery() {
        EconConfig.perHeadTax = 50;
        updateTo(-60_000L);
        assertEquals(2, TreasuryCrisis.activeTier());

        updateTo(-2_000_000L);
        assertEquals(4, TreasuryCrisis.activeTier());
        assertEquals(500, EconConfig.perHeadTax, "Tier 4 → 500");

        updateTo(1_000_000L);
        assertEquals(0, TreasuryCrisis.activeTier());
        assertEquals(50, EconConfig.perHeadTax,
                "BUGFIX: perHeadTax restored to 50 after Tier 2→4→Recovery");
    }

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  Hard Floor + vollständige Recovery                                */
    /* ═══════════════════════════════════════════════════════════════════ */

    @Test
    void tier5_hardFloor_allSystemsDisabled() {
        updateTo(-10_000_000L);
        assertEquals(5, TreasuryCrisis.activeTier());
        assertTrue(TreasuryCrisis.isHardFloor());

        assertFalse(EconConfig.stateWarehousesEnabled);
        assertFalse(EconConfig.warehouseMarketEnabled);
        assertFalse(EconConfig.laborMarketEnabled);
        assertEquals(0.50, EconConfig.loyaltyAtMaxGini, 0.001);
    }

    @Test
    void fullRecovery_restoresAllDefaults() {
        updateTo(-10_000_000L);
        assertEquals(5, TreasuryCrisis.activeTier());

        updateTo(1_000_000L);
        assertEquals(0, TreasuryCrisis.activeTier());
        assertFalse(TreasuryCrisis.isInCrisis());

        // Tier 1 Recovery
        assertEquals(1000, EconConfig.productionSubsidyMax);
        assertTrue(EconConfig.transportFeeEnabled);
        assertTrue(EconConfig.handoutToWallet);
        assertEquals(400, EconConfig.handoutWalletAmount);

        // Tier 2 Recovery
        assertEquals(50, EconConfig.defaultWage);

        // Tier 4 Recovery
        assertTrue(EconConfig.wagesEnabled);

        // Tier 5 Recovery
        assertTrue(EconConfig.stateWarehousesEnabled);
        assertEquals(0.85, EconConfig.loyaltyAtMaxGini, 0.001);
    }

    @Test
    void isInCrisis_falseAboveZero() {
        updateTo(100_000L);
        assertFalse(TreasuryCrisis.isInCrisis());
        assertFalse(TreasuryCrisis.isHardFloor());
    }

    @Test
    void isInCrisis_trueAtTier1() {
        updateTo(-10_000L);
        assertTrue(TreasuryCrisis.isInCrisis());
        assertFalse(TreasuryCrisis.isHardFloor());
    }

    /* ═══════════════════════════════════════════════════════════════════ */
    /*  Helper                                                             */
    /* ═══════════════════════════════════════════════════════════════════ */

    private static void updateTo(long treasury) {
        TreasuryCrisis.update(treasury, null);
    }
}
