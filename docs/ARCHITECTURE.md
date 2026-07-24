# SyxEconomyMod — Architektur-Dokumentation

> Version 0.1.5-in-progress | Songs of Syx V71.44 | Stand: 2026-07-24
>
> **v0.1.5 UI-Refactor:** `vannon.syx.economy.ui` angelegt; `EconWindowBase`, `EconContext`, `EconTab`, `EconWidgets` sowie `WindowOverview`, `WindowEconomy`, `WindowState` mit 9 Tabs implementiert. Legacy `EconomyWindow.java` noch im Baum, wird aber nicht von `InstanceScript` verwendet.
>
> **v0.1.4 Extraktionen:** `RoomOperatingModeController` (FirmLedger→79 LOC),
> `PropertyMarketController` (EconomySim→179 LOC), `CrisisDispatch` (EconomySim→27 LOC).
> EconomySim: 1.553→1.442 LOC (−111). Der Re-Entry-Guard von v0.1.4.1 (boolean + try/finally) addierte 3 LOC gegenüber dem 1.439-Stand nach den drei Controller-Extraktionen.
>
> **v0.1.4 IdentityHashMap Phase 1:** 3 Maps von `IdentityHashMap<RoomBlueprintImp, …>` auf
> `HashMap<String, …>` migriert (stabiler Key: `blueprint.key`).
> `FirmLedger.serviceRevenue`, `FirmLedger.stateWageMarginal`, `StateWageMarket.carry`.

---

## Überblick

Das Mod fügt Songs of Syx eine parallele Wirtschaftsschicht hinzu. Jeder Siedler hat ein eigenes Portemonnaie, Firmen rechnen ab, der Staat kann Steuern erheben und Subventionen verteilen. Das Mod ersetzt keine Vanilla-Systeme, sondern arbeitet daneben.

---

## Dateistruktur (Stand v0.1.5-Plan)

