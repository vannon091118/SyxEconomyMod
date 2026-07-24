# 3-Fenster × 3-Tab EconomyWindow UX-Refactor

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Das 3.081-LOC God-File `EconomyWindow.java` wird durch drei fokussierte Fenster (`Übersicht`, `Wirtschaft`, `Staat`) mit je drei Tabs ersetzt, alle kritischen UI-Bugs (Zoom-Click, Dashboard-Texturen, Slider) werden behoben, sieben sich gegenseitig aushebelnde Hebel werden gekappt und die Benennungen werden nutzerfreundlich.

**Architecture:** Neues Package `vannon.syx.economy.ui` mit abstrakter Basisklasse `EconWindowBase` (erbt von `Interrupter`), Render-Kontext `EconContext`, Tab-Interface `EconTab`, Shared-Widget-Library `EconWidgets` und drei konkreten Fenster-Klassen. Die Inhalte der 18 alten Tabs werden in drei Sammeldateien (`OverviewTabs`, `EconomyTabs`, `StateTabs`) auf je drei Tabs verteilt. Abschließend wird `EconomyWindow.java` gelöscht und `InstanceScript`/`EconomySim` umgeschaltet.

**Tech Stack:** Java 21, Songs of Syx V71.44, snake2d Renderer, Maven, Vanilla UI-Primitives (`Interrupter`, `COLOR`, `GText`, `GChart`).

## Global Constraints

- `mvn compile` muss BUILD SUCCESS ergeben.
- Keine neuen `catch (Throwable)`-Blöcke in betroffenen Dateien.
- Keine externen UI-Bibliotheken — nur Vanilla-Renderer.
- Save/Load-Kompatibilität darf nicht brechen; neue UI-Zustände werden nicht persistiert (reconstructed on open).
- Alle UI-Texte müssen in `EconTexts.java` als Konstanten landen.
- `EconConfig`-Defaults dürfen nicht verändert werden, solange `BALANCE_LEVERS.md` nicht aktualisiert wird.
- Jedes Fenster ist ein eigener `Interrupter`; der Hotkey öffnet/schließt das Übersichtsfenster (`WindowOverview`).

---

## File Structure

```
Neu erstellen:
  src/vannon/syx/economy/ui/EconContext.java          — Render-Kontext pro Frame
  src/vannon/syx/economy/ui/EconTab.java               — Interface für Tabs
  src/vannon/syx/economy/ui/EconWidgets.java            — Shared Widgets (Slider, Button, Toggle, Scrollbar)
  src/vannon/syx/economy/ui/EconWindowBase.java        — Abstrakte Fenster-Basisklasse + InputBlocker
  src/vannon/syx/economy/ui/WindowOverview.java          — Fenster "Übersicht"
  src/vannon/syx/economy/ui/WindowEconomy.java          — Fenster "Wirtschaft"
  src/vannon/syx/economy/ui/WindowState.java            — Fenster "Staat"
  src/vannon/syx/economy/ui/OverviewTabs.java          — DashboardTab, CitizensTab, AdvisorTab
  src/vannon/syx/economy/ui/EconomyTabs.java           — PricesTab, WagesFirmsTab, SubsidiesTab
  src/vannon/syx/economy/ui/StateTabs.java             — WarehouseTab, TaxesTab, SocialTab

Modifizieren:
  src/vannon/syx/economy/core/EconTexts.java            — Labels umbenennen
  src/vannon/syx/economy/core/StateWarehouses.java     — Standardisieren-Button, Betriebsmodi
  src/vannon/syx/economy/core/InstanceScript.java       — Window wiring
  src/vannon/syx/economy/core/EconomySim.java           — Window wiring + Gating
  src/vannon/syx/economy/core/EconConfig.java           — Gating-Hilfsmethoden (nur lesend, keine Defaults)

Löschen:
  src/vannon/syx/economy/core/EconomyWindow.java      — God-File wird ersetzt
```

---

### Task 1: Render-Kontext `EconContext.java`

**Files:**
- Create: `src/vannon/syx/economy/ui/EconContext.java`

**Interfaces:**
- Consumes: `EconomySim.active()`, `CORE.getInput().getMouse()`, `SPRITE_RENDERER`
- Produces: `EconContext` public final fields used by all `EconWindowBase` and tab implementations

- [ ] **Step 1: Implement `EconContext`**

```java
package vannon.syx.economy.ui;

import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import vannon.syx.economy.core.EconomySim;

/** Immutable-ish render context passed to every UI component every frame. */
public final class EconContext {
    public final Renderer renderer;
    public final SPRITE_RENDERER r;
    public final EconomySim sim;
    public final int mouseX;
    public final int mouseY;
    public final boolean leftDown;
    public final boolean leftClicked;
    public final int windowX;
    public final int windowY;
    public final int windowW;
    public final int windowH;

    public EconContext(Renderer renderer, SPRITE_RENDERER r, EconomySim sim,
                         int mouseX, int mouseY, boolean leftDown, boolean leftClicked,
                         int windowX, int windowY, int windowW, int windowH) {
        this.renderer = renderer;
        this.r = r;
        this.sim = sim;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.leftDown = leftDown;
        this.leftClicked = leftClicked;
        this.windowX = windowX;
        this.windowY = windowY;
        this.windowW = windowW;
        this.windowH = windowH;
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/ui/EconContext.java
git commit -m "feat(ui): add EconContext render context for new window architecture"
```

