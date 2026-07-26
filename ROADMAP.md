# SyxEconomyMod — Entwicklung & Roadmap

> **Version:** v0.13.40 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-26
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

### Sprint 7 — Adapter-Dispatcher + Schema-SSoT (Active, 2026-07-26)

Sprint-Theme: Zentraler Dispatcher macht alle 6 Adapter patchbar. 1 YAML-Schema statt
5 Field-String-Konstanten. Bei Engine-Update (V71→V72): 1 Diff in vanilla-schema.yaml
statt 5 Adapter-Dateien durchsuchen. NPC-Faktionen erstmals angebunden (Civil-Verhalten,
Job-Learning-Grundlage).

| Task | Inhalt | LoC | Datei-Ref | Status |
|---|---|---|---|---|
| **T19.0** | `tools/vanilla-schema.yaml` — 15 Klassen (~50 Felder), maschinenlesbare SSoT | ~150 | `tools/vanilla-schema.yaml` | Active |
| **T19.1** | `SchemaValidator.java` — pre-flight Class.forName + getDeclaredField-Prüfung | ~120 | `adapter/seam/SchemaValidator.java` | Active |
| **T19.2** | `AdapterDispatcher.java` — zentraler Builder für 6 Adapter, ersetzt 5 createXxxAdapter() | ~120 | `adapter/AdapterDispatcher.java` | Active |
| **T19.3** | `ISyxNpc.java` + `NpcFactionAdapter.java` — NPC-Preis/Resource-Zugriff via BypassGate | ~260 | `adapter/ISyxNpc.java`, `adapter/NpcFactionAdapter.java` | Active |
| **T19.4** | `EconomySim` entkoppelt — 5 createXxxAdapter() → 1 AdapterDispatcher.build() | ~20 | `core/EconomySim.java` | Active |
| **T19.5** | `build-gate.sh` Gate 7 (Schema-Präsenz-Check) + `audit-bytecode.sh` Whitelist-Erweiterungen | ~20 | `tools/build-gate.sh`, `tools/audit-bytecode.sh` | Active |
| **T19.6** | `EconomySim.debugAdapterStatus` + `debugSelfTest` um ISyxNpc erweitert | ~10 | `core/EconomySim.java` | Active |

**Sprint-7-Total:** 7 Tasks (~700 LoC additiv, davon ~670 LoC Java + ~170 LoC YAML).
Validation: `mvn verify install` 6/7 Gates grün, `mvn test` 296 Tests, 0 Fehler.

---

## Planned Backlog (P1/P2-Blocker, ready for Sprint 8+)