```
src/vannon/syx/economy/
│
├── core/  (98 Dateien, ~21.400 LOC)
│   ├── EINTRITTS-PUNKTE (vom Spiel aufgerufen)
│   │   ├── MainScript.java          — Registriert Booster beim Spielstart
│   │   └── InstanceScript.java      — Erstellt EconomySim + UI-Fenster
│   │
│   ├── ORCHESTRATOR
│   │   ├── EconomySim.java          — Zentrale Instanz, tickt jede Stunde (~1.442 LOC)
│   │   ├── PropertyMarketController.java — Phase 5e: Property-Markt-Logik, aus EconomySim extrahiert
│   │   ├── CrisisDispatch.java      — Phase 5e: TreasuryCrisis-Update-Wrapper
│   │   └── RoomOperatingModeController.java — Phase 5e: Per-Room Op-Mode + Cost-Scaling
│   │
│   ├── UI-LEGACY
│   │   ├── EconomyWindow.java       — Legacy God-File (3.081 LOC, 18 Tabs) — noch vorhanden, aber unbenutzt; Löschung nach vollständiger Migration
│   │   └── EconTexts.java           — Alle UI-Strings (DE/EN)
│   │
│   ├── KONFIGURATION
│   │   └── EconConfig.java          — 140+ statische Regler + stage-gated Wallets
│   │
│   ├── WIRTSCHAFTS-KERNSYSTEME
│   │   ├── Wallets.java             — Geldbörsen, Yard-Sale-Transfer (stage-gated)
│   │   ├── FlowPrices.java          — Angebot/Nachfrage → Preisbildung
│   │   ├── FlowMeter.java           — Ressourcen-Tracking (V71 Consumption API)
│   │   ├── FirmLedger.java          — Betriebsbuchhaltung, Hillclimber, Income-Tracking
│   │   │                             — v0.1.4: Cold-Start-Guard + marginal-Cap (wageMax)
│   │   └── ...
│   │
│   ├── DIAGNOSTIK & KRISEN
│   │   ├── DiagnosticExporter.java  — 3 CSV-Exporte pro Spieltag (opt-in)
│   │   ├── TreasuryCrisis.java      — 5-stufige Krisenmechanik + Hard-Floor
│   │   └── AffordabilityGate.java   — Food-Affordability (v0.1.3: gate default=true)
│   │
│   └── HILFSKLASSEN (30+)
│
├── adapter/  (17 Dateien) [Phase 4]
│   ├── INTERFACES (5)
│   │   ├── ISyxAI.java
│   │   ├── ISyxTransport.java
│   │   ├── ISyxWarehouse.java
│   │   ├── ISyxBoosting.java
│   │   └── ISyxDiplomacy.java
│   ├── VANILLA-IMPLEMENTIERUNGEN (8)
│   │   ├── VanillaAIAdapter.java
│   │   ├── VanillaTransportAdapter.java
│   │   ├── VanillaTransportAdapterMH.java      — MethodHandle-optimiert
│   │   ├── VanillaWarehouseAdapter.java
│   │   ├── VanillaWarehouseAdapterMH.java      — MethodHandle-optimiert
│   │   ├── VanillaBoostingAdapter.java
│   │   ├── VanillaDiplomacyAdapter.java
│   │   └── VanillaDiplomacyAdapterMH.java      — MethodHandle-optimiert
│   └── FALLBACK-IMPLEMENTIERUNGEN (4)
│       ├── FallbackTransportAdapter.java
│       ├── FallbackWarehouseAdapter.java
│       ├── FallbackBoostingAdapter.java
│       └── FallbackDiplomacyAdapter.java
│
├── ui/  (10 Dateien implementiert) [Phase 5 UI-Refactor — siehe Plan 2026-07-24-3-window-ux-refactor.md]
│   ├── EconWindowBase.java          — Abstrakter Interrupter, KPI-Header, Tab-Bar, Input-Blocking
│   ├── EconContext.java             — Render-Kontext pro Frame
│   ├── EconTab.java                 — Tab-Interface (inkl. default click-Callback)
│   ├── EconWidgets.java             — Shared Widgets (Slider, Button, Toggle, Scrollbar, Text)
│   ├── WindowOverview.java          — Fenster "Übersicht"
│   ├── WindowEconomy.java           — Fenster "Wirtschaft"
│   ├── WindowState.java             — Fenster "Staat"
│   ├── OverviewTabs.java            — DashboardTab, CitizensTab, AdvisorTab
│   ├── EconomyTabs.java             — PricesTab, WagesFirmsTab, SubsidiesTab
│   └── StateTabs.java               — WarehouseTab, TaxesTab, SocialTab
│
├── benchmark/  (1 Datei)
│   └── AdapterReflectionBenchmark.java
│
└── settlement/room/  (Brücken-Klassen)
    ├── main/employment/LaborMarketAccess.java   — Package-Private Bridge
    └── room/service/food/*/Economy*Access.java   — Service-Zugriffe
```

> **Single Source of Truth für UI-Refactor:** `docs/superpowers/plans/2026-07-24-3-window-ux-refactor.md`

---

## Schichten-Modell

```
╔═══════════════════════════════════════════════════════╗
║  SCHICHT 1: Vanilla Engine (Songs of Syx)            ║
║  10.992 Klassen, nicht veränderbar                    ║
╚══════════════════════╤═════════════════════════════════╝
                       │
                       ▼
╔═══════════════════════════════════════════════════════╗
║  SCHICHT 2: Adapter-Layer (adapter/)                  ║
║  5 Interfaces, 12 Implementierungen                   ║
║  Einzige Schicht die Vanilla-Klassen direkt aufruft    ║
╚═════════════════════╤═════════════════════════════════╝
                       │
                       ▼
╔═══════════════════════════════════════════════════════╗
║  SCHICHT 3: Wirtschafts-Logik (eigener Code)          ║
║  EconomySim, Wallets, FlowPrices, FirmLedger, etc.    ║
║  120 Dateien (98 core/ + 17 adapter/), ~21.400 LOC      ║
╚═════════════════════╤═════════════════════════════════╝
                       │
                       ▼
╔═══════════════════════════════════════════════════════╗
║  SCHICHT 4: UI (3-Fenster-Refactor)                   ║
║  WindowOverview (3 Tabs) + WindowEconomy (3 Tabs) +   ║
║  WindowState (3 Tabs). Single Source of Truth:       ║
║  docs/superpowers/plans/2026-07-24-3-window-ux-refactor.md ║
╚═══════════════════════════════════════════════════════╝
```

---

## Ablauf beim Spielstart

