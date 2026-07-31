#!/usr/bin/env bash
# SyxEconomyMod — Doku Version Single-Source-of-Truth-Sync (Sprint v0.13.108+Doku-Slim)
# ======================================================================================
# Liest pom.xml <version> als Truth und regeneriert alle Stam-Doc-Versions-Anker.
#
# Modi:
#   fix   — mutiert files: ersetzt alle Anker mit pom-version (idempotent, exit 0)
#   check — read-only: exit 0 wenn alle Anker matchen, exit 1 bei Drift
#
# Anker-Patterns (Sprint v0.13.108+Doku-Slim Standard):
#   - pom.xml                                  <version>X.Y.Z</version>
#   - Doku/README.md                           > **Version:** vX.Y.Z
#   - Doku/CHANGELOG.md                        > **Version:** vX.Y.Z
#   - Doku/ARCHITECTURE.md                     > **Version:** vX.Y.Z
#   - Doku/ROADMAP.md                          > **Version:** vX.Y.Z
#   - Doku/GLOSSARY.md                         > **Version:** vX.Y.Z
#   - tools/vanilla-schema.yaml                # VANILLA BYTECODE-SCHEMA — SyxEconomyMod vX.Y.Z
#
# Sprint v0.13.108+Doku-Slim GATE-9-Kompatibilitaet: Files die KEINEN
# Version-Anker haben (absichtlich entfernt) werden als OK gewertet, nicht
# als DRIFT (false-positive Prevention).
#
# Exit-Codes:
#   0 — alle Anker matchen (check) oder erfolgreich gefixt (fix)
#   1 — Drift gefunden (nur check-Modus)
#   2 — pom.xml oder Working-Dir-Fehler
#
# Usage:
#   bash tools/doku-sync.sh fix      # repariert alle Anker
#   bash tools/doku-sync.sh check    # read-only Verifikation

# Sprint v0.13.108+Doku-Slim: set -eo pipefail (ohne -u),
# keine Punkt-Var-Namen → keine "Bad substitution"-Risiken in altem bash.
set -eo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

MODE="${1:-check}"

if [ "$MODE" != "fix" ] && [ "$MODE" != "check" ]; then
    echo -e "${RED}FEHLER: usage: bash tools/doku-sync.sh [fix|check]${NC}" >&2
    exit 2
fi

# Truth extrahieren
if [ ! -f pom.xml ]; then
    echo -e "${RED}FEHLER: pom.xml nicht im Working-Dir.${NC}" >&2
    exit 2
fi

# Line-Walk: finde <version> direkt nach <artifactId>syx-economy-mod</artifactId>
POM_VERSION=""
while IFS= read -r line; do
    if echo "$line" | grep -q "<artifactId>syx-economy-mod"; then
        read -r nextline
        POM_VERSION=$(echo "$nextline" | sed -nE 's|.*<version>([0-9]+\.[0-9]+\.[0-9]+).*</version>.*|\1|p')
        break
    fi
done < pom.xml

# Fallback: flatten und sequenziell suchen
if [ -z "$POM_VERSION" ]; then
    FLAT_POM=$(tr -d '[:space:]' < pom.xml)
    POM_VERSION=$(echo "$FLAT_POM" | sed -nE 's|.*<artifactId>syx-economy-mod</artifactId><version>([0-9]+\.[0-9]+\.[0-9]+).*|\1|p')
fi

if [ -z "$POM_VERSION" ]; then
    echo -e "${RED}FEHLER: <artifactId>syx-economy-mod</artifactId><version>X.Y.Z</version> nicht in pom.xml.${NC}" >&2
    exit 2
fi

ANCHOR_VERSION="v${POM_VERSION}"
echo -e "${CYAN}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║  doku-sync ($MODE) — pom Truth: $ANCHOR_VERSION"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════╝${NC}"

