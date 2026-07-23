package vannon.syx.economy.adapter;

import game.faction.diplomacy.DipWarPlayer;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.Bitmap1D;
import vannon.syx.economy.core.EventLog;

/**
 * Forward-kompatible, optimierte Variante des {@link VanillaDiplomacyAdapter}: VarHandle
 * statt {@link java.lang.reflect.Field#get(Object)} / setDouble / setInt.
 *
 * <p>Erwarteter Speedup: 4–6× auf JDK 21+ (keine Reflection-Access-Checks
 * pro Aufruf, C2 intrinsified VarHandle.get/set auf Mov-Instruktionen).</p>
 */
public final class VanillaDiplomacyAdapterMH implements ISyxDiplomacy {

    private static final String UPDATE_INDEX_FIELD     = "upI";
    private static final String PLAYER_POWER_FIELD    = "pPow";
    private static final String COALITION_POWER_FIELD = "coalitionPow";
    private static final String WILLING_BITS_FIELD    = "bWilling";

    private final VarHandle updateIndex;
    private final VarHandle playerPower;
    private final VarHandle coalitionPower;
    private final VarHandle willingBits;

    private final boolean initAvailable;
    private boolean runtimeFailed;

    private boolean initFailedLogged;
    private boolean runtimeFailedLogged;

    public VanillaDiplomacyAdapterMH() {
        VarHandle up = null, pp = null, cp = null, bits = null;
        boolean ok = false;
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandles.Lookup priv = MethodHandles.privateLookupIn(DipWarPlayer.class, lookup);
            up = priv.findVarHandle(DipWarPlayer.class, UPDATE_INDEX_FIELD, int.class);
            pp = priv.findVarHandle(DipWarPlayer.class, PLAYER_POWER_FIELD, double.class);
            cp = priv.findVarHandle(DipWarPlayer.class, COALITION_POWER_FIELD, double.class);
            bits = priv.findVarHandle(DipWarPlayer.class, WILLING_BITS_FIELD, Bitmap1D.class);
            ok = true;
        } catch (Throwable t) {
            ok = false;
            if (!this.initFailedLogged) {
                this.initFailedLogged = true;
                EventLog.log("SEAM", "VanillaDiplomacyAdapterMH init failed: "
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
            logRuntime("getWillingBits", t);
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ArrayList<?> getWillingList(DipWarPlayer war) {
        // Public Getter — kein Reflection nötig. LIST<FactionNPC> Signatur,
        // aber konkrete Instanz ist ArrayList.
        return war != null ? (ArrayList<?>) (Object) war.willing() : null;
    }

    @Override
    public void setNumericState(DipWarPlayer war, int updateIndexValue,
                                 double playerPowerValue, double coalitionPowerValue) {
        if (!isAvailable() || war == null) {
            return;
        }
        try {
            this.playerPower.set(war, playerPowerValue);
            this.coalitionPower.set(war, coalitionPowerValue);
            this.updateIndex.set(war, updateIndexValue);
        } catch (Throwable t) {
            this.runtimeFailed = true;
            logRuntime("setNumericState", t);
        }
    }

    private void logRuntime(String method, Throwable t) {
        if (!this.runtimeFailedLogged) {
            this.runtimeFailedLogged = true;
            EventLog.log("SEAM", "VanillaDiplomacyAdapterMH." + method + " failed: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + ". Schulden-Puffer dauerhaft inaktiv.");
        }
    }
}
