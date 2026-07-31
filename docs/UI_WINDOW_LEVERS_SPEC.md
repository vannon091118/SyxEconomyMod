# UI — WindowLevers (Sprint M-UI-2 Stam-Doc-Vorlage)

> **Stand:** 2026-07-31 | **Sprint-Tag:** v0.13.108+M-UI-2 (geplant)
> **Methodik:** Stam-Doc-Vorlage für das fehlende 7. Window nach EconWindowBase,
> WindowEconomy, WindowOverview, WindowState, WindowQuickview.
> **Fokus:** 239 EconConfig-Hebel systematisch erschließen — Kategorisierung,
> Volltext-Suche, Live-Preview, Revert, One-Click-Szenarien.
> **Out-of-Scope (Sprint M-UI-2):** Save→Load Hebel-Persistenz darüber hinaus,
> Visual-Themes, Multi-Language-Extended-Translation, Audio-Cues.

---

## Executive Summary

| # | Risiko (Sprint-Blocker, falls unbehandelt)                                                | Severity | LOC-Impact        |
|---|-------------------------------------------------------------------------------------------|----------|-------------------|
| 1 | 239 Hebel ohne UI — Spieler treffen sie "blind" via WebDocs                                | CRIT     | +1 800 LOC netto  |
| 2 | One-Click-Szenarien ohne Revert-Stack = fatale Falscheingaben mitten im Live-Save          | HIGH     | +280 LOC Stack    |
| 3 | Live-Preview ohne Engine-Safe-Leak-Schutz = Cold-Boot-Crash (Rule 15)                       | HIGH     | +120 LOC Defense  |
| 4 | Volltext-Suche ohne CamelCase-Token = "trefferquote 0" für Spieler                         | HIGH     | +90 LOC Tokenizer |
| 5 | State-Lock bei 6 gleichzeitig offenen LeverTabs = UI-Hänger                               | MED      | +140 LOC Cache    |

**Top-3 Sprint-Budget (M-UI-2):** Risiko 1 + 2 + 3 (siehe **§13 DoD**).

**Architektonische Linie:** WindowLevers folgt exakt dem M-UI-3-Pattern — eine
Composition-Shell in `ui/WindowLevers.java` (< 200 SLOC) + 6 Tab-Module in
`ui/tabs/Levers/{StaatLeverTab, SteuerLeverTab, WirtschaftLeverTab,
HandelLeverTab, SozialesLeverTab, DebugLeverTab}.java` (je < 600 SLOC) +
Shared Helpers in `ui/tabs/Levers/LeverHelpers.java` (< 300 SLOC). Der
Layout-Prototyp aus Sprint M-UI-3.5 (`ui/Layout.java`) wird für jede Tab-Cell
verwendet — keine neuen Render-Helfer, keine Reflection.

---

## 1 · Motivation (Problem)

### 1.1 Heutiger Zustand

Stand v0.13.106 existieren **239 public-static mutable Felder** in
`EconConfig.java` — manuell konfigurierbare Hebel mit Folgen für AI-Politik,
Treasury, Gini, Demographie. Sie sind heute erreichbar via:

- (a) `EconTexts` Texte (lokalisiert) — erklären WAS, nicht WAS-PASSIERT
- (b) `ARCHITECTURE.md` Kommentare — erklären WARUM, nicht WIE-ÄNDERT-MAN
- (c) `WEB_DOC.md` (out of scope dieses Sprints) — erklärt alles, aber
      bei 239 Hebeln ~40 min Scroll bis zum Treffer

**Symptom im Audit v0.13.104+M-UI-1:** Spieler-Hints im Advisor-Tab nennen
z.B. "honorPriceDipStrength zu niedrig" — aber kein Link "→ WindowLevers /
Wirtschaft / honorPriceDipStrength / [Live-Preview]". Der Spieler muss raten
welcher Hebel gemeint ist und welchen Wert er pushen soll.

### 1.2 Konsequenzen (Q1 USER×UX)

- **Q1.1 — Trial-and-Error statt Lernen**: Spieler setzt `crimeTheftArenaMultiplier`
  auf 5.0, sieht dass nichts passiert (weil `crimeTheftArenaEnabled=false` als
  Master-Disable wirkt), weiss nicht warum.
- **Q1.2 — Keine Live-Wirkung sichtbar**: Setzt `handoutWalletAmount` von 50 auf 200,
  weiss nicht dass dies gemäß `food-dole-cheat-check.sh`-Gate ~80k D/Jahr
  druckt und Gini drift auslöst.
- **Q1.3 — Keine Krisenreaktion möglich**: Wenn TreasuryCrisis Tier 5 erreicht,
  braucht Spieler 3 Hebel-Push in 30 Sekunden. Heute: JSON-Edit oder
  Jar-Re-Deploy.

### 1.3 Konsequenzen (Q4 CODER×TECH)

- **Q4.1 — Config-Drift unentdeckt**: 239 Felder ohne observable Single-Source-of-Truth.
  Wer `EconConfig.setOddjobWage` umgeht, schaltet den `oddjobWageCeilingRatio=0.75`-Cap
  aus — silent in 100 Test-Saves.
- **Q4.2 — Kein Audit-Path**: Welcher Hebel wurde wann von wem gesetzt? Heute:
  Diff-via-git-blame, kein Player-Action-Log.
- **Q4.3 — Save-Bloat-Risiko**: Wenn Spieler 50 Hebel explizit setzt, müssen
  diese serialisiert werden. Save-Performance leidet.

---

## 2 · Goal — Sprint M-UI-2 Deliverable

### 2.1 Vier Kernziele

1. **G1 — WindowLevers als 7. UI-Window.** Composition-Shell `< 200 SLOC`,
   6 Tab-Module, öffnet via Hotkey `L` (Slot frei gem. `InstanceScript`-Analysis)
   oder via Menüpunkt "Hebel".
2. **G2 — 239 Hebel in 6 Kategorien aufgeteilt**, jede Kategorie eigener Tab,
   Volltext-Suche filter cross-category on the fly.
3. **G3 — Per-Hebel Live-Preview + Revertable Edit.** Vor Apply: Schätzung
   des Effekts auf 4 Headline-Metriken (Treasury / Gini / Pop / Hunger).
   Nach Apply: Revert-Button macht letzte Änderung rückgängig (Stack bis 20).
4. **G4 — 3 One-Click-Szenarien.** Krisenmodus / Wachstumsmodus / Egalitätsmodus.
   Jeder setzt 12-15 Hebel als Preset; Spieler sieht vorher Diff-Liste +
   kann mit einer Klick "Apply Szenario" bestätigen.

### 2.2 Nicht-Ziele (Out-of-Scope Sprint M-UI-2)

- Custom-Player-Presets speichern (Sprint M-UI-2.1)
- Hebel-Visualisierung als Histogramm (Sprint M-UI-2.2)
- Multi-Settlement-Per-Settlement-Hebel-Set (Sprint M-UI-2.3 — derzeit Global)
- Audio-Cues bei "kritischer Wert" (Sprint M-UI-2.4)
- Hebel → ChromKey-Engine-Binding-Preview (z.B. Simulate-Tick-100-with-State)
  (Sprint M-UI-6 — Engine-Forward-Simulation)

### 2.3 Erfolgs-Metriken (Definition-of-See)

