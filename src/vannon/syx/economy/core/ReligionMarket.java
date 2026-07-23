package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.time.TIME;
import init.type.HCLASSES;
import init.type.HTYPES;
import java.io.IOException;
import settlement.entity.humanoid.Humanoid;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.rnd.RND;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EngineSeams;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class ReligionMarket implements Saveable {
    private int lastSeason = -1;
    private long lastCollected;
    private int lastConversions;
    private int lastDebtors;

    public long lastCollected() {
        return this.lastCollected;
    }

    public int lastConversions() {
        return this.lastConversions;
    }

    public int lastDebtors() {
        return this.lastDebtors;
    }

    public static void ensureSized() {
        int n = EngineSeams.religionCount();
        if (EconConfig.religionHeadTax.length != n) {
            int[] grown = new int[n];
            int oldLen = EconConfig.religionHeadTax.length;
            System.arraycopy(EconConfig.religionHeadTax, 0, grown, 0, Math.min(oldLen, n));
            // Newly discovered religions get a conservative default head tax.
            for (int i = oldLen; i < n; ++i) {
                grown[i] = EconConfig.religionHeadTaxDefault;
            }
            EconConfig.religionHeadTax = grown;
        }
    }

    private static int rateOf(int religionIndex) {
        int[] rates = EconConfig.religionHeadTax;
        return religionIndex >= 0 && religionIndex < rates.length ? Math.max(0, rates[religionIndex]) : 0;
    }

    private static int cheapestReligion() {
        int[] rates = EconConfig.religionHeadTax;
        int best = -1;
        int bestRate = Integer.MAX_VALUE;
        for (int i = 0; i < rates.length; ++i) {
            int r = Math.max(0, rates[i]);
            if (r >= bestRate) continue;
            bestRate = r;
            best = i;
        }
        return best;
    }

    public long update(Roster roster, Wallets wallets) {
        if (!EconConfig.religionTaxEnabled) {
            return 0L;
        }
        ReligionMarket.ensureSized();
        int season = TIME.seasons().bitsSinceStart();
        if (this.lastSeason == -1) {
            this.lastSeason = season;
            return 0L;
        }
        if (season == this.lastSeason) {
            return 0L;
        }
        this.lastSeason = season;
        this.lastCollected = 0L;
        this.lastConversions = 0;
        this.lastDebtors = 0;
        int cheapest = ReligionMarket.cheapestReligion();
        int cheapestRate = cheapest < 0 ? 0 : ReligionMarket.rateOf(cheapest);
        long collected = 0L;
        for (int i = 0; i < roster.size(); ++i) {
            boolean canFlee;
            int religion;
            int rate;
            Humanoid h = roster.get(i);
            if (h.indu().hType() == HTYPES.CHILD() || h.indu().hType() == HTYPES.CHILD_SLAVE() || h.indu().clas() == HCLASSES.SLAVE() || (rate = ReligionMarket.rateOf(religion = EngineSeams.religionIndexOf(h))) <= 0) continue;
            int spendable = wallets.spendable(h);
            if (spendable >= rate) {
                wallets.add(h, -rate);
                wallets.accrueTax(h, rate);
                collected += (long)rate;
                continue;
            }
            boolean bl = canFlee = cheapest >= 0 && religion != cheapest && cheapestRate < rate;
            if (canFlee && (double)RND.rFloat() < 0.5) {
                EngineSeams.convertTo(h, cheapest);
                ++this.lastConversions;
                continue;
            }
            if (!EngineSeams.isEnslaveablePleb(h)) continue;
            wallets.addDebt(h, rate);
            ++this.lastDebtors;
        }
        if (collected > 0L) {
            FACTIONS.player().credits().inc((double)collected, FCredits.CTYPE.TAX);
        }
        this.lastCollected = collected;
        return collected;
    }

    public void save(FilePutter file) {
        file.i(this.lastSeason);
        file.i(EconConfig.religionHeadTax.length);
        for (int rate : EconConfig.religionHeadTax) {
            file.i(rate);
        }
    }

    public void load(FileGetter file) throws IOException {
        this.lastSeason = file.i();
        int n = file.i();
        int[] rates = new int[Math.max(0, n)];
        for (int i = 0; i < n; ++i) {
            rates[i] = file.i();
        }
        EconConfig.religionHeadTax = rates;
        ReligionMarket.ensureSized();
    }

    public void clear() {
        this.lastSeason = -1;
        this.lastCollected = 0L;
        this.lastConversions = 0;
        this.lastDebtors = 0;
    }
}

