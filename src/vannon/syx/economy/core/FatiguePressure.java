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
 * L-02: Registriert einen STAMINA-Booster über BOOSTABLES.PHYSICS().STAMINA.
 * Bürger mit hoher Fatigue erleiden einen Malus auf die Ausdauer (bis 0.5×),
 * was die Engine-AI-Priorität für Arbeitsaufgaben reduziert (indirekte forcedRest).
 *
 * <p>Fatigue wird pro Tick in {@link Wallets#updateFatigue(Roster)} aktualisiert.
 * Dieser Booster liest den aktuellen Fatigue-Wert und gibt einen linearen
 * Multiplikator zurück: fatigue=0 → 1.0×, fatigue=threshold → fatigueStaminaMin.</p>
 *
 * <p>Pattern: identisch zu {@link HealthPressure} (BValue lambda + BoosterValue).</p>
 */
public final class FatiguePressure {
    private static Boostable registeredOn = null;

    public static void register() {
        if (!EconConfig.fatigueEnabled) return;
        Boostable stamina = BOOSTABLES.PHYSICS().STAMINA;
        if (registeredOn == stamina) return;
        registeredOn = stamina;

        BValue.BValueInduOnly pressure = new BValue.BValueInduOnly() {
            public double vGet(Induvidual indu) {
                if (!EconConfig.fatigueEnabled) return 1.0;
                EconomySim sim = EconomySim.active();
                if (sim == null) return 1.0;
                int fatigue = sim.wallets().getFatigue(indu);
                int threshold = EconConfig.fatigueRestThreshold;
                if (threshold <= 0 || fatigue <= 0) return 1.0;
                double ratio = Math.min(1.0, (double) fatigue / (double) threshold);
                double min = EconConfig.fatigueStaminaMin;
                return Math.max(min, 1.0 - ratio * (1.0 - min));
            }

            public double vGet(Div div) { return 1.0; }
            public double vGet(HCLASS_RACE group) { return 1.0; }
        };

        new BoosterValue(
            (BValue) pressure,
            new BSourceInfo((CharSequence) "Erschöpfung", (SPRITE) UI.icons().s.human),
            EconConfig.fatigueStaminaMin,
            1.0,
            true
        ).add(BOOSTABLES.PHYSICS().STAMINA);

        if (EconConfig.debugLoggingEnabled) {
            EventLog.log("SYSTEM", "fatigue pressure -> stamina booster registered ("
                + EconConfig.fatigueStaminaMin + "x .. 1.0x)");
        }
    }

    private FatiguePressure() {}
}
