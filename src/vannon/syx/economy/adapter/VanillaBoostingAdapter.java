package vannon.syx.economy.adapter;

import game.boosting.BOOSTABLES;
import game.boosting.Boostable;
import java.lang.reflect.Field;
import vannon.syx.economy.core.EventLog;

/**
 * V71.44-Adapter: iteriert die deklarierten Felder der {@code BOOSTABLES.CIVICS()}
 * Instanz und sucht nach einem Feld, dessen Typ {@link Boostable} ist und dessen
 * Name in {@link #CANDIDATE_FIELDS} steht (derzeit: {@code GOV}). Cache-Treffer
 * erfolgt einmalig im Konstruktor.
 *
 * <p>Vor jedem Cache-Treffer wird ein One-Shot-SEAM-Guard geschrieben — kein
 * EventLog-Spam bei wiederholten Erfolgen oder Fehlschlägen.</p>
 */
public final class VanillaBoostingAdapter implements ISyxBoosting {

    /** V71.44-verifizierte Feldnamen, die das Admin-Boostable enthalten. */
    private static final String[] CANDIDATE_FIELDS = {"GOV"};

    private Boostable adminBoostable;
    private boolean available;
    private boolean initFailedLogged;

    public VanillaBoostingAdapter() {
        this.adminBoostable = null;
        this.available = false;
        try {
            Object civics = BOOSTABLES.CIVICS();
            // Defensiv: BOOSTABLES.CIVICS() kann vor Engine-Ready-State null sein.
            if (civics == null) {
                throw new IllegalStateException("BOOSTABLES.CIVICS() returned null before engine ready");
            }
            Boostable found = null;
            // Reflection: probiere die priorisierten Feldnamen in Reihenfolge.
            // Jeder Eintrag in CANDIDATE_FIELDS wird als exakter Field-Name
            // geprüft (kein Substring-Match — sonst Kollisionen mit ähnlichen Namen).
            for (String fieldName : CANDIDATE_FIELDS) {
                if (found != null) break;
                for (Field f : civics.getClass().getDeclaredFields()) {
                    if (f.getName().equals(fieldName) && Boostable.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        found = (Boostable) f.get(civics);
                        break;
                    }
                }
            }
            this.adminBoostable = found;
            this.available = found != null;
            if (this.available) {
                EventLog.log("SEAM", "VanillaBoostingAdapter: READY (GOV-Boostable gefunden)");
            }
        } catch (Throwable t) {
            this.adminBoostable = null;
            this.available = false;
            if (!this.initFailedLogged) {
                this.initFailedLogged = true;
                EventLog.log("SEAM", "VanillaBoostingAdapter init failed: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage()
                        + ". Industrie-Bonus (+20% Admin) fällt auf No-op zurück.");
            }
        }
    }

    @Override
    public boolean isAdminBoosterAvailable() {
        return this.available;
    }

    @Override
    public Boostable getAdminBoostable() {
        return this.available ? this.adminBoostable : null;
    }
}
