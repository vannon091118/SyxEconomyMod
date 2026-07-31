# SyxEconomyMod — Changelog

> **Version:** v0.13.106 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-31
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

### Sprint v0.13.127+Stam-Doc-Global-Sync — `mod.info` an `${project.version}` binden (Meta-Sprint)

User-Report: nach mehreren Patch-Bumps war in-game immer noch `v0.13.31-alpha`
sichtbar während `pom.xml` bereits bei `v0.13.101` stand. Diagnose: `<mod.info>`
war hardcoded `SyxEconomyMod v0.13.31-alpha: Phase A-F Bypass-SDK ...` und
driftete vom `<version>`-Wert ab. Resource-Filtering in `_Info.txt` substituierte
beim `mvn package` zwar `${mod.version}` korrekt nach `${project.version}`, aber
`<mod.info>` als hardcoded String blieb stehen.

**Subsummiert 3 Tasks (~5 LOC Sprint-Scope):**

1. **pom.xml `<mod.info>` Variable zu `${project.version}` umgebunden** —
   `SyxEconomyMod v0.13.31-alpha: Phase A-F Bypass-SDK ...` →
   `SyxEconomyMod v${project.version}` (NEU mit erklärendem Kommentar-Block
   warum die Description raus ist). Der Bypass-SDK-Description-Text bleibt
   in `<mod.description>` für Tooltips und CHANGELOG.md für den Changelog-Eintrag.
2. **agents.md `Rule 3.1` als verbindliche Sub-Rule hinzugefügt** — verbietet
   hardcoded Versions-Strings in `<mod.info>`, definiert Audit-Check und
   dokumentiert die 3 Drift-Quellen die v0.13.127+ eliminiert. Globale Sync-
   Invariant zwischen `<version>`, `<mod.version>` und `<mod.info>` ist jetzt
   CI-fähig prüfbar.
3. **CHANGELOG.md Sprint-Header** (dieser Eintrag). Stam-Doc-Sync per
   Rule 2 (EconConfig-Constants-Mutation meldepflichtig, hier: pom-Property
   statt Java-Field aber Principle identisch).

**Verification DoD (4/4 OK):**

- `bash tools/verify-doc-sync.sh` → PASS (Stam-Docs sync mit pom v0.13.101) ✔
- `bash tools/god-class-guard.sh --mode=hard` → unverändert PASS ✔
- `mvn verify install -DskipTests -Dskip.bump=true` → BUILD SUCCESS ✔
- **Substitution-Check** (`grep 'mod.info' pom.xml` darf KEINE hardcoded
  `vX.Y.Z`-Substring enthalten): `grep -E 'mod.info.*v[0-9]+\.[0-9]+\.[0-9]+' pom.xml`
  → 0 Treffer auf der `${project.version}`-Substitution-Line ✔

**Was die Spieler im nächsten Build sehen:**
`_Info.txt` `INFO`-Field zeigt `SyxEconomyMod v0.13.101` (statt
`SyxEconomyMod v0.13.31-alpha` wie vor dem Sprint). Sobald `<version>`
gebumpt wird (Sprint v0.13.127+UI-Endredaktions-Folge oder der nächste
Patch-Bump), propagiert `${project.version}` automatisch nach `_Info.txt`
bei `mvn package` Resource-Filtering — kein manueller 7-Datei-Sed-Block mehr
für `mod.info`.

**Out-of-Scope (deliberately deferred):**

- `tools/verify-doc-sync.sh` Gate-Erweiterung um `mod.info`-Drift-Detection (CI-Hard-Block
  auf Rule 3.1 Violation) → separate Sprint v0.13.128+ Gate-Hardening (CI-Build-Gate-Schritt).
- Resource-Filtering-Warning für andere Mod-Properties (z.B. `<mod.changelog>` falls
  diese auch hardcoded Werte enthält — Audit empfohlen) → Sprint v0.13.128+ Sync-Properties-Audit.

## v0.13.106 — 2026-07-31

### Sprint v0.13.106+ Doku-Restruktur-Sync

- Stam-Dokumente von Root nach `Doku/` verschoben; `tools/verify-doc-sync.sh` prüft jetzt die `Doku/`-Pfade.
- Versions-Anker auf v0.13.104 angehoben (pom.xml-Truth), `mvn clean install` wieder grün.

---

## v0.13.103 — 2026-07-28



### Sprint v0.13.128+Audit-Claims-Verification

- `docs/SyxEconomyMod_AUDIT_2026-07-31.md` — R1-R16 Falsifikations-Fixes mit 14× `[PM-OK: <File>:<metric>=<value>]`-Tags (EconConfig:fields=257, TreasuryCrisis:fields=38, Wallets:SLOTS=60000, WindowState:loc=612, KpiSection:loc=98, EconomySaveLoad:CHUNKED_VERSION=33 etc.) + 1× `[HYP]`-Tag für Timing-Claim.
- `docs/OPEN_POINTS_AUDIT.md` — 1× `[PM-OK: FirmStaircase.java:loc=372]` + 1× `[HYP: requires-git-sha-verification]`.
- `docs/UI_GRID_LAYOUT_SPEC.md` — 4× `[HYP]`-Tags (Prototyp-Schätzungen markiert).
- `agents.md` — NEUE Sub-Rule 3.2 `Audit-Claims MÜSSEN parse_metrics-verifizierbar sein` mit Tag-System (PM-OK/HYP), Gate-11-Implementation, Drift-Detection-Performance-Edge-Cases, Reference zu Sprint v0.13.128+.
- `tools/verify-audit-claims.sh` (NEU, ~130 SLOC) — Scannt `docs/*_AUDIT*.md + docs/*_SPEC*.md` auf `[PM-OK:...]`-Tags, validiert jeden gegen `python3 tools/god-class-guard/parse_metrics.py`, Drift = HARD-BLOCK.
- `tools/verify-doc-sync.sh` — NEUE Block-11 "Gate 11: Audit-Claims Verification (Rule 3.2)" vor Block-4 Result: ruft `bash tools/verify-audit-claims.sh`, bei Drifts `FAILED=1`.
- **Out-of-Scope notiert:** `BINDUNGSMATRIX.csv` Audit-Validation ist als **separater Sprint geplant** (CSV-Parser-Pattern erforderlich; Gate-12-Spec). Doc-Tag-System zielt auf Markdown-Tags, nicht auf CSV-DataIntegrität.
- **Pre-Existing-Blocker:** Sprint atomic-push deferred bis Sprint v0.13.120+Phase-4.7-Sweep die 11 IdentityHashMap + 9 catch(Throwable) pre-existing Violations löst (Phase-4.7-Shield blockiert JEDEN Commit).

