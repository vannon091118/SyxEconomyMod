package vannon.syx.economy.ui.tabs.Overview;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.gui.GuiSection;
import snake2d.util.misc.ACTION;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.gui.misc.GText;
import vannon.syx.economy.core.DiagnosticExporter;
import vannon.syx.economy.core.EconIndicators;
import vannon.syx.economy.core.EconProgression;
import vannon.syx.economy.core.EconSnapshot;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.WealthStats;

/**
 * Sprint v0.13.106+M-UI-3 Tab-Modul-Split — Shared UI-Helpers für die 4 Overview-Tabs.
 *
 * <p>Ehemalige private static helpers aus WindowOverview (948 LOC). Konsolidiert
 * in einem package-public File damit alle 4 Tabs (Dashboard/Demographics/Advisor/
 * Property) ohne Duplikation darauf zugreifen können. Single SSoT für:</p>
 * <ul>
 *   <li>Health-Ampel: addTrafficLight, addTrendArrow, addMilestoneIcon</li>
 *   <li>Status-Text: allClear, buildStatusText</li>
 *   <li>Advisor-Logic: nextStageReqs, buildWarningChains,
 *       countChainAffected, CHAIN_IMPACT_THRESHOLD</li>
 *   <li>Visuals: coloredBar, countLines</li>
 *   <li>Property-Toggle: addCheckbox</li>
 * </ul>
 *
 * <p>Rule-15 konform: keine {@code static final}-Init mit Engine-Singletons.
 * Rule-14 God-Class-Guard: bleibt unter allen Hard-Blocks (~290 SLOC).</p>
 */
public final class OverviewHelpers {

    private OverviewHelpers() {}

    /** Mindest-Total-Koeffizient um eine Ressource als kettenbetroffen zu zählen.
     *  L[i,j] > 0.1 bedeutet: für jede Einheit mehr von Ressource j werden
     *  0.1 Einheiten von Ressource i über die Kette benötigt. */
    public static final double CHAIN_IMPACT_THRESHOLD = 0.1;

    // ─── Visuals ──────────────────────────────────────────────────────

    /** Plain colored rectangle (no text, no border). COLOR.render() in render override
     *  — no bitmap font dependency. */
    public static GuiSection coloredBar(final COLOR color, int w, int h) {
        GuiSection bar = new GuiSection() {
            @Override
            public void render(SPRITE_RENDERER r, float ds) {
                color.render(r, body());
                super.render(r, ds);
            }
        };
        bar.body().setDim(w, h);
        return bar;
    }

    /** Traffic-light row: state −1=gray (inactive), 0=red, 1=yellow, 2=green.
     *  Renders colored bar + label. */
    public static void addTrafficLight(GuiSection section, int x, int y, String label, int state) {
        COLOR barColor;
        switch (state) {
            case 2:  barColor = GCOLOR.UI().GOOD.normal; break;
            case 1:  barColor = GCOLOR.UI().SOSO.normal; break;
            case 0:  barColor = GCOLOR.UI().BAD.normal; break;
            default: barColor = GCOLOR.T().INACTIVE; break;
        }
        section.add(coloredBar(barColor, 50, 10), x, y + 4);

        GText lbl = new GText(UI.FONT().M,
                vannon.syx.economy.ui.EconWindowBase.FONTW_KPI);
        lbl.set(label);
        lbl.color(state >= 0 ? GCOLOR.T().NORMAL : GCOLOR.T().INACTIVE);
        section.add(lbl, x + 55, y);
    }

    /** Milestone row: icon (allRight/cancel) + label. Used in Advisor progression. */
    public static void addMilestoneIcon(GuiSection section, int x, int y, String label, boolean achieved) {
        if (achieved) {
            section.add(UI.icons().s.allRight, x, y + 2);
        } else {
            section.add(UI.icons().s.cancel, x, y + 2);
        }
        GText ms = new GText(UI.FONT().M,
                vannon.syx.economy.ui.EconWindowBase.FONTW_HDR);
        ms.set(label);
        ms.color(achieved ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
        section.add(ms, x + 20, y);
    }

    /** Count "{@code \n}" line-separators in multiline string. */
    public static int countLines(String s) {
        if (s == null || s.isEmpty()) return 0;
        int n = 1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') n++;
        }
        return n;
    }

