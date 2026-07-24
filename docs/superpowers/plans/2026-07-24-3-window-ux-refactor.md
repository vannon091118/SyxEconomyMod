# 3-Window UX Refactor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 3081-LOC EconomyWindow god-file with 3 modular Interrupter-based windows (Übersicht, Wirtschaft, Staat) × 3 tabs each. Fix 5 bugs, resolve 7 conflict levers, replace 6 foreign words, add standardized warehouse pricing.

**Architecture:** 10 new files in `vannon.syx.economy.ui`. Abstract base class `EconWindowBase extends Interrupter` handles frame, KPI header, tab bar, input blocking. Each window instantiates 3 tabs via `EconTab` interface. Shared widgets (`EconWidgets`) are static methods consuming `EconContext`. Existing `EconomyWindow.java` is deleted after full migration.

**Tech Stack:** Java 21, Songs of Syx V71.44, snake2d Renderer/SPRITE_RENDERER/COLOR/GText/GChart, Interrupter lifecycle, Maven.

## Global Constraints

- Java 21 (maven.compiler.target = 21)
- Songs of Syx V71.44 — no V72 features
- `mvn compile` must yield BUILD SUCCESS, zero new warnings
- `mvn test` — 24 tests must pass at every commit
- No behavioral change in migrated tabs — structural and UX changes only
- All new files in `src/vannon/syx/economy/ui/` (NEW package)
- EconomyWindow.java deleted after full migration; MainScript.java references the 3 new windows
- COLOR constants: use only vanilla-available (WHITE100, WHITE150, WHITE200, WHITE120, REDISH, WHITE35, WHITE25, WHITE50, YELLOW100, WHITE15, GREENISH, WHITE20, WHITE10, GREEN100 — all confirmed in game JAR)
- No new EconConfig fields without BALANCE_LEVERS.md update

---

### File Structure

```
src/vannon/syx/economy/ui/          (NEW package — 10 files, ~2345 LOC total)
├── EconContext.java                Render context record (~60 LOC)
├── EconTab.java                    Tab interface (~15 LOC)
├── EconWidgets.java                Shared UI widgets — static methods (~250 LOC)
├── EconWindowBase.java             Abstract Interrupter shell (~250 LOC)
├── WindowOverview.java             Window 1: Übersicht (~40 LOC)
├── WindowEconomy.java              Window 2: Wirtschaft (~40 LOC)
├── WindowState.java                Window 3: Staat (~40 LOC)
├── OverviewTabs.java               DashboardTab, CitizensTab, AdvisorTab (~550 LOC)
├── EconomyTabs.java                PricesTab, WagesFirmsTab, SubsidiesTab (~550 LOC)
└── StateTabs.java                  WarehouseTab, TaxesTab, SocialTab (~550 LOC)

src/vannon/syx/economy/core/
├── EconomyWindow.java              DELETED after Task 8 migration
├── EconTexts.java                  MODIFIED: 6 labels renamed
├── MainScript.java                 MODIFIED: reference 3 windows instead of 1
├── StateWarehouses.java            MODIFIED: add standardizeAllPrices(), globalPriceScale()

tools/
└── phase47-shield.sh               MODIFIED: update file paths in gate rules
```

---

### Task 1: EconContext.java — Render Context Record

**Files:**
- Create: `src/vannon/syx/economy/ui/EconContext.java`

**Interfaces:**
- Consumes: snake2d.Renderer, snake2d.MButt, snake2d.util.datatypes.COORDINATE, vannon.syx.economy.core.EconomySim
- Produces: `EconContext(Renderer r, EconomySim sim, COORDINATE mouse, boolean leftClicked, boolean leftDown, int wheelScroll, int x1, int x2, int maxW, GText label, GText line)` — used by all tabs and widgets

- [ ] **Step 1: Create EconContext.java**

```java
package vannon.syx.economy.ui;

import snake2d.Renderer;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.sprite.text.StringInputSprite;
import util.gui.misc.GText;
import vannon.syx.economy.core.EconomySim;

public final class EconContext {
    public final Renderer r;
    public final EconomySim sim;
    public final COORDINATE mouse;
    public final boolean leftClicked;
    public final boolean leftDown;
    public final int wheelScroll;
    public final int x1, x2, maxW;
    public final GText label;
    public final GText line;
    public final StringInputSprite input;

    // Mutable grab/edit state shared with EconWindowBase
    public Object grabbedId;
    public Object editingId;
    public int pendingScroll;

    public EconContext(Renderer r, EconomySim sim, COORDINATE mouse,
                       boolean leftClicked, boolean leftDown, int wheelScroll,
                       int x1, int x2, int maxW,
                       GText label, GText line, StringInputSprite input) {
        this.r = r;
        this.sim = sim;
        this.mouse = mouse;
        this.leftClicked = leftClicked;
        this.leftDown = leftDown;
        this.wheelScroll = wheelScroll;
        this.x1 = x1;
        this.x2 = x2;
        this.maxW = maxW;
        this.label = label;
        this.line = line;
        this.input = input;
        this.grabbedId = null;
        this.editingId = null;
        this.pendingScroll = 0;
    }
}
```

- [ ] **Step 2: Compile**

Run: `mvn compile -pl .`
Expected: BUILD SUCCESS (EconContext has no dependencies on other new files)

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/ui/EconContext.java
git commit -m "feat(ui): add EconContext render context record for 3-window refactor"
```

---

### Task 2: EconTab.java — Tab Interface

**Files:**
- Create: `src/vannon/syx/economy/ui/EconTab.java`

**Interfaces:**
- Consumes: EconContext
- Produces: `interface EconTab { String label(); void render(EconContext ctx, int yStart); default void hover(EconContext ctx) {} default void onOpen() {} }`

- [ ] **Step 1: Create EconTab.java**

```java
package vannon.syx.economy.ui;

