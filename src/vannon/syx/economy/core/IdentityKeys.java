package vannon.syx.economy.core;

import settlement.entity.humanoid.Humanoid;
import settlement.room.main.RoomBlueprintImp;

/**
 * v0.1.3 (Phase-4.7-Blocker #8) — Stable Long-IDs für Save/Load-stabile Maps.
 * Aktuelle Methoden sind Save/Load-stabil (funktionsgleich über Engine-Restart).
 * Task 2 der Plan-Datei fügt roomKey() hinzu, sobald die Engine-API
 * (mTile() vs. tile() vs. coords()) verifiziert ist.
 */
public final class IdentityKeys {

    private IdentityKeys() {}

    public static long humanoidKey(Humanoid h) {
        return h == null ? 0L : (long) h.id();
    }

    public static long blueprintKey(RoomBlueprintImp bp) {
        if (bp == null || bp.key == null) return 0L;
        return (long) bp.key.hashCode();
    }
}
