# Onboarding & Player Agency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement immediate player agency controls in Tab 1, 4-button EconHUD quick-action bar, and a 10-minute interactive popup tutorial flow.

**Architecture:** 
- `EconTutorialController`: Lightweight state machine handling 4-stage onboarding popups triggered by timer and game state.
- `WindowOverview`: Tab 1 consolidated control panel with wages, taxes, state warehouse mode, and emergency liquidations active by default.
- `EconHud`: HUD extensions for instant 1-click action triggers.

**Tech Stack:** Java 21, Songs of Syx V71 GUI API (`GuiSection`, `GButt`, `GText`, `GPanel`), Maven.

## Global Constraints

- Game Version: Songs of Syx V71.44
- Mod Version: v0.14.0
- All controls active by default (`enabled = true`).
- Standard doc headers and `verify-doc-sync.sh` gates must pass.

---

### Task 1: Onboarding Tutorial Controller (`EconTutorialController.java`)

**Files:**
- Create: `src/vannon/syx/economy/core/EconTutorialController.java`
- Modify: `src/vannon/syx/economy/core/EconomySim.java`

**Interfaces:**
- Consumes: `EconomySim` time and state indicators.
- Produces: `EconTutorialController` methods `update(double ds)`, `activeStage()`, `dismissCurrent()`.

- [ ] **Step 1: Create `EconTutorialController.java`**

```java
package vannon.syx.economy.core;

public final class EconTutorialController {
    public enum Stage {
        NONE,
        WELCOME_WAGES,
        WAREHOUSE_MODE,
        TAXES_FISCAL,
        EMERGENCY_ACTIONS,
        COMPLETED
    }

    private Stage currentStage = Stage.NONE;
    private double timer = 0.0;
    private boolean active = true;

    public void update(double ds) {
        if (!active || currentStage == Stage.COMPLETED) return;
        timer += ds;

        if (currentStage == Stage.NONE && timer >= 10.0) {
            currentStage = Stage.WELCOME_WAGES;
        } else if (currentStage == Stage.WELCOME_WAGES && timer >= 120.0) {
            currentStage = Stage.WAREHOUSE_MODE;
        } else if (currentStage == Stage.WAREHOUSE_MODE && timer >= 300.0) {
            currentStage = Stage.TAXES_FISCAL;
        } else if (currentStage == Stage.TAXES_FISCAL && timer >= 600.0) {
            currentStage = Stage.EMERGENCY_ACTIONS;
        }
    }

    public Stage currentStage() { return currentStage; }
    public void dismissCurrent() {
        if (currentStage == Stage.EMERGENCY_ACTIONS) {
            currentStage = Stage.COMPLETED;
        } else {
            timer += 100.0;
        }
    }
    public void disableTutorial() { active = false; }
    public boolean isActive() { return active; }
}
```

- [ ] **Step 2: Connect `EconTutorialController` inside `EconomySim.java`**

Add field:
```java
private final EconTutorialController tutorial = new EconTutorialController();
public EconTutorialController tutorial() { return tutorial; }
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/vannon/syx/economy/core/EconTutorialController.java src/vannon/syx/economy/core/EconomySim.java
git commit -m "feat(tutorial): add EconTutorialController state machine"
```

---

### Task 2: Consolidated Tab 1 Control Dashboard (`WindowOverview.java`)

**Files:**
- Modify: `src/vannon/syx/economy/ui/WindowOverview.java`

**Interfaces:**
- Consumes: `EconomySim`, `StateWarehouses`, `EconConfig`.
- Produces: Tab 1 UI rendering KPIs + Wage Sliders + Tax Controls + Warehouse Modes + Emergency Actions.

- [ ] **Step 1: Integrate Controls & Tutorial Popup into `DashboardTab`**

Add direct action controls (Lohn-Slider, Staatslager-Modus, Steuern-Toggle, Not-Liquidation) into `DashboardTab.build()` in `WindowOverview.java`.

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/ui/WindowOverview.java
git commit -m "feat(ui): consolidate all core levers into Tab 1 Dashboard"
```

---

### Task 3: HUD Quick-Action Bar (`EconHud.java`)

**Files:**
- Modify: `src/vannon/syx/economy/ui/EconHud.java`

**Interfaces:**
- Consumes: `EconomySim`.
- Produces: 4-button quick action section rendered inside HUD.

- [ ] **Step 1: Add Quick Action buttons to `EconHud`**

Buttons:
- `[Lohn: X D]`
- `[Lager: Modus]`
- `[Not-Liquidation]`
- `[Steuern]`

- [ ] **Step 2: Verify compilation and doc sync**

Run: `mvn package -DskipTests`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/ui/EconHud.java
git commit -m "feat(hud): add 4-button quick-action bar for player agency"
```
