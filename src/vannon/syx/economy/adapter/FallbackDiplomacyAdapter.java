package vannon.syx.economy.adapter;

import game.faction.diplomacy.DipWarPlayer;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.Bitmap1D;

/**
 * Fallback fuer {@link ISyxDiplomacy}, wenn die Vanilla-Reflection auf die
 * vier {@code DipWarPlayer}-Felder fehlgeschlagen ist (Spiel-Update, Engine-
 * API-Aenderung, Klassenumbau).
 *
 * <p>Alle Lese-Methoden liefern {@code null}; Schreib-Methoden sind No-ops.
 * Aufrufer ({@code DebtDiplomacyBuffer}) prüft {@link #isAvailable()} vor jedem
 * Aufruf und überspringt die Puffer-Logik komplett.</p>
 */
public final class FallbackDiplomacyAdapter implements ISyxDiplomacy {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Bitmap1D getWillingBits(DipWarPlayer war) {
        return null;
    }

    @Override
    public ArrayList<?> getWillingList(DipWarPlayer war) {
        return null;
    }

    @Override
    public void setNumericState(DipWarPlayer war, int updateIndex, double playerPower, double coalitionPower) {
        // No-op.
    }
}
