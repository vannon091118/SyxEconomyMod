package vannon.syx.economy.adapter;

import game.faction.diplomacy.DipWarPlayer;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.Bitmap1D;
import vannon.syx.economy.adapter.seam.BypassGate;
import vannon.syx.economy.adapter.seam.FieldAccessor;
import vannon.syx.economy.core.EventLog;

/**
 * V71.44-Adapter powered by {@link BypassGate}: liest/schreibt vier private
 * Felder der {@code DipWarPlayer} per VarHandle (primär) mit Reflection-Fallback.
 *
 * <p>Der BypassGate wählt automatisch die schnellste verfügbare Zugriffsstrategie
 * (VarHandle auf JDK 21+, sonst Reflection). Kein manueller MH-Toggle nötig.</p>
 *
 * <p>Alles-oder-Nichts: wenn ein Feld nicht gefunden wird, ist der gesamte
 * Adapter deaktiviert ({@link #isAvailable()} = false).</p>
 */
public final class VanillaDiplomacyAdapter implements ISyxDiplomacy {

    private static final String UPDATE_INDEX_FIELD     = "upI";
    private static final String PLAYER_POWER_FIELD    = "pPow";
    private static final String COALITION_POWER_FIELD = "coalitionPow";
    private static final String WILLING_BITS_FIELD    = "bWilling";

    private final BypassGate gate;
    private final FieldAccessor.IntField    updateIndex;
    private final FieldAccessor.DoubleField playerPower;
    private final FieldAccessor.DoubleField coalitionPower;
    private final FieldAccessor.RefField<Bitmap1D> willingBits;

    private boolean runtimeFailed;
    private boolean runtimeFailedLogged;

    public VanillaDiplomacyAdapter() {
        this.gate = new BypassGate("VanillaDiplomacyAdapter");
        this.updateIndex   = gate.intField(DipWarPlayer.class, UPDATE_INDEX_FIELD);
        this.playerPower   = gate.doubleField(DipWarPlayer.class, PLAYER_POWER_FIELD);
        this.coalitionPower = gate.doubleField(DipWarPlayer.class, COALITION_POWER_FIELD);
        this.willingBits   = gate.refField(DipWarPlayer.class, WILLING_BITS_FIELD, Bitmap1D.class);

        if (gate.isAvailable()) {
            EventLog.log("SEAM", "VanillaDiplomacyAdapter: READY (4/4 Felder via BypassGate)");
        }
    }

    @Override
    public boolean isAvailable() {
        return gate.isAvailable() && !this.runtimeFailed;
    }

    @Override
    public Bitmap1D getWillingBits(DipWarPlayer war) {
        if (!isAvailable() || war == null) return null;
        try {
            return willingBits.get(war);
        } catch (Throwable t) {
            this.runtimeFailed = true;
            logRuntime("getWillingBits", t);
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ArrayList<?> getWillingList(DipWarPlayer war) {
        return war != null ? (ArrayList<?>) (Object) war.willing() : null;
    }

    @Override
    public void setNumericState(DipWarPlayer war, int updateIndexValue,
                                 double playerPowerValue, double coalitionPowerValue) {
        if (!isAvailable() || war == null) return;
        try {
            playerPower.set(war, playerPowerValue);
            coalitionPower.set(war, coalitionPowerValue);
            updateIndex.set(war, updateIndexValue);
        } catch (Throwable t) {
            this.runtimeFailed = true;
            logRuntime("setNumericState", t);
        }
    }

    private void logRuntime(String method, Throwable t) {
        if (!this.runtimeFailedLogged) {
            this.runtimeFailedLogged = true;
            EventLog.log("SEAM", "VanillaDiplomacyAdapter." + method + " failed: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + ". Schulden-Puffer dauerhaft inaktiv.");
        }
    }
}
