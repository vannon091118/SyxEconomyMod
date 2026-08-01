#!/usr/bin/env bash
# tools/food-dole-cheat-check.sh — Phase 5h Pre-Phase-6 Blocker Audit
#
# Misst die Equity-Drift des Food-Dole-Subsystems:
#       ratio = ration_out / total_money       (über die letzte macro-CSV-Reihe)
# Exited 1 wenn ratio > THRESHOLD (default 5 %) — Cheat-Drift erkannt.
#
# Plus: statischer Default-Check auf EconConfig.handoutWalletAmount > HANDOUT_CAP.
# Defensiv: wenn keine macro-CSV gefunden → Fehler mit Anleitung statt Silent-Pass.
#
# Usage:
#   bash tools/food-dole-cheat-check.sh                     # default threshold 5%
#   THRESHOLD=3 bash tools/food-dole-cheat-check.sh        # override (CI/CD)
#   HANDOUT_CAP=80 bash tools/food-dole-cheat-check.sh     # override static cap
#   DIAGNOSTICS_DIR=/path/to/dir bash ... tool override    # custom dir

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
THRESHOLD="${THRESHOLD:-5.0}"        # percent — default 5%
HANDOUT_CAP="${HANDOUT_CAP:-99}"     # absolute Denari — default 99
DIAGNOSTICS_DIR="${DIAGNOSTICS_DIR:-$HOME/.local/share/songsofsyx/mods/SyxEconomyMod/diagnostics}"

echo "[food-dole-cheat-check] Phase 5h audit — threshold=${THRESHOLD}%  handout-cap=${HANDOUT_CAP}  dir=${DIAGNOSTICS_DIR}"

# ────────────────────────────────────────────────────────────────────────
# A) RUNTIME RATIO  — uses latest macro CSV from DiagnosticExporter
# ────────────────────────────────────────────────────────────────────────

LATEST_MACRO=$(ls -t "$DIAGNOSTICS_DIR"/rebalance_macro_*.csv 2>/dev/null | head -1 || true)
if [[ -z "${LATEST_MACRO}" || ! -f "${LATEST_MACRO}" ]]; then
    echo "[FAIL] no macro CSV found at $DIAGNOSTICS_DIR/rebalance_macro_*.csv"
    echo "        enable diagnosticsExport=true in EconConfig during play-session,"
    echo "        or run 30+ in-game days for exportDay() to fire."
    exit 1
fi

# Header-Index-Lookup für ration_out und total_money (positional, robust gegen Spalten-Reihenfolge-Änderungen).
HEADER=$(head -1 "$LATEST_MACRO")
RATION_COL=$(echo "$HEADER" | tr ',' '\n' | grep -n '^ration_out$' | cut -d: -f1 || true)
MONEY_COL=$(echo "$HEADER" | tr ',' '\n' | grep -n '^total_money$' | cut -d: -f1 || true)
DAY_COL=$(echo "$HEADER" | tr ',' '\n' | grep -n '^game_day$' | cut -d: -f1 || true)

if [[ -z "$RATION_COL" || -z "$MONEY_COL" ]]; then
    echo "[FAIL] macro CSV missing 'ration_out' or 'total_money' columns."
    echo "        file: $LATEST_MACRO"
    echo "        header: $HEADER"
    exit 1
fi

# Schema-drift-Detection: header field count must equal last row field count. Ohne diesen
# Check kann eine zukünftige DiagnosticExporter-Änderung (Spalte addiert/entfernt) dazu
# führen, dass cut -f"$COL" die falsche Spalte oder ein leeres Feld liest — silent Bug.
HEADER_FIELDS=$(awk -F, 'END{print NF+0}' <<<"$HEADER")
LAST_ROW=$(tail -n 1 "$LATEST_MACRO")
LAST_FIELDS=$(awk -F, 'END{print NF+0}' <<<"$LAST_ROW")
if [[ "$HEADER_FIELDS" != "$LAST_FIELDS" ]]; then
    echo "[FAIL] CSV schema-drift detected: header has $HEADER_FIELDS columns, last row has $LAST_FIELDS."
    echo "        This is a DiagnosticExporter bug, not a food-dole issue."
    echo "        file: $LATEST_MACRO"
    exit 1
fi

