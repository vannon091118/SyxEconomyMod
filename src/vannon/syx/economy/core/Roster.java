package vannon.syx.economy.core;

import java.util.Arrays;
import settlement.entity.ENTETIES;
import settlement.entity.ENTITY;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;

public final class Roster {
    private Humanoid[] people = new Humanoid[1024];
    private int count = 0;

    public void rebuild() {
        this.count = 0;
        ENTETIES ents = SETT.ENTITIES();
        if (ents == null) {
            return;
        }
        ENTITY[] all = ents.getAllEnts();
        int max = ents.Imax();
        for (int i = 0; i <= max && i < all.length; ++i) {
            ENTITY e = all[i];
            if (e == null || e.isRemoved() || !(e instanceof Humanoid)) continue;
            if (this.count == this.people.length) {
                this.people = Arrays.copyOf(this.people, this.people.length * 2);
            }
            this.people[this.count++] = (Humanoid)e;
        }
    }

    public int size() {
        return this.count;
    }

    public Humanoid get(int i) {
        return this.people[i];
    }
}

