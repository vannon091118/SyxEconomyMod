# SyxEconomyMod — Entwicklung & Roadmap

> **Version:** v0.13.42 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-26
>
> Stam-Doku-Synchron-Anker: Die obenstehende Version MUSS identisch mit `pom.xml` `<version>` sein.
> `tools/verify-doc-sync.sh` validiert dies (9 Checks inkl. YAML-Schema + YAML↔Java).

---

## Global Task Index (priorisiert)

**Legende:** 🔴 P0 = Crash/Datenverlust · 🟠 P1 = Broken Feature · 🟡 P2 = Papercut · 🟢 P3 = Nice-to-have · ⚪ Closed

| ID | Prio | Task | LoC | Sprint | Status |
|---|---|---|---|---|---|
| **T-COV-9** | 🟠 P1 | Mockito-Inject für engine-coupled Branches + JaCoCo auf 30/15% | ~600 | 10 | Planned |
| **B-011** | 🟠 P1 | Balance-CI-Gate: `tools/scarcity_sim.py`-Algorithmus dokumentieren → `tools/balance-smoke.sh` Golden-Snapshot-Spezifikation (5%-Toleranz). **Snapshot-Erzeugung ist Teil dieses Tasks.** Output: `tools/balance-smoke.sh` kann gegen einen validen CSV-Referenz-Datensatz rechnen. | ~35 | 9 | Planned |
| **BAL-1** | 🟠 P1 | `tools/balance-smoke.sh`: CSV-Snapshot laden, Gini/Steuer/Treasury gegen Toleranz prüfen, Build-Break bei 5% Drift. **Abhängig von B-011 (Snapshot muss existieren).** Ohne B-011: Leer-Start mit Warnung, kein Fail. | ~40 | 9 | Planned |
| **BOOST-1** | 🟡 P2 | Booster-Eval (ergebnisoffen): 6 Behaviour-Booster (LAWFULNESS/SUBMISSION/HAPPI/HAPPI_SLAVES/SANITY/LOYALTY) auf Mod-Mechanik-Relevanz messen. **Valides Ergebnis: 'keiner lohnt sich' oder 'X,Y,Z sind relevant'.** Keine Einbau-Garantie — Messung, nicht Zielvorgabe. | ~30 | 9 | Planned |
| **T-COV-5** | 🟡 P2 | 5 ungetestete Klassen: NpcFactionAdapter, AdapterDispatcher, SchemaValidator, DebugCsv, LoggingAdapter | ~200 | 10 | Planned |
| **B-001** | 🟡 P2 | FlowMeter: `SETT.ROOMS().ins()` für ROOM_PRODUCER_INSTANCE iterieren | ~25 | 10 | Planned |
| **B-005** | 🟡 P2 | Oddjob-Clamp: harte Grenze via `EconConfig.oddjobMaxPay` | ~12 | 10 | Planned |
| **B-009** | 🟡 P2 | Hungersignal → echte Bevölkerungs-Kopplung (MeticImmigration + Roster) | ~18 | 10 | Planned |
| **B-004** | 🟢 P3 | Vermögensklassen-Drift: WealthStats ↔ CitizenClass angleichen | ~30 | Backlog | Planned |
| **B-002** | 🟢 P3 | AccessAutomation-Spam: Rate-Limiter für Statusmeldungen | ~6 | Backlog | Planned |
| **B-006** | 🟢 P3 | IdentityHashMap-Migration Phase 2/3 | ~50 | Backlog | Planned |
| **B-008** | 🟢 P3 | EngineSeams-Direkt-Calls: 31→0 | ~40 | Backlog | Planned |
| **B-010** | 🟢 P3 | Carpenter targetWage=0 in FlowPrices | ~8 | Backlog | Planned |
| **T22** | 🟢 P3 | Savegame-Compat-Headless-Test | ~50 | Backlog | Planned |

**Summary:** 14 Tasks total — 2×P1 (Balance-CI + Coverage), 4×P2 (Booster + Bug-Fixes), 8×P3 (Cleanup).

---

