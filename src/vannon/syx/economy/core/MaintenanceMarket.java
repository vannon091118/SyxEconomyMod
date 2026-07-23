package vannon.syx.economy.core;

import init.type.HCLASSES;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.maintenance.ROOM_DEGRADER;
import settlement.room.infra.janitor.ROOM_JANITOR;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import game.time.TIME;
import settlement.stats.STATS;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomicRoles;
import vannon.syx.economy.core.FirmEconomyKernel;
import vannon.syx.economy.core.FirmLedger;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class MaintenanceMarket implements Saveable {
    private final Map<RoomInstance, WorkplaceState> workplaces = new IdentityHashMap<RoomInstance, WorkplaceState>();
    private final Set<Integer> controlledDisabled = new HashSet<Integer>();
    private int lastRefreshTick = -1073741824;
    private long lastPaid;

    /** Phase-4.7 (Task 2): register the RoomInstance-keyed workplaces map for clearOnLoad.
     *  Pattern follows FlowMeter / WarehouseMarket — explicit constructor body
     *  because the type lacked one. Map rebuilds via computeIfAbsent every refresh tick. */
    public MaintenanceMarket() {
        IdentityMapRegistry.register("MaintenanceMarket", "workplaces", workplaces);
    }

    public long lastPaid() {
        return this.lastPaid;
    }

    public Settlement update(int ticks, Roster roster, Wallets wallets, FirmLedger ledger) {
        this.lastPaid = 0L;
        if (!EconConfig.maintenanceMarketEnabled || SETT.ROOMS() == null) {
            this.releaseControlledTiles();
            return new Settlement(0L, 0L);
        }
        int refreshThreshold = Math.max(1, (int)(EconConfig.maintenanceRefreshDays * TIME.secondsPerDay()));
        if (ticks - this.lastRefreshTick < refreshThreshold) {
            return new Settlement(0L, 0L);
        }
        this.lastRefreshTick = ticks;
        ArrayList<Candidate> candidates = new ArrayList<Candidate>();
        Set<RoomInstance> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        long due = 0L;
        for (RoomBlueprintIns<?> blueprint : SETT.ROOMS().ins()) {
            if (blueprint == SETT.ROOMS().JANITOR) continue;
            for (int i = 0; i < blueprint.instancesSize(); ++i) {
                long roomDue;
                int collected;
                ROOM_DEGRADER degrader;
                RoomInstance room2 = blueprint.getInstance(i);
                if (room2 == null || !room2.exists() || room2.employees() == null || room2.employees().max() <= 0 || EconomicRoles.stateFundedPublicWorks((RoomBlueprintImp)room2.blueprintI()) || (degrader = room2.degrader(room2.mX(), room2.mY())) == null) continue;
                seen.add(room2);
                double degradation = Math.max(degrader.get(), degrader.getSecret());
                int bid = MaintenanceMarket.bid(degradation, EconConfig.maintenanceBidBase);
                int jobs = Math.max(0, degrader.jobs());
                WorkplaceState state = this.workplaces.computeIfAbsent(room2, ignored -> new WorkplaceState(jobs, bid));
                if (state.initialized && jobs < state.jobs && (collected = MaintenanceMarket.charge(room2, (int)Math.min(Integer.MAX_VALUE, roomDue = (long)(state.jobs - jobs) * (long)Math.max(0, state.bid)), roster, wallets)) > 0) {
                    due += (long)collected;
                    ledger.recordFirmCost(room2, collected);
                }
                state.jobs = jobs;
                state.bid = bid;
                state.initialized = true;
                ArrayList<Integer> jobTiles = this.maintenanceTiles(room2);
                if (jobTiles.isEmpty()) continue;
                candidates.add(new Candidate(room2, bid, degradation, jobTiles));
            }
        }
        this.workplaces.keySet().removeIf(room -> !seen.contains(room));
        candidates.sort(Comparator.comparingInt(Candidate::bid).reversed().thenComparing(Comparator.comparingDouble(Candidate::degradation).reversed()).thenComparingInt(candidate -> candidate.room().mY() * SETT.TWIDTH + candidate.room().mX()));
        int winners = MaintenanceMarket.winnerCount(this.janitorWorkers(), EconConfig.maintenanceWorkplacesPerJanitor);
        this.applyWinners(candidates, winners);
        this.lastPaid = this.payJanitors(due, roster, wallets, ledger);
        return new Settlement(due, this.lastPaid);
    }

    private long payJanitors(long rawDue, Roster roster, Wallets wallets, FirmLedger ledger) {
        if (rawDue <= 0L) {
            return 0L;
        }
        int payable = (int)Math.min(Integer.MAX_VALUE, rawDue);
        ROOM_JANITOR janitors = SETT.ROOMS().JANITOR;
        ArrayList<RoomInstance> active = new ArrayList<RoomInstance>();
        for (int i = 0; i < janitors.instancesSize(); ++i) {
            RoomInstance room = janitors.getInstance(i);
            if (room == null || room.employees() == null || room.employees().employed() <= 0) continue;
            active.add(room);
        }
        if (active.isEmpty()) {
            return 0L;
        }
        int[] shares = FirmEconomyKernel.split(payable, active.size());
        int credited = 0;
        for (int i = 0; i < active.size(); ++i) {
            credited += ledger.distributeFirmRevenue(roster, wallets, (RoomInstance)active.get(i), shares[i]);
        }
        return credited;
    }

    private static int charge(RoomInstance room, int amount, Roster roster, Wallets wallets) {
        if (room == null || amount <= 0) {
            return 0;
        }
        ArrayList<Humanoid> workers = new ArrayList<Humanoid>();
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid worker = roster.get(i);
            if (worker.indu().clas() == HCLASSES.SLAVE() || STATS.WORK().EMPLOYED.get(worker.indu()) != room) continue;
            workers.add(worker);
        }
        if (workers.isEmpty()) {
            return 0;
        }
        int[] shares = FirmEconomyKernel.split(amount, workers.size());
        int collected = 0;
        for (int i = 0; i < shares.length; ++i) {
            int due = Math.min(shares[i], wallets.spendable((Humanoid)workers.get(i)));
            if (due <= 0) continue;
            wallets.add((Humanoid)workers.get(i), -due);
            collected += due;
        }
        return collected;
    }

    private int janitorWorkers() {
        int workers = 0;
        ROOM_JANITOR janitors = SETT.ROOMS().JANITOR;
        for (int i = 0; i < janitors.instancesSize(); ++i) {
            RoomInstance room = janitors.getInstance(i);
            if (room == null || room.employees() == null) continue;
            workers += room.employees().employed();
        }
        return workers;
    }

    private ArrayList<Integer> maintenanceTiles(RoomInstance room) {
        ArrayList<Integer> result = new ArrayList<Integer>();
        for (COORDINATE tile : room.body()) {
            int index;
            if (!room.is(tile.x(), tile.y()) || !SETT.MAINTENANCE().isser.is(tile.x(), tile.y()) || SETT.MAINTENANCE().disabled.is(index = tile.x() + tile.y() * SETT.TWIDTH) && !this.controlledDisabled.contains(index)) continue;
            result.add(index);
        }
        return result;
    }

    private void applyWinners(ArrayList<Candidate> candidates, int winners) {
        HashSet<Integer> live = new HashSet<Integer>();
        for (Candidate candidate : candidates) {
            live.addAll(candidate.jobTiles());
        }
        Iterator<Integer> iterator = this.controlledDisabled.iterator();
        while (iterator.hasNext()) {
            int tile = iterator.next();
            if (live.contains(tile)) continue;
            SETT.MAINTENANCE().disabled.set(tile, false);
            iterator.remove();
        }
        for (int i = 0; i < candidates.size(); ++i) {
            boolean winning = i < winners;
            for (int tile : candidates.get(i).jobTiles()) {
                if (winning) {
                    if (!this.controlledDisabled.remove(tile)) continue;
                    SETT.MAINTENANCE().disabled.set(tile, false);
                    continue;
                }
                if (SETT.MAINTENANCE().disabled.is(tile)) continue;
                SETT.MAINTENANCE().disabled.set(tile, true);
                this.controlledDisabled.add(tile);
            }
        }
    }

    static int bid(double degradation, int base) {
        double d = Math.max(0.0, Math.min(1.0, degradation));
        return (int)Math.ceil((double)Math.max(0, base) * d);
    }

    static int winnerCount(int janitorWorkers, int workplacesPerJanitor) {
        long value = (long)Math.max(0, janitorWorkers) * (long)Math.max(0, workplacesPerJanitor);
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)value;
    }

    public void save(FilePutter file) {
        file.i(this.controlledDisabled.size());
        for (int tile : this.controlledDisabled) {
            file.i(tile);
        }
    }

    public void load(FileGetter file) throws IOException {
        this.controlledDisabled.clear();
        int count = file.i();
        if (count < 0 || count > SETT.TAREA) {
            throw new IOException("invalid maintenance-market tile count: " + count);
        }
        for (int i = 0; i < count; ++i) {
            int tile = file.i();
            if (tile < 0 || tile >= SETT.TAREA) {
                throw new IOException("invalid maintenance-market tile: " + tile);
            }
            this.controlledDisabled.add(tile);
        }
    }

    public void clear() {
        this.releaseControlledTiles();
        this.workplaces.clear();
        this.lastRefreshTick = -1073741824;
        this.lastPaid = 0L;
    }

    private void releaseControlledTiles() {
        if (SETT.ROOMS() != null) {
            for (int tile : this.controlledDisabled) {
                SETT.MAINTENANCE().disabled.set(tile, false);
            }
        }
        this.controlledDisabled.clear();
    }

    public record Settlement(long billed, long credited) {
    }

    private static final class WorkplaceState {
        int jobs;
        int bid;
        boolean initialized;

        WorkplaceState(int jobs, int bid) {
            this.jobs = jobs;
            this.bid = bid;
        }
    }

    private record Candidate(RoomInstance room, int bid, double degradation, ArrayList<Integer> jobTiles) {
    }
}

