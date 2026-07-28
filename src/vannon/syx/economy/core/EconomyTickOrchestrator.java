package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.faction.npc.FactionNPC;
import game.time.TIME;
import snake2d.LOG;
import vannon.syx.economy.adapter.EngineMirror;
import snake2d.util.sets.LIST;

/**
 * Extracted from EconomySim (Spluck-TECHD-01): the heavy middle section
 * of {@code update()} covering wages, taxes, labor, encounters,
 * conservation, stats, indicators, and daily cadence.
 *
 * <p>Each method receives the EconomySim instance for field access.</p>
 */
final class EconomyTickOrchestrator {

    private EconomyTickOrchestrator() {}

    /**
     * Phases 7-10: Wages → Transport → Corvee → Labor → Taxes → Fiscal →
     * Housing → Debt → Purchases → Encounters → Conservation.
     * Called from update() after warehouse/pricing/foodPlan phases.
     */
    static void tickPhases7To11(EconomySim sim, double ds) {
        // ── Phase 7: Wages & transport ─────────────────────────────
        FirmLedger.UpdateResult firmUpdate = sim.firmLedger.update(
            sim.roster, sim.wallets, sim.flowMeter, sim.flowPrices,
            sim.affordabilityGate, ds, sim.ticks);
        sim.guildIncomePaid += firmUpdate.paid();

        MaintenanceMarket.Settlement upkeep = sim.maintenanceMarket.update(
            sim.ticks, sim.roster, sim.wallets, sim.firmLedger);
        sim.fiscal.settleMerchantRemainder(
            (int) Math.min(Integer.MAX_VALUE, Math.max(0L, upkeep.billed() - upkeep.credited())));

        sim.guildIncomePaid += sim.productionSubsidies.update(
            sim.flowMeter, sim.firmLedger, sim.roster, sim.wallets);
        sim.guildIncomePaid += sim.stateWages.update(ds, sim.roster, sim.wallets, sim.firmLedger);
        sim.wagesPaid += sim.wages.update(sim.roster, sim.wallets);
        sim.guildIncomePaid += sim.transportMarket.update(
            ds / (double) TIME.secondsPerDay(), sim.roster, sim.wallets, sim.firmLedger);
        sim.guildIncomePaid += sim.handoutRelief.update(sim.roster, sim.wallets);
        sim.guildIncomePaid += sim.stateWarehouses.payWages(sim.roster, sim.wallets);
        sim.warehouseTaxCollected += sim.warehouseMarket.taxInventory(
            sim.roster, sim.wallets, sim.firmLedger);

        // ── Phase 8: Corvee → Labor → Taxes → Fiscal → Housing ────
        sim.corveeController.update(sim.roster);
        sim.accessAutomation.update(sim.flowMeter.snapshot(), sim.ticks);
        sim.laborMarket.setScarcitySignal(sim.scarcitySignal);
        sim.laborMarket.update(sim.firmLedger, sim.ticks);
        sim.stateWarehouses.updateEmploymentPriority(sim.laborMarket.meanWage());
        OddjobAutomation.autoTune(sim.roster, sim.laborMarket);
        sim.guildIncomePaid += sim.oddjobMarket.update(sim.roster, sim.wallets);
        sim.taxesCollected += sim.taxes.update(sim.roster, sim.wallets);
        sim.fiscal.update(sim.roster, sim.wallets);
        sim.religionTaxCollected += sim.religionMarket.update(sim.roster, sim.wallets);
        sim.liturgyCollected += sim.liturgy.update(sim.roster, sim.wallets);
        sim.housingRentCollected += sim.housingMarket.update(sim.roster, sim.wallets, sim.firmLedger);
        sim.propertyMarket.update();
        settleTaxSeason(sim);
        sim.debtBondage.update(sim.roster, sim.wallets);
        sim.spent += sim.purchases.update(sim.roster, sim.wallets, sim.affordabilityGate, sim.ticks);

        // ── Phase 9: Encounters + conservation ─────────────────────
        long before = EconConfig.checkConservation
            ? EconomyAuditEngine.totalLiving(sim) : 0L;
        PairSource source = EconConfig.pairMode == EconConfig.PairMode.PROXIMITY
            ? sim.proximityPairs : sim.randomPairs;
        sim.encounterCarry += EconConfig.encountersPerGameSecond * ds;
        int n = (int) sim.encounterCarry;
        sim.encounterCarry -= (double) n;
        if (n > 0) {
            source.encounters(sim.roster, n, sim.exchange);
        }
        if (EconConfig.checkConservation) {
            long after = EconomyAuditEngine.totalLiving(sim);
            if (before != after) {
                System.err.println("[ECON] KERNEL LEAK: " + before + " -> " + after
                    + " (delta " + (after - before) + ") \u2014 the exchange is not conserving money");
            }
            EconomyAuditEngine.auditSupply(sim);
        }

        // ── Phase 10: Stats refresh + dump ─────────────────────────
        int medianRefresh = Math.max(1, (int)(EconConfig.medianRefreshDays * TIME.secondsPerDay()));
        if (EconConfig.medianRefreshDays > 0 && sim.ticks % medianRefresh == 0) {
            sim.stats.recompute(sim.roster, sim.wallets);
            if (EconConfig.citizenClassesEnabled) {
                CitizenClass.classifyAll(sim.wallets, sim.roster, sim.stats, sim.housingMarket.ledger());
            }
        }
        int dumpInterval = Math.max(1, (int)(EconConfig.dumpIntervalDays * TIME.secondsPerDay()));
        if (EconConfig.dumpIntervalDays > 0 && sim.ticks % dumpInterval == 0) {
            sim.histogram.dump(sim.roster, sim.wallets, sim.ticks);
            EconomyAuditEngine.logLedger(sim);
        }

        // ── Phase 11: Indicators + diplomacy + render + daily ──────
        sim.econIndicatorTickCounter++;
        if (sim.econIndicatorTickCounter >= EconomySim.ECON_INDICATOR_INTERVAL) {
            sim.econIndicatorTickCounter = 0;
            EconSnapshot snap = new EconSnapshot(sim);
            sim.econIndicators.update(snap);
            sim.progression.update(snap);
            GiniConsequences.announceIfCrossed(snap, TIME.seasons().bitsSinceStart());
        }

        if (EconConfig.opinionEconomyLinkEnabled
                && sim.ticks % EconConfig.opinionMonitorIntervalTicks == 0
                && EngineMirror.api() != null
                && EngineMirror.api().factions() != null) {
            EconomyAuditEngine.monitorFactionOpinion(sim);
        }

        sim.renderCaches.update(sim.roster, sim.wallets, sim.stateWarehouses);
        sim.renderCaches.track(sim.ticks);

        // Daily cadence: history push, conflict warn, diagnostics, foreign trade
        if (sim.ticks % (int) EconConfig.DEFAULT_TICKS_PER_DAY == 0) {
            sim.treasuryHistory.push((double) sim.treasury());
            sim.giniHistory.push(sim.stats.gini);
            String conflict = EconConfig.conflictWarning();
            if (conflict != null) EventLog.logSampled("CONFIG", conflict);
            DiagnosticExporter.exportDay(sim);
            sim.foreignTradeLedger.dailyTick(sim.ticks);
        }
    }

    // ── Private helpers ────────────────────────────────────────────

    private static void settleTaxSeason(EconomySim sim) {
        int season = TIME.seasons().bitsSinceStart();
        if (sim.lastTaxSeason == -1) {
            sim.lastTaxSeason = season;
            return;
        }
        if (season == sim.lastTaxSeason) return;
        sim.lastTaxSeason = season;
        sim.wallets.settleTaxResentment();
    }
}