```
Spiel startet → sucht Mods → liest _Info.txt
    │
    ▼
MainScript.initBeforeGameInited()
    ├── WealthHappiness.register()   → Reichtum → Glück (BEHAVIOUR.HAPPI)
    ├── InflationOff.register()      → Inflation aus (CIVICS.DEFALTION)
    ├── MeticImmigration.register()  → Einwanderer (CIVICS.IMMIGRATION)
    ├── PropertyHappiness.register() → Eigentum → Glück (BEHAVIOUR.HAPPI) [ab WOHLSTAND]
    └── GiniConsequences.register()  → Gini → Loyalty (BEHAVIOUR.LOYALTY) [NEU v1.7.0]
    │
    ▼
InstanceScript-Konstruktor
    ├── EconConfig.init()            → Lazy Vanilla-Init [NEU v1.7.0]
    ├── EconomySim erstellen
    ├── WindowOverview erstellen       → Hauptfenster (Übersicht)
    ├── WindowEconomy erstellen      → Wirtschaftsfenster
    ├── WindowState erstellen        → Staatsfenster
    └── SubjectWallet erstellen
    │
    ▼
Spiel läuft — jeden Tick:
    ├── EconomySim.update()       → Wirtschaft tickt
    ├── Aktives Fenster render()  → UI zeichnet
    └── mouseClick/hover          → Interaktion
```

---

## Wirtschaftsablauf (pro Tick)

```
1. Wer lebt noch?          → Roster.rebuild()
2. Preise aktualisieren    → FlowPrices.refresh()
3. Firmen abrechnen        → FirmLedger.update()
4. Arbeitsmarkt            → LaborMarket + StateWageMarket
5. Dienstleistungen        → ServiceMarket / GrainMarket
6. Steuern einziehen       → Taxes.collect() + Fiscal.disburse()
7. Geld transferieren      → Wallets.exchange() (Yard-Sale)
8. Konsistenz prüfen       → AuditKernel.checkConservation()
9. UI Cache aktualisieren  → updateRenderCaches()
```

---

## Geldfluss-Prinzip

Das Mod ist **konservativ**: Geld wird nicht erzeugt oder vernichtet, nur transferiert.

```
Staat
├── Einnahmen: Kopfsteuer + Markt-Abschöpfung + Mieteinnahmen + Lagersteuer
├── Ausgaben: Staatsgehälter + Subventionen + Kornspende + Importkäufe
└── Überwachung: AuditKernel meldet jeden Delta

Bürger
├── Einnahmen: Lohn + Verkäufe + Erbschaft + Dividenden
├── Ausgaben: Einkaufen + Steuern + Miete + Transport
└── Glück: Reichtum relativ zum Median + Eigenheim-Bonus
```

---

## Wichtige Design-Entscheidungen v1.7.0

### 1. Lazy Initialization in EconConfig
**Problem:** 130+ statische Felder, einige greifen beim Class-Loading auf Vanilla zu → NPE oder stille 0-Werte.
**Lösung:** `private static volatile boolean initialized` + `public static synchronized void init()` wird aus `InstanceScript` Konstruktor aufgerufen (Engine garantiert initialisiert).

### 2. Package-Private Bridge für LaborMarket
**Problem:** `RoomEmployment$Priority` Felder sind package-private → Mod kann nicht von außen zugreifen.
**Lösung:** `LaborMarketAccess` in exakt gleichem Package `settlement.room.main.employment` → direkter Zugriff auf `priority.get()/set()/min()/max()`, `freeShare()`, `restorePriority()`, `employmentOf()`. Kein Reflection, compile-time sicher.

### 3. Gini → Loyalty Coupling
**Problem:** Gini wurde berechnet, hatte aber keine spielbare Konsequenz.
**Lösung:** `GiniConsequences` registriert `BValue.BValueInduOnly` auf `BOOSTABLES.BEHAVIOUR().LOYALTY` (nicht CIVICS!).
- Gini 0.0 → 1.0x Loyalty (kein Effekt)
- Gini 1.0 → `loyaltyAtMaxGini` (Default 0.85 = -15% Loyalty)
- Gruppeneffekt (nicht individuell wie WealthHappiness)
- EventLog-Eintrag "UNREST" pro Saison bei Gini > 0.35

