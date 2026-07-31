package vannon.syx.economy.ui.tabs.Overview;

import init.sprite.UI.UI;
import snake2d.util.gui.GuiSection;
import snake2d.util.misc.ACTION;
import util.colors.GCOLOR;
import util.gui.misc.GText;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.DiagnosticExporter;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.ui.EconWindowBase;

/**
 * Sprint v0.13.106+M-UI-3 — PropertyTab extrahiert aus WindowOverview.
 *
 * <p>Ehemalige {@code private static final class PropertyTab} aus der 948-LOC
 * WindowOverview-Datei jetzt eigenes File im Sub-Package
 * {@code vannon.syx.economy.ui.tabs.Overview}. Behavior 1:1 erhalten:</p>
 * <ul>
 *   <li>5 KPI-Paare: Mieteinnahmen / Mietforderungen / Räumungen / Immobilienverkauf / Dividenden</li>
 *   <li>3 Hebel-Slider: Miete/Kachel, Räumung-Schwelle, Schonfrist (Tage)</li>
 *   <li>2 Toggle-Checkboxen: Immobilienmarkt aktiv / Hauskauf erlaubt</li>
 * </ul>
 *
 * <p>Verwendet {@link EconWindowBase#addKpi} + {@link EconWindowBase#addSlider} +
 * {@link OverviewHelpers#addCheckbox} (letzteres meldet Toggle-Events via
 * DiagnosticExporter).</p>
 *
 * <p>Rule-14 Guard: ~100 SLOC (unter 600 warn). Rule-15 konform.</p>
 */
public final class PropertyTab implements EconWindowBase.TabContent {

    @Override public CharSequence title() { return "Immobilien"; }

    @Override
    public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
        GText header = new GText(UI.FONT().M, EconWindowBase.FONTW_HDR);
        header.set("--- Immobilienmarkt ---");
        header.lablify();
        content.add(header, x, y);
        y += 24;

        EconWindowBase.addKpi(content, x, y, "Mieteinnahmen",
                CompactNumber.format(sim.housingMarket().lastRentCollected()) + " D", GCOLOR.UI().GOOD.normal);
        EconWindowBase.addKpi(content, x + 380, y, "Mietforderungen",
                CompactNumber.format(sim.housingMarket().lastRentDue()) + " D", GCOLOR.T().NORMAL);
        y += 30;

        EconWindowBase.addKpi(content, x, y, "Zwangsraeumungen",
                String.valueOf(sim.housingMarket().lastEvictions()),
                sim.housingMarket().lastEvictions() > 3 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
        EconWindowBase.addKpi(content, x + 380, y, "Immobilienverkauf",
                CompactNumber.format(sim.propertySalesCollected()) + " D", GCOLOR.T().NORMAL);
        y += 30;

        EconWindowBase.addKpi(content, x, y, "Dividenden",
                CompactNumber.format(sim.propertyDividendsPaid()) + " D", GCOLOR.T().NORMAL);
        y += 50;

        GText sliderHdr = new GText(UI.FONT().M, EconWindowBase.FONTW_HDR);
        sliderHdr.set("--- Hebel ---");
        sliderHdr.lablify();
        content.add(sliderHdr, x, y);
        y += 22;

        EconWindowBase.addSlider(content, x, y, "Miete/Kachel", () -> EconConfig.housingBaseRentPerTile, 0, 500,
                new ACTION() {
                    @Override public void exe() {
                        int old = EconConfig.housingBaseRentPerTile;
                        EconConfig.housingBaseRentPerTile = Math.min(500, EconConfig.housingBaseRentPerTile + 5);
                        DiagnosticExporter.logConfigChange("housingBaseRentPerTile", old, EconConfig.housingBaseRentPerTile);
                    }
                },
                new ACTION() {
                    @Override public void exe() {
                        int old = EconConfig.housingBaseRentPerTile;
                        EconConfig.housingBaseRentPerTile = Math.max(0, EconConfig.housingBaseRentPerTile - 5);
                        DiagnosticExporter.logConfigChange("housingBaseRentPerTile", old, EconConfig.housingBaseRentPerTile);
                    }
                });
        y += 38;

        EconWindowBase.addSlider(content, x, y, "Raeumung bei Schulden >", () -> EconConfig.housingEvictionDebtThreshold, 0, 5000,
                new ACTION() {
                    @Override public void exe() {
                        EconConfig.housingEvictionDebtThreshold = Math.min(5000, EconConfig.housingEvictionDebtThreshold + 100);
                    }
                },
                new ACTION() {
                    @Override public void exe() {
                        EconConfig.housingEvictionDebtThreshold = Math.max(0, EconConfig.housingEvictionDebtThreshold - 100);
                    }
                });
        y += 38;

        EconWindowBase.addSlider(content, x, y, "Schonfrist (Tage)", () -> EconConfig.housingGraceDays, 0, 30,
                new ACTION() {
                    @Override public void exe() {
                        EconConfig.housingGraceDays = Math.min(30, EconConfig.housingGraceDays + 1);
                    }
                },
                new ACTION() {
                    @Override public void exe() {
                        EconConfig.housingGraceDays = Math.max(0, EconConfig.housingGraceDays - 1);
                    }
                });
        y += 50;

        GText toggleHdr = new GText(UI.FONT().M, EconWindowBase.FONTW_HDR);
        toggleHdr.set("--- Schalter ---");
        toggleHdr.lablify();
        content.add(toggleHdr, x, y);
        y += 22;

        OverviewHelpers.addCheckbox(content, x, y, "Immobilienmarkt aktiv", EconConfig.housingMarketEnabled,
                b -> EconConfig.housingMarketEnabled = b);
        y += 22;
        OverviewHelpers.addCheckbox(content, x, y, "Hauskauf erlaubt", EconConfig.homePurchaseEnabled,
                b -> EconConfig.homePurchaseEnabled = b);
    }
}
