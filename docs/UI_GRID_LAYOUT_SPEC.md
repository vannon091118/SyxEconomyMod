# UI Grid-Layout-Spec — Sprint M-UI-3.5 Prototyp

> **Stand:** 2026-07-31 | **Basis:** v0.13.106+M-UI-3 (Tab-Modul-Split) | **Engine:** Songs of Syx V71.44
> **Methodik:** Fluent-API + Pre-Computed-Cells (kein Reflection-in-Render-Loop)
> **Fokus:** Layout.java Prototyp + Migration-Plan für die 16 Tabs (5 Windows)
> **Tag-Konvention:** `[HYP]` für Prototyp-Schätzungen — Implementierung in Sprint M-UI-5 liefert parse_metrics-Werte.

## Executive Summary

Ersetze hardcoded `x + 170` / `x + 240` / `x + 380` / `x + 480` durch ein deklaratives Grid-Layout. Trade-Off: ~190 SLOC Layout-Komponente `[HYP: prototype-estimate]`, −~150 SLOC Layout-Boilerplate verteilt über 16 Tabs `[HYP: speculative-migration-savings]`. **Migration selbst:** Sprint M-UI-5 (separater Sprint, da kein Rule-11 Theme-Scope für Tab-Modifikation in M-UI-3.5; Prototyp ist additiv).

## 1 · Motivation (Problem)

**Symptom:** Tabs verwenden hardcoded Pixel-Offsets statt deklarativer Spalten-Position `[HYP: requires-M-UI-3-tab-files-verification]`:

| Datei | Magic-Numbers | Cells/Tab (geschätzt) `[HYP]` |
|---|---:|---:|
| `Overview/DashboardTab.java` | `x+240`, `x+480` | ~30 |
| `Overview/DemographicsTab.java` | `x+110`, `x+180`, `x+290` | ~25 |
| `Overview/AdvisorTab.java` | `x+160`, `x+220`, `x+400`, `x+440` | ~40 |
| `Overview/PropertyTab.java` | `x+380` | ~12 |
| `WindowEconomy.java` (6 Tabs) | `x+170`, `x+380`, `x+480` | ~50 |
| `WindowState.java` (6 Tabs) | `x+380` | ~50 |

Folgen:
- **Column-Width-Change** → 16 Anfass-Stellen, Magic-Numbers inkonsistent (170 vs 240)
- **Tab-Code verseucht** mit `y += 50; y += 60;` Boilerplate (Coordinate-Walking)
- **Reflow unmöglich** (Window-Resize reagiert nicht propportional)
- **Keine Layout-Unit-Tests** möglich: Pixel-Position ist emergent, nicht deklarativ
- **Spalten-Unterscheidung** (z.B. Advisor nutzt `160` für Trend-Arrow vs `240` für KPI-Col) ist nirgends dokumentiert

## 2 · Goal

Deklaratives Layout. Tab-Code beschreibt **WAS** (welche Widgets in welcher logischen Position), nicht **WO** (Pixel-Koordinaten). Die Layout-Komponente komputiert Positionen einmalig in `build()` und delegiert Render an existierende `EconWindowBase.<widget>`-Helfer.

## 3 · API-Surface

### 3.1 Erzeugung (Builder)

```java
import vannon.syx.economy.ui.Layout;
import snake2d.util.color.COLOR;
import snake2d.util.gui.GuiSection;

Layout grid = Layout.grid(3, 10)         // 3 Spalten, 10px Gap
    .at(12, 30)                          // Origin (Anker oben-links)
    .cellWidth(240)                      // Spaltenbreite (Default: auto-flow)
    .rowHeight(30);                      // Default-Zellenhöhe (Default: 30)
```

### 3.2 Builder-Methoden (Fluent)

