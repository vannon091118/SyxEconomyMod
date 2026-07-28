package vannon.syx.economy.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 9 — 7-1a: Unit-Test-Coverage für alle public static Felder in EconConfig.
 * Jeder Test prüft Default-Werte, Range-Invarianten oder Clamping-Logik.
 */
class EconConfigTest {

    @AfterEach
    void resetConfig() {
        EconConfig.resetLaborDefaults();
        EconConfig.phaseFactorEnabled = true;
        EconConfig.phaseFactorThreshold = 300;
        EconConfig.phaseFactorMin = 0.5;
        EconConfig.population = 0;
        EconConfig.setOddjobWage(EconConfig.DEFAULT_ODDJOB_WAGE_PER_TASK);
        EconConfig.stateFundedWageRegulationOnly = false;
        EconConfig.wagesEnabled = true;
        EconConfig.foodAffordabilityGateEnabled = true;
        EconConfig.handoutWalletAmount = 50;
        EconConfig.resetWalletsOnLoad = false;
    }

    // ─── Wallet & Wealth ─────────────────────────────────────────────

    @Test
    void initialWalletDefaults() {
        assertEquals(5000, EconConfig.initialWallet);
        assertEquals(1000, EconConfig.immigrantWallet);
        assertEquals(0, EconConfig.newbornWallet);
        assertFalse(EconConfig.resetWalletsOnLoad);
    }

    @Test
    void escheatAndHeirDefaults() {
        assertTrue(EconConfig.escheatToPlayerTreasury);
        assertEquals(64, EconConfig.maxHeirSearchDepth);
    }

    @Test
    void wealthHappinessDefaults() {
        assertTrue(EconConfig.wealthAffectsHappiness);
        assertEquals(0.75, EconConfig.happinessAtPoorest, 1e-9);
        assertEquals(1.25, EconConfig.happinessAtRichest, 1e-9);
        assertEquals(2.0, EconConfig.relativeWealthMedians, 1e-9);
        assertEquals(30.0 / EconConfig.DEFAULT_TICKS_PER_DAY, EconConfig.medianRefreshDays, 1e-9);
    }

    // ─── Gini & Loyalty ───────────────────────────────────────────────

    @Test
    void giniLoyaltyDefaults() {
        assertTrue(EconConfig.giniAffectsLoyalty);
        assertEquals(0.85, EconConfig.loyaltyAtMaxGini, 1e-9);
    }

    // ─── Wages ────────────────────────────────────────────────────────

    @Test
    void wageDefaults() {
        assertTrue(EconConfig.wagesEnabled);
        assertEquals(50, EconConfig.defaultWage);
        assertEquals(200000, EconConfig.startingTreasury);
        assertEquals(1000, EconConfig.wageMax);
        assertEquals(5, EconConfig.wageStep);
    }

    @Test
    void wageRangeInvariant() {
        assertTrue(EconConfig.wageMax > EconConfig.defaultWage,
                "wageMax must be greater than defaultWage");
        assertTrue(EconConfig.defaultWage > 0);
    }

    @Test
    void allThirteenWageConstantsAreFifty() {
        assertEquals(50, EconConfig.militaryTrainingWagePerDay);
        assertEquals(50, EconConfig.exportDepotWagePerDay);
        assertEquals(50, EconConfig.haulerWagePerDay);
        assertEquals(50, EconConfig.armySupplyWagePerDay);
        assertEquals(50, EconConfig.laboratoryWagePerDay);
        assertEquals(50, EconConfig.libraryWagePerDay);
        assertEquals(50, EconConfig.embassyWagePerDay);
        assertEquals(50, EconConfig.waterWagePerDay);
        assertEquals(50, EconConfig.cannibalWagePerDay);
        assertEquals(50, EconConfig.policeWagePerDay);
        assertEquals(50, EconConfig.guardWagePerDay);
        assertEquals(50, EconConfig.stockadeWagePerDay);
        assertEquals(50, EconConfig.prisonWagePerDay);
    }