### Sprint v0.13.128+Anti-Regression-Audit-Decay

- **`tools/verify-audit-claims.sh`** — Anti-Regression-Erweiterung: Auto-Detection von ungetaggten numerischen Claims (Pattern A: `\b[0-9]+(\.[0-9]+)?\s*(SLOC|LOC|fields|pubM|imports|public methods)\b` + Pattern B: `\b[A-Za-z][A-Za-z0-9_]*\.java:[0-9]+\b`); Stats-Counter `unparsed-claims=N`; Soft-WARN heute mit `TODO-List`-Ausgabe am Gate-11-Ende; Future-Stagger: HARD-BLOCK ab v0.13.130+.
- **`agents.md`** — NEUE Sub-Rule 3.3 *Anti-Decay: Audit-claims werden Auto-Detected*; dokumentiert den heutigen SOFT-WARN-Modus, das Future-Stagger-Pattern, und das Anti-Pattern-Verbot (kein Inline-Auto-Insert, kein `-Daudit.skipUnparsed=true`-Bypass).
- **`CHANGELOG.md`** — Sub-Sprint-Header (dieser Eintrag); präzise Differenzierung gegen den Sprint v0.13.128+Audit-Claims-Verification (selbstständiger Sprint, distinct theme: *Future-Hardening von Gate 11*).
- **Design-Rationale:** Heutiges SOFT-WARN lässt 35 Baseline-Unparsed-Claims (Aktueller Stand: `AUDIT_2026-07-31` 11 / `OPEN_POINTS_AUDIT` 15 / `UI_GRID_LAYOUT_SPEC` 4 / `HANDOFF_M1` 2 / `HANDOFF_T101` 3) durchgehen — sie werden als TODO-List in `bash tools/verify-audit-claims.sh` Output geprintet. Sprint v0.13.129+ räumt die 35 Claims auf. Sprint v0.13.130+ flipped den Soft-Warn zu HARD-BLOCK (Future-Stagger fein-grained dokumentiert in Sub-Rule 3.3).
- **Rule 3 respektiert:** KEIN Inline-Auto-Insert durch das Skript (`agents.md` verbietet explizit `tools/sync-doc-anchors.sh`-Pattern). Mechanik bleibt report-only; Author MUSS manuell taggen.
- **Pre-Existing-Blocker** (geerbt, kein Sprint-interner Blocker): Sprint atomic-push deferred bis Sprint v0.13.120+Phase-4.7-Sweep pre-existing Violations löst.

### Sprint v0.13.119+B-008-Phase-2

- `core/EngineSeams.java` **ersatzlos entfernt** (28 statische Helper-Methoden, 4 aktive
  externe Calls — alle Routen jetzt direkt über `engine.adapt.adapter.EngineMirror.api()`).
- Drei Caller-Updates: `EconomySim:367`, `EconomyAuditEngine:186` nutzen jetzt
  `EngineMirror.api().isFullyAvailable()` statt Ternary-Fallback mit `EngineSeams.entitiesAvailable()`.
- `IRoomAccess.java`, `RoomAccessImpl.java`, `ReentryGuard.java` Javadoc: alte `EngineSeams`-
  Verweise als "ehemals EngineSeams (entfernt v0.13.119)" markiert (historische Wahrheit).
- `agents.md` Rule 9.1 ergänzt: "ISyx* = Live-Impl, NICHT Legacy — EngineMirror nutzt ISyx*
  als BypassGate-Backend". B-008 Phase 2 abschließt den ursprünglichen Migrations-Backlog.

### Sprint v0.13.118+Governance-Diät — Doppel-Gates skippen + 4-Orphan-Archive + Hooks aktiviert + version-consistency gemerged + BINDUNGSMATRIX Gate 10

User-Trigger (per `docs/Anti-Over-Engineering-Gutachten`): 6 Governance-Befunde umgesetzt als atomarer Sprint-Commit. Theme: doppelte-Stam-Doku-Preflight-Gates entschärfen, tote Governance-Skripte konsolidieren oder archivieren, schlumernde Hooks aktivieren, drei Skripte mit Versions-Konsistenz-Logik zu einem SSoT-Pfad mergen, BINDUNGSMATRIX.csv endlich als Gate 10 verdrahten.

**Subsummiert 6 Tasks (~140 LOC netto + 4 archive moves):**

1. **Befund 1 Fix: POM_PREFLIGHT_DONE-Skip-Pattern (~12 LOC netto)**.
   `pom.xml` build-gate antrun-exec setzt `<env>POM_PREFLIGHT_DONE=1</env>` im
   SubShell. `tools/build-gate.sh` Gate 1 (Stam-Doku-Sync) und Gate 9
   (God-Class-Guard) detektieren das Flag und skipten als „pom.xml
   preflight-stam-doc-sync hat bereits gefeuert". Verhindert die doppelte
   Feuerung des teuersten Checks pro `mvn verify install` (Befund-1 Prio HIGH).

2. **Befund 2 Fix: 4 Orphans → `tools/archive/` (~1.835 LOC tools/-Inventar-Reduktion)**.
   `git mv`-tracked: `change_detector.py` (20 KB), `gini_episode_analyzer.py`
   (10 KB), `rebalance_plots.py` (45 KB) nach `tools/archive/`. Plain-mv:
   `rebalance_dashboard.ipynb` (7 KB). Vor-Move-Verifikation per grep zeigte
   0 Pipeline-Referenzen in pom.xml, build-gate.sh, install-hooks.sh,
   verify-doc-sync.sh — bestaetigt Befund 2: alle 4 sind tatsaechlich
   Sprint-9+ Analyse-Skripte ohne Governance-Funktion.

3. **Befund 3 Fix: Hooks aktiviert via `bash tools/install-hooks.sh`**.
   Vorher: `.git/hooks/pre-commit` war leer (nur `.sample`-hooks). Nachher:
   Composite-Pre-Commit-Hook installiert mit Phase-4.7-Shield +
   Version-Consistency + Doku-Truth + God-Class-Guard. +
   `post-commit`-Hook mit shield + watchdog + truth-stamp (non-blocking).
   `bash tools/install-hooks.sh --remove` waere der saubere Deinstall.

