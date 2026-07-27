package vannon.syx.economy.core;

import game.time.TIME;
import settlement.main.SETT;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.IHumanoidAccess;
import vannon.syx.economy.adapter.IRoomAccess;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EngineSeams;
import vannon.syx.economy.core.Roster;

public final class CorveeController {
    private int lastDraftedFirms;
    private double lastDraftFraction;

    public int lastDraftedFirms() {
        return this.lastDraftedFirms;
    }

    public double lastDraftFraction() {
        return this.lastDraftFraction;
    }

    public static int seasonsPerYear() {
        return Math.max(1, TIME.seasons().bitsPerCycle());
    }

    public static int daysPerSeason() {
        return Math.max(1, TIME.days().bitsPerCycle());
    }

    public static int calendarCells() {
        return CorveeController.seasonsPerYear() * CorveeController.daysPerSeason();
    }

    public static int currentCell() {
        int dps = CorveeController.daysPerSeason();
        int season = TIME.seasons().bitCurrent();
        int day = TIME.days().bitCurrent();
        return season * dps + day;
    }

    private static boolean isExemptFromCorvee(RoomBlueprintIns<?> blueprint) {
        if (blueprint == null || blueprint.key == null) {
            return false;
        }
        if (EconConfig.corveeExemptRoomKeys == null || EconConfig.corveeExemptRoomKeys.length == 0) {
            return false;
        }
        String key = blueprint.key.toUpperCase();
        for (String exempt : EconConfig.corveeExemptRoomKeys) {
            if (exempt != null && key.contains(exempt.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    public static void ensureSized() {
        int cells = CorveeController.calendarCells();
        if (EconConfig.corveeDays.length != cells) {
            boolean[] grown = new boolean[cells];
            System.arraycopy(EconConfig.corveeDays, 0, grown, 0, Math.min(EconConfig.corveeDays.length, cells));
            EconConfig.corveeDays = grown;
        }
    }

    public static boolean isCorveeToday() {
        if (!EconConfig.corveeEnabled) {
            return false;
        }
        CorveeController.ensureSized();
        int cell = CorveeController.currentCell();
        return cell >= 0 && cell < EconConfig.corveeDays.length && EconConfig.corveeDays[cell];
    }

    public void update(Roster roster) {
        double fraction;
        this.lastDraftedFirms = 0;
        this.lastDraftFraction = 0.0;
        if (!CorveeController.isCorveeToday() || SETT.ROOMS() == null) {
            return;
        }
        int employable = 0;
        for (int i = 0; i < roster.size(); ++i) {
            IHumanoidAccess hum = EngineMirror.api() != null ? EngineMirror.api().humanoids() : null;
            if (!(hum != null ? hum.isEmployableWorker(roster.get(i)) : EngineSeams.isEmployableWorker(roster.get(i)))) continue;
            ++employable;
        }
        if (employable <= 0) {
            return;
        }
        // Scale draft percent by settlement size: no drafting below threshold,
        // ramping linearly to the configured percent at/above the full-scale population.
        double draftScale = 1.0;
        int rampDistance = EconConfig.corveePopFullScale - EconConfig.corveePopThreshold;
        if (rampDistance > 0) {
            draftScale = (double)(employable - EconConfig.corveePopThreshold) / (double)rampDistance;
            draftScale = Math.max(0.0, Math.min(1.0, draftScale));
        } else if (employable < EconConfig.corveePopThreshold) {
            draftScale = 0.0;
        }
        double effectivePercent = (double)EconConfig.corveeDraftPercent * draftScale;
        int byPercent = (int)Math.floor((double)employable * Math.max(0.0, Math.min(100.0, effectivePercent)) / 100.0);
        int draft = Math.min(byPercent, Math.max(0, EconConfig.corveeDraftMax));
        if (draft <= 0) {
            return;
        }
        this.lastDraftFraction = fraction = Math.min(1.0, (double)draft / (double)employable);
        for (RoomBlueprintIns<?> blueprint : SETT.ROOMS().ins()) {
            // Skip essential rooms (food, water, wood, stone, etc.) from corvée drafting.
            if (CorveeController.isExemptFromCorvee(blueprint)) {
                continue;
            }
            for (int i = 0; i < blueprint.instancesSize(); ++i) {
                int employed;
                RoomInstance room = blueprint.getInstance(i);
                if (room == null || !room.exists() || room.employees() == null || room.employees().max() <= 0 || (employed = room.employees().employed()) <= 0) continue;
                int kept = (int)Math.round((double)employed * (1.0 - fraction));
                if (kept < 0) {
                    kept = 0;
                }
                IRoomAccess rm = EngineMirror.api() != null ? EngineMirror.api().rooms() : null;
                if (rm != null) rm.setFirmTarget(room, kept); else EngineSeams.setFirmTarget(room, kept);
                ++this.lastDraftedFirms;
            }
        }
    }
}

