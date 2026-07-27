package vannon.syx.economy.core;

import game.time.TIME;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;

import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.IRoomAccess;

import java.io.IOException;

/**
 * Phase 5e: Property-market controller extracted from EconomySim.
 *
 * <p>Handles the per-season property cycle: home purchases, firm share
 * trading, dividend accrual/payout, and orphaned-property reclamation.
 * Extracted to keep EconomySim under 1,500 LOC while maintaining zero
 * behavior change.</p>
 */
public final class PropertyMarketController {

    private final HousingMarket housingMarket;
    private final FirmLedger firmLedger;
    private final Wallets wallets;
    private final Roster roster;

    long propertySalesCollected;
    long propertyDividendsPaid;
    int lastPropertySeason = -1;

    public PropertyMarketController(HousingMarket housingMarket, FirmLedger firmLedger,
                                     Wallets wallets, Roster roster) {
        this.housingMarket = housingMarket;
        this.firmLedger = firmLedger;
        this.wallets = wallets;
        this.roster = roster;
    }

    public long salesCollected() { return propertySalesCollected; }
    public long dividendsPaid()  { return propertyDividendsPaid; }
    public int lastSeason()      { return lastPropertySeason; }

    /**
     * Phase 2: Property market tick (per season).
     * - Checks if citizens can afford to buy their state-owned homes.
     * - Accrues and pays out firm dividends to shareholders.
     */
    public void update() {
        if (!EconConfig.propertyMarketEnabled) return;
        int season = TIME.seasons().bitsSinceStart();
        if (this.lastPropertySeason == -1) {
            this.lastPropertySeason = season;
            return;
        }
        if (season == this.lastPropertySeason) return;
        this.lastPropertySeason = season;

        PropertyLedger ledger = this.housingMarket.ledger();

        // 0. Remove entries for demolished rooms
        ledger.cleanupGoneRooms();

        // 1. Home purchase
        IRoomAccess rooms = EngineMirror.api() != null ? EngineMirror.api().rooms() : null;
        if (EconConfig.homePurchaseEnabled && SETT.ROOMS() != null) {
            settlement.room.home.house.ROOM_HOME homeRoom = rooms != null ? rooms.getHome() : EngineSeams.settRoomsHome();
            if (homeRoom != null && homeRoom.service != null) {
                int tw = SETT.TWIDTH;
                int th = SETT.THEIGHT;
                for (int ty = 0; ty < th; ++ty) {
                    for (int tx = 0; tx < tw; ++tx) {
                        settlement.room.home.house.HomeInstance home =
                            homeRoom.service.get(tx, ty);
                        if (home == null || home.occupants() <= 0) continue;
                        PropertyLedger.Entry e = ledger.get(home, "HOME");
                        if (e == null || !e.isStateOwned()) continue;
                        for (int oi = 0; oi < home.occupants(); ++oi) {
                            Humanoid occupant = home.occupant(oi);
                            if (occupant == null || occupant.isRemoved()) continue;
                            long price = ledger.buyHome(occupant,
                                (settlement.room.home.HOME) home, this.wallets);
                            if (price > 0L) {
                                this.propertySalesCollected += price;
                                break;
                            }
                        }
                    }
                }
            }
            settlement.room.home.chamber.ROOM_CHAMBER chamberRoom = rooms != null ? rooms.getChamber() : EngineSeams.settRoomsChamber();
            if (chamberRoom != null) {
                for (int i = 0; i < chamberRoom.instancesSize(); ++i) {
                    settlement.room.home.chamber.ChamberInstance chamber =
                        chamberRoom.getInstance(i);
                    if (chamber == null || !chamber.exists() || chamber.occupants() <= 0) continue;
                    PropertyLedger.Entry e = ledger.get(chamber, "CHAMBER");
                    if (e == null || !e.isStateOwned()) continue;
                    for (int oi = 0; oi < chamber.occupants(); ++oi) {
                        Humanoid occupant = chamber.occupant(oi);
                        if (occupant == null || occupant.isRemoved()) continue;
                        long price = ledger.buyHome(occupant,
                            (settlement.room.home.HOME) chamber, this.wallets);
                        if (price > 0L) {
                            this.propertySalesCollected += price;
                            break;
                        }
                    }
                }
            }
        }

        // 2. Reclaim property from dead/emigrated citizens
        reclaimOrphanedProperty();

        // 3. Firm share purchases and dividend cycle
        if (EconConfig.workplaceSharesEnabled) {
            ledger.accrueDividends(this.firmLedger, rooms != null ? rooms.getRoomImps() : EngineSeams.settRoomsImps());
            this.propertyDividendsPaid += ledger.payDividends(this.wallets, this.roster, season);

            for (int i = 0; i < this.roster.size(); ++i) {
                Humanoid citizen = this.roster.get(i);
                int wealth = this.wallets.spendable(citizen);
                double firmMult = EconConfig.citizenClassesEnabled
                    ? this.wallets.classOf(citizen).firmBuyThresholdMultiplier : 1.0;
                if (firmMult >= 999.0) continue;
                if (wealth < (int)((double)EconConfig.initialWallet * 5.0 * firmMult)) continue;
                firmLoop:
                for (RoomBlueprintImp bp : EconomySim.active().cachedWorkplaces()) {
                    if (!(bp instanceof RoomBlueprintIns)) continue;
                    RoomBlueprintIns<?> ins = (RoomBlueprintIns<?>) bp;
                    for (int j = 0; j < ins.instancesSize(); ++j) {
                        RoomInstance room = (RoomInstance) ins.getInstance(j);
                        if (room == null || !room.exists()) continue;
                        PropertyLedger.Entry e = ledger.get(room);
                        if (e == null || e.shares >= 100) continue;
                        int maxShares = ledger.maxSharesForCitizen((long) citizen.id());
                        if (maxShares <= 0) continue;
                        int available = 100 - e.shares;
                        int toBuy = Math.min(maxShares, Math.min(available, 10));
                        if (toBuy <= 0) continue;
                        long sharePrice = ledger.buyShares(citizen, room, toBuy,
                            this.wallets, this.firmLedger);
                        if (sharePrice > 0L) {
                            this.propertySalesCollected += sharePrice;
                            break firmLoop;
                        }
                    }
                }
            }
        }
    }

    private void reclaimOrphanedProperty() {
        if (!EconConfig.propertyMarketEnabled) return;
        java.util.HashSet<Integer> alive = new java.util.HashSet<>();
        for (int i = 0; i < this.roster.size(); ++i) {
            alive.add(this.roster.get(i).id());
        }
        this.housingMarket.ledger().reclaimDeadOwners(alive);
    }

    void save(FilePutter file) {
        file.l(propertySalesCollected);
        file.l(propertyDividendsPaid);
        file.i(lastPropertySeason);
    }

    void load(FileGetter file, int expectedEnd) throws IOException {
        this.propertySalesCollected = expectedEnd - file.getPosition() >= 8 ? file.l() : 0L;
        this.propertyDividendsPaid = expectedEnd - file.getPosition() >= 8 ? file.l() : 0L;
        this.lastPropertySeason = expectedEnd - file.getPosition() >= 4 ? file.i() : -1;
    }

    void reset() {
        this.propertySalesCollected = 0L;
        this.propertyDividendsPaid = 0L;
        this.lastPropertySeason = -1;
    }
}
