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
                // Sprint v0.13.108+StartingFromGround: harter Migrations-Cap.
                // Bei Pop >= Cap wird der tanh-Booster neutralisiert (return 0.5),
                // sodass die Vanilla-Engine bis Cap-Anhebung via UI keine neuen
                // Bürger akzeptiert. Pop-Quelle: EconConfig.population (live, durch
                // EconomySim.update() aktualisiert via T8).
                if (EconConfig.population >= EconConfig.meticImmigrationCap) {
                    return 0.5;
                }
                int m = sim.taxes().foreignTaxModifier();
                double s = EconConfig.meticImmigrationSteepness;
                if (s <= 0.0) {
                    return 0.5;
                }
                return (1.0 + Math.tanh((double)(-m) / s)) / 2.0;
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

