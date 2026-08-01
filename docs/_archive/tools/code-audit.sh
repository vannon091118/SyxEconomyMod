#!/usr/bin/env bash
# SyxEconomyMod — Code Audit: Silent Failure Detection
# =====================================================
# Scannt den Quellcode auf Muster, die Fehler still verschlucken.
#
# Exit-Codes:
#   0 — sauber (keine Blocker)
#   1 — Warnungen
#   2 — Blocker (leere catch-Blöcke, printStackTrace)
#
# Usage:
#   bash tools/code-audit.sh            # Default: Warnungen non-blocking
#   bash tools/code-audit.sh --strict   # Warnungen werden zu Blockern

set -euo pipefail

SRC="src/vannon/"
EXCLUDE_BENCHMARK="src/vannon/syx/economy/benchmark/"
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

STRICT=false
[[ "${1:-}" == "--strict" ]] && STRICT=true

VIOLATIONS=0
WARNINGS=0

echo -e "${CYAN}═══ SyxEconomyMod Code Audit ═══${NC}"
echo ""

# ── 1. Leere catch-Blöcke (BLOCKER) ───────────────────────────────────
# Verwendet awk mit Brace-Depth-Tracking für zuverlässige Erkennung.
echo -n "  [1] Leere catch-Blöcke … "