| Methode | Signatur | Effekt |
|---|---|---|
| `static grid(int cols, int gap)` | `(int, int) → Layout` | Neue Grid-Instanz |
| `at(int x, int y)` | `(int, int) → Layout` | Origin setzen |
| `cellWidth(int w)` | `(int) → Layout` | Spaltenbreite setzen |
| `rowHeight(int h)` | `(int) → Layout` | Default-Zellenhöhe |
| `row()` | `() → Layout` | Explizit neue Zeile (manuell nach Spaltenlimit) |
| `newLine()` | `() → Layout` | Vertikaler Spacer + neue Zeile |
| `kpi(label, value, color)` | `(String, String, COLOR) → Layout` | KPI-Cell (1 Spalte, 30px) |
| `icon(sprite, label, value, color)` | `(SPRITE, String, String, COLOR) → Layout` | Icon-KPI-Cell (1 Spalte) |
| `slider(label, supplier, min, max, step, plus, minus)` | `(String, IntSupplier, int, int, int, ACTION, ACTION) → Layout` | Slider-Cell (1 Spalte, 38px) |
| `checkbox(label, initial, setter)` | `(String, boolean, Consumer<Boolean>) → Layout` | Toggle-Cell (1 Spalte, 22px) |
| `header(text)` | `(String) → Layout` | Volle-Zeile Header (col=0, row++) |
| `text(text, width)` | `(String, int) → Layout` | Multi-Line-Text (1 Spalte, auto-height) |
| `span(int colspan)` | `(int) → Layout` | Column-Skip: nächste Cell rückt um (colspan − 1) Spalten weiter. **Nicht** Cell-Stretch (siehe §6.1) — Cell-Stretch über N Spalten wäre Sprint M-UI-5+ (cellStretch-Helper mit width-aware addKpi). |
| `precompute()` | `() → Layout` | Position-Pass ohne Render (für Tests) |
| `build(GuiSection parent)` | `(GuiSection) → void` | Terminal: precompute + render |

### 3.3 Beispiel-Refactor DashboardTab (Ist vs Soll)

**Vorher** (~15 SLOC):
```java
EconWindowBase.addKpi(content, x, y, "Staatskasse", treasuryStr, treasuryColor);
EconWindowBase.addKpi(content, x + 240, y, UI.icons().m.citizen, "Bevölkerung",
        String.valueOf(stats.people), peopleColor);
EconWindowBase.addKpi(content, x + 480, y, "Stufe", prog.stage.displayName, normColor);
y += 50;
EconWindowBase.addKpi(content, x, y, UI.icons().m.heart, "Gini", giniStr, giniColor);
EconWindowBase.addKpi(content, x + 240, y, "Median", medianStr, medianColor);
EconWindowBase.addKpi(content, x + 480, y, UI.icons().m.pickaxe, "Lohn/Tag", wageStr, wageColor);
y += 60;
```

**Nachher** (~7 SLOC):
```java
Layout.grid(3, 10).at(x, y).cellWidth(240)
    .kpi("Staatskasse", treasuryStr, treasuryColor)
    .icon(UI.icons().m.citizen, "Bevölkerung", String.valueOf(stats.people), peopleColor)
    .kpi("Stufe", prog.stage.displayName, normColor)
    .row()
    .icon(UI.icons().m.heart, "Gini", giniStr, giniColor)
    .kpi("Median", medianStr, medianColor)
    .icon(UI.icons().m.pickaxe, "Lohn/Tag", wageStr, wageColor)
    .build(content);
```

LOC-Reduktion im Tab: ~50%; Magic-Numbers eliminiert; einziger Pixel ist `at(x,y)` Origin.

## 4 · Performance-Modell: Generic-Layout-Trap vermeiden

### 4.1 Was wir NICHT machen

❌ **Reflection-in-Render-Loop**: `cell.kind.doRender(parent)` via `Method.invoke(cell, ...)` — JIT kann nicht inlinen, Profiling zeigt 5-10× Slowdown.

❌ **Class.forName-Lookup**: `Class.forName(type).newInstance()` — Class-Loader-Trap, GC-Pressure bei jeder Build.

❌ **Map<Class, Renderer<Cell>> mit Reflection-Key**: JIT-Inlining unmöglich, vtable-Debloat.

❌ **Generic `<T extends Widget>` Layout**: Type-Erasure-Casts + Boxing-Overhead für primitive `int row, col`.

