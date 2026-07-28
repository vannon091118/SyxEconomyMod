package vannon.syx.economy.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vannon.syx.economy.adapter.ISyxAI;
import vannon.syx.economy.adapter.ISyxBoosting;
import vannon.syx.economy.adapter.ISyxDiplomacy;
import vannon.syx.economy.adapter.ISyxTransport;
import vannon.syx.economy.adapter.ISyxWarehouse;

/**
 * Sprint 9 — 8-1: Erster Mockito-Test für EconomySim.
 * Fokussiert auf Mockito-spezifische Features (verify, when/thenReturn).
 * Generische Subsystem-Exposition wird von {@link EconomySimComponentTest} abgedeckt.
 */
@ExtendWith(MockitoExtension.class)
class EconomySimMockitoTest {

    @Mock private ISyxTransport mockTransport;
    @Mock private ISyxWarehouse mockWarehouse;
    @Mock private ISyxBoosting mockBoosting;
    @Mock private ISyxDiplomacy mockDiplomacy;
    @Mock private ISyxAI mockAI;

    @AfterEach
    void clearActive() {
        EconomySim.clearActive();
    }

    private EconomySim newSim() {
        return new EconomySim(mockTransport, mockWarehouse, mockBoosting, mockDiplomacy, mockAI);
    }

    // ─── Construction + AI Adapter Injection ─────────────────────────

    @Test
    void construction_registersAsActive_andInjectsAdapters() {
        EconomySim sim = newSim();
        assertSame(sim, EconomySim.active());
        assertSame(mockAI, sim.aiAdapter());
    }

    @Test
    void aiAdapter_isOddjobbing_delegatesToMock() {
        when(mockAI.isOddjobbing(any())).thenReturn(true);
        EconomySim sim = newSim();
        assertTrue(sim.aiAdapter().isOddjobbing(null));
        verify(mockAI).isOddjobbing(any());
    }

    // ─── Debug Adapter Status ────────────────────────────────────────

    @Test
    void debugAdapterStatus_queriesAdapterAvailability() {
        when(mockTransport.isDistanceAvailable()).thenReturn(true);
        when(mockWarehouse.isStoringLockAvailable()).thenReturn(true);
        when(mockDiplomacy.isAvailable()).thenReturn(true);
        EconomySim sim = newSim();
        String[] status = sim.debugAdapterStatus();
        assertNotNull(status);
        assertTrue(status.length >= 5);
        verify(mockTransport).isDistanceAvailable();
        verify(mockWarehouse).isStoringLockAvailable();
        verify(mockDiplomacy, atLeastOnce()).isAvailable();
    }

    @Test
    void debugAdapterStatus_reportsOK_whenAdaptersAvailable() {
        when(mockTransport.isDistanceAvailable()).thenReturn(true);
        when(mockWarehouse.isStoringLockAvailable()).thenReturn(true);
        when(mockDiplomacy.isAvailable()).thenReturn(true);
        EconomySim sim = newSim();
        String[] status = sim.debugAdapterStatus();
        boolean foundTransport = false;
        for (String line : status) {
            if (line.contains("Transport")) {
                assertTrue(line.contains("OK"), line);
                foundTransport = true;
            }
        }
        assertTrue(foundTransport, "Status should include Transport line");
    }

    @Test
    void debugAdapterStatus_reportsFAIL_whenAdaptersUnavailable() {
        when(mockTransport.isDistanceAvailable()).thenReturn(false);
        when(mockWarehouse.isStoringLockAvailable()).thenReturn(false);
        when(mockDiplomacy.isAvailable()).thenReturn(false);
        EconomySim sim = newSim();
        String[] status = sim.debugAdapterStatus();
        boolean foundTransportFail = false;
        for (String line : status) {
            if (line.contains("Transport")) {
                assertTrue(line.contains("FAIL"), line);
                foundTransportFail = true;
            }
        }
        assertTrue(foundTransportFail, "Status should report Transport FAIL");
    }

    // ─── Update Safety (Mockito-Variante) ───────────────────────────

    @Test
    void update_withZeroDelta_returnsEarly() {
        EconomySim sim = newSim();
        sim.update(0.0);
        assertEquals(0, sim.ticks());
    }

    @Test
    void update_ticksStayZeroWithoutEngine() {
        EconomySim sim = newSim();
        for (int i = 0; i < 10; i++) {
            sim.update(1.0);
        }
        assertEquals(0, sim.ticks());
    }

    // ─── Subsystem Wiring ────────────────────────────────────────────

    @Test
    void allMajorSubsystemsNonNull_afterConstruction() {
        EconomySim sim = newSim();
        assertNotNull(sim.wallets());
        assertNotNull(sim.firmLedger());
        assertNotNull(sim.warehouseMarket());
        assertNotNull(sim.stateWarehouses());
        assertNotNull(sim.progression());
        assertNotNull(sim.flowMeter());
        assertNotNull(sim.flowPrices());
        assertNotNull(sim.transportMarket());
        assertNotNull(sim.housingMarket());
    }
}
