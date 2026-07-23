package settlement.room.service.food.tavern;

import init.resources.RESOURCES;
import init.resources.ResGDrink;
import settlement.room.main.RoomInstance;
import settlement.room.service.food.tavern.ROOM_TAVERN;
import settlement.room.service.food.tavern.TavernInstance;

public final class EconomyTavernAccess {
    public static int[] stock(ROOM_TAVERN room, int tx, int ty) {
        int[] result = new int[RESOURCES.DRINKS().all().size()];
        TavernInstance instance = room.getter.get(tx, ty);
        if (instance == null) {
            return result;
        }
        for (int i = 0; i < result.length; ++i) {
            ResGDrink drink = RESOURCES.DRINKS().all().get(i);
            long amount = room.dist.stored(drink.resource).get((RoomInstance)instance);
            result[i] = EconomyTavernAccess.clamp(amount);
        }
        return result;
    }

    public static long totalStock(ROOM_TAVERN room) {
        long total = 0L;
        for (int i = 0; i < room.instancesSize(); ++i) {
            TavernInstance instance = room.getInstance(i);
            if (instance == null) continue;
            for (ResGDrink drink : RESOURCES.DRINKS().all()) {
                total += (long)Math.max(0, room.dist.stored(drink.resource).get((RoomInstance)instance));
            }
        }
        return total;
    }

    public static boolean consume(ROOM_TAVERN room, int tx, int ty, int[] bundle) {
        if (!EconomyTavernAccess.valid(bundle)) {
            return false;
        }
        TavernInstance instance = room.getter.get(tx, ty);
        if (instance == null || !EconomyTavernAccess.covers(EconomyTavernAccess.stock(room, tx, ty), bundle)) {
            return false;
        }
        for (int i = 0; i < bundle.length; ++i) {
            int consumed;
            if (bundle[i] <= 0 || (consumed = room.dist.consume(RESOURCES.DRINKS().all().get(i).resource, bundle[i], tx, ty)) == bundle[i]) continue;
            throw new IllegalStateException("tavern stock changed during exact consumption");
        }
        return true;
    }

    private static boolean valid(int[] bundle) {
        if (bundle == null || bundle.length != RESOURCES.DRINKS().all().size()) {
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

    private EconomyTavernAccess() {
    }
}