### 4. Wirtschaftsstufen (4→5) mit Sichtbarkeit & Freischaltung
**Problem:** `onStageAdvance()` war unsichtbar (nur `System.out.println`), Privatisierung war ab Tag 1 aktiv.
**Lösung:** 
- 5-Stufen-System: SUBSISTENZ(0)→HANDEL(1)→INDUSTRIE(2)→WOHLSTAND(3)→IMPERIUM(4)
- `EventLog.log("STAGE", ...)` bei jedem Aufstieg
- **INDUSTRIE:** Neue Zwischenstufe — Labor + Bibliothek + Militär-Gebäude. CIVIC_ADMIN/GOV-Boost (+20% Admin).
- `propertyMarketEnabled`, `homePurchaseEnabled`, `workplaceSharesEnabled` Defaults auf `false`
- Freischaltung in `onStageAdvance()`:
  - INDUSTRIE: `registerAdminBooster()` (ADMIN/GOV Reflection-Fallback)
  - WOHLSTAND: Privatisierung (Hauskauf + Firmenanteile), `PropertyHappiness.register()`
  - IMPERIUM: Aktienhandel freigeschaltet
- **Save-Migration v33:** Bestehende Saves (WOHLSTAND=2→3, IMPERIUM=3→4) via `rawLevel + 1`

### 5. EconIndicators: Trends sichtbar & konsequent
**Problem:** `wagesFalling`, `treasuryDeclining` wurden berechnet aber nirgends gelesen.
**Lösung:**
- `wasWagesFalling` Feld für Change-Detection (nur beim Umschlagen loggen)
- `EventLog.log("TREND", ...)` bei Trend-Wechsel
- Echte Konsequenz: sinkende Einnahmen → `doleHeadcap` auf 85% von `doleHeadcapBase` gekappt

### 6. Deterministisches Sampling in EventLog
**Problem:** `Math.random()` bricht Determinismus.
**Lösung:** `RND.rFloat()` (Vanilla RNG) für `logSampled()`.

### 7. meanWage-Fix: Echten Bürgerlohn statt Grenzgewinn
**Problem:** `EconProgression.checkAdvance()` verglich `LaborMarket.meanPositiveMarginal()` (Betriebs-Grenzgewinn) gegen `EconConfig.defaultWage` (50) — zwei verschiedene Größen.
**Lösung:** `EconSnapshot.actualMeanWage` aus `Wages`-Zahlungen (echter Bürgerlohn), verwendet im Stufenaufstieg HANDEL→WOHLSTAND.

### 8. UI-Sichtbarkeit: CitizenClass, PropertyLedger, DebtDiplomacyBuffer
**Problem:** Drei Backend-Systeme liefen komplett unsichtbar — 0 von 3 Sichtbarkeitskanälen.
**Lösung:**
- **CitizenClass-Panel:** 2×3-KPI-Grid im VERMÖGEN-Tab (BOSS, HEIR, MIGRANT, POOR, MIDDLE, UPPER) — sofort sichtbar.
- **Firmenbesitz-Panel:** Pro Blueprint Besitz-Quote + Dividenden-Pool im BETRIEBE-Tab.
- **DebtDiplomacyBuffer:** Chronik "DIPLO" (max 1×/Saison) + Berater-Zeile "Diplomatie-Puffer: X Fraktionen abgeschreckt".

### 9. PropertyLedger-Bugfixes [NEU v1.7.0]
**Problem 1:** `key(tx, ty, blueprintKey)` akzeptierte `blueprintKey` als Parameter, ignorierte ihn aber komplett — nur Tile-Koordinaten im Key. Haus und Firma auf demselben Feld → Kollision.
**Lösung:** `blueprintKey.hashCode()` in Key-Berechnung einbezogen: `((long) tx << 32L) | ((long) (ty ^ h) & 0xFFFFFFFFL)`. Save-Version auf 2 erhöht, `legacyKey()` für alte Spielstände.

**Problem 2:** Abgerissene Gebäude blieben für immer in `entries`-Map — monotones Wachstum, kein Cleanup.
**Lösung:** `cleanupGoneRooms()` via `SETT.ROOMS().map.get(tx, ty)` prüft Existenz jedes Eintrags, entfernt Geister. Aufgerufen aus `EconomySim.updatePropertyMarket()` einmal pro Saison.