    @Test
    void resetLaborDefaultsRestoresAll() {
        EconConfig.defaultWage = 999;
        EconConfig.armySupplyWagePerDay = 999;
        EconConfig.transportFeePer100TileDay = 999;
        EconConfig.resetLaborDefaults();
        assertEquals(5, EconConfig.transportFeePer100TileDay);
        assertEquals(50, EconConfig.armySupplyWagePerDay);
    }

    @Test
    void transportFeeDefaults() {
        assertTrue(EconConfig.transportFeeEnabled);
        assertEquals(5, EconConfig.DEFAULT_TRANSPORT_FEE_PER_100_TILE_DAY);
        assertEquals(5, EconConfig.transportFeePer100TileDay);
    }

    // ─── Labor Market ─────────────────────────────────────────────────

    @Test
    void laborMarketDefaults() {
        assertTrue(EconConfig.laborMarketEnabled);
        assertEquals(10, EconConfig.laborNeutralPriority);
        assertEquals(2, EconConfig.laborFrictionPoints);
        assertEquals(50.0 / EconConfig.DEFAULT_TICKS_PER_DAY, EconConfig.laborRefreshDays, 1e-9);
    }

    // ─── Firm & Sizing ────────────────────────────────────────────────

    @Test
    void firmLedgerDefaults() {
        assertTrue(EconConfig.firmLedgerEnabled);
        assertTrue(EconConfig.firmSizingEnabled);
        assertEquals(1, EconConfig.minimumWorkersPerWorkplace);
    }

    @Test
    void guildSurplusDefaults() {
        assertEquals(0.25, EconConfig.guildSurplusShare, 1e-9);
        assertEquals(10.0, EconConfig.guildSurplusMinProfitPerWorker, 1e-9);
        assertEquals(6.0, EconConfig.profitElasticity, 1e-9);
    }

    @Test
    void firmSizingDefaults() {
        assertEquals(1, EconConfig.firmSizingHillclimbStep);
        assertEquals(1.0, EconConfig.firmSizingHysteresis, 1e-9);
        assertEquals(3600.0 / EconConfig.DEFAULT_TICKS_PER_DAY, EconConfig.firmSizingRefreshDays, 1e-9);
    }

    @Test
    void minimumWorkersIsNotZero() {
        assertTrue(EconConfig.minimumWorkersPerWorkplace >= 1,
                "minimumWorkers must be >= 1 to prevent cold-start death spiral");
    }

    @Test
    void guildSurplusShareInRange() {
        assertTrue(EconConfig.guildSurplusShare >= 0.0 && EconConfig.guildSurplusShare <= 1.0,
                "guildSurplusShare must be in [0, 1]");
    }

    // ─── Warehouse ────────────────────────────────────────────────────

    @Test
    void warehouseDefaults() {
        assertTrue(EconConfig.warehouseMarketEnabled);
        assertTrue(EconConfig.stateWarehousesEnabled);
        assertTrue(EconConfig.warehouseAutoTuneEnabled);
        assertTrue(EconConfig.warehouseAutoHireEnabled);
        assertEquals(50, EconConfig.stateWarehouseWage);
        assertEquals(10000, EconConfig.statePriceMax);
        assertEquals(75, EconConfig.DEFAULT_CROWN_MARKET_PRICE);
        assertEquals(0, EconConfig.warehouseTaxPercent);
    }

    // ─── Construction ─────────────────────────────────────────────────

    @Test
    void constructionDefaults() {
        assertTrue(EconConfig.autoProcureConstruction);
        assertEquals(1.5, EconConfig.autoProcurePremiumMultiplier, 1e-9);
        assertEquals(5000, EconConfig.maxAutoBuySpendPerTick);
        assertTrue(EconConfig.constructionHoardingEnabled);
    }

    // ─── Maintenance ──────────────────────────────────────────────────

