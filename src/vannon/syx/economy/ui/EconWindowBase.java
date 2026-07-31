package vannon.syx.economy.ui;

import init.constant.C;
import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.Rec;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.misc.ACTION;
import snake2d.util.sprite.SPRITE;
import java.util.function.IntSupplier;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.gui.misc.GText;
import util.gui.panel.GPanel;
import view.interrupter.InterGuisection;
import view.interrupter.InterManager;
import vannon.syx.economy.core.BuildStamp;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.DiagnosticExporter;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.EventLog;

/**
 * Vanilla-Komponenten-basierte Basis für alle Economy-Fenster.
 * Unterstützt 1–3 Tabs via {@link TabContent}. Fenster ohne Tabs
 * überschreiben einfach {@link #build(GPanel, GuiSection)} direkt.
 */
public abstract class EconWindowBase {

    protected final EconomySim sim;

    private static EconWindowBase winOverview;
    private static EconWindowBase winEconomy;
    private static EconWindowBase winState;
    private static EconWindowBase winQuickview;

    public static void setSiblings(EconWindowBase overview, EconWindowBase economy, EconWindowBase state, EconWindowBase quickview) {
        winOverview = overview;
        winEconomy = economy;
        winState = state;
        winQuickview = quickview;
    }

    /** Fixed anchor position per window type — no more diagonal drift. */
    // Each subclass implements anchorX()/anchorY() for its fixed corner.
    // This replaces the old STACK_OFFSET/openCount diagonal stacking.

    private InterGuisection inter;
    private int activeTab = 0;

    /** Sets the active tab index (for external tab switching). */
    public void setActiveTab(int index) {
        this.activeTab = index;
    }

    /** Interface for tab-based content. Implement in inner classes. */
    public interface TabContent {
        CharSequence title();
        void build(EconomySim sim, GuiSection content, int x, int y, int w, int h);
    }

    protected EconWindowBase(EconomySim sim) {
        this.sim = sim;
    }

    /** Toggles the window open/closed. Preserves activeTab for tab switching. */
    public void toggle() {
        if (isShown()) {
            close();
        } else {
            InterManager manager = currentManager();
            if (manager == null) return;
            inter = new InterGuisection(manager);

            GPanel panel = new GPanel();
            panel.setTitle(stampedTitle());
            panel.setCloseAction(new ACTION() {
                @Override
                public void exe() {
                    close();
                }
            });

            GuiSection content = new GuiSection();
            build(panel, content);

            GuiSection root = new GuiSection() {
                @Override
                public void render(SPRITE_RENDERER r, float ds) {
                    Rec inner = panel.inner();
                    GCOLOR.UI().panBG.render(r, inner.x1(), inner.x2(), inner.y1(), inner.y2());
                    GCOLOR.UI().borderH(r, inner, 0);
                    super.render(r, ds);
                }
            };
            root.add(panel, 0, 0);
            root.add(content, panel.inner().x1(), panel.inner().y1());

            position(root);
            inter.activate(root);
        }
    }

    /** Closes the window if open. */
    public void close() {
        if (inter != null) {
            InterGuisection old = inter;
            inter = null;
            old.close();
        }
    }

    /** True if this window is currently active/open. */
    public boolean isShown() {
        return inter != null && inter.current() != null;
    }

    private InterManager currentManager() {
        try {
            return view.main.VIEW.current().uiManager;
        } catch (Exception e) {
            return null;
        }
    }

    /** Override to position the root section at a fixed anchor per window type.
     *  Default centers on screen (legacy behavior); subclasses should override
     *  with anchorX()/anchorY() for consistent positions across sessions. */
    protected void position(GuiSection root) {
        Rec b = (Rec) root.body();
        b.moveX1Y1(anchorX(), anchorY());
    }

    /** X anchor for this window type. Override to fix position. */
    protected int anchorX() {
        return (C.WIDTH() - panelWidth()) / 2;
    }

    /** Y anchor for this window type. Override to fix position. */
    protected int anchorY() {
        return (C.HEIGHT() - panelHeight()) / 2;
    }

    /** Window title shown in the GPanel header. */
    protected abstract CharSequence title();

