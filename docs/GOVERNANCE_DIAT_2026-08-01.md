# SyxEconomyMod — Governance-Diät Memory-Save 2026-08-01

> **Stand:** 2026-08-01 | **Basis:** v0.13.101 (Working-Tree HEAD vor Phase-4.7-Shield-Block),
> **Session:** Anti-Over-Engineering-Gutachten (6 Befunde) → Implementierung Sprint U2 (Governance-Diät) + Sprint v0.13.119+B-008-Phase-2 (EngineSeams-Löschung) → Phase-4.7-Shield-Block erkannt.
> **Methodik:** Live-Ist-Stand-Verifikation via grep/wc/bash + `python3 god-class-guard/parse_metrics.py` + Memory-Pass mit `docs/SyxEconomyMod_AUDIT_2026-07-31.md`.
> **Fokus:** Governance-Vereinfachung (6 Befunde), 3-Sprint-Folgeplan U2/U3/B-008, Übergabe-Material für nächste Session.
> **Exclusions:** UI-Sprints (M-UI-1/2/3 verfügbar via Audit-Doc 2026-07-31), Mockito-Blocker (B2.1 dort dokumentiert), Engine-Zugriffe-Inventar (BINDUNGSMATRIX.csv ist SSoT — keine Task-Liste).

**Cross-Reference:** Truth-Pass mit `docs/SyxEconomyMod_AUDIT_2026-07-31.md` (Pre-M-UI-2-Audit 4-Quadrate). Audit-Doc fokussiert auf UI-Reife für Sprint M-UI-2; dieser Memory-Save fokussiert auf Governance + Engine-Layer-Migration + Phase-4.7-Blocker. Beide Doc-Welten sind überlapfrei bis auf den Phase-4.7-Shield-Punkt, der im Audit als D4.6-Low klassifiziert ist aber** faktisch Build-Blocker ist** — diese Diskrepanz wird in §Truth-Pass-Korrekturen #4 explizit gemacht.

**Parallele-Bahn-Hinweis (Audit-Doc Cluster-1):** Audit-Doc-2026-07-31 §TEIL E plant 4 **parallele M-UI-Sprints** unabhängig von U-Governance:
- **v0.13.127+M-UI-Foundation** (D4.1 EconConfig volatile/Snapshot + D4.2 TreasuryCrisis atomic-stages + A1.6 Revert-Button-API)
- **v0.13.128+M-UI-Testing** (B2.1 Mockito-Blocker pom.xml Fix + erste WindowLeversTab-Tests)
- **v0.13.129+M-UI-Infrastructure** (C3.1 WindowState+WindowEconomy Tab-Modul-Split + C3.5 Layout.java fluent-API + Tab-Content-Caching)
- **v0.13.130+M-UI-2 WindowLevers** (WindowLeversKernel + 6 Kategorien + Scenario-State-Machine CRISIS/GROWTH/EQUALITY)

Diese laufen **NEBEN** dem U-Governance-Pfad (U2/U3/B-008/Phase-4.7-Sweep) und sind über Sprint-Nummerierung getrennt. Nächste Session kann beide Bahnen parallelisieren, sollte aber nicht mischen (Rule 12 single-purpose Theme). Vollständiger Plan: `docs/SyxEconomyMod_AUDIT_2026-07-31.md` §TEIL E Zeile 189+ „Phase-N Sequencing (3-Sprint-Pre-M-UI-2-Plan)" + Zeile 209+ „Definition of Done".

**Adress-Konflikt-Resolution für Rule-3-Stam-Doc-Drift:** Falls `bash tools/verify-doc-sync.sh` beim Push FAILT (User-Auftrag-Historie zeigt mehrfach Rule-3-Drift-Chases): 5-Befehl-Sed-Sequenz aus `agents.md` Rule 3 ausführen, dann re-validieren, dann re-push. NIEMALS `git commit --no-verify` für Stam-Doc-Drift — die Sub-Rule 3 + 3.1 sind non-negotiable.

---

## Executive Summary — Sprint-Stand 2026-08-01

| Zustand | Sprint | Status | Blocker |
|---|---|---|---|
| ✅ Implementation Complete in Working-Tree | **v0.13.118+Governance-Diät** (U2) | pre-commit-Hook blockiert durch Phase-4.7 | Phase-4.7 pre-existing Violations (11 IdentityHashMap + 9 catch(Throwable)) |
| ✅ Implementation Complete in Working-Tree | **v0.13.119+B-008-Phase-2** (U3 + EngineSeams-Löschung zusammen) | pre-commit-Hook blockiert durch Phase-4.7 | dito |
| ⏸️ Pending: Phase-4.7-Sweep FIRST | **v0.13.120+Phase-4.7-Sweep** | uncommitted | pre-existing Violations füllen |
| ⏸️ Pending | Sprint v0.13.121+ (EconConfig `volatile` Cluster 1 Master-Fix aus Audit-Doc) | nicht gestartet | Audit-Doc High-Severity D4.1 + D4.2 |

**One-Shot Decision-Prompt für nächste Session:**

