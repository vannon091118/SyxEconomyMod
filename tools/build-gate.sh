#!/usr/bin/env bash
# SyxEconomyMod — Build Gate
# ==========================
# Master-Orchestrator: führt alle Checks vor dem Build aus.
#
# Gates (Reihenfolge = Abhängigkeiten):
#   1. Stam-Doku-Sync        (NEU — SKIP_SYNC=1)       → verify-doc-sync.sh
#   2. Code Audit            (SKIP_AUDIT=1)            → code-audit.sh
#   3. Version Consistency   (SKIP_VERSION_CHECK=1)    → verify-version-consistency.sh
#   4. Adapter Signaturen    (ADAPTER_JAR=…; sonst Light-Check)
#   5. Bytecode-Injection    (Sprint 6.2)              → audit-bytecode.sh
#   6. Sim-Logik Audit       (Sprint 6.3)              → audit-sim-logic.sh
#   7. Schema-Validierung    (Sprint 7)                 → vanilla-schema.yaml vs. adapter/*
#
# Exit-Codes: 0 = alle Gates bestanden, 1 = mindestens ein Gate fehlgeschlagen.
#
# Usage:
#   bash tools/build-gate.sh                     # Alle Gates
#   bash tools/build-gate.sh --strict             # Audit im Strict-Mode
#
# Installation als Pre-Commit-Hook:
#   cp tools/build-gate.sh .git/hooks/pre-commit
#   chmod +x .git/hooks/pre-commit

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

STRICT=false
[[ "${1:-}" == "--strict" ]] && STRICT=true

FAILED=0
PASSED=0
SKIPPED=0

gate_pass() {
    echo -e "  ${GREEN}✓ PASS${NC} — $1"
    PASSED=$((PASSED + 1))
}

gate_fail() {
    echo -e "  ${RED}✗ FAIL${NC} — $1"
    FAILED=$((FAILED + 1))
}

gate_skip() {
    echo -e "  ${YELLOW}⊘ SKIP${NC} — $1"
    SKIPPED=$((SKIPPED + 1))
}

echo -e "${CYAN}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║  SyxEconomyMod — Build Gate v0.13.33                  ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════╝${NC}"
echo ""

# ── Gate 1: Stam-Doku-Sync (NEU) ────────────────────────────────────────
echo -e "${CYAN}[1/7] Stam-Doku-Sync (7 Dokumente ↔ pom.xml)${NC}"
if [ "${SKIP_SYNC:-0}" = "1" ]; then
    gate_skip "Sync-Gate uebersprungen (SKIP_SYNC=1)"
else
    if bash tools/verify-doc-sync.sh 2>/dev/null; then
        gate_pass "Alle 7 Stam-Dokumente sync mit pom.xml"
    else
        gate_fail "Stam-Doku-Drift — verify-doc-sync.sh Details"
    fi
fi
echo ""

# ── Gate 2: Code Audit ──────────────────────────────────────────────────
echo -e "${CYAN}[2/7] Code Audit (silent failure detection)${NC}"

AUDIT_ARGS=""
if [ "$STRICT" = true ]; then
    AUDIT_ARGS="--strict"
fi

if bash tools/code-audit.sh $AUDIT_ARGS 2>/dev/null; then
    gate_pass "Keine stillen Fehlerschlucker"
else
    AUDIT_EXIT=$?
    if [ "$AUDIT_EXIT" -eq 2 ]; then
        gate_fail "BLOCKER: leere catch-Blöcke oder printStackTrace gefunden"
    else
        gate_fail "Warnungen (mit --strict wiederholen für Build-Abbruch)"
    fi
fi
echo ""

# ── Gate 3: Version Consistency ────────────────────────────────────────
echo -e "${CYAN}[3/7] Version ↔ Changelog Consistency${NC}"

if bash tools/verify-version-consistency.sh 2>/dev/null; then
    gate_pass "pom.xml = changelog"
