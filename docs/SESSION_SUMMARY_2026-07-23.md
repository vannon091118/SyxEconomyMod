# Session-Änderungen — 23. Juli 2026

> Komplette Zusammenfassung aller Änderungen, kategorisiert nach Zweck.

---

## 1. VORBEREITUNG — Architektur & Zukunftssicherheit

### 1.1 Phase 4: Adapter-Architektur (17 Dateien, ~500 LOC)

**Wozu:** Alle 5 Reflection/String-Matching-Stellen auf Vanilla-Klassen hinter Interfaces gekapselt. Bei einem Spiel-Update müssen nur 5 Adapter geprüft werden — nicht mehr Code quer durchs ganze Projekt.

| Interface | Vanilla-Adapter | Fallback | MH-optimiert | Gekapseltes Ziel |
|-----------|----------------|----------|-------------|------------------|
| `ISyxAI` | `VanillaAIAdapter` | — | — | 6 package-private AI-Plan-Klassen |
| `ISyxTransport` | `VanillaTransportAdapter` | `FallbackTransportAdapter` | `VanillaTransportAdapterMH` | `TransportInstance.distance` (float) |
| `ISyxWarehouse` | `VanillaWarehouseAdapter` | `FallbackWarehouseAdapter` | `VanillaWarehouseAdapterMH` | `StockpileInstance.storingSet(boolean)` |
| `ISyxBoosting` | `VanillaBoostingAdapter` | `FallbackBoostingAdapter` | — | `BOOSTABLES.CIVICS().GOV` |
| `ISyxDiplomacy` | `VanillaDiplomacyAdapter` | `FallbackDiplomacyAdapter` | `VanillaDiplomacyAdapterMH` | `DipWarPlayer` (5 Felder) |

**Dateien:** `src/vannon/syx/economy/adapter/` — 5 Interfaces + 8 Vanilla + 4 Fallback = 17 Dateien

### 1.2 ISyxAI + ISyxDiplomacy gemäß PHASE4_ADAPTER_PLAN Schritte 5.4 + 5.5

**Wozu:** `EconProgression` GOV-Reflection und `DebtDiplomacyBuffer` 5× DipWarPlayer-Felder jetzt via Adapter statt direktem Reflection.

### 1.3 Step 5.6 Cleanup: EngineSeams.isFoodPlan()/isOddjobbing()-Aufrufer migriert

**Wozu:** Alle direkten Aufrufer auf `sim.aiAdapter()` umgestellt. `@Deprecated`-Methoden aus EngineSeams gelöscht. Keine String-Matching-Stellen mehr außerhalb adapter/.

### 1.4 Reflection-Benchmark (`AdapterReflectionBenchmark.java`)

**Wozu:** Misst Constructor-Time, Runtime-Reflection, MethodHandle/VarHandle, JIT-Warmup. Entscheidungsgrundlage für MH-Migration.

### 1.5 MethodHandle-optimierte Adapter (3 Dateien)

**Wozu:** `VanillaTransportAdapterMH`, `VanillaDiplomacyAdapterMH`, `VanillaWarehouseAdapterMH` via `VarHandle`/`MethodHandle`. Toggle: `EconConfig.useMethodHandleAdapters = false`.

### 1.6 DiagnosticExporter — 3 CSV-Dateien pro Spieltag

**Wozu:** Longitudinale Daten für Balancing-Entscheidungen. Vorher: alles Ad-hoc im Code.

| CSV | Spalten | Inhalt |
|-----|---------|--------|
| `rebalance_macro_<epoch>.csv` | 32 | Treasury, Gini, Löhne, Steuern, seed_money, audit_delta, … |
| `rebalance_resources_<epoch>.csv` | 10 | Pro Ressource: Preis, Bestand, Angebot, Nachfrage, Knappheit |
| `rebalance_firms_<epoch>.csv` | 7 | Pro Firma: Blueprint, Mitarbeiter, Income, Unpaid-Workers |

### 1.7 Rebalance-Dashboard (Python/Jupyter)

**Wozu:** 5 Plots für CSV-Analyse: Scarcity-Heatmap, Macro-Trend-Stacked, Anker-vs-Markt-Preis-Drift, Gini-vs-Treasury, Firm-Profitability.

**Dateien:** `tools/rebalance_plots.py`, `tools/rebalance_dashboard.ipynb`

### 1.8 pom.xml-Metadaten-Sync & Build-Gate-System

**Wozu:** Version auf 0.1.0 zurückgesetzt (0.0.1+ SemVer, kein öffentliches Repo). Changelog auf 10 Einträge gekappt. `mod.version.history` via git tags. 3 Pre-Compile-Gates.

| Gate | Prüfung |
|------|---------|
| 1 — Code Audit | Keine leeren catch-Blöcke, keine catch(Throwable) außerhalb adapter/benchmark |
| 2 — Version↔Changelog | pom.xml-Version konsistent mit letzter Changelog-Zeile |
| 3 — Adapter↔Engine | Adapter-Dateien gegen Source verifiziert |

