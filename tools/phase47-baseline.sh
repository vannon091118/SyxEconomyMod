#!/usr/bin/env bash
# tools/phase47-baseline.sh — Baseline-Manager für phase47-shield --mode=delta-only.
#
# Sprint-Start: `bash tools/phase47-baseline.sh capture`  setzt Baseline = HEAD.
# Mid-Sprint:   `bash tools/phase47-baseline.sh refresh` aktualisiert Baseline-Counts
#                                       (z. B. nach bewusster Refactor-Verbesserung).
#               `bash tools/phase47-baseline.sh show`     prints aktuellen Baseline-State.
#               `bash tools/phase47-baseline.sh drop`     löscht Baseline-File (nur
#                                                            für Notfälle / Tests).
#
# Format (kompatibel mit post-commit-shield.sh — fügt BASELINE_SHA hinzu):
#   BASELINE_SHA=<git rev-parse HEAD>
#   IDENTITYHASH=<count>
#   ENGINESEAMS=<count>
#   CATCH_THROWABLE=<count>
#   PRINTSTACKTRACE=<count>
#
# Speicherort: .git/hooks/.phase47-baseline (DELEGIERT an post-commit-shield.sh-
#              Schema, sodass ein einziges Baseline-File zwei Modi bedient).
#
# Sub-Rule 15.1 Mode-Selection siehe agents.md.

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BASELINE=".git/hooks/.phase47-baseline"
CORE_DIR="src/vannon/syx/economy/core"
ALLOW_REGISTRY="IdentityMapRegistry.java"
ALLOW_KEYS="IdentityKeys.java"

count_identityhash() {
    { grep -rln 'new IdentityHashMap' "$CORE_DIR" 2>/dev/null \
        | grep -vE "$ALLOW_REGISTRY|$ALLOW_KEYS" || true; } | wc -l
}
count_engineseams() {
    { grep -rEn '\bEngineSeams\.[a-zA-Z][a-zA-Z0-9_]*\(' "$CORE_DIR" 2>/dev/null || true; } | wc -l
}
count_throwable() {
    { grep -rEn 'catch \(Throwable' "$CORE_DIR" 2>/dev/null || true; } | wc -l
}
count_printstacktrace() {
    { grep -rEn 'printStackTrace\(\)' "$CORE_DIR" 2>/dev/null | grep -v '//' || true; } | wc -l
}

write_baseline() {
    local sha="$1"
    mkdir -p "$(dirname "$BASELINE")"
    cat > "$BASELINE" <<EOF
BASELINE_SHA=$sha
IDENTITYHASH=$(count_identityhash)
ENGINESEAMS=$(count_engineseams)
CATCH_THROWABLE=$(count_throwable)
PRINTSTACKTRACE=$(count_printstacktrace)
EOF
}

cmd=${1:-show}

case "$cmd" in
    capture)
        if ! command -v git >/dev/null 2>&1; then
            echo -e "${RED}[FAIL] 'git' nicht im PATH — Baseline-SHA braucht git rev-parse.${NC}" >&2
            exit 2
        fi
        SHA="$(git rev-parse HEAD)"
        write_baseline "$SHA"
        echo -e "${GREEN}[phase47-baseline] capture: SHA=$SHA$(printf '%.0s' ''){1}✓${NC}"
        echo ""
        echo -e "${GREEN}Aktuelle Baseline:${NC}"
        bash "$0" show
        ;;
    refresh)
        if [[ ! -f "$BASELINE" ]]; then
            echo -e "${YELLOW}[phase47-baseline] refresh: keine Baseline vorhanden — starte capture.${NC}"
            bash "$0" capture
            exit 0
        fi
        # Source und SHA beibehalten, Counts neu messen.
        # shellcheck source=/dev/null
        source "$BASELINE"
        write_baseline "${BASELINE_SHA:-unknown}"
        echo -e "${GREEN}[phase47-baseline] refresh: Counts neu gemessen, SHA=$BASELINE_SHA erhalten.${NC}"
        echo ""
        echo -e "${GREEN}Aktualisierte Baseline:${NC}"
        bash "$0" show
        ;;
    show)
        if [[ ! -f "$BASELINE" ]]; then
            echo -e "${YELLOW}[phase47-baseline] show: keine Baseline vorhanden — sprint hat noch nicht begonnen.${NC}"
            echo "  Sprint-Start: bash tools/phase47-baseline.sh capture"
            exit 0
        fi
        echo -e "${GREEN}[phase47-baseline] aktuelle Baseline (file: $BASELINE):${NC}"
        cat "$BASELINE"
        ;;
    drop)
        if [[ -f "$BASELINE" ]]; then
            rm "$BASELINE"
            echo -e "${YELLOW}[phase47-baseline] drop: Baseline gelöscht.${NC}"
            echo -e "${YELLOW}  ACHTUNG: post-commit-shield.sh wird beim nächsten Commit eine NEUE Baseline anlegen.${NC}"
            echo -e "${YELLOW}  phase47-shield --mode=delta-only ohne Baseline → fallback auf absolute-Modus.${NC}"
        else
            echo -e "${YELLOW}[phase47-baseline] drop: keine Baseline vorhanden.${NC}"
        fi
        ;;
    -h|--help)
        cat <<'EOF'
Aufruf: bash tools/phase47-baseline.sh <command>
Commands:
  capture   Snapshot der aktuellen Metrics bei git rev-parse HEAD anlegen
  refresh   Counts neu messen, SHA beibehalten (z. B. nach Refactor-Erfolg)
  show      Aktuelle Baseline anzeigen
  drop      Baseline-File löschen (Notfall)
EOF
        ;;
    *)
        echo -e "${RED}[FAIL] unbekanntes Argument: $1${NC}" >&2
        bash "$0" --help
        exit 2
        ;;
esac
