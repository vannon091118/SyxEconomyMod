package vannon.syx.economy.adapter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.HAI;
import vannon.syx.economy.adapter.seam.ClassResolver;
import vannon.syx.economy.core.EventLog;

/**
 * V71.44-Adapter powered by {@link ClassResolver}: erkennt 6 package-private
 * Vanilla-AI-Pläne via {@code Class.forName(name, true, GAME_CL)} +
 * {@link Class#isInstance(Object)}.
 *
 * <p>Der {@link ClassResolver} nutzt den Game-ClassLoader
 * ({@code Humanoid.class.getClassLoader()}) — derselbe Loader wie die Engine,
 * daher sind package-private Plan-Klassen sichtbar.</p>
 */
public final class VanillaAIAdapter implements ISyxAI {

    private static final String ODDJOBBER_CLASS  = "settlement.entity.humanoid.ai.work.PlanOddjobber";
    private static final String FOOD_EATERY_CLASS  = "settlement.entity.humanoid.ai.consume.F_SPlanEatery";
    private static final String FOOD_CANTEEN_CLASS = "settlement.entity.humanoid.ai.consume.F_SPlanCanteen";
    private static final String FOOD_RAW_CLASS     = "settlement.entity.humanoid.ai.consume.F_PlanEat";
    private static final String TAVERN_CLASS       = "settlement.entity.humanoid.ai.consume.PlanTavern";
    private static final String MARKET_CLASS       = "settlement.entity.humanoid.ai.consume.M_PlanMarket";

    private static final ClassLoader GAME_CL;
    static {
        ClassLoader cl = Humanoid.class.getClassLoader();
        GAME_CL = cl != null ? cl : ClassLoader.getSystemClassLoader();
    }

    private static final ClassResolver resolver = new ClassResolver(GAME_CL);

    private final Class<?> oddjobberClass;
    private final Class<?> foodEateryClass;
    private final Class<?> foodCanteenClass;
    private final Class<?> foodRawClass;
    private final Class<?> tavernClass;
    private final Class<?> marketClass;

    private static final Set<String> MISSING_CLASS_LOGS = Collections.synchronizedSet(new HashSet<>());

    private boolean oddjobbingFailedLogged;
    private boolean foodPlanFailedLogged;
    private boolean tavernPlanFailedLogged;
    private boolean marketPlanFailedLogged;

    public VanillaAIAdapter() {
        this.oddjobberClass  = resolveClass(ODDJOBBER_CLASS);
        this.foodEateryClass  = resolveClass(FOOD_EATERY_CLASS);
        this.foodCanteenClass = resolveClass(FOOD_CANTEEN_CLASS);
        this.foodRawClass     = resolveClass(FOOD_RAW_CLASS);
        this.tavernClass      = resolveClass(TAVERN_CLASS);
        this.marketClass      = resolveClass(MARKET_CLASS);

        int loaded = 0;
        if (this.oddjobberClass != null) loaded++;
        if (this.foodEateryClass != null) loaded++;
        if (this.foodCanteenClass != null) loaded++;
        if (this.foodRawClass != null) loaded++;
        if (this.tavernClass != null) loaded++;
        if (this.marketClass != null) loaded++;
        if (loaded > 0) {
            EventLog.log("SEAM", "VanillaAIAdapter: READY (" + loaded + "/6 Plan-Klassen via ClassResolver)");
        }
    }

    private static Class<?> resolveClass(String fqcn) {
        try {
            return resolver.resolve(fqcn);
        } catch (Throwable t) {
            if (MISSING_CLASS_LOGS.add(fqcn)) {
                EventLog.log("SEAM", "VanillaAIAdapter konnte Klasse '" + fqcn
                        + "' nicht laden: " + t.getClass().getSimpleName()
                        + " - Plan-Erkennung fällt auf false zurück.");
            }
            return null;
        }
    }

    @Override
    public boolean isOddjobbing(Humanoid humanoid) {
        try {
            if (humanoid == null) return false;
            HAI hAI = humanoid.ai();
            if (!(hAI instanceof AIManager)) return false;
            AIManager manager = (AIManager) hAI;
            AIPLAN plan = manager.plan();
            return this.oddjobberClass != null && this.oddjobberClass.isInstance(plan);
        } catch (Throwable t) {
            if (!oddjobbingFailedLogged) {
                oddjobbingFailedLogged = true;
                EventLog.log("SEAM", "Plan-Erkennung 'PlanOddjobber' fehlgeschlagen ("
                        + t.getClass().getSimpleName() + ") - Corvée/Gelegenheitsarbeit unerkannt.");
            }
            return false;
        }
    }

    @Override
    public boolean isFoodPlan(AIPLAN plan) {
        try {
            return plan != null
                    && ((this.foodEateryClass != null && this.foodEateryClass.isInstance(plan))
                    || (this.foodCanteenClass != null && this.foodCanteenClass.isInstance(plan))
                    || (this.foodRawClass != null && this.foodRawClass.isInstance(plan)));
        } catch (Throwable t) {
            if (!foodPlanFailedLogged) {
                foodPlanFailedLogged = true;
                EventLog.log("SEAM", "Plan-Erkennung 'F_SPlanEatery|Canteen|F_PlanEat' fehlgeschlagen ("
                        + t.getClass().getSimpleName() + ") - Essenspläne unerkannt.");
            }
            return false;
        }
    }

    @Override
    public boolean isTavernPlan(AIPLAN plan) {
        try {
            return plan != null && this.tavernClass != null && this.tavernClass.isInstance(plan);
        } catch (Throwable t) {
            if (!tavernPlanFailedLogged) {
                tavernPlanFailedLogged = true;
                EventLog.log("SEAM", "Plan-Erkennung 'PlanTavern' fehlgeschlagen ("
                        + t.getClass().getSimpleName() + ") - Tavernenpläne unerkannt.");
            }
            return false;
        }
    }

    @Override
    public boolean isMarketPlan(AIPLAN plan) {
        try {
            return plan != null && this.marketClass != null && this.marketClass.isInstance(plan);
        } catch (Throwable t) {
            if (!marketPlanFailedLogged) {
                marketPlanFailedLogged = true;
                EventLog.log("SEAM", "Plan-Erkennung 'M_PlanMarket' fehlgeschlagen ("
                        + t.getClass().getSimpleName() + ") - Marktpläne unerkannt.");
            }
            return false;
        }
    }
}
