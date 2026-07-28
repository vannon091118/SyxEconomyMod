package vannon.syx.economy.adapter;

import init.race.Race;
import init.type.HCLASS;
import init.type.HCLASSES;
import init.type.HTYPE;
import settlement.stats.POP;
import settlement.stats.STATS;
import settlement.stats.standing.STANDINGS;
import settlement.stats.standing.StandingCitizen;
import snake2d.util.sets.LIST;
import vannon.syx.economy.core.EngineLevers;
import vannon.syx.economy.core.LoggingAdapter;
import vannon.syx.economy.adapter.seam.BypassGate;
import vannon.syx.economy.adapter.seam.ClassResolver;
import vannon.syx.economy.adapter.seam.FieldAccessor;
import vannon.syx.economy.adapter.seam.MethodAccessor;

import java.lang.invoke.MethodHandles;
import java.util.Map;

/**
 * PopulationAccessImpl — IPopulationAccess Implementation.
 *
 * <p>Direkter Zugriff auf STATS.POP(), STANDINGS, HTYPES via BypassGate.
 * Für V71.44 validiert.</p>
 */
public final class PopulationAccessImpl implements IPopulationAccess {

    // ─── BypassGate Access ──────────────────────────────────────
    private final Object populationData;
    private final boolean available;

    public PopulationAccessImpl() {
        Object popData = null;
        boolean ok = true;

        // 1. STATS.POP via BypassGate
        BypassGate statsGate = new BypassGate("PopulationAccessImpl-STATS", MethodHandles.lookup());
        ClassResolver resolver = statsGate.classResolver(settlement.stats.STATS.class.getClassLoader());
        try {
            Class<?> statsClass = resolver.resolve("settlement.stats.STATS");
            Object statsInstance = resolver.resolve("settlement.stats.STATS");
            if (statsInstance != null) {
                // Get STATS.s (static instance field)
                FieldAccessor.RefField<Object> sField = statsGate.refField(statsClass, "s", Object.class);
                if (statsGate.isAvailable()) {
                    Object statsInst = sField.getStatic();
                    if (statsInst != null) {
                        // Get POP field from StatsPopulation
                        FieldAccessor.RefField<Object> popField = statsGate.refField(statsInst.getClass(), "pop", Object.class);
                        if (statsGate.isAvailable()) {
                            popData = popField.get(statsInst);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("POPULATION", "INIT", LoggingAdapter.Severity.ERROR,
                    "init_stats_pop_error", t.getMessage(), "");
            ok = false;
        }

        this.populationData = popData;
        this.available = ok && popData != null;
    }

    // ─── isAvailable ────────────────────────────────────────────
    @Override
    public boolean isAvailable() {
        return EngineLevers.populationAccessEnabled && available;
    }

    // ─── Totals by Class ────────────────────────────────────────
    @Override
    public int getTotalPopulation(HCLASS cl) {
        if (!EngineLevers.populationAccessEnabled) return 0;
        try {
            int v = POP.tot(cl, null);
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.DEBUG,
                    "getTotalPopulation", String.valueOf(v), cl != null ? cl.toString() : "null");
            return v;
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.ERROR,
                    "getTotalPopulation_error", t.getMessage(), "");
            return 0;
        }
    }

    @Override
    public int getTotalPopulation(HCLASS cl, Race race) {
        if (!EngineLevers.populationAccessEnabled) return 0;
        try {
            int v = POP.tot(cl, race);
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.DEBUG,
                    "getTotalPopulation_race", String.valueOf(v),
                    (cl != null ? cl.toString() : "null") + "/" + (race != null ? race.key : "null"));
            return v;
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.ERROR,
                    "getTotalPopulation_race_error", t.getMessage(), "");
            return 0;
        }
    }

    @Override
    public Map<HCLASS, Integer> getAllClassTotals() {
        return Map.of();
    }

    // ─── By Race ────────────────────────────────────────────────
    @Override
    public int getRacePopulation(Race race) {
        int sum = 0;
        for (HCLASS cl : HCLASSES.ALL()) {
            sum += getTotalPopulation(cl, race);
        }
        return sum;
    }

    @Override
    public int getRaceClassPopulation(HCLASS cl, Race race) {
        return getTotalPopulation(cl, race);
    }

