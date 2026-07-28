package vannon.syx.economy.core;

import game.battle.div.Div;
import game.boosting.BOOSTABLES;
import game.boosting.BSourceInfo;
import game.boosting.BValue;
import game.boosting.Boostable;
import game.boosting.BoosterValue;
import game.faction.npc.FactionNPC;
import game.faction.player.Player;
import game.faction.royalty.Royalty;
import init.sprite.UI.UI;
import init.type.HCLASS_RACE;
import settlement.stats.Induvidual;
import snake2d.util.sprite.SPRITE;
import vannon.syx.economy.core.EconConfig;
import world.map.regions.Region;

public final class InflationOff {
    private static Boostable registeredOn = null;
    private static final double KILL_FACTOR = 1000000.0;

    public static void register() {
        if (!EconConfig.disableVanillaInflation) {
            return;
        }
        Boostable deflation = BOOSTABLES.CIVICS().DEFALTION;
        if (registeredOn == deflation) {
            return;
        }
        registeredOn = deflation;
        BValue v = new BValue(){

            public double vGet(Player f) {
                return 1.0;
            }

            public double vGet(FactionNPC f) {
                return 0.0;
            }

            public double vGet(Region reg) {
                return this.vGet(reg.faction());
            }

            public double vGet(Induvidual i) {
                return this.vGet(i.faction());
            }

            public double vGet(Div div) {
                return this.vGet(div.faction());
            }

            public double vGet(Royalty roy) {
                return 0.0;
            }

            public double vGet(HCLASS_RACE r) {
                return 0.0;
            }
        };
        new BoosterValue(v, new BSourceInfo((CharSequence)EconTexts.¤¤boosterInflationOff, (SPRITE)UI.icons().s.money), 1.0, 1000000.0, true).add(deflation);
        EventLog.log("SYSTEM", "vanilla treasury inflation disabled for the player (DEFLATION x1000000)");
    }

    private InflationOff() {
    }
}

