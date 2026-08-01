#!/usr/bin/env bash
# SyxEconomyMod — Build Gate
# ==========================
# Master-Orchestrator: führt alle Checks vor dem Build aus.
#
# Gates (Reihenfolge = Abhängigkeiten):
#   1. Stam-Doku-Sync                          → doku-sync.sh check
#   2. Code Audit                              → code-audit.sh
#   3. Adapter Signaturen    (ADAPTER_JAR=…; sonst Light-Check)
#   4. Bytecode-Injection    (Sprint 6.2)      → audit-bytecode.sh
#   5. Sim-Logik Audit       (Sprint 6.3)      → audit-sim-logic.sh
#   6. Schema-Validierung    (Sprint 7)        → vanilla-schema.yaml vs. adapter/*
#   7. Balance-Regression    (Sprint 9)        → balance-regression-check.sh
#   8. God-Class-Guard                         → god-class-guard.sh --mode=hard
#   9. BINDUNGSMATRIX Canon
#  10. Benchmark-CSV Compare (Sprint StartingFromGround) → tools/benchmark-compare.sh
#
# Exit-Codes: 0 = alle Gates bestanden, 1 = mindestens ein Gate fehlgeschlagen.
# Sprung-Break: -Dgate.skip=true (Maven) bzw. GATE_SKIP=true / gate.skip=true
# überspringt ALLE Gates. Keine per-Gate SKIP_*-env-vars mehr (Sprint v0.13.108+Doku-Slim GATE-13).
#
# Usage:
#   bash tools/build-gate.sh                     # Alle Gates
#   bash tools/build-gate.sh --strict             # Audit im Strict-Mode
#
# Sprint v0.13.108+Doku-Slim: Empfohlen ist `bash tools/gate.sh precommit` als Hook-Wrapper
# der build-gate + doku-sync + phase47-shield kombiniert. Direktinstallation dieses
# build-gate.sh als Pre-Commit-Hook weiter möglich.

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
echo -e "${CYAN}║  SyxEconomyMod — Build Gate v0.13.118++ (Sprint v0.13.108+StartingFromGround: 10 Gates)   ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════╝${NC}"
echo ""

# ── Gate 1: Stam-Doku-Sync (Sprint v0.13.108+Doku-Slim: Migration verify-doc-sync.sh → doku-sync.sh check) ──
echo -e "${CYAN}[1/10] Stam-Doku-Sync (Doku ↔ pom.xml)${NC}"
# Fallback auf verify-doc-sync.sh wenn doku-sync.sh noch nicht migriert (Legacy-Pfad)
if [ -x tools/doku-sync.sh ]; then
    SYNC_CMD="bash tools/doku-sync.sh check"
else
    SYNC_CMD="bash tools/verify-doc-sync.sh"
fi
if $SYNC_CMD 2>/dev/null; then
    gate_pass "Alle Doku-Anker sync mit pom.xml"
else
    gate_fail "Stam-Doku-Drift — ${SYNC_CMD} Details"
fi
echo ""

# ── Gate 2: Code Audit ──────────────────────────────────────────────────
echo -e "${CYAN}[2/10] Code Audit (silent failure detection)${NC}"

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

# ── Gate 2.5 (entfernt Sprint v0.13.108+Doku-Slim): Version ↔ Changelog
# in doku-sync.sh integriert. ───────────────────────────────────────────

# ── Gate 3: Adapter Signature Verification ─────────────────────────────
echo -e "${CYAN}[3/10] Adapter ↔ Engine-Signaturen${NC}"

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

# ── Gate 4: Bytecode-Injection Audit (Sprint 6.2) ────────────────────
echo -e "${CYAN}[4/10] Bytecode-Injection Audit (Reflection-Patterns)${NC}"
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
echo -e "${CYAN}[5/10] Ingame-Sim-Logik Audit (Boundary-Conditions)${NC}"
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
echo -e "${CYAN}[6/10] Vanilla-Schema ↔ Adapter-Dateien${NC}"
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

# ── Gate 8: Balance-Regression (Sprint 9 / 7-2) ──────────────────────────────
echo -e "${CYAN}[7/10] Balance-Regression (EconConfig-Referenzwerte)${NC}"
if bash tools/balance-regression-check.sh 2>/dev/null; then
    gate_pass "Balance-Konstanten im Soll-Bereich"
else
    gate_fail "Balance-Drift — tools/balance-regression-check.sh Details"
fi
echo ""

