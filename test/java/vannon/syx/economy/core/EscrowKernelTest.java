package vannon.syx.economy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für {@link EscrowKernel} — Escrow-Sperrkonto-Buchhaltung.
 *
 * <p>Läuft ohne Spiel-Engine; pure Funktion.</p>
 */
class EscrowKernelTest {

    /* ── spendable() ─────────────────────────────────────────────────── */

    @Test
    void spendable_zeroBalance_zeroReserved_isZero() {
        assertEquals(0, EscrowKernel.spendable(0, 0));
    }

    @Test
    void spendable_balanceGreaterThanReserved_isBalanceMinusReserved() {
        assertEquals(700, EscrowKernel.spendable(1000, 300));
    }

    @Test
    void spendable_balanceLessThanReserved_clampedToZero() {
        // balance - reserved would be negative → clamped to 0
        assertEquals(0, EscrowKernel.spendable(500, 700));
    }

    @Test
    void spendable_negativeReserved_treatedAsZero() {
        // reserved=-5: max(0,-5) = 0 → 1000 - 0 = 1000
        assertEquals(1000, EscrowKernel.spendable(1000, -5));
    }

    @Test
    void spendable_negativeBalance_clampedToZero() {
        assertEquals(0, EscrowKernel.spendable(-100, 0));
    }

    /* ── canReserve() ────────────────────────────────────────────────── */

    @Test
    void canReserve_spendableCoversQuote_isTrue() {
        assertTrue(EscrowKernel.canReserve(1000, 300, 700));
    }

    @Test
    void canReserve_spendableBelowQuote_isFalse() {
        assertFalse(EscrowKernel.canReserve(1000, 500, 600),
            "spendable=500 < quote=600");
    }

    @Test
    void canReserve_quoteNegative_isFalse() {
        assertFalse(EscrowKernel.canReserve(1000, 0, -1),
            "negative quote must always be rejected");
    }

    @Test
    void canReserve_zeroQuote_isTrue() {
        // 0 quote is always reservable
        assertTrue(EscrowKernel.canReserve(0, 0, 0));
        assertTrue(EscrowKernel.canReserve(1000, 999, 0));
    }

    /* ── canSettle() ─────────────────────────────────────────────────── */

    @Test
    void canSettle_normalSettlement_isTrue() {
        // balance=1000, reserved=700, quote=700, bill=600
        //   balance ≥ bill (1000 ≥ 600) ✓, reserved ≥ quote (700 ≥ 700) ✓, bill ≤ quote (600 ≤ 700) ✓
        assertTrue(EscrowKernel.canSettle(1000, 700, 700, 600));
    }

    @Test
    void canSettle_billExceedsQuote_isFalse() {
        assertFalse(EscrowKernel.canSettle(1000, 500, 700, 800),
            "settling more than was reserved is fraud");
    }

    @Test
    void canSettle_balanceBelowBill_isFalse() {
        assertFalse(EscrowKernel.canSettle(500, 0, 700, 600),
            "cannot settle bill larger than current balance");
    }

    @Test
    void canSettle_reservedBelowQuote_isFalse() {
        assertFalse(EscrowKernel.canSettle(1000, 200, 700, 600),
            "cannot settle if reserved < quote (released the reservation first)");
    }

    @Test
    void canSettle_quoteNegative_isFalse() {
        assertFalse(EscrowKernel.canSettle(1000, 0, -1, 0),
            "negative quote always rejected");
    }

    @Test
    void canSettle_billNegative_isFalse() {
        assertFalse(EscrowKernel.canSettle(1000, 0, 100, -1),
            "negative bill always rejected");
    }
}
