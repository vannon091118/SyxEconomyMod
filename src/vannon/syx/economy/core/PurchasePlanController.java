package vannon.syx.economy.core;

import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.HAI;
import vannon.syx.economy.adapter.ISyxAI;
import vannon.syx.economy.core.AffordabilityGate;
import vannon.syx.economy.core.DrinkTransactionPlan;
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.IHumanoidAccess;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EngineSeams;
import vannon.syx.economy.core.GoodsTransactionPlan;
import vannon.syx.economy.core.Roster;

public final class PurchasePlanController {
    private final DrinkTransactionPlan drink;
    private final GoodsTransactionPlan goods;
    private final ISyxAI ai;
    /** v1.7.2 Perf: Round-Robin-Sharding — nur 1/shardCount des Rosters pro Tick,
     *  aber ueber shardCount Ticks wird jeder Buerger einmal geprueft.
     *  shardCount=1 deaktiviert Sharding (voller Scan wie vorher). */
    private int tickCounter = 0;
    private final int shardCount;

    public PurchasePlanController(AffordabilityGate gate, ISyxAI ai) {
        this.drink = new DrinkTransactionPlan(gate);
        this.goods = new GoodsTransactionPlan(gate);
        this.shardCount = Math.max(1, EconConfig.planControllerShardCount);
        this.ai = ai;
    }

    public void update(Roster roster) {
        if (!EconConfig.consumptionGateEnabled) {
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
        AIManager manager;
        Humanoid humanoid = roster.get(i);
        HAI hAI = humanoid.ai();
        if (!(hAI instanceof AIManager)
            || (manager = (AIManager)hAI).plan() == null
            || manager.plan() == this.drink
            || manager.plan() == this.goods)
            return;
        AIPLAN current = manager.plan();
        if (this.ai.isTavernPlan(current)) {
            IHumanoidAccess hum = EngineMirror.api() != null ? EngineMirror.api().humanoids() : null;
            if (hum != null) hum.overwritePlan(humanoid, (AIPLAN)this.drink); else EngineSeams.overwritePlan(humanoid, (AIPLAN)this.drink);
            return;
        }
        if (!this.ai.isMarketPlan(current)) return;
        IHumanoidAccess hum2 = EngineMirror.api() != null ? EngineMirror.api().humanoids() : null;
        if (hum2 != null) hum2.overwritePlan(humanoid, (AIPLAN)this.goods); else EngineSeams.overwritePlan(humanoid, (AIPLAN)this.goods);
    }
}
