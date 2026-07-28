package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.time.TIME;
import settlement.entity.humanoid.Humanoid;
import vannon.syx.economy.core.PropertyLedger;
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
        // ── Phase 7: Wages & transport (EVERY TICK — core income) ──
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

        // ── Phase 8a: Labor + access (EVERY TICK — hiring decisions) ──
        sim.accessAutomation.update(sim.flowMeter.snapshot(), sim.ticks);
        sim.laborMarket.setScarcitySignal(sim.scarcitySignal);
        sim.laborMarket.update(sim.firmLedger, sim.ticks);
        sim.stateWarehouses.updateEmploymentPriority(sim.laborMarket.meanWage());
        sim.propertyMarket.update();

        // ── Phase 8b: Taxes + Gini (staggered — every Nth tick) ──
        int taxStagger = Math.max(1, EconConfig.taxesStaggerTicks);
        if (sim.ticks % taxStagger == 0) {
            sim.taxesCollected += sim.taxes.update(sim.roster, sim.wallets);
            sim.taxesCollected += collectGiniSurcharge(sim);
            settleTaxSeason(sim);
        }

        // ── Phase 8c: Fiscal (every tick — treasury settlement) ──
        sim.fiscal.update(sim.roster, sim.wallets);

        // ── Phase 8d: Market subsystems (staggered — every Nth tick) ──
        int marketStagger = Math.max(1, EconConfig.marketStaggerTicks);
        if (sim.ticks % marketStagger == 0) {
            sim.corveeController.update(sim.roster);
            OddjobAutomation.autoTune(sim.roster, sim.laborMarket);
            sim.guildIncomePaid += sim.oddjobMarket.update(sim.roster, sim.wallets);
            sim.housingRentCollected += sim.housingMarket.update(sim.roster, sim.wallets, sim.firmLedger);
            sim.debtBondage.update(sim.roster, sim.wallets);
        }

        // ── Phase 8e: Religion + Liturgy + Handout (staggered — every Nth tick) ──
        int religionStagger = Math.max(1, EconConfig.religionStaggerTicks);
        if (sim.ticks % religionStagger == 0) {
            sim.religionTaxCollected += sim.religionMarket.update(sim.roster, sim.wallets);
            sim.liturgyCollected += sim.liturgy.update(sim.roster, sim.wallets);
        }

        // ── Phase 8f: Purchases (every tick — citizen consumption) ──
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
            // Sprint v0.13.99 — PriorityVector recompute. Day-once, im Sim-Thread.
            // flowMeter.snapshot() ist ein teurer Klon (O(RESOURCES)) — daher NICHT
            // doppelt pro Tick aufrufen. forceDiagnosticExport-Reentry über den
            // DiagnosticExporter.resetExportGuard() ruft exportDay() NUR mit eigenem
            // ExportGuard-Reset, der recompute-Hook läuft trotzdem einmal am Tag.
            if (EconConfig.priorityVectorEnabled) {
                try {
                    PriorityRegistry.instance().recompute(sim.flowMeter.snapshot(), sim.ticks);
                } catch (RuntimeException re) {
                    // Defense-in-Depth: PriorityVector ist observability, darf nie den
                    // Sim-Tick-Loop crashen. Loggen und weiterlaufen lassen.
                    System.err.println("[ECON] PriorityRegistry.recompute failed: "
                            + re.getClass().getSimpleName() + ": " + re.getMessage());
                }
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────

    /**
     * BA-02: Multi-factor progressive wealth surcharge during extreme inequality.
     *
     * <p>Base layer: flat cap = median × giniWealthCapMultiplier, surcharge =
     * excess × giniWealthSurchargeRateBp / 10000.</p>
     *
     * <p>Additional factors (multiplicative on base surcharge):
     * <ul>
     *   <li><b>Property:</b> citizens owning firm shares or homes pay more.</li>
     *   <li><b>Debt:</b> heavily indebted citizens get relief.</li>
     *   <li><b>Scarcity:</b> surcharge reduced during resource crises.</li>
     *   <li><b>FirmProfit:</b> owners of high-profit firms pay more.</li>
     * </ul></p>
     */
    private static long collectGiniSurcharge(EconomySim sim) {
        if (!EconConfig.giniAffectsLoyalty) return 0L;
        double gini = sim.stats.gini;
        if (gini <= EconConfig.giniWealthSurchargeThreshold) return 0L;
        int median = sim.stats.median;
        long cap = (long)(median * EconConfig.giniWealthCapMultiplier);
        if (cap <= 0) return 0L;

        // ── Settlement-level factors (computed once) ──────────────

        // Scarcity factor: reduce surcharge during resource crises
        double scarcityFactor = 1.0;
        if (EconConfig.giniScarcityReduction > 0.0 && sim.scarcitySignal != null) {
            double maxScarcity = sim.scarcitySignal.maxScarcity();
            if (maxScarcity > 0.3) {
                scarcityFactor = Math.max(0.1, 1.0 - EconConfig.giniScarcityReduction * maxScarcity);
            }
        }
        if (scarcityFactor <= 0.0) return 0L; // Extreme crisis — no surcharge

        // Firm profit factor: total profitable firms signal
        boolean firmProfitActive = EconConfig.giniFirmProfitSurchargeFactor > 0.0
                && sim.firmLedger.meanPositiveMarginal() > EconConfig.giniFirmProfitThreshold;

        // ── Per-citizen surcharge (O(N)) ─────────────────────────
        long surchargeTotal = 0L;
        PropertyLedger propLedger = sim.housingMarket != null ? sim.housingMarket.ledger() : null;

        for (int i = 0; i < sim.roster.size(); ++i) {
            Humanoid h = sim.roster.get(i);
            int wealth = sim.wallets.get(h);
            if (wealth <= cap) continue;

            long excess = (long)wealth - cap;
            double baseSurcharge = (double)excess * EconConfig.giniWealthSurchargeRateBp / 10000.0;

            // Pre-compute property lookups once per citizen (O(E) each)
            int shares = 0;
            boolean homeOwner = false;
            if (propLedger != null) {
                shares = propLedger.shareCount(h.id());
                homeOwner = propLedger.isHomeOwner(h.id());
            }

            // Factor 1: Property ownership — citizens owning firm shares or homes
            // represent capital concentration beyond wallet balance.
            double propertyMult = 1.0;
            if (propLedger != null && EconConfig.giniPropertySurchargeFactor > 0.0) {
                if (shares >= EconConfig.giniPropertySurchargeMinShares) {
                    propertyMult += EconConfig.giniPropertySurchargeFactor * (shares - EconConfig.giniPropertySurchargeMinShares + 1);
                }
                if (homeOwner) {
                    propertyMult += EconConfig.giniPropertySurchargeFactor;
                }
            }

            // Factor 2: Debt relief — citizens with heavy debt relative to wealth
            // get a reduction (they're rich on paper but cash-poor).
            double debtMult = 1.0;
            int citizenDebt = sim.wallets.debt(h);
            if (citizenDebt > 0 && EconConfig.giniDebtReliefFactor > 0.0) {
                double debtRatio = (double)citizenDebt / Math.max(1, wealth);
                if (debtRatio > 0.3) {
                    debtMult = Math.max(0.2, 1.0 - EconConfig.giniDebtReliefFactor * debtRatio);
                }
            }

            // Factor 3: Firm profit — owners of shares in highly profitable firms
            // (reuse shares count from above)
            double firmMult = 1.0;
            if (firmProfitActive && shares > 0) {
                firmMult += EconConfig.giniFirmProfitSurchargeFactor * Math.min(shares, 10);
            }

            // Final: base × property × debt × firm × scarcity
            double finalSurcharge = baseSurcharge * propertyMult * debtMult * firmMult * scarcityFactor;
            int chargeable = (int) Math.min(finalSurcharge, sim.wallets.spendable(h));
            if (chargeable <= 0) continue;

            sim.wallets.add(h, -chargeable);
            surchargeTotal += chargeable;
        }            if (surchargeTotal > 0L) {
                FACTIONS.player().credits().inc((double)surchargeTotal, FCredits.CTYPE.TAX);
                DiagnosticExporter.logPlayerAction("gini-surcharge", surchargeTotal + " D, scarcity=" + String.format(java.util.Locale.US, "%.2f", scarcityFactor));
            }
        return surchargeTotal;
    }

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
