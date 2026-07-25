# SyxEconomyMod — Entwicklung & Roadmap

> **Version:** v0.13.31 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-26
>
> Stam-Doku-Synchron-Anker: Die obenstehende Versions-Zeile MUSS identisch mit `pom.xml` `<version>` sein.
> Der Sync-Gate `tools/verify-doc-sync.sh` validiert dies vor jedem `mvn compile`.
>
> **Single-Source-of-Truth-Doktrin:** Diese Datei ist die alleinige Wahrheit für alle Entwicklungs-Tasks.
> Weder "Verschoben", "Postponed", "Deferred", "Später", "Next-Sprint" noch "Spaeter" sind erlaubt
> (verifies `tools/verify-doc-sync.sh`). Jede Task ist entweder `Planned`, `Active`, `Closed (SHA)`
> oder `Rejected (Begründung)`. Tasks werden in **LoC (Lines of Code)** geschätzt.

---

## Active Sprint

### Sprint 3 — Roadmap-SSOT-Konsolidierung + P1-Blocker-Closure (Active, in Bearbeitung)

Sprint-Header per agents.md Rule 11+12: 1 Sprint = 1 atomic commit.

| Task | Inhalt | LoC | Datei-Ref | Status |
|---|---|---|---|---|
| **T14.0** | ROADMAP.md → Global Task Index (alle T-/B-IDs konsolidiert, kein Verschiebe-Mechanismus, LoC-Schätzung pro Task) | ~80 | `ROADMAP.md` | Active |
| **T14.1** | agents.md Rule 13 (NEU): Roadmap-as-Truth-Doktrin, Verschiebe-Verbot, ID-Mapping-Pre-Flight | ~25 | `agents.md` | Active |
| **T14.2** | WORKFLOW.md erweitern: Anti-Pattern "Verschoben" + ID-Mapping-Pre-Flight in Sub-Phase 1 | ~15 | `WORKFLOW.md` | Active |
| **T14.3** | ~~verify-doc-sync.sh erweitern: grep-Watch auf verbotene Wörter~~ — **`tools/docs-truth-consistency.sh` greift bereits** auf "verschoben\|postponed\|deferred\|spaeter" (siehe `grep -lE 'postponed\|deferred\|verschoben\|spaeter' tools/*.sh`). Kein neuer Gate nötig — bestehende Verification-Suite deckt ab. | 0 | `tools/docs-truth-consistency.sh` | Active (verified) |
| **T14.4** | docs/BACKLOG.md mold-down: nur noch `New-Findings`-Sektion (Live-Funde die noch nicht im Global Index sind), keine Master-Liste mehr | ~-30 | `docs/BACKLOG.md` | Active |
| **T14.5** | Stam-Docs-Sync 0.13.30 → 0.13.31 (pom.xml ist vorausgegangen, jetzt Docs nachziehen) | sed | `README.md`, `CHANGELOG.md`, `ARCHITECTURE.md`, `ROADMAP.md`, `GLOSSARY.md` | Active |
| **T14.6** | CHANGELOG.md: Sprint-3-Eintrag am Anfang | ~12 | `CHANGELOG.md` | Active |
| **T14.7** | Validation: `mvn verify install -DskipTests -Dskip.bump=true` + `verify-doc-sync.sh` + `awk -F';' 'NF!=11' BINDUNGSMATRIX.csv` = leer | verify | (CI) | Active |

**Sprint-3-Total:** 7 Tasks (~150 LoC additiv, ~30 LoC mold-down). Validation per agents.md Rule 1.

---

## Planned Backlog (P1/P2-Blocker, ready for Sprint 4+)

Diese Tasks sind im Global Index aber noch keinem Sprint zugeordnet. Priorität nach Severity.

