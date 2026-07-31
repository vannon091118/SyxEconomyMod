package vannon.syx.economy.ui;

import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.ui.EconWindowBase.TabContent;
import vannon.syx.economy.ui.tabs.Overview.AdvisorTab;
import vannon.syx.economy.ui.tabs.Overview.DashboardTab;
import vannon.syx.economy.ui.tabs.Overview.DemographicsTab;
import vannon.syx.economy.ui.tabs.Overview.PropertyTab;

/**
 * Sprint v0.13.106+M-UI-3 — Composition-Shell der 4 Overview-Tabs.
 *
 * <p>Ehemalige 948-LOC WindowOverview reduziert auf eine reine Tab-Composition-
 * Shell. Das Verhalten bleibt 1:1 erhalten; nur die innere Struktur wurde
 * aufgeteilt:</p>
 * <ul>
 *   <li>{@link DashboardTab}     — KPI-Reihen + Ampel + Player-Controller + Tutorial + History-Chart</li>
 *   <li>{@link DemographicsTab}  — Vermoegensverteilung + Wohlstandsbänder + Housing-Footer</li>
 *   <li>{@link AdvisorTab}       — Ampel-Dashboard + Warnketten + Trend + Stufe/Meilensteine + Advisor-Logik</li>
 *   <li>{@link PropertyTab}      — Immobilien-KPIs + Hebel-Slider + Toggle-Checkboxen</li>
 * </ul>
 * <p>Shared UI-Helpers (addTrafficLight, buildAdvice, buildWarningChains,
 * countChainAffected, coloredBar, addCheckbox, …) liegen in
 * {@link vannon.syx.economy.ui.tabs.Overview.OverviewHelpers}.</p>
 *
 * <p>Rule-14 God-Class-Guard Effect: 948 LOC → ~75 LOC (-92%). Re-Baseline
 * in tools/god-class-baselines.yml per Rule-14 Pflicht.</p>
 */
public final class WindowOverview extends EconWindowBase {

    private static WindowOverview activeInstance;

    public static WindowOverview activeInstance() {
        return activeInstance;
    }

    private static final TabContent[] TABS = {
        new DashboardTab(),
        new DemographicsTab(),
        new AdvisorTab(),
        new PropertyTab()
    };

    public WindowOverview(EconomySim sim) {
        super(sim);
        activeInstance = this;
    }

    @Override
    protected CharSequence title() {
        return "Uebersicht";
    }

    @Override
    protected int panelWidth() { return 840; }

    @Override
    protected int anchorX() {
        return 8; // top-left fixed
    }

    @Override
    protected int anchorY() {
        return 48; // just below UIPanelTop
    }

    @Override
    protected TabContent[] tabs() { return TABS; }

    /** Set the active tab by index and reopen the window. */
    public void setActiveTab(int index) {
        if (index >= 0 && index < TABS.length) {
            super.setActiveTab(index);
            if (isShown()) {
                close();
                toggle();
            }
        }
    }
}
