package vannon.syx.economy.adapter;

import game.faction.FACTIONS;
import game.faction.FCredits;
import game.faction.player.PCredits;
import init.resources.RESOURCE;
import snake2d.util.sets.LIST;
import vannon.syx.economy.core.EngineLevers;
import vannon.syx.economy.core.LoggingAdapter;
import vannon.syx.economy.adapter.seam.BypassGate;
import vannon.syx.economy.adapter.seam.FieldAccessor;

import java.lang.invoke.MethodHandles;

/**
 * TreasuryAccessImpl — ITreasuryAccess Implementation.
 *
 * <p>Nutzt öffentliche API wo möglich (FACTIONS.player().credits()),
 * BypassGate nur für Felder die über öffentliche API nicht erreichbar sind.
 * Für V71.44 validiert.</p>
 */
public final class TreasuryAccessImpl implements ITreasuryAccess {

    // ─── BypassGate Access für nicht-öffentliche Felder ───────────
    private final FieldAccessor.IntField yearlyTurnoverField;
    private final FieldAccessor.IntField yearlyProfitsField;
    private final FieldAccessor.IntField yearlyLossesField;
    @SuppressWarnings("rawtypes")
    private final FieldAccessor.RefField<LIST> credHistoryField;

    private final PCredits creditsInstance;
    private final boolean available;

    public TreasuryAccessImpl() {
        PCredits credits = null;
        boolean ok = true;

        BypassGate gate = new BypassGate("TreasuryAccessImpl", MethodHandles.lookup());

        try {
            credits = FACTIONS.player().credits();
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("TREASURY", "INIT", LoggingAdapter.Severity.ERROR,
                    "init_credits_error", t.getMessage(), "");
            ok = false;
        }

        // PCredits fields via BypassGate
        if (credits != null) {
            // Yearly history fields
            this.yearlyTurnoverField = gate.intField(PCredits.Yearly.class, "TURNOVER");
            this.yearlyProfitsField = gate.intField(PCredits.Yearly.class, "PROFITS");
            this.yearlyLossesField = gate.intField(PCredits.Yearly.class, "LOSSES");
            this.credHistoryField = gate.refField(PCredits.class, "all", snake2d.util.sets.LIST.class);
        } else {
            this.yearlyTurnoverField = null;
            this.yearlyProfitsField = null;
            this.yearlyLossesField = null;
            this.credHistoryField = null;
            ok = false;
        }

        this.creditsInstance = credits;
        this.available = ok && credits != null;
    }

    // ─── isAvailable ────────────────────────────────────────────

    @Override
    public boolean isAvailable() {
        return EngineLevers.treasuryAccessEnabled && available;
    }

    // ─── Player Treasury ────────────────────────────────────────

