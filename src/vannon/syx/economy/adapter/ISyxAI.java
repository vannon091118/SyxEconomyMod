package vannon.syx.economy.adapter;

import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIPLAN;

/**
 * Kapselt die Erkennung von Vanilla-AI-Plänen, die package-private sind
 * und daher nicht via {@code instanceof} geprüft werden können.
 *
 * <p>Implementierungen nutzen {@link Class#forName(String)} um die
 * Vanilla-Plan-Klassen zu laden und prüfen mit {@link Class#isInstance(Object)}.
 * Bei einem Spiel-Update muss nur die Adapter-Implementierung
 * geprüft werden — nicht mehr alle 5 Aufrufer in 4 Dateien.</p>
 *
 * <p>V71.44-verifiziert: alle 6 Plan-Klassennamen existieren in
 * {@code SongsOfSyx-sources.jar} (COVERAGE_AUDIT_FINAL_2026-07-23).</p>
 */
public interface ISyxAI {

    /**
     * Prüft ob der Bürger gerade Gelegenheitsarbeit ({@code PlanOddjobber}) ausführt.
     * @param humanoid der zu prüfende Bürger
     * @return true wenn der aktive AI-Plan {@code PlanOddjobber} ist
     */
    boolean isOddjobbing(Humanoid humanoid);

    /**
     * Prüft ob der AI-Plan ein Essensplan ist
     * ({@code F_SPlanEatery}, {@code F_SPlanCanteen}, oder {@code F_PlanEat}).
     * @param plan der zu prüfende Plan (darf null sein)
     * @return true wenn es ein Essensplan ist
     */
    boolean isFoodPlan(AIPLAN plan);

    /**
     * Prüft ob der AI-Plan ein Tavernenplan ({@code PlanTavern}) ist.
     * @param plan der zu prüfende Plan (darf null sein)
     * @return true wenn es ein Tavernenplan ist
     */
    boolean isTavernPlan(AIPLAN plan);

    /**
     * Prüft ob der AI-Plan ein Marktplan ({@code M_PlanMarket}) ist.
     * @param plan der zu prüfende Plan (darf null sein)
     * @return true wenn es ein Marktplan ist
     */
    boolean isMarketPlan(AIPLAN plan);

    /**
     * Gibt an ob mindestens eine der 6 Plan-Klassen erfolgreich geladen wurde.
     * Consumer sollten diesen Check vor der Plan-Erkennung durchführen.
     * @return true wenn AI-Plan-Erkennung verfügbar ist
     */
    boolean isAvailable();
}
