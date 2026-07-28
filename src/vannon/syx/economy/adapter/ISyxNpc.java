package vannon.syx.economy.adapter;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.faction.npc.FactionNPC;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import snake2d.util.sets.LIST;

/**
 * Interface für NPC-Faktionen-Zugriff.
 *
 * <p>Kapselt den Zugriff auf NPC-Preise (priceSell/priceBuy) und
 * Treasury-Manipulation. Ermöglicht dem Mod, NPC-Wirtschaftsverhalten
 * zu beeinflussen — Grundlage für Civil-Verhalten-Steuerung und
 * zukünftiges Job-Learning via NPC-Ökonomie.</p>
 *
 * <p>Die Implementierung nutzt BypassGate für package-private
 * {@code FactionResource}-Felder und ClassResolver für die
 * package-private innere Klasse.</p>
 */
public interface ISyxNpc {

    /** @return true wenn der NPC-Adapter erfolgreich initialisiert wurde */
    boolean isAvailable();

    /** @return Anzahl der NPC-Faktionen im Spiel */
    int npcCount();

    /**
     * Liest den Sell-Preis einer Ressource für die erste NPC-Faktion.
     * @param resourceKey z.B. "WOOD", "GRAIN"
     * @return aktueller Sell-Preis oder 0 wenn nicht verfügbar
     */
    int getSellPrice(String resourceKey);

    /**
     * Setzt den Sell-Preis einer Ressource für ALLE NPC-Faktionen.
     * @param resourceKey z.B. "WOOD"
     * @param price neuer Preis
     */
    void setSellPrice(String resourceKey, int price);

    /**
     * Liest den Buy-Preis einer Ressource für die erste NPC-Faktion.
     */
    int getBuyPrice(String resourceKey);

    /**
     * Setzt den Buy-Preis einer Ressource für ALLE NPC-Faktionen.
     */
    void setBuyPrice(String resourceKey, int price);

    /**
     * Treasury der ersten NPC-Faktion.
     * @return aktuelle Treasury oder 0
     */
    double getTreasury();

    /**
     * Erhöht die Treasury der ersten NPC-Faktion.
     * @param amount Betrag
     * @param rtypeName CTYPE-Name (z.B. "TRADE", "TAX")
     */
    void incTreasury(double amount, String rtypeName);
}