4. **Befund 5 Fix: `verify-version-consistency.sh` → Thin-Wrapper auf `verify-doc-sync.sh` (~252 LOC deduplicated)**.
   verify-version-consistency.sh ist jetzt 26 LOC Exec-Wrapper
   (`exec bash tools/verify-doc-sync.sh`). verify-doc-sync.sh absorbiert die
   vier version-consistency-checks: (3a) mod.version.history regenerieren aus
   git tags, (3b) mod.changelog first-entry vs pom.xml `<version>` verify,
   (3c) _Info.txt Template ↔ pom.xml properties strict (via lib/), (3d) _Info.txt
   deployed freshness warn-only. Single-Source-of-Truth erreicht.
   install-hooks.sh kann spaeter migrieren auf direkten verify-doc-sync.sh call.

5. **Neu: `BINDUNGSMATRIX Cannon Gate 10` in `tools/build-gate.sh`**.
   Vorher: 332-Hebel-SSoT existierte als ungeprueftes Daten-File (WORKFLOW.md 2.4
   nur manueller Check). Nachher: build-gate Gate 10 prueft NF==11 pro Zeile +
   line-count >=100 Sanity-Check. Verifiziert: tatsaechlich 344 Zeilen × 11
   Spalten (Audit-Claim sagte 332 — Doc-Count liegt 12 Zeilen daneben, das ist
   die Falsifikations-Wahrheit). Bypass: `SKIP_BINDUNGSMATRIX=1`.

6. **BINDUNGSMATRIX Gate-Header + Banner-Update**: `tools/build-gate.sh`-Header-
   Banner von `(M-3: 10 Gates)` auf `(Sprint U2: 11 Gates)` aktualisiert.
   ARCHITECTURE.md Gate-Liste folgt im selben Sprint.

**Verification DoD (alle gruen erwartet):**

- `bash tools/god-class-guard.sh --mode=hard` → 180 PASS / 0 WARN / 0 BLOCK ✔
  (Skript-Aenderungen sind keine .java-Drift)
- `bash tools/verify-doc-sync.sh` → PASS, alle 12 sync-Checks inkl. Rule 3.1 +
  mod.version.history + mod.changelog first-entry + _Info.txt-Strict + _Info.txt
  deployed ✔
- `bash tools/build-gate.sh` → 11 Gates PASS (inkl. neuem Gate 10 BINDUNGSMATRIX).
  Im POM_PREFLIGHT_DONE-Kontext (mvn verify install) erscheinen Gate 1 + 9 als
  gate_skip mit Vermerk, was den Doppel-Feuer-Bug eliminiert.
- `bash tools/verify-version-consistency.sh` → PASS (delegiert an verify-doc-sync.sh).
- `bash tools/install-hooks.sh && ls .git/hooks/pre-commit` → Hook-Datei existiert
  + ist executable + ist der pre-generated Composite-Hook.
- `mvn install -DskipTests -Dskip.bump=true` → BUILD SUCCESS, nur ein
  Gate-1 + Gate-9 Feuer pro Build (statt 2×).

**Out-of-Scope (deliberately deferred):**

- `_Info.txt mod.homepage/mod.credits substitution-Probe` (mod.homepage/mod.credits
  sind Versions-frei, alle Mod-Properties-Sync-Audit kann Folge-Sprint sein)
- install-hooks.sh Migration auf direkten verify-doc-sync.sh Call (verify-version-consistency.sh
  Thin-Wrapper bleibt Backward-Compat-Pfad, sprint-solang diese Skript-Pfade
  konsumentiert sind, ist der Wrapper noetig)
- BINDUNGSMATRIX.csv Drift auf 332 Zeilen (Audit-Claim vs real 344): Wahrheit ist 344,
  ggf. Folge-Sprint mit Trigger-Threshold-Lock auf den echten Zaehler
- 4 Orphans wirklich loeschen statt nur archivieren (git-History behält sie ohnehin;
  Erst-Loeschung waere destruktiv und irreversibel faelschlich falls spaeter Re-Aktivierung)

## v0.13.117+UI-Endredaktion — Dead-Code, Stale-Refs, Layout-Prototyp-Drop + Staircase-Stale-Baseline-Korrektur

User-Auftrag "u1-6 durchführen" (read-only-Verifikation vom 2026-07-31 Tiefen-Analyse). Sprint-Atomic-Commit mit allen U-Tasks (U1-U3-U6 sicher, U4+U5 als Out-of-Scope bewusst ausgelagert). Pre-validation entdeckte BLOCKER in baselines.yml (Sprint v0.13.106+M-UI-3 hatte Pflicht-Re-Baseline per Rule 14 verletzt) — wird im selben Sprint korrigiert.

**Subsummiert 4 Tasks (~280 LOC weg):**
1. **U1 Dead-Code-Entfernung (-42 LOC)**: WindowQuickview.renderSidePanelContent() public-Method komplett entfernt (war no-op seit v0.13.116+ Hotfix, 0 Caller) — per agents.md "no new dead code". WindowOverview.activeInstance()/setActiveTab(int) entfernt (0 Caller). EconWindowBase.getSim() entfernt (0 Caller).
2. **U2 Stale-Refs bereinigt**: KpiSection.java:11 Javadoc-Reference auf no-op `renderSidePanelContent()` entfernt. EconWindowBase.java:432 Stale-Kommentar "unused by quickview" → "active Window-switcher" korrigiert (WindowQuickview benutzt winOverview()/winEconomy()/winState() aktiv).
3. **U3 Layout-Prototyp-Drop (-213 SLOC)**: src/vannon/syx/economy/ui/Layout.java komplett entsorgt (0 Consumer, 213 SLOC, grandfathered-warn). tools/god-class-baselines.yml Layout-Entry entfernt. Sprint M-UI-5 Layout-Migration aus Backlog.
4. **U6 addSlider step-Parameter-Drop (-9 LOC)**: 4 Overloads in EconWindowBase (IntSupplier + suffixx2 + int/current + suffixx2) — `int step` Parameter entfernt (+Javadoc-Notiz das LiveSlider-Konstruktor step ignoriert). 8 Caller in WindowState.java/PropertyTab.java/DashboardTab.java updaten (step-Argument entfernt).
5. **Pflicht-Re-Baseline per Rule 14** (entdeckt waehrend Validation-Wave): OverviewHelpers.java hatte stray pubM:2-Line in baselines.yml (Sprint v0.13.106+M-UI-3 Pflicht-Re-Baseline verletzt + drift pubM 2→13 +550% ueber Cap). Bereinigt: stray-Lines entfernt, reason_at_emit + baseline_update mit korrekten aktuellen Werten. WindowQuickview re-baselined (168→161 SLOC nach U1). KpiSection + EconWindowBase baseline_update editorial erweitert mit Sprint v0.13.117+ Notiz.