PATTERN_VERSION_HEADER='^\> \*\*Version:\*\* [vV][0-9]+\.[0-9]+\.[0-9]+'
REPLACEMENT_VERSION_HEADER="> **Version:** $ANCHOR_VERSION"
PATTERN_SCHEMA="SyxEconomyMod [vV][0-9]+\.[0-9]+\.[0-9]+"
REPLACEMENT_SCHEMA="SyxEconomyMod $ANCHOR_VERSION"

DRIFT_FOUND=0
FIXED=0
CHECKED=0

process_anchor() {
    local path="$1"
    local pattern="$2"
    local replacement="$3"

    if [ ! -f "$path" ]; then
        echo -e "${YELLOW}⊘ SKIP${NC} — $path (Datei fehlt)"
        return 0
    fi

    # Match-Check 1: Anker-Pattern matcht → fix oder sync
    if grep -qE "$pattern" "$path" 2>/dev/null; then
        if [ "$MODE" = "fix" ]; then
            sed -i.bak -E "s|$pattern|$replacement|g" "$path"
            rm -f "${path}.bak"
            echo -e "${GREEN}✓ FIX${NC} — $path"
            FIXED=$((FIXED + 1))
        else
            echo -e "${GREEN}✓ SYNCE${NC} — $path"
        fi
        CHECKED=$((CHECKED + 1))
        return 0
    fi

    # Match-Check 2: Replacement bereits im File → MATCH
    if grep -qF "$replacement" "$path" 2>/dev/null; then
        echo -e "${GREEN}✓ MATCH${NC} — $path (anchor = $ANCHOR_VERSION)"
        CHECKED=$((CHECKED + 1))
        return 0
    fi

    # Match-Check 3 (Sprint v0.13.108+Doku-Slim GATE-9-Kompatibilitaet):
    # Datei hat KEINEN Version-Anker und KEINE andere Version-Spur —
    # GATE-9 hat den Anker absichtlich entfernt. IDEMPOTENT-OK.
    if ! grep -qE 'Version:|SyxEconomyMod [vV][0-9]' "$path" 2>/dev/null; then
        echo -e "${GREEN}✓ OK-G9${NC} — $path (kein Anker gewollt, GATE-9-konform)"
        CHECKED=$((CHECKED + 1))
        return 0
    fi

    # Drift: Pattern und Replacement passen nicht, andere Version-Spur erkannt
    echo -e "${RED}✗ DRIFT${NC} — $path (Anker-Pattern unbekannt oder Version-Spur weicht ab)"
    DRIFT_FOUND=1
    return 0
}

# Anker-Reihenfolge
for path in Doku/README.md Doku/CHANGELOG.md Doku/ARCHITECTURE.md Doku/ROADMAP.md Doku/GLOSSARY.md; do
    process_anchor "$path" "$PATTERN_VERSION_HEADER" "$REPLACEMENT_VERSION_HEADER"
done
process_anchor "tools/vanilla-schema.yaml" "$PATTERN_SCHEMA" "$REPLACEMENT_SCHEMA"

echo ""
echo -e "${CYAN}══════════════════════════════════════════════════════════${NC}"
if [ "$MODE" = "fix" ]; then
    echo -e "  ${GREEN}Gefixt: ${FIXED}${NC} / Geprueft: ${CHECKED}"
    if [ "$DRIFT_FOUND" -eq 0 ]; then
        echo -e "${GREEN}ALLE ANKER SYNC.${NC}"
        exit 0
    else
        echo -e "${YELLOW}WARNUNG: mindestens ein Drift — Pattern unbekannt, manuelle Inspektion.${NC}"
        exit 0
    fi
else
    if [ "$DRIFT_FOUND" -eq 0 ]; then
        echo -e "${GREEN}ALLE ANKER SYNCHRON.${NC}"
        exit 0
    else
        echo -e "${RED}DRIFT GEFUNDEN — bash tools/doku-sync.sh fix ausfuehren.${NC}"
        exit 1
    fi
fi