EMPTY_CATCHES=$(awk '
/^\s*catch\s*\(/ {
    in_catch = 1
    brace_open = 0
    body = ""
    line_num = NR
    file = FILENAME
    next
}
in_catch {
    body = body $0 "\n"
    for (i = 1; i <= length($0); i++) {
        c = substr($0, i, 1)
        if (c == "{") brace_open++
        if (c == "}") brace_open--
    }
    if (brace_open < 0) {
        cleaned = body
        gsub(/\/\/[^\n]*/, "", cleaned)
        gsub(/\/\*[^*]*\*\//, "", cleaned)
        gsub(/[[:space:]]/, "", cleaned)
        if (length(cleaned) == 0) {
            print file ":" line_num
        }
        in_catch = 0
    }
}' $(find "$SRC" -name '*.java' 2>/dev/null | sort) 2>/dev/null || true)

# Robust zaehlen: wc -l, keine grep -c Magie
if [ -n "$EMPTY_CATCHES" ]; then
    COUNT=$(echo "$EMPTY_CATCHES" | wc -l | awk '{print int($1)}')
else
    COUNT=0
fi

if [ "$COUNT" -gt 0 ] 2>/dev/null; then
    echo -e "${RED}✗ BLOCKER (${COUNT} gefunden)${NC}"
    echo "$EMPTY_CATCHES" | head -20 | while read -r loc; do echo "      ${loc}"; done
    VIOLATIONS=$((VIOLATIONS + COUNT))
else
    echo -e "${GREEN}✓${NC}"
fi

# ── 2. printStackTrace (BLOCKER) ───────────────────────────────────────
echo -n "  [2] printStackTrace-Aufrufe … "
PST_FILES=$(grep -rl '\.printStackTrace()' "$SRC" 2>/dev/null || true)
if [ -n "$PST_FILES" ]; then
    PST_COUNT=$(echo "$PST_FILES" | wc -l | awk '{print int($1)}')
    echo -e "${RED}✗ BLOCKER (${PST_COUNT} gefunden)${NC}"
    grep -rn '\.printStackTrace()' "$SRC" 2>/dev/null | head -10 | while read -r loc; do echo "      ${loc}"; done
    VIOLATIONS=$((VIOLATIONS + PST_COUNT))
else
    echo -e "${GREEN}✓${NC}"
fi

# ── 3. catch(Throwable) außerhalb von Adaptern und Benchmark ────────
echo -n "  [3] catch(Throwable) außerhalb adapter/ … "

THROWABLE_OUTSIDE=$(grep -rl 'catch\s*(\s*Throwable' "$SRC" 2>/dev/null | grep -v 'adapter/' | grep -v 'benchmark/' || true)
THROWABLE_ADAPTER=$(grep -rl 'catch\s*(\s*Throwable' "$SRC" 2>/dev/null | grep 'adapter/' | wc -l | awk '{print int($1)}' || echo 0)

if [ -n "$THROWABLE_OUTSIDE" ]; then
    THROWABLE_COUNT=$(echo "$THROWABLE_OUTSIDE" | wc -l | awk '{print int($1)}')
    echo -e "${YELLOW}⚠ ${THROWABLE_COUNT} außerhalb adapter/ (${THROWABLE_ADAPTER} in adapter/ erlaubt)${NC}"
    echo "$THROWABLE_OUTSIDE" | head -10 | while read -r loc; do echo "      ${loc}"; done
    WARNINGS=$((WARNINGS + THROWABLE_COUNT))
else
    echo -e "${GREEN}✓ (${THROWABLE_ADAPTER} in adapter/ — Phase-4-Design)${NC}"
fi

# ── 4. catch(Exception) außerhalb Benchmark — Übersicht ────────────────
echo -n "  [4] catch(Exception) — Übersicht … "
EXCEPTION_FILES=$(grep -rl 'catch\s*(\s*Exception' "$SRC" 2>/dev/null | grep -v 'benchmark/' || true)
if [ -n "$EXCEPTION_FILES" ]; then
    EXCEPTION_COUNT=$(echo "$EXCEPTION_FILES" | wc -l | awk '{print int($1)}')
else
    EXCEPTION_COUNT=0
fi
echo -e "${YELLOW}${EXCEPTION_COUNT} insgesamt (manuelle Prüfung empfohlen)${NC}"
WARNINGS=$((WARNINGS + EXCEPTION_COUNT))

# ── 5. InterruptedException ohne Re-Interrupt (WARNUNG) ────────────────
echo -n "  [5] InterruptedException ohne Re-Interrupt … "
# Nur Sites zaehlen die KEIN interrupt() nach dem Catch haben
IE_FILES_RAW=$(grep -rl 'catch\s*(\s*InterruptedException' "$SRC" 2>/dev/null || true)
IE_MISSING_COUNT=0
if [ -n "$IE_FILES_RAW" ]; then
    while IFS= read -r f; do
        # Suche interrupt() in den 5 Zeilen nach dem catch
        if ! grep -A5 'catch\s*(\s*InterruptedException' "$f" 2>/dev/null | grep -q 'interrupt()'; then
            IE_MISSING_COUNT=$((IE_MISSING_COUNT + 1))
        fi
    done <<< "$IE_FILES_RAW"
fi
IE_TOTAL=$(echo "$IE_FILES_RAW" | wc -l | awk '{print int($1)}')
if [ "$IE_MISSING_COUNT" -gt 0 ]; then
    echo -e "${RED}✗ ${IE_MISSING_COUNT} ohne Re-Interrupt von ${IE_TOTAL}${NC}"
    VIOLATIONS=$((VIOLATIONS + IE_MISSING_COUNT))
else
    echo -e "${GREEN}✓ alle korrekt (${IE_TOTAL} mit interrupt())${NC}"
fi

# ── Zusammenfassung ────────────────────────────────────────────────────
echo ""
echo -e "${CYAN}─── Zusammenfassung ───${NC}"
echo -e "  Blocker:  ${RED}${VIOLATIONS}${NC}"
echo -e "  Warnungen: ${YELLOW}${WARNINGS}${NC}"

if [ "$VIOLATIONS" -gt 0 ]; then
    echo -e "\n${RED}✗ AUDIT FEHLGESCHLAGEN — ${VIOLATIONS} Blocker müssen behoben werden.${NC}"
    exit 2
fi

if [ "$STRICT" = true ] && [ "$WARNINGS" -gt 0 ]; then
    echo -e "\n${RED}✗ AUDIT FEHLGESCHLAGEN (--strict) — ${WARNINGS} Warnungen blocken den Build.${NC}"
    exit 1
fi

if [ "$WARNINGS" -gt 0 ]; then
    echo -e "\n${YELLOW}⚠ ${WARNINGS} Warnungen — mit --strict werden sie zu Blockern.${NC}"
fi

echo -e "\n${GREEN}✓ Code Audit bestanden${NC}"
exit 0
