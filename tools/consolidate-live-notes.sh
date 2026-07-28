#!/usr/bin/env bash
# consolidate-live-notes.sh — Weekly auto-pass for the live-notes funnel.
#
# Scans docs/live-notes/ for unprocessed .md files, parses YAML frontmatter
# tags, generates a categorized consolidation report, and moves processed
# notes to docs/live-notes/processed/.
#
# Usage:
#   ./tools/consolidate-live-notes.sh          # full pass
#   ./tools/consolidate-live-notes.sh --dry-run  # report only, don't move files
#
# Exit codes: 0 = success, 1 = error

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LIVE_DIR="$REPO_ROOT/docs/live-notes"
PROCESSED_DIR="$LIVE_DIR/processed"
REJECT_LOG="$PROCESSED_DIR/rejected.log"
BACKLOG="$REPO_ROOT/docs/BACKLOG.md"
TODAY="$(date +%Y-%m-%d)"
REPORT="$LIVE_DIR/consolidation-$TODAY.md"

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
    DRY_RUN=true
    echo "=== DRY RUN — no files will be moved ==="
    echo ""
fi

# ── Guards ────────────────────────────────────────────────────────────

if [[ ! -d "$LIVE_DIR" ]]; then
    echo "[ERROR] docs/live-notes/ directory not found at $LIVE_DIR" >&2
    exit 1
fi

mkdir -p "$PROCESSED_DIR"

# ── Collect unprocessed notes ─────────────────────────────────────────

NOTES=()
while IFS= read -r -d '' file; do
    NOTES+=("$file")
done < <(find "$LIVE_DIR" -maxdepth 1 -name '*.md' \
    ! -name 'README.md' \
    ! -name '.template.md' \
    ! -name 'consolidation-*.md' \
    -print0 2>/dev/null || true)

