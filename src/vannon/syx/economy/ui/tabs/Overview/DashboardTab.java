package vannon.syx.economy.ui.tabs.Overview;

import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.gui.GuiSection;
import snake2d.util.misc.ACTION;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.gui.misc.GText;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.DiagnosticExporter;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconIndicators;
import vannon.syx.economy.core.EconProgression;
import vannon.syx.economy.core.EconSnapshot;
import vannon.syx.economy.core.EconTutorialController;
import vannon.syx.economy.core.EconTutorialController.Stage;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.StateWarehouses;
import vannon.syx.economy.core.WealthStats;
import vannon.syx.economy.ui.EconWindowBase;

/**
 * Sprint v0.13.106+M-UI-3 — DashboardTab extrahiert aus WindowOverview.
 *
 * <p>Ehemalige {@code private static final class DashboardTab} aus der 948-LOC
 * WindowOverview-Datei jetzt eigenes File im sub-package
 * {@code vannon.syx.economy.ui.tabs.Overview}. Behavior 1:1 erhalten:</p>
 * <ul>
 *   <li>2 KPI-Reihen (Staatskasse/Bevölkerung/Stufe; Gini/Median/Lohn)</li>
 *   <li>5-Ampel-Reihen (Finanzen/Gleichheit/Wachstum/Arbeit/Versorgung)</li>
 *   <li>Trend-Pfeile nur für Finanzen + Gleichheit (Audit Q1.6 Inkonsistenz, kommt mit M-UI-3.1)</li>
 *   <li>Status-Text + Direkter Spieler-Controller-Block
 *       (Lagerlohn-Slider, Kopfsteuer-Slider, Handels-Modus-Buttons, Not-Liquidation)</li>
 *   <li>Tutorial-Popup (4 Stages)</li>
 *   <li>20-Tage Kassen-Timeline als farbige Bars</li>
 * </ul>
 *
 * <p>Verwendet OverviewHelpers.coloredBar, addTrafficLight,
 * addTrendArrow + buildStatusText. Helper-Lookup via same-package
 * (kein Import noetig).</p>
 *
 * <p>Rule-14 Guard: ~190 SLOC (unter 600 warn). Rule-15 konform.</p>
 */
public final class DashboardTab implements EconWindowBase.TabContent {

    @Override public CharSequence title() { return "Dashboard"; }

    @Override
    public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
        WealthStats stats = sim.stats();
        EconProgression prog = sim.progression();
        EconIndicators ind = sim.econIndicators();
        long treasury = sim.treasury();

        boolean hasPop = stats.people > 0;