❌ **Polymorphic Sub-Classes mit abstrakter `render()`**: V-Table-Dispatch pro Cell, kein Constant-Folding.

### 4.2 Was wir STATTDESSEN machen

✅ **Sealed Switch auf CellKind-enum**:
```java
private void render(GuiSection parent) {
    switch (this.kind) {
        case KPI:
            EconWindowBase.addKpi(parent, x, y, kpiLabel, kpiValue, kpiColor);
            break;
        case ICON:
            EconWindowBase.addKpi(parent, x, y, iconSprite, kpiLabel, kpiValue, kpiColor);
            break;
        case SLIDER:
            EconWindowBase.addSlider(parent, x, y, sliderLabel, sliderSupplier,
                    sliderMin, sliderMax, sliderStep, sliderPlus, sliderMinus);
            break;
        case CHECKBOX:
            OverviewHelpers.addCheckbox(parent, x, y, cbLabel, cbInitial, cbSetter);
            break;
        case HEADER:
        case TEXT:
            renderText(parent);
            break;
        default:
            throw new IllegalStateException("Unknown CellKind: " + kind);
    }
}
```
**5 konstante Cases** → JIT kompiliert zu dichtem `tableswitch` (oder `lookupswitch` bei Sparse-Enums). Kein Reflection, kein V-Table.

✅ **Pre-Compute-Position einmalig in `.build(parent)`**:
```java
public void build(GuiSection parent) {
    // Pass 1: Pre-Compute (single linear O(N))
    for (Cell c : cells) {
        c.x = originX + c.col * (cellWidth + gap);
        c.y = originY + c.row * rowHeight;
    }
    // Pass 2: Render (single linear, JIT-tableswitch on CellKind)
    for (Cell c : cells) {
        c.render(parent);
    }
}
```

✅ **Mutable Single-Cell-Class mit Union-Fields** (kein Box, kein Generic-T):
```java
private static final class Cell {
    CellKind kind;
    int row, col;
    int x = -1, y = -1;          // mutable, set in precompute step
    // Per-Kind fields (kept allocated, ignored when kind != matching)
    String kpiLabel, kpiValue;
    COLOR kpiColor;
    SPRITE iconSprite;
    String sliderLabel;
    IntSupplier sliderSupplier;
    int sliderMin, sliderMax, sliderStep;
    ACTION sliderPlus, sliderMinus;
    String cbLabel;
    boolean cbInitial;
    Consumer<Boolean> cbSetter;
    String textString;
    int textWidth;
}
```

### 4.3 Bench-Annahme (rough)

Pro Tab-Build: ~30-50 Cells.
- **Pre-Compute**: O(N) ≈ 0.05ms (50 Cells × 1µs each)
- **Render**: O(N) ≈ 0.1ms (50 Cells × 2µs each + Delegation zu addKpi/addSlider)
- **Total**: <0.2ms pro Tab-Build (deutlich unter 16ms Frame-Budget)
- **Vergleich zu Hardcoded**: 0.3-0.5ms (viele separate `addKpi().addSlider()` Calls ohne Pre-Compute-Phase)

GC-Pressure: keine zusätzliche Allocation im Render-Path (Cells als transient ArrayList, nicht new-each-render).

## 5 · Snake2D-Integration

`Layout.java` hat **keine eigene Render-Logik**. Es delegiert vollständig an existierende Helfer (Stand M-UI-3 Visibility-Tweak):

- `EconWindowBase.addKpi(parent, x, y, label, value, color)` — String-Label-Variante
- `EconWindowBase.addKpi(parent, x, y, sprite, label, value, color)` — Icon-Variante (SPRITE-Layer)
- `EconWindowBase.addSlider(parent, x, y, label, IntSupplier, min, max, step, plus, minus)` — Live-Slider
- `OverviewHelpers.addCheckbox(parent, x, y, label, initial, setter)` — Property-Toggle
- Direkt: `new GText(font, width) → parent.add(t, x, y)` für `header()` / `text()`

`Layout` ist **Add-On**: kompatibel mit dem bestehenden UI-Stack ohne Engine-Hook-Refactor.