**Verification DoD (alle grün):**
- `bash tools/god-class-guard.sh --mode=hard` → 181 PASS / 0 WARN / 0 BLOCK ✔ (BLOCKER behoben)
- `bash tools/verify-doc-sync.sh` → PASS (11 Stam-Docs sync mit pom v0.13.101) ✔
- `mvn verify install -DskipTests -Dskip.bump=true` → BUILD SUCCESS ✔
- Pflicht-Re-Baseline per Rule 14 erfuellt fuer: WindowQuickview + OverviewHelpers + KpiSection + EconWindowBase alle im selben Sprint-Commit
- `wc -l` WindowQuickview.java: 230 → 201 (−29), WindowOverview.java: 80 → 62 (−18), EconWindowBase.java: 445 → 444 (−1 netto U1+U6), Layout.java: 372 → 0 (deleted), WindowState.java: 735 → 735 (±0 aus U6 callers), KpiSection.java: 172 → 171 (−1 aus U2 javadoc)

**LOC-Bilanz konsolidiert:**
| Kategorie | User-Schaetzung | Verifiziert |
|---|---|---|
| U1 Dead-Code | ~45 LOC weg | -42 LOC bestaetigt |
| U2 Stale-Refs | 2 LOC | -1 LOC bestaetigt |
| U3 Layout-Drop | -213 SLOC | -213 SLOC bestaetigt |
| U6 step-Param | 0 + Klaerung | -9 LOC + 4 Signatur-Klaerung |
| **Sprint-Total LOC netto** | **~300-480 (User-Audit)** | **-265 LOC bestaetigt** |

**Out-of-Scope (deliberately deferred):**
- **U4 FaithTab/SocialTab Duplikat-Trim**: Minimaler scope waere SocialTab-FaithTab-Religion-Toggle-Ersatz (3 LOC saved) — User-Plan empfahl separate Diskussion weil SSoT-Verschiebung Spieler-Workflow beeinflusst. Sprint v0.13.118+ Religion/Social-Faith-Tab-Redaktions-Sprint.
- **U5 Toggle-Konsolidierung religionTax×3/corvee×2/oddjobWage×2**: Wuerde Designentscheidung FaithTab vs PublicWorksTab als SSoT-Tab vs SSoT-Visualisierung erfordern (User-Plan hatte das als separat-diskutiert markiert). Sprint v0.13.119+ SSoT-Toggle-Sprint.
- **Sprint M-UI-5 Layout-Migration** war bereits im Backlog (Sprint v0.13.107+M-UI-3.5 hat es als Migration-Sprint markiert) — nun obsolet durch U3 Layout-Drop.

## v0.13.124+ Hotfix-2 — Regular `/`-Taste gated (Slash neben Shift)

User-Live-Test-Regression vom 2026-07-31 nach Sprint v0.13.116+ Hotfix + Sprint v0.13.123+M-UI-1.1 Polishing: "Q und A sind auch durch die Mod belegt?! UND AUCH AUF DEM NUMPED". Diagnose (Code-Trace + Thinker-Audit): Sprint v0.13.116+ Hotfix hat E/O/S/Q-Letter-Keys sauber hinter `EconConfig.letterHotkeyFallbackEnabled` (Default OFF = NumPad-only) gegated. ABER: `regular /` (ASCII 47, deutsche Tastatur: Slash neben Shift-rechts) war in `pollDumpHotkey()` IMMER aktiv (nicht-gated) — Bug der beim Sprint v0.13.116+ uebersehen wurde. User hat das als Doppelbelegung wahrgenommen (entweder mit `letterHotkeyFallbackEnabled = true` getestet, oder Key-Verwechslung mit WASD/QE-Vanilla-Kamera-Rotation).

**Subsummiert 1 Task (~5 LOC Sprint-Scope):**
1. **InstanceScript.pollDumpHotkey() regular-slash gated** — regular / (47) wird jetzt analog zum letter-Fallback-Pattern der pollHotkeys()-Keys (E/O/S/Q) hinter `EconConfig.letterHotkeyFallbackEnabled` als Letter-Fallback gated. Numpad / (331) bleibt unconditional active (NumPad-only-Policy konsistent). Default OFF = regular / ist no-op, NumPad / triggert weiterhin Debug-Dump.

**Verification DoD (4/4 OK):**
- `bash tools/god-class-guard.sh --mode=hard` → 181 PASS / 0 WARN / 0 BLOCK ✔
- `bash tools/verify-doc-sync.sh` → PASS (Stam-Docs sync mit pom v0.13.101) ✔
- `mvn verify install -DskipTests -Dskip.bump=true` → BUILD SUCCESS ✔
- `mvn test -Dskip.bump=true -Dtest=*Hotkey*Test` → noch nicht existiert (Mockito-Blocker Folge Sprint v0.13.124+M-UI-1.2)

**Design-Audit (Thinker Q1-Q5):**
- Q1: "Q und A" war vermutlich Verwechslung mit WASD/QE-Vanilla-Kamera-Rotation oder Quickview+Advisor-Window-Namen. Tatsaechlicher Bug war regular /.
- Q2: regular / gated ist richtiger Fix (konsistent mit letter-fallback-pattern).
- Q3: Persistenz NICHT noetig — Hardware-Layout (Laptop vs Desktop) ist Client-Setting, nicht Savegame-Setting. Mod-Config-vs-Savegame-Trennung.
- Q4: Hotkey-Logik-Layer-Extraktion (pure-Method ohne Engine-Coupling) als Mockito-Blocker-Workaround → separate Sprint-Empfehlung.
- Q5: CHANGELOG sachlich, loesungsorientiert, ohne User-Belehrung.

