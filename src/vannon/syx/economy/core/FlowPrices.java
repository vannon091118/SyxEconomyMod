package vannon.syx.economy.core;

import java.util.Arrays;
import vannon.syx.economy.core.FlowMeter;

public final class FlowPrices {
    private static final double COVERAGE_FLOOR = 0.005;

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

    public static double targetStock(double demandPerDay, double targetCoverageDays) {
        if (!(demandPerDay > 0.0) || !(targetCoverageDays > 0.0)) {
            return 0.0;
        }
        return demandPerDay * targetCoverageDays;
    }

    public static double effectiveCoverage(double stock, double supplyPerDay, double demandPerDay, double targetCoverageDays, double flowLookaheadDays) {
        double target = FlowPrices.targetStock(demandPerDay, targetCoverageDays);
        if (!(target > 0.0) || !Double.isFinite(target)) {
            return 1.0;
        }
        double beta = Math.max(0.0, flowLookaheadDays);
        double projected = Math.max(0.0, stock) + beta * (supplyPerDay - demandPerDay);
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

