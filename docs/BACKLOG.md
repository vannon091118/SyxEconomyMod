# Backlog (New-Findings Inbox)

> **Auto-managed Inbox.** Entries flow in from:
> - `tools/consolidate-live-notes.sh` — `gap:net-new` and `ux:papercut` tags
> - Manual backlog triage during plan reviews
>
> **Master-Task-Liste: [`ROADMAP.md`](../ROADMAP.md)** — alle T-/B-IDs werden
> dort konsolidiert (per agents.md Rule 13, ab v0.13.31).
> Diese Datei dient nur als **Inbox für neue Live-Findings**, die noch
> nicht im ROADMAP-GlobalIndex sind. Sobald ein Finding in die ROADMAP
> aufgenommen wird (T- oder B-ID vergeben), wird der Eintrag hier gelöscht.
>
> Format: `### [ID] Title` with Source, Severity, Status. Severity:
> 🔴 P0 (crash/data-loss) · 🟠 P1 (broken feature) · 🟡 P2 (papercut) · 🟢 P3 (nice-to-have).
>
> **Verschieb-Verbot aktiv** (agents.md Rule 13): Keine "Verschoben" / "Postponed" / "Deferred" / "Next-Sprint"-Markierungen. Tasks sind entweder hier (neu, nicht im Sprint-Plan) oder in der ROADMAP (Planned/Active/Closed/Rejected).

---

## Sprint-Snapshot

Letzter abgeschlossener Sprint: **Sprint 3 — Roadmap-SSOT-Konsolidierung + P1-Blocker-Closure** (7 Tasks T14.0–T14.6).

Backlog-Master-Liste: [`ROADMAP.md § Planned Backlog`](../ROADMAP.md#planned-backlog-p1p2-blocker-ready-for-sprint-4+).
T-IDs (Sprint-Tasks) und B-IDs (Live-Findings) sind dort zentral verwaltet. Diese Datei hier dient nur als Inbox für noch-nicht-erfasste Findings.

---

## New-Findings Inbox (leer)

Keine neuen Findings seit dem letzten Sprint. Bekannte B-Items sind in der ROADMAP § Planned Backlog:
[B-001](../ROADMAP.md#planned-backlog-p1p2-blocker-ready-for-sprint-4+),
[B-002](../ROADMAP.md#planned-backlog-p1p2-blocker-ready-for-sprint-4+),
[B-004](../ROADMAP.md#planned-backlog-p1p2-blocker-ready-for-sprint-4+),
[B-005](../ROADMAP.md#planned-backlog-p1p2-blocker-ready-for-sprint-4+),
[B-006](../ROADMAP.md#planned-backlog-p1p2-blocker-ready-for-sprint-4+),
[B-008](../ROADMAP.md#planned-backlog-p1p2-blocker-ready-for-sprint-4+),
[B-009](../ROADMAP.md#planned-backlog-p1p2-blocker-ready-for-sprint-4+),
[B-010](../ROADMAP.md#planned-backlog-p1p2-blocker-ready-for-sprint-4+),
[B-011](../ROADMAP.md#planned-backlog-p1p2-blocker-ready-for-sprint-4+).

Wenn ein neuer Live-Fund während des Spiels auftritt (z.B. via
`consolidate-live-notes.sh` mit `gap:net-new` oder `ux:papercut` Tag),
wird er hier erfasst und beim nächsten Sprint in die ROADMAP migriert.

---

## Historische Einträge (vor v0.13.31, archiviert)

Vor v0.13.31 enthielt diese Datei eine Master-Task-Liste mit B-001..B-011.
Diese wurden in den Sprint 2 / Sprint 3 migriert und sind jetzt in der
ROADMAP als Planned-Backlog konsolidiert (siehe oben). Die alte Form
dieser Datei wird durch die aktuelle New-Findings-Inbox ersetzt.