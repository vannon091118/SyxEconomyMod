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

/**
 * L-01 (v0.13.67): BrokeFoodPlan Oddjob Escape.
 * <p>Wenn ein Bürger sich Essen nicht leisten kann, wird NICHT sofort
 * der desperate-Status aktiviert. Solange der Hunger nicht maximal ist,
 * bleibt der Bürger in der normalen AI-Pipeline — diese prüft automatisch
 * Oddjob-Arbeit (PlanOddjobber) und andere Einkommensquellen vor dem
 * Verhungern. Erst wenn der Hunger MAXIMAL ist UND keine Arbeit half,
 * wird der desperate-Status aktiviert (Tod durch Verhungern).</p>
 */

public final class BrokeFoodPlan
extends AIPLAN.PLANRES {
    /** Gecachter Hunger-INT_O — vermeidet Chains wie TYPES().HUNGER.stat().stat().indu() pro AI-Tick. */
    @SuppressWarnings("unchecked")
    private static final INT_O.INT_OE<Induvidual> HUNGER = (INT_O.INT_OE<Induvidual>) (Object) NEEDS.TYPES().HUNGER.stat().stat().indu();

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
            // L-01: Verhungern NUR wenn Hunger bereits maximal.
            return HUNGER.isMax(humanoid.indu());
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
        if (HUNGER.isMax(humanoid.indu())) {
            // v1.7.2-Fix: Starvation war der einzige permanente Effekt ohne EventLog-Eintrag.
            // Kein Crash, kein UI-Feedback — der Bürger verschwand einfach still.
            // Jetzt geloggt, damit die Frage "liegt das an meiner Preispolitik?"
            // im Wirtschaftsfenster nachvollziehbar ist.
            EventLog.log("STARVATION", "Citizen starved (hunger at max). Check food affordability gate and grain dole coverage.");
            AIManager.dead = CAUSE_LEAVES.STARVED();
        }
    }
}

