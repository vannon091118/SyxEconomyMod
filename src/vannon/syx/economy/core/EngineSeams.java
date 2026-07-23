package vannon.syx.economy.core;

import game.time.TIME;
import game.time.TIMECYCLE;
import init.religion.RELIGIONS;
import init.religion.Religion;
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
import settlement.room.service.module.RoomService;
import settlement.stats.STATS;
import settlement.stats.colls.StatsReligion;
import settlement.stats.service.StatService;
import snake2d.util.sets.LIST;
import vannon.syx.economy.adapter.ISyxAI;
import vannon.syx.economy.adapter.VanillaAIAdapter;

public final class EngineSeams {

    /**
     * AI plan recognition adapter.
     * Encapsulates the 6 fragile {@code getSimpleName().equals()} checks
     * that would silently break on a game update. One-shot guards prevent
     * EventLog spam per session.
     *
     * <p>Phase 4 Step 5.1: extracted from inline constants and one-shot
     * flags into {@link VanillaAIAdapter}. Backward-compatible delegation
     * through static methods below.</p>
     */
    private static final ISyxAI aiAdapter = new VanillaAIAdapter();

    public static void overwritePlan(Humanoid humanoid, AIPLAN plan) {
        HAI hAI = humanoid.ai();
        if (!(hAI instanceof AIManager)) {
            throw new IllegalStateException("Humanoid AI is not an AIManager");
        }
        AIManager manager = (AIManager)hAI;
        manager.overwrite(humanoid, plan);
    }

    public static void setFirmTarget(RoomInstance firm, int target) {
        if (firm.employees() == null) {
            throw new IllegalArgumentException("Room has no employment module");
        }
        firm.employees().neededSet(target);
    }

    public static int hungerRaw(Humanoid humanoid) {
        return NEEDS.TYPES().HUNGER.stat().stat().indu().get(humanoid.indu());
    }

    public static void hungerRawSet(Humanoid humanoid, int value) {
        NEEDS.TYPES().HUNGER.stat().stat().indu().set(humanoid.indu(), value);
    }

    public static int eventNeedPriority(Humanoid humanoid, NEED need) {
        if (!(need instanceof NEED_E)) {
            throw new IllegalArgumentException("Need has no per-agent event priority: " + String.valueOf(need));
        }
        NEED_E eventNeed = (NEED_E)need;
        return eventNeed.stat().getPrio(humanoid);
    }

    public static double serviceFulfilment(Humanoid humanoid, StatService service) {
        return service.total().indu().getD(humanoid.indu());
    }

    public static ServiceCapacity serviceCapacity(RoomService service) {
        return new ServiceCapacity(service.total(), service.available(), service.load());
    }

    public static Humanoid livingParent(Humanoid child) {
        return STATS.REL().humanParent(child);
    }

    public static RoomInstance employedRoom(Humanoid humanoid) {
        return (RoomInstance)STATS.WORK().EMPLOYED.get(humanoid.indu());
    }

    public static boolean isSurplusLaborer(Humanoid humanoid) {
        return EngineSeams.isEmployableWorker(humanoid) && EngineSeams.employedRoom(humanoid) == null;
    }

    /**
     * @deprecated Phase 4 migration: use {@code sim.aiAdapter().isTavernPlan(plan)} directly.
     * Kept for backward compatibility with existing callers.
     */
    @Deprecated
    public static boolean isTavernPlan(AIPLAN plan) {
        return aiAdapter.isTavernPlan(plan);
    }

    /**
     * @deprecated Phase 4 migration: use {@code sim.aiAdapter().isMarketPlan(plan)} directly.
     * Kept for backward compatibility with existing callers.
     */
    @Deprecated
    public static boolean isMarketPlan(AIPLAN plan) {
        return aiAdapter.isMarketPlan(plan);
    }

    public static boolean isEmployableWorker(Humanoid humanoid) {
        return humanoid.indu().hType().isWorks() && humanoid.indu().clas() != HCLASSES.SLAVE();
    }

    public static boolean isWorking(Humanoid humanoid) {
        return HPoll.Handler.works((Humanoid)humanoid);
    }

    public static double workCycleSeconds() {
        return Math.max(1.0, TIME.workSeconds());
    }

    public static double gameSecondsSinceStart() {
        TIMECYCLE.Days days = TIME.days();
        return (double)days.bitsSinceStart() * days.bitSeconds() + days.secondOfBit();
    }

    public static int religionIndexOf(Humanoid humanoid) {
        StatsReligion.StatReligion stat = (StatsReligion.StatReligion)STATS.RELIGION().getter.get(humanoid.indu());
        return stat == null ? -1 : stat.religion.index();
    }

    public static void convertTo(Humanoid humanoid, int religionIndex) {
        LIST all = STATS.RELIGION().ALL;
        for (int i = 0; i < all.size(); ++i) {
            if (((StatsReligion.StatReligion)all.get((int)i)).religion.index() != religionIndex) continue;
            STATS.RELIGION().getter.set(humanoid.indu(), (StatsReligion.StatReligion)all.get(i));
            return;
        }
    }

    public static int religionCount() {
        return RELIGIONS.ALL().size();
    }

    public static CharSequence religionName(int index) {
        LIST all = RELIGIONS.ALL();
        return index >= 0 && index < all.size() ? ((Religion)all.get((int)index)).info.name : "?";
    }

    public static boolean isEnslaveablePleb(Humanoid humanoid) {
        return humanoid.indu().hType() == HTYPES.SUBJECT();
    }

    public static void enslave(Humanoid humanoid) {
        humanoid.indu().hTypeSet(humanoid, HTYPES.SLAVE(), null, null);
    }

    private EngineSeams() {
    }

    public record ServiceCapacity(int total, int available, double utilisation) {
    }
}