    @Test
    void maintenanceDefaults() {
        assertTrue(EconConfig.maintenanceMarketEnabled);
        assertEquals(50.0 / EconConfig.DEFAULT_TICKS_PER_DAY, EconConfig.maintenanceRefreshDays, 1e-9);
        assertEquals(20, EconConfig.maintenanceBidBase);
        assertEquals(2, EconConfig.maintenanceWorkplacesPerJanitor);
        assertEquals(1000, EconConfig.productionSubsidyMax);
    }

    // ─── Food & Consumption ───────────────────────────────────────────

    @Test
    void foodGateDefaults() {
        assertTrue(EconConfig.foodAffordabilityGateEnabled);
        assertTrue(EconConfig.consumptionGateEnabled);
        assertTrue(EconConfig.firmInputGateEnabled);
        assertEquals(1, EconConfig.gateRoundingMargin);
    }

    @Test
    void grainDoleDefaults() {
        assertTrue(EconConfig.grainDoleEnabled);
        assertFalse(EconConfig.grainDoleToSlaves);
        assertEquals(500, EconConfig.doleWealthThreshold);
        assertEquals(100, EconConfig.doleHeadcap);
    }

    @Test
    void handoutDefaults() {
        assertTrue(EconConfig.handoutToWallet);
        assertEquals(50, EconConfig.handoutWalletAmount);
    }

    // ─── Service Market ───────────────────────────────────────────────

    @Test
    void serviceMarketDefaults() {
        assertTrue(EconConfig.serviceMarketEnabled);
        assertEquals(0.8, EconConfig.serviceUtilTarget, 1e-9);
        assertEquals(0.2, EconConfig.servicePriceUp, 1e-9);
        assertEquals(0.08, EconConfig.servicePriceDown, 1e-9);
        assertEquals(0.25, EconConfig.serviceBidWealthWeight, 1e-9);
        assertEquals(20, EconConfig.serviceBasePrice);
        assertEquals(1, EconConfig.servicePriceMin);
        assertEquals(2000, EconConfig.servicePriceMax);
    }

    @Test
    void servicePriceRangeInvariant() {
        assertTrue(EconConfig.servicePriceMin <= EconConfig.servicePriceMax,
                "servicePriceMin must be <= servicePriceMax");
    }

    // ─── Tax & Diplomacy ──────────────────────────────────────────────

    @Test
    void taxDefaults() {
        assertTrue(EconConfig.taxesEnabled);
        assertEquals(0, EconConfig.perHeadTax);
        assertEquals(0.05, EconConfig.marketTaxRate, 1e-9);
        assertEquals(500, EconConfig.perHeadTaxExemptionThreshold);
        assertEquals(0.5, EconConfig.taxHappinessAtFullRate, 1e-9);
        assertEquals(0.25, EconConfig.taxPainReference, 1e-9);
        assertEquals(0.05, EconConfig.taxPainFreeRate, 1e-9);
    }

    @Test
    void diplomacyDefaults() {
        assertTrue(EconConfig.diplomacyDebtBufferEnabled);
        assertEquals(-100000000L, EconConfig.diplomacyDebtThreshold);
        assertTrue(EconConfig.disableVanillaInflation);
    }

    // ─── Immigration ──────────────────────────────────────────────────

    @Test
    void immigrationDefaults() {
        assertEquals(0.35, EconConfig.meticImmigrationDepth, 1e-9);
        assertEquals(10.0, EconConfig.meticImmigrationSteepness, 1e-9);
    }

    // ─── Housing ──────────────────────────────────────────────────────

    @Test
    void housingDefaults() {
        assertTrue(EconConfig.housingMarketEnabled);
        assertEquals(1, EconConfig.housingBaseRentPerTile);
        assertEquals(100, EconConfig.housingEvictionDebtThreshold);
        assertEquals(3, EconConfig.housingGraceDays);
    }

    // ─── Property ─────────────────────────────────────────────────────

