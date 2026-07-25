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
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.gui.misc.GText;
import util.gui.panel.GPanel;
import view.interrupter.InterGuisection;
import view.interrupter.InterManager;
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
            panel.setTitle(title());
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
            inter.close();
            inter = null;
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

    /** Override to position the root section; default centers on screen.
     *  snake2d {@link Rec#centerIn} takes (left, right, top, bottom). An older call
     *  passed (0, 0, WIDTH(), HEIGHT()) — degenerate X-range + off-screen Y-range —
     *  which placed the panel at a negative-X / below-screen position invisible to
     *  the player. Quickview bypasses this by overriding position(). */
    protected void position(GuiSection root) {
        Rec b = (Rec) root.body();
        b.centerIn(0, C.WIDTH(), 0, C.HEIGHT());
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

        int innerW = panelW - 24;

        // Tab bar layout constants (used by both nav strip and tab bar)
        int tabY = 6;
        int tabH = 22;
        int tabGap = 4;

        // ── Navigation strip (top-right, below tab bar) ───────
        int navY = tabY + tabH + 4;
        int navGap = 4;
        String[] navLabels = {"Uebersicht", "Wirtschaft", "Staat"};
        EconWindowBase[] navTargets = {winOverview, winEconomy, winState};
        int navX = panelW - 12;
        for (int i = navLabels.length - 1; i >= 0; i--) {
            if (navTargets[i] == null || navTargets[i] == this) continue;
            final EconWindowBase target = navTargets[i];
            GButt.ButtPanel navBtn = new GButt.ButtPanel(navLabels[i], 80);
            navBtn.hoverInfoSet("Oeffne " + navLabels[i] + "-Fenster");
            navBtn.clickActionSet(new ACTION() {
                @Override public void exe() { target.toggle(); }
            });
            content.add(navBtn, navX - 80 - navGap, navY);
            navX -= (80 + navGap);
        }

        // Tab bar
        int tabX = 12;
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            GText label = new GText(UI.FONT().S, 64);
            label.clear().add(tabs[i].title());
            // Minimum 120px wide — prevents truncation of labels like "Demografie", "Soziales"
            int tw = Math.max(120, label.width() + 28);
            boolean active = (i == this.activeTab);
            GButt.ButtPanel tabBtn = new GButt.ButtPanel(tabs[i].title(), tw);
            tabBtn.hoverInfoSet(tabs[i].title());
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

    /** Override for window width. Default: 780. */
    protected int panelWidth() {
        return 780;
    }

    /** Override for window height. Default: 520. */
    protected int panelHeight() {
        return 520;
    }

    // ─── Shared widget helpers ───────────────────────────────────────

    /** KPI label+value pair. Label in UI.FONT().S, value in UI.FONT().M. */
    protected static void addKpi(GuiSection section, int x, int y, String label, String value, COLOR valueColor) {
        GText lbl = new GText(UI.FONT().S, 128);
        lbl.set(label);
        lbl.color(GCOLOR.T().NORMAL);
        section.add(lbl, x, y);

        GText val = new GText(UI.FONT().M, 128);
        val.set(value);
        val.color(valueColor);
        section.add(val, x, y + 14);
    }

    /** KPI with a leading vanilla icon. Icon renders at (x, y+2), label + value shift right by 28px. */
    protected static void addKpi(GuiSection section, int x, int y, SPRITE icon, String label, String value, COLOR valueColor) {
        section.add(new RENDEROBJ.Sprite(icon), x, y + 2);
        GText lbl = new GText(UI.FONT().S, 128);
        lbl.set(label);
        lbl.color(GCOLOR.T().NORMAL);
        section.add(lbl, x + 28, y);

        GText val = new GText(UI.FONT().M, 128);
        val.set(value);
        val.color(valueColor);
        section.add(val, x + 28, y + 14);
    }

    /** Visual slider: [-] button + bar + value + [+] button. Denari suffix.
     *  Bar uses ASCII # and - (safe for game's bitmap font). */
    protected static void addSlider(GuiSection section, int x, int y,
                                     String label, int current, int min, int max, int step,
                                     ACTION plusAction, ACTION minusAction) {
        addSlider(section, x, y, label, current, min, max, step, plusAction, minusAction, " D");
    }

    /** Visual slider with custom value suffix (e.g. "%", " D", ""). */
    protected static void addSlider(GuiSection section, int x, int y,
                                     String label, int current, int min, int max, int step,
                                     ACTION plusAction, ACTION minusAction, String suffix) {
        GText lbl = new GText(UI.FONT().S, 128);
        lbl.set(label);
        lbl.color(GCOLOR.T().NORMAL);
        section.add(lbl, x, y);

        GButt.ButtPanel minus = new GButt.ButtPanel("-", 24);
        minus.clickActionSet(minusAction);
        minus.hoverInfoSet(label + " senken");
        section.add(minus, x, y + 14);

        double ratio = max > min ? (double)(current - min) / (double)(max - min) : 0;
        int filled = Math.max(0, Math.min(10, (int)(ratio * 10)));
        StringBuilder barStr = new StringBuilder();
        for (int i = 0; i < 10; i++) barStr.append(i < filled ? '#' : '-');
        COLOR barColor = filled >= 8 ? GCOLOR.UI().GOOD.normal : filled >= 4 ? GCOLOR.UI().SOSO.normal : filled > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.T().INACTIVE;

        GText bar = new GText(UI.FONT().M, 32);
        bar.set(barStr.toString());
        bar.color(barColor);
        section.add(bar, x + 32, y + 16);

        GText val = new GText(UI.FONT().M, 64);
        val.set(CompactNumber.format(current) + suffix);
        val.color(GCOLOR.T().NORMAL);
        section.add(val, x + 120, y + 16);

        GButt.ButtPanel plus = new GButt.ButtPanel("+", 24);
        plus.clickActionSet(plusAction);
        plus.hoverInfoSet(label + " erhöhen");
        section.add(plus, x + 200, y + 14);
    }

    /** Column header in UI.FONT().S for table layouts. */
    protected static void addColHeader(GuiSection section, int x, int y, String label, int w) {
        GText hdr = new GText(UI.FONT().S, 64);
        hdr.set(label);
        hdr.color(GCOLOR.T().NORMAL);
        section.add(hdr, x, y);
    }

    // ─── Switcher helpers (unused by quickview) ──────────────────────

    /** Returns the overview window reference (for switcher buttons). */
    protected static EconWindowBase winOverview() { return winOverview; }
    /** Returns the economy window reference. */
    protected static EconWindowBase winEconomy() { return winEconomy; }
    /** Returns the state window reference. */
    protected static EconWindowBase winState() { return winState; }
}
