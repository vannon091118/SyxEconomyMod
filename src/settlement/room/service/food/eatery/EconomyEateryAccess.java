package settlement.room.service.food.eatery;

import init.resources.RESOURCES;
import init.resources.ResGEat;
import settlement.room.main.RoomInstance;
import settlement.room.service.food.eatery.EateryInstance;
import settlement.room.service.food.eatery.ROOM_EATERY;

public final class EconomyEateryAccess {
    public static int[] stock(ROOM_EATERY room, int tx, int ty) {
        int[] result = new int[RESOURCES.EDI().all().size()];
        EateryInstance instance = room.getter.get(tx, ty);
        if (instance == null) {
            return result;
        }
        for (int i = 0; i < result.length; ++i) {
            ResGEat food = RESOURCES.EDI().all().get(i);
            long amount = room.dist.stored(food.resource).get((RoomInstance)instance);
            result[i] = EconomyEateryAccess.clamp(amount);
        }
        return result;
    }

    public static boolean consume(ROOM_EATERY room, int tx, int ty, int[] bundle) {
        if (!EconomyEateryAccess.valid(bundle)) {
            return false;
        }
        EateryInstance instance = room.getter.get(tx, ty);
        if (instance == null || !EconomyEateryAccess.covers(EconomyEateryAccess.stock(room, tx, ty), bundle)) {
            return false;
        }
        for (int i = 0; i < bundle.length; ++i) {
            int consumed;
            if (bundle[i] <= 0 || (consumed = room.dist.consume(RESOURCES.EDI().all().get(i).resource, bundle[i], tx, ty)) == bundle[i]) continue;
            throw new IllegalStateException("eatery stock changed during exact consumption");
        }
        return true;
    }

    private static boolean valid(int[] bundle) {
        if (bundle == null || bundle.length != RESOURCES.EDI().all().size()) {
            return false;
        }
        int total = 0;
        for (int amount : bundle) {
            if (amount < 0) {
                return false;
            }
            total += amount;
        }
        return total > 0;
    }

    private static boolean covers(int[] stock, int[] bundle) {
        for (int i = 0; i < bundle.length; ++i) {
            if (stock[i] >= bundle[i]) continue;
            return false;
        }
        return true;
    }

    private static int clamp(long value) {
        return value <= 0L ? 0 : (value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)value);
    }

    private EconomyEateryAccess() {
    }
}