    @Test
    void propertyDefaults() {
        assertFalse(EconConfig.propertyMarketEnabled);
        assertFalse(EconConfig.homePurchaseEnabled);
        assertEquals(20.0, EconConfig.homePriceMultiplier, 1e-9);
        assertFalse(EconConfig.workplaceSharesEnabled);
        assertEquals(12.0, EconConfig.firmPriceMultiplier, 1e-9);
        assertEquals(50, EconConfig.maxSharesPerFirm);
        assertEquals(5, EconConfig.progressiveShareStep);
        assertEquals(10, EconConfig.minSharesPerFirm);
        assertEquals(0.30, EconConfig.dividendRate, 1e-9);
        assertEquals(0.15, EconConfig.propertyHappinessBoost, 1e-9);
    }

    // ─── Debt & Slavery ───────────────────────────────────────────────

    @Test
    void debtDefaults() {
        assertTrue(EconConfig.debtSlaveryEnabled);
        assertEquals(5000, EconConfig.debtSlaveThreshold);
        assertEquals(0.6, EconConfig.unpaidHappiness, 1e-9);
    }

    // ─── Military ─────────────────────────────────────────────────────

    @Test
    void militaryPayrollDefault() {
        assertTrue(EconConfig.militaryPayrollEnabled);
    }

    // ─── Prices & Flow ────────────────────────────────────────────────

    @Test
    void flowPricingDefaults() {
        assertTrue(EconConfig.flowPricingEnabled);
        assertEquals(1.0, EconConfig.flowSmoothingDays, 1e-9);
        assertEquals(5.0, EconConfig.constructionSmoothingDays, 1e-9);
        assertEquals(1.0, EconConfig.flowLookaheadDays, 1e-9);
        assertEquals(1.0, EconConfig.flowDefaultTargetCoverageDays, 1e-9);
    }

    @Test
    void scarcityDefaults() {
        assertEquals(1.5, EconConfig.scarcityMaxMultiple, 1e-9);
        assertEquals(1.0, EconConfig.scarcitySteepness, 1e-9);
        assertEquals(0.8, EconConfig.scarcityElasticityUp, 1e-9);
        assertEquals(1.375, EconConfig.scarcityElasticityDown, 1e-9);
        assertEquals(0.3, EconConfig.scarcityPriceBoost, 1e-9);
        assertEquals(0.4, EconConfig.scarcityLaborBoost, 1e-9);
    }

    @Test
    void priceClampDefaults() {
        assertEquals(0.001, EconConfig.priceClampLo, 1e-9);
        assertEquals(100.0, EconConfig.priceClampHi, 1e-9);
        assertEquals(50000.0, EconConfig.priceAbsoluteMax, 1e-9);
    }

    @Test
    void priceClampRangeInvariant() {
        assertTrue(EconConfig.priceClampHi >= EconConfig.priceClampLo,
                "priceClampHi must be >= priceClampLo");
        assertTrue(EconConfig.priceClampLo > 0.0,
                "priceClampLo must be positive");
    }

    @Test
    void targetStockDaysDefaults() {
        assertEquals(6.0, EconConfig.targetFoodDays, 1e-9);
        assertEquals(24.0, EconConfig.FOOD_DAYS_MAX, 1e-9);
        assertEquals(6.0, EconConfig.targetDrinkDays, 1e-9);
        assertEquals(1.0, EconConfig.targetGoodsCoverage, 1e-9);
    }

    @Test
    void chargeForGoodsDefault() {
        assertTrue(EconConfig.chargeForGoods);
    }

    // ─── D-001: Food Price Absolute Max ───────────────────────────────

    @Test
    void d001_foodPriceCapMultiplierDefault() {
        assertEquals(6.0, EconConfig.foodPriceCapMultiplier, 1e-9,
                "D-001: foodPriceCapMultiplier must be 6.0 (anker-relativ)");
    }

    @Test
    void d001_foodCapMultiplierIsPositive() {
        assertTrue(EconConfig.foodPriceCapMultiplier > 0.0,
                "food price cap multiplier must be positive");
    }

    // ─── Phase Factor ─────────────────────────────────────────────────