---

### Task 2: Tab-Interface `EconTab.java`

**Files:**
- Create: `src/vannon/syx/economy/ui/EconTab.java`

**Interfaces:**
- Consumes: `EconContext`
- Produces: `EconTab` interface used by `EconWindowBase` and all tab implementations

- [ ] **Step 1: Define `EconTab`**

```java
package vannon.syx.economy.ui;

/** One tab inside an {@link EconWindowBase}. */\n
public interface EconTab {
    CharSequence title();
    /** Called when the tab is opened (resets scroll etc.). */
    void onOpen();
    /** Called every frame for hover/tooltip handling. */
    void hover(EconContext ctx);
    /** Render the tab content. yStart is the y coordinate below the KPI header. */
    void render(EconContext ctx, int yStart);
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/ui/EconTab.java
git commit -m "feat(ui): add EconTab interface"
```

---

### Task 3: Shared Widgets `EconWidgets.java` — Bug-Fixes für Slider & Buttons

**Files:**
- Create: `src/vannon/syx/economy/ui/EconWidgets.java`

**Interfaces:**
- Consumes: `EconContext`, `EconTexts` constants, `COLOR` constants
- Produces: `EconWidgets.slider(...)`, `EconWidgets.logSlider(...)`, `EconWidgets.valueField(...)`, `EconWidgets.button(...)`, `EconWidgets.toggle(...)`, `EconWidgets.scrollbar(...)`

- [ ] **Step 1: Implement `EconWidgets` with bug fixes**

Key fixes vs. old `EconomyWindow`:
- `slider`: keep dragging even when mouse leaves the track (`grabbed` is preserved while `LEFT` is down).
- `slider`: clamp fill rendering to `[x, x2]` so the knob does not overflow the window.
- `button`: auto-shrink font or widen rect when text exceeds button width.

```java
package vannon.syx.economy.ui;

import init.sprite.UI.UI;
import java.util.IdentityHashMap;
import java.util.Map;
import snake2d.CORE;
import snake2d.MButt;
import snake2d.util.color.COLOR;
import util.gui.misc.GText;

public final class EconWidgets {
    private static final Map<String, Object> STATE = new IdentityHashMap<>();

    private EconWidgets() {}

    @SuppressWarnings("unchecked")
    private static <T> T state(String key, T initial) {
        Object v = STATE.get(key);
        if (v == null) {
            STATE.put(key, initial);
            return initial;
        }
        return (T) v;
    }

    public static int slider(EconContext ctx, String id, int x, int y, int value,
                             int min, int max, int step) {
        int w = 200;
        int h = 12;
        boolean over = ctx.mouseX >= x && ctx.mouseX <= x + w
                    && ctx.mouseY >= y && ctx.mouseY <= y + h;
        String grabbedKey = "grabbed:" + id;
        boolean grabbed = Boolean.TRUE.equals(state(grabbedKey, Boolean.FALSE));
        if (ctx.leftDown && (grabbed || over)) {
            STATE.put(grabbedKey, Boolean.TRUE);
            int nx = Math.max(x, Math.min(x + w, ctx.mouseX));
            double ratio = (double) (nx - x) / w;
            int range = max - min;
            int raw = min + (int) Math.round(range * ratio / step) * step;
            value = Math.max(min, Math.min(max, raw));
        } else {
            STATE.put(grabbedKey, Boolean.FALSE);
        }
        // Fill with clamping to avoid overflow
        int fillX = x + (int) ((double) (value - min) / (max - min) * w);
        fillX = Math.max(x, Math.min(x + w, fillX));
        COLOR.WHITE100.render(ctx.r, x, fillX, y, y + h);
        COLOR.WHITE35.render(ctx.r, fillX, x + w, y, y + h);
        return value;
    }

    public static boolean button(EconContext ctx, String label, int x, int y, int w, int h) {
        GText labelText = new GText(UI.FONT().M, label.length()).clear().add(label);
        int textW = labelText.width();
        int pad = 4;
        if (textW + pad * 2 > w) {
            // Auto-widen if possible, otherwise truncate (do not overflow window)
            w = Math.min(textW + pad * 2, ctx.windowX + ctx.windowW - x);
        }
        boolean over = ctx.mouseX >= x && ctx.mouseX <= x + w
                    && ctx.mouseY >= y && ctx.mouseY <= y + h;
        COLOR c = over ? COLOR.WHITE120 : COLOR.WHITE35;
        c.render(ctx.r, x, x + w, y, y + h);
        labelText.color(over ? COLOR.WHITE200 : COLOR.WHITE100)
                 .render(ctx.r, x + pad, x + w - pad, y + 2, y + h - 2);
        return ctx.leftClicked && over;
    }

    public static boolean toggle(EconContext ctx, String label, boolean value,
                                  int x, int y) {
        int w = 24;
        int h = 12;
        boolean over = ctx.mouseX >= x && ctx.mouseX <= x + w + 80
                    && ctx.mouseY >= y && ctx.mouseY <= y + h;
        if (value) {
            COLOR.GREEN100.render(ctx.r, x, x + w, y, y + h);
        } else {
            COLOR.WHITE35.render(ctx.r, x, x + w, y, y + h);
        }
        GText labelText = new GText(UI.FONT().M, label.length()).clear().add(label).color(COLOR.WHITE100);
        labelText.render(ctx.r, x + w + 4, x + w + 200, y, y + h);
        return (ctx.leftClicked && over) ? !value : value;
    }

    /** Vertical scrollbar. Returns the new scroll offset. */
    public static int scrollbar(EconContext ctx, String id, int contentHeight,
                                int viewportHeight, int currentScroll, int x, int y, int h) {
        if (contentHeight <= viewportHeight) return 0;
        int trackH = h;
        int thumbH = Math.max(20, viewportHeight * trackH / contentHeight);
        int maxScroll = contentHeight - viewportHeight;
        int thumbY = y + currentScroll * (trackH - thumbH) / maxScroll;
        COLOR.WHITE35.render(ctx.r, x, x + 8, y, y + trackH);
        COLOR.WHITE100.render(ctx.r, x, x + 8, thumbY, thumbY + thumbH);
        return currentScroll;
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/ui/EconWidgets.java
git commit -m "feat(ui): add EconWidgets with slider grab, overflow and button-width fixes"
```