**Problem 3:** `payDividends()` lineare Bürger-Suche pro Dividenden-Eintrag → O(Einträge × Bürger).
**Lösung:** HashMap<Integer, Humanoid> für O(1)-Lookup — einmal bauen, pro Eintrag abfragen.

### 10. SEAM-EventLog-Hooks [NEU v1.7.0, ERWEITERT v1.7.2]

**v1.7.0 — 7 Catches in 4 Dateien:**
- `EconProgression.java:261` — ADMIN/GOV-Reflection
- `DebtDiplomacyBuffer.java:53,156` — DipWarPlayer-Reflection (Init + Runtime)
- `TransportMarket.java:105,116` — Transport-Distanz-Reflection (inkl. `reflectionOk`-Flag)
- `StateWarehouses.java:328,341` — StockpileInstance.storingSet-Reflection (inkl. One-Shot-Flag gegen Spam)

**v1.7.2 — 5 weitere Catches in 2 Dateien:**
- `CitizenClass.java:105,121,129` — 3× `catch (Exception ignored)` → One-Shot EventLog (hType, POP, REL)
- `LocalPrices.java:50,74` — 2× `catch (Exception e)` → One-Shot EventLog (foodDays, drinkDays)

**v1.7.2 Phase 4 — 4 weitere in VanillaAIAdapter:**
- `adapter/VanillaAIAdapter.java` — 4× `catch (Throwable)` für Plan-Erkennung (extrahiert aus EngineSeams)

**Gesamt: 16 SEAM-EventLog-Hooks in 7 Dateien.** Keine stummen Catch-Blöcke mehr im gesamten Mod.
Kategorie "SEAM" (Nahtstelle) erscheint im Chronik-Tab.
One-Shot-Guards verhindern EventLog-Spam: jeder Fehler wird nur beim ersten Auftreten pro Session geloggt.

### 11. Tote Assets & Scaffolding entfernt [NEU v1.7.0]

---

## Wichtige Design-Entscheidungen v1.7.2

### 12. P0: EconIndicators-Block aus roster<2-Guard [NEU v1.7.2]
**Problem:** Der gesamte EconIndicators/EconProgression/GiniConsequences-Block in `EconomySim.update()` saß innerhalb von `if (this.roster.size() < 2)` — mit `return` danach. Bei 2+ Bürgern (jede echte Stadt) wurde die Trend-Pipeline **nie** ausgeführt. Stufenaufstieg, Gini-Warnungen, Trend-Erkennung — alles tot seit dem ersten Commit.
**Lösung:** Block aus dem `<2`-Guard herausgelöst und ans Ende des normalen Update-Pfads direkt vor `updateRenderCaches()` verschoben. Kommentar im Code: `// v1.7.2-Fix: War zuvor im roster<2 Guard → lief nie bei echter Population.` (EconomySim.java:616).

### 13. Stumme Catches in CitizenClass + LocalPrices [NEU v1.7.2]
**Problem:** `CitizenClass.java` enthielt 3× `catch (Exception ignored) {}`, `LocalPrices.java` 2× `catch (Exception e) {}` — insgesamt 5 stille Fallbacks die Vanilla-Zugriffsfehler komplett verschluckten.
**Lösung:** Alle 5 Catches mit One-Shot EventLog-Hooks instrumentiert (`EventLog.log("SEAM", ...)`). Statische Boolean-Flags verhindern Spam. Siehe Sektion 10 für die vollständige Liste.

### 14. Debug-Flags in Production deaktiviert [NEU v1.7.2]
**Problem:** `EconConfig.checkConservation = true` machte JEDEN Tick einen O(n)-Sweep über alle Wallets. `EconConfig.dumpIntervalTicks = 600` schrieb alle 600 Ticks Histogram-Dumps auf stdout.
**Lösung:** `checkConservation = false`, `dumpIntervalTicks = 0`.

