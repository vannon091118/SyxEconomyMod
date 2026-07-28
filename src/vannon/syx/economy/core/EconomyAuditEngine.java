package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.faction.npc.FactionNPC;
import game.time.TIME;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.Induvidual;
import settlement.stats.STATS;
import snake2d.LOG;
import snake2d.util.rnd.RND;
import snake2d.util.sets.LIST;
import vannon.syx.economy.adapter.EngineMirror;

/**
 * Extracted from EconomySim (Spluck-TECHD-01): audit delta, supply
 * mismatch detection, money-supply conservation checks, demography
 * (hunger-deaths), heir-finding, and NPC opinion monitoring.
 *
 * <p>Each method receives the EconomySim instance for field access.</p>
 */
final class EconomyAuditEngine {

    private EconomyAuditEngine() {}

    // ── Heir search ────────────────────────────────────────────────

    static Humanoid findHeir(EconomySim sim, int deadRef) {
        if (deadRef <= 0) return null;
        int found = 0;
        Humanoid chosen = null;
        block0:
        for (int i = 0; i < sim.roster.size(); ++i) {
            Humanoid h = sim.roster.get(i);
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

    // ── Departure handler ──────────────────────────────────────────

    static void onDeparture(EconomySim sim, int estate, int relRef, boolean emigrated) {
        if (emigrated) {
            ++sim.emigrations;
            sim.exported += (long) estate;
            return;
        }
        ++sim.deaths;
        if (estate == 0) return;
        Humanoid heir = findHeir(sim, relRef);
        if (heir != null) {
            ++sim.inherited;
            sim.wallets.add(heir, estate);
            return;
        }
        ++sim.heirless;
        sim.escheated += (long) estate;
        if (EconConfig.escheatToPlayerTreasury) {
            FACTIONS.player().credits().inc((double) estate, FCredits.CTYPE.TAX);
        }
    }

    // ── Money-supply audit ─────────────────────────────────────────

    static long auditDelta(EconomySim sim) {
        return AuditKernel.delta(totalLiving(sim), auditTerms(sim));
    }

    static void auditSupply(EconomySim sim) {
        long expected = AuditKernel.expected(auditTerms(sim));
        long actual = totalLiving(sim);
        long delta = actual - expected;
        if (delta != 0L) {
            if (Math.abs(delta) <= EconConfig.roundingDriftThreshold) {
                sim.roundingDrift += delta;
            } else if (delta != sim.reportedAuditDelta) {
                System.err.println("[ECON] SUPPLY MISMATCH: living=" + actual
                    + " expected=" + expected
                    + " (seed=" + sim.seedSupply
                    + " +imported=" + sim.imported
                    + " +treasuryIncome=" + sim.guildIncomePaid
                    + " +rationOut=" + sim.fiscal.rationOut()
                    + " +wagesPaid=" + sim.wagesPaid
                    + " +propertyDividends=" + sim.propertyMarket.dividendsPaid()
                    + " -exported=" + sim.exported
                    + " -escheated=" + sim.escheated
                    + " -wealthTax=" + sim.taxesCollected
                    + " -headTax=" + sim.fiscal.headTaxCollected()
                    + " -market=" + sim.fiscal.marketReceipts()
                    + " -legacySpent=" + sim.spent
                    + " -religionTax=" + sim.religionTaxCollected
                    + " -liturgy=" + sim.liturgyCollected
                    + " -warehouseTax=" + sim.warehouseTaxCollected
                    + " -housingRent=" + sim.housingRentCollected
                    + " -propertySales=" + sim.propertyMarket.salesCollected()
                    + " -roundingDrift=" + sim.roundingDrift + ")");
            }
        }
        sim.reportedAuditDelta = delta;
    }

    static long totalLiving(EconomySim sim) {
        return sim.wallets.circulating();
    }

    static void seedTreasury() {
        int floor = EconConfig.startingTreasury;
        if (floor <= 0) return;
        double have = FACTIONS.player().credits().credits();
        if (have >= (double) floor) return;
        int topUp = (int) ((double) floor - have);
        FACTIONS.player().credits().inc((double) topUp, FCredits.CTYPE.MISC);
        if (EconConfig.debugLoggingEnabled) {
            LOG.ln("[ECON] starting treasury topped up by " + topUp + " to " + floor
                + " \u2014 enough to make payroll while the city finds its feet.");
        }
    }

    static void logSeed(EconomySim sim) {
        int n = sim.roster.size();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < n; ++i) {
            int m = sim.wallets.get(sim.roster.get(i));
            if (m < min) min = m;
            if (m > max) max = m;
        }
        LOG.ln("[ECON] seeded " + n + " settlers | min=" + min + " max=" + max
            + " supply=" + sim.seedSupply
            + " | alpha=" + EconConfig.alpha
            + " mode=" + String.valueOf((Object) EconConfig.pairMode)
            + " encounters/gamesec=" + EconConfig.encountersPerGameSecond);
        if (min != max) {
            LOG.ln("[ECON] NOTE: wallets were not uniform at start \u2014 this save already holds economy data. Set EconConfig.resetWalletsOnLoad = true to start flat.");
        }
    }

    static void logLedger(EconomySim sim) {
        LOG.ln("[ECON] supply: living=" + totalLiving(sim)
            + " = seed " + sim.seedSupply
            + " + imported " + sim.imported
            + " + treasury-funded income " + sim.guildIncomePaid
            + " + ration procurement " + sim.fiscal.rationOut()
            + " - exported " + sim.exported
            + " - escheated " + sim.escheated
            + " - wealth taxes " + sim.taxesCollected
            + " - head taxes " + sim.fiscal.headTaxCollected()
            + " - market receipts " + sim.fiscal.marketReceipts()
            + " - legacy spent " + sim.spent
            + " - housing rent " + sim.housingRentCollected
            + " - wages paid " + sim.wagesPaid
            + " - religion tax " + sim.religionTaxCollected
            + " - liturgy " + sim.liturgyCollected
            + " - warehouse tax " + sim.warehouseTaxCollected
            + " | drift=" + sim.roundingDrift
            + " | deaths=" + sim.deaths
            + " (inherited=" + sim.inherited + ", heirless=" + sim.heirless + ")"
            + " emigrations=" + sim.emigrations
            + " | current guild flow: paid " + sim.firmLedger.lastIncomePaid()
            + "/" + sim.firmLedger.lastIncomeDue()
            + (sim.firmLedger.lastWorkersUnpaid() > 0
                ? " INSOLVENT (" + sim.firmLedger.lastWorkersUnpaid() + " unpaid shares)" : ""));
        LOG.ln("[ECON] B2B: merchants bought " + sim.warehouseMarket.lastUnitsBought()
            + " units for " + sim.warehouseMarket.lastBought()
            + " | sold " + sim.warehouseMarket.lastUnitsSold()
            + " for " + sim.warehouseMarket.lastSold()
            + " | construction materials " + sim.warehouseMarket.lastConstructionPaid()
            + " | export purchases " + sim.warehouseMarket.lastExportBought()
            + " | stock levy " + sim.warehouseMarket.lastTaxed()
            + (sim.warehouseMarket.lastUnitsBought() == 0
                ? "  <- NOBODY IS BUYING: no crates allocated, or merchants are broke" : ""));
    }

    // ── Demography (hunger damage + starvation risk) ───────────────

    static void updateDemography(EconomySim sim) {
        if (!(EngineMirror.api() != null && EngineMirror.api().rooms() != null
                ? EngineMirror.api().rooms().entitiesAvailable()
                : EngineSeams.entitiesAvailable())) return;
        int threshold = EconConfig.hungerDeathThreshold;
        if (threshold <= 0) return;
        int hungerDeaths = 0;
        for (int i = 0; i < sim.roster.size(); ++i) {
            Humanoid h = sim.roster.get(i);
            int hunger;
            try {
                hunger = EngineMirror.api().humanoids().getHungerRaw(h);
            } catch (RuntimeException e) {
                continue;
            }
            if (hunger < threshold) continue;
            int walletDamage;
            if (hunger >= 90) {
                walletDamage = Math.max(1, sim.wallets.get(h) / 500);
            } else {
                walletDamage = Math.max(1, sim.wallets.get(h) / 2000);
            }
            if (sim.wallets.get(h) >= walletDamage) {
                sim.wallets.charge(h, walletDamage);
            }
            hungerDeaths++;
        }
        sim.starvationRiskCount = hungerDeaths;
    }

    // ── DIPLO-01: NPC opinion monitoring ───────────────────────────

    static void monitorFactionOpinion(EconomySim sim) {
        try {
            LIST<FactionNPC> npcList = FACTIONS.NPCs();
            if (npcList == null) return;
            int crisisTier = TreasuryCrisis.activeTier();
            double gini = sim.stats.gini;
            int deathsSince = sim.deaths;

            for (int i = 0; i < npcList.size(); i++) {
                FactionNPC npc = npcList.get(i);
                if (npc == null || !npc.isActive()) continue;
                double delta = 0.0;
                if (crisisTier >= 1) delta -= 0.5 * crisisTier;
                if (gini >= 0.5) delta -= (gini - 0.4) * 0.5;
                if (deathsSince > 0) delta -= deathsSince * 0.1;
                if (delta != 0.0) {
                    EngineMirror.api().factions().adjustFactionOpinion(npc, delta);
                }
            }

            if (crisisTier >= 1 || gini >= 0.5 || deathsSince > 0) {
                EventLog.logSampled("DIPLO",
                    "Opinion-Monitor: crisis=" + crisisTier
                    + " gini=" + String.format("%.2f", gini)
                    + " deaths=" + deathsSince
                    + " \u2014 Write via BypassGate (DIPLO-03, gated by royaltyOpinionWriteEnabled).");
            }
        } catch (Throwable t) {
            // Opinion-Monitoring ist nicht kritisch
        }
    }

    // ── Refresh flow prices ────────────────────────────────────────

    static void refreshFlowPrices(EconomySim sim) {
        int goods = RESOURCES.ALL().size();
        double[] anchors = new double[goods];
        double[] targets = new double[goods];
        for (int i = 0; i < goods; ++i) {
            RESOURCE resource = (RESOURCE) RESOURCES.ALL().get(i);
            anchors[i] = PolityPriceAnchor.priceOf(resource);
            targets[i] = RESOURCES.EDI().is(resource)
                ? EconConfig.targetFoodDays
                : (RESOURCES.DRINKS().is(resource)
                    ? EconConfig.targetDrinkDays
                    : EconConfig.flowDefaultTargetCoverageDays);
        }
        if (EconConfig.scarcityPriceBoost > 0.0 || EconConfig.scarcityLaborBoost > 0.0) {
            sim.scarcitySignal.update(sim.flowMeter.snapshot(), EconConfig.flowPriceRefreshDays);
            sim.laborMarket.refreshBlueprintOutputs(sim.flowMeter);
        }
        sim.flowPrices.refresh(anchors, sim.flowMeter.snapshot(),
            new FlowPrices.Parameters(targets, EconConfig.flowLookaheadDays,
                EconConfig.scarcityElasticityUp, EconConfig.scarcityElasticityDown,
                EconConfig.priceClampLo, EconConfig.priceClampHi, EconConfig.priceAbsoluteMax),
            sim.scarcitySignal.snapshot());

        if (EconConfig.foodPriceCapMultiplier > 0.0) {
            for (int i = 0; i < goods; ++i) {
                RESOURCE res = (RESOURCE) RESOURCES.ALL().get(i);
                if (RESOURCES.EDI().is(res)) {
                    double cap = anchors[i] * EconConfig.foodPriceCapMultiplier;
                    if (cap > 0.0) sim.flowPrices.enforceCap(i, cap);
                }
            }
        }

        if (EconConfig.debugLoggingEnabled) {
            FlowMeter.Snapshot meter = sim.flowMeter.snapshot();
            for (int i = 0; i < goods; ++i) {
                RESOURCE res = (RESOURCE) RESOURCES.ALL().get(i);
                LOG.ln("[ECON_DEBUG_PRICE] " + res.name
                    + ": price=" + String.format("%.2f", sim.flowPrices.price(i))
                    + " (anchor=" + anchors[i] + ")"
                    + ", supply=" + String.format("%.1f", meter.supplyPerDay(i))
                    + ", demand=" + String.format("%.1f", meter.demandPerDay(i))
                    + ", stock=" + String.format("%.1f", meter.stock(i))
                    + ", cov=" + String.format("%.2f", sim.flowPrices.coverage(i)));
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────

    private static AuditKernel.Terms auditTerms(EconomySim sim) {
        return new AuditKernel.Terms(
            sim.seedSupply, sim.imported,
            sim.guildIncomePaid + sim.fiscal.rationOut(),
            sim.roundingDrift, sim.exported, sim.escheated,
            sim.taxesCollected, sim.fiscal.headTaxCollected(),
            sim.fiscal.marketReceipts(), sim.spent,
            sim.religionTaxCollected, sim.liturgyCollected,
            sim.warehouseTaxCollected, sim.wagesPaid,
            sim.housingRentCollected,
            sim.propertyMarket.salesCollected(),
            sim.propertyMarket.dividendsPaid());
    }
}
