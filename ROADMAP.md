# SyxEconomyMod — Entwicklung & Roadmap

> **Version:** v0.13.67 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-26
>
> Stam-Doku-Synchron-Anker: `tools/verify-doc-sync.sh` (9 Checks).
> Abgeschlossene Sprints → [`CHANGELOG.md`](CHANGELOG.md).

---

## Global Task Index (Backlog)

**Legende:** 🔴 P0 · 🟠 P1 · 🟡 P2 · 🟢 P3

| Task | Prio | Kurzbeschreibung | LoC | Sprint |
|---|---|---|---|---|
| **7-1a** | 🟠 P1 | Balance-CI: Scarcity-Kaskaden-Algorithmus dokumentieren (Spezifikation aus `FlowPrices.java:scarcityMultiplier()` + `LocalPrices.java:scarcity()` + `EconConfig.scarcityElasticityUp/Down`; Konstanten UP=0.8 / DOWN=1.375 / Clamp=100. **Reihenfolge:** 7-1a vor 7-1b, 7-1b vor 7-2. | ~35 | 9 |
| **7-1b** | 🟠 P1 | Balance-CI: Golden-Snapshot-Erzeugung gegen `EconConfig`-Formeln (Ist-Output für 7-2-Toleranzcheck). **Reihenfolge:** 7-1a (Algorithmus) vor 7-1b. **Hinweis:** NICHT durch Wiederbelebung der gelöschten `tools/scarcity_sim.py` — Build aus den vorhandenen Engine-Klassen. | ~25 | 9 |
| **7-2** | 🟠 P1 | `tools/balance-smoke.sh`: CSV→Toleranz-Check→Build-Break (5%). Hängt von 7-1 ab. | ~40 | 7 |
| **7-3** | 🟡 P2 | ✅ **Booster-Eval abgeschlossen (v0.13.51).** 12 Boostable-Kategorien evaluiert (402 total, 3 genutzt, 399 ungenutzt). **Ergebnis: 2/6 Behaviour-Booster wirtschaftsrelevant** (LOYALTY=genutzt via GiniConsequences, HAPPI=Konsum-Multiplikator). LAWFULNESS/SUBMISSION/HAPPI_SLAVES/SANITY für zukünftige Vektoren (Kriminalität, Sklaven-Ökonomie). Siehe §Booster-Eval unten. | ~30 | 9 |
| **8-1** | 🟠 P1 | Mockito-Inject Coverage: Fiscal/Housing/Labor/Affordability/EconProgression + JaCoCo line=30% / branch=15%. **Anti-Bias-Wording (Rule 1.6):** Schwellen sind Ziel-Werte, keine Pflicht-Quoten. Wenn Coverage nach Mockito-Inject unter Schwellen bleibt → Sprint-Abschluss mit dokumentiertem Befund, kein Pflicht-Sprint-Folgesprint. `ggf.`-Pfad als B-014 (separater Sprint). **Reihenfolge:** Unabhängig vom 7-1a/b/2-Pfad; Mess-Basis für 7-3. | ~600 | 9 |
| **8-2** | 🟡 P2 | 5 ungetestete Klassen: NpcFactionAdapter, AdapterDispatcher, SchemaValidator, DebugCsv, LoggingAdapter | ~200 | 8 |
| **8-3** | ✅ Closed (v0.13.64) | FlowMeter: `SETT.ROOMS().ins()` für ROOM_PRODUCER_INSTANCE iterieren (B-001) | ~25 | 8 |
| **8-4** | ✅ Closed (v0.13.64) | Oddjob-Clamp: harte Grenze via `EconConfig.oddjobWageCeilingRatio` (B-005) | ~12 | 8 |
| **8-5** | ✅ Closed (v0.13.64) | Hungersignal → Bevölkerungs-Kopplung: MeticImmigration + Roster (B-009) | ~18 | 8 |
| *B-004* | ✅ Closed (v0.13.61) | Vermögensklassen-Drift: WealthStats ↔ CitizenClass angleichen | ~30 | — |
| *B-002* | ✅ Closed (v0.13.57) | AccessAutomation-Spam: Rate-Limiter für Statusmeldungen | ~6 | — |
| *B-006* | 🟢 P3 | IdentityHashMap-Migration Phase 2/3 | ~50 | — |
| *B-008* | ✅ Closed (v0.13.66) | EngineSeams-Direkt-Calls: 55→0 (Phase 1: 25, Phase 2: 30 — alle migriert auf EngineMirror mit Fallback) | ~40 | — |
| *B-010* | ✅ Closed (v0.13.46) | Carpenter targetWage=0 in FlowPrices | ~8 | — |
| *T22* | 🟢 P3 | Savegame-Compat-Headless-Test | ~50 | — |
| **A-01** | ✅ Closed (v0.13.64) | **EngineLevers.java** — Config-Toggles pro Vanilla-Zugriff (~80 Felder, analog EconConfig) | ~200 | A-1 |
| **A-02** | ✅ Closed (v0.13.64) | **IRoomAccess + RoomAccessImpl** — Stockpile, Transport, Room-Iteration, Service (bündelt ISyxWarehouse + ISyxTransport + EngineSeams) | ~500 | A-1 |
| **A-03** | ✅ Closed (v0.13.64) | **IFactionAccess + FactionAccessImpl** — NPC, Diplomacy, Trade, Royalty, Player (bündelt ISyxDiplomacy + ISyxNpc + public API) | ~600 | A-1 |
| **A-04** | ✅ Closed (v0.13.64) | **IHumanoidAccess + HumanoidAccessImpl** — AI-Plans (12), Stats, Boosting (bündelt ISyxAI + ISyxBoosting + EngineSeams) | ~500 | A-1 |
| **A-04b** | ✅ Closed (v0.13.64) | **IStatsAccess + remaining** — Maintenance, Time, Religion, Weather, Tourism, Events | ~300 | A-1 |
| **A-05** | ✅ Closed (v0.13.64) | **EngineMirror.java + AdapterDispatcher + Stam-Docs** — Zentrale Fassade, ersetzt EngineSeams graduell | ~400 | A-1 |
| **UI-CENT** | ✅ Closed | **UI-Zentralisierung:** 122 GText → 10 FONTW_*-Konstanten in EconWindowBase | ~15 | 10 |
| **AUDIT-1** | 📝 Notiert | **emigrationRisk = Dead Code** — AtomicInteger nie gelesen, D-002 wirkungslos | — | 10 |

**Total:** 35 Tasks (17 bestehende + 6 Sprint A-1 + 12 Sprint M-3) — 6×Closed D-001–D-006 (→ CHANGELOG v0.13.57), 2×Closed Sprint 10 (UI-CENT, AUDIT-1), 5×Sprint 9 Active (7-1a, 7-1b, 7-2, 8-1, 7-3), 4×Sprint 9 Planned (8-2–8-5), 12×Sprint M-3 Planned, 6×P3 Backlog.

**Sprint 9 Dependency-Edges (Rule 1.7 Pre-Note):**

```
  7-1a (Algorithmus-Doku, ~35 LoC)  -->  7-1b (Snapshot-Erzeugung, ~25 LoC)  -->  7-2 (balance-smoke Gate)
  8-1 (Mockito-Coverage) ist unabhaengig vom 7-1a/b/2-Pfad
  7-3 (Booster-Eval) nutzt 8-1 als Mess-Basis, sonst unabhaengig
  8-2..8-5 (Probe-Objekte + B-001/FlowMeter + B-005/Oddjob + B-009/Hunger) sind unabhaengig
  D-001 (Food-Price-Hyperinflation) --> D-002 (Emigration-Kaskade) — Symptom-Reihenfolge
  D-001 (PriceCap) --> D-003 (Carpenter Cold-Start) — Preisdeckel fixt Profit=0 erst
  D-004 (_WOOD-Broken-Link) ist unabhaengig von D-001..D-003
  D-005 (Wealth-Concentration) ist unabhaengig, aber Ergebnis von D-001 cascade
  D-006 (UI-Struktur-Verifikation) ist unabhaengig von allen D-Tasks
```

Maschinenlesbare Validation der Dep-Edges ist Sprint-10-Folgeaufgabe (Gate 11 in eigenem Folge-Sprint).

---

## Sprint A-1 — EngineMirror: Full Engine Access Layer (→ CHANGELOG v0.13.64)

**Ziel:** EIN zentraler EngineMirror der ALLE Vanilla-Zugriffe bündelt — private (via BypassGate)
und public (direkt). Jeder Zugriff wird katalogisiert, geloggt und mod-intern konfigurierbar.
Version-gebunden für V71.44. SDK-Generic kommt später (alle 4–6 Monate Vanilla-Update).

**Architektur:** Hybride Fassade (Option D):
- **Private Zugriffe** → bestehende ISyx\* Adapter via BypassGate SDK (bleiben wie sie sind)
- **Public Zugriffe** → direkte Compilezeit-Links (SETT, STATS, TIME, FACTIONS etc.)
- **Config** → `EngineLevers.java` (analog `EconConfig` — Toggle pro Zugriff)
- **Logging** → `LoggingAdapter.csvTrace()` in jedem Mirror-Method
- **Katalog** → `EngineMirror.java` als zentrale Fassade mit Sub-Interfaces

**Gesamtumfang:** 6 Tasks, ~2.500 LoC | **Naming:** `A-` Prefix für EngineMirror-Sprints

### Dependency-Chain

```
A-01 (EngineLevers Config)      ──→ A-02..A-04b (Sub-Interfaces, parallel)
A-02 (RoomAccess)               ──┐
A-03 (FactionAccess)            ──┤
A-04 (HumanoidAccess)           ──┼──→ A-05 (EngineMirror Fassade + Migration)
A-04b (StatsAccess + remaining) ──┘
```

### Task-Liste

| Task | Prio | Beschreibung | Vanilla-Ziel | LoC |
|---|---|---|---|---|
| **A-01** | ✅ Closed (v0.13.64) | **EngineLevers.java** — Config-Klasse analog `EconConfig`. Jeder Vanilla-Zugriff hat einen `boolean`-Toggle (default true). ~80 Felder für V71.44. Startup-Dump via `LoggingAdapter`. | — | ~200 |
| **A-02** | ✅ Closed (v0.13.64) | **IRoomAccess + RoomAccessImpl** — Stockpile (storedD, fetchingSet, getUsedSpace, crateSize, totalCrates, setSpecialAmount, storingSet), Transport (distance, efficiency, fetchTime, stationWorkers, resource, radius), Room-Iteration (SETT.ROOMS().ins(), EATERIES, CANTEENS, HOME, CHAMBER, JANITOR), Service-Metriken. Bündelt ISyxWarehouse + ISyxTransport + EngineSeams-Room-Aufrufe. | `StockpileInstance`, `TransportInstance`, `SETT.ROOMS()` | ~500 |
| **A-03** | ✅ Closed (v0.13.64) | **IFactionAccess + FactionAccessImpl** — NPC (preise, treasury, stockpile, bonus, request, race, citizens, military), Diplomacy (war power, coalition, distress, willing, potential, proxy), Trade (worldPrice, toll, tariff, buyer/seller), Royalty (opinion, trust), Player (credits, tech, levels). Bündelt ISyxDiplomacy + ISyxNpc + public API. | `FactionNPC`, `DipWarPlayer`, `FACTIONS`, `ResourcePrices`, `TradeManager`, `Royalty` | ~600 |
| **A-04** | ✅ Closed (v0.13.64) | **IHumanoidAccess + HumanoidAccessImpl** — AI-Plan-Erkennung (12 Pläne: food, oddjob, market, work, crime), Stats (hunger, religion, work, employment), Boosting (alle CIVICS + BEHAVIOUR + PHYSICS), Entity-Metriken. Bündelt ISyxAI + ISyxBoosting + EngineSeams-Humanoid-Aufrufe. | `Humanoid`, `AIPLAN`, `BOOSTABLES`, `STATS` | ~500 |
| **A-04b** | 🟠 P1 | **IStatsAccess + remaining Sub-Interfaces** — Maintenance (MAINTENANCE, MConsumption, MRoom, ROOM_DEGRADER), Time (TIME, TIMECYCLE, Seasons), Religion (RELIGIONS, StatsReligion), Weather, Tourism, Events. Alles was nicht in A-02..A-04 passt. | `MAINTENANCE`, `TIME`, `RELIGIONS`, `WEATHER` | ~300 |
| **A-05** | 🔴 P0 | **EngineMirror.java + AdapterDispatcher-Erweiterung + Stam-Docs** — Zentrale Fassade: `EngineMirror.initialize(AdapterBundle)`, `EngineMirror.api().rooms()/.factions()/.humanoids()/.stats()`. Ersetzt `EngineSeams` graduell. Logging in JEDEM Mirror-Method. ARCHITECTURE.md + CHANGELOG.md. | — | ~400 |

### Architektur-Diagramm

```
╔══════════════════════════════════════════════════════════════╗
║  EngineLevers.java — Config-Toggles pro Zugriff (~80 Felder) ║
╚═════════════════════════════════════╤════════════════════════╝
                                       ▼
╔══════════════════════════════════════════════════════════════╗
║  EngineMirror.java — Zentrale Fassade (initialize + api())    ║
║  ├── .rooms()      → IRoomAccess (Stockpile, Transport, SETT) ║
║  ├── .factions()   → IFactionAccess (NPC, Diplo, Trade, Roy)  ║
║  ├── .humanoids()  → IHumanoidAccess (AI, Stats, Boosting)    ║
║  └── .stats()      → IStatsAccess (Maint, Time, Rel, Weather) ║
╚═════════════════════════════════════╤════════════════════════╝
                                       ▼
╔══════════════════════════════════════════════════════════════╗
║  Implementierungen — Hybrid: BypassGate (private) + direkt    ║
║  RoomAccessImpl, FactionAccessImpl, HumanoidAccessImpl, ...   ║
║  Jede Methode: if (!EngineLevers.xxx) return; + csvTrace()    ║
╚═════════════════════════════════════╤════════════════════════╝
                                       ▼
╔══════════════════════════════════════════════════════════════╗
║  Vanilla Engine (V71.44) — 2.443 Java-Files                   ║
║  Private: BypassGate/VarHandle/MethodHandle (3-6× schneller)  ║
║  Public:  Compilezeit-Links (SETT, STATS, TIME, FACTIONS)     ║
╚══════════════════════════════════════════════════════════════╝
```

### Vanilla-Coverage nach Sprint A-1

| Sub-Interface | Zugriffe | Vanilla-Ziel |
|---|---|---|
| **IRoomAccess** | ~30 | Stockpile (storedD, storing, fetching, crates, limits, space), Transport (distance, efficiency, fetch, radius, resource), Room-Iteration (ins, EATERIES, CANTEENS, HOME, CHAMBER, JANITOR), Service-Metriken |
| **IFactionAccess** | ~25 | NPC (preise, treasury, stockpile, bonus, request, race, citizens, military), Diplomacy (war, coalition, distress, willing, potential), Trade (worldPrice, toll, tariff, buyer/seller), Royalty (opinion, trust), Player (credits) |
| **IHumanoidAccess** | ~25 | AI-Plans (12 Klassen), Stats (hunger, religion, work, employment), Boosting (12 CIVICS + 6 BEHAVIOUR + 6 PHYSICS), Entity-Metriken |
| **IStatsAccess** | ~15 | Maintenance (consumption, room, degrader), Time (time, seasons, light), Religion (religions, stats), Weather, Tourism, Events |
| **Total** | **~95** | **Alle Vanilla-Zugriffe die das Mod braucht, zentral katalogisiert** |

### Freeze-Override

`adapter/` ist seit Sprint 6 ❄️ FROZEN. Sprint A-1 hebt das Freeze **temporär** auf.
Die bestehenden ISyx\* Adapter werden in die EngineMirror-Implementierungen
**integriert** (nicht ersetzt) — die Adapter bleiben als interne Detail-Klassen.
Nach Sprint A-1 wird `adapter/` + `core/EngineMirror*` wieder eingefroren.

### Definition of Done

1. `mvn verify install -DskipTests` — BUILD SUCCESS
2. `mvn test` — alle bestehenden Tests grün
3. `bash tools/verify-doc-sync.sh` — PASS
4. `EngineMirror.api()` liefert vollständige Fassade mit 4 Sub-Interfaces
5. `EngineLevers` hat ~80 Toggles, alle default `true`
6. Jeder Mirror-Method loggt via `LoggingAdapter.csvTrace("MIRROR", ...)`
7. `EngineMirror.dump()` zeigt vollständige Zugriffs-Übersicht bei Startup
8. ARCHITECTURE.md aktualisiert mit EngineMirror-Diagramm

---

## Sprint M-1 — WarehouseMarket God-Class-Sanierung (→ CHANGELOG v0.13.61)

**T-101..T-108** (8 Tasks) — 1 God-Class (1.902 LOC) → 6 Engines + 1 SharedState + 1 Facade (320 LOC)

| Task | Engine | LOC | Status |
|---|---|---|---|
| T-101 B-001 | MarketSharedState | 51 | ✅ Closed |
| T-102 | WholesaleEngine | 553 | ✅ Closed |
| T-103 | CrownTitleEngine | 200 | ✅ Closed |
| T-104 | RetailSyncEngine | 200 | ✅ Closed |
| T-105 | AutoProcurementEngine | 175 | ✅ Closed |
| T-106 | MarketMaintenanceEngine | 260 | ✅ Closed |
| T-107 | MarketTaxEngine | 60 | ✅ Closed |
| T-108 | Save V8 + Facade Cleanup | — | ✅ Closed |
| **Total** | **8 Dateien** | **1.499** | **BUILD SUCCESS** |

Offene Punkte: `mvn test` Integrationstest, `WarehouseMarketIsolationTest` FORMAT-8-Migration.
Siehe `docs/superpowers/specs/HANDOFF_M1.md`.

---

## Sprint M-3 — God-Class-Guard CI-Tooling (→ CHANGELOG v0.13.61)

**T-GC-01..T-GC-12** (12 Tasks) — 7 Tools + YAML-Baseline + Build-Gate Gate 9 + Pre-Commit-Hook + Stam-Docs

| Task | Tool/Datei | LOC | Status |
|---|---|---|---|
| T-GC-01 | `tools/god-class-guard/parse_metrics.py` — Metrik-Parser | ~140 | ✅ Closed (v0.13.61) |
| T-GC-02 | `tools/god-class-guard/parse_yaml.py` — YAML-Loader | ~280 | ✅ Closed (v0.13.61) |
| T-GC-03 | `tools/god-class-guard/emit_yaml.py` — Auto-Generator | ~110 | ✅ Closed (v0.13.61) |
| T-GC-04 | `tools/god-class-guard.sh` + `run_check.py` — Master-Wrapper | ~235 | ✅ Closed (v0.13.61) |
| T-GC-05 | `tools/god-class-baselines.yml` — Baseline (19 entries) | ~140 | ✅ Closed (v0.13.61) |
| T-GC-06 | `tools/god-class-guard.on-failure.md` — Recovery-Anleitung | ~70 | ✅ Closed (v0.13.61) |
| T-GC-07 | `tools/tests/god-class-guard/run_meta_tests.sh` — Meta-Tests | ~120 | ✅ Closed (v0.13.61) |
| T-GC-08 | `tools/build-gate.sh` Gate 9 (`SKIP_GOD_GUARD=1` Toggle) | ~25 | ✅ Closed (v0.13.61) |
| T-GC-09 | `pom.xml` preflight Execution (validate-Phase) | ~25 | ✅ Closed (v0.13.61) |
| T-GC-10 | `tools/install-hooks.sh` Schritt [4/4] | ~10 | ✅ Closed (v0.13.61) |
| T-GC-11 | Stam-Docs: agents.md Rule 14, CHANGELOG, ARCHITECTURE, README, GLOSSARY, ROADMAP | ~250 | ✅ Closed (v0.13.61) |
| T-GC-12 | Atomic Commit + Build + Review | 0 | ✅ Closed (v0.13.61) |
| **Total** | **12 Dateien** | **~1.405** | **✅ Closed (v0.13.61)** |

---

## Sprint 10 — Diagnostik-Fixes + UI-Zentralisierung (→ CHANGELOG v0.13.57)

**D-001–D-006** (6 Tasks) + **UI-Zentralisierung** + **Dead-Code-Audit** → alle in CHANGELOG.md §v0.13.57 dokumentiert.

---

## Booster-Eval (7-3, v0.13.51)

**402 Boostables im Spiel, 3 vom Mod genutzt, 399 ungenutzt.**

### Genutzte (3)
| Boostable | Mod-Stelle |
|---|---|
| `CIVICS.GOV` | `EconProgression` — Admin-Boost pro Wirtschaftsstufe |
| `BEHAVIOUR.LOYALTY` | `GiniConsequences` — −50% bei Tier-5-TreasuryCrisis |
| `RATES.HUNGER` | Indirekt via `AffordabilityGate` + `GrainDole` |

### Wirtschaftsrelevant, ungenutzt (Tier 1 — ~40)
| Kategorie | Boostables | Wirtschaftshebel |
|---|---|---|
| `BEHAVIOUR.HAPPI` | 1 | Konsum-Multiplikator: glückliche Bürger geben mehr Geld aus → `WealthHappiness` koppeln |
| `BEHAVIOUR.SANITY` | 1 | Psychische Gesundheit → Arbeitsproduktivität → `FirmLedger.profit` modifizieren |
| `PHYSICS.HEALTH` | 1 | Krankheitsrate → Workforce-Ausfall → `Roster` + `LaborMarket` beeinflussen |
| `ACTIVITY.*` | ~5 | Arbeitsgeschwindigkeit → `FlowMeter.supplyPerDay` skalieren |
| `ROOM_*` | ~35 | Pro-Industrie-Produktionseffizienz via `ISyxBoosting` → `FirmLedger` |

### Zukunftsvektoren (Tier 2 — ~30)
| Kategorie | Boostables | Vektor |
|---|---|---|
| `BEHAVIOUR.LAWFULNESS` | 1 | Kriminalität → Armut → Wirtschaft (Sicherheits-Vektor) |
| `BEHAVIOUR.SUBMISSION` | 1 | Autoritäre Kontrolle → `CorveeController` + `DebtBondage` (Rassismus-Vektor) |
| `BEHAVIOUR.HAPPI_SLAVES` | 1 | Sklaven-Ökonomie → `payWagesToSlaves`-Hebel (Rassismus-Vektor) |
| `NOBLE.*` | 6 | Adelsverhalten → Steuerpolitik, Kriegsentscheidungen (Diplomacy-Vektor) |
| `PHYSICS.DEATH_AGE` | 1 | Rentenalter → `EconConfig.ticksPerGameDay`-Kopplung (Demographie-Vektor) |
| `PHYSICS.REPRODUCTION_SPEED` | 1 | Bevölkerungswachstum → `MeticImmigration`-Alternative |
| `CIVICS.*` | ~15 | Steuersätze, Handelspolitik, Bürgerrechte → `Fiscal` + `Taxes` |
| `RATES.*` | ~4 | Hunger/Durst/Schlaf-Raten → `FoodPlanController` + `ServicePlanController` |

### Nicht wirtschaftsrelevant (Tier 3 — ~329)
| Kategorie | Boostables | Grund |
|---|---|---|
| `PHYSICS.MASS/STAMINA/SPEED/ACCELERATION` | 4 | Kampf-Physik, kein Wirtschaftshebel |
| `PHYSICS.RESISTANCE_HOT` | 1 | Klima, kein Wirtschaftshebel |
| `BATTLE.*` | ~10 | Kampfwerte |
| `EQUIP_LEVEL_TOOL_*` | 32 | Ausrüstungs-Level |
| `WORLD_PRODUCTION/BUILDING_*` | ~20 | Weltkarten-Ebene, nicht Siedlungs-Ebene |
| Restliche `ROOM_*` / `CONSUMPTION_*` | ~262 | Sub-Kategorien innerhalb der 35 Industrien |

**Fazit:** Von 399 ungenutzten Boostables sind ~40 direkt wirtschaftsrelevant (Tier 1), ~30 für Zukunftsvektoren (Tier 2), ~329 nicht wirtschaftsrelevant (Tier 3). Keine blinde Integration — jeder Boostable braucht einen konkreten Mod-Mechanismus. Nächster Schritt: `BEHAVIOUR.HAPPI` → `WealthHappiness`-Kopplung als isolierter Mini-Sprint (B-013).

---

## Definition of Done

1. `mvn verify install -DskipTests` — 7/7 Gates
2. `mvn test` — 296 Tests, 0 Fail
3. `bash tools/verify-doc-sync.sh` — 9 Checks PASS
4. Pre-Commit-Hook: `.git/hooks/pre-commit → tools/build-gate.sh`

---

## Freeze-Status (seit Sprint 6)

| Schicht | Status |
|---|---|
| `core/`, `ui/`, `adapter/` | ❄️ **FROZEN** |
| `tools/vanilla-schema.yaml` | ✅ Engine-Updates (V72: 1 Diff) |
| `EconConfig.java` | ✅ Balancing-Parameter |
| `test/` | ✅ Neue Tests |
