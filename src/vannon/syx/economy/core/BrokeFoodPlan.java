package vannon.syx.economy.core;

import init.type.CAUSE_LEAVES;
import init.type.NEEDS;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AI;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.AISUB;
import settlement.stats.Induvidual;
import util.data.INT_O;

public final class BrokeFoodPlan
extends AIPLAN.PLANRES {
    private final AIPLAN.PLANRES.Resumer starving = new AIPLAN.PLANRES.Resumer("starving"){

        protected AISUB.AISubActivation setAction(Humanoid humanoid, AIManager manager) {
            BrokeFoodPlan.markStarvedIfLethal(humanoid);
            return AI.SUBS().desperate.activate(humanoid, manager);
        }

        protected AISUB.AISubActivation res(Humanoid humanoid, AIManager manager) {
            BrokeFoodPlan.markStarvedIfLethal(humanoid);
            return null;
        }

        public boolean con(Humanoid humanoid, AIManager manager) {
            return true;
        }

        public void can(Humanoid humanoid, AIManager manager) {
        }
    };

    public BrokeFoodPlan() {
        super("ECON_BROKE_FOOD");
    }

    protected AISUB.AISubActivation init(Humanoid humanoid, AIManager manager) {
        return this.starving.set(humanoid, manager);
    }

    static void markStarvedIfLethal(Humanoid humanoid) {
        // INT_OE<Induvidual> type confirmed from vanilla StatsNeeds constructor (DataByte/DataNibble param)
        @SuppressWarnings("unchecked")
        INT_O.INT_OE<Induvidual> hunger = (INT_O.INT_OE<Induvidual>) (Object) NEEDS.TYPES().HUNGER.stat().stat().indu();
        if (hunger.isMax(humanoid.indu())) {
            // v1.7.2-Fix: Starvation war der einzige permanente Effekt ohne EventLog-Eintrag.
            // Kein Crash, kein UI-Feedback — der Bürger verschwand einfach still.
            // Jetzt geloggt, damit die Frage "liegt das an meiner Preispolitik?"
            // im Wirtschaftsfenster nachvollziehbar ist.
            EventLog.log("STARVATION", "Citizen starved (hunger at max). Check food affordability gate and grain dole coverage.");
            AIManager.dead = CAUSE_LEAVES.STARVED();
        }
    }
}

