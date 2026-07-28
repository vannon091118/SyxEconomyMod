#!/usr/bin/env bash
# SyxEconomyMod — Version-Changelog Consistency Gate
# ===================================================
# Prüft vor jedem Commit, ob die pom.xml-Version (project.version)
# mit dem ersten Eintrag in mod.changelog übereinstimmt.
#
# Aktualisiert automatisch mod.version.history aus git tags,
# damit _Info.txt immer die letzten 5 Tags enthält.
#
# Installation:
#   cp tools/verify-version-consistency.sh .git/hooks/pre-commit
#   chmod +x .git/hooks/pre-commit
#
# Oder als manueller Check:
#   bash tools/verify-version-consistency.sh

set -euo pipefail

POM="pom.xml"
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

if [ ! -f "$POM" ]; then
    echo -e "${RED}✗ $POM nicht gefunden — bist du im Projekt-Root?${NC}"
    exit 2
fi

# ── 1. mod.version.history aus git tags updaten ─────────────────────────
if command -v git &>/dev/null && git rev-parse --git-dir &>/dev/null 2>&1; then
    TAGS=$(git tag --sort=-creatordate 2>/dev/null | head -5 | paste -sd ';' - || true)
    if [ -n "$TAGS" ]; then
        # Update mod.version.history in pom.xml (in-place)
        if grep -q '<mod.version.history>' "$POM"; then
            sed -i "s|<mod.version.history>.*</mod.version.history>|<mod.version.history>${TAGS}</mod.version.history>|" "$POM"
            echo -e "${GREEN}✓ mod.version.history aktualisiert: ${TAGS}${NC}"
        fi
    fi
fi

# ── 2. Version aus pom.xml extrahieren ──────────────────────────────────
POM_VERSION=$(grep -m1 '<version>' "$POM" | sed 's/.*<version>\(.*\)<\/version>.*/\1/')

if [ -z "$POM_VERSION" ]; then
    echo -e "${RED}✗ Konnte <version> nicht aus $POM extrahieren${NC}"
    exit 2
fi

# ── 3. Ersten Changelog-Eintrag extrahieren ────────────────────────────
# Format: "v0.1.0 (Phase 4) - ..."  oder  "v0.0.9 - ..."
CHANGELOG_FIRST=$(grep -m1 '<mod.changelog>' "$POM" | sed 's/.*<mod.changelog>//' | sed 's/;.*//' | grep -oP 'v[\d]+\.[\d]+\.[\d]+' | head -1)

if [ -z "$CHANGELOG_FIRST" ]; then
    echo -e "${YELLOW}⚠ Konnte keinen Versionseintrag in mod.changelog finden — überspringe Check${NC}"
    exit 0
fi

# ── 4. Vergleich ───────────────────────────────────────────────────────
CHANGELOG_NUM=$(echo "$CHANGELOG_FIRST" | sed 's/^v//')

if [ "$CHANGELOG_NUM" != "$POM_VERSION" ]; then
    echo -e "${RED}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║  VERSION MISMATCH                                           ║${NC}"
    echo -e "${RED}╠══════════════════════════════════════════════════════════════╣${NC}"
    echo -e "${RED}║  pom.xml version:    ${POM_VERSION}                                  ${NC}"
    echo -e "${RED}║  mod.changelog:      ${CHANGELOG_FIRST}                                  ${NC}"
    echo -e "${RED}╠══════════════════════════════════════════════════════════════╣${NC}"
    echo -e "${RED}║  Fix: bump <version> in pom.xml ODER korrigiere den         ${NC}"
    echo -e "${RED}║  ersten Eintrag in <mod.changelog>.                         ${NC}"
    echo -e "${RED}╚══════════════════════════════════════════════════════════════╝${NC}"
    exit 1
fi

echo -e "${GREEN}✓ pom.xml-Version (${POM_VERSION}) konsistent mit erstem CHANGELOG-Heading (${CHANGELOG_FIRST})${NC}"
echo "  (Sync mit README/ARCHITECTURE/ROADMAP/GLOSSARY wird separat in verify-doc-sync.sh geprüft)"

# ── 5+6. _Info.txt Sync (Template ↔ pom.xml + Deployed freshness) ──
# Geteilte Logik in tools/lib/_info-txt-sync.sh:
#   - sync_info_txt_template_report  prueft placeholder <-> property
#   - sync_info_txt_deployed_report prueft deployed-Copy freshness
SCRIPT_DIR_VVC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/_info-txt-sync.sh
. "${SCRIPT_DIR_VVC}/lib/_info-txt-sync.sh"

# 5: Template ↔ pom.xml-Properties (hard fail on drift)
if ! sync_info_txt_template_report "strict" "$POM"; then
    exit 1
fi

# 6: Deployed _Info.txt freshness (gitignored → warn only).
#    Lib-Funktion returnt 1 bei stale — '|| true' maskiert, damit 'set -e'
#    den Warn-Only-Intent nicht in einen Hard-Fail verwandelt.
sync_info_txt_deployed_report "$POM_VERSION" || true

exit 0
