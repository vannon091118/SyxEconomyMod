package vannon.syx.economy.adapter;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import init.type.HCLASSES;
import init.type.HTYPES;
import init.type.NEED;
import init.type.NEEDS;
import init.type.NEED_E;
import settlement.entity.humanoid.HPoll;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.HAI;
import settlement.room.main.RoomInstance;
import settlement.stats.STATS;
import settlement.stats.colls.StatsReligion;
import settlement.stats.service.StatService;
import settlement.stats.service.StatService;
import snake2d.util.sets.LIST;
import vannon.syx.economy.adapter.seam.BypassGate;
import vannon.syx.economy.adapter.seam.ClassResolver;
import vannon.syx.economy.core.EngineLevers;
import vannon.syx.economy.core.EventLog;
import vannon.syx.economy.core.LoggingAdapter;

/**
 * V71.44-Implementierung von {@link IHumanoidAccess}.
 *
 * <p>Hybride Architektur:
 * <ul>
 *   <li><b>Humanoid-Stat-APIs</b> (alle public): direkte Compilezeit-Links
 *       auf {@code NEEDS}, {@code STATS}, {@code HTYPES}, {@code HPoll},
 *       {@code RELIGIONS}. Kein BypassGate nötig.</li>
 *   <li><b>AI-Plan-Klassen</b> (alle package-private in
 *       {@code settlement.entity.humanoid.ai.work}): {@link ClassResolver}
 *       für Discovery, {@link java.lang.reflect.Constructor} für
 *       Instantiierung.</li>
 *   <li><b>AIManager.overwrite()</b> (public): direkter Compilezeit-Link.</li>
 * </ul></p>
 *
 * <p>Jeder Zugriff prüft {@link EngineLevers} vor der Ausführung und loggt
 * via {@link LoggingAdapter#csvTrace}. Fehler werden pro Methode protokolliert
 * und die Methode dauerhaft deaktiviert (kein Retry-Loop).</p>
 */
public final class HumanoidAccessImpl implements IHumanoidAccess {

    // ─── AI-Plan Class FQCNs (package-private) ──────────────
    private static final String AI_WORK_PKG = "settlement.entity.humanoid.ai.work.";
    private static final String[] PLAN_CLASS_NAMES = {
        AI_WORK_PKG + "PlanBlueprint",
        AI_WORK_PKG + "PlanWork",
        AI_WORK_PKG + "PlanFetchEquip",
        AI_WORK_PKG + "PlanHangArround",   // Vanilla typo — double 'r'
        AI_WORK_PKG + "PlanOddjobber",
        AI_WORK_PKG + "PlanOddHunt",
    };

    private static final ClassLoader GAME_CL;
    static {
        ClassLoader cl = Humanoid.class.getClassLoader();
        GAME_CL = cl != null ? cl : ClassLoader.getSystemClassLoader();
    }

    // ─── PlanCatalog (ClassResolver-backed) ─────────────────
    private final PlanCatalogImpl planCatalog;

    // ─── Status ─────────────────────────────────────────────
    private final boolean initOk;
    private final Set<String> failedMethods = Collections.synchronizedSet(new HashSet<>());

    // ─── Constructor ────────────────────────────────────────

    public HumanoidAccessImpl() {
        // ── Resolve AI-Plan classes via ClassResolver ──
        BypassGate gate = new BypassGate("HumanoidAccessImpl-Plans",
                MethodHandles.lookup());
        ClassResolver resolver = gate.classResolver(GAME_CL);

        Map<String, Class<?>> resolved = new HashMap<>();
        for (String fqcn : PLAN_CLASS_NAMES) {
            try {
                Class<?> clazz = resolver.resolve(fqcn);
                if (clazz != null) {
                    // Store by simple name (e.g. "PlanOddjobber")
                    String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
                    resolved.put(simple, clazz);
                }
            } catch (Throwable t) {
                EventLog.log("SEAM", "HumanoidAccessImpl: failed to resolve "
                        + fqcn + " — " + t.getClass().getSimpleName()
                        + ": " + t.getMessage());
            }
        }

        this.planCatalog = new PlanCatalogImpl(resolved);
        this.initOk = true;

        EventLog.log("SEAM", "HumanoidAccessImpl: READY ("
                + resolved.size() + "/" + PLAN_CLASS_NAMES.length
                + " AI-Plan classes resolved, ClassResolver="
                + resolver + ")");
    }

