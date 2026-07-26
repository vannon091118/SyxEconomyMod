#!/usr/bin/env bash
# SyxEconomyMod — Gate Report Library
# ====================================
# Shared helpers for tool/audit scripts: ANSI colors, gate counters,
# working-tree check, pom-version-extract. Eliminates the color/exit-code
# drift between verify-doc-sync.sh, code-audit.sh, audit-bytecode.sh, etc.
#
# Funkionen:
#   gate_print_header TITLE
#   gate_pass|gate_fail|gate_skip|gate_warn "message"
#   gate_summary [exit_code]
#   util_extract_pom_version [pom_path] (default: pom.xml)
#   util_check_working_tree  (return 0 clean, 1 dirty)
#   util_count_pattern_in_dir PATTERN DIR [exclude]
#
# Konvention (Sprint 5 Tooling-Konsolidierung):
#   Diese Bibliothek darf kein 'set -e'/'set -u' setzen, weil sie in
#   Callern mit unterschiedlichen Shell-Modi gesourced wird.

# Farben
export RED='\033[0;31m'
export GREEN='\033[0;32m'
export YELLOW='\033[1;33m'
export CYAN='\033[0;36m'
export NC='\033[0m'

# Globale Zähler (von gate_summary konsumiert)
_GATE_PASSED=0
_GATE_FAILED=0
_GATE_SKIPPED=0
_GATE_WARNINGS=0

# ── Gate Reporting ─────────────────────────────────────────────────────
gate_print_header() {
    local title="$1"
    echo -e "${CYAN}=== ${title} ===${NC}"
}

gate_pass() {
    echo -e "  ${GREEN}PASS${NC} — $1"
    _GATE_PASSED=$((_GATE_PASSED + 1))
}

gate_fail() {
    echo -e "  ${RED}FAIL${NC} — $1"
    _GATE_FAILED=$((_GATE_FAILED + 1))
}

gate_skip() {
    echo -e "  ${YELLOW}SKIP${NC} — $1"
    _GATE_SKIPPED=$((_GATE_SKIPPED + 1))
}

gate_warn() {
    echo -e "  ${YELLOW}WARN${NC} — $1"
    _GATE_WARNINGS=$((_GATE_WARNINGS + 1))
}

# Druckt eine Zusammenfassung. Optionaler Exit-Code (default: 1 wenn Failed > 0).
gate_summary() {
    local exit_code="${1:--1}"
    echo ""
    echo -e "${CYAN}--- Summary ---${NC}"
    echo -e "  Passed:    ${GREEN}${_GATE_PASSED}${NC}"
    echo -e "  Failed:    ${RED}${_GATE_FAILED}${NC}"
    echo -e "  Skipped:   ${YELLOW}${_GATE_SKIPPED}${NC}"
    echo -e "  Warnings:  ${YELLOW}${_GATE_WARNINGS}${NC}"
    if [[ "$exit_code" == "-1" ]]; then
        if [[ "$_GATE_FAILED" -gt 0 ]]; then
            return 1
        fi
        return 0
    fi
    return "$exit_code"
}

# ── util_extract_pom_version: gibt die <version>-Zeile (ohne v) zurueck ─
util_extract_pom_version() {
    local pom="${1:-pom.xml}"
    if [[ ! -f "$pom" ]]; then
        echo ""
        return 1
    fi
    grep -m1 '<version>' "$pom" | sed 's/.*<version>\([^<]*\)<\/version>.*/\1/'
}

# ── util_check_working_tree: 0 = clean, 1 = dirty ───────────────────────
util_check_working_tree() {
    if ! command -v git >/dev/null 2>&1; then return 0; fi
    if ! git rev-parse --git-dir >/dev/null 2>&1; then return 0; fi
    if git diff --quiet HEAD 2>/dev/null; then
        return 0  # clean
    else
        return 1  # dirty
    fi
}

# ── util_count_pattern_in_dir: zaehlt Treffer fuer ein Regex-Pattern ────
# Doppelt genutzt von audit-bytecode.sh + audit-sim-logic.sh + code-audit.sh.
util_count_pattern_in_dir() {
    local pattern="$1"
    local dir="$2"
    local exclude="${3:-}"
    if [[ -n "$exclude" ]]; then
        grep -rEn "$pattern" "$dir" 2>/dev/null | grep -v "$exclude" | wc -l | awk '{print $1}'
    else
        grep -rEn "$pattern" "$dir" 2>/dev/null | wc -l | awk '{print $1}'
    fi
}
