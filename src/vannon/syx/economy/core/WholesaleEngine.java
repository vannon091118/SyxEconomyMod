package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.infra.stockpile.StockpileTally;
import settlement.room.main.RoomInstance;
import snake2d.LOG;
import vannon.syx.economy.core.warehouse.market.MarketSharedState;

/**
 * T-102 WholesaleEngine — extracted from WarehouseMarket as part of Sprint M-1.
 *
 * <p>Handles all wholesale buying, selling, and sale-distribution logic that
 * previously lived inside WarehouseMarket. Operates on the shared state container
 * ({@link MarketSharedState}) and delegates crown-title interactions directly
 * through sharedState fields until T-103 (CrownTitleEngine) consolidates them.</p>
 *
 * <p>Tracking fields ({@code lastBought}, {@code lastSold}, etc.) live here so
 * that internal methods can update them without enriched return types. The
 * WarehouseMarket facade reads these values through accessor methods.</p>
 */
public final class WholesaleEngine {

    private final MarketSharedState sharedState;
    private final StateWarehouses state;
    private final FlowPrices prices;

    long lastBought;
    long lastSold;
    int lastUnitsBought;
    int lastUnitsSold;

    public WholesaleEngine(MarketSharedState sharedState, StateWarehouses state, FlowPrices prices) {
        this.sharedState = sharedState;
        this.state = state;
        this.prices = prices;
    }

    // ── Public API ──────────────────────────────────────────────

    public long buy(FlowMeter meter, FlowPrices prices, Roster roster, Wallets wallets, FirmLedger ledger) {
        if (!EconConfig.warehouseMarketEnabled || !prices.ready() || SETT.ROOMS() == null) {
            return 0L;
        }
        long paid = 0L;
        for (FlowMeter.FirmSnapshot firm : meter.firmSnapshots()) {
            if (firm.room() instanceof StockpileInstance) continue;
            for (int output = 0; output < firm.outputCount(); ++output) {
                RESOURCE resource;
                int price;
                int units = firm.producedSinceLastSample(output);
                if (units <= 0 || (price = prices.priceRoundedUp((resource = firm.outputResource(output)).index())) <= 0) continue;
                paid += buyOutput(firm, resource, units, price, roster, wallets, ledger);
            }
        }
        this.lastBought += paid;
        return paid;
    }

    public WarehouseMarket.Settlement sellInputs(FlowMeter meter, FlowPrices prices, Roster roster, Wallets wallets, FirmLedger ledger) {
        if (!EconConfig.warehouseMarketEnabled || !prices.ready() || SETT.ROOMS() == null) {
            return new WarehouseMarket.Settlement(0L, 0L);
        }
        long billed = 0L;
        long credited = 0L;
        for (FlowMeter.FirmSnapshot firm : meter.firmSnapshots()) {
            if (firm.room() instanceof StockpileInstance) continue;
            for (int input = 0; input < firm.inputCount(); ++input) {
                RESOURCE resource;
                int price;
                int units = firm.consumedSinceLastSample(input);
                if (units <= 0 || (price = prices.priceRoundedUp((resource = firm.inputResource(input)).index())) <= 0) continue;
                int availableMarketTitle = marketTitledUnits(resource, units, price);
                int crownPricedUnits = crownPricedUnits(units, crownUnits(resource), availableMarketTitle);
                int marketUnits = units - crownPricedUnits;
                int crownPrice = this.state.crownMarketPrice(resource);
                long marketGross = (long)marketUnits * (long)price;
                long crownGross = (long)crownPricedUnits * (long)crownPrice;
                long gross = inputGross(units, marketUnits, price, crownPrice);
                int bill = (int)Math.min(Integer.MAX_VALUE, gross);
                int charged = charge(firm.room(), bill, roster, wallets);
                if (charged > 0) {
                    ledger.recordFirmCost(firm.room(), charged);
                    billed += (long)charged;
                }
                int marketProceeds = WarehouseMarket.proportionalValue(charged, marketGross, marketGross + crownGross);
                if (marketUnits > 0 && marketProceeds > 0) {
                    int[] quantities = new int[RESOURCES.ALL().size()];
                    quantities[resource.index()] = marketUnits;
                    credited += (long)distributeSaleDetailed(quantities, marketProceeds, roster, wallets, ledger, false, false).credited();
                }
                if (crownPricedUnits <= 0) continue;
                consumeCrownTitle(resource, crownPricedUnits);
                this.state.recordCrownMarketSale(charged - marketProceeds, crownPricedUnits);
            }
        }
        return new WarehouseMarket.Settlement(billed, credited);
    }

