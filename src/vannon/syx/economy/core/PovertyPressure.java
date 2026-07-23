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
import settlement.stats.STATS;
import snake2d.LOG;
import snake2d.util.sprite.SPRITE;

/**
 * Bestraft arbeitslose Bürger mit niedrigem Vermögen über einen
 * Happiness-Malus (BOOSTABLES.BEHAVIOUR().HAPPI).
 * 
 * Ohne diesen Druck bleiben Bürger mit foodAffordabilityGateEnabled=false
 * dauerhaft zuhause — sie essen gratis, kriegen HandoutRelief, und haben
 * keinen Überlebensdruck, Arbeit zu suchen.
 * 
 * Der Booster gibt 50% Happiness bei vermögenslos + arbeitslos,
 * 100% Happiness bei vermögend ODER beschäftigt.
 */
public final class PovertyPressure {
    private static Boostable registeredOn = null;

    public static void register() {
        if (!EconConfig.povertyPressureEnabled) return;
        Boostable happi = BOOSTABLES.BEHAVIOUR().HAPPI;
        if (registeredOn == happi) return;
        registeredOn = happi;

        BValue.BValueInduOnly pressure = new BValue.BValueInduOnly() {
            public double vGet(Induvidual indu) {
                if (!EconConfig.povertyPressureEnabled) return 1.0;
                EconomySim sim = EconomySim.active();
                if (sim == null) return 1.0;
                int wealth = sim.wallets().moneyOf(indu);
                // Über der Schwelle → kein Malus
                if (wealth >= EconConfig.povertyPressureWealthThreshold) return 1.0;
                // Hat einen Job → kein Malus (arbeitet ja)
                if (STATS.WORK().EMPLOYED.get(indu) != null) return 1.0;
                // Arm UND arbeitslos → voller Malus
                return 0.0;
            }

            public double vGet(Div div) { return 1.0; }
            public double vGet(HCLASS_RACE group) { return 1.0; }
        };

        new BoosterValue(
            (BValue) pressure,
            new BSourceInfo((CharSequence) EconTexts.¤¤boostPoverty, (SPRITE) UI.icons().s.money),
            EconConfig.povertyPressureHappinessMin,
            1.0,
            true
        ).add(BOOSTABLES.BEHAVIOUR().HAPPI);

        LOG.ln("[ECON] poverty pressure -> happiness booster registered ("
            + EconConfig.povertyPressureHappinessMin + "x unemployed-poor .. 1.0x employed-or-rich)");
    }

    private PovertyPressure() {}
}
