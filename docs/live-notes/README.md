# Live Notes Funnel

> **Capture everything. Lose nothing. Consolidate weekly.**

This directory holds raw, timestamped notes written during live playtests. Each note
is a brain-dump — unbalanced, uncurated, but full of insight that would otherwise
evaporate. Once a week (or after each significant playtest session), the funnel
consolidates them into the plan-doc and backlog.

## Workflow

```
Playtest → Write raw note → Tag it → Weekly funnel → Plan-doc / Backlog / Reject-log
```

### 1. Write a note

Copy `.template.md`, rename to `YYYY-MM-DD-brief-title.md`, dump your observation.
Don't overthink — write like you talk. The tags do the structure work.

### 2. Tag it

Every note MUST have at least one tag in its frontmatter:

| Tag | Meaning | Consolidation target |
|-----|---------|---------------------|
| `cover:plan-task-N` | Maps to existing plan task N | Appended as "Live Note" under Task N in the plan-doc |
| `gap:net-new` | New gap/feature not in any plan | Appended to `BACKLOG.md` |
| `reject:scope-out` | Acknowledged but explicitly out of scope | Logged to `processed/rejected.log` |
| `ux:papercut` | UI/UX annoyance, not a crash-bug | Backlog with low-severity marker |
| `balance:drift` | Numbers feel wrong in live play | Backlog with balance-tag for next tuning pass |
| `bug:silent` | Silent corruption or data loss | Escalated — goes to plan-doc as P0 blocker |

Multiple tags allowed. Example frontmatter:

```yaml
---
date: 2026-07-24
session: Playtest v0.3.2, 200-pop settlement, Year 5
tags: [cover:plan-task-10, ux:papercut]
---
```

### 3. Run the funnel

```bash
./tools/consolidate-live-notes.sh
```

This:
- Scans `docs/live-notes/` for unprocessed `.md` files
- Parses tags from frontmatter
- Generates `docs/live-notes/consolidation-YYYY-MM-DD.md` — a categorized report
- Moves processed notes to `docs/live-notes/processed/`
- Prints a summary to stdout

### 4. Integrate

Read the consolidation report. Manually apply:
- `cover:plan-task-N` entries → append to the plan-doc's task section
- `gap:net-new` entries → file in `docs/BACKLOG.md` or create a new plan task
- `reject:scope-out` entries → review the rejection log periodically for pattern detection

The integration step is MANUAL by design — the funnel surfaces, you decide.

## Anti-Patterns

- **Don't curate during the playtest.** Write raw, tag later (or tag rough).
- **Don't skip tagging.** An untagged note is invisible to the funnel and will be ignored.
- **Don't let notes pile up past a week.** The funnel loses value when there are 30+ unprocessed notes.
- **Don't edit notes after consolidation.** They're timestamped artifacts. If the insight evolved, write a NEW note referencing the old one.

## Example Note

See `.template.md` for the blank template. Here's a real example:

```markdown
---
date: 2026-07-24
session: Playtest v0.3.2, Year 5, 200 pop
tags: [cover:plan-task-10, ux:papercut]
---

# OddjobWage Slider hat 213 tote Positionen

Slider geht 0-250, aber Cap ist 37. Spieler zieht auf 200, nächster
Frame schnappt er auf 37 zurück. Sieht aus wie ein Bug, ist aber
korrektes Clamping. UX-Lüge.

## Suggested Action
Slider-Max in EconomyWindow auf `(int)(defaultWage * oddjobWageCeilingRatio)`
begrenzen, damit die Skala ehrlich ist.
```