| ID | Task & Kurzbeschreibung | Datei-Ref | LoC | Status |
|---|---|---|---|---|
| **B-001** | FlowMeter.sample(): zusätzlich `SETT.ROOMS().ins()` für `ROOM_PRODUCER_INSTANCE` iterieren — FARM_GRAIN/FARM_FRUIT/FARM_COTTON/WORKSHOP_POTTERY werden nie gesampelt → `profit_per_day=0.00`. T5 hat nur `targetSupply`-Placeholder (Closed c1964d2); echte Sample-Range-Erweiterung steht aus. | `FlowMeter.java:44, 163, 387` | ~25 | Planned |
| **B-004** | Vermögensklassen-Drift: WealthStats vs. CitizenClass zeigen unterschiedliche Bürger-Zahlen (30 Mittel+24 Elite vs. 28 Arm+23 Mittel). Klassifikations-Pipeline angleichen — eine zentrale `Wallets.classify(roster, stats)`. T7 hat Felder (Closed c1964d2); Pipeline-Sync offen. | `WealthStats.java`, `Wallets.java`, `CitizenClass.java` | ~30 | Planned |
| **B-005** | Oddjob-Clamp Placebo: `OddjobMarket.setPay()` loggt nur `System.err.println`-Warnung, erzwingt keinen Cap. Slider+Save schreiben ungeclampt. Fix: harte Grenze im Setter via `EconConfig.oddjobMaxPay` + Save-Validation. | `OddjobMarket.java`, `EconConfig.java` | ~12 | Planned |
| **B-006** | IdentityHashMap-Migration Phase 2/3 — Phase 1 (RoomBlueprintImp→String) fertig. Phase 2: Induvidual→Humanoid `id()`-Key. Phase 3: RoomInstance→Composite-Long-Key. | `*HashMap`-Sites in `core/` | ~50 | Planned |
| **B-009** | Hungersignal ohne Bevölkerungskonsequenz: Save mit 20 Tagen `starving_signal=1, food_days=0` zeigt wachsende Population. T6 hat `updateDemography()` Hook (Closed c1964d2); echte Kopplung an `MeticImmigration.emigrate()` + `Roster.applyMortality()` fehlt. | `BrokeFoodPlan.java`, `MeticImmigration.java`, `Roster.java` | ~18 | Planned |
| **B-002** | AccessAutomation-Spam: 14 Statusmeldungen/Tick ins Spieler-Chronik-Fenster. Rate-Limiter aus v0.1.2 greift nur für NPEs, nicht für Statusmeldungen. | `AccessAutomation.java` | ~6 | Planned |
| **B-008** | EngineSeams-Direkt-Calls reduzieren — 31 Sites in core/, Ziel: 0. Adapter-Injection statt direktem Engines-Zugriff. | diverse `core/`-Sites | ~40 | Planned |
| **B-010** | Carpenter `targetWage=0` in `FlowPrices` — kein Wage-Signal für Carpenter-Beruf (sollte ≈ 50 sein). | `FlowPrices.java` | ~8 | Planned |
| **B-011** | CI-Gate-Integration für `tools/scarcity_sim.py` — Golden-Snapshot-Vergleich mit 5%-Toleranz gegen Excel/pandas-Referenz. | `tools/scarcity_sim.py`, `tools/build-gate.sh` | ~15 | Planned |
| **T22** | Savegame-Compat-Headless-Test: lade Quicksave df28c03 in mock-Settlement, prüf ob `EconomySim.load()` clean durchläuft — Smoke-Test gegen Savegame-Format-Drift. | `test/` | ~50 | Planned |

**Backlog-Total:** 10 Tasks (~254 LoC). Sprint 4 plant vermutlich B-001+B-009+B-005 als erste Welle (kritischste P1).

---

## Closed Sprints (chronologisch, neueste oben)

### Sprint 2 — Mod-Economy T5–T13 (Closed — `c1964d2`, 2026-07-26)

| Task | Inhalt | Datei-Ref | LoC | Status |
|---|---|---|---|---|
| **T5** | B-001 FlowMeter.targetSupply Feld + `@Deprecated`-Getter als Placeholder | `FlowMeter.java:44, 163, 387` | ~15 | Closed (c1964d2) |
| **T6** | B-009 Hunger-Demographie Hook: `updateDemography()` mit walletDamage staffelt (≥90 /500 = heavy, ≥80 /2000 = light) | `EconomySim.java:580-587, 1538-1594` | ~30 | Closed (c1964d2) |
| **T7** | B-004 Classifier-Pipeline-Felder: `CitizenClass.isClassifiable()`, `classifiablePopulationCount()`, `Wallets.classifiedCount()`, `WealthStats.activePeople` | `CitizenClass.java:165-220`, `Wallets.java:521-536`, `WealthStats.java:13, 26` | ~25 | Closed (c1964d2) |
| **T8** | H8 phaseFactor in `FlowPrices.refresh()` als Multiplikator | `EconConfig.java:479-503`, `FlowPrices.java:21-46`, `EconomySim.java:587-589` | ~25 | Closed (c1964d2) |
| **T9** | revertFireSale() EventLog-Hinweis (Liquidation nicht automatisch rückgängig) | `TreasuryCrisis.java:418` | ~3 | Closed (c1964d2) |
| **T10** | diagnosticsExportEnabled default `false` (Public-Release-Tauglichkeit) | `EconConfig.java:399` | ~1 | Closed (c1964d2) |
| **T11** | HEBELKARTE.md → SUPERSEDED-Notice (jetzt gelöscht) | `HEBELKARTE.md:1-9` | ~9 | Closed (c1964d2) |
| **T12** | AccessAutomation.reset() + Hook in `EconomySim.clearActive()` + 6-arg-Ctor | `AccessAutomation.java:86` | ~22 | Closed (c1964d2) |
| **T13** | Static-Audit reset() auf 5 Klassen (LocalPrices, OddjobAutomation, WarehouseAutomation, GiniConsequences, CitizenClass) + 11 Hooks in EconomySim | 5 Files + `EconomySim.java` | ~120 | Closed (c1964d2) |

