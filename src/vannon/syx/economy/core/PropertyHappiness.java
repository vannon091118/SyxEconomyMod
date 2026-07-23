package vannon.syx.economy.core;

import game.battle.div.Div;
import game.boosting.BOOSTABLES;
import game.boosting.BSourceInfo;
import game.boosting.BValue;
import game.boosting.Boostable;
import game.boosting.BoosterValue;
import init.sprite.UI.UI;
import init.type.HCLASS_RACE;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.Induvidual;
import snake2d.LOG;
import snake2d.util.sprite.SPRITE;

/**
 * Phase 2: Eigentums-Glück-Booster.
 *
 * Bürger, die mindestens ein Haus besitzen, bekommen einen
 * konstanten Glücks-Boost (+propertyHappinessBoost × base),
 * der den Reichtums-Booster ergänzt.
 *
 * Pattern identisch zu WealthHappiness: registriert einen
 * BValue.BValueInduOnly beim BOOSTABLES.BEHAVIOUR().HAPPI.
 */
public final class PropertyHappiness {
    private static Boostable registeredOn = null;

    /** Find the Humanoid matching a given Induvidual by searching the roster. */
    private static Humanoid humanOf(Induvidual indu) {
        EconomySim sim = EconomySim.active();
        if (sim == null) return null;
        for (int i = 0; i < sim.roster().size(); ++i) {
            Humanoid h = sim.roster().get(i);
            if (h.indu() == indu) return h;
        }
        return null;
    }

    public static void register() {
        if (!EconConfig.propertyMarketEnabled || !EconConfig.homePurchaseEnabled) {
            return;
        }
        Boostable happi = BOOSTABLES.BEHAVIOUR().HAPPI;
        if (registeredOn == happi) {
            return;
        }
        registeredOn = happi;

        BValue.BValueInduOnly owned = new BValue.BValueInduOnly() {
            public double vGet(Induvidual indu) {
                Humanoid h = humanOf(indu);
                if (h == null) return 0.0;
                EconomySim sim = EconomySim.active();
                if (sim == null) return 0.0;
                PropertyLedger ledger = sim.housingMarket().ledger();
                if (ledger == null) return 0.0;
                return ledger.isHomeOwner((long) h.id()) ? 1.0 : 0.0;
            }

            public double vGet(Div div) {
                return 0.0;
            }

            public double vGet(HCLASS_RACE group) {
                EconomySim sim = EconomySim.active();
                if (sim == null) return 0.0;
                PropertyLedger ledger = sim.housingMarket().ledger();
                if (ledger == null) return 0.0;
                double total = 0.0;
                int count = 0;
                for (int i = 0; i < sim.roster().size(); ++i) {
                    Humanoid h = sim.roster().get(i);
                    if (group.cl != null && h.indu().clas() != group.cl) continue;
                    if (group.race != null && h.indu().race() != group.race) continue;
                    total += ledger.isHomeOwner((long) h.id()) ? 1.0 : 0.0;
                    count++;
                }
                return count == 0 ? 0.0 : total / (double) count;
            }
        };

        new BoosterValue(
            (BValue) owned,
            new BSourceInfo((CharSequence) EconTexts.¤¤boostProperty, (SPRITE) UI.icons().s.money),
            1.0,
            1.0 + EconConfig.propertyHappinessBoost,
            true
        ).add(BOOSTABLES.BEHAVIOUR().HAPPI);

        LOG.ln("[ECON] property ownership -> happiness booster registered (+" + EconConfig.propertyHappinessBoost + "x for homeowners)");
    }

    private PropertyHappiness() {}
}
