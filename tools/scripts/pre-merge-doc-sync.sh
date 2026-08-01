#!/usr/bin/env bash
# SyxEconomyMod — Pre-Merge Doc-Sync Check
# =========================================
# Sprint v0.13.129+ Sprint-Folge: Verhindert Workspace-Drift Pattern wie
# Sprint v0.13.108-128 (~30 Commits auf Feature-Branch ohne pom.xml-Bump,
# Doku wurde nicht aktualisiert, Aerger-Suche in Sprint-Logs).
#
# Aufruf: VOR jedem Git-Merge in main ausfuehren:
#   bash tools/scripts/pre-merge-doc-sync.sh              # Real-Run
#   bash tools/scripts/pre-merge-doc-sync.sh --dry-run    # Show-Only
#   bash tools/scripts/pre-merge-doc-sync.sh --force      # WARN skipped
#
# Exit-Codes:
#   0 = PASS          (alle Sync-Checks gruen, ready-to-merge)
#   1 = FAIL          (Drift in Stam-Doku, MERGE BLOCKIERT)
#   2 = ERROR         (Skript-Umgebungsfehler: kein git, kein verify-doc-sync.sh)
#   3 = WARN          (Workspace-Drift erkannt: Branch hat commits ohne pom-Bump;
#                      Aktion noetig vor Merge: bump-version.sh aufrufen)
#
# Integrations-Pattern (siehe tools/install-hooks.sh):
#   - Aufruf als manueller Pre-Merge-Hook: bash tools/scripts/pre-merge-doc-sync.sh
#   - Als CI-Step vor `mvn release:prepare`:
#       stage('Pre-Merge-Doc-Sync') { sh 'bash tools/scripts/pre-merge-doc-sync.sh' }
#   - In `git merge --no-ff feature/X`-Pre-Step Workflow einbinden

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

DRY_RUN=0; FORCE=0
BASE_BRANCH="main"
TARGET_BRANCH="main"
POM="pom.xml"
VERIFY_DOC_SYNC="tools/verify-doc-sync.sh"

# Parse Args
for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=1 ;;
        --force) FORCE=1 ;;
        --base=*) BASE_BRANCH="${arg#*=}" ;;
        --target=*) TARGET_BRANCH="${arg#*=}" ;;
        -h|--help)
            echo "Usage: $0 [--dry-run] [--force] [--base=BRANCH] [--target=BRANCH]"
            echo "  --dry-run    Show what would be checked, don't fail"
            echo "  --force      Skip WARN (workspace-drift) gate, only FAIL blocks"
            exit 0
            ;;
    esac
done

# ── Pre-Flight ────────────────────────────────────────────────────────
echo -e "${CYAN}==============================================================${NC}"
echo -e "${CYAN}  SyxEconomyMod — Pre-Merge Doc-Sync Check                     ${NC}"
echo -e "${CYAN}==============================================================${NC}"
echo ""
[ "$DRY_RUN" -eq 1 ] && echo -e "  ${YELLOW}DRY-RUN${NC} — nur Analyse, keine Blocker-Effekte" && echo
[ "$FORCE" -eq 1 ] && echo -e "  ${YELLOW}FORCE${NC} — WARN-Gate uebersprungen (FAIL blockt weiterhin)" && echo

if ! command -v git >/dev/null 2>&1; then
    echo -e "${RED}FEHLER: git nicht verfuegbar${NC}" >&2; exit 2
fi
if ! git rev-parse --git-dir >/dev/null 2>&1; then
    echo -e "${RED}FEHLER: nicht in einem Git-Repository${NC}" >&2; exit 2
fi
if [ ! -f "$VERIFY_DOC_SYNC" ]; then
    echo -e "${RED}FEHLER: $VERIFY_DOC_SYNC nicht gefunden — Stam-Doku-Verifikation nicht moeglich${NC}" >&2; exit 2
fi