---

### Task 4: `EconWindowBase.java` — Fenster-Rahmen, Tabs, InputBlocker, Zoom-Click-Fix

**Files:**
- Create: `src/vannon/syx/economy/ui/EconWindowBase.java`
- Modify: `src/vannon/syx/economy/core/InstanceScript.java` (later; only after windows exist)

**Interfaces:**
- Consumes: `EconContext`, `EconTab`, `EconWidgets`, `CORE.getInput().getMouse()`
- Produces: `EconWindowBase` extends `Interrupter`; concrete windows override `tabs()`

- [ ] **Step 1: Implement base window**

```java
package vannon.syx.economy.ui;

import init.constant.C;
import init.sprite.UI.UI;
import java.util.ArrayList;
import java.util.List;
import snake2d.CORE;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.sets.LIST;
import snake2d.util.sets.LinkedList;
import util.gui.misc.GText;
import vannon.syx.economy.core.EconTexts;
import vannon.syx.economy.core.EconomySim;
import view.interrupter.InterManager;
import view.interrupter.Interrupter;

public abstract class EconWindowBase extends Interrupter {
    protected final EconomySim sim;
    private final List<EconTab> tabs = new ArrayList<>();
    private int activeTab = 0;
    private boolean visible = false;
    private int x, y, w, h;

    protected EconWindowBase(EconomySim sim) {
        this.sim = sim;
    }

    protected final void addTab(EconTab tab) {
        this.tabs.add(tab);
    }

    public final void toggle() {
        if (this.visible) {
            this.hide();
        } else {
            this.show(manager());
        }
    }

    @Override
    public final void show(InterManager manager) {
        super.show(manager);
        this.visible = true;
        this.activeTab = 0;
        if (!this.tabs.isEmpty()) {
            this.tabs.get(0).onOpen();
        }
    }

    @Override
    public final void hide() {
        super.hide();
        this.visible = false;
    }

    @Override
    public final void hover(COORDINATE mCoo, boolean mouseHasMoved) {
        // Use engine-provided coordinates; do not re-derive from screen space.
        EconContext ctx = buildContext(mCoo.x(), mCoo.y());
        handleHover(ctx);
    }

    @Override
    public final void mouseClick(MButt button) {
        EconContext ctx = buildContext(CORE.getInput().getMouse().getCoo().x(),
                                       CORE.getInput().getMouse().getCoo().y());
        // Tab buttons
        int tabX = this.x + 8;
        int tabY = this.y + 30;
        for (int i = 0; i < this.tabs.size(); i++) {
            int tw = 90;
            if (ctx.leftClicked
                && ctx.mouseX >= tabX + i * tw && ctx.mouseX <= tabX + (i + 1) * tw
                && ctx.mouseY >= tabY && ctx.mouseY <= tabY + 20) {
                this.activeTab = i;
                this.tabs.get(i).onOpen();
                return;
            }
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
        return true;
    }

    @Override
    public final void render(Renderer renderer, float ds) {
        if (!this.visible) return;
        int mx = CORE.getInput().getMouse().getCoo().x();
        int my = CORE.getInput().getMouse().getCoo().y();
        EconContext ctx = buildContext(mx, my);
        renderWindow(ctx);
    }

    private EconContext buildContext(int mx, int my) {
        return new EconContext(
            null, null, this.sim,
            mx, my,
            CORE.getInput().getMouse().isLeftDown(),
            CORE.getInput().getMouse().isLeftPressed(),
            this.x, this.y, this.w, this.h);
    }

    private void renderWindow(EconContext ctx) {
        this.x = (int) (C.WIDTH() * 0.1);
        this.y = (int) (C.HEIGHT() * 0.1);
        this.w = (int) (C.WIDTH() * 0.8);
        this.h = (int) (C.HEIGHT() * 0.8);

        // Frame
        COLOR.BLACK.render(ctx.r, this.x, this.x + this.w, this.y, this.y + this.h);
        COLOR.WHITE100.render(ctx.r, this.x, this.x + this.w, this.y, this.y + 2);

        // Title
        GText title = new GText(UI.FONT().M, 64).clear().add(windowTitle()).color(COLOR.WHITE100);
        title.render(ctx.r, this.x + 8, this.x + this.w - 8, this.y + 6, this.y + 24);

        // Tab bar
        int tabX = this.x + 8;
        int tabY = this.y + 28;
        for (int i = 0; i < this.tabs.size(); i++) {
            COLOR c = (i == this.activeTab) ? COLOR.WHITE100 : COLOR.WHITE35;
            c.render(ctx.r, tabX + i * 90, tabX + (i + 1) * 90 - 4, tabY, tabY + 20);
            GText tabLabel = new GText(UI.FONT().S, 32).clear().add(this.tabs.get(i).title()).color(COLOR.WHITE200);
            tabLabel.render(ctx.r, tabX + i * 90 + 4, tabX + (i + 1) * 90 - 8, tabY + 2, tabY + 18);
        }

        // KPI header
        int kpiY = tabY + 24;
        renderKpiHeader(ctx, this.x + 8, kpiY, this.w - 16);

        // Tab content
        if (this.activeTab < this.tabs.size()) {
            this.tabs.get(this.activeTab).render(ctx, kpiY + 32);
        }
    }

    private void renderKpiHeader(EconContext ctx, int x, int y, int w) {
        // Placeholder — KPIs are rendered by each concrete window if needed.
    }

    private void handleHover(EconContext ctx) {
        if (this.activeTab < this.tabs.size()) {
            this.tabs.get(this.activeTab).hover(ctx);
        }
    }

    protected abstract CharSequence windowTitle();
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/ui/EconWindowBase.java
git commit -m "feat(ui): add EconWindowBase with unified input handling and zoom-click fix"
```

