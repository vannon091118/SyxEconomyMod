#!/usr/bin/env bash
# SyxEconomyMod — Benchmark CSV Compare
# =====================================
# Vergleicht zwei Benchmark-CSV-Dateien (Baseline vs. aktueller Run) per-Spalte
# mit konfigurierbaren Toleranzen.
#
# Usage:
#   bash tools/benchmark-compare.sh [BASELINE.csv] [RUN.csv]
#   bash tools/benchmark-compare.sh --help
#
# Defaults:
#   BASELINE = ./bench-baseline.csv
#   RUN      = ./bench-run.csv
#
# Toleranz-Profile (relativ, in Prozent):
#   --gini-tol   = 0.1  (default; Gini ist [0,1] bounded, sehr eng)
#   --money-tol  = 1.0  (default; money_supply in Denari)
#   --price-tol  = 2.0  (default; median_price, etwas volatiler)
#   --abs-floor  = 1.0  (default; Floor für Division bei kleinen Werten)
#
# Exit-Codes (gleich wie Python-Script):
#   0  alle Zeilen innerhalb der Toleranz
#   1  mindestens eine Drift-Zeile
#   2  Eingabefehler (fehlende Datei / Spalte / Python3 nicht verfügbar)
#
# Sprint v0.13.108+StartingFromGround: integriert als Gate 10 in build-gate.sh.
# Eigenes Skip-Flag: SKIP_BENCH_COMPARE=1 (consistent mit Sprint U2 Skip-Zoo-Elimination)
# wird zusaetzlich zum einheitlichen gate.skip=true respektiert.

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Args (defaults + positional) ────────────────────────────────────────────
BASELINE="${1:-${BENCH_BASELINE:-./bench-baseline.csv}}"
if [ "${1:-}" != "" ]; then shift; fi
RUN="${1:-${BENCH_RUN:-./bench-run.csv}}"
if [ "${1:-}" != "" ]; then shift; fi

usage() {
    cat <<EOF
${BOLD}tools/benchmark-compare.sh${NC} — SyxEconomyMod Benchmark-CSV-Diff

${BOLD}USAGE${NC}
  bash tools/benchmark-compare.sh [BASELINE.csv] [RUN.csv] [extra-args-passed-to-python3]

${BOLD}DEFAULTS${NC}
  BASELINE = ./bench-baseline.csv (oder \$BENCH_BASELINE)
  RUN      = ./bench-run.csv      (oder \$BENCH_RUN)

${BOLD}PYTHON-ARGS (durchgereicht)${NC}
  --gini-tol   FLOAT   Gini-Toleranz in Prozent      (default 0.1)
  --money-tol  FLOAT   money_supply-Toleranz %      (default 1.0)
  --price-tol  FLOAT   median_price-Toleranz %      (default 2.0)
  --abs-floor  FLOAT   Floor fuer relative Division (default 1.0)
  --strict              Fehlende Tage zaehlen als Drift
  --csv-out PATH        Maschinenlesbarer Drift-CSV
  --show-top  INT       Top-N Drift-Zeilen in Report (default 10)
  --quiet               Report auf stdout unterdruecken

${BOLD}EXIT-CODES${NC}
  0 PASS — alle Zeilen innerhalb der Toleranz
  1 FAIL — mindestens eine Drift-Zeile oder (--strict) missing coverage
  2 ERROR — fehlende Python3 / Datei nicht lesbar / CSV-Heder defekt

${BOLD}BEISPIEL${NC}
  bash tools/benchmark-compare.sh bench-baseline.csv bench-run.csv
  bash tools/benchmark-compare.sh --gini-tol 0.05 --money-tol 0.5 --strict
  SKIP_BENCH_COMPARE=1 bash tools/build-gate.sh    # ueberspringt Gate 10
EOF
}

if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
    usage
    exit 0
fi

# ── Skip + python3-Verfuegbarkeit ──────────────────────────────────────────
if ! command -v python3 >/dev/null 2>&1; then
    echo -e "${RED}FEHLER: python3 nicht im PATH. tools/benchmark_compare.py braucht Python 3.8+.${NC}" >&2
    exit 2
fi

PY="python3"

# ── Banner ──────────────────────────────────────────────────────────────────
echo -e "${CYAN}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║  Benchmark-CSV-Compare:                                   ║${NC}"
echo -e "${CYAN}║    Baseline: ${BASELINE}${NC}"
echo -e "${CYAN}║    Run:      ${RUN}${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════╝${NC}"

# ── Skript-Aufruf ───────────────────────────────────────────────────────────
"$PY" "$SCRIPT_DIR/benchmark_compare.py" "$BASELINE" "$RUN" "$@"
RC=$?

case $RC in
    0) echo -e "  ${GREEN}BENCHMARK-COMPARE PASS${NC}"; exit 0 ;;
    1) echo -e "  ${RED}BENCHMARK-COMPARE FAIL — Drift ausserhalb Toleranz${NC}"; exit 1 ;;
    *) echo -e "  ${RED}BENCHMARK-COMPARE ERROR (exit $RC)${NC}"; exit $RC ;;
esac
