package vannon.syx.economy.ui;

import init.constant.C;
import init.sprite.UI.UI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import snake2d.CORE;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import util.gui.misc.GBox;
import util.gui.misc.GText;
import vannon.syx.economy.core.EconomySim;
import view.interrupter.Interrupter;

public abstract class EconWindowBase extends Interrupter {
    protected final EconomySim sim;
    private final List<EconTab> tabs = new ArrayList<>();
    private final Map<String, Object> state = new HashMap<>();
    private boolean pendingLeftClick = false;
    private MButt pendingButton = null;
    private int activeTab = 0;
    private int x, y, w, h;
    private boolean shown = false;

    private final GText titleText;
    private final GText tabLabelText;
    private final GText kpiText;

    protected EconWindowBase(EconomySim sim) {
        this.sim = sim;
        this.titleText = new GText(UI.FONT().M, 128);
        this.tabLabelText = new GText(UI.FONT().S, 64);
        this.kpiText = new GText(UI.FONT().S, 128);
    }

    @Override
    protected void deactivateAction() {
        this.shown = false;
    }

    protected final void addTab(EconTab tab) {
        this.tabs.add(tab);
    }

    @Override
    public final boolean hover(COORDINATE mCoo, boolean mouseHasMoved) {
        computeWindowBounds();
        int mx = mCoo.x();
        int my = mCoo.y();
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public final void mouseClick(MButt button) {
        if (button == MButt.LEFT) {
            this.pendingLeftClick = true;
            this.pendingButton = button;
        }
    }

    @Override
    public final boolean otherClick(MButt button) {
        return false;
    }

    @Override
    public final void hoverTimer(GBox text) {
        // Tooltip rendering can be hooked here if needed.
    }

    @Override
    public final boolean update(float ds) {
        // false = Interrupter bleibt aktiv; true würde sofortiges Schließen signalisieren.
        return false;
    }

    /** Toggle this window on the current view's InterManager. */
    public void toggle() {
        if (this.shown) {
            hide();
        } else {
            view.main.VIEW.ViewSubSimple current = view.main.VIEW.current();
            if (current != null && current.uiManager != null) {
                this.shown = show(current.uiManager);
            }
        }
    }

    @Override
    public final boolean render(Renderer renderer, float ds) {
        // false = Hintergrund weiterhin rendern; true würde alles darunter blockieren.
        computeWindowBounds();
        int mx = CORE.getInput().getMouse().getCoo().x();
        int my = CORE.getInput().getMouse().getCoo().y();
        EconContext ctx = buildContext(renderer, mx, my);

        // Tab bar click handling (IMGUI)
        handleTabBarClick(ctx);

        renderWindow(ctx);

        // Remaining click goes to the active tab
        if (ctx.leftClicked && this.activeTab < this.tabs.size()) {
            this.tabs.get(this.activeTab).click(ctx, this.pendingButton != null ? this.pendingButton : MButt.LEFT);
        }

        // Hover/tooltip handling after rendering so overlays stay on top
        if (this.activeTab < this.tabs.size()) {
            this.tabs.get(this.activeTab).hover(ctx);
        }

        // End of frame: reset transient click state
        this.pendingLeftClick = false;
        this.pendingButton = null;
        return true;
    }

    private void computeWindowBounds() {
        this.x = (int) (C.WIDTH() * 0.1);
        this.y = (int) (C.HEIGHT() * 0.1);
        this.w = (int) (C.WIDTH() * 0.8);
        this.h = (int) (C.HEIGHT() * 0.8);
    }

    private void handleTabBarClick(EconContext ctx) {
        if (!ctx.leftClicked || this.tabs.isEmpty()) {
            return;
        }
        int tabX = this.x + 8;
        int tabY = this.y + 28;
        if (ctx.mouseY < tabY || ctx.mouseY > tabY + 20) {
            return;
        }
        for (int i = 0; i < this.tabs.size(); i++) {
            int tw = 90;
            if (ctx.mouseX >= tabX + i * tw && ctx.mouseX <= tabX + (i + 1) * tw) {
                this.activeTab = i;
                this.tabs.get(i).onOpen();
                ctx.consumeClick();
                return;
            }
        }
    }

    private EconContext buildContext(Renderer renderer, int mx, int my) {
        return new EconContext(
            renderer,
            this.sim,
            mx, my,
            MButt.LEFT.isDown(),
            this.pendingLeftClick,
            this.x, this.y, this.w, this.h,
            this.state);
    }

    private void renderWindow(EconContext ctx) {
        // Frame
        COLOR.BLACK.render(ctx.renderer, this.x, this.x + this.w, this.y, this.y + this.h);
        COLOR.WHITE100.render(ctx.renderer, this.x, this.x + this.w, this.y, this.y + 2);

        // Title
        titleText.clear();
        titleText.add(windowTitle());
        titleText.color(COLOR.WHITE100);
        titleText.render(ctx.renderer, this.x + 8, this.x + this.w - 8, this.y + 6, this.y + 24);

        // Tab bar
        int tabX = this.x + 8;
        int tabY = this.y + 28;
        for (int i = 0; i < this.tabs.size(); i++) {
            COLOR c = (i == this.activeTab) ? COLOR.WHITE100 : COLOR.WHITE35;
            c.render(ctx.renderer, tabX + i * 90, tabX + (i + 1) * 90 - 4, tabY, tabY + 20);
            tabLabelText.clear();
            tabLabelText.add(this.tabs.get(i).title());
            tabLabelText.color(COLOR.WHITE200);
            tabLabelText.render(ctx.renderer, tabX + i * 90 + 4, tabX + (i + 1) * 90 - 8, tabY + 2, tabY + 18);
        }

        // KPI header
        int kpiY = tabY + 24;
        renderKpiHeader(ctx, this.x + 8, kpiY, this.w - 16);

        // Tab content
        if (this.activeTab < this.tabs.size()) {
            this.tabs.get(this.activeTab).render(ctx, kpiY + 32);
        }
    }

    protected void renderKpiHeader(EconContext ctx, int x, int y, int w) {
        // Default KPI header: treasury, gini, stage.
        kpiText.clear();
        kpiText.add("Treasury: ").add(sim.treasury()).add(" | Gini: ").add(String.format("%.2f", sim.stats().gini)).add(" | Stage: ").add(sim.progression().stage.displayName);
        kpiText.color(COLOR.WHITE150);
        kpiText.render(ctx.renderer, x, x + w, y, y + 16);
    }

    protected abstract CharSequence windowTitle();
}
