package vannon.syx.economy.core;

/**
 * Treasury-Krisenmechanik mit 6 eskalierenden Stufen.
 *
 * <p>Jede Stufe erzwingt konkrete Maßnahmen — keine reinen Warnungen.
 * Der Staat kann nicht beliebig tief ins Minus rutschen: ab Tier 3
 * werden Assets zwangsverkauft, ab Tier 5 wird die Simulation eingefroren.</p>
 *
 * <h3>Stufen</h3>
 * <table>
 *   <tr><th>Tier</th><th>Name</th><th>Schwelle</th><th>Maßnahmen</th><th>Erholung ab</th></tr>
 *   <tr><td>0</td><td>Normal</td><td>&gt; −5K</td><td>—</td><td>—</td></tr>
 *   <tr><td>1</td><td>Warnung</td><td>≤ −5K</td><td>Alle nicht-essenziellen Ausgaben gestoppt: Produktionssubventionen, Transportpauschale, HandoutRelief, OddjobAutoTune</td><td>&gt; 0</td></tr>
 *   <tr><td>2</td><td>Sparprogramm</td><td>≤ −50K</td><td>Tier 1 + ALLE Staatslöhne halbiert (13 Konstanten), Marktsteuer +15%, GrainDole-Headcap auf 50%</td><td>&gt; −10K</td></tr>
 *   <tr><td>3</td><td>Zwangsverkauf</td><td>≤ −250K</td><td>Tier 1+2 + ALLE Staatslager auf Liquidierung, Immobilienmarkt zwangsaktiviert, PropertyMarket erzwungen</td><td>&gt; −50K</td></tr>
 *   <tr><td>4</td><td>Staatsbankrott</td><td>≤ −1M</td><td>Tier 1+2+3 + Kopfsteuer auf Maximum (500), Corvée deaktiviert, GrainDole deaktiviert, Schuldknechtschaft deaktiviert</td><td>&gt; −250K</td></tr>
 *   <tr><td>5</td><td>Kollaps</td><td>≤ −5M</td><td>Tier 1+2+3+4 + LOYALTY auf Minimum (0.5), IMMIGRATION gefroren, alle verbleibenden Ausgaben gestoppt. HARD FLOOR: weitere Ausgaben blockiert.</td><td>&gt; −1M</td></tr>
 * </table>
 *
 * <p>One-Shot-Guards: jede Stufe wird nur beim ersten Übergang geloggt.
 * Erholung wird ebenfalls geloggt. Kein EventLog-Spam bei anhaltender Krise.</p>
 */
public final class TreasuryCrisis {

    /* ── Schwellen ──────────────────────────────────────────────────── */

    private static final long TIER1_WARNING    =    -5_000L;
    private static final long TIER2_AUSTERITY  =   -50_000L;
    private static final long TIER3_FIRESALE   =  -250_000L;
    private static final long TIER4_BANKRUPT   = -1_000_000L;
    private static final long TIER5_COLLAPSE   = -5_000_000L;

    private static final long TIER1_RECOVERY =         0L;
    private static final long TIER2_RECOVERY =   -10_000L;
    private static final long TIER3_RECOVERY =   -50_000L;
    private static final long TIER4_RECOVERY =  -250_000L;
    private static final long TIER5_RECOVERY = -1_000_000L;

    /* ── Gespeicherte Originalwerte für Recovery ────────────────────── */

    // Tier 2: alle 13 Lohnkonstanten
    private static int savedMilitaryTrainingWage;
    private static int savedExportDepotWage;
    private static int savedHaulerWage;
    private static int savedArmySupplyWage;
    private static int savedLaboratoryWage;
    private static int savedLibraryWage;
    private static int savedEmbassyWage;
    private static int savedWaterWage;
    private static int savedCannibalWage;
    private static int savedPoliceWage;
    private static int savedGuardWage;
    private static int savedStockadeWage;
    private static int savedPrisonWage;
    private static int savedDefaultWage;
    private static int savedStateWarehouseWage;
    private static double savedMarketTaxRate;
    private static int savedDoleHeadcap;
    private static int savedPerHeadTax;

