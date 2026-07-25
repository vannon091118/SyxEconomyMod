# SyxEconomyMod — LLM Sprint-Workflow

> **Obligatorisches Sprint-Pattern für jede AI-Agent-Session.**
> Abgeleitet aus Phase-A–F (2026-07-25, 11 unabhängige Gaps, 2 Reviewer),
> TreasuryCrisis-Reset-Sprint (T1–T4) und Mod-Economy T5–T13-Sprint
> (11 Tasks). Verhindert micro-task-commits die Sprint-Inhalt
> un-rekonstruierbar machen.
>
> Letzte Aktualisierung: 2026-07-25 | Sprint: Sprint-Workflow-Etablierung

---

## Sprint-Konzept

Ein **Sprint** = thematischer Cluster von 5–15 Tasks, geteiltes
Architektur-Ziel, endet mit **genau einem atomaren Commit**.

```
┌───────────────────────────────────────────────────────────────┐
│                    SPRINT (5-15 Tasks)                         │
│                                                                │
│   ┌─BAUEN──┐   ┌─PRÜFEN─┐   ┌─HÄRTEN──┐                       │
│   │ T1..Tn │ → │ gates  │ → │ review  │ → 1 atomic commit    │
│   └────────┘   └────────┘   └─────────┘                       │
└───────────────────────────────────────────────────────────────┘
```

Innerhalb des Sprints laufen die 3 Sub-Phasen **einmal am Sprint-Ende**,
nicht pro Task:

| Sub-Phase | Wann | Was |
|---|---|---|
| **BAUEN** | alle Tasks | Implement + Stam-Doc-Updates (per Rule 2+3) |
| **PRÜFEN** | sprint-end | `mvn verify install` + `verify-doc-sync.sh` + grep-scans |
| **HÄRTEN** | sprint-end | code-reviewer-minimax-m3 + Gap-Closure |

**Sprint-Naming:** Theme-basiert (z.B. "TreasuryCrisis Reset",
"BINDUNGSMATRIX-Canonical", "Phase-A–F SDK"). Sprint-Header in CHANGELOG.md
nennt den Namen + subsummierten Tasks.

**Sprint-Bound vs. Session-Bound:** 1 Sprint pro AI-Session. Wenn nicht
in einer Session schaffbar → Sprint zu groß, splitten. Mehrere Sprints
pro Session OK wenn jedes für sich validiert + committed vor nächstem.

---

## Sub-Phase 1: BAUEN — Konstruktion

### Regeln

| # | Regel | Begründung |
|---|---|---|
| 1.1 | **5–15 Tasks pro Sprint.** Theme-bound, nicht zeit-bound. | Kleinere Sprints haben zu wenig Impact, größere splittet Logik. |
| 1.2 | **Stam-Docs einmal im Sprint-Commit** (Rule 3 friction). | 1 sed-Block pro Sprint statt per-Task. |
| 1.3 | **Innerhalb BAUEN: KEIN Commit, KEIN Push.** | Sprint-Commit ist der einzige Commit-Punkt im Sprint. |
| 1.4 | **Tasks dürfen einander brechen** (z.B. Refactor vor Build). | Sprint-Commit endet mit konsistentem Stand. |
| 1.5 | **BINDUNGSMATRIX.csv als kanonische Reference-Data** (nicht Stam-Doc). | Single-source-of-truth für Hebel-Verifikation. tools/-Skripte lesen csv, nicht HEBELKARTE. |

### Checkliste vor Sub-Phase-Sub-Phase-Übergang (nicht atomar, nur am Sprint-Ende!)

- [ ] Alle Tasks fertig (Code + Tests wo angemessen)
- [ ] ARCHITECTURE.md / GLOSSARY.md / ROADMAP.md / CHANGELOG.md / README.md auf aktuellem Stand
- [ ] BINDUNGSMATRIX.csv neu gebaut (via `python3 tools/build_bindungsmatrix.py`) falls Daten sich geändert haben

---

## Sub-Phase 2: PRÜFEN — Sprint-End Gate

### Regeln

| # | Regel | Begründung |
|---|---|---|
| 2.1 | **Sync-Gate explizit:** `bash tools/verify-doc-sync.sh`. | Der Gate fängt nicht alle Muster (z.B. CHANGELOG-Header). |
| 2.2 | **Stale-Referenz-Scan:** `grep -rn 'Fallback\|*MH\|useMethodHandleAdapters\|HEBELKARTE' src/ test/ tools/`. | Nach tools/-Migration sollten keine HEBELKARTE-Refs mehr existieren. |
| 2.3 | **Phantom-Dokumentation löschen.** ARCHITECTURE/GLOSSARY/ROADMAP-Behauptungen müssen mit `find src/ -name '*.java'` verifizierbar sein. | agents.md Rule 5. |
| 2.4 | **BINDUNGSMATRIX.csv-Sanity:** NF-Check, HEBEL-Coverage, Marker-Distribution. | `awk -F';' 'NF!=11' BINDUNGSMATRIX.csv` muss leer sein. |
| 2.5 | **Sprint-Commit-Atomicity:** Sprint-Commit enthält alle Tasks in EINEM Commit. | Verboten: per-Task-Commits zerhacken das Sprint-Thema. |

### Checkliste vor Sprint-Commit

- [ ] `mvn verify install -DskipTests -Dskip.bump=true` = BUILD SUCCESS
- [ ] `bash tools/verify-doc-sync.sh` = PASS (5 Stam-Docs)
- [ ] `awk -F';' 'NF!=11' BINDUNGSMATRIX.csv` = leer
- [ ] Keine `HEBELKARTE` Referenzen mehr in `tools/` (außer SUPERSEDED-Notice falls noch da)
- [ ] Keine Phantom-Klassen in GLOSSARY.md
- [ ] CHANGELOG.md: Sprint-Header mit allen Tasks