CURRENT_BRANCH=$(git branch --show-current 2>/dev/null || echo "DETACHED")
echo -e "  ${CYAN}INFO${NC}  current-branch=$CURRENT_BRANCH  base=$BASE_BRANCH  target=$TARGET_BRANCH  pom=$POM"
echo ""

FAILED=0; WARNINGS=0; CHECKED=0; STATUS="PASS"

# ── 1. pom.xml Version Detection (Branch-Tip vs Base-Tip) ──────────
echo -e "${CYAN}>>> Check 1: pom.xml Version Drift (Branch-Tip vs $BASE_BRANCH-Tip)${NC}"
POM_VERSION_TIP=$(git show HEAD:$POM 2>/dev/null | grep -m1 '<version>' | sed 's/.*<version>\([^<]*\)<\/version>.*/\1/' || echo "")
POM_VERSION_BASE=$(git show $BASE_BRANCH:$POM 2>/dev/null | grep -m1 '<version>' | sed 's/.*<version>\([^<]*\)<\/version>.*/\1/' || echo "")
echo -e "  ${CYAN}INFO${NC}  HEAD pom.xml version:    '$POM_VERSION_TIP'"
echo -e "  ${CYAN}INFO${NC}  $BASE_BRANCH pom.xml version: '$POM_VERSION_BASE'"

if [ -z "$POM_VERSION_TIP" ] || [ -z "$POM_VERSION_BASE" ]; then
    echo -e "  ${RED}FAIL${NC}  pom.xml Version nicht extrahierbar"
    FAILED=1
elif [ "$POM_VERSION_TIP" = "$POM_VERSION_BASE" ]; then
    echo -e "  ${GREEN}OK${NC}    pom.xml Version identisch — kein Bump in der Branch"
    CHECKED=$((CHECKED + 1))
else
    echo -e "  ${GREEN}OK${NC}    pom.xml Version Bump erkannt: $POM_VERSION_BASE → $POM_VERSION_TIP"
    CHECKED=$((CHECKED + 1))
fi

# ── 2. Commit-Diff Detection (Branch commits vs Base) ───────────────
echo -e "${CYAN}>>> Check 2: Commit-Diff seit $BASE_BRANCH (Workspace-Drift Detection)${NC}"
# robuster Count: git rev-list --count liefert single integer (kein Pipe-Output-Kollisions-Bug)
# und ist immun gegen set -o pipefail weil es kein Pipe ist
COMMITS_SINCE_BASE=$(git rev-list --count "$BASE_BRANCH"..HEAD 2>/dev/null | head -1 | tr -d '[:space:]' || true)
COMMITS_SINCE_BASE="${COMMITS_SINCE_BASE:-0}"
if ! printf '%s' "$COMMITS_SINCE_BASE" | grep -qE '^[0-9]+$'; then
    echo -e "  ${RED}FAIL${NC}  Commit-Count nicht parsbar ('$COMMITS_SINCE_BASE') — git-Branch ungueltig?"
    FAILED=1
    COMMITS_SINCE_BASE=0
fi
if [ "$COMMITS_SINCE_BASE" -eq 0 ]; then
    echo -e "  ${GREEN}OK${NC}    keine neuen Commits seit $BASE_BRANCH — keine Merge noetig"
    CHECKED=$((CHECKED + 1))
    echo ""
    echo -e "${CYAN}==============================================================${NC}"
    [ "$DRY_RUN" -eq 0 ] && echo -e "${GREEN}  PASS${NC}  — keine Commits zum Mergen, Sync nicht relevant (Exit 0)"
    exit 0
fi
echo -e "  ${CYAN}INFO${NC}  $COMMITS_SINCE_BASE Commits ahead-of-$BASE_BRANCH"
LATEST_3=$(git log --oneline -3 $BASE_BRANCH..HEAD 2>/dev/null | head -3 | sed 's/^/      /')
[ -n "$LATEST_3" ] && printf '%s\n' "$LATEST_3"

