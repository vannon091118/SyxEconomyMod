package vannon.syx.economy.core.io;

import init.resources.RESOURCES;
import settlement.room.industry.module.Industry;
import vannon.syx.economy.core.FlowMeter;

/**
 * Phase 2 (IO-Analysis): Empirische Input-Output-Tabelle.
 *
 * <p>Berechnet aus {@link FlowMeter.Snapshot}-Daten die technische
 * Koeffizienten-Matrix {@code A[n×n]} und die Leontief-Inverse
 * {@code L = (I - A)^(-1)}.</p>
 *
 * <p>{@code A[i][j]} = wie viel Ressource i wird pro Einheit Ressource j
 * verbraucht (direkt). {@code L[i][j]} = Gesamtbedarf von i für eine
 * Einheit finale Nachfrage nach j (inkl. Ketteneffekte).</p>
 *
 * <p>Berechnung: einmal pro Ingame-Tag, NICHT jeden Tick.
 * Snapshot-basiert, empirisch (FlowMeter-Supply als Produktionsraten).</p>
 *
 * <p>Mockito-safe: Konstruktor akzeptiert {@code 0} für lazy-init.
 * {@link #resize(int)} allokiert Arrays nach Engine-Bootstrap.</p>
 */
public final class IOMatrix {

    private int N;  // non-final für lazy-init (resize)
    private double[][] A;  // Technische Koeffizienten: A[input][output]
    private double[][] L;  // Leontief-Inverse: (I - A)^(-1)
    private boolean valid;

    // Pre-allocated work arrays (avoid GC pressure per-tick)
    private double[][] flowFromTo;
    private double[] totalOutput;
    private double[][] gaussAugmented;

    /**
     * @param numResources Anzahl der Ressourcen (0 für lazy-init, siehe resize())
     */
    public IOMatrix(int numResources) {
        this.N = numResources;
        this.A = numResources > 0 ? new double[numResources][numResources] : new double[0][0];
        this.L = numResources > 0 ? new double[numResources][numResources] : new double[0][0];
        this.flowFromTo = numResources > 0 ? new double[numResources][numResources] : new double[0][0];
        this.totalOutput = numResources > 0 ? new double[numResources] : new double[0];
        this.gaussAugmented = numResources > 0 ? new double[numResources][numResources * 2] : new double[0][0];
    }

    /**
     * Lazy-Resize — wird aufgerufen wenn die initiale Größe 0 war
     * (Mockito-safe: EconomySim initialisiert IOMatrix(0), resize folgt
     * im ersten Compute-Zyklus nach Engine-Bootstrap).
     */
    public void resize(int newSize) {
        if (newSize > 0 && newSize != this.N) {
            this.N = newSize;
            this.A = new double[newSize][newSize];
            this.L = new double[newSize][newSize];
            this.flowFromTo = new double[newSize][newSize];
            this.totalOutput = new double[newSize];
            this.gaussAugmented = new double[newSize][newSize * 2];
            this.valid = false;
        }
    }

    /** Gibt zurück ob die Matrix erfolgreich berechnet wurde. */
    public boolean isValid() { return this.valid; }

    /** Anzahl der Ressourcen (Matrix-Dimension). */
    public int size() { return this.N; }

    /**
     * Berechnet die empirische IO-Matrix aus FlowMeter-Daten.
     * {@code A[i][j]} = (Input-Flow von i in j-Produzenten) / (Output von j).
     *
     * <p>Wird einmal pro Ingame-Tag aufgerufen. Multi-Output-Industrien
     * verteilen ihre Inputs proportional auf ihre empirischen Output-Raten
     * (supplyPerDay aus FlowMeter.Snapshot).</p>
     *
     * @param meter FlowMeter mit aktuellen FirmState-Snapshots
     * @param graph IOGraph für Industrie-Struktur
     */
    public void compute(FlowMeter meter, IOGraph graph) {
        if (!graph.isBuilt() || N == 0) return;

        // Reset A
        for (int i = 0; i < N; ++i) {
            java.util.Arrays.fill(this.A[i], 0.0);
        }

        FlowMeter.Snapshot snap = meter.snapshot();
        if (snap == null) return;

        // Re-use pre-allocated work arrays (no GC pressure)
        for (int i = 0; i < N; ++i) {
            java.util.Arrays.fill(this.flowFromTo[i], 0.0);
        }
        java.util.Arrays.fill(this.totalOutput, 0.0);
        double[][] flowFromTo = this.flowFromTo;
        double[] totalOutput = this.totalOutput;

        // Iteriere alle Ressourcen über den IOGraph
        for (int r = 0; r < N; ++r) {
            init.resources.RESOURCE res = (init.resources.RESOURCE) RESOURCES.ALL().get(r);
            for (Industry ind : graph.getProducers(res)) {
                // Outputs dieser Industrie
                int outCount = ind.outs().size();
                if (outCount == 0) continue;

                // Sammle alle Output-Ressourcen und empirische Raten
                int[] outIndices = new int[outCount];
                double[] outRates = new double[outCount];
                double totalOutRate = 0.0;
                for (int o = 0; o < outCount; ++o) {
                    outIndices[o] = ind.outs().get(o).resource.index();
                    // Empirische Rate: supplyPerDay aus FlowMeter.Snapshot
                    double rate = (outIndices[o] >= 0 && outIndices[o] < snap.size())
                            ? Math.max(1.0, snap.supplyPerDay(outIndices[o]))
                            : 1.0;
                    outRates[o] = rate;
                    totalOutRate += rate;
                }
                if (totalOutRate <= 0.0) continue;

                // Inputs dieser Industrie
                int inCount = ind.ins().size();
                for (int i2 = 0; i2 < inCount; ++i2) {
                    int inIdx = ind.ins().get(i2).resource.index();
                    if (inIdx < 0 || inIdx >= N) continue;

                    // Verteile Input proportional auf Outputs
                    for (int o = 0; o < outCount; ++o) {
                        int outIdx = outIndices[o];
                        if (outIdx < 0 || outIdx >= N) continue;
                        double share = outRates[o] / totalOutRate;
                        flowFromTo[inIdx][outIdx] += share;
                        totalOutput[outIdx] += share;
                    }
                }
            }
        }

        // Berechne technische Koeffizienten: A[i][j] = flowFromTo[i][j] / totalOutput[j]
        for (int j = 0; j < N; ++j) {
            if (totalOutput[j] <= 0.0) continue;
            for (int i = 0; i < N; ++i) {
                this.A[i][j] = flowFromTo[i][j] / totalOutput[j];
            }
        }

        // Berechne Leontief-Inverse L = (I - A)^(-1)
        computeLeontiefInverse();
        this.valid = true;
    }

