# SyxEconomyMod — Changelog

> **Version:** v0.13.56 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-26
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
| **9** | UI-Sprint — SK-01/06/09/10 Bugfixes (inline-Text, Rohkeys, Null-Farbe, leere Header) | _staged_ | 2026-07-26 |
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