## 6 · Edge Cases

### 6.1 Column-Skip (`span(int)`)

```java
.slider("Lange Beschreibung", supplier, 0, 100, 5, plus, minus).span(2)
.kpi("Neben-Cell", "...", color)
```
`span(colspan)` rückt die **folgende** Cell um (colspan−1) Spalten weiter. Beispiel: nach `.slider(...)` und `.span(2)` startet `.kpi(...)` zwei Spalten rechts vom Slider — beide Cells werden weiterhin als 1-Spalten-Cells gerendert.

**Nicht implementiert** (Sprint M-UI-5+ Future-Work) — Cell-Stretch über N Spalten, was width-aware `addKpi(parent, x, y, width, ...)` oder ein neuer `addKpiSpan(...)`-Helper erfordern würde. Aktueller Prototyp liefert Column-Skip als 80%-Lösung; für die 16 Tabs (vorwiegend KPI-Reihen + Slider-Inline-Spalten) deckt das die Use-Cases ab. Sprint M-UI-5 kann bei Bedarf `cellStretch(int colspan)` ergänzen mit eigenem Render-Pfad.

### 6.2 Out-of-Bounds (`precompute` Sanity-Check)

Wenn Row-Count explizit überschritten wird (z.B. 11 Cells bei `grid(2, 10)` → 6 Rows statt 5.5), wird der Row-Counter unbegrenzt inkrementiert. `precompute()` (und damit `build()`) wirft `IllegalStateException` bei `row > MAX_GRID_ROWS` (=**16**, abgeleitet aus Tab-Panel-Default-Height). Tests können dies abfangen.

### 6.3 Mixed-Numeric-Tabellen

z.B. Trend-Tabelle in AdvisorTab (Tag|Kasse|Gini|Lohn|Nahrung|Unpaid) ist eine **feste Tabelle** mit fixer Spaltenbreite. Layout eignet sich weniger. Bestehendes Pattern mit `EconWindowBase.addColHeader()` und freier Pixel-Positionierung bleibt für Tabellen-Tabs **reserviert** (WindowEconomy.PricesTab? — nein, M-UI-1 hat das schon als `Layout.grid` migriert in Mentor-Form).

### 6.4 Sub-Grids / Nested-Layout

Mehrere `Layout`-Instanzen können verschachtelt werden (z.B. Header-Bereich als eigenes Grid). Jede `Layout.build(parent)` fügt Cells zur gemeinsamen `parent`-Section hinzu. Keine State-Shared-Mutation.

## 7 · Test-Plan

### 7.1 Unit-Tests (Sprint M-UI-5 mit Mockito-Fixture analog T-COV-9)

1. **`Layout_grid_three_cols_emits_three_horizontal_cells`**: Auto-Advance nach 3 Cells → row=1, col=0
2. **`Layout_at_zero_or_negative_throws`**: Origin-Sanity
3. **`Layout_build_renders_cells_in_grid_order`**: 3×2 KPI-Grid, x/y aus precompute exakt (originX + col*250, originY + row*30)
4. **`Layout_build_throws_on_row_overflow`**: 17 Rows → IllegalStateException
5. **`Layout_slider_delegates_to_econwindowbase_addSlider`**: Verify-Call (Mockito-Mock auf `EconWindowBase` — eigentlich nicht mock-fähig, deshalb direct-addMock)
6. **`Layout_span_override_consumes_two_columns`**: span(2) → col-Skip um 1
7. **`Layout_perf_no_reflection_in_build_pass`**: Reflection-Use-Logger == 0 während Build

### 7.2 Bench-Test (Sprint M-UI-5)

`benchmark/LayoutBench.java`: 1k Builds, GC-Tracking. Akzeptanz: `Layout.declarative.time ≤ Hardcoded.time × 1.05` (= Performance-Parität trotz Indirektion).

## 8 · Migration-Plan

### 8.1 Phase-Roadmap

