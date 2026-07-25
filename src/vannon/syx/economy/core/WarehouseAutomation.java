package vannon.syx.economy.core;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import java.util.ArrayList;
import java.util.Comparator;
import settlement.main.SETT;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.RoomInstance;
import settlement.room.main.job.StorageCrate;
import snake2d.util.datatypes.COORDINATE;

/**
 * Konfiguriert StateWarehouses buyPrice/sellPrice automatisch aus
 * FlowPrices (Marktpreise), FlowMeter (Bestände) und verfügbarer
 * Crate-Kapazität — vollständig proaktiv, kein manuelles Konfigurieren nötig.
 *
 * <h3>Vollautomatische Ressourcen-Erkennung</h3>
 * Anders als die Vorgänger-Version benötigt diese keine manuelle
 * "userActivated"-Markierung. Die Automation:
 * <ul>
 *   <li>Erkennt kritische Knappheit (stock=0 AND demand>0) — auto-enable buy</li>
 *   <li>Erkennt Bau-Materialbedarf (constructionWithdrawals>0) — auto-enable buy</li>
 *   <li>Hält 3-Tage-Notfallpuffer für alle Nahrungs-Ressourcen</li>
 *   <li>Verkauft Überschuss (>9-Tage-Ziel) automatisch</li>
 *   <li>Budget-aware: stoppt alle Käufe wenn Treasury negativ</li>
 *   <li>Priorisiert nach Scarcity-Score (Knappheit × Nachfrage)</li>
 * </ul>
 */
public final class WarehouseAutomation {
    private static int lastAppliedSeason = -1;
    private static final double BUY_PREMIUM = 1.20;
    private static final double SELL_DISCOUNT = 0.90;
    private static final double AGGRESSIVE_SELL_DISCOUNT = 0.75;
    private static final int EMERGENCY_STOCK_DAYS = 3;
    private static final int OVERSTOCK_DAYS = 9;
    private static final int MIN_OVERSTOCK_UNITS = 50;