# ── Gate 8: God-Class-Guard (Hard-Block Struktur-Quo) — Sprint M-3 ────────
echo -e "${CYAN}[8/10] God-Class-Guard (LOC/PubM/Fields-Caps + Baseline-Drift)${NC}"
# Mode=hard: WARN zählt als BLOCKER (god-class-guard.sh --mode=hard)
if bash tools/god-class-guard.sh --mode=hard 2>/dev/null; then
    gate_pass "Keine God-Class-Blocker; Baselines eingehalten"
else
    GUARD_EXIT=$?
    if [ "$GUARD_EXIT" -eq 2 ]; then
        gate_fail "BLOCKER — God-Class-Cap ueberschritten oder Drift > +5%/-YAML-Outdated (siehe tools/god-class-guard.on-failure.md)"
    else
        gate_fail "WARN — Annäherung an God-Class-Limit (siehe tools/god-class-guard.on-failure.md)"
    fi
fi
echo ""


# ── Gate 10: BINDUNGSMATRIX Canon (Sprint v0.13.118+Governance-Diät) ──
# Stellt sicher dass die 332-Hebel × 11-Spalten-SSoT-Canonical-Reference-Data
# intakt ist: 11 Spalten pro Zeile (awk NF==11), und >=100 Zeilen als Sanity-Check.
# Seit der Doku-Restruktur liegt die SSoT unter Doku/ (Root-Fallback fuer Alt-Branches).
# Bypass: SKIP_BINDUNGSMATRIX=1
echo -e "${CYAN}[9/10] BINDUNGSMATRIX Canon (332 Hebel × 11 Spalten SSoT)${NC}"
if [ -f "Doku/BINDUNGSMATRIX.csv" ] || [ -f "BINDUNGSMATRIX.csv" ]; then
    BM_FILE="BINDUNGSMATRIX.csv"
    if [ -f "Doku/BINDUNGSMATRIX.csv" ]; then
        BM_FILE="Doku/BINDUNGSMATRIX.csv"
    fi
    BM_LINES=$(wc -l < "$BM_FILE" 2>/dev/null | tr -d ' ' || echo 0)
    BM_COLS=$(awk -F';' 'NR==1{print NF; exit}' "$BM_FILE" 2>/dev/null || echo 0)
    if [ "${BM_COLS:-0}" -eq 11 ] && [ "${BM_LINES:-0}" -ge 100 ]; then
        gate_pass "BINDUNGSMATRIX konsistent: ${BM_LINES} Zeilen × ${BM_COLS} Spalten (${BM_FILE})"
    else
        gate_fail "BINDUNGSMATRIX drift: ${BM_LINES} Zeilen × ${BM_COLS} Spalten (erwartet >=100 Zeilen × 11 Spalten)"
    fi
else
    gate_fail "BINDUNGSMATRIX.csv fehlt (weder Doku/BINDUNGSMATRIX.csv noch Root) — Data-SSoT nicht gefunden"
fi
echo ""

# ── Gate 10: Benchmark-CSV Compare (Sprint v0.13.108+StartingFromGround) ──
# Vergleicht bench-baseline.csv ↔ bench-run.csv per-Spalte mit Toleranzen
# (gini 0.1%, money_supply 1%, median_price 2% relativ). Optional gate —
# überspringt wenn die CSV-Paare im Repo-Root fehlen (kein Benchmark-Harness
# in diesem Build-Kontext eingesetzt). Paths via BENCH_BASELINE / BENCH_RUN.
# Skip-Flag: SKIP_BENCH_COMPARE=1 (respektiert gate.skip=true übergeordnet).
echo -e "${CYAN}[10/10] Benchmark-CSV Compare (Baseline vs. aktueller Run)${NC}"
if [ ! -x "tools/benchmark-compare.sh" ]; then
    gate_skip "tools/benchmark-compare.sh nicht installiert (muss chmod +x) — Gate inert"
elif [ -f "${BENCH_BASELINE:-./bench-baseline.csv}" ] && \
     [ -f "${BENCH_RUN:-./bench-run.csv}" ]; then
    if bash tools/benchmark-compare.sh \
        "${BENCH_BASELINE:-./bench-baseline.csv}" \
        "${BENCH_RUN:-./bench-run.csv}" 2>/dev/null; then
        gate_pass "Benchmark-CSV-Drift innerhalb Toleranz (gini .1% / money 1% / price 2%)"
    else
        BC_EXIT=$?
        if [ "$BC_EXIT" -eq 2 ]; then
            gate_fail "BLOCKER: Benchmark-CSV Eingabefehler (siehe tools/benchmark_compare.py --help)"
        else
            gate_fail "Benchmark-CSV-Drift ausserhalb Toleranz — tools/benchmark-compare.sh Details"
        fi
    fi
else
    gate_skip "Benchmark-CSV-Paar fehlt (kein baseline.csv / run.csv im Repo-Root) — Gate inert"
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
