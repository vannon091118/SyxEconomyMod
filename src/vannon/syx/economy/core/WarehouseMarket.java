package vannon.syx.economy.core;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.type.HCLASSES;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.infra.stockpile.StockpileTally;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.stats.STATS;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import vannon.syx.economy.core.warehouse.market.MarketSharedState;

/**
 * WarehouseMarket — pure facade for the warehouse-market subsystem.
 *
 * <p>Sprint M-1 (T-101..T-108): all engine logic extracted into 6 dedicated engines.
 * This facade delegates every public method to the appropriate engine and handles
 * only save/load serialization, static utility helpers, and inner record types.</p>
 */
public final class WarehouseMarket implements Saveable {
    static final int FORMAT = 8;

    private final MarketSharedState sharedState = new MarketSharedState();
    private final WholesaleEngine wholesale;
    private final CrownTitleEngine crown;
    private final AutoProcurementEngine procurement;
    private final RetailSyncEngine retail;
    private final MarketMaintenanceEngine maintenance;
    private final MarketTaxEngine tax;
    private final StateWarehouses state;
    private final FlowPrices prices;

    static boolean supportsFormat(int version) {
        return version >= 1 && version <= 8;
    }

    public WarehouseMarket(StateWarehouses state, FlowPrices prices) {
        this.state = state;
        this.prices = prices;
        this.wholesale = new WholesaleEngine(sharedState, state, prices);
        this.crown = new CrownTitleEngine(sharedState, state, prices, wholesale);
        this.procurement = new AutoProcurementEngine(sharedState, state, prices, wholesale);
        this.retail = new RetailSyncEngine(sharedState, prices);
        this.maintenance = new MarketMaintenanceEngine(sharedState, state, crown, wholesale);
        this.tax = new MarketTaxEngine(sharedState, state, prices);
    }

    // ── Read-accessors ──────────────────────────────────────────

    public long lastBought()             { return this.wholesale.lastBought; }
    public long lastSold()               { return this.wholesale.lastSold; }
    public int  lastUnitsBought()        { return this.wholesale.lastUnitsBought; }
    public int  lastUnitsSold()          { return this.wholesale.lastUnitsSold; }
    public long lastConstructionPaid()   { return this.procurement.lastConstructionPaid; }
    public long lastExportBought()       { return this.procurement.lastExportBought; }
    public long lastTaxed()              { return this.tax.lastTaxed; }
    public int  lastTaxPayers()          { return this.tax.lastTaxPayers; }

    // ── Lifecycle ───────────────────────────────────────────────

    public void beginPurchases() { this.wholesale.beginPurchases(); }
    public void beginTick()      { this.wholesale.beginTick(); }

    // ── Wholesale (T-102) ───────────────────────────────────────

    public long buy(FlowMeter meter, FlowPrices prices, Roster roster, Wallets wallets, FirmLedger ledger) {
        return this.wholesale.buy(meter, prices, roster, wallets, ledger);
    }
    public Settlement sellInputs(FlowMeter meter, FlowPrices prices, Roster roster, Wallets wallets, FirmLedger ledger) {
        return this.wholesale.sellInputs(meter, prices, roster, wallets, ledger);
    }
    public int distributeSale(int[] quantities, int amount, Roster roster, Wallets wallets, FirmLedger ledger) {
        return this.wholesale.distributeSale(quantities, amount, roster, wallets, ledger);
    }

    // ── Crown (T-103) ───────────────────────────────────────────

    public void recordProducerlessOutput(FlowMeter meter) { this.crown.recordProducerlessOutput(meter); }
    public long crownUnits(RESOURCE resource)              { return this.crown.crownUnits(resource); }
    public long buyCheaperCrownGoods(Roster r, Wallets w)  { return this.crown.buyCheaperCrownGoods(r, w); }
    public long buyRemainingCrownGoods(Roster r, Wallets w) { return this.crown.buyRemainingCrownGoods(r, w); }
    public OwnerlessRetailClaims waiveOwnerlessRetailClaims(int[] sold, int[] wholesale) {
        return this.crown.waiveOwnerlessRetailClaims(sold, wholesale);
    }

    // ── Retail (T-104) ──────────────────────────────────────────

    public void observeRetailDeliveries() { this.retail.observeRetailDeliveries(); }
    public RetailQuote retailWholesaleQuote(RoomInstance seller, int[] sold) {
        return this.retail.retailWholesaleQuote(seller, sold);
    }