## Closed Sprints

### Sprint 8 — Global-Audit + Freeze (Closed — `2ac5191`, `804cbf3`, 2026-07-26)

**Theme:** 6-Scope Subagent-Audit (tools/target/docs/test/src/root) + Thinker-Freeze-Architektur. Dead-Code-Bereinigung, 6 Drift-Fixes, Pre-Commit-Hook.

| Task | Inhalt | LoC | Commit | Status |
|---|---|---|---|---|
| **T20.0** | `tools/scarcity_sim.py` gelöscht (0 Cross-Refs) + leeres archive/ gelöscht | ~-400 | `2ac5191` | Closed |
| **T20.1** | `docs/README.md`: HANDOFF.md-Referenz entfernt | ~2 | `2ac5191` | Closed |
| **T20.2** | `ARCHITECTURE.md`: HEBELKARTE-Referenz entfernt | ~1 | `2ac5191` | Closed |
| **T20.3** | `ROADMAP.md`: Stale-Refs bereinigt | ~5 | `804cbf3` | Closed |
| **T20.4** | `.gitignore`: `economy_events.log` hinzugefügt | ~1 | `2ac5191` | Closed |
| **T20.5** | 6-Drift-Resolution: verify-doc-sync.sh +3 Checks (CHANGELOG-Kopf, YAML-Version, YAML↔Java) | ~44 | `804cbf3` | Closed |
| **T20.6** | `FlowMeter.java`: TODO → Closed-Comment (T5/B-001) | ~1 | `804cbf3` | Closed |
| **T20.7** | `.git/hooks/pre-commit` → `tools/build-gate.sh` (symlink) | 0 | `804cbf3` | Closed |

**Sprint-8-Total:** 7 Tasks (+54/-445 LoC).

### Sprint 7 — Adapter-Dispatcher + Schema-SSoT (Closed — `4efa7c4`, 2026-07-26)

**Theme:** Zentraler Dispatcher + `vanilla-schema.yaml` als SSoT. NPC-Faktionen via BypassGate angebunden.

| Task | Inhalt | LoC | Datei-Ref | Status |
|---|---|---|---|---|
| **T19.0** | `tools/vanilla-schema.yaml` — 15 Klassen, ~50 Felder | ~150 | `tools/vanilla-schema.yaml` | Closed (`4efa7c4`) |
| **T19.1** | `SchemaValidator.java` — pre-flight Class.forName/getDeclaredField | ~120 | `adapter/seam/SchemaValidator.java` | Closed (`4efa7c4`) |
| **T19.2** | `AdapterDispatcher.java` — ersetzt 5 createXxxAdapter() | ~120 | `adapter/AdapterDispatcher.java` | Closed (`4efa7c4`) |
| **T19.3** | `ISyxNpc.java` + `NpcFactionAdapter.java` — NPC-Preis/Resource | ~260 | `adapter/ISyxNpc.java`, `adapter/NpcFactionAdapter.java` | Closed (`4efa7c4`) |
| **T19.4** | `EconomySim` entkoppelt → `AdapterDispatcher.build()` | ~20 | `core/EconomySim.java` | Closed (`4efa7c4`) |
| **T19.5** | `build-gate.sh` Gate 7 + `audit-bytecode.sh` Whitelist | ~20 | `tools/build-gate.sh`, `tools/audit-bytecode.sh` | Closed (`4efa7c4`) |
| **T19.6** | `debugAdapterStatus` + `debugSelfTest` um ISyxNpc | ~10 | `core/EconomySim.java` | Closed (`4efa7c4`) |

**Sprint-7-Total:** 7 Tasks (~700 LoC). Validation: 7/7 Gates, 296 Tests.

### Sprint 6 — Coverage + Audit + CSV-Logging (Closed — in `4efa7c4` subsumiert, 2026-07-26)

**Theme:** 4 Test-Klassen, Bytecode/Sim-Audit-Skripte, Unified-CSV-Logging, Mockito-Dep.

