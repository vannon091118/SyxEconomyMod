package vannon.syx.economy.adapter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import game.faction.FACTIONS;
import game.faction.Faction;
import game.faction.diplomacy.DIP;
import game.faction.diplomacy.DipWarPlayer;
import game.faction.npc.FactionNPC;
import game.faction.player.Player;
import game.faction.royalty.Royalty;
import game.faction.royalty.opinion.ROPINION;
import game.faction.trade.TradeManager;
import init.resources.RESOURCE;
import init.race.Race;
import snake2d.util.sets.LIST;
import vannon.syx.economy.core.EngineLevers;
import vannon.syx.economy.core.EventLog;
import vannon.syx.economy.core.LoggingAdapter;

/**
 * V71.44-Implementierung von {@link IFactionAccess}.
 *
 * <p>Hybride Architektur — KEIN Reflection, alles Compilezeit:
 * <ul>
 *   <li><b>NPC</b>: {@link ISyxNpc} Adapter für Preise/Treasury (BypassGate),
 *       direkte public API für {@link FactionNPC}-Methoden.</li>
 *   <li><b>Diplomacy</b>: {@link ISyxDiplomacy} Adapter für private Felder,
 *       direkte public API via {@code DIP.WAR_PLAYER()}.</li>
 *   <li><b>Trade</b>: {@code FACTIONS.PRICE()} für Weltmarktpreise,
 *       {@link TradeManager} direkt (public static Methoden).</li>
 *   <li><b>Royalty</b>: Direkte public API via {@code FactionNPC.king()}.</li>
 *   <li><b>Player</b>: Direkte public API via {@code FACTIONS.player()}.</li>
 * </ul></p>
 *
 * <p>Jeder Zugriff prüft {@link EngineLevers} vor der Ausführung und loggt
 * via {@link LoggingAdapter#csvTrace}. Fehler werden pro Methode protokolliert
 * und die Methode dauerhaft deaktiviert.</p>
 */
public final class FactionAccessImpl implements IFactionAccess {

    // ─── Injected Adapters ──────────────────────────────────
    private final ISyxDiplomacy diplomacyAdapter;
    private final ISyxNpc npcAdapter;

    // ─── Status ─────────────────────────────────────────────
    private final boolean initOk;
    private final Set<String> failedMethods = Collections.synchronizedSet(new HashSet<>());

    // ─── Constructor ────────────────────────────────────────

    /**
     * Erzeugt eine neue FactionAccessImpl.
     *
     * @param diplomacyAdapter bestehender ISyxDiplomacy-Adapter (injected)
     * @param npcAdapter       bestehender ISyxNpc-Adapter (injected)
     */
    public FactionAccessImpl(ISyxDiplomacy diplomacyAdapter, ISyxNpc npcAdapter) {
        this.diplomacyAdapter = diplomacyAdapter;
        this.npcAdapter = npcAdapter;

        // initOk: always true — public API + adapters provide fallbacks.
        // No reflection needed: TradeManager is public static, DIP.WAR_PLAYER()
        // is public static, FACTIONS is public static.
        this.initOk = true;

        EventLog.log("SEAM", "FactionAccessImpl: READY (DIP.WAR_PLAYER() + FACTIONS"
                + " + TradeManager direct + ISyxDiplomacy=" + diplomacyAdapter.isAvailable()
                + ", ISyxNpc=" + npcAdapter.isAvailable() + ")");
    }

    // ═══ IFactionAccess Implementation ══════════════════════

    @Override
    public boolean isAvailable() {
        return initOk;
    }

    // ─── NPC ────────────────────────────────────────────────