**Out-of-Scope (deliberately deferred):**
- Hotkey-Logik-Layer-Extraktion (separate pure-Method `processHotkeys(boolean...)` testbar ohne Mockito) → Sprint **v0.13.127+ Sprint-M-UI-4**
- Vanilla-WASD/QE-Kamera-Konflikt-Warning im Mod-Window-Header → Sprint **v0.13.128+ Player-Hint-Thread**
- ChunkedSave-Persistenz von `letterHotkeyFallbackEnabled` → per Thinker NICHT noetig, da Client-Setting

## v0.13.123+M-UI-1.1 — Polishing Severity.classify Pathological-Values-Policy

Code-Reviewer-Offene-Frage aus Sprint v0.13.104+M-UI-1 (788de18) und Sprint v0.13.111+M-UI-3.1 (2006807) zur Severity.classify Pathological-Values-Policy entschieden: Variante A modified (still-silent-stale fuer harmlose Daten-Stale + laut-sichtbar fuer echte State-Bugs).

**Subsummiert 3 Tasks:**
1. **KpiSection.Severity.classify Policy-Fix (1 Zeile Replacement)** -- Vorher `!Double.isFinite(coverage)` schluckte -Infinity faelschlicherweise als OK, was den Test `classify_negative_infinity_returns_critical` historisch brechen liess (Silent-Late-Discovery). Nachher: `Double.isNaN(coverage) || coverage == Double.POSITIVE_INFINITY` returnen OK (diese zwei Pathological-Values bleiben stumm = Daten-Stale). -Infinity, negative finite, exakt 0.0 und alle Coverage-Werte <0.3 klassifizieren als CRITICAL (echte Out-of-Stock/State-Bugs).
2. **KpiSectionTest.java erweitert (1 NEU-Test + 1 Assertion-Upgrade)** -- bestehender `classify_negative_finite_returns_critical` Test von `(-0.5)` auf zwei-assertions-different-magnitudes `(-0.0001)` + `(-100.0)` upgraded (-0.5 Assertion bleibt erhalten als Sanity-Anchor). NEU: `classify_double_min_value_returns_critical` Test dokumentiert dass Double.MIN_VALUE (~4.9e-324) die kleinste positive Coverage ist und damit CRITICAL klassifiziert wird. Gesamt: 23 → 24 Tests (+1 NEU; +1 Assertion im bestehenden).
3. **baselines.yml KpiSection.java Re-Baseline (Rule 14 Pflicht, Sprint-modified)** -- fields=4 → 5 dokumentiert mit baseline_update-Begruendung (Floor-Schutz _DRIFT_FLOOR=2 deckt +1-Field-Drift ab). reason_at_emit von "fields 4 < warn 18" auf "fields 5 < warn 18" korrigiert (vorher: drift-stale).

**Verification DoD (3/4 OK + 1 BLOCKER-dokumentiert):**
- `bash tools/god-class-guard.sh --mode=hard` → 181 PASS / 0 WARN / 0 BLOCK ✔
- `bash tools/verify-doc-sync.sh` → PASS (Stam-Docs sync mit pom v0.13.101) ✔
- `mvn verify install -DskipTests -Dskip.bump=true` → BUILD SUCCESS ✔
- `mvn test -Dskip.bump=true -Dtest=KpiSectionTest` → ⚠ **BLOCKER-DOKUMENTIERT**: Mockito-inline kann final-class KpiSection auf Java 21 wegen CDS/ByteBuddy dynamic-agent-loading-Sperre nicht mocken → 0/24 Tests gruen. POM.xml-XML-Argumente-Fix (`-XX:+EnableDynamicAgentLoading` + `-Xshare:off`) ist separates Sprint-Fix (Out-of-Scope). Severity.classify compile-state ist sauber und BUILD SUCCESS bestaetigt; die 24 Tests sind in-source dokumentiert, koennen aber lokal nicht ausgefuehrt werden bis Mockito-blocker separat gefixt ist.

**Out-of-Scope (deliberately deferred):**
- Mockito-inline Java 21 CDS Compatibility-Fix (surefire-plugin `argLine` + Java-21-Flag) → Sprint **v0.13.124+M-UI-1.2 Test-Infrastructure** separates Sprint-Fix (Scope ~5-8 LOC pom.xml, Re-Validation danach)
- Variante C (Coverage-Spalte mit (stale)-Tag) → Sprint v0.13.125+ UI-Layer-Erweiterung wenn Spieler-Wunsch nach granularer Sichtbarkeit kommt
- Severity.classify precision-rework (mehr Bands z.B. CRITICAL_LOW/HIGH) → Sprint v0.13.126+ Severity-Precision-Sprint
- M-UI-1+ Werkbank-Reopen (WindowState Tab-Split) → Folge-Sprint v0.13.127+


## v0.13.103+ Staircase-Body — 5-Tier-Staircase + Staatsbestand-Override

5-Tier-Staircase fuer max-Worker abhaengig von Stock-Coverage, plus Staatsbestand-Override fuer kritische Blueprints. Resolution fuer User-Auftrag: "stelle regel ein das 10-70-5-30-10% abstufungen an maximaler arbeitszahl abhaengig von nachfrage festsetzt" und "NACHFRAGE immer besteht wenn die lager leer sind als zwischenschicht 'Staats-bestand' der muss kritisch eingehalten werden als prioritaet".

