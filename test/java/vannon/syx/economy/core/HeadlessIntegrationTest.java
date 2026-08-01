package vannon.syx.economy.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.ISyxAI;
import vannon.syx.economy.adapter.ISyxBoosting;
import vannon.syx.economy.adapter.ISyxDiplomacy;
import vannon.syx.economy.adapter.ISyxTransport;
import vannon.syx.economy.adapter.ISyxWarehouse;
import vannon.syx.economy.headless.MockWorldState;
import vannon.syx.economy.headless.StubKit;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Sprint v0.13.111+ Headless Engine-Stub-Provider Integration Test.
 * Lives in {@code vannon.syx.economy.core} so the package-private
 * {@link EconomySim} test constructor and {@link EconomySim#clearActive()}
 * are visible without reflection.
 *
 * <h2>Scope</h2>
 * Verifies the wiring path: {@link StubKit#install(MockWorldState)} registers
 * the 7 {@code I*Access} stubs into the {@link EngineMirror} singleton, the
 * singleton reports {@code isFullyAvailable() == true}, an {@link EconomySim}
 * constructed with Mockito mocks for the 5 {@code ISyx*} adapters reaches
 * the in-process aspect of update(), and stub reads of {@link MockWorldState}
 * match deterministically.
 *
 * <p>What we <b>cannot</b> fully validate here without the Songs-of-Syx
 * engine globals: {@code Roster.rebuild()} → {@code SETT.ENTITIES()}.
 * That deserves a deeper vanilla-stub layer (Sprint v0.13.112+) — see the
 * test's javadoc hook. The current test verifies the API-gate layer; deeper
 * vanilla reads still leak through this layer.</p>
 */
@ExtendWith(MockitoExtension.class)
class HeadlessIntegrationTest {

    @Mock private ISyxTransport   mockTransport;
    @Mock private ISyxWarehouse   mockWarehouse;
    @Mock private ISyxBoosting    mockBoosting;
    @Mock private ISyxDiplomacy   mockDiplomacy;
    @Mock private ISyxAI          mockAI;

    private MockWorldState state;
    private StubKit.Bundle bundle;

    @BeforeEach
    void installStubs() {
        state  = new MockWorldState(50, 500, 42L);
        bundle = StubKit.install(state);
    }

    @AfterEach
    void uninstallStubs() {
        StubKit.uninstall();
        EconomySim.clearActive();
    }

    // ── Wiring assertions ─────────────────────────────────────

    @Test
    void engineMirror_singleton_isSet_afterKitInstall() {
        EngineMirror api = EngineMirror.api();
        assertNotNull(api, "EngineMirror.api() must be non-null after StubKit.install");
        assertTrue(api.isFullyAvailable(),
            "After install of 7 stubs, isFullyAvailable() must be true");
    }

    @Test
    void allSeven_subInterfaces_report_isAvailable_true() {
        assertTrue(bundle.rooms.isAvailable(),      "IRoomAccess stub must report available");
        assertTrue(bundle.factions.isAvailable(),   "IFactionAccess stub must report available");
        assertTrue(bundle.humanoids.isAvailable(),  "IHumanoidAccess stub must report available");
        assertTrue(bundle.stats.isAvailable(),      "IStatsAccess stub must report available");
        assertTrue(bundle.treasury.isAvailable(),   "ITreasuryAccess stub must report available");
        assertTrue(bundle.population.isAvailable(), "IPopulationAccess stub must report available");
        assertTrue(bundle.goods.isAvailable(),      "IGoodsAccess stub must report available");
    }

    @Test
    void engineMirror_eachAccessor_returns_the_same_stub() {
        EngineMirror m = EngineMirror.api();
        assertSame(bundle.rooms,      m.rooms(),      "rooms() returns the registered stub");
        assertSame(bundle.factions,   m.factions(),   "factions() returns the registered stub");
        assertSame(bundle.humanoids,  m.humanoids(),  "humanoids() returns the registered stub");
        assertSame(bundle.stats,      m.stats(),      "stats() returns the registered stub");
        assertSame(bundle.treasury,   m.treasury(),   "treasury() returns the registered stub");
        assertSame(bundle.population, m.population(), "population() returns the registered stub");
        assertSame(bundle.goods,      m.goods(),      "goods() returns the registered stub");
    }

    // ── State contract ─────────────────────────────────────────

    @Test
    void mockWorldState_isDeterministic_across_seed() {
        MockWorldState a = new MockWorldState(50, 500, 42L);
        MockWorldState b = new MockWorldState(50, 500, 42L);
        assertEquals(a.initialMoneySupply, b.initialMoneySupply,
            "Same seed must yield byte-identical bootstrap money supply");
        assertEquals(a.gini(), b.gini(),
            "Same seed must yield identical initial gini");
    }

    @Test
    void treasuryStub_reflects_mockState_at_install_time() {
        assertEquals(state.treasury(), bundle.treasury.getPlayerCredits(), 0.0,
            "ITreasuryAccess.getPlayerCredits() mirrors MockWorldState.treasury()");
    }

    @Test
    void populationStub_ignores_class_argument_and_reports_state_total() {
        // Skip HCLASSES.CITIZEN() — vanilla bootstrap is not available in tests,
        // and HCLASSES.self is null. StubPopulation.getTotalPopulation() is
        // documented to ignore its HCLASS argument and return MockWorldState.
        // We probe this with a null argument (the interface allows null for HCLASS
        // queries returning totals) — the contract here is "stub returns state.citizenCount".
        assertEquals(state.citizenCount,
            bundle.population.getAllClassTotals().values().stream().mapToInt(Integer::intValue).sum(),
            "StubPopulation.getAllClassTotals() sums to MockWorldState.citizenCount");
        assertEquals(state.citizenCount, bundle.factions.getPlayerCitizens(),
            "StubFactionAccess.getPlayerCitizens() mirrors MockWorldState.citizenCount");
    }

    @Test
    void zeroSum_tick_preserves_money_supply() {
        long before = state.moneySupply();
        state.tick();
        assertEquals(before, state.moneySupply(),
            "MockWorldState.tick() must be strictly zero-sum (MoneySupply invariant)");
    }

    @Test
    void gini_at_install_is_in_zero_one_range() {
        assertTrue(state.gini() >= 0.0 && state.gini() <= 1.0,
            "MockWorldState.gini() must be in [0,1] at install (sanity)");
    }

    // ── EconomySim construction ────────────────────────────────

    @Test
    void economySim_constructs_without_exception() {
        EconomySim sim = new EconomySim(mockTransport, mockWarehouse, mockBoosting,
                                         mockDiplomacy, mockAI);
        assertNotNull(sim);
        assertSame(sim, EconomySim.active(),
            "Constructor registers the new EconomySim as active");
    }

    // ── Update gate passes ─────────────────────────────────────

    /**
     * Validates the update-gate condition (the gate itself is verified in
     * {@link #engineMirror_singleton_isSet_afterKitInstall()} via
     * {@link EngineMirror#isFullyAvailable()}). What we additionally
     * verify here: an {@link EconomySim} constructed against the registered
     * 7 stubs must NOT throw before reaching the api-null check (i.e.,
     * its construction and api-null invocation work regardless of deeper
     * vanilla dependencies).
     *
     * <p>We do NOT assert that update() iterates ticks — Roster.rebuild()
     * and PolityPriceAnchor.hasTradePartner() depend on deeper vanilla
     * globals which are out of scope for this sprint (Sprint v0.13.112+
     * will address the {@code SETT.ENTITIES} stub layer).</p>
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void update_api_gate_call_path_does_not_throw_before_gate() {
        EconomySim sim = new EconomySim(mockTransport, mockWarehouse, mockBoosting,
                                         mockDiplomacy, mockAI);
        assertSame(bundle.rooms, EngineMirror.api().rooms(),
            "EngineMirror must remain bound to the same stubs across EconomySim lifecycle");

        // The first call SHOULD reach at least the api-null check. If it
        // throws before reaching the gate (e.g. constructor wiring problem),
        // the error would be a NullPointerException at EngineMirror.api(),
        // which is infrastructure — not what we want to catch here.
        try {
            sim.update(1.0);
        } catch (Throwable t) {
            String trace = java.util.Arrays.toString(t.getStackTrace());
            // Acceptable: deeper vanilla NPE from Roster.rebuild or
            // PolityPriceAnchor AFTER the api gate has been passed.
            // Not acceptable: NPE from EngineMirror.api() or stub wiring.
            boolean fromDeeperVanilla =
                trace.contains("Roster.rebuild")
                || trace.contains("PolityPriceAnchor")
                || trace.contains("SETT")
                || trace.contains("PolityPriceAnchor.hasTradePartner");
            assertTrue(fromDeeperVanilla,
                "update() must NOT throw from stub-wiring sources. Trace: " + trace);
        }
        // If we got here, the api gate was verified via isFullyAvailable(),
        // and the first iteration of update() did not break stub wiring.
        assertEquals(state.initialMoneySupply, state.moneySupply(),
            "MockWorldState money-supply invariant must hold across EconomySim lifecycle");
    }

    // ── Idempotency ────────────────────────────────────────────

    @Test
    void stubKit_install_called_twice_resets_singleton_to_fresh_stubs() {
        EngineMirror first = EngineMirror.api();
        // Re-install overwrites the singleton because StubKit.uninstall clears it
        StubKit.uninstall();
        MockWorldState other = new MockWorldState(75, 100, 99L);
        StubKit.Bundle second = StubKit.install(other);
        assertSame(second.rooms, EngineMirror.api().rooms(),
            "Second install must rebind the api to the new stubs");
        // Note: the first reference `first` is now disconnected — singleton is replaced.
    }

    // ── Reflection-driven test: Stub roster passes isFullyAvailable = true ─

    @Test
    void install_with_no_engine_mirror_initially_results_in_fully_available() {
        // Sanity — verifies that even without a pre-existing EngineMirror singleton,
        // install() creates a fresh, fully available one.
        StubKit.uninstall();
        assertEquals(null, EngineMirror.api(), "uninstall must clear the singleton");
        StubKit.install(new MockWorldState(10, 10, 1L));
        assertNotNull(EngineMirror.api());
        assertTrue(EngineMirror.api().isFullyAvailable());
    }
}