    // ═══ IHumanoidAccess Implementation ═════════════════════

    @Override
    public boolean isAvailable() {
        return initOk;
    }

    // ═══ Resident Enumeration (Sprint v0.13.129+ResidentImportFix) ═══

    /**
     * Vanilla-Pfad: {@link VanillaQueries#residentCount()} zentralisiert den
     * Songs-of-Syx-V71.44-Zugriff auf {@code SETT.ENTITIES().humans()} mit
     * mehrschichtigem graceful Fallback. Wenn die Engine nicht verfügbar
     * (Headless-Test / Pre-Init), return 0 (= "no census available").
     */
    @Override
    public int getResidentCount() {
        if (!canAccess("getResidentCount", EngineLevers.humanoidAccessEnabled)) return 0;
        try {
            int n = VanillaQueries.residentCount();
            trace("getResidentCount", String.valueOf(n), "");
            return n;
        } catch (Throwable t) {
            return fail("getResidentCount", t, 0);
        }
    }

    /**
     * Vanilla-Pfad: {@link VanillaQueries#forEachResident(Consumer)} iteriert
     * über die lebenden Bewohner. Eine einzelne Visitor-Exception darf nicht
     * die ganze Iteration abbrechen (graceful Skip-and-Continue-Semantik).
     */
    @Override
    public void forEachResident(Consumer<Humanoid> action) {
        if (!canAccess("forEachResident", EngineLevers.humanoidAccessEnabled)) return;
        if (action == null) return;
        try {
            VanillaQueries.forEachResident(action);
            trace("forEachResident", "ok", "");
        } catch (RuntimeException t) {
            failVoid("forEachResident", t);
        }
    }

    // ─── Employment & Labor ─────────────────────────────────

