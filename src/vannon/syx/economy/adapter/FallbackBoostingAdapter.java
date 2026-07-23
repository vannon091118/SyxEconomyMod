package vannon.syx.economy.adapter;

import game.boosting.Boostable;

/**
 * Fallback für {@link ISyxBoosting}, wenn die Vanilla-Reflection auf das
 * {@code GOV}-Feld in {@code BOOSTABLES.CIVICS()} fehlgeschlagen hat.
 *
 * <p>{@link #getAdminBoostable} liefert {@code null} — die Aufrufer-Seite
 * ({@code EconProgression.registerAdminBooster()}) registriert dann keinen
 * Booster und loggt einen SEAM-Eintrag. Die Wirtschaftssimulation läuft
 * normal weiter, nur ohne den +20%-Admin-Boost bei INDUSTRIE-Stufe.</p>
 */
public final class FallbackBoostingAdapter implements ISyxBoosting {

    @Override
    public boolean isAdminBoosterAvailable() {
        return false;
    }

    @Override
    public Boostable getAdminBoostable() {
        return null;
    }
}