public interface EconTab {
    String label();
    void render(EconContext ctx, int yStart);
    default void hover(EconContext ctx) {}
    default void onOpen() {}
}
```

- [ ] **Step 2: Compile**

Run: `mvn compile -pl .`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/ui/EconTab.java
git commit -m "feat(ui): add EconTab interface for 3-window refactor"
```

---

### Task 3: EconWidgets.java — Shared Widgets (Extract from EconomyWindow)

**Files:**
- Create: `src/vannon/syx/economy/ui/EconWidgets.java`
- Read from: `src/vannon/syx/economy/core/EconomyWindow.java:258-273` (hit, scrollbar), `989-1147` (valueField, slider, logSlider, button, toggle)

**Interfaces:**
- Consumes: EconContext, snake2d.SPRITE_RENDERER, snake2d.util.color.COLOR, java.util.Objects, java.util.function.Consumer
- Produces: `EconWidgets.hit(EconContext, int x1, int x2, int y1, int y2)`, `EconWidgets.scrollbar(EconContext, int y1, int y2, int scroll, int visible, int total)`, `EconWidgets.slider(EconContext, String id, int x, int y, int value, int min, int max, int step)`, `EconWidgets.logSlider(...)`, `EconWidgets.valueField(...)`, `EconWidgets.button(...)`, `EconWidgets.toggle(...)`

- [ ] **Step 1: Extract hit()**

Copy from EconomyWindow L.258-261. Convert from instance method to static method taking EconContext:

```java
package vannon.syx.economy.ui;

import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import java.util.Objects;
import java.util.function.Consumer;

public final class EconWidgets {
    private EconWidgets() {}

    public static boolean hit(EconContext ctx, int x1, int x2, int y1, int y2) {
        return ctx.mouse.x() >= x1 && ctx.mouse.x() <= x2
            && ctx.mouse.y() >= y1 && ctx.mouse.y() <= y2;
    }
}
```

- [ ] **Step 2: Extract scrollbar()**

Copy from EconomyWindow L.262-273. Replace `this.win.x2()` with `ctx.x2`. Replace `COLOR.WHITE25.render((SPRITE_RENDERER)r, ...)` with static calls:

```java
    public static void scrollbar(EconContext ctx, int y1, int y2, int scroll, int visible, int total) {
        if (total <= visible) return;
        int x2 = ctx.x2 - 5;
        int x1 = x2 - 4;
        COLOR.WHITE25.render((SPRITE_RENDERER)ctx.r, x1, x2, y1, y2);
        int track = y2 - y1;
        int thumb = Math.max(16, track * visible / total);
        int top = y1 + (track - thumb) * scroll / Math.max(1, total - visible);
        COLOR.WHITE100.render((SPRITE_RENDERER)ctx.r, x1, x2, top, top + thumb);
    }
```

- [ ] **Step 3: Extract slider()**

Copy from EconomyWindow L.1039-1070. Replace `this.mouseX`/`this.mouseY` with `ctx.mouse.x()`/`ctx.mouse.y()`. Replace `this.grabbed`/`this.grabX1`/`this.grabX2` with `ctx.grabbedId` and local variables stored in base window (passed via context). Use `MButt.LEFT.isDown()` directly:

```java
    public static int slider(EconContext ctx, String id, int x, int y, int value, int min, int max, int step) {
        int x2 = x + 260;
        int cy = y + 15 - 5;
        COLOR.WHITE25.render((SPRITE_RENDERER)ctx.r, x, x2, cy, cy + 10);
        boolean over = ctx.mouse.x() >= x - 4 && ctx.mouse.x() <= x2 + 4
                    && ctx.mouse.y() >= y && ctx.mouse.y() <= y + 30;
        // BUG-03 FIX: grabbed persists even when over becomes false,
        // as long as LEFT is still down
        if (over && ctx.leftDown && ctx.grabbedId == null) {
            ctx.grabbedId = id;
        }
        if (Objects.equals(ctx.grabbedId, id) && ctx.leftDown) {
            double t = (double)(ctx.mouse.x() - x) / (double)(x2 - x);
            t = Math.max(0.0, Math.min(1.0, t));
            int v = (int)Math.round((double)min + t * (double)(max - min));
            value = v / step * step;
            if (value < min) value = min;
            if (value > max) value = max;
        }
        double frac = max > min ? (double)(value - min) / (double)(max - min) : 0.0;
        // BUG-04 FIX: clamp fill to slider bounds
        int fill = Math.max(x, Math.min(x2, (int)((double)x + frac * 260.0)));
        if (fill > x) {
            COLOR.WHITE120.render((SPRITE_RENDERER)ctx.r, x, fill, cy, cy + 10);
        }
        int knob = Math.max(x, Math.min(x2 - 10, fill - 5));
        boolean active = Objects.equals(ctx.grabbedId, id) || over;
        (active ? COLOR.GREENISH : COLOR.WHITE150)
            .render((SPRITE_RENDERER)ctx.r, knob, knob + 10, cy - 3, cy + 10 + 3);
        return value;
    }
```

- [ ] **Step 4: Extract logSlider(), valueField(), button(), toggle()**

Same pattern as slider(): copy from EconomyWindow, replace instance refs with EconContext, make static. For valueField: the `StringInputSprite input` is already in EconContext. The `Setter` interface is replaced with `Consumer<Integer>`:

