package vannon.syx.economy.ui;

import init.resources.RESOURCE;
import init.sprite.UI.UI;
import java.util.List;
import snake2d.util.color.COLOR;
import util.gui.misc.GText;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.FlowMeter;
import vannon.syx.economy.core.FlowPrices;

/** Tabs for the {@link WindowEconomy}. */
public final class EconomyTabs {

    private EconomyTabs() {}

    public static final class PricesTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);
        private int scroll = 0;

        public PricesTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Preise"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            List<RESOURCE> resources = sim.cachedAllResources();
            FlowPrices prices = sim.flowPrices();
            FlowMeter.Snapshot flow = sim.flowMeter().snapshot();
            int rowH = 18;
            int viewportH = ctx.windowY + ctx.windowH - yStart - 20;
            int visible = Math.max(1, viewportH / rowH);
            int maxScroll = Math.max(0, resources.size() - visible);
            scroll = EconWidgets.scrollbar(ctx, "prices", resources.size(), visible, scroll,
                ctx.windowX + ctx.windowW - 24, yStart, viewportH);
            int y = yStart;
            int x = ctx.windowX + 20;
            for (int i = scroll; i < resources.size() && i < scroll + visible; i++) {
                RESOURCE res = resources.get(i);
                int idx = res.index();
                int local = idx < flow.size() ? prices.priceRoundedUp(idx) : 0;
                double anchor = idx < flow.size() ? prices.anchor(idx) : 0.0;
                text.clear();
                text.add(res.name).add("  ").add(CompactNumber.format(local)).add(" (Anker ").add(CompactNumber.format((long)anchor)).add(")");
                text.color(COLOR.WHITE150);
                text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 32, y, y + 16);
                y += rowH;
            }
        }
    }

    public static final class WagesFirmsTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);

        public WagesFirmsTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Löhne & Firmen"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            int x = ctx.windowX + 20;
            int y = yStart;
            text.clear();
            text.add("Mean wage: ").add(CompactNumber.format((long)sim.laborMarket().meanWage())).add("  Wages paid: ").add(CompactNumber.format(sim.wagesPaid()));
            text.color(COLOR.WHITE150);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
            y += 24;
            text.clear();
            text.add("Firm income due: ").add(CompactNumber.format(sim.firmLedger().lastIncomeDue())).add("  paid: ").add(CompactNumber.format(sim.firmLedger().lastIncomePaid()));
            text.color(sim.firmLedger().lastIncomePaid() < sim.firmLedger().lastIncomeDue() ? COLOR.REDISH : COLOR.WHITE150);
            text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 20, y, y + 18);
        }
    }

    public static final class SubsidiesTab implements EconTab {
        private final EconomySim sim;
        private final GText text = new GText(UI.FONT().M, 128);
        private int scroll = 0;

        public SubsidiesTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return "Subventionen"; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            List<RESOURCE> resources = sim.cachedAllResources();
            int rowH = 18;
            int viewportH = ctx.windowY + ctx.windowH - yStart - 20;
            int visible = Math.max(1, viewportH / rowH);
            scroll = EconWidgets.scrollbar(ctx, "subsidies", resources.size(), visible, scroll,
                ctx.windowX + ctx.windowW - 24, yStart, viewportH);
            int y = yStart;
            int x = ctx.windowX + 20;
            for (int i = scroll; i < resources.size() && i < scroll + visible; i++) {
                RESOURCE res = resources.get(i);
                int bounty = sim.productionSubsidies().bounty(res);
                text.clear();
                text.add(res.name).add("  ").add(CompactNumber.format(bounty)).add(" denari/Einheit");
                text.color(bounty > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
                text.render(ctx.renderer, x, ctx.windowX + ctx.windowW - 32, y, y + 16);
                y += rowH;
            }
        }
    }
}
