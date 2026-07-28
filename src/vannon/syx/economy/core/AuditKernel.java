package vannon.syx.economy.core;

public final class AuditKernel {
    public static long expected(Terms t) {
        return t.seed + t.imported + t.treasuryOut + t.roundingDrift + t.wagesPaid + t.propertyDividendsPaid - t.exported - t.escheated - t.wealthTax - t.headTax - t.marketReceipts - t.legacyConsumption - t.religionTax - t.liturgyTax - t.warehouseTax - t.housingRent - t.propertySalesCollected;
    }

    public static long delta(long living, Terms terms) {
        return living - AuditKernel.expected(terms);
    }

    private AuditKernel() {
    }

    public record Terms(long seed, long imported, long treasuryOut, long roundingDrift, long exported, long escheated, long wealthTax, long headTax, long marketReceipts, long legacyConsumption, long religionTax, long liturgyTax, long warehouseTax, long wagesPaid, long housingRent, long propertySalesCollected, long propertyDividendsPaid) {
    }
}