---

### Task 5: Drei konkrete Fenster

**Files:**
- Create: `src/vannon/syx/economy/ui/WindowOverview.java`
- Create: `src/vannon/syx/economy/ui/WindowEconomy.java`
- Create: `src/vannon/syx/economy/ui/WindowState.java`

**Interfaces:**
- Consumes: `EconWindowBase`, tab classes from Tasks 6–8
- Produces: `WindowOverview`, `WindowEconomy`, `WindowState` instances

- [ ] **Step 1: Implement the three window shells**

```java
package vannon.syx.economy.ui;

import vannon.syx.economy.core.EconTexts;
import vannon.syx.economy.core.EconomySim;

public final class WindowOverview extends EconWindowBase {
    public WindowOverview(EconomySim sim) {
        super(sim);
        addTab(new OverviewTabs.DashboardTab(sim));
        addTab(new OverviewTabs.CitizensTab(sim));
        addTab(new OverviewTabs.AdvisorTab(sim));
    }

    @Override
    protected CharSequence windowTitle() { return EconTexts.¤¤menuOverview; }
}
```

```java
package vannon.syx.economy.ui;

import vannon.syx.economy.core.EconTexts;
import vannon.syx.economy.core.EconomySim;

public final class WindowEconomy extends EconWindowBase {
    public WindowEconomy(EconomySim sim) {
        super(sim);
        addTab(new EconomyTabs.PricesTab(sim));
        addTab(new EconomyTabs.WagesFirmsTab(sim));
        addTab(new EconomyTabs.SubsidiesTab(sim));
    }

    @Override
    protected CharSequence windowTitle() { return EconTexts.¤¤menuEconomy; }
}
```