GAME_DAY=$(echo "$LAST_ROW"  | cut -d',' -f"$DAY_COL")
RATION_OUT=$(echo "$LAST_ROW" | cut -d',' -f"$RATION_COL")
TOTAL_MONEY=$(echo "$LAST_ROW" | cut -d',' -f"$MONEY_COL")

# Numerische Validität: wenn CSV-Corruption einen Non-Numeric-Wert (z. B. '—' oder '') in
# ration_out einträgt, würde awk -v r="..." 0 liefern und silent als 0 %-PASS maskiert.
# Wir brechen stattdessen hart ab — Corruption ist ein separater Diagnose-Pfad.
if ! [[ "$RATION_OUT" =~ ^[0-9]+$ ]]; then
    echo "[FAIL] ration_out='$RATION_OUT' is non-numeric in $LATEST_MACRO — CSV corruption, abort."
    exit 1
fi
if ! [[ "$TOTAL_MONEY" =~ ^[0-9]+$ ]]; then
    echo "[FAIL] total_money='$TOTAL_MONEY' is non-numeric in $LATEST_MACRO — CSV corruption, abort."
    exit 1
fi

if [[ "$TOTAL_MONEY" -le 0 ]]; then
    echo "[WARN] total_money=0 (col=$MONEY_COL, day=$GAME_DAY). Skipping ratio (likely new save)."
    RATIO="0.000"
else
    RATIO=$(awk -v r="$RATION_OUT" -v m="$TOTAL_MONEY" 'BEGIN{printf "%.4f", (r/m)*100.0}')
fi

RATIO_OK=$(awk -v r="$RATIO" -v t="$THRESHOLD" 'BEGIN{print (r<=t) ? "yes" : "no"}')

echo ""
echo "── RUNTIME equity-drift (latest snapshot, day=$GAME_DAY) ──"
printf "  ration_out       = %s\n" "$RATION_OUT"
printf "  total_money      = %s\n" "$TOTAL_MONEY"
printf "  ratio            = %s%%\n" "$RATIO"
printf "  threshold        = %s%% → %s\n" "$THRESHOLD" "$RATIO_OK"
if [[ "$RATIO_OK" != "yes" ]]; then
    echo "[FAIL] Equity-Drift exceeds threshold (${RATIO}% > ${THRESHOLD}%) — food-dole is bleeding money into the economy."
    echo "       likely root cause: handoutWalletAmount too high, or foodAffordabilityGateEnabled=false."
    RUNTIME_FAIL=1
fi

# ────────────────────────────────────────────────────────────────────────
# B) STATIC DEFAULT CHECK — handoutWalletAmount must stay ≤ HANDOUT_CAP
# ────────────────────────────────────────────────────────────────────────

ECONCONFIG="$ROOT/src/vannon/syx/economy/core/EconConfig.java"
if [[ ! -f "$ECONCONFIG" ]]; then
    echo "[WARN] EconConfig.java not found at $ECONCONFIG — skipping static check."
else
    AMOUNT=$(grep -E '^\s*public\s+static\s+int\s+handoutWalletAmount\s*=' "$ECONCONFIG" | sed -E 's/.*=\s*([0-9]+).*/\1/' | head -1 || true)
    if [[ -z "$AMOUNT" ]]; then
        echo "[WARN] could not parse handoutWalletAmount from EconConfig.java — skipping static check."
    else
        echo ""
        echo "── STATIC default check ──"
        printf "  handoutWalletAmount = %s\n" "$AMOUNT"
        printf "  HANDOUT_CAP         = %s → " "$HANDOUT_CAP"
        if [[ "$AMOUNT" -gt "$HANDOUT_CAP" ]]; then
            echo "[FAIL]"
            echo "        handoutWalletAmount ($AMOUNT) exceeds HANDOUT_CAP ($HANDOUT_CAP). Phase 5h regressed."
            STATIC_FAIL=1
        else
            echo "[OK]"
        fi
    fi
fi

# ────────────────────────────────────────────────────────────────────────
# SUMMARY
# ────────────────────────────────────────────────────────────────────────

echo ""
if [[ "${RUNTIME_FAIL:-0}" -eq 1 || "${STATIC_FAIL:-0}" -eq 1 ]]; then
    echo "[food-dole-cheat-check] FAIL — one or more gates tripped."
    exit 1
fi

echo "[food-dole-cheat-check] PASS — ratio ${RATIO}% ≤ ${THRESHOLD}%, handoutWalletAmount=${AMOUNT} ≤ ${HANDOUT_CAP}."
exit 0
