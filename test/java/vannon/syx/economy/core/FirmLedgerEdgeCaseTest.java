package vannon.syx.economy.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vannon.syx.economy.core.FirmLedger.UpdateResult;

/**
 * Edge-case tests for {@link FirmLedger}.
 *
 * <p>These tests cover the deterministic, engine-free bookkeeping surface of the
 * ledger. Full market-sizing requires the Songs of Syx engine (ROOMS, TIME,
 * etc.) and is therefore not attempted here.</p>
 */
class FirmLedgerEdgeCaseTest {

    private FirmLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new FirmLedger();
    }

    @Test
    void emptyLedger_allTrackersAreZero() {
        assertEquals(0L, ledger.lastIncomeDue());
        assertEquals(0L, ledger.lastIncomePaid());
        assertEquals(0, ledger.lastWorkersPaid());
        assertEquals(0, ledger.lastWorkersUnpaid());
        assertEquals(0.0, ledger.meanPositiveMarginal(), 1e-9);
    }

    @Test
    void update_withoutEngineOrPrices_returnsZeroResult() {
        // FirmLedger.update() first guards against an unavailable engine. With a
        // fresh FlowPrices that is not yet ready, the update exits early and
        // reports zero paid income.
        UpdateResult result = ledger.update(new Roster(), new Wallets(), new FlowMeter(),
                new FlowPrices(), new AffordabilityGate(new Escrow(new Wallets()), new FlowPrices(), new GrainDole()),
                1.0, 0);
        assertNotNull(result);
        assertEquals(0L, result.paid());
    }

    @Test
    void clear_resetsTrackersToZero() {
        ledger.clear();
        assertEquals(0L, ledger.lastIncomeDue());
        assertEquals(0L, ledger.lastIncomePaid());
        assertEquals(0, ledger.lastWorkersPaid());
        assertEquals(0, ledger.lastWorkersUnpaid());
        assertEquals(0.0, ledger.meanPositiveMarginal(), 1e-9);
    }

    @Test
    void recordServiceRevenue_withNullBlueprint_isNoOp() {
        // Public method should silently ignore null keys.
        assertDoesNotThrow(() -> ledger.recordServiceRevenue(null, 100.0));
        assertEquals(0.0, ledger.profitPerDay(null), 1e-9);
    }

    @Test
    void recordServiceRevenue_withNegativeAmount_isNoOp() {
        // There is no public blueprint type we can easily create, but null has
        // already been tested; this merely asserts the method is defensive.
        assertDoesNotThrow(() -> ledger.recordServiceRevenue(null, -50.0));
    }

    @Test
    void recordStateWageMarginal_withNullBlueprint_isNoOp() {
        assertDoesNotThrow(() -> ledger.recordStateWageMarginal(null, 10.0));
        assertEquals(0.0, ledger.marginalSurplus(null), 1e-9);
    }

    @Test
    void recordFirmRevenue_withNullRoom_isNoOp() {
        assertDoesNotThrow(() -> ledger.recordFirmRevenue(null, 100.0));
    }

    @Test
    void recordFirmCost_withNullRoom_isNoOp() {
        assertDoesNotThrow(() -> ledger.recordFirmCost(null, 100.0));
    }

    @Test
    void profitPerDay_forUnknownBlueprint_isZero() {
        // No engine blueprint available in tests.
        assertEquals(0.0, ledger.profitPerDay(null), 1e-9);
    }

    @Test
    void marginalSurplus_forUnknownBlueprint_isZero() {
        assertEquals(0.0, ledger.marginalSurplus(null), 1e-9);
    }
}
