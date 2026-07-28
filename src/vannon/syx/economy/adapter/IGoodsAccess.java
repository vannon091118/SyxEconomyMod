package vannon.syx.economy.adapter;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.trade.TR;
import init.trade.TRADABLE;
import settlement.main.SETT;
import snake2d.util.sets.LIST;

/**
 * Goods / Economy-Zugriff — Preise, Produktion, Verbrauch, Lagerbestände, Import/Export.
 *
 * <p>Teil des EngineMirror. Bündelt alle Goods-bezogenen Zugriffe,
 * die aktuell über {@code FACTIONS.PRICE()}, {@code SETT.ROOMS().STOCKPILE},
 * {@code SETT.TRADE()}, {@code FlowPrices}, {@code WarehouseMarket} verteilt sind.</p>
 *
 * <p>Version-gebunden für Songs of Syx V71.44.</p>
 */
public interface IGoodsAccess {

    // ─── Availability ───────────────────────────────────────

    boolean isAvailable();

    // ─── Prices ─────────────────────────────────────────────

    /**
     * Weltmarktpreis für TRADABLE (Mirror von IFactionAccess).
     */
    int getWorldPrice(TRADABLE tradable);

    /**
     * Lokaler Preis (FlowPrices.localPrice) für TRADABLE — Mod-Preis nach
     * Scarcity-Multiplier, Transportkosten, etc.
     */
    double getLocalPrice(TRADABLE tradable);

    /**
     * Anker-Preis (Vanilla-Baseline) für TRADABLE.
     */
    int getAnchorPrice(TRADABLE tradable);

    /**
     * Scarcity Multiplier (1.0 = normal, >1 = knapp, <1 = Überfluss).
     */
    double getScarcityMultiplier(TRADABLE tradable);

    /**
     * Effective Coverage (Supply / (Demand * Target)) für TRADABLE.
     * 1.0 = genau gedeckt, <1 = Mangel, >1 = Überfluss.
     */
    double getEffectiveCoverage(TRADABLE tradable);

    /**
     * Preis-Cap aktiv? (FlowPrices.enforceCap).
     */
    boolean isPriceCapped(TRADABLE tradable);

    // ─── Production / Consumption ───────────────────────────

    /**
     * Tägliche Produktion (Supply/Day) für TRADABLE.
     * Mirror von FlowMeter.supplyPerDay.
     */
    double getDailyProduction(TRADABLE tradable);

    /**
     * Täglicher Verbrauch (Demand/Day) für TRADABLE.
     * Mirror von FlowMeter.demandPerDay.
     */
    double getDailyConsumption(TRADABLE tradable);

    /**
     * Netto-Flow pro Tag (Produktion - Verbrauch).
     */
    double getNetDailyFlow(TRADABLE tradable);

    /**
     * Produzenten-Anzahl für TRADABLE (Industrien die produzieren).
     */
    int getProducerCount(TRADABLE tradable);

    /**
     * Konsumenten-Anzahl für TRADABLE (Industrien die verbrauchen).
     */
    int getConsumerCount(TRADABLE tradable);

    // ─── Stockpiles ─────────────────────────────────────────

    /**
     * Gesamt-Lagerbestand (alle Stockpiles + Hauler + Station) für RESOURCE.
     * Mirror von SETT.ROOMS().STOCKPILE.tally().amountTotal(res).
     */
    long getTotalStockpileAmount(RESOURCE res);

    /**
     * Reservierbarer Lagerbestand für RESOURCE.
     */
    long getReservableStockpileAmount(RESOURCE res);

    /**
     * Lagerbestand in Staatslagern (Crown Storage) für RESOURCE.
     */
    long getCrownStorageAmount(RESOURCE res);

    /**
     * Lagerbestand in Spieler-eigenen Lagern für TRADABLE.
     */
    long getPlayerStockpileAmount(TRADABLE tradable);

    // ─── Import / Export ────────────────────────────────────

    /**
     * Importiert die Ressource? (SETT.TRADE().buyer(res).importing()).
     */
    boolean isImporting(TRADABLE tradable);

    /**
     * Exportiert die Ressource? (SETT.TRADE().seller(res).exporting() != null).
     */
    boolean isExporting(TRADABLE tradable);

    /**
     * Import-Limit pro Tag (max units/Tag).
     */
    int getImportLimit(TRADABLE tradable);

    /**
     * Export-Limit pro Tag.
     */
    int getExportLimit(TRADABLE tradable);

    /**
     * Tatsächlich importierte Menge gestern.
     */
    double getImportedYesterday(TRADABLE tradable);

    /**
     * Tatsächlich exportierte Menge gestern.
     */
    double getExportedYesterday(TRADABLE tradable);

    /**
     * Import-Preis (was wir zahlen).
     */
    double getImportPrice(TRADABLE tradable);

    /**
     * Export-Preis (was wir bekommen).
     */
    double getExportPrice(TRADABLE tradable);

    // ─── FlowPrices / Market Detail ─────────────────────────

    /**
     * Alle TRADABLEs mit Nicht-Null-Produktion oder -Verbrauch.
     */
    LIST<TRADABLE> getActiveTradables();

    /**
     * Preis-Historie (letzte N Tage) für TRADABLE.
     * Array[0] = gestern, Array[N-1] = vor N Tagen.
     */
    double[] getPriceHistory(TRADABLE tradable, int days);

    /**
     * Produktions-Historie (letzte N Tage).
     */
    double[] getProductionHistory(TRADABLE tradable, int days);

    /**
     * Verbrauchs-Historie (letzte N Tage).
     */
    double[] getConsumptionHistory(TRADABLE tradable, int days);

    // ─── Treasury / Trade Balance ──────────────────────────

    /**
     * Spieler-Treasury (Denari).
     */
    double getPlayerCredits();

    /**
     * Tägliche Handelsbilanz (Export-Einnahmen - Import-Kosten).
     */
    double getDailyTradeBalance();

    /**
     * Kumulative Handelsbilanz seit Spielstart.
     */
    double getCumulativeTradeBalance();
}