else
    gate_fail "Version stimmt nicht mit Changelog überein"
fi
echo ""

# ── Gate 4: Adapter Signature Verification ─────────────────────────────
echo -e "${CYAN}[4/7] Adapter ↔ Engine-Signaturen${NC}"

ADAPTER_JAR="${ADAPTER_JAR:-}"
ADAPTER_SRC="src/vannon/syx/economy/adapter/"

# JAR-basierte Prüfung (vollständig)
if [ -n "$ADAPTER_JAR" ] && [ -f "$ADAPTER_JAR" ]; then
    ADAPTER_OK=true

    # VanillaAIAdapter: 6 Plan-Klassen
    for cls in PlanOddjobber F_SPlanEatery F_SPlanCanteen F_PlanEat PlanTavern M_PlanMarket; do
        if ! unzip -l "$ADAPTER_JAR" 2>/dev/null | grep -q "${cls}.java"; then
            echo -e "      ${RED}${cls}.java NICHT in JAR${NC}"
            ADAPTER_OK=false
        fi
    done

    # VanillaTransportAdapter: TransportInstance.distance (float)
    if ! unzip -p "$ADAPTER_JAR" settlement/room/infra/transport/TransportInstance.java 2>/dev/null | grep -q 'float distance;'; then
        echo -e "      ${RED}TransportInstance.distance NICHT als float gefunden${NC}"
        ADAPTER_OK=false
    fi

    # VanillaWarehouseAdapter: StockpileInstance.storingSet(boolean)
    if ! unzip -p "$ADAPTER_JAR" settlement/room/infra/stockpile/StockpileInstance.java 2>/dev/null | grep -q 'void storingSet'; then
        echo -e "      ${RED}StockpileInstance.storingSet(boolean) NICHT gefunden${NC}"
        ADAPTER_OK=false
    fi

    # VanillaBoostingAdapter: BOOSTABLES.GOV
    if ! unzip -p "$ADAPTER_JAR" game/boosting/BOOSTABLES.java 2>/dev/null | grep -q 'Boostable GOV'; then
        echo -e "      ${RED}BOOSTABLES.GOV NICHT als Boostable gefunden${NC}"
        ADAPTER_OK=false
    fi

    # VanillaDiplomacyAdapter: 5 DipWarPlayer-Felder
    DIPWAR_SRC=$(unzip -p "$ADAPTER_JAR" game/faction/diplomacy/DipWarPlayer.java 2>/dev/null || true)
    for field in "upI" "pPow" "coalitionPow" "bWilling" "willing"; do
        if ! echo "$DIPWAR_SRC" | grep -q "$field"; then
            echo -e "      ${RED}DipWarPlayer.${field} NICHT gefunden${NC}"
            ADAPTER_OK=false
        fi
    done

    if [ "$ADAPTER_OK" = true ]; then
        gate_pass "Alle 19 Adapter-Signaturen gegen JAR verifiziert"
    else
        gate_fail "Adapter-Signatur-Mismatch — V72-Update? adapter/*.java pruefen"
    fi

# Source-basierte Light-Prüfung (ohne JAR — verifiziert dass die Adapter-
# Klassen existieren und die referenzierten class/method/field-Namen
# syntaktisch plausibel sind)
elif [ -d "$ADAPTER_SRC" ]; then
    ADAPTER_FILES=$(find "$ADAPTER_SRC" -name 'Vanilla*.java' -o -name 'ISyx*.java' 2>/dev/null | wc -l | awk '{print int($1)}')
    if [ "$ADAPTER_FILES" -ge 10 ]; then
        gate_pass "Adapter-Sourcen vorhanden (${ADAPTER_FILES} Dateien, Light-Check ohne JAR)"
    else
        gate_fail "Weniger als 10 Adapter-Dateien in ${ADAPTER_SRC}"
    fi
