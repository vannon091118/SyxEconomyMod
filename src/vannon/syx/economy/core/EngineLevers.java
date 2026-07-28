package vannon.syx.economy.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * EngineLevers — Config-Toggles für ALLE Vanilla-Engine-Zugriffe.
 *
 * <p>Analog zu {@link EconConfig} für Wirtschaftsparameter. Jeder Zugriff auf
 * die Vanilla-Engine wird über einen {@code boolean}-Toggle gesteuert
 * (default: {@code true}). Bei Vanilla-Update (V71.44→V72) können einzelne
 * Zugriffe deaktiviert werden, bis der Adapter angepasst ist.</p>
 *
 * <p>Organisiert nach den 4 Sub-Interfaces des EngineMirror:
 * {@code IRoomAccess}, {@code IFactionAccess}, {@code IHumanoidAccess},
 * {@code IStatsAccess}. Die Master-Toggles pro Sub-Interface erlauben
 * granulares Abschalten bei Engine-Inkompatibilität.</p>
 *
 * <p>Version-gebunden für Songs of Syx V71.44. SDK-Generic kommt später.</p>
 */
public final class EngineLevers {

    // ─── Global ─────────────────────────────────────────────
    /** Master-Switch für den gesamten EngineMirror. false = alle Zugriffe deaktiviert. */
    public static boolean engineMirrorEnabled = true;
    /** Aktiviert CSV-Logging für jeden Mirror-Zugriff via LoggingAdapter. */
    public static boolean engineMirrorLoggingEnabled = false; // DC-01: 99.6% debug.csv-Reduktion — nur Summary-Events
    /** Dump aller Hebel bei Startup via EventLog. */
    public static boolean engineMirrorDumpOnStartup = true;

    // ═══ IRoomAccess ═══════════════════════════════════════
    /** Master-Switch für alle Room-Zugriffe. */
    public static boolean roomAccessEnabled = true;
    /** Sub-Master: Stockpile-Zugriffe (Lager). */
    public static boolean stockpileAccessEnabled = true;
    /** Sub-Master: Transport-Zugriffe. */
    public static boolean transportAccessEnabled = true;
    /** Sub-Master: Raum-Iteration (Blueprints, Eateries, etc.). */
    public static boolean roomIterationEnabled = true;
    /** Sub-Master: Service & Employment. */
    public static boolean serviceEmploymentEnabled = true;

    // ─── Stockpile Read ─────────────────────────────────────
    /** {@code storedD(RESOURCE)} — Lager-Füllstand als Verhältnis 0.0–1.0. */
    public static boolean stockpileStoredDEnabled = true;
    /** {@code getUsedSpace()} — Gesamte belegte Lagerkapazität. */
    public static boolean stockpileUsedSpaceEnabled = true;
    /** {@code crateSize()} / {@code crateSize(RESOURCE)} — Crate-Kapazität. */
    public static boolean stockpileCrateSizeEnabled = true;
    /** {@code totalCrates()} — Anzahl der Crates im Stockpile. */
    public static boolean stockpileTotalCratesEnabled = true;
    /** {@code getSpecialAmount(RESOURCE)} — Benutzer-Limit pro Ressource. */
    public static boolean stockpileSpecialAmountReadEnabled = true;
    /** {@code moveCapacityAm(RESOURCE)} — Verbleibende Transportkapazität. */
    public static boolean stockpileMoveCapacityAmEnabled = true;

    // ─── Stockpile Write ────────────────────────────────────
    /** {@code storingSet(boolean)} — Physikalische Lager-Sperre (via BypassGate). */
    public static boolean stockpileStoringSetEnabled = true;
    /** {@code fetchingSet(boolean)} — Fetch-Status setzen (via BypassGate). */
    public static boolean stockpileFetchingSetEnabled = true;
    /** {@code setSpecialAmount(RESOURCE, int)} — Benutzer-Limit setzen (via BypassGate). */
    public static boolean stockpileSetSpecialAmountEnabled = true;

    // ─── Transport Read ─────────────────────────────────────
    /** {@code distance}-Feld — Echte Transportdistanz (via BypassGate). */
    public static boolean transportDistanceEnabled = true;
    /** {@code efficiency()} — Gesamt-Effizienz der Transportstation. */
    public static boolean transportEfficiencyEnabled = true;
    /** {@code fetchTime}-Feld — Aktuelle Fetch-Zeit. */
    public static boolean transportFetchTimeEnabled = true;
    /** {@code stationWorkers}-Feld — Arbeiter pro Station. */
    public static boolean transportStationWorkersEnabled = true;
    /** {@code stationProblem}-Feld — Station-Problem-Flag. */
    public static boolean transportStationProblemEnabled = true;
    /** {@code resource()} — Aktuelle Transport-Ressource. */
    public static boolean transportResourceEnabled = true;
    /** {@code radiusRaw()} — Transport-Radius. */
    public static boolean transportRadiusRawEnabled = true;

