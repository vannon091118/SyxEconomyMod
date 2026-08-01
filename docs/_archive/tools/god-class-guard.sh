#!/usr/bin/env bash
# SyxEconomyMod — God-Class Guard (Master-Script)
# ===============================================
# Wrapper-Script. Delegiert an tools/god-class-guard/run_check.py.
# Wird vom Build-Gate (Gate 9) und Pre-Commit-Hook aufgerufen.
#
# Modes (RFC M-3 §12):
#   --mode=dry   (default) Report only, exit 0 unless blocker
#   --mode=soft           WARN exit 1, BLOCK exit 2
#   --mode=hard / --strict  WARN becomes BLOCK, exit 2
#
# Andere Flags:
#   --json           JSON-Output fuer CI
#   --quiet          Nur Zusammenfassung, keine Per-File-Zeilen
#   --run-meta-tests Lokale Tools/tests/god-class-guard/run_meta_tests.sh
#
# Exit-Codes (gespiegelt von run_check.py):
#   0 = PASS (alle Files OK)
#   1 = mind. 1 WARN (Mode != hard)
#   2 = mind. 1 BLOCKER (oder mind. 1 WARN in Mode=hard)

set -eo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

# shellcheck source=lib/gate_report.sh
. "$SCRIPT_DIR/lib/gate_report.sh"

MODE=dry
JSON_FLAG=""
QUIET=false
META=false

for arg in "$@"; do
    case "$arg" in
        --mode=dry)  MODE=dry ;;
        --mode=soft) MODE=soft ;;
        --mode=hard) MODE=hard ;;
        --strict)    MODE=hard ;;
        --json)      JSON_FLAG="--json" ;;
        --quiet)     QUIET=true ;;
        --run-meta-tests) META=true ;;
        -h|--help)
            cat <<EOF
Usage: bash tools/god-class-guard.sh [OPTIONS]

Options:
  --mode=dry|soft|hard    Operating mode (default: dry = report-only, exit 0 unless blocker)
  --strict                Alias for --mode=hard (WARN counts as BLOCKER)
  --json                  Emit JSON for CI consumption
  --quiet                 Summary only (no per-file output)
  --run-meta-tests        Run synthetic meta-tests under tools/tests/god-class-guard/

Exit codes:
  0  All files PASS
  1  WARN present (Mode=soft or default-dry-with-blocker would still 2)
  2  BLOCKER present (or WARN in Mode=hard)

Examples:
  bash tools/god-class-guard.sh               # Dry-run report
  bash tools/god-class-guard.sh --mode=soft   # Local check during development
  bash tools/god-class-guard.sh --strict      # CI / pre-commit / gate
  bash tools/god-class-guard.sh --json        # Pipe to CI log aggregator
  bash tools/god-class-guard.sh --run-meta-tests  # Self-test the gate

EOF
            exit 0 ;;
        *) echo "Unknown arg: $arg" >&2; exit 1 ;;
    esac
done

# ── Branch: Meta-Tests ─────────────────────────────────────────────────
if [ "$META" = true ]; then
    gate_print_header "God-Class-Guard Meta-Tests"
    echo "  Delegating to tools/tests/god-class-guard/run_meta_tests.sh"
    if bash tools/tests/god-class-guard/run_meta_tests.sh; then
        gate_pass "Meta-Tests bestanden"
        gate_summary 0
        exit 0
    else
        gate_fail "Meta-Tests fehlgeschlagen"
        gate_summary 2
        exit 2
    fi
fi

# ── Normal-Run ─────────────────────────────────────────────────────────
gate_print_header "God-Class-Guard (Mode=$MODE)"
# Delegation an Python-Runner. Exit-Code wird 1:1 propagiert.
exec python3 tools/god-class-guard/run_check.py --mode "$MODE" ${JSON_FLAG:-}
