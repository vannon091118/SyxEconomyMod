package vannon.syx.economy.core;

import game.GAME;
import game.faction.FResources;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.resources.ResG;
import init.resources.ResGEat;
import init.type.NEEDS;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.room.service.food.canteen.ROOM_CANTEEN;
import settlement.room.service.food.eatery.ROOM_EATERY;
import settlement.stats.STATS;
import vannon.syx.economy.core.FoodRollbackKernel;

public final class FoodRollback {
    public static StallSnapshot nearestStallSnapshot(Humanoid humanoid) {
        Candidate result;
        int[] stock;
        StallSnapshot nearest = null;
        long bestDistance = Long.MAX_VALUE;
        for (ROOM_EATERY eatery : EngineSeams.settRoomsEateries()) {
            result = FoodRollback.nearestInstance(humanoid, eatery, stock = FoodRollback.eateryStock(eatery), bestDistance);
            if (result == null) continue;
            nearest = result.snapshot;
            bestDistance = result.distanceSquared;
        }
        for (ROOM_CANTEEN canteen : EngineSeams.settRoomsCanteens()) {
            result = FoodRollback.nearestInstance(humanoid, canteen, stock = FoodRollback.canteenStock(canteen), bestDistance);
            if (result == null) continue;
            nearest = result.snapshot;
            bestDistance = result.distanceSquared;
        }
        return nearest;
    }

    public static int estimateUnitsEaten(Humanoid humanoid, int hungerBefore, int hungerAfter) {
        int delta = hungerBefore - hungerAfter;
        if (delta <= 0) {
            return 0;
        }
        int chunk = NEEDS.TYPES().HUNGER.stat().breakpoint();
        int events = Math.max(1, (delta + chunk - 1) / chunk);
        int decree = STATS.FOOD().FOOD.decree().get(humanoid);
        if (decree <= 0) {
            return 0;
        }
        long estimate = (long)events * (long)decree;
        return estimate > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)estimate;
    }

    public static int restore(Humanoid humanoid, StallSnapshot stall, int amount) {
        if (stall == null || amount <= 0) {
            return 0;
        }
        boolean[] preferred = new boolean[stall.stock.length];
        for (int i = 0; i < preferred.length; ++i) {
            preferred[i] = humanoid.race().pref().foodMask.has(((ResGEat)RESOURCES.EDI().all().get((int)i)).resource);
        }
        int[] allocation = FoodRollbackKernel.allocate(amount, stall.stock, preferred);
        int restored = 0;
        for (int i = 0; i < allocation.length; ++i) {
            int units = allocation[i];
            if (units <= 0) continue;
            RESOURCE resource = ((ResGEat)RESOURCES.EDI().all().get((int)i)).resource;
            SETT.THINGS().resources.create(stall.x, stall.y, resource, units);
            GAME.player().res().inc(resource, FResources.RTYPE.CONSUMED, units);
            restored += units;
        }
        return restored;
    }

    private static Candidate nearestInstance(Humanoid humanoid, RoomBlueprintIns<?> blueprint, int[] stock, long distanceLimit) {
        Candidate nearest = null;
        for (int i = 0; i < blueprint.instancesSize(); ++i) {
            long dy;
            RoomInstance instance = blueprint.getInstance(i);
            long dx = (long)instance.mX() - (long)humanoid.tc().x();
            long distance = dx * dx + (dy = (long)instance.mY() - (long)humanoid.tc().y()) * dy;
            if (distance >= distanceLimit) continue;
            distanceLimit = distance;
            nearest = new Candidate(distance, new StallSnapshot(instance.mX(), instance.mY(), (int[])stock.clone()));
        }
        return nearest;
    }

    private static int[] eateryStock(ROOM_EATERY eatery) {
        int[] result = new int[RESOURCES.EDI().all().size()];
        for (int i = 0; i < result.length; ++i) {
            ResG food = (ResG)RESOURCES.EDI().all().get(i);
            result[i] = FoodRollback.clampStock(eatery.amount(food));
        }
        return result;
    }

    private static int[] canteenStock(ROOM_CANTEEN canteen) {
        int[] result = new int[RESOURCES.EDI().all().size()];
        for (int i = 0; i < result.length; ++i) {
            ResG food = (ResG)RESOURCES.EDI().all().get(i);
            result[i] = FoodRollback.clampStock(canteen.amount(food));
        }
        return result;
    }

    private static int clampStock(long amount) {
        if (amount <= 0L) {
            return 0;
        }
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)amount;
    }

    private FoodRollback() {
    }

    private record Candidate(long distanceSquared, StallSnapshot snapshot) {
    }

    public record StallSnapshot(int x, int y, int[] stock) {
        public StallSnapshot {
            stock = stock.clone();
        }

        @Override
        public int[] stock() {
            return stock.clone();
        }
    }
}