```java
    public static int logSlider(EconContext ctx, String id, int x, int y, int value, int min, int max) {
        int x2 = x + 260;
        int cy = y + 15 - 5;
        if (max <= min) return min;
        COLOR.WHITE25.render((SPRITE_RENDERER)ctx.r, x, x2, cy, cy + 10);
        boolean over = ctx.mouse.x() >= x - 4 && ctx.mouse.x() <= x2 + 4
                    && ctx.mouse.y() >= y && ctx.mouse.y() <= y + 30;
        if (over && ctx.leftDown && ctx.grabbedId == null) {
            ctx.grabbedId = id;
        }
        if (Objects.equals(ctx.grabbedId, id) && ctx.leftDown) {
            double t = (double)(ctx.mouse.x() - x) / (double)(x2 - x);
            t = Math.max(0.0, Math.min(1.0, t));
            value = min + (int)Math.round(Math.expm1(t * Math.log1p(max - min)));
            if (value < min) value = min;
            if (value > max) value = max;
        }
        double frac = max > min ? Math.log1p(value - min) / Math.log1p(max - min) : 0.0;
        int fill = Math.max(x, Math.min(x2, (int)((double)x + frac * 260.0)));
        if (fill > x) {
            COLOR.WHITE120.render((SPRITE_RENDERER)ctx.r, x, fill, cy, cy + 10);
        }
        int knob = Math.max(x, Math.min(x2 - 10, fill - 5));
        boolean active = Objects.equals(ctx.grabbedId, id) || over;
        (active ? COLOR.GREENISH : COLOR.WHITE150)
            .render((SPRITE_RENDERER)ctx.r, knob, knob + 10, cy - 3, cy + 10 + 3);
        return value;
    }

    public static void valueField(EconContext ctx, String id, int x, int y, int w,
                                   int value, int min, int max, Consumer<Integer> setter,
                                   String prefix, String suffix, COLOR col) {
        int editX1 = x;
        int editX2 = x + w;
        int editY1 = y + 3;
        int editY2 = editY1 + 24;
        boolean over = hit(ctx, editX1, editX2, editY1, editY2);
        boolean active = Objects.equals(ctx.editingId, id);
        if (ctx.leftClicked && over && !active) {
            ctx.editingId = id;
            ctx.input.del();
            ctx.input.placeHolder(String.valueOf(value));
            ctx.input.listen();
        }
        if (active) {
            COLOR.WHITE35.render((SPRITE_RENDERER)ctx.r, editX1, editX2, editY1, editY2);
            COLOR.WHITE120.render((SPRITE_RENDERER)ctx.r, editX1, editX2, editY1, editY1 + 1);
            COLOR.WHITE120.render((SPRITE_RENDERER)ctx.r, editX1, editX2, editY2 - 1, editY2);
            ctx.input.listen();
            ctx.input.render((SPRITE_RENDERER)ctx.r, editX1 + 6, editY1 + 4);
            return;
        }
        if (over) {
            COLOR.WHITE25.render((SPRITE_RENDERER)ctx.r, editX1, editX2, editY1, editY2);
        }
        ctx.line.clear().add(prefix).add(String.valueOf(value)).add(suffix);
        ctx.line.color(over ? COLOR.WHITE200 : col);
        ctx.line.render((SPRITE_RENDERER)ctx.r, editX1 + 6, editX2, editY1 + 4, editY2);
    }

    public static boolean button(EconContext ctx, int x, int y, int w, int h, CharSequence text) {
        boolean over = hit(ctx, x, x + w, y, y + h);
        (over ? COLOR.WHITE50 : COLOR.WHITE25)
            .render((SPRITE_RENDERER)ctx.r, x, x + w, y, y + h);
        ctx.label.clear().add(text);
        ctx.label.color(over ? COLOR.WHITE200 : COLOR.WHITE150);
        ctx.label.render((SPRITE_RENDERER)ctx.r, x + 10, x + w, y + 6, y + h);
        return over && ctx.leftClicked;
    }

    public static boolean toggle(EconContext ctx, int x, int y, int w, int h,
                                  boolean on, CharSequence text) {
        boolean over = hit(ctx, x, x + w, y, y + h);
        COLOR color = on ? COLOR.GREENISH : COLOR.WHITE25;
        color.render((SPRITE_RENDERER)ctx.r, x, x + w, y, y + h);
        if (over && ctx.leftClicked) {
            on = !on;
        }
        ctx.label.clear().add(text);
        ctx.label.color(on ? COLOR.WHITE200 : COLOR.WHITE120);
        ctx.label.render((SPRITE_RENDERER)ctx.r, x + 8, x + w, y + 5, y + h);
        return on;
    }
}
```

- [ ] **Step 5: Compile**

Run: `mvn compile -pl .`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/vannon/syx/economy/ui/EconWidgets.java
git commit -m "feat(ui): extract EconWidgets from EconomyWindow (slider, valueField, button, toggle, scrollbar, hit) with BUG-03 and BUG-04 fixes"
```

---

### Task 4: EconWindowBase.java — Abstract Interrupter Shell

**Files:**
- Create: `src/vannon/syx/economy/ui/EconWindowBase.java`
- Read from: `src/vannon/syx/economy/core/EconomyWindow.java:1-166` (fields, constructor), `168-199` (hover, takeScroll), `200-256` (click), `248-256` (toggle), `275-319` (placeButton, placeWindow, winW, menuRec, tabRec), `321-383` (render), `385-399` (renderButton, frame), `401-472` (renderStatusIndicators, indicatorWidth, drawIndicator, colors), `474-507` (renderTabs), `943-947` (ownerRec), `949-987` (beginEdit, commit, cancelEdit), `2016-2063` (InputBlocker)

**Interfaces:**
- Consumes: snake2d.CORE, snake2d.MButt, snake2d.Renderer, snake2d.SPRITE_RENDERER, snake2d.util.datatypes.COORDINATE, snake2d.util.datatypes.Rec, init.sprite.UI.UI, util.gui.misc.GText, view.interrupter.Interrupter, view.main.VIEW, vannon.syx.economy.core.*
- Produces: `EconWindowBase(String title, EconTab[] tabs)` — abstract class, subclasses pass their 3 tabs

- [ ] **Step 1: Create EconWindowBase skeleton with fields, constructor, InputBlocker**

Copy all non-tab-specific code from EconomyWindow into EconWindowBase. The class extends Interrupter and contains the InputBlocker as a nested class (moved from EconomyWindow). Key changes from EconomyWindow:
- `mouseX`/`mouseY` removed — coordinates come from `mCoo` in hover(), stored temporarily
- Build `EconContext` in render() before delegating to tabs
- `tabs` array replaces switch(activeTab) dispatch
- KPI header (renderStatusIndicators) renders in every window
- BUG-01 FIX: coordinates ONLY from InputBlocker.hover() path. Remove the EconomyWindow.hover() fallback. The InputBlocker's hover() receives correct UI-space coordinates from the Interrupter system.

```java
package vannon.syx.economy.ui;