    // Was wurde bereits modifiziert?
    private static boolean wagesHalved    = false;
    private static boolean taxesHiked     = false;
    private static boolean doleCapped     = false;
    private static boolean corveeFrozen   = false;
    private static boolean doleFrozen     = false;
    private static boolean debtSlaveryFrozen = false;

    /* ── State ───────────────────────────────────────────────────────── */

    private static int activeTier = 0;
    private static boolean tier1Logged, tier2Logged, tier3Logged,
                           tier4Logged, tier5Logged, recoveryLogged;

    private TreasuryCrisis() {}

    /**
     * Prüft Treasury-Stand und löst Krisenstufen aus oder nimmt sie zurück.
     * Einmal pro Tick aufrufen, NACH den Game-State-Guards.
     *
     * @param treasury aktueller Kontostand
     * @param sim EconomySim für erzwungene Aktionen (Liquidation, etc.)
     */
    public static void update(long treasury, EconomySim sim) {
        int newTier = classify(treasury);

        if (newTier > activeTier) {
            for (int t = activeTier + 1; t <= newTier; t++) {
                activateTier(t, sim);
            }
        } else if (newTier < activeTier) {
            // Hysterese: Recovery nur wenn über der Recovery-Schwelle
            int recoveryTier = classifyRecovery(treasury);
            if (recoveryTier < activeTier) {
                deactivateTiers(recoveryTier);
                newTier = recoveryTier;
            } else {
                // Noch nicht erholt — aktuelles Tier halten
                newTier = activeTier;
            }
        }

        activeTier = newTier;
    }

    public static int activeTier() { return activeTier; }
    public static boolean isInCrisis() { return activeTier >= 1; }
    /** Tier 5 = Hard Floor — keine Ausgaben mehr möglich. */
    public static boolean isHardFloor() { return activeTier >= 5; }

    /* ═══════════════════════════════════════════════════════════════════ */

    /** Aktivierungs-Schwellen (strenger = niedriger). */
    private static int classify(long treasury) {
        if (treasury <= TIER5_COLLAPSE) return 5;
        if (treasury <= TIER4_BANKRUPT) return 4;
        if (treasury <= TIER3_FIRESALE) return 3;
        if (treasury <= TIER2_AUSTERITY) return 2;
        if (treasury <= TIER1_WARNING)  return 1;
        return 0;
    }

    /** Recovery-Schwellen (höher = frühere Erholung → Hysterese). */
    private static int classifyRecovery(long treasury) {
        if (treasury <= TIER5_RECOVERY) return 5;
        if (treasury <= TIER4_RECOVERY) return 4;
        if (treasury <= TIER3_RECOVERY) return 3;
        if (treasury <= TIER2_RECOVERY) return 2;
        if (treasury <= TIER1_RECOVERY) return 1;
        return 0;
    }

    private static void activateTier(int tier, EconomySim sim) {
        switch (tier) {
            case 1 -> activateWarning();
            case 2 -> activateAusterity();
            case 3 -> activateFireSale(sim);
            case 4 -> activateBankrupt();
            case 5 -> activateCollapse();
        }
    }

    /* ── Tier 1: Warnung — nicht-essenzielle Ausgaben stoppen ────────── */

    private static void activateWarning() {
        if (tier1Logged) return;
        tier1Logged = true;
        recoveryLogged = false; // Recovery-Flag für nächste deactivateTiers(0) re-armen

        // Produktionssubventionen deaktivieren
        EconConfig.productionSubsidyMax = 0;

        // Transportpauschale deaktivieren
        EconConfig.transportFeeEnabled = false;
        EconConfig.transportFeePer100TileDay = 0;

        // HandoutRelief stoppen
        EconConfig.handoutToWallet = false;
        EconConfig.handoutWalletAmount = 0;

        // Oddjob-Automation deaktivieren (kein künstlicher Lohn-Push mehr)
        EconConfig.oddjobAutoTuneEnabled = false;
        // Oddjob-Lohn auf Minimum
        EconConfig.setOddjobWage(1);

        // Warehouse-Automation deaktivieren (keine Käufe mehr)
        EconConfig.warehouseAutoTuneEnabled = false;

        // Auto-Beschaffung für Bau deaktivieren
        EconConfig.autoProcureConstruction = false;

        EventLog.log("TREASURY",
                "⚠ STUFE 1 — Staatskasse unter −5K. "
                + "Subventionen, Transportpauschale, Handouts, "
                + "Oddjob-Automation und Lager-Automation gestoppt.");
    }

