package vannon.syx.economy.core;

import snake2d.util.rnd.RND;
import vannon.syx.economy.core.PairSource;
import vannon.syx.economy.core.Roster;

public final class RandomPairSource
implements PairSource {
    @Override
    public void encounters(Roster roster, int encounters, PairSource.PairConsumer out) {
        int n = roster.size();
        if (n < 2) {
            return;
        }
        for (int i = 0; i < encounters; ++i) {
            int ib;
            int ia = RND.rInt((int)n);
            if (ia == (ib = RND.rInt((int)n))) continue;
            out.pair(roster.get(ia), roster.get(ib));
        }
    }
}

