package vannon.syx.economy.adapter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entity.humanoid.ai.main.AIPLAN;
import settlement.entity.humanoid.ai.main.HAI;
import vannon.syx.economy.core.EventLog;    /**
     * V71.44 Adapter: erkennt Vanilla-AI-Pläne via {@link Class#forName(String, boolean, ClassLoader)}
     * mit dem Game-ClassLoader und {@link Class#isInstance(Object)}.
     *
     * <p>Die 6 Plan-Klassen sind package-private in Vanilla und daher nicht
     * via {@code instanceof} prüfbar. {@code Class.forName(name, true, GAME_CL)} nutzt den
     * ClassLoader des Spiels — nicht den des Mods — da package-private Klassen
     * nur für den ladenden ClassLoader sichtbar sind.</p>
     *
     * <p>Fallback: wenn Class.forName fehlschlägt (z.B. durch API-Änderung), fällt die
     * Erkennung still auf {@code false} zurück.</p>
     *
     * <p>One-Shot-Guards verhindern EventLog-Spam: jeder Plan-Name wird
     * nur beim ersten Fehlschlag pro Session geloggt.</p>
     */
public final class VanillaAIAdapter implements ISyxAI {

    /* ── V71.44-verified plan class fully qualified names ───────────── */

    private static final String ODDJOBBER_CLASS = "settlement.entity.humanoid.ai.work.PlanOddjobber";
    private static final String FOOD_EATERY_CLASS = "settlement.entity.humanoid.ai.consume.F_SPlanEatery";
    private static final String FOOD_CANTEEN_CLASS = "settlement.entity.humanoid.ai.consume.F_SPlanCanteen";
    private static final String FOOD_RAW_CLASS = "settlement.entity.humanoid.ai.consume.F_PlanEat";
    private static final String TAVERN_CLASS = "settlement.entity.humanoid.ai.consume.PlanTavern";
    private static final String MARKET_CLASS = "settlement.entity.humanoid.ai.consume.M_PlanMarket";

    /* ── Loaded via Class.forName at construction time ──────────────── */

    /**
     * Game-ClassLoader — bezogen von einer öffentlichen API-Klasse.
     * Package-private Plan-Klassen sind NUR über diesen Loader sichtbar,
     * nicht über den Mod-ClassLoader. Null-Guard: fällt auf System-ClassLoader
     * zurück (unwahrscheinlich, aber sicherheitshalber).
     */
    private static final ClassLoader GAME_CL;
    static {
        ClassLoader cl = Humanoid.class.getClassLoader();
        GAME_CL = cl != null ? cl : ClassLoader.getSystemClassLoader();
    }

    private final Class<?> oddjobberClass;
    private final Class<?> foodEateryClass;
    private final Class<?> foodCanteenClass;
    private final Class<?> foodRawClass;
    private final Class<?> tavernClass;
    private final Class<?> marketClass;

    /* ── One-shot guards (prevents EventLog spam) ────────────────────── */

    private static final Set<String> MISSING_CLASS_LOGS = Collections.synchronizedSet(new HashSet<>());

    private boolean oddjobbingFailedLogged = false;
    private boolean foodPlanFailedLogged = false;
    private boolean tavernPlanFailedLogged = false;
    private boolean marketPlanFailedLogged = false;

    public VanillaAIAdapter() {
        this.oddjobberClass = loadClass(ODDJOBBER_CLASS);
        this.foodEateryClass = loadClass(FOOD_EATERY_CLASS);
        this.foodCanteenClass = loadClass(FOOD_CANTEEN_CLASS);
        this.foodRawClass = loadClass(FOOD_RAW_CLASS);
        this.tavernClass = loadClass(TAVERN_CLASS);
        this.marketClass = loadClass(MARKET_CLASS);

        int loaded = 0;
        if (this.oddjobberClass != null) loaded++;
        if (this.foodEateryClass != null) loaded++;
        if (this.foodCanteenClass != null) loaded++;
        if (this.foodRawClass != null) loaded++;
        if (this.tavernClass != null) loaded++;
        if (this.marketClass != null) loaded++;
        if (loaded > 0) {
            EventLog.log("SEAM", "VanillaAIAdapter: READY (" + loaded + "/6 Plan-Klassen geladen)");
        }
    }

    private static Class<?> loadClass(String name) {
        try {
            // Game-ClassLoader verwenden, nicht den Mod-ClassLoader.
            // Class.forName(String) nutzt den Loader des AUFRUFERS (Mod),
            // der package-private Spiel-Klassen nicht sieht.
            return Class.forName(name, true, GAME_CL);
        } catch (Throwable t) {
            if (MISSING_CLASS_LOGS.add(name)) {
                EventLog.log("SEAM", "VanillaAIAdapter konnte Klasse '" + name
                        + "' nicht laden: " + t.getClass().getSimpleName()
                        + " - Plan-Erkennung fällt auf false zurück.");
            }
            return null;
        }
    }

    /* ── ISyxAI implementation ───────────────────────────────────────── */

    @Override
    public boolean isOddjobbing(Humanoid humanoid) {
        try {
            if (humanoid == null) {
                return false;
            }
            HAI hAI = humanoid.ai();
            if (!(hAI instanceof AIManager)) {
                return false;
            }
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
