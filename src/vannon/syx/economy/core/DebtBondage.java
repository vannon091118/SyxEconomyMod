package vannon.syx.economy.core;

import game.time.TIME;
import java.io.IOException;
import settlement.entity.humanoid.Humanoid;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.IHumanoidAccess;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class DebtBondage implements Saveable {
    private int lastSeason = -1;
    private int lastEnslaved;
    private long lastOutstanding;
    private int debtorCount;
    private long totalEnslaved;

    public int lastEnslaved() {
        return this.lastEnslaved;
    }

    public long lastOutstanding() {
        return this.lastOutstanding;
    }

    public int debtorCount() {
        return this.debtorCount;
    }

    public long totalEnslaved() {
        return this.totalEnslaved;
    }

    public void update(Roster roster, Wallets wallets) {
        boolean enslaveNow;
        int season = TIME.seasons().bitsSinceStart();
        boolean seasonTurned = season != this.lastSeason && this.lastSeason != -1;
        this.lastSeason = season;
        boolean bl = enslaveNow = EconConfig.debtSlaveryEnabled && seasonTurned;
        if (enslaveNow) {
            this.lastEnslaved = 0;
        }
        long outstanding = 0L;
        int debtors = 0;
        int threshold = Math.max(1, EconConfig.debtSlaveThreshold);
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid h = roster.get(i);
            int debt = wallets.debt(h);
            if (debt <= 0) continue;
            outstanding += (long)debt;
            ++debtors;
            IHumanoidAccess hum = EngineMirror.api().humanoids();
            if (!enslaveNow || debt < threshold || !hum.isEnslaveablePleb(h)) continue;
            hum.enslave(h);
            EventLog.log("DEBT", h.title() + " enslaved due to debt of " + debt + " denari!");
            wallets.clearDebt(h);
            ++this.lastEnslaved;
            ++this.totalEnslaved;
        }
        this.lastOutstanding = outstanding;
        this.debtorCount = debtors;
    }

    public void save(FilePutter file) {
        file.i(this.lastSeason);
        file.l(this.totalEnslaved);
    }

    public void load(FileGetter file) throws IOException {
        this.lastSeason = file.i();
        this.totalEnslaved = file.l();
    }

    public void clear() {
        this.lastSeason = -1;
        this.lastEnslaved = 0;
        this.lastOutstanding = 0L;
        this.debtorCount = 0;
        this.totalEnslaved = 0L;
    }
}

