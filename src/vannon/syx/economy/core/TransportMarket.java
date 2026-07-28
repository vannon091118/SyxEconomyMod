package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.faction.FCredits;
import settlement.main.SETT;
import settlement.room.infra.station.ROOM_STATION;
import settlement.room.infra.transport.ROOM_TRANSPORT;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomInstance;
import vannon.syx.economy.adapter.ISyxTransport;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EventLog;
import vannon.syx.economy.core.FirmLedger;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class TransportMarket {
    private final ISyxTransport transportAdapter;
    private long lastPaid;
    private double lastMeanDistance;
    private int lastActiveStations;
    private boolean lastUsedReflection;

    public long lastPaid() {
        return this.lastPaid;
    }

    public double lastMeanDistance() {
        return this.lastMeanDistance;
    }

    public int lastActiveStations() {
        return this.lastActiveStations;
    }

    public boolean lastUsedReflection() {
        return this.lastUsedReflection;
    }

    public TransportMarket(ISyxTransport transportAdapter) {
        this.transportAdapter = transportAdapter;
    }

    public long update(double elapsedDays, Roster roster, Wallets wallets, FirmLedger ledger) {
        this.lastPaid = 0L;
        this.lastMeanDistance = 0.0;
        this.lastActiveStations = 0;
        if (!EconConfig.transportFeeEnabled || elapsedDays <= 0.0 || SETT.ROOMS() == null) {
            return 0L;
        }
        ROOM_TRANSPORT loading = SETT.ROOMS().TRANSPORT;
        ROOM_STATION unloading = SETT.ROOMS().STATION;
        if (loading == null) {
            return 0L;
        }
        double rate = (double)Math.max(0, EconConfig.transportFeePer100TileDay) / 100.0;
        if (rate <= 0.0) {
            return 0L;
        }
        double distSum = 0.0;
        int distCount = 0;
        for (int i = 0; i < loading.instancesSize(); ++i) {
            double dist;
            RoomInstance room = loading.getInstance(i);
            if (room == null || !room.exists() || room.employees() == null || room.employees().employed() <= 0 || (dist = this.haulDistance(room, unloading)) <= 0.0) continue;
            distSum += dist;
            ++distCount;
        }
        if (distSum <= 0.0) {
            return 0L;
        }
        this.lastActiveStations = distCount;
        this.lastMeanDistance = distCount > 0 ? distSum / (double)distCount : 0.0;
        long fee = (long)Math.floor(distSum * rate * elapsedDays);
        if (fee <= 0L) {
            return 0L;
        }
        int unloadHalf = (int)Math.min(Integer.MAX_VALUE, fee / 2L);
        int loadHalf = (int)Math.min(Integer.MAX_VALUE, fee - (long)unloadHalf);
        long credited = ledger.distributeServiceRevenue(roster, wallets, (RoomBlueprintImp)loading, loadHalf);
        if (unloading != null) {
            credited += (long)ledger.distributeServiceRevenue(roster, wallets, (RoomBlueprintImp)unloading, unloadHalf);
        }
        if (credited > 0L) {
            FACTIONS.player().credits().inc((double)(-credited), FCredits.CTYPE.MISC);
        }
        this.lastPaid = credited;
        return credited;
    }

    private double haulDistance(RoomInstance loadingStation, ROOM_STATION unloading) {
        double real = this.transportAdapter.getReflectedDistance(loadingStation);
        if (real >= 0.0) {
            this.lastUsedReflection = true;
            return real;
        }
        this.lastUsedReflection = false;
        return this.transportAdapter.getGeometricDistance(loadingStation, unloading);
    }

    private static double geometricDistance(RoomInstance loadingStation, ROOM_STATION unloading) {
        if (unloading == null || unloading.instancesSize() == 0) {
            return 0.0;
        }
        int lx = loadingStation.mX();
        int ly = loadingStation.mY();
        double best = Double.MAX_VALUE;
        for (int i = 0; i < unloading.instancesSize(); ++i) {
            double dy;
            double dx;
            double d;
            RoomInstance s = unloading.getInstance(i);
            if (s == null || !s.exists() || !((d = Math.sqrt((dx = (double)(s.mX() - lx)) * dx + (dy = (double)(s.mY() - ly)) * dy)) < best)) continue;
            best = d;
        }
        return best == Double.MAX_VALUE ? 0.0 : best;
    }

    public void clear() {
        this.lastPaid = 0L;
        this.lastMeanDistance = 0.0;
        this.lastActiveStations = 0;
    }
}

