package vannon.syx.economy.adapter;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
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
import init.resources.RESOURCE;
import snake2d.util.sets.LIST;
import vannon.syx.economy.adapter.seam.BypassGate;
import vannon.syx.economy.adapter.seam.ClassResolver;
import vannon.syx.economy.adapter.seam.FieldAccessor;
import vannon.syx.economy.adapter.seam.MethodAccessor;
import vannon.syx.economy.core.EngineLevers;
import vannon.syx.economy.core.EventLog;
import vannon.syx.economy.core.LoggingAdapter;

/**
 * V71.44-Implementierung von {@link IRoomAccess}.
 *
 * <p>Hybride Architektur:
 * <ul>
 *   <li><b>StockpileInstance</b> (public): direkte Methodenaufrufe für Read,
 *       {@link BypassGate} für package-private Write-Methoden
 *       ({@code fetchingSet}, {@code setSpecialAmount}).</li>
 *   <li><b>TransportInstance</b> (package-private): {@link ClassResolver} für
 *       Klassen-Ladung, {@link BypassGate} für Feldzugriffe (fetchTime,
 *       stationWorkers), {@link Method} via Reflection für public Methoden
 *       (efficiency, resource, radiusRaw).</li>
 *   <li><b>Room-Iteration</b>: direkte Compilezeit-Links via {@code SETT.ROOMS()}.</li>
 * </ul></p>
 *
 * <p>Jeder Zugriff prüft {@link EngineLevers} vor der Ausführung und loggt
 * via {@link LoggingAdapter#csvTrace}. Fehler werden pro Methode protokolliert
 * und die Methode dauerhaft deaktiviert (kein Retry-Loop).</p>
 *
 * <p>Ersetzt graduell die room-bezogenen {@code EngineSeams}-Aufrufe (Task B-008).</p>
 */
public final class RoomAccessImpl implements IRoomAccess {

    private static final String TRANSPORT_INSTANCE_CLASS =
            "settlement.room.infra.transport.TransportInstance";

    private static final ClassLoader GAME_CL;
    static {
        ClassLoader cl = Humanoid.class.getClassLoader();
        GAME_CL = cl != null ? cl : ClassLoader.getSystemClassLoader();
    }

    // ─── Injected Adapters ──────────────────────────────────
    private final ISyxWarehouse warehouseAdapter;
    private final ISyxTransport transportAdapter;

    // ─── BypassGate: StockpileInstance private methods ───────
    private final MethodAccessor.VoidMethod fetchingSetMethod;
    private final MethodAccessor.VoidMethod setSpecialAmountMethod;

    // ─── Station tally() — cached Method objects (Rule 9 note: BypassGate SDK
    // has no generic-return MethodAccessor, only VoidMethod/BooleanMethod. Public
    // methods on runtime-resolved classes use cached java.lang.reflect.Method,
    // consistent with efficiencyMethod/resourceMethod/radiusRawMethod pattern.)
    private final java.lang.reflect.Method stationTallyCached;
    private final java.lang.reflect.Method stationAmountTotalCached;

    // ─── BypassGate: TransportInstance (package-private) ─────
    private final Class<?> transportInstanceClass;
    private final Method efficiencyMethod;
    private final Method resourceMethod;
    private final Method radiusRawMethod;
    private final Method radiusRawSetMethod;
    private final FieldAccessor.FloatField fetchTimeAccessor;
    private final FieldAccessor.FloatField stationWorkersAccessor;
    private final Field stationProblemField; // java.lang.reflect.Field for boolean

    // ─── Status ─────────────────────────────────────────────
    private final boolean initOk;
    private static volatile boolean engineUnavailable = false;
    private final Set<String> failedMethods = Collections.synchronizedSet(new HashSet<>());

    // ─── Constructor ────────────────────────────────────────