    /* ── Tier 2: Sparprogramm — alle Löhne halbieren ─────────────────── */

    private static void activateAusterity() {
        activateWarning(); // Tier 1 Maßnahmen kaskadieren
        if (tier2Logged) return;
        tier2Logged = true;

        // Alle 15 Lohnkonstanten halbieren
        if (!wagesHalved) {
            savedDefaultWage           = EconConfig.defaultWage;
            savedStateWarehouseWage    = EconConfig.stateWarehouseWage;
            savedMilitaryTrainingWage  = EconConfig.militaryTrainingWagePerDay;
            savedExportDepotWage       = EconConfig.exportDepotWagePerDay;
            savedHaulerWage            = EconConfig.haulerWagePerDay;
            savedArmySupplyWage        = EconConfig.armySupplyWagePerDay;
            savedLaboratoryWage        = EconConfig.laboratoryWagePerDay;
            savedLibraryWage           = EconConfig.libraryWagePerDay;
            savedEmbassyWage           = EconConfig.embassyWagePerDay;
            savedWaterWage             = EconConfig.waterWagePerDay;
            savedCannibalWage          = EconConfig.cannibalWagePerDay;
            savedPoliceWage            = EconConfig.policeWagePerDay;
            savedGuardWage             = EconConfig.guardWagePerDay;
            savedStockadeWage          = EconConfig.stockadeWagePerDay;
            savedPrisonWage            = EconConfig.prisonWagePerDay;

            EconConfig.defaultWage               = Math.max(1, savedDefaultWage / 2);
            EconConfig.stateWarehouseWage        = Math.max(1, savedStateWarehouseWage / 2);
            EconConfig.militaryTrainingWagePerDay= Math.max(1, savedMilitaryTrainingWage / 2);
            EconConfig.exportDepotWagePerDay     = Math.max(1, savedExportDepotWage / 2);
            EconConfig.haulerWagePerDay          = Math.max(1, savedHaulerWage / 2);
            EconConfig.armySupplyWagePerDay      = Math.max(1, savedArmySupplyWage / 2);
            EconConfig.laboratoryWagePerDay      = Math.max(1, savedLaboratoryWage / 2);
            EconConfig.libraryWagePerDay         = Math.max(1, savedLibraryWage / 2);
            EconConfig.embassyWagePerDay         = Math.max(1, savedEmbassyWage / 2);
            EconConfig.waterWagePerDay           = Math.max(1, savedWaterWage / 2);
            EconConfig.cannibalWagePerDay        = Math.max(1, savedCannibalWage / 2);
            EconConfig.policeWagePerDay          = Math.max(1, savedPoliceWage / 2);
            EconConfig.guardWagePerDay           = Math.max(1, savedGuardWage / 2);
            EconConfig.stockadeWagePerDay        = Math.max(1, savedStockadeWage / 2);
            EconConfig.prisonWagePerDay          = Math.max(1, savedPrisonWage / 2);
            wagesHalved = true;
        }

        // Marktsteuer erhöhen
        if (!taxesHiked) {
            savedMarketTaxRate = EconConfig.marketTaxRate;
            EconConfig.marketTaxRate = Math.min(1.0, savedMarketTaxRate + 0.15);
            taxesHiked = true;
        }

        // GrainDole-Headcap auf 50%
        if (!doleCapped) {
            savedDoleHeadcap = EconConfig.doleHeadcap;
            EconConfig.doleHeadcap = Math.max(10, savedDoleHeadcap / 2);
            doleCapped = true;
        }

        EventLog.log("TREASURY",
                "⚠⚠ STUFE 2 — Staatskasse unter −50K. "
                + "ALLE Staatslöhne halbiert (15 Konstanten), "
                + "Marktsteuer +15%, GrainDole-Headcap auf 50%.");
    }

