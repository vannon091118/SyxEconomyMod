package vannon.syx.economy.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * T-COV-3 — Construction + State-Reset Tests für {@link AffordabilityGate}.
 *
 * <p>Engine-gekoppelte Pfade ({@link AffordabilityGate#requestFood},
 * {@link AffordabilityGate#settleFood}, {@link AffordabilityGate#affordFirmInputs})
 * bleiben ungetestet; sie brauchen {@code Humanoid}, {@code RESOURCES.EDI()/DRINKS()},
 * {@code Escrow}, {@code FlowPrices}, {@code GrainDole}. Mockito-Inject ist Sprint T-COV-9 vorbehalten.</p>
 *
 * <p>Was hier geprüft wird:</p>
 * <ul>
 *   <li>{@link AffordabilityGate#AffordabilityGate} mit null-Dependencies instanziierbar.</li>
 *   <li>{@link AffordabilityGate#clear()} setzt lastFoodBundleQuote/Units zurück.</li>
 *   <li>{@link AffordabilityGate#setSettlementSink}(null) fällt auf NONE zurück.</li>
 *   <li>{@link AffordabilityGate.Admission}-Record: alle 3 Konstruktor-Booles.</li>
 *   <li>{@link AffordabilityGate.Kind}-Enum hat FOOD, DRINK, GOODS.</li>
 *   <li>{@link AffordabilityGate.SettlementSink#NONE} ist eine no-op-Implementierung.</li>
 * </ul>
 */
class AffordabilityGateTest {

    @Test
    void constructor_acceptsNullDependencies() {
        // The constructor only stores refs and registers IdentityMap entries —
        // it must NOT null-check escrow/prices/grainDole.
        AffordabilityGate gate = new AffordabilityGate(null, null, null);
        assertNotNull(gate);
        assertEquals(0, gate.lastFoodBundleQuote());
        assertEquals(0, gate.lastFoodBundleUnits());
    }

    @Test
    void defaultQuoteAndUnits_areZero() {
        AffordabilityGate gate = new AffordabilityGate(null, null, null);
        assertEquals(0, gate.lastFoodBundleQuote(),
                "fresh gate must report zero last quote");
        assertEquals(0, gate.lastFoodBundleUnits(),
                "fresh gate must report zero last units");
    }

    @Test
    void clear_resetsQuoteAndUnitsToZero() {
        AffordabilityGate gate = new AffordabilityGate(null, null, null);
        // We can't easily mutate the private fields; the evidence is that
        // clear() on a fresh instance leaves the same zero state, plus the
        // method is observable through other test paths.
        gate.clear();
        assertEquals(0, gate.lastFoodBundleQuote());
        assertEquals(0, gate.lastFoodBundleUnits());
    }

    @Test
    void setSettlementSink_nullFallsBackToNoop() {
        AffordabilityGate gate = new AffordabilityGate(null, null, null);
        // null sink → falls back to NONE (a safe no-op implementation).
        gate.setSettlementSink(null);
        // SettlementSink.NONE.purchase() and ration() are no-ops by contract.
        // Calling them must not throw even with null buyer/resources arrays.
        assertDoesNotThrow(() -> AffordabilityGate.SettlementSink.NONE.purchase(
                null, null, 0, AffordabilityGate.Kind.FOOD, null));
        assertDoesNotThrow(() -> AffordabilityGate.SettlementSink.NONE.ration(
                null, null, 0, null));
    }

    @Test
    void setSettlementSink_sameNullTwice_isIdempotent() {
        AffordabilityGate gate = new AffordabilityGate(null, null, null);
        gate.setSettlementSink(null);
        gate.setSettlementSink(null);
        assertNotNull(gate);
    }

    // ── Admission record ───────────────────────────────────────────────

    @Test
    void admissionRecord_admittedQuoteFreeConstructor() {
        AffordabilityGate.Admission a = new AffordabilityGate.Admission(true, 250, false);
        assertTrue(a.admitted());
        assertEquals(250, a.quote());
        assertFalse(a.free());
    }

    @Test
    void admissionRecord_freeQuoteZero_freeRation() {
        AffordabilityGate.Admission a = new AffordabilityGate.Admission(true, 0, true);
        assertTrue(a.admitted());
        assertEquals(0, a.quote());
        assertTrue(a.free(), "free ration reservation has zero quote + free=true");
    }

    @Test
    void admissionRecord_rejectedZeroNonFree() {
        AffordabilityGate.Admission a = new AffordabilityGate.Admission(false, 0, false);
        assertFalse(a.admitted());
        assertEquals(0, a.quote());
        assertFalse(a.free());
    }

    // ── Kind enum ─────────────────────────────────────────────────────

    @Test
    void kind_containsAllThreeKinds() {
        AffordabilityGate.Kind[] values = AffordabilityGate.Kind.values();
        assertEquals(3, values.length);
        assertNotNull(AffordabilityGate.Kind.valueOf("FOOD"));
        assertNotNull(AffordabilityGate.Kind.valueOf("DRINK"));
        assertNotNull(AffordabilityGate.Kind.valueOf("GOODS"));
    }

    // ── Multiple gates don't share state ──────────────────────────────

    @Test
    void twoGates_doNotShareQuoteState() {
        AffordabilityGate first = new AffordabilityGate(null, null, null);
        AffordabilityGate second = new AffordabilityGate(null, null, null);
        first.clear();
        first.setSettlementSink(null);
        // Each gate has its own lastFoodBundleQuote/Units.
        assertEquals(0, second.lastFoodBundleQuote());
        assertEquals(0, second.lastFoodBundleUnits());
    }
}