    @Test
    void phaseFactorDefaults() {
        assertTrue(EconConfig.phaseFactorEnabled);
        assertEquals(300, EconConfig.phaseFactorThreshold);
        assertEquals(0.5, EconConfig.phaseFactorMin, 1e-9);
    }

    // ─── Sharding ─────────────────────────────────────────────────────

    @Test
    void shardingDefaults() {
        assertEquals(4, EconConfig.planControllerShardCount);
    }

    // ─── Lambda & Exchange ────────────────────────────────────────────

    @Test
    void lambdaDefaults() {
        assertTrue(EconConfig.heterogeneousLambda);
        assertEquals(0.0, EconConfig.lambdaMin, 1e-9);
        assertEquals(0.99, EconConfig.lambdaMax, 1e-9);
        assertEquals(0.002, EconConfig.alpha, 1e-9);
        assertEquals(200.0, EconConfig.encountersPerGameSecond, 1e-9);
    }

    @Test
    void pairModeDefaults() {
        assertEquals(EconConfig.PairMode.PROXIMITY, EconConfig.pairMode);
        assertEquals(32, EconConfig.proximityRadiusPx);
    }

    // ─── Audit & Diagnostics ──────────────────────────────────────────

    @Test
    void auditDefaults() {
        assertTrue(EconConfig.checkConservation);
        assertEquals(20, EconConfig.roundingDriftThreshold);
        assertEquals(0.0, EconConfig.dumpIntervalDays, 1e-9);
    }

    @Test
    void debugDefaults() {
        assertTrue(EconConfig.debugLoggingEnabled);
        assertTrue(EconConfig.debugTracing);
        assertTrue(EconConfig.debugPriceLogging);
        assertFalse(EconConfig.diagnosticsExportEnabled);
        assertTrue(EconConfig.debugFurnitureDump);
        assertEquals(50, EconConfig.debugFurnitureDumpEveryTicks);
    }

    // ─── Citizen Classes ──────────────────────────────────────────────

    @Test
    void citizenClassesDefault() {
        assertTrue(EconConfig.citizenClassesEnabled);
    }

    // ─── Poverty Pressure ─────────────────────────────────────────────

    @Test
    void povertyPressureDefaults() {
        assertTrue(EconConfig.povertyPressureEnabled);
        assertEquals(500, EconConfig.povertyPressureWealthThreshold);
        assertEquals(0.5, EconConfig.povertyPressureHappinessMin, 1e-9);
    }

    // ─── Hunger ───────────────────────────────────────────────────────

    @Test
    void hungerDefaults() {
        assertEquals(80, EconConfig.hungerDeathThreshold);
        assertEquals(2, EconConfig.hungerDamageRate);
    }

    // ─── Window & UI ──────────────────────────────────────────────────

    @Test
    void windowDefault() {
        assertTrue(EconConfig.windowEnabled);
    }

    // ─── Oddjob ───────────────────────────────────────────────────────

    @Test
    void oddjobDefaults() {
        assertTrue(EconConfig.oddjobWageEnabled);
        assertTrue(EconConfig.oddjobAutoTuneEnabled);
        assertEquals(3, EconConfig.DEFAULT_ODDJOB_WAGE_PER_TASK);
        assertEquals(250, EconConfig.ODDJOB_WAGE_MAX);
        assertEquals(0.75, EconConfig.oddjobWageCeilingRatio, 1e-9);
    }

    @Test
    void setOddjobWageClampsToCeiling() {
        EconConfig.defaultWage = 50;
        EconConfig.setOddjobWage(999);
        int ceiling = (int)(EconConfig.defaultWage * EconConfig.oddjobWageCeilingRatio);
        assertEquals(ceiling, EconConfig.oddjobWagePerTask,
                "setOddjobWage must clamp to defaultWage * oddjobWageCeilingRatio");
    }

    @Test
    void setOddjobWagePreservesValidValue() {
        EconConfig.defaultWage = 50;
        EconConfig.setOddjobWage(5);
        assertEquals(5, EconConfig.oddjobWagePerTask);
    }

