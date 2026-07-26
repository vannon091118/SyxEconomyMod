# SyxEconomyMod — Architektur

> **Version:** v0.13.51 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-26
>
> Stam-Doku-Synchron-Anker: Die obenstehende Versions-Zeile MUSS identisch mit `pom.xml` `<version>` sein.
> Der Sync-Gate `tools/verify-doc-sync.sh` validiert dies vor jedem `mvn compile`.
>
> Diese Doku ist maschinell ableitbar: `find src -name '*.java' | wc -l` und
> `wc -l $(find src -name '*.java')` entsprechen den unten genannten Zahlen.

---

## Sprint-Workflow (neu ab v0.13.30)

Per `agents.md`-Rule 11+12 und `WORKFLOW.md`-Reform: **1 Sprint = 1 atomic
commit** mit 5-15 thematisch verbundenen Tasks.

**Sprint-Pipeline:**
```
BAUEN (Tasks implementieren + Stam-Docs built-inside-Commit)
  ↓
PRÜFEN (validate-gate: mvn verify + sync-gate + BINDUNGSMATRIX-NF-check)
  ↓
HÄRTEN (code-reviewer-minimax-m3 + Gap-Closure)
  ↓
1 atomic commit: `sprint: <Name> — <Task-Liste>`
```

**Kanonische Reference-Data:** `BINDUNGSMATRIX.csv` (332 Zeilen,
11 Spalten, semikolon-getrennt). Header:
`ID;Datenpunkt;Wert-Typ;Quelle-Klasse;Zugriffspfad;Zugriffsart;Mod nutzt;UI-Kandidat;Status;Lücke;ModVerifiziert`

**tools/-Stand (post-Sprint):**
| Skript | Status |
|---|---|
| `build_bindungsmatrix.py` | ✅ kanonisch |
| `gen_bindungsmatrix_v2.py` | 🗑️ geloescht |
| `gen_bindungsmatrix_v3.py` | 🗑️ geloescht |
| `recover_bindungsmatrix.py` | 🗑️ geloescht |
| `refactor_bindungsmatrix_macro.py` | 🗑️ geloescht |

Spec-Migration: BINDUNGSMATRIX.csv ist Single-Source-of-Truth.


## Überblick

Das Mod fügt Songs of Syx eine parallele Wirtschaftsschicht hinzu. Jeder Siedler hat ein eigenes Wallet, Firmen rechnen边际isch ab, der Staat kann Steuern erheben und Subventionen verteilen. Das Mod ersetzt keine Vanilla-Systeme, sondern arbeitet über einen **Adapter-Layer** strikt getrennt daneben.

Modul-Bilanz: **128 Java-Dateien, ~22.700 LOC**

| Modul | Dateien | LOC | Aufgabe |
|---|---:|---:|---|
| `vannon/syx/economy/core/` | 100 | ~19.247 | Wirtschafts-Sim + Subsysteme |
| `vannon/syx/economy/adapter/` | 14 | ~1.050 | Engine-API-Wrapper + Bypass-SDK |
| `vannon/syx/economy/ui/` | 5 | ~2.345 | 4 Fenster + Base |
| `vannon/syx/economy/benchmark/` | 1 | ~200 | Reflection-vs-MethodHandle-Benchmark |
| `settlement/room/...` | 4 | ~600 | Package-Private Brücken (compile-time-safe) |

---

## Schichten-Modell