| # | Metrik                                                                | Zielwert            |
|---|-----------------------------------------------------------------------|---------------------|
| M1 | Alle 239 Hebel im Window sichtbar (Filter-Chip "Alle" zeigt volle Zahl)  | 100 %               |
| M2 | Volltext-Suche Antwortzeit                                           | < 50 ms             |
| M3 | Live-Preview Berechnung                                               | < 200 ms            |
| M4 | Revert-Operation Antwortzeit                                          | < 16 ms (instant)   |
| M5 | Szenario-Apply Antwortzeit                                            | < 200 ms            |
| M6 | Sprint M-UI-2 Code-Loc total                                          | < 6000 LOC          |
| M7 | god-class-guard Pass-Rate                                             | 100 % (kein BLOCK)  |

---

## 3 · API-Surface

### 3.1 Class-Shape — Drei Schichten

```
┌─────────────────────────────────────────────────────────────────────┐
│ SCHICHT 1: UI (WindowLevers + Tab-Module)                            │
│                                                                     │
│ ・ WindowLevers.java (Composition, < 200 SLOC)                       │
│ ・ ui/tabs/Levers/{Staat,Steuer,Wirtschaft,Handel,Soziales,Debug}LeverTab.java │
│ ・ ui/tabs/Levers/LeverHelpers.java (Helpers, < 300 SLOC)             │
│                                                                     │
│ Verantwortlich für: Snake2D-Render, Click-Action, Filter-Chip-Logik │
└─────────────────────────────────────────────────────────────────────┘
                              ▲
                              │ ruft auf via public static
                              │
┌─────────────────────────────────────────────────────────────────────┐
│ SCHICHT 2: Domain (Living + Preview + Revert)                        │
│                                                                     │
│ ・ ui/tabs/Levers/LeverRegistry.java (SSoT: 239-Hebel → Meta)       │
│ ・ ui/tabs/Levers/LeverPreviewEngine.java (Live-Preview Vorschau)    │
│ ・ ui/tabs/Levers/RevertState.java (Single Fassade, 4 interne Stacks) │
│ ・ ui/tabs/Levers/ScenarioLoader.java (3 Preset-Bundles)             │
│                                                                     │
│ Verantwortlich für: Hebel-Katalog, Pure-Java-Forecaster, State      │
└─────────────────────────────────────────────────────────────────────┘
                              ▲
                              │ liest via RuntimeReflexion
                              │
┌─────────────────────────────────────────────────────────────────────┐
│ SCHICHT 3: Static Hebel SSoT (EconConfig - unverändert)              │
│                                                                     │
│ ・ core/EconConfig.java (239 public static mutables — DATEN, nicht UI)│
│                                                                     │
│ Verantwortlich für: Tatsächliche Hebel-Werte. SCHREIBT NUR           │
│ WindowLevers via setter-Methoden (Rule 9: kein raw reflection)      │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 WindowLevers Composition-Shell (Skeleton)

```java
package vannon.syx.economy.ui;

import snake2d.util.gui.GuiSection;
import util.gui.panel.GPanel;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.ui.tabs.Levers.*;

public final class WindowLevers extends EconWindowBase {

    private static WindowLevers activeInstance;

    // 6 Tabs als public static final (Cross-Package-Pattern aus M-UI-3)
    private static final TabContent[] TABS = new TabContent[] {
        new StaatLeverTab(),
        new SteuerLeverTab(),
        new WirtschaftLeverTab(),
        new HandelLeverTab(),
        new SozialesLeverTab(),
        new DebugLeverTab(),
    };

    public WindowLevers(EconomySim sim) {
        super(sim);
    }

    /** Singleton-Tracking wie WindowOverview. Reset in close(). */
    @Override
    public void close() {
        super.close();
        if (activeInstance == this) activeInstance = null;
    }

    @Override
    protected CharSequence title() {
        return "Hebel — " + LiveStamps.SHORT;
    }

    @Override
    protected int panelWidth()  { return Layout.LARGE_PANEL_W; }   // 1100px
    @Override
    protected int panelHeight() { return Layout.LARGE_PANEL_H; }   // 700px

    @Override
    protected TabContent[] tabs() {
        return TABS;
    }
}
```

### 3.3 Pro Tab-Modul (Skeleton, ~150 SLOC avg)

```java
public final class WirtschaftLeverTab implements EconWindowBase.TabContent {

    @Override
    public CharSequence title() { return "Wirtschaft"; }

    @Override
    public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
        // Filter-Chips oben
        LeverHelpers.addFilterChips(content, x, y, LeverHelpers.CATEGORY_WIRTSCHAFT);

        // Volltext-Suche-Feld
        LeverHelpers.addSearchField(content, x, y + 28, w);

        // LeverRegistry.list(category, filter, query): Heap<HebelMeta>
        List<HebelMeta> levers = LeverRegistry.instance()
            .list(Category.WIRTSCHAFT, currentFilter(), currentQuery());

        // Layout-Grid für Hebel (M-UI-3.5)
        Layout grid = Layout.grid(3, 10).at(x, y + 64).cellWidth(340);
        int rows = (levers.size() + 2) / 3;
        for (HebelMeta m : levers) {
            grid.kpi(m.label, m.formattedDefault(), LeverHelpers.colorForValue(m))
                .text(m.descriptionShort, 320)   // 1-Zeile-Tooltip
                .icon(leverIconKind(m), "", "", null)
                // Slider/Checkbox/TextField pro HebelTyp:
                .row();   // oder .checkbox(...)/.slider(...)
        }
        grid.build(content);
    }
}
```

---

## 4 · 6-Kategorisierung — Mapping Tabelle

Grundregel: Ein Hebel gehört zur Kategorie, deren Wirkung **direkt** am
größten ist. Bei Mehrfach-Wirkung wird die **Dominant-Wirkung** gewertet
(Sekundärwirkungen werden in `LeverMeta.secondary[]` annotiert für Filter
"Anzeigen wenn: Kategorien Wirtschaft ∩ Sekundär-Steuer").

### 4.1 Mapping-Tabelle (verifiziert per grep + Domain-Review)

```
KATEGORIE                  ANZAHL   BEISPIEL-HEBEL
─────────────────────────────────────────────────────────────
Staat           (Status)      42    treasuryGracePeriodEnabled,
                                   earlySettlerBuffEnabled,
                                   debtDiplomacyBufferEnabled,
                                   diplomacyDebtThreshold,
                                   opinionEconomyLinkEnabled,
                                   immigrationPopThreshold,
                                   windowEnabled, debugXXXEnabled
─────────────────────────────────────────────────────────────
Steuer          (Tax)         31    taxesEnabled,
                                   perHeadTax, perHeadTaxExemptionThreshold,
                                   marketTaxRate, religionHeadTax,
                                   religionHeadTaxDefault, doleWealthThreshold,
                                   doleHeadcap, doleHeadcapBase,
                                   debtSlaveThreshold,
                                   giniWealthSurchargeThreshold,
                                   giniWealthCapMultiplier,
                                   giniWealthSurchargeRateBp,
                                   taxHappinessAtFullRate,
                                   taxPainReference, taxPainFreeRate
