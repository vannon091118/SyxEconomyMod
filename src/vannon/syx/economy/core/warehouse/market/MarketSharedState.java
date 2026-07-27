package vannon.syx.economy.core.warehouse.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.RoomInstance;
import vannon.syx.economy.core.IdentityMapRegistry;
import vannon.syx.economy.core.WarehouseMarket;

/**
 * Phase-1 (T-101) shared state container for WarehouseMarket.
 *
 * <p>Extracted from {@link WarehouseMarket} as the first sub-step of Sprint M-1
 * ("WarehouseMarket Hybrid-Facade"). Holds the 11 reference-keyed data fields
 * plus 3 scalar-state flags that previously lived directly on WarehouseMarket.
 * Tracks every per-firm market position and the accumulated crown-title pool
 * referenced by the future engine split (T-102..T-108).</p>
 *
 * <p>IdentityMapRegistry-key entries keep their legacy {@code "WarehouseMarket"}
 * owner-string so that save-chunks written before this refactor still resolve
 * their reference-keyed maps after the V7-to-V8 save migration in T-108.</p>
 *
 * <p>The 8 inner Records ({@code Book}, {@code DirectClaim}, {@code RetailBook},
 * {@code RetailLot}, 4x {@code Pending*}) stay nested in WarehouseMarket for
 * this phase. They migrate into dedicated engine sub-classes in T-102..T-108.</p>
 */
public final class MarketSharedState {

    public final ArrayList<WarehouseMarket.PendingBook> pending = new ArrayList<>();
    public final ArrayList<WarehouseMarket.PendingIntakeLock> pendingIntakeLocks = new ArrayList<>();
    public final Map<StockpileInstance, WarehouseMarket.Book[]> books = new IdentityHashMap<>();
    public final Map<Integer, ArrayList<WarehouseMarket.DirectClaim>> directClaims = new HashMap<>();
    public final ArrayList<WarehouseMarket.PendingDirectClaim> pendingDirectClaims = new ArrayList<>();
    public final Map<RoomInstance, WarehouseMarket.RetailBook[]> retailBooks = new IdentityHashMap<>();
    public final ArrayList<WarehouseMarket.PendingRetailBook> pendingRetailBooks = new ArrayList<>();
    public long[] crownUnits = new long[0];
    public long[] abandonedUnits = new long[0];
    public boolean inferCrownFromLoose = true;
    public final Map<StockpileInstance, Map<Integer, Integer>> intakeLocks = new IdentityHashMap<>();

    public MarketSharedState() {
        // v0.1.3 (Phase-4.7-Blocker #8): reference-keyed maps must be registered
        // so Save/Load-clear does not silently drop entries when the engine
        // recreates StockpileInstance and RoomInstance on chunk/legacy load.
        IdentityMapRegistry.register("WarehouseMarket", "books", books);
        IdentityMapRegistry.register("WarehouseMarket", "retailBooks", retailBooks);
        IdentityMapRegistry.register("WarehouseMarket", "intakeLocks", intakeLocks);
    }
}