    // ─── Health-Ampel ────────────────────────────────────────────────

    /** True wenn keine der 5 Krisen-Indikatoren aktiv ist. */
    public static boolean allClear(EconIndicators ind) {
        return !ind.isInequalityRising() && !ind.isWagesFalling()
            && !ind.isTreasuryDeclining() && !ind.isEmigrationSpike()
            && !ind.isFurnishingCrisis();
    }

    /** Sammelt aktive Krisen-Signale in einen kurzen Status-Text. */
    public static String buildStatusText(EconIndicators ind, WealthStats stats, long treasury) {
        if (stats.people == 0) {
            return "Keine Bevoelkerung erfasst — Wirtschaftsdaten noch nicht verfuegbar.";
        }
        StringBuilder sb = new StringBuilder();
        if (treasury < 0) sb.append("Staatskasse negativ! ");
        if (ind.isInequalityRising()) sb.append("Gini steigt! ");
        if (ind.isWagesFalling()) sb.append("Loehne sinken! ");
        if (ind.isTreasuryDeclining()) sb.append("Einnahmen ruecklaeufig! ");
        if (ind.isEmigrationSpike()) sb.append("Abwanderung! ");
        if (ind.isFurnishingCrisis()) sb.append("Einrichtungskrise! ");
        if (sb.length() == 0) {
            return "Wirtschaft stabil — keine Warnungen.";
        }
        return sb.toString().trim();
    }

    /** Trend arrow based on latest-vs-previous snapshot delta. Renders vanilla icon. */
    public static void addTrendArrow(GuiSection section, int x, int y, EconIndicators ind, String field) {
        if (ind.count() < 2) return;
        EconSnapshot latest = ind.latest();
        EconSnapshot prev = ind.get(ind.count() - 2);
        if (latest == null || prev == null) return;

        double curr = getSnapshotField(latest, field);
        double past = getSnapshotField(prev, field);

        double delta = curr - past;
        if (Math.abs(delta) < 0.001) {
            section.add(UI.icons().s.arrow_right, x, y + 2);
        } else if (delta > 0) {
            section.add(UI.icons().s.arrowUp, x, y + 2);
        } else {
            section.add(UI.icons().s.arrowDown, x, y + 2);
        }
    }

    /** Read named field from {@code EconSnapshot} via string switch (Compiler optimizes
     *  to Map-get on HotSpot). */
    public static double getSnapshotField(EconSnapshot snap, String field) {
        switch (field) {
            case "treasuryCurrent": return snap.treasuryCurrent;
            case "gini": return snap.gini;
            default: return 0;
        }
    }

    // ─── Advisor-Logic ───────────────────────────────────────────────

    /** Anforderungen an die naechste Stufe als Text. */
    public static String nextStageReqs(EconProgression prog, WealthStats stats) {
        switch (prog.stage) {
            case SUBSISTENZ:
                return "50 Siedler, Lagerhaus, 3d Nahrung, 30 Tage in Stufe";
            case HANDEL:
                return "100 Siedler, Export, Lohn>50, Taverne/Markt";
            case INDUSTRIE:
                return "150 Siedler, Labor, Bibliothek, Militaer, 30 Tage";
            case WOHLSTAND:
                return "200 Siedler, 100d ohne Insolvenz, Gini<0.35 30d, Export>10K";
            default:
                return "Maximale Stufe erreicht!";
        }
    }

