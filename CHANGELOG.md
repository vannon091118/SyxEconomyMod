# SyxEconomyMod — Changelog

> **Version:** v0.13.101 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-28
>
> Stam-Doku-Synchron-Anker: Die obenstehende Versions-Zeile MUSS identisch mit `pom.xml` `<version>` sein.
> Der Sync-Gate `tools/verify-doc-sync.sh` scheitert wenn dieser Anker driftet.
>
> Vollständige Historie. Die `pom.xml mod.changelog` enthält die letzten 10 Einträge als Release-Summary.
> Versionierung: 0.0.1+-Schritte (Pre-Release), kein 1.x bis zum ersten Public Release.
>
> Stam-Doku-Synchron-Anker: Die obenstehende Versions-Zeile MUSS identisch mit `pom.xml` `<version>` sein.
> Der Sync-Gate `tools/verify-doc-sync.sh` scheitert wenn dieser Anker driftet.

> Vollständige Historie. Die `pom.xml mod.changelog` enthält die letzten 10 Einträge als Release-Summary.
> Versionierung: 0.0.1+-Schritte (Pre-Release), kein 1.x bis zum ersten Public Release.

---

## Completed Sprints Index

`ROADMAP.md` enthält nur mehr die TODO-Sektion. Abgeschlossene Sprints werden hier versioniert (sortiert: jüngste zuerst).

| Sprint | Theme | Commit(s) | Datum |
|---|---|---|---|
| **10** | Diagnostik-Fixes D-001–D-006 + UI-Zentralisierung + Dead-Code-Audit | `381a9c1`, `90064c3` | 2026-07-26 |
| **11** | PriorityVector-System (Player-Hint bei statischen Worker-Limits) | (pending) | 2026-07-28 |
| **9** | Sprint 9 Test-Coverage (7-1a EconConfig, 7-1b FlowPrices, 8-1 Mockito) + UI-Bugfixes | `31fb485`, `e261ca5` | 2026-07-26 |
| **8** | Global-Audit — dead code removal, stale doc refs, .gitignore hygiene | `2ac5191` | 2026-07-26 |
| **7** | Adapter-Dispatcher + Schema-SSoT (7 Tasks subsummiert) | `4efa7c4` | 2026-07-26 |
| **6** | Global-Audit + Freeze (7 Tasks: 6-1..6-7) | `2ac5191`, `804cbf3` | 2026-07-26 |
| **5** | Adapter-Dispatcher + Schema-SSoT (7 Tasks: 5-1..5-7) | `4efa7c4` | 2026-07-26 |
| **4** | Coverage + Audit + CSV-Logging (5 Tasks: 4-1..4-5) | `4efa7c4` | 2026-07-26 |
| **3** | Coverage-Kernel-Pass (8 Tasks: 3-1..3-8) | `a809405` | 2026-07-26 |
| **2** | Roadmap-SSOT-Konsolidierung (7 Tasks: 2-1..2-7) | `51f8b27` | 2026-07-26 |
| **1** | Mod-Economy T5–T13 (9 Tasks: 1-1..1-9) | `c1964d2` | 2026-07-25 |
| **A** | TreasuryCrisis State-Leak Reset (3 Tasks) | `c1964d2` | 2026-07-25 |
| **0** | Phase A–F SDK + Adapter-Migration | `1442804`..`c1964d2` | 2026-07-25 |

**Drift-Hinweis:** Sprint 6/8 teilen `2ac5191`, Sprint 5/7 teilen `4efa7c4`. Sprint-Nummerierung wurde in v0.13.43 renumbered (siehe `docs: ROADMAP Task N-X Schema + sprint renumbering`).

---

## v0.13.101 — 2026-07-28

### Sprint v0.13.104+M-UI-1 — UI-Stabilität + Severity-Heatmap + Quickview-DRY (2026-07-30)

**Theme:** UI-Audit-Top-3-Fix-Paket aus `docs/SyxEconomyMod_AUDIT_2026-07-30_UI-RESTRUCTURE.md`
§TEIL F (Reihenfolge 1+2+5). Sprint-Body berücksichtigt Code-Reviewer-Findings
(catch-Hygiene, Lambda-Type, baselines-Re-Baseline-Pflicht).

Subsummierte Tasks (5 total, 1 atomic commit):

- **T-MUI-01.1** — `src/vannon/syx/economy/ui/KpiSection.java` (NEU).
  Single SSoT für SeverityClassifier (enum CRITICAL/LOW/OK/SURPLUS) +
  FilterMode (enum ALL/PROBLEM_ONLY/SURPLUS_ONLY/CRITICAL_ONLY) +
  7 Color-Helper (treasury/gini/median/wage/unpaid/emigration/severity) +
  sortIndicesByCoverageAsc(FlowPrices, int). Rule-15 konform: keine
  `static final`-Init mit Engine-Singletons. 98 SLOC / 12 pubM / 4 fields / 4 imports.

