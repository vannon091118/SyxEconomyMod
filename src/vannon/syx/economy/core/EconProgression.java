package vannon.syx.economy.core;

import game.battle.div.Div;
import game.boosting.BOOSTABLES;
import game.boosting.BSourceInfo;
import game.boosting.BValue;
import game.boosting.Boostable;
import game.boosting.BoosterValue;
import init.sprite.UI.UI;
import init.type.HCLASS_RACE;
import settlement.main.SETT;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.stats.Induvidual;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.sets.LIST;
import snake2d.util.sprite.SPRITE;
import vannon.syx.economy.adapter.ISyxBoosting;
import java.io.IOException;

/**
 * Verwaltet die Wirtschaftsstufen (SUBSISTENZ -> IMPERIUM).
 * Save/Load: persists in EconomySim chunked save format (global format v33).
 * Kein Vanilla-Import ausser poll-basiertem SETT.ROOMS().
 */
public final class EconProgression implements Saveable {

    // v33: Stage.INDUSTRIE eingefügt; Save-Format schreibt jetzt Version-Header.
    public static final int SAVE_VERSION = 33;

    // === STUFEN ===
    public enum Stage {
        SUBSISTENZ(0, "Subsistenz"),
        HANDEL(1, "Handel"),
        INDUSTRIE(2, "Industrie"),
        WOHLSTAND(3, "Wohlstand"),
        IMPERIUM(4, "Imperium");

        public final int level;
        public final String displayName;

        Stage(int level, String displayName) {
            this.level = level;
            this.displayName = displayName;
        }

        public static Stage fromLevel(int level) {
            for (Stage s : values()) if (s.level == level) return s;
            return SUBSISTENZ;
        }

        public Stage next() {
            Stage[] v = values();
            return level + 1 < v.length ? v[level + 1] : this;
        }
    }

    // Aktuelle Stufe
    public Stage stage = Stage.SUBSISTENZ;
    public int stageDays = 0;

    // === AKKUMULATOREN ===
    public long cumulativeWagesPaid    = 0;
    public long cumulativeExportValue  = 0;

    // === STABILITAETSZAEHLER ===
    public int daysSinceInsolvency     = 0;
    public int daysLowGini             = 0;
    public int daysVeryLowGini         = 0;
    public int daysStableTreasury      = 0;
    private long lastTreasuryBalance   = -1;

    // === MILESTONES (einmal gesetzt, nie gecleared) ===
    public boolean msFirstStockpile    = false;
    public boolean msFirstExport       = false;
    public boolean msFirstTavern       = false;
    public boolean msFirstMarket       = false;
    public boolean msFirstTemple       = false;
    public boolean msFirstLaboratory   = false;
    public boolean msFirstMilitary     = false;
    public boolean msFirstEmbassy      = false;
    public boolean msStableWages       = false;

    // === STATUS-FLAGS (können flippen) ===
    public boolean statusLowInequality = false;

    // Advance-Cooldown verhindert Stufenflicker
    private int advanceCooldownTicks   = 0;
    private static final int ADVANCE_COOLDOWN = 60;

    // === ADAPTER (Phase 4.4) ===
    private final ISyxBoosting boostingAdapter;

    /**
     * Instanz-Status: ein registrierter Boostable (oder null) — verhindert
     * Mehrfachregistrierung wenn der Adapter denselben Boostable zweimal liefert.
     */
    private Boostable adminRegisteredOn = null;

    // === GEBAEUDE-CACHE ===
    public int cachedStockpileCount  = 0;
    public int cachedTavernCount     = 0;
    public int cachedMarketCount     = 0;
    public int cachedTempleCount     = 0;
    public int cachedExportCount     = 0;
    public int cachedLabCount        = 0;
    public int cachedLibraryCount    = 0;
    public int cachedMilitaryCount   = 0;
    public int cachedEmbassyCount    = 0;

    /**
     * Phase 4.4-Konstruktor — die Reflection-Suche nach dem GOV-Boostable
     * wird vom injected {@link ISyxBoosting} Adapter übernommen.
     */
    public EconProgression(ISyxBoosting boostingAdapter) {
        this.boostingAdapter = boostingAdapter;
    }

