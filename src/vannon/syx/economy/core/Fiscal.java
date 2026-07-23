package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.time.TIME;
import init.type.HTYPES;
import java.io.IOException;
import settlement.entity.humanoid.Humanoid;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomInstance;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import vannon.syx.economy.core.AffordabilityGate;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EngineSeams;
import vannon.syx.economy.core.FirmLedger;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;
import vannon.syx.economy.core.WarehouseMarket;

public final class Fiscal implements Saveable {
    private int lastSeason = -1;
    private long headTaxCollected;
    private long marketReceipts;
    private long rationOut;
    private long producerIncome;
    private long creditsTax;
    private long creditsTrade;
    private long creditsMisc;

    public long headTaxCollected() {
        return this.headTaxCollected;
    }

    public long marketReceipts() {
        return this.marketReceipts;
    }

    public long rationOut() {
        return this.rationOut;
    }

    public long producerIncome() {
        return this.producerIncome;
    }

    public long creditsTax() {
        return this.creditsTax;
    }

    public long creditsTrade() {
        return this.creditsTrade;
    }

    public long creditsMisc() {
        return this.creditsMisc;
    }

    public long update(Roster roster, Wallets wallets) {
        if (EconConfig.perHeadTax <= 0) {
            return 0L;
        }
        int season = TIME.seasons().bitsSinceStart();
        if (this.lastSeason == -1) {
            this.lastSeason = season;
            return 0L;
        }
        if (season == this.lastSeason) {
            return 0L;
        }
        this.lastSeason = season;
        long collected = 0L;
        for (int i = 0; i < roster.size(); ++i) {
            int shortfall;
            Humanoid h = roster.get(i);
            if (h.indu().hType() == HTYPES.CHILD() || h.indu().hType() == HTYPES.CHILD_SLAVE()) continue;
            // Poverty exemption: citizens below the tax exemption threshold are not
            // charged, so the per-head tax cannot push the poorest into debt bondage.
            if (wallets.netWorth(h) < EconConfig.perHeadTaxExemptionThreshold) {
                continue;
            }
            int due = Math.min(EconConfig.perHeadTax, wallets.spendable(h));
            if (due > 0) {
                wallets.add(h, -due);
                wallets.accrueTax(h, due);
                collected += (long)due;
            }
            if ((shortfall = EconConfig.perHeadTax - due) <= 0 || !EngineSeams.isEnslaveablePleb(h)) continue;
            wallets.addDebt(h, shortfall);
        }
        if (collected > 0L) {
            FACTIONS.player().credits().inc((double)collected, FCredits.CTYPE.TAX);
            this.creditsTax += collected;
        }
        this.headTaxCollected += collected;
        return collected;
    }

    public void settlePurchase(Humanoid buyer, int[] resources, int gross, AffordabilityGate.Kind kind, RoomInstance seller, Roster roster, Wallets wallets, FirmLedger ledger, WarehouseMarket warehouses) {
        if (gross <= 0) {
            return;
        }
        Split split = Fiscal.split(gross, EconConfig.marketTaxRate);
        if (buyer != null) {
            wallets.accrueTax(buyer, split.tax());
        }
        WarehouseMarket.RetailQuote quote = seller == null ? new WarehouseMarket.RetailQuote(split.net(), new int[0]) : warehouses.retailWholesaleQuote(seller, resources);
        WarehouseMarket.OwnerlessRetailClaims claims = seller == null ? new WarehouseMarket.OwnerlessRetailClaims(0, resources) : warehouses.waiveOwnerlessRetailClaims(resources, quote.byResource());
        RetailSettlement settlement = Fiscal.retailSettlement(split.net(), Math.max(0, quote.total() - claims.waivedValue()));
        int upstreamCredited = warehouses.distributeSale(claims.payableQuantities(), settlement.warehouse(), roster, wallets, ledger);
        int retailCredited = ledger.distributeFirmRevenue(roster, wallets, seller, settlement.retailer());
        int credited = retailCredited + upstreamCredited;
        this.producerIncome += (long)credited;
        int treasury = gross - credited;
        if (treasury > 0) {
            FACTIONS.player().credits().inc((double)treasury, FCredits.CTYPE.TRADE);
            this.creditsTrade += (long)treasury;
        }
        this.marketReceipts += (long)treasury;
    }

