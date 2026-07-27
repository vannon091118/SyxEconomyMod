package vannon.syx.economy.core;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.type.HCLASS;
import init.type.HCLASSES;
import settlement.stats.STATS;

/**
 * Automatisierte Steuerung von Behausungs-Einrichtungszielen und Zugangsrechten.
 *
 * <p>Setzt für alle {@link HCLASS} Einrichtungsziele via {@code STATS.HOME().targetSet()}
 * basierend auf dem aktuellen Lagerbestand in {@link FlowMeter.Snapshot}, um permanente 0-Ziel-Krisen
 * zu verhindern.</p>
 */
public final class AccessAutomation {

    /** Mindestens 30 Ticks zwischen Updates. */
    private static final int UPDATE_INTERVAL = 30;
    /** Mindestens 100 Ticks zwischen Fehler-Logs. */
    private static final int ERROR_LOG_INTERVAL = 100;

    private int lastUpdateTick = -UPDATE_INTERVAL;
    /** Globales Rate-Limit über alle Instanzen (es können 15–20 Scanner parallel laufen). */
    private static int lastErrorLogTick = -ERROR_LOG_INTERVAL;
    /** Globales Flag: Zugangserkennung wurde wegen einer Exception deaktiviert. */
    private static boolean accessDetectionDisabled = false;
    /** Tick, an dem die Deaktivierung erfolgte. Fuer Mid-Session-Recovery. */
    private static int disabledAtTick = 0;
    /** Nach 1800 Ticks (~6 Ingame-Tage) wird ein Reaktivierungsversuch unternommen. */
    private static final int RECOVERY_INTERVAL = 1800;

    public void update(FlowMeter.Snapshot snap, int ticks) {
        if (accessDetectionDisabled) {
            // Mid-Session-Recovery: Nach RECOVERY_INTERVAL Ticks Reaktivierung versuchen.
            // B-011: static boolean blieb nach erstem Fehler permanent tot —
            // Housing-Einrichtungsziele funktionierten danach nicht mehr.
            boolean recoveryDue = ticks >= disabledAtTick + RECOVERY_INTERVAL || ticks < disabledAtTick;
            if (recoveryDue) {
                accessDetectionDisabled = false;
                EventLog.log("ACCESS",
                        "AccessAutomation re-enabled — retrying after recovery interval.");
            } else {
                // Rate-limited Erinnerung, dass die Erkennung abgeschaltet ist.
                boolean due = ticks >= lastErrorLogTick + ERROR_LOG_INTERVAL || ticks < lastErrorLogTick;
                if (due) {
                    lastErrorLogTick = ticks;
                    EventLog.log("ACCESS",
                            "AccessAutomation room scan disabled — retry in " + (RECOVERY_INTERVAL - (ticks - disabledAtTick)) + " ticks.");
                }
                return;
            }
        }

        if (ticks - lastUpdateTick < UPDATE_INTERVAL) {
            return;
        }
        lastUpdateTick = ticks;

        if (snap == null || STATS.HOME() == null) {
            return;
        }

        try {
            for (RESOURCE res : RESOURCES.ALL()) {
                if (res == null) continue;
                int resIdx = res.index();
                double stockD = (resIdx >= 0 && resIdx < snap.size()) ? snap.stock(resIdx) : 0.0;
                int stock = (int) Math.max(0.0, stockD);

                for (HCLASS c : HCLASSES.ALL()) {
                    if (c == null) continue;
                    int maxTarget = STATS.HOME().max(c, null, res);
                    if (maxTarget > 0) {
                        int target = 0;
                        if (stock > 50) {
                            target = Math.min(3, maxTarget);
                        } else if (stock > 0) {
                            target = 1;
                        }
                        STATS.HOME().targetSet(target, c, null, res);
                    }
                }
            }
        } catch (RuntimeException t) {  // T-004 (Phase-4.7): catch breit → RuntimeException.
            accessDetectionDisabled = true;
            disabledAtTick = ticks;
            lastErrorLogTick = ticks;
            EventLog.log("ACCESS", "AccessAutomation room scan failed: "
                    + t.getClass().getSimpleName() + " — deaktiviert fuer " + RECOVERY_INTERVAL + " ticks.");
        }
    }

    /**
     * Setzt alle session-persistenten statischen Felder zurueck.
     * Wird beim Save/Load und EconomySim.clearActive()/Konstruktion aufgerufen,
     * damit ein geladenes Savegame nicht die Detection-Disables der vorigen Session erbt.
     * Pattern vgl. TreasuryCrisis.reset() (gleicher State-Leak-Bug-Typ).
     */
    public static void reset() {
        lastErrorLogTick = 0;
        accessDetectionDisabled = false;
        disabledAtTick = 0;
    }
}
