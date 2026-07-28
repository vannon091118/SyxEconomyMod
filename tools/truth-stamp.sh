#!/usr/bin/env bash
# SyxEconomyMod — Truth-Stamp Tool
# ==================================
# Haelt docs/reports/TRUTH_REPORT.md als lebendige Audit-Quelle aktuell.
# Bei jedem Commit wird die `## Stand:`-Zeile jeder Sektion, deren
# `<!-- truth-tracks: ... -->`-Registry mindestens eine geaenderte Datei
# aus docs/ enthaelt, auf aktuellen Zeitstempel + Commit-Hash gesetzt.
#
# Verwendung:
#   bash tools/truth-stamp.sh           # stamp-Mode (Post-Commit-Hook)
#   bash tools/truth-stamp.sh --init    # init-Mode (einmaliges Retrofit)
#
# Hook-Installation:
#   cp tools/truth-stamp.sh .git/hooks/post-commit
#   chmod +x .git/hooks/post-commit
# Oder via tools/install-hooks.sh (automatisierte Variante).
#
# Sektion-Convention (vom Skript erwartet, von init-Mode eingefuegt):
#   ## N. Title
#   <!-- truth-tracks: file1, file2, ... -->
#   ## Stand: YYYY-MM-DD HH:MM | commit <hash> | checks: <N>
#
# Exit-Codes:
#   0 - kein Fehler (auch wenn nichts zu stempeln war)
#   2 - Umgebungsfehler (Dateien fehlen, falscher Aufruf)

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

REPORT="docs/reports/TRUTH_REPORT.md"
PY="tools/truth-stamp.py"
MODE="${1:-stamp}"

# Preflight - silent skip if env not suitable
command -v python3 >/dev/null 2>&1 || exit 0

[[ -f "$REPORT" ]] || { echo "FEHLER: $REPORT fehlt" >&2; exit 2; }
[[ -f "$PY"    ]] || { echo "FEHLER: $PY fehlt" >&2; exit 2; }

case "$MODE" in
    stamp)
        # Pflicht-Preflight: nur laufen wenn in git mit parent commit
        command -v git >/dev/null 2>&1 || exit 0
        git rev-parse --git-dir >/dev/null 2>&1 || exit 0
        git rev-parse HEAD~1 >/dev/null 2>&1 || exit 0

        LAST_HASH=$(git rev-parse HEAD)
        LAST_HASH_SHORT=${LAST_HASH:0:7}
        TIMESTAMP=$(date +"%Y-%m-%d %H:%M")

        echo ""
        echo "=============================================================="
        printf '  Truth-Stamp Hook: %s\n' "$TIMESTAMP"
        echo "=============================================================="

        python3 "$PY" stamp "$REPORT" "$TIMESTAMP" "$LAST_HASH_SHORT"
        ;;

    --init|init)
        echo ""
        echo "=============================================================="
        printf '  Truth-Stamp Init-Mode\n'
        echo "=============================================================="
        python3 "$PY" init "$REPORT"
        echo ""
        echo "Sektionen mit Truth-Registry:"
        grep -nE '^<!-- truth-tracks:' "$REPORT" | sed 's/^/  /'
        ;;

    *)
        echo "Usage: $0 [stamp|--init]" >&2
        exit 2
        ;;
esac

exit 0
