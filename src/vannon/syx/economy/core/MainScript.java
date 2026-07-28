package vannon.syx.economy.core;

import script.SCRIPT;
import snake2d.LOG;
import util.info.INFO;
import vannon.syx.economy.integration.VanillaUIIntegration;

public final class MainScript implements SCRIPT {

    private final INFO info = new INFO(EconTexts.¤¤modName, EconTexts.¤¤modDesc);

    @Override
    public CharSequence name() {
        return this.info.name;
    }

    @Override
    public CharSequence desc() {
        return this.info.desc;
    }

    @Override
    public void initBeforeGameCreated() {
        LOG.ln("[ECONOMY MOD] initBeforeGameCreated");
    }

    @Override
    public void initBeforeGameInited() {
        LOG.ln("[ECONOMY MOD] initBeforeGameInited");
        WealthHappiness.register();
        InflationOff.register();
        MeticImmigration.register();
        PropertyHappiness.register();
        GiniConsequences.register();
        PovertyPressure.register();
        HealthPressure.register();
        FatiguePressure.register();
        // Initialize vanilla-native UI integration
        VanillaUIIntegration.init();
    }

    @Override
    public boolean isSelectable() {
        return true;
    }

    @Override
    public boolean forceInit() {
        return true;
    }

    @Override
    public SCRIPT.SCRIPT_INSTANCE createInstance() {
        LOG.ln("[ECONOMY MOD] createInstance");
        return new InstanceScript();
    }

    public MainScript() {
    }
}