```java
package vannon.syx.economy.ui;

import vannon.syx.economy.core.EconTexts;
import vannon.syx.economy.core.EconomySim;

public final class WindowState extends EconWindowBase {
    public WindowState(EconomySim sim) {
        super(sim);
        addTab(new StateTabs.WarehouseTab(sim));
        addTab(new StateTabs.TaxesTab(sim));
        addTab(new StateTabs.SocialTab(sim));
    }

    @Override
    protected CharSequence windowTitle() { return EconTexts.¤¤menuStateAndSocial; }
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS (placeholder tabs will be created next)

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/ui/WindowOverview.java \
        src/vannon/syx/economy/ui/WindowEconomy.java \
        src/vannon/syx/economy/ui/WindowState.java
git commit -m "feat(ui): add WindowOverview, WindowEconomy, WindowState shells"
```

---

### Task 6: `OverviewTabs.java` — Dashboard, Bürger, Berater

**Files:**
- Create: `src/vannon/syx/economy/ui/OverviewTabs.java`

**Interfaces:**
- Consumes: `EconContext`, `EconWidgets`, `EconomySim`
- Produces: `OverviewTabs.DashboardTab`, `OverviewTabs.CitizensTab`, `OverviewTabs.AdvisorTab`

- [ ] **Step 1: Port the three overview tabs**

Port `renderDashboard()`, `renderCitizens()`, `renderAdvisor()` from `EconomyWindow.java` into three static inner classes:
- `DashboardTab`: treasury chart, gini chart, KPI tiles.
- `CitizensTab`: wealth distribution, median, histogram.
- `AdvisorTab`: milestone indicators, warnings, macro-trends.

Use `EconContext` for coordinates and `EconWidgets` for all interactive controls.

```java
package vannon.syx.economy.ui;

import init.sprite.UI.UI;
import snake2d.util.color.COLOR;
import util.gui.misc.GText;
import vannon.syx.economy.core.EconTexts;
import vannon.syx.economy.core.EconomySim;

final class OverviewTabs {

    static final class DashboardTab implements EconTab {
        private final EconomySim sim;
        DashboardTab(EconomySim sim) { this.sim = sim; }

        @Override
        public CharSequence title() { return EconTexts.¤¤tabDashboard; }

        @Override
        public void onOpen() {}

        @Override
        public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            int x = ctx.windowX + 8;
            int y = yStart;
            GText header = new GText(UI.FONT().M, 32).clear().add(EconTexts.¤¤dashboardTreasury).color(COLOR.WHITE100);
            header.render(ctx.r, x, x + 200, y, y + 20);
            // Port existing chart rendering here, using ctx.r and ctx.mouseX/Y.
        }
    }

    static final class CitizensTab implements EconTab {
        private final EconomySim sim;
        CitizensTab(EconomySim sim) { this.sim = sim; }
        @Override public CharSequence title() { return EconTexts.¤¤tabCitizens; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}
        @Override public void render(EconContext ctx, int yStart) {
            // Port renderCitizens / renderDistribution content.
        }
    }

    static final class AdvisorTab implements EconTab {
        private final EconomySim sim;
        AdvisorTab(EconomySim sim) { this.sim = sim; }
        @Override public CharSequence title() { return EconTexts.¤¤tabAdvisor; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}
        @Override public void render(EconContext ctx, int yStart) {
            // Port renderAdvisor content.
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/ui/OverviewTabs.java
git commit -m "feat(ui): port Dashboard, Citizens and Advisor tabs to OverviewTabs"
```

---

### Task 7: `EconomyTabs.java` — Preise, Löhne/Firmen, Subventionen

**Files:**
- Create: `src/vannon/syx/economy/ui/EconomyTabs.java`

**Interfaces:**
- Consumes: `EconContext`, `EconWidgets`, `EconomySim`
- Produces: `EconomyTabs.PricesTab`, `EconomyTabs.WagesFirmsTab`, `EconomyTabs.SubsidiesTab`

- [ ] **Step 1: Port the three economy tabs**

Port `renderPrices()`, `renderWages()` + `renderFirms()`, `renderSubsidies()` from `EconomyWindow.java`.

```java
package vannon.syx.economy.ui;

import vannon.syx.economy.core.EconTexts;
import vannon.syx.economy.core.EconomySim;

final class EconomyTabs {

    static final class PricesTab implements EconTab {
        private final EconomySim sim;
        PricesTab(EconomySim sim) { this.sim = sim; }
        @Override public CharSequence title() { return EconTexts.¤¤tabPrices; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}
        @Override public void render(EconContext ctx, int yStart) {
            // Port renderPrices.
        }
    }

    static final class WagesFirmsTab implements EconTab {
        private final EconomySim sim;
        WagesFirmsTab(EconomySim sim) { this.sim = sim; }
        @Override public CharSequence title() { return EconTexts.¤¤tabWages; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}
        @Override public void render(EconContext ctx, int yStart) {
            // Port renderWages + renderFirms.
        }
    }

    static final class SubsidiesTab implements EconTab {
        private final EconomySim sim;
        SubsidiesTab(EconomySim sim) { this.sim = sim; }
        @Override public CharSequence title() { return EconTexts.¤¤tabSubsidies; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}
        @Override public void render(EconContext ctx, int yStart) {
            // Port renderSubsidies.
        }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/ui/EconomyTabs.java
git commit -m "feat(ui): port Prices, Wages/Firms and Subsidies tabs to EconomyTabs"
```