# ── 3. Workspace-Drift Detection (commits > 0 AND pom.xml UNCHANGED) ──
if [ "$COMMITS_SINCE_BASE" -gt 0 ] && [ "$POM_VERSION_TIP" = "$POM_VERSION_BASE" ]; then
    echo -e "  ${YELLOW}WARN${NC}  ⚠ WORKSPACE-DRIFT ERKANNT: $COMMITS_SINCE_BASE Commits auf der Branch,"
    echo -e "                  aber pom.xml version UNCHANGED ('$POM_VERSION_TIP')."
    echo -e "                  Pattern analog Sprint v0.13.108-128 (~30 Commits, kein Bump)."
    echo -e "                  Aktion: tools/bump-version.sh ausfuehren, Doku/* aktualisieren,"
    echo -e "                  dann erneut pre-merge-doc-sync.sh aufrufen."
    WARNINGS=$((WARNINGS + 1))
    STATUS="WARN"
elif [ "$COMMITS_SINCE_BASE" -gt 0 ]; then
    echo -e "  ${GREEN}OK${NC}    $COMMITS_SINCE_BASE Commits + pom.xml Bump erkannt — keine Workspace-Drift"
    CHECKED=$((CHECKED + 1))
fi

# ── 4. Stam-Doku Vs pom.xml Konsistenz-Check ────────────────────────────
echo -e "${CYAN}>>> Check 3: Stam-Doku (Doku/*.md) ist konsistent mit pom.xml${NC}"
# 4a. CHANGELOG.md: erstes "## vX.Y.Z" Heading sollte POM_VERSION_TIP matchen
if [ -n "$POM_VERSION_TIP" ] && [ -f "Doku/CHANGELOG.md" ]; then
    CHANGELOG_HEAD=$(git show HEAD:Doku/CHANGELOG.md 2>/dev/null | grep -m1 '^##[[:space:]]\+v[0-9]' | head -1 || echo "")
    CHANGELOG_VER=$(printf '%s' "$CHANGELOG_HEAD" | grep -oE 'v?[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "")
    if [ -z "$CHANGELOG_VER" ]; then
        echo -e "  ${RED}FAIL${NC}  Doku/CHANGELOG.md: kein '## vX.Y.Z' Heading gefunden"
        FAILED=1
    elif [ "v$POM_VERSION_TIP" != "$CHANGELOG_VER" ] && [ "$POM_VERSION_TIP" != "${CHANGELOG_VER#v}" ]; then
        echo -e "  ${RED}FAIL${NC}  Doku/CHANGELOG.md Heading '$CHANGELOG_VER' != pom.xml '$POM_VERSION_TIP' (Stam-Doku-Drift)"
        FAILED=1
    else
        echo -e "  ${GREEN}OK${NC}    Doku/CHANGELOG.md: '$CHANGELOG_HEAD'"
        CHECKED=$((CHECKED + 1))
    fi
fi
# # 4b. Andere Stam-Doku-Files: '> **Version:** vX.Y.Z' oder '**Version:** vX.Y.Z'
# Auto-Discovery statt hartkodierter Liste: alle Doku/*.md ausser CHANGELOG.md + BACKLOG.md
# (CHANGELOG.md wird separat in 4a behandelt, BACKLOG.md hat keinen Version-Marker)
STAM_DOCS=$(find Doku/ -maxdepth 1 -type f -name '*.md' 2>/dev/null \
    | grep -vE 'Doku/(CHANGELOG|BACKLOG)\.md$' | sort || true)
for doc in $STAM_DOCS; do
    [ -f "$doc" ] || continue
    DOC_VER=$(git show HEAD:"$doc" 2>/dev/null | grep -m1 -E '(\*\*Version:\*\*|> \*\*Version:\*\*)[[:space:]]+v?[0-9]+\.[0-9]+\.[0-9]+' | grep -oE 'v?[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "")
    if [ -z "$DOC_VER" ]; then
        echo -e "  ${YELLOW}WARN${NC}  $doc: keine '**Version:**'-Zeile gefunden (kein Pflicht-Drift, aber ungeprueft)"
        WARNINGS=$((WARNINGS + 1))
    elif [ "v$POM_VERSION_TIP" != "$DOC_VER" ] && [ "$POM_VERSION_TIP" != "${DOC_VER#v}" ]; then
        echo -e "  ${RED}FAIL${NC}  $doc: Version '$DOC_VER' != pom.xml '$POM_VERSION_TIP'"
        FAILED=1
    else
        echo -e "  ${GREEN}OK${NC}    $doc: Version '$DOC_VER'"
    fi
done

# ── 5. verify-doc-sync.sh als Final-Thoroughness ──────────────────────
echo -e "${CYAN}>>> Check 4: bash $VERIFY_DOC_SYNC (Final-Thoroughness)${NC}"
if [ "$DRY_RUN" -eq 1 ]; then
    echo -e "  ${CYAN}INFO${NC}  --dry-run ueberspringt verify-doc-sync.sh"
elif [ -f "$VERIFY_DOC_SYNC" ]; then
    if bash "$VERIFY_DOC_SYNC" > /tmp/pre-merge-vds.log 2>&1; then
        echo -e "  ${GREEN}OK${NC}    verify-doc-sync.sh PASS — alle Stam-Doku + Tools konsistent"
        CHECKED=$((CHECKED + 1))
    else
        VDS_EXIT=$?
        grep -E 'FAIL|DRIFT' /tmp/pre-merge-vds.log | head -5 | sed 's/^/      /'
        echo -e "  ${RED}FAIL${NC}  verify-doc-sync.sh FAIL (Exit-Code $VDS_EXIT) — siehe /tmp/pre-merge-vds.log"
        FAILED=1
    fi
fi

# ── 6. Final Decision ───────────────────────────────────────────────────
echo ""
echo -e "${CYAN}==============================================================${NC}"

if [ "$FAILED" -eq 1 ]; then
    STATUS="FAIL"
fi

case "$STATUS" in
    PASS)
        echo -e "${GREEN}  PASS${NC}  — $CHECKED Pre-Merge-Checks ok, ready-to-merge '$CURRENT_BRANCH' → '$TARGET_BRANCH'"
        echo -e "             $COMMITS_SINCE_BASE Commits, pom=$POM_VERSION_TIP"
        exit 0
        ;;
    WARN)
        if [ "$FORCE" -eq 1 ]; then
            echo -e "${YELLOW}  WARN-IGNORED${NC} — Workspace-Drift erkannt, --force ueberspringt: $COMMITS_SINCE_BASE Commits ohne pom-Bump"
            echo -e "             Trotzdem wird der Merge ohne weitere Pruefung fortgesetzt."
            exit 0
        fi
        echo -e "${YELLOW}  WARN${NC}  — Workspace-Drift erkannt: $COMMITS_SINCE_BASE Commits ohne pom.xml-Bump"
        echo -e "             Aktion: bash tools/bump-version.sh  (CHANGELOG + ROADMAP + Stam-Doku anpassen)"
        echo -e "             Dann: bash tools/scripts/pre-merge-doc-sync.sh erneut aufrufen"
        echo -e "             Override mit --force ist moeglich, aber nicht empfohlen."
        exit 3
        ;;
    FAIL)
        echo -e "${RED}  FAIL${NC}  — Pre-Merge blockiert: Stam-Doku-Drift zwischen Branch und pom.xml"
        echo -e "             Reproduction: bash tools/scripts/pre-merge-doc-sync.sh"
        echo -e "             Fix: bash $VERIFY_DOC_SYNC  (detaillierte Drift-Befunde)"
        # Dry-Run Override: bei --dry-run keine Blocker-Effekte; zeige was passieren wuerde
        if [ "$DRY_RUN" -eq 1 ]; then
            echo -e "${YELLOW}             (DRY-RUN: real-run wuerde mit Exit-Code 1 abbrechen)${NC}"
            exit 0
        fi
        exit 1
        ;;
    *)
        echo -e "${RED}  INTERNAL ERROR${NC}  — unhandled STATUS='$STATUS'. Bug im Script." >&2
        exit 2
        ;;
esac
