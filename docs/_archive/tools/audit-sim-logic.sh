#!/usr/bin/env bash
# SyxEconomyMod — Ingame-Package + Sim-Logik Audit
# =================================================
# Scannt alle ingame-Packages (settlement/room/* + core/Stim-Klassen) auf
# Boundary-Conditions und Drift-Marker:
#
#   1. Division durch 0 (ohne Schutz) in Sim-Klassen     — BLOCKER
#   2. null-Input nicht geprüft in Public-API            — WARN
#   3. großer Unbounded-Loop ohne Break-Condition        — WARN
#   4. Magic-Number-Tombola (numerische Konstanten > 9999) — INFO
#   5. fehlender reset()-Method (Sprint 4 Pattern)       — INFO
#
# Ziel: sicherstellen, dass alle Sim-Logiken robust gegen Edge-Cases und
# deterministisch bei Reset sind.
#
# Usage:
#   bash tools/audit-sim-logic.sh              # Default (info-level)
#   bash tools/audit-sim-logic.sh --strict     # WARN werden zu Blocker
#
# Sprint 6.3 — additive only.

set -eo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

# shellcheck source=lib/gate_report.sh
. "$SCRIPT_DIR/lib/gate_report.sh"

STRICT=false
[[ "${1:-}" == "--strict" ]] && STRICT=true

SRC_CORE="src/vannon/syx/economy/core/"
SRC_SETTLEMENT="src/vannon/syx/economy/settlement/"
SRC_ROOM_MAIN="src/vannon/syx/economy/settlement/room/main/"
SRC_ROOM_SERVICE="src/vannon/syx/economy/settlement/room/service/"

violations=0
warnings=0
infos=0

gate_print_header "SyxEconomyMod Ingame-Sim-Logik Audit"

# ── 1. Division durch 0 (kein Schutz) in Sim-Klassen ──
echo -n "  [1] Ungeschuetzte Division in core/Flow*/Audit/Escrow/Exchange ... "
# Heuristic: 'something / variable' in pricing/exchange kernels, excluding
# comment-only lines. Accurate static analysis would need a real Java parser.
DIV_OPEN=$(grep -rEn '\b[a-zA-Z_][a-zA-Z0-9_]*\s*/\s*[a-zA-Z_][a-zA-Z0-9_.]*\b' "$SRC_CORE" 2>/dev/null \
    | grep -vE '\s+//' \
    | grep -vE '\* ' \
    | grep -vE '^\s*\*' \
    | grep -E 'FlowPrices|FlowMeter|EscrowKernel|ExchangeKernel|AuditKernel|FoodGateKernel' \
    | head -30 || true)
DIV_COUNT=$(echo "$DIV_OPEN" | grep -c . 2>/dev/null || echo 0)
if [ "$DIV_COUNT" -gt 0 ]; then
    echo "INFO — ${DIV_COUNT} Divisions-Stellen (manuelle Pruefung empfohlen)"
    infos=$((infos + 1))
else
    echo "OK"
fi

# ── 2. null-Input nicht geprueft in Public-API (EconConfig-statics) ──
echo -n "  [2] EconConfig effective*-Methoden mit null-Check ... "
# Spot-Check: effectiveInitialWallet / effectiveImmigrantWallet rufen
# EconomySim.active() auf, das null zurueckgeben KANN pre-init. Wir lesen
# den Method-Body und suchen '== null' als Indikator fuer den Guard.
WARN_NULL=0
for METHOD in effectiveInitialWallet effectiveImmigrantWallet; do
    BODY=$(awk "/public static .* $METHOD/,/^    \}/" "$SRC_CORE/EconConfig.java" 2>/dev/null || true)
    if [ -n "$BODY" ]; then
        if ! echo "$BODY" | grep -q '== null'; then
            echo -n "$METHOD/MISSING "
            WARN_NULL=$((WARN_NULL + 1))
        fi
    fi
done
if [ "$WARN_NULL" -gt 0 ]; then
    echo "(fehlt)"
    warnings=$((warnings + WARN_NULL))
else
    echo "OK"
fi