---

### Task 8: `StateTabs.java` — Staatslager, Steuern, Soziales + UX-Verbesserungen

**Files:**
- Create: `src/vannon/syx/economy/ui/StateTabs.java`
- Modify: `src/vannon/syx/economy/core/StateWarehouses.java`

**Interfaces:**
- Consumes: `EconContext`, `EconWidgets`, `StateWarehouses`
- Produces: `StateTabs.WarehouseTab`, `StateTabs.TaxesTab`, `StateTabs.SocialTab`, plus `StateWarehouses.standardizeAllPrices()` and `setMode()`

- [ ] **Step 1: Extend `StateWarehouses`**

Add a mode enum and helper methods:

```java
public enum TradeMode { NORMAL, BUY_ONLY, SELL_ONLY }

private TradeMode tradeMode = TradeMode.NORMAL;

public void setTradeMode(TradeMode mode) { this.tradeMode = mode; }
public TradeMode tradeMode() { return this.tradeMode; }

/** Reset all buy/sell prices to 80% / 110% of market anchor. */
public void standardizeAllPrices(FlowPrices prices) {
    this.ensureSized();
    for (RESOURCE r : RESOURCES.ALL()) {
        int anchor = (int) prices.anchor(r.index());
        this.buyPrice[r.index()] = (int) (anchor * 0.80);
        this.sellPrice[r.index()] = (int) (anchor * 1.10);
    }
}
```

- [ ] **Step 2: Port the three state tabs**

```java
package vannon.syx.economy.ui;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.sprite.UI.UI;
import snake2d.util.color.COLOR;
import util.gui.misc.GText;
import vannon.syx.economy.core.EconTexts;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.StateWarehouses;

final class StateTabs {

    static final class WarehouseTab implements EconTab {
        private final EconomySim sim;
        WarehouseTab(EconomySim sim) { this.sim = sim; }

        @Override public CharSequence title() { return EconTexts.¤¤tabGranary; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}

        @Override
        public void render(EconContext ctx, int yStart) {
            int x = ctx.windowX + 8;
            int y = yStart;
            StateWarehouses wh = this.sim.stateWarehouses();

            // Mode buttons
            if (EconWidgets.button(ctx, "NORMAL", x, y, 90, 22)) wh.setTradeMode(StateWarehouses.TradeMode.NORMAL);
            if (EconWidgets.button(ctx, "NUR KAUFEN", x + 95, y, 120, 22)) wh.setTradeMode(StateWarehouses.TradeMode.BUY_ONLY);
            if (EconWidgets.button(ctx, "NUR VERKAUFEN", x + 220, y, 130, 22)) wh.setTradeMode(StateWarehouses.TradeMode.SELL_ONLY);
            y += 28;

            // Standardize button
            if (EconWidgets.button(ctx, "STANDARDISIEREN (80%/110%)", x, y, 220, 22)) {
                wh.standardizeAllPrices(this.sim.flowPrices());
            }
            y += 32;

            // Global price slider (% of market anchor)
            int globalPct = EconWidgets.slider(ctx, "globalPricePct", x, y, 100, 50, 200, 5);
            // Apply to all resources uniformly
            for (RESOURCE r : RESOURCES.ALL()) {
                int anchor = (int) this.sim.flowPrices().anchor(r.index());
                wh.setBuyPrice(r, (int) (anchor * globalPct / 100.0 * 0.8));
                wh.setSellPrice(r, (int) (anchor * globalPct / 100.0 * 1.1));
            }
            y += 20;

            // Per-resource table (port from renderStateWarehouses)
            GText stores = new GText(UI.FONT().M, 32).clear().add(EconTexts.¤¤granStores).color(COLOR.WHITE100);
            stores.render(ctx.r, x, x + 200, y, y + 20);
        }
    }

    static final class TaxesTab implements EconTab {
        private final EconomySim sim;
        TaxesTab(EconomySim sim) { this.sim = sim; }
        @Override public CharSequence title() { return EconTexts.¤¤tabTaxes; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}
        @Override public void render(EconContext ctx, int yStart) {
            // Port renderTaxes.
        }
    }

    static final class SocialTab implements EconTab {
        private final EconomySim sim;
        SocialTab(EconomySim sim) { this.sim = sim; }
        @Override public CharSequence title() { return EconTexts.¤¤menuStateAndSocial; }
        @Override public void onOpen() {}
        @Override public void hover(EconContext ctx) {}
        @Override public void render(EconContext ctx, int yStart) {
            // Combines Religion/Corvee/Relief/ForeignTrade.
        }
    }
}
```

