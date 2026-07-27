# SyxEconomyMod — Entwicklung & Roadmap

> **Version:** v0.13.62 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-26
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
| **8-3** | 🟡 P2 | FlowMeter: `SETT.ROOMS().ins()` für ROOM_PRODUCER_INSTANCE iterieren (B-001) | ~25 | 8 |
| **8-4** | 🟡 P2 | Oddjob-Clamp: harte Grenze via `EconConfig.oddjobMaxPay` (B-005) | ~12 | 8 |
| **8-5** | 🟡 P2 | Hungersignal → Bevölkerungs-Kopplung: MeticImmigration + Roster (B-009) | ~18 | 8 |
| *B-004* | 🟢 P3 | Vermögensklassen-Drift: WealthStats ↔ CitizenClass angleichen | ~30 | — |
| *B-002* | 🟢 P3 | AccessAutomation-Spam: Rate-Limiter für Statusmeldungen | ~6 | — |
| *B-006* | 🟢 P3 | IdentityHashMap-Migration Phase 2/3 | ~50 | — |
| *B-008* | 🟢 P3 | EngineSeams-Direkt-Calls: 31→0 | ~40 | — |
| *B-010* | 🟢 P3 | Carpenter targetWage=0 in FlowPrices | ~8 | — |
| *T22* | 🟢 P3 | Savegame-Compat-Headless-Test | ~50 | — |
| **A-01** | 🔴 P0 | ISyx\* Interfaces erweitern — 6 bestehende Interfaces um neue Methodensignaturen | ~120 | A-1 |
| **A-02** | 🔴 P0 | WarehouseAdapter erweitern — storedD, fetchingSet, getUsedSpace, crateSize, totalCrates, setSpecialAmount, moveCapacityAm | ~180 | A-1 |
| **A-03** | 🔴 P0 | TransportAdapter erweitern — efficiency, fetchTime, stationWorkers, stationProblem, resource, radiusRaw | ~160 | A-1 |
| **A-04** | 🟠 P1 | DiplomacyAdapter erweitern — coalitionAdvantage, distress, potential, proxy | ~120 | A-1 |
| **A-05** | 🟠 P1 | AIAdapter erweitern — Work/Crime-Module: WorkFarmer, WorkPolice, WorkGuard, Theft, Murder | ~100 | A-1 |
| **A-06** | 🟠 P1 | BoostingAdapter erweitern — CIVICS: SPOILAGE, MAINTENANCE, IMMIGRATION, DEFLATION, HAPPINESS, LOYALTY | ~150 | A-1 |
| **A-07** | 🟠 P1 | NpcAdapter erweitern — getStockpile, getBonus, getRequest, getRace, getCitizens, getOffensivePower | ~140 | A-1 |
| **A-08** | 🟠 P1 | ISyxTrade + VanillaTradeAdapter — ResourcePrices, TradeManager, FBUYER/FSELLER | ~200 | A-1 |
| **A-09** | 🟠 P1 | ISyxStats + VanillaStatsAdapter — StatsFood, StatsPopulation, StatsWork, StatsDisease, StatsLaw, StatsEquip | ~180 | A-1 |
| **A-10** | 🟡 P2 | ISyxRoyalty + VanillaRoyaltyAdapter — Royalty, NPCCourt, ROPINION, RTrust | ~160 | A-1 |
| **A-11** | 🟡 P2 | ISyxMaintenance + VanillaMaintenanceAdapter — MAINTENANCE, MConsumption, MRoom, ROOM_DEGRADER | ~140 | A-1 |
| **A-12** | 🟡 P2 | ISyxTime + VanillaTimeAdapter — TIME, TIMECYCLE, Seasons, Light | ~100 | A-1 |
| **A-13** | 🟡 P2 | ISyxReligion + VanillaReligionAdapter — RELIGIONS, Religion, StatsReligion | ~100 | A-1 |
| **A-14** | 🔴 P0 | EngineAccessCatalog — Zentraler Adapter-Registry + Startup-Dump (analog EconConfig) | ~200 | A-1 |
| **A-15** | 🟠 P1 | Unified Adapter Logging — LoggingAdapter.csvTrace() in alle Adapter (init + runtime) | ~80 | A-1 |
| **A-16** | 🟠 P1 | SchemaValidator + vanilla-schema.yaml erweitern — alle neuen Klassen/Felder/Methoden | ~60 | A-1 |
| **A-17** | 🟠 P1 | Stam-Docs — ARCHITECTURE.md (14→26 Dateien), ROADMAP.md, CHANGELOG.md | ~100 | A-1 |
| **UI-CENT** | ✅ Closed | **UI-Zentralisierung:** 122 GText → 10 FONTW_*-Konstanten in EconWindowBase | ~15 | 10 |
| **AUDIT-1** | 📝 Notiert | **emigrationRisk = Dead Code** — AtomicInteger nie gelesen, D-002 wirkungslos | — | 10 |