**Dateien:** `tools/build-gate.sh`, `tools/code-audit.sh`, `tools/verify-version-consistency.sh`

---

## 2. HÄRTUNG — Crash-Fixes & Stabilität

### 2.1 Showstopper: Class.forName()-ClassLoader-Bug

**Problem:** `Class.forName(String)` nutzt Mod-ClassLoader → package-private Spiel-Klassen (`PlanOddjobber`, `F_SPlanEatery`, …) unsichtbar → **ALLE AI-Plan-Erkennung tot**. Betroffene Systeme: FoodPlanController, ConstructionHoardController, OddjobMarket, PurchasePlanController.

**Fix:** `Class.forName(name, true, GAME_CL)` mit Game-ClassLoader + Null-Guard für Bootstrap-ClassLoader-Edge-Case.

**Dateien:** `VanillaAIAdapter.java`, `VanillaTransportAdapter.java`, `VanillaTransportAdapterMH.java`

### 2.2 openCsvFolder-Crash (Render-Loop-reißend)

**Problem:** `Desktop.open()` auf nicht existierendes Verzeichnis → `IllegalArgumentException` (RuntimeException) lief durch `catch(IOException)`-Block → zerriss kompletten Render-Loop.

**Fix:** `Files.createDirectories()` vor `open()`. Alle 3 Catch-Blöcke auf `IOException | RuntimeException` erweitert. Null/Leer-Path-Guard am Methoden-Eingang.

**Datei:** `EconomyWindow.java`

### 2.3 catch-Tightening — 10 Sites von Throwable/Exception auf RuntimeException

**Problem:** `catch(Throwable)`/`catch(Exception)` verschluckten echte Fehler (OutOfMemory, NoClassDefFound, …) und taten so als wäre alles in Ordnung.

**Fix:** Alle 10 Sites auf `catch(RuntimeException)` eingegrenzt.

| Datei | Sites | Art |
|-------|-------|-----|
| `EconProgression.java` | 1 | catch(Throwable) → catch(RuntimeException) |
| `DiagnosticExporter.java` | 1 | catch(Throwable) → catch(RuntimeException) |
| `EconSnapshot.java` | 1 | catch(Throwable) → catch(RuntimeException) |
| `FurnishingAutomation.java` | 1 | catch(Throwable) → catch(RuntimeException) |
| `AccessAutomation.java` | 1 | catch(Throwable) → catch(RuntimeException) |
| `LocalPrices.java` | 2 | catch(Exception) → catch(RuntimeException) |
| `CitizenClass.java` | 3 | catch(Exception) → catch(RuntimeException) |

### 2.4 Build-Gate-Bugs behoben

**Problem:** "Bestanden: 2, Übersprungen: 1" — Gate 3 double-counted, Adapter-Check immer SKIP, Bash-Integer-Errors in code-audit.sh.

**Fix:** `build-gate.sh` komplett neu geschrieben (3 Gates, alle PASS, 0 Skips). `code-audit.sh`: Benchmark-Package ausgeschlossen, InterruptedException-Check korrigiert, `grep -c` → `wc -l | awk`.

### 2.5 TreasuryCrisis — initial 3-stufig, dann 5-stufig + Hard Floor (2× Rewrite)

**Wozu:** Vor dieser Session: **0 Treffer** für treasuryFloor/debtCeiling/bankrupt im gesamten Codebase. Staatskasse konnte auf −900M fallen ohne Konsequenz.

**Erste Version (3 Stufen):** −100K/−500K/−1M mit Steuererhöhung+Lohnkürzung.

**Zweite und finale Version (5 Stufen + Hard-Floor-Verhalten in Tier 5):** −5K/−50K/−250K/−1M/−5M mit erzwungener Liquidation, System-Deaktivierung, Hard Floor. Hard Floor ist KEIN 6. Tier — er ist Teil von Tier 5 (`isHardFloor() = activeTier >= 5`). Schwellen siehe `src/vannon/syx/economy/core/TreasuryCrisis.java`.

| Stufe | Schwelle | Maßnahmen |
|-------|----------|-----------|
| 1 — Warnung | ≤ −5K | Subventionen, Transport, Handouts, Auto-Tunes gestoppt |
| 2 — Sparprogramm | ≤ −50K | ALLE 15 Lohnkonstanten halbiert, Marktsteuer +15%, Dole-Headcap 50% |
| 3 — Zwangsverkauf | ≤ −250K | ALLE Staatslager liquidieren, Immobilienmarkt erzwungen |
| 4 — Staatsbankrott | ≤ −1M | Kopfsteuer=500, Marktsteuer=50%, Corvée/Dole/Schuldknechtschaft/Wages deaktiviert |
| 5 — Kollaps | ≤ −5M | ALLE 11 Systeme deaktiviert, LOYALTY −50%. HARD FLOOR |

**Datei:** `TreasuryCrisis.java`

### 2.6 Hard-Threshold-Alert-Layer

