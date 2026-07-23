package vannon.syx.economy.core;

/**
 * Ring buffer of up to 6 EconSnapshots with trend detection.
 * No vanilla imports needed — pure mod-internal math.
 * Hooked into EconomySim.update() after audit, before render caches.
 */
import vannon.syx.economy.core.EventLog;

public final class EconIndicators {

    private static final int MAX_SNAPSHOTS = 20;
    private final EconSnapshot[] ring = new EconSnapshot[MAX_SNAPSHOTS];
    private int head = 0;
    private int count = 0;

    // Trend flags (computed from snapshot diffs)
    private boolean inequalityRising;
    private boolean wagesFalling;
    private boolean treasuryDeclining;
    private boolean emigrationSpike;
    private boolean furnishingCrisis;

    private boolean wasTreasuryDeclining = false;
    private boolean wasWagesFalling = false;
    private boolean wasFurnishingCrisis = false;

    public boolean isInequalityRising() { return inequalityRising; }
    public boolean isWagesFalling() { return wagesFalling; }
    public boolean isTreasuryDeclining() { return treasuryDeclining; }
    public boolean isEmigrationSpike() { return emigrationSpike; }
    public boolean isFurnishingCrisis() { return furnishingCrisis; }
    /** Setzt den Furnishing-Crisis-Status und loggt den Zustandswechsel via EventLog. */
    public void setFurnishingCrisis(boolean crisis) {
        if (crisis && !this.wasFurnishingCrisis) {
            EventLog.log("ECON", "Einrichtungs-Krise: Holz-Versorgung kritisch - mehr Holzfäller bauen oder Holz am Markt kaufen.");
        }
        this.wasFurnishingCrisis = crisis;
        this.furnishingCrisis = crisis;
    }

    // Thresholds (from COVERAGE_AUDIT_FINAL_2026-07-23.md)
    public static double GINI_WARNING = 0.35;
    public static int EMIGRATION_SPIKE = 3;
    public static int TREND_PERIODS = 3;

    /**
     * Insert new snapshot. Ring buffer overwrites oldest entry.
     */
    public void update(EconSnapshot snap) {
        // Compute trend from previous snapshot
        if (count >= 1) {
            EconSnapshot prev = ring[(head - 1 + MAX_SNAPSHOTS) % MAX_SNAPSHOTS];
            if (prev != null) {
                computeTrends(prev, snap);
            }
        }

        // Insert new snapshot
        ring[head] = snap;
        head = (head + 1) % MAX_SNAPSHOTS;
        if (count < MAX_SNAPSHOTS) count++;
    }

    private void computeTrends(EconSnapshot prev, EconSnapshot curr) {
        // Gini rising AND above threshold
        this.inequalityRising = curr.gini > prev.gini && curr.gini > GINI_WARNING;

        // Wage decreased
        this.wagesFalling = curr.meanWage < prev.meanWage;

        // Treasury shrinking
        long prevIncome = prev.headTax + prev.marketReceipts;
        long currIncome = curr.headTax + curr.marketReceipts;
        this.treasuryDeclining = currIncome < prevIncome;

        // Emigration spike
        int emigrationDelta = curr.emigrations - prev.emigrations;
        this.emigrationSpike = emigrationDelta > EMIGRATION_SPIKE;

        // Visibility: trend reversal is logged to the chronicle (only on change).
        if (this.wagesFalling && !this.wasWagesFalling) {
            EventLog.log("TREND", "Durchschnittslohn sinkt.");
        }
        if (this.treasuryDeclining && !this.wasTreasuryDeclining) {
            EventLog.log("TREND", "Staatseinnahmen ruecklaeufig -- Sparmassnahmen eingeleitet.");
        }

        // Real consequence: persistent revenue decline forces austerity measures.
        if (this.treasuryDeclining) {
            EconConfig.doleHeadcap = Math.max(0, (int) (EconConfig.doleHeadcapBase * 0.85));
        } else {
            EconConfig.doleHeadcap = EconConfig.doleHeadcapBase;
        }

        this.wasTreasuryDeclining = this.treasuryDeclining;
        this.wasWagesFalling = this.wagesFalling;

        // Furnishing crisis: wood demand exceeds supply at minimal stock.
        // Detects the classic "furnishing 12%" situation from the GUI audit.
        this.setFurnishingCrisis(FurnishingAutomation.detectCrisis(curr));
    }

    /**
     * Latest snapshot or null.
     */
    public EconSnapshot latest() {
        if (count == 0) return null;
        int idx = (head - 1 + MAX_SNAPSHOTS) % MAX_SNAPSHOTS;
        return ring[idx];
    }

    /**
     * Number of stored snapshots.
     */
    public int count() {
        return count;
    }

    /**
     * Snapshot at index (0 = oldest, count-1 = newest).
     */
    public EconSnapshot get(int index) {
        if (index < 0 || index >= count) return null;
        int actual = (head - count + index + MAX_SNAPSHOTS) % MAX_SNAPSHOTS;
        return ring[actual]; // null-safe: ring slots start as null
    }
}