    /**
     * Erzeugt eine neue RoomAccessImpl.
     *
     * @param warehouseAdapter bestehender ISyxWarehouse-Adapter (injected)
     * @param transportAdapter bestehender ISyxTransport-Adapter (injected)
     */
    public RoomAccessImpl(ISyxWarehouse warehouseAdapter, ISyxTransport transportAdapter) {
        this.warehouseAdapter = warehouseAdapter;
        this.transportAdapter = transportAdapter;

        // ── StockpileInstance private methods via BypassGate ──
        BypassGate stockpileGate = new BypassGate("RoomAccessImpl-Stockpile",
                MethodHandles.lookup());

        MethodAccessor.VoidMethod fs = null;
        try {
            fs = stockpileGate.voidMethod(StockpileInstance.class,
                    "fetchingSet", boolean.class);
        } catch (Throwable t) {
            EventLog.log("SEAM", "RoomAccessImpl: fetchingSet not available — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        this.fetchingSetMethod = fs;

        // ── Station tally() — cache Method objects in constructor ──
        java.lang.reflect.Method stTally = null;
        java.lang.reflect.Method stAmount = null;
        try {
            Object stationInstance = SETT.ROOMS() == null ? null : SETT.ROOMS().STATION;
            if (stationInstance != null) {
                stTally = stationInstance.getClass().getMethod("tally");
                // tally() returns a RoomTally-like object with amountTotal(RESOURCE)
                // Resolve amountTotal on the return type
                Class<?> tallyType = stTally.getReturnType();
                stAmount = tallyType.getMethod("amountTotal", RESOURCE.class);
            }
        } catch (Throwable t) {
            EventLog.log("SEAM", "RoomAccessImpl: Station tally() not available — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        this.stationTallyCached = stTally;
        this.stationAmountTotalCached = stAmount;

        MethodAccessor.VoidMethod ssa = null;
        try {
            ssa = stockpileGate.voidMethod(StockpileInstance.class,
                    "setSpecialAmount", RESOURCE.class, int.class);
        } catch (Throwable t) {
            EventLog.log("SEAM", "RoomAccessImpl: setSpecialAmount not available — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        this.setSpecialAmountMethod = ssa;

        // ── TransportInstance (package-private) via ClassResolver ──
        BypassGate transportGate = new BypassGate("RoomAccessImpl-Transport",
                MethodHandles.lookup());
        ClassResolver resolver = transportGate.classResolver(GAME_CL);

        Class<?> tiClass = null;
        Method effMethod = null;
        Method resMethod = null;
        Method rrMethod = null;
        Method rrsMethod = null;
        FieldAccessor.FloatField ftAcc = null;
        FieldAccessor.FloatField swAcc = null;
        Field spField = null;

        try {
            tiClass = resolver.resolve(TRANSPORT_INSTANCE_CLASS);
            effMethod = tiClass.getMethod("efficiency");
            resMethod = tiClass.getMethod("resource");
            rrMethod = tiClass.getMethod("radiusRaw");
            rrsMethod = tiClass.getMethod("radiusRawSet", byte.class);
            ftAcc = transportGate.floatField(tiClass, "fetchTime");
            swAcc = transportGate.floatField(tiClass, "stationWorkers");

            // stationProblem is boolean — BypassGate has no booleanField,
            // use raw reflection
            spField = tiClass.getDeclaredField("stationProblem");
            spField.setAccessible(true);
        } catch (Throwable t) {
            EventLog.log("SEAM", "RoomAccessImpl: TransportInstance access "
                    + "partially failed — " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
        }

        this.transportInstanceClass = tiClass;
        this.efficiencyMethod = effMethod;
        this.resourceMethod = resMethod;
        this.radiusRawMethod = rrMethod;
        this.radiusRawSetMethod = rrsMethod;
        this.fetchTimeAccessor = ftAcc;
        this.stationWorkersAccessor = swAcc;
        this.stationProblemField = spField;

        // initOk: at least TransportInstance resolved + StockpileInstance works
        this.initOk = (tiClass != null);

        if (this.initOk) {
            EventLog.log("SEAM", "RoomAccessImpl: READY (TransportInstance via "
                    + "ClassResolver + BypassGate, StockpileInstance direct)");
        } else {
            EventLog.log("SEAM", "RoomAccessImpl: DEGRADED (TransportInstance "
                    + "not available — transport methods return defaults)");
        }
    }

    // ═══ IRoomAccess Implementation ═════════════════════════

    @Override
    public boolean isAvailable() {
        return initOk;
    }

    // ─── Stockpile Read ─────────────────────────────────────

    @Override
    public double getStoredRatio(StockpileInstance stockpile, RESOURCE res) {
        if (!canAccess("stockpile_storedD", EngineLevers.stockpileStoredDEnabled,
                EngineLevers.stockpileAccessEnabled)) return 1.0;
        if (stockpile == null) return 1.0;
        try {
            double v = stockpile.storedD(res);
            trace("stockpile_storedD", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("stockpile_storedD", t, 1.0);
        }
    }

    @Override
    public double getUsedSpace(StockpileInstance stockpile) {
        if (!canAccess("stockpile_usedSpace", EngineLevers.stockpileUsedSpaceEnabled,
                EngineLevers.stockpileAccessEnabled)) return 0.0;
        if (stockpile == null) return 0.0;
        try {
            double v = stockpile.getUsedSpace();
            trace("stockpile_usedSpace", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("stockpile_usedSpace", t, 0.0);
        }
    }

    @Override
    public int getCrateSize(StockpileInstance stockpile) {
        if (!canAccess("stockpile_crateSize", EngineLevers.stockpileCrateSizeEnabled,
                EngineLevers.stockpileAccessEnabled)) return 0;
        if (stockpile == null) return 0;
        try {
            int v = stockpile.crateSize();
            trace("stockpile_crateSize", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("stockpile_crateSize", t, 0);
        }
    }

    @Override
    public int getCrateSize(StockpileInstance stockpile, RESOURCE res) {
        if (!canAccess("stockpile_crateSize_res", EngineLevers.stockpileCrateSizeEnabled,
                EngineLevers.stockpileAccessEnabled)) return 0;
        if (stockpile == null) return 0;
        try {
            int v = stockpile.crateSize(res);
            trace("stockpile_crateSize", String.valueOf(v),
                    res != null ? res.key : "null");
            return v;
        } catch (Throwable t) {
            return fail("stockpile_crateSize_res", t, 0);
        }
    }

    @Override
    public int getTotalCrates(StockpileInstance stockpile) {
        if (!canAccess("stockpile_totalCrates", EngineLevers.stockpileTotalCratesEnabled,
                EngineLevers.stockpileAccessEnabled)) return 0;
        if (stockpile == null) return 0;
        try {
            int v = stockpile.totalCrates();
            trace("stockpile_totalCrates", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("stockpile_totalCrates", t, 0);
        }
    }

    @Override
    public int getSpecialAmount(StockpileInstance stockpile, RESOURCE res) {
        if (!canAccess("stockpile_specialAmount", EngineLevers.stockpileSpecialAmountReadEnabled,
                EngineLevers.stockpileAccessEnabled)) return 0;
        if (stockpile == null || res == null) return 0;
        try {
            int v = stockpile.getSpecialAmount(res);
            trace("stockpile_specialAmount", String.valueOf(v), res.key);
            return v;
        } catch (Throwable t) {
            return fail("stockpile_specialAmount", t, 0);
        }
    }

    @Override
    public int getMoveCapacityAm(StockpileInstance stockpile, RESOURCE res) {
        if (!canAccess("stockpile_moveCapacityAm",
                EngineLevers.stockpileMoveCapacityAmEnabled,
                EngineLevers.stockpileAccessEnabled)) return 0;
        if (stockpile == null || res == null) return 0;
        try {
            int v = stockpile.moveCapacityAm(res);
            trace("stockpile_moveCapacityAm", String.valueOf(v), res.key);
            return v;
        } catch (Throwable t) {
            return fail("stockpile_moveCapacityAm", t, 0);
        }
    }

    // ─── Stockpile Write ────────────────────────────────────

    @Override
    public void setStoring(StockpileInstance granary, boolean locked) {
        if (!canAccess("stockpile_storingSet", EngineLevers.stockpileStoringSetEnabled,
                EngineLevers.stockpileAccessEnabled)) return;
        if (granary == null) return;
        try {
            warehouseAdapter.setStoring(granary, locked);
            trace("stockpile_storingSet", String.valueOf(locked), "");
        } catch (Throwable t) {
            failVoid("stockpile_storingSet", t);
        }
    }

    @Override
    public void setFetching(StockpileInstance stockpile, boolean enabled) {
        if (!canAccess("stockpile_fetchingSet", EngineLevers.stockpileFetchingSetEnabled,
                EngineLevers.stockpileAccessEnabled)) return;
        if (stockpile == null || fetchingSetMethod == null) return;
        try {
            fetchingSetMethod.invoke(stockpile, enabled);
            trace("stockpile_fetchingSet", String.valueOf(enabled), "");
        } catch (Throwable t) {
            failVoid("stockpile_fetchingSet", t);
        }
    }

    @Override
    public void setSpecialAmount(StockpileInstance stockpile, RESOURCE res, int amount) {
        if (!canAccess("stockpile_setSpecialAmount",
                EngineLevers.stockpileSetSpecialAmountEnabled,
                EngineLevers.stockpileAccessEnabled)) return;
        if (stockpile == null || res == null || setSpecialAmountMethod == null) return;
        try {
            setSpecialAmountMethod.invoke(stockpile, res, amount);
            trace("stockpile_setSpecialAmount", String.valueOf(amount), res.key);
        } catch (Throwable t) {
            failVoid("stockpile_setSpecialAmount", t);
        }
    }

    // ─── Transport Read ─────────────────────────────────────

    @Override
    public double getDistance(RoomInstance loadingStation) {
        if (!canAccess("transport_distance", EngineLevers.transportDistanceEnabled,
                EngineLevers.transportAccessEnabled)) return -1.0;
        if (loadingStation == null) return -1.0;
        try {
            double d = transportAdapter.getReflectedDistance(loadingStation);
            trace("transport_distance", String.valueOf(d), "");
            return d;
        } catch (Throwable t) {
            return fail("transport_distance", t, -1.0);
        }
    }

    @Override
    public double getEfficiency(RoomInstance loadingStation) {
        if (!canAccess("transport_efficiency", EngineLevers.transportEfficiencyEnabled,
                EngineLevers.transportAccessEnabled)) return 0.0;
        if (loadingStation == null || efficiencyMethod == null) return 0.0;
        try {
            double v = (double) efficiencyMethod.invoke(loadingStation);
            trace("transport_efficiency", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("transport_efficiency", t, 0.0);
        }
    }

    @Override
    public float getFetchTime(RoomInstance loadingStation) {
        if (!canAccess("transport_fetchTime", EngineLevers.transportFetchTimeEnabled,
                EngineLevers.transportAccessEnabled)) return 0.0f;
        if (loadingStation == null || fetchTimeAccessor == null) return 0.0f;
        try {
            float v = fetchTimeAccessor.get(loadingStation);
            trace("transport_fetchTime", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("transport_fetchTime", t, 0.0f);
        }
    }

    @Override
    public float getStationWorkers(RoomInstance loadingStation) {
        if (!canAccess("transport_stationWorkers",
                EngineLevers.transportStationWorkersEnabled,
                EngineLevers.transportAccessEnabled)) return 0.0f;
        if (loadingStation == null || stationWorkersAccessor == null) return 0.0f;
        try {
            float v = stationWorkersAccessor.get(loadingStation);
            trace("transport_stationWorkers", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("transport_stationWorkers", t, 0.0f);
        }
    }

    @Override
    public boolean hasStationProblem(RoomInstance loadingStation) {
        if (!canAccess("transport_stationProblem",
                EngineLevers.transportStationProblemEnabled,
                EngineLevers.transportAccessEnabled)) return false;
        if (loadingStation == null || stationProblemField == null) return false;
        try {
            boolean v = (boolean) stationProblemField.get(loadingStation);
            trace("transport_stationProblem", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("transport_stationProblem", t, false);
        }
    }

    @Override
    public RESOURCE getTransportResource(RoomInstance loadingStation) {
        if (!canAccess("transport_resource", EngineLevers.transportResourceEnabled,
                EngineLevers.transportAccessEnabled)) return null;
        if (loadingStation == null || resourceMethod == null) return null;
        try {
            RESOURCE v = (RESOURCE) resourceMethod.invoke(loadingStation);
            trace("transport_resource", v != null ? v.key : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("transport_resource", t, null);
        }
    }

    @Override
    public byte getRadiusRaw(RoomInstance loadingStation) {
        if (!canAccess("transport_radiusRaw", EngineLevers.transportRadiusRawEnabled,
                EngineLevers.transportAccessEnabled)) return 0;
        if (loadingStation == null || radiusRawMethod == null) return 0;
        try {
            byte v = (byte) radiusRawMethod.invoke(loadingStation);
            trace("transport_radiusRaw", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("transport_radiusRaw", t, (byte) 0);
        }
    }

    // ─── Transport Write ────────────────────────────────────

    @Override
    public void setRadiusRaw(RoomInstance loadingStation, byte radius) {
        if (!canAccess("transport_radiusRawSet", EngineLevers.transportRadiusRawSetEnabled,
                EngineLevers.transportAccessEnabled)) return;
        if (loadingStation == null || radiusRawSetMethod == null) return;
        try {
            radiusRawSetMethod.invoke(loadingStation, radius);
            trace("transport_radiusRawSet", String.valueOf(radius), "");
        } catch (Throwable t) {
            failVoid("transport_radiusRawSet", t);
        }
    }

    // ─── Room Iteration ─────────────────────────────────────

    @Override
    public boolean entitiesAvailable() {
        if (!canAccess("entities_available", EngineLevers.entitiesAvailableEnabled,
                EngineLevers.roomIterationEnabled)) return false;
        if (engineUnavailable) return false;
        try {
            boolean v = SETT.ENTITIES() != null;
            trace("entities_available", String.valueOf(v), "");
            return v;
        } catch (LinkageError e) {
            engineUnavailable = true;
            return fail("entities_available", e, false);
        }
    }

    @Override
    public LIST<RoomBlueprintImp> getRoomImps() {
        if (!canAccess("room_imps", EngineLevers.roomInsEnabled,
                EngineLevers.roomIterationEnabled)) return null;
        try {
            LIST<RoomBlueprintImp> v = SETT.ROOMS() == null ? null
                    : SETT.ROOMS().imps();
            trace("room_imps", v != null ? String.valueOf(v.size()) : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("room_imps", t, null);
        }
    }

    @Override
    public LIST<RoomBlueprintIns<?>> getRoomIns() {
        if (!canAccess("room_ins", EngineLevers.roomInsEnabled,
                EngineLevers.roomIterationEnabled)) return null;
        if (engineUnavailable) return null;
        try {
            LIST<RoomBlueprintIns<?>> v = SETT.ROOMS() == null ? null
                    : SETT.ROOMS().ins();
            trace("room_ins", v != null ? String.valueOf(v.size()) : "null", "");
            return v;
        } catch (LinkageError e) {
            engineUnavailable = true;
            return fail("room_ins", e, null);
        }
    }

    @Override
    public LIST<ROOM_EATERY> getEateries() {
        if (!canAccess("room_eateries", EngineLevers.roomEateriesEnabled,
                EngineLevers.roomIterationEnabled)) return null;
        try {
            LIST<ROOM_EATERY> v = SETT.ROOMS() == null ? null
                    : SETT.ROOMS().EATERIES;
            trace("room_eateries", v != null ? String.valueOf(v.size()) : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("room_eateries", t, null);
        }
    }

    @Override
    public LIST<ROOM_CANTEEN> getCanteens() {
        if (!canAccess("room_canteens", EngineLevers.roomCanteensEnabled,
                EngineLevers.roomIterationEnabled)) return null;
        try {
            LIST<ROOM_CANTEEN> v = SETT.ROOMS() == null ? null
                    : SETT.ROOMS().CANTEENS;
            trace("room_canteens", v != null ? String.valueOf(v.size()) : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("room_canteens", t, null);
        }
    }

    @Override
    public ROOM_HOME getHome() {
        if (!canAccess("room_home", EngineLevers.roomHomeEnabled,
                EngineLevers.roomIterationEnabled)) return null;
        try {
            ROOM_HOME v = SETT.ROOMS() == null ? null : SETT.ROOMS().HOME;
            trace("room_home", v != null ? "ok" : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("room_home", t, null);
        }
    }

    @Override
    public ROOM_CHAMBER getChamber() {
        if (!canAccess("room_chamber", EngineLevers.roomChamberEnabled,
                EngineLevers.roomIterationEnabled)) return null;
        try {
            ROOM_CHAMBER v = SETT.ROOMS() == null ? null : SETT.ROOMS().CHAMBER;
            trace("room_chamber", v != null ? "ok" : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("room_chamber", t, null);
        }
    }

    @Override
    public ROOM_JANITOR getJanitor() {
        if (!canAccess("room_janitor", EngineLevers.roomJanitorEnabled,
                EngineLevers.roomIterationEnabled)) return null;
        try {
            ROOM_JANITOR v = SETT.ROOMS() == null ? null : SETT.ROOMS().JANITOR;
            trace("room_janitor", v != null ? "ok" : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("room_janitor", t, null);
        }
    }

    // ─── Service & Employment ───────────────────────────────

    @Override
    public ServiceCapacity getServiceCapacity(RoomService service) {
        if (!canAccess("service_capacity", EngineLevers.serviceCapacityEnabled,
                EngineLevers.serviceEmploymentEnabled)) return null;
        if (service == null) return null;
        try {
            ServiceCapacity v = new ServiceCapacity(
                    service.total(), service.available(), service.load());
            trace("service_capacity", v.total() + "/" + v.available(), "");
            return v;
        } catch (Throwable t) {
            return fail("service_capacity", t, null);
        }
    }

    @Override
    public void setFirmTarget(RoomInstance firm, int target) {
        if (!canAccess("set_firm_target", EngineLevers.setFirmTargetEnabled,
                EngineLevers.serviceEmploymentEnabled)) return;
        if (firm == null) return;
        try {
            if (firm.employees() == null) {
                EventLog.log("MIRROR", "RoomAccessImpl.setFirmTarget: room has "
                        + "no employment module");
                return;
            }
            firm.employees().neededSet(target);
            trace("set_firm_target", String.valueOf(target), "");
        } catch (Throwable t) {
            failVoid("set_firm_target", t);
        }
    }

    // ─── Station ─────────────────────────────────────────────

    @Override
    public Object getStation() {
        if (!canAccess("room_station", EngineLevers.roomStationEnabled,
                EngineLevers.roomIterationEnabled)) return null;
        try {
            Object v = SETT.ROOMS() == null ? null : SETT.ROOMS().STATION;
            trace("room_station", v != null ? "ok" : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("room_station", t, null);
        }
    }

    @Override
    public long getStationTally(RESOURCE res) {
        if (!canAccess("station_tally", EngineLevers.roomStationEnabled,
                EngineLevers.roomIterationEnabled)) return 0L;
        if (res == null || stationTallyCached == null || stationAmountTotalCached == null) return 0L;
        try {
            Object station = SETT.ROOMS() == null ? null : SETT.ROOMS().STATION;
            if (station == null) return 0L;
            Object tally = stationTallyCached.invoke(station);
            if (tally == null) return 0L;
            Object result = stationAmountTotalCached.invoke(tally, res);
            long v = result instanceof Number ? ((Number) result).longValue() : 0L;
            trace("station_tally", String.valueOf(v), res.key);
            return v;
        } catch (Throwable t) {
            return fail("station_tally", t, 0L);
        }
    }

    @Override
    public RoomInstance getEmployedRoom(Humanoid humanoid) {
        if (!canAccess("employed_room", EngineLevers.employedRoomEnabled,
                EngineLevers.serviceEmploymentEnabled)) return null;
        if (humanoid == null) return null;
        try {
            // Uses STATS.WORK().EMPLOYED — pre-v0.13.119 als EngineSeams.employedRoom() statisch exposed
            RoomInstance v = (RoomInstance) settlement.stats.STATS.WORK()
                    .EMPLOYED.get(humanoid.indu());
            trace("employed_room", v != null ? "ok" : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("employed_room", t, null);
        }
    }

    // ═══ Internal Helpers ═══════════════════════════════════

    /**
     * Prüft ob ein Zugriff erlaubt ist (EngineLevers + Master-Toggles + Failure-Set).
     */
    private boolean canAccess(String method, boolean specificLever, boolean subMasterLever) {
        return EngineLevers.engineMirrorEnabled
                && EngineLevers.roomAccessEnabled
                && subMasterLever
                && specificLever
                && !failedMethods.contains(method);
    }

    /** Trace-Log via LoggingAdapter (nur wenn Logging aktiviert). */
    private void trace(String key, String value, String note) {
        if (EngineLevers.engineMirrorLoggingEnabled) {
            LoggingAdapter.csvTrace("MIRROR", "ROOM", "TRACE", key, value, note);
        }
    }

    /** Error-Handler für Read-Methoden: loggt, markiert als failed, gibt Default zurück. */
    private <T> T fail(String method, Throwable t, T defaultValue) {
        if (failedMethods.add(method)) {
            EventLog.log("MIRROR", "RoomAccessImpl." + method + " failed — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + ". Method permanently disabled.");
            LoggingAdapter.csvTrace("MIRROR", "ROOM", "ERROR", method,
                    t.getClass().getSimpleName(), t.getMessage());
        }
        return defaultValue;
    }

    /** Error-Handler für Write-Methoden: loggt, markiert als failed. */
    private void failVoid(String method, Throwable t) {
        if (failedMethods.add(method)) {
            EventLog.log("MIRROR", "RoomAccessImpl." + method + " failed — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + ". Method permanently disabled.");
            LoggingAdapter.csvTrace("MIRROR", "ROOM", "ERROR", method,
                    t.getClass().getSimpleName(), t.getMessage());
        }
    }
}
