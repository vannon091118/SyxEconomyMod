package vannon.syx.economy.core;

import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.HAI;
import vannon.syx.economy.core.AffordabilityGate;
import vannon.syx.economy.core.DrinkTransactionPlan;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EngineSeams;
import vannon.syx.economy.core.GoodsTransactionPlan;
import vannon.syx.economy.core.Roster;

public final class PurchasePlanController {
    private final DrinkTransactionPlan drink;
    private final GoodsTransactionPlan goods;
    /** v1.7.2 Perf: Round-Robin-Sharding — nur 1/shardCount des Rosters pro Tick,
     *  aber ueber shardCount Ticks wird jeder Buerger einmal geprueft.
     *  shardCount=1 deaktiviert Sharding (voller Scan wie vorher). */
    private int tickCounter = 0;
    private final int shardCount;

    public PurchasePlanController(AffordabilityGate gate) {
        this.drink = new DrinkTransactionPlan(gate);
        this.goods = new GoodsTransactionPlan(gate);
        this.shardCount = Math.max(1, EconConfig.planControllerShardCount);
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
        if (EngineSeams.isTavernPlan(current)) {
            EngineSeams.overwritePlan(humanoid, (AIPLAN)this.drink);
            return;
        }
        if (!EngineSeams.isMarketPlan(current)) return;
        EngineSeams.overwritePlan(humanoid, (AIPLAN)this.goods);
    }
}