```
╔════════════════════════════════════════════════════════════════╗
║  SCHICHT 0: Vanilla Engine (Songs of Syx V71)                  ║
║  unverändert, ~10.992 Klassen                                  ║
╚═════════════════════════════════════╤══════════════════════════╝
                                       │ nur über
                                       ▼
╔════════════════════════════════════════════════════════════════╗
║  SCHICHT 1: Adapter-Layer (14 Dateien)                         ║
║  5 Interfaces                                                  ║
║   ISyxAI, ISyxTransport, ISyxWarehouse, ISyxBoosting,          ║
║   ISyxDiplomacy                                                ║
║  5 Vanilla-Implementierungen (via BypassGate SDK,               ║
║   VarHandle/MethodHandle auto-select)                           ║
║  4 Bypass-SDK-Klassen (FieldAccessor, MethodAccessor,           ║
║   ClassResolver, BypassGate)                                    ║
║  Engine-Zugriff erlaubt AUSSCHLIESSLICH hier                    ║
╚═════════════════════════════════════╤══════════════════════════╝
                                       ▼
╔════════════════════════════════════════════════════════════════╗
║  SCHICHT 2: Wirtschafts-Logik (`core/`, 100 Dateien)           ║
║  Orchestrator: EconomySim (1.459 LOC)                          ║
║  Subsysteme: Wallets, FirmLedger, Fiscal, Taxes, LaborMarket,  ║
║    StateWageMarket, OddjobMarket, WarehouseMarket,             ║
║    StateWarehouses, TransportMarket, ReligionMarket, Liturgy,  ║
║    ConstructionHoardController, PropertyMarketController,     ║
║    CrisisDispatch, FlowPrices, FlowMeter, PolityPriceAnchor,  ║
║    ScarcitySignal, Roster, EconIndicators, EconProgression,    ║
║    WealthStats, WealthHappiness, PropertyHappiness,            ║
║    DebtDiplomacyBuffer, HandoutRelief, CorveeController,       ║
║    AccessAutomation, DiagnosticExporter, EventLog,              ║
║    AffordabilityGate, FoodPlanController, GrainDole,           ║
║    ServiceMarket, ServicePlanController, PurchasePlanController║
╚═════════════════════════════════════╤══════════════════════════╝
                                       ▼
╔════════════════════════════════════════════════════════════════╗
║  SCHICHT 3: UI (`ui/`, 5 Dateien, 16 inhärente Tabs)           ║
║  EconWindowBase (368 LOC) — abstrakte Basis mit KPI-Header,    ║
║   TabBar, lastSet() für Top-Rendering, mouseClick mit            ║
║   LEFT/RIGHT/MIDDLE, isShown(), toggle()                        ║
║  WindowOverview (744 LOC) — 4 Tabs                              ║
║   DashboardTab, DemographicsTab, AdvisorTab, PropertyTab        ║
║  WindowEconomy (510 LOC) — 6 Tabs                              ║
║   MarketsTab, PricesTab, FirmsTab, WagesTab, SubsidiesTab,      ║
║   BooksTab                                                     ║
║  WindowState (528 LOC) — 6 Tabs                                ║
║   WarehousesTab, FiscalTab, PublicWorksTab, SocialTab,         ║
║   FaithTab, DebugTab (permanent sichtbar)                        ║
║  WindowQuickview (195 LOC) — kompakte Anzeige (Numpad 0)        ║
╚════════════════════════════════════════════════════════════════╝
```

### Truth-Tabelle (keine Phantom-Dateien)

| Datei | Existiert? | Pfad |
|---|---|---|
| `EconWindowBase.java` | ✅ | `src/vannon/syx/economy/ui/EconWindowBase.java` (368 LOC) |
| `WindowEconomy.java` | ✅ | `src/vannon/syx/economy/ui/WindowEconomy.java` (510 LOC) |
| `WindowOverview.java` | ✅ | `src/vannon/syx/economy/ui/WindowOverview.java` (744 LOC) |
| `WindowState.java` | ✅ | `src/vannon/syx/economy/ui/WindowState.java` (528 LOC) |
| `WindowQuickview.java` | ✅ | `src/vannon/syx/economy/ui/WindowQuickview.java` (195 LOC) |
| ~~`EconContext.java`~~ | ❌ nicht mehr — Inhalt wurde in die 4 Window-Files integriert | — |
| ~~`EconTab.java`~~ | ❌ nicht mehr — `TabContent`-Interface direkt in `EconWindowBase` | — |
| ~~`EconWidgets.java`~~ | ❌ nicht mehr — Vanilla-Widgets (`SPanel`, `GColor`, `GButton`, `GCheckBox`, `GSlider`) | — |
| ~~`OverviewTabs.java`~~ | ❌ nicht mehr — Tabs sind als statische innere Klassen in `WindowOverview` | — |
| ~~`EconomyTabs.java`~~ | ❌ nicht mehr — Tabs sind als statische innere Klassen in `WindowEconomy` | — |
| ~~`StateTabs.java`~~ | ❌ nicht mehr — Tabs sind als statische innere Klassen in `WindowState` | — |

---

## Adapter-Layer (14 Dateien, vollständig aufgelistet)

### Bypass-SDK (`adapter/seam/`, 4 Dateien — Phase A, v0.13.3)