    public void settleRation(Humanoid diner, int[] resources, int marketValue, RoomInstance seller, Roster roster, Wallets wallets, FirmLedger ledger, WarehouseMarket warehouses) {
        int budget = Math.max(0, (int)Math.min(2.147483647E9, Math.floor(FACTIONS.player().credits().credits())));
        int paid = Math.min(Math.max(0, marketValue), budget);
        if (paid <= 0) {
            return;
        }
        WarehouseMarket.RetailQuote quote = seller == null ? new WarehouseMarket.RetailQuote(paid, new int[0]) : warehouses.retailWholesaleQuote(seller, resources);
        WarehouseMarket.OwnerlessRetailClaims claims = seller == null ? new WarehouseMarket.OwnerlessRetailClaims(0, resources) : warehouses.waiveOwnerlessRetailClaims(resources, quote.byResource());
        RetailSettlement settlement = Fiscal.retailSettlement(paid, Math.max(0, quote.total() - claims.waivedValue()));
        int credited = warehouses.distributeSale(claims.payableQuantities(), settlement.warehouse(), roster, wallets, ledger);
        if ((credited += ledger.distributeFirmRevenue(roster, wallets, seller, settlement.retailer())) <= 0) {
            return;
        }
        FACTIONS.player().credits().inc((double)(-credited), FCredits.CTYPE.MISC);
        this.creditsMisc -= (long)credited;
        this.rationOut += (long)credited;
        this.producerIncome += (long)credited;
    }

    public void settleService(Humanoid diner, int gross, RoomBlueprintImp blueprint, Roster roster, Wallets wallets, FirmLedger ledger) {
        if (gross <= 0) {
            return;
        }
        Split split = Fiscal.split(gross, EconConfig.marketTaxRate);
        if (diner != null) {
            wallets.accrueTax(diner, split.tax());
        }
        int credited = ledger.distributeServiceRevenue(roster, wallets, blueprint, split.net());
        this.producerIncome += (long)credited;
        int treasury = gross - credited;
        if (treasury > 0) {
            FACTIONS.player().credits().inc((double)treasury, FCredits.CTYPE.TRADE);
            this.creditsTrade += (long)treasury;
        }
        this.marketReceipts += (long)treasury;
    }

    public void settleMerchantRemainder(int amount) {
        if (amount <= 0) {
            return;
        }
        FACTIONS.player().credits().inc((double)amount, FCredits.CTYPE.TRADE);
        this.creditsTrade += (long)amount;
        this.marketReceipts += (long)amount;
    }

    public void settleCrownWholesale(long amount) {
        if (amount <= 0L) {
            return;
        }
        FACTIONS.player().credits().inc((double)amount, FCredits.CTYPE.TRADE);
        this.creditsTrade += amount;
        this.marketReceipts += amount;
    }

    static Split split(int gross, double rate) {
        int g = Math.max(0, gross);
        double r = Math.max(0.0, Math.min(1.0, rate));
        int tax = (int)Math.floor((double)g * r);
        return new Split(tax, g - tax);
    }

    static RetailSettlement retailSettlement(int net, int recordedWholesale) {
        int proceeds = Math.max(0, net);
        int warehouse = Math.min(proceeds, Math.max(0, recordedWholesale));
        return new RetailSettlement(warehouse, proceeds - warehouse);
    }

    public void save(FilePutter file) {
        file.i(this.lastSeason);
        file.l(this.headTaxCollected);
        file.l(this.marketReceipts);
        file.l(this.rationOut);
        file.l(this.producerIncome);
    }

    public void load(FileGetter file) throws IOException {
        this.lastSeason = file.i();
        this.headTaxCollected = file.l();
        this.marketReceipts = file.l();
        this.rationOut = file.l();
        this.producerIncome = file.l();
    }

    public void clear() {
        this.lastSeason = -1;
        this.producerIncome = 0L;
        this.rationOut = 0L;
        this.marketReceipts = 0L;
        this.headTaxCollected = 0L;
    }

    public record Split(int tax, int net) {
    }

    public record RetailSettlement(int warehouse, int retailer) {
    }

    // Thin wrappers for Advisor-Tab slider controls
    public int headTax() { return EconConfig.perHeadTax; }
    public void setHeadTax(int v) { EconConfig.perHeadTax = Math.max(0, v); }
    public double marketLevy() { return EconConfig.marketTaxRate; }
    public void setMarketLevy(double v) { EconConfig.marketTaxRate = Math.max(0.0, v); }
}

