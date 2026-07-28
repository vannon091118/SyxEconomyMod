package vannon.syx.economy.core;

public final class CompactNumber {
    private static final String[] SUFFIX = new String[]{"", "K", "M", "B", "T", "Q", "E"};

    private CompactNumber() {
    }

    public static String format(long value) {
        return CompactNumber.format((double)value);
    }

    public static String format(double value) {
        double scaled;
        if (!Double.isFinite(value)) {
            return "--";
        }
        double absolute = Math.abs(value);
        if (absolute < 999.95) {
            return CompactNumber.small(value);
        }
        int suffix = 0;
        for (scaled = absolute; scaled >= 1000.0 && suffix < SUFFIX.length - 1; scaled /= 1000.0, ++suffix) {
        }
        long tenths = Math.round(scaled * 10.0);
        if (tenths >= 10000L && suffix < SUFFIX.length - 1) {
            tenths = Math.round((double)tenths / 1000.0);
            ++suffix;
        }
        String sign = value < 0.0 ? "-" : "";
        long whole = tenths / 10L;
        long decimal = tenths % 10L;
        return sign + whole + (String)(decimal == 0L ? "" : "." + decimal) + SUFFIX[suffix];
    }

    private static String small(double value) {
        long hundredths = Math.round(value * 100.0);
        long absolute = Math.abs(hundredths);
        String sign = hundredths < 0L ? "-" : "";
        long whole = absolute / 100L;
        long fraction = absolute % 100L;
        if (fraction == 0L) {
            return sign + whole;
        }
        if (fraction % 10L == 0L) {
            return sign + whole + "." + fraction / 10L;
        }
        return sign + whole + "." + (fraction < 10L ? "0" : "") + fraction;
    }
}

