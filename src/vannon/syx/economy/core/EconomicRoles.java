package vannon.syx.economy.core;

import settlement.room.food.cannibal.ROOM_CANNIBAL;
import settlement.room.infra.embassy.ROOM_EMBASSY;
import settlement.room.infra.export.ROOM_EXPORT;
import settlement.room.infra.hauler.ROOM_HAULER;
import settlement.room.knowledge.laboratory.ROOM_LABORATORY;
import settlement.room.knowledge.library.ROOM_LIBRARY;
import settlement.room.law.guard.ROOM_GUARD;
import settlement.room.law.police.ROOM_POLICE;
import settlement.room.law.prison.ROOM_PRISON;
import settlement.room.law.stockade.ROOM_STOCKADE;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.military.supply.ROOM_SUPPLY;
import settlement.room.military.training.ROOM_M_TRAINER;
import settlement.room.service.hearth.ROOM_HEARTH;
import settlement.room.service.hygine.well.ROOM_WELL;
import settlement.room.spirit.temple.ROOM_TEMPLE;

final class EconomicRoles {
    static boolean stateFundedMilitary(RoomBlueprintImp blueprint) {
        return blueprint instanceof ROOM_M_TRAINER;
    }

    static boolean stateFundedExportDepot(RoomBlueprintImp b) {
        return b instanceof ROOM_EXPORT;
    }

    static boolean stateFundedHauler(RoomBlueprintImp b) {
        return b instanceof ROOM_HAULER;
    }

    static boolean stateFundedArmySupply(RoomBlueprintImp b) {
        return b instanceof ROOM_SUPPLY;
    }

    static boolean stateFundedLaboratory(RoomBlueprintImp b) {
        return b instanceof ROOM_LABORATORY;
    }

    static boolean stateFundedLibrary(RoomBlueprintImp b) {
        return b instanceof ROOM_LIBRARY;
    }

    static boolean stateFundedEmbassy(RoomBlueprintImp b) {
        return b instanceof ROOM_EMBASSY;
    }

    static boolean stateFundedWaterworks(RoomBlueprintImp b) {
        return "_WATERPUMP".equals(b.key);
    }

    static boolean stateFundedCannibal(RoomBlueprintImp b) {
        return b instanceof ROOM_CANNIBAL;
    }

    static boolean stateFundedPolice(RoomBlueprintImp b) {
        return b instanceof ROOM_POLICE;
    }

    static boolean stateFundedGuard(RoomBlueprintImp b) {
        return b instanceof ROOM_GUARD;
    }

    static boolean stateFundedStockade(RoomBlueprintImp b) {
        return b instanceof ROOM_STOCKADE;
    }

    static boolean stateFundedPrison(RoomBlueprintImp b) {
        return b instanceof ROOM_PRISON;
    }

    static boolean stateWageFunded(RoomBlueprintImp b) {
        return EconomicRoles.stateFundedMilitary(b) || EconomicRoles.stateFundedExportDepot(b) || EconomicRoles.stateFundedHauler(b) || EconomicRoles.stateFundedArmySupply(b) || EconomicRoles.stateFundedLaboratory(b) || EconomicRoles.stateFundedLibrary(b) || EconomicRoles.stateFundedEmbassy(b) || EconomicRoles.stateFundedWaterworks(b) || EconomicRoles.stateFundedCannibal(b) || EconomicRoles.stateFundedPolice(b) || EconomicRoles.stateFundedGuard(b) || EconomicRoles.stateFundedStockade(b) || EconomicRoles.stateFundedPrison(b);
    }

    static boolean excludedFromMarketSizing(RoomBlueprintImp b) {
        return EconomicRoles.stateWageFunded(b);
    }

    static boolean excludedFromMarketAccounting(RoomBlueprintImp b) {
        return EconomicRoles.stateFundedMilitary(b);
    }

    static boolean stateFundedPublicWorks(RoomBlueprintImp blueprint) {
        return blueprint instanceof ROOM_TEMPLE || blueprint instanceof ROOM_WELL || blueprint instanceof ROOM_HEARTH;
    }

    private EconomicRoles() {
    }
}

