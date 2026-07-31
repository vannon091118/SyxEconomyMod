package vannon.syx.economy.core;

/**
 * Sprint v0.13.103+ — 5-Tier-Staircase für max-Worker abhängig von Stock-Coverage.
 *
 * <p>User-Auftrag wörtlich: "stelle regel ein das 10-70-5-30-10% abstufungen an
 * maximaler arbeitszahl abhängig von nachfrage festsetzt". 5 Tier-Stufen mit
 * ascending Coverage-Breakpoints und descending Worker-Fractions:</p>
 *
 * <pre>
 *   Tier  Coverage-Bereich   Worker-Fraction   Beispiel (max=45)
 *   ─────────────────────────────────────────────────────────────
 *    0    coverage ≤ 0.00     100% (= max)        45  (CRITICAL catch-up)
 *    1    coverage ≤ 0.30      70%                32  (knapp)
 *    2    coverage ≤ 0.70      30%                14  (komfortabel)
 *    3    coverage ≤ 1.00      10%                 5  (1-Tage-Vorrat)
 *    4    coverage >  1.00      5%                 2  (Surplus, idle)
 * </pre>
 *
 * <p>Staatsbestand-Override: wenn {@code coverage < firmStaatsbestandMinCoverage}
 * (default 0.10), wird der Cap auf 100% (maxCapacity) forciert — auch wenn die
 * Staircase sonst einen niedrigeren Tier liefern würde. Das ist der "kritisch
 * eingehalten werden als Priorität"-Mechanismus: bei leerem MOEBEL-Lager
 * läuft der Carpenter mit voller Kapazität, unabhängig von der normalen
 * Demand-Heuristik.</p>
 *
 * <p>Performance: O(min(5, tierCount)) = O(1) Iterationsschritte. Kein
 * allokierter State pro Aufruf — alle Werte kommen aus {@link EconConfig}.</p>
 */
public final class FirmStaircase {

    private FirmStaircase() {}

    /**
     * Bestimmt den Tier-Index für eine gegebene Stock-Coverage.
     * @param coverage stock / demand, ≥ 0. Werte &lt; 0 werden als 0 behandelt.
     * @return Tier-Index 0..tiers.length-1. Bei coverage > alle Tiers → letzter Tier.
     */
    public static int getTier(double coverage) {
        if (!EconConfig.firmStaircaseEnabled) return 0;
        double safe = Math.max(0.0, coverage);
        double[] tiers = EconConfig.firmStaircaseCoverageTiers;
        for (int i = 0; i < tiers.length; i++) {
            if (safe <= tiers[i]) return i;
        }
        return tiers.length - 1;
    }

    /**
     * Berechnet das effektive max-Worker-Limit basierend auf Stock-Coverage.
     * Staatsbestand-Override: bei stock &lt; firmStaatsbestandMinCoverage wird
     * 100% (full capacity) zurückgegeben, auch wenn die Staircase weniger liefern würde.
     * @param maxCapacity blueprint-max (z.B. 45 für Carpenter)
     * @param coverage stock / demand
     * @return int max-Worker-Cap, mindestens 1 wenn maxCapacity > 0
     */
    public static int scaleMax(int maxCapacity, double coverage) {
        if (maxCapacity <= 0) return 0;
        if (!EconConfig.firmStaircaseEnabled) return maxCapacity;
        // Staatsbestand-Override: kritischer Lagerstand → volle Kapazität.
        if (isStaatsbestandCritical(coverage)) return maxCapacity;
        int tier = getTier(coverage);
        double[] fracs = EconConfig.firmStaircaseWorkerFractions;
        double fraction = (tier < fracs.length) ? fracs[tier] : fracs[fracs.length - 1];
        int capped = (int) Math.round(maxCapacity * fraction);
        return Math.max(1, capped);
    }

    /**
     * @return true wenn stock coverage &lt; firmStaatsbestandMinCoverage (default 10%).
     *         Coverage &lt; 0 oder nicht-finite → false (kein State-Override).
     */
    public static boolean isStaatsbestandCritical(double coverage) {
        if (!EconConfig.firmStaatsbestandEnabled) return false;
        if (!Double.isFinite(coverage) || coverage < 0.0) return false;
        return coverage < EconConfig.firmStaatsbestandMinCoverage;
    }
}
