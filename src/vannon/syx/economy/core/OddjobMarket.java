package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.time.TIME;
import java.util.HashMap;
import java.util.HashSet;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.Induvidual;
import vannon.syx.economy.core.CorveeController;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EngineSeams;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class OddjobMarket {
    private final HashMap<Induvidual, Double> accruedSeconds = new HashMap<>();
    private int currentSeason = -1;
    private long currentSeasonPaid;
    private int currentSeasonTasks;
    private long lastPaid;
    private int lastTasks;
    private int activeWorkersNow;
    private double lastGameSecond = Double.NaN;
    private int cycleProgressPercent;
    private boolean treasuryBlocked;

    public long currentPaid() {
        return this.currentSeasonPaid;
    }

    public int currentTasks() {
        return this.currentSeasonTasks;
    }

    public long lastPaid() {
        return this.lastPaid;
    }

    public int lastTasks() {
        return this.lastTasks;
    }

    public int activeWorkersNow() {
        return this.activeWorkersNow;
    }

    public int cycleProgressPercent() {
        return this.cycleProgressPercent;
    }

    public boolean treasuryBlocked() {
        return this.treasuryBlocked;
    }

    public long update(Roster roster, Wallets wallets) {
        this.rotateSeason();
        boolean payWork = EconConfig.oddjobWageEnabled && !CorveeController.isCorveeToday();
        double now = EngineSeams.gameSecondsSinceStart();
        double elapsed = OddjobMarket.clockDelta(this.lastGameSecond, now);
        this.lastGameSecond = now;
        double cycle = EngineSeams.workCycleSeconds();
        // Phase 5e: cap oddjob-wage at defaultWage * oddjobWageCeilingRatio (default 0.75)
        // so Tagelöhner nie reguläre Arbeiter-Löhne überbieten. Migration zu XP-Berufen (Phase 5a)
        // funktioniert nur wenn Oddjob strukturell unattraktiv bleibt.
        int wage = this.effectiveWage();
        long budget = Math.max(0L, (long)Math.floor(FACTIONS.player().credits().credits()));
        long paid = 0L;
        int tasks = 0;
        this.activeWorkersNow = 0;
        this.treasuryBlocked = false;
        for (int i = 0; i < roster.size(); ++i) {
            boolean working;
            Humanoid h = roster.get(i);
            Induvidual indu = h.indu();
            if (!EngineSeams.isEmployableWorker(h)) {
                this.accruedSeconds.remove(indu);
                continue;
            }
            boolean oddjob = EngineSeams.isSurplusLaborer(h) || EconomySim.active().aiAdapter().isOddjobbing(h);
            boolean bl = working = oddjob && EngineSeams.isWorking(h);
            if (working) {
                ++this.activeWorkersNow;
            }
            if (!working || !payWork || elapsed <= 0.0) continue;
            Progress progress = OddjobMarket.progress(this.accruedSeconds.getOrDefault(indu, 0.0), elapsed, cycle);
            int unpaidTasks = 0;
            for (int completed = 0; completed < progress.tasks(); ++completed) {
                if (wage <= 0) continue;
                if (budget < (long)wage) {
                    ++unpaidTasks;
                    this.treasuryBlocked = true;
                    continue;
                }
                wallets.add(h, wage);
                budget -= (long)wage;
                paid += (long)wage;
                ++tasks;
            }
            double carried = progress.remainderSeconds() + (double)unpaidTasks * cycle;
            if (carried > 0.0) {
                this.accruedSeconds.put(indu, carried);
                continue;
            }
            this.accruedSeconds.remove(indu);
        }
        this.cycleProgressPercent = OddjobMarket.progressPercent(this.accruedSeconds, cycle);
        if (this.accruedSeconds.size() > roster.size() * 2 + 64) {
            this.prune(roster);
        }
        if (paid > 0L) {
            FACTIONS.player().credits().inc((double)(-paid), FCredits.CTYPE.MISC);
        }
        this.currentSeasonPaid += paid;
        this.currentSeasonTasks += tasks;
        return paid;
    }

    static double clockDelta(double previous, double current) {
        if (!Double.isFinite(previous) || !Double.isFinite(current) || current <= previous) {
            return 0.0;
        }
        return current - previous;
    }

    private static int progressPercent(HashMap<Induvidual, Double> accrued, double cycle) {
        if (!(cycle > 0.0) || accrued.isEmpty()) {
            return 0;
        }
        double maximum = 0.0;
        for (double seconds : accrued.values()) {
            if (!Double.isFinite(seconds)) continue;
            maximum = Math.max(maximum, seconds);
        }
        return (int)Math.max(0.0, Math.min(100.0, Math.floor(maximum * 100.0 / cycle)));
    }

    static Progress progress(double accrued, double elapsed, double cycle) {
        double safeAccrued = Double.isFinite(accrued) ? Math.max(0.0, accrued) : 0.0;
        double safeElapsed = Double.isFinite(elapsed) ? Math.max(0.0, elapsed) : 0.0;
        double safeCycle = Double.isFinite(cycle) ? Math.max(1.0, cycle) : 1.0;
        double total = safeAccrued + safeElapsed;
        long completed = (long)Math.floor(total / safeCycle);
        int tasks = (int)Math.min(Integer.MAX_VALUE, completed);
        double remainder = total - (double)tasks * safeCycle;
        return new Progress(tasks, Math.max(0.0, remainder));
    }

    private void rotateSeason() {
        int season = TIME.seasons().bitsSinceStart();
        if (this.currentSeason == -1) {
            this.currentSeason = season;
            return;
        }
        if (season == this.currentSeason) {
            return;
        }
        this.lastPaid = this.currentSeasonPaid;
        this.lastTasks = this.currentSeasonTasks;
        this.currentSeasonPaid = 0L;
        this.currentSeasonTasks = 0;
        this.currentSeason = season;
    }

    private void prune(Roster roster) {
        HashSet<Induvidual> living = new HashSet<>();
        for (int i = 0; i < roster.size(); ++i) {
            living.add(roster.get(i).indu());
        }
        this.accruedSeconds.keySet().retainAll(living);
    }

    public void clear() {
        this.accruedSeconds.clear();
        this.currentSeason = -1;
        this.currentSeasonPaid = 0L;
        this.currentSeasonTasks = 0;
        this.lastPaid = 0L;
        this.lastTasks = 0;
        this.activeWorkersNow = 0;
        this.lastGameSecond = Double.NaN;
        this.cycleProgressPercent = 0;
        this.treasuryBlocked = false;
    }

    record Progress(int tasks, double remainderSeconds) {
    }

    // Thin wrappers for Advisor-Tab slider controls
    public int pay() { return effectiveWage();
    }
    /** Phase 5e: set oddjob wage through the hard-cap choke-point. */
    public void setPay(int wage) {
        EconConfig.setOddjobWage(wage);
    }

    /** Phase 5e: effective oddjob wage, hard-clamped at the ceiling-ratio × defaultWage. */
    public int effectiveWage() {
        return Math.max(0, Math.min(EconConfig.oddjobWagePerTask,
                (int)(EconConfig.defaultWage * EconConfig.oddjobWageCeilingRatio)));
    }

    /**
     * Phase 5e: original uncapped {@code oddjobWagePerTask} value before the
     * {@link EconConfig#oddjobWageCeilingRatio} ceiling is applied. Surfaced so
     * the Phase-6 EconomyWindow "Workforce"-tab can render both numbers
     * side-by-side (configured vs. effective). Marked as a Phase-6 consumer
     * hook: if Phase 6 EconomyWindow is cut, this accessor should be removed.
     */
    public int uncappedWage() {
        return EconConfig.oddjobWagePerTask;
    }
}

