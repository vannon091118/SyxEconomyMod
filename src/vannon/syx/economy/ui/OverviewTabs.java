package vannon.syx.economy.ui;

import init.sprite.UI.UI;
import java.util.ArrayList;
import java.util.List;
import settlement.entity.humanoid.Humanoid;
import snake2d.util.color.COLOR;
import util.gui.misc.GText;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.EconomySim;

/** Tabs for the {@link WindowOverview}. */
public final class OverviewTabs {

    private OverviewTabs() {}

    public static final class DashboardTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);

        public DashboardTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Dashboard"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            text.clear();
            text.add("Treasury: ").add(CompactNumber.format(sim.treasury())).add("  Gini: ").add(String.format("%.2f", sim.stats().gini)).add("  Stage: ").add(sim.progression().stage.displayName);
            text.color(COLOR.WHITE200);
            text.render(ctx.renderer, ctx.windowX + 20, ctx.windowX + ctx.windowW - 20, yStart, yStart + 18);
            text.clear();
            text.add("Median wealth: ").add(CompactNumber.format(sim.stats().median)).add("  Mean: ").add(CompactNumber.format(sim.stats().mean));
            text.color(COLOR.WHITE150);
            text.render(ctx.renderer, ctx.windowX + 20, ctx.windowX + ctx.windowW - 20, yStart + 24, yStart + 42);
        }
    }

    public static final class CitizensTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);

        public CitizensTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Bürger"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            int x = ctx.windowX + 20;
            int y = yStart;
            text.clear();
            text.add("Median: ").add(CompactNumber.format(sim.stats().median)).add("  Mean: ").add(CompactNumber.format(sim.stats().mean)).add("  Gini: ").add(String.format("%.2f", sim.stats().gini));
            text.color(COLOR.WHITE150);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 24;
            Humanoid richest = sim.cachedRichestCitizen();
            text.clear();
            if (richest == null) {
                text.add("No citizen with money yet.");
            } else {
                text.add("Richest: ").add(richest.race().info.name).add(" ").add(CompactNumber.format(sim.wallets().get(richest))).add(" denari");
            }
            text.color(COLOR.WHITE200);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
        }
    }

    /** Unified advisor that resolves the old "Defizit" vs "Stabil" contradiction. */
    public static final class AdvisorTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);

        public AdvisorTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Berater"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            long t = sim.treasury();
            long in = sim.taxesCollected() + sim.marketReceipts() + sim.guildIncomePaid();
            long out = sim.spent() + sim.wagesPaid(); // rough outgoing proxy
            boolean deficit = out > in;
            COLOR color = deficit ? COLOR.REDISH : (t < 1000 ? COLOR.YELLOW100 : COLOR.GREENISH);
            String status = deficit ? "Defizit — Ausgaben > Einnahmen" : (t < 1000 ? "Knapp — Kasse niedrig" : "Staatskasse stabil");
            text.clear();
            text.add("Status: ").add(status);
            text.color(color);
            text.render(ctx.renderer, ctx.windowX + 20, ctx.windowX + ctx.windowW - 20, yStart, yStart + 18);
            text.clear();
            text.add("Einnahmen ").add(CompactNumber.format(in)).add(" / Ausgaben ").add(CompactNumber.format(out));
            text.color(COLOR.WHITE150);
            text.render(ctx.renderer, ctx.windowX + 20, ctx.windowX + ctx.windowW - 20, yStart + 20, yStart + 38);
        }
    }
}
