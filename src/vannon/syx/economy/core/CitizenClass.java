/*
 * CitizenClass — wealth-based social classification with behavioral presets.
 *
 * Phase 3: Bürger-Diversifizierung. Jeder Bürger wird anhand von Vermögen,
 * Eigentum, Herkunft und Familienstruktur in eine von sechs internen Klassen
 * eingestuft. Die Klasse modifiziert Kaufentscheidungen (Haus, Firmen-Anteile).
 *
 * Klassifikation (Priorität von oben nach unten):
 *   BOSS    — besitzt >30% einer Firma (PropertyLedger.shareCount > 0 mit shares >= 30)
 *   HEIR    — geboren (BORN) + hat lebende Eltern + Vermögen > Median
 *   MIGRANT — nicht geboren + Vermögen < 50% des Medians
 *   POOR    — Vermögen < Median
 *   MIDDLE  — Vermögen ∈ [Median, 3× Median)
 *   UPPER   — Vermögen ≥ 3× Median
 *
 * Speicherung: 1 Byte pro Wallet-Slot in Wallets.citizenClass[].
 * Save-Format: Wallets v33 (bump von v32).
 */

package vannon.syx.economy.core;

import init.type.CAUSE_ARRIVES;
import init.type.HCLASSES;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.Induvidual;
import settlement.stats.STATS;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public enum CitizenClass {

    /** Besitzt >30% mindestens einer Firma. Aggressiver Investor. */
    BOSS(6, 2.0, 1.0, "Reicher Firmenboss"),

    /** Geboren, hat lebende Eltern, Vermögen > Median. Lebt vom Erbe. */
    HEIR(5, 1.0, 2.0, "Reicher Erbe"),

    /** Nicht geboren (Immigrant), Vermögen < 50% Median. Frisch angekommen. */
    MIGRANT(4, 0.5, 999.0, "Migrant"),

    /** Vermögen < Median. Kämpft ums Überleben. */
    POOR(3, 0.7, 999.0, "Armer Single/Familie"),

    /** Vermögen ∈ [Median, 3× Median). Stabiler Mittelstand. */
    MIDDLE(2, 1.0, 8.0, "Mittelstand"),

    /** Vermögen ≥ 3× Median. Oberschicht. */
    UPPER(1, 1.5, 3.0, "Gehobene Klasse"),

    /** Fallback — sollte nie vorkommen. */
    UNCLASSIFIED(0, 1.0, 5.0, "Unklassifiziert");

    /** Ordnungszahl für ordinal-basierte Defaults. */
    public final int order;

    /**
     * Multiplikator für die Kaufpreis-Schwelle bei Hauskauf.
     * Basis-Schwelle ist price + price/2 (1.5×). Dieser Wert ersetzt die 1.5.
     * Niedriger = kauft früher. MIGRANT=0.5 (kauft bei 1.0× Preis),
     * BOSS=2.0 (kauft erst bei 2.5× Preis).
     */
    public final double homeBuyMultiplier;

    /**
     * Multiplikator für die Vermögens-Schwelle bei Firmen-Anteil-Kauf.
     * Basis: 5× initialWallet. Dieser Wert multipliziert die 5.
     * 999.0 = kauft nie. BOSS=1.0 (kauft bei 5×), UPPER=3.0 (kauft bei 15×).
     */
    public final double firmBuyThresholdMultiplier;

    /** Deutscher Anzeigename für UI (spätere Verwendung). */
    public final String displayName;

    CitizenClass(int order, double homeBuyMultiplier, double firmBuyThresholdMultiplier, String displayName) {
        this.order = order;
        this.homeBuyMultiplier = homeBuyMultiplier;
        this.firmBuyThresholdMultiplier = firmBuyThresholdMultiplier;
        this.displayName = displayName;
    }

    // —— Klassifikation ——————————————————————————————————————————

    /**
     * Klassifiziere einen Bürger anhand von Vermögen, Eigentum, Herkunft
     * und Familienstruktur.
     *
     * @param h         der Bürger
     * @param wealth    aktuelles Vermögen (netWorth)
     * @param median    aktueller Vermögens-Median der Population
     * @param ledger    PropertyLedger für Eigentums-Prüfung (kann null sein)
     * @return die passende CitizenClass
     */
    public static CitizenClass classify(Humanoid h, int wealth, int median, PropertyLedger ledger) {
        if (h == null) return UNCLASSIFIED;

        Induvidual indu = h.indu();
        if (indu == null) return UNCLASSIFIED;

        // Sklaven und Kinder werden nicht klassifiziert
        if (indu.clas() == HCLASSES.SLAVE()) {
            return UNCLASSIFIED;
        }
        try {
            if (!indu.hType().isWorks()) {
                return UNCLASSIFIED;
            }
        } catch (RuntimeException e) {
            if (!hTypeFailed) { hTypeFailed = true; EventLog.log("SEAM", "CitizenClass: hType().isWorks() failed — " + e.getClass().getSimpleName()); }
        }

        // 1. BOSS: besitzt >30% mindestens einer Firma
        if (ledger != null && ledger.shareCount((long) h.id()) > 0) {
            // Check if any entry has >= 30% shares
            for (PropertyLedger.Entry e : ledger.ownedBy((long) h.id())) {
                if (e.shares() >= 30 && !e.isHome()) {
                    return BOSS;
                }
            }
        }

        // 2. HEIR: geboren + hat lebende Eltern + Vermögen > Median
        boolean born = false;
        try {
            born = STATS.POP().COUNT.arrive.get(indu) == CAUSE_ARRIVES.BORN();
        } catch (RuntimeException e) {
            if (!popFailed) { popFailed = true; EventLog.log("SEAM", "CitizenClass: STATS.POP().COUNT.arrive failed — " + e.getClass().getSimpleName()); }
        }

        if (born && wealth > median && median > 0) {
            try {
                int ref = STATS.REL().reference(indu);
                if (STATS.REL().hasParent(ref)) {
                    return HEIR;
                }
            } catch (RuntimeException e) {
                if (!relFailed) { relFailed = true; EventLog.log("SEAM", "CitizenClass: STATS.REL() failed — " + e.getClass().getSimpleName()); }
            }
        }

        // 3. MIGRANT: nicht geboren + Vermögen < 50% Median
        if (!born && wealth < median / 2 && median > 0) {
            return MIGRANT;
        }

        // 4-6. Wealth-based
        if (median <= 0) {
            return wealth <= 0 ? POOR : MIDDLE;
        }
        if (wealth < median) return POOR;
        if (wealth < median * 3L) return MIDDLE;
        return UPPER;
    }

    /**
     * Effiziente Klassifikation ohne PropertyLedger (fällt zurück auf
     * BOSS-Check über shareCount > 0 als Heuristik).
     */
    public static CitizenClass classifySimple(Humanoid h, int wealth, int median) {
        return classify(h, wealth, median, null);
    }

    /**
     * T7 (B-004): Kanonische Filter-Pruefung. Returns true wenn der Buerger in
     * classify() beruecksichtigt wird (= kein Sklave, hType.isWorks() == true).
     * Verwendet von Wallets.classifyAll() und WealthStats.activePeople().
     */
    public static boolean isClassifiable(Induvidual indu) {
        if (indu == null) return false;
        if (indu.clas() == HCLASSES.SLAVE()) return false;
        try {
            return indu.hType().isWorks();
        } catch (RuntimeException e) {
            // P1-Korrektur: bei API-Crash als NICHT classifiable annehmen — verhindert
            // dass korrupte Buerger in activePeople gezaehlt werden. classify() hat
            // eigenen SEAM-Defensive-Pfad und ueberspringt diese Buerger ebenfalls.
            if (!hTypeFailed) {
                hTypeFailed = true;
                EventLog.log("SEAM", "CitizenClass.isClassifiable: hType().isWorks() failed — " + e.getClass().getSimpleName());
            }
            return false;
        }
    }

    /**
     * T7 (B-004): Zaehlt nur Buerger die NICHT durch classify() gefiltert werden.
     * Konsistente Definition zwischen Wallets.classifyAll() und WealthStats.
     * Die Differenz people - activePeople zeigt jetzt sichtbar wie viele Buerger
     * (Sklaven, Non-Worker) ausgeschlossen sind.
     */
    public static int classifiablePopulationCount(Roster roster) {
        int count = 0;
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid h = roster.get(i);
            if (h == null || h.indu() == null) continue;
            if (isClassifiable(h.indu())) count++;
        }
        return count;
    }

    /** Byte-Kodierung für Wallets-Array */
    private static boolean hTypeFailed = false;
    private static boolean popFailed = false;
    private static boolean relFailed = false;

    // —— Byte-Kodierung für Wallets-Array ————————————————————————
    public byte toByte() {
        return (byte) this.ordinal();
    }

    /** Dekodiere Byte zurück zur Klasse. */
    public static CitizenClass fromByte(byte b) {
        CitizenClass[] values = values();
        int idx = b & 0xFF;
        return idx < values.length ? values[idx] : UNCLASSIFIED;
    }

    /**
     * T13 (P1-kritisch): Session-Reset der 3 SEAM-Failure-Flags.
     * hTypeFailed/popFailed/relFailed sind static und wuerden sonst State der
     * vorigen Session ueberleben — nach Save/Load wuerden neue API-Crashes
     * nicht mehr geloggt. Pattern vgl. TreasuryCrisis.reset().
     */
    public static void reset() {
        hTypeFailed = false;
        popFailed = false;
        relFailed = false;
    }
}
