package vannon.syx.economy.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import init.resources.RESOURCE;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import game.time.TIME;
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.IRoomAccess;

/**
 * Extracted from FirmLedger (Sprint 7 Legacy-Drift-Reduktion): firm sizing
 * hill-climber logic and furniture debug dump.
 *
 * <p>Static utility — all methods receive the FirmLedger instance or
 * package-private fields directly.</p>
 */
final class FirmSizing {

    private FirmSizing() {}

    /** Production-Stuck-Threshold in Game-Sekunden.
     *  FirmSizingRefreshDays × secondsPerDay × 0.5 — also halbe Sizing-Periode.
     *  Verhindert dass neue Firmen (Cold-Start) fälschlich als stuck erkannt werden. */
    static int stuckThresholdSeconds() {
        return Math.max(10, (int)(EconConfig.firmSizingRefreshDays * TIME.secondsPerDay() * 0.5));
    }

    // ── Firm sizing ────────────────────────────────────────────────

    static void size(FirmLedger ledger, RoomInstance room, FirmLedger.FirmState state) {
        if (EngineMirror.api() == null) return;
        IRoomAccess roomAccess = EngineMirror.api().rooms();
        int minimum = Math.min(room.employees().max(), Math.max(0, EconConfig.minimumWorkersPerWorkplace));
        if (FirmEconomyKernel.shouldIdle(state.profit, EconConfig.firmSizingHysteresis)) {
            // Production-Stuck-Override: bei anhaltender Ressourcen-Senke
            // (output=0, input>0) komplett idle statt minimum.
            int idleTarget = (state.stuckSeconds > stuckThresholdSeconds()) ? 0 : minimum;
            state.marketTarget = idleTarget;
            state.hill = new FirmEconomyKernel.HillState(idleTarget, 0.0, 1, true);
            roomAccess.setFirmTarget(room, idleTarget);
            return;
        }
        int target = Math.max(minimum, state.marketTarget);
        if (Math.abs(room.employees().employed() - target) > 1) {
            // settle-phase early-return: auch Blueprint-Reorg-Firmen soll der Player-Hint erreichen.
            emitPriorityHint(state, room, true);
            return;
        }
        emitPriorityHint(state, room, false);
        FirmEconomyKernel.HillState before = state.hill;
        int oldBest = before == null ? target : before.bestTarget();
        FirmEconomyKernel.HillResult result = FirmEconomyKernel.hillStep(
            before, target, state.profit, minimum, room.employees().max(),
            EconConfig.firmSizingHillclimbStep, EconConfig.firmSizingHysteresis);
        state.hill = result.state();
        if (target < oldBest && result.observedSlope() > 0.0) {
            state.marginal = FirmLedger.slopeClamp(result.observedSlope());
        } else if (result.observedSlope() != 0.0 && target > oldBest) {
            state.marginal = FirmLedger.slopeClamp(result.observedSlope());
        }
        // Sprint v0.13.99+ Escape-Cliff: Flag persistieren für furniture_debug-Audit-Trail,
        // UND nextTarget zwingend clampen — Engine setFirmTarget darf nie blueprint-max+1 setzen.
        state.escapeCliffTriggered = result.escapeCliff();
        int maxEmp = room.employees().max();
        int clampedTarget = Math.max(minimum, Math.min(maxEmp, result.nextTarget()));
        state.marketTarget = clampedTarget;
        roomAccess.setFirmTarget(room, clampedTarget);
        if (result.escapeCliff()) {
            // raw observedSlope statt state.marginal — letzteres kann stale sein
            // (slopeClamp-bias aus vorigen ticks wenn weder target<oldBest noch target>oldBest).
            EventLog.log("FIRM_ESCAPE_CLIFF",
                    (room.blueprintI() != null ? room.blueprintI().key : "?")
                            + " at blueprint-max=" + maxEmp
                            + " marginal=" + String.format(java.util.Locale.ROOT, "%.2f", result.observedSlope())
                            + " profit=" + String.format(java.util.Locale.ROOT, "%.2f", state.profit)
                            + " — build additional instance");
        }
    }