### 15. Phase 4 Adapter-Layer — Schritt 5.1 [NEU v1.7.2]
**Problem:** 6 `getSimpleName().equals()`-Vergleiche in `EngineSeams.java` für AI-Plan-Erkennung — stirbt lautlos bei jedem Spiel-Update das Klassennamen ändert.
**Lösung:** Neues `adapter/`-Package mit `ISyxAI`-Interface + `VanillaAIAdapter`-Implementierung. Extrahiert alle 6 String-Konstanten und 4 try/catch-Methoden aus EngineSeams. EngineSeams-Methoden als `@Deprecated`-Wrapper erhalten (Backward-Compat für 5 Caller). Weitere Schritte (ISyxTransport, ISyxWarehouse, ISyxBoosting, ISyxDiplomacy) folgen in PHASE4_ADAPTER_PLAN.md.

### 16. Advisor-Tab Meilenstein-Anzeige [NEU v1.7.2]
**Problem:** `renderAdvisor()` zeigte nur 5 Meilensteine mit hardcodierten Strings — 5 weitere existierten als `EconTexts.¤¤advMs*`-Konstanten, wurden aber nie gerendert.
**Lösung:** Alle 10 `EconProgression`-Milestone-Flags werden jetzt im BERATER-Tab mit ✅/❌ gerendert. Hardcodierte Strings durch `EconTexts.¤¤advMs*`-Konstanten ersetzt. 2 neue Keys: `advMsFirstTemple`, `advMsFirstEmbassy`.

---

## Wichtige Design-Entscheidungen v0.1.0 (Phase 4)

### 22. Adapter-Architektur (Phase 4) — Forward-kompatibel bei Engine-API-Änderungen [NEU v0.1.0]
**Problem:** 4 Reflection-Stellen + 1 String-Matching-Stelle griffen direkt auf Vanilla-Klassen zu — bei Spiel-Updates stille Fehler.
**Lösung:** 5 Interfaces + 12 Implementierungen in `adapter/`-Package. Alle VanillaAdapter machen Reflection nur im Konstruktor (One-Shot). FallbackAdapter sind reine No-Ops. Bei Engine-API-Änderungen müssen nur 5 Adapter-Dateien geprüft werden.

### 23. Class.forName-ClassLoader-Fix [NEU v0.1.0]
**Problem:** `Class.forName(String)` nutzt Mod-ClassLoader — package-private Spiel-Klassen unsichtbar → alle 6 Plan-Erkennungen tot.
**Lösung:** `Class.forName(name, true, GAME_CL)` mit `Humanoid.class.getClassLoader()`. Null-Guard für Bootstrap-ClassLoader-Edge-Case.

### 24. DiagnosticExporter + Rebalance-Dashboard [NEU v0.1.0]
**Problem:** Keine longitudinalen Daten für Balancing-Entscheidungen — alles Ad-hoc im Code.
**Lösung:** 3 CSV-Dateien pro Spieltag (Macro, Resources, Firms). Python/Jupyter-Dashboard mit 5 Plots. Hard-Threshold-Alert-Layer für In-Game-Warnungen.

### 25. TreasuryCrisis — Negative Staatskasse [NEU v0.1.0]
**Problem:** 0 Treffer für treasuryFloor/debtCeiling/bankrupt im gesamten Codebase. Staatskasse konnte ins Bodenlose fallen.
**Lösung:** **5-stufige** Krisenmechanik mit Hysterese + Hard-Floor-Verhalten in Tier 5. Tier 1 Warnung (≤−5K, Subventionen aus) → Tier 2 Sparprogramm (≤−50K, 15 Lohnkonstanten halbiert) → Tier 3 Zwangs-Liquidation (≤−250K, alle Staatslager verkauft) → Tier 4 Staatsbankrott (≤−1M, Kopfsteuer=500, Marktsteuer=50%, Corvée/Dole/Schuldknechtschaft/Wages deaktiviert) → Tier 5 Kollaps + Hard Floor (≤−5M, ALLE 11 Systeme deaktiviert, **LOYALTY −50%** via `BOOSTABLES.BEHAVIOUR().LOYALTY` → Emigration/Strikes-Risiko).

### 26. Stage-gated Initial Wallet [NEU v0.1.0]
**Problem:** `initialWallet=5000` × 200 Bürger = 1M D Scheinwirtschaft am Tag 0 — kein BIP, sondern initiale Geldmengen-Injektion.
**Lösung:** Wallet skaliert mit Wirtschaftsstufe (200→500→2000→5000). `seed_money`-CSV-Spalte für Transparenz.

