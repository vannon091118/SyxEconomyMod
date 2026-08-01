package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.Faction;
import game.faction.diplomacy.DIP;
import game.faction.npc.FactionNPC;
import init.resources.RESOURCE;

/**
 * Provides trade-partner-only price quotes for resource anchoring.
 *
 * <p>Only actual trade partners ({@code DIP.traders()}) contribute to the
 * returned price. Neighboring factions without a trade agreement are ignored —
 * this prevents the old behavior where {@code RD.DIST().neighs()} let every
 * neighboring faction's sell-price leak into the player's economy before any
 * trade route was established.</p>
 *
 * <p>When no trade partners exist, returns {@code 0}. Callers (especially
 * {@link EconomyAuditEngine#refreshFlowPrices}) must handle this by falling
 * back to local pricing (vanilla base price or FlowPrices default anchor).</p>
 *
 * <p>Design contract: this class ONLY returns prices from real trade partners.
 * It does NOT fall back to {@code FACTIONS.PRICE()} (global vanilla price
 * registry). The decision about what to use when no partner exists belongs
 * to the caller, not to this class.</p>
 */
public final class PolityPriceAnchor {

    /**
     * Returns the best (lowest) sell-price for the given resource across all
     * active trade partners, or {@code 0} if no trade partner is available.
     *
     * <p>Returning 0 signals to callers that no external price anchor exists —
     * they should fall back to local pricing logic (e.g. vanilla base price
     * from {@code FACTIONS.PRICE()} or FlowPrices default).</p>
     *
     * @param resource the resource to price
     * @return best trade-partner sell-price, or 0 if no partners
     */
    public static int priceOf(RESOURCE resource) {
        int best = Integer.MAX_VALUE;

        // Only consider actual trade partners (DIP.traders()) — neighbors
        // without a trade agreement must NOT influence local market prices.
        for (Faction faction : DIP.traders()) {
            if (!(faction instanceof FactionNPC)) continue;
            FactionNPC npc = (FactionNPC) faction;
            best = consider(best, npc, resource);
        }

        // No trade partner found → return 0 (no external anchor).
        // Callers decide the fallback (typically vanilla base price).
        if (best == Integer.MAX_VALUE) {
            return 0;
        }
        return best;
    }

    /**
     * Returns true if at least one active trade partner currently exists
     * (i.e. {@code DIP.traders()} is non-empty with at least one active NPC).
     * Useful for UI indicators and gating price-convergence behavior.
     */
    public static boolean hasTradePartner() {
        for (Faction faction : DIP.traders()) {
            if (faction instanceof FactionNPC npc && isTradeable(npc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ein NPC-Faction ist ein gueltiger Trade-Partner genau dann wenn er aktiv ist
     * UND eine Capitol-Region hat. Wird von {@link #hasTradePartner()} (Gate-Check
     * fuer Once-Only-Bump in {@code EconomySim.applyEarlyPhaseBumpRules}) und von
     * {@link #consider(int, FactionNPC, RESOURCE)} (Preis-Lookup in
     * {@link #priceOf(RESOURCE)}) gemeinsam genutzt — damit beide Methoden
     * konsistent entscheiden, ob ein NPC tatsaechlich zum Pricing beitragen kann.
     * Sprint v0.13.107+: extrahiert aus hasTradePartner() und consider() zur
     * Vereinheitlichung der Active-Check-Logik.
     */
    private static boolean isTradeable(FactionNPC npc) {
        return npc.isActive() && npc.capitolRegion() != null;
    }

    private static int consider(int current, FactionNPC faction, RESOURCE resource) {
        if (!isTradeable(faction)) {
            return current;
        }
        int quote = faction.res(resource.tr()).priceSellP();
        return quote > 0 ? Math.min(current, quote) : current;
    }

    private PolityPriceAnchor() {
    }
}
