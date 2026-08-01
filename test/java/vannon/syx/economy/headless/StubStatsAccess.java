package vannon.syx.economy.headless;

import game.boosting.Boostable;
import vannon.syx.economy.adapter.IStatsAccess;

/**
 * Headless stub for {@link IStatsAccess}. Returns deterministic values
 * wherever the underlying component is a pure query (time counters,
 * religion count) and safe-null elsewhere. BOOSTABLES are all-null —
 * {@link EconomySim} does not consume Boostable types during its own
 * update() loop, only the diagnostic tab does.
 *
 * <p>Backed by a single {@link MockWorldState} shared with the other 6
 * stub providers. {@code isAvailable()} returns {@code true} so
 * {@link vannon.syx.economy.adapter.EngineMirror#isFullyAvailable()}
 * passes the gate in {@code EconomySim.update()}.</p>
 */
public final class StubStatsAccess implements IStatsAccess {

    private final MockWorldState state;
    private static final int STUB_RELIGION_COUNT = 3;

    public StubStatsAccess(MockWorldState state) {
        this.state = state;
    }

    @Override public boolean isAvailable() { return true; }

    // ── Time ────────────────────────────────────────────────
    @Override public double getWorkCycleSeconds() { return 3.0; }
    @Override public double getGameSecondsSinceStart() {
        // 1 in-game second per real-world debug tick → matches EconomySim.ticks()
        return (double) state.day();
    }

    // ── Religion Stats ──────────────────────────────────────
    @Override public int getReligionCount() { return STUB_RELIGION_COUNT; }
    @Override public CharSequence getReligionName(int index) {
        return (index >= 0 && index < STUB_RELIGION_COUNT)
            ? RELIGION_NAMES[index] : "?";
    }
    private static final String[] RELIGION_NAMES = {
        "Sun-Worship", "Earth-Mother", "Ancestors"
    };

    // ── BOOSTABLES ──────────────────────────────────────────
    @Override public Boostable getCivicGov() { return null; }
    @Override public Boostable getCivicBoostable(String fieldName) { return null; }
    @Override public Boostable getBehaviourBoostable(String fieldName) { return null; }
    @Override public Object getCivicsInstance() { return null; }
    @Override public Object getBehaviourInstance() { return null; }
}
