# SyxEconomyMod — Architektur

> **Version:** v0.13.90 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-28
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

Modul-Bilanz: **163 Java-Dateien, ~31.152 LOC** (core 22.539 + adapter 5.164 + ui 2.623 + benchmark 328 + warehouse/market 51 + bridges 309 + io 220)

| Modul | Dateien | LOC | Aufgabe |
|---|---:|---:|---|
| `vannon/syx/economy/core/` | 126 | ~22.539 | Wirtschafts-Sim + Subsysteme (inkl. EngineLevers 103 Toggles, io/ 2 Dateien) |
| `vannon/syx/economy/adapter/` | 27 | ~5.164 | EngineMirror-SDK (9) + ISyx* Legacy (7) + Vanilla (5) + Bypass-SDK (5) + Dispatcher (1) |
| `vannon/syx/economy/ui/` | 5 | ~2.623 | 4 Fenster + Base |
| `vannon/syx/economy/benchmark/` | 1 | ~328 | Reflection-vs-MethodHandle-Benchmark |
| `vannon/syx/economy/warehouse/market/` | 1 | ~51 | MarketSharedState (Sprint M-1) |
| `settlement/room/...` | 4 | ~309 | Package-Private Brücken (compile-time-safe, außerhalb mod-package) |

---

## Schichten-Modell

```
╔════════════════════════════════════════════════════════════════╗
║  SCHICHT 0: Vanilla Engine (Songs of Syx V71)                  ║
╚═════════════════════════════════════╤══════════════════════════╝
                                       ▼
╔════════════════════════════════════════════════════════════════╗
║  SCHICHT 1: Adapter-Layer (27 Dateien)                         ║
║  EngineMirror-SDK (9) + ISyx* Legacy (7) + Vanilla (5) + Bypass-SDK (5) + Dispatcher (1) ║
╚═════════════════════════════════════╤══════════════════════════╝
                                       ▼
╔════════════════════════════════════════════════════════════════╗
║  SCHICHT 2: Wirtschafts-Logik (`core/`, 126 Dateien)           ║
║  Orchestrator: EconomySim — 6 Engines + 1 Facade (Sprint M-1) ║
╚═════════════════════════════════════╤══════════════════════════╝
                                       ▼
╔════════════════════════════════════════════════════════════════╗
║  SCHICHT 3: UI (`ui/`, 5 Dateien, 16 Tabs)                     ║
╚════════════════════════════════════════════════════════════════╝
```

### IO-Analysis-Subsystem (Sprint IO-1, v0.13.79)

Empirische Input-Output-Analyse der Industrieverflechtungen:

```
IOGraph (Recipe Graph, ~110 LOC)
├── build() — baut Adjacency aus SETT.ROOMS().industries.all
├── getProducers(res) / getConsumers(res) — wer produziert/verbraucht was
├── consumerCount(res) — Zähler für Advisor: "X-Mangel betrifft Y Industrien"
└── isUpstream(a, b) / directInputsFor(output) — Ketten-Query

IOMatrix (Empirische IO-Tabelle, ~110 LOC)
├── compute(meter, graph) — A[n×n] aus FlowMeter supplyPerDay + IOGraph
├── computeLeontiefInverse() — L=(I-A)^(-1) via Gauß-Jordan
├── computeTotalRequirements(∆D) — ∆X=L×∆D (Gesamtbedarf inkl. Ketten)
└── export via DiagnosticExporter → rebalance_io_<epoch>.csv
```

Integration: EconomySim (lazy-init IOMatrix(0), day-tracked compute),
DiagnosticExporter (Long-Format CSV: direct + total coefficients),
WindowOverview (Advisor: chain-bottleneck warning bei ≥3 Downstream-Konsumenten).

### Warehouse-Subsystem (Sprint M-1, v0.13.61)

Ehemals 1 God-Class (`WarehouseMarket`, 1.902 LOC) → jetzt 8 kohäsive Dateien:

```
WarehouseMarket (Facade, ~320 LOC)
├── MarketSharedState (T-101, 51 LOC) — shared data container
├── WholesaleEngine (T-102, 553 LOC) — buy/sell/distribute
├── CrownTitleEngine (T-103, 200 LOC) — crown-title operations
├── RetailSyncEngine (T-104, 200 LOC) — retail delivery sync
├── AutoProcurementEngine (T-105, 175 LOC) — construction/export procurement
├── MarketMaintenanceEngine (T-106, 260 LOC) — prune/seizures/intake-locks
└── MarketTaxEngine (T-107, 60 LOC) — inventory taxation
```