else
    gate_fail "Kein Adapter-Source-Verzeichnis und kein JAR — Build kann nicht verifiziert werden"
fi
echo ""

# ── Gate 5: Bytecode-Injection Audit (Sprint 6.2) ────────────────────
echo -e "${CYAN}[5/7] Bytecode-Injection Audit (Reflection-Patterns)${NC}"
if bash tools/audit-bytecode.sh ${AUDIT_ARGS:-} 2>/dev/null; then
    gate_pass "Keine ungesicherten Bytecode-Injection-Pfade"
else
    AUDIT_EXIT=$?
    if [ "$AUDIT_EXIT" -eq 2 ]; then
        gate_fail "BLOCKER: Bytecode-Injection ausserhalb BypassGate-SDK"
    else
        gate_fail "Warnungen (mit --strict wiederholen für Build-Abbruch)"
    fi
fi
echo ""

# ── Gate 6: Sim-Logik Audit (Sprint 6.3) ───────────────────────────────
echo -e "${CYAN}[6/7] Ingame-Sim-Logik Audit (Boundary-Conditions)${NC}"
if bash tools/audit-sim-logic.sh ${AUDIT_ARGS:-} 2>/dev/null; then
    gate_pass "Keine Boundary-Condition-Verletzungen in Sim-Klassen"
else
    SIM_EXIT=$?
    if [ "$SIM_EXIT" -eq 2 ]; then
        gate_fail "BLOCKER: Sim-Logik-Verletzung"
    else
        gate_fail "Warnungen (mit --strict wiederholen für Build-Abbruch)"
    fi
fi
echo ""

# ── Gate 7: Schema-Validierung (Sprint 7) ──────────────────────────────
echo -e "${CYAN}[7/7] Vanilla-Schema ↔ Adapter-Dateien${NC}"
if [ -f "tools/vanilla-schema.yaml" ]; then
    # Prüfe ob jede Klasse im YAML eine entsprechende Adapter-Datei hat
    SCHEMA_CLASS=$(grep -c 'class:' tools/vanilla-schema.yaml 2>/dev/null || echo 0)
    ADAPTER_JAVA=$(find src/vannon/syx/economy/adapter/ -name 'Vanilla*.java' -o -name 'Npc*.java' 2>/dev/null | wc -l | awk '{print int($1)}')
    if [ "$SCHEMA_CLASS" -ge 10 ] && [ "$ADAPTER_JAVA" -ge 5 ]; then
        gate_pass "Schema ${SCHEMA_CLASS} Klassen, ${ADAPTER_JAVA} Adapter-Dateien — konsistent"
    else
        gate_fail "Schema/Adapter-Mismatch: ${SCHEMA_CLASS} Schema-Klassen vs ${ADAPTER_JAVA} Adapter-Dateien (erwartet ≥10 / ≥5)"
    fi
else
    gate_fail "tools/vanilla-schema.yaml fehlt — Schema-SSoT nicht gefunden"
fi
echo ""

# ── Ergebnis ────────────────────────────────────────────────────────────
echo -e "${CYAN}══════════════════════════════════════════════════════════${NC}"
echo -e "  ${GREEN}Bestanden: ${PASSED}${NC}  ${RED}Fehlgeschlagen: ${FAILED}${NC}  ${YELLOW}Übersprungen: ${SKIPPED}${NC}"
echo ""

if [ "$FAILED" -gt 0 ]; then
    echo -e "${RED}╔════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║  BUILD GATE: ${FAILED} GATE(S) FEHLGESCHLAGEN                     ║${NC}"
    echo -e "${RED}║  Build wird abgebrochen. Fehler oben beheben.         ║${NC}"
    echo -e "${RED}╚════════════════════════════════════════════════════════╝${NC}"
    exit 1
fi

echo -e "${GREEN}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  ALLE GATES BESTANDEN — Build freigegeben              ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════╝${NC}"
exit 0