    /**
     * Einmal pro Saison aufrufen — nach FlowPrices.refresh().
     * @param state StateWarehouses-Instanz
     * @param prices aktuelle FlowPrices
     * @param meter FlowMeter-Snapshot (Angebot/Nachfrage/Bestand)
     * @param constructionWithdrawals pro Ressource: wie viel Bau diese Saison abgerufen hat
     * @param treasury aktueller Kontostand — bei negativ keine Käufe
     */
    public static void autoTune(StateWarehouses state, FlowPrices prices,
                                 FlowMeter.Snapshot meter,
                                 int[] constructionWithdrawals,
                                 long treasury) {
        int season = game.time.TIME.seasons().bitsSinceStart();
        if (season == lastAppliedSeason) return;
        lastAppliedSeason = season;

        if (!EconConfig.warehouseAutoTuneEnabled || !EconConfig.stateWarehousesEnabled
                || !prices.ready()) return;

        boolean budgetOk = treasury > 0;
        int goods = RESOURCES.ALL().size();
        if (goods == 0) return;

        // ═══ Phase 1: Crate-Kapazität pro Ressource sammeln ═══

        int[] crateCount = new int[goods];   // Anzahl Crates dieser Ressource
        int[] totalStock = new int[goods];   // Gesamtbestand aller Crates
        int[] freeSpace  = new int[goods];   // Freier Platz
        boolean[] hasAnyCrate = new boolean[goods];
        int totalFreeSlots = 0;

        if (SETT.ROOMS() != null) {
            int n = EconProgression.reliableStockpileCount();
            for (int i = 0; i < n; ++i) {
                StockpileInstance wh = (StockpileInstance) SETT.ROOMS().STOCKPILE.getInstance(i);
                if (wh == null || !wh.exists() || !state.isStateOwned((RoomInstance) wh)) continue;
                for (COORDINATE tile : wh.body()) {
                    if (!wh.is(tile)) continue;
                    StorageCrate crate = wh.crate(tile.x(), tile.y());
                    if (crate == null || crate.resource() == null) continue;
                    int idx = crate.resource().index();
                    if (idx >= goods) continue;
                    int cap = wh.crateSize(crate.resource());
                    int used = crate.amount() + crate.storageReserved();
                    int free = Math.max(0, cap - used);
                    crateCount[idx]++;
                    totalStock[idx] += crate.amount();
                    freeSpace[idx] += free;
                    hasAnyCrate[idx] = true;
                    if (free > 0) totalFreeSlots += free;
                }
            }
        }

        // ═══ Phase 2: Kandidaten bauen (alle Ressourcen, nicht nur userActivated) ═══

        ArrayList<ResourceCandidate> candidates = new ArrayList<>();

        for (int i = 0; i < goods; i++) {
            RESOURCE res = RESOURCES.ALL().get(i);
            if (res == null) continue;
            int idx = res.index();
            if (idx >= meter.size()) continue;

            double stock = meter.stock(idx);
            double demand = meter.demandPerDay(idx);
            int marketPrice = prices.priceRoundedUp(idx);
            if (marketPrice <= 0) continue;

            int constructionNeed = (constructionWithdrawals != null
                    && idx < constructionWithdrawals.length)
                    ? constructionWithdrawals[idx] : 0;

            // Kritische Knappheit: kein Bestand, aber Nachfrage → IMMER kaufen
            boolean isScarce = (stock <= 0 && demand > 0);

            // Geringer Bestand: weniger als 3-Tage-Ziel
            boolean isLowStock = demand > 0 && stock < demand * EMERGENCY_STOCK_DAYS;

            // Bau-Material wird benötigt
            boolean isConstructionMaterial = constructionNeed > 0;

            // Überbestand: >9-Tage-Ziel UND >50 Einheiten → verkaufen
            boolean isOverstocked = (demand > 0 && stock > Math.max(demand * OVERSTOCK_DAYS, MIN_OVERSTOCK_UNITS))
                    || (demand == 0 && stock > MIN_OVERSTOCK_UNITS);

            // Nahrungs-Notfallpuffer: alle FOOD-Ressourcen immer auf 3 Tage halten
            boolean isFood = isFoodResource(res);
            boolean foodEmergency = isFood && isLowStock;

            // Scarcity-Score (0..1) × Nachfrage — je knapper, desto höher
            double scarcityScore = 0.0;
            if (demand > 0) {
                double targetStock = demand * EMERGENCY_STOCK_DAYS;
                double coverage = Math.max(0.0, stock) / Math.max(1.0, targetStock);
                scarcityScore = (1.0 - Math.min(1.0, coverage)) * demand;
            }
            if (stock <= 0) scarcityScore = demand * 2.0;
            // Nahrung + Bau-Material bekommen Prioritäts-Bonus
            if (isFood && isLowStock) scarcityScore *= 1.5;
            if (isConstructionMaterial) scarcityScore *= 1.3;

            candidates.add(new ResourceCandidate(res, idx, marketPrice, stock, demand,
                    isScarce, isLowStock, isOverstocked, isFood, foodEmergency,
                    isConstructionMaterial, constructionNeed,
                    crateCount[idx], totalStock[idx], freeSpace[idx],
                    hasAnyCrate[idx], scarcityScore));
        }

        candidates.sort(Comparator.comparingDouble((ResourceCandidate c) -> c.scarcityScore).reversed());

        // ═══ Phase 3: Allokation — Buy/Sell pro Ressource ═══

        int remainingSlots = totalFreeSlots;
        int buyCount = 0, sellCount = 0, emergencyCount = 0;

        for (ResourceCandidate c : candidates) {
            RESOURCE res = c.res;
            int idx = c.idx;

            // ── KRITISCH: stock=0 mit Nachfrage → Notkauf, egal ob Budget ──
            if (c.isScarce) {
                int buyAt = (int) Math.min(EconConfig.statePriceMax,
                        Math.round(c.marketPrice * BUY_PREMIUM));
                state.setBuyPrice(res, buyAt);
                state.setSellPrice(res, 0);
                emergencyCount++;
                remainingSlots = Math.max(0, remainingSlots - Math.min(c.freeSpace, remainingSlots));
                continue;
            }

            // ── NAHRUNG: 3-Tage-Puffer immer halten ──
            if (c.foodEmergency && budgetOk && c.hasCrates && remainingSlots > 0) {
                state.setBuyPrice(res, c.marketPrice);
                state.setSellPrice(res, 0);
                buyCount++;
                remainingSlots = Math.max(0, remainingSlots - Math.min(c.freeSpace, remainingSlots));
                continue;
            }

            // ── BAUMATERIAL: wenn Bau-Projekte aktiv sind ──
            if (c.isConstructionMaterial && budgetOk && c.hasCrates && remainingSlots > 0) {
                int buyAt = (int) Math.min(EconConfig.statePriceMax,
                        Math.round(c.marketPrice * BUY_PREMIUM));
                state.setBuyPrice(res, buyAt);
                state.setSellPrice(res, 0);
                buyCount++;
                remainingSlots = Math.max(0, remainingSlots - Math.min(c.freeSpace, remainingSlots));
                continue;
            }

            // ── NIEDRIGER BESTAND + Budget + Slots frei → einkaufen ──
            if (c.isLowStock && budgetOk && c.hasCrates && remainingSlots > 0) {
                state.setBuyPrice(res, c.marketPrice);
                state.setSellPrice(res, 0);
                buyCount++;
                remainingSlots = Math.max(0, remainingSlots - Math.min(c.freeSpace, remainingSlots));
                continue;
            }

            // ── ÜBERBESTAND → verkaufen ──
            if (c.isOverstocked) {
                double discount = (c.demand == 0) ? AGGRESSIVE_SELL_DISCOUNT : SELL_DISCOUNT;
                int sellAt = (int) Math.round(c.marketPrice * discount);
                state.setBuyPrice(res, 0);
                state.setSellPrice(res, Math.max(1, sellAt));
                sellCount++;
                continue;
            }

            // ── KEINE Crates → Buy-Order deaktivieren (kein physischer Platz) ──
            if (!c.hasCrates) {
                state.setBuyPrice(res, 0);
                state.setSellPrice(res, 0);
                continue;
            }

            // ── Neutral: weder kaufen noch verkaufen ──
            // Wenn der Spieler manuell einen Buy/Sell gesetzt hat, respektieren wir das
            // (kein Überschreiben wenn bereits konfiguriert und kein Automationsgrund)
            boolean manuallyConfigured = state.buyPrice(res) > 0 || state.sellPrice(res) > 0;
            if (!manuallyConfigured) {
                state.setBuyPrice(res, 0);
                state.setSellPrice(res, 0);
            }
        }

        // ═══ Phase 4: Nicht-kandidierte Ressourcen neutralisieren ═══

        boolean[] isCandidate = new boolean[goods];
        for (ResourceCandidate c : candidates) isCandidate[c.idx] = true;
        for (int i = 0; i < goods; i++) {
            RESOURCE res = RESOURCES.ALL().get(i);
            if (res == null) continue;
            int idx = res.index();
            if (idx >= meter.size() || isCandidate[idx]) continue;
            boolean manuallyConfigured = state.buyPrice(res) > 0 || state.sellPrice(res) > 0;
            if (!manuallyConfigured) {
                state.setBuyPrice(res, 0);
                state.setSellPrice(res, 0);
            }
        }

        // ═══ EventLog (nur bei Änderungen) ═══

        if (emergencyCount > 0 || buyCount > 0 || sellCount > 0) {
            String budgetNote = budgetOk ? "" : " (KEIN BUDGET — nur Notkäufe)";
            EventLog.log("ECON", "Lager-Automation: "
                    + emergencyCount + " Notkäufe, "
                    + buyCount + " Käufe, "
                    + sellCount + " Verkäufe. "
                    + "Freie Slots: " + totalFreeSlots
                    + budgetNote);
        }
    }