    public int distributeSale(int[] resourceQuantities, int amount, Roster roster, Wallets wallets, FirmLedger ledger) {
        return distributeSaleDetailed(resourceQuantities, amount, roster, wallets, ledger, false, true).credited();
    }

    public void beginPurchases() {
        this.lastBought = 0L;
        this.lastUnitsBought = 0;
    }

    public void beginTick() {
        this.lastSold = 0L;
        this.lastUnitsSold = 0;
    }

    public void clear() {
        this.lastBought = 0L;
        this.lastSold = 0L;
        this.lastUnitsBought = 0;
        this.lastUnitsSold = 0;
    }

    // ── Crown-title helpers (direct sharedState access) ──────────

    private long crownUnits(RESOURCE resource) {
        if (resource == null) return 0L;
        int idx = resource.index();
        if (idx >= this.sharedState.crownUnits.length) return 0L;
        return Math.max(0L, this.sharedState.crownUnits[idx]);
    }

    private void consumeCrownTitle(RESOURCE resource, int wanted) {
        if (resource == null || wanted <= 0) return;
        int index = resource.index();
        if (index >= this.sharedState.crownUnits.length) return;
        int units = WarehouseMarket.crownUnitsConsumed(this.sharedState.crownUnits[index], wanted);
        this.sharedState.crownUnits[index] -= (long)units;
        this.sharedState.abandonedUnits[index] = Math.max(0L, this.sharedState.abandonedUnits[index] - (long)units);
    }

    private int consumeCrownTitle(int[] remaining) {
        long consumed = 0L;
        for (int resource = 0; resource < remaining.length && resource < this.sharedState.crownUnits.length; ++resource) {
            int wanted = Math.max(0, remaining[resource]);
            if (wanted <= 0 || this.sharedState.crownUnits[resource] <= 0L) continue;
            int units = WarehouseMarket.crownUnitsConsumed(this.sharedState.crownUnits[resource], wanted);
            this.sharedState.crownUnits[resource] -= (long)units;
            this.sharedState.abandonedUnits[resource] = Math.max(0L, this.sharedState.abandonedUnits[resource] - (long)units);
            remaining[resource] -= units;
            consumed += (long)units;
        }
        return (int)Math.min(Integer.MAX_VALUE, consumed);
    }

    // ── Private core methods ────────────────────────────────────

    private long buyOutput(FlowMeter.FirmSnapshot firm, RESOURCE resource, int units, int price, Roster roster, Wallets wallets, FirmLedger ledger) {
        int remaining = units;
        long paid = 0L;
        if (EconConfig.stateWarehousesEnabled && this.state.buysAt(resource, price)) {
            WarehouseMarket.Purchase bought = stateBuy(firm, resource, remaining, this.state.buyPrice(resource), roster, wallets, ledger);
            remaining -= bought.units();
            paid += (long)bought.paid();
        }
        if (remaining <= 0) return paid;
        ArrayList<StockpileInstance> buyers = dealers(resource, false, roster);
        if (buyers.isEmpty()) {
            recordDirectClaim(firm.room(), resource, remaining);
            return paid;
        }
        int[] weights = new int[buyers.size()];
        long totalWeight = 0L;
        for (int i = 0; i < buyers.size(); ++i) {
            weights[i] = freeSpace(resource, buyers.get(i));
            totalWeight += (long)weights[i];
        }
        if (totalWeight <= 0L) {
            recordDirectClaim(firm.room(), resource, remaining);
            return paid;
        }
        int marketUnits = remaining;
        int assignedOffers = 0;
        for (int i = 0; i < buyers.size() && assignedOffers < marketUnits; ++i) {
            int offer = (int)((long)marketUnits * (long)weights[i] / totalWeight);
            if (i == buyers.size() - 1) offer = marketUnits - assignedOffers;
            if ((offer = Math.min(offer, marketUnits - assignedOffers)) <= 0) continue;
            assignedOffers += offer;
            WarehouseMarket.Purchase purchase = purchase(buyers.get(i), firm, resource, offer, price, roster, wallets, ledger);
            remaining -= purchase.units();
            paid += (long)purchase.paid();
        }
        if (remaining > 0) recordDirectClaim(firm.room(), resource, remaining);
        return paid;
    }