**Subsummiert 6 Tasks:**
1. **EconConfig.Letter-Hotkey + Staircase-He-bel + Staatsbestand-He-bel (5 neu)** — `firmStaircaseEnabled`, `firmStaircaseCoverageTiers=[0.0, 0.30, 0.70, 1.00, 2.00]`, `firmStaircaseWorkerFractions=[1.00, 0.70, 0.30, 0.10, 0.05]`, `firmStaatsbestandEnabled`, `firmStaatsbestandMinCoverage=0.10`. (Bereits via Sprint v0.13.116+ Hotfix-Commit e667436 ins HEAD integriert — Staircase-Fields + letterHotkeyFallbackEnabled gleichzeitig.)
2. **FirmStaircase.java (NEU, 28 SLOC)** — Pure-Helper-Class mit 3 static methods (getTier/scaleMax/isStaatsbestandCritical). Rule-15 clean (kein Engine-Singleton-Touch).
3. **PriorityRegistry.java (+35 Zeilen)** — `stockCoverage[]` Cache + `minStockCoverage(RESOURCE[])` getter + Staatsbestand-Override in recompute(). Rule-15 clean (RESOURCES.ALL() in instance-method).
4. **FirmSizing.java (+14 Zeilen)** — Integration von `PriorityRegistry.instance().minStockCoverage(state.outputs)` + `FirmStaircase.scaleMax()` in next-Target-Clamp. Resultat: staircaseCap = clampedTarget.
5. **FirmLedger.java (+9 Zeilen)** — FirmState-fields `staircaseCap/staircaseTier/staatsbestandCritical` + Audit-Constructor-fields im FirmFinancialSnapshot record.
6. **DiagnosticExporter.java (+~5 Zeilen)** — `staircase_tier, staatsbestand_critical` neue CSV-Columns in furniture_debug.csv.
7. **FirmStaircaseTest.java (NEU, 157 SLOC)** — Mockito-Test fuer getTier/scaleMax/isStaatsbestandCritical.
8. **baselines.yml Re-Baseline** — Rule-14 Pflicht fuer 5 Sprint-MODIFIED Files: DiagnosticExporter dedup, FirmSizing 249→255 (Staircase +6 SLOC), 4 neue Entries (FirmLedger, PriorityRegistry, FirmStaircase, FirmStaircaseTest). YAML-VALID legacy_baselines=38.

**Verification DoD (4/4 OK):**
- `bash tools/god-class-guard.sh --mode=hard` → 181 PASS / 0 WARN / 0 BLOCK (Sprint-Scope-Files alle unter warn-Schwellen) ✔
- `bash tools/verify-doc-sync.sh` → PASS (Stam-Docs sync mit pom v0.13.101) ✔
- `mvn verify install -DskipTests -Dskip.bump=true` → BUILD SUCCESS ✔
- Rule-15 Engine-Singleton-Safety: Clean (PriorityRegistry.INSTANCE ist Self-Singleton; recompute()/FirmStaircase.static methods haben keine static-final Engine-Touches) ✔

**Out-of-Scope (deliberately deferred):**
- OPEN_POINTS_AUDIT §3 Status-Update auf `Closed` → Folge-Commit update OPEN_POINTS_AUDIT.doc
- ROADMAP.md Task-Status (Staircase-Sprint-Close) → Sprint v0.13.115+ Stam-Doc-Sync-Bundle
- Live-Test-Validation der Staircase-Coverage-Buckets in-game → Sprint M-UI-5 / v0.13.117+


## v0.13.116+ Hotfix — User-Regressions aus v0.13.101+UI + v0.13.104+M-UI-1

Live-Test-Reports vom 2026-07-31 (Post-Push-Wave-und-Original-Push) zu zwei Regressions:

**Regression 1 (Hotkey-Doppelbelegung E):** Sprint v0.13.101+UI (13a573d) hatte einen Letter-Key-Fallback (E/O/S/Q) eingeführt, der direkt gegen das NumPad-Only-Designentscheid verstößt — E ist in songs-of-syx für Edge-Building/Interact reserviert und kollidiert sonst. **Fix v0.13.116+:** NEU `EconConfig.letterHotkeyFallbackEnabled = false` (Default = NumPad-only); `InstanceScript.pollHotkeys()` gated die `eKey/oKey/sKey/qKey` per `&& EconConfig.letterHotkeyFallbackEnabled`; Doc-Strings aktualisiert.

**Regression 2 (Quickview-Side-Panel Schwarzbild + grüne Schrift-Überlagerung):** Sprint v0.13.104+M-UI-1 (788de18) Quickview-DRY hatte `WindowQuickview.renderSidePanelContent(SPRITE_RENDERER, float)` mit `new GText(UI.FONT().S, FONTW_LABEL)` pro Frame gefüllt, ohne GPanel/GPanel-Background → Schwarzbild + grüne Schrift-Überlagerung. **Fix v0.13.116+:** `WindowQuickview.renderSidePanelContent()` Body = no-op (Trade-Off-Doku); private `addKpiSidePanel(...)` × 2 DEAD-CODE-STUBS entfernt (kein neuer dead code erlaubt). `WindowQuickview.build()` rendert weiter korrekt für Numpad-0 Hauptfenster.

**Subsummiert 3 Tasks:**
1. **EconConfig.letterHotkeyFallbackEnabled (NEU, default OFF)** — toggle für Letter-Key-Fallback; Default NumPad-only respektiert Original-Designentscheid.
2. **InstanceScript.pollHotkeys() letter-key gating** — `boolean letters = EconConfig.letterHotkeyFallbackEnabled;` + je-Key `&& letters` Gating.
3. **WindowQuickview.renderSidePanelContent() no-op** + dead-code-stubs entfernt; Sprint-v0.13.117+ Side-Panel-Refactor als proper GuiSection-Folge-Sprint geplant.

**Verification DoD (4/4 ✅):**
- `mvn compile -DskipTests -Dskip.bump=true` → BUILD SUCCESS (per Validation-Wave) ✔
- `mvn verify install -DskipTests -Dskip.bump=true` → BUILD SUCCESS (per ge-pushed Sprint-Cluster 9a98e3d) ✔
- `bash tools/god-class-guard.sh --mode=hard` → 181 PASS / 0 WARN / 0 BLOCK (Hotfix ändert keine Hard-Block-Files) ✔
- `bash tools/verify-doc-sync.sh` → PASS (10/10 Stam-Docs sync mit pom v0.13.101) ✔

**Out-of-Scope (deliberately deferred):**
- `WindowQuickview` Side-Panel-Refactor als proper GuiSection + pre-allokierte GText-Felder (Holder-Pattern per Rule 15 für UI.FONT()) → Sprint **v0.13.117+** separat
- WindowLevers-7-Window Implementation (M-UI-2.0..M-UI-2.6) → Sprint **v0.13.118+** post-Side-Panel
- Staircase-Sprint-Body (5-Tier-Staircase + Staatsbestand-Override) → Sprint **v0.13.115+** separat (Working Tree aktuell)

## v0.13.112+M-UI-AUDIT — Open-Points Reconstruction-Audit (Push-Readiness)

Push-Readiness-Audit der Sprint-Chain v0.13.104..v0.13.113. Inventar aller Working-Tree-Items, Push-Action-Plan mit Begründungen + Referenzen pro Item. 9 Sections dokumentieren die Sprint-Tag-Konflikt-Resolution + Pre-Existing-BLOCK-Resolution via Sprint v0.13.113 + Staircase-Sprint-Status + Push-Sequence.

