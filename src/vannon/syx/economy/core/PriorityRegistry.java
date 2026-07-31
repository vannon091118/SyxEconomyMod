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

    /** Sprint v0.13.103+ — Stock-Coverage-Cache fuer Staircase + Staatsbestand.
     *  Indexed per RESOURCE (Slot-Map = RESOURCES.ALL().size()). Aktualisiert in
     *  recompute(); gelesen von FirmSizing.size() via minStockCoverage(outputs). */
    double[] stockCoverage = new double[0];

    /** Minimum Stock-Coverage ueber alle output-Ressourcen. Returns 0.0 (= "kein
     *  Constraint → Tier 0 = full capacity") wenn outputs leer/null oder REGCACHE
     *  noch nicht initialisiert. Reviewer-Fix v0.13.103+: MAX_VALUE-Sentinel
     *  hat alle Firms in Tier 4 (idle) gezwungen, was explizit falsch ist
     *  (Service-Firmen ohne outputs, Cold-Start vor erstem recompute-Tick). */
    public double minStockCoverage(RESOURCE[] outputs) {
        if (outputs == null || outputs.length == 0) return 0.0;
        if (RESOURCES.ALL() == null) return 0.0;
        if (stockCoverage.length == 0) return 0.0;
        double min = Double.MAX_VALUE;
        int allSize = RESOURCES.ALL().size();
        for (RESOURCE r : outputs) {
            if (r == null) continue;
            // Snake2D LIST hat kein indexOf() — lineare Suche. Firmen haben 1-2
            // outputs, also O(N) pro Lookup ist OK (N ≈ 100-500 Resource-Typen).
            int idx = -1;
            for (int j = 0; j < allSize; j++) {
                if (r == RESOURCES.ALL().get(j)) { idx = j; break; }
            }
            if (idx < 0 || idx >= stockCoverage.length) continue;
            double c = stockCoverage[idx];
            if (Double.isFinite(c) && c < min) min = c;
        }
        return min == Double.MAX_VALUE ? 0.0 : min;
    }

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
        // Sprint v0.13.103+ — Coverage-Cache (Slab aligned with RESOURCES.ALL().size()).
        int size = RESOURCES.ALL().size();
        if (stockCoverage.length < size) stockCoverage = new double[size];

        for (int i = 0; i < snapshot.size(); ++i) {
            RESOURCE r = RESOURCES.ALL().get(i);
            if (r == null) continue;

            // Stock-Guard vor Demand-Guard: leeres Lager ist immer ein Signal,
            // auch wenn demandPerDay() in der Init-Phase noch nicht stabilisiert ist.
            double stock = snapshot.stock(i);
            if (!Double.isFinite(stock) || stock <= 0.0) {
                pressure.put(r, 1.0);
                if (i < stockCoverage.length) stockCoverage[i] = 0.0;
                continue;
            }
            double demand = snapshot.demandPerDay(i);
            if (!Double.isFinite(demand) || demand < minDemand) {
                pressure.put(r, 0.0);
                if (i < stockCoverage.length) stockCoverage[i] = Double.MAX_VALUE;
                continue;
            }
            // Anti-Micro: nur demand >= minDemand UND endlicher stock -> coverage-based pressure.
            double coverage = stock / demand;
            if (i < stockCoverage.length) stockCoverage[i] = coverage;
            // Sprint v0.13.103+ — Staatsbestand-Override: critical stock (< minCoverage)
            // forciert pressure = 1.0 als kritischer Bedarf. Beispiel: MOEBEL-Lager
            // unter 10% von Tagesbedarf → CARPENTER wird HIGH-PRIORITY.
            if (EconConfig.firmStaatsbestandEnabled
                    && coverage < EconConfig.firmStaatsbestandMinCoverage) {
                pressure.put(r, 1.0);
            } else {
                pressure.put(r, Math.max(0.0, 1.0 - coverage));
            }
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