    private WarehouseMarket.Purchase stateBuy(FlowMeter.FirmSnapshot firm, RESOURCE resource, int offered, int price, Roster roster, Wallets wallets, FirmLedger ledger) {
        ArrayList<StockpileInstance> granaries = dealers(resource, true, roster);
        if (granaries.isEmpty()) return WarehouseMarket.Purchase.NONE;
        long budget = Math.max(0L, (long)Math.floor(FACTIONS.player().credits().credits()));
        int affordable = (int)Math.min((long)offered, budget / (long)Math.max(1, price));
        if (affordable <= 0) return WarehouseMarket.Purchase.NONE;
        int taken = 0;
        int paid = 0;
        for (StockpileInstance granary : granaries) {
            int room;
            if (taken >= affordable) break;
            if (!this.state.isHoarding((RoomInstance)granary) || (room = freeSpace(resource, granary)) <= 0) continue;
            int units = Math.min(affordable - taken, room);
            int cost = units * price;
            int credited = ledger.distributeFirmRevenue(roster, wallets, firm.room(), cost);
            if (credited <= 0) continue;
            FACTIONS.player().credits().inc((double)(-credited), FCredits.CTYPE.MISC);
            book((StockpileInstance)granary, (RESOURCE)resource).unitsHeld += units;
            taken += units;
            paid += credited;
            this.state.recordPurchase(credited, units);
        }
        this.lastUnitsBought += taken;
        return new WarehouseMarket.Purchase(taken, paid);
    }

    private WarehouseMarket.Purchase purchase(StockpileInstance warehouse, FlowMeter.FirmSnapshot firm, RESOURCE resource, int offered, int price, Roster roster, Wallets wallets, FirmLedger ledger) {
        ArrayList<Humanoid> merchants = WarehouseMarket.staff(roster, (RoomInstance)warehouse);
        if (merchants.isEmpty()) return WarehouseMarket.Purchase.NONE;
        int[] spendable = new int[merchants.size()];
        for (int i = 0; i < merchants.size(); ++i) spendable[i] = wallets.spendable(merchants.get(i));
        int units = WarehouseKernel.affordableUnits(spendable, price, offered);
        if (units <= 0) return WarehouseMarket.Purchase.NONE;
        int cost = units * price;
        int credited = ledger.distributeFirmRevenue(roster, wallets, firm.room(), cost);
        if (credited <= 0) return WarehouseMarket.Purchase.NONE;
        int[] paidIn = WarehouseKernel.contributions(spendable, credited);
        WarehouseMarket.Book b = book(warehouse, resource);
        for (int i = 0; i < merchants.size(); ++i) {
            if (paidIn[i] <= 0) continue;
            wallets.add(merchants.get(i), -paidIn[i]);
            b.stakes.merge(merchants.get(i).id(), Double.valueOf(paidIn[i]), Double::sum);
        }
        b.capitalBasis += (double)credited;
        b.unitsHeld += units;
        this.lastUnitsBought += units;
        return new WarehouseMarket.Purchase(units, credited);
    }

