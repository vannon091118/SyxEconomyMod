package vannon.syx.economy.core;

import java.util.IdentityHashMap;
import java.util.Map;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomInstance;

/**
 * Phase 5e: Per-room operating-mode controller extracted from FirmLedger.
 *
 * <p>Manages the PRODUCE / PAUSED / MOTHBALLED lifecycle for state-funded
 * public works. When a room is PAUSED, its state-wage-marginal contribution
 * is zero. When MOTHBALLED, it scales by {@link EconConfig#mothballOperatingCostMultiplier}.
 * Non-state-funded blueprints always return 1.0 (vanilla behavior).</p>
 *
 * <p>The backing map is registered with {@link IdentityMapRegistry} for
 * explicit clear-on-load — Save/Load re-instantiates RoomInstance objects,
 * breaking reference equality. The registry pre-emptively clears the map
 * instead of silently returning null.</p>
 */
public final class RoomOperatingModeController {

    private final Map<RoomInstance, EconConfig.RoomOperatingMode> opModes =
        new IdentityHashMap<>();

    public RoomOperatingModeController() {
        IdentityMapRegistry.register("RoomOperatingModeController", "opModes", opModes);
    }

    /** Set operating-mode for a room. Caller (UI) decides PRODUCE / PAUSED / MOTHBALLED. */
    public void set(RoomInstance room, EconConfig.RoomOperatingMode mode) {
        if (room == null || mode == null) return;
        opModes.put(room, mode);
    }

    /** Get operating-mode for a room, defaulting to PRODUCE. */
    public EconConfig.RoomOperatingMode get(RoomInstance room) {
        if (room == null) return EconConfig.RoomOperatingMode.PRODUCE;
        EconConfig.RoomOperatingMode mode = opModes.get(room);
        return mode == null ? EconConfig.RoomOperatingMode.PRODUCE : mode;
    }

    /**
     * Per-Blueprint Cost-Faktor für state-wage-marginal accumulation.
     * 1.0 für PRODUCE-Mode, 0.0 für ausschließlich PAUSED,
     * {@link EconConfig#mothballOperatingCostMultiplier} für MOTHBALLED.
     * Mittelwert über alle Räume dieses Blueprints. Non-state-funded
     * blueprints: immer 1.0.
     *
     * @param firms the FirmLedger's firm map (RoomInstance → FirmState)
     */
    public double costScale(RoomBlueprintImp blueprint,
                            Map<RoomInstance, ?> firms) {
        if (blueprint == null || !EconomicRoles.stateFundedPublicWorks(blueprint)) {
            return 1.0;
        }
        int total = 0;
        double scaleSum = 0.0;
        for (Map.Entry<RoomInstance, ?> entry : firms.entrySet()) {
            RoomInstance roomInstance = entry.getKey();
            if (roomInstance == null || roomInstance.blueprintI() != blueprint) continue;
            ++total;
            EconConfig.RoomOperatingMode mode =
                opModes.getOrDefault(roomInstance, EconConfig.RoomOperatingMode.PRODUCE);
            switch (mode) {
                case PRODUCE:
                    scaleSum += 1.0;
                    break;
                case PAUSED:
                    scaleSum += 0.0;
                    break;
                case MOTHBALLED:
                    scaleSum += EconConfig.mothballOperatingCostMultiplier;
                    break;
            }
        }
        return total == 0 ? 1.0 : scaleSum / (double) total;
    }
}
