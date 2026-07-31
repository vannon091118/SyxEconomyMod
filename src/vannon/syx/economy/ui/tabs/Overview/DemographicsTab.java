package vannon.syx.economy.ui.tabs.Overview;

import init.sprite.UI.UI;
import snake2d.util.color.COLOR;
import snake2d.util.gui.GuiSection;
import util.colors.GCOLOR;
import util.gui.misc.GText;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.WealthStats;
import vannon.syx.economy.ui.EconWindowBase;

/**
 * Sprint v0.13.106+M-UI-3 — DemographicsTab extrahiert aus WindowOverview.
 *
 * <p>Ehemalige {@code private static final class DemographicsTab} aus der 948-LOC
 * WindowOverview-Datei jetzt eigenes File im Sub-Package
 * {@code vannon.syx.economy.ui.tabs.Overview}. Behavior 1:1 erhalten:</p>
 * <ul>
 *   <li>Settler/Min/Max KPI-Reihe</li>
 *   <li>Median/Mittel/Gini KPI-Reihe</li>
 *   <li>Vermoegensverteilung-Histogramm (WealthStats.histogram-Buckets, 16-Balken)</li>
 *   <li>Wohlstandsbänder (4-Klassen-Aufschlüsselung: Unterschicht/Mitte/Wohlhabend)</li>
 *   <li>Mieteinnahmen + Zwaangsraeumungen-Footer</li>
 * </ul>
 *
 * <p>Verwendet {@link OverviewHelpers#coloredBar} +
 * {@link EconWindowBase#addKpi}/{@link EconWindowBase#addColHeader}.
 * Helper-Lookup via same-package (kein expliziter Import noetig).</p>
 *
 * <p>Rule-14 Guard: ~140 SLOC (unter 600 warn). Rule-15 konform:
 * keine static final Engine-Touchables.</p>
 */
public final class DemographicsTab implements EconWindowBase.TabContent {

    @Override public CharSequence title() { return "Demografie"; }

    @Override
    public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
        WealthStats stats = sim.stats();

        // Stats header
        EconWindowBase.addKpi(content, x, y, "Siedler", String.valueOf(stats.people), GCOLOR.T().NORMAL);
        EconWindowBase.addKpi(content, x + 240, y, "Min", CompactNumber.format(stats.min) + " D", GCOLOR.T().NORMAL);
        EconWindowBase.addKpi(content, x + 480, y, "Max", CompactNumber.format(stats.max) + " D", GCOLOR.T().NORMAL);
        y += 40;

        EconWindowBase.addKpi(content, x, y, "Median", CompactNumber.format(stats.median) + " D", GCOLOR.T().NORMAL);
        EconWindowBase.addKpi(content, x + 240, y, "Mittel", CompactNumber.format((long) stats.mean) + " D", GCOLOR.T().NORMAL);
        EconWindowBase.addKpi(content, x + 480, y, "Gini",
                stats.people > 0 ? String.format("%.3f", stats.gini) : "N/A",
                stats.people > 0 ? (stats.gini > 0.35 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal) : GCOLOR.T().INACTIVE);
        y += 50;

        // Wealth distribution histogram (text-based)
        GText histLabel = new GText(UI.FONT().M, EconWindowBase.FONTW_HDR);
        histLabel.set("--- Vermoegensverteilung ---");
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
                int barW = (int) ((long) count * barMaxW / stats.tallest);
                if (barW <= 0) { y += 16; continue; }

                // Bucket label
                GText lbl = new GText(UI.FONT().S, EconWindowBase.FONTW_LABEL);
                lbl.set(CompactNumber.format(from) + "-" + CompactNumber.format(to));
                lbl.color(GCOLOR.T().NORMAL);
                content.add(lbl, x, y);

                // Colored bar (replaces ASCII # hashes)
                COLOR barColor = count > stats.tallest / 2 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().GOOD.normal;
                content.add(OverviewHelpers.coloredBar(barColor, barW, barH), labelX, y + 2);

