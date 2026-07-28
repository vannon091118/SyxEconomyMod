package vannon.syx.economy.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import snake2d.LOG;
import settlement.entity.humanoid.Humanoid;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;

public final class Histogram {
    private static final Path OUT = Paths.get(System.getProperty("user.home"), "soseconmod-data", "wallets.csv");

    public void dump(Roster roster, Wallets wallets, long tick) {
        int n = roster.size();
        if (n == 0) {
            return;
        }
        long total = 0L;
        int max = 0;
        try {
            Files.createDirectories(OUT.getParent(), new FileAttribute<?>[0]);
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(OUT, new OpenOption[0]));){
                w.println("money,lambda");
                for (int i = 0; i < n; ++i) {
                    Humanoid h = roster.get(i);
                    int m = wallets.get(h);
                    total += (long)m;
                    if (m > max) {
                        max = m;
                    }
                    w.println(m + "," + String.format("%.4f", wallets.lambda(h)));
                }
            }
        }
        catch (IOException e) {
            System.err.println("[ECON] could not write " + String.valueOf(OUT) + ": " + e.getMessage());
            return;
        }
        LOG.ln("[ECON] tick=" + tick + " people=" + n + " total=" + total + " mean=" + total / (long)n + " max=" + max + " -> " + String.valueOf(OUT));
    }
}