import init.constant.C;
import init.sprite.UI.UI;
import snake2d.CORE;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.Rec;
import snake2d.util.sprite.text.StringInputSprite;
import util.gui.misc.GBox;
import util.gui.misc.GText;
import view.interrupter.Interrupter;
import view.main.VIEW;
import vannon.syx.economy.core.*;

public abstract class EconWindowBase extends Interrupter {
    // BUG-05 FIX: button wider (120 instead of 92) so "WIRTSCHAFT" fits
    private static final int BTN_W = 120;
    private static final int BTN_H = 30;
    private static final int WIN_W_MAX = 1280;
    private static final int WIN_H_MAX = 1160;
    private static final int WIN_W_MIN = 900;
    private static final int WIN_H_MIN = 640;
    private static final int PAD = 18;

    private final String title;
    private final EconTab[] tabs;
    private int activeTabIndex = 0;
    private boolean open = false;

    // BUG-01 FIX: coordinates from InputBlocker only, no dual path
    private int mouseX, mouseY;
    private final Rec btn = new Rec();
    private final Rec win = new Rec(12.0, 912.0, 60.0, 700.0);
    private final GText label;
    private final GText line;
    private final StringInputSprite input;
    private final InputBlocker inputBlocker;

    private Object grabbedId;
    private Object editingId;
    private int grabX1, grabX2;
    private int pendingMin, pendingMax;
    private Consumer<Integer> pendingSet;
    private boolean leftWasDown, leftClicked;

    protected EconWindowBase(String title, EconTab[] tabs) {
        this.title = title;
        this.tabs = tabs;
        this.label = new GText(UI.FONT().S, 40);
        this.line = new GText(UI.FONT().S, 110);
        this.mouseX = -1;
        this.mouseY = -1;
        this.input = new StringInputSprite(9, UI.FONT().S) {
            protected void enter() { EconWindowBase.this.commit(); }
        };
        this.inputBlocker = new InputBlocker();
    }

    // --- lifecycle ---
    void ensureShown() { inputBlocker.ensureShown(); }

    private void placeButton() {
        btn.setDim(BTN_W, BTN_H);
        btn.moveX1Y1((double)(C.WIDTH() / 2 - 150 - BTN_W), 6.0);
    }

    private void placeWindow() {
        int w = clamp(C.WIDTH() - 24, WIN_W_MIN, WIN_W_MAX);
        int h = clamp(C.HEIGHT() - 60 - 84, WIN_H_MIN, WIN_H_MAX);
        win.setDim((double)w, (double)h);
        win.moveX1Y1(12.0, 60.0);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    // BUG-01: coordinates ONLY from this path (InputBlocker or direct)
    // No EconomyWindow.hover() dual path
    void updateMouse(COORDINATE mCoo) {
        this.mouseX = mCoo.x();
        this.mouseY = mCoo.y();
    }

    // render method called from outside (MainScript or InputBlocker)
    public void doRender(Renderer r, float ds) {
        if (!EconConfig.windowEnabled) return;
        if (mouseX == -1 && mouseY == -1) {
            COORDINATE mCoo = CORE.getInput().getMouse().getCoo();
            updateMouse(mCoo);
        }
        inputBlocker.ensureShown();
        EconomySim sim = EconomySim.active();
        if (sim == null) return;
        placeButton();
        placeWindow();
        // render button
        boolean hot = mouseX >= btn.x1() && mouseX <= btn.x2() && mouseY >= btn.y1() && mouseY <= btn.y2();
        (hot || open ? COLOR.WHITE50 : COLOR.WHITE25)
            .render((SPRITE_RENDERER)r, btn.x1(), btn.x2(), btn.y1(), btn.y2());
        label.clear().add(title);
        label.color(hot || open ? COLOR.WHITE200 : COLOR.WHITE150);
        label.render((SPRITE_RENDERER)r, btn.x1() + 10, btn.x2(), btn.y1() + 6, btn.y2());

        if (!open) {
            grabbedId = null;
            editingId = null;
            return;
        }
        if (!MButt.LEFT.isDown()) grabbedId = null;
        leftClicked = MButt.LEFT.isDown() && !leftWasDown;
        leftWasDown = MButt.LEFT.isDown();

        // frame
        COLOR.WHITE15.render((SPRITE_RENDERER)r, win.x1(), win.x2(), win.y1(), win.y2());
        COLOR.WHITE35.render((SPRITE_RENDERER)r, win.x1(), win.x2(), win.y1(), win.y1()+1);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, win.x1(), win.x2(), win.y2()-1, win.y2());
        COLOR.WHITE35.render((SPRITE_RENDERER)r, win.x1(), win.x1()+1, win.y1(), win.y2());
        COLOR.WHITE35.render((SPRITE_RENDERER)r, win.x2()-1, win.x2(), win.y1(), win.y2());

        // BUG-02 FIX: flush before KPI rendering
        // KPI header
        renderKpiHeader(r, sim);

        // tab bar
        renderTabBar(r);

        // tab content
        int y = win.y1() + 42 + 84;
        EconContext ctx = new EconContext(r, sim,
            new COORDINATE() { public int x() { return mouseX; } public int y() { return mouseY; } },
            leftClicked, MButt.LEFT.isDown(), 0,
            win.x1(), win.x2(), win.x2() - win.x1() - 36,
            label, line, input);
        ctx.grabbedId = grabbedId;
        ctx.editingId = editingId;
        tabs[activeTabIndex].render(ctx, y);
        grabbedId = ctx.grabbedId;
        editingId = ctx.editingId;

        // commit on click outside fields
        if (leftClicked && editingId != null) {
            commit();
        }
    }