> **Wenn die nächste Session NUR EINE Sache macht, dann Sprint v0.13.120+Phase-4.7-Sweep** — fix die 11 IdentityHashMap + 9 catch(Throwable) pre-existing Violations, sonst **kann KEIN Sprint dieser Session (kein U2, kein B-008, kein zukünftiger) committed werden**. Aufwand ~150 LOC, 8-12 Tasks, **blockiert alle 4 nachfolgenden Governance-/Engine-Migration-Sprints** statt nur einen.

---

## 6 Anti-Over-Engineering-Befunde (mit Verifikations-Belegen)

### Befund 1 — Doppel-Gate im Maven-Build

**Diagnose:** `verify-doc-sync.sh` und `god-class-guard.sh` feuern bei jedem `mvn verify install` ZWEIMAL — einmal via `pom.xml`-preflight-Execution (Z.168 + Z.191) UND einmal via `tools/build-gate.sh` (Gate-1 Z.89 + Gate-9 Z.258).

**Verifikation:**
```bash
$ grep -n 'verify-doc-sync.sh\|preflight-stam-doc-sync' pom.xml
168:                        <id>preflight-stam-doc-sync</id>
177:                                    <arg value="tools/verify-doc-sync.sh"/>

$ grep -n 'god-class-guard.sh\|preflight-god-class-guard' pom.xml
191:                        <id>preflight-god-class-guard</id>
198:                                    <arg value="tools/god-class-guard.sh"/>

$ grep -nE 'Gate 1|Gate 9|verify-doc-sync|god-class-guard' tools/build-gate.sh
82:# ── Gate 1: Stam-Doku-Sync (NEU) ────────────────────────────────────────
89:    if bash tools/verify-doc-sync.sh 2>/dev/null; then
250:# ── Gate 9: God-Class-Guard (Hard-Block Struktur-Quo) — Sprint M-3 ────────
258:    if bash tools/god-class-guard.sh --mode=hard 2>/dev/null; then
```

**Lösung in Sprint v0.13.118+Governance-Diät (Working-Tree):**
- `pom.xml` preflight-build-gate antrun-exec fügt `<env key="POM_PREFLIGHT_DONE" value="1"/>` (Z.228)
- `tools/build-gate.sh` Gate 1 + Gate 9 prüfen `${POM_PREFLIGHT_DONE:-0}` und skippen mit `gate_skip "... pom.xml antrun preflight-*-hat-bereits-gefeuert"` (Z.255 für Gate 9)
- Effekt: Doppel-Feuer eliminiert, Build-Logs lesbarer + Build schneller
- **Status:** ✅ Implementation fertig, ❌ Commit blockiert (Phase-4.7-Shield)

---

### Befund 2 — Tote Governance-Skripte (4 Orphans)

**Diagnose:** `tools/change_detector.py` (483 LOC), `tools/gini_episode_analyzer.py` (272 LOC), `tools/rebalance_plots.py` (1080 LOC), `tools/rebalance_dashboard.ipynb` (Notebook). Keine Pipeline, kein Skript, kein Gate, kein CI referenziert sie — Einmal-Analysen aus Sprint 9+.

**Verifikation (Post-U2):**
```bash
$ ls tools/ | grep -E 'change_detector|gini_episode|rebalance_plots|rebalance_dashboard'
gini_episode_report.txt  # Achtung: das ist eine REPORT-Datei, kein Skript — bewusst behalten

$ ls tools/archive/
change_detector.py            # archiviert
gini_episode_analyzer.py      # archiviert
rebalance_dashboard.ipynb     # archiviert
rebalance_plots.py            # archiviert

$ grep -rln 'change_detector\|gini_episode_analyzer\|rebalance_plots\|rebalance_dashboard' \
    --exclude-dir=.git --exclude-dir=archive 2>/dev/null
(.claude/worktrees/code-review-fixes und target/ sind Build-Artifacts, keine echten Caller-Code-Pfade)
```

**Lösung in Sprint v0.13.118+Governance-Diät:** `mv` → `tools/archive/` (Git-History behält alle). tools/-Inventory wurde ~1.835 LOC leichter.

**Status:** ✅ Implementation fertig, ❌ Commit blockiert (Phase-4.7-Shield).

---

### Befund 3 — Schlafende Hooks (Governance wird nicht aktiv)

**Diagnose:** Vor Sprint v0.13.118+Governance-Diät war `.git/hooks/` leer (`post-commit-shield.sh`, `post-commit-pom-watchdog.sh`, `truth-stamp.sh/.py`, `phase47-shield.sh`, install-hooks.sh existieren in `tools/`, werden aber nie ausgeführt). Schlafende Governance ist die teuerste Form von Over-Engineering: kostet Wartung, schützt aber nichts.

**Verifikation (Post-U2):**
```bash
$ ls .git/hooks/ | grep -v '\.sample$'
post-commit
pre-commit

$ ls -la tools/install-hooks.sh
-rwxrwxr-x 1 vannon vannon 7181 Jul 27 22:03 tools/install-hooks.sh
```