    /**
     * Einziger Update-Einstiegspunkt.
     * Aufgerufen von EconomySim.update() alle 60 Ticks.
     */
    public void update(EconSnapshot snap) {
        stageDays++;
        if (advanceCooldownTicks > 0) advanceCooldownTicks--;

        // Akkumulatoren
        cumulativeWagesPaid   += snap.incomePaid;
        cumulativeExportValue += snap.warehouseSold;

        // Stabilitaetszaehler
        if (snap.workersUnpaid == 0) daysSinceInsolvency++;
        else daysSinceInsolvency = 0;

        if (snap.gini < 0.35) daysLowGini++;
        else daysLowGini = 0;

        if (snap.gini < 0.30) daysVeryLowGini++;
        else daysVeryLowGini = 0;

        // Milestones setzen
        pollBuildings();
        if (cachedStockpileCount > 0) msFirstStockpile = true;
        if (cumulativeExportValue > 0) msFirstExport    = true;
        if (cachedTavernCount > 0)    msFirstTavern    = true;
        if (cachedMarketCount > 0)    msFirstMarket    = true;
        if (cachedTempleCount > 0)    msFirstTemple    = true;
        if (cachedLabCount > 0)       msFirstLaboratory = true;
        if (cachedMilitaryCount > 0)  msFirstMilitary  = true;
        if (cachedEmbassyCount > 0)   msFirstEmbassy   = true;
        if (daysSinceInsolvency >= 100) msStableWages   = true;
        if (daysVeryLowGini >= 50)    statusLowInequality  = true;
        else if (snap.gini >= EconIndicators.GINI_WARNING) statusLowInequality = false;

        // Stufen-Aufstieg pruefen
        if (advanceCooldownTicks == 0) checkAdvance(snap);
    }

    private void checkAdvance(EconSnapshot snap) {
        Stage next = stage.next();
        if (next == stage) return;

        boolean advance = false;
        switch (stage) {
            case SUBSISTENZ:
                // B-012: 50→30 — 37 Siedler kamen nie über 50, Progression
                // blieb bei 3850 Tagen stecken. 30 erlaubt früheres Vorankommen.
                advance = snap.people >= 30
                    && msFirstStockpile
                    && LocalPrices.foodDays() > 0 && LocalPrices.foodDays() >= 3.0
                    && stageDays >= 30;
                break;
            case HANDEL:
                // B-012: 100→75 — Tavernen-/Markt-Erfordernis bleibt, aber
                // kleinere Siedlungen können jetzt HANDEL erreichen.
                advance = snap.people >= 75
                    && msFirstExport
                    && snap.actualMeanWage > 50
                    && (msFirstTavern || msFirstMarket);
                break;
            case INDUSTRIE:
                advance = snap.people >= 150
                    && msFirstLaboratory
                    && cachedLibraryCount > 0
                    && msFirstMilitary
                    && stageDays >= 30;
                break;
            case WOHLSTAND:
                advance = snap.people >= 200
                    && daysSinceInsolvency >= 100
                    && daysLowGini >= 30
                    && cumulativeExportValue > 10000
                    && (msFirstTavern && msFirstMarket);
                break;
            default:
                break;
        }

        if (advance) {
            stage = next;
            stageDays = 0;
            advanceCooldownTicks = ADVANCE_COOLDOWN;
            onStageAdvance(next);
        }
    }

