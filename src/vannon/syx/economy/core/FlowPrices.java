package vannon.syx.economy.core;

import java.util.Arrays;
import vannon.syx.economy.core.FlowMeter;

public final class FlowPrices {
    private static final double COVERAGE_FLOOR = 0.005;

    // ── 7-1a: Scarcity-Kaskaden-Algorithmus ──────────────────────────
    //
    // Zweistufiger Preisbildungs-Mechanismus:
    //
    // STUFE 1 — FlowPrices.scarcityMultiplier(effectiveCoverage, up, down, lo, hi)
    //   coverage < 1.0: multiplier = max(COVERAGE_FLOOR, coverage)^(-UP)
    //     UP=0.8 → bei coverage=0.5: 0.5^(-0.8) ≈ 1.74× Anker
    //     UP=0.8 → bei coverage=0.1: 0.1^(-0.8) ≈ 6.31× Anker
    //   coverage >= 1.0: multiplier = coverage^(-DOWN)
    //     DOWN=1.375 → bei coverage=2.0: 2.0^(-1.375) ≈ 0.39× Anker
    //   Clamp: [priceClampLo=0.001, priceClampHi=100.0]
    //   COVERAGE_FLOOR=0.005 verhindert Infinity bei coverage→0
    //
    // STUFE 2 — LocalPrices.scarcity(perCapita, target)
    //   Signal = scarcityMaxMultiple^(-tanh(ln(perCapita/target) / steepness))
    //   scarcityMaxMultiple=1.5, scarcitySteepness=1.0
    //   perCapita=target → signal=1.0 (kein Effekt)
    //   perCapita<target → signal>1.0 (Knappheit)
    //   perCapita>target → signal<1.0 (Überfluss)
    //
    // POST-PROCESSING (nach Stufe 1+2):
    //   1. scarcityPriceBoost=0.3: preis *= (1 + signal × 0.3)
    //   2. foodPriceCapMultiplier=6.0: Nahrung gecappt auf 6× Anker
    //   3. phaseFactor (Pop<300): preis *= linear(0.5..1.0, pop/300)
    //
    // Referenz-Konstanten: EconConfig.java:345-391, 525-530
    // ─────────────────────────────────────────────────────────────────

    /** Cold-Start / Full-Depletion Coverage. Gibt bei supply=0 UND stock=0
     *  einen moderaten Scarcity-Signal (~2× Anker bei Elasticity 0.8) statt
     *  maximalen Spike (7.1×). Trennt Cold-Start von Mid-Game-Breakdown
     *  (stock>0 umgeht den Guard → D-004 bleibt intakt). Livetest v0.13.56. */
    private static final double COLD_START_COVERAGE = 0.4;

    private double[] price = new double[0];
    private double[] anchor = new double[0];
    private double[] coverage = new double[0];
    private double[] scarcity = new double[0];  // stored for future UI display (scarcity signal per resource)
    private boolean ready;

    public void refresh(double[] anchors, FlowMeter.Snapshot meter, Parameters parameters) {
        this.refresh(anchors, meter, parameters, new double[anchors.length]);
    }

    public void refresh(double[] anchors, FlowMeter.Snapshot meter, Parameters parameters, double[] scarcitySignals) {
        int size = anchors.length;
        if (meter.size() != size || parameters.targetCoverageDays.length != size) {
            throw new IllegalArgumentException("price, meter, and target arrays must have equal lengths");
        }
        this.ensureCapacity(size);
        double popFactor = EconConfig.phaseFactor(); // T8 (H8): Early-Game-Preisdampfung
        for (int good = 0; good < size; ++good) {
            this.anchor[good] = anchors[good];
            this.coverage[good] = FlowPrices.effectiveCoverage(meter.stock(good), meter.supplyPerDay(good), meter.demandPerDay(good), parameters.targetCoverageDays[good], parameters.flowLookaheadDays);
            this.price[good] = FlowPrices.localPrice(anchors[good], this.coverage[good], parameters.scarcityElasticityUp, parameters.scarcityElasticityDown, parameters.priceClampLo, parameters.priceClampHi, parameters.priceAbsoluteMax);
            if (good < scarcitySignals.length) {
                double s = Math.max(0.0, Math.min(1.0, scarcitySignals[good]));
                this.scarcity[good] = s;
                this.price[good] *= (1.0 + s * EconConfig.scarcityPriceBoost);
                if (parameters.priceAbsoluteMax > 0.0 && this.price[good] > parameters.priceAbsoluteMax) {
                    this.price[good] = parameters.priceAbsoluteMax;
                }
            }
            // T8 (P3-korrigiert): phaseFactor anwenden. PhaseFactor < 1.0 reduziert den
            // Preis NACH dem absoluteMax-Clamp — early-game Preise bleiben kleiner als
            // absoluteMax weil der Faktor unter 1.0 liegt. Das Clamp wird dadurch NICHT
            // enger gezogen (absoluteMax ist eine Obergrenze), sondern der resultierende
            // Preis liegt bei early-game garantiert unter absoluteMax.
            if (popFactor < 1.0) {
                this.price[good] *= popFactor;
            }
        }
        this.ready = true;
    }

    public boolean ready() {
        return this.ready;
    }

    public double price(int good) {
        return this.ready && good >= 0 && good < this.price.length ? this.price[good] : 0.0;
    }

    public int priceRoundedUp(int good) {
        double value = this.price(good);
        if (!(value > 0.0)) {
            return 0;
        }
        if (value >= 2.147483647E9) {
            return Integer.MAX_VALUE;
        }
        return (int)Math.ceil(value);
    }

    public double anchor(int good) {
        return this.ready && good >= 0 && good < this.anchor.length ? this.anchor[good] : 0.0;
    }