**Total:** 34 Tasks (17 bestehende + 17 Sprint A-1) — 6×Closed D-001–D-006 (→ CHANGELOG v0.13.57), 2×Closed Sprint 10 (UI-CENT, AUDIT-1), 5×Sprint 9 Active (7-1a, 7-1b, 7-2, 8-1, 7-3), 4×Sprint 9 Planned (8-2–8-5), 6×P3 Backlog.

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

| **T-GC-01** | 🟢 P3 | `tools/god-class-guard/parse_metrics.py` Per-File-Metrik-Parser (LOC/PubM/Fields/Imports, Annotation-Prefix-faehig). | ~140 | M-3 (in Sprint) |
| **T-GC-02** | 🟢 P3 | `tools/god-class-guard/parse_yaml.py` YAML-Loader mit Validation (try/except, pre-compile regex, leerer-Regex-Schutz). | ~280 | M-3 (in Sprint) |
| **T-GC-03** | 🟢 P3 | `tools/god-class-guard/emit_yaml.py` Auto-Generator fuer `tools/god-class-baselines.yml`. | ~110 | M-3 (in Sprint) |
| **T-GC-04** | 🟢 P3 | `tools/god-class-guard.sh` + `run_check.py` Master-Wrapper mit `--mode=dry\|soft\|hard`. | ~235 | M-3 (in Sprint) |
| **T-GC-05** | 🟢 P3 | `tools/god-class-baselines.yml` (auto-generiert, 19 grandfathered entries). | ~140 | M-3 (in Sprint) |
| **T-GC-06** | 🟢 P3 | `tools/god-class-guard.on-failure.md` Recovery-Anleitung (3 Pfade). | ~70 | M-3 (in Sprint) |
| **T-GC-07** | 🟢 P3 | `tools/tests/god-class-guard/run_meta_tests.sh` (4-Stub Meta-Tests + Assertions). | ~120 | M-3 (in Sprint) |
| **T-GC-08** | 🟢 P3 | `tools/build-gate.sh` Gate 9 hinzu (`SKIP_GOD_GUARD=1` Toggle). | ~25 | M-3 (in Sprint) |
| **T-GC-09** | 🟢 P3 | `pom.xml` `<execution>preflight-god-class-guard</execution>` (validate-Phase, failonerror=true). | ~25 | M-3 (in Sprint) |
| **T-GC-10** | 🟢 P3 | `tools/install-hooks.sh` Pre-Commit-Hook Schritt `[4/4]`. | ~10 | M-3 (in Sprint) |
| **T-GC-11** | 🟢 P3 | Stam-Docs integrieren: `agents.md` Rule 14, CHANGELOG M-3 Header, ARCHITECTURE Gate 9, README Build-Gates-Tabelle (4→9), GLOSSARY Tooling-Infrastruktur, ROADMAP §Global Task Index (diese Zeilen). | ~250 | M-3 (in Sprint) |
| **T-GC-12** | 🟢 P3 | M-3 Atomic Commit + `mvn verify install -DskipTests -Dskip.bump=true` + Code-Reviewer-Pass. | 0 | M-3 (in Sprint) |

## Sprint A-1 — Full Engine Access Layer

**Ziel:** Vollständige Adapter-Abdeckung aller Vanilla-Engine-Systeme. 6 bestehende Adapter erweitern + 6 neue Adapter + zentrale Katalogisierung + Unified Logging.

**Gesamtumfang:** 14 Tasks, ~3.100 LoC | **Naming:** `A-` Prefix für Adapter-Sprints

### Dependency-Chain