    // ─── Transport Write ────────────────────────────────────
    /** {@code radiusRawSet(byte)} — Transport-Radius setzen. */
    public static boolean transportRadiusRawSetEnabled = true;

    // ─── Room Iteration ─────────────────────────────────────
    /** {@code SETT.ROOMS().ins()} — Alle Raum-Blueprints. */
    public static boolean roomInsEnabled = true;
    /** {@code SETT.ROOMS().EATERIES} — Alle Gasthäuser. */
    public static boolean roomEateriesEnabled = true;
    /** {@code SETT.ROOMS().CANTEENS} — Alle Kantinen. */
    public static boolean roomCanteensEnabled = true;
    /** {@code SETT.ROOMS().HOME} — Wohnhäuser. */
    public static boolean roomHomeEnabled = true;
    /** {@code SETT.ROOMS().CHAMBER} — Kammern. */
    public static boolean roomChamberEnabled = true;
    /** {@code SETT.ROOMS().JANITOR} — Hausmeister. */
    public static boolean roomJanitorEnabled = true;
    /** {@code SETT.ROOMS().STATION} — Bahnhof (Station). Gap G8. */
    public static boolean roomStationEnabled = true;

    // ─── Service & Employment ───────────────────────────────
    /** {@code SETT.ENTITIES() != null} — Engine-Verfügbarkeit. */
    public static boolean entitiesAvailableEnabled = true;
    /** {@code serviceCapacity(RoomService)} — Service-Kapazität. */
    public static boolean serviceCapacityEnabled = true;
    /** {@code setFirmTarget(RoomInstance, int)} — Beschäftigungsziel setzen. */
    public static boolean setFirmTargetEnabled = true;
    /** {@code employedRoom(Humanoid)} — Beschäftigungsraum lesen. */
    public static boolean employedRoomEnabled = true;

    // ═══ IFactionAccess (A-03) ══════════════════════════════
    /** Master-Switch für alle Faction-Zugriffe. */
    public static boolean factionAccessEnabled = true;

    // ─── NPC ────────────────────────────────────────────────
    public static boolean npcPriceReadEnabled = true;
    public static boolean npcPriceWriteEnabled = true;
    public static boolean npcTreasuryReadEnabled = true;
    public static boolean npcTreasuryWriteEnabled = true;
    public static boolean npcStockpileEnabled = true;
    public static boolean npcBonusEnabled = true;
    public static boolean npcRequestEnabled = true;
    public static boolean npcRaceEnabled = true;
    public static boolean npcCitizensEnabled = true;
    public static boolean npcMilitaryEnabled = true;

    // ─── Diplomacy ──────────────────────────────────────────
    public static boolean diplomacyWarPowerReadEnabled = true;
    public static boolean diplomacyWarPowerWriteEnabled = true;
    public static boolean diplomacyCoalitionReadEnabled = true;
    public static boolean diplomacyCoalitionWriteEnabled = true;
    public static boolean diplomacyDistressEnabled = true;
    public static boolean diplomacyWillingReadEnabled = true;
    public static boolean diplomacyWillingWriteEnabled = true;
    public static boolean diplomacyPotentialEnabled = true;
    public static boolean diplomacyProxyEnabled = true;

    // ─── Trade ──────────────────────────────────────────────
    public static boolean tradeWorldPriceEnabled = true;
    public static boolean tradeTollEnabled = true;
    public static boolean tradeTariffEnabled = true;
    public static boolean tradeBuyerSellerEnabled = true;

    // ─── Royalty ────────────────────────────────────────────
    public static boolean royaltyOpinionEnabled = true;
    public static boolean royaltyOpinionReadEnabled = true;
    public static boolean royaltyOpinionWriteEnabled = false; // DIPLO-03: opt-in, default off
    public static boolean royaltyTrustEnabled = true;
    public static boolean royaltyTrustReadEnabled = true;
    public static boolean royaltyCourtEnabled = true;
    public static boolean royaltyKingEnabled = true;

    // ─── Player ─────────────────────────────────────────────
    public static boolean playerCreditsEnabled = true;

    // ═══ IHumanoidAccess (A-04) ═════════════════════════════
    /** Master-Switch für alle Humanoid-Zugriffe. */
    public static boolean humanoidAccessEnabled = true;

    // ─── AI Plans ───────────────────────────────────────────
    public static boolean aiOddjobDetectionEnabled = true;
    public static boolean aiFoodPlanDetectionEnabled = true;
    public static boolean aiTavernPlanDetectionEnabled = true;
    public static boolean aiMarketPlanDetectionEnabled = true;
    public static boolean aiWorkModuleDetectionEnabled = true;
    public static boolean aiCrimeModuleDetectionEnabled = true;