        // KPI row 1
        EconWindowBase.addKpi(content, x, y, UI.icons().m.coins, "Staatskasse", CompactNumber.format(treasury) + " D",
                !hasPop ? GCOLOR.T().INACTIVE : treasury >= 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
        EconWindowBase.addKpi(content, x + 240, y, UI.icons().m.citizen, "Bevölkerung", String.valueOf(stats.people),
                hasPop ? GCOLOR.T().NORMAL : GCOLOR.T().INACTIVE);
        EconWindowBase.addKpi(content, x + 480, y, "Stufe", prog.stage.displayName, GCOLOR.T().NORMAL);
        y += 50;

        // KPI row 2
        EconWindowBase.addKpi(content, x, y, UI.icons().m.heart, "Gini",
                hasPop ? String.format("%.3f", stats.gini) : "N/A",
                !hasPop ? GCOLOR.T().INACTIVE : stats.gini > 0.40 ? GCOLOR.UI().BAD.normal : stats.gini > 0.35 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().GOOD.normal);
        EconWindowBase.addKpi(content, x + 240, y, "Median", CompactNumber.format(stats.median) + " D",
                !hasPop ? GCOLOR.T().INACTIVE : stats.median > 100 ? GCOLOR.UI().GOOD.normal : stats.median > 30 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().BAD.normal);
        EconWindowBase.addKpi(content, x + 480, y, UI.icons().m.pickaxe, "Lohn/Tag", CompactNumber.format((long) sim.laborMarket().meanWage()) + " D",
                !hasPop ? GCOLOR.T().INACTIVE : sim.laborMarket().meanWage() > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
        y += 60;

        // Ampel (traffic lights) — ASCII text indicators, no unicode dots that might render as '?'
        GText ampelHeader = new GText(UI.FONT().M, EconWindowBase.FONTW_HDR);
        ampelHeader.set("--- AMPANZEIGE ---");
        ampelHeader.lablify();
        content.add(ampelHeader, x, y);
        y += 24;

        OverviewHelpers.addTrafficLight(content, x, y, "Finanzen",
                !hasPop ? -1 : treasury > 10000 ? 2 : treasury > 0 ? 1 : 0);
        OverviewHelpers.addTrendArrow(content, x + 160, y, ind, "treasuryCurrent");
        OverviewHelpers.addTrafficLight(content, x + 240, y, "Gleichheit",
                !hasPop ? -1 : stats.gini < 0.30 ? 2 : stats.gini < 0.40 ? 1 : 0);
        OverviewHelpers.addTrendArrow(content, x + 400, y, ind, "gini");
        OverviewHelpers.addTrafficLight(content, x + 480, y, "Wachstum",
                !hasPop ? -1 : !ind.isTreasuryDeclining() ? 2 : treasury > -5000 ? 1 : 0);
        y += 30;

        OverviewHelpers.addTrafficLight(content, x, y, "Arbeit",
                !hasPop ? -1 : ind.isWagesFalling() ? 0 : sim.firmLedger().lastWorkersUnpaid() == 0 ? 2 : 1);
        OverviewHelpers.addTrafficLight(content, x + 240, y, "Versorgung",
                !hasPop ? -1 : !ind.isFurnishingCrisis() ? 2 : 1);

        // Status text
        y += 40;
        String statusText = OverviewHelpers.buildStatusText(ind, stats, treasury);
        boolean hasWarnings = !OverviewHelpers.allClear(ind) || !hasPop || treasury < 0;
        GText status = new GText(UI.FONT().M, EconWindowBase.FONTW_BODY);
        status.set(statusText);
        status.color(!hasPop ? GCOLOR.T().INACTIVE : hasWarnings ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().GOOD.normal);
        content.add(status, x, y);

        // ─── DIRECT PLAYER CONTROLS (SOFORT-STEUERUNG) ───
        y += 30;
        GText ctrlHdr = new GText(UI.FONT().M, EconWindowBase.FONTW_HDR);
        ctrlHdr.set("--- DIREKTE SOFORT-STEUERUNG ---");
        ctrlHdr.lablify();
        content.add(ctrlHdr, x, y);
        y += 22;

        StateWarehouses wh = sim.stateWarehouses();

        // Row 1: Wages Slider & Tax Slider
        EconWindowBase.addSlider(content, x, y, "Lagerlohn/Tag", wh::wage, 0, EconConfig.wageMax,
                new ACTION() { @Override public void exe() { wh.setWage(wh.wage() + EconConfig.wageStep); } },
                new ACTION() { @Override public void exe() { wh.setWage(Math.max(0, wh.wage() - EconConfig.wageStep)); } });

        EconWindowBase.addSlider(content, x + 380, y, "Kopfsteuer/Saison", () -> EconConfig.perHeadTax, 0, 500,
                new ACTION() { @Override public void exe() { int old = EconConfig.perHeadTax; EconConfig.perHeadTax = Math.min(500, EconConfig.perHeadTax + 5); DiagnosticExporter.logConfigChange("perHeadTax", old, EconConfig.perHeadTax); } },
                new ACTION() { @Override public void exe() { int old = EconConfig.perHeadTax; EconConfig.perHeadTax = Math.max(0, EconConfig.perHeadTax - 5); DiagnosticExporter.logConfigChange("perHeadTax", old, EconConfig.perHeadTax); } });
        y += 38;

        // Row 2: Warehouse Mode buttons & Emergency Liquidation
        GButt.ButtPanel normalMode = new GButt.ButtPanel("Handel: Normal", 130) {
            @Override protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
                selectedSet(wh.tradeMode() == StateWarehouses.TradeMode.NORMAL);
                super.render(r, ds, isActive, isSelected, isHovered);
            }
        };
        normalMode.clickActionSet(() -> { DiagnosticExporter.logPlayerAction("overview.trade_mode", "NORMAL"); wh.setTradeMode(StateWarehouses.TradeMode.NORMAL); });
        content.add(normalMode, x, y);

        GButt.ButtPanel buyMode = new GButt.ButtPanel("Nur Kaufen", 110) {
            @Override protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
                selectedSet(wh.tradeMode() == StateWarehouses.TradeMode.BUY_ONLY);
                super.render(r, ds, isActive, isSelected, isHovered);
            }
        };
        buyMode.clickActionSet(() -> { DiagnosticExporter.logPlayerAction("overview.trade_mode", "BUY_ONLY"); wh.setTradeMode(StateWarehouses.TradeMode.BUY_ONLY); });
        content.add(buyMode, x + 135, y);