| Task | Inhalt | LoC | Status |
|---|---|---|---|
| **T18.0** | `audit-bytecode.sh` + `audit-sim-logic.sh` + `gate_report.sh` | ~370 | Closed |
| **T18.1** | `pom.xml`: Mockito 5.14.2, Version-Bump | ~10 | Closed |
| **T18.2** | `DebugCsv.java` + `LoggingAdapter.java` | ~220 | Closed |
| **T18.3** | 4 Test-Klassen (EconConfigMath, AuditKernelDelta, DebugCsvFormat, EventLogCsvBridge) | ~280 | Closed |
| **T18.4** | `build-gate.sh` Gate 5 (Bytecode) + Gate 6 (Sim-Logik) | ~20 | Closed |
| **T18.5** | 2 Pre-Existing-Bug-Patches (pom.xml mod.changelog + effectiveImmigrantWallet) | ~5 | Closed |

**Sprint-6-Total:** 5 Tasks (~905 LoC).

### Sprint 4 — Coverage-Kernel-Pass (Closed — `a809405`, 2026-07-26)

**Theme:** Coverage-Decke für Kern-Kernels. Pure-Helper, Save/Load-Roundtrips, Statische Math.

| Task | Inhalt | LoC | Status |
|---|---|---|---|
| **T-COV-1** | `FiscalTest.java` — split(), retailSettlement(), Save/Load (15 Tests) | ~210 | Closed (`a809405`) |
| **T-COV-2** | `EconProgressionTest.java` — Stage.fromLevel/next, v32→v33 (14 Tests) | ~190 | Closed (`a809405`) |
| **T-COV-3** | `AffordabilityGateTest.java` — null-Deps, Admission, Kind, NONE (7 Tests) | ~80 | Closed (`a809405`) |
| **T-COV-4** | `LaborMarketTest.java` — blend(), profitPriority(), save/load (12 Tests) | ~170 | Closed (`a809405`) |
| **T-COV-5** | `HousingMarketTest.java` — lastRent*, ledger(), save/load (8 Tests) | ~140 | Closed (`a809405`) |
| **T-COV-6** | JaCoCo-Coverage-Gate in pom.xml (report-only, 0%-Threshold) | ~30 | Closed (`a809405`) |
| **T-COV-7** | `PairSourceTest.java` — Random + Proximity (8 Tests) | ~110 | Closed (`a809405`) |
| **T-COV-8** | `DiagnosticExporterTest.java` — Path-Validation, resetExportGuard (4 Tests) | ~60 | Closed (`a809405`) |

**Sprint-4-Total:** 8 Tasks (~990 LoC).

### Sprint 3 — Roadmap-SSOT-Konsolidierung (Closed — `51f8b27`, 2026-07-26)

| Task | Inhalt | LoC | Status |
|---|---|---|---|
| **T14.0** | ROADMAP.md → Global Task Index | ~80 | Closed (`51f8b27`) |
| **T14.1** | agents.md Rule 13: Roadmap-as-Truth, Verschiebe-Verbot | ~25 | Closed (`51f8b27`) |
| **T14.2** | WORKFLOW.md Anti-Pattern erweitert | ~15 | Closed (`51f8b27`) |
| **T14.3** | tools/docs-truth-consistency.sh grep-Watch verifiziert | 0 | Closed (`51f8b27`) |
| **T14.4** | docs/BACKLOG.md mold-down → New-Findings-Only | ~-30 | Closed (`51f8b27`) |
| **T14.5** | Stam-Docs-Sync 0.13.30→0.13.31 | sed | Closed (`51f8b27`) |
| **T14.6** | CHANGELOG.md Sprint-3-Eintrag | ~12 | Closed (`51f8b27`) |

**Sprint-3-Total:** 7 Tasks (~132 LoC).

### Sprint 2 — Mod-Economy T5–T13 (Closed — `c1964d2`, 2026-07-25)