    /* ── Tier 3: Zwangsverkauf — alle Staatslager liquidieren ────────── */

    private static void activateFireSale(EconomySim sim) {
        activateAusterity(); // Tier 1+2 kaskadieren
        if (tier3Logged) return;
        tier3Logged = true;

        // Immobilienmarkt zwangsaktivieren
        EconConfig.propertyMarketEnabled = true;
        EconConfig.homePurchaseEnabled   = true;
        EconConfig.workplaceSharesEnabled = true;

        // ALLE Staatslager auf Liquidierung setzen
        if (sim != null && sim.stateWarehouses() != null) {
            sim.stateWarehouses().setAllLiquidating(true);
        }

        EventLog.log("TREASURY",
                "⚠⚠⚠ STUFE 3 — ZWANGSVERKAUF: Staatskasse unter −250K. "
                + "ALLE Staatslager liquidieren, Immobilienmarkt erzwungen, "
                + "Firmenanteile freigegeben.");
    }

    /* ── Tier 4: Staatsbankrott ──────────────────────────────────────── */

    private static void activateBankrupt() {
        activateFireSale(null); // Tier 3 kaskadieren (sim via Loop, null OK — Property-Market + Liquidation via Tier 3)
        if (tier4Logged) return;
        tier4Logged = true;

        // Immobilienmarkt trotzdem an (falls Tier 3 übersprungen — sim war null)
        EconConfig.propertyMarketEnabled = true;
        EconConfig.homePurchaseEnabled   = true;

        // Kopfsteuer-Original IMMER sichern, bevor wir es überschreiben
        savedPerHeadTax = EconConfig.perHeadTax;

        // Kopfsteuer auf Maximum
        if (!taxesHiked) {
            savedMarketTaxRate = EconConfig.marketTaxRate;
            EconConfig.marketTaxRate = 0.50; // 50%
            taxesHiked = true;
        } else {
            // taxesHiked von Tier 2 — nur perHeadTax nochmal pushen
            EconConfig.marketTaxRate = 0.50;
        }
        EconConfig.perHeadTax = 500; // Maximum

        // Corvée deaktivieren
        if (!corveeFrozen) {
            EconConfig.corveeEnabled = false;
            corveeFrozen = true;
        }

        // GrainDole komplett deaktivieren
        if (!doleFrozen) {
            EconConfig.grainDoleEnabled = false;
            doleFrozen = true;
        }

        // Schuldknechtschaft deaktivieren (keine neuen Sklaven)
        if (!debtSlaveryFrozen) {
            EconConfig.debtSlaveryEnabled = false;
            debtSlaveryFrozen = true;
        }

        // Wages komplett deaktivieren
        EconConfig.wagesEnabled = false;

        EventLog.log("TREASURY",
                "⚠⚠⚠⚠ STUFE 4 — STAATSBANKROTT: Staatskasse unter −1M. "
                + "Kopfsteuer=500, Marktsteuer=50%, Corvée/GrainDole/"
                + "Schuldknechtschaft/Wages deaktiviert.");
    }

    /* ── Tier 5: Kollaps — Hard Floor ────────────────────────────────── */

    private static void activateCollapse() {
        activateBankrupt(); // Tier 1+2+4 kaskadieren
        if (tier5Logged) return;
        tier5Logged = true;

        // Alle verbleibenden Ausgaben stoppen
        EconConfig.stateWarehousesEnabled = false;
        EconConfig.warehouseMarketEnabled = false;
        EconConfig.laborMarketEnabled = false;
        EconConfig.firmLedgerEnabled = false;
        EconConfig.serviceMarketEnabled = false;
        EconConfig.maintenanceMarketEnabled = false;
        EconConfig.consumptionGateEnabled = false;
        EconConfig.religionTaxEnabled = false;
        EconConfig.liturgyEnabled = false;
        EconConfig.militaryPayrollEnabled = false;

        // LOYALTY auf Minimum
        EconConfig.loyaltyAtMaxGini = 0.50; // −50% Loyalty

        EventLog.log("TREASURY",
                "☠☠☠☠☠ STUFE 5 — KOLLAPS: Staatskasse unter −5M. "
                + "ALLE Wirtschaftssysteme deaktiviert. "
                + "LOYALTY auf Minimum (−50%). HARD FLOOR erreicht.");
    }

