package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FResources;
import init.resources.Meal;
import init.resources.RBIT;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.resources.ResG;
import init.resources.ResGEat;
import init.type.NEEDS;
import java.util.IdentityHashMap;
import settlement.entity.animal.ANIMAL_ROOM_RUINER;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AI;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.AISTATE;
import settlement.entity.humanoid.ai.main.AISUB;
import settlement.main.SETT;
import settlement.misc.util.FSERVICE;
import settlement.path.finders.SFinderFindable;
import settlement.path.finders.SFinderMisc;
import settlement.room.main.Room;
import settlement.room.main.RoomInstance;
import settlement.room.service.food.canteen.EconomyCanteenAccess;
import settlement.room.service.food.canteen.ROOM_CANTEEN;
import settlement.room.service.food.eatery.EconomyEateryAccess;
import settlement.room.service.food.eatery.ROOM_EATERY;
import settlement.room.service.module.RoomServiceAccess;
import settlement.stats.Induvidual;
import settlement.stats.STATS;
import settlement.thing.THINGS;
import settlement.thing.ThingsCorpses;
import settlement.tilemap.terrain.TGrowable;
import settlement.tilemap.terrain.Terrain;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.DIR;
import snake2d.util.rnd.RND;
import vannon.syx.economy.core.AffordabilityGate;
import vannon.syx.economy.core.BrokeFoodPlan;
import vannon.syx.economy.core.RationOptimizer;

