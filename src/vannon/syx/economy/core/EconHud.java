package vannon.syx.economy.core;

import init.constant.C;
import init.sprite.SPRITES;
import init.sprite.UI.UI;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.gui.GuiSection;
import snake2d.util.misc.ACTION;
import util.colors.GCOLOR;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.misc.GText;
import vannon.syx.economy.ui.EconWindowBase;
import static vannon.syx.economy.ui.EconWindowBase.FONTW_TINY;
import static vannon.syx.economy.ui.EconWindowBase.FONTW_LABEL;

/**
 * Vanilla-konforme HUD-Buttons mit Ampel-Rahmen (farbige Balken unter
 * jedem Button) und Text-Labels. Ampel-Farben werden jeden Frame aus
 * den live EconomySim-Metriken berechnet:
 *   Finanzen  = Treasury
 *   Gleichheit = Gini-Koeffizient
 *   Wachstum  = Treasury-Trend
 *   Arbeit    = Löhne / unbezahlte Arbeiter
 */
public final class EconHud {

    private final GuiSection section = new GuiSection();
    private static final int BTN_DIM = 36;
    private static final int BAR_H = 3;
    private static final int X_GAP = 6;

    // ── Ampel bars — mutable color, updated every frame ──
    private final AmpelBar barFinanzen;
    private final AmpelBar barGleichheit;
    private final AmpelBar barWachstum;
    private final AmpelBar barArbeit;

    private final EconomySim sim;

    public EconHud(EconomySim sim,
                   EconWindowBase overview, EconWindowBase economy,
                   EconWindowBase state, EconWindowBase quickview) {
        this.sim = sim;

        // ── Create Ampel bars (rendered under buttons) ──
        barFinanzen   = new AmpelBar(BTN_DIM, BAR_H);
        barGleichheit = new AmpelBar(BTN_DIM, BAR_H);
        barWachstum   = new AmpelBar(BTN_DIM, BAR_H);
        barArbeit     = new AmpelBar(BTN_DIM, BAR_H);

        // ── Buttons ──
        GButt.ButtPanel ovBtn = new GButt.ButtPanel(SPRITES.icons().l.gov);
        ovBtn.clickActionSet(new ACTION() { @Override public void exe() { overview.toggle(); } });
        ovBtn.hoverInfoSet("Wirtschafts-Uebersicht (Numpad +)");
        ovBtn.setDim(BTN_DIM, 36);

        GButt.ButtPanel ecBtn = new GButt.ButtPanel(SPRITES.icons().m.coins);
        ecBtn.clickActionSet(new ACTION() { @Override public void exe() { economy.toggle(); } });
        ecBtn.hoverInfoSet("Wirtschaftsfenster (Numpad -)");
        ecBtn.setDim(BTN_DIM, 36);

        GButt.ButtPanel stBtn = new GButt.ButtPanel(SPRITES.icons().m.admin);
        stBtn.clickActionSet(new ACTION() { @Override public void exe() { state.toggle(); } });
        stBtn.hoverInfoSet("Staatsfenster (Numpad *)");
        stBtn.setDim(BTN_DIM, 36);

        GButt.ButtPanel qvBtn = new GButt.ButtPanel(SPRITES.icons().m.cog);
        qvBtn.clickActionSet(new ACTION() { @Override public void exe() { quickview.toggle(); } });
        qvBtn.hoverInfoSet("Quickview (Numpad 0)");
        qvBtn.setDim(BTN_DIM, 36);

        GButt.ButtPanel liqBtn = new GButt.ButtPanel(SPRITES.icons().s.cancel);
        liqBtn.clickActionSet(new ACTION() { @Override public void exe() { sim.stateWarehouses().setAllLiquidating(!sim.stateWarehouses().allLiquidating()); } });
        liqBtn.hoverInfoSet("Not-Liquidation Umschalten");
        liqBtn.setDim(BTN_DIM, 36);

        GButt.ButtPanel whModeBtn = new GButt.ButtPanel(SPRITES.icons().s.trade);
        whModeBtn.clickActionSet(new ACTION() {
            @Override public void exe() {
                StateWarehouses.TradeMode m = sim.stateWarehouses().tradeMode();
                if (m == StateWarehouses.TradeMode.NORMAL) sim.stateWarehouses().setTradeMode(StateWarehouses.TradeMode.BUY_ONLY);
                else if (m == StateWarehouses.TradeMode.BUY_ONLY) sim.stateWarehouses().setTradeMode(StateWarehouses.TradeMode.SELL_ONLY);
                else sim.stateWarehouses().setTradeMode(StateWarehouses.TradeMode.NORMAL);
            }
        });
        whModeBtn.hoverInfoSet("Staatslager-Modus Durchschalten (Normal/Kaufen/Verkaufen)");
        whModeBtn.setDim(BTN_DIM, 36);

        // ── Layout: button, ampel-bar, label ──
        int x0 = 0;
        int x1 = BTN_DIM + X_GAP;
        int x2 = 2 * (BTN_DIM + X_GAP);
        int x3 = 3 * (BTN_DIM + X_GAP);
        int x4 = 4 * (BTN_DIM + X_GAP);
        int x5 = 5 * (BTN_DIM + X_GAP);

        section.add(ovBtn, x0, 0);
        section.add(ecBtn, x1, 0);
        section.add(stBtn, x2, 0);
        section.add(qvBtn, x3, 0);
        section.add(liqBtn, x4, 0);
        section.add(whModeBtn, x5, 0);

        // Ampel bars directly under buttons (y = BTN_DIM = 36)
        section.add(barFinanzen,   x0, BTN_DIM);
        section.add(barGleichheit, x1, BTN_DIM);
        section.add(barWachstum,   x2, BTN_DIM);
        section.add(barArbeit,     x3, BTN_DIM);

        // Text labels under bars
        int labelY = BTN_DIM + BAR_H + 2;
        addLabel(section, x0, labelY, "Ueber.");
        addLabel(section, x1, labelY, "Wirt.");
        addLabel(section, x2, labelY, "Staat");
        addLabel(section, x3, labelY, "Quick");
        addLabel(section, x4, labelY, "Not-Liq");
        addLabel(section, x5, labelY, "Lager");

        // Version stamp below labels
        GText versionLabel = new GText(UI.FONT().S, FONTW_TINY);
        try {
            versionLabel.set(BuildStamp.FULL_ID);
        } catch (NoClassDefFoundError e) {
            versionLabel.set("vDEV");
        }
        versionLabel.color(GCOLOR.T().INACTIVE);
        section.add(versionLabel, 0, labelY + 14);
    }