    @Override
    public int getIncomingPopulation(HCLASS cl, Race race) {
        if (!EngineLevers.populationAccessEnabled) return 0;
        try {
            int v = POP.incoming(cl, race);
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.DEBUG,
                    "getIncomingPopulation", String.valueOf(v),
                    (cl != null ? cl.toString() : "null") + "/" + (race != null ? race.key : "null"));
            return v;
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.ERROR,
                    "getIncomingPopulation_error", t.getMessage(), "");
            return 0;
        }
    }

    // ─── Loyalty / Standing ──────────────────────────────────────
    @Override
    public double getLoyalty(HCLASS cl) {
        if (!EngineLevers.populationAccessEnabled) return 0;
        try {
            StandingCitizen sc = STANDINGS.get(cl);
            double v = sc.current();
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.DEBUG,
                    "getLoyalty", String.valueOf(v), cl != null ? cl.toString() : "null");
            return v;
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.ERROR,
                    "getLoyalty_error", t.getMessage(), "");
            return 0;
        }
    }

    @Override
    public double getLoyalty(HCLASS cl, Race race) {
        if (!EngineLevers.populationAccessEnabled) return 0;
        try {
            StandingCitizen sc = STANDINGS.get(cl);
            double v = sc.loyalty.getD(race);
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.DEBUG,
                    "getLoyalty_race", String.valueOf(v),
                    (cl != null ? cl.toString() : "null") + "/" + (race != null ? race.key : "null"));
            return v;
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.ERROR,
                    "getLoyalty_race_error", t.getMessage(), "");
            return 0;
        }
    }

    @Override
    public double getTargetLoyalty(HCLASS cl) {
        if (!EngineLevers.populationAccessEnabled) return 0;
        try {
            StandingCitizen sc = STANDINGS.get(cl);
            double v = sc.target();
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.DEBUG,
                    "getTargetLoyalty", String.valueOf(v), cl != null ? cl.toString() : "null");
            return v;
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.ERROR,
                    "getTargetLoyalty_error", t.getMessage(), "");
            return 0;
        }
    }

    @Override
    public double getExpectation() {
        return 0;
    }

    // ─── Wealth / Wallet (Mod-spezifisch, via EconomySim) ──────
    @Override
    public double getAvgWallet(HCLASS cl, Race race) {
        if (!EngineLevers.populationAccessEnabled) return 0;
        LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.DEBUG,
                "getAvgWallet", "delegated", (cl != null ? cl.toString() : "null") + "/" + (race != null ? race.key : "null"));
        return 0;
    }

    @Override
    public double getMedianWallet(HCLASS cl, Race race) {
        return 0;
    }

    @Override
    public double getGini(HCLASS cl, Race race) {
        return 0;
    }

    @Override
    public Map<String, Integer> getWealthBrackets(HCLASS cl, Race race) {
        return Map.of();
    }

    // ─── Employment ─────────────────────────────────────────────
    @Override
    public int getEmployedCount(HCLASS cl, Race race) {
        return 0;
    }

    @Override
    public int getUnemployedCount(HCLASS cl, Race race) {
        return 0;
    }

    @Override
    public double getAvgWage(HCLASS cl, Race race) {
        return 0;
    }

    // ─── Housing ────────────────────────────────────────────────
    @Override
    public int getHousingCapacity(HCLASS cl) {
        return 0;
    }

    @Override
    public int getHousingUsed(HCLASS cl) {
        return 0;
    }

    @Override
    public int getHousingFree(HCLASS cl) {
        return 0;
    }

    @Override
    public int getHomeless(HCLASS cl, Race race) {
        return 0;
    }

    // ─── Demographics ───────────────────────────────────────────
    @Override
    public int[] getAgeDistribution(Race race) {
        return new int[0];
    }

    @Override
    public double getAverageAge(Race race) {
        return 0;
    }

    @Override
    public double getBirthRate(Race race) {
        return 0;
    }

    @Override
    public double getDeathRate(Race race) {
        return 0;
    }

    // ─── Needs / Happiness ──────────────────────────────────────
    @Override
    public double getHappiness(HCLASS cl, Race race) {
        return getLoyalty(cl, race) * getNeedSatisfaction(cl, race);
    }

    @Override
    public double getNeedSatisfaction(HCLASS cl, Race race) {
        return 1.0;
    }

    // ─── Types (HTYPES) ─────────────────────────────────────────
    @Override
    public int getTypePopulation(HTYPE type) {
        if (!EngineLevers.populationAccessEnabled) return 0;
        try {
            int v = STATS.POP().pop(null, type);
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.DEBUG,
                    "getTypePopulation", String.valueOf(v), type != null ? type.key : "null");
            return v;
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.ERROR,
                    "getTypePopulation_error", t.getMessage(), "");
            return 0;
        }
    }

    @Override
    public int getTypePopulation(HTYPE type, Race race) {
        if (!EngineLevers.populationAccessEnabled) return 0;
        try {
            int v = STATS.POP().pop(race, type);
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.DEBUG,
                    "getTypePopulation_race", String.valueOf(v),
                    (type != null ? type.key : "null") + "/" + (race != null ? race.key : "null"));
            return v;
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("POPULATION", "GET", LoggingAdapter.Severity.ERROR,
                    "getTypePopulation_race_error", t.getMessage(), "");
            return 0;
        }
    }
}