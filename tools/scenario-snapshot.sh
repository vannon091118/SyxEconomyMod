#!/usr/bin/env bash
# SyxEconomyMod — Scenario-Snapshot Tool
# ========================================
# Generate + verify mods/saves/custom/bench-baseline.save with
# ScriptGuide-conformant format and reproducible YAML Pre-Spec.
#
# Usage:
#   bash tools/scenario-snapshot.sh generate [OPTIONS]
#       # generates / verifies bench-baseline.save via scenario_snapshot.py
#       # then re-validates with scenario_verify.py
#   bash tools/scenario-snapshot.sh verify [DIR]
#       # just-verify an existing custom-dir listing
#   bash tools/scenario-snapshot.sh check [DIR]
#       # same as verify (alias)
#   bash tools/scenario-snapshot.sh --help
#       # print help
#
# Exit codes (mimic tools/benchmark-compare.sh):
#   0  generate+verify OK
#   1  validation failed / drift
#   2  I/O error / missing dependency (python3 missing, dir absent, etc.)
#
# Sprint v0.13.108+StartingFromGround: Skript-Klasse für reproduzierbare
# bench-baseline.save Authorings. Vanilla-PATHS-MISC-CUSTOM-Listing simuliert
# in pure Python via os.listdir + .save Filter.

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    cat <<EOF
${BOLD}tools/scenario-snapshot.sh${NC} — SyxEconomyMod Scenario-Snapshot-Tool

${BOLD}USAGE${NC}
  bash tools/scenario-snapshot.sh generate   [py-args]      # default fixture
  bash tools/scenario-snapshot.sh verify     [DIR]          # verify-dir only
  bash tools/scenario-snapshot.sh --help

${BOLD}GENERATE${NC}
  Ruft scenario_snapshot.py mit Standard-Werten auf:
    --out mods/saves/custom/bench-baseline.save
    --population 50 --world-x 128 --world-y 64
    --seed 1392191 --mods SyxEconomyMod:0.13.108
  Plus verifying scenario_verify.py durch emuliertes PATHS.MISC().CUSTOM().list().

${BOLD}VERIFY${NC}
  bash tools/scenario-snapshot.sh verify [DIR]
    DIR default = mods/saves/custom
  Emuliert vanilla's PATHS.MISC().CUSTOM().list() in pure Python;
  validiert Pre-Spec Required-Fields und Byte-Integrität.

${BOLD}EXIT-CODES${NC}
  0 PASS — Generate+Verify OK / Verify OK
  1 FAIL — Missing required fields / target Save nicht in Listing
  2 ERROR — python3 missing / custom-dir nicht vorhanden / I/O failure

${BOLD}BEISPIEL${NC}
  bash tools/scenario-snapshot.sh generate
  bash tools/scenario-snapshot.sh verify mods/saves/custom
EOF
}

if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
    usage
    exit 0
fi

# ── python3-Check ────────────────────────────────────────────────────────────
if ! command -v python3 >/dev/null 2>&1; then
    echo -e "${RED}FEHLER: python3 nicht im PATH (scenario_snapshot.py braucht Python 3.8+).${NC}" >&2
    exit 2
fi

SUBCMD="${1:-generate}"
if [ "$#" -gt 0 ]; then shift; fi

case "$SUBCMD" in
    generate|gen)
        # Banner
        echo -e "${CYAN}╔═══════════════════════════════════════════════════════╗${NC}"
        echo -e "${CYAN}║  Scenario-Snapshot-Tool — Generate + Verify             ║${NC}"
        echo -e "${CYAN}╚═══════════════════════════════════════════════════════╝${NC}"

        # Step 1: generate
        echo -e "${CYAN}>>> [1/2] generate${NC}"
        if ! python3 "$SCRIPT_DIR/scenario_snapshot.py" "$@"; then
            SNAP_EXIT=$?
            echo -e "  ${RED}✗ FAIL${NC} — scenario_snapshot.py exit=$SNAP_EXIT"
            exit "$SNAP_EXIT"
        fi

        # Step 2: verify
        echo ""
        echo -e "${CYAN}>>> [2/2] verify (emulating PATHS.MISC().CUSTOM().list())${NC}"
        if ! python3 "$SCRIPT_DIR/scenario_verify.py" "mods/saves/custom"; then
            VERIFY_EXIT=$?
            echo -e "  ${RED}✗ FAIL${NC} — scenario_verify.py exit=$VERIFY_EXIT"
            exit "$VERIFY_EXIT"
        fi

        echo ""
        echo -e "${GREEN}✓ SCENARIO-SNAPSHOT-COMPLETE: generate + verify OK${NC}"
        exit 0
        ;;

    verify|check)
        # Banner
        echo -e "${CYAN}╔═══════════════════════════════════════════════════════╗${NC}"
        echo -e "${CYAN}║  Scenario-Snapshot-Tool — Verify                       ║${NC}"
        echo -e "${CYAN}╚═══════════════════════════════════════════════════════╝${NC}"
        DIR="${1:-mods/saves/custom}"
        python3 "$SCRIPT_DIR/scenario_verify.py" "$DIR"
        RC=$?
        case $RC in
            0) echo -e "  ${GREEN}✓ VERIFY PASS${NC}"; exit 0 ;;
            1) echo -e "  ${RED}✗ VERIFY FAIL — Drift/Feld-Validation${NC}"; exit 1 ;;
            *) echo -e "  ${RED}✗ VERIFY ERROR (exit $RC)${NC}"; exit $RC ;;
        esac
        ;;

    *)
        echo -e "${RED}Unknown subcommand: $SUBCMD${NC}" >&2
        usage >&2
        exit 2
        ;;
esac