| ID | Task & Kurzbeschreibung | Datei-Ref | LoC | Status |
|---|---|---|---|---|
| **T-COV-9** | Mockito-Inject für engine-coupled Branches: Fiscal.update (22 Pfade), HousingMarket.collectRent/evict, LaborMarket.update (18 Pfade), AffordabilityGate unit-Pricing-Lookups, EconProgression.pollBuildings. Plus JaCoCo-Threshold-Anziehen auf line≥70%/branch≥60%. | test/ + pom.xml | ~600 | Planned |
| **B-001** | FlowMeter.sample(): zusätzlich `SETT.ROOMS().ins()` für `ROOM_PRODUCER_INSTANCE` iterieren — FARM_GRAIN/FARM_FRUIT/FARM_COTTON/WORKSHOP_POTTERY werden nie gesampelt → `profit_per_day=0.00`. | `FlowMeter.java:44, 163, 387` | ~25 | Planned |
| **B-004** | Vermögensklassen-Drift: WealthStats vs. CitizenClass zeigen unterschiedliche Bürger-Zahlen. Klassifikations-Pipeline angleichen — eine zentrale `Wallets.classify(roster, stats)`. | `WealthStats.java`, `Wallets.java`, `CitizenClass.java` | ~30 | Planned |
| **B-005** | Oddjob-Clamp Placebo: `OddjobMarket.setPay()` loggt nur System.err.println-Warnung, erzwingt keinen Cap. Fix: harte Grenze im Setter via `EconConfig.oddjobMaxPay` + Save-Validation. | `OddjobMarket.java`, `EconConfig.java` | ~12 | Planned |
| **B-006** | IdentityHashMap-Migration Phase 2/3 — Phase 1 fertig. Phase 2: Induvidual→Humanoid `id()`-Key. Phase 3: RoomInstance→Composite-Long-Key. | `*HashMap`-Sites in `core/` | ~50 | Planned |
| **B-009** | Hungersignal ohne Bevölkerungskonsequenz: Save mit 20 Tagen `starving_signal=1, food_days=0` zeigt wachsende Population. Echte Kopplung an `MeticImmigration.emigrate()` + `Roster.applyMortality()` fehlt. | `BrokeFoodPlan.java`, `MeticImmigration.java`, `Roster.java` | ~18 | Planned |
| **B-002** | AccessAutomation-Spam: 14 Statusmeldungen/Tick ins Spieler-Chronik-Fenster. Rate-Limiter aus v0.1.2 greift nur für NPEs, nicht für Statusmeldungen. | `AccessAutomation.java` | ~6 | Planned |
| **B-008** | EngineSeams-Direkt-Calls reduzieren — 31 Sites in core/, Ziel: 0. Adapter-Injection statt direktem Engines-Zugriff. | diverse `core/`-Sites | ~40 | Planned |
| **B-010** | Carpenter `targetWage=0` in `FlowPrices` — kein Wage-Signal für Carpenter-Beruf (sollte ≈ 50 sein). | `FlowPrices.java` | ~8 | Planned |
| **B-011** | CI-Gate-Integration für `tools/scarcity_sim.py` — Golden-Snapshot-Vergleich mit 5%-Toleranz gegen Excel/pandas-Referenz. | `tools/scarcity_sim.py`, `tools/build-gate.sh` | ~15 | Planned |
| **T22** | Savegame-Compat-Headless-Test: lade Quicksave df28c03 in mock-Settlement, prüf ob `EconomySim.load()` clean durchläuft — Smoke-Test gegen Savegame-Format-Drift. | `test/` | ~50 | Planned |

**Backlog-Total:** 11 Tasks (~854 LoC). Sprint 5 plant vermutlich T-COV-9 (kritischste Coverage-Erweiterung) + B-001/B-005 als erste Welle.

---

## Closed Sprints (chronologisch, neueste oben)

### Sprint 3 — Roadmap-SSOT-Konsolidierung + P1-Blocker-Closure (Closed — pre-Sprint-4-Commit, 2026-07-26)

Sprint-Header per agents.md Rule 11+12: 1 Sprint = 1 atomic commit.

| Task | Inhalt | LoC | Datei-Ref | Status |
|---|---|---|---|---|
| **T14.0** | ROADMAP.md → Global Task Index | ~80 | `ROADMAP.md` | Closed (pre-Sprint-4-Commit) |
| **T14.1** | agents.md Rule 13 (NEU): Roadmap-as-Truth-Doktrin, Verschiebe-Verbot, ID-Mapping-Pre-Flight | ~25 | `agents.md` | Closed (pre-Sprint-4-Commit) |
| **T14.2** | WORKFLOW.md Anti-Pattern erweitert | ~15 | `WORKFLOW.md` | Closed (pre-Sprint-4-Commit) |
| **T14.3** | tools/docs-truth-consistency.sh grep-Watch verifiziert — kein neuer Gate nötig | 0 | `tools/docs-truth-consistency.sh` | Closed (verified, pre-Sprint-4-Commit) |
| **T14.4** | docs/BACKLOG.md mold-down auf New-Findings-Only | ~-30 | `docs/BACKLOG.md` | Closed (pre-Sprint-4-Commit) |
| **T14.5** | Stam-Docs-Sync 0.13.30 → 0.13.31 | sed | 5 Stam-Docs | Closed (pre-Sprint-4-Commit) |
| **T14.6** | CHANGELOG.md Sprint-3-Eintrag | ~12 | `CHANGELOG.md` | Closed (pre-Sprint-4-Commit) |
| **T14.7** | Validation-Loop per `mvn verify install -DskipTests -Dskip.bump=true` | verify | (CI) | Closed (verified, pre-Sprint-4-Commit) |

**Sprint-3-Total:** 7 Tasks (~150 LoC additiv, ~30 LoC mold-down).

### Sprint 2 — Mod-Economy T5–T13 (Closed — `c1964d2`, 2026-07-26)

