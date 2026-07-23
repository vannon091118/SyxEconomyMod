package vannon.syx.economy.core;

import init.type.CAUSE_ARRIVES;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.Induvidual;
import settlement.stats.STATS;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.rnd.RND;
import vannon.syx.economy.core.CitizenClass;
import vannon.syx.economy.core.EconConfig;

public final class Wallets {
    private static final int ORDER_MASK = 262143;
    private static final int SLOTS = 60000;
    private static final int NOBODY = -1;
    private final int[] money = new int[60000];
    private final int[] owner = new int[60000];
    private final int[] reserved = new int[60000];
    private final int[] taxDebt = new int[60000];
    private final int[] rentDebt = new int[60000];
    private final int[] lambdaBp = new int[60000];
    private final int[] lastTaxRateBp = new int[60000];
    private final int[] taxAccrued = new int[60000];
    private final int[] relRef = new int[60000];
    private final byte[] emigrating = new byte[60000];
    private final int[] seenTick = new int[60000];
    private final byte[] citizenClass = new byte[60000];
    private int[] ownedSlots = new int[1024];
    private int ownedCount = 0;
    private final ArrayList<PendingDeparture> pendingDepartures = new ArrayList<>();
    private final HashMap<Induvidual, Integer> induSlot = new HashMap<>();
    private final Induvidual[] induOf = new Induvidual[60000];
    private final Set<Induvidual> paidThisTick = new HashSet<>();
    private boolean seeded = false;
    public static final int FORMAT = 33;
    private static final int OLDEST_COMPATIBLE_FORMAT = 19;
    // EconomySim global save version: 32 (introduces chunked layout for EconomySim)
    // Keep this in sync with EconomySim.CHUNKED_VERSION.
    // Wallets bumped to 33: added citizenClass[] for Phase 3 citizen diversification.

    public Wallets() {
        Arrays.fill(this.owner, -1);
    }

    private static int slotOf(Humanoid h) {
        return h.id() & 0x3FFFF;
    }

    private int liveSlot(Humanoid h) {
        if (h == null) {
            return -1;
        }
        int id = h.id();
        int slot = id & 0x3FFFF;
        if (slot < 0 || slot >= 60000) {
            return -1;
        }
        return this.owner[slot] == id ? slot : -1;
    }

    private int startingMoney(Humanoid h) {
        if (!this.seeded) {
            // Stage-gated: SUBSISTENZ→200 statt 5000 — verhindert 1M Seed-Geld ohne Produktion
            return EconConfig.effectiveInitialWallet();
        }
        boolean born = STATS.POP().COUNT.arrive.get(h.indu()) == CAUSE_ARRIVES.BORN();
        return born ? EconConfig.newbornWallet : EconConfig.effectiveImmigrantWallet();
    }

    private static int rollLambdaBp() {
        if (!EconConfig.heterogeneousLambda) {
            return (int)(EconConfig.lambdaMax * 10000.0);
        }
        double span = EconConfig.lambdaMax - EconConfig.lambdaMin;
        double v = EconConfig.lambdaMin + (double)RND.rFloat() * span;
        int bp = (int)(v * 10000.0);
        if (bp < 0) {
            bp = 0;
        }
        if (bp > 9999) {
            bp = 9999;
        }
        return bp;
    }

    public double lambda(Humanoid h) {
        int slot = this.liveSlot(h);
        return slot < 0 ? 0.0 : (double)this.lambdaBp[slot] / 10000.0;
    }

    public int moneyOf(Induvidual indu) {
        Integer slot = this.induSlot.get(indu);
        return slot == null ? -1 : this.money[slot];
    }

    private void own(int slot) {
        if (this.ownedCount == this.ownedSlots.length) {
            this.ownedSlots = Arrays.copyOf(this.ownedSlots, this.ownedSlots.length * 2);
        }
        this.ownedSlots[this.ownedCount++] = slot;
    }

