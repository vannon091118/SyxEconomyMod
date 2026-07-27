package vannon.syx.economy.core;

import game.time.TIME;
import init.type.HCLASSES;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.Induvidual;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class GrainDole implements Saveable {
    private final HashSet<Induvidual> roll = new HashSet<>();
    private int lastSeason = -1;
    private int lastRollSize = 0;
    private int mealsDoled = 0;
    private int compulsoryRations = 0;
    private long revenueForegone = 0L;

    public int rollSize() {
        return this.lastRollSize;
    }

    public int mealsDoled() {
        return this.mealsDoled;
    }

    public long revenueForegone() {
        return this.revenueForegone;
    }

    public int compulsoryRations() {
        return this.compulsoryRations;
    }

    public boolean isOnRoll(Induvidual indu) {
        return EconConfig.grainDoleEnabled && this.roll.contains(indu);
    }

    public void recordDoledMeal(int price) {
        ++this.mealsDoled;
        this.revenueForegone += (long)price;
    }

    public void recordRation(Humanoid diner, int price) {
        if (this.isOnRoll(diner.indu())) {
            ++this.mealsDoled;
        } else {
            ++this.compulsoryRations;
        }
        this.revenueForegone += (long)Math.max(0, price);
    }

    public void update(Roster roster, Wallets wallets) {
        if (!EconConfig.grainDoleEnabled) {
            if (!this.roll.isEmpty()) {
                this.roll.clear();
            }
            return;
        }
        int season = TIME.seasons().bitsSinceStart();
        if (season == this.lastSeason) {
            return;
        }
        this.lastSeason = season;
        this.draw(roster, wallets);
    }

    private void draw(Roster roster, Wallets wallets) {
        this.roll.clear();
        int cap = EconConfig.doleHeadcap;
        if (cap <= 0) {
            this.lastRollSize = 0;
            return;
        }
        int effectiveThreshold = effectiveDoleThreshold();
        int n = roster.size();
        ArrayList<Humanoid> eligible = new ArrayList<Humanoid>();
        for (int i = 0; i < n; ++i) {
            Humanoid h = roster.get(i);
            if (!EconConfig.grainDoleToSlaves && h.indu().clas() == HCLASSES.SLAVE() || wallets.netWorth(h) >= effectiveThreshold) continue;
            eligible.add(h);
        }
        eligible.sort((a, b) -> Integer.compare(wallets.netWorth((Humanoid)a), wallets.netWorth((Humanoid)b)));
        int take = Math.min(cap, eligible.size());
        for (int i = 0; i < take; ++i) {
            this.roll.add(((Humanoid)eligible.get(i)).indu());
        }
        this.lastRollSize = take;
    }

    public int eligibleCount(Roster roster, Wallets wallets) {
        int c = 0;
        int effectiveThreshold = effectiveDoleThreshold();
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid h = roster.get(i);
            if (!EconConfig.grainDoleToSlaves && h.indu().clas() == HCLASSES.SLAVE() || wallets.netWorth(h) >= effectiveThreshold) continue;
            ++c;
        }
        return c;
    }

    public void save(FilePutter file) {
        file.i(1);
        file.i(this.lastSeason);
        file.i(EconConfig.doleWealthThreshold);
        file.i(EconConfig.doleHeadcap);
    }

    public void load(FileGetter file) throws IOException {
        file.i();
        file.i();
        EconConfig.doleWealthThreshold = file.i();
        EconConfig.doleHeadcap = file.i();
        this.roll.clear();
        this.lastRollSize = 0;
        this.lastSeason = -1;
    }

    /** BA-04: Bootstrap-Dole-Schwellwert — 5000 während earlySettlerBuff, sonst doleWealthThreshold. */
    private static int effectiveDoleThreshold() {
        return (EconConfig.earlySettlerBuffEnabled
                && EconConfig.population < EconConfig.earlySettlerPopThreshold)
                ? EconConfig.earlySettlerDoleThreshold
                : EconConfig.doleWealthThreshold;
    }

    public void reset() {
        this.roll.clear();
        this.lastSeason = -1;
        this.lastRollSize = 0;
        this.mealsDoled = 0;
        this.compulsoryRations = 0;
        this.revenueForegone = 0L;
    }
}

