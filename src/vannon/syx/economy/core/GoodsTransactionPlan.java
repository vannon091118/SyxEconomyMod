package vannon.syx.economy.core;

import init.race.RACES;
import init.resources.RESOURCES;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AI;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.AISUB;
import settlement.main.SETT;
import settlement.misc.util.FSERVICE;
import settlement.path.finders.SFinderFindable;
import settlement.room.main.Room;
import settlement.room.main.RoomInstance;
import settlement.room.service.market.ROOM_MARKET;
import settlement.stats.equip.WearableResource;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.sets.LIST;
import vannon.syx.economy.core.AffordabilityGate;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.LocalPrices;

public final class GoodsTransactionPlan
extends AIPLAN.PLANRES {
    private final AffordabilityGate gate;
    private final AIPLAN.PLANRES.Resumer walk = new AIPLAN.PLANRES.Resumer("walking to an affordable market"){

        protected AISUB.AISubActivation setAction(Humanoid h, AIManager d) {
            for (int i = 0; i < SETT.ROOMS().MARKET.size(); ++i) {
                AISUB.AISubActivation result;
                ROOM_MARKET market = (ROOM_MARKET)SETT.ROOMS().MARKET.get(i);
                if (!market.service().accessRequest(h) || !market.service().finder.has(h.tc()) || (result = AI.SUBS().walkTo.service(h, d, (SFinderFindable)market.service().finder, market.service().radius)) == null) continue;
                d.planByte3 = (byte)i;
                d.planTile.set((double)d.path.destX(), (double)d.path.destY());
                market.service().reportAccess(h, (COORDINATE)d.planTile);
                market.service().reportDistance(h);
                return result;
            }
            GoodsTransactionPlan.this.release(h, d);
            return null;
        }

        protected AISUB.AISubActivation res(Humanoid h, AIManager d) {
            ROOM_MARKET market = GoodsTransactionPlan.market(d);
            if (market == null) {
                GoodsTransactionPlan.this.release(h, d);
                return null;
            }
            FSERVICE service = market.service().service(d.planTile.x(), d.planTile.y());
            if (service != null && service.findableReservedIs()) {
                service.findableReserveCancel();
            }
            EconomySim sim = EconomySim.active();
            int total = 0;
            int[] resources = new int[RESOURCES.ALL().size()];
            LIST all = RACES.res().all(h.indu().popCL());
            for (int i = 0; i < all.size(); ++i) {
                int amount;
                WearableResource wearable = (WearableResource)all.get(i);
                int needed = Math.max(0, wearable.needed(h.indu()));
                if (needed == 0 || (amount = market.buy(RACES.res().get(wearable.resource(h.indu())), needed, d.planTile.x(), d.planTile.y())) <= 0) continue;
                wearable.wearOut(h.indu());
                wearable.inc(h.indu(), amount);
                int n = wearable.resource(h.indu()).index();
                resources[n] = resources[n] + amount;
                int unit = LocalPrices.goodPrice(wearable, wearable.resource(h.indu()), sim.roster(), sim.ticks());
                long cost = (long)total + (long)unit * (long)amount;
                total = cost >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)cost;
            }
            GoodsTransactionPlan.this.gate.settleGoods(h, GoodsTransactionPlan.this.admission(d), total, resources, GoodsTransactionPlan.servingRoom(d));
            d.planByte1 = 0;
            return null;
        }

        public boolean con(Humanoid h, AIManager d) {
            return true;
        }

        public void can(Humanoid h, AIManager d) {
            GoodsTransactionPlan.this.release(h, d);
        }
    };

    public GoodsTransactionPlan(AffordabilityGate gate) {
        super("ECON_EXACT_GOODS");
        this.gate = gate;
    }

    protected AISUB.AISubActivation init(Humanoid h, AIManager d) {
        EconomySim sim = EconomySim.active();
        if (sim == null) {
            return null;
        }
        AffordabilityGate.Admission admission = this.gate.requestGoods(h, sim.roster(), sim.ticks());
        if (!admission.admitted()) {
            return this.WAIT_AND_EXIT.set(h, d);
        }
        d.planByte1 = 1;
        d.planObject = admission.quote();
        return this.walk.set(h, d);
    }

    private AffordabilityGate.Admission admission(AIManager d) {
        return new AffordabilityGate.Admission(true, Math.max(0, d.planObject), false);
    }

    private void release(Humanoid h, AIManager d) {
        if (d.planByte1 == 1) {
            this.gate.cancel(h, this.admission(d));
        }
        d.planByte1 = 0;
    }

    private static ROOM_MARKET market(AIManager d) {
        int i = d.planByte3 & 0xFF;
        return i < SETT.ROOMS().MARKET.size() ? (ROOM_MARKET)SETT.ROOMS().MARKET.get(i) : null;
    }

    private static RoomInstance servingRoom(AIManager manager) {
        RoomInstance instance;
        Room room = SETT.ROOMS().map.get(manager.planTile.x(), manager.planTile.y());
        return room instanceof RoomInstance ? (instance = (RoomInstance)room) : null;
    }
}

