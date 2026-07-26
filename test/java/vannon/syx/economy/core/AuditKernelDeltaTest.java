package vannon.syx.economy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AuditKernel.expected/delta invariants.
 * Sprint 6.3 — additive test, korrigiert in v0.13.32 (17-Arg Terms-Signatur).
 */
class AuditKernelDeltaTest {

    /** Convenience: builds Terms from args in declared order.
     *  Reihenfolge gemaess {@link AuditKernel.Terms}:
     *  seed, imported, treasuryOut, roundingDrift, exported, escheated,
     *  wealthTax, headTax, marketReceipts, legacyConsumption, religionTax,
     *  liturgyTax, warehouseTax, wagesPaid, housingRent,
     *  propertySalesCollected, propertyDividendsPaid
     */
    private static AuditKernel.Terms terms(
            long seed, long imported, long treasuryOut, long roundingDrift,
            long exported, long escheated, long wealthTax, long headTax,
            long marketReceipts, long legacyConsumption,
            long religionTax, long liturgyTax, long warehouseTax,
            long wagesPaid, long housingRent,
            long propertySalesCollected, long propertyDividendsPaid) {
        return new AuditKernel.Terms(seed, imported, treasuryOut, roundingDrift,
                exported, escheated, wealthTax, headTax, marketReceipts,
                legacyConsumption, religionTax, liturgyTax, warehouseTax,
                wagesPaid, housingRent, propertySalesCollected, propertyDividendsPaid);
    }

    @Test
    void zeroTermsZeroLivingYieldsZeroDelta() {
        AuditKernel.Terms t = terms(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        long expected = AuditKernel.expected(t);
        assertEquals(0L, expected);
        assertEquals(0L, AuditKernel.delta(0L, t));
    }

    @Test
    void inflowsMinusOutflowsEqualsExpected() {
        AuditKernel.Terms t = terms(
                1_000_000L, /*seed*/
                50_000L,    /*imported*/
                -200_000L,  /*treasuryOut*/
                0L,         /*roundingDrift*/
                -20_000L,   /*exported*/
                0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L,
                -100_000L,  /*wagesPaid*/
                -500_000L,  /*housingRent*/
                0L, 0L);
        // expected = 1_000_000 + 50_000 + (-200_000) + 0 + (-100_000) + 0
        //         - (-20_000) - 0 - 0 - 0 - 0 - 0 - 0 - 0
        //         - 0 - (-500_000) - 0 - 0
        // = 1_000_000 + 50_000 - 200_000 - 100_000 + 20_000 + 500_000
        // = 1_270_000
        assertEquals(1_270_000L, AuditKernel.expected(t));
    }

    @Test
    void deltaLivingEqualsLivingMinusExpected() {
        AuditKernel.Terms t = terms(1000L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        long expected = AuditKernel.expected(t);
        long living = expected + 50L;
        assertEquals(50L, AuditKernel.delta(living, t));
        assertEquals(-50L, AuditKernel.delta(expected - 50L, t));
    }

    @Test
    void allOutflowsAreSubtracted() {
        // seed=0, treasuryOut=-100, wagesPaid=-100, alle outflows=0.
        // expected = 0 + 0 + (-100) + 0 + (-100) + 0 - 0 - 0 - 0 - 0 - 0 - 0
        //          - 0 - 0 - 0 - 0
        // = -200
        AuditKernel.Terms t = terms(
                0L, 0L, -100L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L,
                -100L,  /*wagesPaid (treated as +)*/
                0L, 0L, 0L);
        assertEquals(-200L, AuditKernel.expected(t));
    }

    @Test
    void roundingDriftTermContributesLinearly() {
        AuditKernel.Terms base = terms(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        AuditKernel.Terms plus10 = bump(base, 3 /* roundingDrift */, 10L);
        AuditKernel.Terms plusMinus50 = bump(base, 3, -50L);
        // expected(plus10) - expected(base) === 10 (roundingDrift contributes +10)
        assertEquals(AuditKernel.expected(base) + 10L, AuditKernel.expected(plus10));
        // expected(plusMinus50) - expected(base) === -50
        assertEquals(AuditKernel.expected(base) - 50L, AuditKernel.expected(plusMinus50));
    }

    @Test
    void consultationMatchLivingYieldsZeroDelta() {
        AuditKernel.Terms t = terms(
                900_000L, 0L, -200_000L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L,
                -150_000L, -50_000L, 0L, 0L);
        long expected = AuditKernel.expected(t);
        assertEquals(0L, AuditKernel.delta(expected, t));
    }

    /** Bumps a single field by {@code delta} on the given Terms-record. */
    private static AuditKernel.Terms bump(AuditKernel.Terms src, int idx, long delta) {
        long[] a = new long[]{
                src.seed(), src.imported(), src.treasuryOut(), src.roundingDrift(),
                src.exported(), src.escheated(), src.wealthTax(), src.headTax(),
                src.marketReceipts(), src.legacyConsumption(),
                src.religionTax(), src.liturgyTax(), src.warehouseTax(),
                src.wagesPaid(), src.housingRent(),
                src.propertySalesCollected(), src.propertyDividendsPaid()
        };
        a[idx] += delta;
        return new AuditKernel.Terms(a[0], a[1], a[2], a[3], a[4], a[5],
                a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13],
                a[14], a[15], a[16]);
    }
}