    /**
     * Heuristik: ist diese Ressource ein Nahrungsmittel?
     * Prüft auf bekannte Nahrungs-Keys (essbar, nicht verarbeitet zu Industrie).
     */
    private static boolean isFoodResource(RESOURCE res) {
        if (res == null) return false;
        String key = res.key;
        // Alle bekannten V71-Nahrungsressourcen
        return key.contains("GRAIN") || key.contains("BREAD") || key.contains("FLOUR")
                || key.contains("MEAT") || key.contains("FISH") || key.contains("FRUIT")
                || key.contains("VEGETABLE") || key.contains("MUSHROOM") || key.contains("EGG")
                || key.contains("MILK") || key.contains("CHEESE") || key.contains("RATION")
                || key.contains("CARROT") || key.contains("ONION") || key.contains("BERRY")
                || key.contains("CABBAGE") || key.contains("HERB") || key.contains("SPICE");
    }

    /** Interne Datenstruktur für die Priorisierung. */
    private static final class ResourceCandidate {
        final RESOURCE res;
        final int idx, marketPrice, crateCount, totalStock, freeSpace, constructionNeed;
        final double stock, demand, scarcityScore;
        final boolean isScarce, isLowStock, isOverstocked, isFood, foodEmergency,
                isConstructionMaterial, hasCrates;

        ResourceCandidate(RESOURCE res, int idx, int marketPrice, double stock, double demand,
                          boolean isScarce, boolean isLowStock, boolean isOverstocked,
                          boolean isFood, boolean foodEmergency, boolean isConstructionMaterial,
                          int constructionNeed, int crateCount, int totalStock, int freeSpace,
                          boolean hasCrates, double scarcityScore) {
            this.res = res;
            this.idx = idx;
            this.marketPrice = marketPrice;
            this.stock = stock;
            this.demand = demand;
            this.isScarce = isScarce;
            this.isLowStock = isLowStock;
            this.isOverstocked = isOverstocked;
            this.isFood = isFood;
            this.foodEmergency = foodEmergency;
            this.isConstructionMaterial = isConstructionMaterial;
            this.constructionNeed = constructionNeed;
            this.crateCount = crateCount;
            this.totalStock = totalStock;
            this.freeSpace = freeSpace;
            this.hasCrates = hasCrates;
            this.scarcityScore = scarcityScore;
        }
    }

    private WarehouseAutomation() {}

    /**
     * T13: Session-Reset. Pattern vgl. TreasuryCrisis.reset().
     */
    public static void reset() {
        lastAppliedSeason = -1;
    }
}