| Task | Inhalt | Datei-Ref | LoC | Status |
|---|---|---|---|---|
| **T5** | B-001 FlowMeter.targetSupply Feld + `@Deprecated`-Getter | `FlowMeter.java:44, 163, 387` | ~15 | Closed (c1964d2) |
| **T6** | B-009 Hunger-Demographie Hook | `EconomySim.java:580-587, 1538-1594` | ~30 | Closed (c1964d2) |
| **T7** | B-004 Classifier-Pipeline-Felder | `CitizenClass.java`, `Wallets.java`, `WealthStats.java` | ~25 | Closed (c1964d2) |
| **T8** | H8 phaseFactor in FlowPrices.refresh() | `EconConfig.java`, `FlowPrices.java`, `EconomySim.java` | ~25 | Closed (c1964d2) |
| **T9** | revertFireSale() EventLog-Hinweis | `TreasuryCrisis.java:418` | ~3 | Closed (c1964d2) |
| **T10** | diagnosticsExportEnabled default `false` | `EconConfig.java:399` | ~1 | Closed (c1964d2) |
| **T11** | HEBELKARTE.md gelöscht | `HEBELKARTE.md:1-9` | ~9 | Closed (c1964d2) |
| **T12** | AccessAutomation.reset() + 6-arg-Ctor | `AccessAutomation.java:86` | ~22 | Closed (c1964d2) |
| **T13** | Static-Audit reset() auf 5 Klassen + 11 Hooks in EconomySim | 5 Files + `EconomySim.java` | ~120 | Closed (c1964d2) |

**Sprint-2-Total:** 9 Tasks (~250 LoC).

### Sprint 1 — TreasuryCrisis State-Leak Reset (Closed — `c1964d2`, 2026-07-26)

| Task | Inhalt | Datei-Ref | LoC | Status |
|---|---|---|---|---|
| **T1** | TreasuryCrisis.reset() Methode — 6 mutable static + 3 saved*-Werte | `TreasuryCrisis.java:466-502` | ~22 | Closed (c1964d2) |
| **T2** | recoveryLogged-Feld + activateWarning() Re-Arm-Logik | `TreasuryCrisis.java:74, 149, 357, 365, 502` | ~12 | Closed (c1964d2) |
| **T3** | Reset-Hooks in `EconomySim.clearActive()` + 6-arg privater Ctor | `EconomySim.java` (11 Calls) | ~10 | Closed (c1964d2) |
| **T4** | ~~Geplant: IdentityMapRegistry-Hook für TreasuryCrisis-spezifische Maps~~ | (kein) | 0 | **Rejected (in T1+T3 subsummiert)** |

### Sprint 0 — Phase A–F SDK + Adapter-Migration (Closed — `1442804`..`c1964d2`, 2026-07-25)

| Task | Inhalt | Datei-Ref | Status |
|---|---|---|---|
| **Phase A** | BypassGate SDK (4 Dateien in adapter/seam/) | `adapter/seam/*.java` | Closed |
| **Phase B–F** | 5 Adapter migriert, 4 Fallback + 3 MH-Varianten gelöscht | `adapter/*.java` | Closed |
| **Cleanup** | EconConfig.useMethodHandleAdapters + EconomySim-Imports | `EconConfig.java`, `EconomySim.java` | Closed |
| **Workflow-Reform** | agents.md Rule 11/12, WORKFLOW.md rebuild, BINDUNGSMATRIX.csv kanonisch, HEBELKARTE.md gelöscht | 9 Files | Closed |

### Sprint -1 — Pre-Sprint-Wave v0.0.1–v0.13.10 (Closed — historisch)

| Task | Inhalt | Status | Version |
|---|---|---|---|
| Cold-Start-Bug | Carpenter 0-Output: `hill!=null`-Guard | Closed | v0.13.10 |
| mean_wage-Runaway | `Math.min(slope, wageMax=1000)` | Closed | v0.13.10 |
| Re-Entry-Crash | idempotenter Guard + Reset in `load()` | Closed | v0.13.10 |
| God-Class-Split EconomySim | RoomOperatingModeController etc. | Closed | v0.13.10 |
| Bug-Loop-Cheat | `foodAffordabilityGateEnabled=true` | Closed | v0.1.4 |
| Stage-gated Wallets | 200/500/2000/5000 D Thresholds | Closed | v0.13.0 |
| 5-Stufen-System | SUBSISTENZ→IMPERIUM | Closed | v0.1.0 |
| Gini→Loyalty Booster | via `GiniConsequences.java` | Closed | v0.1.0 |
| 5 UI-Fenster + 16 Tabs | `ui/WindowEconomy.java` etc. | Closed | v0.13.0 |
| 6 Hotkeys | Numpad +/−/∗/0//, ESC | Closed | v0.13.0 |
| Save-Format 33 chunked | TLV mit Tag-Skipping | Closed | v0.0.2 |
| B-003 Advisor-Widerspruch | aggregiert zentral | Closed | v0.13.0 |
| B-007 catch(Throwable) 27→0 | `phase47-shield.sh` blockt | Closed | v0.1.0 |
| IdentityHashMap Phase 1 | RoomBlueprintImp→String | Closed | Phase 4.7 |
| Phase-A–F SDK + Adapter | BypassGate, 5 Adapter, Auto-Select | Closed | v0.13.10 |

