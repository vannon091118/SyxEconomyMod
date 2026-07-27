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
import vannon.syx.economy.core.EconomySim;

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

    public static void setSiblings(EconWindowBase overview, EconWindowBase economy, EconWindowBase state) {
        winOverview = overview;
        winEconomy = economy;
        winState = state;
    }

    /** Counter for window stacking offset. Each open window shifts slightly right+down. */
    private static int openCount = 0;
    private static final int STACK_OFFSET = 24;

    private InterGuisection inter;
    private int activeTab = 0;

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

            auditStack();
            incrementStack();
            position(root);
            inter.activate(root);
        }
    }

    /** Closes the window if open. */
    public void close() {
        if (inter != null) {
            InterGuisection old = inter;
            inter = null;
            decrementStack();
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

    /** Override to position the root section; default centers on screen with
     *  stacking offset — each successive open window shifts right+down by 24px. */
    protected void position(GuiSection root) {
        Rec b = (Rec) root.body();
        b.centerIn(0, C.WIDTH(), 0, C.HEIGHT());
        b.moveX1Y1(STACK_OFFSET * openCount, STACK_OFFSET * openCount);
    }

    /** Called by toggle() when opening — increments the shared window counter. */
    private void incrementStack() {
        // Don't double-count if already shown (tab switching re-opens)
        if (!isShown()) openCount++;
    }

    /** Called by close() — decrements the shared window counter. */
    private void decrementStack() {
        if (openCount > 0) openCount--;
    }

    /** Defensive: zaehlt tatsaechlich offene Fenster und korrigiert openCount.
     *  Faengt Faeble ab, wo Vanilla-UI-Manager Fenster schliesst ohne close(). */
    private static void auditStack() {
        int actual = 0;
        if (winOverview != null && winOverview.isShown()) actual++;
        if (winEconomy != null && winEconomy.isShown()) actual++;
        if (winState != null && winState.isShown()) actual++;
        openCount = actual;
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

        // Active tab content
        int contentY = tabY + tabH + 8;
        int contentH = panelH - contentY - 8;
        tabs[this.activeTab].build(this.sim, content, 12, contentY, innerW, contentH);
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
    protected static void addKpi(GuiSection section, int x, int y, String label, String value, COLOR valueColor) {
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
    protected static void addKpi(GuiSection section, int x, int y, SPRITE icon, String label, String value, COLOR valueColor) {
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

    /** Live slider — value is re-read every frame via IntSupplier. */
    protected static void addSlider(GuiSection section, int x, int y,
                                     String label, IntSupplier currentSupplier,
                                     int min, int max, int step,
                                     ACTION plusAction, ACTION minusAction, String suffix) {
        new LiveSlider(section, x, y, label, currentSupplier, min, max, suffix, plusAction, minusAction);
    }

    /** Live slider with " D" suffix. */
    protected static void addSlider(GuiSection section, int x, int y,
                                     String label, IntSupplier currentSupplier,
                                     int min, int max, int step,
                                     ACTION plusAction, ACTION minusAction) {
        addSlider(section, x, y, label, currentSupplier, min, max, step, plusAction, minusAction, " D");
    }

    /** Static snapshot slider (non-live). Value captured once at creation. */
    protected static void addSlider(GuiSection section, int x, int y,
                                     String label, int current, int min, int max, int step,
                                     ACTION plusAction, ACTION minusAction, String suffix) {
        int captured = current;
        new LiveSlider(section, x, y, label, () -> captured, min, max, suffix, plusAction, minusAction);
    }

    /** Static snapshot with " D" suffix. */
    protected static void addSlider(GuiSection section, int x, int y,
                                     String label, int current, int min, int max, int step,
                                     ACTION plusAction, ACTION minusAction) {
        addSlider(section, x, y, label, current, min, max, step, plusAction, minusAction, " D");
    }

    /** Column header in UI.FONT().S for table layouts. */
    protected static void addColHeader(GuiSection section, int x, int y, String label, int w) {
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

    // ─── Switcher helpers (unused by quickview) ──────────────────────

    /** Returns the EconomySim instance for HUD traffic-light computation. */
    public EconomySim getSim() { return sim; }

    /** Returns the overview window reference (for switcher buttons). */
    protected static EconWindowBase winOverview() { return winOverview; }
    /** Returns the economy window reference. */
    protected static EconWindowBase winEconomy() { return winEconomy; }
    /** Returns the state window reference. */
    protected static EconWindowBase winState() { return winState; }
}
