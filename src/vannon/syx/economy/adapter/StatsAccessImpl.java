package vannon.syx.economy.adapter;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import game.boosting.BOOSTABLES;
import game.boosting.Boostable;
import game.time.TIME;
import game.time.TIMECYCLE;
import init.religion.RELIGIONS;
import init.religion.Religion;
import snake2d.util.sets.LIST;
import vannon.syx.economy.adapter.seam.BypassGate;
import vannon.syx.economy.adapter.seam.FieldAccessor;
import vannon.syx.economy.core.EngineLevers;
import vannon.syx.economy.core.EventLog;
import vannon.syx.economy.core.LoggingAdapter;

/**
 * V71.44-Implementierung von {@link IStatsAccess}.
 *
 * <p>Hybride Architektur:
 * <ul>
 *   <li><b>Time/Religion</b>: direkte Compilezeit-Links auf {@code TIME},
 *       {@code TIMECYCLE}, {@code RELIGIONS} (alle public).</li>
 *   <li><b>BOOSTABLES</b>: {@link BypassGate} für private Felder auf
 *       {@code BOOSTABLES.CIVICS()} und {@code BOOSTABLES.BEHAVIOUR()}.</li>
 * </ul></p>
 */
public final class StatsAccessImpl implements IStatsAccess {

    private static final ClassLoader GAME_CL;
    static {
        ClassLoader cl = RELIGIONS.class.getClassLoader();
        GAME_CL = cl != null ? cl : ClassLoader.getSystemClassLoader();
    }

    // ─── BOOSTABLES Field Accessors ────────────────────────
    private final Map<String, FieldAccessor.RefField<Boostable>> civicFields;
    private final Map<String, FieldAccessor.RefField<Boostable>> behaviourFields;
    private final Object civicsInstance;
    private final Object behaviourInstance;

    // ─── Status ─────────────────────────────────────────────
    private final boolean initOk;
    private final Set<String> failedMethods = Collections.synchronizedSet(new HashSet<>());

    // ─── Constructor ────────────────────────────────────────