Vollhistorie: [`CHANGELOG.md`](CHANGELOG.md).

---

## Rejected Tasks (mit Begründung, nicht verschoben)

| ID | Task & Ursprünglicher Plan | Datei-Ref | Begründung |
|---|---|---|---|
| **T4 (Sprint-1)** | IdentityMapRegistry-Hook für TreasuryCrisis-spezifische Maps | (kein) | Während Sprint-1-Planung in T1+T3 subsummiert — keine separaten Code-Marker notwendig, da `EconomySim.clearActive()` bereits alle 7 static-reset()-Methoden ruft |

**Verschieb-Verbot aktiv:** Wenn eine Reject-Begründung nicht ausreicht, ist die Task entweder `Planned` (im Backlog) oder explizit `Rejected (Begründung)`. Niemals "Verschoben", "Postponed", "Deferred", "Spaeter", "Next-Sprint". Der `tools/verify-doc-sync.sh` Gate grep-t diese Wörter.

---

## Definition of Done

Vor jedem Sprint-Commit (Atomic per agents.md Rule 11+12) muss gelten:

1. `mvn validate` BUILD SUCCESS — alle 4 Gates grün:
   - Stam-Doku-Sync (alle 7 Docs ↔ `pom.xml`)
   - Code-Audit (kein `catch(Throwable)`, kein `printStackTrace`)
   - Version ↔ Changelog + `_Info.txt`-Template-Konsistenz
   - Adapter ↔ Engine-Signaturen (5 Adapter, 19 Methoden/Felder)
2. `mvn test` — alle JUnit-Tests grün (167+ → mit Sprint 4: ~235+).
3. Manuell: `bash tools/bump-version.sh patch --dry-run` zeigt nur den nächsten Patch-Schritt.
4. Stam-Dokumente haben oben den Versions-Stempel `**Version:** v0.13.x` o. ä.
5. **NEU:** `bash tools/verify-doc-sync.sh` muss das Verschieb-Wort-Grep-Watch passen.
6. **NEU (Sprint 4):** JaCoCo-Report verfügbar unter `target/site/jacoco/index.html` (report-only, kein Build-Break bis Sprint 9 die Schwellen anzieht).

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

# Coverage-Report (Sprint 4 — T-COV-6)
mvn test
xdg-open target/site/jacoco/index.html
```

---

## Cross-Reference-Tabelle: BINDUNGSMATRIX.csv vs. Roadmap

`BINDUNGSMATRIX.csv` (332 Zeilen, 11 Spalten) ist die **Datenmatrix für Engine-Hebel-Verifikation** —
sie beantwortet "Welche Engine-API hat der Mod angefasst?" (Spalte 11: `++ verified`, `?? orphan`,
`? unclear`, `/ rebuttal`). Die **ROADMAP-Tasks** beantworten "Was bauen wir?" — beide sind getrennte
Welten.

**Referenz-Hub:**
- Engine-Hebel-Verifikation: [`BINDUNGSMATRIX.csv`](BINDUNGSMATRIX.csv)
- Live-Findings (noch nicht im Roadmap): [`docs/BACKLOG.md`](docs/BACKLOG.md) § New-Findings
- Historische Commits: [`CHANGELOG.md`](CHANGELOG.md)
- Architektur-Kontext: [`ARCHITECTURE.md`](ARCHITECTURE.md)
- Vokabular: [`GLOSSARY.md`](GLOSSARY.md)
- Phase-5-Pläne (absorbiert in Roadmap): [`docs/superpowers/plans/`](docs/superpowers/plans/)