    // ── Procurement (T-105) ─────────────────────────────────────

    public int[] observeConstructionWithdrawals() { return this.procurement.observeConstructionWithdrawals(); }
    public long buyConstructionMaterials(int[] w, int[] sw, Roster r, Wallets wa, FirmLedger l) {
        return this.procurement.buyConstructionMaterials(w, sw, r, wa, l);
    }
    public int[] observeExportWithdrawals() { return this.procurement.observeExportWithdrawals(); }
    public long buyExports(int[] shipments, Roster r, Wallets wa, FirmLedger l) {
        return this.procurement.buyExports(shipments, r, wa, l);
    }

    // ── Maintenance (T-106) ─────────────────────────────────────

    public void settleSeizures(Roster r, Wallets w) { this.maintenance.settleSeizures(r, w); }
    public void prune(Roster r)                      { this.maintenance.prune(r); }

    // ── Tax (T-107) ─────────────────────────────────────────────

    public long taxInventory(Roster r, Wallets w, FirmLedger l) {
        return this.tax.taxInventory(r, w, l);
    }

    // ── Read-through ────────────────────────────────────────────

    public int stateStock(RESOURCE resource) {
        if (resource == null || SETT.ROOMS() == null) return 0;
        long total = 0L;
        StockpileTally tally = SETT.ROOMS().STOCKPILE.tally();
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            StockpileInstance warehouse = (StockpileInstance)SETT.ROOMS().STOCKPILE.getInstance(i);
            if (warehouse == null || !warehouse.exists() || !this.state.isStateOwned((RoomInstance)warehouse)
                || (total += (long)Math.max(0, tally.amount.get(resource, warehouse))) < Integer.MAX_VALUE) continue;
            return Integer.MAX_VALUE;
        }
        return (int)total;
    }

    // ── Save / Load / Clear ─────────────────────────────────────

    public void save(FilePutter file) {
        file.i(FORMAT);
        // V8 — all books, retail books, intake locks, direct claims, and crown
        // state are now held in MarketSharedState (T-101). Engines read/write
        // directly. The save format itself is unchanged — only the in-memory
        // holder moved from WarehouseMarket fields to MarketSharedState.
        int goods = RESOURCES.ALL().size();
        // books
        int bookCount = 0;
        for (Map.Entry<StockpileInstance, Book[]> entry : this.sharedState.books.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().exists()) continue;
            ++bookCount;
        }
        file.i(bookCount);
        for (Map.Entry<StockpileInstance, Book[]> entry : this.sharedState.books.entrySet()) {
            StockpileInstance warehouse = entry.getKey();
            if (warehouse == null || !warehouse.exists()) continue;
            file.i(warehouse.mX());
            file.i(warehouse.mY());
            for (int resource = 0; resource < entry.getValue().length && resource < goods; ++resource) {
                Book book = entry.getValue()[resource];
                if (book == null) { file.chars(""); continue; }
                file.chars(((RESOURCE)RESOURCES.ALL().get(resource)).key);
                file.i(book.unitsHeld);
                file.d(book.capitalBasis);
                file.bool(book.stateOwned);
                file.i(book.stakes.size());
                for (Map.Entry<Integer, Double> stake : book.stakes.entrySet()) {
                    file.i(stake.getKey());
                    file.d(stake.getValue());
                }
            }
        }
        // intake locks
        int lockCount = 0;
        for (Map.Entry<StockpileInstance, Map<Integer, Integer>> e : this.sharedState.intakeLocks.entrySet()) {
            if (e.getKey() != null && e.getKey().exists()) ++lockCount;
        }
        file.i(lockCount);
        for (Map.Entry<StockpileInstance, Map<Integer, Integer>> entry : this.sharedState.intakeLocks.entrySet()) {
            StockpileInstance warehouse = entry.getKey();
            if (warehouse == null || !warehouse.exists()) continue;
            file.i(warehouse.mX());
            file.i(warehouse.mY());
            file.i(entry.getValue().size());
            for (Map.Entry<Integer, Integer> lock : entry.getValue().entrySet()) {
                file.i(lock.getKey());
                file.i(lock.getValue());
            }
        }
        // crown units
        file.i(this.sharedState.crownUnits.length);
        for (long unit : this.sharedState.crownUnits) file.l(unit);
        // abandoned units
        file.i(this.sharedState.abandonedUnits.length);
        for (long unit : this.sharedState.abandonedUnits) file.l(unit);
        // direct claims
        file.i(this.sharedState.directClaims.size());
        for (Map.Entry<Integer, ArrayList<DirectClaim>> entry : this.sharedState.directClaims.entrySet()) {
            file.i(entry.getKey());
            file.i(entry.getValue().size());
            for (DirectClaim claim : entry.getValue()) {
                if (claim.producer == null || !claim.producer.exists()) { file.i(0); file.i(0); file.i(0); continue; }
                file.i(claim.producer.mX());
                file.i(claim.producer.mY());
                file.i(claim.unitsHeld);
            }
        }
        // retail books
        int retailCount = 0;
        for (Map.Entry<RoomInstance, RetailBook[]> entry : this.sharedState.retailBooks.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().exists()) continue;
            ++retailCount;
        }
        file.i(retailCount);
        for (Map.Entry<RoomInstance, RetailBook[]> entry : this.sharedState.retailBooks.entrySet()) {
            RoomInstance room = entry.getKey();
            if (room == null || !room.exists()) continue;
            file.i(room.mX());
            file.i(room.mY());
            for (int resource = 0; resource < entry.getValue().length && resource < goods; ++resource) {
                RetailBook book = entry.getValue()[resource];
                if (book == null) { file.chars(""); continue; }
                file.chars(((RESOURCE)RESOURCES.ALL().get(resource)).key);
                file.i(book.observedStock);
                file.i(book.lots.size());
                for (RetailLot lot : book.lots) { file.i(lot.units); file.i(lot.unitPrice); }
            }
        }
    }

    public void load(FileGetter file) throws IOException {
        int version = file.i();
        if (!WarehouseMarket.supportsFormat(version)) {
            throw new IOException("WarehouseMarket: unsupported save format " + version);
        }
        int goods = RESOURCES.ALL().size();
        // books (V1+)
        int bookCount = Math.max(0, file.i());
        for (int i = 0; i < bookCount; ++i) {
            int x2 = file.i();
            int y = file.i();
            Map<Integer, Double> stakes = new HashMap<Integer, Double>();
            int unitsHeld = 0;
            double capitalBasis = 0.0;
            boolean stateOwned = false;
            String key = file.chars();
            RESOURCE resource;
            int paidIn;
            if (!key.isEmpty() && (resource = WarehouseMarket.resource(key)) != null && (unitsHeld = Math.max(0, file.i())) > 0) {
                capitalBasis = file.d();
                stateOwned = version >= 2 ? file.bool() : false;
                int stakeCount = Math.max(0, file.i());
                for (int stake = 0; stake < stakeCount; ++stake) {
                    int id = file.i();
                    double amount = file.d();
                    if (amount <= 0.0 || !Double.isFinite(amount)) continue;
                    stakes.put(id, amount);
                }
                paidIn = version >= 6 ? Math.max(0, file.i()) : 0;
                if (version < 6) capitalBasis += (double)paidIn;
            } else {
                if (!key.isEmpty()) { file.d(); if (version >= 2) file.bool(); int stakeCount = Math.max(0, file.i());
                    for (int stake = 0; stake < stakeCount; ++stake) { file.i(); file.d(); } }
                if (version >= 6) file.i();
                continue;
            }
            this.sharedState.pending.add(new PendingBook(x2, y, key, unitsHeld, stakes, capitalBasis));
        }
        // intake locks (V3+)
        if (version >= 3) {
            int lockCount = Math.max(0, file.i());
            for (int i = 0; i < lockCount; ++i) {
                int x = file.i();
                int y = file.i();
                int tileCount = Math.max(0, file.i());
                Map<Integer, Integer> tiles = new HashMap<Integer, Integer>();
                for (int tile = 0; tile < tileCount; ++tile) {
                    int key2 = file.i();
                    int amount = Math.max(0, file.i());
                    if (amount <= 0) continue;
                    tiles.put(key2, amount);
                }
                this.sharedState.pendingIntakeLocks.add(new PendingIntakeLock(x, y, tiles));
            }
        }
        // direct claims (V4+)
        if (version >= 4) {
            int claimCount = Math.max(0, file.i());
            for (int i = 0; i < claimCount; ++i) {
                int x = file.i();
                int y = file.i();
                String key2 = file.chars();
                int unitsHeld2 = Math.max(0, file.i());
                if (unitsHeld2 <= 0) continue;
                this.sharedState.pendingDirectClaims.add(new PendingDirectClaim(x, y, key2, unitsHeld2));
            }
        }
        // crown units (V5+), abandoned units, retail books
        boolean bl = this.sharedState.inferCrownFromLoose = version < 5;
        if (version >= 5) {
            int crownLen = Math.max(0, file.i());
            this.sharedState.crownUnits = new long[crownLen];
            for (int i = 0; i < crownLen; ++i) this.sharedState.crownUnits[i] = file.l();
            int abandonedLen = Math.max(0, file.i());
            this.sharedState.abandonedUnits = new long[abandonedLen];
            for (int i = 0; i < abandonedLen; ++i) this.sharedState.abandonedUnits[i] = Math.max(0L, file.l());
        }
        // retail books (V7+)
        if (version >= 7) {
            int retailLen = Math.max(0, file.i());
            for (int i = 0; i < retailLen; ++i) {
                int x3 = file.i();
                int y = file.i();
                String key3 = file.chars();
                int observed = Math.max(0, file.i());
                int lotCount = Math.max(0, file.i());
                ArrayList<RetailLot> lots = new ArrayList<RetailLot>();
                for (int lot = 0; lot < lotCount; ++lot) {
                    int units = Math.max(0, file.i());
                    int price = Math.max(0, file.i());
                    if (units <= 0) continue;
                    lots.add(new RetailLot(units, price));
                }
                this.sharedState.pendingRetailBooks.add(new PendingRetailBook(x3, y, key3, observed, lots));
            }
        }
    }

    public void clear() {
        this.sharedState.books.clear();
        this.sharedState.pending.clear();
        this.sharedState.directClaims.clear();
        this.sharedState.pendingDirectClaims.clear();
        this.sharedState.retailBooks.clear();
        this.sharedState.pendingRetailBooks.clear();
        this.sharedState.crownUnits = new long[0];
        this.sharedState.abandonedUnits = new long[0];
        this.sharedState.inferCrownFromLoose = true;
        this.sharedState.intakeLocks.clear();
        this.sharedState.pendingIntakeLocks.clear();
        this.wholesale.clear();
        this.procurement.clear();
        this.tax.clear();
    }

    // ── Static helpers ──────────────────────────────────────────

    static ArrayList<Humanoid> staff(Roster roster, RoomInstance room) {
        ArrayList<Humanoid> result = new ArrayList<Humanoid>();
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid worker = roster.get(i);
            if (worker.indu().clas() == HCLASSES.SLAVE() || STATS.WORK().EMPLOYED.get(worker.indu()) != room) continue;
            result.add(worker);
        }
        return result;
    }

    static Humanoid alive(Roster roster, int id) {
        for (int i = 0; i < roster.size(); ++i) {
            if (roster.get(i).id() != id) continue;
            return roster.get(i);
        }
        return null;
    }

    static int   safeAdd(int left, int right)   { return (int)Math.min(Integer.MAX_VALUE, (long)Math.max(0, left) + (long)Math.max(0, right)); }
    static long  safeAdd(long left, long right) { long b = Math.max(0L, left); long a = Math.max(0L, right); return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : b + a; }
    static long  safeMoneyAdd(long left, long right) { if (right <= 0L) return Math.max(0L, left); return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right; }

    public static boolean crownBeforePrivate(int crownPrice, int privatePrice) { return Math.max(0, crownPrice) <= Math.max(0, privatePrice); }
    public static int crownPurchasableUnits(long physical, long warehouseTitle, long directTitle, long crownAvailable) {
        long untitled = Math.max(0L, physical - Math.max(0L, warehouseTitle) - Math.max(0L, directTitle));
        return (int)Math.min(Integer.MAX_VALUE, Math.min(Math.max(0L, crownAvailable), untitled));
    }
    public static int crownUnitsConsumed(long available, int wanted) {
        if (available <= 0L || wanted <= 0) return 0;
        return (int)Math.min((long)wanted, Math.min(Integer.MAX_VALUE, available));
    }
    public static int proportionalValue(int amount, long claimedValue, long totalValue) {
        if (amount <= 0 || claimedValue <= 0L || totalValue <= 0L) return 0;
        if (claimedValue >= totalValue) return amount;
        return BigInteger.valueOf(amount).multiply(BigInteger.valueOf(claimedValue)).divide(BigInteger.valueOf(totalValue)).intValue();
    }

    static StockpileInstance warehouseAt(int x, int y) {
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            StockpileInstance warehouse = (StockpileInstance)SETT.ROOMS().STOCKPILE.getInstance(i);
            if (warehouse == null || !warehouse.exists() || warehouse.mX() != x || warehouse.mY() != y) continue;
            return warehouse;
        }
        return null;
    }
    static RoomInstance producerAt(int x, int y) {
        for (RoomBlueprintIns blueprint : SETT.ROOMS().ins()) {
            for (int i = 0; i < blueprint.instancesSize(); ++i) {
                RoomInstance room = blueprint.getInstance(i);
                if (room == null || !room.exists() || room.mX() != x || room.mY() != y) continue;
                return room;
            }
        }
        return null;
    }
    static RESOURCE resource(String key) {
        for (RESOURCE r : RESOURCES.ALL()) { if (key.equals(r.key)) return r; }
        return null;
    }

    static Book book(MarketSharedState sharedState, StockpileInstance warehouse, RESOURCE resource) {
        Book[] shelf = sharedState.books.computeIfAbsent(warehouse, ignored -> new Book[RESOURCES.ALL().size()]);
        if (shelf[resource.index()] == null) shelf[resource.index()] = new Book();
        return shelf[resource.index()];
    }

    // ── Inner Records ───────────────────────────────────────────

    public static final class Book {
        int unitsHeld;
        double capitalBasis;
        final Map<Integer, Double> stakes = new HashMap<Integer, Double>();
        boolean stateOwned;
        Book() {}
    }

    public record CrownStorage(StockpileInstance warehouse, int untitledUnits) {}
    public record Purchase(int units, int paid) {
        public static final Purchase NONE = new Purchase(0, 0);
    }

    public static final class DirectClaim {
        final RoomInstance producer;
        int unitsHeld;
        DirectClaim(RoomInstance producer, int unitsHeld) { this.producer = producer; this.unitsHeld = unitsHeld; }
    }

    public record Settlement(long billed, long credited) {}
    public record SaleDistribution(int credited, int merchantUnits, int directUnits, int crownUnits) {}

    public record RetailQuote(int total, int[] byResource) {
        public RetailQuote { byResource = byResource == null ? new int[]{} : (int[])byResource.clone(); }
        @Override public int[] byResource() { return byResource.clone(); }
    }

    public static final class RetailBook {
        int observedStock = -1;
        final java.util.ArrayDeque<RetailLot> lots = new java.util.ArrayDeque();
        RetailBook() {}
    }

    public record OwnerlessRetailClaims(int waivedValue, int[] payableQuantities) {
        public OwnerlessRetailClaims { payableQuantities = payableQuantities == null ? new int[]{} : (int[])payableQuantities.clone(); }
        @Override public int[] payableQuantities() { return payableQuantities.clone(); }
    }

    public static final class RetailLot {
        int units;
        final int unitPrice;
        RetailLot(int units, int unitPrice) { this.units = Math.max(0, units); this.unitPrice = Math.max(0, unitPrice); }
    }

    public static final class PendingBook {
        final int x, y;
        final String resourceKey;
        final int unitsHeld;
        final Map<Integer, Double> stakes;
        final double capitalBasis;
        PendingBook(int x, int y, String resourceKey, int unitsHeld, Map<Integer, Double> stakes, double capitalBasis) {
            this.x = x; this.y = y; this.resourceKey = resourceKey; this.unitsHeld = unitsHeld; this.stakes = stakes; this.capitalBasis = capitalBasis;
        }
    }

    public static final class PendingIntakeLock {
        final int x, y;
        final Map<Integer, Integer> tiles;
        PendingIntakeLock(int x, int y, Map<Integer, Integer> tiles) { this.x = x; this.y = y; this.tiles = tiles; }
    }

    public static final class PendingDirectClaim {
        final int x, y;
        final String resourceKey;
        final int unitsHeld;
        PendingDirectClaim(int x, int y, String resourceKey, int unitsHeld) {
            this.x = x; this.y = y; this.resourceKey = resourceKey; this.unitsHeld = unitsHeld;
        }
    }

    public static final class PendingRetailBook {
        final int x, y;
        final String resourceKey;
        final int observedStock;
        final ArrayList<RetailLot> lots;
        PendingRetailBook(int x, int y, String resourceKey, int observedStock, ArrayList<RetailLot> lots) {
            this.x = x; this.y = y; this.resourceKey = resourceKey; this.observedStock = observedStock; this.lots = lots;
        }
    }
}