    @Override
    public boolean isWorking(Humanoid humanoid) {
        if (!canAccess("isWorking", EngineLevers.workStatusEnabled)) return false;
        if (humanoid == null) return false;
        try {
            boolean v = HPoll.Handler.works(humanoid);
            trace("isWorking", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("isWorking", t, false);
        }
    }

    @Override
    public boolean isEmployableWorker(Humanoid humanoid) {
        if (!canAccess("isEmployableWorker", EngineLevers.workStatusEnabled)) return false;
        if (humanoid == null) return false;
        try {
            boolean v = humanoid.indu().hType().isWorks()
                    && humanoid.indu().clas() != HCLASSES.SLAVE();
            trace("isEmployableWorker", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("isEmployableWorker", t, false);
        }
    }

    @Override
    public boolean isSurplusLaborer(Humanoid humanoid) {
        if (!canAccess("isSurplusLaborer", EngineLevers.workStatusEnabled)) return false;
        if (humanoid == null) return false;
        try {
            // Direct API calls — avoids double canAccess/trace from isEmployableWorker
            // + getEmployedRoom delegates.
            boolean employable = humanoid.indu().hType().isWorks()
                    && humanoid.indu().clas() != HCLASSES.SLAVE();
            boolean v = employable
                    && STATS.WORK().EMPLOYED.get(humanoid.indu()) == null;
            trace("isSurplusLaborer", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("isSurplusLaborer", t, false);
        }
    }

    @Override
    public RoomInstance getEmployedRoom(Humanoid humanoid) {
        if (!canAccess("employedRoom", EngineLevers.employmentAccessEnabled)) return null;
        if (humanoid == null) return null;
        try {
            RoomInstance v = (RoomInstance) STATS.WORK()
                    .EMPLOYED.get(humanoid.indu());
            trace("employedRoom", v != null ? "ok" : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("employedRoom", t, null);
        }
    }

    // ─── Hunger & Needs ─────────────────────────────────────

    @Override
    public int getHungerRaw(Humanoid humanoid) {
        if (!canAccess("hungerRaw", EngineLevers.hungerAccessEnabled)) return 0;
        if (humanoid == null) return 0;
        try {
            int v = NEEDS.TYPES().HUNGER.stat().stat().indu()
                    .get(humanoid.indu());
            trace("hungerRaw", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("hungerRaw", t, 0);
        }
    }

    @Override
    public void setHungerRaw(Humanoid humanoid, int value) {
        if (!canAccess("hungerRawSet", EngineLevers.hungerAccessEnabled)) return;
        if (humanoid == null) return;
        try {
            NEEDS.TYPES().HUNGER.stat().stat().indu()
                    .set(humanoid.indu(), value);
            trace("hungerRawSet", String.valueOf(value), "");
        } catch (Throwable t) {
            failVoid("hungerRawSet", t);
        }
    }

    @Override
    public int getEventNeedPriority(Humanoid humanoid, NEED need) {
        if (!canAccess("eventNeedPrio", EngineLevers.hungerAccessEnabled)) return -1;
        if (humanoid == null || need == null) return -1;
        try {
            if (!(need instanceof NEED_E)) {
                return -1;
            }
            int v = ((NEED_E) need).stat().getPrio(humanoid);
            trace("eventNeedPrio", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("eventNeedPrio", t, -1);
        }
    }

    // ─── Service Fulfilment ─────────────────────────────────

    @Override
    public double getServiceFulfilment(Humanoid humanoid, StatService service) {
        if (!canAccess("serviceFulfilment", EngineLevers.humanoidAccessEnabled)) return -1.0;
        if (humanoid == null || service == null) return -1.0;
        try {
            double v = service.total().indu().getD(humanoid.indu());
            trace("serviceFulfilment", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("serviceFulfilment", t, -1.0);
        }
    }

    // ─── Social Hierarchy ───────────────────────────────────

    @Override
    public Humanoid getLivingParent(Humanoid child) {
        if (!canAccess("livingParent", EngineLevers.humanoidAccessEnabled)) return null;
        if (child == null) return null;
        try {
            Humanoid v = STATS.REL().humanParent(child);
            trace("livingParent", v != null ? "ok" : "null", "");
            return v;
        } catch (Throwable t) {
            return fail("livingParent", t, null);
        }
    }

    // ─── Religion ───────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public int getReligionIndexOf(Humanoid humanoid) {
        if (!canAccess("religionIndexOf", EngineLevers.religionAccessEnabled)) return -1;
        if (humanoid == null) return -1;
        try {
            Object raw = STATS.RELIGION().getter.get(humanoid.indu());
            if (raw instanceof StatsReligion.StatReligion) {
                int v = ((StatsReligion.StatReligion) raw).religion.index();
                trace("religionIndexOf", String.valueOf(v), "");
                return v;
            }
            trace("religionIndexOf", "-1", "no stat");
            return -1;
        } catch (Throwable t) {
            return fail("religionIndexOf", t, -1);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void convertTo(Humanoid humanoid, int religionIndex) {
        if (!canAccess("convertTo", EngineLevers.religionAccessEnabled)) return;
        if (humanoid == null) return;
        try {
            LIST<?> all = STATS.RELIGION().ALL;
            for (int i = 0; i < all.size(); i++) {
                Object entry = all.get(i);
                if (entry instanceof StatsReligion.StatReligion) {
                    StatsReligion.StatReligion stat = (StatsReligion.StatReligion) entry;
                    if (stat.religion.index() == religionIndex) {
                        STATS.RELIGION().getter.set(humanoid.indu(), stat);
                        trace("convertTo", String.valueOf(religionIndex), "");
                        return;
                    }
                }
            }
            trace("convertTo", "notFound", "idx=" + religionIndex);
        } catch (Throwable t) {
            failVoid("convertTo", t);
        }
    }

    // ─── Slavery ────────────────────────────────────────────

    @Override
    public boolean isEnslaveablePleb(Humanoid humanoid) {
        if (!canAccess("isEnslaveablePleb", EngineLevers.slaveryAccessEnabled)) return false;
        if (humanoid == null) return false;
        try {
            boolean v = humanoid.indu().hType() == HTYPES.SUBJECT();
            trace("isEnslaveablePleb", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            return fail("isEnslaveablePleb", t, false);
        }
    }

    @Override
    public void enslave(Humanoid humanoid) {
        if (!canAccess("enslave", EngineLevers.slaveryAccessEnabled)) return;
        if (humanoid == null) return;
        try {
            humanoid.indu().hTypeSet(humanoid, HTYPES.SLAVE(), null, null);
            trace("enslave", "ok", "");
        } catch (Throwable t) {
            failVoid("enslave", t);
        }
    }

    // ─── AI Plan Management ─────────────────────────────────

    @Override
    public void overwritePlan(Humanoid humanoid, AIPLAN plan) {
        if (!canAccess("overwritePlan", EngineLevers.planAccessEnabled)) return;
        if (humanoid == null || plan == null) return;
        try {
            HAI hai = humanoid.ai();
            if (!(hai instanceof AIManager)) {
                EventLog.log("MIRROR", "HumanoidAccessImpl.overwritePlan: "
                        + "AI is not AIManager — " + hai.getClass().getSimpleName());
                return;
            }
            ((AIManager) hai).overwrite(humanoid, plan);
            trace("overwritePlan", "ok", plan.getClass().getSimpleName());
        } catch (Throwable t) {
            failVoid("overwritePlan", t);
        }
    }

    @Override
    public PlanCatalog planCatalog() {
        return planCatalog;
    }

    // ═══ PlanCatalog Implementation ═════════════════════════

    /**
     * ClassResolver-backed Discovery + Factory für package-private
     * AI-Plan-Klassen in {@code settlement.entity.humanoid.ai.work}.
     */
    private static final class PlanCatalogImpl implements PlanCatalog {

        private final Map<String, Class<?>> resolved;

        PlanCatalogImpl(Map<String, Class<?>> resolved) {
            this.resolved = Collections.unmodifiableMap(resolved);
        }

        @Override
        public int resolvedCount() {
            return resolved.size();
        }

        @Override
        public boolean isAvailable() {
            return !resolved.isEmpty();
        }

        @Override
        public Class<?> lookup(String simpleName) {
            if (simpleName == null) return null;
            return resolved.get(simpleName);
        }

        @Override
        @SuppressWarnings("unchecked")
        public AIPLAN create(String simpleName, String key) {
            Class<?> clazz = lookup(simpleName);
            if (clazz == null) {
                return null;
            }
            // Try Constructor(String key) first — PlanWork, PlanFetchEquip,
            // PlanHangArround, PlanOddjobber, PlanOddHunt all have this.
            try {
                Constructor<?> ctor = clazz.getDeclaredConstructor(String.class);
                ctor.setAccessible(true);
                return (AIPLAN) ctor.newInstance(key);
            } catch (NoSuchMethodException e) {
                // Fall through to no-arg constructor
            } catch (Throwable t) {
                EventLog.log("MIRROR", "PlanCatalog.create(" + simpleName
                        + ", " + key + ") failed — "
                        + t.getClass().getSimpleName() + ": " + t.getMessage());
                return null;
            }

            // Try no-arg constructor as fallback
            try {
                Constructor<?> ctor = clazz.getDeclaredConstructor();
                ctor.setAccessible(true);
                return (AIPLAN) ctor.newInstance();
            } catch (Throwable t) {
                EventLog.log("MIRROR", "PlanCatalog.create(" + simpleName
                        + ") no usable constructor — "
                        + t.getClass().getSimpleName() + ": " + t.getMessage());
                return null;
            }
        }

        @Override
        public String toString() {
            return "PlanCatalog{resolved=" + resolved.keySet() + "}";
        }
    }

    // ═══ Internal Helpers ═══════════════════════════════════

    /**
     * Prüft ob ein Zugriff erlaubt ist (EngineLevers + Master-Toggle + Failure-Set).
     */
    private boolean canAccess(String method, boolean specificLever) {
        return EngineLevers.engineMirrorEnabled
                && EngineLevers.humanoidAccessEnabled
                && specificLever
                && !failedMethods.contains(method);
    }

    /** Trace-Log via LoggingAdapter (nur wenn Logging aktiviert). */
    private void trace(String key, String value, String note) {
        if (EngineLevers.engineMirrorLoggingEnabled) {
            LoggingAdapter.csvTrace("MIRROR", "HUMANOID", "TRACE", key, value, note);
        }
    }

    /** Error-Handler für Read-Methoden: loggt, markiert als failed, gibt Default zurück. */
    private <T> T fail(String method, Throwable t, T defaultValue) {
        if (failedMethods.add(method)) {
            EventLog.log("MIRROR", "HumanoidAccessImpl." + method + " failed — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + ". Method permanently disabled.");
            LoggingAdapter.csvTrace("MIRROR", "HUMANOID", "ERROR", method,
                    t.getClass().getSimpleName(), t.getMessage());
        }
        return defaultValue;
    }

    /** Error-Handler für Write-Methoden: loggt, markiert als failed. */
    private void failVoid(String method, Throwable t) {
        if (failedMethods.add(method)) {
            EventLog.log("MIRROR", "HumanoidAccessImpl." + method + " failed — "
                    + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + ". Method permanently disabled.");
            LoggingAdapter.csvTrace("MIRROR", "HUMANOID", "ERROR", method,
                    t.getClass().getSimpleName(), t.getMessage());
        }
    }
}
