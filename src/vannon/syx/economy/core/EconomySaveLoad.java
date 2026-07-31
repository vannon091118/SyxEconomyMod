package vannon.syx.economy.core;

import java.io.IOException;
import snake2d.LOG;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

/**
 * Extracted from EconomySim (Spluck-TECHD-01): all save/load logic,
 * TAG constants, and chunked/legacy format handlers.
 *
 * <p>Each method receives the EconomySim instance to access its
 * package-private fields directly (same package).</p>
 */
final class EconomySaveLoad {

    static final int CHUNKED_VERSION = 33;
    private static final int CHUNK_MAGIC = 0xEC0FEC0F;

    /**
     * Chunk tags for the save format. Nested class to keep top-level
     * field count under God-Class-Guard threshold (Rule 14).
     * Tags are arbitrary positive ints, order-independent.
     */
    static final class Tags {
        static final int CORE_SCALARS = 1;
        static final int ECON_CONFIG = 2;
        static final int WAGES = 3;
        static final int TAXES = 4;
        static final int FISCAL = 5;
        static final int LABOR_MARKET = 6;
        static final int MAINTENANCE_MARKET = 7;
        static final int GRAIN_DOLE = 8;
        static final int RELIGION_MARKET = 9;
        static final int LITURGY = 10;
        static final int DEBT_BONDAGE = 11;
        static final int MILITARY_PAYROLL = 12;
        static final int PRODUCTION_SUBSIDIES = 13;
        static final int STATE_WAREHOUSES = 14;
        static final int WAREHOUSE_MARKET = 15;
        static final int STATE_WAGES = 16;
        static final int PROGRESSION = 17;
        static final int CORVEE = 18;
        static final int HOUSING = 19;
        static final int FOREIGN_TRADE_LEDGER = 20;
        static final int TREASURY_CRISIS = 21;
        static final int END = 0x7FFFFFFF;
        private Tags() {}
    }

    private EconomySaveLoad() {}

    // ── Public entry points ────────────────────────────────────────

    /**
     * Resets all economy subsystems and scalar counters.
     * Extracted from EconomySim.resetEconomy() (Sprint E2).
     * Called after load when {@code EconConfig.resetWalletsOnLoad} is true,
     * and by {@code EconomySim.clearActive()} teardown.
     */
    static void resetAll(EconomySim sim) {
        sim.wallets.reset();
        sim.wages.clear();
        sim.taxes.clear();
        sim.purchases.reset();
        sim.grainDole.clear();
        sim.fiscal.clear();
        sim.laborMarket.clear();
        sim.firmLedger.clear();
        sim.maintenanceMarket.clear();
        sim.serviceMarket.clear();
        if (sim.servicePlanController != null) sim.servicePlanController.clear();
        sim.housingMarket.clear();
        sim.affordabilityGate.clear();
        sim.flowMeter.clear();
        sim.flowPrices.clear();
        sim.scarcitySignal.clear();
        sim.warehouseMarket.clear();
        sim.stateWarehouses.clear();
        sim.religionMarket.clear();
        sim.liturgy.clear();
        sim.debtBondage.clear();
        sim.oddjobMarket.clear();
        sim.militaryPayroll.clear();
        sim.stateWages.clear();
        sim.transportMarket.clear();
        sim.handoutRelief.clear();
        sim.productionSubsidies.clear();
        LocalPrices.clearCache();
        sim.escheated = 0L;
        sim.exported = 0L;
        sim.imported = 0L;
        sim.seedSupply = 0L;
        sim.spent = 0L;
        sim.taxesCollected = 0L;
        sim.guildIncomePaid = 0L;
        sim.liturgyCollected = 0L;
        sim.religionTaxCollected = 0L;
        sim.warehouseTaxCollected = 0L;
        sim.wagesPaid = 0L;
        sim.housingRentCollected = 0L;
        sim.propertyMarket.reset();
        sim.lastTaxSeason = -1;
        sim.roundingDrift = 0L;
        sim.reportedAuditDelta = 0L;
    }