        GButt.ButtPanel sellMode = new GButt.ButtPanel("Nur Verkaufen", 115) {
            @Override protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
                selectedSet(wh.tradeMode() == StateWarehouses.TradeMode.SELL_ONLY);
                super.render(r, ds, isActive, isSelected, isHovered);
            }
        };
        sellMode.clickActionSet(() -> { DiagnosticExporter.logPlayerAction("overview.trade_mode", "SELL_ONLY"); wh.setTradeMode(StateWarehouses.TradeMode.SELL_ONLY); });
        content.add(sellMode, x + 250, y);

        GButt.ButtPanel liquidateBtn = new GButt.ButtPanel("Not-Liquidation", 130) {
            @Override protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
                selectedSet(wh.allLiquidating());
                super.render(r, ds, isActive, isSelected, isHovered);
            }
        };
        liquidateBtn.clickActionSet(() -> { DiagnosticExporter.logPlayerAction("overview.liquidate", "toggle"); wh.setAllLiquidating(!wh.allLiquidating()); });
        content.add(liquidateBtn, x + 370, y);
        y += 32;

        // ─── INTERAKTIVES TUTORIAL POPUP (ONBOARDING) ───
        EconTutorialController tut = sim.tutorial();
        if (tut.isActive() && tut.currentStage() != Stage.NONE && tut.currentStage() != Stage.COMPLETED) {
            GText tutHdr = new GText(UI.FONT().M, EconWindowBase.FONTW_HDR);
            tutHdr.set(">>> ANLEITUNG / ONBOARDING <<<");
            tutHdr.color(GCOLOR.UI().GOOD.normal);
            content.add(tutHdr, x, y);
            y += 18;

            GText tutMsg = new GText(UI.FONT().S, EconWindowBase.FONTW_BODY);
            switch (tut.currentStage()) {
                case WELCOME_WAGES:
                    tutMsg.set("SCHRITT 1/4: Passe oben den 'Lagerlohn/Tag' an. Höhere Löhne ziehen Arbeiter an.");
                    break;
                case WAREHOUSE_MODE:
                    tutMsg.set("SCHRITT 2/4: Wähle den Handelsmodus deiner Staatslager (Normal, Kaufen, Verkaufen).");
                    break;
                case TAXES_FISCAL:
                    tutMsg.set("SCHRITT 3/4: Reguliere die Kopfsteuer, um die Staatskasse im Plus zu halten.");
                    break;
                case EMERGENCY_ACTIONS:
                    tutMsg.set("SCHRITT 4/4: Nutze bei Geldnot die 'Not-Liquidation' für sofortiges Bargeld.");
                    break;
                default:
                    tutMsg.set("Lerne die Steuerelemente kennen.");
                    break;
            }
            tutMsg.color(GCOLOR.T().NORMAL);
            content.add(tutMsg, x, y);

            GButt.ButtPanel nextTut = new GButt.ButtPanel("Weiter", 60);
            nextTut.clickActionSet(() -> { DiagnosticExporter.logPlayerAction("overview.tutorial_next", tut.currentStage().name()); tut.dismissCurrent(); });
            content.add(nextTut, x + 500, y - 2);
            y += 20;
        }

        // History chart — colored bars for treasury timeline
        y += 26;
        if (ind.count() > 0) {
            GText histHeader = new GText(UI.FONT().S, EconWindowBase.FONTW_HDR);
            histHeader.set("--- Kassen-Historie ---");
            histHeader.lablify();
            content.add(histHeader, x, y);
            y += 16;

            int maxBars = Math.min(ind.count(), 20);
            long maxVal = 0;
            long minVal = 0;
            for (int i = 0; i < maxBars; i++) {
                EconSnapshot s = ind.get(i);
                if (s != null && s.treasuryCurrent > maxVal) maxVal = s.treasuryCurrent;
                if (s != null && s.treasuryCurrent < minVal) minVal = s.treasuryCurrent;
            }
            long range = Math.max(1, maxVal - minVal);

            int barSpacing = 14;
            int barW = 10;
            int maxH = 14;
            for (int i = 0; i < maxBars; i++) {
                EconSnapshot s = ind.get(i);
                if (s == null) continue;
                long val = s.treasuryCurrent;
                int level = (int) ((val - minVal) * 5 / range);
                int barH = Math.max(1, maxH * Math.abs(level) / 5);
                COLOR barColor = val >= 0
                    ? (level >= 4 ? GCOLOR.UI().GOOD.normal : level >= 2 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().BAD.normal)
                    : GCOLOR.UI().BAD.normal;

                content.add(OverviewHelpers.coloredBar(barColor, barW, barH), x + i * barSpacing, y + maxH - barH);
            }
            y += maxH + 4;

            GText chartLabel = new GText(UI.FONT().S, EconWindowBase.FONTW_HDR);
            chartLabel.set(CompactNumber.format(maxVal) + " D max — " + maxBars + " Tage");
            chartLabel.color(GCOLOR.T().NORMAL);
            content.add(chartLabel, x, y);
        }
    }
}