    /**
     * Berechnet L = (I - A)^(-1) via Gauß-Jordan-Elimination.
     * Ergebnis in this.L[input][output].
     */
    private void computeLeontiefInverse() {
        // B = I - A (re-use pre-allocated augmented matrix)
        double[][] B = this.gaussAugmented;
        for (int i = 0; i < N; ++i) {
            for (int j = 0; j < N; ++j) {
                B[i][j] = (i == j ? 1.0 : 0.0) - this.A[i][j];
            }
            for (int j = N; j < N * 2; ++j) {
                B[i][j] = 0.0;
            }
            B[i][N + i] = 1.0; // Identity auf der rechten Seite
        }

        // Gauß-Jordan
        for (int col = 0; col < N; ++col) {
            // Pivot-Suche
            int pivot = col;
            double maxVal = Math.abs(B[col][col]);
            for (int row = col + 1; row < N; ++row) {
                if (Math.abs(B[row][col]) > maxVal) {
                    maxVal = Math.abs(B[row][col]);
                    pivot = row;
                }
            }
            if (maxVal < 1e-12) continue; // Singulär — überspringe

            // Swap
            if (pivot != col) {
                double[] tmp = B[col];
                B[col] = B[pivot];
                B[pivot] = tmp;
            }

            // Normalize
            double diag = B[col][col];
            for (int j = 0; j < N * 2; ++j) {
                B[col][j] /= diag;
            }

            // Eliminate
            for (int row = 0; row < N; ++row) {
                if (row == col) continue;
                double factor = B[row][col];
                if (Math.abs(factor) < 1e-15) continue;
                for (int j = 0; j < N * 2; ++j) {
                    B[row][j] -= factor * B[col][j];
                }
            }
        }

        // Extrahiere L aus der rechten Hälfte
        for (int i = 0; i < N; ++i) {
            for (int j = 0; j < N; ++j) {
                this.L[i][j] = B[i][N + j];
            }
        }
    }

    /**
     * Berechnet den Gesamtbedarf: ∆X = L × ∆D.
     *
     * @param finalDemand Vektor der finalen Nachfrage pro Ressource
     * @return Vektor des Gesamtbedarfs (inkl. Ketteneffekte)
     */
    public double[] computeTotalRequirements(double[] finalDemand) {
        double[] result = new double[N];
        if (!this.valid || N == 0) return result;
        for (int i = 0; i < N; ++i) {
            double sum = 0.0;
            for (int j = 0; j < N; ++j) {
                sum += this.L[i][j] * finalDemand[j];
            }
            result[i] = sum;
        }
        return result;
    }

    /** Direkter Koeffizient: A[input][output]. */
    public double getDirectCoefficient(int input, int output) {
        if (input < 0 || input >= N || output < 0 || output >= N) return 0.0;
        return this.A[input][output];
    }

    /** Gesamtkoeffizient (Leontief): L[input][output]. */
    public double getTotalCoefficient(int input, int output) {
        if (input < 0 || input >= N || output < 0 || output >= N) return 0.0;
        return this.L[input][output];
    }

    /** Gibt die rohe A-Matrix zurück (read-only intent). */
    public double[][] getA() { return this.A; }

    /** Gibt die rohe L-Matrix zurück (read-only intent). */
    public double[][] getL() { return this.L; }
}
