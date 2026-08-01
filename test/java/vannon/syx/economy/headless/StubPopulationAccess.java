package vannon.syx.economy.headless;

import init.race.Race;
import init.type.HCLASS;
import init.type.HTYPE;
import snake2d.util.sets.LIST;
import vannon.syx.economy.adapter.IPopulationAccess;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Headless stub for {@link IPopulationAccess}. Single-polity, single-class,
 * single-race stub layout: all {@code HCLASS} / {@code Race}-keyed queries
 * return {@link MockWorldState}-driven totals. Entity-typed read methods
 * ({@code getAvgWallet}, {@code getMedianWallet}, {@code getGini}) read from
 * the same state arrays.
 */
public final class StubPopulationAccess implements IPopulationAccess {

    private final MockWorldState state;

    public StubPopulationAccess(MockWorldState state) { this.state = state; }

    @Override public boolean isAvailable() { return true; }

    // ── Totals by Class ─────────────────────────────────────
    @Override public int getTotalPopulation(HCLASS cl) { return state.citizenCount; }
    @Override public int getTotalPopulation(HCLASS cl, Race race) { return state.citizenCount; }
    @Override public Map<HCLASS, Integer> getAllClassTotals() {
        Map<HCLASS, Integer> m = new HashMap<>();
        // Prefer real CITIZEN enum constant if vanilla bootstrap is present;
        // fall back to a null-key entry otherwise so the Map is non-empty
        // regardless of init.type.HCLASSES.self state.
        try {
            m.put(init.type.HCLASSES.CITIZEN(), state.citizenCount);
        } catch (Throwable vanillaNotBootstrapped) {
            m.put(null, state.citizenCount);
        }
        return Collections.unmodifiableMap(m);
    }

    // ── By Race ─────────────────────────────────────────────
    @Override public int getRacePopulation(Race race) { return state.citizenCount; }
    @Override public int getRaceClassPopulation(HCLASS cl, Race race) { return state.citizenCount; }
    @Override public int getIncomingPopulation(HCLASS cl, Race race) { return 0; }

    // ── Loyalty / Standing ──────────────────────────────────
    @Override public double getLoyalty(HCLASS cl) { return 1.0; }
    @Override public double getLoyalty(HCLASS cl, Race race) { return 1.0; }
    @Override public double getTargetLoyalty(HCLASS cl) { return 1.0; }
    @Override public double getExpectation() { return 0.5; }

    // ── Wealth / Wallet ─────────────────────────────────────
    @Override public double getAvgWallet(HCLASS cl, Race race) {
        return state.moneySupply() / Math.max(1, state.citizenCount);
    }
    @Override public double getMedianWallet(HCLASS cl, Race race) { return state.medianWallet(); }
    @Override public double getGini(HCLASS cl, Race race) { return state.gini(); }
    @Override public Map<String, Integer> getWealthBrackets(HCLASS cl, Race race) {
        // 5 buckets but no real bracket split — return flat count.
        Map<String, Integer> m = new HashMap<>();
        m.put("very_poor", 0);
        m.put("poor", 0);
        m.put("middle", state.citizenCount);
        m.put("rich", 0);
        m.put("very_rich", 0);
        return Collections.unmodifiableMap(m);
    }

    // ── Employment ──────────────────────────────────────────
    @Override public int getEmployedCount(HCLASS cl, Race race) { return state.citizenCount; }
    @Override public int getUnemployedCount(HCLASS cl, Race race) { return 0; }
    @Override public double getAvgWage(HCLASS cl, Race race) { return 50.0; }

    // ── Housing ─────────────────────────────────────────────
    @Override public int getHousingCapacity(HCLASS cl) { return state.citizenCount + 50; }
    @Override public int getHousingUsed(HCLASS cl) { return state.citizenCount; }
    @Override public int getHousingFree(HCLASS cl) { return 50; }
    @Override public int getHomeless(HCLASS cl, Race race) { return 0; }

    // ── Demographics ────────────────────────────────────────
    @Override public int[] getAgeDistribution(Race race) {
        int[] d = new int[10];
        java.util.Arrays.fill(d, state.citizenCount / 10);
        return d;
    }
    @Override public double getAverageAge(Race race) { return 30.0; }
    @Override public double getBirthRate(Race race) { return 0.05; }
    @Override public double getDeathRate(Race race) { return 0.02; }

    // ── Needs / Happiness ───────────────────────────────────
    @Override public double getHappiness(HCLASS cl, Race race) { return 0.7; }
    @Override public double getNeedSatisfaction(HCLASS cl, Race race) { return 0.8; }

    // ── Types (HTYPES) ──────────────────────────────────────
    @Override public int getTypePopulation(HTYPE type) { return state.citizenCount; }
    @Override public int getTypePopulation(HTYPE type, Race race) { return state.citizenCount; }
}
