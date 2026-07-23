package vannon.syx.economy.core;

import settlement.main.SETT;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import vannon.syx.economy.core.EconConfig;

public final class WorkplaceDefaults {
    public void update() {
        if (SETT.ROOMS() == null) {
            return;
        }
        int minimum = Math.max(0, EconConfig.minimumWorkersPerWorkplace);
        if (minimum == 0) {
            return;
        }
        for (RoomBlueprintIns<?> blueprint : SETT.ROOMS().ins()) {
            for (int i = 0; i < blueprint.instancesSize(); ++i) {
                int target;
                RoomInstance room = blueprint.getInstance(i);
                if (room.employees() == null || (target = WorkplaceDefaults.target(room.employees().hardTarget(), room.employees().max(), minimum)) == room.employees().hardTarget()) continue;
                room.employees().neededSet(target);
            }
        }
    }

    static int target(int current, int maximum, int minimum) {
        if (maximum <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(maximum, Math.max(current, minimum)));
    }
}

