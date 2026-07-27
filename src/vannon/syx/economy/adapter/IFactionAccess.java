package vannon.syx.economy.adapter;

import game.faction.Faction;
import game.faction.npc.FactionNPC;
import game.faction.player.Player;
import game.faction.royalty.Royalty;
import init.resources.RESOURCE;
import snake2d.util.sets.LIST;

/**
 * EngineMirror Sub-Interface für alle Factions-Zugriffe.
 *
 * <p>Bündelt die Zugriffe von {@link ISyxDiplomacy}, {@link ISyxNpc}
 * und den factions-bezogenen direkten API-Aufrufen in eine einheitliche
 * Fassade. Jeder Zugriff wird über {@code EngineLevers} konfigurierbar
 * und via {@code LoggingAdapter} geloggt.</p>
 *
 * <p>Organisiert in 5 Bereiche:
 * <ul>
 *   <li><b>NPC</b> — Preise, Treasury, Stockpile, Bonus, Request, Race, Citizens, Military</li>
 *   <li><b>Diplomacy</b> — Kriegsmacht, Koalition, Distress, Willing, Potential, Proxy</li>
 *   <li><b>Trade</b> — Weltmarktpreise, Toll, Tariff, Buyer/Seller</li>
 *   <li><b>Royalty</b> — König, Hof, Opinion, Trust</li>
 *   <li><b>Player</b> — Credits, Fraktionsdaten</li>
 * </ul></p>
 *
 * <p>Version-gebunden für Songs of Syx V71.44.</p>
 */
public interface IFactionAccess {

    // ─── Availability ───────────────────────────────────────

    /**
     * @return true wenn mindestens die kritischen Adapter (ISyxDiplomacy,
     *         ISyxNpc) erfolgreich initialisiert wurden.
     */
    boolean isAvailable();

    // ═══ NPC ════════════════════════════════════════════════

    /** Anzahl der NPC-Faktionen im Spiel. */
    int getNpcCount();

    /**
     * NPC-Fraktion als FactionNPC (public API via FACTIONS.NPCs()).
     * @param index 0-basierter Index
     * @return die FactionNPC oder null wenn nicht verfügbar
     */
    FactionNPC getNpc(int index);

    /**
     * Alle NPC-Fraktionen (inkl. inaktiver). Nutze {@code Faction.isActive()}
     * zum Filtern wenn nur aktive NPCs benötigt werden.
     * @return Liste aller NPCs oder null
     */
    LIST<FactionNPC> getActiveNpcs();

    /**
     * Sell-Preis einer Ressource für die erste NPC-Fraktion.
     * @return Preis oder 0 wenn nicht verfügbar
     */
    int getNpcSellPrice(String resourceKey);

    /**
     * Setzt den Sell-Preis für ALLE NPC-Fraktionen (via ISyxNpc Adapter).
     */
    void setNpcSellPrice(String resourceKey, int price);

    /**
     * Buy-Preis einer Ressource für die erste NPC-Fraktion.
     */
    int getNpcBuyPrice(String resourceKey);

    /**
     * Setzt den Buy-Preis für ALLE NPC-Fraktionen (via ISyxNpc Adapter).
     */
    void setNpcBuyPrice(String resourceKey, int price);

    /**
     * Treasury der ersten NPC-Fraktion (public API: credits().credits()).
     */
    double getNpcTreasury();

    /**
     * Erhöht Treasury der ersten NPC-Fraktion.
     * @param amount Betrag
     * @param rtypeName CTYPE-Name (z.B. "TRADE", "TAX")
     */
    void incNpcTreasury(double amount, String rtypeName);

    /**
     * NPC-Rasse (public API: race()).
     */
    String getNpcRace(FactionNPC npc);

    /**
     * NPC-Bürgerzahl (public API: citizens(Race)).
     * @param npc die NPC-Fraktion (null → erste NPC)
     * @return Bürgerzahl oder 0
     */
    int getNpcCitizens(FactionNPC npc);

    /**
     * NPC-militärische Stärke (public API: offensivePower()).
     */
    double getNpcMilitaryPower(FactionNPC npc);

    /**
     * NPC-Iteration-Zähler (public API: iteration()).
     */
    int getNpcIteration(FactionNPC npc);

    // ═══ Diplomacy ══════════════════════════════════════════

    /**
     * Spieler-Macht im aktuellen Krieg (via ISyxDiplomacy oder public API).
     * @return Macht oder 0.0 wenn nicht verfügbar
     */
    double getPlayerPower();

    /**
     * Koalitions-Macht im aktuellen Krieg.
     */
    double getCoalitionPower();

    /**
     * Koalitions-Vorteil 0.0–1.0 (public API: coalitionAdvantage()).
     */
    double getCoalitionAdvantage();

    /**
     * Setzt die drei numerischen Kriegsfelder atomar (via ISyxDiplomacy).
     */
    void setWarNumericState(int updateIndex, double playerPower, double coalitionPower);

    /**
     * Distress-Wert einer Fraktion (public API: distress(Faction)).
     */
    double getDistress(Faction faction);

    /**
     * Kriegswillige Fraktionen (public API: willing()).
     */
    int getWillingCount();

    /**
     * Potenzielle Feinde (public API: potential()).
     */
    int getPotentialCount();

    /**
     * Proxy-Fraktionen (public API: proxy()).
     */
    int getProxyCount();

    // ═══ Trade ══════════════════════════════════════════════

    /**
     * Weltmarktpreis für eine Ressource (public API: FACTIONS.PRICE().get()).
     * @param resource die Ressource
     * @return Preis oder 0 wenn nicht verfügbar
     */
    int getWorldPrice(RESOURCE resource);

    /**
     * Toll-Berechnung für einen Trade (via TradeManager, Reflection).
     * @return Toll-Wert oder -1 wenn nicht verfügbar
     */
    double getTradeToll(int fromFactionIndex, int toFactionIndex);

    /**
     * Tariff-Berechnung für einen Trade (via TradeManager, Reflection).
     * @return Tariff-Wert oder -1 wenn nicht verfügbar
     */
    double getTradeTariff(int fromFactionIndex, int toFactionIndex);

    // ═══ Royalty ════════════════════════════════════════════

    /**
     * König einer NPC-Fraktion (public API: king()).
     * @return Royalty oder null
     */
    Royalty getKing(FactionNPC npc);

    /**
     * König-Name einer NPC-Fraktion (public API: rulerName()).
     */
    CharSequence getRulerName(FactionNPC npc);

    // ═══ Player ═════════════════════════════════════════════

    /**
     * Spieler-Fraktion (public API: FACTIONS.player()).
     */
    Player getPlayer();

    /**
     * Spieler-Treasury (public API: credits().credits()).
     */
    double getPlayerCredits();

    /**
     * Spieler-Bevölkerung (public API: citizens(null)).
     */
    int getPlayerCitizens();
}
