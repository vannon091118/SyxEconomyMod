# ⚠ DEPRECATED — Redirect zu `CHANGELOG.md` (Root)

> **Diese Datei ist eine Redirect-Notice.** Der kanonische Changelog befindet sich im
> Repo-Root unter [`/CHANGELOG.md`](../CHANGELOG.md). Dieser Eintrag hier ist veraltet
> (er enthielt z. B. eine falsche Behauptung über TreasuryCrisis-Stufen) und wird nicht
> mehr gepflegt.
>
> **Siehe auch:** [docs/reports/TRUTH_REPORT.md](reports/TRUTH_REPORT.md) für den vollständigen
> Truth-Audit vom 2026-07-23, der die Abweichungen dokumentiert.

---

## Frühere Version (UNWIDERRUFEN — bitte ignorieren)

**Hinweis:** Die nachfolgenden Einträge wurden am 2026-07-23 als Duplikat des Root-CHANGELOG
identifiziert. Sie enthielten veraltete Angaben (3-tier TreasuryCrisis statt 5 Stufen +
Hard Floor, Datei-Anzahl 108 statt 112, CSV-Spalten teilweise 31 statt 32). Die korrekte
Quelle ist `../CHANGELOG.md`.

Historischer Root-Inhalt v0.1.0–v0.0.1 (zur Nachvollziehbarkeit):

---

## v0.1.0 — Phase 4: Adapter-Architektur & Crash-Härtung (23. Juli 2026)

**Versionierung auf 0.1.0 zurückgesetzt** (0.0.1+ SemVer, kein öffentliches Repo).

### Neu

- **Adapter-Architektur (Phase 4):** 5 Interfaces (`ISyxAI`, `ISyxTransport`, `ISyxWarehouse`, `ISyxBoosting`, `ISyxDiplomacy`) + 12 Implementierungen (8 Vanilla + 4 Fallback). Alle Reflection-Zugriffe gekapselt. Forward-kompatibel bei Engine-API-Änderungen.
- **DiagnosticExporter:** 3 CSV-Dateien pro Spieltag (Macro 32 Spalten, Resources, Firms). Rebalance-Dashboard (Python/Jupyter, 5 Plots: Scarcity-Heatmap, Macro-Trend-Stacked, Anker-vs-Markt-Preis-Drift, Gini-vs-Treasury, Firm-Profitability).
- **TreasuryCrisis:** 3-stufige Krisenmechanik (−100K/−500K/−1M). War vorher nicht existent (0 Treffer für treasuryFloor/debtCeiling im Codebase).
- **Debug-Tab:** Live-Snapshot der CSV-Export-Werte + "Ordner öffnen"-Button im Wirtschaftsfenster.
- **Build-Gate-System:** 3 Pre-Compile-Gates (Code Audit, Version↔Changelog, Adapter↔Engine-Signaturen). Maven Antrun-Plugin in validate-Phase.
- **Rebalance-Dashboard:** `tools/rebalance_plots.py` + `tools/rebalance_dashboard.ipynb` (pandas+matplotlib).
- **Reflection-Benchmark:** `AdapterReflectionBenchmark.java` — misst Constructor-Time, Runtime Reflection, MethodHandle/VarHandle, JIT-Warmup.
- **MethodHandle-optimierte Adapter:** `VanillaTransportAdapterMH`, `VanillaDiplomacyAdapterMH`, `VanillaWarehouseAdapterMH` via `VarHandle`/`MethodHandle`. Toggle: `EconConfig.useMethodHandleAdapters`.
- **Hard-Threshold-Alert-Layer:** `[REBALANCE]`-EventLog-Einträge bei Gini>0.40 oder Audit-Delta>1000.

### Behoben

- **Showstopper: ClassLoader-Bug** (`VanillaAIAdapter`, `VanillaTransportAdapter`, `VanillaTransportAdapterMH`): `Class.forName(String)` nutzte Mod-ClassLoader → package-private Spiel-Klassen unsichtbar → alle AI-Plan-Erkennung tot (FoodPlanController, ConstructionHoardController, OddjobMarket, PurchasePlanController). Fix: `Class.forName(name, true, GAME_CL)` mit Game-ClassLoader + Null-Guard.
- **openCsvFolder-Crash:** `Desktop.open()` auf nicht existierendes Verzeichnis → `IllegalArgumentException` (RuntimeException) riss Render-Loop. Fix: `Files.createDirectories()` vor `open()`, alle 3 Catch-Blöcke auf `IOException | RuntimeException` erweitert.
- **catch-Tightening (10 Sites):** `catch(Throwable)`→`catch(RuntimeException)` in EconProgression, DiagnosticExporter, EconSnapshot, FurnishingAutomation, AccessAutomation. `catch(Exception)`→`catch(RuntimeException)` in LocalPrices (2×), CitizenClass (3×).
- **code-audit.sh:** Bash-Integer-Errors gefixt (`grep -c` + `tr -d` → `wc -l | awk`). Benchmark-Package von Checks ausgeschlossen. InterruptedException-Check korrigiert.
- **build-gate.sh:** Double-Skip gefixt (if/elif statt zwei if-Blöcke). Gate 3 PASS bei fehlendem JAR (zählt Adapter-Java-Dateien).
- **Early-Game-Inflation:** `initialWallet=5000` × 200 Bürger = 1M Scheinwirtschaft am Tag 0. Fix: Stage-gated Wallet (SUBSISTENZ→200, HANDEL→500, INDUSTRIE→2000, WOHLSTAND→5000). `seed_money`-CSV-Spalte für Transparenz. Immigrant-Wallet ebenfalls stage-gated (1/5 von initial, min 50).

### Geändert

