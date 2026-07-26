# SyxEconomyMod — Entwicklung & Roadmap

> **Version:** v0.13.43 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-26
>
> Stam-Doku-Synchron-Anker: `tools/verify-doc-sync.sh` (9 Checks).
> Abgeschlossene Sprints → [`CHANGELOG.md`](CHANGELOG.md).

---

## Global Task Index (Backlog)

**Legende:** 🔴 P0 · 🟠 P1 · 🟡 P2 · 🟢 P3

| Task | Prio | Kurzbeschreibung | LoC | Sprint |
|---|---|---|---|---|
| **7-1** | 🟠 P1 | Balance-CI: Golden-Snapshot-Spezifikation + `scarcity_sim`-Algorithmus dokumentieren | ~35 | 7 |
| **7-2** | 🟠 P1 | `tools/balance-smoke.sh`: CSV→Toleranz-Check→Build-Break (5%). Hängt von 7-1 ab. | ~40 | 7 |
| **7-3** | 🟡 P2 | Booster-Eval (ergebnisoffen): 6 Behaviour-Booster auf Mod-Relevanz messen | ~30 | 7 |
| **8-1** | 🟠 P1 | Mockito-Inject Coverage: Fiscal/Housing/Labor/Affordability/EconProgression + JaCoCo 30/15% | ~600 | 8 |
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

**Total:** 14 Tasks — 2×P1 (Sprint 7), 3×P2 (Sprint 8), 3×P2 (Sprint 8-Plan), 6×P3 (Backlog).

---

## Abgeschlossene Sprints (Details → CHANGELOG.md)

| Sprint | Theme | Tasks | Commit | Datum |
|---|---|---|---|---|
| **6** | Global-Audit + Freeze (7 Tasks: 6-1..6-7) | Dead-Code, Drift-Fixes, Pre-Commit-Hook | `2ac5191`, `804cbf3` | 2026-07-26 |
| **5** | Adapter-Dispatcher + Schema-SSoT (7 Tasks: 5-1..5-7) | vanilla-schema.yaml, SchemaValidator, AdapterDispatcher, ISyxNpc | `4efa7c4` | 2026-07-26 |
| **4** | Coverage + Audit + CSV-Logging (5 Tasks: 4-1..4-5) | audit-bytecode, audit-sim-logic, DebugCsv, LoggingAdapter, 4 Tests | `4efa7c4` | 2026-07-26 |
| **3** | Coverage-Kernel-Pass (8 Tasks: 3-1..3-8) | 8 Test-Klassen, JaCoCo-Gate | `a809405` | 2026-07-26 |
| **2** | Roadmap-SSOT-Konsolidierung (7 Tasks: 2-1..2-7) | agents.md Rule 13, BACKLOG mold-down | `51f8b27` | 2026-07-26 |
| **1** | Mod-Economy T5–T13 (9 Tasks: 1-1..1-9) | FlowMeter, Hunger-Hook, Classifier, AccessAutomation, Static-Audit | `c1964d2` | 2026-07-25 |
| **A** | TreasuryCrisis State-Leak Reset (3 Tasks) | TreasuryCrisis.reset(), EconomySim.clearActive() | `c1964d2` | 2026-07-25 |
| **0** | Phase A–F SDK + Adapter-Migration | BypassGate, 5 Adapter migriert, 7 Dateien gelöscht | `1442804`..`c1964d2` | 2026-07-25 |

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
