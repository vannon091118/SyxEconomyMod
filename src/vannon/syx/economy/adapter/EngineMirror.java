package vannon.syx.economy.adapter;

import vannon.syx.economy.core.EngineLevers;
import vannon.syx.economy.core.EventLog;
import vannon.syx.economy.core.LoggingAdapter;

/**
 * EngineMirror — Zentrale Fassade für ALLE Vanilla-Engine-Zugriffe.
 *
 * <p>Singleton-Zugriff via {@code EngineMirror.api()}. Bündelt die 4
 * Sub-Interfaces des EngineAccess-Systems:</p>
 * <ul>
 *   <li>{@code .rooms()}      → {@link IRoomAccess} (30 Zugriffe)</li>
 *   <li>{@code .factions()}   → {@link IFactionAccess} (28 Zugriffe)</li>
 *   <li>{@code .humanoids()}  → {@link IHumanoidAccess} (18 + PlanCatalog)</li>
 *   <li>{@code .stats()}      → {@link IStatsAccess} (Time, Religion, BOOSTABLES)</li>
 * </ul>
 *
 * <p>Initialisierung via {@link #init(AdapterDispatcher.AdapterBundle)} — wird
 * von {@link AdapterDispatcher#build()} aufgerufen. Kein Lazy-Init, da die
 * Adapter-Bundle-Initialisierung deterministisch sein muss.</p>
 *
 * <p>Jeder Zugriff wird über {@link EngineLevers} getogglet und via
 * {@link LoggingAdapter} geloggt. Bei Vanilla-Update (V71→V72) können
 * einzelne Sub-Interfaces per EngineLevers deaktiviert werden.</p>
 *
 * <pre>
 * EngineMirror.api()
 *   ├── .rooms()      → IRoomAccess      (BypassGate hybrid)
 *   ├── .factions()   → IFactionAccess   (kein Reflection)
 *   ├── .humanoids()  → IHumanoidAccess  (ClassResolver für AI-Plans)
 *   └── .stats()      → IStatsAccess     (BypassGate für BOOSTABLES)
 * </pre>
 */
public final class EngineMirror {

    // ─── Singleton ──────────────────────────────────────────
    private static volatile EngineMirror instance;

    // ─── Sub-Interfaces ─────────────────────────────────────
    private final IRoomAccess rooms;
    private final IFactionAccess factions;
    private final IHumanoidAccess humanoids;
    private final IStatsAccess stats;

    // ─── Status ─────────────────────────────────────────────
    private final boolean initOk;
    private final long initTimestamp;

    // ─── Private Constructor ────────────────────────────────

    private EngineMirror(IRoomAccess rooms, IFactionAccess factions,
                         IHumanoidAccess humanoids, IStatsAccess stats) {
        this.rooms = rooms;
        this.factions = factions;
        this.humanoids = humanoids;
        this.stats = stats;
        this.initOk = (rooms != null && factions != null
                && humanoids != null && stats != null);
        this.initTimestamp = System.currentTimeMillis();
    }

    // ═══ Public API ═════════════════════════════════════════

    /**
     * Liefert die Singleton-Instanz des EngineMirror.
     *
     * @return die Instanz oder null wenn nicht initialisiert
     */
    public static EngineMirror api() {
        return instance;
    }

    /**
     * Initialisiert den EngineMirror mit den gebauten Sub-Interfaces.
     * Wird von {@link AdapterDispatcher#build()} aufgerufen.
     *
     * <p>Darf nur einmal aufgerufen werden — zweiter Aufruf wird geloggt
     * und ignoriert.</p>
     */
    public static void init(IRoomAccess rooms, IFactionAccess factions,
                            IHumanoidAccess humanoids, IStatsAccess stats) {
        if (instance != null) {
            EventLog.log("MIRROR", "EngineMirror.init() called twice — "
                    + "ignoring. Existing instance age: "
                    + (System.currentTimeMillis() - instance.initTimestamp) + "ms");
            return;
        }
        instance = new EngineMirror(rooms, factions, humanoids, stats);

        EngineLevers.init();
        if (EngineLevers.engineMirrorDumpOnStartup) {
            EngineLevers.dump();
        }

        EventLog.log("MIRROR", "EngineMirror: INITIALIZED (rooms="
                + (rooms != null && rooms.isAvailable())
                + ", factions=" + (factions != null && factions.isAvailable())
                + ", humanoids=" + (humanoids != null && humanoids.isAvailable())
                + ", stats=" + (stats != null && stats.isAvailable()) + ")");
    }

    /**
     * Convenience-Init aus einem AdapterBundle — baut die Sub-Interfaces
     * und initialisiert den EngineMirror in einem Schritt.
     */
    public static void initFromBundle(AdapterDispatcher.AdapterBundle bundle) {
        if (instance != null) {
            EventLog.log("MIRROR", "EngineMirror.initFromBundle() called twice — ignoring");
            return;
        }

        // Sub-Interfaces bauen — jeder nutzt die bestehenden Adapter
        // RoomAccessImpl(ISyxWarehouse, ISyxTransport) — ACHTUNG: Reihenfolge!
        IRoomAccess rooms = new RoomAccessImpl(bundle.warehouse, bundle.transport);

        IFactionAccess factions = new FactionAccessImpl(
                bundle.diplomacy, bundle.npc);

        IHumanoidAccess humanoids = new HumanoidAccessImpl();

        IStatsAccess stats = new StatsAccessImpl();

        init(rooms, factions, humanoids, stats);
    }

    /** @return IRoomAccess — Stockpile, Transport, Room-Iteration, Service */
    public IRoomAccess rooms() {
        return rooms;
    }

    /** @return IFactionAccess — NPC, Diplomacy, Trade, Royalty, Player */
    public IFactionAccess factions() {
        return factions;
    }

    /** @return IHumanoidAccess — Labor, Hunger, Religion, Slavery, AI-Plans */
    public IHumanoidAccess humanoids() {
        return humanoids;
    }

    /** @return IStatsAccess — Time, Religion Stats, BOOSTABLES */
    public IStatsAccess stats() {
        return stats;
    }

    // ═══ Status ═════════════════════════════════════════════

    /** @return true wenn alle 4 Sub-Interfaces erfolgreich initialisiert wurden */
    public boolean isFullyAvailable() {
        return initOk
                && rooms.isAvailable()
                && factions.isAvailable()
                && humanoids.isAvailable()
                && stats.isAvailable();
    }

    /**
     * Dump aller Sub-Interface-Status via EventLog.
     * Für Debug-Tab oder Startup.
     */
    public void dump() {
        EventLog.log("MIRROR", "EngineMirror Status:");
        EventLog.log("MIRROR", "  rooms:     " + status(rooms));
        EventLog.log("MIRROR", "  factions:  " + status(factions));
        EventLog.log("MIRROR", "  humanoids: " + status(humanoids));
        EventLog.log("MIRROR", "  stats:     " + status(stats));
        EventLog.log("MIRROR", "  fully:     " + isFullyAvailable());
    }

    private static String status(Object sub) {
        if (sub == null) return "NULL";
        if (sub instanceof IRoomAccess r) return r.isAvailable() ? "OK" : "DEGRADED";
        if (sub instanceof IFactionAccess f) return f.isAvailable() ? "OK" : "DEGRADED";
        if (sub instanceof IHumanoidAccess h) return h.isAvailable() ? "OK" : "DEGRADED";
        if (sub instanceof IStatsAccess s) return s.isAvailable() ? "OK" : "DEGRADED";
        return "UNKNOWN";
    }

    /** Resets the singleton — for testing only. */
    static void resetForTesting() {
        instance = null;
    }
}
