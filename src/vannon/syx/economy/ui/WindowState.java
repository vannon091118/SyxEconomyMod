package vannon.syx.economy.ui;

import vannon.syx.economy.core.EconomySim;

public final class WindowState extends EconWindowBase {

    public WindowState(EconomySim sim) {
        super(sim);
        addTab(new StateTabs.WarehouseTab(sim));
        addTab(new StateTabs.TaxesTab(sim));
        addTab(new StateTabs.SocialTab(sim));
    }

    @Override
    protected CharSequence windowTitle() {
        return "Staat";
    }
}
