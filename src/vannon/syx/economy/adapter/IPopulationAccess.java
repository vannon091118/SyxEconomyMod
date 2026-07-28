package vannon.syx.economy.adapter;

import init.race.Race;
import init.type.HCLASS;
import init.type.HTYPE;
import snake2d.util.sets.LIST;

/**
 * Population-Zugriff — Bürger, Rassen, Klassen, Loyalty, Housing, Needs.
 *
 * <p>Teil des EngineMirror. Bündelt alle Population-bezogenen Zugriffe,
 * die aktuell über {@code STATS.POP()}, {@code STANDINGS}, {@code POP}
 * und {@code SETT.ROOMS().HOME} verteilt sind.</p>
 *
 * <p>Version-gebunden für Songs of Syx V71.44.</p>
 */
public interface IPopulationAccess {

    // ─── Availability ───────────────────────────────────────

    boolean isAvailable();

    // ─── Totals by Class ────────────────────────────────────

    /**
     * Gesamtbevölkerung für Klasse (CITIZEN, SLAVE, NOBLE, etc.).
     * Mirror von {@code STATS.POP().POP.data(cl).get(null)}.
     */
    int getTotalPopulation(HCLASS cl);

    /**
     * Gesamtbevölkerung für Klasse + Rasse.
     */
    int getTotalPopulation(HCLASS cl, Race race);

    /**
     * Alle Klassen-Populationen auf einmal.
     */
    java.util.Map<HCLASS, Integer> getAllClassTotals();

    // ─── By Race ────────────────────────────────────────────

    /**
     * Bevölkerung einer Rasse (alle Klassen).
     */
    int getRacePopulation(Race race);

    /**
     * Bevölkerung einer Rasse in einer Klasse.
     */
    int getRaceClassPopulation(HCLASS cl, Race race);

    /**
     * Eingehende Migration (Incoming) für Klasse + Rasse.
     */
    int getIncomingPopulation(HCLASS cl, Race race);

    // ─── Loyalty / Standing ─────────────────────────────────

    /**
     * Loyalty für Klasse (gesamt über alle Rassen).
     * Mirror von {@code STANDINGS.get(cl).current()}.
     */
    double getLoyalty(HCLASS cl);

    /**
     * Loyalty für Klasse + Rasse.
     */
    double getLoyalty(HCLASS cl, Race race);

    /**
     * Target-Loyalty für Klasse.
     */
    double getTargetLoyalty(HCLASS cl);

    /**
     * Erwartung (Expectation) für Bürger.
     */
    double getExpectation();

    // ─── Wealth / Wallet (Mod-spezifisch, via EconomySim) ───

    /**
     * Durchschnittliches Wallet (Denari) pro Bürger in Klasse + Rasse.
     * Liefert 0 wenn EconomySim nicht verfügbar oder keine Wallets.
     */
    double getAvgWallet(HCLASS cl, Race race);

    /**
     * Median Wallet (Denari).
     */
    double getMedianWallet(HCLASS cl, Race race);

    /**
     * Gini-Koeffizient für Klasse + Rasse (0.0-1.0).
     */
    double getGini(HCLASS cl, Race race);

    /**
     * Vermögensverteilung: Brackets (arm/niedrig/mittel/hoch/reich) -> Count.
     */
    java.util.Map<String, Integer> getWealthBrackets(HCLASS cl, Race race);

    // ─── Employment ─────────────────────────────────────────

    /**
     * Beschäftigte in Firmen für Klasse + Rasse.
     */
    int getEmployedCount(HCLASS cl, Race race);

    /**
     * Arbeitslose (Oddjobber) für Klasse + Rasse.
     */
    int getUnemployedCount(HCLASS cl, Race race);

    /**
     * Durchschnittlicher Lohn für Klasse + Rasse.
     */
    double getAvgWage(HCLASS cl, Race race);

    // ─── Housing ────────────────────────────────────────────

    /**
     * Wohnraum: Gesamtkapazität für Klasse.
     */
    int getHousingCapacity(HCLASS cl);

    /**
     * Wohnraum: Belegt für Klasse.
     */
    int getHousingUsed(HCLASS cl);

    /**
     * Wohnraum: Frei für Klasse.
     */
    int getHousingFree(HCLASS cl);

    /**
     * Obdachlose für Klasse + Rasse.
     */
    int getHomeless(HCLASS cl, Race race);

    // ─── Demographics ───────────────────────────────────────

    /**
     * Altersverteilung (Demography) für Rasse.
     * Array Index = Altersgruppe, Wert = Anzahl.
     */
    int[] getAgeDistribution(Race race);

    /**
     * Durchschnittsalter für Rasse.
     */
    double getAverageAge(Race race);

    /**
     * Geburtenrate (pro Tag) für Rasse.
     */
    double getBirthRate(Race race);

    /**
     * Sterberate (pro Tag) für Rasse.
     */
    double getDeathRate(Race race);

    // ─── Needs / Happiness ──────────────────────────────────

    /**
     * Glück (Happiness) für Klasse + Rasse (0.0-1.0).
     */
    double getHappiness(HCLASS cl, Race race);

    /**
     * Bedarfsdeckung für Klasse + Rasse (0.0-1.0).
     */
    double getNeedSatisfaction(HCLASS cl, Race race);

    // ─── Types (HTYPES) ──────────────────────────────────────

    /**
     * Bevölkerung für einen spezifischen HTYPE (z.B. SOLDIER, RIOTER, PRISONER).
     */
    int getTypePopulation(HTYPE type);

    /**
     * Bevölkerung für HTYPE + Rasse.
     */
    int getTypePopulation(HTYPE type, Race race);
}