    /**
     * Build the panel content. Default implementation renders tabs if
     * {@link #tabs()} returns a non-empty array; subclasses can override
     * for non-tabbed windows.
     */
    protected void build(GPanel background, GuiSection content) {
        int panelW = panelWidth();
        int panelH = panelHeight();
        background.setDim(panelW, panelH);

        TabContent[] tabs = tabs();
        if (tabs == null || tabs.length == 0) return;

        // Safety: if activeTab exceeds tab count (e.g. Debug tab removed), reset to 0
        if (this.activeTab >= tabs.length) this.activeTab = 0;

        int innerW = panelW - 24;

        int tabY = 6;
        int tabH = 22;
        int tabGap = 4;
        int tabX = 12;
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            GText label = new GText(UI.FONT().S, FONTW_LABEL);
            label.clear().add(tabs[i].title());
            // Minimum 120px wide — prevents truncation of labels like "Demografie", "Soziales"
            int tw = Math.max(140, label.width() + 32);
            boolean active = (i == this.activeTab);
            GButt.ButtPanel tabBtn = new GButt.ButtPanel(tabs[i].title(), tw);
            tabBtn.clickActionSet(new ACTION() {
                @Override
                public void exe() {
                    DiagnosticExporter.logPlayerAction("tab_switch", "tab=" + idx);
                    activeTab = idx;
                    close();
                    toggle();
                }
            });
            if (active) {
                tabBtn.selectedSet(true);
            }
            content.add(tabBtn, tabX, tabY);
            tabX += tw + tabGap;
        }