Alle Engines liegen im selben Package `vannon.syx.economy.core`. MarketSharedState
im Subpackage `warehouse.market` (public Felder für cross-package access).
Save-Format: FORMAT 8 (backward-compatible mit V7). 14 Inner Records verbleiben
in WarehouseMarket (Book, DirectClaim, RetailBook, RetailLot, 4× Pending*,
Purchase, CrownStorage, SaleDistribution, Settlement, RetailQuote, OwnerlessRetailClaims).

### Truth-Tabelle (keine Phantom-Dateien)

| Datei | Existiert? | Pfad |
|---|---|---|
| `EconWindowBase.java` | ✅ | `src/vannon/syx/economy/ui/EconWindowBase.java` (413 LOC) |
| `WindowEconomy.java` | ✅ | `src/vannon/syx/economy/ui/WindowEconomy.java` (528 LOC) |
| `WindowOverview.java` | ✅ | `src/vannon/syx/economy/ui/WindowOverview.java` (841 LOC) |
| `WindowState.java` | ✅ | `src/vannon/syx/economy/ui/WindowState.java` (645 LOC) |
| `WindowQuickview.java` | ✅ | `src/vannon/syx/economy/ui/WindowQuickview.java` (195 LOC) |
| ~~`EconContext.java`~~ | ❌ nicht mehr — Inhalt wurde in die 4 Window-Files integriert | — |
| ~~`EconTab.java`~~ | ❌ nicht mehr — `TabContent`-Interface direkt in `EconWindowBase` | — |
| ~~`EconWidgets.java`~~ | ❌ nicht mehr — Vanilla-Widgets (`SPanel`, `GColor`, `GButton`, `GCheckBox`, `GSlider`) | — |
| ~~`OverviewTabs.java`~~ | ❌ nicht mehr — Tabs sind als statische innere Klassen in `WindowOverview` | — |
| ~~`EconomyTabs.java`~~ | ❌ nicht mehr — Tabs sind als statische innere Klassen in `WindowEconomy` | — |
| ~~`StateTabs.java`~~ | ❌ nicht mehr — Tabs sind als statische innere Klassen in `WindowState` | — |

---

## Adapter-Layer (27 Dateien, vollständig aufgelistet)

### Bypass-SDK (`adapter/seam/`, 5 Dateien — Phase A, v0.13.3)

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

### EngineMirror-SDK (IMPLEMENTIERT, v0.13.64)

Zentrale Fassade die ALLE Vanilla-Zugriffe bündelt. Hybride Architektur:
- **Private Zugriffe** → bestehende ISyx\* Adapter via BypassGate SDK
- **Public Zugriffe** → direkte Compilezeit-Links (SETT, STATS, TIME, FACTIONS)
- **Config** → `EngineLevers.java` (103 Toggles, granulare Degradation pro Zugriff)
- **Logging** → `LoggingAdapter.csvTrace()` in jedem Mirror-Method
- **Version-gebunden** V71.44 — SDK-Generic kommt später

**9 Dateien (EngineLevers.java liegt in core/, nicht adapter/), 3.223 LoC implementiert:**

| Datei | LOC | Aufgabe |
|---|---:|---|
| `EngineMirror.java` | 184 | Zentrale Fassade: `api().rooms()/.factions()/.humanoids()/.stats()` |
| `EngineLevers.java` | 288 | 100 boolean Toggles für granulare Degradation |
| `IRoomAccess.java` | 233 | Interface: 32 Methoden (Stockpile, Transport, Rooms, Station) |
| `RoomAccessImpl.java` | 712 | BypassGate hybrid Implementation (inkl. Station tally via cached Methods) |
| `IFactionAccess.java` | 206 | Interface: 28 Methoden (NPC, Diplomacy, Trade, Royalty) |
| `FactionAccessImpl.java` | 548 | Compilezeit-only Implementation |
| `IHumanoidAccess.java` | 224 | Interface: 18 Methoden + PlanCatalog (6 AI-Plan-Klassen) |
| `HumanoidAccessImpl.java` | 463 | ClassResolver für package-private AI-Plans |
| `IStatsAccess.java` | 100 | Interface: 12 Methoden (BOOSTABLES, Religion, Weather) |
| `StatsAccessImpl.java` | 265 | BypassGate für BOOSTABLES-Zugriffe |