    public int touch(Humanoid h, int tick) {
        int slot = Wallets.slotOf(h);
        int id = h.id();
        int minted = 0;
        if (this.owner[slot] != id) {
            boolean slotWasOwned;
            boolean bl = slotWasOwned = this.owner[slot] != -1;
            if (slotWasOwned) {
                this.pendingDepartures.add(new PendingDeparture(this.money[slot], this.relRef[slot], this.emigrating[slot] != 0));
                if (this.induOf[slot] != null) {
                    this.induSlot.remove(this.induOf[slot]);
                    this.induOf[slot] = null;
                }
            }
            this.owner[slot] = id;
            this.money[slot] = this.startingMoney(h);
            this.reserved[slot] = 0;
            this.taxDebt[slot] = 0;
            this.rentDebt[slot] = 0;
            this.lambdaBp[slot] = Wallets.rollLambdaBp();
            this.lastTaxRateBp[slot] = 0;
            this.taxAccrued[slot] = 0;
            this.citizenClass[slot] = 0; // will be reclassified
            minted = this.money[slot];
            if (!slotWasOwned) {
                this.own(slot);
            }
        }
        if (this.induOf[slot] != h.indu()) {
            if (this.induOf[slot] != null) {
                this.induSlot.remove(this.induOf[slot]);
            }
            this.induOf[slot] = h.indu();
            this.induSlot.put(h.indu(), slot);
        }
        this.seenTick[slot] = tick;
        this.relRef[slot] = STATS.REL().reference(h.indu());
        this.emigrating[slot] = (byte)(STATS.POP().EMMIGRATING.indu().get(h.indu()) != 0 ? 1 : 0);
        return minted;
    }

    public int get(Humanoid h) {
        int slot = this.liveSlot(h);
        return slot < 0 ? 0 : this.money[slot];
    }

    public int reserved(Humanoid h) {
        int slot = this.liveSlot(h);
        return slot < 0 ? 0 : this.reserved[slot];
    }

    public int spendable(Humanoid h) {
        int slot = this.liveSlot(h);
        return slot < 0 ? 0 : Math.max(0, this.money[slot] - this.reserved[slot]);
    }

    boolean reserve(Humanoid h, int amount) {
        if (amount < 0) {
            return false;
        }
        int slot = this.liveSlot(h);
        if (slot < 0) {
            return false;
        }
        if (this.money[slot] - this.reserved[slot] < amount) {
            return false;
        }
        int n = slot;
        this.reserved[n] = this.reserved[n] + amount;
        return true;
    }

    void release(Humanoid h, int amount) {
        if (amount <= 0) {
            return;
        }
        int slot = this.liveSlot(h);
        if (slot < 0) {
            return;
        }
        this.reserved[slot] = Math.max(0, this.reserved[slot] - amount);
    }

    boolean settleReserved(Humanoid h, int quote, int bill) {
        if (quote < 0 || bill < 0 || bill > quote) {
            return false;
        }
        int slot = this.liveSlot(h);
        if (slot < 0) {
            return false;
        }
        if (this.reserved[slot] < quote || this.money[slot] < bill) {
            return false;
        }
        int n = slot;
        this.reserved[n] = this.reserved[n] - quote;
        int n2 = slot;
        this.money[n2] = this.money[n2] - bill;
        return true;
    }

    public int debt(Humanoid h) {
        int slot = this.liveSlot(h);
        return slot < 0 ? 0 : this.taxDebt[slot];
    }

