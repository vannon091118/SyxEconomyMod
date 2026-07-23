package vannon.syx.economy.core;

import java.util.Map;

import init.resources.RBIT;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AI;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.AISUB;
import settlement.job.Job;
import settlement.main.SETT;
import settlement.path.components.SComponent;
import settlement.path.finders.SFINDER;
import snake2d.util.datatypes.COORDINATE;
import vannon.syx.economy.core.StateWarehouses;

final class ConstructionHoardPlan
extends AIPLAN.PLANRES {
    private static final int MAX_CARRY = 8;
    private final StateWarehouses state;
    private final RBIT.RBITImp hoarded = new RBIT.RBITImp();
    private Job found;
    private Humanoid preparedFor;
    private int preparedX;
    private int preparedY;
    private Humanoid activeWorker;
    private Map<Integer, Integer> activeTargetedResources;
    private Job lastPreparedJob;

    public Job lastPreparedJob() {
        return this.lastPreparedJob;
    }

    private final SFINDER finder = new SFINDER(){

        public boolean isInComponent(SComponent component, double distance) {
            return SETT.PATH().comps.data.jobs.has(component, (RBIT)ConstructionHoardPlan.this.hoarded);
        }

        public boolean isTile(int tx, int ty, int tileNr) {
            Job job = SETT.JOBS().getter.get(tx, ty);
            if (!ConstructionHoardPlan.this.eligible(job, ConstructionHoardPlan.this.activeWorker, ConstructionHoardPlan.this.activeTargetedResources) || !job.jobReserveCanBe()) {
                return false;
            }
            ConstructionHoardPlan.this.found = job;
            return true;
        }
    };
    private final AIPLAN.PLANRES.Resumer toWarehouse = new AIPLAN.PLANRES.Resumer("collecting state construction materials"){

        protected AISUB.AISubActivation setAction(Humanoid worker, AIManager manager) {
            AISUB.AISubActivation result = AI.SUBS().walkTo.coo(worker, manager, ConstructionHoardPlan.sourceX(manager), ConstructionHoardPlan.sourceY(manager));
            if (result == null) {
                ConstructionHoardPlan.this.release(worker, manager);
            }
            return result;
        }

        protected AISUB.AISubActivation res(Humanoid worker, AIManager manager) {
            RESOURCE resource = ConstructionHoardPlan.resource(manager);
            int reserved = manager.planByte1 & 0xFF;
            int picked = ConstructionHoardPlan.this.state.pickupConstructionReservation(ConstructionHoardPlan.sourceX(manager), ConstructionHoardPlan.sourceY(manager), resource, reserved);
            manager.planByte1 = 0;
            if (picked <= 0 || !ConstructionHoardPlan.this.jobStillReserved(manager, resource)) {
                ConstructionHoardPlan.this.release(worker, manager);
                return null;
            }
            for (int i = 0; i < picked; ++i) {
                manager.resourceCarriedSet(resource);
            }
            return ConstructionHoardPlan.this.toSite.set(worker, manager);
        }

        public boolean con(Humanoid worker, AIManager manager) {
            return true;
        }

        public void can(Humanoid worker, AIManager manager) {
            ConstructionHoardPlan.this.release(worker, manager);
        }
    };
    private final AIPLAN.PLANRES.Resumer toSite = new AIPLAN.PLANRES.Resumer("delivering state construction materials"){

        protected AISUB.AISubActivation setAction(Humanoid worker, AIManager manager) {
            AISUB.AISubActivation result = AI.SUBS().walkTo.coo(worker, manager, manager.planTile.x(), manager.planTile.y());
            if (result == null) {
                ConstructionHoardPlan.this.release(worker, manager);
            }
            return result;
        }

        protected AISUB.AISubActivation res(Humanoid worker, AIManager manager) {
            RESOURCE resource = ConstructionHoardPlan.resource(manager);
            Job job = SETT.JOBS().getter.get((COORDINATE)manager.planTile);
            if (job == null || !job.jobReservedIs(resource) || manager.resourceCarried() != resource) {
                ConstructionHoardPlan.this.release(worker, manager);
                return null;
            }
            int amount = Math.min(manager.resourceA(), Math.max(0, job.jobResourcesNeeded(worker)));
            if (amount > 0) {
                job.jobPerform(worker, resource, amount);
                ConstructionHoardPlan.this.state.recordConstructionDelivery(resource, amount);
                manager.resourceAInc(-amount);
            }
            if (manager.resourceCarried() != null) {
                manager.resourceDrop(worker);
            }
            clear(manager);
            return null;
        }

        public boolean con(Humanoid worker, AIManager manager) {
            return ConstructionHoardPlan.this.jobStillReserved(manager, ConstructionHoardPlan.resource(manager));
        }

        public void can(Humanoid worker, AIManager manager) {
            ConstructionHoardPlan.this.release(worker, manager);
        }
    };

    ConstructionHoardPlan(StateWarehouses state) {
        super("ECON_CONSTRUCTION_HOARD_FETCH");
        this.state = state;
    }

    boolean prepare(Humanoid worker, Map<Integer, Integer> targetedResources) {
        this.preparedFor = null;
        this.lastPreparedJob = null;
        Job job = this.findJob(worker, targetedResources);
        if (job == null) {
            return false;
        }
        return this.prepare(worker, job, targetedResources);
    }

    boolean prepare(Humanoid worker, Job job, Map<Integer, Integer> targetedResources) {
        this.state.hoardedResourceMask(this.hoarded);
        if (!this.eligible(job, worker, targetedResources)) {
            return false;
        }
        this.preparedFor = worker;
        this.preparedX = job.jobCoo().x();
        this.preparedY = job.jobCoo().y();
        this.lastPreparedJob = job;
        return true;
    }

    protected AISUB.AISubActivation init(Humanoid worker, AIManager manager) {
        Job job;
        if (this.preparedFor == worker) {
            job = SETT.JOBS().getter.get(this.preparedX, this.preparedY);
            this.preparedFor = null;
        } else {
            job = this.findJob(worker, null);
        }
        if (!this.eligible(job, worker, null) || !job.jobReserveCanBe()) {
            return null;
        }
        RESOURCE resource = job.resourceCurrentlyNeeded();
        int wanted = Math.min(8, Math.max(1, job.jobResourcesNeeded(worker)));
        StateWarehouses.ConstructionSource source = this.state.reserveForConstruction(resource, worker.tc().x(), worker.tc().y(), job.jobCoo().x(), job.jobCoo().y(), wanted);
        if (source == null) {
            return null;
        }
        manager.planTile.set(job.jobCoo());
        manager.planObject = ConstructionHoardPlan.pack(source.x(), source.y());
        manager.planByte1 = (byte)source.amount();
        manager.planByte2 = resource.bIndex();
        job.jobReserve(resource);
        return this.toWarehouse.set(worker, manager);
    }

    private Job findJob(Humanoid worker, Map<Integer, Integer> targetedResources) {
        this.state.hoardedResourceMask(this.hoarded);
        if (this.hoarded.isClear()) {
            return null;
        }
        this.found = null;
        this.activeWorker = worker;
        this.activeTargetedResources = targetedResources;
        COORDINATE destination = SETT.PATH().finders.finder().findDest(worker.tc().x(), worker.tc().y(), this.finder, Integer.MAX_VALUE);
        this.activeWorker = null;
        this.activeTargetedResources = null;
        return destination == null ? null : this.found;
    }

    private boolean eligible(Job job, Humanoid worker, Map<Integer, Integer> targetedResources) {
        if (job == null || job.resourceCurrentlyNeeded() == null || !this.hoarded.has(job.resourceCurrentlyNeeded())) {
            return false;
        }
        if (!job.isConstruction() && !SETT.ROOMS().construction.isser.is(job.jobCoo())) {
            return false;
        }
        if (targetedResources != null && worker != null) {
            int key = job.jobCoo().x() << 16 | (job.jobCoo().y() & 0xFFFF);
            int alreadyTargeted = targetedResources.getOrDefault(key, 0);
            if (job.jobResourcesNeeded(worker) - alreadyTargeted <= 0) {
                return false;
            }
        }
        return true;
    }

    private boolean jobStillReserved(AIManager manager, RESOURCE resource) {
        Job job = SETT.JOBS().getter.get((COORDINATE)manager.planTile);
        return job != null && job.jobReservedIs(resource);
    }

    private void release(Humanoid worker, AIManager manager) {
        Job job;
        RESOURCE resource = ConstructionHoardPlan.resource(manager);
        int reserved = manager.planByte1 & 0xFF;
        if (reserved > 0 && resource != null) {
            this.state.cancelConstructionReservation(ConstructionHoardPlan.sourceX(manager), ConstructionHoardPlan.sourceY(manager), resource, reserved);
        }
        if ((job = SETT.JOBS().getter.get((COORDINATE)manager.planTile)) != null && job.jobReservedIs(resource)) {
            job.jobReserveCancel(resource);
        }
        if (manager.resourceCarried() != null) {
            manager.resourceDrop(worker);
        }
        ConstructionHoardPlan.clear(manager);
    }

    private static void clear(AIManager manager) {
        manager.planByte1 = 0;
        manager.planByte2 = (byte)-1;
        manager.planObject = 0;
    }

    private static RESOURCE resource(AIManager manager) {
        byte index = manager.planByte2;
        return index >= 0 && index < RESOURCES.ALL().size() ? RESOURCES.ALL().get(index) : null;
    }

    private static int pack(int x, int y) {
        return x << 16 | y & 0xFFFF;
    }

    private static int sourceX(AIManager manager) {
        return manager.planObject >>> 16;
    }

    private static int sourceY(AIManager manager) {
        return manager.planObject & 0xFFFF;
    }
}

