package vannon.syx.economy.core;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.misc.util.TILE_STORAGE;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.RoomInstance;
import settlement.thing.THINGS;
import settlement.thing.ThingsResources;
import snake2d.util.datatypes.COORDINATE;
import vannon.syx.economy.core.warehouse.market.MarketSharedState;

/**
 * T-106 MarketMaintenanceEngine — extracted from WarehouseMarket as part of Sprint M-1.
 *
 * <p>Handles periodic market maintenance: seizure settlement, ownerless-book pruning,
 * intake-lock management, pending-book resolution, and crown-title inference.</p>
 */
public final class MarketMaintenanceEngine {

    private final MarketSharedState sharedState;
    private final StateWarehouses state;
    private final CrownTitleEngine crown;
    private final WholesaleEngine wholesale;

    public MarketMaintenanceEngine(MarketSharedState sharedState, StateWarehouses state, CrownTitleEngine crown, WholesaleEngine wholesale) {
        this.sharedState = sharedState;
        this.state = state;
        this.crown = crown;
        this.wholesale = wholesale;
    }

    // ── Public API ──────────────────────────────────────────────

    public void settleSeizures(Roster roster, Wallets wallets) {
        if (SETT.ROOMS() == null) return;
        this.crown.ensureCrownCapacity();
        for (Map.Entry<StockpileInstance, WarehouseMarket.Book[]> entry : this.sharedState.books.entrySet()) {
            boolean nowState = this.state.isStateOwned((RoomInstance)entry.getKey());
            for (WarehouseMarket.Book book : entry.getValue()) {
                if (book == null) continue;
                if (nowState && !book.stateOwned && !book.stakes.isEmpty()) {
                    for (Map.Entry<Integer, Double> stake : book.stakes.entrySet()) {
                        int owed;
                        Humanoid holder = WarehouseMarket.alive(roster, stake.getKey());
                        if (holder == null || (owed = (int)Math.min(Integer.MAX_VALUE, Math.round(Math.max(0.0, stake.getValue())))) <= 0) continue;
                        wallets.accrueTax(holder, owed);
                    }
                    book.stakes.clear();
                    book.capitalBasis = 0.0;
                }
                book.stateOwned = nowState;
            }
        }
    }

