#!/usr/bin/env bash
# SyxEconomyMod — Meta-Tests für tools/snapshot-stam-version.sh
# =============================================================
# Hält die Snapshot-Logik federnd für spätere Refactors. Drei
# Asserts auf das Sub-Tool-Interface:
#   1. capture legt Snapshot-Datei an + exit 0
#   2. check ohne Drift exit 0
#   3. check mit Drift (pom künstlich gebumpt) exit 1
#   4. broken pom (kein <version>-Tag) → exit 2 (Round-4 Hardening)
#   5. reset entfernt Snapshot exit 0
#
# Struktur parallel zu tools/tests/god-class-guard/run_meta_tests.sh:
#   set -e + trap-basierte Cleanup-Pflicht, damit der Test auch bei
#   Mid-Test-Crash die Snapshot-Datei und pom.xml wieder in den
#   Pre-Test-Zustand zurückrollt.
#
# Aufruf: bash tools/tests/snapshot-stam-version-test.sh
# Exit: 0 bei allen Asserts OK, 1 sonst.

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SNAPSHOT="$REPO_ROOT/.git/hooks/.stam-version-snapshot"
POM="$REPO_ROOT/pom.xml"
SNAP="$REPO_ROOT/tools/snapshot-stam-version.sh"

PASS=0
FAIL=0

assert_exit() {
    local label="$1" expected="$2" actual="$3"
    if [ "$actual" = "$expected" ]; then
        echo -e "  ${GREEN}✓${NC}  $label  (exit=${actual})"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}✗${NC}  $label  (expected=${expected}, actual=${actual})"
        FAIL=$((FAIL + 1))
    fi
}

assert_file_exists() {
    local label="$1" path="$2"
    if [ -f "$path" ]; then
        echo -e "  ${GREEN}✓${NC}  $label  ($path)"
        PASS=$((PASS + 1))
    else
        echo -e "  ${RED}✗${NC}  $label  ($path missing)"
        FAIL=$((FAIL + 1))
    fi
}

echo "─────────────────────────────────────────────────────────────"
echo -e "${CYAN}SyxEconomyMod — snapshot-stam-version tests${NC}"
echo "─────────────────────────────────────────────────────────────"
echo ""

# Cleanup-Pflicht: Restore Snapshot und pom.xml bei jedem Test-Exit
# (success oder fail), damit der Test sich selbst aufräumt.
cp "$POM" /tmp/pom-baseline-backup.xml
trap 'cp /tmp/pom-baseline-backup.xml "$POM" 2>/dev/null; rm -f "$SNAPSHOT"' EXIT

# Snapshot-File vor Test wegräumen für saubere Assertion
rm -f "$SNAPSHOT"

# ── Test 1: capture legt Snapshot-Datei an, exit 0 ────────────
echo "[T1] capture erzeugt Snapshot-Datei"
set +e
bash "$SNAP" capture > /dev/null 2>&1
exit_capture=$?
set -e
assert_file_exists "snapshot file created" "$SNAPSHOT"
assert_exit "capture exits 0" "0" "$exit_capture"

# ── Test 2: check ohne Drift → exit 0 ──────────────────────────
echo "[T2] check bei aligned pom"
set +e
bash "$SNAP" check > /dev/null 2>&1
exit_check_aligned=$?
set -e
assert_exit "check aligned exits 0" "0" "$exit_check_aligned"

# ── Test 3: check mit Phantom-Bump (pom doctored) → exit 1 ─────
echo "[T3] check bei phantom-bump"
sed -i 's|<version>0\.13\.[0-9]*|<version>0.13.999-DRIFT-TEST|' "$POM"
set +e
bash "$SNAP" check > /dev/null 2>&1
exit_check_bumped=$?
set -e
assert_exit "check phantom-bump exits 1" "1" "$exit_check_bumped"
# pom restore für nächsten Test
cp /tmp/pom-baseline-backup.xml "$POM"

# ── Test 4a: broken pom (kein <version>-Tag) → exit 2 ─────────
echo "[T4a] capture bei kaputter pom (kein <version>-Tag)"
cp "$POM" /tmp/pom-baseline-backup-broken.xml
echo '<?xml version="1.0"?><project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>y</artifactId><!-- KEIN version-Tag --><packaging>jar</packaging></project>' > "$POM"
set +e
bash "$SNAP" capture > /dev/null 2>&1
exit_broken=$?
set -e
assert_exit "capture broken-pom (kein <version>) exits 2" "2" "$exit_broken"
cp /tmp/pom-baseline-backup-broken.xml "$POM"

# ── Test 4b: broken pom (<version> nur im Kommentar) → exit 2 ────
# Round-4-Reviewer-Hardening: ein <version> im XML-Kommentar darf nicht
# als gültige Version gewertet werden — grep-m1 matcht zwar die Zeile,
# aber downstream regex findet kein MAJOR.MINOR.PATCH. Früher exit 1
# via pipefail, jetzt expliziter empty-check auf final-normalized output.
echo "[T4b] capture bei kaputter pom (<version> nur im Kommentar)"
cp "$POM" /tmp/pom-baseline-backup-comment.xml
echo '<?xml version="1.0"?><project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>y</artifactId><!-- hat <version> nur im Kommentar, kein gültiger Tag --><packaging>jar</packaging></project>' > "$POM"
set +e
bash "$SNAP" capture > /dev/null 2>&1
exit_comment=$?
set -e
assert_exit "capture broken-pom (comment-trap) exits 2" "2" "$exit_comment"
cp /tmp/pom-baseline-backup-comment.xml "$POM"

# ── Test 5: reset löscht Snapshot, exit 0 ─────────────────────
echo "[T5] reset entfernt Snapshot"
# Erst nochmal capture für saubere Voraussetzung
set +e
bash "$SNAP" capture > /dev/null 2>&1
set -e
assert_file_exists "pre-reset: snapshot exists" "$SNAPSHOT"
set +e
bash "$SNAP" reset > /dev/null 2>&1
exit_reset=$?
set -e
assert_exit "reset exits 0" "0" "$exit_reset"
if [ ! -f "$SNAPSHOT" ]; then
    echo -e "  ${GREEN}✓${NC}  post-reset: snapshot removed"
    PASS=$((PASS + 1))
else
    echo -e "  ${RED}✗${NC}  post-reset: snapshot still present"
    FAIL=$((FAIL + 1))
fi

# ── Zusammenfassung ───────────────────────────────────────────
echo ""
echo "─────────────────────────────────────────────────────────────"
echo -e "  ${GREEN}Bestanden: ${PASS}${NC}  ${RED}Fehlgeschlagen: ${FAIL}${NC}"
echo "─────────────────────────────────────────────────────────────"
[ "$FAIL" -eq 0 ]
