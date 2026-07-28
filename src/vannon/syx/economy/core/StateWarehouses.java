package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.time.TIME;
import init.resources.RBIT;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.type.HCLASSES;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.infra.stockpile.StockpileTally;
import settlement.room.main.Room;
import settlement.room.main.RoomInstance;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.employment.LaborMarketAccess;
import settlement.room.main.employment.RoomEmployment;
import settlement.room.main.job.StorageCrate;
import settlement.stats.STATS;
import snake2d.LOG;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.rnd.RND;
import vannon.syx.economy.adapter.ISyxWarehouse;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EventLog;
import vannon.syx.economy.core.MilitaryPayroll;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class StateWarehouses implements Saveable {
    static final int FORMAT = 4;

    /** Operating mode for state-owned warehouses. */
    public enum TradeMode {
        NORMAL,      // buy and sell according to configured prices
        BUY_ONLY,    // accumulate stock, never sell
        SELL_ONLY    // liquidate stock, never buy
    }

    public StateWarehouses(ISyxWarehouse warehouseAdapter) {
        this.warehouseAdapter = warehouseAdapter;
    }

    final HashSet<Long> owned = new HashSet<>();
    final HashSet<Long> liquidating = new HashSet<>();
    int[] buyPrice = new int[0];
    int[] sellPrice = new int[0];
    TradeMode tradeMode = TradeMode.NORMAL;
    int[] crownMarketPrice = new int[0];
    boolean[] hoardingBuyerFor = new boolean[0];
    private int[] constructionDelivered = new int[0];
    private int lastSeason = -1;
    private long lastWagesPaid;
    private int lastWorkersPaid;
    private int lastWorkersUnpaid;
    private long lastBought;
    private long lastSold;
    private int lastUnitsBought;
    private int lastUnitsSold;
    private long lastCrownMarketSold;
    private int lastCrownMarketUnitsSold;
    private final ISyxWarehouse warehouseAdapter;

    static boolean supportsFormat(int version) {
        return version >= 1 && version <= 4;
    }

    public long lastWagesPaid() {
        return this.lastWagesPaid;
    }

    public int lastWorkersPaid() {
        return this.lastWorkersPaid;
    }

    public int lastWorkersUnpaid() {
        return this.lastWorkersUnpaid;
    }

    public long lastBought() {
        return this.lastBought;
    }

    public long lastSold() {
        return this.lastSold;
    }

    public int lastUnitsBought() {
        return this.lastUnitsBought;
    }

    public int lastUnitsSold() {
        return this.lastUnitsSold;
    }

    public long lastCrownMarketSold() {
        return this.lastCrownMarketSold;
    }

    public int lastCrownMarketUnitsSold() {
        return this.lastCrownMarketUnitsSold;
    }

    static long key(RoomInstance room) {
        return (long)room.mX() << 32 | (long)room.mY() & 0xFFFFFFFFL;
    }

    public boolean isStateOwned(RoomInstance room) {
        return room != null && !this.owned.isEmpty() && this.owned.contains(StateWarehouses.key(room));
    }

    public void setStateOwned(RoomInstance room, boolean state) {
        if (room == null) {
            return;
        }
        if (state) {
            this.owned.add(StateWarehouses.key(room));
        } else {
            this.owned.remove(StateWarehouses.key(room));
            this.liquidating.remove(StateWarehouses.key(room));
        if (room instanceof StockpileInstance) {
            StockpileInstance granary = (StockpileInstance)room;
            this.warehouseAdapter.setStoring(granary, false);
        }
        }
    }

    public void toggleStateOwned(RoomInstance room) {
        this.setStateOwned(room, !this.isStateOwned(room));
    }

    public int ownedCount() {
        return this.owned.size();
    }

    public void prune() {
        if (this.owned.isEmpty() || SETT.ROOMS() == null) {
            return;
        }
        HashSet<Long> live = new HashSet<Long>();
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            StockpileInstance warehouse = SETT.ROOMS().STOCKPILE.getInstance(i);
            if (warehouse == null || !warehouse.exists()) continue;
            live.add(StateWarehouses.key(warehouse));
        }
        this.owned.retainAll(live);
        this.liquidating.retainAll(live);
    }

    void ensureSized() {
        int size = RESOURCES.ALL().size();
        if (this.buyPrice.length == size && this.crownMarketPrice.length == size) {
            return;
        }
        int oldCrownSize = this.crownMarketPrice.length;
        this.buyPrice = Arrays.copyOf(this.buyPrice, size);
        this.sellPrice = Arrays.copyOf(this.sellPrice, size);
        this.crownMarketPrice = Arrays.copyOf(this.crownMarketPrice, size);
        Arrays.fill(this.crownMarketPrice, oldCrownSize, size, 75);
        this.hoardingBuyerFor = Arrays.copyOf(this.hoardingBuyerFor, size);
        this.constructionDelivered = Arrays.copyOf(this.constructionDelivered, size);
    }

    public boolean isHoarding(RoomInstance warehouse) {
        return WarehouseTrade.isHoarding(this, warehouse);
    }

    public boolean isLiquidating(RoomInstance warehouse) {
        return WarehouseTrade.isLiquidating(this, warehouse);
    }

    public void setLiquidating(RoomInstance warehouse, boolean value) {
        WarehouseTrade.setLiquidating(this, warehouse, value);
    }

    public void toggleLiquidating(RoomInstance warehouse) {
        WarehouseTrade.toggleLiquidating(this, warehouse);
    }

    public void setAllLiquidating(boolean value) {
        WarehouseTrade.setAllLiquidating(this, value);
    }

    public boolean allLiquidating() {
        return WarehouseTrade.allLiquidating(this);
    }

    public void setTradeMode(TradeMode mode) {
        WarehouseTrade.setTradeMode(this, mode);
    }

    public TradeMode tradeMode() {
        return WarehouseTrade.tradeMode(this);
    }

    /**
     * Resets all buy/sell prices to 80% / 110% of the current market anchor.
     * Uses {@link FlowPrices#anchor(int)} for the per-resource anchor value.
     */
    public void standardizeAllPrices(FlowPrices prices) {
        WarehouseTrade.standardizeAllPrices(this, prices);
    }

    public int buyPrice(RESOURCE resource) {
        return WarehouseTrade.buyPrice(this, resource);
    }

    public void setBuyPrice(RESOURCE resource, int price) {
        WarehouseTrade.setBuyPrice(this, resource, price);
    }

    public int sellPrice(RESOURCE resource) {
        return WarehouseTrade.sellPrice(this, resource);
    }

    public void setSellPrice(RESOURCE resource, int price) {
        WarehouseTrade.setSellPrice(this, resource, price);
    }

    public int crownMarketPrice(RESOURCE resource) {
        return WarehouseTrade.crownMarketPrice(this, resource);
    }

    public void setCrownMarketPrice(RESOURCE resource, int price) {
        WarehouseTrade.setCrownMarketPrice(this, resource, price);
    }

    private static int clampPrice(int price) {
        return WarehouseTrade.clampPrice(price);
    }

    public boolean buysAt(RESOURCE resource, int marketPrice) {
        return WarehouseTrade.buysAt(this, resource, marketPrice);
    }

    public boolean sellsAt(RoomInstance warehouse, RESOURCE resource, int marketPrice) {
        return WarehouseTrade.sellsAt(this, warehouse, resource, marketPrice);
    }

    public long[] withheldStock(int[] stateConstructionWithdrawals) {
        int i;
        long[] withheld = new long[RESOURCES.ALL().size()];
        this.ensureSized();
        if (!EconConfig.stateWarehousesEnabled || this.owned.isEmpty() || SETT.ROOMS() == null) {
            Arrays.fill(this.constructionDelivered, 0);
            return withheld;
        }
        StockpileTally tally = SETT.ROOMS().STOCKPILE.tally();
        for (i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            StockpileInstance granary = SETT.ROOMS().STOCKPILE.getInstance(i);
            if (granary == null || !granary.exists() || !this.isHoarding(granary)) continue;
            for (RESOURCE resource : RESOURCES.ALL()) {
                if (!granary.crateMask.has(resource)) continue;
                int n = resource.index();
                withheld[n] = withheld[n] + (long)Math.max(0, tally.amount.get(resource, granary));
            }
        }
        i = 0;
        while (i < withheld.length) {
            int consumed = stateConstructionWithdrawals != null && i < stateConstructionWithdrawals.length ? Math.max(0, stateConstructionWithdrawals[i]) : 0;
            int n = i++;
            withheld[n] = withheld[n] + (long)consumed;
        }
        return withheld;
    }

    public long[] withheldStock() {
        return this.withheldStock(null);
    }

    void recordPurchase(long denari, int units) {
        this.lastBought += denari;
        this.lastUnitsBought += units;
    }

    void recordSale(long denari, int units) {
        this.lastSold += denari;
        this.lastUnitsSold += units;
    }

    void recordCrownMarketSale(long denari, int units) {
        this.lastCrownMarketSold += Math.max(0L, denari);
        this.lastCrownMarketUnitsSold += Math.max(0, units);
    }

    public void beginTick() {
        this.lastSold = 0L;
        this.lastBought = 0L;
        this.lastUnitsSold = 0;
        this.lastUnitsBought = 0;
        this.lastCrownMarketSold = 0L;
        this.lastCrownMarketUnitsSold = 0;
        this.refreshWarehouseState();
    }

    /** Sets employment priority for every state-owned warehouse based on the
     *  configured state warehouse wage. Higher wages make the clerk job more
     *  attractive, pulling workers away from other professions.
     *  Note: RoomEmployment priority is per-blueprint, so this affects all
     *  stockpiles, not only state-owned ones. */
    public void updateEmploymentPriority(double meanWage) {
        if (!EconConfig.stateWarehousesEnabled || !EconConfig.warehouseAutoHireEnabled || SETT.ROOMS() == null) {
            return;
        }
        RoomBlueprintImp stockpileBlueprint = SETT.ROOMS().STOCKPILE;
        RoomEmployment empl = LaborMarketAccess.employmentOf(stockpileBlueprint);
        if (empl == null) {
            return;
        }
        int wage = this.wage();
        int min = empl.priority.min();
        int max = empl.priority.max();
        int priority;
        if (wage <= 0) {
            priority = min;
        } else {
            int base = min + 2;
            int wageBonus = wage / Math.max(1, EconConfig.wageStep);
            priority = base + wageBonus;
            if (meanWage > 0.0 && wage > meanWage) {
                priority += (int)((wage - meanWage) / Math.max(1.0, meanWage) * 5.0);
            }
            priority = Math.max(min, Math.min(max, priority));
        }
        if (empl.priority.get() != priority) {
            empl.priority.set(priority);
        }
        if (this.owned.isEmpty()) {
            return;
        }
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            StockpileInstance granary = SETT.ROOMS().STOCKPILE.getInstance(i);
            if (granary == null || !granary.exists() || !this.isStateOwned(granary)) continue;
            if (granary.employees() != null && granary.employees().hardTarget() <= 0) {
                granary.employees().neededSet(1);
            }
        }
    }

    /**
     * Phase 4 (ISyxWarehouse): resolves whether the storingSet() lock is currently
     * available via the injected warehouse adapter. Called once per refresh tick
     * so any adapter-side initialization (Reflection lookup at construction time)
     * has already settled before the per-granary setStoring() loop runs.
     */
    private boolean hasStoringLock() {
        return this.warehouseAdapter.isStoringLockAvailable();
    }

    private void refreshWarehouseState() {
        this.ensureSized();
        Arrays.fill(this.hoardingBuyerFor, false);
        if (!EconConfig.stateWarehousesEnabled || this.owned.isEmpty() || SETT.ROOMS() == null) {
            return;
        }
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            StockpileInstance granary = SETT.ROOMS().STOCKPILE.getInstance(i);
            if (granary == null || !granary.exists() || !this.isStateOwned(granary)) continue;
            boolean hoarding = this.isHoarding(granary);
            this.warehouseAdapter.setStoring(granary, hoarding);
            if (!hoarding) continue;
            for (RESOURCE resource : RESOURCES.ALL()) {
                if (this.buyPrice[resource.index()] <= 0 || !granary.crateMask.has(resource)) continue;
                this.hoardingBuyerFor[resource.index()] = true;
            }
        }
    }

    void hoardedResourceMask(RBIT.RBITImp target) {
        target.clear();
        if (!EconConfig.stateWarehousesEnabled || this.owned.isEmpty() || SETT.ROOMS() == null) {
            return;
        }
        StockpileTally tally = SETT.ROOMS().STOCKPILE.tally();
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            StockpileInstance warehouse = SETT.ROOMS().STOCKPILE.getInstance(i);
            if (warehouse == null || !warehouse.exists() || !this.isHoarding(warehouse)) continue;
            for (RESOURCE resource : RESOURCES.ALL()) {
                if (!warehouse.crateMask.has(resource) || tally.amount.get(resource, warehouse) <= 0) continue;
                target.or(resource);
            }
        }
    }

    ConstructionSource reserveForConstruction(RESOURCE resource, int workerX, int workerY, int jobX, int jobY, int wanted) {
        if (resource == null || wanted <= 0 || this.owned.isEmpty() || SETT.ROOMS() == null) {
            return null;
        }
        StockpileInstance bestWarehouse = null;
        int bestX = -1;
        int bestY = -1;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            StockpileInstance warehouse = SETT.ROOMS().STOCKPILE.getInstance(i);
            if (warehouse == null || !warehouse.exists() || !this.isHoarding(warehouse) || !warehouse.crateMask.has(resource)) continue;
            for (COORDINATE tile : warehouse.body()) {
                long distance;
                StorageCrate crate;
                if (!warehouse.is(tile) || (crate = warehouse.crate(tile.x(), tile.y())) == null || crate.resource() != resource || crate.reservable() <= 0 || (distance = Math.abs((long)workerX - (long)tile.x()) + Math.abs((long)workerY - (long)tile.y()) + Math.abs((long)jobX - (long)tile.x()) + Math.abs((long)jobY - (long)tile.y())) >= bestDistance) continue;
                bestDistance = distance;
                bestWarehouse = warehouse;
                bestX = tile.x();
                bestY = tile.y();
            }
        }
        if (bestWarehouse == null) {
            return null;
        }
        StorageCrate crate = bestWarehouse.crate(bestX, bestY);
        int amount = Math.min(wanted, crate == null ? 0 : crate.reservable());
        for (int i = 0; i < amount; ++i) {
            crate.findableReserve();
        }
        return amount > 0 ? new ConstructionSource(bestX, bestY, amount) : null;
    }

    int pickupConstructionReservation(int x, int y, RESOURCE resource, int wanted) {
        int amount;
        RoomInstance instance;
        RoomInstance room;
        if (wanted <= 0 || resource == null || SETT.ROOMS() == null) {
            return 0;
        }
        Room room2 = SETT.ROOMS().map.get(x, y);
        RoomInstance roomInstance = room = room2 instanceof RoomInstance ? (instance = (RoomInstance)room2) : null;
        if (!(room instanceof StockpileInstance)) {
            return 0;
        }
        StockpileInstance warehouse = (StockpileInstance)room;
        StorageCrate crate = warehouse.crate(x, y);
        if (crate == null || crate.resource() != resource) {
            return 0;
        }
        for (amount = 0; amount < wanted && crate.findableReservedIs(); ++amount) {
            crate.resourcePickup();
        }
        return amount;
    }

    void recordConstructionDelivery(RESOURCE resource, int amount) {
        if (resource == null || amount <= 0) {
            return;
        }
        this.ensureSized();
        int i = resource.index();
        this.constructionDelivered[i] = (int)Math.min(Integer.MAX_VALUE, (long)this.constructionDelivered[i] + (long)amount);
    }

    int[] matchConstructionDeliveries(int[] constructionWithdrawals) {
        this.ensureSized();
        int[] matched = new int[RESOURCES.ALL().size()];
        for (int i = 0; i < matched.length; ++i) {
            int withdrawn = constructionWithdrawals != null && i < constructionWithdrawals.length ? Math.max(0, constructionWithdrawals[i]) : 0;
            matched[i] = Math.min(withdrawn, Math.max(0, this.constructionDelivered[i]));
            this.constructionDelivered[i] = 0;
        }
        return matched;
    }

    void cancelConstructionReservation(int x, int y, RESOURCE resource, int amount) {
        RoomInstance instance;
        RoomInstance room;
        if (amount <= 0 || resource == null || SETT.ROOMS() == null) {
            return;
        }
        Room room2 = SETT.ROOMS().map.get(x, y);
        RoomInstance roomInstance = room = room2 instanceof RoomInstance ? (instance = (RoomInstance)room2) : null;
        if (!(room instanceof StockpileInstance)) {
            return;
        }
        StockpileInstance warehouse = (StockpileInstance)room;
        StorageCrate crate = warehouse.crate(x, y);
        if (crate == null || crate.resource() != resource) {
            return;
        }
        while (amount-- > 0 && crate.findableReservedIs()) {
            crate.findableReserveCancel();
        }
    }

    public int wage() {
        return MilitaryPayroll.clampWage(EconConfig.stateWarehouseWage);
    }

    public void setWage(int wage) {
        EconConfig.stateWarehouseWage = MilitaryPayroll.clampWage(wage);
    }

    public long payWages(Roster roster, Wallets wallets) {
        int season = TIME.seasons().bitsSinceStart();
        if (this.lastSeason == -1) {
            this.lastSeason = season;
            return 0L;
        }
        if (season == this.lastSeason) {
            return 0L;
        }
        this.lastSeason = season;
        this.lastWagesPaid = 0L;
        this.lastWorkersPaid = 0;
        this.lastWorkersUnpaid = 0;
        int wage = this.wage();
        if (wage <= 0 || roster.size() == 0 || this.owned.isEmpty()) {
            return 0L;
        }
        long budget = Math.max(0L, (long)Math.floor(FACTIONS.player().credits().credits()));
        int offset = RND.rInt(roster.size());
        for (int i = 0; i < roster.size(); ++i) {
            RoomInstance workplace;
            Humanoid clerk = roster.get((i + offset) % roster.size());
            if (clerk.indu().clas() == HCLASSES.SLAVE() || !((workplace = STATS.WORK().EMPLOYED.get(clerk.indu())) instanceof StockpileInstance) || !this.isStateOwned(workplace)) continue;
            if (budget < (long)wage) {
                ++this.lastWorkersUnpaid;
                continue;
            }
            wallets.add(clerk, wage);
            wallets.markPaidThisTick(clerk.indu());
            budget -= (long)wage;
            this.lastWagesPaid += (long)wage;
            ++this.lastWorkersPaid;
        }
        if (this.lastWagesPaid > 0L) {
            FACTIONS.player().credits().inc((double)(-this.lastWagesPaid), FCredits.CTYPE.MISC);
        }
        return this.lastWagesPaid;
    }

    public void save(FilePutter file) {
        int i;
        this.ensureSized();
        file.i(4);
        file.i(this.lastSeason);
        file.i(this.owned.size());
        for (long tile : this.owned) {
            file.l(tile);
        }
        file.i(this.liquidating.size());
        for (long tile : this.liquidating) {
            file.l(tile);
        }
        file.i(this.tradeMode.ordinal());
        int count = 0;
        for (i = 0; i < this.buyPrice.length; ++i) {
            if (this.buyPrice[i] <= 0 && this.sellPrice[i] <= 0 && this.crownMarketPrice[i] == 75) continue;
            ++count;
        }
        file.i(count);
        for (i = 0; i < this.buyPrice.length; ++i) {
            if (this.buyPrice[i] <= 0 && this.sellPrice[i] <= 0 && this.crownMarketPrice[i] == 75) continue;
            file.chars((CharSequence)RESOURCES.ALL().get(i).key);
            file.i(this.buyPrice[i]);
            file.i(this.sellPrice[i]);
            file.i(this.crownMarketPrice[i]);
        }
    }

    public void load(FileGetter file) throws IOException {
        int i;
        int version = file.i();
        if (!StateWarehouses.supportsFormat(version)) {
            throw new IOException("unsupported state warehouse format " + version);
        }
        this.clear();
        this.ensureSized();
        this.lastSeason = file.i();
        int ownedCount = Math.max(0, file.i());
        for (int i2 = 0; i2 < ownedCount; ++i2) {
            this.owned.add(file.l());
        }
        if (version >= 2) {
            int liq = Math.max(0, file.i());
            for (i = 0; i < liq; ++i) {
                this.liquidating.add(file.l());
            }
        }
        if (version >= 4) {
            int mode = file.i();
            this.tradeMode = mode >= 0 && mode < TradeMode.values().length ? TradeMode.values()[mode] : TradeMode.NORMAL;
        }
        int policies = Math.max(0, file.i());
        for (i = 0; i < policies; ++i) {
            String key = file.chars();
            if (version < 2) {
                file.bool();
            }
            int buy = file.i();
            int sell = file.i();
            int crown = version >= 3 ? file.i() : 75;
            RESOURCE resource = StateWarehouses.resource(key);
            if (resource == null) continue;
            this.setBuyPrice(resource, buy);
            this.setSellPrice(resource, sell);
            this.setCrownMarketPrice(resource, crown);
        }
    }

    private static RESOURCE resource(String key) {
        for (int i = 0; i < RESOURCES.ALL().size(); ++i) {
            RESOURCE candidate = RESOURCES.ALL().get(i);
            if (!candidate.key.equals(key)) continue;
            return candidate;
        }
        return null;
    }

    public void clear() {
        this.owned.clear();
        this.liquidating.clear();
        this.tradeMode = TradeMode.NORMAL;
        this.buyPrice = new int[0];
        this.sellPrice = new int[0];
        this.crownMarketPrice = new int[0];
        this.hoardingBuyerFor = new boolean[0];
        this.constructionDelivered = new int[0];
        this.lastSeason = -1;
        this.lastSold = 0L;
        this.lastBought = 0L;
        this.lastWagesPaid = 0L;
        this.lastWorkersUnpaid = 0;
        this.lastWorkersPaid = 0;
        this.lastUnitsSold = 0;
        this.lastUnitsBought = 0;
        this.lastCrownMarketSold = 0L;
        this.lastCrownMarketUnitsSold = 0;
    }

    record ConstructionSource(int x, int y, int amount) {
    }
}

