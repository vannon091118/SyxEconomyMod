package vannon.syx.economy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für {@link AuditKernel} — Goldmengen-Audit (Treasury-Soll-Ist-Differenz).
 *
 * <p>Läuft ohne Spiel-Engine. {@link AuditKernel.Terms} ist ein 17-Felder-Record;
 * jeder Aufruf muss alle 17 long-Werte liefern (Java widening konvertiert int → long
 * für die Argumente).</p>
 *
 * <p>Field order in {@code Terms}:</p>
 * <pre>
 *   [0] seed                  [+]   [9]  legacyConsumption      [-]
 *   [1] imported              [+]   [10] religionTax            [-]
 *   [2] treasuryOut           [+]   [11] liturgyTax             [-]
 *   [3] roundingDrift         [+]   [12] warehouseTax           [-]
 *   [4] exported              [-]   [13] wagesPaid              [+]
 *   [5] escheated             [-]   [14] housingRent            [-]
 *   [6] wealthTax             [-]   [15] propertySalesCollected [-]
 *   [7] headTax               [-]   [16] propertyDividendsPaid  [+]
 *   [8] marketReceipts        [-]
 * </pre>
 */
class AuditKernelTest {

    /** All-zero Terms record. */
    private static AuditKernel.Terms emptyTerms() {
        return new AuditKernel.Terms(
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L
        );
    }

    @Test
    void expected_zeroInputs_isZero() {
        assertEquals(0L, AuditKernel.expected(emptyTerms()));
    }

    @Test
    void expected_onlySeedsArePositive_componentsAddUp() {
        // Positives: seed=1000, imported=200, treasuryOut=50, roundingDrift=5, wagesPaid=300, dividendsPaid=10
        // All subtractors zero.
        AuditKernel.Terms t = new AuditKernel.Terms(
            1000L, 200L, 50L, 5L,
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
            300L,
            0L, 0L,
            10L
        );
        assertEquals(1565L, AuditKernel.expected(t),
            "expected = 1000+200+50+5+300+10 = 1565");
    }

    @Test
    void expected_subtractorsOffsetAdditions() {
        // Full 17-field record. Adds at positions 0-3, 13, 16; subs at 4-12, 14, 15.
        AuditKernel.Terms t = new AuditKernel.Terms(
            // [0-3] adds: seed, imported, treasuryOut, roundingDrift
            1000L, 200L, 50L, 5L,
            // [4-12] subs: exported, escheated, wealthTax, headTax, marketReceipts,
            //                  legacyConsumption, religionTax, liturgyTax, warehouseTax
            100L, 50L, 200L, 100L, 50L, 50L, 30L, 20L, 10L,
            // [13] add: wagesPaid
            300L,
            // [14-15] subs: housingRent, propertySalesCollected
            200L, 30L,
            // [16] add: propertyDividendsPaid
            10L
        );
        long adds  = 1000L + 200L + 50L + 5L + 300L + 10L;
        long subs  = 100L + 50L + 200L + 100L + 50L + 50L + 30L + 20L + 10L + 200L + 30L;
        assertEquals(adds - subs, AuditKernel.expected(t));
    }

    @Test
    void delta_zero_iff_balanced() {
        AuditKernel.Terms t = new AuditKernel.Terms(
            1000L, 100L, 50L, 0L,
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
            100L,
            0L, 0L,
            0L
        );
        long expected = AuditKernel.expected(t);
        assertEquals(0L, AuditKernel.delta(expected, t),
            "delta = living - expected = 0 when living == expected");
    }

    @Test
    void delta_detectsLeak_negativeDelta() {
        // Terms says: economy should hold 100L (seed=100).
        // Living count is 50 → 50 missing → delta = 50 - 100 = -50.
        AuditKernel.Terms t = new AuditKernel.Terms(
            100L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L,
            0L
        );
        long living = 50L;
        assertEquals(-50L, AuditKernel.delta(living, t),
            "delta < 0 when living < expected (money leaked)");
    }

    @Test
    void delta_detectsInjection_positiveDelta() {
        // Terms says: economy should hold 100L (seed=100).
        // Living count is 150 → 50 surplus → delta = 150 - 100 = +50.
        AuditKernel.Terms t = new AuditKernel.Terms(
            100L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L,
            0L
        );
        long living = 150L;
        assertEquals(50L, AuditKernel.delta(living, t),
            "delta > 0 when living > expected (money appeared unaccounted)");
    }

    @Test
    void emptyEconomy_balanced() {
        assertEquals(0L, AuditKernel.delta(0L, emptyTerms()),
            "empty terms + living=0 → balanced");
    }

    @Test
    void delta_conceptIsPureLinear_longAccumulation() {
        // Adding 1000 to seed and asking for delta on living = expected must yield 0.
        AuditKernel.Terms t1 = emptyTerms();
        AuditKernel.Terms t2 = new AuditKernel.Terms(
            1000L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L,
            0L
        );
        long living1 = AuditKernel.expected(t1);
        long living2 = AuditKernel.expected(t2);
        assertEquals(0L, AuditKernel.delta(living1, t1), "t1 balanced");
        assertEquals(0L, AuditKernel.delta(living2, t2), "t2 balanced");
    }
}
