package vannon.syx.economy.headless;

import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.IFactionAccess;
import vannon.syx.economy.adapter.IGoodsAccess;
import vannon.syx.economy.adapter.IHumanoidAccess;
import vannon.syx.economy.adapter.IPopulationAccess;
import vannon.syx.economy.adapter.IRoomAccess;
import vannon.syx.economy.adapter.IStatsAccess;
import vannon.syx.economy.adapter.ITreasuryAccess;

/**
 * Factory wiring a {@link MockWorldState} into the 7 EngineMirror sub-interface
 * stub providers and registering the resulting singleton via
 * {@link EngineMirror#init}. Tests then construct an
 * {@link vannon.syx.economy.core.EconomySim} against the same world state.
 *
 * <h2>Usage (from {@code HeadlessIntegrationTest})</h2>
 * <pre>{@code
 * MockWorldState state = new MockWorldState(50, 500, 42L);
 * StubKit.Bundle b = StubKit.install(state);
 * assertTrue(EngineMirror.api().isFullyAvailable());
 *
 * // pass your own ISyx*Adapter mocks to EconomySim here
 * EconomySim sim = new EconomySim(mockT, mockW, mockB, mockD, mockAI);
 * for (int i = 0; i < 500; i++) sim.update(1.0);
 * }</pre>
 *
 * <p>All seven {@code isAvailable()} flags are {@code true}, so the gate at
 * the top of {@code EconomySim.update()} passes and the rest of the loop
 * runs (modulo any vanilla-entity reads that the test harness may still
 * leak through other subsystems).</p>
 */
public final class StubKit {

    private StubKit() {}

    /** Result of install: engine mirror singleton is set + bundle carries the 7 stubs. */
    public static final class Bundle {
        public final MockWorldState                    state;
        public final IRoomAccess                       rooms;
        public final IFactionAccess                    factions;
        public final IHumanoidAccess                   humanoids;
        public final IStatsAccess                      stats;
        public final ITreasuryAccess                   treasury;
        public final IPopulationAccess                 population;
        public final IGoodsAccess                      goods;

        Bundle(MockWorldState state,
               IRoomAccess rooms,
               IFactionAccess factions,
               IHumanoidAccess humanoids,
               IStatsAccess stats,
               ITreasuryAccess treasury,
               IPopulationAccess population,
               IGoodsAccess goods) {
            this.state      = state;
            this.rooms      = rooms;
            this.factions   = factions;
            this.humanoids  = humanoids;
            this.stats      = stats;
            this.treasury   = treasury;
            this.population = population;
            this.goods      = goods;
        }
    }

    /**
     * Build the 7 stubs from a shared {@link MockWorldState} and register
     * them into {@link EngineMirror#api()}. Idempotent: a previous instance
     * is replaced (resetForTesting is called first).
     */
    public static Bundle install(MockWorldState state) {
        IRoomAccess       rooms      = new StubRoomAccess(state);
        IFactionAccess    factions   = new StubFactionAccess(state);
        IHumanoidAccess   humanoids  = new StubHumanoidAccess(state);
        IStatsAccess      stats      = new StubStatsAccess(state);
        ITreasuryAccess   treasury   = new StubTreasuryAccess(state);
        IPopulationAccess population = new StubPopulationAccess(state);
        IGoodsAccess      goods      = new StubGoodsAccess(state);

        // Cross-package reset: resetForTesting() is package-private in adapter.
        // We cannot be in the adapter package without polluting production
        // namespaces, so we reach through reflection — single entry point.
        resetEngineMirrorSingleton();

        EngineMirror.init(rooms, factions, humanoids, stats, treasury, population, goods);

        return new Bundle(state, rooms, factions, humanoids, stats,
                          treasury, population, goods);
    }

    /** Tear down the singleton — call in {@code @AfterEach}. */
    public static void uninstall() {
        resetEngineMirrorSingleton();
    }

    /**
     * Reflection bypass for {@link EngineMirror#resetForTesting()} which is
     * package-private to {@code vannon.syx.economy.adapter}. Hidden state
     * mutation guarded by setAccessible(true); survives SecurityManager in
     * CI because tests run with all permissions.
     */
    private static void resetEngineMirrorSingleton() {
        try {
            java.lang.reflect.Method m =
                EngineMirror.class.getDeclaredMethod("resetForTesting");
            m.setAccessible(true);
            m.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "StubKit cannot reset EngineMirror singleton via reflection — "
                + "EngineMirrors resetForTesting() must remain accessible",
                e);
        }
    }
}
