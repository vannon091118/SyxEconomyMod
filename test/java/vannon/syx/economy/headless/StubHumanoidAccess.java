package vannon.syx.economy.headless;

import init.type.NEED;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.room.main.RoomInstance;
import settlement.stats.service.StatService;
import vannon.syx.economy.adapter.IHumanoidAccess;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.Collections;
import java.util.List;

/**
 * Headless stub for {@link IHumanoidAccess}. All methods take Vanilla entity
 * references (Humanoid, RoomInstance, NEED, StatService) — none of which are
 * loadable in a headless test. Each method returns the documented safe default
 * so that {@link vannon.syx.economy.core.EconomySim} subsystems traversing the
 * stub layer never throw NPE.
 *
 * <p>For tests that need population iteration (census, demographics walks),
 * set {@link #setResidentFactory(java.util.function.Supplier)} to a Mockito-
 * based factory returning <code>Mockito.mock(Humanoid.class, Mockito.RETURNS_DEEP_STUBS)</code>
 * instances. Without a factory the iteration is a no-op (count via
 * {@link #getResidentCount()} only).</p>
 */
public final class StubHumanoidAccess implements IHumanoidAccess {

    private final MockWorldState state;
    private final PlanCatalogStub catalog = new PlanCatalogStub();

    /**
     * Optional factory for census-style tests. Sprint v0.13.129+: wenn gesetzt,
     * iteriert forEachResident() über die gelieferte Liste. Default null =
     * no-op (Backward-Compat).
     */
    private Supplier<List<Humanoid>> residentFactory = null;

    public StubHumanoidAccess(MockWorldState state) { this.state = state; }

    /**
     * Setter for resident factory. Sprint v0.13.129+: tests verifying
     * population iteration can install a Mockito-backed supplier here. Caller
     * is responsible for providing computeMockN := state.citizenCount mocks.
     */
    public void setResidentFactory(Supplier<List<Humanoid>> factory) {
        this.residentFactory = factory;
    }

    @Override public boolean isAvailable() { return true; }

    // ═══ Sprint v0.13.129+ResidentImportFix ═══
    // Stub layer liefert deterministische Counts aus MockWorldState. Ohne
    // Factory ist die Iteration ein no-op; mit Factory (von Tests gesetzt)
    // werden die Mockito-mock-Humanoids ausgeliefert.
    @Override public int getResidentCount() { return state.citizenCount; }

    @Override public void forEachResident(Consumer<Humanoid> action) {
        if (action == null) return;
        if (residentFactory == null) return; // no-op wenn keine factory gesetzt
        List<Humanoid> residents = residentFactory.get();
        if (residents == null) return;
        for (Humanoid h : residents) {
            if (h != null) {
                try {
                    action.accept(h);
                } catch (RuntimeException visitorException) {
                    // Visitor-Semantik: einzelne Exception darf Iteration
                    // nicht abbrechen (graceful Skip-and-Continue).
                }
            }
        }
    }

    // ── Employment & Labor ──────────────────────────────────
    @Override public boolean isWorking(Humanoid h) { return false; }
    @Override public boolean isEmployableWorker(Humanoid h) { return false; }
    @Override public boolean isSurplusLaborer(Humanoid h) { return false; }
    @Override public RoomInstance getEmployedRoom(Humanoid h) { return null; }

    // ── Hunger & Needs ──────────────────────────────────────
    @Override public int getHungerRaw(Humanoid h) { return 50; } // 0..100; 50 = neutral
    @Override public void setHungerRaw(Humanoid h, int value) { /* no-op */ }
    @Override public int getEventNeedPriority(Humanoid h, NEED need) { return -1; }

    // ── Service Fulfilment ─────────────────────────────────
    @Override public double getServiceFulfilment(Humanoid h, StatService service) { return 0.0; }

    // ── Social Hierarchy ───────────────────────────────────
    @Override public Humanoid getLivingParent(Humanoid child) { return null; }

    // ── Religion ───────────────────────────────────────────
    @Override public int getReligionIndexOf(Humanoid h) { return 0; }
    @Override public void convertTo(Humanoid h, int religionIndex) { /* no-op */ }

    // ── Slavery ────────────────────────────────────────────
    @Override public boolean isEnslaveablePleb(Humanoid h) { return false; }
    @Override public void enslave(Humanoid h) { /* no-op */ }

    // ── AI Plan Management ─────────────────────────────────
    @Override public void overwritePlan(Humanoid h, AIPLAN plan) { /* no-op */ }
    @Override public PlanCatalog planCatalog() { return catalog; }

    private static final class PlanCatalogStub implements PlanCatalog {
        @Override public int resolvedCount() { return 0; }
        @Override public boolean isAvailable() { return false; }
        @Override public Class<?> lookup(String simpleName) { return null; }
        @Override public AIPLAN create(String simpleName, String key) { return null; }
    }
}
