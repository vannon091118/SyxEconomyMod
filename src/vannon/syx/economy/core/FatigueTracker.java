package vannon.syx.economy.core;

import java.io.IOException;
import java.util.Arrays;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.Induvidual;
import settlement.stats.STATS;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

/**
 * L-02: Per-Bürger Fatigue-Tracking. Extrahiert aus {@link Wallets} zur
 * God-Class-Guard-Compliance (Wallets 525→≤507 LOC).
 *
 * <p>Speicherung: {@code int[60000]} parallel zu Wallets-Slots.
 * Fatigue wird pro Tick aktualisiert (beschäftigte: +perTick, andere: -recovery).
 * Der {@link FatiguePressure}-Booster liest den Wert und drosselt STAMINA.</p>
 *
 * <p>Save/Load: Wallets v34+ ({@code file.is(this.fatigue)}).</p>
 */
public final class FatigueTracker {

    /** Fatigue-Werte parallel zu Wallets-Slots. 0=fresh, threshold=erschöpft. */
    final int[] fatigue = new int[60000];

    /** Fatigue-Wert eines Bürgers abrufen (via Induvidual → Wallets slot mapping). */
    int getFatigue(int slot) {
        return slot < 0 || slot >= 60000 ? 0 : this.fatigue[slot];
    }

    /**
     * Fatigue für alle Bürger pro Tick aktualisieren.
     * Beschäftigte: +fatiguePerTick. Nicht-beschäftigte: -fatigueRecoveryRate.
     * Wird von {@link EconomySim#update} aufgerufen (post-bootstrap, STATS safe).
     *
     * @param induOf Wallets.induOf[] — Induvidual-Referenz pro Slot
     * @param owner  Wallets.owner[] — Slot-Besitzer (slot aktiv wenn != -1)
     */
    void updateFatigue(Induvidual[] induOf, int[] owner) {
        if (!EconConfig.fatigueEnabled) return;
        int perTick = EconConfig.fatiguePerTick;
        int recovery = EconConfig.fatigueRecoveryRate;
        int max = EconConfig.fatigueRestThreshold * 2; // hard cap at 2× threshold
        for (int slot = 0; slot < 60000; ++slot) {
            if (owner[slot] == -1) continue;
            Induvidual indu = induOf[slot];
            if (indu == null) continue;
            boolean working = STATS.WORK().EMPLOYED.get(indu) != null;
            if (working) {
                this.fatigue[slot] = Math.min(max, this.fatigue[slot] + perTick);
            } else {
                this.fatigue[slot] = Math.max(0, this.fatigue[slot] - recovery);
            }
        }
    }

    /** Neuer Bürger: Fatigue zurücksetzen. */
    void onNewCitizen(int slot) {
        if (slot >= 0 && slot < 60000) this.fatigue[slot] = 0;
    }

    /** Bürger weg: Fatigue zurücksetzen. */
    void onDeparture(int slot) {
        if (slot >= 0 && slot < 60000) this.fatigue[slot] = 0;
    }

    /** Reset aller Fatigue-Werte (Session-Neustart). */
    void reset() {
        Arrays.fill(this.fatigue, 0);
    }

    // —— Save/Load (delegiert von Wallets) ——————————————————————————

    /** Speichern (Wallets v34+). */
    void save(FilePutter file) {
        file.is(this.fatigue);
    }

    /** Laden (Wallets v34+). */
    void load(FileGetter file) throws IOException {
        file.is(this.fatigue);
    }

    /** Laden (Wallets v33 oder älter — Fatigue = 0). */
    void loadLegacy() {
        Arrays.fill(this.fatigue, 0);
    }
}