```
EngineLevers.java (100 Config-Toggles)
       ↓
EngineMirror.java (Fassade: api().rooms()/.factions()/.humanoids()/.stats())
       ↓
4 Sub-Interfaces + Stats + 4 Impl (Hybrid: BypassGate + direkt, Logging)
       ↓
Vanilla Engine V71.44 (2.443 Java-Files)
```

~88 Vanilla-Zugriffe total (30 Room + 28 Faction + 18 Humanoid + 12 Stats).
Bestehende ISyx\* Adapter bleiben als Legacy — EngineMirror ersetzt langfristig.

**B-008 Migration (Phase 1 abgeschlossen, v0.13.64):** 25 von 55 EngineSeams-Aufrufen
in 6 Core-Dateien auf EngineMirror migriert (Fallback-Pattern:
`EngineMirror.api() != null ? ... : EngineSeams.*`). 30 verbleibende warten auf Phase 2.

Siehe `ROADMAP.md` §Sprint A-1 + B-008 für vollständige Task-Liste + Definition of Done.

### Package-Private Brücken (4, in `src/settlement/room/` — außerhalb `vannon.syx.economy`)

> **Achtung:** Diese Dateien liegen NICHT unter `src/vannon/syx/economy/`, sondern direkt
> im Spiel-Package-Namensraum `src/settlement/room/`. Sie teilen das Package mit den
> Vanilla-Klassen → kein Reflection nötig. Der God-Class-Guard scannt sie korrekt.

| Datei | LOC | Vanilla-Klasse | Zweck |
|---|---|---|---|
| `LaborMarketAccess.java` | 68 | `RoomEmployment.Priority` | Direkter Lese-/Schreibzugriff auf `priority` |
| `EconomyTavernAccess.java` | 80 | `TavernInstance` | Drink-Vorrat lesen |
| `EconomyEateryAccess.java` | 68 | `EateryInstance` | Food-Vorrat lesen |
| `EconomyCanteenAccess.java` | 93 | `CanteenInstance` | Food-Vorrat lesen |

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
3. `EngineMirror.api().rooms().entitiesAvailable()` + `ds>0` Guards (Fallback: EngineSeams).
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
| `mvn test` 402 Tests grün | `mvn surefire:test` |
| `mvn package` produziert `_Info.txt` | Maven-Filter aus `pom.xml` `<mod.info>` & `<mod.changelog>` |
| `mvn clean install` bumpt `<version>` +0.0.1 | Antrun-Block in install-Phase |
| Drift-Freiheit | `tools/verify-doc-sync.sh` PASS |


## Quality-Gates (9 Gates — Master-Build-Gate Orchestrator)

`tools/build-gate.sh` orchestriert 9 Gates in der Maven-`validate`-Phase:

| # | Gate | Skript | Skip-Toggle | Hart-Block? |
|---|---|---|---|---|
| 1 | Stam-Doku-Sync | `verify-doc-sync.sh` | `SKIP_SYNC` | ✅ |
| 2 | Code-Audit (silent failure) | `code-audit.sh` | `SKIP_AUDIT` | ✅ |
| 3 | Version ↔ Changelog | `verify-version-consistency.sh` | `SKIP_VERSION_CHECK` | ✅ |
| 4 | Adapter ↔ Engine-Signaturen | inline in `build-gate.sh` | — | ✅ |
| 5 | Bytecode-Injection Audit | `audit-bytecode.sh` | — | ✅ |
| 6 | Sim-Logic Audit | `audit-sim-logic.sh` | — | ✅ |
| 7 | Schema-Validierung | inline (YAML ↔ Adapter) | — | ✅ |
| 8 | Balance-Regression | `balance-regression-check.sh` | `SKIP_BALANCE` | ✅ |
| **9** | **God-Class-Guard** | **`god-class-guard.sh`** | **`SKIP_GOD_GUARD`** | **✅** |

Sprint M-3 fuehrt Gate 9 ein: Hard-Block gegen neue God-Files. Schwellen
800 LOC / 35 PubM / 24 Fields. Pattern-Exempts fuer Rule 6 (UI-Windows),
Rule 9 (BypassGate-SDK), Benchmark und Settlement-Bridges.
Historic-Baseline-Drift-Toleranz: +5% LOC, +10% PubM/Fields.
Siehe `agents.md` Rule 14 fuer Policy-Spec.
