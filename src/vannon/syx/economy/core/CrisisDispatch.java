package vannon.syx.economy.core;

/**
 * Phase 5e: Treasury-Crisis dispatch extracted from EconomySim. A thin
 * static indirection that keeps the crisis concern separate from the
 * simulation orchestrator.
 *
 * <p>This exists so EconomySim.update() does not need to import or
 * reference {@link TreasuryCrisis} directly. As crisis logic grows
 * (Phase 6: per-citizen crisis effects), the dispatch point is the
 * natural place to add pre/post hooks without touching EconomySim.</p>
 */
public final class CrisisDispatch {

    private CrisisDispatch() {}

    /**
     * Dispatch the treasury-crisis check. Must be called AFTER game-state
     * guards (SETT.ENTITIES != null, ds > 0, roster >= 2).
     *
     * @param treasury current treasury balance
     * @param sim      the EconomySim instance (for forced actions like liquidation)
     */
    public static void update(long treasury, EconomySim sim) {
        TreasuryCrisis.update(treasury, sim);
    }
}
