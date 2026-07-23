package settlement.room.main.employment;

import init.type.HCLASSES;
import init.type.WGROUP;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.employment.RoomEmployment;
import settlement.room.main.employment.RoomEmploymentSimple;
import snake2d.util.sets.LIST;

/**
 * Bridging helpers for the economy mod's labour market.
 *
 * <p>Vanilla {@code RoomEmployment.priority} is {@code public final} and
 * its {@code Priority} class (with {@code get/set/min/max}) is a
 * {@code public static class} implementing {@code INTE}.  All four
 * accessors are reachable directly via {@code e.priority.&lt;method&gt;()}
 * &mdash; no wrapper needed.  The methods that <em>were</em> here
 * ({@code getPriority}, {@code setPriority}, {@code minPriority},
 * {@code maxPriority}, {@code restorePriority}) have been removed
 * (v1.7.4 Vanilla-audit).</p>
 *
 * <p>Remaining methods:</p>
 * <ul>
 * <li>{@link #freeShare(RoomBlueprintImp)} &mdash; genuinely new logic
 *     (free/slave ratio) with no vanilla equivalent</li>
 * <li>{@link #employmentOf(RoomBlueprintImp)} &mdash; convenience bridge
 *     from {@code RoomBlueprintImp} to {@code RoomEmployment}.
 *     Vanilla provides {@code RoomBlueprintIns.employmentExtra()}
 *     which does the same thing but requires an
 *     {@code instanceof RoomBlueprintIns} check first.</li>
 * </ul>
 */
public final class LaborMarketAccess {

    private LaborMarketAccess() {}

    /**
     * Get the RoomEmployment for a blueprint, or null if not applicable.
     * <p>Equivalent to vanilla {@code RoomBlueprintIns.employmentExtra()}
     * but works on the abstract {@code RoomBlueprintImp} without requiring
     * a cast at every call site.</p>
     */
    public static RoomEmployment employmentOf(RoomBlueprintImp b) {
        RoomEmploymentSimple e = b.employment();
        return e instanceof RoomEmployment ? (RoomEmployment) e : null;
    }

    /**
     * Compute free worker share (non-slave workers / total workers).
     * <p>No vanilla equivalent &mdash; this is genuinely new logic.</p>
     */
    public static double freeShare(RoomBlueprintImp b) {
        RoomEmploymentSimple e = b.employment();
        if (e == null) return 1.0;
        int total = e.employed();
        if (total <= 0) return 1.0;
        int free = 0;
        @SuppressWarnings("rawtypes")
        LIST groups = WGROUP.all();
        for (int i = 0; i < groups.size(); ++i) {
            WGROUP g = (WGROUP) groups.get(i);
            if (g.type.CLASS == HCLASSES.SLAVE()) continue;
            free += e.employed(g);
        }
        double s = (double) free / (double) total;
        return s < 0.0 ? 0.0 : (s > 1.0 ? 1.0 : s);
    }
}