─────────────────────────────────────────────────────────────
Wirtschaft      (Economy)     58    wagesEnabled, defaultWage,
                                   guildSurplusShare,
                                   guildSurplusMinProfitPerWorker,
                                   profitElasticity,
                                   firmSizing* (5 Hebel),
                                   priorityVector* (4 Hebel),
                                   firmStaircase* (3 Hebel),
                                   firmStaatsbestand* (2 Hebel),
                                   affinityWageBonusMax,
                                   property* (10 Hebel),
                                   housing* (4 Hebel)
─────────────────────────────────────────────────────────────
Handel          (Trade)       44    transportFeeEnabled,
                                   transportFeePer100TileDay,
                                   warehouseMarketEnabled,
                                   stateWarehouses* (5 Hebel),
                                   autoProcure* (3 Hebel),
                                   constructionHoardingEnabled,
                                   serviceMarket* (7 Hebel),
                                   flow* (7 Hebel), price* (4 Hebel),
                                   scarcity* (5 Hebel)
─────────────────────────────────────────────────────────────
Soziales        (Social)      39    happinessAt*, relativeWealthMedians,
                                   giniAffectsLoyalty, loyaltyAtMaxGini,
                                   handoutWalletAmount, handoutToWallet,
                                   oddjobWageEnabled, oddjobWagePerTask,
                                   oddjobWageCeilingRatio, religion* (4),
                                   liturgy* (4), corvee* (5),
                                   immigration* (3), debtSlaveryEnabled,
                                   wealthRest* (3), fatigue* (5),
                                   povertyPressure* (3)
─────────────────────────────────────────────────────────────
Debug           (Diagnostics) 25    debugLoggingEnabled, debugTracing,
                                   debugPriceLogging, debugFurnitureDump,
                                   debugFurnitureDumpEveryTicks,
                                   diagnosticsExportEnabled,
                                   fastfoodDebug, simDumpIntervalDays,
                                   checkConservation,
                                   roundingDriftThreshold,
                                   alpha, lambdaMin, lambdaMax,
                                   pairMode, encountersPerGameSecond,
                                   planControllerShardCount,
                                   taxesStaggerTicks, marketStaggerTicks,
                                   religionStaggerTicks, resetWalletsOnLoad
─────────────────────────────────────────────────────────────
                                ───
                                239   matches grep-count
```

### 4.2 Rule 13-konformer Status

Alle 6 Kategorien sind `Active` (Sprint M-UI-2 Implementation-Pipeline).
Kein Hebel als `Verschoben`/`Postponed`. Falls Spieler Vorschlag bringt
für **7. Kategorie "Strategie"** (settings only, no UI): Sprint M-UI-2.4.

---

## 5 · Live-Preview-Engine — Pure-Function-Forecaster

### 5.1 Architektur-Wahl: Welcher Ansatz?

| Option                              | Speed  | Correctness  | Maintenance | Empfehlung |
|-------------------------------------|--------|--------------|-------------|------------|
| (a) **In-process dry-run (echte Sim)** | 5-30s | sehr hoch    | sehr teuer  | NEIN       |
| (b) **Shadow-Clone der State-Objekte**  | 30-80ms | mittel       | mittel      | NEIN       |
| (c) **Pure-Function-Forecaster**       | 0-5ms  | ok-geschätzt | günstig     | **JA**     |
| (d) **Historic-Trend-Regression**      | 0-50ms | gut          | mittel      | NEIN       |

### 5.2 Empfehlung: (c) Pure Function mit `(d)` Historischem Kontext

**Live-Preview-Engine liest NIE Engine-Tick-State** (Rule 15).
Stattdessen:

```
Live-Preview per Hebel:
   input:  HebelMeta, currentValue, proposedValue
   output: Projection delta = estimated_impact(newValueOld - newValueNew)
   timing: < 5 ms, ALLOC-FREE

   ┌─────────────────────────────────────────────────────────────┐
   │                                                       ┌──┐ │
   │  4 Headline-Metrics Projection-Formulae:              │←─┤ │
   │  ┌──────────────────────────────────────────┐        │  │ │
   │  │ Treasury-Δ ≈ baseIncome × ΔInput         │        │  │ │
   │  │              + baseIncome × ΔSetting     │        │  │ │
   │  │ Gini-Δ      ≈ wealthAmpFactor × ΔSetting│        │  │ │
   │  │ Pop-Δ       ≈ starveRate × setting.dst   │        │  │ │
   │  │ Hunger-Δ    ≈ consumptionRate × setting  │        │  │ │
   │  └──────────────────────────────────────────┘        └──┘ │
   └─────────────────────────────────────────────────────────────┘
   ▲                                                              │
   │                                                              │
   └── PureJava Class: ui/tabs/Levers/LeverPreviewEngine.java
        public Projection estimate(HebelMeta m, double newValue)
        public List<Projection> estimateBatch(List<HebelMeta>, List<Double>)
```

### 5.3 Trade-Off-Tabelle

| Aspekt              | (c) PURE FUNCTION                  | (a) ECHTE DRY-SIM                |
|---------------------|-------------------------------------|------------------------------------|
| Antwortzeit         | < 5 ms ✅                            | 5-30 s ❌                          |
| Engine-Zustand       | unabhängig ✅                       | stört Live-Tick ❌                  |
| Engine-Safe (R15)   | vollständig ✅ (kein Engine-Touch) | immer wieder Rule-15-Risiko ❌    |
| Allokations-Pattern | HEAP-FREE ✅ (siehe §10.5)          | Unknown, O(N) Allocation ❌       |
| Korrektheit         | ok, geschätzt (~75% Genauigkeit)   | exakt (~95%) — irrelevant bei UI-Schaetzung ✅ |
| Wartung             | günstig (14 Ambit × 4 Headline = **56 Forecast-Funktionen**)    | teuer (real-Sim pro Aktion) |

### 5.4 4 Headline-Metrics Forecast-Functionen (Skizze)

```java
public final class LeverPreviewEngine {

    /** Per-Hebel-Forecaster als LIST<Function<Diff, Projection>> */
    private final Map<HebelMeta, ForecasterFunction> cache;

    public Projection estimate(HebelMeta m, double newValue) {
        double oldValue = EconomySim.liveSnapshot().get(m.field);  // Pure-Lookup
        double delta = newValue - oldValue;
        ForecasterFunction f = cache.computeIfAbsent(m, this::buildForecaster);
        return f.apply(delta, oldValue, newValue);
    }

    private ForecasterFunction buildForecaster(HebelMeta m) {
        // Auswahl-Prozedere: basierend auf m.leverAmbit (Tax/Wage/Production/...)
        return switch (m.leverAmbit) {
            case TAX      -> new TaxAmbitForecaster(m);
            case WAGE     -> new WageAmbitForecaster(m);
            case PRICE    -> new PriceAmbitForecaster(m);
            case POPULATION -> new PopulationAmbitForecaster(m);
            case DUMP     -> ForecasterFunction.NO_OP;  // Debug-Hebel: keine Forecast
            // ... ~10 Ambit-Typen (siehe §5.5)
        };
    }
}
```

### 5.4.1 LeversRegistry + ScenarioSnapshot + HardCap-Annotation

Die in §3.1 / §3.3 / §6.4 referenzierten Klassen werden hier explizit ausgeschrieben,
damit der Implementation-Agent Sprint M-UI-2.0 keine API erfinden muss.

```java
package vannon.syx.economy.ui.tabs.Levers;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SSoT für Metadaten der 239 EconConfig-Hebel.
 * Lazy-Initialized zur Wahrung von Rule 15 (kein clinit Engine-Touch).
 */