                // Count label after the bar
                GText cnt = new GText(UI.FONT().S, EconWindowBase.FONTW_CNT);
                cnt.set(String.valueOf(count));
                cnt.color(GCOLOR.T().NORMAL);
                content.add(cnt, labelX + barW + 6, y);

                y += 16;
            }
        } else {
            GText empty = new GText(UI.FONT().M, EconWindowBase.FONTW_KPI);
            empty.set("Keine Siedler");
            empty.color(GCOLOR.T().NORMAL);
            content.add(empty, x, y);
        }

        // Wealth bands (4-Klassen-Aufschlüsselung)
        GText bandLabel = new GText(UI.FONT().M, EconWindowBase.FONTW_HDR);
        bandLabel.set("--- Wohlstandsbänder ---");
        bandLabel.lablify();
        content.add(bandLabel, x, y);
        y += 22;

        EconWindowBase.addColHeader(content, x, y, "Klasse", 100);
        EconWindowBase.addColHeader(content, x + 110, y, "Buerger", 60);
        EconWindowBase.addColHeader(content, x + 180, y, "Bereich", 100);
        EconWindowBase.addColHeader(content, x + 290, y, "Ø-Vermögen", 90);
        y += 18;

        if (stats.people > 0 && stats.tallest > 0) {
            String[] bands = {"Unterschicht", "Untere Mitte", "Obere Mitte", "Wohlhabend"};
            for (int b = 0; b < 4 && y < 520; b++) {
                int bFrom = b * 4;
                int bTo = Math.min((b + 1) * 4, WealthStats.BUCKETS);
                int bandCount = 0;
                long bandTotal = 0;
                for (int j = bFrom; j < bTo && j < stats.histogram.length; j++) {
                    bandCount += stats.histogram[j];
                    int midW = (j * stats.bucketWidth + (j + 1) * stats.bucketWidth) / 2;
                    bandTotal += (long) stats.histogram[j] * midW;
                }
                int bandAvg = bandCount > 0 ? (int) (bandTotal / bandCount) : 0;
                long fromW = bFrom * stats.bucketWidth;
                long toW = Math.min((long) (bTo - 1) * stats.bucketWidth + stats.bucketWidth - 1, stats.max);

                GText bandName = new GText(UI.FONT().S, EconWindowBase.FONTW_NAME);
                bandName.set(bands[b]);
                bandName.color(bandCount > 0 ? GCOLOR.T().NORMAL : GCOLOR.T().INACTIVE);
                content.add(bandName, x, y);

                GText bandCnt = new GText(UI.FONT().S, EconWindowBase.FONTW_CNT);
                bandCnt.set(String.valueOf(bandCount));
                bandCnt.color(GCOLOR.T().NORMAL);
                content.add(bandCnt, x + 110, y);

                GText bandRange = new GText(UI.FONT().S, EconWindowBase.FONTW_NAME);
                bandRange.set(CompactNumber.format(fromW) + "-" + CompactNumber.format(toW) + " D");
                bandRange.color(GCOLOR.T().NORMAL);
                content.add(bandRange, x + 180, y);

                GText bandAvgT = new GText(UI.FONT().S, EconWindowBase.FONTW_LABEL);
                bandAvgT.set(CompactNumber.format(bandAvg) + " D");
                bandAvgT.color(bandAvg > stats.median ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().SOSO.normal);
                content.add(bandAvgT, x + 290, y);

                y += 14;
            }
        } else {
            GText noBands = new GText(UI.FONT().S, EconWindowBase.FONTW_KPI);
            noBands.set("Keine Vermögensdaten.");
            noBands.color(GCOLOR.T().INACTIVE);
            content.add(noBands, x, y);
            y += 14;
        }

        // Housing info
        y += 8;
        GText housing = new GText(UI.FONT().M, EconWindowBase.FONTW_HDR);
        housing.set("Mieteinnahmen: " + CompactNumber.format(sim.housingMarket().lastRentCollected()) + " D  |  Zwangsraeumungen: " + sim.housingMarket().lastEvictions());
        housing.color(GCOLOR.T().NORMAL);
        content.add(housing, x, y);
    }
}
