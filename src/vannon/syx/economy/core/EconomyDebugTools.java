package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import snake2d.LOG;

/**
 * Debug and cheat API extracted from {@link EconomySim} (Sprint E1).
 * All methods are static, taking an {@code EconomySim} instance as the
 * first parameter. Keeps cheat/debug code out of the core simulation class.
 *
 * <p>Package-private fields of EconomySim are accessible because this
 * class lives in the same package.</p>
 */
public final class EconomyDebugTools {

    private EconomyDebugTools() { }

    /** Returns one-line status per adapter (Transport, Warehouse, Diplomacy, Boosting, AI, NPC). */
    public static String[] debugAdapterStatus(EconomySim sim) {
        return new String[]{
            "Transport:  " + (sim.transportAdapter.isDistanceAvailable() ? "OK" : "FAIL"),
            "Warehouse:  " + (sim.warehouseAdapter.isStoringLockAvailable() ? "OK" : "FAIL"),
            "Diplomacy:  " + (sim.diplomacyAdapter.isAvailable() ? "OK" : "FAIL"),
            "Boosting:   " + (sim.boostingAdapter.isAdminBoosterAvailable() ? "OK" : "FAIL"),
            "AI:         " + (sim.aiAdapter.isAvailable() ? "OK" : "FAIL"),
            "NPC:        " + (sim.npcAdapter != null && sim.npcAdapter.isAvailable() ? "OK" : (sim.npcAdapter != null ? "FAIL" : "N/A"))
        };
    }

    /** Runs a self-test on every BypassGate adapter with real engine access. */
    public static String[] debugSelfTest(EconomySim sim) {
        java.util.List<String> results = new java.util.ArrayList<>();
        boolean tOk = sim.transportAdapter.isDistanceAvailable();
        results.add("Transport  " + (tOk ? "PASS" : "SKIP") + "  distanceField=" + tOk);
        boolean wOk = sim.warehouseAdapter.isStoringLockAvailable();
        results.add("Warehouse  " + (wOk ? "PASS" : "SKIP") + "  storingLock=" + wOk);
        boolean dOk = sim.diplomacyAdapter.isAvailable();
        results.add("Diplomacy  " + (dOk ? "PASS" : "SKIP") + "  numericFields=" + dOk);
        boolean bOk = sim.boostingAdapter.isAdminBoosterAvailable();
        game.boosting.Boostable b = bOk ? sim.boostingAdapter.getAdminBoostable() : null;
        results.add("Boosting   " + (bOk && b != null ? "PASS" : (bOk ? "PARTIAL" : "SKIP"))
                + "  adminBoostable=" + (b != null ? b.key : "null"));
        boolean aOk = sim.aiAdapter.isAvailable();
        boolean nullCheck = !sim.aiAdapter.isFoodPlan(null);
        results.add("AI         " + (aOk && nullCheck ? "PASS" : (aOk ? "PARTIAL" : "SKIP"))
                + "  classResolution=" + aOk + "  nullSafe=" + nullCheck);
        boolean nOk = sim.npcAdapter != null && sim.npcAdapter.isAvailable();
        int npcN = nOk ? sim.npcAdapter.npcCount() : 0;
        results.add("NPC        " + (nOk ? "PASS" : (sim.npcAdapter != null ? "FAIL" : "N/A"))
                + "  priceAccess=" + nOk + "  factions=" + npcN);
        return results.toArray(new String[0]);
    }

    /** Cheat: mint {@code amount} Denari into the player treasury. */
    public static void mintTreasury(EconomySim sim, long amount) {
        FACTIONS.player().credits().inc((double) amount, FCredits.CTYPE.MISC);
        LOG.ln("[ECON CHEAT] minted " + amount + " D into treasury (new balance: " + sim.treasury() + " D)");
        EventLog.log("CHEAT", "Minted " + amount + " D \u2014 new treasury: " + sim.treasury());
        DiagnosticExporter.logPlayerAction(sim.ticks, "CHEAT_MINT", "amount=" + amount + ",treasury=" + sim.treasury());
    }

    /** Cheat: force an immediate diagnostic CSV export (bypasses day-boundary guard). */
    public static void forceDiagnosticExport(EconomySim sim) {
        DiagnosticExporter.resetExportGuard();
        DiagnosticExporter.exportDay(sim);
        LOG.ln("[ECON CHEAT] forced diagnostic export");
        EventLog.log("CHEAT", "Forced diagnostic export");
        DiagnosticExporter.logPlayerAction(sim.ticks, "CHEAT_EXPORT", "forced");
    }

    /** Cheat: log the current money-flow audit delta to stdout and EventLog. */
    public static void logAuditDelta(EconomySim sim) {
        long delta = sim.auditDelta();
        LOG.ln("[ECON CHEAT] auditDelta=" + delta + " | circulating=" + sim.wallets().circulating()
                + " | treasury=" + sim.treasury() + " | seed=" + sim.seedSupply()
                + " | imported=" + sim.imported() + " | exported=" + sim.exported()
                + " | wagesPaid=" + sim.wagesPaid() + " | drift=" + sim.roundingDrift());
        EventLog.log("CHEAT", "Audit delta: " + delta
                + " (circulating=" + sim.wallets().circulating() + ", treasury=" + sim.treasury() + ")");
    }
}