    // handle button click — returns true if consumed
    boolean handleClick(MButt button) {
        if (!EconConfig.windowEnabled) return false;
        if (open && button == MButt.RIGHT) {
            if (editingId != null) { editingId = null; CORE.getInput().clearAllInput(); return true; }
            open = false; return true;
        }
        placeButton();
        placeWindow();
        if (button == MButt.LEFT
            && mouseX >= btn.x1() && mouseX <= btn.x2()
            && mouseY >= btn.y1() && mouseY <= btn.y2()) {
            open = !open;
            if (open) tabs[activeTabIndex].onOpen();
            return true;
        }
        if (!open) return false;
        if (button != MButt.LEFT) return false;
        // tab bar clicks
        int tabW = (win.x2() - win.x1() - 36 - 6 * (tabs.length - 1)) / tabs.length;
        int tabY = win.y1() + 42 + 30 + 6;
        for (int i = 0; i < tabs.length; i++) {
            int tx = win.x1() + 18 + i * (tabW + 6);
            if (mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabY && mouseY <= tabY + 30) {
                if (activeTabIndex != i) {
                    activeTabIndex = i;
                    tabs[i].onOpen();
                }
                return true;
            }
        }
        return true; // consumed (click inside window)
    }

    boolean isOpen() { return open; }

    void toggleWindow() {
        if (!EconConfig.windowEnabled) return;
        if (open && editingId != null) { editingId = null; CORE.getInput().clearAllInput(); }
        open = !open;
    }

    private void renderKpiHeader(Renderer r, EconomySim sim) {
        int h = 28, gap = 8;
        int x = win.x2() - 18;
        int y = win.y1() + 6;
        String treasury = CompactNumber.format(sim.treasury());
        String gini = String.format("%.2f", sim.stats().gini);
        String stage = sim.progression().stage.displayName;
        // ... same rendering as EconomyWindow.renderStatusIndicators ...
    }

    private void renderTabBar(Renderer r) {
        int tabW = (win.x2() - win.x1() - 36 - 6 * (tabs.length - 1)) / tabs.length;
        int tabY = win.y1() + 42 + 30 + 6;
        for (int i = 0; i < tabs.length; i++) {
            int tx = win.x1() + 18 + i * (tabW + 6);
            boolean sel = i == activeTabIndex;
            boolean hot = mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabY && mouseY <= tabY + 30;
            (sel ? COLOR.WHITE50 : (hot ? COLOR.WHITE35 : COLOR.WHITE25))
                .render((SPRITE_RENDERER)r, tx, tx + tabW, tabY, tabY + 30);
            label.clear().add(tabs[i].label());
            label.color(sel ? COLOR.WHITE200 : COLOR.WHITE120);
            label.render((SPRITE_RENDERER)r, tx + 8, tx + tabW, tabY + 5, tabY + 30);
        }
    }

    private void commit() {
        if (pendingSet != null && editingId != null) {
            try {
                String typed = input.text().toString().trim();
                if (!typed.isEmpty()) {
                    int v = Integer.parseInt(typed);
                    v = Math.max(pendingMin, Math.min(pendingMax, v));
                    pendingSet.accept(v);
                }
            } catch (NumberFormatException ignored) {}
        }
        editingId = null;
        pendingSet = null;
        CORE.getInput().clearAllInput();
    }

    // InputBlocker nested class — identical to EconomyWindow.InputBlocker
    private final class InputBlocker extends Interrupter {
        void ensureShown() {
            if (manager() != VIEW.current().uiManager) {
                if (isActivated()) hide();
                show(VIEW.current().uiManager);
            }
        }
        protected boolean hover(COORDINATE mCoo, boolean mouseHasMoved) {
            if (!EconConfig.windowEnabled) return false;
            EconWindowBase.this.updateMouse(mCoo);
            placeButton(); placeWindow();
            return (mouseX >= btn.x1() && mouseX <= btn.x2() && mouseY >= btn.y1() && mouseY <= btn.y2())
                || (open && mouseX >= win.x1() && mouseX <= win.x2() && mouseY >= win.y1() && mouseY <= win.y2());
        }
        protected void mouseClick(MButt button) { EconWindowBase.this.handleClick(button); }
        protected void hoverTimer(GBox text) {}
        protected boolean render(Renderer r, float ds) { return true; }
        protected boolean update(float ds) { return true; }
    }
}
```

- [ ] **Step 2: Compile**

Run: `mvn compile -pl .`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/ui/EconWindowBase.java
git commit -m "feat(ui): add EconWindowBase abstract Interrupter shell with BUG-01/BUG-02/BUG-05 fixes"
```

---

### Task 5: 3 Window Classes (WindowOverview, WindowEconomy, WindowState)

**Files:**
- Create: `src/vannon/syx/economy/ui/WindowOverview.java`
- Create: `src/vannon/syx/economy/ui/WindowEconomy.java`
- Create: `src/vannon/syx/economy/ui/WindowState.java`

**Interfaces:**
- Consumes: EconWindowBase, OverviewTabs, EconomyTabs, StateTabs
- Produces: `WindowOverview extends EconWindowBase`, `WindowEconomy extends EconWindowBase`, `WindowState extends EconWindowBase`

- [ ] **Step 1: Create WindowOverview.java**

