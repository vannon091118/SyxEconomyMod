#!/usr/bin/env bash
# tools/post-commit-session-heandoff.sh — Auto-Memory-Save-Hook
# ==============================================================
# Sprint U-MEM-AUTO: Erzeugt nach jedem Commit automatisch
#   docs/{YYYY-MM-DD}_SESSION_HANDOVER.md
# mit Working-Tree-Status, Modified-Files, Gates-Snapshot, B/T-Item-Updates
# und Verweis auf ggf. existierende thematische Memory-Saves.
#
# Trigger: post-commit-hook (auto-installed via tools/install-hooks.sh).
# Output: docs/{YYYY-MM-DD}_SESSION_HANDOVER.md (OVERWRITE-on-continue).
# Idempotent: jeder re-run regeneriert vollständig. Kein State außerhalb der Datei.
# Non-blocking: Schreibt Errors nach stderr, beendet mit exit=0 damit der
#               gerade abgeschlossene Commit nicht gefährdet wird.
#
# Filename-Konvention: docs/{YYYY-MM-DD}_SESSION_HANDOVER.md
# Bei mehreren Sessions am gleichen Tag: letzte Session gewinnt.
# Thematische Memory-Saves (z.B. docs/GOVERNANCE_DIAT_2026-08-01.md) bleiben separat.

set -uo pipefail

# ANSI
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || { echo -e "${RED}[handover] cd-Root failed${NC}" >&2; exit 0; }

# Nur laufen, wenn wir in einem Git-Repo sind
git rev-parse --git-dir >/dev/null 2>&1 || { echo -e "${YELLOW}[handover] not in a git repo, skipping${NC}" >&2; exit 0; }

# Timestamp heute (UTC-lokal)
TODAY="$(date +%Y-%m-%d)"
SHORT_SHA="$(git rev-parse --short HEAD 2>/dev/null || echo 'no-commit')"
COMMIT_MSG="$(git log -1 --pretty=format:'%s' 2>/dev/null || echo 'no-message')"

OUT="docs/${TODAY}_SESSION_HANDOVER.md"
mkdir -p docs

# ---- 1. Working-Tree-Status ----
WT_STATUS="$(git status --short 2>/dev/null || echo '(unavailable)')"

# ---- 2. Today's Modified Files ----
TODAY_FILES="$(git log --since='midnight' --name-only --pretty=format: 2>/dev/null \
  | sort -u | grep -v '^$' | head -200)"
if [[ -z "$TODAY_FILES" ]]; then
    TODAY_FILES="(no commits today — initial repo or fresh start)"
fi

# ---- 3. PASS/FAIL-Gates Snapshot ----
GATE_BLOCK=""
GATE_STATUS=""

# Phase47-Shield baseline
PHASE47_BASELINE=".git/hooks/.phase47-baseline"
if [[ -f "$PHASE47_BASELINE" ]]; then
    GATE_STATUS+=$'### Phase-4.7-Shield (baseline)\n'
    GATE_STATUS+="$(cat "$PHASE47_BASELINE" 2>/dev/null)\n\n"
else
    GATE_STATUS+=$'### Phase-4.7-Shield\n_no baseline yet — first commit will create one_\n\n'
fi

# God-Class-Guard — dry-run (optional via timeout)
GATE_STATUS+=$'### God-Class-Guard\n'
if [[ -x tools/god-class-guard.sh ]]; then
    GOD_OUT="$(bash tools/god-class-guard.sh --mode=hard 2>&1 | tail -3 || echo '(non-blocking) error')"
    GATE_STATUS+="${GOD_OUT}\n\n"
else
    GATE_STATUS+="_tool not found_\n\n"
fi

# Verify-Doc-Sync — quick summary
GATE_STATUS+=$'### Verify-Doc-Sync\n'
if [[ -x tools/verify-doc-sync.sh ]]; then
    DS_OUT="$(bash tools/verify-doc-sync.sh 2>&1 | tail -5 | tr -d '\033[0-3[0-9]m' || echo '(non-blocking) error')"
    GATE_STATUS+="${DS_OUT}\n\n"