    public void addDebt(Humanoid h, int amount) {
        if (amount <= 0) {
            return;
        }
        int slot = this.liveSlot(h);
        if (slot < 0) {
            return;
        }
        long sum = (long)this.taxDebt[slot] + (long)amount;
        this.taxDebt[slot] = sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)sum;
    }

    public void clearDebt(Humanoid h) {
        int slot = this.liveSlot(h);
        if (slot >= 0) {
            this.taxDebt[slot] = 0;
        }
    }

    public int rentDebt(Humanoid h) {
        int slot = this.liveSlot(h);
        return slot < 0 ? 0 : this.rentDebt[slot];
    }

    public void addRentDebt(Humanoid h, int amount) {
        if (amount <= 0) {
            return;
        }
        int slot = this.liveSlot(h);
        if (slot < 0) {
            return;
        }
        long sum = (long)this.rentDebt[slot] + (long)amount;
        this.rentDebt[slot] = sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)sum;
    }

    public void clearRentDebt(Humanoid h) {
        int slot = this.liveSlot(h);
        if (slot >= 0) {
            this.rentDebt[slot] = 0;
        }
    }

    public void add(Humanoid h, int amount) {
        int slot = this.liveSlot(h);
        if (slot < 0) {
            return;
        }
        int n = slot;
        this.money[n] = this.money[n] + amount;
        if (this.money[slot] < 0) {
            this.money[slot] = 0;
        }
    }

    // Per-tick set used to avoid paying the same citizen twice across
    // FirmLedger, StateWageMarket, StateWarehouses and Wages. Cleared once
    // per EconomySim.update tick before any payment system runs.
    public void markPaidThisTick(Induvidual indu) {
        if (indu != null) {
            this.paidThisTick.add(indu);
        }
    }

    public boolean wasPaidThisTick(Induvidual indu) {
        return indu != null && this.paidThisTick.contains(indu);
    }

    public void clearPaidThisTick() {
        this.paidThisTick.clear();
    }

    public int netWorth(Humanoid h) {
        int slot = this.liveSlot(h);
        return slot < 0 ? 0 : this.money[slot];
    }

    public int netWorth(Induvidual indu) {
        Integer slot = this.induSlot.get(indu);
        return slot == null ? 0 : this.money[slot];
    }

    public void accrueTax(Humanoid h, int amount) {
        if (amount <= 0) {
            return;
        }
        int slot = this.liveSlot(h);
        if (slot < 0) {
            return;
        }
        long sum = (long)this.taxAccrued[slot] + (long)amount;
        this.taxAccrued[slot] = sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)sum;
    }

    public void settleTaxResentment() {
        for (int i = 0; i < this.ownedCount; ++i) {
            int slot = this.ownedSlots[i];
            int paid = this.taxAccrued[slot];
            this.taxAccrued[slot] = 0;
            if (paid <= 0) {
                this.lastTaxRateBp[slot] = 0;
                continue;
            }
            long preTax = (long)this.money[slot] + (long)paid;
            long bp = preTax <= 0L ? 0L : (long)paid * 10000L / preTax;
            this.lastTaxRateBp[slot] = (int)Math.min(10000L, Math.max(0L, bp));
        }
    }

    public double lastTaxRate(Induvidual indu) {
        Integer slot = this.induSlot.get(indu);
        return slot == null ? 0.0 : (double)this.lastTaxRateBp[slot] / 10000.0;
    }

    public int chargeAffordable(Humanoid h, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int slot = this.liveSlot(h);
        if (slot < 0) {
            return 0;
        }
        int take = Math.min(amount, Math.max(0, this.money[slot] - this.reserved[slot]));
        if (take > 0) {
            int n = slot;
            this.money[n] = this.money[n] - take;
        }
        return take;
    }

    public int charge(Humanoid h, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int slot = this.liveSlot(h);
        if (slot < 0) {
            return 0;
        }
        if (this.money[slot] - this.reserved[slot] < amount) {
            return 0;
        }
        int n = slot;
        this.money[n] = this.money[n] - amount;
        return amount;
    }

    public void applyExchange(Humanoid a, Humanoid b, int newA) {
        int sa = this.liveSlot(a);
        int sb = this.liveSlot(b);
        if (sa < 0 || sb < 0) {
            return;
        }
        int total = this.spendable(a) + this.spendable(b);
        newA = Math.max(0, Math.min(total, newA));
        this.money[sa] = this.reserved[sa] + newA;
        this.money[sb] = this.reserved[sb] + total - newA;
    }

    public void markSeeded() {
        this.seeded = true;
    }

    public boolean isSeeded() {
        return this.seeded;
    }

    public void sweepDepartures(int tick, DepartureHandler out) {
        int i;
        for (i = 0; i < this.pendingDepartures.size(); ++i) {
            PendingDeparture p = this.pendingDepartures.get(i);
            out.departed(p.estate, p.relRef, p.emigrated);
        }
        this.pendingDepartures.clear();
        i = 0;
        while (i < this.ownedCount) {
            int slot = this.ownedSlots[i];
            if (this.seenTick[slot] == tick) {
                ++i;
                continue;
            }
            out.departed(this.money[slot], this.relRef[slot], this.emigrating[slot] != 0);
            this.owner[slot] = -1;
            this.money[slot] = 0;
            this.reserved[slot] = 0;
            this.taxDebt[slot] = 0;
            this.rentDebt[slot] = 0;
            this.relRef[slot] = 0;
            this.emigrating[slot] = 0;
            this.citizenClass[slot] = 0;
            if (this.induOf[slot] != null) {
                this.induSlot.remove(this.induOf[slot]);
                this.induOf[slot] = null;
            }
            this.ownedSlots[i] = this.ownedSlots[--this.ownedCount];
        }
    }

    public long circulating() {
        long sum = 0L;
        for (int i = 0; i < this.ownedCount; ++i) {
            sum += (long)this.money[this.ownedSlots[i]];
        }
        return sum;
    }

    public void reset() {
        Arrays.fill(this.owner, -1);
        Arrays.fill(this.money, 0);
        Arrays.fill(this.reserved, 0);
        Arrays.fill(this.taxDebt, 0);
        Arrays.fill(this.rentDebt, 0);
        Arrays.fill(this.lambdaBp, 0);
        Arrays.fill(this.lastTaxRateBp, 0);
        Arrays.fill(this.taxAccrued, 0);
        Arrays.fill(this.relRef, 0);
        Arrays.fill(this.emigrating, (byte)0);
        Arrays.fill(this.seenTick, 0);
        Arrays.fill(this.citizenClass, (byte)0);
        Arrays.fill(this.induOf, null);
        this.induSlot.clear();
        this.pendingDepartures.clear();
        this.ownedCount = 0;
        this.seeded = false;
    }

    static boolean supportsFormat(int version) {
        return version >= 19 && version <= 33;
    }

    public void save(FilePutter file) {
        file.i(33);
        file.bool(this.seeded);
        file.is(this.money);
        file.is(this.owner);
        file.is(this.lambdaBp);
        file.is(this.lastTaxRateBp);
        file.is(this.taxDebt);
        file.is(this.rentDebt);
        file.is(this.taxAccrued);
        file.bs(this.citizenClass);
    }

    public int load(FileGetter file) throws IOException {
        int version = file.i();
        if (!Wallets.supportsFormat(version)) {
            throw new IOException("incompatible economy save format " + version + " (supported 19..33)");
        }
        this.seeded = file.bool();
        file.is(this.money);
        file.is(this.owner);
        file.is(this.lambdaBp);
        file.is(this.lastTaxRateBp);
        file.is(this.taxDebt);
        if (version >= 32) {
            file.is(this.rentDebt);
        } else {
            Arrays.fill(this.rentDebt, 0);
        }
        if (version >= 24) {
            file.is(this.taxAccrued);
        } else {
            Arrays.fill(this.taxAccrued, 0);
        }
        if (version >= 33) {
            file.bs(this.citizenClass);
        } else {
            Arrays.fill(this.citizenClass, (byte)0);
            // Classes will be recomputed when EconomySim calls classifyAll() after load.
        }
        Arrays.fill(this.reserved, 0);
        this.ownedCount = 0;
        for (int slot = 0; slot < 60000; ++slot) {
            if (this.owner[slot] == -1) continue;
            this.own(slot);
        }
        return version;
    }

    // —— citizen class (Phase 3) —————————————————————————————————

    /** Get the stored citizen class for a humanoid. */
    public CitizenClass classOf(Humanoid h) {
        int slot = this.liveSlot(h);
        return slot < 0 ? CitizenClass.UNCLASSIFIED : CitizenClass.fromByte(this.citizenClass[slot]);
    }

    /** Set the citizen class for a humanoid. */
    public void setClass(Humanoid h, CitizenClass c) {
        int slot = this.liveSlot(h);
        if (slot >= 0) {
            this.citizenClass[slot] = c.toByte();
        }
    }

    /**
     * Reclassify all citizens based on current wealth, property, and origin.
     * Should be called after WealthStats.recompute() to have fresh median.
     */
    public void classifyAll(Roster roster, WealthStats stats, PropertyLedger ledger) {
        if (!EconConfig.citizenClassesEnabled) return;
        int median = stats.median;
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid h = roster.get(i);
            int wealth = this.netWorth(h);
            CitizenClass c = CitizenClass.classify(h, wealth, median, ledger);
            this.setClass(h, c);
        }
    }

    private static final class PendingDeparture {
        final int estate;
        final int relRef;
        final boolean emigrated;

        PendingDeparture(int estate, int relRef, boolean emigrated) {
            this.estate = estate;
            this.relRef = relRef;
            this.emigrated = emigrated;
        }
    }

    public static interface DepartureHandler {
        public void departed(int var1, int var2, boolean var3);
    }
}

