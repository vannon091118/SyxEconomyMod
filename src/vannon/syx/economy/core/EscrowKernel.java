package vannon.syx.economy.core;

public final class EscrowKernel {
    public static int spendable(int balance, int reserved) {
        return Math.max(0, balance - Math.max(0, reserved));
    }

    public static boolean canReserve(int balance, int reserved, int quote) {
        return quote >= 0 && EscrowKernel.spendable(balance, reserved) >= quote;
    }

    public static boolean canSettle(int balance, int reserved, int quote, int bill) {
        return quote >= 0 && bill >= 0 && bill <= quote && reserved >= quote && balance >= bill;
    }

    private EscrowKernel() {
    }
}

