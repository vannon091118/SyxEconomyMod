package vannon.syx.economy.core;

import game.faction.FACTIONS;
import game.time.TIME;
import java.util.HashMap;
import java.util.Map;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.home.HOME;
import settlement.room.home.chamber.ChamberInstance;
import settlement.room.home.chamber.ROOM_CHAMBER;
import settlement.room.home.house.HomeInstance;
import settlement.room.home.house.ROOM_HOME;
import settlement.room.main.RoomInstance;
import settlement.stats.STATS;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.IRoomAccess;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.FirmLedger;
import vannon.syx.economy.core.PropertyLedger;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class HousingMarket implements Saveable {
    private final PropertyLedger ledger = new PropertyLedger();
    private final Map<Integer, Integer> graceBySlot = new HashMap<>();
    private int lastSeason = -1;
    private long lastRentCollected = 0L;
    private long lastRentDue = 0L;
    private int lastEvictions = 0;

    public long lastRentCollected() {
        return this.lastRentCollected;
    }

    public long lastRentDue() {
        return this.lastRentDue;
    }

    public int lastEvictions() {
        return this.lastEvictions;
    }

    public PropertyLedger ledger() {
        return this.ledger;
    }

    public long update(Roster roster, Wallets wallets, FirmLedger firmLedger) {
        if (!EconConfig.housingMarketEnabled) {
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
        this.lastRentCollected = 0L;
        this.lastRentDue = 0L;
        this.lastEvictions = 0;
        if (SETT.ROOMS() == null) {
            return 0L;
        }
        this.collectFromHomes(roster, wallets, firmLedger);
        this.collectFromChambers(roster, wallets, firmLedger);
        return this.lastRentCollected;
    }

    private void collectFromHomes(Roster roster, Wallets wallets, FirmLedger firmLedger) {
        IRoomAccess rooms = EngineMirror.api() != null ? EngineMirror.api().rooms() : null;
        if (rooms == null) return;
        ROOM_HOME homeBp = rooms.getHome();
        if (homeBp == null || homeBp.service == null) {
            return;
        }
        int tw = SETT.TWIDTH;
        int th = SETT.THEIGHT;
        for (int ty = 0; ty < th; ++ty) {
            for (int tx = 0; tx < tw; ++tx) {
                HomeInstance home = homeBp.service.get(tx, ty);
                if (home == null || home.occupants() <= 0) continue;
                this.collectRent(home, home.occupants(), "HOME", roster, wallets, firmLedger);
            }
        }
    }

    private void collectFromChambers(Roster roster, Wallets wallets, FirmLedger firmLedger) {
        IRoomAccess rm2 = EngineMirror.api() != null ? EngineMirror.api().rooms() : null;
        if (rm2 == null) return;
        ROOM_CHAMBER chamberBp = rm2.getChamber();
        if (chamberBp == null) {
            return;
        }
        for (int i = 0; i < chamberBp.instancesSize(); ++i) {
            ChamberInstance chamber = chamberBp.getInstance(i);
            if (chamber == null || !chamber.exists()) continue;
            this.collectRent(chamber, chamber.occupants(), "CHAMBER", roster, wallets, firmLedger);
        }
    }

    private void collectRent(HOME home, int occupants, String blueprintKey, Roster roster, Wallets wallets, FirmLedger firmLedger) {
        if (occupants <= 0) {
            return;
        }
        // Phase 2: Privately-owned homes pay no rent to the state.
        if (EconConfig.propertyMarketEnabled && EconConfig.homePurchaseEnabled) {
            PropertyLedger.Entry e = this.ledger.get(home, blueprintKey);
            if (e != null && !e.isStateOwned()) {
                return;  // owner-occupied — no rent collected
            }
        }
        int rent = this.rentFor(home);
        if (rent <= 0) {
            return;
        }
        this.lastRentDue += (long) rent;
        long houseCollected = 0L;
        int remaining = rent;
        for (int oi = 0; oi < occupants; ++oi) {
            Humanoid occupant = home.occupant(oi);
            if (occupant == null || occupant.isRemoved()) continue;
            int share = remaining / (occupants - oi);
            int due = (oi == occupants - 1) ? remaining : share;
            remaining -= due;
            int paid = wallets.chargeAffordable(occupant, due);
            houseCollected += (long) paid;
            if (paid < due) {
                wallets.addRentDebt(occupant, due - paid);
                if (wallets.rentDebt(occupant) >= EconConfig.housingEvictionDebtThreshold) {
                    int grace = this.graceBySlot.getOrDefault(occupant.id(), 0) + 1;
                    this.graceBySlot.put(occupant.id(), grace);
                    if (grace >= Math.max(0, EconConfig.housingGraceDays)) {
                        this.evict(occupant, home, wallets);
                    }
                }
            } else {
                this.graceBySlot.remove(occupant.id());
                wallets.clearRentDebt(occupant);
            }
        }
        this.lastRentCollected += houseCollected;
        if (houseCollected > 0L) {
            FACTIONS.player().credits().inc((double) houseCollected, game.faction.FCredits.CTYPE.MISC);
        }
    }

    private int rentFor(HOME home) {
        if (home == null) return 0;
        double isolation = Math.max(0.0, Math.min(1.0, home.isolation()));
        int area = Math.max(0, home.area());
        double upgrade = 0.0;
        if (home instanceof settlement.room.home.house.HomeInstance) {
            upgrade = ((settlement.room.home.house.HomeInstance) home).upgrade();
        }
        double quality = 1.0 + isolation + upgrade * 0.25;
        long rent = (long) Math.max(0, EconConfig.housingBaseRentPerTile) * (long) area;
        rent = (long) ((double) rent * quality);
        return (int) Math.min((long) Integer.MAX_VALUE, Math.max(0L, rent));
    }

    private void evict(Humanoid occupant, HOME home, Wallets wallets) {
        if (occupant == null || occupant.isRemoved()) return;
        this.lastEvictions++;
        this.graceBySlot.remove(occupant.id());
        if (STATS.HOME().GETTER != null) {
            STATS.HOME().GETTER.set(occupant, null);
        }
        EventLog.log("HOUSING", occupant.title() + " evicted due to unpaid rent debt (" + wallets.rentDebt(occupant) + ")");
    }

    @Override
    public void save(FilePutter file) {
        file.i(this.lastSeason);
        file.l(this.lastRentCollected);
        file.l(this.lastRentDue);
        file.i(this.lastEvictions);
        this.ledger.save(file);
        file.i(this.graceBySlot.size());
        for (Map.Entry<Integer, Integer> e : this.graceBySlot.entrySet()) {
            file.i(e.getKey());
            file.i(e.getValue());
        }
    }

    @Override
    public void load(FileGetter file) throws java.io.IOException {
        this.lastSeason = file.i();
        this.lastRentCollected = file.l();
        this.lastRentDue = file.l();
        this.lastEvictions = file.i();
        this.ledger.load(file);
        this.graceBySlot.clear();
        int graceCount = file.i();
        for (int i = 0; i < graceCount; ++i) {
            int slot = file.i();
            int grace = file.i();
            this.graceBySlot.put(slot, grace);
        }
    }

    public void clear() {
        this.lastSeason = -1;
        this.lastRentCollected = 0L;
        this.lastRentDue = 0L;
        this.lastEvictions = 0;
        this.ledger.clear();
        this.graceBySlot.clear();
    }
}
