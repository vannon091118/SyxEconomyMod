package vannon.syx.economy.core;

import game.battle.div.Div;
import game.boosting.BOOSTABLES;
import game.boosting.BSourceInfo;
import game.boosting.BValue;
import game.boosting.Boostable;
import game.boosting.BoosterValue;
import init.sprite.UI.UI;
import init.type.HCLASS_RACE;
import settlement.stats.Induvidual;
import snake2d.util.sprite.SPRITE;

/**
 * Binds the Gini coefficient to BOOSTABLES.BEHAVIOUR().LOYALTY.
 * Acts group-wide (not per individual like WealthHappiness) —
 * a visibly divided settlement undermines loyalty even for
 * the relatively well-off. Per vanilla docs, Loyalty < 100%
 * increases riot probability — this chain triggers a real,
 * documented in-game consequence (unrest), not just a dashboard number.
 */
public final class GiniConsequences {

    private static Boostable registeredOn = null;
    private static int lastWarnSeason = -1;

    public static void register() {
        if (!EconConfig.giniAffectsLoyalty) return;
        Boostable loyalty = BOOSTABLES.BEHAVIOUR().LOYALTY;
        if (registeredOn == loyalty) return;
        registeredOn = loyalty;

        BValue.BValueInduOnly unrest = new BValue.BValueInduOnly() {
            public double vGet(Induvidual indu)   { return currentGini(); }
            public double vGet(Div div)           { return currentGini(); }
            public double vGet(HCLASS_RACE group)  { return currentGini(); }
        };

        // gini 0.0 (perfect equality) -> 1.0x (no effect)
        // gini 1.0 (max inequality) -> EconConfig.loyaltyAtMaxGini (penalty)
        new BoosterValue(
            (BValue) unrest,
            new BSourceInfo((CharSequence) EconTexts.¤¤boostInequality, (SPRITE) UI.icons().s.money),
            1.0, EconConfig.loyaltyAtMaxGini, true
        ).add(loyalty);

        EventLog.log("SYSTEM", "Gini -> loyalty booster registered (1.0x .. "
            + EconConfig.loyaltyAtMaxGini + "x)");
    }

    private static double currentGini() {
        EconomySim sim = EconomySim.active();
        return sim == null ? 0.0 : sim.stats().gini;
    }

    /** Called once per season from EconomySim.update() -- makes the effect visible in the chronicle. */
    public static void announceIfCrossed(EconSnapshot snap, int season) {
        if (!EconConfig.giniAffectsLoyalty || season == lastWarnSeason) return;
        lastWarnSeason = season;
        if (snap.gini > EconIndicators.GINI_WARNING) {
            EventLog.log("UNREST", String.format(
                "Growing inequality (Gini %.2f) undermines population loyalty.", snap.gini));
        }
    }

    private GiniConsequences() {}
}