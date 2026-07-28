package vannon.syx.economy.core;

import settlement.entity.humanoid.Humanoid;
import vannon.syx.economy.core.Roster;

public interface PairSource {
    public void encounters(Roster var1, int var2, PairConsumer var3);

    public static interface PairConsumer {
        public void pair(Humanoid var1, Humanoid var2);
    }
}