if [[ ${#NOTES[@]} -eq 0 ]]; then
    echo "Nothing to consolidate. docs/live-notes/ has no unprocessed notes."
    exit 0
fi

echo "Found ${#NOTES[@]} unprocessed note(s)."
echo ""

# ── Categorization buckets ────────────────────────────────────────────

declare -a COVER_NOTES=()     # cover:plan-task-N
declare -a GAP_NOTES=()       # gap:net-new
declare -a REJECT_NOTES=()    # reject:scope-out
declare -a UX_NOTES=()        # ux:papercut
declare -a BALANCE_NOTES=()   # balance:drift
declare -a BUG_NOTES=()       # bug:silent
declare -a UNTAGGED=()        # no recognized tags
SKIPPED=0

# ── Parse each note ───────────────────────────────────────────────────

for note in "${NOTES[@]}"; do
    basename="$(basename "$note")"
    
    # Extract YAML frontmatter between --- delimiters
    frontmatter=$(sed -n '/^---$/,/^---$/p' "$note" 2>/dev/null || true)
    
    if [[ -z "$frontmatter" ]]; then
        echo "[WARN] $basename: no YAML frontmatter found — skipping" >&2
        ((SKIPPED++)) || true
        continue
    fi
    
    # Extract tags line
    tags_line=$(echo "$frontmatter" | grep -E '^tags:' | head -1 || true)
    
    if [[ -z "$tags_line" ]]; then
        echo "[WARN] $basename: no 'tags:' field in frontmatter — skipping" >&2
        ((SKIPPED++)) || true
        continue
    fi
    
    # Categorize by tag
    categorized=false
    if echo "$tags_line" | grep -q 'cover:plan-task'; then
        COVER_NOTES+=("$note")
        categorized=true
    fi
    if echo "$tags_line" | grep -q 'gap:net-new'; then
        GAP_NOTES+=("$note")
        categorized=true
    fi
    if echo "$tags_line" | grep -q 'reject:scope-out'; then
        REJECT_NOTES+=("$note")
        categorized=true
    fi
    if echo "$tags_line" | grep -q 'ux:papercut'; then
        UX_NOTES+=("$note")
        categorized=true
    fi
    if echo "$tags_line" | grep -q 'balance:drift'; then
        BALANCE_NOTES+=("$note")
        categorized=true
    fi
    if echo "$tags_line" | grep -q 'bug:silent'; then
        BUG_NOTES+=("$note")
        categorized=true
    fi
    
    if ! $categorized; then
        UNTAGGED+=("$note")
    fi
done

# ── Generate consolidation report ─────────────────────────────────────

exec 3>"$REPORT"

cat >&3 <<HEADER
# Live Notes Consolidation — $TODAY

> **Auto-generated** by \`tools/consolidate-live-notes.sh\`
> **Source:** ${#NOTES[@]} note(s) processed, $SKIPPED skipped

---

HEADER

# Helper function: extract note body (after frontmatter)
extract_body() {
    local file="$1"
    # Print everything after the second --- line
    awk 'BEGIN { count=0 } /^---$/ { count++; if (count==2) { next } } count>=2' "$file"
}

# Helper function: extract H1 title from note
extract_title() {
    local file="$1"
    grep -m1 '^# ' "$file" | sed 's/^# //' || echo "(untitled)"
}

# Helper function: extract tags line from a note file's frontmatter
extract_tags_line() {
    local file="$1"
    local fm
    fm=$(sed -n '/^---$/,/^---$/p' "$file" 2>/dev/null || true)
    echo "$fm" | grep -E '^tags:' | head -1 || true
}

# ── Section: BUG:silent (Critical) ────────────────────────────────────

if [[ ${#BUG_NOTES[@]} -gt 0 ]]; then
    cat >&3 <<SECTION
## 🚨 BUG: Silent Data Loss / Corruption (P0)

> **These must be addressed before any new feature work.**

SECTION
    for note in "${BUG_NOTES[@]}"; do
        title=$(extract_title "$note")
        basename="$(basename "$note")"
        cat >&3 <<ENTRY

### $title
- **Source:** \`$basename\`
- **Status:** ⬜ Unaddressed

$(extract_body "$note")

---
ENTRY
    done
fi

# ── Section: cover:plan-task-N ────────────────────────────────────────

if [[ ${#COVER_NOTES[@]} -gt 0 ]]; then
    cat >&3 <<SECTION
## 📋 Cover: Existing Plan Tasks

> **Integration:** Append each entry under the corresponding Task in the plan-doc.

SECTION
    for note in "${COVER_NOTES[@]}"; do
        title=$(extract_title "$note")
        basename="$(basename "$note")"
        # Extract which task(s) this covers — per-note, not last-loop global
        note_tags=$(extract_tags_line "$note")
        task_ids=$(echo "$note_tags" | grep -oP 'cover:plan-task-\K[0-9]+' | tr '\n' ',' | sed 's/,$//')
        cat >&3 <<ENTRY

### $title
- **Source:** \`$basename\`
- **Target:** Plan Task(s) $task_ids

$(extract_body "$note")

---
ENTRY
    done
fi

# ── Section: gap:net-new ──────────────────────────────────────────────

if [[ ${#GAP_NOTES[@]} -gt 0 ]]; then
    cat >&3 <<SECTION
## 🆕 Gap: Net-New Features / Gaps

> **Integration:** Append to \`docs/BACKLOG.md\` or create a new plan task.

SECTION
    for note in "${GAP_NOTES[@]}"; do
        title=$(extract_title "$note")
        basename="$(basename "$note")"
        cat >&3 <<ENTRY

### $title
- **Source:** \`$basename\`
- **Status:** Unprioritized
- **Severity:** (review and set)

$(extract_body "$note")

---
ENTRY
    done
fi

# ── Section: ux:papercut ──────────────────────────────────────────────

if [[ ${#UX_NOTES[@]} -gt 0 ]]; then
    cat >&3 <<SECTION
## 🖱 UX: Papercuts

> **Integration:** Append to \`docs/BACKLOG.md\` with severity:low.

SECTION
    for note in "${UX_NOTES[@]}"; do
        title=$(extract_title "$note")
        basename="$(basename "$note")"
        cat >&3 <<ENTRY

### $title
- **Source:** \`$basename\`

$(extract_body "$note")

---
ENTRY
    done
fi

# ── Section: balance:drift ────────────────────────────────────────────

if [[ ${#BALANCE_NOTES[@]} -gt 0 ]]; then
    cat >&3 <<SECTION
## ⚖ Balance: Drift Detected

> **Integration:** Append to \`docs/BACKLOG.md\` with balance tag for next tuning pass.

SECTION
    for note in "${BALANCE_NOTES[@]}"; do
        title=$(extract_title "$note")
        basename="$(basename "$note")"
        cat >&3 <<ENTRY

### $title
- **Source:** \`$basename\`

$(extract_body "$note")

---
ENTRY
    done
fi

# ── Section: reject:scope-out ─────────────────────────────────────────

if [[ ${#REJECT_NOTES[@]} -gt 0 ]]; then
    cat >&3 <<SECTION
## ❌ Reject: Scope-Out

> **Integration:** Appended to \`docs/live-notes/processed/rejected.log\` automatically.

SECTION
    for note in "${REJECT_NOTES[@]}"; do
        title=$(extract_title "$note")
        basename="$(basename "$note")"
        cat >&3 <<ENTRY

### $title
- **Source:** \`$basename\`
- **Disposition:** Explicitly out of scope

$(extract_body "$note")

---
ENTRY
        # Append to reject log
        echo "[$TODAY] $basename — $title" >> "$REJECT_LOG"
    done
fi

# ── Section: Uncategorized ────────────────────────────────────────────

if [[ ${#UNTAGGED[@]} -gt 0 ]]; then
    cat >&3 <<SECTION
## ❓ Uncategorized

> **No recognized tags. Review manually.**

SECTION
    for note in "${UNTAGGED[@]}"; do
        title=$(extract_title "$note")
        basename="$(basename "$note")"
        cat >&3 <<ENTRY

### $title
- **Source:** \`$basename\`

$(extract_body "$note")

---
ENTRY
    done
fi

exec 3>&-

# ── Move processed notes ──────────────────────────────────────────────

if $DRY_RUN; then
    echo ""
    echo "=== DRY RUN COMPLETE ==="
    echo "Report written to: $REPORT"
    echo "Would have moved ${#NOTES[@]} note(s) to processed/ (skipped — dry run)"
else
    for note in "${NOTES[@]}"; do
        basename="$(basename "$note")"
        mv "$note" "$PROCESSED_DIR/$basename"
    done
    echo ""
    echo "Moved ${#NOTES[@]} note(s) to docs/live-notes/processed/"
fi

# ── Summary ───────────────────────────────────────────────────────────

echo ""
echo "═══════════════════════════════════════════"
echo " Consolidation Report: $TODAY"
echo "═══════════════════════════════════════════"
echo "  🚨 bug:silent        : ${#BUG_NOTES[@]}"
echo "  📋 cover:plan-task   : ${#COVER_NOTES[@]}"
echo "  🆕 gap:net-new       : ${#GAP_NOTES[@]}"
echo "  🖱  ux:papercut       : ${#UX_NOTES[@]}"
echo "  ⚖  balance:drift     : ${#BALANCE_NOTES[@]}"
echo "  ❌ reject:scope-out   : ${#REJECT_NOTES[@]}"
echo "  ❓ uncategorized      : ${#UNTAGGED[@]}"
echo "  ⏭  skipped (no tags)  : $SKIPPED"
echo "═══════════════════════════════════════════"
echo ""
echo "Report: $REPORT"
if ! $DRY_RUN; then
    echo "Notes moved to: $PROCESSED_DIR"
fi
echo ""
echo "Next: review the report, then manually integrate entries into plan-doc and BACKLOG.md."
