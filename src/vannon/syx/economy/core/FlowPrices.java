package vannon.syx.economy.core;

import java.util.Arrays;
import vannon.syx.economy.core.FlowMeter;

public final class FlowPrices {
    private static final double COVERAGE_FLOOR = 0.005;

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

    /** D-004: stock nur in Coverage zählen wenn tatsächlich Produktions-Zufluss existiert.
     *  Ohne diesen Guard signalisiert ein volles Lager (stock=414) bei supplyPerDay=0
     *  fälschlich Überfluss (coverage=8.7) → Preis kollabiert auf 0.05× Anker → Firmen
     *  finden kein Holz obwohl Lager voll ist (broken link: Lager-Tracking ≠ Firmen-Input).
     *  Mit Fix: supplyPerDay≤0 → effectiveStock=0 → coverage fällt → Preis steigt →
     *  Scarcity-Signal korrekt.
     *
     *  Livetest v0.13.56 Cold-Start-Guard: Bei Spielstart ohne Lager/Werkstatt ist
     *  supplyPerDay=0 UND stock=0 → effectiveStock=0 → projected negativ → coverage=0
     *  → maximaler Scarcity-Multiplier → Steinpreis schießt auf 7.1× Anker.
     *  Fix: Wenn supply=0 UND stock=0 (Cold-Start), neutrale Coverage (1.0) zurückgeben
     *  statt 0 — "wissen wir noch nicht" statt "alles ist knapp". */
    public static double effectiveCoverage(double stock, double supplyPerDay, double demandPerDay, double targetCoverageDays, double flowLookaheadDays) {
        double target = FlowPrices.targetStock(demandPerDay, targetCoverageDays);
        if (!(target > 0.0) || !Double.isFinite(target)) {
            return 1.0;
        }
        // Cold-Start / Full-Depletion Guard: supply=0 UND stock=0.
        // Mid-Game-Breakdown (supply=0, stock>0) umgeht diesen Guard → D-004 bleibt intakt.
        if (supplyPerDay <= 0.0 && stock <= 0.0) {
            return COLD_START_COVERAGE;
        }
        double beta = Math.max(0.0, flowLookaheadDays);
        double effectiveStock = supplyPerDay > 0.0 ? Math.max(0.0, stock) : 0.0;
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