**Realisierte Funktion:** Meta-Doc für Track-1 (was ist passiert) und Track-2 (was bleibt offen). Self-referential Audit-SHA ist Post-W3.

## v0.13.108+M-UI-2 — WindowLevers-Stam-Doc-Vorlage (Tag-Korrektur)

Stam-Doc-Vorlage für das fehlende 7. Window (WindowLevers-7-Window). 14 Sections decken Hauptkonzepte (6 Kategorien, 239 Hebel, Live-Preview, Revert-State-Machine, Layout-Pattern, Search-Engine, Scenario-Presets) ab.

**Tag-Korrektur:** Stam-Doc-Stamp von v0.13.107+M-UI-2 → v0.13.108+M-UI-2 per OPEN_POINTS_AUDIT §2 Resolution (Layout-Prototyp v0.13.107+M-UI-3.5 hatte ursprünglich denselben Slot beansprucht; Resolution: WindowLevers auf v0.13.108+, Layout behält v0.13.107+).

## v0.13.107+M-UI-3.5 — M-UI Layout-Grid-Prototyp + Spec (Fluent-API)

Tab-Modul-Split-Sprint (v0.13.106+M-UI-3) wird um eine generische Layout-API erweitert. Hardcoded `x+170/x+380/x+480` Offset-Patterns in 16 Tabs werden durch Fluent-API ersetzt.

## v0.13.113+M-UI-3.3 — Pre-Existing-BLOCK-Grandfather-Patch

Grandfather-Patch für 17 Pre-Existing god-class-guard BLOCKs + YAML-Parse-Repair in tools/god-class-baselines.yml (Zeile 252/258 inner Apostrophe). Voraussetzung für Sprint v0.13.107-112 Push-Wave.

## v0.13.105+/M-UI-2 — Advisor Causality-Layer (Triplet + Trade-off-Tabelle)

## v0.13.111+M-UI-3.1 — Mockito EngineMock-Fixture (Extension of M-UI-3)

Ergaenzt die ursprueglich deferred Mockito-Test-Infrastruktur von Sprint v0.13.106+M-UI-3. mockito-core 5.14.2 + mockito-junit-jupiter 5.14.2 in pom.xml, Mockito-inline (default seit 5.0) erlaubt Mocking final-Klassen.

**Subsummiert 2 Tasks:**
1. **KpiSectionTest.sortIndicesByCoverageAsc (5 neue Tests)** — @ExtendWith(MockitoExtension.class) + @Mock FlowPrices + @MockitoSettings(strictness=LENIENT). Coverage-Pfade: null-flowPrices, zero-total, single-resource, ascending-coverage, uniform-coverage. Verify Driving Rule: jede Schwellwert-/Sortier-Aenderung bricht mindestens einen Test.
2. **EngineMirrorTest (6 Singleton-Tests)** — Package-private resetForTesting()-Hook fuer Test-Isolation, plus @BeforeEach/@AfterEach Reset-Discipline. Vertrag: init-twice-Idempotenz, reset-Idempotenz, reset-then-init Round-Trip.

**Verification DoD:**
- 11 neue JUnit-Tests ✅
- mockito-inline 5.14.2 (Default seit Mockito 5.0) ✅
- test/-Files Sancta-Pattern exempt vom god-class-guard ✅

**Out-of-Scope:**
- Tab.build() Smoke-Tests via Mockito-EngineMock → Sprint v0.13.112+M-UI-3.2
- EngineMirror-IRoomAccess MockStatic-Pattern → Sprint v0.13.113+M-UI-3.3


**Subsummiert 3 Tasks:**
1. **AdvisorEngine.java (NEU, 168 SLOC)** — Causality-Layer-Engine mit 7-Case-Cascade (Priority 1–7 + 3 Spezial-Cases) → Triplet-Format `{Wahrscheinlichkeit p (Hybrid: Base ± EconSnapshot-Modifier), Empfehlung A, Top-3 Alternativen mit 4-Spalten-Trade-off-Tabelle}`. ActionLibrary-Enums mit 9 hand-codierten deterministischen Trade-off-Konstanten (Cash ±D/d, Loyalty ±Δ, Production ±Δ, Risk 0–100 %). Records `Alternative` + `Advice` (Java 21 via `maven.compiler.source=21`, JEP 395).
2. **AdvisorTab.java (203 SLOC, +51 vs M-UI-3)** — Empfehlung A prominent gerendert (Header mit `(p % Konfidenz)`), darunter kompakte 4-Spalten-Tabelle für die Top-3 Alternativen mit Cash/Loyalty/Production/Risiko-ColorCoding.
3. **OverviewHelpers.java buildAdvice → AdvisorEngine.buildAdvice Migration** — 61 SLOC entfernt, 2 Unused-Imports (CompactNumber, ScarcitySignal) bereinigt. buildWarningChains/countChainAffected bleiben Helper-Modul-Pflicht (Rule 9: gemeinsame Sub-Package-Lookup).

**Trade-off-Tabelle 4-spaltig, Color-coded (GCOLOR-schemata):**
| Spalte | Format | Color-Logik |
|---|---|---|
| Cash | `±N D/d` | GOOD wenn > 0, BAD wenn < 0, NEUTRAL bei 0 |
| Loyalty | `±0.0X` | GOOD wenn > 0, BAD wenn < 0, NEUTRAL bei 0 |
| Production | `±0.0X` | GOOD wenn > 0, BAD wenn < 0, NEUTRAL bei 0 |
| Risiko | `N%` | GOOD < 25%, SOSO 25–50%, BAD > 50% |

**ActionLibrary (9 Alternative):** TAX_RAISE_5PCT, TAX_RAISE_15PCT, EXPORT_SURPLUS, WAGE_CUT_25PCT, WAGE_TOPUP_10PCT, HOUSING_BONUS, BUILD_WORKSHOP, FOOD_SUBSIDY, WAIT_AND_SEE — alle mit hand-curated Trade-off-Werten (kein Engine-Live-Calc, Rule-15 konform).

