package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.Faction;
import game.faction.diplomacy.DIP;
import game.faction.npc.FactionNPC;
import init.resources.RESOURCE;
import world.region.RD;

public final class PolityPriceAnchor {
    public static int priceOf(RESOURCE resource) {
        int best = Integer.MAX_VALUE;
        if (DIP.traders().size() == 0) {
            for (FactionNPC faction : RD.DIST().neighs()) {
                best = PolityPriceAnchor.consider(best, faction, resource);
            }
        } else {
            for (Faction faction : DIP.traders()) {
                if (!(faction instanceof FactionNPC)) continue;
                FactionNPC npc = (FactionNPC)faction;
                best = PolityPriceAnchor.consider(best, npc, resource);
            }
        }
        if (best != Integer.MAX_VALUE) {
            return best;
        }
        return FACTIONS.PRICE().get(resource.tr());
    }

    private static int consider(int current, FactionNPC faction, RESOURCE resource) {
        if (!faction.isActive() || faction.capitolRegion() == null) {
            return current;
        }
        int quote = faction.res(resource.tr()).priceSellP();
        return quote > 0 ? Math.min(current, quote) : current;
    }

    private PolityPriceAnchor() {
    }
}

