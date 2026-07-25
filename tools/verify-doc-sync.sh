#!/usr/bin/env bash
# SyxEconomyMod — Stam-Doku-Sync Gate
# =====================================
# Stellt vor jedem `mvn compile` (validate-Phase) sicher, dass die
# 7 Stam-Dokumente dieselbe Versions-Information tragen wie `pom.xml`.
#
# Stam-Dokumente:
#   1. README.md                  (root)
#   2. CHANGELOG.md               (root) — Erstes `## vX.Y.Z` Heading
#   3. ARCHITECTURE.md            (root)
#   4. ROADMAP.md                 (root)
#   5. GLOSSARY.md                (root)
#   6. pom.xml                    (root) — Truth of Record
#   7. _Info.txt                  (root) — Maven-Filter-Template
#
# Exit-Codes:
#   0 - alle Stam-Dokumente sync
#   1 - Drift in mindestens einem Dokument
#   2 - Skript-Umgebungsfehler (kein pom.xml, etc.)
#
# Installations-Varianten:
#   - Als Maven-Phase: pom.xml → <phase>validate</phase> → <exec> bash tools/verify-doc-sync.sh
#   - Als Pre-Commit-Hook: cp tools/verify-doc-sync.sh .git/hooks/pre-commit
#   - Manuell: bash tools/verify-doc-sync.sh

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

POM="pom.xml"

# ── Preflight ────────────────────────────────────────────────────────
if [ ! -f "$POM" ]; then
    echo -e "${RED}FEHLER: $POM nicht gefunden — bist du im Projekt-Root?${NC}" >&2
    exit 2
fi

# ── 1. Truth aus pom.xml lesen ──────────────────────────────────────
POM_VERSION=$(grep -m1 '<version>' "$POM" | sed 's/.*<version>\([^<]*\)<\/version>.*/\1/' || true)

if [ -z "$POM_VERSION" ]; then
    echo -e "${RED}FEHLER: Konnte <version> nicht aus $POM extrahieren${NC}" >&2
    exit 2
fi

TRUTH="${POM_VERSION}"
TRUTH_V="v${POM_VERSION}"

# ── 2. Header ────────────────────────────────────────────────────────
echo -e "${CYAN}==============================================================${NC}"
echo -e "${CYAN}  SyxEconomyMod — Stam-Doku-Sync Gate                          ${NC}"
echo -e "${CYAN}==============================================================${NC}"
echo ""
echo -e "  Truth from pom.xml:  ${GREEN}${TRUTH}${NC}"
echo -e "  Suche in 7 Stam-Dokumenten..."
echo ""

FAILED=0
CHECKED=0

# ── Helper: einzelnes Dokument prüfen ────────────────────────────────
# Argumente:
#   $1 = Datei-Pfad
#   $2 = zu matchende Regex (extended grep)
#   $3 = Beschreibung der Sync-Strategie
# Returns:
#   0 wenn Match, 1 wenn nicht

check_doc() {
    local file="$1"
    local pattern="$2"
    local strategy_desc="$3"

    if [ ! -f "$file" ]; then
        printf '  %sFAIL%s  %-30s  Datei nicht gefunden\n' "$RED" "$NC" "$file"
        FAILED=1
        return 1
    fi

    local hit
    hit=$(grep -m1 -E "$pattern" "$file" || true)
    if [ -z "$hit" ]; then
        printf '  %sFAIL%s  %-30s  keiner der Sync-Marker gefunden (%s)\n' "$RED" "$NC" "$file" "$strategy_desc"
        FAILED=1
        return 1
    fi

    # Erste v?X.Y.Z aus dem Hit extrahieren
    local found
    found=$(echo "$hit" | grep -oE 'v?[0-9]+\.[0-9]+\.[0-9]+' | head -1 || true)
    if [ -z "$found" ]; then
        printf '  %sFAIL%s  %-30s  Sync-Marker gefunden, aber keine Version (%s)\n' "$RED" "$NC" "$file" "$strategy_desc"
        FAILED=1
        return 1
    fi

    # Akzeptiere form v0.13.2 und 0.13.2
    if [ "$found" = "$TRUTH_V" ] || [ "$found" = "$TRUTH" ]; then
        printf '  %sOK%s    %-30s  found=%s  (truth=%s)\n' "$GREEN" "$NC" "$file" "$found" "$TRUTH"
        CHECKED=$((CHECKED + 1))
        return 0
    fi
    printf '  %sFAIL%s  %-30s  found=%s  expected=%s\n' "$RED" "$NC" "$file" "$found" "$TRUTH"
    FAILED=1
    return 1
}

# ── 3. Pro Stam-Dokument prüfen ─────────────────────────────────────