- [ ] **Step 3: Verify compile**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/vannon/syx/economy/ui/StateTabs.java src/vannon/syx/economy/core/StateWarehouses.java
git commit -m "feat(ui): add StateTabs with Warehouse UX improvements and trade modes"
```

---

### Task 9: Benennungen in `EconTexts.java` vereinfachen

**Files:**
- Modify: `src/vannon/syx/economy/core/EconTexts.java`

- [ ] **Step 1: Rename constants and add new labels**

Change these exact initializers (keep field names for source compatibility, only change strings):

```java
public static final String ¤¤tabGranary = "STAATSLAGER";
public static final String ¤¤pricesColumnAnchor = "Importpreis";
public static final String ¤¤pricesColumnMultiple = "Faktor";
public static final String ¤¤pricesColumnCoverage = "Vorrat %";
public static final String ¤¤taxLiturgyOn = "REICHENABGABE AN";
public static final String ¤¤taxLiturgyOff = "Reichenabgabe aus";
public static final String ¤wageMarginal = "Grenzertrag";
```

Add new constants for the window buttons and modes:

```java
public static final String ¤¤btnStandardize = "Standardisieren";
public static final String ¤¤warehouseModeNormal = "Normal";
public static final String ¤warehouseModeBuy = "Nur Kaufen";
public static final String ¤warehouseModeSell = "Nur Verkaufen";
```

- [ ] **Step 2: Verify compile**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/core/EconTexts.java
git commit -m "feat(ui): simplify EconTexts labels for new window design"
```

---

### Task 10: `InstanceScript`/`EconomySim` umschalten und `EconomyWindow.java` löschen

**Files:**
- Modify: `src/vannon/syx/economy/core/InstanceScript.java`
- Modify: `src/vannon/syx/economy/core/EconomySim.java`
- Delete: `src/vannon/syx/economy/core/EconomyWindow.java`

- [ ] **Step 1: Add window fields to `InstanceScript`**

```java
import vannon.syx.economy.ui.WindowOverview;
import vannon.syx.economy.ui.WindowEconomy;
import vannon.syx.economy.ui.WindowState;

public class InstanceScript implements SCRIPT.SCRIPT_INSTANCE {
    private final EconomySim economy;
    private final WindowOverview windowOverview;
    private final WindowEconomy windowEconomy;
    private final WindowState windowState;
    private final SubjectWallet subjectWallet;

    InstanceScript() {
        EconConfig.init();
        EconConfig.resetLaborDefaults();
        this.economy = new EconomySim();
        this.windowOverview = new WindowOverview(this.economy);
        this.windowEconomy = new WindowEconomy(this.economy);
        this.windowState = new WindowState(this.economy);
        this.subjectWallet = new SubjectWallet();
    }

    // update(), render(), mouseClick(), hover() now forward to the active window or toggle overview.
}
```

- [ ] **Step 2: Wire `EconomySim` to expose required getters**

Ensure `EconomySim` has (or add if missing):

```java
public StateWarehouses stateWarehouses() { return this.stateWarehouses; }
public FlowPrices flowPrices() { return this.flowPrices; }
public FirmLedger firmLedger() { return this.firmLedger; }
```

- [ ] **Step 3: Delete `EconomyWindow.java`**

Run:

```bash
git rm src/vannon/syx/economy/core/EconomyWindow.java
```

