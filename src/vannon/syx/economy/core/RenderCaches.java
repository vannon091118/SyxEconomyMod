package vannon.syx.economy.core;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import snake2d.util.sets.LIST;
import vannon.syx.economy.core.DiagnosticExporter;
import vannon.syx.economy.core.DebugTracer;

/**
 * RenderCaches — Extracted from {@link EconomySim} (TASK-006, v0.13.67).
 *
 * <p>Holds cached data for UI rendering (richest citizen, state warehouses,
 * workplaces, all resources) and building-change tracking. Updated once per
 * tick via {@link #update(Roster, Wallets, StateWarehouses)}.</p>
 *
 * <p>Fields were originally in EconomySim (~130 LOC). This extraction reduces
 * EconomySim below the God-Class-Guard threshold for this subsystem.</p>
 *
 * <p>CHUNKED_VERSION is NOT affected — these fields are ephemeral (not saved).</p>
 */
public final class RenderCaches {

    // ─── Cached UI data ──────────────────────────────────────────
    private volatile Humanoid cachedRichestCitizen;
    private volatile List<StockpileInstance> cachedStateWarehouses = Collections.emptyList();
    private volatile List<RoomBlueprintImp> cachedWorkplaces = Collections.emptyList();
    private volatile List<RESOURCE> cachedAllResources = Collections.emptyList();

    // ─── Building-change tracking ────────────────────────────────
    private int lastStockpileCount = -1;
    private int lastWorkplaceCount = -1;

    // ═══ Accessors ══════════════════════════════════════════════

    public Humanoid cachedRichestCitizen() {
        return this.cachedRichestCitizen;
    }

    public List<StockpileInstance> cachedStateWarehouses() {
        return this.cachedStateWarehouses;
    }

    public List<RoomBlueprintImp> cachedWorkplaces() {
        return this.cachedWorkplaces;
    }

    public List<RESOURCE> cachedAllResources() {
        return this.cachedAllResources;
    }

    // ═══ Update ═════════════════════════════════════════════════

    /**
     * Refreshes all cached render data. Called once per tick from
     * {@code EconomySim.update()}.
     *
     * @param roster         current citizen roster
     * @param wallets        citizen wallet state
     * @param stateWarehouses state-warehouse ownership registry
     */
    public void update(Roster roster, Wallets wallets, StateWarehouses stateWarehouses) {
        // richest citizen
        Humanoid best = null;
        int most = -1;
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid h = roster.get(i);
            int money = wallets.get(h);
            if (money > most) {
                most = money;
                best = h;
            }
        }
        this.cachedRichestCitizen = most > 0 ? best : null;

        // all resources (static, but cache reference to avoid repeated engine calls)
        LIST<RESOURCE> allResources = RESOURCES.ALL();
        ArrayList<RESOURCE> resourcesList = new ArrayList<>(allResources.size());
        for (RESOURCE resource : allResources) {
            resourcesList.add(resource);
        }
        this.cachedAllResources = resourcesList;

        // state-owned warehouses (state-owned first, then private)
        if (SETT.ROOMS() != null && SETT.ROOMS().STOCKPILE != null) {
            int stockpiles = EconProgression.reliableStockpileCount();
            ArrayList<StockpileInstance> ordered = new ArrayList<>(stockpiles);
            for (int i = 0; i < stockpiles; ++i) {
                StockpileInstance w = (StockpileInstance) SETT.ROOMS().STOCKPILE.getInstance(i);
                if (w != null && stateWarehouses.isStateOwned((RoomInstance) w)) {
                    ordered.add(w);
                }
            }
            for (int i = 0; i < stockpiles; ++i) {
                StockpileInstance w = (StockpileInstance) SETT.ROOMS().STOCKPILE.getInstance(i);
                if (w != null && !stateWarehouses.isStateOwned((RoomInstance) w)) {
                    ordered.add(w);
                }
            }
            this.cachedStateWarehouses = Collections.unmodifiableList(ordered);
        } else {
            this.cachedStateWarehouses = Collections.emptyList();
        }

        // workplaces with employment
        if (SETT.ROOMS() != null) {
            LIST<?> all = SETT.ROOMS().imps();
            ArrayList<RoomBlueprintImp> jobs = new ArrayList<>();
            for (int i = 0; i < all.size(); ++i) {
                RoomBlueprintImp b = (RoomBlueprintImp) all.get(i);
                if (b.employment() == null || !(b instanceof RoomBlueprintIns)) {
                    continue;
                }
                RoomBlueprintIns<?> workplace = (RoomBlueprintIns<?>) b;
                if (workplace.instancesSize() > 0) {
                    jobs.add(b);
                }
            }
            this.cachedWorkplaces = Collections.unmodifiableList(jobs);
        } else {
            this.cachedWorkplaces = Collections.emptyList();
        }
    }

    // ═══ Building-Change Tracking ═══════════════════════════════

    /**
     * Livetest: Loggt Änderungen der Raum-Anzahl (Lager, Werkstätten) via
     * DebugTracer.BUILD für Korrelation mit CSV-Diagnostik. Nur aktiv wenn
     * {@code debugTracing=true}. Throttled auf alle 60 Ticks (~12s real)
     * da Bau-Änderungen auf menschlicher Zeitskala passieren.
     *
     * @param ticks current simulation tick count
     */
    public void track(int ticks) {
        if (!DebugTracer.on()) return;
        if (ticks % 60 != 0) return;
        if (SETT.ROOMS() == null) return;

        // Stockpile count
        int stockpiles = 0;
        if (SETT.ROOMS().STOCKPILE != null) {
            stockpiles = EconProgression.reliableStockpileCount();
        }
        if (this.lastStockpileCount >= 0 && stockpiles != this.lastStockpileCount) {
            int delta = stockpiles - this.lastStockpileCount;
            DebugTracer.trace(DebugTracer.BUILD,
                "stockpile " + (delta > 0 ? "+" : "") + delta + " \u2192 now " + stockpiles);
            DiagnosticExporter.logPlayerAction(ticks, "BUILD_STOCKPILE",
                    "delta=" + delta + ",total=" + stockpiles);
        }
        this.lastStockpileCount = stockpiles;

        // Workplace count (total instances across all blueprints)
        int workplaces = 0;
        if (SETT.ROOMS().imps() != null) {
            LIST<?> all = SETT.ROOMS().imps();
            for (int i = 0; i < all.size(); ++i) {
                RoomBlueprintImp b = (RoomBlueprintImp) all.get(i);
                if (b.employment() != null && b instanceof RoomBlueprintIns) {
                    workplaces += ((RoomBlueprintIns<?>) b).instancesSize();
                }
            }
        }
        if (this.lastWorkplaceCount >= 0 && workplaces != this.lastWorkplaceCount) {
            int delta = workplaces - this.lastWorkplaceCount;
            DebugTracer.trace(DebugTracer.BUILD,
                "workplaces " + (delta > 0 ? "+" : "") + delta + " \u2192 now " + workplaces);
            DiagnosticExporter.logPlayerAction(ticks, "BUILD_WORKPLACE",
                    "delta=" + delta + ",total=" + workplaces);
        }
        this.lastWorkplaceCount = workplaces;
    }
}