### 27. Catch-Tightening & Code-Audit-Gate [NEU v0.1.0]
**Problem:** 10 Sites mit `catch(Throwable)`/`catch(Exception)` — stille Fehlerschlucker.
**Lösung:** Alle auf `catch(RuntimeException)` eingegrenzt. Build-Gate blockt neue `catch(Throwable)` außerhalb adapter/benchmark.

### 28. IdentityHashMap-Migration — Phase 4.7-Blocker #8 (v0.1.4, Teilabschluss)
**Problem:** 16 persistente `IdentityHashMap<Object, X>` in core/ verlieren Daten nach Save/Load, weil Vanilla `RoomInstance`, `Induvidual`, `Humanoid`, `RoomBlueprintImp` neu instanziiert. `IdentityMapRegistry.clearOnLoad()` aus v0.1.3 machte den Verlust laut, aber die Daten waren trotzdem weg.
**Lösung Phase 1:** 3 Maps mit `RoomBlueprintImp`-Key auf `HashMap<String, X>` migriert — `blueprint.key` (String) ist save-stabil. Iterations-Pattern von Map-Entry-Iteration auf `SETT.ROOMS().ins()`+Lookup umgestellt (`FirmLedger.serviceRevenue`, `FirmLedger.stateWageMarginal`, `StateWageMarket.carry`).
**Phase 2 (deferred):** `Induvidual`-keyed Maps (8 persistent) — `Induvidual` hat keine `id()`-Methode, benötigt Refactor auf `Humanoid`-Key mit `humanoid.id()`.
**Phase 3 (offen):** `RoomInstance`-keyed Maps (4 persistent) — benötigt `RoomCoordinateKey`-Utility aus `PropertyLedger` (tile-Koordinate + blueprintKey als Composite-Long-Key).

**Kausalkette:** Carpenter Cold-Start-Fix (v0.1.4) nutzt `state.hill != null` als Guard. Ohne IdentityHashMap-Fix überlebt `hill` keinen Save/Load → jeder Load ist Cold-Start-Reset → Hillclimber beginnt von Null → Firmen pendeln zwischen 0 und 1 Arbeiter.

---

## Historische Design-Entscheidungen (Archiv)

### 17. Bauarbeiter-Massenauflauf ("Traubenbildungen") behoben [v1.7.3→v0.0.9]
**Problem:** Alle freien Bauarbeiter und Tagelöhner liefen gleichzeitig für denselben Bauauftrag los (selbst bei minimalem Bedarf), da sie die Zuweisungen im selben Tick nicht abglichen.
**Lösung:** Einführung einer Ressourcen-Reservierungskarte (`targetedResources`) in `ConstructionHoardController` und `ConstructionHoardPlan`, die erfasste Lieferungen trackt. Ein Bauauftrag ist nur wählbar, wenn der verbleibende Bedarf größer ist als die bereits auf dem Weg befindliche Menge.

### 18. Verhungerungstod-Sichtbarkeit [NEU v1.7.3]
**Problem:** `BrokeFoodPlan` setzte den Tod auf `STARVED` ohne jeglichen EventLog-Eintrag, wodurch verhungernde Bürger lautlos verschwanden.
**Lösung:** Instrumentierung von `EventLog.log("STARVATION", ...)` direkt vor dem Ableben des Bürger-Entities.

### 19. O(1) ServiceCache-Lookup im ServicePlanController [NEU v1.7.3]
**Problem:** `serviceAt()` iterierte jeden Tick über alle `RoomServiceAccess.ALL()` für jeden Bürger, was bei großen Städten extreme Performancekosten verursachte.
**Lösung:** Einführung eines statischen Blueprint→Service-Caches (`serviceCache`). Lookup-Kosten sinken von O(ServiceTypes) auf O(1) pro Bürger pro Tick.

### 20. Lohnkonstanten aligned [NEU v1.7.3]
**Problem:** Während `defaultWage` auf 50 balanciert war, standen 13 andere Lohnkonstanten fälschlicherweise noch auf 150 und wurden bei Resets fälschlich zurückgesetzt.
**Lösung:** Alle 13 Konstanten (z. B. `militaryTrainingWagePerDay`) in `EconConfig` auf 50 aligniert und `resetLaborDefaults()` entsprechend korrigiert.