Der **BypassGate** ist der zentrale Entry-Point für alle Private-Access-Bypasses.
Er hält einen `MethodHandles.Lookup`, bietet typisierte Factories für Feld-/Methoden-Zugriffe
und delegiert an `FieldAccessor` (IntField, DoubleField, FloatField, RefField<T>),
`MethodAccessor` (VoidMethod, BooleanMethod) und `ClassResolver` (Class.forName mit
Game-ClassLoader). Primärer Pfad: VarHandle/MethodHandle (JDK 21+, 3–6× Speedup).
Fallback: java.lang.reflect.*.

### Interfaces (5)
| Interface | Vanilla-Zugriff |
|---|---|
| `ISyxAI` | 6 `Class.forName(name, true, Humanoid.class.getClassLoader())`-Aufrufe für `PlanOddjobber`, `F_SPlanEatery`, `F_SPlanCanteen`, `F_PlanEat`, `PlanTavern`, `M_PlanMarket` |
| `ISyxTransport` | `TransportInstance.distance` (float, Field.getFloat) |
| `ISyxWarehouse` | `StockpileInstance.storingSet(boolean)` (Method.invoke) |
| `ISyxBoosting` | `BOOSTABLES.CIVICS().GOV` Boostable für den INDUSTRIE-Stufen-Admin-Boost |
| `ISyxDiplomacy` | 4 Felder + Bitmap auf `DipWarPlayer`: `upI`, `pPow`, `coalitionPow`, `bWilling`; `willing()` über Public-Getter |

### Vanilla-Implementierungen (5)
Alle via BypassGate SDK: `VanillaAIAdapter`, `VanillaTransportAdapter`, `VanillaWarehouseAdapter`, `VanillaBoostingAdapter`, `VanillaDiplomacyAdapter`. VarHandle/MethodHandle wird automatisch bevorzugt (3–6× Speedup), Reflection als Fallback. Keine separaten MH-Varianten mehr (Phase B–D, v0.13.10).

### Fallback-Implementierungen (0)
Keine. Jeder Vanilla-Adapter hat seinen eigenen BypassGate mit eigenem
`initOk`-Flag. Consumer prüfen `ISyx*.isAvailable()` pro Adapter individuell
→ granulare Degradation bleibt erhalten. Ein fehlgeschlagener Diplomacy-
Adapter deaktiviert nicht den Transport-Adapter. Phase B–E, v0.13.10.

### Package-Private Brücken (4, in `src/settlement/room/`)
| Datei | Vanilla-Klasse | Zweck |
|---|---|---|
| `settlement/room/main/employment/LaborMarketAccess.java` | `RoomEmployment.Priority` | Direkter Lese-/Schreibzugriff auf `priority` |
| `settlement/room/service/food/tavern/EconomyTavernAccess.java` | `TavernInstance` | Drink-Vorrat lesen |
| `settlement/room/service/food/eatery/EconomyEateryAccess.java` | `EateryInstance` | Food-Vorrat lesen |
| `settlement/room/service/food/canteen/EconomyCanteenAccess.java` | `CanteenInstance` | Food-Vorrat lesen |

Diese Klassen existieren im SELBEN Java-Package wie die Vanilla-Klassen → Reflection ist nicht nötig, der Compiler prüft die Zugriffe.

---

## Orchestrator — EconomySim (1.459 LOC)

```java
public final class EconomySim {
    public static final int CHUNKED_VERSION = 33;  // Save-Format-Versions-Anker
    // Field-Initializer-Order:
    //   Wallets, Roster, Wages, FirmLedger, Taxes, Fiscal, ReligionMarket,
    //   Liturgy, CorveeController, DebtBondage, OddjobMarket, MilitaryPayroll,
    //   StateWageMarket, HandoutRelief, ProductionSubsidies, FlowMeter,
    //   FlowPrices, ScarcitySignal, EconIndicators, AccessAutomation,
    //   ConstructionHoardController (Kernel-mode), WarehouseMarket,
    //   Escrow, AffordabilityGate, FoodPlanController (Kernel-mode),
    //   PurchasePlanController (Kernel-mode), ServiceMarket,
    //   ServicePlanController (Kernel-mode), HousingMarket, ForeignTradeLedger.
    //   Constructor-built (Adapter-abhängig): transportMarket, stateWarehouses,
    //   progression (braucht ISyxBoosting), debtDiplomacyBuffer (braucht ISyxDiplomacy),
    //   propertyMarket (aus Phase 5e extrahiert).
}
```

