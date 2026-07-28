package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.time.TIME;
import java.io.IOException;
import settlement.entity.humanoid.Humanoid;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class Liturgy implements Saveable {
    private int lastSeason = -1;
    private long lastLevied;
    private int lastNamed;

    public long lastLevied() {
        return this.lastLevied;
    }

    public int lastNamed() {
        return this.lastNamed;
    }

    public long update(Roster roster, Wallets wallets) {
        if (!EconConfig.liturgyEnabled) {
            return 0L;
        }
        int season = TIME.seasons().bitsSinceStart();
        if (this.lastSeason == -1) {
            this.lastSeason = season;
            return 0L;
        }
        if (season - this.lastSeason < Math.max(1, EconConfig.liturgyIntervalSeasons)) {
            return 0L;
        }
        this.lastSeason = season;
        this.lastLevied = 0L;
        this.lastNamed = 0;
        int headcount = Math.max(1, EconConfig.liturgyHeadcount);
        double rate = Math.max(0.0, Math.min(1.0, EconConfig.liturgyRate));
        if (rate <= 0.0) {
            return 0L;
        }
        int n = roster.size();
        Humanoid[] named = new Humanoid[Math.min(headcount, n)];
        boolean[] taken = new boolean[n];
        for (int slot = 0; slot < named.length; ++slot) {
            int best = -1;
            int bestWealth = -1;
            for (int i = 0; i < n; ++i) {
                int w;
                if (taken[i] || (w = wallets.spendable(roster.get(i))) <= bestWealth) continue;
                bestWealth = w;
                best = i;
            }
            if (best < 0 || bestWealth <= 0) break;
            taken[best] = true;
            named[slot] = roster.get(best);
        }
        long collected = 0L;
        for (Humanoid h : named) {
            int wealth;
            int due;
            if (h == null || (due = (int)Math.min((double)(wealth = wallets.spendable(h)), Math.floor((double)wealth * rate))) <= 0) continue;
            wallets.add(h, -due);
            wallets.accrueTax(h, due);
            collected += (long)due;
            ++this.lastNamed;
        }
        if (collected > 0L) {
            FACTIONS.player().credits().inc((double)collected, FCredits.CTYPE.TAX);
        }
        this.lastLevied = collected;
        return collected;
    }

    public void save(FilePutter file) {
        file.i(this.lastSeason);
    }

    public void load(FileGetter file) throws IOException {
        this.lastSeason = file.i();
    }

    public void clear() {
        this.lastSeason = -1;
        this.lastLevied = 0L;
        this.lastNamed = 0;
    }
}