### 21. battleThreat SEAM-Log [NEU v1.7.3]
**Problem:** In `EconSnapshot` gab es einen stummen Catch bei `STATS.BATTLE()`, was der Garantie "keine stummen Crashes" widersprach.
**Lösung:** Try-Catch instrumentiert mit `EventLog.log("BATTLE_STATS_ERROR", ...)`.

---

## Save/Load System

### Version
- Aktuelle Version: **33** (Wallets / EconomySim chunked format)
- Backward compatible: **19**
- Neue Felder müssen version-gated werden
- Neue Subsysteme bekommen eigenes `TAG_*` im chunked save/load

### Speicherformat
```java
file.l(long)      // Long speichern
file.i(int)       // Int speichern
file.bool(boolean) // Boolean speichern
```
**Achtung:** Reihenfolge in `save()` und `load()` muss IDENTISCH sein!

---

## Quellen-Verifikation

| Datei | Verifiziert |
|-------|-------------|
| EconomySim.java | ✅ |
| FlowMeter.java | ✅ |
| WealthStats.java | ✅ |
| FirmLedger.java | ✅ |
| Fiscal.java | ✅ |
| Taxes.java | ✅ |
| LaborMarket.java | ✅ |
| Wallets.java | ✅ |
| WarehouseMarket.java | ✅ |
| EconomyWindow.java | ✅ |
| EconConfig.java | ✅ |
| GiniConsequences.java | ✅ |
| EconIndicators.java | ✅ |
| EconProgression.java | ✅ |
| EventLog.java | ✅ |
| DiagnosticExporter.java | ✅ |
| TreasuryCrisis.java | ✅ |
| VanillaAIAdapter.java | ✅ |
| VanillaTransportAdapter.java | ✅ |
| VanillaWarehouseAdapter.java | ✅ |
| VanillaBoostingAdapter.java | ✅ |
| VanillaDiplomacyAdapter.java | ✅ |

---

## Verzeichnisstruktur der Dokumentation

```
<repo-root>/docs/
├── ARCHITECTURE.md                          # Diese Datei — System-Architektur (v0.1.0)
├── API_REFERENCE.md                         # Verifizierte Vanilla-APIs + Runtime-Verifikation (V71.44)
├── CHANGELOG.md                             # Versions-Historie (v0.0.1–v0.1.0)
├── ROADMAP.md                               # Entwicklungs-Roadmap
├── ICON_INVENTORY.md                        # Vanilla-Icon-Referenz
├── PHASE4_ADAPTER_PLAN.md                   # Phase 4 Bauplan ✅ ABGESCHLOSSEN
├── COVERAGE_AUDIT.md                        # Audit-Ergebnisse
├── archive/                                 # Eingefrorene Snapshots (siehe [archive/README.md](archive/README.md))
│   ├── HISTORICAL_BUGFIX_LOG.md             # Frühere Bugfixes
│   ├── HISTORICAL_FULLSCAN.md               # Früherer Fullscan-Report
│   ├── HISTORICAL_ORIGINAL_README.md        # Original-Mod-Readme
│   ├── HISTORICAL_ORIGINAL_INFO.txt         # Original-Mod-Info
│   ├── HISTORICAL_ORIGINAL_JAR.txt          # Original-JAR-Listing
│   └── HISTORICAL_SEMANTIC_DIFF.md          # Semantischer Diff (109KB — historisch)
└── reports/
    ├── COVERAGE_AUDIT_FINAL_2026-07-23.md   # Audit-Abschlussbericht
    ├── FULLSCAN_CLEANUP_2026-07-23.md       # Fullscan Cleanup-Report
    └── GUI_VS_MOD_GAP_ANALYSIS.md           # GUI vs. Mod Gap-Analyse
```

> **Hinweis:** `VANILLA_VERIFICATION.md`, `ECONOMY_API_REFERENCE.md`, `ADVISOR_VISUALIZATION_CONCEPT.md`
> und `04_roadmap.md` wurden im Juli 2026 in andere Dateien integriert oder umbenannt.
> `docs/reports/` enthält nur aktuelle Reports — historische Dateien sind unter
> [docs/archive/](archive/README.md) (Konsolidierungs-Schritt 2, 2026-07-23).