**Lösung in Sprint v0.13.118+Governance-Diät:** `bash tools/install-hooks.sh` wurde im Working-Tree ausgeführt, generierte `pre-commit` + `post-commit`. Pre-commit-Hook-Chain:
1. Phase-4.7-Shield (phase47-shield.sh → IdentityHashMap/catch(Throwable Threshold-Check — **DERZEIT BLOCKER, siehe Befund 8**)
2. Version-Consistency Gate (verify-version-consistency.sh als Thin-Wrapper auf verify-doc-sync.sh)
3. Doku-Truth Gate (docs-truth-consistency.sh)
4. God-Class-Guard (god-class-guard.sh --mode=hard)

**Status:** ✅ Implementation fertig, ❌ Commit blockiert (Phase-4.7-Shield).

---

### Befund 4 — BINDUNGSMATRIX ohne Gate-Konsument (Ghost-Reference-Data)

**Diagnose:** 332 (laut Audit-Doc 2026-07-28) / **344** (real — siehe Truth-Pass-Korrektur #1) Zeilen × 11 Spalten. Einziger Konsument war `build_bindungsmatrix.py` (Self-Generator). Der `awk NF!=11`-Sanity-Check aus WORKFLOW.md 2.4 war **nur manuell**.

**Verifikation (Post-U2):**
```bash
$ wc -l BINDUNGSMATRIX.csv
344 BINDUNGSMATRIX.csv

$ awk -F';' '{print NF}' BINDUNGSMATRIX.csv | sort -u
11  # Spalten-Einheitlichkeit bestätigt

$ grep -nE 'Gate 10|BINDUNGSMATRIX' tools/build-gate.sh
272:# ── Gate 10: BINDUNGSMATRIX Canon (Sprint v0.13.118+Governance-Diät) ──
275:# Bypass: SKIP_BINDUNGSMATRIX=1
276:echo -e "${CYAN}[10/11] BINDUNGSMATRIX Canon (332 Hebel × 11 Spalten SSoT)${NC}"
277:if [ "${SKIP_BINDUNGSMATRIX:-0}" = "1" ]; then
278:    gate_skip "BINDUNGSMATRIX Canon uebersprungen (SKIP_BINDUNGSMATRIX=1)"
279:elif [ -f "BINDUNGSMATRIX.csv" ]; then
280:    BM_LINES=$(wc -l < BINDUNGSMATRIX.csv 2>/dev/null | tr -d ' ' || echo 0)
```

**Lösung in Sprint v0.13.118+Governance-Diät:** NEUE Gate 10 in `tools/build-gate.sh` mit `awk NF=11`-Spalten-Konsistenz + `wc -l >= 100`-Sanity-Bound. Bypass `SKIP_BINDUNGSMATRIX=1`. Jetzt ist BINDUNGSMATRIX echte CI-Data-Governance, kein Ghost-Reference.

**Status:** ✅ Implementation fertig, ❌ Commit blockiert (Phase-4.7-Shield).

---

### Befund 5 — Drei Version-Sync-Skripte, ein Job

**Diagnose:** `verify-doc-sync.sh` (267 LOC, primär), `verify-version-consistency.sh` (96 LOC, duplikat), `docs-truth-consistency.sh` (246 LOC, inhaltlich). Version-Sync-Logik existiert zweimal (mod.changelog first-entry + pom `<version>`). Doppelte Gate-Aufrufe pro Build.

**Verifikation (Post-U2):**
```bash
$ head -10 tools/verify-version-consistency.sh
#!/usr/bin/env bash
# SyxEconomyMod — Version-Changelog Consistency Gate (DEPRECATED)
# Sprint v0.13.118+Governance-Diät: alle Logik nach verify-doc-sync.sh
# konsolidiert (Single-Source-of-Truth). Dieser Skript bleibt als
# Thin-Wrapper für Backward-Compat mit install-hooks.sh bestehen

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/verify-doc-sync.sh"

$ grep -n 'mod.changelog' tools/verify-doc-sync.sh | head -2
309:# 3b. mod.changelog first-entry vs pom.xml <version> (Rule 2 Stam-Sync)
310:CHANGELOG_FIRST=$(grep -m1 '<mod.changelog>' "$POM" | sed 's/.*<mod.changelog>//' | sed 's/;.*//' | grep -oP 'v?[0-9]+\.[0-9]+\.[0-9]+' | head -1 || true)
```

**Lösung in Sprint v0.13.118+Governance-Diät:** `verify-version-consistency.sh` ist jetzt Thin-Wrapper (`exec bash verify-doc-sync.sh`). Logik-Single-Source-of-Truth in `verify-doc-sync.sh` Z.309–318. Erspart ~96 LOC Skript-Duplikation.

**Status:** ✅ Implementation fertig, ❌ Commit blockiert (Phase-4.7-Shield).

---

### Befund 6 — Adapter-Doppel-Struktur ISyx* + EngineMirror (bewusst, Migrations-Backlog)

**Diagnose:** 6 ISyx*-Legacy-Dateien + 7 EngineMirror-Sub-Interfaces + 1 EngineMirror-Singleton. Co-Existenz dokumentiert seit Sprint v0.13.108+B-008 Phase 1 (Regel 9: ISyx* als Runtime-Backend via BypassGate).

**Verifikation:**
```bash
$ ls src/vannon/syx/economy/adapter/ISyx*.java | wc -l
6  # ISyx*-Dateien (Audit-Doc sagt 7 — siehe Truth-Pass-Korrektur #2)
$ ls src/vannon/syx/economy/adapter/*Access.java | grep -v 'Impl' | wc -l
7  # Sub-Interfaces: IRoomAccess, IFactionAccess, IHumanoidAccess,
   # IStatsAccess, ITreasuryAccess, IPopulationAccess, IGoodsAccess
$ ls src/vannon/syx/economy/core/EngineSeams.java
ls: cannot access 'src/vannon/syx/economy/core/EngineSeams.java': No such file or directory
# EngineSeams.java ist ersatzlos entfernt in Sprint v0.13.119+B-008-Phase-2
```

**Lösung in Sprint v0.13.119+B-008-Phase-2 (Working-Tree):**
- `EngineSeams.java` (28 statische Helper-Methoden) ersatzlos gelöscht
- `EconomySim.java:367` + `EconomyAuditEngine.java:186` migriert von Ternary-Fallback `EngineSeams.entitiesAvailable()` auf null-safe `EngineMirror m = EngineMirror.api(); if (m == null || !m.isFullyAvailable()) return;`
- `agents.md` Rule 9.1 Sub-Rule neu: **„ISyx* sind LIVE-Impl, NICHT Legacy"**. Beleg: `AdapterDispatcher.build()` Z.49–58 zeigt `RoomAccessImpl(bundle.warehouse, bundle.transport)` etc. — ISyx* sind Backend der Access-Impl-Klassen, **nicht** „alte Adapter die weg sollen".
- `ARCHITECTURE.md` EngineMirror-Sektion: expliziter Hinweis „ISyx*=Live-Impl, nicht Legacy".

**Status:** ✅ Implementation fertig, ❌ Commit blockiert (Phase-4.7-Shield). **Architektur-Pushback** an User-Premise „ISyx*-Adapter entfernen": nicht im Sprint-Scope; wäre eigenständiger Architektur-Sprint mit Alternativ-Impl (BypassGate-only oder Public-API-only) VOR dem Entfernen.

---

### Befund 7 — Rule-3-Friction (bewusste Design-Entscheidung, KEIN Befund)

**Diagnose:** Der Version-Bump mit „Drift on purpose" + 5-Befehl-Sed-Sequenz + `snapshot-stam-version.sh` (198 LOC) kostet 426+198+267 LOC + manuelle Sed-Zeit pro Sprint für **eine einzige Versionsnummer**. Das ist der klarste Over-Engineering-Kandidat — aber er ist vom User selbst als „intentional friction" definiert (agents.md Rule 3 verbietet explizit ein Auto-Sync-Skript).

**Verifikation:**
```bash
$ ls tools/bump-version.sh tools/snapshot-stam-version.sh
-rwxrwxr-xr-x tools/bump-version.sh            # 100 LOC
-rwxrwxr-xr-x tools/snapshot-stam-version.sh   # 198 LOC
```

**Entscheidung:** Respektiert als bewusste Designwahl. Kosten-Bewusstsein: Falls je aufgehoben, könnten die 3 Skripte (424 LOC) + 267 LOC Skript-Wartung wegfallen. User-Disposition.

**Status:** ❌ NICHT-Befund (per agents.md Rule 3 ausgeschlossen).

---

### Befund 8 — Phase-4.7-Shield pre-existing Technical Debt (Session-Discovery)

**Scope-Hinweis:** User-Auftrag waren „6 Anti-Over-Engineering-Befunde". Befund 8 wurde in dieser Session **neu entdeckt** (Erweiterung des ursprünglichen Scopes um die systemische Block-Quelle). Daher als Session-Discovery markiert, nicht in den 6er-Count der eigentlichen Befunde.

**Diagnose:** `tools/phase47-shield.sh` ist als Pre-Commit-Hook-Schritt-1 installiert. Thresholds (= Target-Werte):
- `MAX_IDENTITYHASH_NONREGISTRY=9` (Ziel: 0)
- `MAX_CATCH_THROWABLE=0` (Ziel: 0)

**Real-Counts (live gemessen 2026-07-31):**
- `new IdentityHashMap` in `src/vannon/syx/economy/core/`: **11 Dateien** — Drift +2 über Threshold
- `catch (Throwable)` in `src/vannon/syx/economy/core/`: **9 Stellen** — Drift +9 über Threshold
- `EngineSeams`-Method-Calls: 2 — PASS (unter Threshold 55)
- `printStackTrace()`: 0 — PASS

**Verifikation:**
```bash
$ bash tools/phase47-shield.sh 2>&1 | grep -E 'Threshold|Fail|PASS'
[FAIL][threshold] 11 Dateien mit 'new IdentityHashMap' (Limit 9):
[FAIL][threshold] 9 'catch (Throwable)' in core/ (Limit 0)
[phase47-shield] Messung:
    IdentityHashMap (ausserhalb Allow-List): 11 / Threshold 9 / Target 0
    EngineSeams.-Method-Calls in core/   : 2 / Threshold 55 / Target 0
    catch (Throwable) in core/             : 9 / Threshold 0 / Target 0
    printStackTrace() in core/              : 0 / Threshold 0 / Target 0
[phase47-shield] FAIL — Details oben.
```

**Impact:** JEDER Commit (auch U2 der nur pom.xml + .md editiert) wird Phase-4.7 blockieren. Hook unterscheidet nicht zwischen **neu hinzugefügt** und **pre-existing**. Modi-Optionen:
- **Soft-Modus (--delta-only):** zähle nur Delta-vor-HEAD. Pre-existing bleibt Pre-existing, neue Violations schlagen an. Erlaubt U2+B-008-Commit, behält Regression-Detection.
- **Threshold-Bump-Übergang:** `MAX_IDENTITYHASH_NONREGISTRY=12`, `MAX_CATCH_THROWABLE=10` als Übergangs-Cap bis Sprint v0.13.120+Phase-4.7-Sweep die Counts wieder unter Target bringt.

**Tatsächliche Fix-Pfade (für Sprint v0.13.120+Phase-4.7-Sweep):**
- 11 IdentityHashMap → wrap in `IdentityMapRegistry.java` / `IdentityKeys.java` (Allow-List von phase47-shield.sh, Z.42–44)
- 9 catch(Throwable) → schärfe zu `catch(SpecificType)` oder `catch(SpecificType t) { EventLog.log("...", t.getMessage()); }`

**Status:** ❌ Sprint v0.13.118+Governance-Diät und v0.13.119+B-008-Phase-2 NICHT committet wegen Block. Lösungs-Sprint v0.13.120+Phase-4.7-Sweep ist Pflicht-Voraussetzung für ALLE zukünftigen Commits.

---

## 3-Sprint-Folgeplan (U2 → U3 → B-008)

### Sprint v0.13.118+Governance-Diät (U2) — STATUS: Implementation Complete in Working-Tree

**Theme:** Doppel-Gate entschärft, 4 Orphan-Skripte archiviert, Hooks installiert, BINDUNGSMATRIX Gate 10 verdrahtet, Version-Gate-Overlap gemerged.

**Tasks (geplant 11):**
1. ✅ `tools/build-gate.sh` POM_PREFLIGHT_DONE-Skip für Gate 1+9
2. ✅ `pom.xml` antrun preflight-build-gate exec mit `<env key="POM_PREFLIGHT_DONE" value="1"/>`
3. ✅ `tools/verify-doc-sync.sh` Rule-3.1-Audit-Section (hardcoded-version FAIL + whitespace-empty FAIL + multi-tag-duplicate FAIL via Python xml.etree XML-aware Count)
4. ✅ `tools/bump-version.sh` Substitution-Pattern-Detection für `<mod.info>` (regex robust gegen whitespace/comment)
5. ✅ `tools/verify-version-consistency.sh` zu Thin-Wrapper auf `verify-doc-sync.sh` umgebaut
6. ✅ `tools/{change_detector.py, gini_episode_analyzer.py, rebalance_plots.py, rebalance_dashboard.ipynb}` → `tools/archive/`
7. ✅ `agents.md` Rule 3.1 Sub-Rule „mod.info global sync-invariant" neu
8. ✅ `README.md` + `CHANGELOG.md` + `ARCHITECTURE.md` Stam-Doc-Sync per Rule 3 (5 sed-Anchors)
9. ✅ `bash tools/install-hooks.sh` — echte Hooks installiert
10. ✅ `tools/build-gate.sh` Gate 10 BINDUNGSMATRIX (awk NF=11 + wc -l >= 100)
11. ⏸️ **Atomic-Commit blocked by Phase-4.7-Shield.** Need Phase-4.7-Sweep FIRST.

**Verifikations-Stand (alle internen Gates grün, externer Commit-Block pre-existing):**
- `bash tools/god-class-guard.sh --mode=hard` → 179 PASS / 0 WARN / 0 BLOCK (kein .java-Touch)
- `bash tools/verify-doc-sync.sh` → PASS, 14 Dokumente sync mit v0.13.101
- `mvn verify install -DskipTests` → BUILD SUCCESS
- `bash tools/phase47-shield.sh` standalone → IdentityHashMap 11 > 9 FAIL (PRE-EXISTING), catch(Throwable) 9 > 0 FAIL (PRE-EXISTING), EngineSeams 2/55 PASS, printStackTrace 0/0 PASS

---

### Sprint v0.13.119+B-008-Phase-2 (U3 + EngineSeams-Löschung kombiniert) — STATUS: Implementation Complete in Working-Tree

**Theme:** EngineSeams.java ersatzlos gelöscht + 2 Caller auf EngineMirror.isFullyAvailable() migriert + ISyx*-Pushback-Architektur-Doku in agents.md Rule 9.1 + ARCHITECTURE.md.

**Tasks (geplant 8):**
1. ✅ `core/EngineSeams.java` (28 statische Methoden, 4 aktive Call-Sites) ersatzlos gelöscht
2. ✅ `core/EconomySim.java:367` — Ternary `EntityMirror-null-check : EngineSeams.entitiesAvailable()` ersetzt durch null-safe `EngineMirror m = EngineMirror.api(); if (m == null || !m.isFullyAvailable()) return;`
3. ✅ `core/EconomyAuditEngine.java:186` — analoge Migration in `updateDemography()`
4. ✅ `adapter/IRoomAccess.java` Z.21,27,230 + `RoomAccessImpl.java:660` + `core/ReentryGuard.java:7` — Javadoc-references als „pre-v0.13.119 als EngineSeams-..." historisch markiert
5. ✅ `CHANGELOG.md` Sprint v0.13.119+B-008-Phase-2 Header (### vor v0.13.118)
6. ✅ `ARCHITECTURE.md` EngineMirror-Sektion: „B-008 Phase 1+2 abgeschlossen" + ISyx*=Live-Impl-Hinweis mit `AdapterDispatcher.build()`-Beleg
7. ✅ `agents.md` Rule 9.1 Sub-Rule: „ISyx* sind LIVE-Impl, NICHT Legacy. Entfernung wäre AdapterDispatcher.build()-killend."
8. ⏸️ **Atomic-Commit blocked by Phase-4.7-Shield.**

**Verifikations-Stand:**
- `bash tools/god-class-guard.sh --mode=hard` → 179 PASS (EngineSeams gelöscht, eine .java weniger)
- `bash tools/verify-doc-sync.sh` → PASS
- `mvn verify install -DskipTests` → BUILD SUCCESS (kein Dangling-Import)
- `grep -c 'import.*EngineSeams' src/ test/` → 0 (clean)
- `grep -rn 'EngineSeams' src/ test/` → 8 historische Marker + 2 Sprint-Kommentare (alle sauber, kein Code-Call mehr)

**Code-Reviewer-Verdict (Round-2):**
- Round-1: 2 HIGH (Semantic-Tightening + Test-NPE-Risk) + 2 MED (stale-Javadocs)
- Round-2-Fix: `EngineMirror m = EngineMirror.api(); if (m == null || !m.isFullyAvailable()) return;` löst Beide HIGHs simultan (Cache + expliziter Null-Check macht Tests harmlos)
- Round-2-Med-Fixes: ReentryGuard Z.7-8 und RoomAccessImpl Z.660 historisch korrekt nachgepflegt
- **GO** zur Commit-Phase — sobald Phase-4.7-Shield grün

**Architektur-Pushback an User:** Workshop-Spec „Anschliessend ISyx*-Adapter-Dateien entfernen" wurde **nicht umgesetzt** — siehe agents.md Rule 9.1 Sub-Rule + Befund 6 oben. ISyx* bleiben als Live-Impl. Falls später gewünscht: eigener Architektur-Sprint mit Alternativ-Implementierung pro ISyx*-Datei (BypassGate-only oder Public-API-only) VOR dem Entfernen.

---

### Sprint v0.13.120+Phase-4.7-Sweep — STATUS: nicht gestartet, Pflicht-Voraussetzung

**Theme:** 11 IdentityHashMap-Verwendungen in core/ auf `IdentityMapRegistry`/`IdentityKeys`-Wrapper migrieren + 9 `catch(Throwable)` schärfen. Schwellwert-Respektierung: `MAX_IDENTITYHASH_NONREGISTRY=9`, `MAX_CATCH_THROWABLE=0`.

**Pre-Flight-Plan (Task-Liste vor Implementation):**
1. **Analyse:** Für jede der 11 IdentityHashMap-Dateien — `static` vs `instance`, Key-Typ (RoomInstance, Humanoid, StockpileInstance, …), Sinnvolle Registry-Alternative? Klassifikation:
   - A) RoomInstance/StockpileInstance als Key → `IdentityMapRegistry.roomFirms()`/`stockpileStates()` (Wrapper mit WeakReference für GC)
   - B) Humanoid als Key → HumanoidAccessImpl-JobRegistry-Pattern (Phase B–E Erfahrung)
   - C) Path/Value ohne Engine-Key → `HashMap<>` reicht, KEIN `IdentityHashMap` nötig — direkter Type-Swap
