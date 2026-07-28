#!/usr/bin/env bash
# SyxEconomyMod — Balance-Regression-Check (Sprint 9 / 7-2)
# ===========================================================
# Extrahiert kritische Balance-Konstanten aus EconConfig.java und
# vergleicht sie mit einer goldenen Referenz-Datei.
#
# Abweichung > 0.1% → FAIL (versehentliche Balance-Änderung erkannt).
# Neue Konstanten ohne Referenz-Eintrag → WARN (kein Blocker).
#
# Referenz-Datei: tools/balance-reference.txt (eine Zeile pro Konstante)
# Format:          FELDNAME=TYP:WERT
# Beispiel:        defaultWage=int:50
#                  guildSurplusShare=double:0.25
#
# Exit-Codes: 0 = alle Werte im Soll-Bereich
#             1 = Drift in mindestens einer Konstante
#
# Usage:
#   bash tools/balance-regression-check.sh
#   bash tools/balance-regression-check.sh --update  # Referenz aus aktuellem Code neu generieren

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

ECONCONFIG="src/vannon/syx/economy/core/EconConfig.java"
REFERENCE="tools/balance-reference.txt"
DRIFT_COUNT=0
WARN_COUNT=0
OK_COUNT=0

# ── Kritische Balance-Konstanten: Liste der Felder die geprüft werden ────────
# Format: FELDNAME|TYP (TYP = int, double, boolean)
CRITICAL_FIELDS=(
    "foodPriceCapMultiplier|double"
    "defaultWage|int"
    "guildSurplusShare|double"
    "guildSurplusMinProfitPerWorker|double"
    "profitElasticity|double"
    "scarcityMaxMultiple|double"
    "scarcitySteepness|double"
    "priceClampLo|double"
    "priceClampHi|double"
    "priceAbsoluteMax|double"
    "scarcityPriceBoost|double"
    "scarcityLaborBoost|double"
    "oddjobWageCeilingRatio|double"
    "targetFoodDays|double"
    "targetDrinkDays|double"
    "targetGoodsCoverage|double"
    "hungerDeathThreshold|int"
    "hungerDamageRate|int"
    "phaseFactorThreshold|int"
    "phaseFactorMin|double"
    "firmSizingHysteresis|double"
    "minimumWorkersPerWorkplace|int"
    "startingTreasury|int"
    "wageMax|int"
    "wageStep|int"
    "corveeDraftPercent|int"
    "debtSlaveThreshold|int"
    "housingBaseRentPerTile|int"
    "doleWealthThreshold|int"
    "doleHeadcap|int"
    "povertyPressureWealthThreshold|int"
    "meticImmigrationDepth|double"
    "meticImmigrationSteepness|double"
    "serviceUtilTarget|double"
    "serviceBasePrice|int"
    "marketTaxRate|double"
    "taxHappinessAtFullRate|double"
    "perHeadTaxExemptionThreshold|int"
    "laborNeutralPriority|int"
    "laborFrictionPoints|int"
    "transportFeePer100TileDay|int"
)

# ── Extraktion: Holt den Default-Wert eines public static Feldes aus EconConfig ──
extract_value() {
    local field="$1"
    local type="$2"
    local line

    # Suche: public static [final] TYPE FIELD = VALUE;
    line=$(grep -m1 "public static.*${field}\s*=" "$ECONCONFIG" 2>/dev/null || true)
    if [ -z "$line" ]; then
        echo ""
        return
    fi

    # Extrahiere den Wert nach dem '=' (letztes Token vor ';')
    local raw
    raw=$(echo "$line" | sed 's/.*=\s*//' | sed 's/;.*//' | xargs)
    echo "$raw"
}

# ── Vergleich: Prüft Wert gegen Referenz mit Toleranz ────────────────────────
compare_value() {
    local field="$1"
    local type="$2"
    local expected="$3"
    local actual="$4"

    if [ "$type" = "boolean" ]; then
        if [ "$actual" = "$expected" ]; then
            return 0
        else
            return 1
        fi
    fi

    # Numerischer Vergleich mit 0.1% Toleranz
    python3 -c "
a = float('$actual')
e = float('$expected')
if e == 0.0:
    exit(0 if a == 0.0 else 1)
ratio = a / e
exit(0 if 0.999 <= ratio <= 1.001 else 1)
" 2>/dev/null
}

