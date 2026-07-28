package vannon.syx.economy.core;

import init.resources.RESOURCE;
import init.resources.RESOURCES;

import java.util.HashMap;
import java.util.Map;

/**
 * PriorityRegistry — berechnet Ressourcen-Druck (pressure) aus FlowMeter und
 * scort einzelne Räume nach ihrem maximalen Output-Pressure. Wird von
 * FirmSizing verwendet um zu entscheiden welche Betriebe zuerst skaliert werden.
 * <p>
 * Recompute läuft einmal pro Tag im Daily-Cadence-Block. Bei gleichem {@code ticks}
 * ist der Aufruf ein No-Op (Schutz vor forceDiagnosticExport-Reentry).
 * </p>
 */
public final class PriorityRegistry {

    // ── State ──────────────────────────────────────────────────────
    private static final PriorityRegistry INSTANCE = new PriorityRegistry();

    /** Package-private Test-Hook (Sprint 9 Mockito reflection-on-field access). */
    final Map<RESOURCE, Double> pressure = new HashMap<>();

    /** Package-private Test-Hook (Sprint 9 Mockito reflection-on-field access). */
    long lastRecomputeTick = -1L;

    // ── Singleton ──────────────────────────────────────────────────
    private PriorityRegistry() {}
    public static PriorityRegistry instance() { return INSTANCE; }

    // ── API ────────────────────────────────────────────────────────
    public void recompute(FlowMeter.Snapshot snapshot, long ticks) {
        if (ticks == lastRecomputeTick) return;
        lastRecomputeTick = ticks;
        pressure.clear();
        if (snapshot == null || RESOURCES.ALL() == null || RESOURCES.ALL().size() < snapshot.size()) return;

        final double minDemand = EconConfig.priorityExpansionMinDemandLed;

        for (int i = 0; i < snapshot.size(); ++i) {
            RESOURCE r = RESOURCES.ALL().get(i);
            if (r == null) continue;

            // Stock-Guard vor Demand-Guard: leeres Lager ist immer ein Signal,
            // auch wenn demandPerDay() in der Init-Phase noch nicht stabilisiert ist.
            double stock = snapshot.stock(i);
            if (!Double.isFinite(stock) || stock <= 0.0) {
                pressure.put(r, 1.0);
                continue;
            }
            double demand = snapshot.demandPerDay(i);
            if (!Double.isFinite(demand) || demand < minDemand) {
                pressure.put(r, 0.0);
                continue;
            }
            // Anti-Micro: nur demand >= minDemand UND endlicher stock -> coverage-based pressure.
            pressure.put(r, Math.max(0.0, 1.0 - stock / demand));
        }
    }

    /** Score eines Output-Ressourcen-Arrays = max(pressure) über alle Ressourcen. */
    public double score(RESOURCE[] outputs) {
        if (outputs == null) return 0.0;
        double max = 0.0;
        for (RESOURCE r : outputs) {
            if (r == null) continue;
            Double p = pressure.get(r);
            if (p != null) max = Math.max(max, p);
        }
        return max;
    }
}