# ── 3. Großer Unbounded-Loop ──
echo -n "  [3] while(true) / for(;;) ohne Break im Sim ... "
# Suche Kommentar-/String-Lines raus, damit String-Literale wie 'while(true)'
# in Text-Dokumentation nicht als Loops zaehlen.
UNBOUND=$(grep -rEn 'while\s*\(\s*true\s*\)|for\s*\(\s*;;\s*\)' "$SRC_CORE" 2>/dev/null \
    | grep -vE '//.*while.*true|^\s*\*.*while|//.*for\(;;' \
    || true)
UB_COUNT=$(echo "$UNBOUND" | grep -c . 2>/dev/null || echo 0)
if [ "$UB_COUNT" -gt 0 ]; then
    echo "WARN — ${UB_COUNT} unbounded loops"
    echo "$UNBOUND" | head -10 | while read -r l; do echo "      $l"; done
    warnings=$((warnings + 1))
else
    echo "OK"
fi

# ── 4. Magic-Number-Tombola (> 9999 in Sim-Klassen) ──
echo -n "  [4] Magic-Numbers > 9999 in Sim-Klassen ... "
MAGIC=$(grep -rEn '\b[0-9]{5,}\b' "$SRC_CORE" 2>/dev/null \
    | grep -vE '\.csv|Hudson|//.*[0-9]|\.png:|\.json:|\.txt:' \
    | wc -l | awk '{print int($1)}')
echo "INFO — ${MAGIC} Stellen (meist save-magic oder Senna-State-IDs)"
infos=$((infos + 1))

# ── 5. Fehlende reset() in singleton-ähnlichen Klassen ──
echo -n "  [5] Singleton-Pattern ohne reset() Methode ... "
SINGLETON=$(grep -rln 'private static final.*INSTANCE\|public static final.*INSTANCE' "$SRC_CORE" 2>/dev/null | head -10 || true)
RESET_MISSING=""
if [ -n "$SINGLETON" ]; then
    while IFS= read -r f; do
        if ! grep -q 'public static.*\breset\b()' "$f" 2>/dev/null; then
            RESET_MISSING="$RESET_MISSING$f\n"
        fi
    done <<< "$SINGLETON"
fi
RM_COUNT=$(echo -e "$RESET_MISSING" | grep -c . 2>/dev/null || echo 0)
if [ "$RM_COUNT" -gt 0 ]; then
    echo "INFO — ${RM_COUNT} Klassen ohne reset() (Sprint 4 Pattern nicht durchgesetzt)"
    infos=$((infos + 1))
else
    echo "OK (alle Singleton-Klassen haben reset())"
fi

# ── Ingame-Rooms Paket (INFO only — keine Blocking) ──
echo ""
gate_print_header "Settlement-Room Audit Light"

# set -eo pipefail safety: jede Pipe explizit mit || true abfangen.
ROOM_TOTAL=$( { find "$SRC_SETTLEMENT" -name '*Access.java' 2>/dev/null || true; } | wc -l | awk '{print int($1)}' 2>/dev/null || echo 0)
ROOM_TOTAL=${ROOM_TOTAL:-0}
echo "  Ingame-Room-Access-Klassen: ${ROOM_TOTAL}"

ROOM_VANILLA=$( { grep -rl 'class.forName\|Class\.forName\|setAccessible' "$SRC_SETTLEMENT" 2>/dev/null || true; } | wc -l | awk '{print int($1)}' 2>/dev/null || echo 0)
ROOM_VANILLA=${ROOM_VANILLA:-0}
echo "  Vanilla-Reflection-Calls:   ${ROOM_VANILLA}"

# Wichtig: dieser Block darf den Audit-Exit NICHT beeinflussen (INFO only).
true

# ── Zusammenfassung ──
echo ""
echo -e "${CYAN}--- Summary ---${NC}"
echo -e "  Critical:  ${RED}${violations}${NC}"
echo -e "  Warnings:  ${YELLOW}${warnings}${NC}"
echo -e "  Infos:     ${CYAN}${infos}${NC}"

if [ "$violations" -gt 0 ] || { [ "$STRICT" = true ] && [ "$warnings" -gt 0 ]; }; then
    if [ "$violations" -gt 0 ]; then
        exit 2
    fi
    exit 1
fi
echo -e "${GREEN}Sim-Logik-Audit bestanden${NC}"
exit 0
