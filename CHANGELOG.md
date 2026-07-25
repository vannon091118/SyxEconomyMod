# SyxEconomyMod — Changelog

> **Version:** v0.13.31 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-26
>
> Stam-Doku-Synchron-Anker: Die obenstehende Versions-Zeile MUSS identisch mit `pom.xml` `<version>` sein.
> Der Sync-Gate `tools/verify-doc-sync.sh` scheitert wenn dieser Anker driftet.

> Vollständige Historie. Die `pom.xml mod.changelog` enthält die letzten 10 Einträge als Release-Summary.
> Versionierung: 0.0.1+-Schritte (Pre-Release), kein 1.x bis zum ersten Public Release.

---

## v0.13.31 — 2026-07-26

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

The full release history (v0.13.30 back to v0.0.1) is archived in
[`docs/CHANGELOG_ARCHIVE.md`](docs/CHANGELOG_ARCHIVE.md) to keep the
root CHANGELOG focused on the current sprint.