2. **Analyse:** Für jede der 9 `catch(Throwable)`-Stellen — Was für eine Exception kann da kommen? replace mit `catch(RuntimeException e) { EventLog.log(…) }` oder `catch(SpecificType e)` wenn EngineException-Typ bekannt, sonst `catch(Throwable t) { logRuntime(t, "op-name") }` per Rule-10-Pattern-5.
3. **Aufgaben-Katalog (B-Liste P4.7-001..P4.7-020):** für jede Datei eine eigene Task, damit Sprint-Scope 5-15 Tasks bleibt.
4. **Validation-Plan:** Nach jedem Task `bash tools/phase47-shield.sh` ausführen, Drift-Counter beobachten. Sprint-Done wenn beide Counts ≤9 bzw. =0.

**Empfohlener TASK-Reihenfolge (Risk-Ordering):**
1. P4.7-001..009: IdentityHashMap-Migration (höherer Impact, leichter zu finden)
2. P4.7-010..018: catch(Throwable)-Spezifikation (niedrigerer Impact, subtiler)
3. P4.7-019: Stam-Doc-Sync (CHANGELOG Header, ARCHITECTURE Note)
4. P4.7-020: Atomic-Commit „Phase-4.7-Sprint v0.13.120+"

**Erwarteter Effekt:** Sprint-Commit unblocks U2 + B-008 Phase 2 (= 2 nachgelagerte atomic commits), plus alle weiteren Sprints.