else
    GATE_STATUS+="_tool not found_\n\n"
fi

# Verify-Audit-Claims - quick summary
GATE_STATUS+=$'### Verify-Audit-Claims (Gate 11)\n'
if [[ -x tools/verify-audit-claims.sh ]]; then
    VAC_OUT="$(bash tools/verify-audit-claims.sh 2>&1 | tail -3 | tr -d '\033[0-3[0-9]m' || echo '(non-blocking) error')"
    GATE_STATUS+="${VAC_OUT}\n\n"
else
    GATE_STATUS+="_tool not found_\n\n"
fi

# ---- 4. B/T-Items Updated Today ----
# Strategie: parse ROADMAP.md nach {B-/T-}NNN-IDs, dann grep nach heutigen Commit-SHAs.
BT_ITEMS=""
TODAY_SHAS="$(git log --since='midnight' --pretty=format:'%h' 2>/dev/null || true)"
if [[ -n "$TODAY_SHAS" ]] && [[ -f ROADMAP.md ]]; then
    # Suche B-/T-Items die explizit in heutigen Commits referenziert sind
    for sha in $TODAY_SHAS; do
        # Suche im gesamten Repo nach ID-Mentions im commit-message der sha
        COMMIT_TXT="$(git log -1 --pretty=format:'%B' "$sha" 2>/dev/null || true)"
        MATCHES="$(echo "$COMMIT_TXT" | grep -oE '\b(B|T)-[0-9]+' | sort -u || true)"
        for id in $MATCHES; do
            # Suche ROADMAP.md-Zeile mit dieser ID
            LINE="$(grep -E "\\b$id\\b" ROADMAP.md 2>/dev/null | head -1 | sed 's/^/  /')"
            if [[ -n "$LINE" ]]; then
                BT_ITEMS+="- ${id}: ${LINE}\n"
            fi
        done
    done
fi
if [[ -z "$BT_ITEMS" ]]; then
    BT_ITEMS="_(no explicit B-/T-ID references in today's commits — Status via git log /file-inspection)_"
fi

# ---- 5. Thematic Memory-Save Reference ----
# Suche docs/*-{YYYY-MM-DD}.md (außer SESSION_HANDOVER selbst)
THEMATIC_REFS=""
if [[ -d docs ]]; then
    THEMATIC_REFS="$(find docs -maxdepth 1 -type f -name "*${TODAY}.md" \
        ! -name "*_SESSION_HANDOVER.md" 2>/dev/null \
        | sed 's|^|  - |' || true)"
fi
if [[ -z "$THEMATIC_REFS" ]]; then
    THEMATIC_REFS="_(no thematic memory-save for ${TODAY} — overridable, agent-discretion-based)_"
fi

# ---- Compose Output ----
{
cat <<EOF
# Session Handover: ${TODAY}

> **Stand:** ${TODAY} | **Commit:** \`${SHORT_SHA}\` — ${COMMIT_MSG}
> **Auto-generated by:** \`tools/post-commit-session-handover.sh\` (non-blocking)
> **Manuelle Memory-Saves:** nur wenn Substanz über dieses Hook-Output hinausgeht.

## 1. Working-Tree-Status

\`\`\`text
${WT_STATUS}
\`\`\`

## 2. Session Modified Files (Today)

\`\`\`text
${TODAY_FILES}
\`\`\`

## 3. PASS/FAIL-Gates Snapshot

${GATE_STATUS}

## 4. B/T-Items Updated Today

${BT_ITEMS}

## 5. Thematic Memory-Save Reference

Für kompakte Übergabe, falls Substantielle Insights über dieses Hook-Output hinausgehen:

${THEMATIC_REFS}

---

*Generated by tools/post-commit-session-handover.sh · Auto-Memory-Save-Hook (non-blocking) · überschreibbar*
EOF
} > "$OUT" 2>/dev/null

echo -e "${CYAN}[handover] wrote ${OUT}${NC}" >&2

exit 0