---

## Sub-Phase 3: HÄRTEN — Review & Gap-Closure

### Regeln

| # | Regel | Begründung |
|---|---|---|
| 3.1 | **code-reviewer-minimax-m3 einmal pro Sprint-End.** | Sprint-übergreifende Patterns nur am Sprint-Ende sichtbar. |
| 3.2 | **Alle Reviewer-Lücken vor Sprint-Commit schließen.** Kein "später". | Sprint-Commit ist die letzte Chance. |
| 3.3 | **Kein silent-fail** in irgendeinem Code-Pfad. | FieldAccessor-Beispiel: stiller Bilanz-Fehler. |
| 3.4 | **Fehlerverhalten konsistent** zwischen Geschwisterklassen. | FieldAccessor vs MethodAccessor. |

### Checkliste vor Sprint-Commit

- [ ] code-reviewer-minimax-m3 ausgeführt, alle Anmerkungen adressiert
- [ ] Kein `return 0`/`null`/`false` ohne Diagnose in neuem Code
- [ ] Sprint-Commit enthält alle gelösten Lücken
- [ ] Kein offener Work-in-Progress-Stand am Sprint-Ende

---

## Commit-Disziplin (Sprint-übergreifend)

**Der Sprint-Commit ist der EINZIGE Commit-Punkt im Sprint-Workflow.**

```
EIN Sprint-Commit:
  ├── Enthält alle 5-15 Tasks des Sprints
  ├── Enthält Stam-Doc-Updates (1× per Sprint per agents.md Rule 3)
  ├── Hat `mvn verify install -DskipTests -Dskip.bump=true` = BUILD SUCCESS
  ├── Hat `bash tools/verify-doc-sync.sh` = PASS
  ├── Hat `code-reviewer-minimax-m3` Review + alle Lücken geschlossen
  └── Commit-Message-Schema: `sprint: <Name> — <Tasks subsummiert>`
      oder atomare Commits bei klar trennbaren Sprint-Phasen

NIE (innerhalb eines Sprints):
  ├── Per-Task-Commits (verboten — Sprint-Inhalt wird un-rekonstruierbar)
  ├── „Push später" nach abgeschlossenem Review
  ├── Commit ohne grünen Build
  ├── Review abschließen ohne Sprint-Commit
  └── Zwischen-Sprint-Commits stapeln
```

---

## Session-Handoff-Kriterien

Eine AI-Session ist bereit zum Handoff, wenn:

1. ✅ Alle Sprints der Session committed (1 Sprint = 1 Commit-Regel)
2. ✅ `mvn verify install -DskipTests -Dskip.bump=true` = BUILD SUCCESS
3. ✅ `bash tools/verify-doc-sync.sh` = PASS
4. ✅ `git status` = clean (keine uncommitteten Änderungen)
5. ✅ Keine offenen Review-Lücken
6. ✅ agents.md auf aktuellem Stand (neue Regeln dokumentiert)
7. ✅ BINDUNGSMATRIX.csv neu gebaut + sanity-checks PASS
8. ✅ tools/-Scripts konsistent (alte Skripte gelöscht, kanonisch aktiv)
9. ✅ HEBELKARTE.md entweder gelöscht oder explizit als deprecated markiert

---

## Anti-Patterns — Sprint-spezifisch

| Anti-Pattern | Symptom | Folge | Fix |
|---|---|---|---|
| **Task-per-Commit** | Sprint mit 11 Tasks hat 11 Commits (`T5: ...`, `T6: ...`) | Sprint-Inhalt nur im Chat rekonstruierbar, git log unleserlich | 1 Sprint = 1 atomic commit |
| **Sprint-Bruch** | Concept-Shift mid-sprint (z.B. von "TreasuryCrisis" zu "HEBELKARTE-Loeschung") | Zwei thematisch unterschiedliche Commits, Sprint-Thema unklar | Sprint beenden mit Commit, dann neuen Sprint starten |
| **Stale-tools-Refs** | v2/v3 Skripte in tools/ obwohl build_bindungsmatrix.py kanonisch | Drift-Risiko, Dokumentations-Wirrwarr | tools/-Cleanup als eigener Sprint-Block |
| **Sprint-Overflow** | Sprint >15 Tasks weil "thematisch verwandt" | Reviewer übersieht Lücken in der Menge | Tasks zählen, Sprint splitten |
| **Ungeplanter Compile-Push** | Per-Task `mvn install` ohne Sprint-End-Validation | Mehrere potentielle Drift-Punkte, sync-gate feuert unkontrolliert | Sprint-Erlaubnis für compile nur via Sub-Phase-2-Gate |
| **HEBELKARTE-Lurking** | HEBELKARTE.md bleibt + tools/ lesen es + SUPERSEDED-Notice | Zwei Wahrheits-Quellen, Drift possible | Sprint "HEBELKARTE-Loeschung" → tools/-Migration → file-delete |
| **Sprint-Definition-Drift** | agents.md spricht von "Tasks", WORKFLOW.md von "Sub-Phasen", Code von "Phases" | Drei Terminologien, Reviewer verwirrt | Sprint-Vokabular canonical: Sprint > Task > Sub-Phase |

---

> **Pflege-Hinweis:** WORKFLOW.md ist der generalisierte Sprint-Workflow. Sprint-Spezifika
> (welche Tasks, welche Architektur-Entscheidungen) stehen in CHANGELOG.md und der
> Sprint-Commit-Message. Bei Sprint-Anomalien: WORKFLOW.md updaten, agents.md syncen.
