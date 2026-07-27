package vannon.syx.economy.core;

import game.time.TIME;
import init.type.NEED_E;
import java.util.Arrays;
import settlement.entity.humanoid.Humanoid;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.service.hearth.ROOM_HEARTH;
import settlement.room.service.hygine.well.ROOM_WELL;
import settlement.room.service.module.RoomServiceAccess;
import settlement.stats.service.StatService;
import snake2d.util.sets.LIST;
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.IHumanoidAccess;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EngineSeams;
import vannon.syx.economy.core.FirmLedger;
import vannon.syx.economy.core.Fiscal;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class ServiceMarket {
    private double[] prices = new double[0];
    private int lastRefreshDay = Integer.MIN_VALUE;
    private long lastGuildPaid;
    private int admitted;
    private int denied;

    public long lastGuildPaid() {
        return this.lastGuildPaid;
    }

    public int admitted() {
        return this.admitted;
    }

    public int denied() {
        return this.denied;
    }

    public void refresh() {
        int day;
        if (!EconConfig.serviceMarketEnabled) {
            return;
        }
        LIST all = RoomServiceAccess.ALL();
        if (this.prices.length != all.size()) {
            double[] next = new double[all.size()];
            Arrays.fill(next, (double)EconConfig.serviceBasePrice);
            System.arraycopy(this.prices, 0, next, 0, Math.min(this.prices.length, next.length));
            this.prices = next;
        }
        if ((day = TIME.days().bitsSinceStart()) == this.lastRefreshDay) {
            return;
        }
        this.lastRefreshDay = day;
        this.lastGuildPaid = 0L;
        for (int i = 0; i < all.size(); ++i) {
            if (ServiceMarket.isFreePublicService((RoomServiceAccess)all.get(i))) {
                this.prices[i] = 0.0;
                continue;
            }
            if (((RoomServiceAccess)all.get(i)).total() <= 0) continue;
            this.prices[i] = ServiceMarket.adjustPrice(this.prices[i], ((RoomServiceAccess)all.get(i)).load(), EconConfig.serviceUtilTarget, EconConfig.servicePriceUp, EconConfig.servicePriceDown, EconConfig.servicePriceMin, EconConfig.servicePriceMax);
        }
    }

    public int price(RoomServiceAccess service) {
        if (service == null) {
            return Integer.MAX_VALUE;
        }
        if (ServiceMarket.isFreePublicService(service)) {
            return 0;
        }
        if (service.index() >= this.prices.length) {
            this.refresh();
        }
        if (service.index() >= this.prices.length) {
            return EconConfig.serviceBasePrice;
        }
        return Math.max(0, (int)Math.ceil(this.prices[service.index()]));
    }

    public boolean admit(Humanoid humanoid, Wallets wallets, RoomServiceAccess service, Roster roster, Fiscal fiscal, FirmLedger ledger) {
        RoomBlueprintImp roomBlueprintImp;
        boolean won;
        if (!EconConfig.serviceMarketEnabled) {
            return true;
        }
        if (ServiceMarket.isFreePublicService(service)) {
            ++this.admitted;
            return true;
        }
        IHumanoidAccess hum = EngineMirror.api() != null ? EngineMirror.api().humanoids() : null;
        int urgency = service.need instanceof NEED_E ? Math.max(0, Math.min(4, (hum != null ? hum.getEventNeedPriority(humanoid, service.need) : EngineSeams.eventNeedPriority(humanoid, service.need)))) : (int)Math.ceil(4.0 * (1.0 - (hum != null ? hum.getServiceFulfilment(humanoid, (StatService)service.stats()) : EngineSeams.serviceFulfilment(humanoid, (StatService)service.stats()))));
        int price = this.price(service);
        int offered = ServiceMarket.bid((double)urgency / 4.0, wallets.spendable(humanoid), EconConfig.serviceBasePrice, EconConfig.serviceBidWealthWeight);
        boolean bl = won = service.available() > 0 && offered >= price;
        if (!won) {
            ++this.denied;
            return false;
        }
        ++this.admitted;
        if (price > 0 && (roomBlueprintImp = service.room()) instanceof RoomBlueprintIns) {
            RoomBlueprintIns blueprint = (RoomBlueprintIns)roomBlueprintImp;
            int paid = wallets.charge(humanoid, price);
            if (paid > 0) {
                fiscal.settleService(humanoid, paid, (RoomBlueprintImp)blueprint, roster, wallets, ledger);
                this.lastGuildPaid += (long)paid;
            }
        }
        return true;
    }

    private static boolean isFreePublicService(RoomServiceAccess service) {
        return service != null && ServiceMarket.isFreePublicRoomType(service.room().getClass());
    }

    static boolean isFreePublicRoomType(Class<?> roomType) {
        return roomType == ROOM_HEARTH.class || roomType == ROOM_WELL.class;
    }

    static int bid(double urgency, int spendable, int basePrice, double wealthWeight) {
        if (spendable <= 0 || basePrice <= 0) {
            return 0;
        }
        double u = Math.max(0.0, Math.min(1.0, urgency));
        double wealth = Math.log1p((double)spendable / (double)basePrice);
        double value = (double)basePrice * (0.25 + u) * (1.0 + Math.max(0.0, wealthWeight) * wealth);
        return Math.min(spendable, Math.max(0, (int)Math.floor(value)));
    }

    static double adjustPrice(double current, double utilization, double target, double up, double down, double min, double max) {
        double p = Double.isFinite(current) && current > 0.0 ? current : Math.max(1.0, min);
        double u = Math.max(0.0, Math.min(1.0, utilization));
        double change = u >= target ? Math.max(0.0, up) * (u - target) : -Math.max(0.0, down) * (target - u);
        return Math.max(min, Math.min(max, p * Math.exp(change)));
    }

    static int clearingPrice(int[] bids, int slots, int reserve) {
        if (bids == null || bids.length == 0 || slots <= 0) {
            return Math.max(0, reserve);
        }
        int[] copy = (int[])bids.clone();
        Arrays.sort(copy);
        int winners = Math.min(slots, copy.length);
        return Math.max(Math.max(0, reserve), copy[copy.length - winners]);
    }

    public void clear() {
        this.prices = new double[0];
        this.lastRefreshDay = Integer.MIN_VALUE;
        this.lastGuildPaid = 0L;
        this.denied = 0;
        this.admitted = 0;
    }
}