    private void onStageAdvance(Stage newStage) {
        // Sichtbarkeit zuerst: Aufstieg wird IMMER in der Chronik gemeldet.
        EventLog.log("STAGE", "Wirtschaft erreicht Stufe " + newStage.displayName + "!");

        switch (newStage) {
            case HANDEL:
                MeticImmigration.register();
                break;
            case INDUSTRIE:
                // Admin-Punkte-Boost: Industrialisierung bringt Bürokratie-Effizienz.
                registerAdminBooster();
                break;
            case WOHLSTAND:
                EconConfig.happinessAtRichest = Math.min(1.6, EconConfig.happinessAtRichest + 0.15);
                // Vorher gehoert alles dem Staat -- Privatisierung wird ERST hier freigeschaltet,
                // wie urspruenglich im Housing-Design vorgesehen.
                if (!EconConfig.homePurchaseEnabled) {
                    EconConfig.homePurchaseEnabled = true;
                    EconConfig.propertyMarketEnabled = true;
                    PropertyHappiness.register();
                    EventLog.log("STAGE", "Privatisierung freigeschaltet: Buerger koennen nun Haeuser vom Staat kaufen.");
                }
                break;
            case IMPERIUM:
                EconConfig.meticImmigrationDepth = 0.5;
                EconConfig.meticImmigrationSteepness = 8.0;
                if (!EconConfig.workplaceSharesEnabled) {
                    EconConfig.workplaceSharesEnabled = true;
                    EventLog.log("STAGE", "Aktienhandel freigeschaltet: Betriebsanteile koennen nun erworben werden.");
                }
                break;
            default:
                break;
        }
    }

    /** v1.7.2 Ticket 3: Dauerhafte Sichtbarkeit des Industrie-Admin-Boosts.
     *  true wenn Boostable verfügbar war und registriert wurde. */
    public static boolean adminBoostActive = false;