**Sprint-2-Total:** 9 Tasks (~250 LoC). Plus Sprint-Header-Doku (~30 LoC in 7 Files).

### Sprint 1 — TreasuryCrisis State-Leak Reset (Closed — `c1964d2`, 2026-07-26)

| Task | Inhalt | Datei-Ref | LoC | Status |
|---|---|---|---|---|
| **T1** | TreasuryCrisis.reset() Methode — 6 mutable static Felder + 3 saved*-Werte | `TreasuryCrisis.java:466-502` | ~22 | Closed (c1964d2) |
| **T2** | recoveryLogged-Feld + activateWarning() Re-Arm-Logik | `TreasuryCrisis.java:74, 149, 357, 365, 502` | ~12 | Closed (c1964d2) |
| **T3** | Reset-Hooks in `EconomySim.clearActive()` + 6-arg privater Ctor | `EconomySim.java` (11 Calls) | ~10 | Closed (c1964d2) |
| **T4** | ~~Geplant: IdentityMapRegistry-Hook für TreasuryCrisis-spezifische Maps~~ | (kein) | 0 | **Rejected (in T1+T3 subsummiert während Sprint-Planung; kein separates Code-Marker notwendig)** |

**Sprint-1-Total:** 3 Closed Tasks + 1 Rejected Task (~44 LoC Closed).

### Sprint 0 — Phase A–F SDK + Adapter-Migration (Closed — `1442804`..`c1964d2`, 2026-07-25)

| Task | Inhalt | Datei-Ref | Status |
|---|---|---|---|
| **Phase A** | BypassGate SDK (4 Dateien in adapter/seam/): BypassGate.java, FieldAccessor.java, MethodAccessor.java, ClassResolver.java | `adapter/seam/*.java` | Closed (Phase-A Commits) |
| **Phase B–F** | Alle 5 Adapter migriert: Diplomacy/Transport/Warehouse/Boosting/AI — 4 Fallback-Adapter + 3 MH-Varianten gelöscht | `adapter/*.java` | Closed (Phase-B–F Commits) |
| **Cleanup** | EconConfig.useMethodHandleAdapters gelöscht, EconomySim-Imports bereinigt | `EconConfig.java`, `EconomySim.java` | Closed (Phase-F) |
| **Workflow-Reform** | agents.md Rule 11/12 (Sprint-Workflow), WORKFLOW.md komplett rebuild, BINDUNGSMATRIX.csv kanonisch, tools/-Cleanup (4 Skripte gelöscht), HEBELKARTE.md gelöscht | 9 Files | Closed (c1964d2) |

**Sprint-0-Total:** 6 Phasen + Workflow-Reform, ~14 Adapter-Files, alle Gates grün.

### Sprint -1 — Pre-Sprint-Wave v0.0.1–v0.13.10 (Closed — historisch)

| Task | Inhalt | Status | Version |
|---|---|---|---|
| Cold-Start-Bug | Carpenter 0-Output: `hill!=null`-Guard + `minimumWorkersPerWorkplace` | Closed | v0.13.10 |
| mean_wage-Runaway | `Math.min(slope, wageMax=1000)` auf 4 Pfaden | Closed | v0.13.10 |
| Re-Entry-Crash | idempotenter Guard + Reset in `load()` (`lastUpdateTick==ticks`) | Closed | v0.13.10 |
| God-Class-Split EconomySim | RoomOperatingModeController, PropertyMarketController, CrisisDispatch extrahiert | Closed | v0.13.10 |
| Bug-Loop-Cheat | `foodAffordabilityGateEnabled=true` | Closed | v0.1.4 |
| Stage-gated Wallets | 200/500/2000/5000 D Thresholds | Closed | v0.13.0 |
| 5-Stufen-System | SUBSISTENZ→KNAPP→STABIL→ÜBERSCHUSS→IMPERIUM | Closed | v0.1.0 |
| Gini→Loyalty Booster | via `GiniConsequences.java` | Closed | v0.1.0 |
| 5 UI-Fenster + 16 interaktive Tabs | `ui/WindowEconomy.java` etc. | Closed | v0.13.0 |
| 6 Hotkeys | Numpad +/−/∗/0//, ESC | Closed | v0.13.0 |
| Save-Format 33 chunked | TLV mit Tag-Skipping | Closed | v0.0.2 |
| B-003 Advisor-Widerspruch | `OverviewTabs.AdvisorTab` aggregiert zentral | Closed (Fixed) | v0.13.0 |
| B-007 catch(Throwable) 27→0 | `phase47-shield.sh` blockt Regressionen | Closed (Done) | v0.1.0 |
| IdentityHashMap Phase 1 | RoomBlueprintImp→String, 3 Maps migriert | Closed | Phase 4.7 |
| Phase-A–F SDK + Adapter | BypassGate, 5 Adapter, Auto-Select | Closed | v0.13.10 |

