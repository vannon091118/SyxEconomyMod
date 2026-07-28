package vannon.syx.economy.core;

import init.type.HCLASSES;
import init.type.WGROUP;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import settlement.main.SETT;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.employment.RoomEmployment;
import game.time.TIME;
import settlement.room.main.employment.LaborMarketAccess;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.sets.LIST;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.FirmEconomyKernel;
import vannon.syx.economy.core.FirmLedger;

public final class LaborMarket implements Saveable {
    private final HashMap<String, Integer> baseline = new HashMap<>();
    private final HashMap<String, Integer> written = new HashMap<>();
    private final HashMap<String, int[]> blueprintOutputs = new HashMap<>();
    private int lastApplyTick = -9999;
    private double meanWage = 0.0;
    private ScarcitySignal scarcitySignal = null;

    public double meanWage() {
        return this.meanWage;
    }

    /** Set the current scarcity signal for priority boosting. */
        public void setScarcitySignal(ScarcitySignal signal) {
            this.scarcitySignal = signal;
        }

        /** Build a mapping from room blueprint key to the resource indices it produces. */
        public void refreshBlueprintOutputs(FlowMeter flowMeter) {
            this.blueprintOutputs.clear();
            for (FlowMeter.FirmSnapshot firm : flowMeter.firmSnapshots()) {
                if (firm.room() == null || firm.room().blueprintI() == null) continue;
                String key = firm.room().blueprintI().key;
                int[] outputs = this.blueprintOutputs.get(key);
                if (outputs == null) {
                    outputs = new int[firm.outputCount()];
                    for (int i = 0; i < outputs.length; ++i) {
                        outputs[i] = firm.outputResource(i).index();
                    }
                    this.blueprintOutputs.put(key, outputs);
                }
            }
        }

    public int derivedPriority(RoomBlueprintImp b) {
        Integer p = b == null ? null : this.written.get(b.key);
        return p == null ? -1 : p;
    }

    /** v1.7.2 Ticket 1: Average scarcity signal for a blueprint's output resources.
     *  Returns 0.0 if no outputs or no scarcity data. */
    public double scarcityForBlueprint(RoomBlueprintImp b) {
        if (this.scarcitySignal == null || b == null) return 0.0;
        int[] outputs = this.blueprintOutputs.get(b.key);
        if (outputs == null || outputs.length == 0) return 0.0;
        double sum = 0.0;
        for (int good : outputs) sum += this.scarcitySignal.get(good);
        return sum / (double) outputs.length;
    }

        public static int profitPriority(double marginal, double mean, int min, int max) {
            return FirmEconomyKernel.priority(marginal, mean, EconConfig.laborNeutralPriority, EconConfig.profitElasticity, min, max);
        }

        public static int blend(int base, int wagePrio, double freeShare, int min, int max) {
            int p;
            if (freeShare < 0.0) {
                freeShare = 0.0;
            }
            if (freeShare > 1.0) {
                freeShare = 1.0;
            }
            if ((p = (int)Math.round((double)base + freeShare * (double)(wagePrio - base))) < min) {
                p = min;
            }
            if (p > max) {
                p = max;
            }
            return p;
        }

        public void update(FirmLedger ledger, int ticks) {
            if (SETT.ROOMS() == null) {
                return;
            }
            int threshold = Math.max(1, (int)(EconConfig.laborRefreshDays * TIME.secondsPerDay()));
        if (Math.abs(ticks - this.lastApplyTick) < threshold) {
                return;
            }
            this.lastApplyTick = ticks;
            LIST<?> all = SETT.ROOMS().imps();
            @SuppressWarnings("unchecked")
            LIST<RoomBlueprintImp> typed = (LIST<RoomBlueprintImp>) (LIST<?>) all;
            if (!EconConfig.firmLedgerEnabled || !EconConfig.laborMarketEnabled) {
                this.restore(typed);
                return;
            }
            this.meanWage = ledger.meanPositiveMarginal();
            if (!(this.meanWage > 0.0)) {
                this.restore(typed);
                return;
            }
            for (int i = 0; i < all.size(); ++i) {
                RoomBlueprintImp b = (RoomBlueprintImp)all.get(i);
                RoomEmployment e = LaborMarketAccess.employmentOf(b);
                if (e == null) continue;
                int current = e.priority.get();
                Integer ours = this.written.get(b.key);
                boolean playerIntervened = ours == null || ours != current;
                if (playerIntervened) {
                    this.baseline.put(b.key, current);
                }
                int base = this.baseline.getOrDefault(b.key, current);
                double marginal = ledger.marginalSurplus(b);
                // Scarcity boost: if this blueprint produces a scarce resource,
                // increase its effective marginal so workers are pulled toward it.
                if (this.scarcitySignal != null) {
                    int[] outputs = this.blueprintOutputs.get(b.key);
                    if (outputs != null) {
                        double maxScarcity = 0.0;
                        for (int outIdx : outputs) {
                            double s = this.scarcitySignal.get(outIdx);
                            if (s > maxScarcity) maxScarcity = s;
                        }
                        if (maxScarcity > 0.0) {
                            marginal *= (1.0 + maxScarcity * EconConfig.scarcityLaborBoost);
                        }
                    }
                }
                int market = LaborMarket.profitPriority(marginal, this.meanWage, e.priority.min(), e.priority.max());
                int priority = LaborMarket.blend(base, market, LaborMarketAccess.freeShare(b), e.priority.min(), e.priority.max());
                if (!playerIntervened && Math.abs(priority - current) <= EconConfig.laborFrictionPoints) {
                    priority = current;
                }
                if (priority != current) {
                    e.priority.set(priority);
                }
                this.written.put(b.key, e.priority.get());
            }
        }

        private void restore(LIST<RoomBlueprintImp> all) {
            if (this.written.isEmpty()) {
                return;
            }
            for (int i = 0; i < all.size(); ++i) {
                RoomBlueprintImp b = (RoomBlueprintImp)all.get(i);
                Integer base = this.baseline.get(b.key);
                if (base == null) continue;
                RoomEmployment re = LaborMarketAccess.employmentOf(b);
                if (re != null) re.priority.set(base.intValue());
            }
            this.written.clear();
            this.meanWage = 0.0;
        }

    public void save(FilePutter file) {
        file.i(1);
        file.i(this.baseline.size());
        for (Map.Entry<String, Integer> e : this.baseline.entrySet()) {
            file.chars((CharSequence)e.getKey());
            file.i(e.getValue().intValue());
        }
        file.i(this.written.size());
        for (Map.Entry<String, Integer> e : this.written.entrySet()) {
            file.chars((CharSequence)e.getKey());
            file.i(e.getValue().intValue());
        }
    }

    public void load(FileGetter file) throws IOException {
        file.i();
        this.baseline.clear();
        int bm = file.i();
        for (int i = 0; i < bm; ++i) {
            String key = file.chars();
            this.baseline.put(key, file.i());
        }
        this.written.clear();
        int wm = file.i();
        for (int i = 0; i < wm; ++i) {
            String key = file.chars();
            this.written.put(key, file.i());
        }
        this.lastApplyTick = -9999;
    }

    public void clear() {
        this.baseline.clear();
        this.written.clear();
        this.blueprintOutputs.clear();
        this.lastApplyTick = -9999;
        this.meanWage = 0.0;
        this.scarcitySignal = null;
    }
}

