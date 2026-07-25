package vannon.syx.economy.adapter;

import game.boosting.BOOSTABLES;
import game.boosting.Boostable;
import vannon.syx.economy.adapter.seam.BypassGate;
import vannon.syx.economy.adapter.seam.FieldAccessor;
import vannon.syx.economy.core.EventLog;

/**
 * V71.44-Adapter powered by {@link BypassGate}: liest das {@code GOV}-Feld
 * (Boostable) der {@code BOOSTABLES.CIVICS()}-Instanz per VarHandle (primär)
 * mit Reflection-Fallback.
 *
 * <p>Der Feldname {@code GOV} ist source-verifiziert
 * ({@code BOOSTABLES.java:373, public final Boostable GOV in Civic}).</p>
 */
public final class VanillaBoostingAdapter implements ISyxBoosting {

    private final Boostable adminBoostable;
    private final boolean initOk;

    public VanillaBoostingAdapter() {
        BypassGate gate = new BypassGate("VanillaBoostingAdapter");
        Boostable found = null;
        boolean ok = false;
        try {
            Object civics = BOOSTABLES.CIVICS();
            if (civics == null) {
                throw new IllegalStateException("BOOSTABLES.CIVICS() returned null before engine ready");
            }
            FieldAccessor.RefField<Boostable> govField =
                    gate.refField(civics.getClass(), "GOV", Boostable.class);
            if (gate.isAvailable()) {
                found = govField.get(civics);
                ok = found != null;
            }
        } catch (Throwable t) {
            ok = false;
            EventLog.log("SEAM", "VanillaBoostingAdapter init failed: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + ". Industrie-Bonus (+20% Admin) fällt auf No-op zurück.");
        }

        this.adminBoostable = found;
        this.initOk = ok;

        if (this.initOk) {
            EventLog.log("SEAM", "VanillaBoostingAdapter: READY (GOV-Boostable via BypassGate)");
        }
    }

    @Override
    public boolean isAdminBoosterAvailable() {
        return this.initOk;
    }

    @Override
    public Boostable getAdminBoostable() {
        return this.initOk ? this.adminBoostable : null;
    }
}
