package vannon.syx.economy.core;

import init.resources.RESOURCES;
import init.resources.ResGDrink;
import java.util.IdentityHashMap;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AI;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.AISTATE;
import settlement.entity.humanoid.ai.main.AISUB;
import settlement.main.SETT;
import settlement.misc.util.FSERVICE;
import settlement.path.finders.SFinderFindable;
import settlement.room.main.Room;
import settlement.room.main.RoomInstance;
import settlement.room.service.food.tavern.EconomyTavernAccess;
import settlement.room.service.food.tavern.ROOM_TAVERN;
import settlement.room.service.module.RoomServiceAccess;
import settlement.stats.Induvidual;
import settlement.stats.STATS;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.rnd.RND;
import vannon.syx.economy.core.AffordabilityGate;
import vannon.syx.economy.core.RationOptimizer;

public final class DrinkTransactionPlan
extends AIPLAN.PLANRES {
    private final AffordabilityGate gate;
    private final IdentityHashMap<Induvidual, PendingRound> pending = new IdentityHashMap();
    private final AISUB animation = new AISUB.Simple("ECON_DRINKING"){

        protected AISTATE resume(Humanoid h, AIManager d) {
            d.subByte = (byte)(d.subByte + 1);
            return d.subByte < 4 ? AI.STATES().STAND.activate(h, d, (double)(1.5f + RND.rFloat((double)2.0))) : null;
        }
    };
    private final AIPLAN.PLANRES.Resumer walk = new Resumer("walking to an affordable tavern"){

        protected AISUB.AISubActivation setAction(Humanoid h, AIManager d) {
            boolean unaffordable = false;
            for (int i = 0; i < SETT.ROOMS().TAVERNS.size(); ++i) {
                AISUB.AISubActivation result;
                ROOM_TAVERN room = (ROOM_TAVERN)SETT.ROOMS().TAVERNS.get(i);
                RoomServiceAccess service = room.service();
                if (!service.accessRequest(h) || !service.finder.has(h.tc()) || (result = AI.SUBS().walkTo.service(h, d, (SFinderFindable)service.finder, service.radius)) == null) continue;
                d.planByte3 = (byte)i;
                d.planTile.set((double)d.path.destX(), (double)d.path.destY());
                Prepare prepared = DrinkTransactionPlan.this.prepareRound(h, d, room);
                if (prepared == Prepare.READY) {
                    service.reportAccess(h, (COORDINATE)d.planTile);
                    service.reportDistance(h);
                    return result;
                }
                unaffordable |= prepared == Prepare.UNAFFORDABLE;
                DrinkTransactionPlan.cancelService(d);
            }
            DrinkTransactionPlan.this.release(h, d);
            return unaffordable ? DrinkTransactionPlan.this.WAIT_AND_EXIT.set(h, d) : null;
        }

        protected AISUB.AISubActivation res(Humanoid h, AIManager d) {
            return DrinkTransactionPlan.this.serve.set(h, d);
        }

        public boolean con(Humanoid h, AIManager d) {
            return true;
        }

        public void can(Humanoid h, AIManager d) {
            DrinkTransactionPlan.cancelService(d);
            DrinkTransactionPlan.this.release(h, d);
        }
    };
    private final AIPLAN.PLANRES.Resumer serve = new Resumer("drinking"){

        protected AISUB.AISubActivation setAction(Humanoid h, AIManager d) {
            FSERVICE s = DrinkTransactionPlan.service(d);
            if (s != null) {
                s.startUsing();
            }
            return DrinkTransactionPlan.this.animation.activate(h, d);
        }

        protected AISUB.AISubActivation res(Humanoid h, AIManager d) {
            ROOM_TAVERN room = DrinkTransactionPlan.room(d);
            PendingRound round = DrinkTransactionPlan.this.pending.remove(h.indu());
            if (room == null || round == null) {
                DrinkTransactionPlan.this.release(h, d);
                return null;
            }
            FSERVICE s = DrinkTransactionPlan.service(d);
            if (s != null) {
                s.startUsing();
            }
            if (!EconomyTavernAccess.consume(room, d.planTile.x(), d.planTile.y(), round.bundle())) {
                DrinkTransactionPlan.cancelService(d);
                DrinkTransactionPlan.this.release(h, d);
                return null;
            }
            DrinkTransactionPlan.this.gate.settleDrink(h, round.admission(), round.bundle(), DrinkTransactionPlan.servingRoom(d));
            d.planByte1 = 0;
            STATS.FOOD().drink(h, round.servings(), DrinkTransactionPlan.preference(round));
            DrinkTransactionPlan.consumeService(d);
            return null;
        }

        public boolean con(Humanoid h, AIManager d) {
            return DrinkTransactionPlan.room(d) != null;
        }

        public void can(Humanoid h, AIManager d) {
            DrinkTransactionPlan.cancelService(d);
            DrinkTransactionPlan.this.release(h, d);
        }
    };

    public DrinkTransactionPlan(AffordabilityGate gate) {
        super("ECON_EXACT_DRINK");
        this.gate = gate;
        // Phase-4.7 (Task 2): register pending Induvidual map for clearOnLoad.
        IdentityMapRegistry.register("DrinkTransactionPlan", "pending", pending);
    }

    protected AISUB.AISubActivation init(Humanoid h, AIManager d) {
        this.pending.remove(h.indu());
        d.planByte1 = 0;
        return this.walk.set(h, d);
    }

    private Prepare prepareRound(Humanoid h, AIManager d, ROOM_TAVERN room) {
        int[] stock = EconomyTavernAccess.stock(room, d.planTile.x(), d.planTile.y());
        int wanted = Math.max(1, STATS.FOOD().DRINK.decree().get(h));
        boolean[] preferred = new boolean[stock.length];
        for (int i = 0; i < preferred.length; ++i) {
            preferred[i] = h.race().pref().drinkMask.has(((ResGDrink)RESOURCES.DRINKS().all().get((int)i)).resource);
        }
        RationOptimizer.Result result = RationOptimizer.optimize(wanted, stock, this.gate.drinkUnitPrices(), preferred);
        if (result.servings() <= 0) {
            return Prepare.EMPTY;
        }
        AffordabilityGate.Admission admission = this.gate.requestDrink(h, result.bundle());
        if (!admission.admitted()) {
            return Prepare.UNAFFORDABLE;
        }
        this.pending.put(h.indu(), new PendingRound(result.bundle(), result.servings(), result.preferredServings(), admission));
        d.planByte1 = 1;
        d.planObject = admission.quote();
        return Prepare.READY;
    }

    private static RoomInstance servingRoom(AIManager manager) {
        RoomInstance instance;
        Room room = SETT.ROOMS().map.get(manager.planTile.x(), manager.planTile.y());
        return room instanceof RoomInstance ? (instance = (RoomInstance)room) : null;
    }

    private static double preference(PendingRound round) {
        if (round.servings() <= 0) {
            return 0.0;
        }
        int ordinary = round.servings() - round.preferred();
        return ((double)round.preferred() + 0.25 * (double)ordinary) / (double)round.servings();
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

    private static ROOM_TAVERN room(AIManager d) {
        int i = d.planByte3 & 0xFF;
        return i < SETT.ROOMS().TAVERNS.size() ? (ROOM_TAVERN)SETT.ROOMS().TAVERNS.get(i) : null;
    }

    private static FSERVICE service(AIManager d) {
        ROOM_TAVERN room = DrinkTransactionPlan.room(d);
        return room == null ? null : room.service().service(d.planTile.x(), d.planTile.y());
    }

    private static void cancelService(AIManager d) {
        FSERVICE s = DrinkTransactionPlan.service(d);
        if (s != null && s.findableReservedIs()) {
            s.findableReserveCancel();
        }
    }

    private static void consumeService(AIManager d) {
        FSERVICE s = DrinkTransactionPlan.service(d);
        if (s != null && s.findableReservedIs()) {
            s.consume();
        }
    }

    private static enum Prepare {
        READY,
        EMPTY,
        UNAFFORDABLE;

    }

    private record PendingRound(int[] bundle, int servings, int preferred, AffordabilityGate.Admission admission) {
    }
}

