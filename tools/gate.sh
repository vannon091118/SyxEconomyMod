#!/usr/bin/env bash
# ╔════════════════════════════════════════════════════════════════════════╗
# ║ SyxEconomyMod — Universal Gate (Operator Handbuch)                     ║
# ║ Version: Sprint v0.13.121+ConceptGlossary                              ║
# ╚════════════════════════════════════════════════════════════════════════╝
# Ein Skript fuer ALLE Pre-Commit/Pre-Build/Release-Checks im Mod-Repo.
# Konsolidiert seit Sprint v0.13.108+Doku-Slim mehrere legacy-Skripte in
# EINEN Pflicht-Entry-Point — siehe Concept-Glossar „Thin-Wrapper-
# Deprecation-Pattern" fuer den Migrations-Pfad.
#
# MODI & BEISPIEL-AUFRUFE:
#   1. precommit ->  `bash tools/gate.sh precommit`
#      Sub-Phase-Suite: Phase-4.7-Shield + doku-sync check
#                       + _Info.txt ↔ pom.xml Hard-Block (User-Auftrag
#                       Sprint v0.13.131+InfoHardSync: '_info.txt muss
#                       IMMER OHNE AUSNAHME mit pom.xml uebereinstimmen').
#      Gott-Class-Guard war hier Sprint v0.13.131+ToolBoxSlim archiviert.
#      Zielgruppe: Pre-Commit-Hook, schneller lokaler Dev-Loop.
#   2. prebuild  ->  `bash tools/gate.sh prebuild`
#      Ruft build-gate.sh mit allen 11 CI-Gates auf (siehe Concept-Glossar
#      „Build-Gates (1–11)"). Zielgruppe: Maven validate / CI-Server.
#   3. release   ->  `bash tools/gate.sh release`
#      prebuild (build-gate.sh full 11-gate-suite) + bump-version-readiness.
#      Sprint v0.13.131+ToolBoxSlim+InfoHardSync: balance-regression-check.sh nach
#      docs/_archive/tools/ verschoben, dieser Sub-Block ist jetzt gate_skip
#      (siehe release-case unten).
#      Zielgruppe: pre-tag/push-Tag-Pipeline.
#
# AUSGANGS-SPEC (WAS PASSIERT WENN EIN GATE FAILT?):
#   Exit-Code 0  — Alle Checks bestanden. Pipeline laeuft weiter.
#   Exit-Code 1  — Mindestens ein Gate schlug fehl (Pipeline HARD-BLOCKED):
#                   * im precommit-Modus blockiert Git den Commit strikt;
#                   * im prebuild-Modus bricht Maven / CI den Build ab;
#                   * im release-Modus wird der Tag-Push verhindert.
#   Exit-Code 2  — Umgebungs- oder Aufruffehler (z. B. falsches Argument,
#                   fehlender python3, fehlende pom.xml).
#
# SKIP-MECHANISMUS (NOTFALL-BYPASS — DOKUMENTATIONSPFLICHT):
#   Wenn ein Gate fehlerhaft blockiert, kann es via env-var ausgesetzt
#   werden. JEDER Skip MUSS im Commit-Body dokumentiert sein:
#     Shell-env:  `GATE_SKIP=true bash tools/gate.sh precommit`
#     Maven:      `mvn clean install -Dgate.skip=true`
#                 (Property wird automatisch als env in die Bash-Ungebung geleitet).
#   Per-Gate-Skip mit `SKIP_<GATE_NAME>=1` ist VEREHRT — Sprint v0.13.108+
#           hat das im Zuge der Gate-Vereinheitlichung verworten.
#

# Sprint v0.13.108+Doku-Slim: set -eo pipefail (ohne -u).
# Punkt-Var-Namen vermeiden — keine "Bad substitution"-Risiken in altem bash.
set -eo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

MODE="${1:-prebuild}"

if [ "$MODE" != "precommit" ] && [ "$MODE" != "prebuild" ] && [ "$MODE" != "release" ]; then
    echo -e "${RED}FEHLER: usage: bash tools/gate.sh [precommit|prebuild|release]${NC}" >&2
    exit 2
fi

# Helper: gate-result-tracking
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

# run_gate: exit-Code VOR dem if-else einfangen (umgeht 'set -e'-propagation).
# set +e ist notwendig, weil set -eo pipefail sonst vor $? capture abbricht.
run_gate() {
    set +e
    "$@"
    local rc=$?
    set -e
    return "$rc"
}

# Skip via env-var GATE_SKIP (KEIN PUNKT im Namen, keine set -u Komplikation).
# -Dgate.skip=true in Maven wird automatisch als env-Var 'gate.skip' durchgereicht
# — beide Akzeptiert (Maven-Default UNEDR-Dot, Sys-admin Liebhaber).
SKIP_VAL="${GATE_SKIP:-${gate_skip:-false}}"
if [ "$SKIP_VAL" = "true" ]; then
    echo -e "${YELLOW}GATE_SKIP=true (oder gate.skip) — alle Gates werden uebersprungen.${NC}"
    exit 0
fi

echo -e "${CYAN}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║  SyxEconomyMod — gate.sh ($MODE) — Sprint v0.13.108+Doku-Slim ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""