    static void save(EconomySim sim, FilePutter file) {
        sim.wallets.save(file);
        saveChunked(sim, file);
    }

    static void load(EconomySim sim, FileGetter file) throws IOException {
        int version = sim.wallets.load(file);
        if (version >= CHUNKED_VERSION) {
            loadChunked(sim, file);
        } else {
            loadLegacy(sim, file, version);
        }
        if (EconConfig.resetWalletsOnLoad) {
            resetAll(sim);
            if (EconConfig.debugLoggingEnabled) {
                LOG.ln("[ECON] resetWalletsOnLoad=true -> wallets wiped; everyone will be re-seeded with " + EconConfig.initialWallet);
            }
        }
        IdentityMapRegistry.clearOnLoad("Load (version " + version + ")");
    }

    // ── Chunked save ───────────────────────────────────────────────

    private static void saveChunked(EconomySim sim, FilePutter file) {
        int pos;
        file.i(CHUNK_MAGIC);

        pos = ChunkedSave.startChunk(file, Tags.CORE_SCALARS);
        file.l(sim.seedSupply);
        file.l(sim.imported);
        file.l(sim.exported);
        file.l(sim.escheated);
        file.l(sim.guildIncomePaid);
        file.l(sim.taxesCollected);
        file.l(sim.spent);
        file.l(sim.roundingDrift);
        file.l(sim.warehouseTaxCollected);
        file.l(sim.religionTaxCollected);
        file.l(sim.liturgyCollected);
        file.l(sim.wagesPaid);
        file.i(sim.deaths);
        file.i(sim.emigrations);
        file.i(sim.inherited);
        file.i(sim.heirless);
        file.i(sim.lastTaxSeason);
        file.i(sim.ticks);
        file.i(sim.econIndicatorTickCounter);
        file.d(sim.encounterCarry);
        file.l(sim.reportedAuditDelta);
        file.l(sim.housingRentCollected);
        file.l(sim.propertyMarket.salesCollected());
        file.l(sim.propertyMarket.dividendsPaid());
        file.i(sim.propertyMarket.lastSeason());
        ChunkedSave.endChunk(file, pos);

        pos = ChunkedSave.startChunk(file, Tags.ECON_CONFIG);
        file.bool(EconConfig.debtSlaveryEnabled);
        file.i(EconConfig.debtSlaveThreshold);
        file.bool(EconConfig.oddjobWageEnabled);
        file.i(EconConfig.oddjobWagePerTask);
        file.bool(EconConfig.transportFeeEnabled);
        file.i(EconConfig.transportFeePer100TileDay);
        file.i(EconConfig.perHeadTax);
        file.i((int) Math.round(EconConfig.marketTaxRate * 100.0));
        file.i(EconConfig.warehouseTaxPercent);
        file.bool(EconConfig.taxesEnabled);
        file.bool(EconConfig.religionTaxEnabled);
        file.bool(EconConfig.liturgyEnabled);
        file.i((int) Math.round(EconConfig.liturgyRate * 10000.0));
        file.i(EconConfig.liturgyHeadcount);
        file.i(EconConfig.liturgyIntervalSeasons);
        file.bool(EconConfig.autoProcureConstruction);
        file.i((int) Math.round(EconConfig.autoProcurePremiumMultiplier * 100.0));
        file.i(EconConfig.maxAutoBuySpendPerTick);
        file.bool(EconConfig.constructionHoardingEnabled);
        file.i((int) Math.round(EconConfig.constructionSmoothingDays * 100.0));
        file.bool(EconConfig.firmInputGateEnabled);
        ChunkedSave.endChunk(file, pos);

        saveSubsystemChunk(file, Tags.WAGES, sim.wages);
        saveSubsystemChunk(file, Tags.TAXES, sim.taxes);
        saveSubsystemChunk(file, Tags.FISCAL, sim.fiscal);
        saveSubsystemChunk(file, Tags.LABOR_MARKET, sim.laborMarket);
        saveSubsystemChunk(file, Tags.MAINTENANCE_MARKET, sim.maintenanceMarket);
        saveSubsystemChunk(file, Tags.GRAIN_DOLE, sim.grainDole);
        saveSubsystemChunk(file, Tags.RELIGION_MARKET, sim.religionMarket);
        saveSubsystemChunk(file, Tags.LITURGY, sim.liturgy);
        saveSubsystemChunk(file, Tags.DEBT_BONDAGE, sim.debtBondage);
        saveSubsystemChunk(file, Tags.MILITARY_PAYROLL, sim.militaryPayroll);
        saveSubsystemChunk(file, Tags.PRODUCTION_SUBSIDIES, sim.productionSubsidies);
        saveSubsystemChunk(file, Tags.STATE_WAREHOUSES, sim.stateWarehouses);
        saveSubsystemChunk(file, Tags.WAREHOUSE_MARKET, sim.warehouseMarket);
        saveSubsystemChunk(file, Tags.PROGRESSION, sim.progression);
        saveSubsystemChunk(file, Tags.HOUSING, sim.housingMarket);
        saveSubsystemChunk(file, Tags.FOREIGN_TRADE_LEDGER, sim.foreignTradeLedger);
        saveCorveeChunk(file);
        saveStateWagesChunk(sim, file);

        pos = ChunkedSave.startChunk(file, Tags.TREASURY_CRISIS);
        TreasuryCrisis.save(file);
        ChunkedSave.endChunk(file, pos);

        int endPos = ChunkedSave.startChunk(file, Tags.END);
        ChunkedSave.endChunk(file, endPos);
    }