```
A-01 (ISyx* Interfaces erweitern) ──→ A-02..A-07 (Implementierungen, parallel)
A-08 (ISyxTrade)  ──┐
A-09 (ISyxStats)  ──┤
A-10 (ISyxRoyalty)──┼──→ A-14 (EngineAccessCatalog)
A-11 (ISyxMaintenance)┤
A-12 (ISyxTime)   ──┤
A-14 (Catalog) ──→ A-16 (SchemaValidator + vanilla-schema.yaml)
A-16 ──→ A-17 (Stam-Docs + ARCHITECTURE.md + CHANGELOG)
```

### Task-Liste — Gruppe 1: Bestehende Adapter ERWEITERN

| Task | Prio | Beschreibung | Vanilla-Ziel | Neue Methoden | LoC |
|---|---|---|---|---|---|
| **A-01** | 🔴 P0 | **ISyx\* Interfaces erweitern** — 6 bestehende Interfaces um neue Methodensignaturen erweitern (isAvailable + initOk Pattern beibehalten) | — | — | ~120 |
| **A-02** | 🔴 P0 | **WarehouseAdapter erweitern** — `storedD(RESOURCE)`, `fetchingSet(boolean)`, `getUsedSpace()`, `crateSize()`, `totalCrates()`, `setSpecialAmount(RESOURCE,int)`, `moveCapacityAm(RESOURCE)` | `StockpileInstance` | 7 | ~180 |
| **A-03** | 🔴 P0 | **TransportAdapter erweitern** — `efficiency()`, `getFetchTime()`, `getStationWorkers()`, `hasStationProblem()`, `resource()`, `radiusRaw()`, `radiusRawSet(byte)` | `TransportInstance` | 7 | ~160 |
| **A-04** | 🟠 P1 | **DiplomacyAdapter erweitern** — `coalitionAdvantage()`, `distress(Faction)`, `getPotential()`, `getProxy()` | `DipWarPlayer` | 4 | ~120 |
| **A-05** | 🟠 P1 | **AIAdapter erweitern** — Work/Crime-Module: `WorkFarmer`, `WorkPolice`, `WorkGuard`, `Theft`, `Murder` | 6 neue AI-Plan-Klassen | 6 | ~100 |
| **A-06** | 🟠 P1 | **BoostingAdapter erweitern** — CIVICS: `SPOILAGE`, `MAINTENANCE`, `IMMIGRATION`, `DEFLATION`, `HAPPINESS`, `LOYALTY` | `BOOSTABLES.CIVICS()` | 12 | ~150 |
| **A-07** | 🟠 P1 | **NpcAdapter erweitern** — `getStockpile()`, `getBonus()`, `getRequest()`, `getRace()`, `getCitizens(Race)`, `getOffensivePower()` | `FactionNPC` | 6 | ~140 |

### Task-Liste — Gruppe 2: NEUE Adapter erstellen

| Task | Prio | Beschreibung | Vanilla-Ziel | Methoden | LoC |
|---|---|---|---|---|---|
| **A-08** | 🟠 P1 | **ISyxTrade + VanillaTradeAdapter** — `ResourcePrices.getWorldPrice()`, `TradeManager.toll/tarif()`, `FBUYER/FSELLER`-Zugriff | `ResourcePrices`, `TradeManager`, `FBUYER`, `FSELLER` | 6 | ~200 |
| **A-09** | 🟠 P1 | **ISyxStats + VanillaStatsAdapter** — `StatsFood`, `StatsPopulation`, `StatsWork`, `StatsStored`, `StatsDisease`, `StatsLaw`, `StatsEquip`, `StatsService` | 7 Stat-Klassen | 8 | ~180 |
| **A-10** | 🟡 P2 | **ISyxRoyalty + VanillaRoyaltyAdapter** — `Royalty`, `NPCCourt`, `ROPINION`, `RTrust`, `ROpper` | 5 Royalty-Klassen | 6 | ~160 |
| **A-11** | 🟡 P2 | **ISyxMaintenance + VanillaMaintenanceAdapter** — `MAINTENANCE`, `MConsumption`, `MRoom`, `ROOM_DEGRADER` | 4 Maintenance-Klassen | 5 | ~140 |
| **A-12** | 🟡 P2 | **ISyxTime + VanillaTimeAdapter** — `TIME`, `TIMECYCLE`, `Seasons`, `Light` | 4 Time-Klassen | 5 | ~100 |
| **A-13** | 🟡 P2 | **ISyxReligion + VanillaReligionAdapter** — `RELIGIONS`, `Religion`, `StatsReligion` | 3 Religion-Klassen | 4 | ~100 |

