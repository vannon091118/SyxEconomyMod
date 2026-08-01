package vannon.syx.economy.headless;

import game.faction.player.Player;
import init.resources.RESOURCE;
import snake2d.util.sets.LIST;
import vannon.syx.economy.adapter.ITreasuryAccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Headless stub for {@link ITreasuryAccess}. Single-player polity: the
 * treasury value mirrors {@link MockWorldState#treasury()} so the same
 * denari count that HeadlessIntegrationTest validates on EconomySim
 * matches here when the test asserts via EngineMirror.
 */
public final class StubTreasuryAccess implements ITreasuryAccess {

    private final MockWorldState state;

    public StubTreasuryAccess(MockWorldState state) { this.state = state; }

    @Override public boolean isAvailable() { return true; }

    @Override public double getPlayerCredits() { return state.treasury(); }
    @Override public double getPlayerDailyIncome() { return 0.0; }
    @Override public double getPlayerDailyExpenses() { return 0.0; }
    @Override public double getPlayerNetDaily() { return 0.0; }
    @Override public LIST<Double> getTreasuryHistory() { return null; }

    @Override public Map<String, Double> getTaxRates() {
        Map<String, Double> m = new HashMap<>();
        m.put("headTax", 0.0);
        m.put("marketTax", 0.0);
        m.put("immigrationTax", 0.0);
        return Collections.unmodifiableMap(m);
    }
    @Override public boolean setTaxRate(String taxType, double rate) { return true; }
    @Override public double getLastTaxRevenue() { return 0.0; }

    @Override public double getDailySubsidies() { return 0.0; }
    @Override public double getDailyGrainDoleCost() { return 0.0; }
    @Override public boolean isGrainDoleActive() { return false; }
    @Override public void setGrainDoleActive(boolean active) { /* no-op */ }

    @Override public int getCrisisTier() { return 0; }
    @Override public long[] getCrisisThresholds() {
        return new long[]{-5_000L, -50_000L, -250_000L, -1_000_000L, -5_000_000L};
    }
    @Override public boolean isHardFloor() { return state.treasury() <= -5_000_000L; }

    @Override public List<FactionTreasuryInfo> getNpcTreasuries() {
        return Collections.emptyList();
    }

    @Override public int getWorldPrice(RESOURCE resource) {
        // Mirror of IFactionAccess stub: 0 for unknown resources.
        return 0;
    }
}
