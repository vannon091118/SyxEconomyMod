package vannon.syx.economy.core;

import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.AISUB;

public final class BrokeServicePlan
extends AIPLAN.PLANRES {
    public BrokeServicePlan() {
        super("ECON_CANNOT_AFFORD_SERVICE");
    }

    protected AISUB.AISubActivation init(Humanoid humanoid, AIManager manager) {
        return this.WAIT_AND_EXIT.set(humanoid, manager);
    }
}

