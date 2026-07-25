package vannon.syx.economy.adapter;

import game.faction.diplomacy.DipWarPlayer;
import java.lang.reflect.Field;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.Bitmap1D;
import vannon.syx.economy.core.EventLog;

/**
 * V71.44-Adapter: liest/schreibt vier private Felder der
 * {@code DipWarPlayer} per Reflection + nutzt den öffentlichen Getter
 * {@code war.willing()} für die Liste. Alles-oder-Nichts-Initialisierung
 * im Konstruktor: wenn ein Feld nicht gefunden wird, ist der gesamte
 * Adapter deaktiviert.
 *
 * <p>One-Shot-SEAM-Guards bei Init-Failure und Runtime-Failure verhindern
 * EventLog-Spam.</p>
 */
public final class VanillaDiplomacyAdapter implements ISyxDiplomacy {

    /** V71.44-verifizierte Field-Namen (4 Felder, willing via public Getter). */
    private static final String UPDATE_INDEX_FIELD     = "upI";
    private static final String PLAYER_POWER_FIELD    = "pPow";
    private static final String COALITION_POWER_FIELD = "coalitionPow";
    private static final String WILLING_BITS_FIELD    = "bWilling";

    private final Field updateIndex;
    private final Field playerPower;
    private final Field coalitionPower;
    private final Field willingBits;

    private final boolean initAvailable;
    private boolean runtimeFailed;

    private boolean initFailedLogged;
    private boolean runtimeFailedLogged;

    public VanillaDiplomacyAdapter() {
        Field up = null, pp = null, cp = null, bits = null;
        boolean ok = false;
        try {
            up = DipWarPlayer.class.getDeclaredField(UPDATE_INDEX_FIELD);
            pp = DipWarPlayer.class.getDeclaredField(PLAYER_POWER_FIELD);
            cp = DipWarPlayer.class.getDeclaredField(COALITION_POWER_FIELD);
            bits = DipWarPlayer.class.getDeclaredField(WILLING_BITS_FIELD);
            up.setAccessible(true);
            pp.setAccessible(true);
            cp.setAccessible(true);
            bits.setAccessible(true);
            ok = true;
            EventLog.log("SEAM", "VanillaDiplomacyAdapter: READY (4/4 Felder)");
        } catch (Throwable t) {
            ok = false;
            if (!this.initFailedLogged) {
                this.initFailedLogged = true;
                EventLog.log("SEAM", "VanillaDiplomacyAdapter init failed: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + ". Schulden-Puffer ist inaktiv.");
            }
        }
        this.updateIndex = up;
        this.playerPower = pp;
        this.coalitionPower = cp;
        this.willingBits = bits;
        this.initAvailable = ok;
        this.runtimeFailed = false;
    }

    @Override
    public boolean isAvailable() {
        return this.initAvailable && !this.runtimeFailed;
    }

    @Override
    public Bitmap1D getWillingBits(DipWarPlayer war) {
        if (!isAvailable() || war == null || this.willingBits == null) {
            return null;
        }
        try {
            return (Bitmap1D) this.willingBits.get(war);
        } catch (Throwable t) {
            this.runtimeFailed = true;
            if (!this.runtimeFailedLogged) {
                this.runtimeFailedLogged = true;
                EventLog.log("SEAM", "VanillaDiplomacyAdapter.getWillingBits failed: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + ". Schulden-Puffer dauerhaft inaktiv.");
            }
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ArrayList<?> getWillingList(DipWarPlayer war) {
        // Public Getter — kein Reflection nötig. war.willing() returned
        // LIST<FactionNPC>, aber die konkrete Instanz IST ein ArrayList.
        // Cast notwendig weil das Interface ArrayList<?> verlangt
        // (clearSloppy()/add() sind ArrayList-Methoden, nicht LIST).
        return war != null ? (ArrayList<?>) (Object) war.willing() : null;
    }

    @Override
    public void setNumericState(DipWarPlayer war, int updateIndexValue, double playerPowerValue, double coalitionPowerValue) {
        if (!isAvailable() || war == null) {
            return;
        }
        try {
            this.playerPower.setDouble(war, playerPowerValue);
            this.coalitionPower.setDouble(war, coalitionPowerValue);
            this.updateIndex.setInt(war, updateIndexValue);
        } catch (Throwable t) {
            this.runtimeFailed = true;
            if (!this.runtimeFailedLogged) {
                this.runtimeFailedLogged = true;
                EventLog.log("SEAM", "VanillaDiplomacyAdapter.setNumericState failed: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + ". Schulden-Puffer dauerhaft inaktiv.");
            }
        }
    }
}