    /** Builds causal warning chains from current indicator state.
     *  Returns empty string if all clear. */
    public static String buildWarningChains(EconIndicators ind, long treasury, WealthStats stats) {
        StringBuilder sb = new StringBuilder();
        if (treasury < -10000) {
            sb.append("Schuldenkrise >> Sparzwang >> Lohnsenkung >> Abwanderung\n");
        } else if (treasury < 0) {
            sb.append("Kasse negativ >> Export noetig >> Steueranpassung\n");
        }
        if (ind.isFurnishingCrisis()) {
            sb.append("Holzmangel >> Einrichtungskrise >> Produktionsstillstand\n");
        }
        if (ind.isWagesFalling() && ind.isInequalityRising()) {
            sb.append("Lohnrueckgang + Gini-Anstieg >> Kaufkraftverlust >> Nachfragerueckgang\n");
        } else if (ind.isWagesFalling()) {
            sb.append("Lohnrueckgang >> Kaufkraftverlust >> Firmen-Umsatzrueckgang\n");
        } else if (ind.isInequalityRising()) {
            sb.append("Gini-Anstieg >> Vermoegenskonzentration >> Loyalitaetsverlust");
            if (stats.people > 0 && stats.gini > 0.40) sb.append(" (kritisch)");
            sb.append("\n");
        }
        if (ind.isEmigrationSpike() && ind.isWagesFalling()) {
            sb.append("Lohnrueckgang + Abwanderung >> Arbeitskraeftemangel >> Produktionseinbruch\n");
        } else if (ind.isEmigrationSpike()) {
            sb.append("Abwanderung >> Arbeitskraeftemangel >> Loehne erhoehen\n");
        }
        if (ind.isTreasuryDeclining() && !ind.isFurnishingCrisis()) {
            sb.append("Einnahmen ruecklaeufig >> Pruefe Steuersaetze >> Marktsteuer/Liturgie aktivieren\n");
        }
        // IO-Analysis: Chain bottleneck warning (Leontief-aware)
        EconomySim sim = EconomySim.active();
        if (sim != null && sim.ioMatrix() != null && sim.ioMatrix().isValid()) {
            for (int i = 0; i < sim.ioMatrix().size() && i < RESOURCES.ALL().size(); ++i) {
                if (sim.flowPrices().coverage(i) >= 0.5) continue;
                int affected = countChainAffected(sim, i);
                if (affected >= 3) {
                    RESOURCE res = (RESOURCE) RESOURCES.ALL().get(i);
                    sb.append(res.name).append("-Mangel >> Ketten-Engpass >> ").append(affected).append(" Ressourcen in der Kette betroffen\n");
                }
            }
        }
        return sb.toString().trim();
    }

    /** Zählt wie viele Ressourcen über die Leontief-Kette von Ressource
     *  {@code resourceIdx} betroffen sind (Total-Koeffizient > CHAIN_IMPACT_THRESHOLD).
     *  Nutzt {@code computeTotalRequirements()} mit Einheits-Nachfrage-Schock. */
    public static int countChainAffected(EconomySim sim, int resourceIdx) {
        if (sim.ioMatrix() == null || !sim.ioMatrix().isValid()) return 0;
        int n = sim.ioMatrix().size();
        if (resourceIdx < 0 || resourceIdx >= n) return 0;
        double[] demandShock = new double[n];
        demandShock[resourceIdx] = 1.0;
        double[] totalReqs = sim.ioMatrix().computeTotalRequirements(demandShock);
        int affected = 0;
        for (int j = 0; j < n; ++j) {
            if (j != resourceIdx && totalReqs[j] > CHAIN_IMPACT_THRESHOLD) ++affected;
        }
        return affected;
    }

    // ─── Property-Toggle ─────────────────────────────────────────────

    /** Reusable checkbox helper for PropertyTab — encapsulates ClickActionSet +
     *  logPlayerAction diagnostic. PropertyTab-spezifisch (WindowState hat
     *  eigene private addCheckbox). */
    public static void addCheckbox(GuiSection section, int x, int y, String label,
                                    boolean initial, java.util.function.Consumer<Boolean> setter) {
        GButt.Checkbox cb = new GButt.Checkbox(label);
        cb.selectedSet(initial);
        cb.clickActionSet(new ACTION() {
            @Override public void exe() {
                boolean next = !cb.selectedIs();
                DiagnosticExporter.logPlayerAction("overview.toggle", label);
                setter.accept(next);
                cb.selectedSet(next);
            }
        });
        section.add(cb, x, y);
    }
}