```java
package vannon.syx.economy.ui;

public final class WindowOverview extends EconWindowBase {
    public WindowOverview() {
        super("ÜBERSICHT", new EconTab[] {
            new OverviewTabs.DashboardTab(),
            new OverviewTabs.CitizensTab(),
            new OverviewTabs.AdvisorTab()
        });
    }
}
```

- [ ] **Step 2: Create WindowEconomy.java**

```java
package vannon.syx.economy.ui;

public final class WindowEconomy extends EconWindowBase {
    public WindowEconomy() {
        super("WIRTSCHAFT", new EconTab[] {
            new EconomyTabs.PricesTab(),
            new EconomyTabs.WagesFirmsTab(),
            new EconomyTabs.SubsidiesTab()
        });
    }
}
```

- [ ] **Step 3: Create WindowState.java**

```java
package vannon.syx.economy.ui;

public final class WindowState extends EconWindowBase {
    public WindowState() {
        super("STAAT", new EconTab[] {
            new StateTabs.WarehouseTab(),
            new StateTabs.TaxesTab(),
            new StateTabs.SocialTab()
        });
    }
}
```

- [ ] **Step 4: Compile (will fail — tabs not yet created)**

Run: `mvn compile -pl .`
Expected: COMPILE ERROR — OverviewTabs, EconomyTabs, StateTabs not found (expected, resolved in Tasks 6–8)

- [ ] **Step 5: Commit**

```bash
git add src/vannon/syx/economy/ui/WindowOverview.java src/vannon/syx/economy/ui/WindowEconomy.java src/vannon/syx/economy/ui/WindowState.java
git commit -m "feat(ui): add 3 window classes — WindowOverview, WindowEconomy, WindowState"
```

---

### Task 6: OverviewTabs.java — DashboardTab, CitizensTab, AdvisorTab

**Files:**
- Create: `src/vannon/syx/economy/ui/OverviewTabs.java`
- Read from: `src/vannon/syx/economy/core/EconomyWindow.java:1149-1208` (renderDashboard), `1209-1374` (renderDistribution), `682-770` (renderCitizens), `2168-2602` (renderAdvisor)

**Interfaces:**
- Consumes: EconTab, EconContext, EconWidgets, EconTexts, EconomySim, HousingMarket, WealthStats, CompactNumber
- Produces: `OverviewTabs.DashboardTab`, `OverviewTabs.CitizensTab`, `OverviewTabs.AdvisorTab` (static nested classes implementing EconTab)

- [ ] **Step 1: Create OverviewTabs.java with DashboardTab**

Migrate renderDashboard() body. Replace `this.treasuryChart`/`this.giniChart` with local ChartPanel instances in the tab class. Replace `this.win.x1()/x2()` with `ctx.x1`/`ctx.x2`. Replace widget calls with `EconWidgets.*`.

- [ ] **Step 2: Add CitizensTab**

Migrate renderCitizens() + renderDistribution(). Combine into one tab with two sections. Replace `EconConfig.housingMarketEnabled = this.toggle(...)` with local variable + `EconWidgets.toggle(...)`.

- [ ] **Step 3: Add AdvisorTab**

Migrate renderAdvisor(). Keep the 6 KPI boxes and 5 Ampel lights. Replace all `this.slider(...)` with `EconWidgets.slider(ctx, ...)`. Replace `this.valueField(...)` with `EconWidgets.valueField(ctx, ...)`.

- [ ] **Step 4: Compile**

Run: `mvn compile -pl .`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/vannon/syx/economy/ui/OverviewTabs.java
git commit -m "feat(ui): migrate Dashboard, Citizens, Advisor tabs to OverviewTabs"
```

---

### Task 7: EconomyTabs.java — PricesTab, WagesFirmsTab, SubsidiesTab

**Files:**
- Create: `src/vannon/syx/economy/ui/EconomyTabs.java`
- Read from: `src/vannon/syx/economy/core/EconomyWindow.java:509-616` (renderPrices), `1375-1465` (renderWages), `2064-2167` (renderFirms), `617-681` (renderSubsidies)

**Interfaces:**
- Consumes: EconTab, EconContext, EconWidgets, FlowPrices, FlowMeter, FirmLedger, StateWageMarket, ProductionSubsidies
- Produces: `EconomyTabs.PricesTab`, `EconomyTabs.WagesFirmsTab`, `EconomyTabs.SubsidiesTab`

- [ ] **Step 1: Create EconomyTabs.java with PricesTab, WagesFirmsTab, SubsidiesTab**

Same migration pattern as Task 6. Replace all instance field accesses with EconContext. Replace all widget calls with EconWidgets. Each tab manages its own scroll state as a local field.

- [ ] **Step 2: Compile & Commit**

```bash
git add src/vannon/syx/economy/ui/EconomyTabs.java
git commit -m "feat(ui): migrate Prices, Wages/Firms, Subsidies tabs to EconomyTabs"
```

---

### Task 8: StateTabs.java — WarehouseTab, TaxesTab, SocialTab + UX Features

**Files:**
- Create: `src/vannon/syx/economy/ui/StateTabs.java`
- Read from: `src/vannon/syx/economy/core/EconomyWindow.java:771-881` (renderStateWarehouses), `882-942` (renderCrownMarket), `1466-1575` (renderTaxes), `1576-1601` (renderReligion), `1602-1700` (renderCorvee), `1701-1749` (renderRelief), `1750-1811` (renderForeignTrade), `1812-1936` (renderBooks)
- Modify: `src/vannon/syx/economy/core/StateWarehouses.java` (add standardizeAllPrices, globalPriceScale)

**Interfaces:**
- Consumes: EconTab, EconContext, EconWidgets, StateWarehouses, Taxes, ReligionMarket, CorveeController, GrainDole, ForeignTradeLedger
- Produces: `StateTabs.WarehouseTab`, `StateTabs.TaxesTab`, `StateTabs.SocialTab`
- Modifies: `StateWarehouses.standardizeAllPrices(FlowPrices)` → void, `StateWarehouses.setGlobalPriceScale(int percent)` → void

- [ ] **Step 1: Add standardizeAllPrices() to StateWarehouses**

```java
// In StateWarehouses.java, add:
public void standardizeAllPrices(FlowPrices prices) {
    for (int i = 0; i < buyPrice.length && i < sellPrice.length; i++) {
        int market = prices.priceRoundedUp(i);
        if (market > 0) {
            buyPrice[i] = clampPrice((int)(market * 0.80));
            sellPrice[i] = clampPrice((int)(market * 1.10));
        }
    }
}

