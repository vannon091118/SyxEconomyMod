package vannon.syx.economy.core;

import settlement.entity.humanoid.Humanoid;
import vannon.syx.economy.core.Wallets;

public final class Escrow {
    private final Wallets wallets;

    public Escrow(Wallets wallets) {
        this.wallets = wallets;
    }

    public int spendable(Humanoid humanoid) {
        return this.wallets.spendable(humanoid);
    }

    public boolean reserve(Humanoid humanoid, int quote) {
        return this.wallets.reserve(humanoid, quote);
    }

    public void release(Humanoid humanoid, int quote) {
        this.wallets.release(humanoid, quote);
    }

    public boolean settle(Humanoid humanoid, int quote, int bill) {
        return this.wallets.settleReserved(humanoid, quote, bill);
    }

    public int settleOrCharge(Humanoid humanoid, int quote, int bill) {
        if (this.wallets.settleReserved(humanoid, quote, bill)) {
            return bill;
        }
        this.wallets.release(humanoid, quote);
        return this.wallets.chargeAffordable(humanoid, bill);
    }
}

