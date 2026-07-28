package vannon.syx.economy.core.io;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import settlement.main.SETT;
import settlement.room.industry.module.Industry;

/**
 * Phase 1 (IO-Analysis): Logischer Rezeptgraph.
 *
 * <p>Baut aus der Vanilla-Engine {@code SETT.ROOMS().industries.all} eine
 * Adjacency-Liste: Für jede Ressource wird gespeichert, welche Industrien
 * sie produzieren und welche sie verbrauchen.</p>
 *
 * <p>Wird einmal nach Engine-Init gebaut (erster Aufruf von {@link #build()})
 * und nur bei Mods-Reload aktualisiert. Respektiert Rule 15 — kein
 * {@code static final} Touchable auf Engine-Singletons im clinit.</p>
 */
public final class IOGraph {

    /** producersOf[r] = Liste der Industrien die Ressource r produzieren. */
    private List<Industry>[] producersOf;
    /** consumersOf[r] = Liste der Industrien die Ressource r verbrauchen. */
    private List<Industry>[] consumersOf;
    private boolean built;
    private int industryCount;

    /**
     * Baut den Rezeptgraph aus {@code SETT.ROOMS().industries.all}.
     * Idempotent — zweiter Aufruf nach unveränderter Engine ist No-Op.
     * MUSS nach Sim-Bootstrap aufgerufen werden (nicht im clinit).
     */
    @SuppressWarnings("unchecked")
    public void build() {
        int goods = RESOURCES.ALL().size();
        if (this.built && this.producersOf != null && this.producersOf.length == goods) {
            return;
        }
        this.producersOf = new List[goods];
        this.consumersOf = new List[goods];
        for (int i = 0; i < goods; ++i) {
            this.producersOf[i] = new ArrayList<>();
            this.consumersOf[i] = new ArrayList<>();
        }
        this.industryCount = 0;
        for (Industry industry : SETT.ROOMS().industries.all) {
            ++this.industryCount;
            // Outputs: diese Industrie produziert diese Ressourcen
            for (int o = 0; o < industry.outs().size(); ++o) {
                int idx = industry.outs().get(o).resource.index();
                if (idx >= 0 && idx < goods) {
                    this.producersOf[idx].add(industry);
                }
            }
            // Inputs: diese Industrie verbraucht diese Ressourcen
            for (int i2 = 0; i2 < industry.ins().size(); ++i2) {
                int idx = industry.ins().get(i2).resource.index();
                if (idx >= 0 && idx < goods) {
                    this.consumersOf[idx].add(industry);
                }
            }
        }
        this.built = true;
    }

    /** Gibt zurück ob der Graph gebaut wurde. */
    public boolean isBuilt() { return this.built; }

    /** Anzahl der registrierten Industrien. */
    public int industryCount() { return this.industryCount; }

    /**
     * Unmodifiable Liste der Industrien die Ressource {@code res} produzieren.
     * @return nie null, kann leer sein
     */
    public List<Industry> getProducers(RESOURCE res) {
        if (!this.built || res == null) return Collections.emptyList();
        int idx = res.index();
        if (idx < 0 || idx >= this.producersOf.length) return Collections.emptyList();
        return Collections.unmodifiableList(this.producersOf[idx]);
    }

    /**
     * Unmodifiable Liste der Industrien die Ressource {@code res} verbrauchen.
     * @return nie null, kann leer sein
     */
    public List<Industry> getConsumers(RESOURCE res) {
        if (!this.built || res == null) return Collections.emptyList();
        int idx = res.index();
        if (idx < 0 || idx >= this.consumersOf.length) return Collections.emptyList();
        return Collections.unmodifiableList(this.consumersOf[idx]);
    }

    /**
     * Zählt die direkten Downstream-Konsumenten einer Ressource.
     * Nützlich für Advisor: „Holz-Knappheit betrifft 3 Industrien".
     */
    public int consumerCount(RESOURCE res) {
        return getConsumers(res).size();
    }

    /**
     * Prüft ob {@code upstream} direkt von einer Industrie verbraucht wird,
     * die {@code downstream} produziert. BFS über 2 Ebenen.
     * Kein vollständiger Transitive-Closure (Performance-Grund).
     */
    public boolean isUpstream(RESOURCE upstream, RESOURCE downstream) {
        if (!this.built || upstream == null || downstream == null) return false;
        // Alle Industrien die downstream produzieren
        List<Industry> producers = getProducers(downstream);
        for (Industry ind : producers) {
            // Prüfe ob eine davon upstream verbraucht
            for (int i = 0; i < ind.ins().size(); ++i) {
                if (ind.ins().get(i).resource == upstream) return true;
            }
        }
        return false;
    }

    /**
     * Findet alle Ressourcen die direkt von einer Industrie verbraucht werden,
     * die {@code output} produziert. Gibt die Input-Ressourcen zurück.
     * Nützlich für: „Was brauche ich um Möbel zu produzieren?"
     */
    public List<RESOURCE> directInputsFor(RESOURCE output) {
        if (!this.built || output == null) return Collections.emptyList();
        List<Industry> producers = getProducers(output);
        List<RESOURCE> result = new ArrayList<>();
        for (Industry ind : producers) {
            for (int i = 0; i < ind.ins().size(); ++i) {
                RESOURCE r = ind.ins().get(i).resource;
                if (!result.contains(r)) result.add(r);
            }
        }
        return result;
    }
}