public final class FoodTransactionPlan
extends AIPLAN.PLANRES {
    private static final int EATERY = 0;
    private static final int CANTEEN = 1;
    private static final int RAW = 2;
    private static final int SCAVENGE_TERRAIN = 3;
    private static final int SCAVENGE_CORPSE = 4;
    private static final int DESPERATE = 5;
    private final AffordabilityGate gate;
    private final IdentityHashMap<Induvidual, PendingMeal> pending = new IdentityHashMap();
    private final RBIT.RBITImp rawFoodMask = new RBIT.RBITImp();
    private final SFinderMisc.FinderMiscWithoutDest edibleTerrain = new SFinderMisc.FinderMiscWithoutDest(32){

        protected boolean has() {
            return SETT.WEATHER().growthRipe.cropsAreRipe();
        }

        public boolean isTile(int tx, int ty) {
            TGrowable growable;
            Room room = SETT.ROOMS().map.get(tx, ty);
            if (room instanceof ANIMAL_ROOM_RUINER) {
                ANIMAL_ROOM_RUINER ruiner = (ANIMAL_ROOM_RUINER)room;
                return ruiner.canBeGraced(tx, ty);
            }
            Terrain.TerrainTile terrainTile = SETT.TERRAIN().get(tx, ty);
            return terrainTile instanceof TGrowable && (growable = (TGrowable)terrainTile).isEdible(tx, ty) && growable.size.get(tx, ty) > 0;
        }
    };
    private final SFinderMisc.FinderMiscWithoutDest corpses = new SFinderMisc.FinderMiscWithoutDest(32){

        protected boolean has() {
            return true;
        }

        public boolean isTile(int tx, int ty) {
            return FoodTransactionPlan.corpse(tx, ty) != null;
        }
    };
    private final AIPLAN.PLANRES.Resumer denied = new Resumer("starving"){

        protected AISUB.AISubActivation setAction(Humanoid humanoid, AIManager manager) {
            BrokeFoodPlan.markStarvedIfLethal(humanoid);
            if (STATS.FOOD().STARVATION.indu().getD(humanoid.indu()) > 0.0) {
                if (FoodTransactionPlan.this.edibleTerrain.find(humanoid.physics.tileC(), manager.path)) {
                    manager.planByte4 = (byte)3;
                    return AI.SUBS().walkTo.pathRun(humanoid, manager);
                }
                if (FoodTransactionPlan.this.corpses.find(humanoid.physics.tileC(), manager.path)) {
                    manager.planByte4 = (byte)4;
                    return AI.SUBS().walkTo.pathRun(humanoid, manager);
                }
            }
            manager.planByte4 = (byte)5;
            return AI.SUBS().desperate.activate(humanoid, manager);
        }

        protected AISUB.AISubActivation res(Humanoid humanoid, AIManager manager) {
            if (manager.planByte4 == 3) {
                FoodTransactionPlan.this.consumeTerrain(humanoid, manager);
            } else if (manager.planByte4 == 4) {
                FoodTransactionPlan.this.consumeCorpse(humanoid, manager);
            }
            BrokeFoodPlan.markStarvedIfLethal(humanoid);
            return null;
        }

        public boolean con(Humanoid humanoid, AIManager manager) {
            return true;
        }

        public void can(Humanoid humanoid, AIManager manager) {
        }
    };
    private final AISUB eatAnimation = new AISUB.Simple("ECON_EATING"){

        protected AISTATE resume(Humanoid humanoid, AIManager manager) {
            manager.subByte = (byte)(manager.subByte + 1);
            return switch (manager.subByte) {
                case 1, 3 -> AI.STATES().STAND.activate(humanoid, manager, (double)(1.5f + RND.rFloat((double)4.0)));
                case 2, 4 -> AI.STATES().anima.box.activate(humanoid, manager, 2.5 + (double)RND.rFloat((double)2.0));
                default -> null;
            };
        }
    };
    private final AIPLAN.PLANRES.Resumer walk = new Resumer("finding affordable food"){

        protected AISUB.AISubActivation setAction(Humanoid humanoid, AIManager manager) {
            AISUB.AISubActivation activation = FoodTransactionPlan.this.findService(humanoid, manager, manager.planObject);
            return activation != null ? activation : FoodTransactionPlan.this.rawFetch.set(humanoid, manager);
        }

        protected AISUB.AISubActivation res(Humanoid humanoid, AIManager manager) {
            return manager.planByte4 == 0 ? FoodTransactionPlan.this.eateryAnimation.set(humanoid, manager) : FoodTransactionPlan.this.consumeCanteen(humanoid, manager);
        }

        public boolean con(Humanoid humanoid, AIManager manager) {
            return true;
        }

        public void can(Humanoid humanoid, AIManager manager) {
            FoodTransactionPlan.this.releaseAdmission(humanoid, manager);
        }
    };
    private final AIPLAN.PLANRES.Resumer rawFetch = new Resumer("finding raw food"){

        protected AISUB.AISubActivation setAction(Humanoid humanoid, AIManager manager) {
            AISUB.AISubActivation result;
            FoodTransactionPlan.this.rawFoodMask.clearSet(RESOURCES.EDI().mask);
            if (STATS.FOOD().STARVATION.indu().get(humanoid.indu()) <= 0) {
                FoodTransactionPlan.this.rawFoodMask.and(STATS.FOOD().fetchMask(humanoid));
            }
            if ((result = AI.SUBS().walkTo.resource(humanoid, manager, (RBIT)FoodTransactionPlan.this.rawFoodMask, Integer.MAX_VALUE)) == null) {
                FoodTransactionPlan.this.releaseAdmission(humanoid, manager);
            }
            return result;
        }

        protected AISUB.AISubActivation res(Humanoid humanoid, AIManager manager) {
            if (manager.resourceCarried() == null || !RESOURCES.EDI().is(manager.resourceCarried())) {
                return null;
            }
            int foodIndex = FoodTransactionPlan.edibleIndex(manager.resourceCarried());
            if (foodIndex < 0) {
                return null;
            }
            manager.planByte4 = (byte)2;
            manager.planByte3 = (byte)foodIndex;
            int[] quantities = new int[RESOURCES.EDI().all().size()];
            quantities[foodIndex] = 1;
            AffordabilityGate.Admission admission = FoodTransactionPlan.this.gate.requestFood(humanoid, quantities);
            if (!admission.admitted()) {
                manager.resourceDrop(humanoid);
                return FoodTransactionPlan.this.denied.set(humanoid, manager);
            }
            PendingMeal meal = new PendingMeal(quantities, 1, humanoid.race().pref().foodMask.has(manager.resourceCarried()) ? 1 : 0, admission);
            FoodTransactionPlan.this.pending.put(humanoid.indu(), meal);
            manager.planByte1 = 1;
            manager.planObject = admission.quote();
            FoodTransactionPlan.this.complete(humanoid, manager, meal, null);
            FACTIONS.player().res().inc(manager.resourceCarried(), FResources.RTYPE.CONSUMED, -1);
            manager.resourceCarriedSet(null);
            return FoodTransactionPlan.this.rawAnimation.set(humanoid, manager);
        }

        public boolean con(Humanoid h, AIManager d) {
            return true;
        }

        public void can(Humanoid h, AIManager d) {
            d.resourceDrop(h);
            FoodTransactionPlan.this.releaseAdmission(h, d);
        }
    };
    private final AIPLAN.PLANRES.Resumer rawAnimation = new Resumer("eating raw food"){

        protected AISUB.AISubActivation setAction(Humanoid h, AIManager d) {
            return FoodTransactionPlan.this.eatAnimation.activate(h, d);
        }

        protected AISUB.AISubActivation res(Humanoid h, AIManager d) {
            return FoodTransactionPlan.this.afterMeal(h, d);
        }

        public boolean con(Humanoid h, AIManager d) {
            return true;
        }

        public void can(Humanoid h, AIManager d) {
        }
    };
    private final AIPLAN.PLANRES.Resumer eateryAnimation = new Resumer("eating"){

        protected AISUB.AISubActivation setAction(Humanoid h, AIManager d) {
            return FoodTransactionPlan.this.eatAnimation.activate(h, d);
        }

        protected AISUB.AISubActivation res(Humanoid humanoid, AIManager manager) {
            return FoodTransactionPlan.this.consumeEatery(humanoid, manager) ? FoodTransactionPlan.this.afterMeal(humanoid, manager) : FoodTransactionPlan.this.denied.set(humanoid, manager);
        }

        public boolean con(Humanoid humanoid, AIManager manager) {
            return true;
        }

        public void can(Humanoid humanoid, AIManager manager) {
            FoodTransactionPlan.cancelServiceReservation(manager);
            FoodTransactionPlan.this.releaseAdmission(humanoid, manager);
        }
    };
    private final AIPLAN.PLANRES.Resumer chairWalk = new Resumer("walking to table"){

        protected AISUB.AISubActivation setAction(Humanoid h, AIManager d) {
            return null;
        }

        protected AISUB.AISubActivation res(Humanoid h, AIManager d) {
            return FoodTransactionPlan.this.chairLast.set(h, d);
        }

        public boolean con(Humanoid h, AIManager d) {
            return FoodTransactionPlan.canteen(d) != null;
        }

        public void can(Humanoid h, AIManager d) {
            FoodTransactionPlan.releaseChair(d);
        }
    };
    private final AIPLAN.PLANRES.Resumer chairLast = new Resumer("taking a seat"){

        protected AISUB.AISubActivation setAction(Humanoid humanoid, AIManager manager) {
            DIR direction;
            ROOM_CANTEEN canteen = FoodTransactionPlan.canteen(manager);
            DIR dIR = direction = canteen == null ? null : canteen.setChair(manager.planTile.x(), manager.planTile.y(), manager.planObject);
            if (direction == null) {
                FoodTransactionPlan.releaseChair(manager);
                return FoodTransactionPlan.this.eatAtTable.set(humanoid, manager);
            }
            return AI.SUBS().single.activate(humanoid, manager, AI.STATES().WALK2.moveToEdge(humanoid, manager, direction));
        }

        protected AISUB.AISubActivation res(Humanoid h, AIManager d) {
            return FoodTransactionPlan.this.eatAtTable.set(h, d);
        }

        public boolean con(Humanoid h, AIManager d) {
            return FoodTransactionPlan.canteen(d) != null;
        }

        public void can(Humanoid h, AIManager d) {
            FoodTransactionPlan.releaseChair(d);
        }
    };
    private final AIPLAN.PLANRES.Resumer eatAtTable = new Resumer("eating at table"){

        protected AISUB.AISubActivation setAction(Humanoid humanoid, AIManager manager) {
            manager.planByte1 = (byte)(4 + RND.rInt((int)10));
            return FoodTransactionPlan.this.eatAnimation.activate(humanoid, manager);
        }

        protected AISUB.AISubActivation res(Humanoid humanoid, AIManager manager) {
            manager.planByte1 = (byte)(manager.planByte1 - 1);
            if (manager.planByte1 >= 0) {
                return FoodTransactionPlan.this.eatAnimation.activate(humanoid, manager);
            }
            FoodTransactionPlan.releaseChair(manager);
            return FoodTransactionPlan.this.afterMeal(humanoid, manager);
        }

        public boolean con(Humanoid h, AIManager d) {
            return FoodTransactionPlan.canteen(d) != null;
        }

        public void can(Humanoid h, AIManager d) {
            FoodTransactionPlan.releaseChair(d);
        }
    };

    public FoodTransactionPlan(AffordabilityGate gate) {
        super("ECON_EXACT_FOOD");
        this.gate = gate;
        // Phase-4.7 (Task 2): register pending Induvidual map for clearOnLoad.
        // Map is per-AI-plan transient — cleared on plan-init, complete, failure.
        IdentityMapRegistry.register("FoodTransactionPlan", "pending", pending);
    }

    private void consumeTerrain(Humanoid humanoid, AIManager manager) {
        short ty;
        short tx = manager.path.destX();
        if (!this.edibleTerrain.isTile((int)tx, (int)(ty = manager.path.destY()))) {
            return;
        }
        this.gate.recordScavengedMeal(humanoid);
        STATS.FOOD().eat(humanoid, 0, 0.0);
        NEEDS.TYPES().HUNGER.stat().fix(humanoid.indu());
        SETT.TERRAIN().get((int)tx, (int)ty).clearing().clear1((int)tx, (int)ty);
        Room room = SETT.ROOMS().map.get((int)tx, (int)ty);
        if (room != null && room.destroyTileCan((int)tx, (int)ty)) {
            room.destroyTile((int)tx, (int)ty);
        }
    }

    private void consumeCorpse(Humanoid humanoid, AIManager manager) {
        ThingsCorpses.Corpse corpse = FoodTransactionPlan.corpse(manager.path.destX(), manager.path.destY());
        if (corpse == null) {
            return;
        }
        SETT.ROOMS().CANNIBAL.reportCannibal(corpse.race());
        corpse.removeMeat();
        this.gate.recordScavengedMeal(humanoid);
        STATS.FOOD().eat(humanoid, 0, 0.0);
        NEEDS.TYPES().HUNGER.stat().fix(humanoid.indu());
    }

    private static ThingsCorpses.Corpse corpse(int tx, int ty) {
        for (THINGS.Thing thing : SETT.THINGS().get(tx, ty)) {
            ThingsCorpses.Corpse corpse;
            if (!(thing instanceof ThingsCorpses.Corpse) || !(corpse = (ThingsCorpses.Corpse)thing).hasMeat()) continue;
            return corpse;
        }
        return null;
    }

    protected AISUB.AISubActivation init(Humanoid humanoid, AIManager manager) {
        this.pending.remove(humanoid.indu());
        manager.planByte1 = 0;
        manager.planByte2 = 0;
        int maximum = Math.max(1, STATS.FOOD().FOOD.decree().get(humanoid));
        manager.planObject = 1 + RND.rInt((int)maximum);
        return this.walk.set(humanoid, manager);
    }

    private AISUB.AISubActivation findService(Humanoid humanoid, AIManager manager, int requested) {
        int canteens;
        int eateries = SETT.ROOMS().EATERIES.size();
        int total = eateries + (canteens = SETT.ROOMS().CANTEENS.size());
        if (total == 0) {
            return null;
        }
        int start = RND.rInt((int)total);
        boolean unaffordable = false;
        for (int offset = 0; offset < total; ++offset) {
            int candidate = (start + offset) % total;
            if (candidate < eateries) {
                ROOM_EATERY room = (ROOM_EATERY)SETT.ROOMS().EATERIES.get(candidate);
                AISUB.AISubActivation result = FoodTransactionPlan.reserve(humanoid, manager, room.service());
                if (result == null) continue;
                manager.planByte4 = 0;
                manager.planByte3 = (byte)candidate;
                Prepare prepared = this.prepareServiceMeal(humanoid, manager, requested);
                if (prepared == Prepare.READY) {
                    return result;
                }
                unaffordable |= prepared == Prepare.UNAFFORDABLE;
                FoodTransactionPlan.cancelServiceReservation(manager);
                continue;
            }
            int index = candidate - eateries;
            ROOM_CANTEEN room = (ROOM_CANTEEN)SETT.ROOMS().CANTEENS.get(index);
            AISUB.AISubActivation result = FoodTransactionPlan.reserve(humanoid, manager, room.service());
            if (result == null) continue;
            manager.planByte4 = 1;
            manager.planByte3 = (byte)index;
            Prepare prepared = this.prepareServiceMeal(humanoid, manager, requested);
            if (prepared == Prepare.READY) {
                return result;
            }
            unaffordable |= prepared == Prepare.UNAFFORDABLE;
            FoodTransactionPlan.cancelServiceReservation(manager);
        }
        return unaffordable ? this.denied.set(humanoid, manager) : null;
    }

    private Prepare prepareServiceMeal(Humanoid humanoid, AIManager manager, int requested) {
        int[] stock;
        int limit = requested;
        ROOM_EATERY eatery = FoodTransactionPlan.eatery(manager);
        ROOM_CANTEEN canteen = FoodTransactionPlan.canteen(manager);
        if (eatery != null) {
            stock = EconomyEateryAccess.stock(eatery, manager.planTile.x(), manager.planTile.y());
        } else if (canteen != null) {
            stock = EconomyCanteenAccess.stock(canteen, manager.planTile.x(), manager.planTile.y());
            limit = Math.min(limit, EconomyCanteenAccess.servingLimit(canteen, manager.planTile.x(), manager.planTile.y()));
        } else {
            return Prepare.EMPTY;
        }
        RationOptimizer.Result result = this.optimize(humanoid, stock, limit);
        if (result.servings() <= 0) {
            return Prepare.EMPTY;
        }
        AffordabilityGate.Admission admission = this.gate.requestFood(humanoid, result.bundle());
        if (!admission.admitted()) {
            return Prepare.UNAFFORDABLE;
        }
        this.pending.put(humanoid.indu(), new PendingMeal(result.bundle(), result.servings(), result.preferredServings(), admission));
        manager.planByte1 = 1;
        manager.planObject = admission.quote();
        return Prepare.READY;
    }

    private RationOptimizer.Result optimize(Humanoid humanoid, int[] stock, int requested) {
        boolean[] preferred = new boolean[stock.length];
        for (int i = 0; i < preferred.length; ++i) {
            preferred[i] = humanoid.race().pref().foodMask.has(((ResGEat)RESOURCES.EDI().all().get((int)i)).resource);
        }
        return RationOptimizer.optimize(requested, stock, this.gate.foodUnitPrices(), preferred);
    }

    private static AISUB.AISubActivation reserve(Humanoid humanoid, AIManager manager, RoomServiceAccess service) {
        if (!service.accessRequest(humanoid) || !service.finder.has(humanoid.tc())) {
            return null;
        }
        int radius = STATS.FOOD().STARVATION.indu().get(humanoid.indu()) > 0 ? Integer.MAX_VALUE : service.radius;
        AISUB.AISubActivation result = AI.SUBS().walkTo.service(humanoid, manager, (SFinderFindable)service.finder, radius);
        if (result == null) {
            return null;
        }
        manager.planTile.set((double)manager.path.destX(), (double)manager.path.destY());
        service.reportDistance(humanoid);
        service.reportAccess(humanoid, (COORDINATE)manager.planTile);
        return result;
    }

    private boolean consumeEatery(Humanoid humanoid, AIManager manager) {
        ROOM_EATERY room = FoodTransactionPlan.eatery(manager);
        if (room == null) {
            this.releaseAdmission(humanoid, manager);
            return false;
        }
        PendingMeal meal = this.refreshPending(humanoid, manager);
        if (meal == null || !EconomyEateryAccess.consume(room, manager.planTile.x(), manager.planTile.y(), meal.bundle())) {
            FoodTransactionPlan.cancelServiceReservation(manager);
            this.releaseAdmission(humanoid, manager);
            return false;
        }
        this.complete(humanoid, manager, meal, FoodTransactionPlan.servingRoom(manager));
        return true;
    }

    private AISUB.AISubActivation consumeCanteen(Humanoid humanoid, AIManager manager) {
        ROOM_CANTEEN room = FoodTransactionPlan.canteen(manager);
        if (room == null) {
            this.releaseAdmission(humanoid, manager);
            return null;
        }
        PendingMeal pendingMeal = this.refreshPending(humanoid, manager);
        if (pendingMeal == null || !EconomyCanteenAccess.consume(room, manager.planTile.x(), manager.planTile.y(), pendingMeal.bundle())) {
            FoodTransactionPlan.cancelServiceReservation(manager);
            this.releaseAdmission(humanoid, manager);
            return this.denied.set(humanoid, manager);
        }
        int meal = FoodTransactionPlan.mealData(pendingMeal);
        this.complete(humanoid, manager, pendingMeal, FoodTransactionPlan.servingRoom(manager));
        COORDINATE chair = room.getChair(manager.planTile.x(), manager.planTile.y());
        if (chair != null) {
            AISUB.AISubActivation walk = AI.SUBS().walkTo.cooFull(humanoid, manager, chair);
            if (walk != null) {
                manager.planTile.set(chair);
                manager.planObject = meal;
                manager.planByte2 = 1;
                this.chairWalk.set(humanoid, manager);
                return walk;
            }
            room.returnChair(chair.x(), chair.y());
        }
        return this.eatAtTable.set(humanoid, manager);
    }

    private PendingMeal refreshPending(Humanoid humanoid, AIManager manager) {
        int[] stock;
        PendingMeal previous = this.pending.get(humanoid.indu());
        if (previous == null) {
            return null;
        }
        int limit = previous.servings();
        ROOM_EATERY eatery = FoodTransactionPlan.eatery(manager);
        ROOM_CANTEEN canteen = FoodTransactionPlan.canteen(manager);
        if (eatery != null) {
            stock = EconomyEateryAccess.stock(eatery, manager.planTile.x(), manager.planTile.y());
        } else if (canteen != null) {
            stock = EconomyCanteenAccess.stock(canteen, manager.planTile.x(), manager.planTile.y());
            limit = Math.min(limit, EconomyCanteenAccess.servingLimit(canteen, manager.planTile.x(), manager.planTile.y()));
        } else {
            return null;
        }
        RationOptimizer.Result result = this.optimize(humanoid, stock, limit);
        if (result.servings() <= 0) {
            return null;
        }
        AffordabilityGate.Admission admission = this.gate.replaceFood(humanoid, previous.admission(), result.bundle());
        if (!admission.admitted()) {
            this.pending.remove(humanoid.indu());
            manager.planByte1 = 0;
            return null;
        }
        PendingMeal refreshed = new PendingMeal(result.bundle(), result.servings(), result.preferredServings(), admission);
        this.pending.put(humanoid.indu(), refreshed);
        manager.planObject = admission.quote();
        return refreshed;
    }

    private void complete(Humanoid humanoid, AIManager manager, PendingMeal meal, RoomInstance seller) {
        this.gate.settleFood(humanoid, meal.admission(), meal.bundle(), seller);
        this.pending.remove(humanoid.indu());
        manager.planByte1 = 0;
        STATS.FOOD().eat(humanoid, meal.servings(), FoodTransactionPlan.preference(meal));
    }

    private AISUB.AISubActivation afterMeal(Humanoid humanoid, AIManager manager) {
        return null;
    }

    private void releaseAdmission(Humanoid humanoid, AIManager manager) {
        if (manager.planByte1 != 1) {
            return;
        }
        PendingMeal meal = this.pending.remove(humanoid.indu());
        if (meal != null) {
            this.gate.cancelFood(humanoid, meal.admission());
        }
        manager.planByte1 = 0;
    }

    private static int mealData(PendingMeal meal) {
        int[] bundle = meal.bundle();
        for (int i = 0; i < bundle.length; ++i) {
            if (bundle[i] <= 0) continue;
            return Meal.make((ResG)((ResG)RESOURCES.EDI().all().get(i)), (int)meal.servings(), (double)FoodTransactionPlan.preference(meal));
        }
        throw new IllegalStateException("cannot encode an empty optimized meal");
    }

    private static double preference(PendingMeal meal) {
        if (meal.servings() <= 0) {
            return 0.0;
        }
        int ordinary = meal.servings() - meal.preferredServings();
        return ((double)meal.preferredServings() + 0.25 * (double)ordinary) / (double)meal.servings();
    }

    private static void cancelServiceReservation(AIManager manager) {
        FSERVICE service = FoodTransactionPlan.service(manager);
        if (service != null && service.findableReservedIs()) {
            service.findableReserveCancel();
        }
    }

    private static FSERVICE service(AIManager manager) {
        ROOM_EATERY eatery = FoodTransactionPlan.eatery(manager);
        if (eatery != null) {
            return eatery.service().service(manager.planTile.x(), manager.planTile.y());
        }
        ROOM_CANTEEN canteen = FoodTransactionPlan.canteen(manager);
        return canteen == null ? null : canteen.service().service(manager.planTile.x(), manager.planTile.y());
    }

    private static RoomInstance servingRoom(AIManager manager) {
        RoomInstance instance;
        Room room = SETT.ROOMS().map.get(manager.planTile.x(), manager.planTile.y());
        return room instanceof RoomInstance ? (instance = (RoomInstance)room) : null;
    }

    private static ROOM_EATERY eatery(AIManager manager) {
        int index = manager.planByte3 & 0xFF;
        return manager.planByte4 == 0 && index < SETT.ROOMS().EATERIES.size() ? (ROOM_EATERY)SETT.ROOMS().EATERIES.get(index) : null;
    }

    private static ROOM_CANTEEN canteen(AIManager manager) {
        int index = manager.planByte3 & 0xFF;
        return manager.planByte4 == 1 && index < SETT.ROOMS().CANTEENS.size() ? (ROOM_CANTEEN)SETT.ROOMS().CANTEENS.get(index) : null;
    }

    private static void releaseChair(AIManager manager) {
        if (manager.planByte2 != 1) {
            return;
        }
        ROOM_CANTEEN room = FoodTransactionPlan.canteen(manager);
        if (room != null) {
            room.returnChair(manager.planTile.x(), manager.planTile.y());
        }
        manager.planByte2 = 0;
    }

    private static int edibleIndex(RESOURCE resource) {
        for (int i = 0; i < RESOURCES.EDI().all().size(); ++i) {
            if (((ResGEat)RESOURCES.EDI().all().get((int)i)).resource != resource) continue;
            return i;
        }
        return -1;
    }

    private static enum Prepare {
        READY,
        EMPTY,
        UNAFFORDABLE;

    }

    private record PendingMeal(int[] bundle, int servings, int preferredServings, AffordabilityGate.Admission admission) {
        private PendingMeal {
            bundle = bundle.clone();
        }

        @Override
        public int[] bundle() {
            return bundle.clone();
        }
    }
}