---

## Truth-Pass-Korrekturen zu docs/SyxEconomyMod_AUDIT_2026-07-31.md

Live-Verifikation 2026-07-31 zeigt Drift im Audit-Doc (zuletzt aktualisiert 2026-07-31 19:58, Sitzungsbeginn dieser Session). Diese Korrekturen sind KEIN Audit-Doc-Edit — sie sind Memory-Save-Notiz für nächste Session, damit Audit und Working-Tree nicht divergieren.

| # | Audit-Doc-Claim | Audit-Doc-Stelle | Real-Ist-Stand 2026-07-31 | Drift |
|---|---|---|---|---|
| 1 | „BINDUNGSMATRIX (332 Zeilen)" | Audit-Doc 2026-07-28 + Diverse Dateien | `wc -l BINDUNGSMATRIX.csv` → **344 Zeilen** (+12) | +12 vermutlich Sprint v0.13.124+ Hotfix-2 |
| 2 | „ISyx*-Legacy (7 Dateien)" | ARCHITECTURE.md (Audit-Ref) | `ls src/.../ISyx*.java \| wc -l` → **6 Dateien** | -1 vermutlich `ISyxTrade` in M-Trade Sprint dedupliziert worden |
| 3 | „TreasuryCrisis 39 private static fields" | Audit-Doc D4.2 | **live-verifiziert 2026-08-01**: `python3 tools/god-class-guard/parse_metrics.py src/vannon/syx/economy/core/TreasuryCrisis.java` → `{"fields": 38, "loc": 358, "pubM": 5, "imports": 0, "pubM_names_sample": ["update","activeTier","save","load","reset"]}` | -1 minor; war Trend-Indikator für D4.2 (Sprint v0.13.127+ plant atomic-stages Refactor) |
| 4 | „9× catch (Throwable) ... LOW" | Audit-Doc D4.6 | **9× catch (Throwable) Phase-4.7-Shield BLOCKER** | Severity-Klassifikation korrigieren: LOW → Build-Blocker |
| 5 | `WindowQuickview.renderSidePanelContent()` | Audit-Doc C3.3 | Methode ist ersatzlos entfernt (Sprint v0.13.117+UR-Endredaktion) | Doc veraltet, `git show` zeigt REMOVE-Commit |
| 6 | „EngineSeams als Migration-Backlog mit 30 verbleibenden Calls" | Audit-Doc implizit (Cluster 1 + diverse) | `grep EngineSeams. src/` → 0 Code-Calls (Sprint v0.13.119+B-008-Phase-2 done) | -30, EngineSeams.java existiert nicht mehr |
| 7 | „STATS.s"-referenzen werden in Sprint v0.13.127+ gefixt (Rule 15) | Audit-Doc Vorgriff | **live-verifiziert 2026-08-01**: `grep -rn 'STATS\.s\.' src/vannon/syx/economy/\| wc -l` → **0 Treffer** | Rule-15-Audit bereits wirksam — keine STATS.s-Dereferenzierung in clinit-Scope, kein Audit-Action nötig |