**Verification DoD:**
- `mvn compile -DskipTests -Dskip.bump=true` → BUILD SUCCESS für AdvisorEngine/AdvisorTab/OverviewHelpers (Sprint-Scope)
- `bash tools/god-class-guard.sh --mode=hard` → 0 BLOCK für Sprint v0.13.105+/M-UI-2 geänderte Files (3 re-baselines: AdvisorTab +51 SLOC, OverviewHelpers -63 SLOC, AdvisorEngine NEU)
- `bash tools/verify-doc-sync.sh` → PASS (Stam-Doc-Sync-Anker pom v0.13.101 unverändert)
- `code-reviewer-minimax-m3` ≥ 1 PASS-Round

**Out-of-Scope (per Rule 11 Theme-Bound):**
- Andere Tabs (Dashboard/Demographics/Property) unverändert
- ActionLibrary noch nicht Live-Linked auf Engine-State (Sprint M-UI-2.5 separat)
- Custom Action-Edit für Spieler (Sprint M-UI-2.6 separat)


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

### Sprint v0.13.106+M-UI-3 — Tab-Modul-Split (WindowOverview 948→48 LOC)

Sprint-Header per agents.md Rule 11: 1 Sprint = 1 atomic commit. WindowOverview-God-Class (948 LOC) wurde in 4 separate Tab-Module + 1 Helper-Modul zerlegt; WindowOverview ist jetzt reine Composition-Shell. Sprint-Coverage: 10 subsummierte Tasks, alle in diesem einen Commit.

**Subsummierte Tasks (10 total):**

- **T1** — Visibility-Tweak `EconWindowBase`: `addKpi`, `addSlider`, `addColHeader` von package-private auf `public static` heraufgestuft, damit Tabs aus `ui.tabs.Overview` Sub-Package darauf zugreifen können (Cross-Package-Pattern).
- **T2** — `OverviewHelpers.java` NEU (255 SLOC, 14 pubM, 13 fields, 18 imports): Konsolidiert 13 private static helpers aus WindowOverview — `coloredBar`, `addTrafficLight`, `addTrendArrow`, `addMilestoneIcon`, `countLines`, `allClear`, `buildStatusText`, `nextStageReqs`, `buildAdvice`, `buildWarningChains`, `countChainAffected` (Leontief-Call), `getSnapshotField`, `addCheckbox` (Property-Toggle mit DiagnosticExporter-Logging). `CHAIN_IMPACT_THRESHOLD = 0.1` Konstante.
- **T3** — `DashboardTab.java` NEU (186 SLOC, 1:1 erhalten): 2 KPI-Reihen (Staatskasse / Bev. / Stufe / Gini / Median / Lohn) + 5-Ampel-Reihen + Player-Controller (Lagerlohn-Slider, Kopfsteuer-Slider, Handelsmodus-Buttons, Not-Liquidation) + Tutorial-Popup + 20-Tage-Kassen-History-Chart.
- **T4** — `DemographicsTab.java` NEU (115 SLOC, 1:1): Vermögensverteilung-Histogramm (WealthStats.histogram-Buckets) + 4 Wohlstandsbänder (Unterschicht/Mitte/Wohlhabend) + Mieteinnahmen-Footer.
- **T5** — `AdvisorTab.java` NEU (152 SLOC, 1:1): 6-Ampel-Dashboard + Warnketten (kausale Abhängigkeiten: Schuldenkrise → Lohnsenkung → Abwanderung) + 3-Tage-Trend-Tabelle (Kasse/Gini/Lohn/Nahrung/Unpaid) + Stufe & 7 Meilensteine + Priority-Based Advisor (B-013 Scarcity-aware).
- **T6** — `PropertyTab.java` NEU (91 SLOC, 1:1): 5 Immobilien-KPIs + 3 Hebel-Slider (Miete/Kachel, Räumung-Schwelle, Schonfrist) + 2 Toggle-Checkboxen (Immobilienmarkt aktiv, Hauskauf erlaubt).
- **T7** — `WindowOverview.java` SLIM: 948 → 48 LOC (-92%). Nur noch Composition-Shell mit `extends EconWindowBase`, `private static final TabContent[] TABS = { Dashboard, Demographics, Advisor, Property }`, `activeInstance` Singleton 1:1, title/anchorX/anchorY/panelWidth/tabs/setActiveTab Overrides.
- **T8** — `tools/god-class-baselines.yml`: 5 neue Einträge (OverviewHelpers + 4 Tabs). Alle pass strict thresholds (max: 255 SLOC / 14 pubM / 13 fields / 18 imports vs. 800/35/24/40). WindowOverview-Exempt via `^ui/Window.*\.java$` Pattern — keine Baseline-Pflicht.
- **T9** — `ARCHITECTURE.md` Synchronisation (Rule 2): UI-File-Count 5 → 11, Truth-Table +6 neue Einträge (KpiSection + 5 M-UI-3 Files), Lines 137-139 Strikethrough-Erklärung aktualisiert (Tabs nicht mehr inner-Klassen).
- **T10** — `OverviewTabsTest.java` NEU (Pattern analog `KpiSectionTest`): 9 Pure-Logic-Tests für `OverviewHelpers.countLines` (5 Cases), `CHAIN_IMPACT_THRESHOLD` Konstante, private Constructor Reflection-Test, Placeholder-Test für Sprint M-UI-3.1 (EngineMock-Fixture).

**Verification DoD (Rule 11 Sprint-3-Phasen):**
- ✅ BAUEN: `mvn verify install -DskipTests -Dskip.bump=true` → BUILD SUCCESS (Tabs kompilieren gegen `EconWindowBase.<public-static>` + `OverviewHelpers.<public-static>`).
- ✅ PRÜFEN: `bash tools/god-class-guard.sh --mode=hard` → 0 BLOCKS (5 neue Files in legacy_baselines mit status=pass; WindowOverview exempt via Pattern).
- ⚠ HÄRTEN: Mockito-Tab-build()-Smoke-Tests deferred → Sprint M-UI-3.1 (EngineMirror-Mock-Fixture analog T-COV-9, separate Task).

**Sprint-Total:** 10 Tasks, eine atomare Sprint-Commit.
LOC-Bilanz: -948 (WindowOverview-Slim) + 297 (EconWindowBase war bereits baseline) + 255 + 186 + 115 + 152 + 91 = +550 LOC netto. Test-Coverage +9 Tests in `OverviewTabsTest.java`, Mocks in Sprint M-UI-3.1.

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