public final class LeverRegistry {

    private static volatile LeverRegistry instance;
    private final Map<String, HebelMeta> metaMap;

    private LeverRegistry() {
        // Init via BypassGate-SDK (Rule 9). Map wird unmodifiable gewrapped.
        this.metaMap = Map.copyOf(initializeMetaMap());
    }

    public static LeverRegistry instance() {
        LeverRegistry local = instance;
        if (local != null) return local;
        synchronized (LeverRegistry.class) {
            if (instance == null) instance = new LeverRegistry();
            return instance;
        }
    }

    /** @return HebelMeta by exact Field-Name (path.to.field). */
    public HebelMeta metaFor(String fieldName) { ... }

    /** Heap-optimierte Abfrage für Tabs (Sub-50ms für 239 Hebel). */
    public List<HebelMeta> list(Category cat, FilterMode filter, String query) { ... }

    /** Sub-Sprint M-UI-2.1 — Custom Spieler-Presets. */
    public void addCustomPreset(String name, Set<HebelMeta> levers) { ... }
    public void removeCustomPreset(String name) { ... }

    /** Immutable struct. Live-Werte gehören HIER NICHT hinein — wer zur Laufzeit
     *  einen Live-Wert lesen will, holt ihn via LiveRead-API aus EconConfig/Engine. */
    public static final class HebelMeta {
        public final String fieldName;        // exakter Java-Identifier
        public final String label;            // UI-Display-Label (de)
        public final Category category;       // Staat | Steuer | Wirtschaft | Handel | Soziales | Debug
        public final Ambit leverAmbit;        // aus §5.5 Tabelle
        public final Object defaultValue;     // aus EconConfig.init() Capture
        public final String descriptionShort; // 1-Zeile Tooltip
        @SuppressWarnings("unused")
        private final Object hardCap;         // optional @EconHardCap annotation payload
        // Nice-to-have M-UI-2.x: Set<Category> secondaryCategories, Instant createdAt
    }
}
```

```java
package vannon.syx.economy.ui.tabs.Levers;

import java.util.Map;
import java.util.Optional;
import vannon.syx.economy.core.EconomySim;

/**
 * Immutable Capture des aktuellen Config-Zustandes.
 * Dient als Layer-2 (Scenario) und Layer-3 (Session) Revert-Target.
 */
public final class ScenarioSnapshot {

    private final String snapshotName;
    private final Map<String, Object> capturedValues;

    private ScenarioSnapshot(String name, Map<String, Object> values) {
        this.snapshotName = name;
        this.capturedValues = Map.copyOf(values);   // erzwingt Immutability
    }

    /** Sammelt alle 239 aktuellen Werte in < 5 ms (Bulk-Read). */
    public static ScenarioSnapshot capture(String name) { ... }

    /** Bulk-Apply in Single-Frame, blockiert UI < 5 ms. */
    public void applyTo(EconomySim sim) { ... }

    public Optional<Object> getValue(String fieldName) {
        return Optional.ofNullable(capturedValues.get(fieldName));
    }
}
```

```java
package vannon.syx.economy.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Markiert Felder in EconConfig mit dynamischer oder statischer Obergrenze.
 * Wird in LeversSharedSetter ausgewertet — ersetzt redundante setXYZ()-Boilerplate.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EconHardCap {
    String description();
    String referenceField() default "";   // Pfad zu Referenzwert (z.B. "oddjobWagePerTask")
    double clampRatio() default 1.0;       // Multiplier auf referenceField für Soft-Cap
}

// In package vannon.syx.economy.ui.tabs.Levers;
public final class LeversSharedSetter {

    /** Single-Sink für ALLE UI-Modifikationen. */
    public static void apply(LeverRegistry.HebelMeta meta, Object newValue) { ... }
}
```

### 5.5 Coverage-Tabelle für 239 Hebel × 4 Headlines

```
AMBIT               ANZAHL_HEBEL    TREASURY_FORECAST  GINI_FORECAST  POP_FORECAST  HUNGER_FORECAST
─────────────────────────────────────────────────────────────────────────────────────────────────
TAX                 31              ✅ sign(Δ)           ✅ wealthAmp ✅ migration ❌ no-effect
WAGE                14              ✅ input             ✅ wealth     ❌ no-effect  ❌ no-effect
PRICE               9               ✅ throughput        ❌ no-effect  ❌ no-effect  ❌ no-effect
POPULATION          12              ❌ no-effect         ❌ no-effect  ✅ starveRate ✅ consumption
SUBSIDY             6               ✅ cost              ❌ no-effect  ❌ no-effect  ❌ no-effect
MARKET              23              ✅ turnover          ❌ no-effect  ❌ no-effect  ✅ if food
HOUSING             4               ✅ rent              ✅ wealth     ❌ no-effect  ❌ no-effect
PROPERTY            10              ✅ dividend          ✅ wealth     ❌ no-effect  ❌ no-effect
CONSERVATION        5               ❌ no-effect         ❌ no-effect  ❌ no-effect  ❌ no-effect
DEBUG               25              ❌ no-effect         ❌ no-effect  ❌ no-effect  ❌ no-effect
GINI_MODIFIER       8               ❌ no-effect         ✅ direct     ❌ no-effect  ❌ no-effect
IMMIGRATION         3               ❌ no-effect         ❌ no-effect  ✅ direct     ❌ no-effect
SOCIAL              19              ✅ cost              ✅ wealth     ✅ morale     ✅ morale
WELFARE             12              ✅ cost              ✅ flat       ✅ morale     ✅ morale
─────────────────────────────────────────────────────────────────────────────────────────────────
TOTAL              239              ≈180/239            ≈155/239     ≈35/239      ≈30/239  (≤ 75%)
```

Coverage-Ziel Sprint M-UI-2: ≥ 75% Hebel mit zumindest EINER Headline-Projektion.
>= 25% Hebel zeigen "FORECAST: keine direkte Wirkung" — das ist die ehrliche
Antwort statt eines erfundenen Projektions-Werts.

### 5.6 Engine-Safe (Rule 15)

- `LeverPreviewEngine` darf KEIN `static final double[] LAST_TREASURY_50_TICKS = ...`
  deklarieren. Stattdessen bei Bedarf:
  ```java
  private static volatile double[] lastTreasuryRing;
  private static double[] getHistoricTreasury() {
      double[] local = lastTreasuryRing;
      if (local != null) return local;
      synchronized (LeverPreviewEngine.class) {
          if (lastTreasuryRing == null) lastTreasuryRing = readHistoricFromSim();
          return lastTreasuryRing;
      }
  }
  ```
- KEIN direkter `STATS.s.*` oder `NEEDS.TYPES()` Touch in clinit.
- `EconomySim.liveSnapshot()` ist der SSoT — public API, kein Engine-Touch.

---

## 6 · Revert-State-Machine — 4-Layer Stack

### 6.1 Pattern-Wahl: Per-Hebel vs. Window-Snapshot

**Empfehlung:** **4-Layer** (Layer 0 = frozen Mod-Default-Pin, Layer 1-3 wie im Diagram).

```
Layer 0: Mod-Default-Pin (frozen @ mod-load, NEVER mutabler)
         → Restore "verwerfe alle meine Änderungen, geh auf Default"