    public double coverage(int good) {
        return this.ready && good >= 0 && good < this.coverage.length ? this.coverage[good] : 1.0;
    }

    public void clear() {
        Arrays.fill(this.price, 0.0);
        Arrays.fill(this.anchor, 0.0);
        Arrays.fill(this.coverage, 1.0);
        Arrays.fill(this.scarcity, 0.0);
        this.ready = false;
    }

    /** D-001: Package-private post-processing cap für einzelne Ressourcen.
     *  Wird von EconomySim.refreshFlowPrices() für essbare Ressourcen aufgerufen,
     *  unmittelbar nach refresh() — ready ist zu diesem Zeitpunkt garantiert true.
     *  Kein public API — nur EconomySim (gleiches Package) darf Preise kappen. */
    void enforceCap(int good, double max) {
        if (max > 0.0 && good >= 0 && good < this.price.length && this.price[good] > max) {
            this.price[good] = max;
        }
    }

    public static double targetStock(double demandPerDay, double targetCoverageDays) {
        if (!(demandPerDay > 0.0) || !(targetCoverageDays > 0.0)) {
            return 0.0;
        }
        return demandPerDay * targetCoverageDays;
    }

    /** D-004/BA-05: Bestand-basierte Coverage.
     *  Wenn supplyPerDay>0 ist, zählt der Bestand normal als Puffer gegen die Zuflussrate.
     *  BA-05: Wenn supplyPerDay=0 aber stock>0 (z.B. Mid-Game erste Nachfrage erscheint),
     *  muss der vorhandene Bestand als Coverage-Grundlage dienen, damit der Preis nicht
     *  auf 0× Anker fällt oder auf maximalen Scarcity-Spike schießt.
     *  Cold-Start-Guard: supplyPerDay=0 UND stock=0 → neutrale Coverage (0.4),
     *  "wissen wir noch nicht" statt "alles ist knapp". */
    public static double effectiveCoverage(double stock, double supplyPerDay, double demandPerDay, double targetCoverageDays, double flowLookaheadDays) {
        double target = FlowPrices.targetStock(demandPerDay, targetCoverageDays);
        if (!(target > 0.0) || !Double.isFinite(target)) {
            return 1.0;
        }
        // Cold-Start / Full-Depletion Guard: supply=0 UND stock=0.
        // Mid-Game-Breakdown (supply=0, stock>0) umgeht diesen Guard → BA-05 zählt stock.
        if (supplyPerDay <= 0.0 && stock <= 0.0) {
            return COLD_START_COVERAGE;
        }
        double beta = Math.max(0.0, flowLookaheadDays);
        double effectiveStock = Math.max(0.0, stock);
        double projected = effectiveStock + beta * (supplyPerDay - demandPerDay);
        if (!Double.isFinite(projected)) {
            projected = projected > 0.0 ? Double.MAX_VALUE : 0.0;
        }
        return Math.max(0.0, projected / target);
    }

    public static double scarcityMultiplier(double effectiveCoverage, double scarcityElasticityUp, double scarcityElasticityDown, double priceClampLo, double priceClampHi) {
        double coverage;
        if (!(priceClampLo > 0.0) || !(priceClampHi >= priceClampLo)) {
            throw new IllegalArgumentException("invalid positive price clamp");
        }
        double elasticity = (coverage = Math.max(0.0, effectiveCoverage)) < 1.0 ? Math.max(0.0, scarcityElasticityUp) : Math.max(0.0, scarcityElasticityDown);
        double raw = Math.pow(Math.max(COVERAGE_FLOOR, coverage), -elasticity);
        if (Double.isNaN(raw)) {
            raw = 1.0;
        } else if (Double.isInfinite(raw)) {
            raw = priceClampHi;
        }
        return Math.min(priceClampHi, Math.max(priceClampLo, raw));
    }

    public static double localPrice(double anchor, double effectiveCoverage, double scarcityElasticityUp, double scarcityElasticityDown, double priceClampLo, double priceClampHi, double priceAbsoluteMax) {
        if (!(anchor > 0.0) || !Double.isFinite(anchor)) {
            return 0.0;
        }
        double local = anchor * FlowPrices.scarcityMultiplier(effectiveCoverage, scarcityElasticityUp, scarcityElasticityDown, priceClampLo, priceClampHi);
        if (priceAbsoluteMax > 0.0 && local > priceAbsoluteMax) {
            return priceAbsoluteMax;
        }
        return local;
    }

    private void ensureCapacity(int size) {
        if (this.price.length == size) {
            return;
        }
        this.price = new double[size];
        this.anchor = new double[size];
        this.coverage = new double[size];
        this.scarcity = new double[size];
    }

    public static final class Parameters {
        private final double[] targetCoverageDays;
        private final double flowLookaheadDays;
        private final double scarcityElasticityUp;
        private final double scarcityElasticityDown;
        private final double priceClampLo;
        private final double priceClampHi;
        private final double priceAbsoluteMax;

        public Parameters(double[] targetCoverageDays, double flowLookaheadDays, double scarcityElasticityUp, double scarcityElasticityDown, double priceClampLo, double priceClampHi, double priceAbsoluteMax) {
            this.targetCoverageDays = (double[])targetCoverageDays.clone();
            this.flowLookaheadDays = flowLookaheadDays;
            this.scarcityElasticityUp = scarcityElasticityUp;
            this.scarcityElasticityDown = scarcityElasticityDown;
            this.priceClampLo = priceClampLo;
            this.priceClampHi = priceClampHi;
            this.priceAbsoluteMax = priceAbsoluteMax;
        }
    }
}

