package vannon.syx.economy.adapter;

import game.boosting.Boostable;

/**
 * Kapselt die Reflection-Suche nach dem {@code +20%}-Admin-Boostable im
 * {@code CIVICS()}-Instanz-Set der Vanilla-Engine. Wird vom INDUSTRIE-Stufen-
 * Aufstieg in {@code EconProgression} verwendet.
 *
 * <p>Bei einem Spiel-Update muss nur die Adapter-Implementierung
 * geprüft werden — nicht mehr die Reflection-Schleife in
 * {@code EconProgression.registerAdminBooster()}.</p>
 *
 * <p>Felder auf {@code GOV} werden in Songs of Syx V71.44 bestätigt
 * (siehe {@code COVERAGE_AUDIT_FINAL_2026-07-23.md}).</p>
 */
public interface ISyxBoosting {

    /**
     * @return true wenn ein passendes Admin-Boostable per Reflection gefunden
     *         wurde; false wenn der Fallback genutzt werden muss.
     */
    boolean isAdminBoosterAvailable();

    /**
     * Liefert das per Reflection gefundene Admin-Boostable (z. B. GOV-Punkte)
     * für die BoosterValue-Anwendung. Wird gecached.
     *
     * @return das Boostable, oder {@code null} wenn nicht verfügbar
     */
    Boostable getAdminBoostable();
}
