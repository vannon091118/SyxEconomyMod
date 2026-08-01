package vannon.syx.economy.core;

import game.battle.div.Div;
import game.boosting.BOOSTABLES;
import game.boosting.BSourceInfo;
import game.boosting.BValue;
import game.boosting.Boostable;
import game.boosting.BoosterValue;
import game.faction.FACTIONS;
import game.faction.npc.FactionNPC;
import game.faction.player.Player;
import game.faction.royalty.Royalty;
import init.sprite.UI.UI;
import init.type.HCLASS_RACE;
import settlement.stats.Induvidual;
import snake2d.LOG;
import snake2d.util.sprite.SPRITE;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.DiagnosticExporter;
import world.map.regions.Region;

public final class MeticImmigration {
    private static Boostable registeredOn = null;

    public static void register() {
        Boostable immigration = BOOSTABLES.CIVICS().IMMIGRATION;
        if (registeredOn == immigration) {
            return;
        }
        registeredOn = immigration;
        double k = EconConfig.meticImmigrationDepth;
        BValue v = new BValue(){

            public double vGet(HCLASS_RACE reg) {
                if (reg == null || reg.race == null) {
                    return 0.5;
                }
                if (reg.race == FACTIONS.player().race()) {
                    return 0.5;
                }
                EconomySim sim = EconomySim.active();
                if (sim == null) {
                    return 0.5;
                }
                int m = sim.taxes().foreignTaxModifier();
                double s = EconConfig.meticImmigrationSteepness;
                double boosterRaw = (s > 0.0)
                        ? (1.0 + Math.tanh((double)(-m) / s)) / 2.0
                        : 0.5;
                // Sprint v0.13.108+StartingFromGround: harter Migrations-Cap.
                boolean capHit = EconConfig.population >= EconConfig.meticImmigrationCap;
                double boosterFinal = capHit ? 0.5 : boosterRaw;
                // Sprint v0.13.130+MeticImmigrationDebug: pro-Tick CSV-Diagnostik
                // (1× pro Tick via DiagnosticExporter Throttle, nicht pro Race)
                long tick = sim.ticks();
                long day = tick / (long) EconConfig.DEFAULT_TICKS_PER_DAY;
                DiagnosticExporter.appendImmigrationRow(
                        day, tick,
                        EconConfig.population, EconConfig.meticImmigrationCap,
                        sim.stats() != null ? sim.stats().median : 0,
                        sim.stats() != null ? sim.stats().mean : 0.0,
                        m, boosterRaw, boosterFinal);
                return boosterFinal;
            }

            public double vGet(Induvidual indu) {
                return 0.5;
            }

            public double vGet(Region reg) {
                return 0.5;
            }

            public double vGet(Div div) {
                return 0.5;
            }

            public double vGet(Player f) {
                return 0.5;
            }

            public double vGet(FactionNPC f) {
                return 0.5;
            }

            public double vGet(Royalty roy) {
                return 0.5;
            }
        };
        new BoosterValue(v, new BSourceInfo((CharSequence)EconTexts.¤¤boosterMeticTax, (SPRITE)UI.icons().s.money), 1.0 - k, 1.0 + k, true).add(immigration);
        LOG.ln("[ECON] metic tax -> immigration booster registered (x" + (1.0 - k) + " .. x" + (1.0 + k) + ", steepness " + EconConfig.meticImmigrationSteepness + ")");
    }

    private MeticImmigration() {
    }
}

