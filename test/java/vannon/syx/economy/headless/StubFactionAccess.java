package vannon.syx.economy.headless;

import game.faction.Faction;
import game.faction.npc.FactionNPC;
import game.faction.player.Player;
import game.faction.royalty.Royalty;
import init.resources.RESOURCE;
import snake2d.util.sets.LIST;
import vannon.syx.economy.adapter.IFactionAccess;

import java.util.Collections;

/**
 * Headless stub for {@link IFactionAccess}. Single-player polity: every
 * NPC-related method returns a safe default, while player-side queries
 * ({@link #getPlayerCitizens()}, {@link #getPlayerCredits()}) reflect
 * {@link MockWorldState}.
 */
public final class StubFactionAccess implements IFactionAccess {

    private final MockWorldState state;

    public StubFactionAccess(MockWorldState state) { this.state = state; }

    @Override public boolean isAvailable() { return true; }

    // ── NPC ────────────────────────────────────────────────
    @Override public int getNpcCount() { return 0; }
    @Override public FactionNPC getNpc(int index) { return null; }
    @Override public LIST<FactionNPC> getActiveNpcs() {
        // Stub world has no NPC factions — null is the safe value.
        return null;
    }
    @Override public int getNpcSellPrice(String resourceKey) { return 0; }
    @Override public void setNpcSellPrice(String resourceKey, int price) { /* no-op */ }
    @Override public int getNpcBuyPrice(String resourceKey) { return 0; }
    @Override public void setNpcBuyPrice(String resourceKey, int price) { /* no-op */ }
    @Override public double getNpcTreasury() { return 0.0; }
    @Override public void incNpcTreasury(double amount, String rtypeName) { /* no-op */ }
    @Override public String getNpcRace(FactionNPC npc) { return ""; }
    @Override public int getNpcCitizens(FactionNPC npc) { return 0; }
    @Override public double getNpcMilitaryPower(FactionNPC npc) { return 0.0; }
    @Override public int getNpcIteration(FactionNPC npc) { return 0; }

    // ── Diplomacy ──────────────────────────────────────────
    @Override public double getPlayerPower() { return 0.0; }
    @Override public double getCoalitionPower() { return 0.0; }
    @Override public double getCoalitionAdvantage() { return 0.0; }
    @Override public void setWarNumericState(int updateIndex, double playerPower,
                                              double coalitionPower) { /* no-op */ }
    @Override public double getDistress(Faction faction) { return 0.0; }
    @Override public int getWillingCount() { return 0; }
    @Override public int getPotentialCount() { return 0; }
    @Override public int getProxyCount() { return 0; }

    // ── Trade ──────────────────────────────────────────────
    @Override public int getWorldPrice(RESOURCE resource) {
        // Headless stub: no resource table — 0 for unknown resources.
        return 0;
    }
    @Override public double getTradeToll(int fromFactionIndex, int toFactionIndex) { return 0.0; }
    @Override public double getTradeTariff(int fromFactionIndex, int toFactionIndex) { return 0.0; }

    // ── Royalty ────────────────────────────────────────────
    @Override public Royalty     getKing(FactionNPC npc) { return null; }
    @Override public CharSequence getRulerName(FactionNPC npc) { return "?"; }

    // ── Opinion / Trust ─────────────────────────────────────
    @Override public double getFactionOpinion(FactionNPC npc) { return 0.0; }
    @Override public double getFactionTrust(FactionNPC npc) { return 0.5; }
    @Override public void adjustFactionOpinion(FactionNPC npc, double delta) { /* no-op */ }

    // ── Player ─────────────────────────────────────────────
    @Override public Player getPlayer() { return null; }   // no real Player
    @Override public double getPlayerCredits() { return state.treasury(); }
    @Override public int    getPlayerCitizens() { return state.citizenCount; }
}
