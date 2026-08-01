# docs-live-notes-funnel — Format Specification

> **Version:** 1.0  
> **Created:** 2026-07-24  
> **Status:** Active  

## Purpose

The live-notes funnel bridges the gap between unstructured playtest brain-dumps
and structured plan-documentation. Without it, insights captured during live
testing are lost to chat history, screenshots, and mental notes that evaporate
within 48 hours.

## Architecture

```
docs/live-notes/
├── README.md              ← workflow documentation
├── .template.md           ← copy-paste template for new notes
├── YYYY-MM-DD-title.md    ← raw notes (one per observation)
├── processed/             ← notes after consolidation
│   ├── YYYY-MM-DD-title.md
│   └── rejected.log       ← accumulated reject:scope-out entries
└── consolidation-YYYY-MM-DD.md  ← funnel output (temporary, reviewed then deleted)
```

## Note Format

Every live note is a Markdown file with YAML frontmatter.

### Required frontmatter fields

| Field    | Type   | Description |
|----------|--------|-------------|
| `date`   | `YYYY-MM-DD` | Date of the observation |
| `session`| string | Brief playtest context (version, year, population) |
| `tags`   | `string[]` | At least one tag from the tag taxonomy below |

### Tag taxonomy

| Tag | Priority | Consolidation target | Example |
|-----|----------|---------------------|---------|
| `cover:plan-task-N` | High | Appended to plan-doc Task N | `cover:plan-task-10` |
| `gap:net-new` | Medium | Appended to BACKLOG.md | `gap:net-new` |
| `reject:scope-out` | Low | Logged to rejected.log | `reject:scope-out` |
| `ux:papercut` | Medium | Backlog with UX severity marker | `ux:papercut` |
| `balance:drift` | Medium | Backlog with balance tag | `balance:drift` |
| `bug:silent` | Critical | Plan-doc P0 blocker entry | `bug:silent` |

### Body sections (recommended, not enforced)

- `# Live Note: [title]` — H1, punchy
- `## Context` — in-game state at observation time
- `## Observation` — raw, uncurated observation
- `## Impact` — concrete impact statement
- `## Suggested Action` — concrete next step or "Needs investigation"

## Consolidation Script

`tools/consolidate-live-notes.sh` implements the weekly auto-pass.

### Behavior

1. **Scan:** Find all `.md` files in `docs/live-notes/` (exclude `README.md`, `.template.md`, `processed/`)
2. **Parse:** Extract YAML frontmatter, validate `tags` field
3. **Categorize:** Group by tag type
4. **Generate report:** Write `consolidation-YYYY-MM-DD.md` with sections per category
5. **Move:** Transfer processed notes to `docs/live-notes/processed/`
6. **Summarize:** Print counts per category to stdout

### Error handling

- Notes without `tags` field → skipped with warning to stderr
- Notes with unknown tags → grouped under "Uncategorized" in report
- Empty `docs/live-notes/` (no unprocessed notes) → exit 0, print "Nothing to consolidate"

### Exit codes

| Code | Meaning |
|------|---------|
| 0 | Success (with or without notes processed) |
| 1 | Script error (missing directory, parse failure) |

## Integration Points

### With plan-doc

The consolidation report for `cover:plan-task-N` entries includes the exact
Markdown block to append under the task's section. Format:

```markdown
#### Live Note (2026-07-24): [Note title]

[Note body — Observation + Impact + Suggested Action, trimmed to essentials]
```

### With BACKLOG.md

`gap:net-new` entries are appended to `docs/BACKLOG.md` (created if missing) with:

```markdown
### [Note title]
- **Source:** Live note YYYY-MM-DD
- **Severity:** (derived from impact statement)
- **Status:** Unprioritized
- [Note body]
```

### With CI

The `tools/phase47-shield.sh` CI-skript should be extended to warn when
`docs/live-notes/` has >10 unprocessed notes — a signal that the funnel
has stalled.

## Non-Goals

- The funnel does NOT automatically edit plan-docs. Integration is manual by design.
- The funnel does NOT validate note content quality. Garbage-in, garbage-out.
- The funnel does NOT enforce the 4-section body format. It only parses frontmatter.
