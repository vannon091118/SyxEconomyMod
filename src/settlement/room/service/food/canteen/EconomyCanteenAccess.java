package settlement.room.service.food.canteen;

import game.GAME;
import game.faction.FResources;
import init.resources.RESOURCES;
import init.resources.ResG;
import init.resources.ResGEat;
import settlement.room.main.RoomInstance;
import settlement.room.service.food.canteen.CanteenInstance;
import settlement.room.service.food.canteen.ROOM_CANTEEN;
import settlement.room.service.food.canteen.SService;

public final class EconomyCanteenAccess {
    public static int[] stock(ROOM_CANTEEN room, int tx, int ty) {
        int[] result = new int[RESOURCES.EDI().all().size()];
        CanteenInstance instance = room.getter.get(tx, ty);
        if (instance == null) {
            return result;
        }
        for (int i = 0; i < result.length; ++i) {
            result[i] = Math.max(0, instance.amount((ResG)RESOURCES.EDI().all().get(i)));
        }
        return result;
    }

    public static int[] stock(RoomInstance room) {
        int[] result = new int[RESOURCES.ALL().size()];
        if (!(room instanceof CanteenInstance)) {
            return result;
        }
        CanteenInstance instance = (CanteenInstance)room;
        for (ResGEat food : RESOURCES.EDI().all()) {
            result[food.resource.index()] = Math.max(0, instance.amount((ResG)food));
        }
        return result;
    }

    public static int servingLimit(ROOM_CANTEEN room, int tx, int ty) {
        CanteenInstance instance = room.getter.get(tx, ty);
        return instance == null ? 0 : Math.max(0, instance.amountTotal() - instance.serviceReserved() + 1);
    }

    public static boolean consume(ROOM_CANTEEN room, int tx, int ty, int[] bundle) {
        if (!EconomyCanteenAccess.valid(bundle)) {
            return false;
        }
        CanteenInstance instance = room.getter.get(tx, ty);
        SService service = room.food.get(tx, ty);
        if (instance == null || service == null || !service.findableReservedIs() || EconomyCanteenAccess.total(bundle) > EconomyCanteenAccess.servingLimit(room, tx, ty) || !EconomyCanteenAccess.covers(EconomyCanteenAccess.stock(room, tx, ty), bundle)) {
            return false;
        }
        service.consume();
        for (int i = 0; i < bundle.length; ++i) {
            int amount = bundle[i];
            if (amount <= 0) continue;
            ResGEat food = RESOURCES.EDI().all().get(i);
            instance.consume((ResG)food, amount, tx, ty);
            GAME.player().res().inc(food.resource, FResources.RTYPE.CONSUMED, -amount);
        }
        return true;
    }

    private static boolean valid(int[] bundle) {
        if (bundle == null || bundle.length != RESOURCES.EDI().all().size()) {
            return false;
        }
        for (int amount : bundle) {
            if (amount >= 0) continue;
            return false;
        }
        return EconomyCanteenAccess.total(bundle) > 0;
    }

    private static int total(int[] bundle) {
        int total = 0;
        for (int amount : bundle) {
            total += amount;
        }
        return total;
    }

    private static boolean covers(int[] stock, int[] bundle) {
        for (int i = 0; i < bundle.length; ++i) {
            if (stock[i] >= bundle[i]) continue;
            return false;
        }
        return true;
    }

    private EconomyCanteenAccess() {
    }
}

