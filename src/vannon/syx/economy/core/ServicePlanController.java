package vannon.syx.economy.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.HAI;
import settlement.entity.humanoid.ai.service.MPlan;
import settlement.main.SETT;
import settlement.room.main.Room;
import settlement.room.service.module.RoomServiceAccess;
import vannon.syx.economy.core.BrokeServicePlan;
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.IHumanoidAccess;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EngineSeams;
import vannon.syx.economy.core.FirmLedger;
import vannon.syx.economy.core.Fiscal;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.ServiceMarket;
import vannon.syx.economy.core.Wallets;

public final class ServicePlanController {
    private final ServiceMarket market;
    private final Fiscal fiscal;
    private final FirmLedger ledger;
    private final BrokeServicePlan deniedPlan = new BrokeServicePlan();
    private final Map<Humanoid, Object> admittedPlans = new IdentityHashMap<Humanoid, Object>();
    /** v1.7.2 Perf: Persistent statt new HashSet<>() jeden Tick — spart GC-Druck bei grossen Siedlungen. */
    private final HashSet<Humanoid> living = new HashSet<Humanoid>();

    /**
     * v1.7.3 Perf: Blueprint→RoomServiceAccess-Cache.
     * Das alte serviceAt() iterierte RoomServiceAccess.ALL() für JEDEN Bürger pro Tick
     * = O(Bürger × ServiceTypes). Bei 500+ Bürgern und >20 Service-Räumen war das
     * der teuerste Hot-Path im ganzen Mod — kein Audit-Report hatte ihn je erwähnt.
     *
     * Neu: eine HashMap<Object, RoomServiceAccess> wird beim ersten Aufruf einmalig befüllt.
     * Da Service-Typen (Eatery, Tavern etc.) und ihre Blueprints statisch sind, muss der Cache
     * nie invalidiert werden. serviceAt() macht dann nur noch map.get(room.blueprint()) = O(1).
     */
    private final Map<Object, RoomServiceAccess> serviceCache = new HashMap<>();

    public ServicePlanController(ServiceMarket market, Fiscal fiscal, FirmLedger ledger) {
        this.market = market;
        this.fiscal = fiscal;
        this.ledger = ledger;
        // Phase-4.7 (Task 2): register admittedPlans Humanoid-keyed map for clearOnLoad.
        // Per-tick cleared via `living.clear() + retainAll(this.living)` — registry-hook
        // is defensive against identity-loss after engine reload.
        IdentityMapRegistry.register("ServicePlanController", "admittedPlans", admittedPlans);
    }

    /** Baut den Blueprint→Service-Cache beim ersten Aufruf einmalig auf. */
    private void refreshServiceCacheIfNeeded() {
        if (this.serviceCache.isEmpty()) {
            for (RoomServiceAccess service : RoomServiceAccess.ALL()) {
                if (service.room() != null) {
                    this.serviceCache.put(service.room(), service);
                }
            }
        }
    }

    public void update(Roster roster, Wallets wallets) {
        if (!EconConfig.serviceMarketEnabled) {
            return;
        }
        this.refreshServiceCacheIfNeeded();
        this.living.clear();
        for (int i = 0; i < roster.size(); ++i) {
            RoomServiceAccess service;
            Humanoid h = roster.get(i);
            this.living.add(h);
            HAI hAI = h.ai();
            if (!(hAI instanceof AIManager)) continue;
            AIManager manager = (AIManager)hAI;
            AIPLAN plan = manager.plan();
            if (!(plan instanceof MPlan)) {
                this.admittedPlans.remove(h);
                continue;
            }
            if (this.admittedPlans.get(h) == plan || (service = this.serviceAt(manager.planTile.x(), manager.planTile.y())) == null) continue;
            if (this.market.admit(h, wallets, service, roster, this.fiscal, this.ledger)) {
                this.admittedPlans.put(h, plan);
                continue;
            }
            IHumanoidAccess hum = EngineMirror.api() != null ? EngineMirror.api().humanoids() : null;
            if (hum != null) hum.overwritePlan(h, (AIPLAN)this.deniedPlan); else EngineSeams.overwritePlan(h, (AIPLAN)this.deniedPlan);
        }
        this.admittedPlans.keySet().retainAll(this.living);
    }

    /**
     * v1.7.3 Perf: O(1) Cache-Lookup statt O(ServiceTypes)-Scan pro Bürger.
     * Nutzt serviceCache (Map<Object, RoomServiceAccess>), der via refreshServiceCacheIfNeeded()
     * einmalig beim ersten Aufruf befüllt wird.
     */
    private RoomServiceAccess serviceAt(int tx, int ty) {
        if (!SETT.IN_BOUNDS((int)tx, (int)ty)) {
            return null;
        }
        Room room = SETT.ROOMS().map.get(tx, ty);
        if (room == null) {
            return null;
        }
        return this.serviceCache.get(room.blueprint());
    }

    public void clear() {
        this.admittedPlans.clear();
    }
}