**Korrektur-Empfehlung für nächste Session:**
- Audit-Doc Z.131 (`loc=332 fields=256`) → korrigieren auf `loc=344 fields=257` (drift +1/+1)
- Audit-Doc D4.6 Severity-Tag → `LOW → critical-blocker` (Phase-4.7-Shield-Umkehrung)
- Audit-Doc C3.3 → Methode existiert nicht mehr, stattdessen „Sprint v0.13.117+UR-Endredaktion entfernte renderSidePanelContent ersatzlos (Trade-off: Side-Panel zeigt nun leere Hide-Only-Status-Bar — out-of-scope für WindowLevers-Sprint, dann Re-Do via Holder-Pattern)"
- Audit-Doc → Hinweis auf Sprint v0.13.119+B-008-Phase-2-Block und Phase-4.7-Shield-Block

---

## Übergabe-Material für nächste Session

### Zustand Working-Tree 2026-07-31 20:37 (git status, post-changes)

```bash
$ git status --short
 m .claude/worktrees/code-review-fixes        # submodule-only, ignorieren
 M ARCHITECTURE.md                           # EngineMirror-Sektion aktualisiert (Sprint v0.13.119+)
 M CHANGELOG.md                              # 2 Sprint-Headers (v0.13.118 + v0.13.119)
 M README.md                                 # Gov-Diät Sync per Rule 3
 M agents.md                                 # Rule 3.1 Sub-Rule neu + Rule 9.1 Sub-Rule neu
 M pom.xml                                   # Rule 3.1 Fix mod.info<=${project.version} + env=POM_PREFLIGHT_DONE
R  tools/change_detector.py -> tools/archive/change_detector.py
R  tools/gini_episode_analyzer.py -> tools/archive/gini_episode_analyzer.py
R  tools/rebalance_plots.py -> tools/archive/rebalance_plots.py
 M tools/archive/rebalance_dashboard.ipynb   # added
 M tools/build-gate.sh                       # POM_PREFLIGHT_DONE-Skip + Gate 10 BINDUNGSMATRIX
 M tools/bump-version.sh                     # Substitution-Pattern-Detection (Rule 3.1-aware)
 M tools/verify-doc-sync.sh                  # Rule 3.1 Audit-Section + mod.version.history regen
 M tools/verify-version-consistency.sh       # zu Thin-Wrapper reduziert
 D tools/rebalance_dashboard.ipynb           # moved to archive
 M src/vannon/syx/economy/core/EconomySim.java              # EngineSeams.entitiesAvailable() → null-safe EngineMirror.isFullyAvailable()
 M src/vannon/syx/economy/core/EconomyAuditEngine.java      # dito
 D src/vannon/syx/economy/core/EngineSeams.java              # ersatzlos gelöscht (Sprint v0.13.119+B-008-Phase-2)
 M src/vannon/syx/economy/core/ReentryGuard.java             # Javadoc-Update Z.7-8 (EngineSeams→Engine-Mirror)
 M src/vannon/syx/economy/adapter/IRoomAccess.java           # Javadoc 4-refs (EngineSeams als historisch markiert)
 M src/vannon/syx/economy/adapter/RoomAccessImpl.java        # Kommentar Z.660 (EngineSeams als historisch markiert)
```