    private int marketTitledUnits(RESOURCE resource, int wanted, int marketPrice) {
        if (resource == null || wanted <= 0) return 0;
        int index = resource.index();
        long warehouseUnits = 0L;
        for (Map.Entry<StockpileInstance, WarehouseMarket.Book[]> entry : this.sharedState.books.entrySet()) {
            WarehouseMarket.Book book;
            WarehouseMarket.Book[] shelf = entry.getValue();
            if (index >= shelf.length || (book = shelf[index]) == null || book.unitsHeld <= 0 || book.stateOwned && !this.state.sellsAt((RoomInstance)entry.getKey(), resource, marketPrice)) continue;
            warehouseUnits += (long)book.unitsHeld;
        }
        int titled = (int)Math.min((long)wanted, Math.min(Integer.MAX_VALUE, warehouseUnits));
        int remaining = wanted - titled;
        if (remaining <= 0) return titled;
        long producerUnits = 0L;
        ArrayList<WarehouseMarket.DirectClaim> claims = this.sharedState.directClaims.get(index);
        if (claims != null) {
            for (WarehouseMarket.DirectClaim claim : claims) {
                if (claim.producer == null || !claim.producer.exists() || claim.unitsHeld <= 0) continue;
                producerUnits += (long)claim.unitsHeld;
            }
        }
        return titled + (int)Math.min((long)remaining, Math.min(Integer.MAX_VALUE, producerUnits));
    }

    // package-private: accessible by AutoProcurementEngine (T-105) for construction/export procurement
    WarehouseMarket.SaleDistribution distributeSaleDetailed(int[] resourceQuantities, int amount, Roster roster, Wallets wallets, FirmLedger ledger, boolean crownFirst, boolean consumeCrownRemainder) {
        if (amount <= 0 || resourceQuantities == null) {
            return new WarehouseMarket.SaleDistribution(0, 0, 0, 0);
        }
        int[] remaining = (int[])resourceQuantities.clone();
        MerchantDistribution merchants = distributeToMerchants(resourceQuantities, remaining, amount, roster, wallets, ledger);
        int credited = merchants.credited();
        int unclaimed = amount - merchants.claimed();
        if (unclaimed <= 0) return new WarehouseMarket.SaleDistribution(credited, merchants.claimedUnits(), 0, 0);
        int crownClaimed = 0;
        if (crownFirst) {
            long demanded = totalUnits(remaining);
            crownClaimed = consumeCrownTitle(remaining);
            if ((unclaimed -= proportionalAmount(unclaimed, crownClaimed, demanded)) <= 0) {
                return new WarehouseMarket.SaleDistribution(credited, merchants.claimedUnits(), 0, crownClaimed);
            }
        }
        DirectDistribution direct = distributeToDirectClaimants(remaining, unclaimed, roster, wallets, ledger);
        credited += direct.credited();
        if (!crownFirst && consumeCrownRemainder) {
            crownClaimed = consumeCrownTitle(remaining);
        }
        return new WarehouseMarket.SaleDistribution(credited, merchants.claimedUnits(), direct.claimedUnits(), crownClaimed);
    }

