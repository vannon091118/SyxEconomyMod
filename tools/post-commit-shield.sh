#!/usr/bin/env bash
# tools/post-commit-shield.sh — Post-Commit Regression-Detector
# ===============================================================
# Läuft NACH jedem Commit (non-blocking). Vergleicht die aktuellen
# Phase-4.7-Metriken (IdentityHashMap/EngineSeams/catch(Throwable)/
# printStackTrace) gegen eine gespeicherte Baseline.
#
# Drift nach OBEN (= Regression): WARNUNG mit before→after.
# Drift nach UNTEN (= Verbesserung): Baseline wird aktualisiert.
# Keine Baseline vorhanden: Baseline wird angelegt (erster Commit).
#
# Exit: immer 0 (non-blocking — Commit ist bereits durch).

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BASELINE=".git/hooks/.phase47-baseline"
CORE_DIR="src/vannon/syx/economy/core"

# ---- Messung (gleiche grep-Patterns wie phase47-shield.sh) ----
count_identityhash() {
    { grep -rln 'new IdentityHashMap' "$CORE_DIR" 2>/dev/null \
        | grep -vE 'IdentityMapRegistry\.java|IdentityKeys\.java' || true; } | wc -l
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

IH=$(count_identityhash)
ES=$(count_engineseams)
CT=$(count_throwable)
PS=$(count_printstacktrace)

# ---- Baseline-Handling ----
if [[ ! -f "$BASELINE" ]]; then
    # Erster Commit: Baseline anlegen, keine Warnung.
    mkdir -p "$(dirname "$BASELINE")"
    cat > "$BASELINE" <<EOF
IDENTITYHASH=$IH
ENGINESEAMS=$ES
CATCH_THROWABLE=$CT
PRINTSTACKTRACE=$PS
EOF
    echo -e "${GREEN}[post-commit-shield] Baseline angelegt:${NC}"
    echo "  IdentityHashMap=$IH  EngineSeams=$ES  catch(Throwable)=$CT  printStackTrace=$PS"
    exit 0
fi

# Baseline laden
source "$BASELINE"

# ---- Vergleich ----
warned=0
improved=0
compare() {
    local label="$1" current="$2" baseline_val="$3"
    if (( current > baseline_val )); then
        echo -e "  ${RED}${label}: ${baseline_val} → ${current}  REGRESSION (+$((current - baseline_val)))${NC}"
        warned=1
    elif (( current < baseline_val )); then
        echo -e "  ${GREEN}${label}: ${baseline_val} → ${current}  verbessert (-$((baseline_val - current)))${NC}"
        improved=1
    else
        echo "  ${label}: ${current}  (unverändert)"
    fi
}

echo ""
echo -e "${YELLOW}[post-commit-shield] Post-Commit-Regression-Check:${NC}"
compare "IdentityHashMap"   "$IH" "${IDENTITYHASH:-0}"
compare "EngineSeams"       "$ES" "${ENGINESEAMS:-0}"
compare "catch(Throwable)"  "$CT" "${CATCH_THROWABLE:-0}"
compare "printStackTrace"   "$PS" "${PRINTSTACKTRACE:-0}"

# ---- Baseline-Update ----
if (( warned == 1 )); then
    echo ""
    echo -e "${RED}[post-commit-shield] ⚠️  REGRESSION — Metriken haben sich verschlechtert.${NC}"
    echo -e "${RED}  Baseline bleibt auf altem Stand (kein Auto-Update bei Regression).${NC}"
elif (( improved == 1 )); then
    cat > "$BASELINE" <<EOF
IDENTITYHASH=$IH
ENGINESEAMS=$ES
CATCH_THROWABLE=$CT
PRINTSTACKTRACE=$PS
EOF
    echo ""
    echo -e "${GREEN}[post-commit-shield] ✅ Baseline aktualisiert (Verbesserung erkannt).${NC}"
else
    echo ""
    echo -e "${GREEN}[post-commit-shield] ✅ Keine Drift — Baseline unverändert.${NC}"
fi

exit 0
