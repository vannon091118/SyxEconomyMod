package vannon.syx.economy.core;

import java.util.Arrays;
import settlement.entity.ENTETIES;
import settlement.entity.ENTITY;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import snake2d.util.rnd.RND;
import snake2d.util.sets.LIST;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.PairSource;
import vannon.syx.economy.core.Roster;

public final class ProximityPairSource
implements PairSource {
    private Humanoid[] near = new Humanoid[64];

    @Override
    public void encounters(Roster roster, int encounters, PairSource.PairConsumer out) {
        int n = roster.size();
        if (n < 2) {
            return;
        }
        ENTETIES ents = SETT.ENTITIES();
        if (ents == null) {
            return;
        }
        for (int i = 0; i < encounters; ++i) {
            Humanoid a = roster.get(RND.rInt((int)n));
            if (a.isRemoved()) continue;
            LIST<?> found = ents.getArroundPoint(a.body().cX(), a.body().cY(), EconConfig.proximityRadiusPx);
            int c = 0;
            for (int k = 0; k < found.size(); ++k) {
                ENTITY e = (ENTITY)found.get(k);
                if (e == null || e == a || e.isRemoved() || !(e instanceof Humanoid)) continue;
                if (c == this.near.length) {
                    this.near = Arrays.copyOf(this.near, this.near.length * 2);
                }
                this.near[c++] = (Humanoid)e;
            }
            if (c == 0) continue;
            out.pair(a, this.near[RND.rInt((int)c)]);
        }
    }
}

