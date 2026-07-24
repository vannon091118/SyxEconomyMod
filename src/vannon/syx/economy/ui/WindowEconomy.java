package vannon.syx.economy.ui;

import vannon.syx.economy.core.EconomySim;

public final class WindowEconomy extends EconWindowBase {

    public WindowEconomy(EconomySim sim) {
        super(sim);
        addTab(new EconomyTabs.PricesTab(sim));
        addTab(new EconomyTabs.WagesFirmsTab(sim));
        addTab(new EconomyTabs.SubsidiesTab(sim));
    }

    @Override
    protected CharSequence windowTitle() {
        return "Wirtschaft";
    }
}
