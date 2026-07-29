package vannon.syx.economy.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    /** Sancta-Workshop-Cap: Blueprints, die per Default unrentabel skaliert haben.
     *  Wenn ein Blueprint in dieser Map ist UND bereits {@code cap} Instanzen existieren
     *  UND die Firma keinen positiven Profit/Marginal hat, wird kein Player-Hint
     *  emittiert (NO-EXPANSION). Verhindert Skalierung in den Ruin.
     *  Default-Cap für gelistete Blueprints: 3. Für alle anderen: kein Cap. */
    static final Map<String, Integer> sanctaWorkshopCap = Map.of(
            "WORKSHOP_CARPENTER", 3,
            "WORKSHOP_POTTER",    3,
            "WORKSHOP_WEAVER",    3,
            "WORKSHOP_TAILOR",    3,
            "WORKSHOP_TANNER",    3,
            "REFINER_BAKERY",     3,
            "REFINER_BREWERY",    3,
            "REFINER_SMOOTHIE",   3,
            "WORKSHOP_MASON",     3
    );

    /** Tageszähler: wie oft emitPriorityHint oder ESCAPE-CLIFF pro Tag feuert.
     *  DiagnosticExporter.exportDay() liest + resettet diesen Zähler pro In-Game-Tag.
     *  volatile: DiagnosticExporter.exportDay() liest + resettet; size() inkrementiert.
     *  Beide Main-Thread — volatile nur für den Fall dass zukünftige Refactors exportDay()
     *  auf einen Worker verlagern. Kosten: 0. */
    static volatile int priorityExpansionSignalsThisDay;

    /** Per-Blueprint-Signals: blueprintKey → Anzahl 'would-expand'-Vorschläge pro Tag.
     *  DiagnosticExporter.exportDay() liest + resettet diese Map pro In-Game-Tag.
     *  Ermöglicht Pandas-Heatmap: df.pivot('game_day','blueprint','expansion_signals'). */
    static final Map<String, Integer> priorityExpansionSignalsByBlueprint = new HashMap<>();

    /** Dedup-Set: welche Blueprints heute bereits FIRM_ESCAPE_CLIFF geloggt haben.
     *  DiagnosticExporter.exportDay() resettet pro Tag. Verhindert Spam wenn
     *  10 Carpenter-Firmen gleichzeitig am Max sind — nur die erste loggt. */
    static final Set<String> escapeCliffLoggedToday = new HashSet<>();

    // ════════════════════════════════════════════════════════════════════
    // RES-035 — Allocation-Path Log-Hook (FirmSizing-Anteil).
    // Hook 6 (Hill-Step): size() wird aufgerufen. Auch der shouldIdle-Früh-
    //                      return zählt — wichtige Beweisspur dafür dass
    //                      der Hill-Climber überhaupt die Firma inspiziert.
    // Single-Thread (Main-Thread) ⇒ volatile reicht. Drainer:
    // FirmLedger.drainAllocationCounters() liest+resettet am Tagesende.
    // ════════════════════════════════════════════════════════════════════
    static volatile long allocHillStep;

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
        // RES-035 Hook 6 — Hill-Climber inspiziert diese Firma.
        allocHillStep++;
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
            priorityExpansionSignalsThisDay++;
            String bpKey = room.blueprintI() != null ? room.blueprintI().key : "?";
            priorityExpansionSignalsByBlueprint.merge(bpKey, 1, Integer::sum);
            // Dedup: nur einmal pro Blueprint pro Tag loggen (10 Carpenter am Max = 1 Log, nicht 10).
            // raw observedSlope statt state.marginal — letzteres kann stale sein
            // (slopeClamp-bias aus vorigen ticks wenn weder target<oldBest noch target>oldBest).
            if (escapeCliffLoggedToday.add(bpKey)) {
                EventLog.log("FIRM_ESCAPE_CLIFF",
                    (room.blueprintI() != null ? room.blueprintI().key : "?")
                            + " at blueprint-max=" + maxEmp
                            + " marginal=" + String.format(java.util.Locale.ROOT, "%.2f", result.observedSlope())
                            + " profit=" + String.format(java.util.Locale.ROOT, "%.2f", state.profit)
                            + " — build additional instance");
            }
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
        // ── Sancta-Workshop-Cap: NO-EXPANSION wenn Cap erreicht + Firma unrentabel ──
        if (room.blueprintI() != null) {
            Integer cap = sanctaWorkshopCap.get(room.blueprintI().key);
            if (cap != null) {
                int existing = countInstances(room.blueprintI().key);
                boolean profitable = state.profit > 0.0 && state.marginal >= EconConfig.priorityMarginalSafetyThreshold;
                if (existing >= cap && !profitable) {
                    EventLog.log("FIRM_NO_EXPANSION",
                            room.blueprintI().key + " at cap=" + existing + "/" + cap
                            + " profit=" + String.format(java.util.Locale.ROOT, "%.2f", state.profit)
                            + " marginal=" + String.format(java.util.Locale.ROOT, "%.2f", state.marginal)
                            + " — skipping expansion hint (unprofitable at cap)");
                    return;
                }
            }
        }
        if (state.marginal < EconConfig.priorityMarginalSafetyThreshold) return;
        if (state.outputs == null || state.outputs.length == 0) return;
        double score = PriorityRegistry.instance().score(state.outputs);
        if (score <= EconConfig.priorityExpansionThreshold) return;
        String bp = room.blueprintI() != null ? room.blueprintI().key : "?";
        priorityExpansionSignalsThisDay++;
        priorityExpansionSignalsByBlueprint.merge(bp, 1, Integer::sum);
        EventLog.log("FIRM_PRIORITY",
                bp + " pressure=" + String.format(java.util.Locale.ROOT, "%.2f", score)
                + (settled ? " settled — consider building additional instance (demand > capacity)"
                           : " marginal=" + String.format(java.util.Locale.ROOT, "%.2f", state.marginal)
                             + " employed=" + room.employees().employed() + "/" + room.employees().max()));
    }

    /** Zählt existierende Instanzen eines Blueprints (nur wenn Engine verfügbar). */
    private static int countInstances(String blueprintKey) {
        if (EngineMirror.api() == null) return 0;
        int count = 0;
        for (RoomBlueprintIns<?> bp : EngineMirror.api().rooms().getRoomIns()) {
            if (bp != null && bp.key.equals(blueprintKey)) {
                count = bp.instancesSize();
                break;
            }
        }
        return count;
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
                    // RES-035 Hook-2-Trace: |employed − marketTarget| > 1 wird als DIVERGE-Tag
                    // orthogonal angehängt (kann mit anderen Notes koexistieren). Das ist die
                    // Smoking-Gun-Spur für den 6-vs-45-employees-Bug im furniture_debug CSV.
                    String note;
                    if (hardTarget == 0 || marketTarget == 0) {
                        note = "TARGET-ZERO";
                    } else if (s.profit <= 0.0) {
                        note = "PROFIT-NEGATIVE";
                    } else if (sanctaWorkshopCap.containsKey(bp.key)
                            && countInstances(bp.key) >= sanctaWorkshopCap.get(bp.key)) {
                        note = "NO-EXPANSION";
                    } else if (producedDelta == 0 && day > 5.0) {
                        note = "OUT-STUCK";
                    } else if (s.escapeCliffTriggered) {
                        note = "ESCAPE-CLIFF";
                    } else if (isPriorityProposal(s, hardTarget, marketTarget)) {
                        note = "PRIORITY-EXPAND";
                    } else {
                        note = "OK";
                    }
                    if (Math.abs(employed - marketTarget) > 1
                            && Math.abs(employed - hardTarget) > 1) {
                        note = note + "|DIVERGE(e=" + employed
                                + ",mT=" + marketTarget
                                + ",hT=" + hardTarget + ")";
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