case "$MODE" in
    precommit)
        echo -e "${CYAN}[1/3] Phase-4.7-Shield (IdentityHashMap/EngineSeams Regression)${NC}"
        if [ -x tools/phase47-shield.sh ]; then
            run_gate bash tools/phase47-shield.sh --mode=delta-only 2>/dev/null
            GATE_EXIT=$?
            if [ "$GATE_EXIT" -eq 0 ]; then
                gate_pass "Phase-4.7 Regressions OK"
            elif [ "$GATE_EXIT" -eq 2 ]; then
                gate_fail "BLOCKER — Phase-4.7 Regression erkannt"
            else
                gate_fail "WARN — Phase-4.7 Soft-FAIL (Exit $GATE_EXIT)"
            fi
        else
            gate_skip "tools/phase47-shield.sh fehlt"
        fi
        echo ""

        echo -e "${CYAN}[2/3] doku-sync check (Doku ↔ pom.xml Anker-Sync)${NC}"
        if [ -x tools/doku-sync.sh ]; then
            run_gate bash tools/doku-sync.sh check 2>/dev/null
            GATE_EXIT=$?
            if [ "$GATE_EXIT" -eq 0 ]; then
                gate_pass "Doku-Anker sync"
            else
                gate_fail "Doku-Drift (Exit $GATE_EXIT) — bash tools/doku-sync.sh fix"
            fi
        else
            gate_skip "tools/doku-sync.sh fehlt"
        fi
        echo ""

        # Sprint v0.13.131+InfoHardSync (User-Auftrag): _Info.txt deployed-Version
        # MUSS 'ohne Ausnahme' mit pom.xml <version> uebereinstimmen. Hard-Block.
        echo -e "${CYAN}[3/3] _Info.txt Sync (Deployed ↔ pom.xml — User-Hard-Invariant)${NC}"
        POM_VER=$(grep -m1 '<version>' pom.xml 2>/dev/null | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "")
        DEPLOYED_INFO="${SYX_MODS_DIR:-$HOME/.local/share/songsofsyx}/mods/SyxEconomyMod/_Info.txt"
        if [ -z "$POM_VER" ]; then
            gate_fail "pom.xml <version> nicht extrahierbar — Drift-Check nicht moeglich"
        elif [ ! -f "$DEPLOYED_INFO" ]; then
            gate_fail "_Info.txt deployed fehlt ($DEPLOYED_INFO) — bitte 'mvn -Dgate.skip=true clean package install' ausfuehren"
        else
            DEPLOYED_VER=$(grep -m1 '^VERSION:' "$DEPLOYED_INFO" 2>/dev/null | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "")
            if [ -z "$DEPLOYED_VER" ]; then
                gate_fail "_Info.txt deployed hat kein VERSION-Feld — Maven-Resource-Filtering kaputt?"
            elif [ "$DEPLOYED_VER" != "$POM_VER" ]; then
                gate_fail "_Info.txt deployed=$DEPLOYED_VER != pom.xml=$POM_VER — DRIFT! 'mvn -Dgate.skip=true clean package install' regeneriert"
            else
                gate_pass "_Info.txt deployed (${DEPLOYED_VER}) matched pom.xml"
            fi
        fi
        # Sprint v0.13.131+ToolBoxSlim: God-Class-Guard seit diesem Sprint archiviert
        # (nach docs/_archive/tools/ verschoben, dort Path-Coupling kaputt — keine Re-Aktivierung geplant).
        gate_skip "God-Class-Guard archiviert [archived v0.13.131+ToolBoxSlim]"
        echo ""
        ;;

    prebuild)
        if [ -x tools/build-gate.sh ]; then
            run_gate bash tools/build-gate.sh 2>/dev/null
            GATE_EXIT=$?
            if [ "$GATE_EXIT" -eq 0 ]; then
                PASSED=1
            else
                FAILED=1
            fi
        else
            gate_skip "tools/build-gate.sh fehlt"
        fi
        echo ""
        ;;

    release)
        echo -e "${CYAN}Pre-Build Gates:${NC}"
        if [ -x tools/build-gate.sh ]; then
            run_gate bash tools/build-gate.sh 2>/dev/null
            GATE_EXIT=$?
            if [ "$GATE_EXIT" -eq 0 ]; then
                PASSED=$((PASSED + 1))
            else
                FAILED=$((FAILED + 1))
            fi
        else
            gate_skip "tools/build-gate.sh fehlt"
        fi
        echo ""

        echo -e "${CYAN}Balance-Regression (EconConfig-Referenzwerte):${NC}"
        # Sprint v0.13.131+ToolBoxSlim: balance-regression-check.sh nach docs/_archive/tools/
        # verschoben (gleicher Path-Coupling-Bug wie god-class-guard). Skip statt call.
        gate_skip "Balance-Regression-Check archiviert [archived v0.13.131+ToolBoxSlim]"
        echo ""

        echo -e "${CYAN}Bump-Version-Readiness:${NC}"
        if [ -x tools/bump-version.sh ]; then
            gate_pass "bump-version.sh verfuegbar (kein Auto-Bump mehr → explizit)"
        else
            gate_fail "tools/bump-version.sh fehlt"
        fi
        echo ""
        ;;
esac

# Ergebnis
echo -e "${CYAN}══════════════════════════════════════════════════════════${NC}"
echo -e "  ${GREEN}PASS: ${PASSED}${NC}  ${RED}FAIL: ${FAILED}${NC}  ${YELLOW}SKIP: ${SKIPPED}${NC}"
echo ""

if [ "$FAILED" -gt 0 ]; then
    echo -e "${RED}╔════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║  $MODE-Gate: mindestens ein Check FEHLGESCHLAGEN            ║${NC}"
    echo -e "${RED}╚════════════════════════════════════════════════════════╝${NC}"
    exit 1
fi

echo -e "${GREEN}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  $MODE-Gate: ALLE BESTANDEN                                 ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════╝${NC}"
exit 0