### Task-Liste — Gruppe 3: Katalogisierung & Infrastructure

| Task | Prio | Beschreibung | LoC |
|---|---|---|---|
| **A-14** | 🔴 P0 | **EngineAccessCatalog** — Zentrale Klasse die ALLE Adapter-Zugriffe katalogisiert (analog `EconConfig`). Jeder Adapter registriert sich: Interface-Name, Vanilla-Zielklasse, Zugriffsmethode, Status, initOk. Dump via `EventLog` bei Startup. | ~200 |
| **A-15** | 🟠 P1 | **Unified Adapter Logging** — `LoggingAdapter.csvTrace()` in ALLE Adapter (init + runtime). Bestehende `EventLog.log()` ergänzen, nicht ersetzen. | ~80 |
| **A-16** | 🟠 P1 | **SchemaValidator + vanilla-schema.yaml erweitern** — Alle neuen Klassen/Felder/Methoden registrieren. | ~60 |
| **A-17** | 🟠 P1 | **Stam-Docs** — ARCHITECTURE.md (14→26 Dateien), ROADMAP.md, CHANGELOG.md Sprint A-1 Header. | ~100 |

### Adapter-Coverage nach Sprint A-1

| Kategorie | Interface | Vorher | Nachher | Vanilla-Ziel |
|---|---|---|---|---|
| Warehouse | ISyxWarehouse | 2 Methoden | 9 | `StockpileInstance` (storing, fetching, limits, crates) |
| Transport | ISyxTransport | 3 Methoden | 10 | `TransportInstance` (distance, efficiency, fetch, radius) |
| Diplomacy | ISyxDiplomacy | 4 Methoden | 8 | `DipWarPlayer` (war, coalition, distress, potential) |
| AI | ISyxAI | 5 Methoden | 11 | 12 AI-Plan-Klassen (food, oddjob, market, work, crime) |
| Boosting | ISyxBoosting | 2 Methoden | 14 | `BOOSTABLES.CIVICS()` (12 Boostables) + `ROOMS` |
| NPC | ISyxNpc | 8 Methoden | 14 | `FactionNPC` (stockpile, bonus, race, military) |
| Trade | **ISyxTrade** | ❌ | 6 | `ResourcePrices`, `TradeManager`, `FBUYER/FSELLER` |
| Stats | **ISyxStats** | ❌ | 8 | 7 Stat-Klassen (food, pop, work, disease, law, equip) |
| Royalty | **ISyxRoyalty** | ❌ | 6 | `Royalty`, `NPCCourt`, `ROPINION`, `RTrust` |
| Maintenance | **ISyxMaintenance** | ❌ | 5 | `MAINTENANCE`, `MConsumption`, `MRoom` |
| Time | **ISyxTime** | ❌ | 5 | `TIME`, `TIMECYCLE`, `Seasons`, `Light` |
| Religion | **ISyxReligion** | ❌ | 4 | `RELIGIONS`, `Religion`, `StatsReligion` |
| **Katalog** | **EngineAccessCatalog** | ❌ | — | Zentraler Adapter-Registry + Startup-Dump |
| **Logging** | **Unified** | EventLog nur | csvTrace | Alle Adapter via `LoggingAdapter` |

### Freeze-Override

`adapter/` ist seit Sprint 6 ❄️ FROZEN. Sprint A-1 hebt das Freeze **temporär** auf — der Zweck ist gerade, die Adapter-Schicht vollständig zu machen. Nach Sprint A-1 wird `adapter/` wieder eingefroren.

### Definition of Done

1. `mvn verify install -DskipTests` — BUILD SUCCESS
2. `mvn test` — alle bestehenden Tests grün
3. `bash tools/verify-doc-sync.sh` — PASS
4. Jeder Adapter loggt init-Status via `LoggingAdapter.csvTrace("ADAPTER", "SEAM", ...)`
5. `EngineAccessCatalog.dump()` zeigt vollständige Adapter-Übersicht bei Startup
6. `tools/vanilla-schema.yaml` aktualisiert für alle neuen Klassen/Felder
7. ARCHITECTURE.md: Adapter-Layer-Sektion aktualisiert (14→26 Dateien, 6→12 Interfaces)

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