    // ─── Humanoid Stats ─────────────────────────────────────
    public static boolean hungerAccessEnabled = true;
    public static boolean religionAccessEnabled = true;
    public static boolean workStatusEnabled = true;
    public static boolean employmentAccessEnabled = true;
    /** Sklaven-Status: isEnslaveablePleb(), enslave(). */
    public static boolean slaveryAccessEnabled = true;
    /** AI-Plan-Management: overwritePlan(), PlanCatalog. */
    public static boolean planAccessEnabled = true;

    // ─── Boosting ───────────────────────────────────────────
    public static boolean boostingGovEnabled = true;
    public static boolean boostingSpoilageEnabled = true;
    public static boolean boostingMaintenanceEnabled = true;
    public static boolean boostingImmigrationEnabled = true;
    public static boolean boostingHappinessEnabled = true;
    public static boolean boostingLoyaltyEnabled = true;
    public static boolean boostingDeflationEnabled = true;


    // ═══ IStatsAccess (A-04b) ══════════════════════════════
    /** Master-Switch für alle Stats-Zugriffe. */
    public static boolean statsAccessEnabled = true;

    // ─── Maintenance ────────────────────────────────────────
    public static boolean maintenanceEnabled = true;
    public static boolean maintenanceConsumptionEnabled = true;
    public static boolean maintenanceRoomEnabled = true;
    public static boolean maintenanceDegraderEnabled = true;

    // ─── Time ───────────────────────────────────────────────
    public static boolean timeEnabled = true;
    public static boolean timeSeasonsEnabled = true;
    public static boolean timeLightEnabled = true;

    // ─── Religion ───────────────────────────────────────────
    public static boolean religionStatsEnabled = true;

    // ─── Environment ────────────────────────────────────────
    public static boolean weatherEnabled = true;
    public static boolean tourismEnabled = true;
    public static boolean eventsEnabled = true;

    private EngineLevers() {
    }

    /** Initialization hook called once at mod startup. */
    public static void init() {
        // No-op — fields are statically initialized.
    }

    /**
     * Dump aller Hebel via EventLog + LoggingAdapter CSV.
     * Bei Startup oder auf Abruf (Debug-Tab).
     */
    public static void dump() {
        int enabled = countEnabled();
        int total = countTotal();
        EventLog.log("MIRROR", "EngineLevers: " + enabled + "/" + total
                + " Hebel aktiviert (engineMirrorEnabled=" + engineMirrorEnabled + ")");
        LoggingAdapter.csvTrace("MIRROR", "LEVERS", "INFO", "init",
                "enabled=" + enabled + ",total=" + total,
                "EngineLevers initialized for V71.44");

        // Sub-system summary
        logSubSystem("RoomAccess", roomAccessEnabled);
        logSubSystem("FactionAccess", factionAccessEnabled);
        logSubSystem("HumanoidAccess", humanoidAccessEnabled);
        logSubSystem("StatsAccess", statsAccessEnabled);
    }

    private static void logSubSystem(String name, boolean master) {
        String status = master ? "ON" : "OFF";
        EventLog.log("MIRROR", "  " + name + ": " + status);
    }

    /**
     * Zählt aktivierte Hebel via Reflection.
     * Automatisch erweiterbar wenn neue Felder hinzukommen.
     */
    public static int countEnabled() {
        int count = 0;
        for (Field f : EngineLevers.class.getDeclaredFields()) {
            if (f.getType() == boolean.class && Modifier.isStatic(f.getModifiers())
                    && Modifier.isPublic(f.getModifiers())) {
                try {
                    if (f.getBoolean(null)) {
                        count++;
                    }
                } catch (IllegalAccessException e) {
                    // Should never happen for public static fields
                }
            }
        }
        return count;
    }

    /** Zählt Gesamtzahl der boolean-Toggle-Felder. */
    public static int countTotal() {
        int count = 0;
        for (Field f : EngineLevers.class.getDeclaredFields()) {
            if (f.getType() == boolean.class && Modifier.isStatic(f.getModifiers())
                    && Modifier.isPublic(f.getModifiers())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns a human-readable warning when critical levers are set in a way
     * that disables core functionality. Read-only; never changes any value.
     */
    public static String conflictWarning() {
        if (!engineMirrorEnabled) {
            return "engineMirrorEnabled=false — ALL engine access disabled";
        }
        if (!roomAccessEnabled && !factionAccessEnabled
                && !humanoidAccessEnabled && !statsAccessEnabled) {
            return "ALL sub-systems disabled — EngineMirror is a no-op";
        }
        return null;
    }
}
