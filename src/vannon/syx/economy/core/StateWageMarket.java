package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.time.TIME;
import init.type.HCLASSES;
import java.lang.invoke.LambdaMetafactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.stats.STATS;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomicRoles;
import vannon.syx.economy.core.FirmLedger;
import vannon.syx.economy.core.MilitaryPayroll;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class StateWageMarket {
    private final Entry militaryEntry = StateWageMarket.entry(EconTexts.¤¤roleMilitaryTrainees, EconomicRoles::stateFundedMilitary, () -> EconConfig.militaryTrainingWagePerDay, w -> {
        EconConfig.militaryTrainingWagePerDay = w;
    });
    private final Entry[] entries = new Entry[]{StateWageMarket.entry(EconTexts.¤¤roleExportDepot, EconomicRoles::stateFundedExportDepot, () -> EconConfig.exportDepotWagePerDay, w -> {
        EconConfig.exportDepotWagePerDay = w;
    }), StateWageMarket.entry(EconTexts.¤¤roleHaulers, EconomicRoles::stateFundedHauler, () -> EconConfig.haulerWagePerDay, w -> {
        EconConfig.haulerWagePerDay = w;
    }), StateWageMarket.entry(EconTexts.¤¤roleArmySupply, EconomicRoles::stateFundedArmySupply, () -> EconConfig.armySupplyWagePerDay, w -> {
        EconConfig.armySupplyWagePerDay = w;
    }), StateWageMarket.entry(EconTexts.¤¤roleLaboratory, EconomicRoles::stateFundedLaboratory, () -> EconConfig.laboratoryWagePerDay, w -> {
        EconConfig.laboratoryWagePerDay = w;
    }), StateWageMarket.entry(EconTexts.¤¤roleLibrary, EconomicRoles::stateFundedLibrary, () -> EconConfig.libraryWagePerDay, w -> {
        EconConfig.libraryWagePerDay = w;
    }), StateWageMarket.entry(EconTexts.¤¤roleEmbassy, EconomicRoles::stateFundedEmbassy, () -> EconConfig.embassyWagePerDay, w -> {
        EconConfig.embassyWagePerDay = w;
    }), StateWageMarket.entry(EconTexts.¤¤roleWaterWorks, EconomicRoles::stateFundedWaterworks, () -> EconConfig.waterWagePerDay, w -> {
        EconConfig.waterWagePerDay = w;
    }), StateWageMarket.entry(EconTexts.¤¤roleCannibalHouse, EconomicRoles::stateFundedCannibal, () -> EconConfig.cannibalWagePerDay, w -> {
        EconConfig.cannibalWagePerDay = w;
    }), StateWageMarket.entry(EconTexts.¤¤roleSecretPolice, EconomicRoles::stateFundedPolice, () -> EconConfig.policeWagePerDay, w -> {
        EconConfig.policeWagePerDay = w;
    }), StateWageMarket.entry(EconTexts.¤¤roleGuards, EconomicRoles::stateFundedGuard, () -> EconConfig.guardWagePerDay, w -> {
        EconConfig.guardWagePerDay = w;
    }), StateWageMarket.entry(EconTexts.¤¤roleStockadeJailors, EconomicRoles::stateFundedStockade, () -> EconConfig.stockadeWagePerDay, w -> {
        EconConfig.stockadeWagePerDay = w;
    }), StateWageMarket.entry(EconTexts.¤¤rolePrisonJailors, EconomicRoles::stateFundedPrison, () -> EconConfig.prisonWagePerDay, w -> {
        EconConfig.prisonWagePerDay = w;
    })};
    private final Entry[] laborEntries = StateWageMarket.prepend(this.militaryEntry, this.entries);
    private final Map<String, Double> carry = new HashMap<String, Double>();

    public StateWageMarket() {
    }

    private static Entry entry(String name, Predicate<RoomBlueprintImp> covers, IntSupplier get, IntConsumer set) {
        return new Entry(name, covers, get, set);
    }

    private static Entry[] prepend(Entry first, Entry[] rest) {
        Entry[] result = new Entry[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }

    public Entry[] entries() {
        return this.entries;
    }

    public Entry[] laborEntries() {
        return this.laborEntries;
    }

    public long update(double ds, Roster roster, Wallets wallets, FirmLedger ledger) {
        for (Entry entry : this.laborEntries) {
            entry.resetReport();
        }
        if (!(ds > 0.0) || SETT.ROOMS() == null) {
            return 0L;
        }
        double daySeconds = TIME.secondsPerDay();
        if (!(daySeconds > 0.0)) {
            return 0L;
        }
        IdentityHashMap<Object, Group> byBlueprint = new IdentityHashMap<>();
        for (RoomBlueprintIns<?> blueprint : SETT.ROOMS().ins()) {
            Entry entry;
            if (blueprint.instancesSize() <= 0 || (entry = this.entryFor((RoomBlueprintImp)blueprint)) == null) continue;
            byBlueprint.put(blueprint, new Group((RoomBlueprintImp)blueprint, entry));
        }
        for (int i = 0; i < roster.size(); ++i) {
            RoomBlueprintIns<?> blueprint;
            Entry entry;
            RoomInstance room;
            Humanoid worker = roster.get(i);
            if (worker.indu().clas() == HCLASSES.SLAVE() || (room = (RoomInstance)STATS.WORK().EMPLOYED.get(worker.indu())) == null || (entry = this.entryFor((RoomBlueprintImp)(blueprint = room.blueprintI()))) == null) continue;
            Entry finalEntry = entry;
            ++byBlueprint.computeIfAbsent(blueprint, k -> new Group((RoomBlueprintImp) k, finalEntry)).workers;
        }
        ArrayList<Group> groups = new ArrayList<>(byBlueprint.values());
        int[] dues = new int[groups.size()];
        long totalDue = 0L;
        for (int i = 0; i < groups.size(); ++i) {
            Group group = (Group)groups.get(i);
            group.entry.lastWorkers += group.workers;
            int wage = group.entry.wage();
            if (wage <= 0) {
                this.carry.remove(group.blueprint.key);
                continue;
            }
            if (group.workers <= 0) continue;
            Accrual accrual = StateWageMarket.accrue(this.carry.getOrDefault(group.blueprint.key, 0.0), group.workers, wage, ds, daySeconds);
            group.due = accrual.due();
            this.carry.put(group.blueprint.key, accrual.carry());
            dues[i] = group.due;
            totalDue = StateWageMarket.safeAdd(totalDue, group.due);
            group.entry.lastDue = StateWageMarket.safeAdd(group.entry.lastDue, group.due);
        }
        long treasury = Math.max(0L, (long)Math.floor(FACTIONS.player().credits().credits()));
        int[] payments = StateWageMarket.allocateProportionally(dues, treasury);
        double fundedShare = treasury <= 0L ? 0.0 : (totalDue <= 0L || treasury >= totalDue ? 1.0 : (double)treasury / (double)totalDue);
        for (Group group : groups) {
            StateWageMarket.ensureHiringBootstrap(group, fundedShare > 0.0);
        }
        long totalPaid = 0L;
        for (int i = 0; i < groups.size(); ++i) {
            Group group = (Group)groups.get(i);
            int wage = group.entry.wage();
            int credited = payments[i] <= 0 ? 0 : ledger.distributeStateWage(roster, wallets, group.blueprint, payments[i]);
            group.entry.lastPaid = StateWageMarket.safeAdd(group.entry.lastPaid, credited);
            if (credited < group.due) {
                group.entry.treasuryBlocked = true;
            }
            totalPaid = StateWageMarket.safeAdd(totalPaid, credited);
            double marginal = wage > 0 ? (double)wage * fundedShare : 0.0;
            ledger.recordStateWageMarginal(group.blueprint, marginal);
        }
        if (totalPaid > 0L) {
            FACTIONS.player().credits().inc((double)(-totalPaid), FCredits.CTYPE.MISC);
        }
        return totalPaid;
    }

    /** Bootstrapped jede State-Gebäude-Instanz ohne Arbeiter auf mindestens 1 Target.
     *  v1.7.1-Fix: Vorher wurde nur EINE Instanz gebootstrapped und bei
     *  hardTarget>0 sofort retourniert (bug: return statt continue). */
    private static void ensureHiringBootstrap(Group group, boolean treasuryFunded) {
        RoomBlueprintImp roomBlueprintImp;
        if (group.workers > 0 || !treasuryFunded || group.entry.wage() <= 0 || !((roomBlueprintImp = group.blueprint) instanceof RoomBlueprintIns)) {
            return;
        }
        RoomBlueprintIns<?> instances = (RoomBlueprintIns<?>)roomBlueprintImp;
        for (int i = 0; i < instances.instancesSize(); ++i) {
            RoomInstance room = instances.getInstance(i);
            if (room == null || !room.exists() || room.employees() == null || room.employees().max() <= 0) continue;
            int currentTarget = room.employees().hardTarget();
            int employed = room.employees().employed();
            // Bootstrap: setze Target auf mindestens 1, auch wenn bereits employed>0
            // (kann passieren wenn vanilla target=0 aber Arbeiter per manuellem Override da sind)
            if (currentTarget <= 0 && employed <= 0) {
                room.employees().neededSet(1);
            }
        }
    }

    private Entry entryFor(RoomBlueprintImp blueprint) {
        for (Entry entry : this.laborEntries) {
            if (!entry.covers.test(blueprint)) continue;
            return entry;
        }
        return null;
    }

    public void clear() {
        this.carry.clear();
        for (Entry entry : this.laborEntries) {
            entry.resetReport();
        }
    }

    static Accrual accrue(double previousCarry, int workers, int wage, double ds, double daySeconds) {
        if (workers <= 0 || wage <= 0 || !(ds > 0.0) || !(daySeconds > 0.0)) {
            return new Accrual(0, 0.0);
        }
        double gross = Math.max(0.0, previousCarry) + (double)workers * (double)wage * ds / daySeconds;
        if (!Double.isFinite(gross) || gross >= 2.147483647E9) {
            return new Accrual(Integer.MAX_VALUE, 0.0);
        }
        int due = (int)Math.floor(gross + 1.0E-9);
        double remainder = gross - (double)due;
        if (remainder < 0.0) {
            remainder = 0.0;
        }
        if (remainder >= 1.0) {
            remainder %= 1.0;
        }
        return new Accrual(due, remainder);
    }

    static int[] allocateProportionally(int[] dues, long treasury) {
        int[] paid = new int[dues.length];
        long total = 0L;
        for (int due : dues) {
            total = StateWageMarket.safeAdd(total, Math.max(0, due));
        }
        long budget = Math.min(Math.max(0L, treasury), total);
        if (budget <= 0L || total <= 0L) {
            return paid;
        }
        double[] fractions = new double[dues.length];
        long assigned = 0L;
        for (int i = 0; i < dues.length; ++i) {
            int due = Math.max(0, dues[i]);
            double exact = (double)due * (double)budget / (double)total;
            paid[i] = Math.min(due, (int)Math.floor(exact));
            fractions[i] = exact - (double)paid[i];
            assigned += (long)paid[i];
        }
        for (long left = budget - assigned; left > 0L; --left) {
            int best = -1;
            for (int i = 0; i < paid.length; ++i) {
                if (paid[i] >= Math.max(0, dues[i]) || best >= 0 && !(fractions[i] > fractions[best])) continue;
                best = i;
            }
            if (best < 0) break;
            int n = best;
            paid[n] = paid[n] + 1;
            fractions[best] = -1.0;
        }
        return paid;
    }

    private static long safeAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static /* synthetic */ Group lambda$update$26(RoomBlueprintImp blueprint, Entry entry, RoomBlueprintImp ignored) {
        return new Group(blueprint, entry);
    }

    public static final class Entry {
        public final String name;
        final Predicate<RoomBlueprintImp> covers;
        final IntSupplier wageGet;
        final IntConsumer wageSet;
        long lastDue;
        long lastPaid;
        int lastWorkers;
        boolean treasuryBlocked;

        Entry(String name, Predicate<RoomBlueprintImp> covers, IntSupplier get, IntConsumer set) {
            this.name = name;
            this.covers = covers;
            this.wageGet = get;
            this.wageSet = set;
        }

        public long lastDue() {
            return this.lastDue;
        }

        public long lastPaid() {
            return this.lastPaid;
        }

        public int lastWorkers() {
            return this.lastWorkers;
        }

        public boolean treasuryBlocked() {
            return this.treasuryBlocked;
        }

        public int wage() {
            return MilitaryPayroll.clampWage(this.wageGet.getAsInt());
        }

        public void setWage(int wage) {
            this.wageSet.accept(MilitaryPayroll.clampWage(wage));
        }

        void resetReport() {
            this.lastDue = 0L;
            this.lastPaid = 0L;
            this.lastWorkers = 0;
            this.treasuryBlocked = false;
        }
    }

    private static final class Group {
        final RoomBlueprintImp blueprint;
        final Entry entry;
        int workers;
        int due;

        Group(RoomBlueprintImp blueprint, Entry entry) {
            this.blueprint = blueprint;
            this.entry = entry;
        }
    }

    record Accrual(int due, double carry) {
    }
}