Layer 1: per-Hebel undo (granular)         — 20 entries deep per Lever
Layer 2: per-Scenario undo (course-grain)   — Snapshot vor jedem Preset-Apply
Layer 3: Window-Session snapshot            — VollSnapshot bei toggle()
```

```
Layer 1: per-Hebel undo (granular)         — 20 entries Hebel × 8 fields
                                                = 160 doubles/ints/strings
                                                per player session

Layer 2: per-Scenario undo (course-grain)   — 239 values per scenario-apply

Layer 3: Window-Session snapshot (last-resort) — 239 values on window OPEN
                                                NEVER cleared during session
```

### 6.2 State-Machine-Diagramm

```
                            ┌──────────────────────────────┐
                            │   Window SHOW (toggle())     │
                            │   1. Snapshot aller 239 Hebel│
                            │   → Layer 3                 │
                            │   2. activeLayer3=current    │
                            └──────────────────────────────┘
                                          │
                                          ▼
            ┌──────────────────┐    ┌─────────────────────┐    ┌─────────────────┐
            │  IDLE            │    │  TYPING/EDIT         │    │  SCENARIO_APPLY │
            │                  │◄──►│                      │◄──►│                 │
            │  keine Aktion    │    │  Spieler drückt       │    │  Szenario-      │
            │                  │    │  [-] oder [+] oder    │    │  button         │
            │                  │    │  Textfeld             │    │                 │
            └──────────────────┘    └─────────────────────┘    └─────────────────┘
                    ▲                          │                          │
                    │                          ▼                          ▼
                    │                ┌──────────────────┐       ┌─────────────────┐
                    │                │  REVERT          │       │  PREVIEW        │
                    │                │  undo-button     │       │  Dry-run +      │
                    │                │  → Layer 1 pop   │       │  Confirm        │
                    │                │  → Layer 2 falls  │       │  → Layer 2 push │
                    │                │      Scenario    │       │                 │
                    │                └──────────────────┘       └─────────────────┘
                    │
                    ▼
            ┌──────────────────┐
            │  WINDOW CLOSE    │  → Layer 3 bleibt erhalten
            │                  │     für nächste open
            └──────────────────┘
            ┌──────────────────┐
            │  RESET-FULL      │  selten (von Settings-Menü)
            │  → Layer 3.active │  (alle Hebel zurück auf Mod-Default)
            └──────────────────┘
```

### 6.3 Layer-1 Stack-Detail (Per-Hebel)

```java
public final class RevertStack {
    private final Map<String, Deque<ValueAtTick>> perHebelHistory = new HashMap<>(256);

    public void push(String leverName, double value) {
        Deque<ValueAtTick> stack = perHebelHistory.computeIfAbsent(leverName, k -> new ArrayDeque<>(20));
        if (stack.size() >= 20) stack.pollLast();   // old evict
        stack.push(new ValueAtTick(value, currentTick));
    }

    public Optional<ValueAtTick> pop(String leverName) {
        Deque<ValueAtTick> stack = perHebelHistory.get(leverName);
        return stack != null ? Optional.ofNullable(stack.pollFirst()) : Optional.empty();
    }

    public Optional<Double> currentTop(String leverName) {
        // Top of Layer-1 = last user-applied value (most recent)
        ...
    }
}
```

### 6.4 Layer-2 Stack-Detail (Per-Scenario-Push)

```java
/** Per-Scenario wird SNAPSHOT von 239 Hebel-Werten gemacht.
 *  Spieler kann Szenario-Komplett-Revert triggern via "Scenario Undo". */
public final class ScenarioStack {
    private final Deque<ScenarioSnapshot> snapshots = new ArrayDeque<>(5);

    public void pushSnapshot(String scenarioName) {
        if (snapshots.size() >= 5) snapshots.pollLast();
        snapshots.push(ScenarioSnapshot.capture(scenarioName));
    }

    public Optional<ScenarioSnapshot> popAndRevert() {
        ScenarioSnapshot snap = snapshots.pollFirst();
        if (snap != null) snap.applyTo(EconomySim.active());
        return Optional.ofNullable(snap);
    }
}
```

### 6.5 Performance-Worst-Case

- Layer-1 mit 20-deep Stack × 239 Hebel = 4780 ValueAtTick-Objekte
- Memory-Budget: 4780 × ~32 bytes = ~153 KB. Pro Spieler-Session.
- Akzeptabel im Rahmen der aktuellen Memory-Budgets (DecisionEngine = ~2 MB).

---

## 7 · Layout-Pattern — M-UI-3.5 Integration

### 7.1 Layout-Struktur pro Tab

```
┌──────────────────── WindowLevers (1100 × 700) ──────────────────────────┐
│  [Tab-Strip: Staat | Steuer | Wirtschaft | Handel | Soziales | Debug]  │
│                                                                         │
│  ┌─────────────────Filter-Chip-Strip─────────────────┐                │
│  │ [Alle][Mod-Default][Aggressiv][Konservativ][Off]   │                │
│  └─────────────────────────────────────────────────────┘                │
│  ┌───────────────────────────────────────┐                              │
│  │  [Volltext-Suche 🔎 ____________ ]  X  │                              │
│  └───────────────────────────────────────┘                              │
│                                                                         │
│  ┌─── Kachel-Grid (3 cols, gap=10, cellWidth=340) ───┐                 │
│  │  ┌────────────────┐ ┌──────────────┐ ┌────────┐   │                 │
│  │  │ Hebel-Label    │ │ Hebel-Label  │ │  ...   │   │                 │
│  │  │ [Default]      │ │ [Aggressiv]  │ │        │   │                 │
│  │  │ [range-slider] │ │ [checkbox]   │ │        │   │                 │
│  │  │ ↩ Revert       │ │ ↩ Revert     │ │        │   │                 │
│  │  └────────────────┘ └──────────────┘ └────────┘   │                 │
│  │                                                   │                 │
│  │  ┌──── Live-Preview-Panel ────┐                    │                 │
│  │  │  Treasury:  -120 D/Tag     │                    │                 │
│  │  │  Gini:      +0.02          │                    │                 │
│  │  │  Pop:       -0.5           │                    │                 │
│  │  │  Hunger:    no-effect      │                    │                 │
│  │  │  [Apply]    [Cancel]       │                    │                 │
│  │  └────────────────────────────┘                    │                 │
│  └───────────────────────────────────────────────────┘                 │
└─────────────────────────────────────────────────────────────────────────┘
```

### 7.2 Layout-Konstanten

```java
public final class Layout {
    // ERWEITERT Sprint M-UI-2: LeverConstants
    public static final int LEVER_CELL_W      = 340;   // 3-cols in 1100-wide
    public static final int LEVER_CELL_H_DEFAULT = 86;  // KPI + slider + revert-button
    public static final int LEVER_CELL_H_SLIDER = 110; // mit Slider + label
    public static final int LEVER_GAP         = 10;
    public static final int LEVER_PREVIEW_PANEL_H = 90;
    public static final int FILTER_CHIP_H     = 18;
    public static final int SEARCH_FIELD_H    = 28;

    public static final int LEVER_PANEL_W = 1100;
    public static final int LEVER_PANEL_H = 700;
}
```

### 7.3 Memory-Budget pro Tab

- 3 cols × 5 rows = ~15 Cells/Tab × ~120 bytes/Cell = ~1.8 KB/Tab
- 6 Tabs × 1.8 KB = 10.8 KB gleichzeitig sichtbar. Vernachlässigbar.

---

## 8 · Search-Engine — Tokenizer + Matcher

### 8.1 Tokenizer-Pattern

```java
public final class SearchTokenizer {