- **pom.xml:** Version 1.7.3→0.1.0. Changelog auf 10 Einträge gekappt. `mod.version.history` aus git tags. Maven Compiler Strict Mode (`-Xlint:all,-options`). Build-Gate via maven-antrun-plugin.
- **README.md:** Komplett neu geschrieben — v0.1.0, Adapter-Architektur, DiagnosticExporter, TreasuryCrisis, Debug-Tab, Build-Gates.
- **CHANGELOG.md:** Release-Einträge auf diese Datei beschränkt. Frühere v1.x-Einträge archiviert.
- **_Info.txt:** `VERSION_HISTORY`-Feld hinzugefügt.
- **`EconomyWindow.java`:** Debug-Tab (`renderDebug`, `debugInfoLine`, `openCsvFolder` mit defensiven Guards).
- **`EconomySim.java`:** TreasuryCrisis-Integration NACH Game-State-Guards.
- **`EconConfig.java`:** `effectiveInitialWallet()`, `effectiveImmigrantWallet()`, 4 Staging-Konstanten, `useMethodHandleAdapters`.

---

## v0.0.9 — Bauarbeiter-Swarming & Starvation-Sichtbarkeit (23. Juli 2026)

### Behoben
- Bauarbeiter-Massenauflauf: `targetedResources`-Reservierungskarte in `ConstructionHoardController`/`ConstructionHoardPlan`
- Verhungerungstod unsichtbar: `EventLog.log("STARVATION", ...)` in `BrokeFoodPlan`
- O(1) ServiceCache-Lookup im ServicePlanController (Blueprint→Service-Cache)
- 13 Lohnkonstanten auf 50 aligniert
- battleThreat SEAM-Log (`EconSnapshot`)

---

## v0.0.8 — Audit-Fixes & Advisor-Meilensteine (23. Juli 2026)

### Behoben
- STOCKPILE.instancesSize()-Bug an 14 Stellen → `reliableStockpileCount()`
- 5K-Preis-Clamp: `priceAbsoluteMax` 5000→50000
- EventLog Thread-Safety: `synchronized(recentEvents)`
- ServicePlanController GC-Druck: persistentes HashSet
- FoodPlanController + PurchasePlanController: Round-Robin-Sharding
- Panel-Klick-Bug: `updateMouse()` via `CORE.getInput().getMouse().getCoo()`

### Neu
- Advisor Meilenstein-Anzeige (9 Milestones)
- LaborMarket-Prioritäten-UI

---

## v0.0.7 — PovertyPressure, OddjobAutomation, WarehouseAutomation (23. Juli 2026)

### Neu
- `PovertyPressure.java`: Happiness-Malus für Arbeitslose ohne Vermögen
- `OddjobAutomation.java`: Saisonale Tagelöhner-Lohn-Anpassung
- `WarehouseAutomation.java`: Saisonale Lager-Preis-Anpassung

### Behoben
- 25K-Preis-Clamp (Infinity-Explosion in `scarcityMultiplier`)
- Staatsjobs 0/13: `ensureHiringBootstrap()` komplett neu geschrieben

---

## v0.0.6 — Gini→Loyalty, Stufen-Freischaltung, Trend-Konsequenzen (23. Juli 2026)

### Neu
- `GiniConsequences`: Gini→Loyalty via `BOOSTABLES.BEHAVIOUR().LOYALTY`
- INDUSTRIE-Stufe: 5-Stufen-System (Subsistenz→Handel→Industrie→Wohlstand→Imperium)
- Feature-Freischaltung in `onStageAdvance()`: Privatisierung ab WOHLSTAND
- Trend-Erkennung mit echten Konsequenzen: `doleHeadcap`-Kürzung bei sinkender Staatskasse
- Package-Private Bridge: `LaborMarketAccess`
- CitizenClass-Panel + Firmenbesitz-Panel

---

## v0.0.5 — Property-Markt: Hauskauf, Firmen-Anteile, Dividenden (Juli 2026)

### Neu
- `PropertyLedger.java`: Hauskauf, Firmenanteile, Dividenden, Waisen-Reclamation
- `CitizenClass.java`: Dynamische Klassen basierend auf Vermögen/Immobilienbesitz

---

## v0.0.4 — Scarcity→Price→Priority-Kopplung (Juli 2026)

### Neu
- `ScarcitySignal.java`: Ressourcen-Engpass-Erkennung
- Knappheit→Preis-Boost via `FlowPrices`
- Knappheit→Arbeitspriorität via `LaborMarket`

---

## v0.0.3 — Rounding-Drift-Fix, Audit-Komplettierung, Housing-Miete (Juli 2026)

### Neu
- Chunked Save/Load v33
- Housing-Miete im Audit getrackt
- `roundingDrift`-Absorption (Threshold 20 Denari)

---

## v0.0.2 — Chunked save/load, zweistufige UI, Kausale Wirtschaft (Juli 2026)

### Neu
- Bau-Nachfrage aus Vanilla-Abhebungen → fließt in Preise ein
- Gewinn-getriebene Arbeitspriorität
- 3-Menü-Layout: Übersicht, Wirtschaft, Staat & Soziales

---

## v0.0.1 — Berater-Tab, Erstveröffentlichung (Juli 2026)

### Neu
- Fork von TiredGirl4's Economy Mod
- Package-Rename: `tiredgirl4.economy` → `vannon.syx.economy.core`
- Deutsche Lokalisierung (12 Tabs)
- Maven-Build (`mvn clean install`)

---

## Build-Anleitung

```bash
mvn clean install
cp target/SyxEconomyMod.jar ~/.local/share/songsofsyx/mods/SyxEconomyMod/V71/script/
```
