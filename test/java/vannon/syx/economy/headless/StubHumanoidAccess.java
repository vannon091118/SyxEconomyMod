package vannon.syx.economy.headless;

import init.type.NEED;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.room.main.RoomInstance;
import settlement.stats.service.StatService;
import vannon.syx.economy.adapter.IHumanoidAccess;

/**
 * Headless stub for {@link IHumanoidAccess}. All methods take Vanilla entity
 * references (Humanoid, RoomInstance, NEED, StatService) — none of which are
 * loadable in a headless test. Each method returns the documented safe default
 * so that {@link vannon.syx.economy.core.EconomySim} subsystems traversing the
 * stub layer never throw NPE.
 *
 * <p>{@code planCatalog()} returns an empty catalog. Anything that tries to
 * look up an AI-Plan sees no plan classes — by design: AI-Plan replacement is
 * deferred to the headless harness runner, not the sim itself.</p>
 */
public final class StubHumanoidAccess implements IHumanoidAccess {

    private final MockWorldState state;
    private final PlanCatalogStub catalog = new PlanCatalogStub();

    public StubHumanoidAccess(MockWorldState state) { this.state = state; }

    @Override public boolean isAvailable() { return true; }

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
