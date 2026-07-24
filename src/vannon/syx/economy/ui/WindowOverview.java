package vannon.syx.economy.ui;

import vannon.syx.economy.core.EconomySim;

public final class WindowOverview extends EconWindowBase {

    public WindowOverview(EconomySim sim) {
        super(sim);
        addTab(new OverviewTabs.DashboardTab(sim));
        addTab(new OverviewTabs.CitizensTab(sim));
        addTab(new OverviewTabs.AdvisorTab(sim));
    }

    @Override
    protected CharSequence windowTitle() {
        return "Übersicht";
    }
}
