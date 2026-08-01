package vannon.syx.economy.headless;

import init.resources.RESOURCE;
import settlement.entity.humanoid.Humanoid;
import settlement.room.home.chamber.ROOM_CHAMBER;
import settlement.room.home.house.ROOM_HOME;
import settlement.room.infra.janitor.ROOM_JANITOR;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.room.service.food.canteen.ROOM_CANTEEN;
import settlement.room.service.food.eatery.ROOM_EATERY;
import settlement.room.service.module.RoomService;
import snake2d.util.sets.LIST;
import vannon.syx.economy.adapter.IRoomAccess;

import java.util.Collections;

/**
 * Headless stub for {@link IRoomAccess}. No stockpile or transport station
 * exists in the stub world — every read returns the safe default documented
 * on the interface ({@code 1.0}, {@code 0}, {@code -1.0}, etc.). Iteration
 * methods return empty lists. {@code entitiesAvailable() == true} so the
 * EconomySim-side early-return checks pass.
 */
public final class StubRoomAccess implements IRoomAccess {

    private final MockWorldState state;

    public StubRoomAccess(MockWorldState state) { this.state = state; }

    @Override public boolean isAvailable() { return true; }

    // ── Stockpile Read ──────────────────────────────────────
    @Override public double  getStoredRatio(StockpileInstance s, RESOURCE r) { return 1.0; }
    @Override public double  getUsedSpace(StockpileInstance s) { return 0.0; }
    @Override public int     getCrateSize(StockpileInstance s) { return 0; }
    @Override public int     getCrateSize(StockpileInstance s, RESOURCE r) { return 0; }
    @Override public int     getTotalCrates(StockpileInstance s) { return 0; }
    @Override public int     getSpecialAmount(StockpileInstance s, RESOURCE r) { return 0; }
    @Override public int     getMoveCapacityAm(StockpileInstance s, RESOURCE r) { return 0; }

    // ── Stockpile Write ────────────────────────────────────
    @Override public void setStoring(StockpileInstance g, boolean locked) { /* no-op */ }
    @Override public void setFetching(StockpileInstance s, boolean enabled) { /* no-op */ }
    @Override public void setSpecialAmount(StockpileInstance s, RESOURCE r, int a) { /* no-op */ }

    // ── Transport Read ─────────────────────────────────────
    @Override public double  getDistance(RoomInstance s) { return -1.0; }
    @Override public double  getEfficiency(RoomInstance s) { return 0.0; }
    @Override public float   getFetchTime(RoomInstance s) { return 0f; }
    @Override public float   getStationWorkers(RoomInstance s) { return 0f; }
    @Override public boolean hasStationProblem(RoomInstance s) { return false; }
    @Override public RESOURCE getTransportResource(RoomInstance s) { return null; }
    @Override public byte    getRadiusRaw(RoomInstance s) { return 0; }
    @Override public void    setRadiusRaw(RoomInstance s, byte r) { /* no-op */ }

    // ── Room Iteration ─────────────────────────────────────
    @Override public boolean entitiesAvailable() { return true; }
    // Iteration methods return null — the stub world has no rooms. Callers
    // must null-check; no vanilla-entity loading happens here.
    @Override public LIST<RoomBlueprintImp>    getRoomImps()   { return null; }
    @Override public LIST<RoomBlueprintIns<?>> getRoomIns()    { return null; }
    @Override public LIST<ROOM_EATERY>         getEateries()  { return null; }
    @Override public LIST<ROOM_CANTEEN>        getCanteens()  { return null; }
    @Override public ROOM_HOME                getHome()      { return null; }
    @Override public ROOM_CHAMBER             getChamber()   { return null; }
    @Override public ROOM_JANITOR             getJanitor()   { return null; }
    @Override public Object                  getStation()   { return null; }
    @Override public long                    getStationTally(RESOURCE r) { return 0L; }

    // ── Service & Employment ───────────────────────────────
    @Override public ServiceCapacity getServiceCapacity(RoomService service) {
        return new ServiceCapacity(0, 0, 0.0);
    }
    @Override public void setFirmTarget(RoomInstance firm, int target) { /* no-op */ }
    @Override public RoomInstance getEmployedRoom(Humanoid humanoid) { return null; }

}
