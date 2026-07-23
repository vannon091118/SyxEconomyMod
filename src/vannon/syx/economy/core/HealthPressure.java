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
import snake2d.util.sprite.SPRITE;

/**
 * Registriert einen Gesundheit-Booster über BOOSTABLES.PHYSICS().HEALTH.
 * Arme und arbeitslose Bürger erleiden einen leichten Malus auf die Gesundheit (0.85x),
 * während beschäftigte oder vermögende Bürger den vollen Wert (1.0x) behalten.
 */
public final class HealthPressure {
    private static Boostable registeredOn = null;

    public static void register() {
        if (!EconConfig.povertyPressureEnabled) return;
        Boostable health = BOOSTABLES.PHYSICS().HEALTH;
        if (registeredOn == health) return;
        registeredOn = health;

        BValue.BValueInduOnly pressure = new BValue.BValueInduOnly() {
            public double vGet(Induvidual indu) {
                if (!EconConfig.povertyPressureEnabled) return 1.0;
                EconomySim sim = EconomySim.active();
                if (sim == null) return 1.0;
                int wealth = sim.wallets().moneyOf(indu);
                if (wealth >= EconConfig.povertyPressureWealthThreshold) return 1.0;
                if (STATS.WORK().EMPLOYED.get(indu) != null) return 1.0;
                return 0.85;
            }

            public double vGet(Div div) { return 1.0; }
            public double vGet(HCLASS_RACE group) { return 1.0; }
        };

        new BoosterValue(
            (BValue) pressure,
            new BSourceInfo((CharSequence) EconTexts.¤¤boostPoverty, (SPRITE) UI.icons().s.heart),
            0.85,
            1.0,
            true
        ).add(BOOSTABLES.PHYSICS().HEALTH);

        if (EconConfig.debugLoggingEnabled) {
            EventLog.log("SYSTEM", "poverty pressure -> health booster registered (0.85x .. 1.0x)");
        }
    }

    private HealthPressure() {}
}
