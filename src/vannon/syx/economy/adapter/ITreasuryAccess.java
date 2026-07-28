package vannon.syx.economy.adapter;

import game.faction.player.Player;
import init.resources.RESOURCE;
import snake2d.util.sets.LIST;

/**
 * Treasury-Zugriff — Spieler-Kasse, Steuern, Subventionen, Staatssalden.
 *
 * <p>Teil des EngineMirror. Jeder Zugriff wird über {@code EngineLevers}
 * getogglet und via {@code LoggingAdapter} geloggt.</p>
 *
 * <p>Version-gebunden für Songs of Syx V71.44.</p>
 */
public interface ITreasuryAccess {

    // ─── Availability ───────────────────────────────────────

    /**
     * @return true wenn Treasury-API verfügbar ist
     */
    boolean isAvailable();

    // ─── Player Treasury ────────────────────────────────────

    /**
     * Spieler-Denari (public API: {@code FACTIONS.player().credits().credits()}).
     */
    double getPlayerCredits();

    /**
     * Spieler-Einkommen pro Tag (Steuern - Löhne - Subventionen - Kornverteilung).
     * Berechnet aus {@code Fiscal} Internals über BypassGate falls nötig.
     */
    double getPlayerDailyIncome();

    /**
     * Spieler-Ausgaben pro Tag (Militärlöhne + Subventionen + Kornverteilung + Wartung).
     */
    double getPlayerDailyExpenses();

    /**
     * Nettosaldo pro Tag (Einkommen - Ausgaben).
     */
    double getPlayerNetDaily();

    /**
     * Treasury-History (letzte N Tage) für Charts.
     * @return Liste von Tagessalden (ältester zuerst) oder null
     */
    LIST<Double> getTreasuryHistory();

    // ─── Taxation ───────────────────────────────────────────

    /**
     * Aktuelle Steuersätze (Kopfsteuer, Marktsteuer, Einkommenssteuer, etc.).
     * Map: Steuerart -> Satz (0.0-1.0 oder absolut bei Kopfsteuer).
     */
    java.util.Map<String, Double> getTaxRates();

    /**
     * Setzt einen Steuersatz (via BypassGate auf Fiscal/PlayerFaction).
     * @return true wenn gesetzt
     */
    boolean setTaxRate(String taxType, double rate);

    /**
     * Steuereinnahmen letzte Periode.
     */
    double getLastTaxRevenue();

    // ─── Subsidies & Grain Dole ─────────────────────────────

    /**
     * Aktive Subventionen gesamt pro Tag.
     */
    double getDailySubsidies();

    /**
     * Kornverteilung (Grain Dole) Kosten pro Tag.
     */
    double getDailyGrainDoleCost();

    /**
     * Grain Dole aktiv?
     */
    boolean isGrainDoleActive();

    /**
     * Setzt Grain Dole an/aus.
     */
    void setGrainDoleActive(boolean active);

    // ─── Crisis ─────────────────────────────────────────────

    /**
     * Aktuelle Krisen-Stufe (0 = keine Krise, 1-5 = Stufen).
     */
    int getCrisisTier();

    /**
     * Krisen-Schwellen (5 Werte: -5K, -50K, -250K, -1M, -5M).
     */
    long[] getCrisisThresholds();

    /**
     * Ob Treasury im Hard-Floor (Tier 5) ist.
     */
    boolean isHardFloor();

    // ─── Factions / Trade ───────────────────────────────────

    /**
     * NPC-Fraktions-Kassen (für Diplomatie/Trade).
     */
    java.util.List<FactionTreasuryInfo> getNpcTreasuries();

    /**
     * Weltmarktpreis für Ressource (Mirror von IFactionAccess.getWorldPrice).
     */
    int getWorldPrice(RESOURCE resource);

    record FactionTreasuryInfo(String name, double credits, boolean active) {}
}