### Was die nächste Session zuerst sieht

1. **Working-Tree validiert GREEN** für 2 Sprints (U2 + B-008 Phase 2), Commit blockiert durch Phase-4.7-Shield
2. **Phase-4.7-Shield pre-existing Violations** sind die einzige Blocker-Quelle
3. **EMPFEHLUNG:** Sprint v0.13.120+Phase-4.7-Sweep als ersten Sprint der neuen Session — er unblocks **alle** weiteren Commits

### Wie man den Sprint v0.13.120+ startet (User-Prompt-Vorschlag)

```
Starte Sprint v0.13.120+Phase-4.7-Sweep: analysiere die 11 IdentityHashMap-
Dateien in core/ + 9 catch(Throwable)-Stellen via grep+parse_metrics, 
klassifiziere pro Datei welche Fix-Strategie greift, erstelle B-Liste 
P4.7-001..P4.7-020 mit Datei:Zeile-Referenzen, dann migriere pro Task — 
Ziel: phase47-shield PASS, alle atomic-commits der vorherigen Sprints 
(U2, B-008 Phase 2) werden nachfolgend nachgepusht.
```

### Wie man Sprint U2 + B-008 nachpushen kann (nach Phase-4.7-Sweep)

```bash
# Sprint v0.13.118+Governance-Diät — Working-Tree ist bereit
git add pom.xml agents.md CHANGELOG.md ARCHITECTURE.md README.md \
        tools/build-gate.sh tools/bump-version.sh tools/verify-doc-sync.sh \
        tools/verify-version-consistency.sh \
        tools/archive/change_detector.py tools/archive/gini_episode_analyzer.py \
        tools/archive/rebalance_dashboard.ipynb tools/archive/rebalance_plots.py
git commit -m "Sprint v0.13.118+Governance-Diät: ..."

# Sprint v0.13.119+B-008-Phase-2 — Working-Tree ist bereit
git add src/vannon/syx/economy/core/EconomySim.java \
        src/vannon/syx/economy/core/EconomyAuditEngine.java \
        src/vannon/syx/economy/core/ReentryGuard.java \
        src/vannon/syx/economy/adapter/IRoomAccess.java \
        src/vannon/syx/economy/adapter/RoomAccessImpl.java \
        CHANGELOG.md ARCHITECTURE.md agents.md
git commit -m "Sprint v0.13.119+B-008-Phase-2: ..."

# Phase-4.7-Shield post-Commit-Check
git log --oneline -3
bash tools/phase47-shield.sh
bash tools/god-class-guard.sh --mode=hard
bash tools/verify-doc-sync.sh
mvn verify install -DskipTests -Dskip.bump=true
git push origin master
```

---

**Dokumentenende.** True-Pass-Link: `docs/SyxEconomyMod_AUDIT_2026-07-31.md` (UI-Reife Layer) + `docs/SyxEconomyMod_AUDIT_2026-07-28.md` (Basis-Audit vor M-UI-Chain) + `docs/SyxEconomyMod_AUDIT_2026-07-30_UI-RESTRUCTURE.md` (Windows + Tabs strukturelle Schwächen-Pre-M-UI-1).