**Wozu:** Schreibt `[REBALANCE]`-Einträge ins EventLog bei Gini>0.40 oder Audit-Delta>1000 — In-Game-Warnungen statt nur Offline-CSV-Analyse.

**Datei:** `DiagnosticExporter.java`

### 2.7 Vanilla-Signatur-Verifikation

**Wozu:** Alle Reflection-Signaturen per `unzip -p` gegen `SongsOfSyx-sources.jar` verifiziert: `storingSet(boolean)`, `DipWarPlayer`-Felder, `TransportInstance.distance`-Feld.

---

## 3. FEATURE-ANPASSUNG — Balancing & neue Features

### 3.1 Stage-gated Initial Wallet (Early-Game-Inflation-Fix)

**Problem:** `initialWallet=5000` × 200 Bürger = 1.000.000 D Seed-Geld am Tag 0 — erschien als "1M BIP ohne Lager/Arbeiter, nur Tagelöhner".

**Fix:** Wallet skaliert mit Wirtschaftsstufe + `seed_money`-CSV-Spalte für Transparenz.

| Stufe | Vorher (200 Bürger) | Nachher |
|-------|---------------------|---------|
| SUBSISTENZ | 1.000.000 D | **40.000 D** |
| HANDEL | 1.000.000 D | 100.000 D |
| INDUSTRIE | 1.000.000 D | 400.000 D |
| WOHLSTAND | 1.000.000 D | 1.000.000 D |

**Dateien:** `EconConfig.java`, `Wallets.java`, `DiagnosticExporter.java`

### 3.2 WarehouseAutomation — vollständig proaktiv (Rewrite)

**Problem:** Nur `userActivated`-Ressourcen wurden getuned. Keine Nahrungs-Notreserve, kein Bau-Material-Antizipation, kein Budget-Check.

**Fix:** Vollautomatisch — erkennt Knappheit, Bau-Bedarf, Nahrungs-Puffer. Budget-aware.

| Feature | Vorher | Nachher |
|---------|--------|---------|
| Ressourcen-Erkennung | Nur manuell aktiviert | **Alle automatisch** |
| Kritische Knappheit | Ignoriert | **Notkauf auch ohne Budget** |
| Nahrungs-Puffer | Nicht existent | **3-Tage-Notfallpuffer** |
| Bau-Material | Nicht erkannt | **Auto-enable bei Bau-Projekten** |
| Budget-Check | Nicht existent | **Keine Käufe bei Treasury≤0** |
| Verkauf | Nur manuell | **Auto bei >9-Tage-Überbestand** |

**Datei:** `WarehouseAutomation.java`

### 3.3 Debug-Tab im Wirtschaftsfenster

**Wozu:** Live-Snapshot aller CSV-export-relevanten Werte + "CSV-Pfad öffnen"-Button.

**Datei:** `EconomyWindow.java`

### 3.4 FirmLedger — per-Firm-Income-Tracking

**Wozu:** `FirmFinancialSnapshot`-Record für DiagnosticExporter CSV — zeigt welche Firmen strukturell unprofitabel sind.

**Datei:** `FirmLedger.java`

---

## 4. METADATEN & DOKUMENTATION

### 4.1 pom.xml
- Version 1.7.3 → **0.1.0** (0.0.1+ SemVer)
- Changelog auf 10 Einträge gekappt
- `mod.version.history` via git tags
- Maven Compiler Strict Mode (`-Xlint:all,-options`)
- Build-Gate via `maven-antrun-plugin` in validate-Phase
- Benchmark-Package vom Shade-JAR ausgeschlossen

### 4.2 README.md
- Komplett neu: v0.1.0, Adapter-Architektur, DiagnosticExporter, TreasuryCrisis, Debug-Tab, Build-Gates, Wallet-Staging

### 4.3 CHANGELOG.md
- Neu geschrieben: v0.1.0-Top-Eintrag, 10 Release-Einträge (v0.0.1–v0.1.0)

### 4.4 ARCHITECTURE.md
- Schichtenmodell: Adapter-Layer als Schicht 2, Dateistruktur erweitert, 6 neue Design-Entscheidungen

### 4.5 PHASE4_ADAPTER_PLAN.md
- ✅ ABGESCHLOSSEN — alle 6 Schritte erledigt

### 4.6 _Info.txt
- `VERSION_HISTORY`-Feld hinzugefügt

---

## 5. KENNZAHLEN

| Metrik | Vorher | Nachher |
|--------|--------|---------|
| Java-Dateien | 84 | **112** (95 core/ + 17 adapter/) |
| Adapter-Dateien | 0 | **17** |
| Reflection-Stellen (direkt) | 5 | **0** (alle via Adapter) |
| Build-Gates | 0 | **3** (0 Failures, 0 Skips) |
| Treasury-Tiers | 0 | **5** (+ Hard Floor in Tier 5) |
| catch(Throwable) außerhalb adapter/ | 5 | **0** |
| catch(Exception) außerhalb adapter/ | 5 | **0** |
| CSV-Exporte | 0 | **3 pro Spieltag** |
| pom.xml-Version | 1.7.3 | **0.1.0** |