    /**
     * Registriert einen +20% Admin-Punkte-Produktions-Boost. Phase 4.4:
     * die Reflection-Suche wurde in {@link VanillaBoostingAdapter} gekapselt;
     * dieser Konsument liest nur das gecachte Boostable und hängt den
     * {@link BoosterValue} an.
     */
    private void registerAdminBooster() {
        try {
            Boostable admin = this.boostingAdapter.getAdminBoostable();
            if (admin == null) return;

            // Idempotenz pro Instanz: derselbe Boostable nur einmal registrieren.
            if (this.adminRegisteredOn == admin) return;
            this.adminRegisteredOn = admin;

            BValue.BValueInduOnly adminBoost = new BValue.BValueInduOnly() {
                public double vGet(Induvidual indu)   { return 1.20; }
                public double vGet(Div div)           { return 1.20; }
                public double vGet(HCLASS_RACE group)  { return 1.20; }
            };
            new BoosterValue(
                (BValue) adminBoost,
                new BSourceInfo((CharSequence) "Industrie-Stufe", (SPRITE) UI.icons().s.money),
                1.0, 1.0, false
            ).add(admin);
            EventLog.log("STAGE", "Industrie-Boost aktiv: +20% Admin-Punkte-Produktion (Feld: " + admin.toString() + ").");
            adminBoostActive = true;
        } catch (RuntimeException t) {
            adminBoostActive = false;
            EventLog.log("SEAM", "GOV-Booster-Registrierung fehlgeschlagen: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + " — Industrie-Bonus (+20% Admin) inaktiv.");
        }
    }

    private void pollBuildings() {
        if (SETT.ROOMS() == null) return;

        // Stockpile wird jetzt ueber den instanceof-Loop erfasst (wie alle anderen
        // Gebaeudetypen), nicht ueber SETT.ROOMS().STOCKPILE.instancesSize().
        // Grund: Live-Test zeigte Diskrepanz — Loehne-Tab meldete "Arbeiter 2/2"
        // aber msFirstStockpile blieb false, obwohl SETT.ROOMS().STOCKPILE nie null ist.
        // Verdacht: ROOM_STOCKPILE.instancesSize() zaehlt Instanzen in einer anderen
        // Collection als die, die ueber SETT.ROOMS().imps() erreichbar ist.
        cachedStockpileCount = cachedTavernCount = cachedMarketCount = cachedTempleCount = 0;
        cachedExportCount = cachedLabCount = cachedLibraryCount   = 0;
        cachedMilitaryCount = cachedEmbassyCount = 0;

        LIST<RoomBlueprintImp> all = SETT.ROOMS().imps();
        for (int i = 0; i < all.size(); i++) {
            RoomBlueprintImp b = all.get(i);
            if (!(b instanceof RoomBlueprintIns)) continue;
            int count = ((RoomBlueprintIns) b).instancesSize();
            if (count <= 0) continue;

            if (b instanceof settlement.room.infra.stockpile.ROOM_STOCKPILE)  cachedStockpileCount += count;
            if (b instanceof settlement.room.service.food.tavern.ROOM_TAVERN)     cachedTavernCount   += count;
            if (b instanceof settlement.room.service.market.ROOM_MARKET)     cachedMarketCount   += count;
            if (b instanceof settlement.room.spirit.temple.ROOM_TEMPLE)     cachedTempleCount   += count;
            if (b instanceof settlement.room.infra.export.ROOM_EXPORT)     cachedExportCount   += count;
            if (b instanceof settlement.room.knowledge.laboratory.ROOM_LABORATORY) cachedLabCount      += count;
            if (b instanceof settlement.room.knowledge.library.ROOM_LIBRARY)    cachedLibraryCount  += count;
            if (b instanceof settlement.room.military.training.ROOM_M_TRAINER)  cachedMilitaryCount += count;
            if (b instanceof settlement.room.infra.embassy.ROOM_EMBASSY)    cachedEmbassyCount  += count;
        }
    }

    /**
     * Verlässlicher Stockpile-Count via SETT.ROOMS().imps() statt STOCKPILE.instancesSize().
     * Hintergrund: Live-Test zeigte Diskrepanz — Löhne-Tab meldete "Arbeiter 2/2"
     * aber msFirstStockpile blieb false. instancesSize() zählt offenbar Instanzen
     * in einer anderen Collection als der instanceof-Loop über SETT.ROOMS().imps().
     * @return Anzahl der Stockpile-Instanzen oder 0 wenn keine gefunden.
     */
    public static int reliableStockpileCount() {
        if (SETT.ROOMS() == null) return 0;
        LIST<RoomBlueprintImp> all = SETT.ROOMS().imps();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i) instanceof settlement.room.infra.stockpile.ROOM_STOCKPILE) {
                return ((RoomBlueprintIns) all.get(i)).instancesSize();
            }
        }
        return 0;
    }

    // === SAVE / LOAD ===

    public void save(FilePutter file) {
        // v33: Version-Header für Forward-/Backward-Compat.
        file.i(SAVE_VERSION);
        file.i(stage.level);
        file.i(stageDays);
        file.l(cumulativeWagesPaid);
        file.l(cumulativeExportValue);
        file.i(daysSinceInsolvency);
        file.i(daysLowGini);
        file.i(daysVeryLowGini);
        file.bool(msFirstStockpile);
        file.bool(msFirstExport);
        file.bool(msFirstTavern);
        file.bool(msFirstMarket);
        file.bool(msFirstTemple);
        file.bool(msFirstLaboratory);
        file.bool(msFirstMilitary);
        file.bool(msFirstEmbassy);
        file.bool(msStableWages);
        file.bool(statusLowInequality);
    }

    public void load(FileGetter file) throws IOException {
        // v33: Dual-Format-Erkennung.
        // Alter Save (v32): erster int = stage.level (0-3).
        // Neuer Save (v33+): erster int = SAVE_VERSION (≥33), zweiter int = stage.level.
        int first = file.i();
        int rawLevel;
        if (first >= 33) {
            rawLevel = file.i();
        } else {
            rawLevel = first;
            // Migration: Alte Saves hatten WOHLSTAND=2, IMPERIUM=3.
            // Nach INDUSTRIE-Einfügung müssen die um +1 shiften.
            if (rawLevel >= 2) rawLevel++;
        }
        stage               = Stage.fromLevel(rawLevel);
        stageDays           = file.i();
        cumulativeWagesPaid = file.l();
        cumulativeExportValue = file.l();
        daysSinceInsolvency = file.i();
        daysLowGini         = file.i();
        daysVeryLowGini     = file.i();
        msFirstStockpile    = file.bool();
        msFirstExport       = file.bool();
        msFirstTavern       = file.bool();
        msFirstMarket       = file.bool();
        msFirstTemple       = file.bool();
        msFirstLaboratory   = file.bool();
        msFirstMilitary     = file.bool();
        msFirstEmbassy      = file.bool();
        msStableWages       = file.bool();
        statusLowInequality     = file.bool();
    }
}