        // Active tab content (Sprint v0.13.104+M-UI-1: mit Error-Boundary)
        int contentY = tabY + tabH + 8;
        int contentH = panelH - contentY - 8;
        TabContent activeTab = tabs[this.activeTab];
        try {
            activeTab.build(this.sim, content, 12, contentY, innerW, contentH);
        } catch (Exception t) {
            // Sprint M-UI-1 Review-Fix: Throwable → Exception. VM-Errors (OOM, SOE) muessen
            // den Crash-Pfad weiter laufen — kein silent swallow. NPE/IllegalState gehoert
            // hierhin (Tab-Build hat Engine-State verletzt).
            onTabBuildError(activeTab, t, content, contentY);
        }
    }

    /**
     * Sprint v0.13.104+M-UI-1 — render a friendly error placeholder when a
     * tab-build throws. Spieler sieht "Tab konnte nicht geladen werden" +
     * Diagnostik-Hinweis. Statt rohem Crash sind EventLog + DiagnosticExporter
     * mit Race-Condition-Trail gefüllt. Verhindert dass NPE in tab.build() das
     * ganze Fenster leert (Audit Q4.2).
     */
    private void onTabBuildError(TabContent tab, Throwable t, GuiSection content, int contentY) {
        CharSequence title = tab != null ? tab.title() : "?";
        String tabTitle = title != null ? title.toString() : "?";
        EventLog.log("WINDOW_BUILD", "Tab '" + tabTitle + "' threw "
                + t.getClass().getSimpleName() + ": " + t.getMessage());
        DiagnosticExporter.logPlayerAction("window_build_error",
                tabTitle + ":" + t.getClass().getSimpleName());
        GText errorH = new GText(UI.FONT().M, FONTW_HDR);
        errorH.set("--- Tab-Fehler ---");
        errorH.lablify();
        errorH.color(GCOLOR.UI().BAD.normal);
        content.add(errorH, 12, contentY);
        GText errorB = new GText(UI.FONT().S, FONTW_BODY);
        errorB.set("Tab '" + tabTitle + "' konnte nicht geladen werden: "
                + t.getClass().getSimpleName() + ". EventLog enthaelt Details — siehe Debug-Tab.");
        errorB.color(GCOLOR.UI().BAD.normal);
        content.add(errorB, 12, contentY + 22);
    }

    /** Override to provide tabs. Default: null (no tabs). */
    protected TabContent[] tabs() {
        return null;
    }

    /** Override for window width. Default: 840. */
    protected int panelWidth() {
        return 840;
    }

    /** Override for window height. Default: 620. */
    protected int panelHeight() {
        return 620;
    }

    // ─── Centralized UI Sizing (2026-07-26, Notiz 4) ──────────────────
    // Every GText max-width in this project routes through these constants.
    // Change here to resize ALL UI text at once — no more 122-line scavenger hunt.

    /** Tab labels, column headers, button labels. Default: 64. */
    public static final int FONTW_LABEL    = 64;
    /** KPI labels and values, slider labels. Default: 128. U-01: 128→144 — "-1.9M D" Overflow bei Treasury-Werten. */
    public static final int FONTW_KPI    = 144;
    /** Section headers, tutorial headers, chart labels. Default: 256. */
    public static final int FONTW_HDR    = 256;
    /** Tutorial body, event log, advice, status messages. Default: 512. */
    public static final int FONTW_BODY   = 512;
    /** Small counters: employees, wealth columns, trend values. Default: 48. */
    public static final int FONTW_CNT    = 48;
    /** Tiny labels: timeline day, version stamp. Default: 32. */
    public static final int FONTW_TINY   = 32;
    /** Resource names, blueprint keys. Default: 100. */
    public static final int FONTW_NAME   = 100;
    /** LiveSlider value. Default: 80. */
    public static final int FONTW_SLVAL  = 96;  // U-02: 80→96 — "####-500D#" Overflow bei negativen Werten (Kopfsteuer)
    /** LiveSlider bar. Default: 120. */
    public static final int FONTW_SLBAR  = 120;
    /** Wage columns, medium metrics. Default: 56. */
    public static final int FONTW_MED    = 56;
    /** Compact labels: band averages, food stats. Default: 64. */

    // ─── Shared widget helpers ───────────────────────────────────────

    /** KPI label+value pair. Label in UI.FONT().S, value in UI.FONT().M. */
    public static void addKpi(GuiSection section, int x, int y, String label, String value, COLOR valueColor) {
        GText lbl = new GText(UI.FONT().S, FONTW_KPI);
        lbl.set(label);
        lbl.color(GCOLOR.T().NORMAL);
        section.add(lbl, x, y);

        GText val = new GText(UI.FONT().M, FONTW_KPI);
        val.set(value);
        val.color(valueColor);
        section.add(val, x, y + 16);
    }

    /** KPI with a leading vanilla icon. Icon renders at (x, y+2), label + value shift right by 28px. */
    public static void addKpi(GuiSection section, int x, int y, SPRITE icon, String label, String value, COLOR valueColor) {
        section.add(new RENDEROBJ.Sprite(icon), x, y + 2);
        GText lbl = new GText(UI.FONT().S, FONTW_KPI);
        lbl.set(label);
        lbl.color(GCOLOR.T().NORMAL);
        section.add(lbl, x + 28, y);

        GText val = new GText(UI.FONT().M, FONTW_KPI);
        val.set(value);
        val.color(valueColor);
        section.add(val, x + 28, y + 16);
    }

    // ─── Live Slider ────────────────────────────────────────────

    /**
     * Live-updating slider. Bar and value text re-read the current value
     * from {@link IntSupplier} every frame, so external changes (e.g.
     * Numpad commands) are reflected immediately without closing/reopening.
     *
     * Layout: label, [-] button, 10-char bar, value, [+] button.
     * Bar uses ASCII '#' (filled) and '-' (empty) — safe for bitmap font.
     */
    private static final class LiveSlider extends GuiSection {
        private final GText bar;
        private final GText val;
        private final IntSupplier supplier;
        private final int min, max;
        private final String suffix;

        LiveSlider(GuiSection parent, int x, int y, String label,
                    IntSupplier supplier, int min, int max,
                    String suffix, ACTION plusAction, ACTION minusAction) {
            this.supplier = supplier;
            this.min = min;
            this.max = max;
            this.suffix = suffix;

            GText lbl = new GText(UI.FONT().S, FONTW_KPI);
            lbl.set(label);
            lbl.color(GCOLOR.T().NORMAL);
            add(lbl, 0, 0);

            GButt.ButtPanel minus = new GButt.ButtPanel("-", 24);
            minus.clickActionSet(minusAction);
            add(minus, 0, 14);

            bar = new GText(UI.FONT().M, FONTW_SLBAR);
            add(bar, 32, 16);

            val = new GText(UI.FONT().M, FONTW_SLVAL);
            add(val, 112, 16);

            GButt.ButtPanel plus = new GButt.ButtPanel("+", 24);
            plus.clickActionSet(plusAction);
            add(plus, 216, 14);

            // Initial render with current value
            updateDisplay(supplier.getAsInt());

            parent.add(this, x, y);
        }

        @Override
        public void render(SPRITE_RENDERER r, float ds) {
            updateDisplay(supplier.getAsInt());
            super.render(r, ds);
        }

        private void updateDisplay(int current) {
            double ratio = max > min ? (double)(current - min) / (double)(max - min) : 0;
            int filled = Math.max(0, Math.min(10, (int)(ratio * 10)));
            StringBuilder sb = new StringBuilder(10);
            for (int i = 0; i < 10; i++) sb.append(i < filled ? '#' : '-');
            bar.set(sb.toString());
            bar.color(filled >= 8 ? GCOLOR.UI().GOOD.normal
                   : filled >= 4 ? GCOLOR.UI().SOSO.normal
                   : filled > 0  ? GCOLOR.UI().BAD.normal
                   :                GCOLOR.T().INACTIVE);

            val.set(CompactNumber.format(current) + suffix);
        }
    }

    // ─── Slider entry points ────────────────────────────────────────

    /** Live slider — value is re-read every frame via IntSupplier. Sprint v0.13.117+:
     *  step-Parameter entfernt (LiveSlider-Konstruktor nimmt kein step; Callers
     *  implementieren step selbst in ±ACTION, z.B. `minusAction = val -> max(0, val - 5)`). */
    public static void addSlider(GuiSection section, int x, int y,
                                     String label, IntSupplier currentSupplier,
                                     int min, int max,
                                     ACTION plusAction, ACTION minusAction, String suffix) {
        new LiveSlider(section, x, y, label, currentSupplier, min, max, suffix, plusAction, minusAction);
    }

    /** Live slider with " D" suffix. */
    public static void addSlider(GuiSection section, int x, int y,
                                     String label, IntSupplier currentSupplier,
                                     int min, int max,
                                     ACTION plusAction, ACTION minusAction) {
        addSlider(section, x, y, label, currentSupplier, min, max, plusAction, minusAction, " D");
    }

    /** Static snapshot slider (non-live). Value captured once at creation. */
    public static void addSlider(GuiSection section, int x, int y,
                                     String label, int current, int min, int max,
                                     ACTION plusAction, ACTION minusAction, String suffix) {
        int captured = current;
        new LiveSlider(section, x, y, label, () -> captured, min, max, suffix, plusAction, minusAction);
    }

    /** Static snapshot with " D" suffix. */
    public static void addSlider(GuiSection section, int x, int y,
                                     String label, int current, int min, int max,
                                     ACTION plusAction, ACTION minusAction) {
        addSlider(section, x, y, label, current, min, max, plusAction, minusAction, " D");
    }

    /** Column header in UI.FONT().S for table layouts. */
    public static void addColHeader(GuiSection section, int x, int y, String label, int w) {
        GText hdr = new GText(UI.FONT().S, FONTW_LABEL);
        hdr.set(label);
        hdr.color(GCOLOR.T().NORMAL);
        section.add(hdr, x, y);
    }

    // ─── Build-Stamp helper ───────────────────────────────────────

    /** Returns the window title annotated with the build stamp.
     *  Override {@link #title()} to provide the base title; this method
     *  appends the unique build identity so you always know which
     *  compilation you are evaluating during live testing. */
    protected CharSequence stampedTitle() {
        try {
            return title() + "  [" + BuildStamp.FULL_ID + "]";
        } catch (NoClassDefFoundError e) {
            // BuildStamp not yet generated (IDE without Maven)
            return title() + "  [vDEV]";
        }
    }

    // ─── Switcher helpers (active Window-switcher, used by WindowQuickview) ─

    /** Returns the overview window reference (for switcher buttons). */
    protected static EconWindowBase winOverview() { return winOverview; }
    /** Returns the economy window reference. */
    protected static EconWindowBase winEconomy() { return winEconomy; }
    /** Returns the state window reference. */
    protected static EconWindowBase winState() { return winState; }
    /** Returns the quickview window reference. */
    protected static EconWindowBase winQuickview() { return winQuickview; }
}
