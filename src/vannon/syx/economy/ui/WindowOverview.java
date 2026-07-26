package vannon.syx.economy.ui;

import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.gui.GuiSection;
import snake2d.util.misc.ACTION;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.gui.misc.GText;
import util.gui.panel.GPanel;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconIndicators;
import vannon.syx.economy.core.EconProgression;
import vannon.syx.economy.core.EconSnapshot;
import vannon.syx.economy.core.EconTutorialController;
import vannon.syx.economy.core.EconTutorialController.Stage;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.StateWarehouses;
import vannon.syx.economy.core.WealthStats;

/**
 * Übersicht-Fenster: Dashboard, Demografie, Berater.
 */
public final class WindowOverview extends EconWindowBase {

    private static final TabContent[] TABS = {
        new DashboardTab(),
        new DemographicsTab(),
        new AdvisorTab(),
        new PropertyTab()
    };

    public WindowOverview(EconomySim sim) {
        super(sim);
    }

    @Override
    protected CharSequence title() {
        return "Uebersicht";
    }

    @Override
    protected int panelWidth() { return 840; }

    @Override
    protected TabContent[] tabs() { return TABS; }

    // ─── Tab 1: Dashboard ────────────────────────────────────────────

    private static final class DashboardTab implements TabContent {
        @Override public CharSequence title() { return "Dashboard"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            WealthStats stats = sim.stats();
            EconProgression prog = sim.progression();
            EconIndicators ind = sim.econIndicators();
            long treasury = sim.treasury();

            boolean hasPop = stats.people > 0;

            // KPI row 1
            addKpi(content, x, y, UI.icons().m.coins, "Staatskasse", CompactNumber.format(treasury) + " D",
                !hasPop ? GCOLOR.T().INACTIVE : treasury >= 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
            addKpi(content, x + 240, y, UI.icons().m.citizen, "Bevölkerung", String.valueOf(stats.people),
                hasPop ? GCOLOR.T().NORMAL : GCOLOR.T().INACTIVE);
            addKpi(content, x + 480, y, "Stufe", prog.stage.displayName, GCOLOR.T().NORMAL);
            y += 50;

            // KPI row 2
            addKpi(content, x, y, UI.icons().m.heart, "Gini",
                hasPop ? String.format("%.3f", stats.gini) : "N/A",
                !hasPop ? GCOLOR.T().INACTIVE : stats.gini > 0.40 ? GCOLOR.UI().BAD.normal : stats.gini > 0.35 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().GOOD.normal);
            addKpi(content, x + 240, y, "Median", CompactNumber.format(stats.median) + " D",
                !hasPop ? GCOLOR.T().INACTIVE : stats.median > 100 ? GCOLOR.UI().GOOD.normal : stats.median > 30 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().BAD.normal);
            addKpi(content, x + 480, y, UI.icons().m.pickaxe, "Lohn/Tag", CompactNumber.format((long)sim.laborMarket().meanWage()) + " D",
                !hasPop ? GCOLOR.T().INACTIVE : sim.laborMarket().meanWage() > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
            y += 60;

            // Ampel (traffic lights) — ASCII text indicators, no unicode dots that might render as '?'
            GText ampelHeader = new GText(UI.FONT().M, FONTW_HDR);
            ampelHeader.set("--- AMPANZEIGE ---");
            ampelHeader.lablify();
            content.add(ampelHeader, x, y);
            y += 24;

            addTrafficLight(content, x, y, "Finanzen",
                !hasPop ? -1 : treasury > 10000 ? 2 : treasury > 0 ? 1 : 0);
            addTrendArrow(content, x + 160, y, ind, "treasuryCurrent");
            addTrafficLight(content, x + 240, y, "Gleichheit",
                !hasPop ? -1 : stats.gini < 0.30 ? 2 : stats.gini < 0.40 ? 1 : 0);
            addTrendArrow(content, x + 400, y, ind, "gini");
            addTrafficLight(content, x + 480, y, "Wachstum",
                !hasPop ? -1 : !ind.isTreasuryDeclining() ? 2 : treasury > -5000 ? 1 : 0);
            y += 30;

            addTrafficLight(content, x, y, "Arbeit",
                !hasPop ? -1 : ind.isWagesFalling() ? 0 : sim.firmLedger().lastWorkersUnpaid() == 0 ? 2 : 1);
            addTrafficLight(content, x + 240, y, "Versorgung",
                !hasPop ? -1 : !ind.isFurnishingCrisis() ? 2 : 1);

            // Status text
            y += 40;
            String statusText = buildStatusText(ind, stats, treasury);
            boolean hasWarnings = !allClear(ind) || !hasPop || treasury < 0;
            GText status = new GText(UI.FONT().M, FONTW_BODY);
            status.set(statusText);
            status.color(!hasPop ? GCOLOR.T().INACTIVE : hasWarnings ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().GOOD.normal);
            content.add(status, x, y);

            // ─── DIRECT PLAYER CONTROLS (SOFORT-STEUERUNG) ───
            y += 30;
            GText ctrlHdr = new GText(UI.FONT().M, FONTW_HDR);
            ctrlHdr.set("--- DIREKTE SOFORT-STEUERUNG ---");
            ctrlHdr.lablify();
            content.add(ctrlHdr, x, y);
            y += 22;

            StateWarehouses wh = sim.stateWarehouses();

            // Row 1: Wages Slider & Tax Slider
            addSlider(content, x, y, "Lagerlohn/Tag", wh::wage, 0, EconConfig.wageMax, EconConfig.wageStep,
                new ACTION() { @Override public void exe() { wh.setWage(wh.wage() + EconConfig.wageStep); } },
                new ACTION() { @Override public void exe() { wh.setWage(Math.max(0, wh.wage() - EconConfig.wageStep)); } });

            addSlider(content, x + 380, y, "Kopfsteuer/Saison", () -> EconConfig.perHeadTax, 0, 500, 5,
                new ACTION() { @Override public void exe() { EconConfig.perHeadTax = Math.min(500, EconConfig.perHeadTax + 5); } },
                new ACTION() { @Override public void exe() { EconConfig.perHeadTax = Math.max(0, EconConfig.perHeadTax - 5); } });
            y += 38;

            // Row 2: Warehouse Mode buttons & Emergency Liquidation
            GButt.ButtPanel normalMode = new GButt.ButtPanel("Handel: Normal", 120) {
                @Override protected void render(snake2d.SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
                    selectedSet(wh.tradeMode() == StateWarehouses.TradeMode.NORMAL);
                    super.render(r, ds, isActive, isSelected, isHovered);
                }
            };
            normalMode.clickActionSet(() -> wh.setTradeMode(StateWarehouses.TradeMode.NORMAL));
            content.add(normalMode, x, y);

            GButt.ButtPanel buyMode = new GButt.ButtPanel("Nur Kaufen", 100) {
                @Override protected void render(snake2d.SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
                    selectedSet(wh.tradeMode() == StateWarehouses.TradeMode.BUY_ONLY);
                    super.render(r, ds, isActive, isSelected, isHovered);
                }
            };
            buyMode.clickActionSet(() -> wh.setTradeMode(StateWarehouses.TradeMode.BUY_ONLY));
            content.add(buyMode, x + 125, y);

            GButt.ButtPanel sellMode = new GButt.ButtPanel("Nur Verkaufen", 100) {
                @Override protected void render(snake2d.SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
                    selectedSet(wh.tradeMode() == StateWarehouses.TradeMode.SELL_ONLY);
                    super.render(r, ds, isActive, isSelected, isHovered);
                }
            };
            sellMode.clickActionSet(() -> wh.setTradeMode(StateWarehouses.TradeMode.SELL_ONLY));
            content.add(sellMode, x + 230, y);

            GButt.ButtPanel liquidateBtn = new GButt.ButtPanel("Not-Liquidation", 120) {
                @Override protected void render(snake2d.SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
                    selectedSet(wh.allLiquidating());
                    super.render(r, ds, isActive, isSelected, isHovered);
                }
            };
            liquidateBtn.clickActionSet(() -> wh.setAllLiquidating(!wh.allLiquidating()));
            content.add(liquidateBtn, x + 380, y);
            y += 32;

            // ─── INTERAKTIVES TUTORIAL POPUP (ONBOARDING) ───
            EconTutorialController tut = sim.tutorial();
            if (tut.isActive() && tut.currentStage() != Stage.NONE && tut.currentStage() != Stage.COMPLETED) {
                GText tutHdr = new GText(UI.FONT().M, FONTW_HDR);
                tutHdr.set(">>> ANLEITUNG / ONBOARDING <<<");
                tutHdr.color(GCOLOR.UI().GOOD.normal);
                content.add(tutHdr, x, y);
                y += 18;

                GText tutMsg = new GText(UI.FONT().S, FONTW_BODY);
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
                nextTut.clickActionSet(tut::dismissCurrent);
                content.add(nextTut, x + 500, y - 2);
                y += 20;
            }

            // History chart — colored bars for treasury timeline
            y += 26;
            if (ind.count() > 0) {
                GText histHeader = new GText(UI.FONT().S, FONTW_HDR);
                histHeader.set("--- Kassen-Historie ---");
                histHeader.lablify();
                content.add(histHeader, x, y);
                y += 16;

                int maxBars = Math.min(ind.count(), 20);
                long maxVal = 1;
                for (int i = 0; i < maxBars; i++) {
                    EconSnapshot s = ind.get(i);
                    if (s != null && s.treasuryCurrent > maxVal) maxVal = s.treasuryCurrent;
                }

                int barSpacing = 14;
                int barW = 10;
                int maxH = 14;
                for (int i = 0; i < maxBars; i++) {
                    EconSnapshot s = ind.get(i);
                    int level = 0;
                    if (s != null && maxVal > 0) {
                        level = Math.max(0, Math.min(5, (int)((s.treasuryCurrent * 5) / maxVal)));
                    }
                    int barH = maxH * level / 5;
                    COLOR barColor;
                    if (level >= 5)       barColor = GCOLOR.UI().GOOD.normal;
                    else if (level >= 3)  barColor = GCOLOR.UI().SOSO.normal;
                    else if (level >= 1)  barColor = GCOLOR.UI().BAD.normal;
                    else                  { y += barSpacing; continue; }

                    content.add(coloredBar(barColor, barW, barH), x + i * barSpacing, y + maxH - barH);
                }
                y += maxH + 4;

                GText chartLabel = new GText(UI.FONT().S, FONTW_HDR);
                chartLabel.set(CompactNumber.format(maxVal) + " D max — " + maxBars + " Tage");
                chartLabel.color(GCOLOR.T().NORMAL);
                content.add(chartLabel, x, y);
            }
        }
    }

    // ─── Tab 2: Demographics ─────────────────────────────────────────

    private static final class DemographicsTab implements TabContent {
        @Override public CharSequence title() { return "Demografie"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            WealthStats stats = sim.stats();

            // Stats header
            addKpi(content, x, y, "Siedler", String.valueOf(stats.people), GCOLOR.T().NORMAL);
            addKpi(content, x + 240, y, "Min", CompactNumber.format(stats.min) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 480, y, "Max", CompactNumber.format(stats.max) + " D", GCOLOR.T().NORMAL);
            y += 40;

            addKpi(content, x, y, "Median", CompactNumber.format(stats.median) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 240, y, "Mittel", CompactNumber.format((long)stats.mean) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 480, y, "Gini",
                stats.people > 0 ? String.format("%.3f", stats.gini) : "N/A",
                stats.people > 0 ? (stats.gini > 0.35 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal) : GCOLOR.T().INACTIVE);
            y += 50;

            // Wealth distribution histogram (text-based)
            GText histLabel = new GText(UI.FONT().M, FONTW_HDR);
            histLabel.set("--- Vermögensverteilung ---");
            histLabel.lablify();
            content.add(histLabel, x, y);
            y += 24;

            if (stats.people > 0 && stats.tallest > 0) {
                int barMaxW = 300;
                int barH = 12;
                int labelX = x + 100;
                for (int i = 0; i < WealthStats.BUCKETS && y < 450; i++) {
                    int from = i * stats.bucketWidth;
                    int to = (i + 1) * stats.bucketWidth;
                    int count = stats.histogram[i];
                    int barW = (int)((long)count * barMaxW / stats.tallest);
                    if (barW <= 0) { y += 16; continue; }

                    // Bucket label
                    GText lbl = new GText(UI.FONT().S, FONTW_LABEL);
                    lbl.set(CompactNumber.format(from) + "-" + CompactNumber.format(to));
                    lbl.color(GCOLOR.T().NORMAL);
                    content.add(lbl, x, y);

                    // Colored bar (replaces ASCII # hashes)
                    COLOR barColor = count > stats.tallest / 2 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().GOOD.normal;
                    content.add(coloredBar(barColor, barW, barH), labelX, y + 2);

                    // Count label after the bar
                    GText cnt = new GText(UI.FONT().S, FONTW_CNT);
                    cnt.set(String.valueOf(count));
                    cnt.color(GCOLOR.T().NORMAL);
                    content.add(cnt, labelX + barW + 6, y);

                    y += 16;
                }
            } else {
                GText empty = new GText(UI.FONT().M, FONTW_KPI);
                empty.set("Keine Siedler");
                empty.color(GCOLOR.T().NORMAL);
                content.add(empty, x, y);
            }

            // Wealth bands (4-Klassen-Aufschlüsselung)
            GText bandLabel = new GText(UI.FONT().M, FONTW_HDR);
            bandLabel.set("--- Wohlstandsbänder ---");
            bandLabel.lablify();
            content.add(bandLabel, x, y);
            y += 22;

            addColHeader(content, x, y, "Klasse", 100);
            addColHeader(content, x + 110, y, "Bürger", 60);
            addColHeader(content, x + 180, y, "Bereich", 100);
            addColHeader(content, x + 290, y, "Ø-Vermögen", 90);
            y += 18;

            if (stats.people > 0 && stats.tallest > 0) {
                String[] bands = {"Unterschicht", "Untere Mitte", "Obere Mitte", "Wohlhabend"};
                for (int b = 0; b < 4 && y < 440; b++) {
                    int bFrom = b * 4;
                    int bTo = Math.min((b + 1) * 4, WealthStats.BUCKETS);
                    int bandCount = 0;
                    long bandTotal = 0;
                    for (int j = bFrom; j < bTo && j < stats.histogram.length; j++) {
                        bandCount += stats.histogram[j];
                        int midW = (j * stats.bucketWidth + (j + 1) * stats.bucketWidth) / 2;
                        bandTotal += (long)stats.histogram[j] * midW;
                    }
                    int bandAvg = bandCount > 0 ? (int)(bandTotal / bandCount) : 0;
                    long fromW = bFrom * stats.bucketWidth;
                    long toW = Math.min((long)(bTo - 1) * stats.bucketWidth + stats.bucketWidth - 1, stats.max);

                    GText bandName = new GText(UI.FONT().S, FONTW_NAME);
                    bandName.set(bands[b]);
                    bandName.color(bandCount > 0 ? GCOLOR.T().NORMAL : GCOLOR.T().INACTIVE);
                    content.add(bandName, x, y);

                    GText bandCnt = new GText(UI.FONT().S, FONTW_CNT);
                    bandCnt.set(String.valueOf(bandCount));
                    bandCnt.color(GCOLOR.T().NORMAL);
                    content.add(bandCnt, x + 110, y);

                    GText bandRange = new GText(UI.FONT().S, FONTW_NAME);
                    bandRange.set(CompactNumber.format(fromW) + "-" + CompactNumber.format(toW) + " D");
                    bandRange.color(GCOLOR.T().NORMAL);
                    content.add(bandRange, x + 180, y);

                    GText bandAvgT = new GText(UI.FONT().S, FONTW_LABEL);
                    bandAvgT.set(CompactNumber.format(bandAvg) + " D");
                    bandAvgT.color(bandAvg > stats.median ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().SOSO.normal);
                    content.add(bandAvgT, x + 290, y);

                    y += 14;
                }
            } else {
                GText noBands = new GText(UI.FONT().S, FONTW_KPI);
                noBands.set("Keine Vermögensdaten.");
                noBands.color(GCOLOR.T().INACTIVE);
                content.add(noBands, x, y);
                y += 14;
            }

            // Housing info
            y += 8;
            GText housing = new GText(UI.FONT().M, FONTW_HDR);
            housing.set("Mieteinnahmen: " + CompactNumber.format(sim.housingMarket().lastRentCollected()) + " D  |  Zwangsraeumungen: " + sim.housingMarket().lastEvictions());
            housing.color(GCOLOR.T().NORMAL);
            content.add(housing, x, y);
        }
    }

    // ─── Tab 3: Advisor ──────────────────────────────────────────────

    private static final class AdvisorTab implements TabContent {
        @Override public CharSequence title() { return "Berater"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            EconProgression prog = sim.progression();
            WealthStats stats = sim.stats();
            EconIndicators ind = sim.econIndicators();
            long treasury = sim.treasury();
            boolean hasPop = stats.people > 0;

            // === AMPEL-DASHBOARD (5 Indikatoren) ===
            GText ampelHdr = new GText(UI.FONT().M, FONTW_HDR);
            ampelHdr.set("--- Ampel-Status ---");
            ampelHdr.lablify();
            content.add(ampelHdr, x, y);
            y += 22;

            addTrafficLight(content, x, y, "Finanzen",
                !hasPop ? -1 : treasury > 10000 ? 2 : treasury > 0 ? 1 : 0);
            addTrendArrow(content, x + 160, y, ind, "treasuryCurrent");
            addTrafficLight(content, x + 220, y, "Arbeit",
                !hasPop ? -1 : ind.isWagesFalling() ? 0 : sim.firmLedger().lastWorkersUnpaid() == 0 ? 2 : 1);
            addTrafficLight(content, x + 440, y, "Versorgung",
                !hasPop ? -1 : !ind.isFurnishingCrisis() ? 2 : 1);
            y += 24;

            addTrafficLight(content, x, y, "Gleichheit",
                !hasPop ? -1 : stats.gini < 0.30 ? 2 : stats.gini < 0.40 ? 1 : 0);
            addTrendArrow(content, x + 160, y, ind, "gini");
            addTrafficLight(content, x + 220, y, "Wachstum",
                !hasPop ? -1 : !ind.isTreasuryDeclining() ? 2 : treasury > -5000 ? 1 : 0);
            addTrafficLight(content, x + 440, y, "Abwanderung",
                !hasPop ? -1 : ind.isEmigrationSpike() ? 0 : 2);
            y += 32;

            // === WARNKETTEN (kausale Abhängigkeiten) ===
            String chains = buildWarningChains(ind, treasury, stats);
            if (!chains.isEmpty()) {
                GText warnHdr = new GText(UI.FONT().M, FONTW_HDR);
                warnHdr.set("--- Warnketten ---");
                warnHdr.lablify();
                content.add(warnHdr, x, y);
                y += 20;

                GText chainsText = new GText(UI.FONT().S, FONTW_BODY);
                chainsText.set(chains);
                chainsText.color(GCOLOR.UI().SOSO.normal);
                content.add(chainsText, x, y);
                y += 20 + countLines(chains) * 14;
            }

            // === TREND-TABELLE (letzte 3 Tage) ===
            if (ind.count() >= 2) {
                GText trendHdr = new GText(UI.FONT().M, FONTW_HDR);
                trendHdr.set("--- Trend (letzte 3 Tage) ---");
                trendHdr.lablify();
                content.add(trendHdr, x, y);
                y += 18;

                addColHeader(content, x, y, "Tag", 35);
                addColHeader(content, x + 45, y, "Kasse", 75);
                addColHeader(content, x + 130, y, "Gini", 55);
                addColHeader(content, x + 195, y, "Lohn", 60);
                addColHeader(content, x + 265, y, "Nahrung", 55);
                addColHeader(content, x + 330, y, "Unpaid", 45);
                y += 16;

                int maxRows = Math.min(ind.count(), 3);
                for (int i = ind.count() - maxRows; i < ind.count(); i++) {
                    EconSnapshot s = ind.get(i);
                    if (s == null) continue;
                    int dayLabel = i - (ind.count() - 1);

                    GText dayT = new GText(UI.FONT().S, FONTW_TINY);
                    dayT.set(dayLabel == 0 ? "Heute" : "D" + dayLabel);
                    dayT.color(dayLabel == 0 ? GCOLOR.T().NORMAL : GCOLOR.T().INACTIVE);
                    content.add(dayT, x, y);

                    GText treasuryT = new GText(UI.FONT().S, FONTW_LABEL);
                    treasuryT.set(CompactNumber.format(s.treasuryCurrent));
                    treasuryT.color(s.treasuryCurrent >= 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
                    content.add(treasuryT, x + 45, y);

                    GText giniT = new GText(UI.FONT().S, FONTW_CNT);
                    giniT.set(String.format("%.3f", s.gini));
                    giniT.color(s.gini > 0.40 ? GCOLOR.UI().BAD.normal : s.gini > 0.35 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().GOOD.normal);
                    content.add(giniT, x + 130, y);

                    GText wageT = new GText(UI.FONT().S, FONTW_MED);
                    wageT.set(CompactNumber.format((long)s.actualMeanWage));
                    wageT.color(GCOLOR.T().NORMAL);
                    content.add(wageT, x + 195, y);

                    GText foodT = new GText(UI.FONT().S, FONTW_CNT);
                    foodT.set(String.format("%.1fd", s.foodDays));
                    foodT.color(s.foodDays > 3 ? GCOLOR.UI().GOOD.normal : s.foodDays > 1 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().BAD.normal);
                    content.add(foodT, x + 265, y);

                    GText unpaidT = new GText(UI.FONT().S, FONTW_CNT);
                    unpaidT.set(String.valueOf(s.workersUnpaid));
                    unpaidT.color(s.workersUnpaid > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
                    content.add(unpaidT, x + 330, y);

                    y += 14;
                }
                y += 8;
            }

            // === STUFE & MEILENSTEINE ===
            GText stageHdr = new GText(UI.FONT().M, FONTW_HDR);
            stageHdr.set("--- Stufe & Meilensteine ---");
            stageHdr.lablify();
            content.add(stageHdr, x, y);
            y += 22;

            addKpi(content, x, y, "Stufe", prog.stage.displayName, GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, "Tage in Stufe", String.valueOf(prog.stageDays), GCOLOR.T().NORMAL);
            y += 30;

            addKpi(content, x, y, "Bevölkerung", String.valueOf(stats.people), GCOLOR.T().NORMAL);
            addKpi(content, x + 240, y, "Unbezahlte", String.valueOf(sim.firmLedger().lastWorkersUnpaid()),
                sim.firmLedger().lastWorkersUnpaid() > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
            addKpi(content, x + 480, y, "Tote", String.valueOf(sim.deaths()), GCOLOR.T().NORMAL);
            y += 30;

            addKpi(content, x, y, "Ausgewandert", String.valueOf(sim.emigrations()),
                sim.emigrations() > 0 ? GCOLOR.UI().SOSO.normal : GCOLOR.T().NORMAL);
            addKpi(content, x + 240, y, "Erben", String.valueOf(sim.inherited()), GCOLOR.T().NORMAL);
            addKpi(content, x + 480, y, "Erblos", String.valueOf(sim.heirless()), GCOLOR.T().NORMAL);
            y += 30;

            addMilestoneIcon(content, x, y, "Lagerhaus", prog.msFirstStockpile);
            addMilestoneIcon(content, x + 220, y, "Export", prog.msFirstExport);
            addMilestoneIcon(content, x + 440, y, "Taverne/Markt", prog.msFirstTavern || prog.msFirstMarket);
            y += 18;
            addMilestoneIcon(content, x, y, "Stabile Löhne", prog.msStableWages);
            addMilestoneIcon(content, x + 220, y, "Tempel", prog.msFirstTemple);
            addMilestoneIcon(content, x + 440, y, "Labor", prog.msFirstLaboratory);
            y += 18;
            addMilestoneIcon(content, x, y, "Botschaft", prog.msFirstEmbassy);
            y += 22;

            // Nächste Stufe
            String nextReqs = nextStageReqs(prog, stats);
            GText nextText = new GText(UI.FONT().S, FONTW_BODY);
            nextText.set("Naechste Stufe: " + nextReqs);
            nextText.color(GCOLOR.UI().SOSO.normal);
            content.add(nextText, x, y);
            y += 24;

            // === WAS SOLL ICH TUN? ===
            GText adviceHdr = new GText(UI.FONT().M, FONTW_HDR);
            adviceHdr.set("--- Was soll ich heute tun? ---");
            adviceHdr.lablify();
            content.add(adviceHdr, x, y);
            y += 20;

            String advice = buildAdvice(sim, stats, ind, treasury);
            GText adviceText = new GText(UI.FONT().M, FONTW_BODY);
            adviceText.set(advice);
            adviceText.color(GCOLOR.UI().SOSO.normal);
            content.add(adviceText, x, y);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    // ─── Helpers ─────────────────────────────────────────────────────

    /** Creates a plain colored rectangle (no text, no border).
     *  Uses COLOR.render() in its render override — no bitmap font dependency. */
    private static GuiSection coloredBar(final COLOR color, int w, int h) {
        GuiSection bar = new GuiSection() {
            @Override
            public void render(SPRITE_RENDERER r, float ds) {
                color.render(r, body());
                super.render(r, ds);
            }
        };
        bar.body().setDim(w, h);
        return bar;
    }

    private static void addTrafficLight(GuiSection section, int x, int y, String label, int state) {
        // state: -1=gray, 0=red, 1=yellow, 2=green
        COLOR barColor;
        switch (state) {
            case 2:  barColor = GCOLOR.UI().GOOD.normal; break;
            case 1:  barColor = GCOLOR.UI().SOSO.normal; break;
            case 0:  barColor = GCOLOR.UI().BAD.normal; break;
            default: barColor = GCOLOR.T().INACTIVE; break;
        }

        // Colored rectangle replaces ASCII # bars
        section.add(coloredBar(barColor, 50, 10), x, y + 4);

        GText lbl = new GText(UI.FONT().M, FONTW_KPI);
        lbl.set(label);
        lbl.color(state >= 0 ? GCOLOR.T().NORMAL : GCOLOR.T().INACTIVE);
        section.add(lbl, x + 55, y);
    }

    private static void addMilestoneIcon(GuiSection section, int x, int y, String label, boolean achieved) {
        if (achieved) {
            section.add(UI.icons().s.allRight, x, y + 2);
        } else {
            section.add(UI.icons().s.cancel, x, y + 2);
        }
        GText ms = new GText(UI.FONT().M, FONTW_HDR);
        ms.set(label);
        ms.color(achieved ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
        section.add(ms, x + 20, y);
    }

    private static boolean allClear(EconIndicators ind) {
        return !ind.isInequalityRising() && !ind.isWagesFalling()
            && !ind.isTreasuryDeclining() && !ind.isEmigrationSpike()
            && !ind.isFurnishingCrisis();
    }

    private static String buildStatusText(EconIndicators ind, WealthStats stats, long treasury) {
        if (stats.people == 0) {
            return "Keine Bevoelkerung erfasst — Wirtschaftsdaten noch nicht verfuegbar.";
        }
        StringBuilder sb = new StringBuilder();
        if (treasury < 0) sb.append("Staatskasse negativ! ");
        if (ind.isInequalityRising()) sb.append("Gini steigt! ");
        if (ind.isWagesFalling()) sb.append("Loehne sinken! ");
        if (ind.isTreasuryDeclining()) sb.append("Einnahmen ruecklaeufig! ");
        if (ind.isEmigrationSpike()) sb.append("Abwanderung! ");
        if (ind.isFurnishingCrisis()) sb.append("Einrichtungskrise! ");
        if (sb.length() == 0) {
            return "Wirtschaft stabil — keine Warnungen.";
        }
        return sb.toString().trim();
    }

    private static String nextStageReqs(EconProgression prog, WealthStats stats) {
        switch (prog.stage) {
            case SUBSISTENZ:
                return "50 Siedler, Lagerhaus, 3d Nahrung, 30 Tage in Stufe";
            case HANDEL:
                return "100 Siedler, Export, Lohn>50, Taverne/Markt";
            case INDUSTRIE:
                return "150 Siedler, Labor, Bibliothek, Militaer, 30 Tage";
            case WOHLSTAND:
                return "200 Siedler, 100d ohne Insolvenz, Gini<0.35 30d, Export>10K";
            default:
                return "Maximale Stufe erreicht!";
        }
    }

    /** Priority-based advisor: scans the current state and returns the single most
     *  important action the player should take. Modeled after the old AdvisorTab. */
    private static String buildAdvice(EconomySim sim, WealthStats stats, EconIndicators ind, long treasury) {
        if (stats.people == 0) {
            return "Baue Haeuser und ziehe Siedler an, um die Wirtschaft zu starten.";
        }

        // Priority 1: Unpaid workers → wages too low or treasury empty
        int unpaid = sim.firmLedger().lastWorkersUnpaid();
        if (unpaid > 0 && treasury <= 0) {
            return "Kasse leer! Exportiere Ressourcen oder erhoehe Steuern, um " + unpaid + " unbezahlte Arbeiter zu bezahlen.";
        }
        if (unpaid > stats.people / 4) {
            return "Krise: " + unpaid + " Arbeiter unbezahlt! Sofort Export starten oder Lohn senken (Staat → Lager).";
        }

        // Priority 2: Evictions
        long evictions = sim.housingMarket().lastEvictions();
        if (evictions > 3) {
            return evictions + " Zwangsraeumungen! Mieteinnahmen: " + CompactNumber.format(sim.housingMarket().lastRentCollected()) + " D. Mehr Wohnungen bauen oder Mietzuschuss erhoehen.";
        }

        // Priority 3: Treasury crisis
        if (treasury < -10000) {
            return "Schuldenkrise! Staat muss dringend sparen: Steuern aktivieren, Loehne senken, Ressourcen exportieren.";
        }
        if (treasury < 0) {
            return "Kasse im Minus. Export starten (Lager → Nur verkaufen) oder Steuern erhoehen.";
        }

        // Priority 4: Empty state warehouses
        int whCount = sim.stateWarehouses().ownedCount();
        if (whCount == 0) {
            return "Keine Staatslager gebaut. Lagerhaus als Staat uebernehmen (Rechtsklick -> Staatlich).";
        }

        // Priority 5: Furnishing crisis
        if (ind.isFurnishingCrisis()) {
            return "Einrichtungskrise! Holzproduktion erhoehen oder Holz am Markt zukaufen.";
        }

        // Priority 6: Emigration
        if (ind.isEmigrationSpike()) {
            return "Abwanderung! Loehne und Wohnqualitaet verbessern, um Buerger zu halten.";
        }

        // Priority 7: No production
        if (sim.firmLedger().firmFinancialSnapshots().isEmpty()) {
            return "Keine produzierenden Betriebe. Baue Werkstaetten (Holzfaeller, Baecker, Schneider).";
        }

        // OK
        return "Alles im Lot. Weiter Bevoelkerung und Produktion ausbauen.";
    }

    /** Render a trend arrow using vanilla icons next to a traffic light indicator.
     *  Compares the latest snapshot with the second-to-last one. */
    private static void addTrendArrow(GuiSection section, int x, int y, EconIndicators ind, String field) {
        if (ind.count() < 2) return;
        EconSnapshot latest = ind.latest();
        EconSnapshot prev = ind.get(ind.count() - 2);
        if (latest == null || prev == null) return;

        double curr = getSnapshotField(latest, field);
        double past = getSnapshotField(prev, field);

        double delta = curr - past;
        if (Math.abs(delta) < 0.001) {
            section.add(UI.icons().s.arrow_right, x, y + 2);
        } else if (delta > 0) {
            section.add(UI.icons().s.arrowUp, x, y + 2);
        } else {
            section.add(UI.icons().s.arrowDown, x, y + 2);
        }
    }

    private static double getSnapshotField(EconSnapshot snap, String field) {
        switch (field) {
            case "treasuryCurrent": return snap.treasuryCurrent;
            case "gini": return snap.gini;
            default: return 0;
        }
    }

    /** Builds causal warning chains from the current indicator state.
     *  Returns empty string if all clear. */
    private static String buildWarningChains(EconIndicators ind, long treasury, WealthStats stats) {
        StringBuilder sb = new StringBuilder();
        if (treasury < -10000) {
            sb.append("Schuldenkrise >> Sparzwang >> Lohnsenkung >> Abwanderung\n");
        } else if (treasury < 0) {
            sb.append("Kasse negativ >> Export noetig >> Steueranpassung\n");
        }
        if (ind.isFurnishingCrisis()) {
            sb.append("Holzmangel >> Einrichtungskrise >> Produktionsstillstand\n");
        }
        if (ind.isWagesFalling() && ind.isInequalityRising()) {
            sb.append("Lohnrueckgang + Gini-Anstieg >> Kaufkraftverlust >> Nachfragerueckgang\n");
        } else if (ind.isWagesFalling()) {
            sb.append("Lohnrueckgang >> Kaufkraftverlust >> Firmen-Umsatzrueckgang\n");
        } else if (ind.isInequalityRising()) {
            sb.append("Gini-Anstieg >> Vermoegenskonzentration >> Loyalitaetsverlust");
            if (stats.people > 0 && stats.gini > 0.40) sb.append(" (kritisch)");
            sb.append("\n");
        }
        if (ind.isEmigrationSpike() && ind.isWagesFalling()) {
            sb.append("Lohnrueckgang + Abwanderung >> Arbeitskraeftemangel >> Produktionseinbruch\n");
        } else if (ind.isEmigrationSpike()) {
            sb.append("Abwanderung >> Arbeitskraeftemangel >> Loehne erhoehen\n");
        }
        if (ind.isTreasuryDeclining() && !ind.isFurnishingCrisis()) {
            sb.append("Einnahmen ruecklaeufig >> Pruefe Steuersaetze >> Marktsteuer/Liturgie aktivieren\n");
        }
        return sb.toString().trim();
    }

    // ─── Tab 4: Property ─────────────────────────────────────────────

    private static final class PropertyTab implements TabContent {
        @Override public CharSequence title() { return "Immobilien"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            GText header = new GText(UI.FONT().M, FONTW_HDR);
            header.set("--- Immobilienmarkt ---");
            header.lablify();
            content.add(header, x, y);
            y += 24;

            addKpi(content, x, y, "Mieteinnahmen",
                CompactNumber.format(sim.housingMarket().lastRentCollected()) + " D", GCOLOR.UI().GOOD.normal);
            addKpi(content, x + 380, y, "Mietforderungen",
                CompactNumber.format(sim.housingMarket().lastRentDue()) + " D", GCOLOR.T().NORMAL);
            y += 30;

            addKpi(content, x, y, "Zwangsraeumungen",
                String.valueOf(sim.housingMarket().lastEvictions()),
                sim.housingMarket().lastEvictions() > 3 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
            addKpi(content, x + 380, y, "Immobilienverkauf",
                CompactNumber.format(sim.propertySalesCollected()) + " D", GCOLOR.T().NORMAL);
            y += 30;

            addKpi(content, x, y, "Dividenden",
                CompactNumber.format(sim.propertyDividendsPaid()) + " D", GCOLOR.T().NORMAL);
            y += 50;

            GText sliderHdr = new GText(UI.FONT().M, FONTW_HDR);
            sliderHdr.set("--- Hebel ---");
            sliderHdr.lablify();
            content.add(sliderHdr, x, y);
            y += 22;

            addSlider(content, x, y, "Miete/Kachel", () -> EconConfig.housingBaseRentPerTile, 0, 500, 5,
                new ACTION() { @Override public void exe() { EconConfig.housingBaseRentPerTile = Math.min(500, EconConfig.housingBaseRentPerTile + 5); } },
                new ACTION() { @Override public void exe() { EconConfig.housingBaseRentPerTile = Math.max(0, EconConfig.housingBaseRentPerTile - 5); } });
            y += 38;

            addSlider(content, x, y, "Raeumung bei Schulden >", () -> EconConfig.housingEvictionDebtThreshold, 0, 5000, 100,
                new ACTION() { @Override public void exe() { EconConfig.housingEvictionDebtThreshold = Math.min(5000, EconConfig.housingEvictionDebtThreshold + 100); } },
                new ACTION() { @Override public void exe() { EconConfig.housingEvictionDebtThreshold = Math.max(0, EconConfig.housingEvictionDebtThreshold - 100); } });
            y += 38;

            addSlider(content, x, y, "Schonfrist (Tage)", () -> EconConfig.housingGraceDays, 0, 30, 1,
                new ACTION() { @Override public void exe() { EconConfig.housingGraceDays = Math.min(30, EconConfig.housingGraceDays + 1); } },
                new ACTION() { @Override public void exe() { EconConfig.housingGraceDays = Math.max(0, EconConfig.housingGraceDays - 1); } });
            y += 50;

            GText toggleHdr = new GText(UI.FONT().M, FONTW_HDR);
            toggleHdr.set("--- Schalter ---");
            toggleHdr.lablify();
            content.add(toggleHdr, x, y);
            y += 22;

            addCheckbox(content, x, y, "Immobilienmarkt aktiv", EconConfig.housingMarketEnabled,
                b -> EconConfig.housingMarketEnabled = b);
            y += 22;
            addCheckbox(content, x, y, "Hauskauf erlaubt", EconConfig.homePurchaseEnabled,
                b -> EconConfig.homePurchaseEnabled = b);
        }
    }

    private static void addCheckbox(GuiSection section, int x, int y, String label, boolean initial, java.util.function.Consumer<Boolean> setter) {
        GButt.Checkbox cb = new GButt.Checkbox(label);
        cb.selectedSet(initial);
        cb.clickActionSet(new ACTION() {
            @Override public void exe() {
                boolean next = !cb.selectedIs();
                setter.accept(next);
                cb.selectedSet(next);
            }
        });
        section.add(cb, x, y);
    }

    private static int countLines(String s) {
        if (s == null || s.isEmpty()) return 0;
        int n = 1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') n++;
        }
        return n;
    }
}