| Task | Inhalt | LoC | Status |
|---|---|---|---|
| **T5** | B-001 FlowMeter.targetSupply + @Deprecated-Getter | ~15 | Closed (`c1964d2`) |
| **T6** | B-009 Hunger-Demographie Hook | ~30 | Closed (`c1964d2`) |
| **T7** | B-004 Classifier-Pipeline angleichen | ~25 | Closed (`c1964d2`) |
| **T8** | H8 phaseFactor in FlowPrices.refresh() | ~25 | Closed (`c1964d2`) |
| **T9** | revertFireSale() EventLog-Hinweis | ~3 | Closed (`c1964d2`) |
| **T10** | diagnosticsExportEnabled default false | ~1 | Closed (`c1964d2`) |
| **T11** | HEBELKARTE.md gelöscht | ~9 | Closed (`c1964d2`) |
| **T12** | AccessAutomation.reset() | ~22 | Closed (`c1964d2`) |
| **T13** | Static-Audit reset() auf 5 Klassen | ~120 | Closed (`c1964d2`) |

**Sprint-2-Total:** 9 Tasks (~250 LoC).

### Sprint 1 — TreasuryCrisis State-Leak Reset (Closed — `c1964d2`, 2026-07-25)

| Task | Inhalt | LoC | Status |
|---|---|---|---|
| **T1** | TreasuryCrisis.reset() — 6 mutable static + 3 saved-Werte | ~22 | Closed (`c1964d2`) |
| **T2** | recoveryLogged + activateWarning() Re-Arm | ~12 | Closed (`c1964d2`) |
| **T3** | Reset-Hooks in EconomySim.clearActive() | ~10 | Closed (`c1964d2`) |
| **T4** | IdentityMapRegistry-Hook (subsummiert) | 0 | Rejected |

**Sprint-1-Total:** 3 Tasks (~44 LoC).

### Sprint 0 — Phase A–F SDK + Workflow-Reform (Closed — `1442804`..`c1964d2`, 2026-07-25)

| Task | Inhalt | Status |
|---|---|---|
| **Phase A** | BypassGate SDK (4 Dateien: BypassGate, FieldAccessor, MethodAccessor, ClassResolver) | Closed |
| **Phase B** | Diplomacy-Adapter auf BypassGate migriert, Fallback+MH gelöscht | Closed |
| **Phase C** | Transport-Adapter migriert | Closed |
| **Phase D** | Warehouse-Adapter migriert | Closed |
| **Phase E** | Boosting-Adapter migriert, letzter Fallback entfernt | Closed |
| **Phase F** | AI-Adapter, ClassResolver, BuildStamp, Imports-Cleanup | Closed |
| **Workflow** | agents.md Rule 11+12, WORKFLOW.md, BINDUNGSMATRIX.csv kanonisch | Closed |

### Sprint -1 — Historisch v0.0.1–v0.13.10 (Closed, 2026-07-23/24)

Cold-Start-Fix, mean_wage-Runaway, Re-Entry-Crash, God-Class-Split, 5-Stufen-System, Gini→Loyalty, 5 UI-Fenster + 16 Tabs, 6 Hotkeys, Save-Format 33 chunked, IdentityHashMap Phase 1.

---

## Rejected Tasks

| ID | Task | Begründung |
|---|---|---|
| **T4** | IdentityMapRegistry-Hook für TreasuryCrisis | In T1+T3 subsummiert |

---

## Definition of Done

1. `mvn verify install -DskipTests` — 7/7 Gates grün
2. `mvn test` — alle Tests grün (296 aktuell)
3. `bash tools/verify-doc-sync.sh` — 9 Checks PASS (Stam-Docs + YAML + YAML↔Java)
4. Pre-Commit-Hook aktiv: `.git/hooks/pre-commit → tools/build-gate.sh`

---

## Freeze-Status (seit Sprint 8)

| Schicht | Status |
|---|---|
| `core/`, `ui/`, `adapter/` | ❄️ **FROZEN** — keine Code-Änderungen |
| `tools/vanilla-schema.yaml` | ✅ Engine-Updates (V72: 1 Diff) |
| `EconConfig.java` | ✅ Balancing-Parameter |
| `test/` | ✅ Neue Tests jederzeit |