| Phase | Sprint | Inhalt |
|---|---|---|
| **1** | **M-UI-3.5 (jetzt)** | `Layout.java` Prototyp + Spec. **Keine Tab-Migration.** |
| 2 | M-UI-4 | WindowState-Split (612 LOC, 6 Tabs inner) + WindowEconomy-Split (486 LOC, 6 Tabs inner) — Pattern-Replication aus M-UI-3 |
| 3 | M-UI-5 | Layout-Migration auf 14 verbleibende Tabs (MarketTab/WagesTab/PricesTab/FirmsTab/SubsidiesTab/BooksTab/WarehousesTab/FiscalTab/PublicWorksTab/SocialTab/FaithTab/DebugTab/AdvisorTab/DemographicsTab/PropertyTab). Pilot: MarketTab (kleines 1-KPI-Reihen-Tab). |

### 8.2 Migration-Reihenfolge (Sprint M-UI-5)

1. **Pilot**: `WindowEconomy.MarketTab` — 1 KPI-Reihe, klein, einfaches Layout. Verifizieren dass Spec passt.
2. **Demo**: 2 weitere Tabs pro Window (WindowState, WindowEconomy) — Coverage-Tests.
3. **Sweep**: Alle 14 verbleibenden Tabs.
4. **Refactor-Pass**: alte Hardcoded `x + N` Patterns auskommentieren/rauswerfen; Konstanten-Migration auf `Layout.grid(WIDTH, GAP)`.

### 8.3 Backward-Compatibility

`Layout.java` ist **additiv** — keine Refactor bestehender Tabs bis Sprint M-UI-5. Pilot-Tab (M-UI-5.1) wird in eigenem Sprint migriert und committed.

## 9 · Definition of Done für Sprint M-UI-3.5 (jetzt)

- [x] `docs/UI_GRID_LAYOUT_SPEC.md` (dieses Dokument)
- [x] `src/vannon/syx/economy/ui/Layout.java` Prototyp (Grid + Fluent API + Sealed-Switch-Render + Pre-Compute-Pass)
- [x] `mvn compile -DskipTests -Dskip.bump=true` → BUILD SUCCESS
- [x] `bash tools/god-class-guard.sh --mode=hard` → 0 BLOCK (Layout.java < Block-Limits)
- [x] Bestehendes UI: 1:1 unverändert (Layout-Prototyp NICHT in Tab-Code injiziert; additiv neben den 5 Fenstern)
- [x] `code-reviewer-minimax-m3` PASS-Round
- [x] Keine Stam-Doc-Bump-Pflicht (kein `pom.xml <version>` change, kein `_Info.txt`, keine Migration in der Stam-Doku-7)

## 10 · Sprint-Total (M-UI-3.5 — diese 2 Files)

`Layout.java` Prototyp (~190 SLOC, < bei 200 SLOC Soft-Limit) + Spec (~280 Zeilen Markdown). Tab-Migration (Sprint M-UI-5) ist separate Sprint mit eigener Atomic-Commit. Pilot-Migration als M-UI-5.1 Sub-Sprint empfohlen.

## 11 · Sprint-Tag (für CHANGELOG bei Sprint-M-UI-3.5-Commit)

> `Sprint v0.13.106+M-UI-3.5: UI-Layout-Prototyp (Fluent Grid-API, Reflection-free Render-Path)`

## 12 · Offene Punkte / Future Work

- **`cellStretch(int colspan)`**: Cell-Stretch über N Spalten (width-aware addKpi/addSlider erforderlich) — Sprint M-UI-5+
- **`span(rowspan)`**: vertical-Merge (z.B. 3-Spalten-Tab mit Header-Spalte) — Sprint M-UI-5+
- **`Layout.alignRight()`** + **`Layout.alignCenter()`**: per-row alignment override — Sprint M-UI-5+
- **`nestedLayout(Layout inner, int row, int col)`**: Sub-Grid-Embedding — Sprint M-UI-5+
- **`LazyRebuild()`**: Cache für statische Cells wenn state-unverändert — Performance-Plus in M-UI-5+

Diese Erweiterungen sind im aktuellen Prototyp **nicht** enthalten (Scope-Discipline), aber Design lässt sie offen.