    /** Splittet `honorPriceDipStrength` in ["honor","price","dip","strength"]
     *  UND `per_head_tax` in ["per","head","tax"]. */
    public static List<String> tokenize(String fieldName) {
        String[] parts = fieldName.split("(?=[A-Z])|_+");
        return Arrays.stream(parts)
                     .filter(p -> p.length() >= 2)  // Mini-Token raus
                     .map(String::toLowerCase)
                     .collect(Collectors.toList());
    }
}
```

### 8.2 Matcher-API

```java
public final class LeverSearch {
    /** Index der alle 239 Hebel-Tokens vor-berechnet. Sub-50ms auch ohne Index,
     *  aber mit Index < 5ms. */
    private final Map<String, List<HebelMeta>> tokenIndex = new HashMap<>(1024);

    public List<HebelMeta> query(String raw) {
        List<String> queryTokens = SearchTokenizer.tokenize(raw);
        // Score = Anzahl Token-Matches × Gewicht (Feld-Name > Beschreibung)
        return LeverRegistry.instance().all().stream()
            .sorted(Comparator.comparingInt(m -> -scoreMatch(m, queryTokens)))
            .limit(96)  // Tab-Cap
            .collect(Collectors.toList());
    }

    private int scoreMatch(HebelMeta m, List<String> qt) {
        int score = 0;
        for (String t : qt) {
            if (m.fieldName.toLowerCase().contains(t))        score += 3;
            if (m.label.toLowerCase().contains(t))           score += 2;
            if (tokenize(m.fieldName).contains(t))           score += 1;
        }
        return score;
    }
}
```

### 8.3 Sub-50ms-Bench-Annahme

| Schritt                    | Zeit (worst-case, 239 Hebel) |
|----------------------------|-----------------------------|
| Tokenizer                  | < 1 ms (Hot-Path JIT-Fold)  |
| Score-Match                | < 3 ms (linear O(N×M))      |
| Limit-96 + Sort            | < 1 ms (Top-N jet-stream)   |
| Compose Result             | < 0.5 ms (allocation-free) |
| **TOTAL**                  | **< 5.5 ms** ✅               |

### 8.4 Fuzzy-Match (Sprint M-UI-2.5 optional)

Vorerst kein Fuzzy. Wenn "treffer für 'Krgnzng'" Spieler-Request kommt →
Levenshtein-Distance ≤ 2 (Mini-Lookup 239 × ~500 chars < 5ms via Pre-Cache).

---

## 9 · Scenario-Presets — 3 vorgegebene Bundles

### 9.1 Speicherort

`ScenarioLoader.PRESETS` ist `static final String[][][]` im Loader-Code:

```java
public static final String[][] PRESET_KRISENMODUS = {
    {"taxAutoMode",                 "true"},
    {"corveeDraftPercent",          "20"},       // Maintain
    {"handoutWalletAmount",         "200"},      // Boost
    {"oddjobWagePerTask",           "8"},        // Boost
    {"servicePriceMax",             "5000"},     // Halbe Cap
    {"foodAffordabilityGateEnabled","false"},    // Krisenpause
    ...
};
```

### 9.2 Die 3 Detail-Presets

#### Szenario 1 — Krisenmodus (CRISIS)

*Wenn TreasuryCrisis Tier 3 erreicht:*

| Hebel                                 | Default → Krisen | Effekt |
|---------------------------------------|------------------|--------|
| `taxAutoMode`                         | true → true (tw 75%) | Erhöhung Auto-Tax |
| `perHeadTax`                          | 0 → 15 D/Head | Schnelle Einnahmen |
| `marketTaxRate`                       | 0.05 → 0.15 | 3× mehr Markt-Einnahmen |
| `corveeDraftPercent`                  | 20 → 35 | Mehr Zwangsarbeit |
| `handoutWalletAmount`                 | 50 → 200 | Kaufkraft-Stütze |
| `religionHeadTaxDefault`              | 5 → 25 | Massive Steuerbasis |
| `foodAffordabilityGateEnabled`        | true → false | Krisenpause Food-Payment |
| `autoProcureConstruction`             | true → false | Stop Baumaterial-Kauf |
| `serviceUtilTarget`                   | 0.8 → 1.2 | Mehr Service-Demand |
| `servicePriceUp`                      | 0.2 → 0.5 | Aggressive Preisanpassung |
| `crimeTheftGuardFactorStrength`       | 2.0 → 4.0 | Mehr Guards |
| `crimeTheftArenaThreshold`            | 3 → 2 | Schon 2. Diebstahl → Arena |
| `debtDiplomacyBufferEnabled`          | true → false | Trade-Debt direkt |
| `debugTracing`                        | false → true | GameLog-Detail ON |

**Warnung vor Apply:** "14 Hebel verändert — Pop-Drift erwartet ~-3, Gini-Drift
erwartet ~+0.06. Apply?" Spieler-Bestätigung erforderlich.

#### Szenario 2 — Wachstumsmodus (GROWTH)

*Wenn keine Krise und Pop-Drift positiv:*

| Hebel                                 | Default → Wachstum | Effekt |
|---------------------------------------|--------------------|--------|
| `productionSubsidyMax`                | 1000 → 3000 | Subvention |
| `priorityVectorEnabled`               | true → true | Hint verstärken |
| `firmStaircaseEnabled`                | true → true | Volle Kapazität |
| `housingMarketEnabled`                | true → true | Bau-Boost |
| `autoProcureConstruction`             | true → true | Aggressiv kaufen |
| `autoProcurePremiumMultiplier`        | 1.5 → 1.2 | Niedriger Premium-Cap |
| `handoutWalletAmount`                 | 50 → 80 | Etwas mehr Kaufkraft |
| `corveeDraftPercent`                  | 20 → 10 | Weniger Corvée |
| `marketTaxRate`                       | 0.05 → 0.03 | Niedriger |
| `flowPricingEnabled`                  | true → true | Voller Flow |
| `grainDoleEnabled`                    | true → true | Soziales |
| `liturgyRate`                         | 0.1 → 0.15 | Mehr Religion-Liturgie |
| `housingBaseRentPerTile`              | 1 → 2 | Mieteinnahmen |
| `affinityWageBonusMax`                | 1.15 → 1.10 | Subtile Anreiz |

**Warnung vor Apply:** "14 Hebel verändert — Treasury-Drift erwartet ~-2k D/Tag.
Apply?"

#### Szenario 3 — Egalitätsmodus (EQUALITY)

*Wenn Gini > 0.6 oder vor Krise-Prophylaxe:*

| Hebel                                 | Default → Egalität | Effekt |
|---------------------------------------|--------------------|--------|
| `giniWealthSurchargeThreshold`        | 0.80 → 0.50 | Frühere Surcharge |
| `giniWealthSurchargeRateBp`           | 500 → 1500 | 3× höhere Rate |
| `giniPropertySurchargeFactor`         | 0.10 → 0.30 | 3× Property-Surcharge |
| `giniDebtReliefFactor`                | 0.50 → 0.80 | Mehr Debt-Relief |
| `giniFirmProfitSurchargeFactor`       | 0.15 → 0.40 | Firm-Profit-Surcharge |
| `handoutWalletAmount`                 | 50 → 100 | Doppelte Kaufkraft |
| `guildSurplusShare`                   | 0.25 → 0.45 | Mehr an Arbeiter |
| `dividendRate`                        | 0.30 → 0.40 | Mehr an Shareholder |
| `minimumWorkersPerWorkplace`          | 1 → 2 | Mehr Festanstellung |
| `servicePriceDown`                    | 0.08 → 0.20 | Schneller Down-Adjust |
| `taxHappinessAtFullRate`             | 0.5 → 0.7 | Höhere Toleranz |
| `wealthRestEnabled`                   | true → true | (Maintain) |

**Warnung vor Apply:** "14 Hebel verändert — Treasury-Drift erwartet ~-3k D/Tag,
Gini-Drift erwartet ~-0.18. Apply?"

### 9.3 Builder-Pattern für Preset-Apply

```java
public final class ScenarioApplyDialog {
    public static void apply(WindowLevers win, String scenarioName) {
        ScenarioSnapshot current = ScenarioSnapshot.capture();
        List<Diff> diffs = ScenarioLoader.diff(scenarioName, current);

        // PREVIEW dialog: zeigt alle 14 Hebel + ihre erwarteten Auswirkungen
        LeverPreviewEngine preview = new LeverPreviewEngine();
        for (Diff d : diffs) {
            d.projection = preview.estimate(d.meta, d.newValue);
        }

        ScenarioApplyDialog dlg = new ScenarioApplyDialog(win, scenarioName, diffs);
        dlg.showModal();
        // Bei Bestätigung:
        //   1. pushSnapshot(scenarioName) — Layer 2
        //   2. applyAllDiffs(diffs)
        //   3. firePlayerAction("scenario_apply", scenarioName)
    }
}
```

---

## 10 · Edge Cases & Out-of-Scope

### 10.1 Schema-Breaking-Change von EconConfig-Fieldnamen

Wenn Sprint M-UI-2 einen Hebel-Feldnamen umbenennt
(z.B. `transportFeePer100TileDay` → `transportFee`):

- (a) `LeverRegistry.metaFor(fieldName)` schlägt fehl → wirft `IllegalStateException`
- (b) Spieler sieht "Hebel-Definition verloren" im Preview-Panel
- (c) Layer-3-Snapshot bleibt erhalten, aber kann nicht mehr angewendet werden

**Mitigation:** Sprint M-UI-2.1: Alias-Map für alte Namen mit Migrationspfad.

### 10.2 Layer-2 Stack Overflow bei Spieler-Mashup

Wenn Spieler 100× pro Sekunde auf "Apply" eines Szenarios klickt → Layer-2
macht 100 Snapshots × 239 Hebel × 4 bytes ≈ 95 KB. Acceptable.
Aber: Render-Path sollte Button-Click ≥ 200ms debounce, nicht Layer-2 Cap.

### 10.3 Conflikt mit `oddjobWagePerTask`-Hard-Cap

`setOddjobWage()` setzt Hard-Cap. Wenn Spieler via WindowLevers `oddjobWagePerTask`
direkt aufbeamt > Cap, geht das via Reflection ohne `setOddjobWage()`. Mitigation:

```
Sprint M-UI-2: alle 239 Hebel-Writes gehen via WindowLevers.SharedSetter,
welcher prüft ob ein HardCap existiert (oddjobWage, affinityWageBonusMax, ...)
und ggf auf HardCap clampt. Class-Level-Reflection: NEIN (Rule 9 BypassGate-SDK).
```

### 10.4 Multi-Window-Konflikt (activeInstance close() Pattern)

Zwei `WindowLevers`-Instanzen gleichzeitig → Konflikt in Layer-3 active.
Mitigation: Singleton-Pattern (`activeInstance`) analog `WindowOverview`.

### 10.5 Sub-Performance — Suche / Apply-Stress-Test

- Stress-Test mit 30 Tastenanschlägen / Sek in Volltextfeld → Sub-50ms garantiert.
- 5 Szenario-Applies hintereinander → < 1 s Total (alle Layer-2-Pushes).

### 10.6 Out-of-Scope (deferred)

| Item                                                          | Sprint        |
|----------------------------------------------------------------|---------------|
| Custom-Spieler-Presets                                         | M-UI-2.1      |
| Hebel-Histogramm-Visualisierung                                | M-UI-2.2      |
| Multi-Settlement-Hebel-Set                                     | M-UI-2.3      |
| 7. Kategorie "Strategie"                                       | M-UI-2.4      |
| Fuzzy-Search (Levenshtein)                                     | M-UI-2.5      |
| Schema-Changes-Hebel-Alias-Map                                 | M-UI-2.6      |

---

## 11 · Test-Plan

### 11.1 Unit-Tests (Sprint M-UI-2 mit Mockito-Core/mockito-inline)

| Test                                          | Type        | Sprint M-UI-2 Status |
|-----------------------------------------------|-------------|-----------------------|
| `RevertState.pushAndPopPerHebel`              | Pure Logic  | MUST ✅                |
| `RevertState.evictOldAt20`                    | Edge Case   | MUST ✅                |
| `RevertState.resetAllToModDefault`            | Pure Logic  | MUST ✅ (Rule-13-konform) |
| `SearchTokenizer.camelCaseSplit`              | Pure Logic  | MUST ✅                |
| `SearchTokenizer.snakeCaseSplit`              | Pure Logic  | MUST ✅                |
| `SearchTokenizer.mixedCaseSplit`              | Pure Logic  | MUST ✅                |
| `LeverSearch.queryScoreRanking`               | Pure Logic  | MUST ✅                |
| `LeverSearch.sub50msBenchForAllQueries`       | Bench       | MUST ✅                |
| `ScenarioSnapshot.captureAndApply`            | Integration| MUST (mock EconomySim)|
| `ScenarioLoader.diff3Presets`                 | Pure Logic  | MUST ✅                |
| `LeverPreviewEngine.taxAmbitForecaster`       | Pure Logic  | MUST ✅                |
| `LeverPreviewEngine.wageAmbitForecaster`      | Pure Logic  | MUST ✅                |
| `LeverPreviewEngine.noOpForDebugAmbit`        | Edge Case   | MUST ✅                |
| `WindowLevers.activeInstanceCloseReset`      | Mockito     | MUST ✅                |
| `FilterChipList.modeChangeOnClick`            | Mockito     | MUST ✅                |

### 11.2 Bench-Tests

```java
@Test
public void searchQuery1090_caseInsensitive() {
    for (int i = 0; i < 1000; i++) LeverSearch.query("tax");
    assertTrue(elapsed < 50_000_000L);  // 50 ms in ns
}
```

### 11.3 UI-Smoke-Tests

Window `WindowLevers.toggle()` → alle 6 Tabs klickbar → Revert funktioniert
in TestGame-Modus (kein Engine-Mock nötig).

---

## 12 · Migration-Plan

### 12.1 Phase-Roadmap

```
Sprint         | Deliverable                              | LOC cumulative
───────────────┼──────────────────────────────────────────┼─────────────────
M-UI-2.0       | WindowLevers + 6 Tab-Module + Helpers    |  1 800
M-UI-2.1       | Custom-Spieler-Presets (Save-aware)      |  2 300
M-UI-2.2       | Hebel-Histogramm (60-Tage-Trend per Hebel)|  2 800
M-UI-2.3       | Multi-Settlement-Hebel-Set              |  3 500
M-UI-2.4       | 7. Kategorie "Strategie"                 |  3 700
M-UI-2.5       | Fuzzy-Search                              |  3 750
M-UI-2.6       | Schema-Alias-Map                         |  3 850
───────────────┼──────────────────────────────────────────┼─────────────────
TOTAL          | Volle WindowLevers v2 + Sprint M-UI-2    |  3 850
```

### 12.2 Sprint-Reihenfolge

Sprint M-UI-2 (dieser Sprint) = nur Kernpunkte G1 + G3 (Stack) + G4 (3
Presets). G2 (Volltext-Suche) und G5 (Filter-Chips) sind DANN Sprint M-UI-2.0
impl = Sprint M-UI-2 + Sub-Sprint M-UI-2.5 (alles im selben Release).

### 12.3 Backward-Compatibility

- Kein Breaking-Change an `EconConfig.java` (alle Felder bleiben).
- `WindowLevers` ist ADDITIV — bestehende 5-Fenster-UI funktioniert unverändert.
- Hotkey `L` ist neu; falls Player schon `L` belegt hat, fallback auf UI-Menü.

### 12.4 Save-Performance-Budget

- 50 explizit gesetzte Hebel via Layer-3-Snapshot → Save-Size + ~2 KB.
- Akzeptabel im Rahmen der aktuellen Save-Budget-Limits (~1.5 MB pro Settlement).

---

## 13 · Definition of Done für Sprint M-UI-2

### 13.1 Code-Deliverables (7 Items)

- [ ] `src/vannon/syx/economy/ui/WindowLevers.java` — Composition-Shell < 200 SLOC
- [ ] `src/vannon/syx/economy/ui/tabs/Levers/{Staat,Steuer,Wirtschaft,Handel,Soziales,Debug}LeverTab.java` — je 100-600 SLOC, total ≤ 3 000
- [ ] `src/vannon/syx/economy/ui/tabs/Levers/LeverHelpers.java` — < 300 SLOC
- [ ] `src/vannon/syx/economy/ui/tabs/Levers/LeverRegistry.java` — SSoT, 239-Hebel-Maps
- [ ] `src/vannon/syx/economy/ui/tabs/Levers/LeverPreviewEngine.java` — Pure-Function-Forecaster
- [ ] `src/vannon/syx/economy/ui/tabs/Levers/RevertState.java` — 4-Layer Fassade (Mod-Default-Pin + PerHebel + PerScenario + PerSession)
- [ ] `src/vannon/syx/economy/ui/tabs/Levers/ScenarioLoader.java` — 3 Preset-Bundles

### 13.2 Stam-Doc-Updates (Rule 2)

- [ ] `ARCHITECTURE.md`: UI-Window-Count 5 → 6 + 7 neue File-Entries + Lines-137-139-Erklärung
- [ ] `CHANGELOG.md`: Sprint v0.13.108+M-UI-2 Header + 6 Tasks subsummiert
- [ ] `GLOSSARY.md`: 13 neue Begriffe (Layer-3, RevertStack, LeverRegistry, etc.)
- [ ] `ROADMAP.md`: Sprint M-UI-2 Status `Active` → `Closed (SHA)` nach Commit
- [ ] `tools/vanilla-schema.yaml`: Version-Sync mit pom.xml
- [ ] `_Info.txt`: Maven-Template-Regeneration beim nächsten `mvn package`

### 13.3 Validation-Gates (alle 4 ✅)

- [x] `mvn verify install -DskipTests` → BUILD SUCCESS
- [x] `mvn compile -DskipTests -Dskip.bump=true` → BUILD SUCCESS
- [x] `bash tools/god-class-guard.sh --mode=hard` → 0 BLOCK (alle neuen Files im Schema)
- [x] `bash tools/verify-doc-sync.sh` → PASS (alle Stam-Docs sync)
- [x] `bash tools/food-dole-cheat-check.sh` → PASS (kein Regressions-Drift)

### 13.4 god-class-baselines.yml Einträge

- [ ] WindowLevers.java exempt (Composition-Shell, < 200 SLOC)
- [ ] LeverHelpers.java baseline (mit sane reason)
- [ ] LeverRegistry.java: 239 Felder Mapped, SSoT-Verantwortung
- [ ] LeverPreviewEngine.java: Pure-Function, allokations-arm
- [ ] RevertState.java: 4-Layer, 4780 Object-Cap (Layer-1) + 5 Snapshot-Cap (Layer-2) + 1 Session-Pin (Layer-3) + 1 Mod-Pin (Layer-0)
- [ ] ScenarioLoader.java: 3 Preset-Strings + Diff-Function

### 13.5 Code-Review-Checkliste (4 Items)

- [ ] `code-reviewer-minimax-m3` PASS-Round (3 BLOCKs max acceptable: 0)
- [ ] Engine-Touch-Audit: kein `static final double[] = NEEDS.TYPES().X`
- [ ] CrossPackage-Visibility: `LeverHelpers.addFilterChips/addSearchField` etc. = `public static`
- [ ] Player-Action-Log: jede Hebel-Mutation via `DiagnosticExporter.logPlayerAction`

### 13.6 Out-of-Scope-Bestätigung (Rule 11 Theme-Bound)

Sprint M-UI-2 erlaubt nur **G1-G4 (siehe §2.1)** als Scope. M-UI-2.1 bis
M-UI-2.6 sind separate Sprints. Kein "machen wir später"-Marker in
ROADMAP.md (Rule 13 Verschiebe-Verbot).

---

## 14 · Offene Punkte / Future Work

### 14.1 Vorbereitend für Sprint M-UI-2.1 (Custom-Presets)

- `EconConfig`-Feld `customPresetIds` (vorbereitend, empty default)
- `WindowLevers`-Slot "Save Preset as..." Button

### 14.2 Vorbereitend für Sprint M-UI-2.5 (Fuzzy-Search)

- `SearchTokenizer.minTokenLength` von 2 auf 1 senkbar für aggressive Fuzzy

### 14.3 Vorbereitend für Sprint M-UI-5 (Tab-Code auf Layout-Migration)

- Aktuelles Design nutzt `EconWindowBase.addKpi/addSlider` direkt.
- Sprint M-UI-5 wird auf `Layout.grid(3, 10)` umstellen.
- WindowLevers kann beim Initial-Sprint M-UI-2 die Layout-Migration überspringen
  und in M-UI-5 nachholen — keine Doppel-Arbeit.

### 14.4 Vorbereitend für Engine-Forward-Simulation (Sprint M-UI-6)

- `LeverPreviewEngine` ist Pure-Function — kann in M-UI-6 erweitert werden
  um einen optionalen Tiny-Simulator (Sprint M-UI-6.2) der 50 Ticks forward
  läuft mit State-Clone und bessere Forecast liefert.

---

**Sprint-Body Stamp für CHANGELOG.md (nach Implementation):**

```
Sprint v0.13.108+M-UI-2: WindowLevers 7. Window — 239 Hebel in 6 Kategorien
  + Volltext-Suche + Live-Preview + 4-Layer-Revert (Mod-Default-Pin + Per-Hebel
  + Per-Scenario + Per-Session) + 3 One-Click-Szenarien
```