    public StatsAccessImpl() {
        BypassGate gate = new BypassGate("StatsAccessImpl", MethodHandles.lookup());

        Map<String, FieldAccessor.RefField<Boostable>> cFields = new HashMap<>();
        Map<String, FieldAccessor.RefField<Boostable>> bFields = new HashMap<>();
        Object cInstance = null;
        Object bInstance = null;

        // ── CIVICS ──
        try {
            cInstance = BOOSTABLES.CIVICS();
            if (cInstance != null) {
                for (String name : new String[]{"GOV", "SPOILAGE", "MAINTENANCE", "IMMIGRATION"}) {
                    try {
                        FieldAccessor.RefField<Boostable> f =
                                gate.refField(cInstance.getClass(), name, Boostable.class);
                        if (gate.isAvailable() && f.get(cInstance) != null) {
                            cFields.put(name, f);
                        }
                    } catch (Throwable t) {
                        // Field not available — skip
                    }
                }
            }
        } catch (Throwable t) {
            EventLog.log("SEAM", "StatsAccessImpl: CIVICS init failed — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        // ── BEHAVIOUR ──
        try {
            bInstance = BOOSTABLES.BEHAVIOUR();
            if (bInstance != null) {
                for (String name : new String[]{"LOYALTY", "HAPPI"}) {
                    try {
                        FieldAccessor.RefField<Boostable> f =
                                gate.refField(bInstance.getClass(), name, Boostable.class);
                        if (gate.isAvailable() && f.get(bInstance) != null) {
                            bFields.put(name, f);
                        }
                    } catch (Throwable t) {
                        // Field not available — skip
                    }
                }
            }
        } catch (Throwable t) {
            EventLog.log("SEAM", "StatsAccessImpl: BEHAVIOUR init failed — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
        }

        this.civicFields = Collections.unmodifiableMap(cFields);
        this.behaviourFields = Collections.unmodifiableMap(bFields);
        this.civicsInstance = cInstance;
        this.behaviourInstance = bInstance;
        this.initOk = (cInstance != null || bInstance != null);

        EventLog.log("SEAM", "StatsAccessImpl: READY (CIVICS="
                + cFields.size() + " fields, BEHAVIOUR=" + bFields.size()
                + " fields, Time+Religion direct)");
    }

    // ═══ IStatsAccess Implementation ═══════════════════════

    @Override
    public boolean isAvailable() {
        return initOk;
    }

    // ─── Time ───────────────────────────────────────────────

    @Override
    public double getWorkCycleSeconds() {
        if (!canAccess("workCycleSeconds", EngineLevers.timeEnabled)) return -1.0;
        try {
            double v = Math.max(1.0, TIME.workSeconds());
            trace("workCycleSeconds", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("workCycleSeconds", t, -1.0);
        }
    }

    @Override
    public double getGameSecondsSinceStart() {
        if (!canAccess("gameSeconds", EngineLevers.timeEnabled)) return -1.0;
        try {
            TIMECYCLE.Days days = TIME.days();
            double v = (double) days.bitsSinceStart() * days.bitSeconds()
                    + days.secondOfBit();
            trace("gameSeconds", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("gameSeconds", t, -1.0);
        }
    }

    // ─── Religion Stats ─────────────────────────────────────

    @Override
    public int getReligionCount() {
        if (!canAccess("religionCount", EngineLevers.religionStatsEnabled)) return 0;
        try {
            int v = RELIGIONS.ALL().size();
            trace("religionCount", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("religionCount", t, 0);
        }
    }

    @Override
    public CharSequence getReligionName(int index) {
        if (!canAccess("religionName", EngineLevers.religionStatsEnabled)) return "?";
        try {
            LIST<Religion> all = RELIGIONS.ALL();
            if (index >= 0 && index < all.size()) {
                CharSequence v = all.get(index).info.name;
                trace("religionName", v != null ? v.toString() : "?", "idx=" + index);
                return v;
            }
            return "?";
        } catch (Throwable t) {
            return fail("religionName", t, "?");
        }
    }

    // ─── BOOSTABLES ────────────────────────────────────────

    @Override
    public Boostable getCivicGov() {
        if (!canAccess("civicGov", EngineLevers.boostingGovEnabled)) return null;
        return getCivicBoostable("GOV");
    }

    @Override
    public Boostable getCivicBoostable(String fieldName) {
        boolean lever = switch (fieldName != null ? fieldName : "") {
            case "GOV" -> EngineLevers.boostingGovEnabled;
            case "SPOILAGE" -> EngineLevers.boostingSpoilageEnabled;
            case "MAINTENANCE" -> EngineLevers.boostingMaintenanceEnabled;
            case "IMMIGRATION" -> EngineLevers.boostingImmigrationEnabled;
            default -> EngineLevers.boostingGovEnabled;
        };
        if (!canAccess("civic_" + fieldName, lever)) return null;
        if (fieldName == null || civicsInstance == null) return null;
        try {
            FieldAccessor.RefField<Boostable> f = civicFields.get(fieldName);
            if (f == null) return null;
            Boostable v = f.get(civicsInstance);
            trace("civic_" + fieldName, v != null ? v.toString() : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("civic_" + fieldName, t, null);
        }
    }

    @Override
    public Boostable getBehaviourBoostable(String fieldName) {
        boolean lever = switch (fieldName != null ? fieldName : "") {
            case "LOYALTY" -> EngineLevers.boostingLoyaltyEnabled;
            case "HAPPI" -> EngineLevers.boostingHappinessEnabled;
            default -> EngineLevers.boostingLoyaltyEnabled;
        };
        if (!canAccess("behaviour_" + fieldName, lever)) return null;
        if (fieldName == null || behaviourInstance == null) return null;
        try {
            FieldAccessor.RefField<Boostable> f = behaviourFields.get(fieldName);
            if (f == null) return null;
            Boostable v = f.get(behaviourInstance);
            trace("behaviour_" + fieldName, v != null ? v.toString() : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("behaviour_" + fieldName, t, null);
        }
    }

    @Override
    public Object getCivicsInstance() {
        return civicsInstance;
    }

    @Override
    public Object getBehaviourInstance() {
        return behaviourInstance;
    }

    // ═══ Internal Helpers ═══════════════════════════════════

    private boolean canAccess(String method, boolean specificLever) {
        return EngineLevers.engineMirrorEnabled
                && EngineLevers.statsAccessEnabled
                && specificLever
                && !failedMethods.contains(method);
    }

    private void trace(String key, String value, String note) {
        if (EngineLevers.engineMirrorLoggingEnabled) {
            LoggingAdapter.csvTrace("MIRROR", "STATS", "TRACE", key, value, note);
        }
    }

    private <T> T fail(String method, Throwable t, T defaultValue) {
        if (failedMethods.add(method)) {
            EventLog.log("MIRROR", "StatsAccessImpl." + method + " failed — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + ". Method permanently disabled.");
            LoggingAdapter.csvTrace("MIRROR", "STATS", "ERROR", method,
                    t.getClass().getSimpleName(), t.getMessage());
        }
        return defaultValue;
    }
}
