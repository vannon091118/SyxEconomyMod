package vannon.syx.economy.core;

import init.type.NEEDS;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.HAI;
import vannon.syx.economy.core.AffordabilityGate;
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.IHumanoidAccess;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.FoodTransactionPlan;
import vannon.syx.economy.core.Roster;

public final class FoodPlanController {
    private final FoodTransactionPlan plan;
    /** v1.7.2 Perf: Round-Robin-Sharding — nur 1/shardCount des Rosters pro Tick,
     *  aber ueber shardCount Ticks wird jeder Buerger einmal geprueft.
     *  shardCount=1 deaktiviert Sharding (voller Scan wie vorher). */
    private int tickCounter = 0;
    private final int shardCount;

    public FoodPlanController(AffordabilityGate gate) {
        this.plan = new FoodTransactionPlan(gate);
        this.shardCount = Math.max(1, EconConfig.planControllerShardCount);
    }

    public void update(Roster roster) {
        if (!EconConfig.foodAffordabilityGateEnabled) {
            return;
        }
        int size = roster.size();
        if (size == 0) return;

        if (this.shardCount <= 1) {
            // Sharding deaktiviert — voller Scan jeden Tick
            for (int i = 0; i < size; ++i) processCitizen(roster, i);
            return;
        }

        // Sharding aktiv — nur 1/shardCount des Rosters pro Tick
        int shardSize = Math.max(1, size / this.shardCount);
        int start = (this.tickCounter % this.shardCount) * shardSize;
        if (start >= size) { this.tickCounter++; return; }
        int end = (this.tickCounter % this.shardCount == this.shardCount - 1)
            ? size : Math.min(size, start + shardSize);
        for (int i = start; i < end; ++i) processCitizen(roster, i);
        this.tickCounter++;
    }

    private void processCitizen(Roster roster, int i) {
        HAI hAI;
        AIManager manager;
        Humanoid humanoid = roster.get(i);
        if (NEEDS.TYPES().HUNGER.stat().getPrio(humanoid) <= 0
            || !((hAI = humanoid.ai()) instanceof AIManager)
            || (manager = (AIManager)hAI).plan() == this.plan
            || manager.plan() == null
            || !EconomySim.active().aiAdapter().isFoodPlan(manager.plan()))
            return;
        IHumanoidAccess hum = EngineMirror.api().humanoids();
        hum.overwritePlan(humanoid, (AIPLAN)this.plan);
    }
}
