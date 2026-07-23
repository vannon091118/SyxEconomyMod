package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.time.TIME;
import java.io.IOException;
import java.util.HashMap;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.Induvidual;
import snake2d.LOG;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EngineSeams;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class Taxes implements Saveable {
    public static final int DEFAULT_FLOOR = 3000;
    public static final int DEFAULT_RATE = 0;
    private int floor = 3000;
    private int rate = 0;
    public static final int METIC_MIN = -25;
    public static final int METIC_MAX = 25;
    private int foreignTaxModifier = 0;
    private int lastSeason = -1;
    private final HashMap<Induvidual, Integer> lastTax = new HashMap<>();
    private final HashMap<Induvidual, Integer> lastWealth = new HashMap<>();
    private long lastCollected = 0L;
    private int lastPayers = 0;

    public int floor() {
        return this.floor;
    }

    public int rate() {
        return this.rate;
    }

    public long lastCollected() {
        return this.lastCollected;
    }

    public int lastPayers() {
        return this.lastPayers;
    }

    public void setFloor(int v) {
        this.floor = Math.max(0, v);
    }

    public void setRate(int v) {
        this.rate = Math.max(0, Math.min(100, v));
    }

    public int taxPaidBy(Induvidual indu) {
        Integer v = this.lastTax.get(indu);
        return v == null ? 0 : v;
    }

    public int wealthWhenTaxed(Induvidual indu) {
        Integer v = this.lastWealth.get(indu);
        return v == null ? 0 : v;
    }

    public int foreignTaxModifier() {
        return this.foreignTaxModifier;
    }

    public void setForeignTaxModifier(int v) {
        if (v < -25) {
            v = -25;
        }
        if (v > 25) {
            v = 25;
        }
        this.foreignTaxModifier = v;
    }

    public static boolean isForeign(Induvidual indu) {
        return indu.race() != FACTIONS.player().race();
    }

    public static double immigrationMultiplier(int meticModifier) {
        double k = EconConfig.meticImmigrationDepth;
        double s = EconConfig.meticImmigrationSteepness;
        if (s <= 0.0) {
            return 1.0;
        }
        return 1.0 + k * Math.tanh((double)(-meticModifier) / s);
    }

    public double immigrationMultiplier() {
        return Taxes.immigrationMultiplier(this.foreignTaxModifier);
    }

    public int taxOn(int wealth) {
        return this.taxOn(wealth, false);
    }

    public int taxOn(int wealth, boolean foreign) {
        if (wealth <= this.floor) {
            return 0;
        }
        long tax = (long)(wealth - this.floor) * (long)this.rate / 100L;
        if (foreign && this.foreignTaxModifier != 0) {
            tax = tax * (long)(100 + this.foreignTaxModifier) / 100L;
        }
        if (tax > (long)wealth) {
            tax = wealth;
        }
        if (tax < 0L) {
            tax = 0L;
        }
        return (int)tax;
    }

    public long projectedRevenue(Roster roster, Wallets wallets) {
        long sum = 0L;
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid h = roster.get(i);
            sum += (long)this.taxOn(wallets.get(h), Taxes.isForeign(h.indu()));
        }
        return sum;
    }

    public long update(Roster roster, Wallets wallets) {
        if (!EconConfig.taxesEnabled) {
            return 0L;
        }
        int season = TIME.seasons().bitsSinceStart();
        if (this.lastSeason == -1) {
            this.lastSeason = season;
            return 0L;
        }
        if (season == this.lastSeason) {
            return 0L;
        }
        this.lastSeason = season;
        return this.collect(roster, wallets);
    }

    private long collect(Roster roster, Wallets wallets) {
        this.lastTax.clear();
        this.lastWealth.clear();
        long collected = 0L;
        int payers = 0;
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid h = roster.get(i);
            int wealth = wallets.get(h);
            int tax = this.taxOn(wealth, Taxes.isForeign(h.indu()));
            int paid = Math.min(tax, wallets.spendable(h));
            wallets.accrueTax(h, paid);
            int shortfall = tax - paid;
            if (shortfall > 0 && EngineSeams.isEnslaveablePleb(h)) {
                wallets.addDebt(h, shortfall);
            }
            if (paid <= 0) continue;
            wallets.add(h, -paid);
            collected += (long)paid;
            ++payers;
            this.lastTax.put(h.indu(), paid);
            this.lastWealth.put(h.indu(), wealth);
        }
        if (collected > 0L) {
            FACTIONS.player().credits().inc((double)collected, FCredits.CTYPE.TAX);
            LOG.ln("[ECON] taxes: collected=" + collected + " from " + payers + " payers | " + this.rate + "% above floor " + this.floor + " | treasury=" + (int)FACTIONS.player().credits().credits());
        }
        this.lastCollected = collected;
        this.lastPayers = payers;
        return collected;
    }

    public void save(FilePutter file) {
        file.i(3);
        file.i(this.lastSeason);
        file.i(this.floor);
        file.i(this.rate);
        file.i(this.foreignTaxModifier);
    }

    public void load(FileGetter file) throws IOException {
        file.i();
        this.lastSeason = file.i();
        this.floor = file.i();
        this.rate = file.i();
        this.foreignTaxModifier = file.i();
        this.lastTax.clear();
        this.lastWealth.clear();
    }

    public void reset() {
        this.floor = 3000;
        this.rate = 0;
        this.foreignTaxModifier = 0;
        this.lastSeason = -1;
        this.lastTax.clear();
        this.lastWealth.clear();
        this.lastCollected = 0L;
        this.lastPayers = 0;
    }
}