# ═══════════════════════════════════════════════════════════════════════════════
#  MAIN
# ═══════════════════════════════════════════════════════════════════════════════

echo -e "${CYAN}═══ SyxEconomyMod Balance-Regression-Check ═══${NC}"

# ── Dependency-Check ────────────────────────────────────────────────────────
if ! command -v python3 &>/dev/null; then
    echo -e "  ${YELLOW}⊘ SKIP${NC} — python3 nicht verfügbar (benötigt für Float-Vergleich)"
    exit 0
fi

# ── --update: Referenz aus aktuellem Code generieren ────────────────────────
if [ "${1:-}" = "--update" ]; then
    echo "Generiere $REFERENCE aus aktuellem Code …"
    > "$REFERENCE"
    for entry in "${CRITICAL_FIELDS[@]}"; do
        field="${entry%%|*}"
        type="${entry##*|}"
        val=$(extract_value "$field" "$type")
        if [ -n "$val" ]; then
            echo "${field}=${type}:${val}" >> "$REFERENCE"
        else
            echo -e "  ${YELLOW}WARN${NC} Feld $field nicht in EconConfig gefunden"
        fi
    done
    echo -e "${GREEN}✓${NC} Referenz mit $(wc -l < "$REFERENCE") Konstanten geschrieben."
    exit 0
fi

# ── Prüfung: Aktuellen Code gegen Referenz validieren ──────────────────────
if [ ! -f "$REFERENCE" ]; then
    echo -e "  ${YELLOW}⊘ SKIP${NC} — Keine Referenz-Datei. Einmalig generieren mit:"
    echo "      bash tools/balance-regression-check.sh --update"
    exit 0
fi

if [ ! -f "$ECONCONFIG" ]; then
    echo -e "  ${RED}✗ FAIL${NC} — EconConfig.java nicht gefunden"
    exit 1
fi

declare -A REF_MAP
while IFS='=' read -r field typed_val; do
    [ -z "$field" ] && continue
    REF_MAP["$field"]="$typed_val"
done < "$REFERENCE"

for entry in "${CRITICAL_FIELDS[@]}"; do
    field="${entry%%|*}"
    type="${entry##*|}"
    actual=$(extract_value "$field" "$type")

    if [ -z "$actual" ]; then
        echo -e "  ${YELLOW}WARN${NC} $field: Feld nicht in EconConfig.java gefunden"
        WARN_COUNT=$((WARN_COUNT + 1))
        continue
    fi

    ref="${REF_MAP[$field]:-}"
    if [ -z "$ref" ]; then
        echo -e "  ${YELLOW}WARN${NC} $field=$actual — keine Referenz (neu? --update ausführen)"
        WARN_COUNT=$((WARN_COUNT + 1))
        continue
    fi

    expected="${ref#*:}"
    ref_type="${ref%%:*}"

    if [ "$type" != "$ref_type" ]; then
        echo -e "  ${RED}DRIFT${NC} $field: Typ-Mismatch (ref=$ref_type, actual=$type)"
        DRIFT_COUNT=$((DRIFT_COUNT + 1))
        continue
    fi

    if compare_value "$field" "$type" "$expected" "$actual"; then
        OK_COUNT=$((OK_COUNT + 1))
    else
        echo -e "  ${RED}DRIFT${NC} $field: expected=$expected  actual=$actual"
        DRIFT_COUNT=$((DRIFT_COUNT + 1))
    fi
done

# ── Ergebnis ────────────────────────────────────────────────────────────────
echo ""
echo -e "  ${GREEN}OK: ${OK_COUNT}${NC}  ${RED}DRIFT: ${DRIFT_COUNT}${NC}  ${YELLOW}WARN: ${WARN_COUNT}${NC}"

if [ "$DRIFT_COUNT" -gt 0 ]; then
    echo -e "${RED}✗ FAIL${NC} — ${DRIFT_COUNT} Balance-Konstante(n) haben Drift."
    echo "  Fix: Entweder den Code zurücksetzen oder die Referenz aktualisieren:"
    echo "       bash tools/balance-regression-check.sh --update"
    exit 1
fi

echo -e "${GREEN}✓ PASS${NC} — Alle ${OK_COUNT} Balance-Konstanten im Soll-Bereich."
exit 0