public void setGlobalPriceScale(FlowPrices prices, int percent) {
    // percent: 50–150, scales buyPrice/sellPrice relative to market
    for (int i = 0; i < buyPrice.length && i < sellPrice.length; i++) {
        int market = prices.priceRoundedUp(i);
        if (market > 0) {
            buyPrice[i] = clampPrice(market * percent / 100);
            int sellBase = (int)(market * 1.10);
            sellPrice[i] = clampPrice(sellBase * percent / 100);
        }
    }
}
```

- [ ] **Step 2: Create StateTabs.java with WarehouseTab**

Includes:
- Standardize-Button: `if (EconWidgets.button(ctx, x, y, 200, 26, "STANDARDISIEREN")) { ctx.sim.stateWarehouses().standardizeAllPrices(ctx.sim.flowPrices()); }`
- Global Price Slider: `int pct = EconWidgets.slider(ctx, "global_price_pct", x, y, currentPct, 50, 150, 5);`
- Mode toggle per warehouse: NORMAL / NUR KAUFEN / NUR VERKAUFEN (replaces LIQUIDIEREN/HORTEN)
- Crown market section

- [ ] **Step 3: Add TaxesTab**

Migrate renderTaxes() + renderReligion(). Combine into one tab.

- [ ] **Step 4: Add SocialTab**

Migrate renderRelief() + renderCorvee() + renderBooks(). Add placeholder comments:

```java
// 🔲 PLATZHALTER Phase 5d: ForeignTradeLedger-Integration
// Wenn ForeignTradeLedger aktiv, zeige hier Tages-Handelsbilanz und aktive Fraktionen
if (ctx.sim.foreignTrade() != null) {
    // ctx.line.clear().add("Handelsbilanz: ...");
}
```

- [ ] **Step 5: Compile**

Run: `mvn compile -pl .`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/vannon/syx/economy/ui/StateTabs.java src/vannon/syx/economy/core/StateWarehouses.java
git commit -m "feat(ui): migrate Warehouse, Taxes, Social tabs with Standardize button, global price slider, warehouse modes, placeholders"
```

---

### Task 9: EconTexts.java — Replace 6 Foreign Words

**Files:**
- Modify: `src/vannon/syx/economy/core/EconTexts.java`

- [ ] **Step 1: Rename labels**

Replace these exact field initializations in EconTexts.java:

```java
// OLD → NEW
public static final String tabGranary    = "LAGER";         // → "STAATSLAGER"
public static final String granBought    = "Kornspeicher: gekauft "; // → "Staatl. Einkauf: "
public static final String granBtnHoard  = "HORTEN (gehalten)";      // → "NORMALBETRIEB"
public static final String granBtnLiq    = "LIQUIDIEREN";            // → "NUR VERKAUFEN"
public static final String granBtnLiqAll = "ALLES LIQUIDIEREN";      // → "ALLE LAGER ABVERKAUFEN"
public static final String pricesColumnAnchor = "Handelsanker";      // → "Importpreis"
public static final String pricesColumnMultiple = "Vielfaches";      // → "Faktor"
public static final String pricesColumnCoverage = "Deckung";         // → "Vorrat %"
public static final String wageMarginal  = "   Marginal ";           // → "   Grenzertrag "
public static final String taxLiturgyOn  = "LITURGIE AN";           // → "REICHENABGABE AN"
public static final String taxLiturgyOff = "Liturgie aus";           // → "Reichenabgabe aus"
public static final String taxMarketSkim = "Markt-Abschöpfung";      // → "Marktsteuer"
```

- [ ] **Step 2: Add new labels for warehouse modes**

```java
public static final String granBtnNormal    = "NORMALBETRIEB";
public static final String granBtnBuyOnly   = "NUR KAUFEN";
public static final String granBtnSellOnly  = "NUR VERKAUFEN";
public static final String granBtnStandardize = "STANDARDISIEREN";
public static final String granGlobalPrice  = "Preisniveau %";
```

- [ ] **Step 3: Compile**

Run: `mvn compile -pl .`
Expected: BUILD SUCCESS (old labels still referenced in EconomyWindow but that gets deleted next)

- [ ] **Step 4: Commit**

```bash
git add src/vannon/syx/economy/core/EconTexts.java
git commit -m "ux(ui): replace 6 foreign words, add warehouse mode labels"
```

---

### Task 10: Wire Everything — Delete EconomyWindow, Update MainScript

**Files:**
- Delete: `src/vannon/syx/economy/core/EconomyWindow.java`
- Modify: `src/vannon/syx/economy/core/MainScript.java` — find EconomyWindow references, replace with 3 windows
- Modify: `tools/phase47-shield.sh` — update file paths in gate rules

**Interfaces:**
- Consumes: WindowOverview, WindowEconomy, WindowState
- Produces: MainScript calls `overview.doRender(r, ds)`, `economy.doRender(r, ds)`, `state.doRender(r, ds)` in its render loop

- [ ] **Step 1: Update MainScript.java**

