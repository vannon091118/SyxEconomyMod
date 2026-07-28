package vannon.syx.economy.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import game.faction.FACTIONS;
import game.faction.FCredits;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.home.HOME;
import settlement.room.main.Room;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomInstance;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.sets.LIST;

/**
 * Externes Eigentums-Register für Räume (Phase 2: aktiviert).
 *
 * Jeder Raum-Eintrag hat einen Eigentümer (ownerId == STATE = Staat, sonst
 * Bürger-Induvidual-ID) und einen Anteil (shares 0..100). Bürger können
 * Häuser komplett kaufen (shares=100) oder Firmen-Anteile erwerben (1..100).
 *
 * Progressive Monopol-Bremse: Je mehr Firmen ein Bürger bereits besitzt,
 * desto weniger Anteile darf er pro neuer Firma kaufen.
 *
 * Dividenden: Firmen-Gewinne werden anteilig an private Anteilseigner
 * ausgeschüttet (per Saison).
 */
public final class PropertyLedger {
    public static final long STATE = -1L;
    private static final int SAVE_VERSION = 2;

    private final Map<Long, Entry> entries = new HashMap<>();

    // —— key helpers ————————————————————————————————————————————

    private static long key(int tx, int ty, String blueprintKey) {
        int h = blueprintKey != null ? blueprintKey.hashCode() : 0;
        return ((long) (tx & 0xFFFFFF) << 40L) | ((long) (ty & 0xFFFFFF) << 16L) | ((long) (h & 0xFFFFL));
    }

    /** Legacy key (v1): tile coordinates only — blueprintKey ignored. */
    private static long legacyKey(int tx, int ty) {
        return ((long) tx << 32L) | ((long) ty & 0xFFFFFFFFL);
    }

    // —— accessors ——————————————————————————————————————————————

    public Entry get(int tx, int ty, String blueprintKey) {
        long k = key(tx, ty, blueprintKey);
        Entry e = entries.get(k);
        if (e == null) {
            e = new Entry(STATE, 0, 0L, 0L, -1, blueprintKey, tx, ty);
            entries.put(k, e);
        }
        return e;
    }

    public Entry get(HOME home, String blueprintKey) {
        return get(home.serviceX(), home.serviceY(), blueprintKey);
    }

    public Entry get(RoomInstance room) {
        String key = room.blueprintI().key;
        return get(room.mX(), room.mY(), key);
    }