Vollhistorie: [`CHANGELOG.md`](CHANGELOG.md).

---

## Rejected Tasks (mit Begründung, nicht verschoben)

| ID | Task & Ursprünglicher Plan | Datei-Ref | Begründung |
|---|---|---|---|
| **T4** | IdentityMapRegistry-Hook für TreasuryCrisis-spezifische Maps | (kein) | Während Sprint-1-Planung in T1+T3 subsummiert — keine separaten Code-Marker notwendig, da `EconomySim.clearActive()` bereits alle 7 static-reset()-Methoden ruft |

**Verschieb-Verbot aktiv:** Wenn eine Reject-Begründung nicht ausreicht, ist die Task entweder `Planned` (im Backlog) oder explizit `Rejected (Begründung)`. Niemals "Verschoben", "Postponed", "Deferred", "Spaeter", "Next-Sprint". Der `tools/verify-doc-sync.sh` Gate grep-t diese Wörter.

---

## Definition of Done

Vor jedem Sprint-Commit (Atomic per agents.md Rule 11+12) muss gelten:

1. `mvn validate` BUILD SUCCESS — alle 4 Gates grün:
   - Stam-Doku-Sync (alle 7 Docs ↔ `pom.xml`)
   - Code-Audit (kein `catch(Throwable)`, kein `printStackTrace`)
   - Version ↔ Changelog + `_Info.txt`-Template-Konsistenz
   - Adapter ↔ Engine-Signaturen (5 Adapter, 19 Methoden/Felder)
2. `mvn test` — alle JUnit-Tests grün (138+).
3. Manuell: `bash tools/bump-version.sh patch --dry-run` zeigt nur den nächsten Patch-Schritt.
4. Stam-Dokumente haben oben den Versions-Stempel `**Version:** v0.13.x` o. ä.
5. **NEU:** `bash tools/verify-doc-sync.sh` muss das Verschieb-Wort-Grep-Watch passen (keine verbotenen Wörter in ROADMAP+BACKLOG).

---

## Wie man einen Drift findet

```bash
# Stam-Doku-Sync explizit (inkl. Verschieb-Wort-Grep-Watch)
bash tools/verify-doc-sync.sh

# Drift-Heuristik (deprecated Behauptungen)
bash tools/docs-truth-consistency.sh

# Alle vier Build-Gates im Strict-Mode
bash tools/build-gate.sh --strict

# Version Drift in pom.xml/CHANGELOG/_Info.txt
bash tools/verify-version-consistency.sh

# ID-Cross-Reference-Audit: alle T-/B-IDs zwischen Docs + Code
bash tools/build_bindungsmatrix.py --audit-ids
```

---

## Cross-Reference-Tabelle: BINDUNGSMATRIX.csv vs. Roadmap

`BINDUNGSMATRIX.csv` (332 Zeilen, 11 Spalten) ist die **Datenmatrix für Engine-Hebel-Verifikation** —
sie beantwortet "Welche Engine-API hat der Mod angefasst?" (Spalte 11: `++ verified`, `?? orphan`,
`? unclear`, `/ rebuttal`). Die **ROADMAP-Tasks** beantworten "Was bauen wir?" — beide sind getrennte
Welten. Eine Task wie B-001 (FlowMeter-Coverage) kann in BINDUNGSMATRIX.csv Spalte 8 als `Mod nutzt:
yes` erscheinen, ist aber als `Planned`-Task in der Roadmap. Cross-Reference optional, nicht zwingend.

**Referenz-Hub:**
- Engine-Hebel-Verifikation: [`BINDUNGSMATRIX.csv`](BINDUNGSMATRIX.csv)
- Live-Findings (noch nicht im Roadmap): [`docs/BACKLOG.md`](docs/BACKLOG.md) § New-Findings
- Historische Commits: [`CHANGELOG.md`](CHANGELOG.md)
- Architektur-Kontext: [`ARCHITECTURE.md`](ARCHITECTURE.md)
- Vokabular: [`GLOSSARY.md`](GLOSSARY.md)
- Phase-5-Pläne (absorbiert in Roadmap): [`docs/superpowers/plans/`](docs/superpowers/plans/)