Update-Pfad pro Tick (`ds` Game-Sekunden):
1. Re-Entry-Guard-Check (`ReentryGuard.tryEnter()`).
2. `debtDiplomacyBuffer.update()` — IMMER im Tick, auch bei `roster<2`.
3. `EngineSeams.entitiesAvailable()` + `ds>0` Guards.
4. `roster.rebuild()` — Bevölkerung neu laden.
5. Treasury-Crisis-Check (`CrisisDispatch.update`).
6. `warehouseMarket.beginTick()` + `stateWarehouses.beginTick()`.
7. **Workspace-Tick:** `FlowMeter.sample() → WarehouseAutomation.autoTune() → FlowPrices.refresh() → wallets.touch() (seeding vs. importing) → GrainDole.update() → warehouseMarket.prune() → foodPlanController.update() → purchasePlanController.update() → serviceMarket.refresh() → servicePlanController.update() → constructionHoardController.update()`.
8. **Handel:** `warehouseMarket.buy/buyCheaperCrownGoods/buyExports/buyConstructionMaterials/settleSeizures/sellInputs/sellInputs(b2b)`.
9. **Löhne:** `firmLedger.update() → maintenanceMarket.update() → productionSubsidies.update() → stateWages.update() → wages.update() → transportMarket.update() → handoutRelief.update() → stateWarehouses.payWages() → warehouseMarket.taxInventory() → corveeController.update() → accessAutomation.update() → laborMarket.update() → oddjobMarket.update() → oddjobAutomation.autoTune()`.
10. **Staat:** `taxes.update() → fiscal.update() → religionMarket.update() → liturgy.update() → housingMarket.update() → propertyMarket.update() → settleTaxSeason() → debtBondage.update() → purchases.update()`.
11. **Audit + Yardsale:** `auditSupply() → stats.recompute() (alle X Tage) → histogram.dump() (selten) → Encounter-Sampling via `PairSource` (proximity oder random, `encountersPerGameSecond * ds`) → `wallets.applyExchange()` pro Paar.
12. **EconIndicators:** alle 60 Ticks ein neuer `EconSnapshot`, `EconIndicators.update`, `EconProgression.update`, `GiniConsequences.announceIfCrossed`.
13. **Day-Rollover:** bei `ticks % DEFAULT_TICKS_PER_DAY == 0`: `treasuryHistory.push()`, `giniHistory.push()`, `EventLog.logSampled("CONFIG", conflict)`, `DiagnosticExporter.exportDay()`, `ForeignTradeLedger.dailyTick()`.
14. **ReentryGuard.exit()`** im finally.

---

## Save/Load (chunked TLV, Version 33)

```java
private static final int CHUNK_MAGIC = 0xEC0FEC0F;
private static final int TAG_CORE_SCALARS    =  1;
private static final int TAG_ECON_CONFIG     =  2;
private static final int TAG_WAGES           =  3;
private static final int TAG_TAXES           =  4;
private static final int TAG_FISCAL          =  5;
private static final int TAG_LABOR_MARKET    =  6;
private static final int TAG_MAINTENANCE_MARKET = 7;
private static final int TAG_GRAIN_DOLE      =  8;
private static final int TAG_RELIGION_MARKET =  9;
private static final int TAG_LITURGY         = 10;
private static final int TAG_DEBT_BONDAGE    = 11;
private static final int TAG_MilitaryPayroll = 12;
private static final int TAG_PRODUCTION_SUBSIDIES = 13;
private static final int TAG_STATE_WAREHOUSES = 14;
private static final int TAG_WAREHOUSE_MARKET  = 15;
private static final int TAG_STATE_WAGES = 16;
private static final int TAG_PROGRESSION = 17;
private static final int TAG_CORVEE = 18;
private static final int TAG_HOUSING = 19;
private static final int TAG_FOREIGN_TRADE_LEDGER = 20;
private static final int TAG_END = 0x7FFFFFFF;
```

Speicher-Syntax (aus `snake2d.util.file`):
```java
file.l(long)        // 8 bytes
file.i(int)         // 4 bytes
file.d(double)      // 8 bytes
file.bool(boolean)  // 1 byte
```

**Reihenfolge in `save()` und `load()` muss IDENTISCH sein.** Lesen via `ChunkedSave.startChunk(file, TAG)` / `ChunkedSave.endChunk(file, pos)` liefert die Chunk-Länge, sodass unbekannte historische oder zukünftige Chunks sauber übersprungen werden.