    /** All entries belonging to a citizen. */
    public List<Entry> ownedBy(long citizenId) {
        List<Entry> owned = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (e.ownerId == citizenId && e.shares > 0) {
                owned.add(e);
            }
        }
        return owned;
    }

    /** Number of distinct firms (non-home entries) this citizen owns shares in. */
    public int shareCount(long citizenId) {
        int count = 0;
        for (Entry e : entries.values()) {
            if (e.ownerId == citizenId && e.shares > 0 && !isHomeKey(e.blueprintKey)) {
                count++;
            }
        }
        return count;
    }

    /** Does this citizen own at least one home outright? */
    public boolean isHomeOwner(long citizenId) {
        for (Entry e : entries.values()) {
            if (e.ownerId == citizenId && e.shares >= 100 && isHomeKey(e.blueprintKey)) {
                return true;
            }
        }
        return false;
    }

    // —— pricing —————————————————————————————————————————————————

    /** Purchase price for a home. Based on annual rent × multiplier. */
    public long priceForHome(HOME home) {
        if (home == null || !EconConfig.homePurchaseEnabled) return Long.MAX_VALUE;
        double isolation = Math.max(0.0, Math.min(1.0, home.isolation()));
        int area = Math.max(0, home.area());
        double upgrade = 0.0;
        if (home instanceof settlement.room.home.house.HomeInstance) {
            upgrade = ((settlement.room.home.house.HomeInstance) home).upgrade();
        }
        double quality = 1.0 + isolation + upgrade * 0.25;
        long annualRent = (long) EconConfig.housingBaseRentPerTile * (long) area * 4L; // 4 seasons/year
        annualRent = (long) ((double) annualRent * quality);
        long price = (long) ((double) annualRent * EconConfig.homePriceMultiplier);
        return Math.max(1L, Math.min((long) Integer.MAX_VALUE, price));
    }

    /** Purchase price for X% of a firm. Based on annual profit × multiplier. */
    public long priceForFirm(RoomInstance room, FirmLedger firmLedger, int percent) {
        if (room == null || !EconConfig.workplaceSharesEnabled) return Long.MAX_VALUE;
        double dailyProfit = firmLedger.profitPerDay(room.blueprintI());
        if (dailyProfit <= 0.0) {
            // Unprofitable firm: nominal price based on wage bill
            dailyProfit = firmLedger.marginalSurplus(room.blueprintI());
            if (dailyProfit <= 0.0) dailyProfit = 1.0;
        }
        long annualProfit = (long) (dailyProfit * 16.0 * 4.0); // 16 days/season, 4 seasons
        long fullPrice = (long) ((double) annualProfit * EconConfig.firmPriceMultiplier);
        long sharePrice = (fullPrice * (long) percent) / 100L;
        return Math.max(1L, Math.min((long) Integer.MAX_VALUE, sharePrice));
    }

    // —— progressive limit ———————————————————————————————————————

    /**
     * Maximum shares a citizen can own in a new firm, given how many firms
     * they already own. Formula: max(10, 50 - shareCount × 5).
     */
    public int maxSharesForCitizen(long citizenId) {
        int owned = shareCount(citizenId);
        int max = Math.max(EconConfig.minSharesPerFirm, EconConfig.maxSharesPerFirm - owned * EconConfig.progressiveShareStep);
        return Math.max(EconConfig.minSharesPerFirm, max);
    }

    // —— purchase ————————————————————————————————————————————————

    /**
     * Attempt to buy a home outright (shares=100). Deducts price from wallet.
     * @return price paid (always > 0 on success), 0 if purchase failed.
     */
    public long buyHome(Humanoid citizen, HOME home, Wallets wallets) {
        if (citizen == null || home == null || !EconConfig.homePurchaseEnabled) return 0L;
        Entry e = get(home, isHomeKey("") ? "HOME" : "CHAMBER");
        // Determine actual key
        String bpKey;
        if (home instanceof settlement.room.home.house.HomeInstance) bpKey = "HOME";
        else bpKey = "CHAMBER";
        e = get(home, bpKey);
        if (!e.isStateOwned()) return 0L; // already privately owned

        long price = priceForHome(home);
        int wallet = wallets.spendable(citizen);
        // Citizen-class-aware cushion: base 1.5×, scaled by homeBuyMultiplier
        double classMult = EconConfig.citizenClassesEnabled ? wallets.classOf(citizen).homeBuyMultiplier : 1.0;
        long required = price + (long)((double)price / 2.0 * classMult);
        if ((long) wallet < required) return 0L;

        int deducted = wallets.chargeAffordable(citizen, (int) Math.min((long) Integer.MAX_VALUE, price));
        if (deducted < (int) price) return 0L; // couldn't afford

        e.ownerId = (long) citizen.id();
        e.shares = 100;
        e.purchasePrice = price;
        if (price > 0L) {
            FACTIONS.player().credits().inc((double) price, FCredits.CTYPE.MISC);
        }
        EventLog.log("PROPERTY", citizen.id() + " bought " + bpKey + " at (" + e.tx + "," + e.ty + ") for " + price);
        return price;
    }

    /**
     * Attempt to buy shares in a firm.
     * @return price paid (always > 0 on success), 0 if purchase failed.
     */
    public long buyShares(Humanoid citizen, RoomInstance room, int percent, Wallets wallets, FirmLedger firmLedger) {
        if (citizen == null || room == null || percent <= 0 || percent > 100 || !EconConfig.workplaceSharesEnabled) return 0L;

        int maxAllowed = maxSharesForCitizen(citizen.id());
        if (percent > maxAllowed) percent = maxAllowed;
        if (percent <= 0) return 0L;

        Entry e = get(room);
        int available = 100 - e.shares;
        if (available <= 0) return 0L;
        if (percent > available) percent = available;

        long price = priceForFirm(room, firmLedger, percent);
        int wallet = wallets.spendable(citizen);
        if ((long) wallet < price + (price / 4L)) return 0L; // need 25% cushion

        int deducted = wallets.chargeAffordable(citizen, (int) Math.min((long) Integer.MAX_VALUE, price));
        if (deducted < (int) price) return 0L;

        e.ownerId = (long) citizen.id();
        e.shares += percent;
        e.purchasePrice += price;
        if (price > 0L) {
            FACTIONS.player().credits().inc((double) price, FCredits.CTYPE.MISC);
        }
        EventLog.log("PROPERTY", citizen.id() + " bought " + percent + "% of " + room.blueprintI().key + " for " + price);
        return price;
    }

    // —— dividends ———————————————————————————————————————————————

    /**
     * Accrue this season's dividends into each entry's dividendPool.
     * Uses per-firm marginalSurplus so shareholders in profitable firms
     * earn more than shareholders in struggling ones.
     */
    public void accrueDividends(FirmLedger firmLedger, LIST<RoomBlueprintImp> firms) {
        if (!EconConfig.workplaceSharesEnabled) return;
        // Build key→RoomBlueprintImp lookup for fast per-entry profit queries
        HashMap<String, RoomBlueprintImp> byKey = new HashMap<>();
        for (int i = 0; i < firms.size(); ++i) {
            RoomBlueprintImp bp = firms.get(i);
            byKey.put(bp.key, bp);
        }
        for (Entry e : entries.values()) {
            if (e.shares <= 0 || e.ownerId == STATE || isHomeKey(e.blueprintKey)) continue;
            RoomBlueprintImp bp = byKey.get(e.blueprintKey);
            if (bp == null) continue; // firm no longer exists
            double dailyProfit = firmLedger.marginalSurplus(bp);
            if (dailyProfit <= 0.0) continue;
            long seasonProfit = (long) (dailyProfit * 16.0 * (double) e.shares / 100.0);
            long dividend = (long) ((double) seasonProfit * EconConfig.dividendRate);
            if (dividend > 0L) {
                e.dividendPool += dividend;
            }
        }
    }

    /**
     * Pay out all accumulated dividends to citizen wallets.
     * Called once per season.
     */
    public long payDividends(Wallets wallets, Roster roster, int season) {
        if (!EconConfig.workplaceSharesEnabled) return 0L;
        long totalPaid = 0L;
        // Build citizen lookup map for O(1) access instead of O(n) linear scan
        java.util.HashMap<Integer, Humanoid> citizenById = new java.util.HashMap<>();
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid h = roster.get(i);
            citizenById.put(h.id(), h);
        }
        for (Entry e : entries.values()) {
            if (e.dividendPool <= 0L || e.ownerId == STATE) continue;
            int paid = (int) Math.min((long) Integer.MAX_VALUE, e.dividendPool);
            Humanoid h = citizenById.get((int) e.ownerId);
            if (h != null) {
                wallets.add(h, paid);
                totalPaid += (long) paid;
            }
            e.dividendPool = 0L;
            e.lastDividendSeason = season;
        }
        if (totalPaid > 0L) {
            EventLog.logSampled("PROPERTY", "paid " + totalPaid + " in dividends to shareholders");
        }
        return totalPaid;
    }

    // —— helpers ————————————————————————————————————————————————

    private static boolean isHomeKey(String key) {
        return "HOME".equals(key) || "CHAMBER".equals(key);
    }

    // —— death / inheritance —————————————————————————————————

    /** Reclaim all property from owners not in the alive set (back to STATE). */
    public void reclaimDeadOwners(java.util.Set<Integer> aliveIds) {
        for (Entry e : entries.values()) {
            if (e.ownerId == STATE) continue;
            if (!aliveIds.contains((int) e.ownerId)) {
                e.ownerId = STATE;
                e.shares = 0;
                e.purchasePrice = 0L;
                e.dividendPool = 0L;
            }
        }
    }

    // —— cleanup —————————————————————————————————————————————————

    /**
     * Remove entries for rooms that no longer exist (demolished buildings).
     * Called once per season from EconomySim, following same pattern as
     * WarehouseMarket.books.keySet().removeIf() and HousingMarket.
     * Uses SETT.ROOMS().map.get(tx, ty) — the proven room lookup API
     * used by FoodTransactionPlan, ServicePlanController, etc.
     */
    public void cleanupGoneRooms() {
        entries.values().removeIf(e -> {
            Room room = SETT.ROOMS().map.get(e.tx, e.ty);
            if (!(room instanceof RoomInstance)) {
                return true; // no valid room at these coordinates → remove
            }
            RoomInstance ri = (RoomInstance) room;
            if (!ri.exists()) {
                return true; // room demolished → remove
            }
            // Room exists — check if blueprint key matches
            return !e.blueprintKey.equals(ri.blueprintI().key);
        });
    }

    // —— lifecycle ——————————————————————————————————————————————

    public void clear() {
        entries.clear();
    }

    // —— save / load (versioned) ————————————————————————————————

    public void save(FilePutter file) {
        file.i(SAVE_VERSION);
        file.i(entries.size());
        for (Entry e : entries.values()) {
            file.i(e.tx);
            file.i(e.ty);
            file.chars((CharSequence) e.blueprintKey);
            file.l(e.ownerId);
            file.i(e.shares);
            file.l(e.purchasePrice);
            file.l(e.dividendPool);
            file.i(e.lastDividendSeason);
        }
    }

    public void load(FileGetter file) throws IOException {
        entries.clear();
        int version = file.i();
        int count = file.i();
        for (int i = 0; i < count; ++i) {
            int tx = file.i();
            int ty = file.i();
            String key = file.chars();
            long ownerId = file.l();
            int shares = file.i();
            long purchasePrice;
            long dividendPool;
            int lastDividendSeason;
            if (version >= 1) {
                purchasePrice = file.l();
                dividendPool = file.l();
                lastDividendSeason = file.i();
            } else {
                purchasePrice = 0L;
                dividendPool = 0L;
                lastDividendSeason = -1;
            }
            long k = (version < 2) ? legacyKey(tx, ty) : key(tx, ty, key);
            entries.put(k, new Entry(ownerId, shares, purchasePrice, dividendPool, lastDividendSeason, key, tx, ty));
        }
    }

    // —— entry ——————————————————————————————————————————————————

    public static final class Entry {
        long ownerId;
        int shares;
        long purchasePrice;
        long dividendPool;
        int lastDividendSeason;
        final String blueprintKey;
        final int tx;
        final int ty;

        Entry(long ownerId, int shares, long purchasePrice, long dividendPool,
              int lastDividendSeason, String blueprintKey, int tx, int ty) {
            this.ownerId = ownerId;
            this.shares = shares;
            this.purchasePrice = purchasePrice;
            this.dividendPool = dividendPool;
            this.lastDividendSeason = lastDividendSeason;
            this.blueprintKey = blueprintKey;
            this.tx = tx;
            this.ty = ty;
        }

        public long ownerId()          { return ownerId; }
        public int shares()            { return shares; }
        public long purchasePrice()    { return purchasePrice; }
        public long dividendPool()     { return dividendPool; }
        public int lastDividendSeason(){ return lastDividendSeason; }
        public String blueprintKey()   { return blueprintKey; }
        public int tx()                { return tx; }
        public int ty()                { return ty; }
        public boolean isStateOwned()  { return ownerId == STATE; }
        public boolean isHome()        { return isHomeKey(blueprintKey); }
    }
}