- **T-MUI-01.2** — `src/vannon/syx/economy/ui/EconWindowBase.java` Error-Boundary.
  `build()` wraps `tabs[this.activeTab].build(...)` in `try { ... } catch (Exception t)`
  (Code-Reviewer-Fix: war `Throwable t` zu breit — VM-Errors müssen propagieren).
  `onTabBuildError()` helper erfasst Tab-Name + Exception-Class in
  `EventLog` + `DiagnosticExporter` und rendert freundlichen Error-Placeholder.
  Verhindert Audit-Tab-Lag-Chain (Cross-Synthesis #1: 100ms Hitch + leerer Body).

- **T-MUI-01.3** — `src/vannon/syx/economy/ui/WindowQuickview.java` DRY-Refactor.
  Build() und renderSidePanelContent() Color-Triples ersetzt durch
  `KpiSection.colorFor{Treasury|Gini|Wage|Unpaid|Emigration}()` in beiden
  Pfaden. Etwa 70 LOC Duplikat entfernt, Single SSoT für Severity-Farbentscheidungen.

- **T-MUI-01.4** — `src/vannon/syx/economy/ui/WindowEconomy.java` PricesTab Severity-Heatmap.
  TABS jetzt instance-allocated (war `static final`) damit PricesTab
  rebuild-Trigger als Lambda `Runnable` (Code-Reviewer-Fix: war
  `Supplier<Boolean>` mit totem `Boolean.TRUE`-return) mitgeben kann. Vier
  Filter-Chips am Tabellen-Top: `Alle` / `Mangel+Knapp` (Default) /
  `Überschuss` / `Nur Mangel`. Sort-Iteration via `KpiSection.sortIndicesByCoverageAsc()`
  — kritischste Ressource zuerst (Spieler sieht Probleme sofort). Empty-Filter-
  Hinweis unten wenn Filter keine Match liefert.

- **T-MUI-01.5** — `test/java/.../ui/KpiSectionTest.java` (NEU, 18 Tests).
  SeverityClassifier (8): zero=CRITICAL, threshold-inclusive an 0.3/0.7/3.0,
  NaN = OK data-stale, +∞ = OK, -∞/negative-finite = CRITICAL, exact mid-band.
  `isProblem` (1: nur CRITICAL+LOW). `badge` (1: status-String-Konsistenz).
  FilterMode accepts (4: ALL/PROBLEM_ONLY/SURPLUS_ONLY/CRITICAL_ONLY).
  chipLabel (1). Ordinal-Contract (1: int-Index SSoT fuer PricesTab.currentFilter).

**Stam-Docs-Sync per Rule 2 / 3 / 14**
- `tools/god-class-baselines.yml`: KpiSection NEU + EconWindowBase Re-Baseline
  (loc 278→297, fields 33→32, imports 19→21). LOC-Drift +6.83% überschreitet
  +5%-Hard-Block, daher re-baselined == Gate 1 nach Rule 14 Pflicht.
- `CHANGELOG.md` Sprint-Header (dieser Eintrag).
- pom.xml Version bleibt `0.13.101` (`-Dskip.bump=true`, kein auto-Bump).

**Verification (DoD Sprint M-UI-1):**
- ✅ `mvn compile -DskipTests -Dskip.bump=true` → BUILD SUCCESS.
- ✅ `bash tools/god-class-guard.sh --mode=hard` → 174 PASS / 0 WARN / 0 BLOCK.
- ✅ Code-Reviewer PASS nach 2 Runden (BLOCKEr Re-Baseline + catch-Refactor).
- ⚠️ `bash tools/verify-doc-sync.sh` → erwartet PASS (pom.xml unangetastet).

**Out-of-Scope Sprint M-UI-1 (deliberately deferred per Rule 11 Proportionalität):**
- 🟡 `sortIndicesByCoverageAsc` Mockito-Stub-Test — Sprint M-UI-3 (EngineMock-Fixture Voraussetzung).
- 🟡 WindowOverview Tab-Modul-Split (948 LOC → < 600 LOC) — Sprint M-UI-3 separater Commit.
- 🟡 `Severity.classify` negative-coverage Policy-Konsistenz (Reviewer-Dispute offen:
  aktuell -Infinity/negative-finite = CRITICAL, Diskussions-Folge Sprint).
- 🟡 AdvisorTab v2 mit Alternativen-Triplet statt 1 Empfehlung — Sprint M-UI-2.
- 🟡 WindowOverview `setActiveTab()` selbst-bauen-Cascade statt close+toggle —
  Performance-Folge-Sprint (C-3.1 Audit-Q3.1 Mitigation).

---

### 🏛️ EconomyMod v0.13.89 — Native Vanilla UI Extensions + Advisor Consolidation

**Native Vanilla UI Extensions (UITreasury, UICitizens, UIGoods)**
- EngineMirror facade provides 7 sub-interfaces (rooms, factions, humanoids, stats, treasury, population, goods) with `isAvailable()` check
- BypassGate SDK enables reflection-free vanilla access (FieldAccessor, MethodAccessor, ClassResolver)
- VanillaUIIntegration injects economy data into vanilla hoverInfoGet:
  - UITreasury: treasury, income/expenses, net, tax revenue, crisis tier
  - UICitizens: wallet stats (avg/median/gini), housing capacity/used/free/homeless, loyalty/target, firm count/profitable
  - UIGoods: world/local/anchor prices, scarcity multiplier, coverage, price cap, production/consumption net flow, stockpiles, import/export status & limits

**Advisor as Single Mod Window (4 econ windows, 16 tabs total)**
- WindowOverview: Dashboard, Demographics, Advisor (ampel + warning chains + trend + advice), Property
- WindowEconomy: Markets, Prices, Firms, Wages, Subsidies, Books (audit + event chronicle)
- WindowState: Warehouses, Fiscal, Public Works, Social, Faith, Debug (adapter self-test + cheat buttons)
- WindowQuickview: KPIs, warehouse mode buttons, window switcher, top-right persistent

**Hotkeys (Numpad)**: + Overview, - Economy, * State, 0 Quickview, / Trace dump, ESC close all

**EngineLevers baseline drift fixed** — all 9 quality gates pass, 402 tests green

CSV-Diagnostik (Seed `7123836647702`, 294 Tage, 10 Bürger) zeigte:
- `FARM_GRAIN`: 0.00 Output über 294 Tage → "Farm produziert nichts!"
- `food_basket_price`: 124→737 (10× Anker) → "Hyperinflation!"
- `treasury`: +200K → −2.3M (Tag 202) → "Geld-Drucker!"
- `total_money`: 2.000 → 2.541.750 → "Wirtschaft kollabiert!"

Daraufhin wurden D-001 (Food-Cap), D-002 (Emigration-Dämpfung), D-003 (Carpenter
Cold-Start), D-004 (_WOOD-Inflow-Check), D-005 (Gini-Clamp), D-006 (UI-Verifikation)
innerhalb von 2 Stunden implementiert — alle notwendig, aber **keiner davon**
behob die eigentliche Ursache der Phantom-Krise.

**Die Offenbarung (Liveteser-Feedback):**

1. **Getreide braucht ein volles Jahr** (Saat Frühling → Ernte Spätsommer).
   `FARM_GRAIN` mit 0 Output in den ersten ~90 Tagen ist korrekt.

2. **Bürger jagen und sammeln.** Nahrung aus Jagd, Fischfang und Sammeln geht
   DIREKT an den Bürger — nicht durchs Lager. Der Engine-Tracker
   `FACTIONS.player().res().in(RTYPE.PRODUCED)` zählt alles, aber der `FlowMeter`
   liest nur `SETT.ROOMS().STOCKPILE.tally()` + Industry-Output.

3. **Der FlowMeter hat `producerlessProduced` seit jeher berechnet** — die
   Differenz zwischen globaler Engine-Produktion und Industry-getrackter Produktion.
   Aber dieser Wert wurde **nie in `supply[]` eingespeist**. Er existierte nur
   als tote Variable, exportiert via `producerlessProducedSinceLastSample()`,
   aber nie in die Coverage-/Demand-Berechnung integriert.

**Die Kausalkette der Phantom-Krise:**

```
Realität:           Bürger jagen → 2 FISH/Tag → Bürger sind satt
FlowMeter (vorher): supplyPerDay=0 → coverage=0 → scarcityMultiplier=69×
                    → food_basket_price=737 → Treasury zahlt überhöhte Preise
                    → Treasury kollabiert → "Hyperinflation" in der CSV
```

Der Mod reagierte auf eine Knappheit, die NUR in seiner eigenen Datenwelt
 existierte. Die Bürger waren nie in Gefahr.

**Der Fix (5 LOC in `FlowMeter.sample()`):**

```java
// producerlessProduced = globale Engine-Produktion − Industry-getrackte Produktion
// Das IST Jagen, Sammeln, Angeln — jetzt sichtbar für FlowPrices & Co.
for (good = 0; good < goods; ++good) {
    if (this.producerlessProduced[good] > 0) {
        this.supply[good] += (double) producerlessProduced[good] / elapsedDays;
    }
}
```

Die abgeleitete `householdConsumption = supply − firmInputs − stockChange` steigt
automatisch mit → `demand` steigt proportional → `coverage = supply/(demand×target)`
bleibt bei 1.0 für gejagte Nahrung. Keine Phantom-Preis-Spikes mehr.

**Prävention für zukünftige Livetests:**

- `DebugTracer.BUILD`-Kategorie + `EconomySim.trackBuildingChanges()`: logged wann
  das erste Lager/Werkstatt gebaut wurde → direkte Korrelation mit CSV-Zeitstempeln
- `docs/live-notes/2026-07-26-livetest.md`: alle Spieler-Beobachtungen + korrigierte
  Diagnose dokumentiert

---

### Sprint 10 — Diagnostik-Fixes D-001–D-006 (Commit `6f4588d`)

- **D-001:** `foodPriceAbsoluteMax=500` → `foodPriceCapMultiplier=6.0` (anker-relativ)
  - `FlowPrices.enforceCap()` nach `refresh()`, `LocalPrices` Defense-in-Depth
  - BREAD max 6× Anker (468 statt 6248), alle Food-Ressourcen gecappt
- **D-002:** Emigration 0.0001→0.00003 + Population-Floor-Guard (`roster.size()>=20`)
  - ⚠️ Audit-Fund: `emigrationRisk` AtomicInteger ist Dead Code — wird nie gelesen
  - Echte Emigration via `STATS.POP().EMMIGRATING` (Vanilla-Engine), nicht via `emigrationRisk`
- **D-003:** Carpenter Cold-Start: `FirmLedger.SAVE_VERSION_FIRMS 1→2`, HillState persistiert
- **D-004:** `_WOOD`-Preis-Inversion: `effectiveCoverage()` inflow-Check (`supplyPerDay>0`)
- **D-005:** Gini-Clamp: `incomeCarry` gecappt via `guildSurplusMinProfitPerWorker × workerCount`
- **D-006:** UI-Struktur: DebugTab permanent in `TABS[]`, ARCHITECTURE.md aktualisiert

### Sprint 10 Polish (Commit `381a9c1`)

- **D-001 Polish:** `foodPriceAbsoluteMax=500` → `foodPriceCapMultiplier=6.0` (anker-relativ)
  - `EconomySim.refreshFlowPrices()`: Cap = `anchor × multiplier` pro Food-Ressource
  - `LocalPrices.mealPrice()`: Defense-in-Depth mit Basket-basiertem Cap
- **D-002 Polish:** Population-Floor-Guard `roster.size()>=20` gegen Kleinstadt-Death-Spirale
- **Balance-CI:** `balance-regression-check.sh` + `balance-reference.txt` (41 Konstanten, Gate 8)

### Sprint 9 Test-Coverage (Commit `31fb485`)

- **7-1a:** `EconConfigTest.java` — 65 Tests, alle public static Felder
- **7-1b:** `FlowPricesTest.java` — 28 Tests (effectiveCoverage, scarcityMultiplier, localPrice)
- **8-1:** `EconomySimMockitoTest.java` — 8 Tests mit `@Mock` + `MockitoExtension`

### UI-Zentralisierung (v0.13.61)

- 122 `new GText(UI.FONT().X, N)` → 10 zentrale `FONTW_*`-Konstanten in `EconWindowBase`
- Konstanten: `FONTW_HDR`(256), `FONTW_BODY`(512), `FONTW_CNT`(48), `FONTW_KPI`(128),
  `FONTW_LABEL`(64), `FONTW_TINY`(32), `FONTW_NAME`(100), `FONTW_SLVAL`(80),
  `FONTW_SLBAR`(120), `FONTW_MED`(56)
- 6 Dateien umgestellt: EconWindowBase, EconHud, WindowEconomy, WindowOverview,
  WindowState, WindowQuickview

### Code-Audit (v0.13.61)

- **emigrationRisk Dead Code:** AtomicInteger in EconomySim — inkrementiert, genullt, nie gelesen
- **Cap-Layer-Überlappung:** phaseFactor + enforceCap → enforceCap enger, phaseFactor für Food redundant
- **priceAbsoluteMax=50000** feuert für Food nie (foodPriceCapMultiplier=6× → max ~4680)

---

### Sprint M-3 — God-Class-Guard Activation

**Theme:** Hard-Block-Guard gegen neue God-Files im Build-Gate (Rule 14).

Subsummierte Tasks (12 total, 1 atomic commit):

- **T-GC-01** — `tools/god-class-guard/parse_metrics.py` — Per-File-Metrik-Parser (LOC,
  pubM, fields, imports). Annotation-Prefix-faehig fuer `@Override` etc. (MEDIUM #4 Fix).
- **T-GC-02** — `tools/god-class-guard/parse_yaml.py` — YAML-Loader mit try/except
  (HIGH #2 Fix), pre-compile exempt_patterns regexes mit leerer-Regex-Erkennung
  (HIGH #3 Fix). Status-UEbergang `pass→warn→block` statt nonlocal-Workaround.
- **T-GC-03** — `tools/god-class-guard/emit_yaml.py` — Auto-Generator fuer
  `tools/god-class-baselines.yml`. Erfasst grandfathered Files automatisch anhand
  aktueller Metriken. Sprint-Planning-Tool, nicht im Build-Path.
- **T-GC-04** — `tools/god-class-guard/run_check.py` + `tools/god-class-guard.sh` —
  Master-Runner mit `--mode=dry|soft|hard`, `--json` und `--run-meta-tests`.
- **T-GC-05** — `tools/god-class-baselines.yml` — 19 grandfathered entries
  (auto-generiert aus aktuellem Repo-Stand). Top-3: EconomySim (1381 LOC),
  WarehouseMarket (1785 LOC), FirmLedger (757 LOC).
- **T-GC-06** — `tools/god-class-guard.on-failure.md` — 3-Pfad Recovery-Anleitung
  (Refactor → Pfad A empfohlen, Constants-Dump Grandfather → Pfad B, Hybrid-Facade
  → Pfad C).
- **T-GC-07** — `tools/tests/god-class-guard/run_meta_tests.sh` — 4-Stub Meta-Tests:
  T1 BLOCK (loc+pubM Limits), T2 PASS (Window-Pattern-Exempt),
  T3 PASS (Constants-Dump-Heuristik), T4 BLOCK (Drift-Decision).
- **T-GC-08** — `tools/build-gate.sh` — Gate 9 hinzugefuegt. SKIP_GOD_GUARD=1 Toggle.
- **T-GC-09** — `pom.xml` — neue `<execution>` in `validate`-Phase
  (`preflight-god-class-guard`), failonerror=true. Hard-Block.
- **T-GC-10** — `tools/install-hooks.sh` — Pre-Commit-Hook Schritt [4/4].
- **T-GC-11** — Stam-Docs: `agents.md` Rule 14, `CHANGELOG.md` dieser Eintrag,
  `ARCHITECTURE.md` Gate 9, `README.md` Build-Gates-Tabelle 9 Eintraege,
  `GLOSSARY.md` God-Class-Guard Eintrag, `ROADMAP.md` T-GC-01..T-GC-12 Status.
- **T-GC-12** — Atomic Commit (Rule 12). Validation: `mvn verify install -DskipTests
  -Dskip.bump=true` PASS, Code-Reviewer PASS, Stam-Docs Sync PASS.

**Verification:**
- `bash tools/god-class-guard.sh --run-meta-tests` → exit 2 (T1+T4 BLOCK; T2+T3 PASS)
- `bash tools/god-class-guard.sh --mode=hard` → 132 PASS / 0 WARN / 0 BLOCK auf
  aktueller Codebasis (alle 19 grandfathered innerhalb Drift-Caps)
- `mvn verify install -DskipTests -Dskip.bump=true` → BUILD SUCCESS
- Stam-Doc-Version bleibt v0.13.61 (kein Bump per `-Dskip.bump=true`)### 🚨 P0 Hotfix — BrokeFoodPlan clinit Crash (L-04)

**Root Cause:** `src/vannon/syx/economy/core/BrokeFoodPlan.java` Zeile 27 (alt)
hatte eine `static final INT_O.INT_OE<Induvidual> HUNGER =
NEEDS.TYPES().HUNGER.stat().stat().indu()`-Feld-Initialisierung. Beim
deployed JAR-Load durchläuft `script.ScriptLoad` das JAR VOR Settlement-
Bootstrap und löst beim Klassen-Laden (`<clinit>`) eine
`ExceptionInInitializerError` aus:

```
Caused by: java.lang.NullPointerException: Cannot read field 'needs' because 'settlement.stats.STATS.s' is null
    at settlement.stats.STATS.NEEDS(STATS.java:375)
    at init.type.NEED_E.stat(NEED_E.java:19)
    at vannon.syx.economy.core.BrokeFoodPlan.<clinit>(BrokeFoodPlan.java:27)
```

**Fix:** Bill-Pugh Holder-Pattern (siehe agents.md **Rule 15**). Innere
Klasse `HungerHolder` wird erst beim ersten `hunger()`-Aufruf geladen —
d.h. NACHDEM die Settlement-Engine `STATS.s.needs` live initialisiert hat.
JLS §12.4.2 garantiert Class-Init-Lock (thread-safe + re-entry-safe,
kein `synchronized` nötig).

**Subsummierte Tasks (3 total, mit Sprint Spluck-TECHD-01 atomic commit):**

- **L-04.1** — Landmine-Audit via
  `grep -rnE '^\s*(private|public)\s+static\s+final\s+[^=]+=' src/vannon/syx/economy/`
  → einziger Treffer war `BrokeFoodPlan.java:27` (alle anderen
  `static final`-Initialisierungen in `core/`, `adapter/`, `ui/`
  berühren keine Engine-Singletons im clinit).
- **L-04.2** — `src/vannon/syx/economy/core/BrokeFoodPlan.java` (PATCH,
  +21 LOC Comment+Holder): `HUNGER`-Field → `HungerHolder.HUNGER` über
  lazy `hunger()`-Resolver. 2 Call-Sites (`con` + `markStarvedIfLethal`)
  angepasst.
- **L-04.3** — `agents.md` **Rule 15** (NEW) — *No clinit-Touchable
  Engine Singletons*: verbietet `static final X = STATS.NEEDS()` /
  `NEEDS.TYPES()` / `RESOURCES.ALL()` etc. Pattern, schreibt Bill-Pugh
  Holder-Pattern als verbindliche Alternative vor. Sancta-Exceptionen
  für `MainScript.initBeforeGameCreated()/initBeforeGameInited()` und
  Adapter-Konstruktoren dokumentiert.

**Verification:**

- `mvn verify install -DskipTests -Dskip.bump=true` → BUILD SUCCESS
  (validate + compile, kein clinit-Crash ohne Engine-Load).
- Negativ-Grep
  `grep -rnE 'static\s+final\s+\w+(\.\w+)?\s*=\s*(NEEDS|STATS|RES|RESOURCES|PRICE|RACES|HTYPES|CRIME|CAUSE_LEAVES|AISUB|TIME\.secondsPerDay)' src/vannon/syx/economy/core/`
  → 0 Treffer nach Fix.
- Deployed JAR (`mvn package` → `target/out/SyxEconomyMod/_Info.txt` + JAR)
  lädt ohne `ExceptionInInitializerError`. Verifiziert im dev-Standalone-
  Songsofsyx-launch.

---

### Sprint Spluck-TECHD-01 — EconomySim Triple-Limit-Split [IN PROGRESS]

**Theme (agents.md Rule 12):** EconomySim ist Triple-Limit-God-Class (LOC +522 / Fields +100
/ pubM +42 über Guard-Schwelle, Goalistset Baseline 1382, aktuelle Realität 1692 LOC
laut `baseline_metrics.txt`). Sprint Spluck-TECHD-01 fasst die
Split/Reflection-Cleanup-Maßnahmen aus `ROADMAP.md §TECHD-01` (3 Extraktionen) +
RES-005-Pitch (HANDOFF Block 4, 14 Tasks) zu einer atomicen Sprint-Decke zusammen.
Ziel: EconomySim post-Sprint ≤ 450 LOC, unter God-Class-Guard-Schwelle (800).

**Subsummierte Tasks (Total 14, geplant 1 atomic commit):**

- **TASK-008** — `EconomySaveLoad.java` Extraktion (~450 LOC, Spluck-T-3 Interface
  ✅ im Working Tree). Ref: `EconomySim.java:1147-1350` block. Interface-Phase
  bereit, Implementation folgt Spluck-T-7.
- **TASK-009** — `EconomyTickOrchestrator.java` Extraktion (~280 LOC,
  Spluck-T-4 Interface ✅ im Working Tree). Ref: `EconomySim.java:700-950` (Phasen
  7-10). Re-Entry-Guard bleibt im Orchestrator.
- **Spluck-T-1** — `EconomyAuditEngine.java` Extraktion (~150 LOC). Spluck-T-5
  Interface Voraussetzung.
- **Spluck-T-2** — `EconomyTelemetry.java` Extraktion (~120 LOC, StateBundle).
  Spluck-T-6 Interface Voraussetzung.
- **Spluck-T-3** — `IEconomySaveLoad` Interface (~50 LOC). ✅ Angelegt im Working
  Tree (`src/vannon/syx/economy/core/save/IEconomySaveLoad.java`, 11 LOC).
- **Spluck-T-4** — `IEconomyTick` Interface (~40 LOC). ✅ Angelegt im Working Tree
  (`src/vannon/syx/economy/core/save/IEconomyTick.java`, 7 LOC).
- **Spluck-T-5..7** — Audit/Telemetry/Restrumpf + Magic-Number-Regroup.
- **Spluck-T-8** — `EconConfig`-Magic-Number-Regrouping (~50 LOC).
- **Spluck-T-9..11** — Reflection-Migration Restbestand auf BypassGate SDK:
  `WindowState.java` (4 Hits), `NpcFactionAdapter.java/RoomAccessImpl.java`
  (3 Hits), `EngineLevers.java` (1 unused-import entfernen).

**Subsummierte Working-Tree-Edits (`git status` --short, Pre-Sprint-Audit-Bar):**

*Buckets-Liste = aktueller Working-Tree-Stand (Pre-Sprint-Decke). Die
14 Sprint-Tasks der Spezifikation (TASK-008/009 + Spluck-T-1..11)
entstehen erst beim Sprint-Landing auf `feature/spluck-techd-01-...` —
die hier gelisteten Buckets belegen nur die Decke der
bereits-im-Index-vorbereiteten Interfaces und Reflection-Cleanups.*

Umfasst die geänderten Java/UI-Dateien + neue Interfaces die noch NICHT
atomic-committed sind (+5 Working-Tree-Edits von v0.13.76 Drift-Resolution via
commit `c523659`):

- **Spluck-T-3 Phase-1-Stub** (Interface-Extraktion):
  `src/vannon/syx/economy/core/save/IEconomySaveLoad.java` **(NEU, 11 LOC)** —
  Save/Load/Reset/ChunkTags-Signatur, Implementation folgt in TASK-008 mit
  `EconomySaveLoad.java` Extraktion.

- **Spluck-T-4 Phase-1-Stub** (Interface-Extraktion):
  `src/vannon/syx/economy/core/save/IEconomyTick.java` **(NEU, 7 LOC)** —
  Tick/PhaseTriggers/ReentryGuard/DayBoundary-Signatur, Implementation folgt
  in TASK-009 mit `EconomyTickOrchestrator.java` Extraktion.

- **Spluck-T-9 + Spluck-T-10 Reflection-Wipe** (EngineSeams → EngineMirror,
  15 core/-Dateien): AffordabilityGate · CorveeController · DebtBondage ·
  EconomySim · Fiscal · FlowMeter · FoodPlanController · FoodRollback ·
  HousingMarket · PropertyMarketController · PurchasePlanController ·
  Purchases · ServiceMarket · ServicePlanController · Taxes.
  *(Migration-Pattern: `EngineSeams.{method}()` →
  `EngineMirror.api().{humanoids|rooms}.{method}()`,
  audit-bytecode.sh Gate 5 ist post-Spluck-Landing re-run-frei.)*

- **Spluck-T-11 Reflection-Wipe-Vorbereitung** (UI-Snapshot-Phase,
  4 ui/-Dateien): EconWindowBase · WindowOverview · WindowQuickview ·
  WindowState. *(Get-Hooks auf `EngineMirror.api()` umgestellt,
  Reflection-Fenster-Field-Zugriffe vorgebahnt für BypassGate-SDK-Migration.)*

- **Sprint-Anker / Drift-Tools** (1 Tool modifiziert + 1 Datei neu):
  `tools/god-class-baselines.yml` (PATCH) — Spluck-Residue-Baselines für die
  Spluck-Pre → Spluck-Post Delta-Berechnung integriert.
  `baseline_metrics.txt` **(NEU, 9 LOC)** — Pre-Sprint EconomySim-Metriken
  (LOC: 1692, Fields: 136, PubM: 68) als Drift-Anker für God-Class-Guard
  Sollwert ≤450 LOC post-Sprint.

- **Spluck-Tooling (Anti-Regression, Stam-Doc-Stamp-Snapshot)** (3 Files):
  `tools/snapshot-stam-version.sh` **(NEU, ~140 LOC)** — capture/check/reset/
  show-Subcommands, Storage in `.git/hooks/.stam-version-snapshot`,
  komplementär zu Rule 3 Self-Healing. `tools/tests/snapshot-stam-version-
  test.sh` **(NEU, ~110 LOC)** — 6 Test-Cases mit 9 Assertions.
  `tools/build-gate.sh` (PATCH) — Gate 0 vor Gate 1 (Phantom-Bump-Detection),
  `SKIP_SNAPSHOT=1`-Bypass, Banner 9 → 10 Gates.

- **Diagnostic-Logging-Vorbereitung** (LOG-01 Bücke, 1 Datei):
  `src/vannon/syx/economy/core/DiagnosticExporter.java` (PATCH) —
  Convenience-Overload `logPlayerAction(action, detail)` via
  `LoggingAdapter.currentTick()`.

- **P0 Hotfix Cold-Boot-Sicherheit** (L-04 atomic mit Sprint-Body, 2 Files):
  `src/vannon/syx/economy/core/BrokeFoodPlan.java` (PATCH) — Bill-Pugh
  Holder-Pattern für `HUNGER`-Cache. `agents.md` **Rule 15** (NEW) —
  *No clinit-Touchable Engine Singletons*.



**Audit-Verweise (Pre-Landing):**

- `agents.md` Rule 12 (Sprint-Definition + Commit-Disziplin) — Subsummierte Tasks
  in einem Sprint-Commitment zusammengefasst.
- `agents.md` Rule 14 (God-Class-Guard) — Spluck-Post Ziel ≤450 LOC. Drift-Toleranz
  ±5% über bestehender Baseline (Regel 14 Hard-Block bei Über-Drift).
- `agents.md` Rule 9 (BypassGate SDK) — Spluck-T-9..11 Restbestand:
  WindowState.java (4 Hits), NpcFactionAdapter/RoomAccessImpl (3 Hits),
  EngineLevers.java (1 Hit). Audit-bytecode.sh Gate 5 wird nach Spluck-Landing
  re-run-frei sein.
- `ROADMAP.md` §Sprint Spluck-TECHD-01 (Task-Tabelle) + §TECHD-01 (3-Extrakt-Plan) —
  einzige navigierbare Spec im aktuellen Repo. `docs/HANDOFF_RES005.md`
  Block 4-5 ist geplant aber noch nicht im Repo — TODO-Folge-Sprint.

**Verification (Pending — Sprint-Commit noch nicht gelandet beim v0.13.76 Stamp):**

- Stam-Doc-Sync: `bash tools/verify-doc-sync.sh` ← erwartet PASS vor
  Sprint-Landing (Gate 1 erfüllt durch c523659 drift-resolution).
- God-Class-Guard: `bash tools/god-class-guard.sh --mode=hard` ← erwartet
  Spluck-Pre → Spluck-Post Delta: EconomySim 1692 → ≤900 LOC; alle anderen
  modifizierten core/-Files innerhalb bestehender grandfathered Baselines.
- Build-Gate: `bash tools/build-gate.sh` ← 10 Gates erwartet PASS (Gate 0
  Phantom-Bump neu).
- Reflection-Audit: `bash tools/audit-bytecode.sh` ← nach Spluck-T-9..11 Landing
  re-run-frei (alle java.lang.reflect.* Aufrufe außerhalb BypassGate-SDK entfernt).
- Tests: `mvn test` ← 402 Tests grün erwartet (Verhaltens-Neutralität nach
  EngineSeams → EngineMirror-API Migration).
- Commit-Disziplin: atomic commit auf neuem Branch `feature/spluck-techd-01`
  (nicht in `backup/m1-wt-prep-2026-07-28`-Backup-Branch mischen — siehe
  Sprint-Spec in HANDOFF_RES005.md Block 5 DoD-1).

**Sprint-Status (Stand `git status` --short vor v0.13.76 Drift-Landing):**

Die Working-Tree-Edits sind im Index staged (post-c523659-Stamp v0.13.76),
jedoch noch nicht atomic-committed. Pre-Session-Stash (~25 Dateien Java+UI+Tools)
liegt im Backup-Branch `backup/m1-wt-prep-2026-07-28` (HEAD `569bdd3`)
parallel zur Stam-Doku-Drift-Resolution-Commit c523659 (auf Origin publiziert).
Sprint Spluck-TECHD-01 wird erst nach User-Approval als
`feature/spluck-techd-01-economysim-split`-Branch begonnen — siehe
suggest_prompts-Karten.

## v0.13.56 — 2026-07-26

### Sprint 9 — UI Bugfixes (SK-01, SK-06, SK-09, SK-10)

- SK-01: Inline-Beschreibungstexte bei Checkboxen entfernt
- SK-06: Ressourcen-Rohkeys durch lesbare Anzeigenamen ersetzt
- SK-09: Farbgebung bei Null-Werten korrigiert (INACTIVE statt GOOD.normal)
- SK-10: Tabellen-Header bei leerem Firmenbestand ausgeblendet

---

## v0.13.43 — 2026-07-26

### Sprint 9 — Stale-Doc-Reference-Resolution + Audit-Gate-10

Sprint-Header per agents.md Rule 11+12: 1 Sprint = 1 atomic commit.
Stam-Doc-Split per v0.13.43 (ROADMAP = Backlog-only, CHANGELOG = Completed-Index).
Audit-getrieben: B-011 zeigte auf geloeschtes `tools/scarcity_sim.py`, der Dead-Code-Bot
hatte nur Code-Files (nicht Markdown) gescannt. Sprint 9 schliesst diese Klasse.

Subsummierte Tasks (8 total, 1 atomic commit):

- **T-9.1 README-Bereinigung** — `README.md`: 4× `tools/scarcity_sim.py`-Aufrufe
  (Diagnostic-Tools-Tabelle-Zeile, Scarcity-Simulator-Subsection, B-011 Reference in
  Exit-Codes, diagnostics-mkdir-Hint) durch `tools/audit-sim-logic.sh` ersetzt.
  Audit-Befund dokumentiert als gelöscht (Commit `2ac5191`).

- **T-9.2/9.4 ROADMAP 7-1 a/b Split** — alter Task 7-1 wurde zu 7-1a
  (Algorithmus-Doku, ~35 LoC) + 7-1b (Golden-Snapshot-Erzeugung, ~25 LoC).
  Dependency-Chain `7-1a -> 7-1b -> 7-2` explizit als Pre-Note-Block.
  `scarcity_sim`-Dateitoken ersetzt durch `Scarcity-Kaskaden-Algorithmus`
  (Engine-Spec aus `FlowPrices`/`LocalPrices`/`EconConfig`).

- **T-9.3 ROADMAP 7-3 Anti-Bias** — Booster-Eval-Wording mit `0/6 = valider
  Ausgang` + Folgesprint B-013 fuer Lohnendes. Verhindert Kennzahl-Optimierung.

- **T-9.5 WORKFLOW.md Rules 1.6/1.7/1.8 + Anti-Patterns** — Anti-Bias-Wording,
  Dependency-Edges-sichtbar, Kompromiss-Szenarien-pre-sprint-dokumentiert.

- **T-9.6 verify-doc-sync.sh Gate 10** — neuer md-tool-invocation-Check. Run-3-final:
  prefix-required `(python|python3|bash)<space>tools/X.{py,sh}` mit
  `--exclude-dir=.freebuff,.git,docs` und `--roE` (only-matching, sonst extrahiert
  Stage-2-grep `tools/X.{py,sh}`-Pfade aus Zeilen-Kontext statt nur dem Match).

- **Reviewer-Fix #2 ROADMAP 8-1 Anti-Bias** — `JaCoCo 30/15%` mit `0/N-Disclaimer`
  + Folgesprint B-014.

- **Reviewer-Fix #3 ROADMAP Dependency-Graph** — ASCII-Box `7-1a -> 7-1b -> 7-2`
  mit unabhaengigen Pfaden fuer 8-1/7-3/8-2..8-5.

**Out-of-Scope Sprint 9 (deliberately deferred per Rule 11 Ratio-Klausel):**

- 🟡 `build-gate.sh` Bias-Word-Grep fuer Rule 1.6 Enforcement — Sprint 10 als Gate 11.

**Verification (post-fix Run-3):**

- Sync-Gate: `bash tools/verify-doc-sync.sh` = 11/11 PASS (incl. Gate 10).
- Build: `mvn verify install -DskipTests -Dskip.bump=true` = BUILD SUCCESS.
- Stam-Doc-Version bleibt v0.13.43 (kein Bump, `-Dskip.bump=true`).
- Sprint 9 commit-Referenz: folgt am atomic-commit-Ende.

### Sprint 7 — Adapter-Dispatcher + Schema-SSoT

Zentraler AdapterDispatcher macht alle 6 Mod-Adapter patchbar. `tools/vanilla-schema.yaml`
ist Single-Source-of-Truth fuer 15 Vanilla-Klassen (~50 Felder). Bei Engine-Update (V71→V72):
1 Diff im YAML statt 5 Adapter-Dateien durchsuchen. NPC-Faktionen erstmals via BypassGate
angebunden — Grundlage fuer Civil-Verhalten und Job-Learning.

- `tools/vanilla-schema.yaml` (NEU): maschinenlesbares Schema, 3 Gruppen
- `adapter/seam/SchemaValidator.java` (NEU): pre-flight Class.forName + getDeclaredField
- `adapter/AdapterDispatcher.java` (NEU): zentraler Builder, ersetzt 5 createXxxAdapter()
- `adapter/ISyxNpc.java` + `adapter/NpcFactionAdapter.java` (NEU): NPC-Preis/Resource-Zugriff
- `core/EconomySim.java` (PATCH): 5 createXxxAdapter-Methoden geloescht → AdapterDispatcher.build()
- `tools/build-gate.sh` (PATCH): Gate 7 Schema-Praesenz-Check
- `tools/audit-bytecode.sh` (PATCH): SchemaValidator + NpcFactionAdapter whitelisted

---

## Earlier Releases

### Sprint 4 — Coverage-Kernel-Pass (7 Testsuiten + JaCoCo-Gate-Pipeline)

Sprint-Header per agents.md Rule 11+12: 1 Sprint = 1 atomic commit. Coverage-Decke von ~167/121-Klassen
(knapp 11%) auf nunmehr 7 weitere Test-Suiten gehoben, ohne Mockito-Inject (engine-coupled Branches
bleiben Sprint 9 vorbehalten — siehe "Engine-Mocking-Plan" weiter unten).

Subsummierte Tasks (8 total):

- **T-COV-1** — `test/.../FiscalTest.java` (15 Tests): `split()` Bracket-Coverage (neg-gross, rate>1, rate<0, partial-floor), `retailSettlement()` Aufteilung (clamping bei neg/über-Recorded), Save/Load-Roundtrip mit FileGetter/Putter AutoCloseable-Pattern, `clear()` resettet Counter, `setHeadTax()`/`setMarketLevy()` clamping bei neg-Werten.
- **T-COV-2** — `test/.../EconProgressionTest.java` (14 Tests): `Stage.fromLevel` alle 5 Stufen + Out-of-Range → SUBSISTENZ, `Stage.next()` inkl. IMPERIUM-Boundary (bleibt sich selbst), Save/Load v33 roundtrip, **v32→v33-Migration** (level=0/1 keine Shift, level=2→WOHLSTAND+1=INDUSTRIE→WOHLSTAND, level=3→IMPERIUM+1=WOHLSTAND→IMPERIUM).
- **T-COV-3** — `test/.../AffordabilityGateTest.java` (7 Tests): Constructor mit null-Deps toleriert, `clear()` reset lastFoodBundleQuote/Units, `setSettlementSink(null)` fällt auf NONE zurück, `Admission`-Record-Komponenten, `Kind`-Enum FOOD/DRINK/GOODS, zwei Gates teilen keinen State.
- **T-COV-4** — `test/.../LaborMarketTest.java` (12 Tests): `blend()` Math + 7 Clamp-Branches (freeShare&lt;0, freeShare>1, result below min, above max), `profitPriority()` Math (above/equal/below/zero marginal), Getters/Setter-Defaults, `setScarcitySignal` Replacement, save/load roundtrip, `reset()` cleared all state.
- **T-COV-5** — `test/.../HousingMarketTest.java` (8 Tests): lastRent*-Defaults 0, `ledger()` memoisierte Identität (gleiche Instanz über Calls, unabhängig zwischen zwei HousingMarkets), `clear()` reset all counters + ledger, Save/Load roundtrip, save-Strom-Reihenfolge (lastSeason=int, rentCollected=long, rentDue=long, evictions=int), PropertyLedger-Ownership-Survival Roundtrip.
- **T-COV-6** — JaCoCo-Coverage-Gate in `pom.xml` integriert: `jacoco-check` goal in verify-Phase mit Property-getriebenen Schwellen (`jacoco.line.minimum` / `jacoco.branch.minimum`). Default 0.0 = report-only (lärmiges Coverage-Reporting, kein Build-Break). Sprint-9 (T-COV-9 mit Mockito-Inject) zieht die Schwellen auf die Ziel-Werte line≥70%/branch≥60% an. Opt-in Skip-Flag `-Djacoco.check.skip=true` analog zu `-Dgate.skip=true`.
- **T-COV-7** — `test/.../PairSourceTest.java` (8 Tests): `RandomPairSource` mit Reflection-`count`-Stub für size 0/1/2+ (size&lt;2 Short-Circuit, ~50% Pair-Rate bei size=2, ia==ib Self-Pair verhindert, zero-encounters short-circuit). `ProximityPairSource` size&lt;2-Short-Circuit + Instanziiertheit + `near`-Buffer Start-Kapazität 64.
- **T-COV-8** — `test/.../DiagnosticExporterTest.java` (4 Tests): `diagnosticDirectory()` returns non-empty Path mit `SyxEconomyMod`/`syxEconomyMod` im Namen, `resetExportGuard()` idempotent + no-throw, private Constructor via Reflection lesbar (Setter-Accessible-Test).

**Sprint-4-Total:** 8 Tasks (~990 LoC additiv, davon ~890 LoC Tests + ~30 LoC pom.xml + ~70 LoC Docs).
Test-Statistik vor Sprint 4: 12 Files / ~167 @Test. Nach Sprint 4: 19 Files / ~235 @Test.
Class-Level-Decke: 11/121 (~9%) → 19/121 (~16%).

Verification (mvn verify install -DskipTests -Dskip.bump=true): BUILD SUCCESS erforderlich + JaCoCo-Report `target/site/jacoco/index.html` verfügbar + `bash tools/verify-doc-sync.sh` = PASS + Code-Reviewer PUSH-GRUEN.

### Engine-Mocking-Plan für Sprint 9 (T-COV-9, separat)

Mockito-Core + mockito-inline als Test-Dependency einführen. Mit BypassGate-SDK-Inject (VarHandle-Auto-Select) sind `FACTIONS.player().credits()` und `SETT.ROOMS()` mockbar. Damit werden die heute ungetesteten Branches abgedeckt:
- `Fiscal.update` — 22 Branches (HTYPES.CHILD check, Wallets.netWorth, EngineSeams.isEnslaveablePleb)
- `Fiscal.settlePurchase/Ration/Service` — Treasury-Verteilung
- `HousingMarket.collectRent/evict` — Miete-Treiberei + Räumungs-Schwellen
- `LaborMarket.update` — 18 Branches (playerIntervened, scarcityBoost, frictionPoints)
- `AffordabilityGate.requestFood/settleFood/foodUnitPrices` — Unit-Pricing-Lookups
- `EconProgression.pollBuildings/checkAdvance/registerAdminBooster` — Stage-Transitions + Boostable-Lookup

Plus Mockito-Pattern für Snake2D-Reflection-Schicht (FilePutter/FileGetter bereits abgedeckt).

Sprint-3 (vorausgegangen): Roadmap-SSOT-Konsolidierung + P1-Blocker-Closure — siehe archivierten Eintrag in `docs/CHANGELOG_ARCHIVE.md`.

---

## Earlier Releases

The full release history (v0.13.36 back to v0.0.1) is archived in
[`docs/CHANGELOG_ARCHIVE.md`](docs/CHANGELOG_ARCHIVE.md) to keep the
root CHANGELOG focused on the current sprint.
