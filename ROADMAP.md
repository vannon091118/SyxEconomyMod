# SyxEconomyMod — Entwicklung & Roadmap

> **Version:** v0.13.78 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-26
>
> Stam-Doku-Synchron-Anker: `tools/verify-doc-sync.sh` (9 Checks).
> Abgeschlossene Sprints → [`CHANGELOG.md`](CHANGELOG.md).

---

## Global Task Index (Backlog)

**Legende:** 🔴 P0 · 🟠 P1 · 🟡 P2 · 🟢 P3

| Task | Prio | Kurzbeschreibung | LoC | Sprint |
|---|---|---|---|---|
| **7-1a** | ✅ Closed (v0.13.79) | **Scarcity-Kaskaden-Algorithmus dokumentiert:** Zweistufiger Mechanismus (FlowPrices.scarcityMultiplier + LocalPrices.scarcity) + Post-Processing (scarcityPriceBoost, foodPriceCap, phaseFactor) als Code-Spec in `FlowPrices.java` Header. | ~30 | 9 |
| **7-1b** | ✅ Closed (v0.13.79) | **Golden-Snapshot existiert bereits:** `tools/balance-reference.txt` (30 Konstanten) + `tools/balance-regression-check.sh` (0.1% Toleranz — strenger als 5% Spec). | ~0 | 9 |
| **7-2** | ✅ Closed (v0.13.79) | **Balance-Smoke-Gate existiert bereits:** `tools/balance-regression-check.sh` prüft 30 EconConfig-Konstanten gegen Referenz (0.1% Toleranz). Build-Gate Gate 8 integriert. | ~0 | 7 |
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
| **AUDIT-1** | ✅ Closed (v0.13.68) | **emigrationRisk = Dead Code** — AtomicInteger und zugehörige Inkrements/Nullungen aus `EconomySim.java` entfernt. | — | 10 |
| **U-01** | ✅ Closed (v0.13.67) | **Treasury-Display-Inkonsistenz:** Quickview -19M vs Dashboard -1.9M — FONTW_KPI 128→144, GText-Overflow behoben. `AccessAutomation.java:30` | ~10 | U-1 |
| **U-02** | ✅ Closed (v0.13.67) | **GText-Overflow Kopfsteuer:** `####-500D#` bei negativem Treasury — FONTW_SLVAL 80→96, Plus-Button x 200→216. `EconWindowBase.java:239,312` | ~8 | U-1 |
| **U-03** | ✅ Closed (v0.13.79) | **CitizenClass-Tabelle:** SocialTab in WindowState zeigt Bürgerklassen-Verteilung (Anzahl, Haus/Firma-Multiplikatoren). `Wallets.countByClass()` hinzugefügt. `WindowState.java`, `Wallets.java` | ~25 | U-1 |
| **U-04** | 🟡 P2 | **Firmen-Namen als interne Keys:** FARM_GRAIN/FISHERY_NORMAL statt lokalisierter Namen | ~10 | U-1 |
| **U-05** | 🟡 P2 | **Ampel-Pfeile inkonsistent:** "Finanzen →", "Gleichheit →" aber "Arbeit"/"Wachstum"/"Versorgung" ohne Pfeil | ~5 | U-1 |
| **U-06** | 🟡 P2 | **Advisor-Text clipped:** mid-sentence Abbruch, kein Scrollindikator, kein Hinweis auf weiteren Inhalt | ~10 | U-1 |
| **U-07** | 🟡 P2 | **Religionssteuer-Label-Overflow:** WindowEconomy GText-Feld x+300 zu eng (x+308 in WindowState) | ~4 | U-1 |
| **U-08** | 🟡 P2 | **Onboarding überlebt Krisenzustand:** SCHRITT 4/4 bei -1.8M Treasury — keine Priorisierung | ~15 | U-1 |
| **U-09** | 🟠 P1 | **Bücher-Tab Konsistenzzeile:** "Kasse + Umlauf = 203.3K D" widerspricht -1.8M Treasury — widersprüchliche Anzeige | ~15 | U-1 |
| **B-011** | ✅ Closed (v0.13.67) | **AccessAutomation permanent deaktiviert:** `accessDetectionDisabled` (static) → Mid-Session-Recovery nach 1800 Ticks. `AccessAutomation.java:30-44` | ~5 | B-FIX |
| **B-012** | ✅ Closed (v0.13.79) | **EconProgression Fortschritt:** SUBSISTENZ→HANDEL Pop-Schwelle 50→30, HANDEL→INDUSTRIE 100→75. Kleinere Siedlungen kommen jetzt voran. `EconProgression.java` | ~10 | B-FIX |
| **B-013** | 🟡 P2 | **Advisor-Preis-Disconnect:** Berater empfiehlt Export bei Stone 75.3x/Wood 73.7x — Preisdaten nicht mit Advisor-Empfehlungen verknüpft | ~25 | B-FIX |
| **L-01** | ✅ Closed (v0.13.67) | **BrokeFoodPlan-Escape:** `con()` prüft jetzt `hunger.isMax()` vor desperate-Aktivierung. Hunger < MAX → normale AI-Pipeline (Oddjob). `BrokeFoodPlan.java:38-42` | ~20 | L-1 |
| **L-04** | ✅ Closed (v0.13.78) | **BrokeFoodPlan clinit-Crash (DCL v2.4):** `script.ScriptLoad` erzwingt `<clinit>` via `Class.forName(initialize=true)` VOR Sim-Bootstrap. Bill-Pugh `HungerHolder` war nutzlos (innere Klasse selbst im JAR → gleicher Scan). Fix: DCL auf `volatile hungerCache` in Outer-Class (kein nested Holder), `NEEDS.TYPES()==null` Pre-Check LEBT IM LOCK (TOCTOU defense-in-depth), `LinkageError`-only Catch, Caller-Null-Degrade. `agents.md` Rule 15 (verbindlich ab v0.13.76). Runtime-verifiziert: Mod-Start ohne `ExceptionInInitializerError`. `BrokeFoodPlan.java` | ~30 | L-1 |
| **L-02** | ✅ Closed (v0.13.79) | **SubjectFatigue + FatiguePressure:** FatigueTracker extrahiert (int[60000] + STAMINA-Booster via BOOSTABLES.PHYSICS().STAMINA). Config: fatigueEnabled, fatiguePerTick=1, fatigueRestThreshold=100, fatigueRecoveryRate=5, fatigueStaminaMin=0.5. Wallets v34 Save-Format. `FatigueTracker.java`, `FatiguePressure.java`, `Wallets.java`, `EconConfig.java`, `MainScript.java`, `EconomySim.java` | ~80 | L-1 |
| **L-03** | 🟡 P2 | **WealthRest:** Reiche Bürger (>2× Median) arbeiten nur noch Teilzeit via reduzierter Fatigue-Schwelle. `EconConfig.wealthRestEnabled` + `wealthRestThreshold`. | ~40 | L-1 |
| **LOG-01** | ✅ Closed (v0.13.79) | **ACTION-Logging:** 25/27 clickActionSet-Calls in 4 UI-Fenstern mit DiagnosticExporter verknüpft (2 verbleibende: EconWindowBase Slider-Primitive ohne Kontext). WindowState, EconWindowBase Tab-Switch, + WindowOverview/WindowQuickview (Spluck-Prep). | ~10 | LOG-1 |
| **LOG-02** | 🟡 P2 | **Session-Identifikation:** nanoTime-Epoch im Dateinamen vereinheitlichen (DebugTracer/DebugCsv/DiagnosticExporter). Gemeinsamer Session-Join-Key. | ~20 | LOG-1 |
| **LOG-03** | 🟡 P2 | **4-Log-Split (Variante C Hybrid):** log_berechnung/log_aktionen/log_zugriffe/log_sonstiges.csv via LoggingAdapter. economy_events.log bleibt als In-Game-Chronik. debug.csv + rebalance_*.csv entfallen. | ~150 | LOG-1 |
| **BA-01** | ✅ Closed (v0.13.79) | **Treasury-Drain Early-Game:** `earlySettlerWalletBonus` 300→500, `earlySettlerDoleThreshold` 5000→10000. Mehr Startkapital + breitere Gratiskorn-Versorgung. `EconConfig.java` | ~5 | BA |
| **BA-02** | 🟡 P2 | **Extrem-Gini 0.946:** 3 Ausreißer (333K/500K/1.3M), 34 Bürger bei Median 4D. GiniConsequences greift, aber Effekt nicht spürbar. | ~10 | BA |
| **BA-03** | ✅ Closed (v0.13.67) | **Arbeitslosigkeits-Todesspirale:** Covered by L-01 (BrokeFoodPlan con() prüft hunger.isMax()). | ~0 | L-1 |
| **BA-04** | ✅ Closed (v0.13.67) | **Thron-Bug / Bootstrap-Lücke:** Bürger verbrauchen Startkapital bevor Wirtschaft Einkommen generiert → Thron-Essen → Pleite-Spirale. Fix: `earlySettlerDoleThreshold` (5000D) — alle Bürger essen gratis solange pop < 50. `EconConfig.java:541, GrainDole.java:74-78` | ~15 | BA |
| **BA-05** | ✅ Closed (v0.13.68) | **effectiveCoverage() Stock-Fallback (D-004):** `FlowPrices.java` — stock-basierte Coverage bei supplyPerDay=0 und stock>0 implementiert; Cold-Start-Guard bleibt erhalten. | `FlowPrices.java:120-138` | ~15 | BA |
| **DC-03** | ✅ Closed (v0.13.79) | **mean_wage → wage_config_max:** CSV-Header + Event-Detail umbenannt. `DiagnosticExporter.java` | ~2 | DC |
| **DC-04** | ✅ Closed (v0.13.79) | **FirmLedger CSV-Filter entschärft:** `firmFinancialSnapshots()` filtert jetzt wie `update()` — `!physicalSeen && !cashTracked && !marketTracked`. `FirmLedger.java` | ~3 | DC |
| **DC-05** | 🟡 P2 | **_WOOD Supply-Spike-Anomalie:** `rebalance_resources.csv` zeigt _WOOD supply=45.371 und 84.698 D/Tag (unrealistisch — Vanilla-Holzfäller produziert keine 45k/Tag). Coverage bleibt trotzdem 0.4 (2.25× anchor). Vermutlich Float-Precision-Problem oder Tick-Aggregat statt Tages-Wert. Kein Breaking-Bug, aber Diagnostik-Rauschen. | `FlowMeter.java` | ~8 | DC |
| **DC-06** | 🟡 P2 | **Duplicate Day 43 im Exporter:** `rebalance_macro.csv` Zeile 43 (Season 3) erscheint zweimal mit leicht unterschiedlichen Werten (max_wealth 1837→1843). Frame-Boundary-Problem oder Doppel-Export bei Event auf Schreibtakt-Grenze. | `DiagnosticExporter.java` | ~5 | DC |
| **SK-07** | 🟡 P2 | **AccessAutomation Chronicle-UI-Flood:** Drei identische `[ACCESS]`-Zeilen im Chronik-Panel pro Tick — `LOG.ln()`-Aufrufe aus AccessAutomation fluten das UI-Chronik-Panel. Gleicher Bug wie B-011 (NPE→disabled), anderer Symptomkanal (UI statt Funktionalität). Fix: LOG.ln()-Aufrufe aus AccessAutomation entfernen oder via EventLog-Kanal statt UI-Chronik. | `AccessAutomation.java:LOG.ln()` | ~5 | DC |
| **DOC-01** | 🟡 P2 | **ARCHITECTURE.md + GLOSSARY.md Brücken-Korrektur:** 4 settlement/room/*-Brücken existieren (309 LOC in `src/settlement/room/`). Wurden in v0.13.67 fälschlich gelöscht, jetzt restauriert mit korrekten LOC-Zahlen. | ~0 | DOC |
| **DOC-02** | 🟡 P2 | **BINDUNGSMATRIX J1-J6:** StatsBehaviour → StatsMultipliers (Vanilla-Source-verifiziert). CIVICS-Count korrigieren. | ~5 | DOC |
| **DOC-03** | 🟡 P2 | **agents.md Canary-Update:** Flachwitz-Refresh nach Rule-7-Pflicht (Session-Start, doc-Update). | ~2 | DOC |
| **DOC-04** | 🟡 P2 | **settlement/room/ Bridges existieren:** 4 Dateien (309 LOC) in `src/settlement/room/` — ARCHITECTURE+GLOSSARY hatten sie fälschlich gelöscht. Korrigiert in v0.13.67. | ~0 | DOC |
| **LOC-01** | 🟠 P1 | **358 hartcodierte Strings, 0 Lokalisierung:** `EconTexts.java` (358 Konstanten) + 369 UI-String-Literale — alles deutsch, kein Übersetzungssystem. **Code:** `EconTexts.java:1-358`, 5 UI-Dateien (`WindowEconomy.java`, `WindowOverview.java`, `WindowState.java`, `WindowQuickview.java`, `EconHud.java`). **Plan:** `LocaleStrings.java` (74 LOC, erstellt v0.13.67) + `EconConfig.locale` (neu). Schrittweise Migration über `LocaleStrings.t("key", EconTexts.¤¤fallback)`. English-Fallback via `LocaleStrings.en` Map. | ~200 | LOC |
| **TECHD-01** | 🟡 P2 | **EconomySim 1692 LOC — Modularisierung:** God-Class-Guard-Baseline ist 1382, Realität 1692 (+310 Drift). 74 pub-Methoden, 129 Felder, 73 Imports. **Plan (3 Extraktionen):** (a) `EconomySaveLoad.java` (~400 LOC) — saveChunked/loadChunked + 21 Chunk-Tags + StateBundle-Pattern. Ref: `EconomySim.java:1147-1350`. (b) `EconomyTickOrchestrator.java` (~250 LOC) — update()-Phasen 7-10 (Wages→Taxes→Fiscal→Ledger). Ref: `EconomySim.java:700-950`. (c) `EconomyAuditEngine.java` (~150 LOC) — Phase 11 (AuditKernel + MoneySupply-Check). Ref: `EconomySim.java:950-1050`. **Ziel:** EconomySim auf ~900 LOC, unter God-Class-Guard-Schwelle (800). **RES-005 Pitch:** Atomare Implementation in [`docs/HANDOFF_RES005.md`](docs/HANDOFF_RES005.md) §Sprint Spluck-TECHD-01 (14 Tasks, 1 atomic Commit). | ~800 | TECHD |
| **TASK-008** | 🟠 P1 (Active) | **EconomySim-SaveLoad-Extraktion:** Implement Extraction 1 aus TECHD-01. Extrahiere `saveChunked()` + `loadChunked()` + 21 Chunk-Tags + PropertyMarket/Corvee/StateWages-Sub-Chunks → `core/save/EconomySaveLoad.java` (~450 LOC). Interface `IEconomySaveLoad` mit `save(FilePutter)` / `load(FileGetter)` / `resetOnLoad()` / `chunkTags()`. **Goal (Intermediate):** EconomySim nach TASK-008 auf ~880 LOC. **Goal (Sprint-Final):** EconomySim nach Spluck-TECHD-01 auf ≤ 450 LOC (unter 800-Schwelle, vgl. HANDOFF Block 5 DoD-4). **Schließungskriterium:** `mvn verify install -DskipTests` EXIT 0 + 1 Save-Roundtrip-Test grün + `mvn test` EXIT 0 (402 Tests). **Spec:** [`docs/HANDOFF_RES005.md`](docs/HANDOFF_RES005.md) Block 4 Task 2+3, Block 5 DoD-Punkte 1-10. | ~450 | Spluck-TECHD-01 |
| **TASK-009** | 🟠 P1 (Active) | **EconomySim-TickOrchestrator-Extraktion:** Implement Extraction 2 aus TECHD-01. Extrahiere `update()`-Hauptphasen (Wages→Taxes→Fiscal→Ledger→Labor→Service→Audit) → `core/save/EconomyTickOrchestrator.java` (~280 LOC). Interface `IEconomyTick` mit `tick(double ds)` / `phaseTriggers()` / `reentryGuard()` / `dayBoundary()`. Re-Entry-Guard bleibt im Orchestrator (war vorher in EconomySim). **Goal (Intermediate):** EconomySim nach TASK-008+TASK-009 auf ~560 LOC. **Goal (Sprint-Final):** EconomySim nach Spluck-TECHD-01 auf ≤ 450 LOC. **Schließungskriterium:** `mvn test` 402 Tests grün (Verhaltens-Neutralität) + Re-Entry-E2E-Test grün. **Spec:** [`docs/HANDOFF_RES005.md`](docs/HANDOFF_RES005.md) Block 4 Task 4+5. | ~280 | Spluck-TECHD-01 |
| **TECHD-02** | 🟡 P2 | **EconConfig 207 Felder Constants-Dump:** 55 bool + 75 int + 61 double + 16 other. 555 LOC. **Plan:** 3 Sub-Configs extrahieren: `BalanceConfig` (Preise, Löhne, Steuern), `BehaviorConfig` (Toggles, Schwellwerte), `PhaseConfig` (Progression, Stufen). AffinConfig-Nested-Class existiert bereits (leer). `EconConfig.locale` hinzugefügt (v0.13.67). | ~150 | TECHD |
| **TEST-01** | 🟠 P1 | **Save-Migration-Integrationstest fehlt:** CHUNKED_VERSION=33, keine Headless-Test-Suite für Savegame-Migration. Tagebuch: „Migrations-Pfad im Feld unbewiesen". | ~80 | TEST |
| **DA-01** | 🟡 P2 | **Tagebuch-Abgleich v0.13.67:** 17 Claims gegen Code verifiziert (11 bestätigt, 6 korrigiert, 0 widerlegt). Detaillierte Tabelle unten. | ~0 | DOC |
| **DIPLO-01** | ✅ Closed (v0.13.67) | Opinion-Monitoring aktiv: `EconomySim.monitorFactionOpinion()` liest TreasuryCrisis/Gini/Deaths → `adjustFactionOpinion()` (Logging-only, Write deferred to DIPLO-02). `IFactionAccess.getFactionOpinion()` + `getFactionTrust()` via `ROPINION.get()` (public API). | `EconomySim.java:925`, `FactionAccessImpl.java:457-505` |
| **DIPLO-02** | ✅ Closed (v0.13.67) | `getFactionOpinion()` + `getFactionTrust()` + `adjustFactionOpinion()` implementiert. Write-Pfad ist Logging-only da Vanilla `ROPINION.setOpinionValue()` + `SuperBoostable.incD()` package-private — BypassGate-Lösung in DIPLO-03. | `FactionAccessImpl.java:457-505` |
| **DIPLO-03** | 🟡 P2 | **BINDUNGSMATRIX: ROPINION + bOpinion + TRUST katalogisieren** — 3 Engine-Hebel fehlen komplett in der Matrix. | ~10 | DIPLO |

**Total:** 75 Tasks (60 bestehende + 13 aus Sprint-DA-01-Decke: BA-05, DC-03/04/05/06, SK-07, DOC-04, LOC-01, TECHD-01/02, TEST-01, DA-01, DIPLO-03 + 2 RES-005-Neu: TASK-008, TASK-009) — plus 44 Closed.

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
| **A-01** | ✅ Closed (v0.13.64) | **EngineLevers.java** — Config-Toggles pro Vanilla-Zugriff (97 boolean Toggles, 103 public static Felder). Startup-Dump via `LoggingAdapter`. | — | ~200 |
| **A-02** | ✅ Closed (v0.13.64) | **IRoomAccess + RoomAccessImpl** — Stockpile, Transport, Room-Iteration, Service (bündelt ISyxWarehouse + ISyxTransport + EngineSeams) | ~500 | A-1 |
| **A-03** | ✅ Closed (v0.13.64) | **IFactionAccess + FactionAccessImpl** — NPC, Diplomacy, Trade, Royalty, Player (bündelt ISyxDiplomacy + ISyxNpc + public API) | ~600 | A-1 |
| **A-04** | ✅ Closed (v0.13.64) | **IHumanoidAccess + HumanoidAccessImpl** — AI-Plans (12), Stats, Boosting (bündelt ISyxAI + ISyxBoosting + EngineSeams) | ~500 | A-1 |
| **A-04b** | ✅ Closed (v0.13.64) | **IStatsAccess + StatsAccessImpl** — Maintenance, Time, Religion, Weather, Tourism, Events. Beide Dateien existieren: `IStatsAccess.java` (100 LOC) + `StatsAccessImpl.java` (265 LOC). | ~300 | A-1 |
| **A-05** | ✅ Closed (v0.13.64) | **EngineMirror.java + AdapterDispatcher + Stam-Docs** — Zentrale Fassade, ersetzt EngineSeams graduell | ~400 | A-1 |

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
2. `mvn test` — 402 Tests, 0 Fail
3. `bash tools/verify-doc-sync.sh` — 9 Checks PASS
4. Pre-Commit-Hook: `.git/hooks/pre-commit → tools/build-gate.sh`

---

## Sprint U-1 — Livetest UI-Fixes (v0.13.64-B868DC9-DIRTY)

**Quelle:** Livetest v0.13.64, 26.7.26, 37 Siedler, 3850 Tage, `-B868DC9-DIRTY`

**Ziel:** Alle 9 UI-Bugs aus dem Livetest beheben — von kritischen Anzeigefehlern bis kosmetischen Inkonsistenzen.

| Task | Prio | Befund | Ursache | Fix |
|---|---|---|---|---|
| **U-01** | 🔴 P0 | Quickview `-19M D`, Dashboard `-1.9M D` | CompactNumber.format(treasury) unterschiedlicher Snapshot-Zeitpunkt oder treasury()-Pfad | Treasury-Snapshot zum gleichen Tick einfrieren oder CompactNumber-Format prüfen |
| **U-02** | 🔴 P0 | `####-500D#` im Kopfsteuer-Feld | GText-Feldbreite < labelText-Länge → Render-Overflow | GText.maxWidth erhöhen oder Label kürzen/abbreviieren |
| **U-03** | 🟠 P1 | Demografie-Tabelle: 37 Siedler, 0 Zeilen | CitizenClass.classifiablePopulationCount() = 0 oder Render-Loop überspringt alle | Debug-Log in CitizenClass + Render-Bedingung prüfen |
| **U-04** | 🟡 P2 | Firmen-Tab: `FARM_GRAIN` statt lokalisiertem Namen | `blueprint.key` statt `blueprint.name` verwendet | `blueprint.info.name` verwenden |
| **U-05** | 🟡 P2 | Ampel-Pfeile inkonsistent | Nicht alle Ampel-Typen rendern denselben Pfeil-Indikator | Einheitliches Pfeil-Rendering pro Ampel |
| **U-06** | 🟡 P2 | Berater-Text mid-sentence abgeschnitten | GText ohne Scroll/Expand, Textlänge > Feldkapazität | Scroll-Panel oder Expand-Button |
| **U-07** | 🟡 P2 | Religionssteuer-Label-Overflow | x+300 in WindowEconomy vs x+308 in WindowState | Breite vereinheitlichen |
| **U-08** | 🟡 P2 | Onboarding SCHRITT 4/4 bei -1.8M Treasury | Tutorial-State unabhängig von Crisis-State | Onboarding ausblenden wenn CrisisDispatch.active==true |
| **U-09** | 🟠 P1 | Bücher-Tab: "Kasse + Umlauf = 203.3K D" bei -1.8M Treasury | Addiert treasury + circulating falsch oder verwendet veralteten Snapshot | Formel prüfen: treasury() + wallets().circulating() muss mit seedSupply() übereinstimmen |

**Geschätzt:** 9 Tasks, ~100 LoC

---

## Sprint B-FIX — Livetest Verhaltens-Fixes (v0.13.64-B868DC9-DIRTY)

**Quelle:** Livetest v0.13.64 + EventLog-Analyse

| Task | Prio | Befund | Ursache | Fix |
|---|---|---|---|---|
| **B-011** | 🔴 P0 | AccessAutomation permanent deaktiviert | `accessDetectionDisabled` (static) bei erster Exception=true, `reset()` nur bei Save/Load → kein Mid-Session-Recovery | Periodischer Reset nach N Ticks ODER retry-Logik mit Backoff |
| **B-012** | 🟠 P1 | 3850 Tage in Subsistenz | EconProgression.checkAdvance() blockiert durch Taverne/Labor die Spieler nicht baut | Entweder: Meilenstein-Logik lockern (≠ require buildings) oder Advisor-Empfehlung deutlicher machen |
| **B-013** | 🟡 P2 | Advisor empfiehlt Export bei Stone/Wood 73-75× Preis | Advisor-Logik unabhängig von FlowPrices.scarcitySignal | Preisdaten in Advisor-Empfehlungen einfließen lassen (wenn scarcity > 10× → "Baue X produzierende Gebäude" statt "Exportiere") |

**Geschätzt:** 3 Tasks, ~50 LoC

---

## Sprint L-1 — Labor-Kreislauf: Arbeit, Fatigue, Reichtum

**Ziel:** Den Teufelskreis "Broke→Starve" durchbrechen und einen natürlichen Arbeitszyklus einführen.

**Vanilla-Verifikation (26.7.26):**
- `BOOSTABLES.PHYSICS().STAMINA` = 1.0 — Beschreibung: *"How long a subject can walk or run before needing to rest."*
- `StatsMultipliers.OVERTIME` (StatMultiplierWork) + `.DAY_OFF` (StatMultiplierAction) existieren
- `PlanOddjobber` existiert in `settlement/entity/humanoid/ai/work/` (package-private)
- `AIPLAN.PLANRES.WAIT_AND_EXIT` existiert

| Task | Prio | Beschreibung | Vanilla-Anker | LoC |
|---|---|---|---|---|
| **L-01** | 🔴 P0 | **BrokeFoodPlan-Escape:** Vor `desperate`-Sprung prüfen: `isEmployableWorker && isSurplusLaborer` → Oddjob-Plan aktivieren. Statt: kein Geld → kein Essen → verhungern. Neu: kein Geld → Oddjob → Geld → Essen. | `PlanOddjobber`, `AIPLAN.PLANRES` | ~20 |
| **L-02** | 🟠 P1 | **SubjectFatigue + FatiguePressure:** Pro gearbeitetem Tick Fatigue inkrementieren. Bei Schwellenwert `forcedRest` via `AI.SUBS().rest.activate()`. STAMINA-Booster modifizieren. `EconConfig.fatigueEnabled`, `fatiguePerTick`, `fatigueRestThreshold`, `fatigueRecoveryRate`. | `BOOSTABLES.PHYSICS().STAMINA`, `StatsMultipliers.OVERTIME`/`.DAY_OFF` | ~100 |
| **L-03** | 🟡 P2 | **WealthRest:** Bei `relativeWealth > wealthRestThreshold` (2× Median) → Fatigue-Schwelle halbieren → Bürger arbeiten 50% weniger. Kein "Ruhestand", nur Teilzeit. `EconConfig.wealthRestEnabled`, `wealthRestMedianMultiplier`. | `WealthHappiness.relativeWealth()`, `BOOSTABLES.BEHAVIOUR().HAPPI` | ~40 |

**Dependency-Chain:** L-01 (BrokeFoodPlan) → L-02 (Fatigue) → L-03 (WealthRest). L-01 kann unabhängig deployed werden.

**Geschätzt:** 3 Tasks, ~160 LoC

---

## Sprint LOG-1 — Diagnose-Infrastruktur (Session-Livetest v0.13.64)

**Quelle:** Session-Identifikations-Analyse + 4-Log-Split-Vorschlag

**Bestehende Probleme (verifiziert):**
- 3 parallele Log-Schemata: EventLog (Freitext), DebugCsv (`;`-separiert), DiagnosticExporter (`,`-separiert)
- 25 `clickActionSet`-Calls in 4 UI-Fenstern — **0** geloggt
- `DiagnosticExporter.SESSION_EPOCH = System.nanoTime()` — DebugTracer nimmt `nanoTime()` beim DUMP, nicht beim Session-Start → kein Join möglich
- DebugCsv day = Float, DiagnosticExporter day = Long → kein SQL-Join möglich

| Task | Prio | Beschreibung | LoC |
|---|---|---|---|
| **LOG-01** | 🟠 P1 | **ACTION-Logging:** Alle `clickActionSet`-Calls (25 in WindowOverview/Quickview/State) mit `EventLog.log("ACTION", "slider=headTax old=45 new=135")` verknüpfen. Old/New-Transition dokumentieren. | ~35 |
| **LOG-02** | 🟡 P2 | **Session-Identifikation:** `SESSION_EPOCH` als zentrale Konstante in `DiagnosticExporter`. DebugTracer.dump() verwendet diesen Epoch, nicht eigenen nanoTime(). DebugCsv schreibt Epoch in Header-Zeile. | ~20 |
| **LOG-03** | 🟡 P2 | **4-Log-Split (Variante C Hybrid):** `log_berechnung.csv` (ECON/TREND/STAGE/REBALANCE), `log_aktionen.csv` (ACTION), `log_zugriffe.csv` (SEAM/ACCESS/BOOSTERS/ADAPTER), `log_sonstiges.csv` (SYSTEM/CHEAT/CONFIG/TRACE). `economy_events.log` bleibt In-Game-Chronik. `debug.csv` + `rebalance_*.csv` entfallen, Schema in 4-Log übernommen. | ~150 |

**Geschätzt:** 3 Tasks, ~205 LoC

---

## Balance-Audit (BA) — Livetest v0.13.64

| Task | Prio | Befund | Ursache |
|---|---|---|---|
| **BA-01** | 🟠 P1 | -1.8M Treasury bei 37 Siedlern, 5 Arbeitern | Lohn/Subventions-Spirale: 5 Arbeiter × 50D + MilitaryPayroll + HandoutRelief + Maintenance. Grace Period (v0.13.67) existiert, aber Early-Game-Phase reicht über Jahr 1 hinaus. |
| **BA-02** | 🟡 P2 | Gini 0.946 — 3 Ausreißer vs 34 Mittellose | `relativeWealth` = money/median. Wenn median=4D und 3 Bürger 333K–1.3M haben → Faktor 83.000×. GiniConsequences bestraft Loyalty, aber kein Mechanismus reduziert die Konzentration. |
| **BA-03** | ✅ Closed (v0.13.67) | Arbeitslosigkeits-Todesspirale | Siehe L-01. BrokeFoodPlan con() prüft jetzt hunger.isMax(). |
| **BA-04** | ✅ Closed (v0.13.67) | **Thron-Bug:** Bürger verbrauchen Startkapital (500D) → Thron-Essen → Pleite. Fix: `earlySettlerDoleThreshold` = 5000D bootstrap threshold in GrainDole. | `EconConfig.java:541, GrainDole.java:74-78` | ~15 |

---

## Doc-Audit (DOC) — Aktueller Stand

| Task | Prio | Befund | Fix |
|---|---|---|---|
| **DOC-01** | 🟡 P2 | **ARCHITECTURE.md + GLOSSARY.md Brücken-Korrektur:** 4 settlement/room/*-Brücken existieren (309 LOC in `src/settlement/room/`). Wurden in v0.13.67 fälschlich gelöscht, jetzt restauriert mit korrekten LOC-Zahlen. | ~0 | DOC |
| **DOC-02** | 🟡 P2 | **BINDUNGSMATRIX J1–J6:** `StatsBehaviour` → `StatsMultipliers` (Vanilla-Source-verifiziert). | Quell-Klasse in BINDUNGSMATRIX korrigieren. |
| **DOC-03** | 🟡 P2 | **agents.md Canary-Update:** Flachwitz-Refresh nach Rule-7-Pflicht (Session-Start, doc-Update). | ~2 | DOC |

---

## Tagebuch-Abgleich — Claims vs Code (v0.13.67)

**Quelle:** Senior-Dev-Tagebuch, Wochenende 26.7.26. 17 Claims systematisch gegen den Code geprüft.

### Verifizierte Claims (11/17 bestätigt)

| # | Tagebuch-Claim | Code-Realität | Status |
|---|---|---|---|
| 1 | 5 Wirtschaftsstufen: Subsistenz→Handel→Industrie→Wohlstand→Imperium | `EconProgression.Stage`: SUBSISTENZ, HANDEL, INDUSTRIE, WOHLSTAND, IMPERIUM | ✅ |
| 2 | SAVE_VERSION = 33 | `EconomySim.CHUNKED_VERSION = 33` | ✅ |
| 3 | TreasuryCrisis 5 Tiers + Hard Floor + Grace Period | Tier 1–5, Grace unterdrückt 1–4, Tier 5 immer aktiv | ✅ ~5 Tiers, nicht 6 |
| 4 | Gini→Loyalty-Kopplung existiert | `GiniConsequences` bindet Gini an `BOOSTABLES.BEHAVIOUR().LOYALTY` | ✅ |
| 5 | ReentryGuard mit `volatile` + injizierbarem Log-Kanal | `volatile inProgress/hasWarned` + `Consumer<String> logSink` im Konstruktor | ✅ |
| 6 | God-Class-Guard existiert als Build-Gate | Gate 9 in `build-gate.sh`, 149 Dateien gescannt, `SKIP_GOD_GUARD=1` Toggle | ✅ |
| 7 | WarehouseMarket wurde aufgeteilt | WholesaleEngine 555 LOC + AutoProcurementEngine 187 LOC + WarehouseMarket Facade 496 LOC | ✅ |
| 8 | SchemaValidator Fail-Fast existiert | `Class.forName` + `getDeclaredField` in `validate()`, tracked `failed` count | ✅ |
| 9 | Save-Migration existiert (v32→v33) | `EconomySim.load()`: `if (version >= CHUNKED_VERSION)` Migrations-Pfad | ✅ |
| 10 | settlement/room/ Brücken existieren | 4 Dateien, 309 LOC, direkt in `src/settlement/room/` (NICHT unter vannon-package) | ✅ |
| 11 | Hartcodierte Strings, keine Lokalisierung | `EconTexts.java`: 358 String-Konstanten. UI: 369 String-Literale. 0 Übersetzungssystem. | ✅ |

### Korrigierte Claims (6/17 — Tagebuch weicht ab)

| # | Tagebuch-Claim | Code-Realität | Delta |
|---|---|---|---|
| 12 | "175 Java-Dateien" | 149 Source-Dateien (145 vannon + 4 bridges) + 26 Test-Dateien = 175 total | ✅ Summe stimmt, aber 149 Source (nicht 175) |
| 13 | "EconomySim 1683 LOC" | 1382 LOC (God-Class-Guard-Baseline) | −301 LOC — Tagebuch überschätzt |
| 14 | "EconConfig 205 Felder" | 207 Felder (55 bool + 75 int + 61 double + 16 other) | +2 — Tagebuch unterschätzt knapp |
| 15 | "357 hartcodierte Strings" | 358 EconTexts Konstanten + 369 UI-Literale | +1 (EconTexts) — Tagebuch minimal unterschätzt |
| 16 | "26 echte Tests" | 402 @Test-Methoden in 26 Test-Dateien | Tagebuch meinte Test-DATEIEN, nicht Test-METHODEN |
| 17 | "TreasuryCrisis 6 Tiers" | 5 Tiers + Grace Period (kein 6. Tier) | −1 Tier — Tagebuch zählte Grace als Tier |

### Neue ROADMAP-Tasks aus diesem Abgleich

| Task | Prio | Beschreibung |
|---|---|---|
| **LOC-01** | 🟠 P1 | 358 Strings → `LocaleStrings.java` (74 LOC) + `EconConfig.locale`. Schrittweise Migration über `LocaleStrings.t("key", EconTexts.¤¤fallback)`. |
| **TECHD-01** | 🟡 P2 | EconomySim 1692 LOC → 3 Extraktionen: `EconomySaveLoad` (Save/Load, ~400 LOC), `EconomyTickOrchestrator` (Phasen 7-10, ~250 LOC), `EconomyAuditEngine` (Phase 11, ~150 LOC). Ziel: ~900 LOC. |
| **TECHD-02** | 🟡 P2 | EconConfig 555 LOC → 3 Sub-Configs: `BalanceConfig`, `BehaviorConfig`, `PhaseConfig`. `EconConfig.locale` hinzugefügt. |
| **TEST-01** | 🟠 P1 | Save-Migration-Integrationstest (CHUNKED_VERSION 33) |
| **DA-01** | 🟡 P2 | Tagebuch-Abgleich v0.13.67 (diese Sektion) |

**Fazit:** 11/17 Claims bestätigt, 6 numerische Abweichungen (alle ±klein, keine strukturellen Fehler).
Das Tagebuch ist eine akkurate Außenperspektive auf den Code-Stand v0.13.64→v0.13.67.

---

## Sprint DIPLO — Faction-Opinion/Trust-Mechanik (Vanilla-Lücke)

**Quelle:** Vanilla-Source-Analyse `BOOSTABLES.CIVICS().bOpinion` + `ROPINION.trust()` vs. Mod-Code

**Befund:** Die Vanilla-Mechanik "Faktionen verlieren Wohlwollen" ist im Mod praktisch nicht abgebildet:
- `ROPINION.trust()` wird NUR in DebtDiplomacyBuffer.java:91 GELESEN (um Kriegsbereitschaft zu prüfen)
- Der Mod schreibt NIEMALS Trust/Opinion-Werte — kann sie also nicht beeinflussen
- `BOOSTABLES.CIVICS().bOpinion` (default 1.5, "Determines the opinion of other factions") — ungenutzt
- `BOOSTABLES.CIVICS().TRUST` (default 0, "A faction's trust") — ungenutzt
- `royaltyOpinionEnabled = true` in EngineLevers — toter Config-Flag, kein Consumer
- IFactionAccess deklariert "Royalty — Opinion, Trust" aber implementiert nur `getKing()` + `getRulerName()`
- BINDUNGSMATRIX hat 0 Einträge für `ROPINION`, `bOpinion`, `TRUST`

**Warum das wichtig ist:** Wenn die Spieler-Wirtschaft kollabiert (Treasury -1.8M, Gini 0.946, Bürger verhungern), sollten NPC-Fraktionen das Vertrauen verlieren → Handel wird teurer/schwieriger, Krieg wahrscheinlicher. Ohne diese Rückkopplung ist der Wirtschafts-Kollaps folgenlos für die Außenpolitik.

| Task | Prio | Beschreibung | LoC |
|---|---|---|---|
| **DIPLO-01** | ✅ Closed (v0.13.67) | `ROPINION.trust()` Read + EconomySim Opinion-Monitoring implementiert. Write deferred to DIPLO-02. | `EconomySim.java:925`, `FactionAccessImpl.java:457-505` |
| **DIPLO-02** | 🟡 P2 → DIPLO-03 | Write-Pfad via BypassGate: `ROPINION.setOpinionValue()` + `SuperBoostable.incD()` aufschließen. Read-Pfad (DIPLO-01) ist komplett. | `FactionAccessImpl.java:457` |
| **DIPLO-03** | 🟡 P2 | **BINDUNGSMATRIX: ROPINION + bOpinion + TRUST katalogisieren** — 3 Engine-Hebel dokumentieren (Klasse, Zugriffspfad, Mod-Nutzung). | ~10 |

**Geschätzt:** 3 Tasks, ~95 LoC

---

## Sprint TECHD — EconomySim Modularisierung (v0.13.67 Plan)

**Problem:** `EconomySim.java` hat **1692 LOC** (God-Class-Guard-Baseline: 1382, Drift: +310).
74 pub-Methoden, 129 Felder, 73 Imports. Die God-Class wächst unkontrolliert.
WarehouseMarket wurde erfolgreich gesplittet (M-1: 1902→320 LOC Facade),
EconomySim nicht.

**Lösung: StateBundle-Extraction-Pattern** (wie M-1, aber für Save/Load):

### Extraktion 1: EconomySaveLoad.java (~400 LOC)
- **Quelle:** `EconomySim.java:1147-1350` (save/load-Methoden)
- **Enthält:** `saveChunked(FilePutter)`, `loadChunked(FileGetter)`,
  21 Chunk-Tags (TAG_CORE_SCALARS..TAG_TREASURY_CRISIS),
  `saveSubsystemChunk()`, `loadSubsystemChunk()`, Legacy-Stream-Fallback,
  CHUNK_MAGIC, CHUNKED_VERSION
- **Interface:** `EconomySaveLoad.load(EconomySimStateBundle, FileGetter)`
  und `EconomySaveLoad.save(EconomySimStateBundle, FilePutter)`
- **StateBundle:** Neues inneres Record mit allen 21 Subsystem-Referenzen

### Extraktion 2: EconomyTickOrchestrator.java (~250 LOC)
- **Quelle:** `EconomySim.java:700-950` (update()-Methode, Phasen 7-10)
- **Enthält:** Wages→Taxes→Fiscal→FirmLedger Phasen-Ausführung,
  FlowPrices.refresh(), LaborMarket.update(), ServiceMarket.tick()
- **Interface:** `EconomyTickOrchestrator.tick(EconomySimStateBundle, int day)`

### Extraktion 3: EconomyAuditEngine.java (~150 LOC)
- **Quelle:** `EconomySim.java:950-1050` (update()-Methode, Phase 11+Diagnostik)
- **Enthält:** AuditKernel.run(), MoneySupply-Check, GiniConsequences,
  EconSnapshot-Sampling, DiagnosticExporter-Trigger
- **Interface:** `EconomyAuditEngine.audit(EconomySimStateBundle)`

### Goal
- EconomySim nach Extraktion: **~900 LOC** (unter God-Class-Guard-Schwelle 800? Fast.)
- God-Class-Guard-Baseline updaten: 1692→900
- Kein Verhalten ändern — nur Datei-Split

---

## Sprint Spluck-TECHD-01 — EconomySim Triple-Limit-Split (RES-005 Pitch, v0.13.74)

**Quelle:** RES-005 Audit-Pitch [`docs/HANDOFF_RES005.md`](docs/HANDOFF_RES005.md)
**Status:** Pitch-Phase (vor Sprint-Start, Tasks 8+9 als `Active` vorbereitet)
**Branch:** `feature/spluck-techd-01-economysim-split` (von main @ v0.13.74)

**Problem:** `EconomySim.java` ist die einzige Triple-Limit-God-Class (LOC +522 / Fields +100 / PubM +42 über Schwellwert). Reflection-Reste in `WindowState.java` (4 Hits) und `NpcFactionAdapter.java`/`RoomAccessImpl.java` (7 Hits) verletzen Rule 9.

**Lösung (atomic-task pitch):** 8 neue Dateien in `core/save/` Subpaket + Reflection-Migration auf BypassGate SDK.

| Task | Datei | LOC-Soll | Status | Sprint |
|---|---|---:|---|---|
| **TASK-008** | `EconomySaveLoad.java` (Extr. 1) | ~450 | 🟠 P1 (Active) | Spluck-TECHD-01 |
| **TASK-009** | `EconomyTickOrchestrator.java` (Extr. 2) | ~280 | 🟠 P1 (Active) | Spluck-TECHD-01 |
| Spluck-T-1 | `EconomyAuditEngine.java` (Extr. 3) | ~150 | 🟡 P2 | Spluck-TECHD-01 |
| Spluck-T-2 | `EconomyTelemetry.java` (StateBundle) | ~120 | 🟡 P2 | Spluck-TECHD-01 |
| Spluck-T-3 | `IEconomySaveLoad.java` (Interface) | ~50 | 🟡 P2 | Spluck-TECHD-01 |
| Spluck-T-4 | `IEconomyTick.java` (Interface) | ~40 | 🟡 P2 | Spluck-TECHD-01 |
| Spluck-T-5 | `IEconomyAuditEngine.java` (Interface) | ~40 | 🟡 P2 | Spluck-TECHD-01 |
| Spluck-T-6 | `IEconomyTelemetry.java` (Interface) | ~30 | 🟡 P2 | Spluck-TECHD-01 |
| Spluck-T-7 | EconomySim-Restrumpf (Facade/Delegation) | ≤ 450 | 🟠 P1 | Spluck-TECHD-01 |
| Spluck-T-8 | EconConfig-Magic-Number-Regrouping | ~50 | 🟡 P2 | Spluck-TECHD-01 |
| Spluck-T-9 | WindowState.java Reflection→ISyxBoosting | Δ−4 | 🟠 P1 | Spluck-TECHD-01 |
| Spluck-T-10 | NpcFactionAdapter/RoomAccessImpl Reflection→BypassGate | Δ−7 | 🟠 P1 | Spluck-TECHD-01 |
| Spluck-T-11 | EngineLevers.java unused-import entfernen | Δ−1 | 🟡 P2 | Spluck-TECHD-01 |
| Spluck-T-12 | Validierungs-Gate + Atomic-Commit + ROADMAP-Close | — | 🔴 P0 | Spluck-TECHD-01 |
| **Total** | **8 neue Dateien + 5 Edits** | **~1.485** | **1 atomic Commit** | |

**Definition of Done:** Siehe [`docs/HANDOFF_RES005.md`](docs/HANDOFF_RES005.md) Block 5 (10 Kriterien, inkl. `EconomySim.java` ≤ 800 LOC + keine Reflection außerhalb `adapter/seam/` + ROADMAP-TASK-008/009 → `Closed (<SHA>)`).

**Dependency-Chain:**
```
Spluck-T-3 (IEconomySaveLoad) ──→ TASK-008 (EconomySaveLoad)
Spluck-T-4 (IEconomyTick)      ──→ TASK-009 (EconomyTickOrchestrator)
Spluck-T-5 (IEconomyAudit)     ──→ Spluck-T-1 (EconomyAuditEngine)
Spluck-T-6 (IEconomyTelemetry) ──→ Spluck-T-2 (EconomyTelemetry)
TASK-008 + TASK-009 + Spluck-T-1..2 ──→ Spluck-T-7 (Restrumpf-Anpassung)
Spluck-T-9..11 (Reflection-Cleanup vor Refactor)
Spluck-T-7 + Spluck-T-9..11 ──→ Spluck-T-12 (Validierungs-Gate)
```

**Sprint-Boundary:** 14 Tasks, ~1485 LOC Δ, 1 atomic Commit. Kein Push ohne User-Approval (Rule 11/12).

**Task-Naming-Konvention Spluck-TECHD-01:** `TASK-008` und `TASK-009` sind external atomare Pitch-Marker (vom User literal benannt, Block-sichtbar). `Spluck-T-1..12` sind die internen Sub-Tasks der 14-Task-Liste aus HANDOFF_RES005.md Block 4. Beide Systeme coexistieren; z.B. `TASK-008` und `Spluck-T-3 (EconomySaveLoad extrahieren)` beziehen sich auf dieselbe Code-Datei `core/save/EconomySaveLoad.java`. **Mapping Tabelle im HANDOFF Block 4 nachschlagen.**

---

## Sprint LOC — Lokalisierungs-Brücke (v0.13.67)

**Problem:** 727 Strings (358 EconTexts-Konstanten + 369 UI-Literale),
alles hartcodiert auf Deutsch. Kein Übersetzungssystem.

**Lösung (nicht-invasiv):** `LocaleStrings.java` (74 LOC, erstellt in v0.13.67)
+ `EconConfig.locale` Flag ("de"|"en").

### Migrationspfad
1. ✅ **LocaleStrings.java** erstellt — English-Fallback-Map + `t(key, de)` API
2. ✅ **EconConfig.locale** Feld hinzugefügt (default "de")
3. **Phase 1:** Tab-Namen + Window-Titel migrieren (17 Strings → `LocaleStrings.t()`)
4. **Phase 2:** UI-Labels migrieren (Denari, /Tag, /Einheit — ~10 Strings)
5. **Phase 3:** EconTexts-Flächendeckung (verbleibende ~330 Konstanten)

### Referenzen
- `EconTexts.java:1-358` — 358 String-Konstanten
- `LocaleStrings.java:1-74` — English-Fallback-Map
- `EconConfig.java:8-10` — `locale` Feld
- UI-Dateien: `WindowEconomy.java`, `WindowOverview.java`, `WindowState.java`,
  `WindowQuickview.java`, `EconHud.java`

---

## Freeze-Status (seit Sprint 6)

| Schicht | Status |
|---|---|
| `core/`, `ui/`, `adapter/` | ❄️ **FROZEN** |
| `tools/vanilla-schema.yaml` | ✅ Engine-Updates (V72: 1 Diff) |
| `EconConfig.java` | ✅ Balancing-Parameter |
| `test/` | ✅ Neue Tests |