    private static void addLabel(GuiSection section, int x, int y, String text) {
        GText lbl = new GText(UI.FONT().S, FONTW_LABEL);
        lbl.set(text);
        lbl.color(GCOLOR.T().NORMAL);
        section.add(lbl, x, y);
    }

    /** Position the HUD section further left (shifted left by >2x button width).
     *  Livetest v0.13.56: 820 caused overlap with vanilla UI elements.
     *  760 shifts the panel right by ~2 icon-widths. */
    public void initPosition() {
        section.body().moveX2(C.WIDTH() - 760);
        section.body().moveY1(2);
    }

    public void render(Renderer r, float ds) {
        updateAmpel();
        SPRITE_RENDERER sr = (SPRITE_RENDERER) r;
        section.render(sr, ds);
    }

    public void pollHover(COORDINATE mCoo, GBox tooltipText) {
        section.hover(mCoo);
    }

    public void pollClick(MButt button) {
        if (button == MButt.LEFT) {
            section.click();
        }
    }

    public void pollHoverTimer(GBox text) {
        section.hoverInfoGet(text);
    }

    // ── Ampel status computation (called every frame from render) ─────

    private void updateAmpel() {
        if (sim == null || sim.stats() == null) {
            barFinanzen.setColor(GCOLOR.T().INACTIVE);
            barGleichheit.setColor(GCOLOR.T().INACTIVE);
            barWachstum.setColor(GCOLOR.T().INACTIVE);
            barArbeit.setColor(GCOLOR.T().INACTIVE);
            return;
        }

        boolean hasPop = sim.stats().people > 0;
        if (!hasPop) {
            barFinanzen.setColor(GCOLOR.T().INACTIVE);
            barGleichheit.setColor(GCOLOR.T().INACTIVE);
            barWachstum.setColor(GCOLOR.T().INACTIVE);
            barArbeit.setColor(GCOLOR.T().INACTIVE);
            return;
        }

        long treasury = sim.treasury();
        double gini = sim.stats().gini;
        EconIndicators ind = sim.econIndicators();

        // Finanzen: green >10k, yellow >0, red ≤0
        barFinanzen.setColor(trafficColor(
            treasury > 10000 ? 2 : treasury > 0 ? 1 : 0));

        // Gleichheit: green gini<0.30, yellow <0.40, red ≥0.40
        barGleichheit.setColor(trafficColor(
            gini < 0.30 ? 2 : gini < 0.40 ? 1 : 0));

        // Wachstum: green if treasury rising, yellow if >-5k, red if falling hard
        barWachstum.setColor(trafficColor(
            (ind != null && !ind.isTreasuryDeclining()) ? 2
            : treasury > -5000 ? 1 : 0));

        // Arbeit: green if all paid, yellow if some unpaid, red if wages falling
        barArbeit.setColor(trafficColor(
            (ind != null && ind.isWagesFalling()) ? 0
            : sim.firmLedger().lastWorkersUnpaid() == 0 ? 2 : 1));
    }

    private static COLOR trafficColor(int level) {
        return switch (level) {
            case 2 -> GCOLOR.UI().GOOD.normal;
            case 1 -> GCOLOR.UI().SOSO.normal;
            case 0 -> GCOLOR.UI().BAD.normal;
            default -> GCOLOR.T().INACTIVE;
        };
    }

    // ── Ampel bar: colored rectangle with mutable color ───────────────
    // Extends GuiSection to get proper RENDEROBJ implementation for free.

    private static final class AmpelBar extends GuiSection {
        private final int barW;
        private final int barH;
        private COLOR color = GCOLOR.T().INACTIVE;

        AmpelBar(int w, int h) {
            this.barW = w;
            this.barH = h;
        }

        void setColor(COLOR c) { this.color = c; }

        @Override
        public void render(SPRITE_RENDERER r, float ds) {
            // Draw colored bar over the section's body area
            int x1 = body().x1();
            int y1 = body().y1();
            color.render(r, x1, x1 + barW, y1, y1 + barH);
        }
    }
}