    /* ── Recovery ────────────────────────────────────────────────────── */

    private static void deactivateTiers(int newMaxTier) {
        // Rückwärts deaktivieren: höhere Tiers zuerst

        if (activeTier >= 5 && newMaxTier < 5) revertCollapse();
        if (activeTier >= 4 && newMaxTier < 4) revertBankrupt();
        if (activeTier >= 3 && newMaxTier < 3) revertFireSale();
        if (activeTier >= 2 && newMaxTier < 2) revertAusterity();
        if (activeTier >= 1 && newMaxTier < 1) revertWarning();

        if (newMaxTier == 0 && activeTier > 0) {
            if (!recoveryLogged) {
                recoveryLogged = true;
                EventLog.log("TREASURY",
                        "✓ Staatskasse erholt — alle Krisenstufen deaktiviert. "
                        + "Systeme auf Ursprungswerte zurückgesetzt.");
            }
            tier1Logged = tier2Logged = tier3Logged = tier4Logged = tier5Logged = false;
        }
        // Hinweis: recoveryLogged-Reset passiert in activateWarning() beim Re-Arm
        // der Tier-1-Maschine — nicht hier, sonst wird der nächste Recovery-Log
        // schon im selben deactivateTiers(0)-Call wieder unterdrückt.
    }

    private static void revertWarning() {
        if (!tier1Logged) return;
        // Subventionen, Transport, Handouts, Oddjob, Warehouse-Automation wiederherstellen
        EconConfig.productionSubsidyMax = 1000;
        EconConfig.transportFeeEnabled = true;
        EconConfig.transportFeePer100TileDay = EconConfig.DEFAULT_TRANSPORT_FEE_PER_100_TILE_DAY;
        EconConfig.handoutToWallet = true;
        EconConfig.handoutWalletAmount = 400;
        EconConfig.oddjobAutoTuneEnabled = true;
        EconConfig.setOddjobWage(EconConfig.DEFAULT_ODDJOB_WAGE_PER_TASK);
        EconConfig.warehouseAutoTuneEnabled = true;
        EconConfig.autoProcureConstruction = true;
    }

    private static void revertAusterity() {
        if (!tier2Logged) return;
        if (wagesHalved) {
            EconConfig.defaultWage                = savedDefaultWage;
            EconConfig.stateWarehouseWage         = savedStateWarehouseWage;
            EconConfig.militaryTrainingWagePerDay = savedMilitaryTrainingWage;
            EconConfig.exportDepotWagePerDay      = savedExportDepotWage;
            EconConfig.haulerWagePerDay           = savedHaulerWage;
            EconConfig.armySupplyWagePerDay       = savedArmySupplyWage;
            EconConfig.laboratoryWagePerDay       = savedLaboratoryWage;
            EconConfig.libraryWagePerDay          = savedLibraryWage;
            EconConfig.embassyWagePerDay          = savedEmbassyWage;
            EconConfig.waterWagePerDay            = savedWaterWage;
            EconConfig.cannibalWagePerDay         = savedCannibalWage;
            EconConfig.policeWagePerDay           = savedPoliceWage;
            EconConfig.guardWagePerDay            = savedGuardWage;
            EconConfig.stockadeWagePerDay         = savedStockadeWage;
            EconConfig.prisonWagePerDay           = savedPrisonWage;
            wagesHalved = false;
        }
        if (taxesHiked) {
            EconConfig.marketTaxRate = savedMarketTaxRate;
            taxesHiked = false;
        }
        if (doleCapped) {
            EconConfig.doleHeadcap = savedDoleHeadcap;
            doleCapped = false;
        }
    }

