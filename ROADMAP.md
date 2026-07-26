# SyxEconomyMod — Entwicklung & Roadmap

> **Version:** v0.13.55 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-26
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
| **D-001** | ✅ Closed (6f4588d) | **Food-Price-Hyperinflation fixen.** `foodPriceCapMultiplier=6.0` (anker-relativ). FlowPrices.enforceCap() nach refresh(), LocalPrices-Defense-in-Depth. **Ergebnis:** BREAD max 6× Anker (468 statt 6248), alle Food-Ressourcen gecappt. | ~20 | 10 |
| **D-002** | ✅ Closed (6f4588d) | **Emigration-Kaskade bei median_wealth=0.** Rate 0.0001→0.00003 (1%/Tag statt 3%/Tag). Polish: Population-Floor-Guard (`roster.size()>=20`) gegen Frühspiel-Kleinstadt-Death-Spirale. Dependency: D-001 zuerst (Emigration ist Symptom der Inflation). | ~18 | 10 |
| **D-003** | ✅ Closed (6f4588d) | **Carpenter Cold-Start nach Save/Load.** `FirmLedger.SAVE_VERSION_FIRMS 1→2`. HillState (bestTarget, bestProfit, direction, initialized) persistiert — null-safe via sentinel values. Backward-compat: v1-Saves laden ohne HillState, Cold-Start-Grace greift im nächsten Tick. | ~30 | 10 |
| **D-004** | ✅ Closed (6f4588d) | **_WOOD-Preis-Inversion: Broken-Link-Analyse.** `FlowPrices.effectiveCoverage()`: `effectiveStock = supplyPerDay>0 ? stock : 0.0` — Inflow-Check statt Symptom-Clamp. Physischer Lagerbestand zählt nur bei Produktions-Zufluss. | ~25 | 10 |
| **D-005** | ✅ Closed (6f4588d) | **Wealth-Concentration-Clamp (Gini 0.95+).** `incomeCarry` gecappt via `guildSurplusMinProfitPerWorker × workerCount`. Pro-Firma statt absolut — skaliert mit Firmengröße. Follow-up: medianWealth-basierter Cap für große Populationen. | ~15 | 10 |
| **D-006** | ✅ Closed (6f4588d) | **UI-Struktur-Verifikation.** DebugTab permanent in `TABS[]` (nicht mehr conditional). ARCHITECTURE.md aktualisiert: 6 Tabs, DebugTab permanent sichtbar. Stam-Docs synchron. | ~15 | 10 |

**Total:** 21 Tasks — 6×Closed (D-001–D-006, 6f4588d), 5×Sprint 9 Active (7-1a, 7-1b, 7-2, 8-1, 7-3), 4×Sprint 9 Planned (8-2–8-5), 6×P3 Backlog.

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