# README.md: "**Version:** vX.Y.Z"
check_doc "README.md" \
    '(\*\*Version:\*\*|Version:)[[:space:]]*v?[0-9]+\.[0-9]+\.[0-9]+' \
    "**Version:** vX.Y.Z  ODER  Version: vX.Y.Z"

# CHANGELOG.md: erstes "## vX.Y.Z" Heading
check_doc "CHANGELOG.md" \
    '^##[[:space:]]+v[0-9]+\.[0-9]+\.[0-9]+' \
    "## vX.Y.Z als erstes Release-Heading"

# ARCHITECTURE.md: "> **Version:** vX.Y.Z"
check_doc "ARCHITECTURE.md" \
    '>\s*\*\*Version:\*\*[[:space:]]*v?[0-9]+\.[0-9]+\.[0-9]+' \
    "> **Version:** vX.Y.Z"

# ROADMAP.md: "> **Version:** vX.Y.Z"
check_doc "ROADMAP.md" \
    '>\s*\*\*Version:\*\*[[:space:]]*v?[0-9]+\.[0-9]+\.[0-9]+' \
    "> **Version:** vX.Y.Z"

# GLOSSARY.md: "> **Version:** vX.Y.Z" oder "Version: vX.Y.Z"
check_doc "GLOSSARY.md" \
    '>\s*\*\*Version:\*\*[[:space:]]*v?[0-9]+\.[0-9]+\.[0-9]+|Version:[[:space:]]+v?[0-9]+\.[0-9]+\.[0-9]+' \
    "> **Version:** vX.Y.Z"

# _Info.txt enthält ${mod.version}-Placeholder, der durch Maven-Filter aufgelöst wird.
# Wir prüfen die Existenz des Placeholders und vergleichen die tatsächlich gebaute Version
# in target/out/SyxEconomyMod/_Info.txt (falls vorhanden) gegen die erwartete Version.
INFO_TXT_DEPLOYED="target/out/SyxEconomyMod/_Info.txt"
if [ -f "$INFO_TXT_DEPLOYED" ]; then
    DEPLOYED=$(grep -oE 'VERSION:[[:space:]]*"[^"]+"' "$INFO_TXT_DEPLOYED" | head -1 | sed 's/.*"\([^"]*\)".*/\1/' || true)
    if [ -n "$DEPLOYED" ]; then
        if [ "$DEPLOYED" = "$TRUTH_V" ] || [ "$DEPLOYED" = "$TRUTH" ]; then
            printf '  %sOK%s    %-30s  deployed=%s (truth=%s)\n' "$GREEN" "$NC" "$INFO_TXT_DEPLOYED" "$DEPLOYED" "$TRUTH"
            CHECKED=$((CHECKED + 1))
        else
            printf '  %sWARN%s  %-30s  deployed=%s stale vs truth=%s — run: mvn package -DskipTests\n' "$YELLOW" "$NC" "$INFO_TXT_DEPLOYED" "$DEPLOYED" "$TRUTH"
            # WARN statt FAIL — _Info.txt wird neu generiert beim nächsten package
        fi
    fi
else
    printf '  %sINFO%s  %-30s  noch nicht gebaut (kein target/out/.../_Info.txt vorhanden)\n' "$CYAN" "$NC" "$INFO_TXT_DEPLOYED"
    # Kein FAIL — _Info.txt wird erst nach `mvn package` aus dem Template generiert.
fi

# _Info.txt Template-Placeholder muss existieren
if grep -q '\${mod.version}' _Info.txt; then
    printf '  %sOK%s    %-30s  Template referenziert ${mod.version} (Maven-Filter korrekt)\n' "$GREEN" "$NC" "_Info.txt"
    CHECKED=$((CHECKED + 1))
else
    printf '  %sFAIL%s  %-30s  Template-Placeholder ${mod.version} fehlt\n' "$RED" "$NC" "_Info.txt"
    FAILED=1
fi

# ── 4. Result ─────────────────────────────────────────────────────────

echo ""
echo -e "${CYAN}==============================================================${NC}"
if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}  PASS${NC}  — $CHECKED Dokumente sync mit pom.xml ${TRUTH}"
    exit 0
fi

echo -e "${RED}  DRIFT${NC} — Stam-Doku-Inkonsistenz. Build wird in validate-Phase abgebrochen."
echo ""
echo "  Fix pro Drift-Typ:"
echo "    - Versions-Zeile oben in der Doku-Datei angleichen:"
echo "        **Version:** v${TRUTH}      ODER"
echo "        > **Version:** v${TRUTH}    (für Block-Quote Style)"
echo "    - CHANGELOG.md: erstes Heading ## v${TRUTH} — … sicherstellen"
echo "    - _Info.txt deployed: mvn package -DskipTests (regeneriert target/out/...)"
echo ""
echo "  Manuell:"
echo "    bash tools/verify-doc-sync.sh"
exit 1