    private static void revertFireSale() {
        if (!tier3Logged) return;
        // Liquidation wird nicht automatisch zurückgenommen (Spieler-Entscheidung).
        // Der EventLog-Hinweis macht die manuelle Ruecknahme-Pflicht sichtbar.
        EventLog.log("TREASURY", "Recovery Tier3: Liquidation (Fire-Sale) nicht automatisch rueckgaengig. Spieler muss manuell eingreifen (siehe StateWarehouses/Fire-Sale-Knopf).");
    }

    private static void revertBankrupt() {
        if (!tier4Logged) return;
        if (corveeFrozen)   { EconConfig.corveeEnabled = true;       corveeFrozen = false; }
        if (doleFrozen)     { EconConfig.grainDoleEnabled = true;    doleFrozen = false; }
        if (debtSlaveryFrozen) { EconConfig.debtSlaveryEnabled = true; debtSlaveryFrozen = false; }
        EconConfig.wagesEnabled = true;
        // Steuern revertieren — savedPerHeadTax wird JETZT IMMER gesichert (Bugfix)
        if (taxesHiked) {
            EconConfig.perHeadTax = savedPerHeadTax;
            EconConfig.marketTaxRate = savedMarketTaxRate;
            taxesHiked = false;
        }
    }

    private static void revertCollapse() {
        if (!tier5Logged) return;
        EconConfig.stateWarehousesEnabled = true;
        EconConfig.warehouseMarketEnabled = true;
        EconConfig.laborMarketEnabled = true;
        EconConfig.firmLedgerEnabled = true;
        EconConfig.serviceMarketEnabled = true;
        EconConfig.maintenanceMarketEnabled = true;
        EconConfig.consumptionGateEnabled = true;
        EconConfig.religionTaxEnabled = true;
        EconConfig.liturgyEnabled = true;
        EconConfig.militaryPayrollEnabled = true;
        EconConfig.loyaltyAtMaxGini = 0.85;
    }

    /* ── State-Reset (fuer Save/Load- und Test-Isolation) ──────────────── */

    /**
     * Setzt alle statischen TierCrisis-Felder zurueck auf Initial-Werte.
     * Wird aufgerufen:
     *   - in EconomySim.clearActive() (Test-Reset + Save/Load-Reset)
     *   - in EconomySim()-no-arg-Konstruktor (defensiv bei frischem Spielstand)
     *
     * Begruendung: TreasuryCrisis haelt 31+ statische Felder. Ohne expliziten
     * Reset laeuft ein Save/Load in eine inkonsistente Recovery-Phase hinein,
     * weil die saved*Wage-Stash aus der vorigen Session stammt und beim ersten
     * deactivateTiers(0) auf den frisch geladenen EconConfig zurueck-
     * geschrieben wird. Recovery ist nicht idempotent ueber Session-Grenzen.
     *
     * Muss idempotent sein: Mehrfach-Aufruf == einzelner Aufruf.
     */
    public static void reset() {
        // saved*-Stash (17 Wage + 3 Tax/Headcap/Head-Tax = 20 Felder)
        savedMilitaryTrainingWage = 0;
        savedExportDepotWage      = 0;
        savedHaulerWage           = 0;
        savedArmySupplyWage       = 0;
        savedLaboratoryWage       = 0;
        savedLibraryWage          = 0;
        savedEmbassyWage          = 0;
        savedWaterWage            = 0;
        savedCannibalWage         = 0;
        savedPoliceWage           = 0;
        savedGuardWage            = 0;
        savedStockadeWage         = 0;
        savedPrisonWage           = 0;
        savedDefaultWage          = 0;
        savedStateWarehouseWage   = 0;
        savedMarketTaxRate        = 0.0;
        savedDoleHeadcap          = 0;
        savedPerHeadTax           = 0;

        // Action-Flags (6)
        wagesHalved       = false;
        taxesHiked        = false;
        doleCapped        = false;
        corveeFrozen      = false;
        doleFrozen        = false;
        debtSlaveryFrozen = false;

        // Tier- und Recovery-State (7)
        activeTier   = 0;
        tier1Logged  = false;
        tier2Logged  = false;
        tier3Logged  = false;
        tier4Logged  = false;
        tier5Logged  = false;
        recoveryLogged = false;
    }
}