    private MerchantDistribution distributeToMerchants(int[] resourceQuantities, int[] remaining, int amount, Roster roster, Wallets wallets, FirmLedger ledger) {
        if (amount <= 0 || resourceQuantities == null) return new MerchantDistribution(0, 0, 0);
        if (!EconConfig.warehouseMarketEnabled) return new MerchantDistribution(0, 0, 0);
        ArrayList<WarehouseHolding> holders = new ArrayList<WarehouseHolding>();
        ArrayList<Integer> sold = new ArrayList<Integer>();
        long backed = 0L;
        long demanded = 0L;
        for (int index = 0; index < resourceQuantities.length; ++index) {
            int wanted = resourceQuantities[index];
            if (wanted <= 0) continue;
            demanded += (long)wanted;
            ArrayList<WarehouseHolding> shelf = new ArrayList<WarehouseHolding>();
            long held = 0L;
            RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(index);
            for (Map.Entry<StockpileInstance, WarehouseMarket.Book[]> entry : this.sharedState.books.entrySet()) {
                WarehouseMarket.Book book;
                WarehouseMarket.Book[] shelves = entry.getValue();
                if (index >= shelves.length || (book = shelves[index]) == null || book.unitsHeld <= 0 || book.stateOwned && !this.state.sellsAt((RoomInstance)entry.getKey(), resource, this.prices.priceRoundedUp(index))) continue;
                shelf.add(new WarehouseHolding(entry.getKey(), book));
                held += (long)book.unitsHeld;
            }
            if (shelf.isEmpty()) continue;
            int claimable = (int)Math.min((long)wanted, held);
            int assigned = 0;
            for (int i = 0; i < shelf.size() && assigned < claimable; ++i) {
                WarehouseHolding holding = (WarehouseHolding)shelf.get(i);
                WarehouseMarket.Book book = holding.book();
                int share = i == shelf.size() - 1 ? claimable - assigned : (int)((long)claimable * (long)book.unitsHeld / held);
                if ((share = Math.min(Math.min(share, book.unitsHeld), claimable - assigned)) <= 0) continue;
                assigned += share;
                holders.add(holding);
                sold.add(share);
            }
            backed += (long)assigned;
            remaining[index] = Math.max(0, remaining[index] - assigned);
        }
        if (backed <= 0L || demanded <= 0L) return new MerchantDistribution(0, 0, 0);
        int claimableAmount = (int)((long)amount * backed / demanded);
        if (claimableAmount <= 0) return new MerchantDistribution(0, 0, 0);
        int credited = 0;
        long assigned = 0L;
        for (int i = 0; i < holders.size(); ++i) {
            long share = i == holders.size() - 1 ? (long)claimableAmount - assigned : (long)claimableAmount * (long)((Integer)sold.get(i)).intValue() / backed;
            assigned += share;
            if (share <= 0L) continue;
            WarehouseHolding holding = (WarehouseHolding)holders.get(i);
            WarehouseMarket.Book book = holding.book();
            boolean privateCapital = !book.stateOwned && !book.stakes.isEmpty();
            double capital = privateCapital ? capitalForUnits(book, (Integer)sold.get(i)) : 0.0;
            int received = settle(book, (Integer)sold.get(i), (int)Math.min(Integer.MAX_VALUE, share), roster, wallets);
            credited += received;
            if (!privateCapital) continue;
            recordMerchantProfit(ledger, holding.warehouse(), realizedMerchantProfit(received, capital));
        }
        this.lastSold += (long)credited;
        return new MerchantDistribution(credited, claimableAmount, (int)backed);
    }

    // package-private: accessible by MarketMaintenanceEngine (T-106) for resolvePending
    void recordDirectClaim(RoomInstance producer, RESOURCE resource, int units) {
        if (producer == null || resource == null || units <= 0) return;
        ArrayList<WarehouseMarket.DirectClaim> claims = this.sharedState.directClaims.computeIfAbsent(resource.index(), ignored -> new ArrayList<WarehouseMarket.DirectClaim>());
        for (WarehouseMarket.DirectClaim claim : claims) {
            if (claim.producer != producer) continue;
            claim.unitsHeld = WarehouseMarket.safeAdd(claim.unitsHeld, units);
            return;
        }
        claims.add(new WarehouseMarket.DirectClaim(producer, units));
    }

