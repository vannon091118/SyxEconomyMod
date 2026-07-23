package vannon.syx.economy.core;

import java.util.HashSet;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.STATS;
import settlement.stats.muls.StatsMultipliers;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class HandoutRelief {
    private final HashSet<Integer> seen = new HashSet<>();
    private boolean primed = false;

    /**
     * Zahlt Handouts NUR an Bürger die arbeiten (EMPLOYED != null),
     * aber trotzdem unter der Armutsgrenze liegen („working poor").
     *
     * <p>Vor diesem Fix bekamen Arbeitslose 400 D + gratis Essen
     * (Safety-Net in freeRation), während Arbeitende 50 D Lohn bekamen
     * und für Essen zahlten — arbeitende Bürger waren ärmer als Chiller.</p>
     */
    public long update(Roster roster, Wallets wallets) {
        if (!EconConfig.handoutToWallet || STATS.MULTIPLIERS() == null) {
            return 0L;
        }
        StatsMultipliers.StatMultiplierAction handout = STATS.MULTIPLIERS().HANDOUT;
        if (handout == null) {
            return 0L;
        }
        int amount = Math.max(0, EconConfig.handoutWalletAmount);
        HashSet<Integer> current = new HashSet<Integer>();
        long credited = 0L;
        for (int i = 0; i < roster.size(); ++i) {
            Humanoid h = roster.get(i);
            if (!handout.markIs(h.indu()) && !handout.consumeIs(h.indu())) continue;
            current.add(h.id());
            if (!this.primed || amount <= 0 || this.seen.contains(h.id())) continue;

            // NUR auszahlen wenn der Bürger arbeitet (EMPLOYED != null).
            // Arbeitslose bekommen kein Handout — sie müssen arbeiten oder
            // über GrainDole versorgt werden.
            if (STATS.WORK().EMPLOYED.get(h.indu()) == null) {
                continue;
            }

            // Trotz Job immer noch arm? Dann aufstocken.
            // Netto-Lohn = wage − localFoodCost. Wenn Netto < amount → Differenz.
            int netWorth = wallets.netWorth(h);
            int effectiveAmount = Math.min(amount, Math.max(1, EconConfig.doleWealthThreshold - netWorth));
            if (effectiveAmount <= 0) continue;

            wallets.add(h, effectiveAmount);
            credited += (long)effectiveAmount;
        }
        this.seen.clear();
        this.seen.addAll(current);
        this.primed = true;
        return credited;
    }

    public void clear() {
        this.seen.clear();
        this.primed = false;
    }
}

