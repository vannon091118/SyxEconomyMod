package vannon.syx.economy.core;

import game.battle.div.Div;
import game.boosting.BOOSTABLES;
import game.boosting.BSourceInfo;
import game.boosting.BValue;
import game.boosting.Boostable;
import game.boosting.BoosterValue;
import init.sprite.UI.UI;
import init.type.HCLASS_RACE;
import java.util.function.ToDoubleFunction;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.Induvidual;
import snake2d.LOG;
import snake2d.util.sprite.SPRITE;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.Roster;

public final class WealthHappiness {
    private static Boostable registeredOn = null;

    public static void register() {
        if (!EconConfig.wealthAffectsHappiness) {
            return;
        }
        Boostable happi = BOOSTABLES.BEHAVIOUR().HAPPI;
        if (registeredOn == happi) {
            return;
        }
        registeredOn = happi;
        BValue.BValueInduOnly wealth = new BValue.BValueInduOnly(){

            public double vGet(Induvidual indu) {
                EconomySim sim = EconomySim.active();
                if (sim == null) {
                    return 0.5;
                }
                return sim.relativeWealth(indu);
            }

            public double vGet(Div div) {
                return 0.5;
            }

            public double vGet(HCLASS_RACE group) {
                return WealthHappiness.populationAverage(group, 0.5, indu -> EconomySim.active().relativeWealth((Induvidual)indu));
            }
        };
        new BoosterValue((BValue)wealth, new BSourceInfo((CharSequence)EconTexts.¤¤boostWealth, (SPRITE)UI.icons().s.money), EconConfig.happinessAtPoorest, EconConfig.happinessAtRichest, true).add(BOOSTABLES.BEHAVIOUR().HAPPI);
        WealthHappiness.registerTaxPenalty();
        LOG.ln("[ECON] wealth -> happiness booster registered (" + EconConfig.happinessAtPoorest + "x poorest .. " + EconConfig.happinessAtRichest + "x richest)");
    }

    private static void registerTaxPenalty() {
        BValue.BValueInduOnly taxed = new BValue.BValueInduOnly(){

            public double vGet(Induvidual indu) {
                EconomySim sim = EconomySim.active();
                if (sim == null) {
                    return 0.0;
                }
                return sim.taxPain(indu);
            }

            public double vGet(Div div) {
                return 0.0;
            }

            public double vGet(HCLASS_RACE group) {
                return WealthHappiness.populationAverage(group, 0.0, indu -> EconomySim.active().taxPain((Induvidual)indu));
            }
        };
        new BoosterValue((BValue)taxed, new BSourceInfo((CharSequence)EconTexts.¤¤boostTaxes, (SPRITE)UI.icons().s.money), 1.0, EconConfig.taxHappinessAtFullRate, true).add(BOOSTABLES.BEHAVIOUR().HAPPI);
    }

    private static double populationAverage(HCLASS_RACE group, double neutral, ToDoubleFunction<Induvidual> value) {
        EconomySim sim = EconomySim.active();
        if (sim == null) {
            return neutral;
        }
        double total = 0.0;
        int count = 0;
        Roster roster = sim.roster();
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid h = roster.get(i);
            Induvidual indu = h.indu();
            if (group.cl != null && indu.clas() != group.cl || group.race != null && indu.race() != group.race) continue;
            total += value.applyAsDouble(indu);
            ++count;
        }
        return count == 0 ? neutral : total / (double)count;
    }

    private WealthHappiness() {
    }
}