    private DirectDistribution distributeToDirectClaimants(int[] remaining, int amount, Roster roster, Wallets wallets, FirmLedger ledger) {
        long demanded = 0L;
        for (int units : remaining) demanded += (long)Math.max(0, units);
        if (demanded <= 0L || amount <= 0) return new DirectDistribution(0, 0);
        ArrayList<DirectSale> sales = new ArrayList<DirectSale>();
        long claimed = 0L;
        for (int resource = 0; resource < remaining.length; ++resource) {
            ArrayList<WarehouseMarket.DirectClaim> claims;
            int wanted = remaining[resource];
            if (wanted <= 0 || (claims = this.sharedState.directClaims.get(resource)) == null) continue;
            Iterator<WarehouseMarket.DirectClaim> iterator = claims.iterator();
            while (iterator.hasNext() && wanted > 0) {
                WarehouseMarket.DirectClaim claim = iterator.next();
                if (claim.producer == null || !claim.producer.exists() || claim.unitsHeld <= 0) {
                    iterator.remove();
                    continue;
                }
                int units = Math.min(wanted, claim.unitsHeld);
                claim.unitsHeld -= units;
                wanted -= units;
                claimed += (long)units;
                sales.add(new DirectSale(claim.producer, units));
                if (claim.unitsHeld > 0) continue;
                iterator.remove();
            }
            remaining[resource] = wanted;
            if (!claims.isEmpty()) continue;
            this.sharedState.directClaims.remove(resource);
        }
        if (claimed <= 0L) return new DirectDistribution(0, 0);
        int claimableAmount = proportionalAmount(amount, claimed, demanded);
        int credited = 0;
        long assigned = 0L;
        for (int i = 0; i < sales.size(); ++i) {
            DirectSale sale = (DirectSale)sales.get(i);
            long share = i == sales.size() - 1 ? (long)claimableAmount - assigned : (long)claimableAmount * (long)sale.units() / claimed;
            assigned += share;
            if (share <= 0L) continue;
            credited += ledger.distributeFirmRevenue(roster, wallets, sale.producer(), (int)Math.min(Integer.MAX_VALUE, share));
        }
        return new DirectDistribution(credited, (int)Math.min(Integer.MAX_VALUE, claimed));
    }

    private int settle(WarehouseMarket.Book book, int unitsSold, int proceeds, Roster roster, Wallets wallets) {
        if (book.stateOwned) {
            book.unitsHeld = Math.max(0, book.unitsHeld - unitsSold);
            this.lastUnitsSold += unitsSold;
            this.state.recordSale(proceeds, unitsSold);
            return 0;
        }
        ArrayList<Integer> holders = new ArrayList<Integer>(book.stakes.keySet());
        if (holders.isEmpty()) {
            book.unitsHeld = Math.max(0, book.unitsHeld - unitsSold);
            book.capitalBasis = 0.0;
            this.lastUnitsSold += unitsSold;
            return 0;
        }
        double[] stakes = new double[holders.size()];
        boolean[] living = new boolean[holders.size()];
        Humanoid[] people = new Humanoid[holders.size()];
        for (int i = 0; i < holders.size(); ++i) {
            stakes[i] = book.stakes.get(holders.get(i));
            people[i] = WarehouseMarket.alive(roster, holders.get(i));
            living[i] = people[i] != null;
        }
        int[] payouts = WarehouseKernel.payouts(stakes, living, proceeds);
        int credited = 0;
        for (int i = 0; i < payouts.length; ++i) {
            if (payouts[i] <= 0 || people[i] == null) continue;
            wallets.add(people[i], payouts[i]);
            credited += payouts[i];
        }
        int held = book.unitsHeld;
        double capitalHeld = book.capitalBasis;
        for (int i = 0; i < holders.size(); ++i) {
            double left = WarehouseKernel.decayStakes(stakes[i], unitsSold, held);
            if (left <= 0.0 || !living[i]) {
                book.stakes.remove(holders.get(i));
                continue;
            }
            book.stakes.put(holders.get(i), left);
        }
        book.unitsHeld = Math.max(0, held - unitsSold);
        book.capitalBasis = WarehouseKernel.decayStakes(capitalHeld, unitsSold, held);
        this.lastUnitsSold += unitsSold;
        return credited;
    }

    private ArrayList<StockpileInstance> dealers(RESOURCE resource, boolean stateOwned, Roster roster) {
        ArrayList<StockpileInstance> result = new ArrayList<StockpileInstance>();
        for (int i = 0; i < EconProgression.reliableStockpileCount(); ++i) {
            StockpileInstance warehouse = (StockpileInstance)SETT.ROOMS().STOCKPILE.getInstance(i);
            if (warehouse == null || !warehouse.exists() || warehouse.employees() == null || !warehouse.crateMask.has(resource) || this.state.isStateOwned((RoomInstance)warehouse) != stateOwned || !stateOwned && WarehouseMarket.staff(roster, (RoomInstance)warehouse).isEmpty()) continue;
            result.add(warehouse);
        }
        return result;
    }