    private static void saveSubsystemChunk(FilePutter file, int tag, Saveable saveable) {
        int pos = ChunkedSave.startChunk(file, tag);
        saveable.save(file);
        ChunkedSave.endChunk(file, pos);
    }

    private static void saveStateWagesChunk(EconomySim sim, FilePutter file) {
        int pos = ChunkedSave.startChunk(file, Tags.STATE_WAGES);
        StateWageMarket.Entry[] entries = sim.stateWages.entries();
        file.i(entries.length);
        for (StateWageMarket.Entry e : entries) {
            file.i(e.wage());
        }
        ChunkedSave.endChunk(file, pos);
    }

    private static void saveCorveeChunk(FilePutter file) {
        int pos = ChunkedSave.startChunk(file, Tags.CORVEE);
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

    // ── Chunked load ───────────────────────────────────────────────

    private static void loadChunked(EconomySim sim, FileGetter file) throws IOException {
        int magic = file.i();
        if (magic != CHUNK_MAGIC) {
            throw new IOException("[ECON] expected chunked save magic 0x"
                + Integer.toHexString(CHUNK_MAGIC) + " but found 0x"
                + Integer.toHexString(magic));
        }

        boolean loadedCore = false;
        boolean loadedConfig = false;

        ChunkedSave.Header header;
        while ((header = ChunkedSave.readHeader(file)) != null) {
            int expectedEnd = header.dataPosition + header.length;
            try {
                switch (header.tag) {
                    case Tags.CORE_SCALARS:
                        sim.seedSupply = file.l();
                        sim.imported = file.l();
                        sim.exported = file.l();
                        sim.escheated = file.l();
                        sim.guildIncomePaid = file.l();
                        sim.taxesCollected = file.l();
                        sim.spent = file.l();
                        sim.roundingDrift = file.l();
                        sim.warehouseTaxCollected = file.l();
                        sim.religionTaxCollected = file.l();
                        sim.liturgyCollected = file.l();
                        sim.wagesPaid = expectedEnd - file.getPosition() >= 8 ? file.l() : 0L;
                        sim.deaths = file.i();
                        sim.emigrations = file.i();
                        sim.inherited = file.i();
                        sim.heirless = file.i();
                        sim.lastTaxSeason = file.i();
                        sim.ticks = file.i();
                        sim.econIndicatorTickCounter = file.i();
                        sim.encounterCarry = file.d();
                        sim.reportedAuditDelta = file.l();
                        sim.housingRentCollected = expectedEnd - file.getPosition() >= 8 ? file.l() : 0L;
                        sim.propertyMarket.load(file, expectedEnd);
                        loadedCore = true;
                        break;
                    case Tags.ECON_CONFIG:
                        EconConfig.debtSlaveryEnabled = file.bool();
                        EconConfig.debtSlaveThreshold = file.i();
                        EconConfig.oddjobWageEnabled = file.bool();
                        EconConfig.setOddjobWage(file.i());
                        EconConfig.transportFeeEnabled = file.bool();
                        EconConfig.transportFeePer100TileDay = file.i();
                        EconConfig.perHeadTax = file.i();
                        EconConfig.marketTaxRate = (double) file.i() / 100.0;
                        EconConfig.warehouseTaxPercent = file.i();
                        EconConfig.taxesEnabled = file.bool();
                        EconConfig.religionTaxEnabled = file.bool();
                        EconConfig.liturgyEnabled = file.bool();
                        EconConfig.liturgyRate = (double) file.i() / 10000.0;
                        EconConfig.liturgyHeadcount = file.i();
                        EconConfig.liturgyIntervalSeasons = file.i();
                        EconConfig.autoProcureConstruction = file.bool();
                        EconConfig.autoProcurePremiumMultiplier = (double) file.i() / 100.0;
                        EconConfig.maxAutoBuySpendPerTick = file.i();
                        EconConfig.constructionHoardingEnabled = file.bool();
                        EconConfig.constructionSmoothingDays = (double) file.i() / 100.0;
                        EconConfig.firmInputGateEnabled = file.bool();
                        loadedConfig = true;
                        break;
                    case Tags.WAGES:                sim.wages.load(file); break;
                    case Tags.TAXES:                sim.taxes.load(file); break;
                    case Tags.FISCAL:               sim.fiscal.load(file); break;
                    case Tags.LABOR_MARKET:         sim.laborMarket.load(file); break;
                    case Tags.MAINTENANCE_MARKET:   sim.maintenanceMarket.load(file); break;
                    case Tags.GRAIN_DOLE:           sim.grainDole.load(file); break;
                    case Tags.RELIGION_MARKET:      sim.religionMarket.load(file); break;
                    case Tags.LITURGY:              sim.liturgy.load(file); break;
                    case Tags.DEBT_BONDAGE:         sim.debtBondage.load(file); break;
                    case Tags.MILITARY_PAYROLL:     sim.militaryPayroll.load(file); break;
                    case Tags.PRODUCTION_SUBSIDIES: sim.productionSubsidies.load(file); break;
                    case Tags.STATE_WAREHOUSES:     sim.stateWarehouses.load(file); break;
                    case Tags.WAREHOUSE_MARKET:     sim.warehouseMarket.load(file); break;
                    case Tags.HOUSING:              sim.housingMarket.load(file); break;
                    case Tags.FOREIGN_TRADE_LEDGER: sim.foreignTradeLedger.load(file); break;
                    case Tags.PROGRESSION:          sim.progression.load(file); break;
                    case Tags.CORVEE:               loadCorvee(file); break;
                    case Tags.TREASURY_CRISIS:      TreasuryCrisis.load(file); break;
                    case Tags.STATE_WAGES:
                        int count = file.i();
                        for (StateWageMarket.Entry e : sim.stateWages.entries()) {
                            if (count-- > 0) e.setWage(file.i());
                        }
                        break;
                    case Tags.END:
                        return;
                    default:
                        if (EconConfig.debugLoggingEnabled) {
                            LOG.ln("[ECON] skipping unknown save chunk tag=" + header.tag + " length=" + header.length);
                        }
                        break;
                }
            } finally {
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

    // ── Legacy load ────────────────────────────────────────────────

    private static void loadLegacy(EconomySim sim, FileGetter file, int version) throws IOException {
        sim.seedSupply = file.l();
        sim.imported = file.l();
        sim.exported = file.l();
        sim.escheated = file.l();
        sim.guildIncomePaid = file.l();
        sim.wages.load(file);
        sim.taxesCollected = file.l();
        sim.taxes.load(file);
        sim.spent = file.l();
        sim.roundingDrift = file.l();
        sim.grainDole.load(file);
        sim.laborMarket.load(file);
        sim.fiscal.load(file);
        sim.maintenanceMarket.load(file);
        sim.religionTaxCollected = file.l();
        sim.liturgyCollected = file.l();
        sim.religionMarket.load(file);
        sim.liturgy.load(file);
        loadCorvee(file);
        EconConfig.debtSlaveryEnabled = file.bool();
        EconConfig.debtSlaveThreshold = file.i();
        sim.debtBondage.load(file);
        EconConfig.oddjobWageEnabled = file.bool();
        EconConfig.setOddjobWage(file.i());
        if (version >= 20) {
            sim.militaryPayroll.load(file);
            sim.productionSubsidies.load(file);
        } else {
            sim.militaryPayroll.clear();
            sim.militaryPayroll.setWage(150);
            sim.productionSubsidies.clear();
        }
        if (version >= 21) {
            sim.stateWarehouses.load(file);
        } else {
            sim.stateWarehouses.clear();
        }
        if (version >= 23) {
            sim.warehouseMarket.load(file);
            sim.warehouseTaxCollected = file.l();
        } else if (version >= 22) {
            sim.warehouseMarket.load(file);
            sim.warehouseTaxCollected = 0L;
        } else {
            sim.warehouseMarket.clear();
        }
        if (version >= 25) {
            EconConfig.transportFeeEnabled = file.bool();
            EconConfig.transportFeePer100TileDay = file.i();
            for (StateWageMarket.Entry e : sim.stateWages.entries()) {
                e.setWage(file.i());
            }
        }
        if (version >= 26) {
            EconConfig.perHeadTax = file.i();
            EconConfig.marketTaxRate = (double) file.i() / 100.0;
            EconConfig.warehouseTaxPercent = file.i();
            EconConfig.taxesEnabled = file.bool();
            EconConfig.religionTaxEnabled = file.bool();
            EconConfig.liturgyEnabled = file.bool();
            EconConfig.liturgyRate = (double) file.i() / 10000.0;
            EconConfig.liturgyHeadcount = file.i();
            EconConfig.liturgyIntervalSeasons = file.i();
        }
        if (version >= 29 && version <= 30) {
            EconConfig.autoProcureConstruction = file.bool();
            EconConfig.autoProcurePremiumMultiplier = (double) file.i() / 100.0;
            EconConfig.maxAutoBuySpendPerTick = file.i();
            if (version >= 30) {
                EconConfig.constructionHoardingEnabled = file.bool();
                EconConfig.constructionSmoothingDays = (double) file.i() / 100.0;
                EconConfig.firmInputGateEnabled = file.bool();
            }
        }
        if (version >= 28) {
            sim.progression.load(file);
        }
        if (version >= 31) {
            EconConfig.autoProcureConstruction = file.bool();
            EconConfig.autoProcurePremiumMultiplier = (double) file.i() / 100.0;
            EconConfig.maxAutoBuySpendPerTick = file.i();
            EconConfig.constructionHoardingEnabled = file.bool();
            EconConfig.constructionSmoothingDays = (double) file.i() / 100.0;
            EconConfig.firmInputGateEnabled = file.bool();
        }
    }

    private static void loadCorvee(FileGetter file) throws IOException {
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
