package vannon.syx.economy.core;

import java.io.IOException;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import vannon.syx.economy.core.EconConfig;

public final class MilitaryPayroll implements Saveable {
    private int lastSeason = -1;

    public int wage() {
        return MilitaryPayroll.clampWage(EconConfig.militaryTrainingWagePerDay);
    }

    public void setWage(int wage) {
        EconConfig.militaryTrainingWagePerDay = MilitaryPayroll.clampWage(wage);
    }

    public void save(FilePutter file) {
        file.i(2);
        file.i(this.lastSeason);
        file.i(this.wage());
    }

    public void load(FileGetter file) throws IOException {
        int version = file.i();
        if (version < 1 || version > 2) {
            throw new IOException("unsupported military payroll format " + version);
        }
        this.lastSeason = file.i();
        this.setWage(version >= 2 ? file.i() : 150);
    }

    public void clear() {
        this.lastSeason = -1;
    }

    static int clampWage(int wage) {
        int maximum = Math.max(0, EconConfig.wageMax);
        int step = Math.max(1, EconConfig.wageStep);
        int clamped = Math.max(0, Math.min(maximum, wage));
        return clamped / step * step;
    }
}

