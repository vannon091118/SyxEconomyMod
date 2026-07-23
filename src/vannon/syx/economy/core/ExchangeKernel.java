package vannon.syx.economy.core;

public final class ExchangeKernel {
    public static int split(int moneyA, int moneyB, double lambdaA, double lambdaB, double eps) {
        int total = moneyA + moneyB;
        double pot = (1.0 - lambdaA) * (double)moneyA + (1.0 - lambdaB) * (double)moneyB;
        int newA = (int)(lambdaA * (double)moneyA + eps * pot);
        if (newA < 0) {
            newA = 0;
        }
        if (newA > total) {
            newA = total;
        }
        return newA;
    }

    public static int split(int moneyA, int moneyB, double eps) {
        return ExchangeKernel.split(moneyA, moneyB, 0.0, 0.0, eps);
    }

    public static int yardSale(int moneyA, int moneyB, double lambdaA, double lambdaB, double eps) {
        int newA;
        int total = moneyA + moneyB;
        int stake = moneyA <= moneyB ? (int)((1.0 - lambdaA) * (double)moneyA) : (int)((1.0 - lambdaB) * (double)moneyB);
        int n = newA = eps < 0.5 ? moneyA + stake : moneyA - stake;
        if (newA < 0) {
            newA = 0;
        }
        if (newA > total) {
            newA = total;
        }
        return newA;
    }

    private ExchangeKernel() {
    }
}