---

## Hotkeys (Umsetzung in `InstanceScript.pollHotkeys`)

GLFW-Key-Codes für Numpad:
- 334 Numpad + → WindowOverview
- 333 Numpad − → WindowEconomy
- 332 Numpad ∗ → WindowState
- 320 Numpad 0 → WindowQuickview (kompakte Anzeige)
- 331 Numpad / → `DebugTracer.dump()` (Log-Datei)
- 256 ESC → `closeAllWindows()`

Edge-Detection via boolean State-Prev (`overviewWasDown`, `economyWasDown`, etc.). Clean-Switching in `switchTo()`: Ziel offen → toggle (schließen); sonst alle anderen schließen, dann Ziel öffnen.

---

## Bootstrap-Reihenfolge

```
Spiel-Start → sucht Mods → liest _Info.txt
    │
    ▼
MainScript.initBeforeGameInited()
    ├── WealthHappiness.register()
    ├── InflationOff.register()
    ├── MeticImmigration.register()
    ├── PropertyHappiness.register()  [ab WOHLSTAND-Stufe]
    ├── GiniConsequences.register()
    ├── PovertyPressure.register()
    ├── HealthPressure.register()
    └── AccessAutomation.register()
    │
    ▼
InstanceScript-Konstruktor
    ├── EconConfig.init()  // Lazy Vanilla-Init
    ├── EconConfig.resetLaborDefaults()
    ├── EconomySim erstellen
    ├── WindowOverview / WindowEconomy / WindowState / WindowQuickview
    ├── SubjectWallet / SubjectJob / EconHud
    └── EconWindowBase.setSiblings(...)  // für switchTo
    │
    ▼
Spiel tickt — pro Frame:
    InstanceScript.update()
        → DebugTracer.tick()
        → EconomySim.update(deltaSeconds)
        → pollViewChange()
        → pollHotkeys()
        → pollDumpHotkey()
```

---

## Phasen & Stufen

Wirtschafts-Stufen (`EconProgression`):
- 0 SUBSISTENZ (Start)
- 1 HANDEL (materielle Märkte)
- 2 INDUSTRIE (komplexe Produktion, Bibliothek, Militär)
- 3 WOHLSTAND (Hauskauf + Firmenanteile, `PropertyHappiness`)
- 4 IMPERIUM (Aktienhandel freigeschaltet)

Stufen-Freischaltung in `onStageAdvance()` mit `EventLog.log("STAGE", ...)`. Aktuelle Stufe aus `EconConfig.economicStage` (Default SUBSISTENZ). Sandbox kann via `MeticImmigration.register` mehr Einwanderung anziehen sobald Wohlstand wächst.

---

## TreasuryCrisis-Kaskade

5 Tier-Stufen + Hard-Floor in Tier 5 (Default-Schwellen aus `EconConfig`):

| Tier | Schwelle | Effekt |
|---|---|---|
| 1 | ≤ −5K | Subventionen aus |
| 2 | ≤ −50K | 15 Lohnkonstanten halbiert |
| 3 | ≤ −250K | Zwangs-Liquidation aller Staatslager |
| 4 | ≤ −1M | Kopfsteuer=500, Marktsteuer=50%, einige Systeme deaktiviert |
| 5 | ≤ −5M | ALLE 11 Systeme aus, Hard Floor, `BOOSTABLES.BEHAVIOUR().LOYALTY` = −50% |

Mit Hysterese: `climbStepsDown`/`climbStepsUp` verhindern Flackern an Tier-Grenzen.

---

## Definition of Done

| Check | Methode |
|---|---|
| `mvn validate` BUILD SUCCESS | Stam-Doku-Sync + Code-Audit + Version-Consistency + Adapter-Signaturen (alle 4 Gates) |
| `mvn compile` BUILD SUCCESS | Keine Compiler-Errors, keine Mod-Code-Warnings |
| `mvn test` 138+ Tests grün | `mvn surefire:test` |
| `mvn package` produziert `_Info.txt` | Maven-Filter aus `pom.xml` `<mod.info>` & `<mod.changelog>` |
| `mvn clean install` bumpt `<version>` +0.0.1 | Antrun-Block in install-Phase |
| Drift-Freiheit | `tools/verify-doc-sync.sh` PASS |