    @Test
    void setOddjobWageClampsToZeroForNegative() {
        EconConfig.defaultWage = 50;
        EconConfig.setOddjobWage(-1);
        assertEquals(0, EconConfig.oddjobWagePerTask,
                "negative wage must be clamped to 0");
    }

    // ─── Room Operating Mode ──────────────────────────────────────────

    @Test
    void roomOperatingModeDefaults() {
        assertFalse(EconConfig.stateFundedWageRegulationOnly);
        assertEquals(EconConfig.RoomOperatingMode.PAUSED, EconConfig.stateRoomDefaultOpMode);
        assertEquals(0.3, EconConfig.mothballOperatingCostMultiplier, 1e-9);
    }

    @Test
    void roomOperatingModeEnumHasThreeValues() {
        assertEquals(3, EconConfig.RoomOperatingMode.values().length);
    }

    // ─── Corvee ───────────────────────────────────────────────────────

    @Test
    void corveeDefaults() {
        assertTrue(EconConfig.corveeEnabled);
        assertEquals(20, EconConfig.corveeDraftPercent);
        assertEquals(9999, EconConfig.corveeDraftMax);
        assertEquals(100, EconConfig.corveePopThreshold);
        assertEquals(500, EconConfig.corveePopFullScale);
        assertNotNull(EconConfig.corveeExemptRoomKeys);
        assertTrue(EconConfig.corveeExemptRoomKeys.length > 0);
    }

    // ─── Religion ─────────────────────────────────────────────────────

    @Test
    void religionDefaults() {
        assertTrue(EconConfig.religionTaxEnabled);
        assertEquals(5, EconConfig.religionHeadTaxDefault);
        assertTrue(EconConfig.liturgyEnabled);
        assertEquals(0.1, EconConfig.liturgyRate, 1e-9);
        assertEquals(1, EconConfig.liturgyHeadcount);
        assertEquals(1, EconConfig.liturgyIntervalSeasons);
    }

    // ─── Slave / Misc ─────────────────────────────────────────────────

    @Test
    void slaveWageDefaults() {
        assertFalse(EconConfig.payWagesToSlaves);
    }

    @Test
    void ticksPerGameDay() {
        assertEquals(300, EconConfig.ticksPerGameDay);
    }

    @Test
    void affinityWageBonusMax() {
        assertEquals(1.15, EconConfig.affinityWageBonusMax, 1e-9);
    }

    // ─── Conflict Warning ─────────────────────────────────────────────

    @Test
    void conflictWarningNullInDefaultConfig() {
        assertNull(EconConfig.conflictWarning());
    }

    @Test
    void conflictWarningOnStateRegulationWithoutWages() {
        EconConfig.stateFundedWageRegulationOnly = true;
        EconConfig.wagesEnabled = false;
        assertNotNull(EconConfig.conflictWarning());
    }

    @Test
    void conflictWarningOnLargeHandoutWithGate() {
        EconConfig.foodAffordabilityGateEnabled = true;
        EconConfig.handoutWalletAmount = 500;
        assertNotNull(EconConfig.conflictWarning());
    }

    // ─── Clamp ────────────────────────────────────────────────────────

    @Test
    void clampIntAtBounds() {
        assertEquals(5, EconConfig.clamp(5, 0, 10));
        assertEquals(0, EconConfig.clamp(-5, 0, 10));
        assertEquals(10, EconConfig.clamp(50, 0, 10));
    }

    @Test
    void clampDoubleAtBounds() {
        assertEquals(0.5, EconConfig.clamp(0.5, 0.0, 1.0), 1e-9);
        assertEquals(0.0, EconConfig.clamp(-0.1, 0.0, 1.0), 1e-9);
        assertEquals(1.0, EconConfig.clamp(99.0, 0.0, 1.0), 1e-9);
    }

    @Test
    void ticksPerDayConstantIsPositive() {
        assertTrue(EconConfig.DEFAULT_TICKS_PER_DAY > 0);
        assertEquals(300.0, EconConfig.DEFAULT_TICKS_PER_DAY, 1e-9);
    }
}
