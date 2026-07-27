package vannon.syx.economy.core;

import java.util.HashMap;
import java.util.Map;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.HAI;
import settlement.job.Job;
import settlement.main.SETT;
import settlement.room.main.RoomInstance;
import snake2d.util.datatypes.COORDINATE;
import vannon.syx.economy.core.ConstructionHoardPlan;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EngineSeams;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.StateWarehouses;
import vannon.syx.economy.adapter.EngineMirror;

final class ConstructionHoardController {
    private static final String WORK_WAIT_PLAN = "settlement.entity.humanoid.ai.work.PlanHangArround";
    private final ConstructionHoardPlan plan;

    ConstructionHoardController(StateWarehouses state) {
        this.plan = new ConstructionHoardPlan(state);
    }

    void update(Roster roster) {
        if (!EconConfig.stateWarehousesEnabled) {
            return;
        }
        
        // v1.7.3 Swarming-Schutz: Verhindert, dass alle Tagelöhner und freien Bauarbeiter
        // gleichzeitig für denselben Bauauftrag losrennen.
        // Key: x << 16 | (y & 0xFFFF) -> targeted_amount
        Map<Integer, Integer> targetedResources = new HashMap<>();
        
        // 1. Erster Durchlauf: Erfasse alle Bau-Ressourcen, die sich bereits auf dem Weg befinden
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid worker = roster.get(i);
            HAI hAI = worker.ai();
            if (!(hAI instanceof AIManager)) continue;
            AIManager manager = (AIManager)hAI;
            if (manager.plan() == this.plan) {
                int key = manager.planTile.x() << 16 | (manager.planTile.y() & 0xFFFF);
                int amount = 0;
                if (manager.resourceCarried() != null) {
                    amount = manager.resourceA();
                } else {
                    amount = manager.planByte1 & 0xFF;
                }
                if (amount > 0) {
                    targetedResources.put(key, targetedResources.getOrDefault(key, 0) + amount);
                }
            }
        }

        // 2. Zweiter Durchlauf: Weise neue Aufgaben unter Beachtung des Limits zu
        for (int i = 0; i < roster.size(); ++i) {
            boolean idleBuilder;
            AIManager manager;
            Humanoid worker = roster.get(i);
            HAI hAI = worker.ai();
            if (!(hAI instanceof AIManager) || (manager = (AIManager)hAI).resourceCarried() != null) continue;
            boolean oddjobber = EconomySim.active().aiAdapter().isOddjobbing(worker);
            RoomInstance workplace = EngineMirror.api() != null && EngineMirror.api().humanoids() != null
                    ? EngineMirror.api().humanoids().getEmployedRoom(worker) : EngineSeams.employedRoom(worker);
            boolean bl = idleBuilder = workplace != null && workplace.blueprint() == SETT.ROOMS().BUILDER && manager.plan() != null && WORK_WAIT_PLAN.equals(manager.plan().getClass().getName());
            if (!oddjobber && !idleBuilder) continue;
            
            Job current = (Job)SETT.JOBS().getter.get((COORDINATE)manager.planTile);
            if (oddjobber && current != null && current.jobReservedIs(current.resourceCurrentlyNeeded())) {
                if (!this.plan.prepare(worker, current, targetedResources)) continue;
                EngineSeams.overwritePlan(worker, (AIPLAN)this.plan);
                int key = current.jobCoo().x() << 16 | (current.jobCoo().y() & 0xFFFF);
                int wanted = Math.min(8, Math.max(1, current.jobResourcesNeeded(worker)));
                targetedResources.put(key, targetedResources.getOrDefault(key, 0) + wanted);
                continue;
            }
            if (!this.plan.prepare(worker, targetedResources)) continue;
            EngineSeams.overwritePlan(worker, (AIPLAN)this.plan);
            if (this.plan.lastPreparedJob() != null) {
                Job job = this.plan.lastPreparedJob();
                int key = job.jobCoo().x() << 16 | (job.jobCoo().y() & 0xFFFF);
                int wanted = Math.min(8, Math.max(1, job.jobResourcesNeeded(worker)));
                targetedResources.put(key, targetedResources.getOrDefault(key, 0) + wanted);
            }
        }
    }
}