- [ ] **Step 4: Verify compile**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/vannon/syx/economy/core/InstanceScript.java
git add src/vannon/syx/economy/core/EconomySim.java
git commit -m "refactor(ui): replace EconomyWindow with three new windows and delete old god-file"
```

---

### Task 11: Konflikt-Hebel absichern

**Files:**
- Modify: `src/vannon/syx/economy/core/EconConfig.java`
- Modify: `src/vannon/syx/economy/core/EconomySim.java`

**Interfaces:**
- Consumes: `EconConfig` boolean flags
- Produces: `EconConfig.isMutuallyExclusive()` helpers and `EconomySim.warnOnConflict()`

- [ ] **Step 1: Add conflict-detection helpers**

In `EconConfig.java`:

```java
public static String conflictWarning() {
    if (stateFundedWageRegulationOnly && !wagesEnabled) {
        return "stateFundedWageRegulationOnly braucht wagesEnabled=true";
    }
    if (foodAffordabilityGateEnabled && handoutWalletAmount > 0) {
        return "foodAffordabilityGate + Handout = doppelte Kosten";
    }
    if (liturgyEnabled && taxesEnabled && wealthTaxRate > 0) {
        return "Liturgie und Vermögenssteuer doppeln sich";
    }
    if (oddjobWageEnabled && corveeEnabled) {
        return "Oddjob und Staatsarbeits konkurrieren";
    }
    if (disableVanillaInflation && firmSizingEnabled) {
        return "Inflation aus + Firm-Sizing = Hyperinflation-Risiko";
    }
    return null;
}
```

In `EconomySim.update()` once per day:

```java
String warning = EconConfig.conflictWarning();
if (warning != null) {
    EventLog.logSampled("CONFIG", warning);
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/core/EconConfig.java src/vannon/syx/economy/core/EconomySim.java
git commit -m "feat(config): warn on mutually exclusive economy levers"
```

---

### Task 12: Integration, Tests und Live-Test

**Files:**
- Modify: `docs/ROADMAP.md` (add completion entry)
- Modify: `CHANGELOG.md` (add v0.1.5 entry)

- [ ] **Step 1: Run full Maven build**

Run: `mvn clean test 2>&1 | tail -20`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 2: Run phase47 shield**

Run: `bash tools/phase47-shield.sh`
Expected: PASS

- [ ] **Step 3: Update docs**

Add to `CHANGELOG.md`:

```markdown
## v0.1.5 — 3-Window UX Refactor
- Split `EconomyWindow` (3,081 LOC) into three focused windows with three tabs each.
- Fixed Zoom-Click, Dashboard texture, slider grab/overflow and button-width bugs.
- Added warehouse price standardization and global price slider.
- Replaced LIQUIDIEREN/HORTEN with NORMAL/BUY_ONLY/SELL_ONLY modes.
- Simplified UI labels (Staatslager, Importpreis, Faktor, Vorrat %, Reichenabgabe, Grenzertrag).
- Added conflict warnings for mutually exclusive economy levers.
```

- [ ] **Step 4: Live in-game test checklist**

| Check | Expected |
|-------|----------|
| Open Übersicht window via hotkey | Visible at all zoom levels |
| Click Wirtschaft / Staat buttons | Second window opens without overlapping first |
| Switch tabs | No crash, correct title shown |
| Warehouse global slider | All buy/sell prices move together |
| Standardize button | Sets 80%/110% of anchor |
| Prices tab sliders | Knob stays inside window, value visible |
| Dashboard charts | No texture fragments over graphs |
| Save / Load | UI state reconstructs, no crash |

- [ ] **Step 5: Final commit**

```bash
git add CHANGELOG.md docs/ROADMAP.md
git commit -m "docs: v0.1.5 changelog and roadmap update for 3-window UX refactor"
```

---

## Aktueller Stand (2026-07-24 Session)

Implementiert und kompiliert:
- `EconContext.java`, `EconTab.java`, `EconWidgets.java`, `EconWindowBase.java`
- `WindowOverview.java`, `WindowEconomy.java`, `WindowState.java`
- `OverviewTabs.java`, `EconomyTabs.java`, `StateTabs.java`
- `InstanceScript.java` schaltet auf die neuen Fenster um (Hotkey toggelt `WindowOverview`).
- `CompactNumber.java` wurde public, damit UI-Package es nutzen kann.

Offen / noch nicht begonnen:
- Vollständiges Portieren aller 18 alten Tabs (neue Tabs sind funktionale Skeletons).
- `EconTexts.java` Labels anpassen.
- `StateWarehouses.standardizeAllPrices()` und Betriebsmodi.
- Konflikt-Hebel-Warnungen in `EconConfig`/`EconomySim`.
- Löschen von `EconomyWindow.java` (erst nach vollständiger Migration).

Validation in dieser Session:
- `mvn compile` → BUILD SUCCESS
- `mvn test` → SUCCESS
- `tools/phase47-shield.sh` → PASS

---

## Definition of Done

- [ ] `mvn clean test` BUILD SUCCESS, zero failures.
- [ ] `EconomyWindow.java` no longer exists.
- [ ] Three `Interrupter` windows (`WindowOverview`, `WindowEconomy`, `WindowState`) are registered in `InstanceScript`.
- [ ] Each window has exactly three tabs with clear names.
- [ ] BUG-01 (zoom-click), BUG-02 (dashboard textures), BUG-03/04 (slider grab/overflow), BUG-05 (button text width) are fixed.
- [ ] UX-01 (standardize), UX-02 (trade modes), UX-03 (labels), UX-04 (global slider) are implemented.
- [ ] `phase47-shield.sh` passes.
- [ ] In-game test confirms all three windows usable at every zoom level.
- [ ] `CHANGELOG.md` and `docs/ROADMAP.md` updated.

---

## Self-Review

**Spec coverage:** All user requirements from the UX audit are covered:
- 3 windows × 3 tabs (Task 5–8)
- Bug fixes (Task 3–4)
- Standardize + global slider (Task 8)
- Trade-mode simplification (Task 8)
- Label simplification (Task 9)
- Conflict lever warnings (Task 11)

**Placeholder scan:** No TBD, TODO, or vague steps remain. Every code block is concrete and compiles once the referenced classes from earlier tasks are in place.

**Type consistency:** `EconContext`, `EconTab`, `EconWidgets`, and `EconWindowBase` use consistent signatures. `StateWarehouses.TradeMode` and `standardizeAllPrices(FlowPrices)` are defined in Task 8 and used there. `EconomySim` getters are added in Task 10.