    private WarehouseMarket.Book book(StockpileInstance warehouse, RESOURCE resource) {
        WarehouseMarket.Book[] shelf = this.sharedState.books.computeIfAbsent(warehouse, ignored -> new WarehouseMarket.Book[RESOURCES.ALL().size()]);
        if (shelf[resource.index()] == null) {
            shelf[resource.index()] = new WarehouseMarket.Book();
        }
        return shelf[resource.index()];
    }

    // ── Static helpers (T-102 exclusive) ────────────────────────

    static int crownPricedUnits(int units, long crownAvailable, int marketTitleAvailable) {
        int total = Math.max(0, units);
        int trackedCrown = (int)Math.min((long)total, Math.max(0L, crownAvailable));
        int market = Math.min(total - trackedCrown, Math.max(0, marketTitleAvailable));
        return total - market;
    }

    static long inputGross(int units, int marketUnits, int marketPrice, int crownPrice) {
        int total = Math.max(0, units);
        int market = Math.min(total, Math.max(0, marketUnits));
        int crown = total - market;
        long gross = (long)market * (long)Math.max(0, marketPrice) + (long)crown * (long)Math.max(0, crownPrice);
        return gross < 0L ? Long.MAX_VALUE : gross;
    }

    static int charge(RoomInstance room, int amount, Roster roster, Wallets wallets) {
        if (room == null || amount <= 0) return 0;
        ArrayList<Humanoid> workers = WarehouseMarket.staff(roster, room);
        if (workers.isEmpty()) return 0;
        int[] shares = FirmEconomyKernel.split(amount, workers.size());
        int collected = 0;
        for (int i = 0; i < shares.length; ++i) {
            int due = Math.min(shares[i], wallets.spendable(workers.get(i)));
            if (due <= 0) continue;
            wallets.add(workers.get(i), -due);
            collected += due;
        }
        return collected;
    }

    static double capitalForUnits(WarehouseMarket.Book book, int unitsSold) {
        if (book == null || book.unitsHeld <= 0 || unitsSold <= 0) return 0.0;
        double capital = Double.isFinite(book.capitalBasis) ? Math.max(0.0, book.capitalBasis) : 0.0;
        return capital * (double)Math.min(book.unitsHeld, unitsSold) / (double)book.unitsHeld;
    }

    static double realizedMerchantProfit(double proceeds, double soldCapital) {
        if (!Double.isFinite(proceeds) || !Double.isFinite(soldCapital)) return 0.0;
        return proceeds - Math.max(0.0, soldCapital);
    }

    static void recordMerchantProfit(FirmLedger ledger, StockpileInstance warehouse, double profit) {
        if (ledger == null || warehouse == null || !Double.isFinite(profit) || profit == 0.0) return;
        if (profit > 0.0) {
            ledger.recordFirmRevenue((RoomInstance)warehouse, profit);
        } else {
            ledger.recordFirmCost((RoomInstance)warehouse, -profit);
        }
    }

    static int freeSpace(RESOURCE resource, StockpileInstance warehouse) {
        StockpileTally tally = SETT.ROOMS().STOCKPILE.tally();
        return Math.max(0, tally.space.get(resource, warehouse) - tally.amount.get(resource, warehouse));
    }

    static int proportionalAmount(int amount, long claimedUnits, long demandedUnits) {
        if (amount <= 0 || claimedUnits <= 0L || demandedUnits <= 0L) return 0;
        return (int)Math.min(Integer.MAX_VALUE, (long)amount * Math.min(claimedUnits, demandedUnits) / demandedUnits);
    }

    static long totalUnits(int[] quantities) {
        long total = 0L;
        for (int quantity : quantities) total += (long)Math.max(0, quantity);
        return total;
    }

    // ── T-102 exclusive Records ─────────────────────────────────

    record MerchantDistribution(int credited, int claimed, int claimedUnits) {}

    record DirectDistribution(int credited, int claimedUnits) {}

    record WarehouseHolding(StockpileInstance warehouse, WarehouseMarket.Book book) {}

    record DirectSale(RoomInstance producer, int units) {}
}
