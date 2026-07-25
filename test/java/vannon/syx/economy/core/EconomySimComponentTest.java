package vannon.syx.economy.core;

import static org.junit.jupiter.api.Assertions.*;

import game.boosting.Boostable;
import game.faction.diplomacy.DipWarPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.infra.station.ROOM_STATION;
import settlement.room.main.RoomInstance;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.Bitmap1D;
import vannon.syx.economy.adapter.ISyxAI;
import vannon.syx.economy.adapter.ISyxBoosting;
import vannon.syx.economy.adapter.ISyxDiplomacy;
import vannon.syx.economy.adapter.ISyxTransport;
import vannon.syx.economy.adapter.ISyxWarehouse;

/**
 * Component tests for {@link EconomySim}.
 *
 * <p>These tests do NOT start the Songs of Syx engine. Instead they inject
 * hand-written mocks for the five adapter interfaces via the package-private
 * test constructor. This isolates EconomySim wiring from the static engine
 * singletons (FACTIONS, SETT, TIME, etc.).</p>
 */
class EconomySimComponentTest {

    /** All-purpose test double that implements the five adapter interfaces. */
    static final class MockAdapterSet implements ISyxTransport, ISyxWarehouse,
            ISyxBoosting, ISyxDiplomacy, ISyxAI {

        boolean distanceAvailable = true;
        boolean storingLockAvailable = true;
        boolean adminBoosterAvailable = true;
        boolean diplomacyAvailable = true;

        @Override
        public boolean isDistanceAvailable() {
            return distanceAvailable;
        }

        @Override
        public double getReflectedDistance(RoomInstance loadingStation) {
            return 0.0;
        }

        @Override
        public double getGeometricDistance(RoomInstance loadingStation, ROOM_STATION unloading) {
            return 0.0;
        }

        @Override
        public boolean isStoringLockAvailable() {
            return storingLockAvailable;
        }

        @Override
        public void setStoring(StockpileInstance granary, boolean locked) {
            // no-op for tests
        }

        @Override
        public boolean isAdminBoosterAvailable() {
            return adminBoosterAvailable;
        }

        @Override
        public Boostable getAdminBoostable() {
            return null;
        }

        @Override
        public boolean isAvailable() {
            return diplomacyAvailable;
        }

        @Override
        public Bitmap1D getWillingBits(DipWarPlayer war) {
            return null;
        }

        @Override
        public ArrayList<?> getWillingList(DipWarPlayer war) {
            return null;
        }

        @Override
        public void setNumericState(DipWarPlayer war, int updateIndex, double playerPower, double coalitionPower) {
            // no-op for tests
        }

        @Override
        public boolean isOddjobbing(Humanoid humanoid) {
            return false;
        }

        @Override
        public boolean isFoodPlan(AIPLAN plan) {
            return false;
        }

        @Override
        public boolean isTavernPlan(AIPLAN plan) {
            return false;
        }

        @Override
        public boolean isMarketPlan(AIPLAN plan) {
            return false;
        }
    }

    private final MockAdapterSet adapters = new MockAdapterSet();

    @BeforeEach
    void resetSingleton() {
        EconomySim.clearActive();
    }

    @AfterEach
    void tearDownSingleton() {
        EconomySim.clearActive();
    }

    private EconomySim newSim() {
        return new EconomySim(adapters, adapters, adapters, adapters, adapters);
    }

    @Test
    void active_isNullBeforeConstruction() {
        assertNull(EconomySim.active());
    }

    @Test
    void construction_setsActiveAndExposesSubsystems() {
        EconomySim sim = newSim();
        assertSame(sim, EconomySim.active());
        assertNotNull(sim.wallets());
        assertNotNull(sim.roster());
        assertNotNull(sim.firmLedger());
        assertNotNull(sim.warehouseMarket());
        assertNotNull(sim.stateWarehouses());
        assertNotNull(sim.laborMarket());
        assertNotNull(sim.progression());
        assertNotNull(sim.aiAdapter());
    }

    @Test
    void update_withoutEngine_returnsEarlyAndDoesNotCrash() {
        EconomySim sim = newSim();
        assertDoesNotThrow(() -> sim.update(1.0));
        // SETT.ENTITIES() is null in tests, so ticks should not advance.
        assertEquals(0, sim.ticks());
    }

    @Test
    void update_withZeroDelta_returnsEarly() {
        EconomySim sim = newSim();
        assertDoesNotThrow(() -> sim.update(0.0));
        assertEquals(0, sim.ticks());
    }

    @Test
    void update_withNegativeDelta_returnsEarly() {
        EconomySim sim = newSim();
        assertDoesNotThrow(() -> sim.update(-1.0));
        assertEquals(0, sim.ticks());
    }

    @Test
    void stats_areZeroForEmptyWorld() {
        EconomySim sim = newSim();
        WealthStats stats = sim.stats();
        assertNotNull(stats);
        assertEquals(0.0, stats.gini, 1e-9);
        assertEquals(0, stats.median);
        assertEquals(0, stats.mean);
    }

    @Test
    void historiesExist_andHaveExpectedCapacity() {
        EconomySim sim = newSim();
        assertNotNull(sim.treasuryHistory());
        assertNotNull(sim.giniHistory());
        // Histories are empty until the daily boundary is crossed, but capacity is fixed.
        assertEquals(60, sim.treasuryHistory().historyRecords());
        assertEquals(60, sim.giniHistory().historyRecords());
    }

    @Test
    void roster_isEmptyInitially() {
        EconomySim sim = newSim();
        assertEquals(0, sim.roster().size());
    }

    @Test
    void tickCounter_staysZeroWhenEngineUnavailable() {
        EconomySim sim = newSim();
        for (int i = 0; i < 10; i++) {
            sim.update(1.0);
        }
        assertEquals(0, sim.ticks());
    }

    @Test
    void multipleInstances_overrideActive() {
        EconomySim first = newSim();
        assertSame(first, EconomySim.active());
        EconomySim second = newSim();
        assertSame(second, EconomySim.active());
    }

    @Test
    void adapterFallbackFlags_canBeToggled() {
        adapters.distanceAvailable = false;
        adapters.storingLockAvailable = false;
        adapters.adminBoosterAvailable = false;
        adapters.diplomacyAvailable = false;
        EconomySim sim = newSim();
        // Construction succeeds even if the mock claims features are unavailable.
        assertNotNull(sim);
    }

    @Test
    void subsystemsExposed_afterUpdateStillNonNull() {
        EconomySim sim = newSim();
        sim.update(1.0);
        assertNotNull(sim.flowMeter());
        assertNotNull(sim.flowPrices());
        assertNotNull(sim.affordabilityGate());
        assertNotNull(sim.housingMarket());
    }
}
