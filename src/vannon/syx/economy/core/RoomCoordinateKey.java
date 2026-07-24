package vannon.syx.economy.core;

/**
 * Phase 3: Stable long-key encoding for room tile coordinates.
 *
 * <p>Songs-of-Syx world grid is fixed across saves, so a (tx, ty) pair is a
 * perfectly stable identifier for a tile. We pack two 24-bit ints into a
 * single long so it can serve as a {@link HashMap} key without boxing.</p>
 *
 * <p>Replaces the IdentityHashMap-by-RoomInstance pattern that silently
 * lost FirmState on save/load because Java reference-identity shifted.</p>
 *
 * <p><b>NOT for use with property ownership</b> — PropertyLedger's
 * (tx, ty, blueprintKey) encoding is semantically distinct (owned-asset
 * vs. ephemeral-room) and we leave it where it lives. Don't unify them.</p>
 *
 * <p>Bit layout (64 bits total):</p>
 * <ul>
 *   <li>Bits 0–23:  tx (mask 0xFFFFFF, supports maps up to 4096×4096 = 16M tiles)</li>
 *   <li>Bits 24–47: ty (mask 0xFFFFFF)</li>
 *   <li>Bits 48–63: unused — reserved for future extensions (e.g. blueprint differentiation)</li>
 * </ul>
 */
final class RoomCoordinateKey {
    private static final int TX_MASK = 0xFFFFFF;
    private static final int TY_MASK = 0xFFFFFF;
    private static final int TX_BITS = 24;

    private RoomCoordinateKey() {}

    /** Pack (tx, ty) tile coordinates into a stable long key. */
    static long tileOf(int tx, int ty) {
        return ((long) (tx & TX_MASK) << TX_BITS) | (long) (ty & TY_MASK);
    }

    /** Unpack tx from a key produced by {@link #tileOf}. */
    static int txOf(long key) {
        return (int) ((key >>> TX_BITS) & TX_MASK);
    }

    /** Unpack ty from a key produced by {@link #tileOf}. */
    static int tyOf(long key) {
        return (int) (key & TY_MASK);
    }
}