    @Override
    public int getNpcCount() {
        if (!canAccess("npc_count", true)) return 0;
        try {
            LIST<FactionNPC> npcs = FACTIONS.NPCs();
            int v = npcs != null ? npcs.size() : 0;
            trace("npc_count", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("npc_count", t, 0);
        }
    }

    @Override
    public FactionNPC getNpc(int index) {
        if (!canAccess("npc_get", true)) return null;
        try {
            LIST<FactionNPC> npcs = FACTIONS.NPCs();
            if (npcs == null || index < 0 || index >= npcs.size()) return null;
            FactionNPC v = npcs.get(index);
            trace("npc_get", v != null ? "ok" : "null", "idx=" + index);
            return v;
        } catch (Throwable t) {
            return fail("npc_get", t, null);
        }
    }

    @Override
    public LIST<FactionNPC> getActiveNpcs() {
        if (!canAccess("npc_active", true)) return null;
        try {
            LIST<FactionNPC> v = FACTIONS.NPCs();
            trace("npc_active", v != null ? String.valueOf(v.size()) : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("npc_active", t, null);
        }
    }

    @Override
    public int getNpcSellPrice(String resourceKey) {
        if (!canAccess("npc_sellPrice", EngineLevers.npcPriceReadEnabled)) return 0;
        if (npcAdapter == null || !npcAdapter.isAvailable()) return 0;
        try {
            int v = npcAdapter.getSellPrice(resourceKey);
            trace("npc_sellPrice", String.valueOf(v), resourceKey);
            return v;
        } catch (Throwable t) {
            return fail("npc_sellPrice", t, 0);
        }
    }

    @Override
    public void setNpcSellPrice(String resourceKey, int price) {
        if (!canAccess("npc_setSellPrice", EngineLevers.npcPriceWriteEnabled)) return;
        if (npcAdapter == null || !npcAdapter.isAvailable()) return;
        try {
            npcAdapter.setSellPrice(resourceKey, price);
            trace("npc_setSellPrice", String.valueOf(price), resourceKey);
        } catch (Throwable t) {
            failVoid("npc_setSellPrice", t);
        }
    }

    @Override
    public int getNpcBuyPrice(String resourceKey) {
        if (!canAccess("npc_buyPrice", EngineLevers.npcPriceReadEnabled)) return 0;
        if (npcAdapter == null || !npcAdapter.isAvailable()) return 0;
        try {
            int v = npcAdapter.getBuyPrice(resourceKey);
            trace("npc_buyPrice", String.valueOf(v), resourceKey);
            return v;
        } catch (Throwable t) {
            return fail("npc_buyPrice", t, 0);
        }
    }

    @Override
    public void setNpcBuyPrice(String resourceKey, int price) {
        if (!canAccess("npc_setBuyPrice", EngineLevers.npcPriceWriteEnabled)) return;
        if (npcAdapter == null || !npcAdapter.isAvailable()) return;
        try {
            npcAdapter.setBuyPrice(resourceKey, price);
            trace("npc_setBuyPrice", String.valueOf(price), resourceKey);
        } catch (Throwable t) {
            failVoid("npc_setBuyPrice", t);
        }
    }

    @Override
    public double getNpcTreasury() {
        if (!canAccess("npc_treasury", EngineLevers.npcTreasuryReadEnabled)) return 0.0;
        if (npcAdapter == null || !npcAdapter.isAvailable()) return 0.0;
        try {
            double v = npcAdapter.getTreasury();
            trace("npc_treasury", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("npc_treasury", t, 0.0);
        }
    }

    @Override
    public void incNpcTreasury(double amount, String rtypeName) {
        if (!canAccess("npc_incTreasury", EngineLevers.npcTreasuryWriteEnabled)) return;
        if (npcAdapter == null || !npcAdapter.isAvailable()) return;
        try {
            npcAdapter.incTreasury(amount, rtypeName);
            trace("npc_incTreasury", String.valueOf(amount), rtypeName);
        } catch (Throwable t) {
            failVoid("npc_incTreasury", t);
        }
    }

    @Override
    public String getNpcRace(FactionNPC npc) {
        if (!canAccess("npc_race", EngineLevers.npcRaceEnabled)) return null;
        if (npc == null) return null;
        try {
            Race race = npc.race();
            if (race == null) return null;
            // Race.key is the canonical identifier in V71.44 (same pattern as
            // RESOURCE.key). Fallback to toString() if field doesn't exist.
            String v;
            try {
                v = race.key;
            } catch (NoSuchFieldError nsfe) {
                v = String.valueOf(race);
            }
            trace("npc_race", v != null ? v : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("npc_race", t, null);
        }
    }

    @Override
    public int getNpcCitizens(FactionNPC npc) {
        if (!canAccess("npc_citizens", EngineLevers.npcCitizensEnabled)) return 0;
        if (npc == null) return 0;
        try {
            int v = npc.citizens(null);
            trace("npc_citizens", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("npc_citizens", t, 0);
        }
    }

    @Override
    public double getNpcMilitaryPower(FactionNPC npc) {
        if (!canAccess("npc_military", EngineLevers.npcMilitaryEnabled)) return 0.0;
        if (npc == null) return 0.0;
        try {
            double v = npc.offensivePower();
            trace("npc_military", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("npc_military", t, 0.0);
        }
    }

    @Override
    public int getNpcIteration(FactionNPC npc) {
        // Proxy toggle: npcStockpileEnabled controls NPC data access broadly.
        // A dedicated npcIterationEnabled could be added if finer control needed.
        if (!canAccess("npc_iteration", EngineLevers.npcStockpileEnabled)) return 0;
        if (npc == null) return 0;
        try {
            int v = npc.iteration();
            trace("npc_iteration", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("npc_iteration", t, 0);
        }
    }

    // ─── Diplomacy ──────────────────────────────────────────

    @Override
    public double getPlayerPower() {
        if (!canAccess("dip_playerPower", EngineLevers.diplomacyWarPowerReadEnabled)) return 0.0;
        try {
            DipWarPlayer war = getWar();
            if (war == null) return 0.0;
            double v = war.playerPower();
            trace("dip_playerPower", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("dip_playerPower", t, 0.0);
        }
    }

    @Override
    public double getCoalitionPower() {
        if (!canAccess("dip_coalitionPower",
                EngineLevers.diplomacyCoalitionReadEnabled)) return 0.0;
        try {
            DipWarPlayer war = getWar();
            if (war == null) return 0.0;
            double v = war.coalitionPower();
            trace("dip_coalitionPower", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("dip_coalitionPower", t, 0.0);
        }
    }

    @Override
    public double getCoalitionAdvantage() {
        if (!canAccess("dip_coalitionAdvantage",
                EngineLevers.diplomacyCoalitionReadEnabled)) return 0.0;
        try {
            DipWarPlayer war = getWar();
            if (war == null) return 0.0;
            double v = war.coalitionAdvantage();
            trace("dip_coalitionAdvantage", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("dip_coalitionAdvantage", t, 0.0);
        }
    }

    @Override
    public void setWarNumericState(int updateIndex, double playerPower, double coalitionPower) {
        if (!canAccess("dip_setNumeric",
                EngineLevers.diplomacyWarPowerWriteEnabled)) return;
        if (diplomacyAdapter == null || !diplomacyAdapter.isAvailable()) return;
        try {
            DipWarPlayer war = getWar();
            if (war == null) return;
            diplomacyAdapter.setNumericState(war, updateIndex, playerPower, coalitionPower);
            trace("dip_setNumeric", playerPower + "/" + coalitionPower, "");
        } catch (Throwable t) {
            failVoid("dip_setNumeric", t);
        }
    }

    @Override
    public double getDistress(Faction faction) {
        if (!canAccess("dip_distress", EngineLevers.diplomacyDistressEnabled)) return 0.0;
        if (faction == null) return 0.0;
        try {
            DipWarPlayer war = getWar();
            if (war == null) return 0.0;
            double v = war.distress(faction);
            trace("dip_distress", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("dip_distress", t, 0.0);
        }
    }

    @Override
    public int getWillingCount() {
        if (!canAccess("dip_willing", EngineLevers.diplomacyWillingReadEnabled)) return 0;
        try {
            DipWarPlayer war = getWar();
            if (war == null) return 0;
            LIST<?> v = war.willing();
            int count = v != null ? v.size() : 0;
            trace("dip_willing", String.valueOf(count), "");
            return count;
        } catch (Throwable t) {
            return fail("dip_willing", t, 0);
        }
    }

    @Override
    public int getPotentialCount() {
        if (!canAccess("dip_potential", EngineLevers.diplomacyPotentialEnabled)) return 0;
        try {
            DipWarPlayer war = getWar();
            if (war == null) return 0;
            LIST<?> v = war.potential();
            int count = v != null ? v.size() : 0;
            trace("dip_potential", String.valueOf(count), "");
            return count;
        } catch (Throwable t) {
            return fail("dip_potential", t, 0);
        }
    }

    @Override
    public int getProxyCount() {
        if (!canAccess("dip_proxy", EngineLevers.diplomacyProxyEnabled)) return 0;
        try {
            DipWarPlayer war = getWar();
            if (war == null) return 0;
            LIST<?> v = war.proxy();
            int count = v != null ? v.size() : 0;
            trace("dip_proxy", String.valueOf(count), "");
            return count;
        } catch (Throwable t) {
            return fail("dip_proxy", t, 0);
        }
    }

    // ─── Trade ──────────────────────────────────────────────

    @Override
    public int getWorldPrice(RESOURCE resource) {
        if (!canAccess("trade_worldPrice", EngineLevers.tradeWorldPriceEnabled)) return 0;
        if (resource == null) return 0;
        try {
            int v = FACTIONS.PRICE().get(resource.tr());
            trace("trade_worldPrice", String.valueOf(v), resource.key);
            return v;
        } catch (Throwable t) {
            return fail("trade_worldPrice", t, 0);
        }
    }

    @Override
    public double getTradeToll(int fromFactionIndex, int toFactionIndex) {
        if (!canAccess("trade_toll", EngineLevers.tradeTollEnabled)) return -1.0;
        try {
            // TradeManager.toll(FactionNPC f) — public static, single-NPC overload.
            // Note: toFactionIndex is unused — this overload computes toll for a
            // single NPC. The two-faction overload (toll(Faction, Faction, double))
            // requires a distance parameter not available from this interface.
            LIST<FactionNPC> npcs = FACTIONS.NPCs();
            if (npcs == null || fromFactionIndex < 0 || fromFactionIndex >= npcs.size()) {
                return -1.0;
            }
            double v = TradeManager.toll(npcs.get(fromFactionIndex));
            trace("trade_toll", String.valueOf(v), "npc=" + fromFactionIndex);
            return v;
        } catch (Throwable t) {
            return fail("trade_toll", t, -1.0);
        }
    }

    @Override
    public double getTradeTariff(int fromFactionIndex, int toFactionIndex) {
        if (!canAccess("trade_tariff", EngineLevers.tradeTariffEnabled)) return -1.0;
        // TODO: TradeManager.tarif(Faction seller, Faction buyer, TRADABLE res, int amount)
        // requires TRADABLE + amount — not available from generic interface.
        // Extend IFactionAccess with proper trade method signatures in future sprint.
        trace("trade_tariff", "-1.0", "stub — needs TRADABLE + amount params");
        return -1.0;
    }

    // ─── Royalty ────────────────────────────────────────────

    @Override
    public Royalty getKing(FactionNPC npc) {
        if (!canAccess("royalty_king", EngineLevers.royaltyKingEnabled)) return null;
        if (npc == null) return null;
        try {
            Royalty v = npc.king();
            trace("royalty_king", v != null ? "ok" : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("royalty_king", t, null);
        }
    }

    @Override
    public CharSequence getRulerName(FactionNPC npc) {
        if (!canAccess("royalty_rulerName", EngineLevers.royaltyKingEnabled)) return null;
        if (npc == null) return null;
        try {
            CharSequence v = npc.rulerName();
            trace("royalty_rulerName", v != null ? v.toString() : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("royalty_rulerName", t, null);
        }
    }

    // ─── Opinion / Trust (DIPLO-01) ─────────────────────────

    @Override
    public double getFactionOpinion(FactionNPC npc) {
        if (!canAccess("royalty_opinion", EngineLevers.royaltyOpinionReadEnabled)) return 0.0;
        if (npc == null) return 0.0;
        try {
            double v = ROPINION.get(npc);
            trace("royalty_opinion", String.valueOf(v), "npc=" + (npc.race() != null ? npc.race().key : "?"));
            return v;
        } catch (Throwable t) {
            return fail("royalty_opinion", t, 0.0);
        }
    }

    @Override
    public double getFactionTrust(FactionNPC npc) {
        if (!canAccess("royalty_trust", EngineLevers.royaltyTrustReadEnabled)) return 0.0;
        if (npc == null) return 0.0;
        try {
            double v = ROPINION.trust().get(npc);
            trace("royalty_trust", String.valueOf(v), "npc=" + (npc.race() != null ? npc.race().key : "?"));
            return v;
        } catch (Throwable t) {
            return fail("royalty_trust", t, 0.0);
        }
    }

    @Override
    public void adjustFactionOpinion(FactionNPC npc, double delta) {
        if (!canAccess("royalty_opinionWrite", EngineLevers.royaltyOpinionWriteEnabled)) return;
        if (npc == null || delta == 0.0) return;
        // DIPLO-01 (v0.13.67): Opinion-Write via direkter Engine-API ist
        // package-private geschützt (ROPINION.setOpinionValue + SuperBoostable.incD).
        // Write-Pfad wird in DIPLO-02 via BypassGate aufgelöst.
        // Für jetzt: Logging des Intents, Monitoring liest via ROPINION.get().
        try {
            double current = ROPINION.get(npc);
            trace("royalty_opinionWrite", "INTENT delta=" + delta
                    + " current=" + String.format("%.1f", current),
                    "npc=" + (npc.race() != null ? npc.race().key : "?")
                    + " — deferred to DIPLO-02 BypassGate");
        } catch (Throwable t) {
            failVoid("royalty_opinionWrite", t);
        }
    }

    // ─── Player ─────────────────────────────────────────────

    @Override
    public Player getPlayer() {
        if (!canAccess("player_get", EngineLevers.playerCreditsEnabled)) return null;
        try {
            Player v = FACTIONS.player();
            trace("player_get", v != null ? "ok" : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("player_get", t, null);
        }
    }

    @Override
    public double getPlayerCredits() {
        if (!canAccess("player_credits", EngineLevers.playerCreditsEnabled)) return 0.0;
        try {
            Player p = FACTIONS.player();
            if (p == null) return 0.0;
            double v = p.credits().credits();
            trace("player_credits", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("player_credits", t, 0.0);
        }
    }

    @Override
    public int getPlayerCitizens() {
        if (!canAccess("player_citizens", EngineLevers.factionAccessEnabled)) return 0;
        try {
            Player p = FACTIONS.player();
            if (p == null) return 0;
            int v = p.citizens(null);
            trace("player_citizens", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("player_citizens", t, 0);
        }
    }

    // ═══ Internal Helpers ═══════════════════════════════════

    /**
     * Prüft ob ein Zugriff erlaubt ist (EngineLevers + Master-Toggle + Failure-Set).
     */
    private boolean canAccess(String method, boolean specificLever) {
        return EngineLevers.engineMirrorEnabled
                && EngineLevers.factionAccessEnabled
                && specificLever
                && !failedMethods.contains(method);
    }

    /** Trace-Log via LoggingAdapter (nur wenn Logging aktiviert). */
    private void trace(String key, String value, String note) {
        if (EngineLevers.engineMirrorLoggingEnabled) {
            LoggingAdapter.csvTrace("MIRROR", "FACTION", "TRACE", key, value, note);
        }
    }

    /** Error-Handler für Read-Methoden: loggt, markiert als failed, gibt Default zurück. */
    private <T> T fail(String method, Throwable t, T defaultValue) {
        if (failedMethods.add(method)) {
            EventLog.log("MIRROR", "FactionAccessImpl." + method + " failed — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + ". Method permanently disabled.");
            LoggingAdapter.csvTrace("MIRROR", "FACTION", "ERROR", method,
                    t.getClass().getSimpleName(), t.getMessage());
        }
        return defaultValue;
    }

    /** Error-Handler für Write-Methoden: loggt, markiert als failed. */
    private void failVoid(String method, Throwable t) {
        if (failedMethods.add(method)) {
            EventLog.log("MIRROR", "FactionAccessImpl." + method + " failed — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + ". Method permanently disabled.");
            LoggingAdapter.csvTrace("MIRROR", "FACTION", "ERROR", method,
                    t.getClass().getSimpleName(), t.getMessage());
        }
    }

    /**
     * Safe accessor for DIP.WAR_PLAYER() — returns null if engine not ready.
     * DIP.WAR_PLAYER() returns DipWarPlayer (not DWar which DIP.WAR() returns).
     */
    private DipWarPlayer getWar() {
        try {
            return DIP.WAR_PLAYER();
        } catch (Throwable t) {
            return null;
        }
    }
}