    public void prune(Roster roster) {
        resolvePending();
        this.sharedState.books.keySet().removeIf(warehouse -> warehouse == null || !warehouse.exists());
        this.sharedState.intakeLocks.keySet().removeIf(warehouse -> warehouse == null || !warehouse.exists());
        this.sharedState.retailBooks.keySet().removeIf(room -> room == null || !room.exists());
        Iterator<Map.Entry<Integer, ArrayList<WarehouseMarket.DirectClaim>>> resources = this.sharedState.directClaims.entrySet().iterator();
        while (resources.hasNext()) {
            ArrayList<WarehouseMarket.DirectClaim> claims = resources.next().getValue();
            claims.removeIf(claim -> claim.producer == null || !claim.producer.exists() || claim.unitsHeld <= 0);
            if (!claims.isEmpty()) continue;
            resources.remove();
        }
        if (SETT.ROOMS() == null) return;
        this.crown.ensureCrownCapacity();
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            StockpileInstance warehouse2 = (StockpileInstance)SETT.ROOMS().STOCKPILE.getInstance(i);
            if (warehouse2 == null || !warehouse2.exists()) continue;
            ArrayList<Humanoid> workers = WarehouseMarket.staff(roster, (RoomInstance)warehouse2);
            boolean stateOwned = this.state.isStateOwned((RoomInstance)warehouse2);
            if (stateOwned || !workers.isEmpty()) {
                unlockIntake(warehouse2);
            } else {
                lockIntake(warehouse2);
            }
            WarehouseMarket.Book[] shelf = this.sharedState.books.get(warehouse2);
            if (shelf == null) continue;
            for (int resourceIndex = 0; resourceIndex < shelf.length; ++resourceIndex) {
                WarehouseMarket.Book book = shelf[resourceIndex];
                if (book == null) continue;
                book.stateOwned = stateOwned;
                if (stateOwned) {
                    book.stakes.clear();
                    book.capitalBasis = 0.0;
                    continue;
                }
                if (workers.isEmpty()) {
                    if (resourceIndex < this.sharedState.crownUnits.length && book.unitsHeld > 0) {
                        this.sharedState.crownUnits[resourceIndex] = WarehouseMarket.safeAdd(this.sharedState.crownUnits[resourceIndex], (long)book.unitsHeld);
                    }
                    if (resourceIndex < this.sharedState.abandonedUnits.length && book.unitsHeld > 0) {
                        this.sharedState.abandonedUnits[resourceIndex] = WarehouseMarket.safeAdd(this.sharedState.abandonedUnits[resourceIndex], (long)book.unitsHeld);
                    }
                    book.unitsHeld = 0;
                    book.stakes.clear();
                    book.capitalBasis = 0.0;
                    continue;
                }
                reconcileOwners(book, workers);
            }
        }
    }

    // ── Private core ────────────────────────────────────────────

    static void reconcileOwners(WarehouseMarket.Book book, ArrayList<Humanoid> workers) {
        if (book.stakes.isEmpty()) return;
        HashSet<Integer> employed = new HashSet<Integer>();
        for (Humanoid humanoid : workers) employed.add(humanoid.id());
        ArrayList<Integer> owners = new ArrayList<Integer>(book.stakes.keySet());
        for (Humanoid worker : workers) {
            if (book.stakes.containsKey(worker.id())) continue;
            owners.add(worker.id());
        }
        double[] dArray = new double[owners.size()];
        boolean[] stillEmployed = new boolean[owners.size()];
        for (int i = 0; i < owners.size(); ++i) {
            dArray[i] = book.stakes.getOrDefault(owners.get(i), 0.0);
            stillEmployed[i] = employed.contains(owners.get(i));
        }
        double[] reassigned = WarehouseKernel.redistributeStakes(dArray, stillEmployed);
        book.stakes.clear();
        for (int i = 0; i < owners.size(); ++i) {
            if (!(reassigned[i] > 0.0)) continue;
            book.stakes.put(owners.get(i), reassigned[i]);
        }
        if (book.stakes.isEmpty()) book.capitalBasis = 0.0;
    }

    private void lockIntake(StockpileInstance warehouse) {
        Map<Integer, Integer> locks = this.sharedState.intakeLocks.computeIfAbsent(warehouse, ignored -> new HashMap<Integer, Integer>());
        for (COORDINATE tile : warehouse.body()) {
            int free;
            TILE_STORAGE crate;
            if (!warehouse.is(tile) || (crate = warehouse.storage(tile.x(), tile.y())) == null || crate.resource() == null || (free = crate.storageReservable()) <= 0) continue;
            crate.storageReserve(free);
            locks.merge(tileKey(tile.x(), tile.y()), free, Integer::sum);
        }
    }

    private void unlockIntake(StockpileInstance warehouse) {
        Map<Integer, Integer> locks = this.sharedState.intakeLocks.remove(warehouse);
        if (locks == null) return;
        for (Map.Entry<Integer, Integer> entry : locks.entrySet()) {
            int release;
            int key = entry.getKey();
            TILE_STORAGE crate = warehouse.storage(tileX(key), tileY(key));
            if (crate == null || (release = Math.min(Math.max(0, entry.getValue()), crate.storageReserved())) <= 0) continue;
            crate.storageUnreserve(release);
        }
    }

    private void resolvePending() {
        RESOURCE resource;
        StockpileInstance warehouse;
        if (this.sharedState.pending.isEmpty() && this.sharedState.pendingIntakeLocks.isEmpty() && this.sharedState.pendingDirectClaims.isEmpty() && this.sharedState.pendingRetailBooks.isEmpty() && !this.sharedState.inferCrownFromLoose || SETT.ROOMS() == null) {
            return;
        }
        for (WarehouseMarket.PendingBook pendingBook : this.sharedState.pending) {
            warehouse = WarehouseMarket.warehouseAt(pendingBook.x, pendingBook.y);
            resource = WarehouseMarket.resource(pendingBook.resourceKey);
            if (warehouse == null || resource == null) continue;
            WarehouseMarket.Book book = WarehouseMarket.book(this.sharedState, warehouse, resource);
            book.unitsHeld = pendingBook.unitsHeld;
            book.stakes.putAll(pendingBook.stakes);
            book.capitalBasis = pendingBook.capitalBasis;
        }
        this.sharedState.pending.clear();
        for (WarehouseMarket.PendingIntakeLock pendingIntakeLock : this.sharedState.pendingIntakeLocks) {
            warehouse = WarehouseMarket.warehouseAt(pendingIntakeLock.x, pendingIntakeLock.y);
            if (warehouse == null) continue;
            this.sharedState.intakeLocks.put(warehouse, new HashMap<Integer, Integer>(pendingIntakeLock.tiles));
        }
        this.sharedState.pendingIntakeLocks.clear();
        for (WarehouseMarket.PendingDirectClaim pendingDirectClaim : this.sharedState.pendingDirectClaims) {
            RoomInstance producer = WarehouseMarket.producerAt(pendingDirectClaim.x, pendingDirectClaim.y);
            resource = WarehouseMarket.resource(pendingDirectClaim.resourceKey);
            if (producer == null || resource == null || pendingDirectClaim.unitsHeld <= 0) continue;
            this.wholesale.recordDirectClaim(producer, resource, pendingDirectClaim.unitsHeld);
        }
        this.sharedState.pendingDirectClaims.clear();
        for (WarehouseMarket.PendingRetailBook pendingRetailBook : this.sharedState.pendingRetailBooks) {
            RoomInstance retailer = WarehouseMarket.producerAt(pendingRetailBook.x, pendingRetailBook.y);
            resource = WarehouseMarket.resource(pendingRetailBook.resourceKey);
            if (retailer == null || resource == null || !RetailSyncEngine.isRetailBlueprint(retailer.blueprintI())) continue;
            WarehouseMarket.RetailBook[] shelf = this.sharedState.retailBooks.computeIfAbsent(retailer, ignored -> new WarehouseMarket.RetailBook[RESOURCES.ALL().size()]);
            WarehouseMarket.RetailBook book = new WarehouseMarket.RetailBook();
            book.observedStock = pendingRetailBook.observedStock;
            book.lots.addAll(pendingRetailBook.lots);
            shelf[resource.index()] = book;
        }
        this.sharedState.pendingRetailBooks.clear();
        if (this.sharedState.inferCrownFromLoose) {
            inferCrownFromLoose();
            this.sharedState.inferCrownFromLoose = false;
        }
    }

    private void inferCrownFromLoose() {
        this.crown.ensureCrownCapacity();
        long[] loose = new long[this.sharedState.crownUnits.length];
        for (Object tile : SETT.TILE_BOUNDS) {
            COORDINATE c = (COORDINATE) tile;
            THINGS.Thing thing = SETT.THINGS().getFirst(c.x(), c.y());
            while (thing != null) {
                ThingsResources.ScatteredResource pile;
                if (thing instanceof ThingsResources.ScatteredResource && !(pile = (ThingsResources.ScatteredResource) thing).isRemoved()) {
                    int resource = pile.resource().index();
                    if (resource >= 0 && resource < loose.length) {
                        loose[resource] = Math.min(Long.MAX_VALUE, loose[resource] + (long)Math.max(0, pile.amount()));
                    }
                }
                thing = thing.tileNext();
            }
        }
        long[] titled = new long[this.sharedState.crownUnits.length];
        for (Map.Entry<Integer, ArrayList<WarehouseMarket.DirectClaim>> entry : this.sharedState.directClaims.entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() >= titled.length) continue;
            for (WarehouseMarket.DirectClaim claim : entry.getValue()) {
                titled[entry.getKey()] = Math.min(Long.MAX_VALUE, titled[entry.getKey()] + (long)Math.max(0, claim.unitsHeld));
            }
        }
        for (WarehouseMarket.Book[] bookArray : this.sharedState.books.values()) {
            for (int resource = 0; resource < bookArray.length && resource < titled.length; ++resource) {
                WarehouseMarket.Book book = bookArray[resource];
                if (book == null || book.unitsHeld <= 0) continue;
                titled[resource] = Math.min(Long.MAX_VALUE, titled[resource] + (long)book.unitsHeld);
            }
        }
        for (int resource = 0; resource < this.sharedState.crownUnits.length; ++resource) {
            this.sharedState.crownUnits[resource] = Math.max(this.sharedState.crownUnits[resource], Math.max(0L, loose[resource] - titled[resource]));
        }
    }

    // ── Tile-key helpers ────────────────────────────────────────

    static int tileKey(int x, int y) {
        return x << 16 | y & 0xFFFF;
    }

    static int tileX(int key) {
        return key >>> 16;
    }

    static int tileY(int key) {
        return key & 0xFFFF;
    }
}