    static int initialMarketTarget(int employed, int maximum, int minimum) {
        if (maximum <= 0) return 0;
        return Math.max(0, Math.min(maximum, Math.max(employed, minimum)));
    }

    /** Sprint v0.13.99 — Player-Hint bei hohem Resource-Druck + profitabler Firma.
     *  KEIN Engine-Override (setFirmTarget würde Blueprint-max nicht erhöhen).
     *  Wird in settle-phase (settled=true) und im Hill-Step-Hauptpfad (settled=false) genutzt. */
    private static void emitPriorityHint(FirmLedger.FirmState state, RoomInstance room, boolean settled) {
        if (!EconConfig.priorityVectorEnabled) return;
        if (state.marginal < EconConfig.priorityMarginalSafetyThreshold) return;
        if (state.outputs == null || state.outputs.length == 0) return;
        double score = PriorityRegistry.instance().score(state.outputs);
        if (score <= EconConfig.priorityExpansionThreshold) return;
        String bp = room.blueprintI() != null ? room.blueprintI().key : "?";
        EventLog.log("FIRM_PRIORITY",
                bp + " pressure=" + String.format(java.util.Locale.ROOT, "%.2f", score)
                + (settled ? " settled — consider building additional instance (demand > capacity)"
                           : " marginal=" + String.format(java.util.Locale.ROOT, "%.2f", state.marginal)
                             + " employed=" + room.employees().employed() + "/" + room.employees().max()));
    }

    static boolean excludedFromMarketSizing(FirmLedger ledger, RoomInstance room) {
        return room == null
            || EconomicRoles.excludedFromMarketSizing((RoomBlueprintImp) room.blueprintI())
            || ledger.stateWarehouse(room);
    }

    /** Sprint v0.13.99 — Furniture-Debug-Audit: gesunde Firma + Score > Threshold?
     *  Liefert TRUE nur wenn die Firma profitabel ist und das PriorityVector-Score die
     *  Schwelle überschreitet. */
    private static boolean isPriorityProposal(FirmLedger.FirmState s, int hardTarget, int marketTarget) {
        return EconConfig.priorityVectorEnabled
                && hardTarget > 0 && marketTarget > 0 && s.profit > 0.0
                && s.outputs != null && s.outputs.length > 0
                && PriorityRegistry.instance().score(s.outputs) > EconConfig.priorityExpansionThreshold;
    }

    // ── Furniture debug dump ───────────────────────────────────────

