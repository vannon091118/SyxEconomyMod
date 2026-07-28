package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.time.TIME;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import java.io.IOException;
import java.util.Arrays;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.FirmLedger;
import vannon.syx.economy.core.FlowMeter;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class ProductionSubsidies implements Saveable {
    private int[] bounty = new int[0];
    private int reportSeason = -1;
    private long seasonDue;
    private long seasonPaid;
    private long seasonUnits;

    public int bounty(RESOURCE resource) {
        this.ensureCapacity();
        return resource == null ? 0 : this.bounty[resource.index()];
    }

    public void setBounty(RESOURCE resource, int denariPerUnit) {
        if (resource == null) {
            return;
        }
        this.ensureCapacity();
        this.bounty[resource.index()] = Math.max(0, Math.min(Math.max(0, EconConfig.productionSubsidyMax), denariPerUnit));
    }

    public long seasonDue() {
        return this.seasonDue;
    }

    public long seasonPaid() {
        return this.seasonPaid;
    }

    public long seasonUnits() {
        return this.seasonUnits;
    }

    public long update(FlowMeter meter, FirmLedger ledger, Roster roster, Wallets wallets) {
        this.rotateSeason();
        this.ensureCapacity();
        long budget = Math.max(0L, (long)Math.floor(FACTIONS.player().credits().credits()));
        long paid = 0L;
        for (FlowMeter.FirmSnapshot firm : meter.firmSnapshots()) {
            for (int output = 0; output < firm.outputCount(); ++output) {
                RESOURCE resource = firm.outputResource(output);
                int rate = this.bounty[resource.index()];
                int units = firm.producedSinceLastSample(output);
                if (rate <= 0 || units <= 0) continue;
                long due = ProductionSubsidies.due(units, rate);
                this.seasonDue = ProductionSubsidies.saturatingAdd(this.seasonDue, due);
                this.seasonUnits = ProductionSubsidies.saturatingAdd(this.seasonUnits, units);
                int payable = ProductionSubsidies.payable(units, rate, budget);
                if (payable <= 0) continue;
                int credited = ledger.distributeFirmRevenue(roster, wallets, firm.room(), payable);
                budget -= (long)credited;
                paid += (long)credited;
                this.seasonPaid = ProductionSubsidies.saturatingAdd(this.seasonPaid, credited);
            }
        }
        if (paid > 0L) {
            FACTIONS.player().credits().inc((double)(-paid), FCredits.CTYPE.MISC);
        }
        return paid;
    }

    public void save(FilePutter file) {
        this.ensureCapacity();
        file.i(1);
        int count = 0;
        for (int value : this.bounty) {
            if (value <= 0) continue;
            ++count;
        }
        file.i(count);
        for (int i = 0; i < this.bounty.length; ++i) {
            if (this.bounty[i] <= 0) continue;
            file.chars((CharSequence)((RESOURCE)RESOURCES.ALL().get((int)i)).key);
            file.i(this.bounty[i]);
        }
    }

    public void load(FileGetter file) throws IOException {
        int version = file.i();
        if (version != 1) {
            throw new IOException("unsupported production subsidy format " + version);
        }
        this.ensureCapacity();
        Arrays.fill(this.bounty, 0);
        int count = file.i();
        for (int i = 0; i < count; ++i) {
            String key = file.chars();
            int value = file.i();
            RESOURCE resource = ProductionSubsidies.resource(key);
            if (resource == null) continue;
            this.setBounty(resource, value);
        }
        this.clearReports();
    }

    public void clear() {
        this.ensureCapacity();
        Arrays.fill(this.bounty, 0);
        this.clearReports();
    }

    private void ensureCapacity() {
        int size = RESOURCES.ALL().size();
        if (this.bounty.length != size) {
            this.bounty = Arrays.copyOf(this.bounty, size);
        }
    }

    private void rotateSeason() {
        int season = TIME.seasons().bitsSinceStart();
        if (this.reportSeason == season) {
            return;
        }
        this.reportSeason = season;
        this.seasonUnits = 0L;
        this.seasonPaid = 0L;
        this.seasonDue = 0L;
    }

    private void clearReports() {
        this.reportSeason = -1;
        this.seasonUnits = 0L;
        this.seasonPaid = 0L;
        this.seasonDue = 0L;
    }

    private static RESOURCE resource(String key) {
        for (int i = 0; i < RESOURCES.ALL().size(); ++i) {
            RESOURCE candidate = (RESOURCE)RESOURCES.ALL().get(i);
            if (!candidate.key.equals(key)) continue;
            return candidate;
        }
        return null;
    }

    private static long saturatingMultiply(int left, int right) {
        long value = (long)left * (long)right;
        return value < 0L ? Long.MAX_VALUE : value;
    }

    static long due(int units, int denariPerUnit) {
        if (units <= 0 || denariPerUnit <= 0) {
            return 0L;
        }
        return ProductionSubsidies.saturatingMultiply(units, denariPerUnit);
    }

    static int payable(int units, int denariPerUnit, long treasuryBudget) {
        if (units <= 0 || denariPerUnit <= 0 || treasuryBudget < (long)denariPerUnit) {
            return 0;
        }
        long affordableUnits = treasuryBudget / (long)denariPerUnit;
        long integerSafeUnits = Integer.MAX_VALUE / denariPerUnit;
        long paidUnits = Math.min((long)units, Math.min(affordableUnits, integerSafeUnits));
        return (int)(paidUnits * (long)denariPerUnit);
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}

