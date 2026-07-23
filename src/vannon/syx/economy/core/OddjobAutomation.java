package vannon.syx.economy.core;

/**
 * Passt den Tagelöhner-Lohn (oddjobWagePerTask) automatisch an die
 * Wirtschaftslage an — kein manuelles Konfigurieren mehr nötig.
 * 
 * Logik:
 * - Viele offene Firmen-Jobs + Arbeiterknappheit → Oddjob-Lohn sinkt,
 *   Arbeiter werden in Firmen gedrängt.
 * - Wenig Firmen-Aktivität + viele Arbeitslose → Oddjob-Lohn steigt
 *   moderat, Arbeitslose haben zumindest ein Einkommen.
 * - Mittlere Auslastung → Oddjob-Lohn pendelt um den Basiswert.
 * 
 * Gekoppelt an: LaborMarket.meanWage (Firmen-Profitabilität),
 *               Anteil beschäftigter Bürger (Roster).
 */
public final class OddjobAutomation {
    private static final int BASE_WAGE = 3;
    private static final int MIN_WAGE = 1;
    private static final int MAX_WAGE = 20;
    private static int lastAppliedSeason = -1;

    /** Einmal pro Saison aufrufen. */
    public static void autoTune(Roster roster, LaborMarket labor) {
        int season = game.time.TIME.seasons().bitsSinceStart();
        if (season == lastAppliedSeason) return;
        lastAppliedSeason = season;

        if (!EconConfig.oddjobAutoTuneEnabled || !EconConfig.oddjobWageEnabled) return;
        if (roster == null || roster.size() == 0) return;

        double meanWage = labor.meanWage();
        int employed = 0;
        for (int i = 0; i < roster.size(); i++) {
            if (settlement.stats.STATS.WORK().EMPLOYED.get(roster.get(i).indu()) != null) employed++;
        }
        double employmentRate = (double) employed / (double) Math.max(1, roster.size());

        // Basis: 3 Denari
        int wage = BASE_WAGE;

        // Faktor 1 — Firmen-Profitabilität: je höher, desto niedriger der Oddjob-Lohn
        // (Arbeiter sollen in profitable Firmen, nicht tagelöhnern)
        if (meanWage > 100) {
            wage = 1;  // minimale Oddjob-Attraktivität — geh in die Firmen!
        } else if (meanWage > 50) {
            wage = 2;
        }

        // Faktor 2 — Beschäftigungsquote: je mehr Leute schon in Firmen sind,
        // desto niedriger der Oddjob-Lohn für die Verbliebenen
        if (employmentRate > 0.8) {
            wage = Math.max(MIN_WAGE, wage - 1);  // fast Vollbeschäftigung → Oddjob runter
        } else if (employmentRate < 0.3) {
            wage = Math.min(MAX_WAGE, wage + 3);  // viele Arbeitslose → Oddjob rauf als soziales Netz
        }

        wage = Math.max(MIN_WAGE, Math.min(MAX_WAGE, wage));
        if (wage != EconConfig.oddjobWagePerTask) {
            EconConfig.setOddjobWage(wage);
            EventLog.log("ECON", "Oddjob-Lohn auto-angepasst: " + wage
                + " D/Aufgabe (Firmen-Ø-Lohn=" + (int) meanWage
                + ", Beschäftigung=" + (int) (employmentRate * 100) + "%)");
        }
    }

    private OddjobAutomation() {}
}