    static void dumpFurnitureDebug(FirmLedger ledger, FlowMeter meter, FlowPrices prices,
                                    int ticks, double gameSeconds) {
        if (meter == null) return;
        int throttle = Math.max(1, EconConfig.debugFurnitureDumpEveryTicks);
        if (ledger.lastFurnitureDumpTick >= 0 && ticks - ledger.lastFurnitureDumpTick < throttle) return;
        ledger.lastFurnitureDumpTick = ticks;
        Map<RoomInstance, FlowMeter.FirmSnapshot> snapsByRoom = new IdentityHashMap<>();
        for (FlowMeter.FirmSnapshot s : meter.firmSnapshots()) {
            snapsByRoom.put(s.room(), s);
        }
        Path path = furnitureDumpPath();
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            boolean writeHeader = !Files.exists(path);
            try (BufferedWriter w = Files.newBufferedWriter(path,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (writeHeader) {
                    w.write("tick;day;bp_key;employed;max;hardTarget;marketTarget;physicalSeen;"
                            + "profit;marginal;incomeCarry;out_count;out0_name;out0_per_day;out0_producedDelta;"
                            + "in_count;in0_name;in0_per_day;in0_consumedDelta;stuckSeconds;stuckThreshold;note\n");
                }
                int stuckThreshold = stuckThresholdSeconds();
                for (Map.Entry<Long, FirmLedger.FirmState> entry : ledger.firms.entrySet()) {
                    if (entry.getKey() == null) continue;
                    RoomInstance room = FirmLedger.roomFor(entry.getKey());
                    if (room == null || !room.exists() || room.employees() == null) continue;
                    RoomBlueprintImp bp = room.blueprintI();
                    if (bp == null) continue;
                    if (EconomicRoles.excludedFromMarketAccounting(bp)) continue;
                    FlowMeter.FirmSnapshot snap = snapsByRoom.get(room);
                    if (snap == null || snap.outputCount() == 0) continue;
                    RESOURCE out = snap.outputResource(0);
                    if (out == null) continue;
                    String outKey = out.key != null ? out.key.toUpperCase(Locale.ROOT) : "?";
                    String bpKey = bp.key != null ? bp.key.toUpperCase(Locale.ROOT) : "?";
                    boolean isFurniture = outKey.contains("MOEBEL") || outKey.contains("FURNITURE")
                            || outKey.contains("CHAIR") || outKey.contains("STUHL")
                            || outKey.contains("TISCH") || outKey.contains("TABLE")
                            || bpKey.contains("ZIMMER") || bpKey.contains("CARPENT");
                    if (!isFurniture) continue;
                    FirmLedger.FirmState s = entry.getValue();
                    int employed = room.employees().employed();
                    int maxEmp = room.employees().max();
                    int hardTarget = room.employees().hardTarget();
                    int marketTarget = s.targetInitialized ? s.marketTarget : 0;
                    double day = (double) ticks / EconConfig.DEFAULT_TICKS_PER_DAY;
                    String inKey = "";
                    double inRate = 0.0;
                    int inConsumed = 0;
                    if (snap.inputCount() > 0 && snap.inputResource(0) != null) {
                        inKey = snap.inputResource(0).key != null ? snap.inputResource(0).key : "";
                        inRate = snap.inputPerDay(0);
                        inConsumed = snap.consumedSinceLastSample(0);
                    }
                    int producedDelta = snap.producedSinceLastSample(0);
                    // Note-Reihenfolge: kritische Stati übersteuern den Priority-Vorschlag.
                    // Sprint v0.13.99+: ESCAPE-CLIFF vor PRIORITY-EXPAND (escape-cliff ist
                    // spezifischer: explizit hillStep hat blueprint-max-recommendation gegeben).
                    String note;
                    if (hardTarget == 0 || marketTarget == 0) {
                        note = "TARGET-ZERO";
                    } else if (s.profit <= 0.0) {
                        note = "PROFIT-NEGATIVE";
                    } else if (producedDelta == 0 && day > 5.0) {
                        note = "OUT-STUCK";
                    } else if (s.escapeCliffTriggered) {
                        note = "ESCAPE-CLIFF";
                    } else if (isPriorityProposal(s, hardTarget, marketTarget)) {
                        note = "PRIORITY-EXPAND";
                    } else {
                        note = "OK";
                    }
                    w.write(String.format(Locale.ROOT,
                            "%d;%.3f;%s;%d;%d;%d;%d;%s;%.2f;%.2f;%.2f;%d;%s;%.4f;%d;%d;%s;%.4f;%d;%d;%d;%s%n",
                            ticks, day, bp.key, employed, maxEmp, hardTarget, marketTarget,
                            s.physicalSeen ? "true" : "false",
                            s.profit, s.marginal, s.incomeCarry,
                            snap.outputCount(), out.key, snap.outputPerDay(0), producedDelta,
                            snap.inputCount(), inKey, inRate, inConsumed,
                            s.stuckSeconds, stuckThreshold, note));
                }
            }
        } catch (IOException e) {
            System.err.println("[ECON] furniture debug dump failed: " + e.getMessage());
        }
    }

    static Path furnitureDumpPath() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            if (appdata == null || appdata.isEmpty()) {
                appdata = System.getProperty("user.home") + "\\AppData\\Roaming";
            }
            return Paths.get(appdata, "songsofsyx", "mods", "SyxEconomyMod", "diagnostics", "furniture_debug.csv");
        }
        return Paths.get(System.getProperty("user.home"),
                ".local", "share", "songsofsyx", "mods", "SyxEconomyMod", "diagnostics", "furniture_debug.csv");
    }
}