    @Override
    public double getPlayerCredits() {
        if (!EngineLevers.treasuryAccessEnabled) return 0;
        try {
            if (creditsInstance != null) {
                double v = creditsInstance.credits();
                LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.DEBUG,
                        "getPlayerCredits", String.valueOf(v), "");
                return v;
            }
            PCredits credits = FACTIONS.player().credits();
            double v = credits != null ? credits.credits() : 0;
            LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.DEBUG,
                    "getPlayerCredits", String.valueOf(v), "");
            return v;
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.ERROR,
                    "getPlayerCredits_error", t.getMessage(), "");
            return 0;
        }
    }

    @Override
    public double getPlayerDailyIncome() {
        if (!EngineLevers.treasuryAccessEnabled) return 0;
        // Income is sum of positive CTYPE changes (TAX, TRADE, TOURISM, etc.)
        double income = 0;
        try {
            if (creditsInstance != null && credHistoryField != null) {
                @SuppressWarnings("unchecked")
                LIST<PCredits.CredHistory> history = (LIST<PCredits.CredHistory>) credHistoryField.get(creditsInstance);
                if (history != null) {
                    for (int i = 0; i < history.size(); i++) {
                        PCredits.CredHistory h = history.get(i);
                        if (h != null && h.IN != null) {
                            income += h.IN.get(); // Daily average from HistoryInt
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.ERROR,
                    "getPlayerDailyIncome_error", t.getMessage(), "");
        }
        LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.DEBUG,
                "getPlayerDailyIncome", String.valueOf(income), "");
        return income;
    }

    @Override
    public double getPlayerDailyExpenses() {
        if (!EngineLevers.treasuryAccessEnabled) return 0;
        // Expenses is sum of negative CTYPE changes (CONSTRUCTION, MERCENARIES, etc.)
        double expenses = 0;
        try {
            if (creditsInstance != null && credHistoryField != null) {
                @SuppressWarnings("unchecked")
                LIST<PCredits.CredHistory> history = (LIST<PCredits.CredHistory>) credHistoryField.get(creditsInstance);
                if (history != null) {
                    for (int i = 0; i < history.size(); i++) {
                        PCredits.CredHistory h = history.get(i);
                        if (h != null && h.OUT != null) {
                            expenses += h.OUT.get();
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.ERROR,
                    "getPlayerDailyExpenses_error", t.getMessage(), "");
        }
        LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.DEBUG,
                "getPlayerDailyExpenses", String.valueOf(expenses), "");
        return expenses;
    }

    @Override
    public double getPlayerNetDaily() {
        return getPlayerDailyIncome() - getPlayerDailyExpenses();
    }

    @Override
    public LIST<Double> getTreasuryHistory() {
        if (!EngineLevers.treasuryAccessEnabled) return null;
        if (creditsInstance == null || creditsInstance.creditsH() == null) return null;
        LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.DEBUG,
                "getTreasuryHistory", "not_impl", "");
        return null;
    }

    // ─── Taxation ────────────────────────────────────────────────

    @Override
    public java.util.Map<String, Double> getTaxRates() {
        if (!EngineLevers.treasuryAccessEnabled) return java.util.Map.of();
        LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.DEBUG,
                "getTaxRates", "not_impl", "");
        return java.util.Map.of();
    }

    @Override
    public boolean setTaxRate(String taxType, double rate) {
        if (!EngineLevers.treasuryAccessEnabled) return false;
        LoggingAdapter.csvTrace("TREASURY", "SET", LoggingAdapter.Severity.DEBUG,
                "setTaxRate", taxType + "=" + rate, "");
        return false; // No direct API for this in vanilla
    }

    @Override
    public double getLastTaxRevenue() {
        if (!EngineLevers.treasuryAccessEnabled) return 0;
        try {
            if (creditsInstance != null) {
                PCredits.CredHistory taxHist = creditsInstance.get(FCredits.CTYPE.TAX);
                if (taxHist != null && taxHist.IN != null) {
                    double v = taxHist.IN.get();
                    LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.DEBUG,
                            "getLastTaxRevenue", String.valueOf(v), "");
                    return v;
                }
            }
        } catch (Throwable t) {
            LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.ERROR,
                    "getLastTaxRevenue_error", t.getMessage(), "");
        }
        return 0;
    }

    // ─── Subsidies & Grain Dole ─────────────────────────────────

    @Override
    public double getDailySubsidies() {
        if (!EngineLevers.treasuryAccessEnabled) return 0;
        // Subsidies not directly exposed in vanilla V71
        LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.DEBUG,
                "getDailySubsidies", "not_impl", "");
        return 0;
    }

    @Override
    public double getDailyGrainDoleCost() {
        return getDailySubsidies();
    }

    @Override
    public boolean isGrainDoleActive() {
        if (!EngineLevers.treasuryAccessEnabled) return false;
        LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.DEBUG,
                "isGrainDoleActive", "not_impl", "");
        return false;
    }

    @Override
    public void setGrainDoleActive(boolean active) {
        if (!EngineLevers.treasuryAccessEnabled) return;
        LoggingAdapter.csvTrace("TREASURY", "SET", LoggingAdapter.Severity.DEBUG,
                "setGrainDoleActive", String.valueOf(active), "");
        // No vanilla API for this
    }

    // ─── Crisis ──────────────────────────────────────────────────

    @Override
    public int getCrisisTier() {
        if (!EngineLevers.treasuryAccessEnabled) return 0;
        // CrisisTracker not found in vanilla V71.44
        LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.DEBUG,
                "getCrisisTier", "not_impl", "");
        return 0;
    }

    @Override
    public long[] getCrisisThresholds() {
        if (!EngineLevers.treasuryAccessEnabled) return new long[0];
        LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.DEBUG,
                "getCrisisThresholds", "not_impl", "");
        return new long[0];
    }

    @Override
    public boolean isHardFloor() {
        return getCrisisTier() >= 5;
    }

    // ─── Factions / Trade ────────────────────────────────────────

    @Override
    public java.util.List<FactionTreasuryInfo> getNpcTreasuries() {
        if (!EngineLevers.treasuryAccessEnabled) return java.util.List.of();
        LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.DEBUG,
                "getNpcTreasuries", "not_impl", "");
        return java.util.List.of();
    }

    @Override
    public int getWorldPrice(init.resources.RESOURCE resource) {
        if (!EngineLevers.treasuryAccessEnabled) return 0;
        var api = EngineMirror.api();
        if (api != null && api.factions() != null) {
            return api.factions().getWorldPrice(resource);
        }
        LoggingAdapter.csvTrace("TREASURY", "GET", LoggingAdapter.Severity.DEBUG,
                "getWorldPrice", "no_factions", "");
        return 0;
    }
}