Find all references to `EconomyWindow` in MainScript.java. Replace with:
```java
private final WindowOverview overviewWindow = new WindowOverview();
private final WindowEconomy economyWindow = new WindowEconomy();
private final WindowState stateWindow = new WindowState();
```
In render loop:
```java
overviewWindow.ensureShown();
overviewWindow.doRender(r, ds);
economyWindow.ensureShown();
economyWindow.doRender(r, ds);
stateWindow.ensureShown();
stateWindow.doRender(r, ds);
```

- [ ] **Step 2: Delete EconomyWindow.java**

```bash
git rm src/vannon/syx/economy/core/EconomyWindow.java
```

- [ ] **Step 3: Update phase47-shield.sh**

Update the allow-list path from `src/vannon/syx/economy/core/EconomyWindow.java` to the new UI package files.

- [ ] **Step 4: Compile & Test**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

Run: `mvn test`
Expected: 24 tests pass, BUILD SUCCESS

Run: `bash tools/phase47-shield.sh`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/vannon/syx/economy/core/MainScript.java tools/phase47-shield.sh
git rm src/vannon/syx/economy/core/EconomyWindow.java
git commit -m "refactor(ui): delete EconomyWindow god-file, wire 3 modular windows in MainScript"
```

---

### Task 11: Konflikt-Hebel — Mutual-Exclusion Logic

**Files:**
- Modify: `src/vannon/syx/economy/ui/StateTabs.java` (WarehouseTab — autoTune vs manual prices)
- Modify: `src/vannon/syx/economy/ui/EconomyTabs.java` (WagesFirmsTab — firmSizing vs stateWage)
- Modify: `src/vannon/syx/economy/ui/StateTabs.java` (SocialTab — corvee vs oddjob)
- Modify: `src/vannon/syx/economy/ui/StateTabs.java` (TaxesTab — liturgy vs wealthTax)

**Interfaces:**
- Consumes: EconConfig booleans, EconWidgets.toggle
- Produces: UI greys out conflicting options with tooltip explanation

- [ ] **Step 1: WarehouseTab — autoTune disables manual prices**

```java
// In WarehouseTab.render():
boolean autoTune = EconConfig.warehouseAutoTuneEnabled;
if (autoTune) {
    // Grey out manual price fields, show tooltip
    ctx.line.clear().add("(Auto-Tune aktiv — manuelle Preise ignoriert)");
    ctx.line.color(COLOR.WHITE100);
    ctx.line.render((SPRITE_RENDERER)ctx.r, x, ctx.x2 - 18, y, y + 12);
}
// valueField calls use `autoTune ? COLOR.WHITE25 : ...` for color
```

Same pattern for all 7 conflict pairs. Each conflicting option pair gets:
- If A is ON: B's UI element is greyed out + tooltip "Deaktiviert weil [A] aktiv ist"
- The EconConfig field is NOT changed — only the UI is greyed

- [ ] **Step 2: Apply to all 7 conflict pairs**

1. warehouseAutoTuneEnabled → grey out buyPrice/sellPrice fields
2. firmSizingEnabled → grey out stateWage per-blueprint sliders
3. foodAffordabilityGateEnabled → grey out handoutToWallet toggle
4. liturgyEnabled + taxesEnabled (wealth) → mutual exclusion hint
5. oddjobWageEnabled vs corveeEnabled → mutual exclusion hint
6. stateFundedWageRegulationOnly → if ON, grey out wagesEnabled toggle
7. disableVanillaInflation → grey out FlowPrices-dependent price caps

- [ ] **Step 3: Compile & Commit**

Run: `mvn compile -pl .`
Expected: BUILD SUCCESS

```bash
git add src/vannon/syx/economy/ui/EconomyTabs.java src/vannon/syx/economy/ui/StateTabs.java
git commit -m "ux(ui): add mutual-exclusion greying for 7 conflict lever pairs"
```

---

### Task 12: Final Verification

- [ ] **Step 1: Full build**

Run: `mvn clean compile`
Expected: BUILD SUCCESS, zero new warnings

- [ ] **Step 2: Tests**

Run: `mvn test`
Expected: 24/24 pass, BUILD SUCCESS

- [ ] **Step 3: Shield gates**

Run: `bash tools/phase47-shield.sh && bash tools/build-gate.sh`
Expected: Both PASS

- [ ] **Step 4: LOC verification**

Run: `find src/vannon/syx/economy/ui -name '*.java' | xargs wc -l | tail -1`
Expected: ~2345 total LOC (vs 3081 in old EconomyWindow)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "release(ui): 3-window UX refactor complete — 2345 LOC, 5 bugs fixed, 7 conflicts resolved, 6 words replaced"
```

---

## Definition of Done

- [ ] EconomyWindow.java deleted
- [ ] 10 new files in `src/vannon/syx/economy/ui/` totaling ~2345 LOC
- [ ] 3 windows: Übersicht (Dashboard, Bürger, Berater), Wirtschaft (Preise, Löhne/Firmen, Subventionen), Staat (Staatslager, Steuern, Soziales)
- [ ] BUG-01: Zoom-Click fixed — single coordinate path via InputBlocker
- [ ] BUG-02: Dashboard textures fixed — flush before KPI rendering
- [ ] BUG-03: Slider grab fixed — persists when mouse leaves slider while LEFT down
- [ ] BUG-04: Slider overflow fixed — fill clamped to slider bounds
- [ ] BUG-05: Button text fixed — button widened to 120px
- [ ] 7 conflict lever pairs greyed out with tooltips
- [ ] 6 foreign words replaced in EconTexts
- [ ] Standardize button + global price slider in WarehouseTab
- [ ] NORMAL/NUR KAUFEN/NUR VERKAUFEN modes replace LIQUIDIEREN/HORTEN
- [ ] Placeholder comments for Phase 5 features (CitizenProfile, ForeignTrade, RoomOperatingMode)
- [ ] `mvn clean compile` BUILD SUCCESS
- [ ] `mvn test` 24/24 pass
- [ ] `phase47-shield.sh` PASS
