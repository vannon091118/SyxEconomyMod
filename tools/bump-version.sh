#!/usr/bin/env bash
# SyxEconomyMod — Version-Bump-Automatisierung
# ==============================================
# Erhoeht die Mod-Version, haengt einen Changelog-Eintrag an,
# regeneriert mod.version.history aus den letzten 5 Git-Tags,
# und setzt einen Git-Tag. Nebenwirkungsfrei via Dry-Run-Modus.
#
# Verwendung:
#   bash tools/bump-version.sh patch [-m "..."] [--dry-run] [--no-tag]
#   bash tools/bump-version.sh minor [-m "..."] [--dry-run] [--no-tag]
#   bash tools/bump-version.sh major [-m "..."] [--dry-run] [--no-tag]
#   bash tools/bump-version.sh --set 0.3.0 [-m "..."] [--dry-run] [--no-tag]
#
# Bump-Modi:
#   patch: 0.0.X -> 0.0.X+1    (Bugfixes)
#   minor: 0.X.0 -> 0.X+1.0  (Features)
#   major: X.0.0 -> X+1.0.0  (Breaking Changes, vor Public-Release optional)
#
# Was es macht:
#   1. pom.xml: <version>, <mod.info>, <mod.changelog> erster Eintrag
#   2. CHANGELOG.md: neuen Eintrag nach Header prependen
#   3. <mod.version.history> aus den letzten 5 Git-Tags regenerieren
#   4. git add + commit + tag
#
# Optionen:
#   -m "..."     Commit-/Changelog-Message (sonst generisch)
#   --dry-run    Schreibt nichts, zeigt nur Diff-Plan
#   --no-tag     Ueberspringt den git-tag (fuer Notfall-Repairs)
#   --no-commit  Ueberspringt den git-commit (fuer manuelle Review)
#   --help       Diese Hilfe
#
# Exit-Codes:
#   0 - Bump erfolgreich
#   1 - Fehler (z. B. ungueltige Version, Datei nicht gefunden)
#   2 - Git-Fehler (kein Repo, dirty working tree)

set -eo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

POM="pom.xml"
CHANGELOG="CHANGELOG.md"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# ── Argumente parsen ─────────────────────────────────────────────────

MODE=""
NEW_VERSION=""
MESSAGE=""
DRY_RUN=0
NO_TAG=0
NO_COMMIT=0

usage() {
    sed -n '2,33p' "$0" | sed 's/^# //; s/^#//'
    exit 0
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        patch|minor|major)
            MODE="$1"
            shift
            ;;
        --set)
            if [[ -n "$MODE" && "$MODE" != "set" ]]; then
                echo -e "${RED}FEHLER: --set kann nicht mit '$MODE' kombiniert werden${NC}" >&2
                echo "  Entweder:  bash tools/bump-version.sh $MODE -m '...'" >&2
                echo "  Oder:      bash tools/bump-version.sh --set $2 -m '...'" >&2
                exit 1
            fi
            NEW_VERSION="$2"
            MODE="set"
            shift 2
            ;;
        -m|--message)
            MESSAGE="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=1
            shift
            ;;
        --no-tag)
            NO_TAG=1
            shift
            ;;
        --no-commit)
            NO_COMMIT=1
            shift
            ;;
        --help|-h)
            usage
            ;;
        *)
            echo -e "${RED}FEHLER: Unbekanntes Argument '$1'${NC}" >&2
            usage >&2
            exit 1
            ;;
    esac
done

if [[ -z "$MODE" ]]; then
    echo -e "${RED}FEHLER: Modus erforderlich (patch|minor|major|--set X.Y.Z)${NC}" >&2
    usage >&2
    exit 1
fi

# ── Preflight ────────────────────────────────────────────────────────

[[ -f "$POM" ]] || { echo -e "${RED}FEHLER: $POM nicht gefunden${NC}" >&2; exit 2; }
[[ -f "$CHANGELOG" ]] || { echo -e "${RED}FEHLER: $CHANGELOG nicht gefunden${NC}" >&2; exit 2; }

if [[ $NO_COMMIT -eq 0 ]] && [[ $DRY_RUN -eq 0 ]]; then
    if ! command -v git >/dev/null 2>&1; then
        echo -e "${RED}FEHLER: 'git' nicht im PATH${NC}" >&2
        exit 2
    fi
    if [[ ! -d .git ]]; then
        echo -e "${RED}FEHLER: kein .git/ gefunden - bitte 'git init' zuerst${NC}" >&2
        exit 2
    fi
    # Working tree muss sauber sein
    if ! git diff --quiet HEAD 2>/dev/null; then
        echo -e "${RED}FEHLER: Working tree ist dirty - bitte zuerst committen/stashen${NC}" >&2
        git status --short >&2
        exit 2
    fi
fi

# Aktuelle Version extrahieren (erste <version>-Zeile in pom.xml)
CURRENT_VERSION=$(grep -m1 '^[[:space:]]*<version>[0-9]' "$POM" | sed 's/.*<version>\([^<]*\)<\/version>.*/\1/')

if [[ -z "$CURRENT_VERSION" ]]; then
    echo -e "${RED}FEHLER: Konnte aktuelle Version aus $POM nicht extrahieren${NC}" >&2
    exit 2
fi

# ── Neue Version berechnen ────────────────────────────────────────────

calc_new_version() {
    local current="$1"
    local mode="$2"
    local major minor patch

    IFS='.' read -r major minor patch <<< "$current"

    case "$mode" in
        patch)
            patch=$((patch + 1))
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        set)
            echo "$NEW_VERSION"
            return
            ;;
        *)
            echo -e "${RED}FEHLER: Unbekannter Modus '$mode'${NC}" >&2
            exit 1
            ;;
    esac

    echo "${major}.${minor}.${patch}"
}

NEW_VERSION=$(calc_new_version "$CURRENT_VERSION" "$MODE")

# Validiere Format
if ! [[ "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo -e "${RED}FEHLER: Ungueltige Version '$NEW_VERSION'${NC}" >&2
    exit 1
fi

DATE=$(date +%Y-%m-%d)
COMMIT_MSG="${MESSAGE:-Bump version to v${NEW_VERSION}}"

# ── Header ────────────────────────────────────────────────────────────

echo "=============================================================="
printf '  SyxEconomyMod \xE2\x80\x94 Version-Bump\n'
echo "=============================================================="
echo ""
printf '  Modus:     \033[0;36m%s\033[0m\n' "${MODE}"
echo "  Aktuell:   ${CURRENT_VERSION}"
printf '  Neu:       \033[0;32m%s\033[0m\n' "${NEW_VERSION}"
echo "  Datum:     ${DATE}"
echo "  Message:   ${COMMIT_MSG}"
echo "  Dry-Run:   ${DRY_RUN}"
echo "  No-Tag:    ${NO_TAG}"
echo "  No-Commit: ${NO_COMMIT}"
echo ""

# ── 1. pom.xml aktualisieren ──────────────────────────────────────────

echo -e "${CYAN}[1/5]${NC} pom.xml wird aktualisiert..."

# 1a. <version>0.1.0</version> -> <version>0.2.0</version> (nur in den ersten 20 Zeilen,
#     um Plugin-/Dependency-Versionen NICHT zu treffen)
if [[ $DRY_RUN -eq 1 ]]; then
    echo "  Wuerde aendern: <version>${CURRENT_VERSION}</version> -> <version>${NEW_VERSION}</version> (Zeilen 1-20)"
else
    sed -i "1,20{s|<version>${CURRENT_VERSION}</version>|<version>${NEW_VERSION}</version>|}" "$POM"
    echo "  - <version>${NEW_VERSION}</version> gesetzt"
fi

# 1b. <mod.info> aktualisieren (SyxEconomyMod v0.1.0 -> v0.2.0)
#     SINGLE replace (kein /g), um unbeabsichtigte Treffer zu vermeiden
if [[ $DRY_RUN -eq 1 ]]; then
    echo "  Wuerde aendern: <mod.info>... v${CURRENT_VERSION} ... -> ... v${NEW_VERSION} ..."
else
    sed -i "s|SyxEconomyMod v${CURRENT_VERSION}|SyxEconomyMod v${NEW_VERSION}|" "$POM"
    echo "  - <mod.info>... v${NEW_VERSION} ... aktualisiert"
fi

# 1c. <mod.changelog> erster Eintrag: v0.1.0 -> v0.2.0
#     OHNE automatischen Phase-Suffix (vermeidet Confusion zwischen Project-Phase
#     und Week-of-Year)
if [[ $DRY_RUN -eq 1 ]]; then
    echo "  Wuerde aendern: <mod.changelog> erster Eintrag v${CURRENT_VERSION} -> v${NEW_VERSION}"
else
    sed -i "/<mod.changelog>/{s|v${CURRENT_VERSION} |v${NEW_VERSION} |}" "$POM"
    echo "  - <mod.changelog> erster Eintrag aktualisiert"
fi

# ── 2. CHANGELOG.md prepended neuen Eintrag ───────────────────────────

echo ""
echo -e "${CYAN}[2/5]${NC} CHANGELOG.md wird prependet..."

NEW_ENTRY="## v${NEW_VERSION} — ${DATE}

${COMMIT_MSG}

---
"

if [[ $DRY_RUN -eq 1 ]]; then
    echo "  Wuerde prependen:"
    echo "$NEW_ENTRY" | sed 's/^/    /'
else
    # Backup + Insert: finde das erste "## v" und fuege davor ein
    cp "$CHANGELOG" "${CHANGELOG}.bak"
    TMP_ENT=$(mktemp)
    printf '%s\n' "$NEW_ENTRY" > "$TMP_ENT"

    awk -v entry_file="$TMP_ENT" '
        BEGIN { found = 0 }
        !found && /^## v[0-9]/ {
            while ((getline line < entry_file) > 0) print line
            close(entry_file)
            found = 1
            next
        }
        { print }
    ' "$CHANGELOG" > "${CHANGELOG}.new"

    mv "${CHANGELOG}.new" "$CHANGELOG"
    rm -f "$TMP_ENT" "${CHANGELOG}.bak"
    echo "  - Neuer Eintrag nach Header eingefuegt"
fi

# ── 3. mod.version.history aus letzten 5 Git-Tags regenerieren ────────
#
# _Info.txt braucht NICHT direkt bearbeitet zu werden: Maven filtert beim
# Build das Property <mod.version.history> aus pom.xml in _Info.txt.
# Daher aktualisieren wir nur die pom.xml-Eigenschaft.

echo ""
echo -e "${CYAN}[3/5]${NC} mod.version.history wird regeneriert..."

if [[ -d .git ]] && command -v git >/dev/null 2>&1; then
    LAST_5_TAGS=$(git tag --sort=-creatordate 2>/dev/null | head -5 | paste -sd ';' - || true)
    # Wenn noch keine Tags vorhanden, ist LAST_5_TAGS leer
    if [[ -z "$LAST_5_TAGS" ]]; then
        LAST_5_TAGS="v${NEW_VERSION}"
        echo "  - Keine bisherigen Tags, setze auf v${NEW_VERSION}"
    else
        echo "  - Letzte 5 Tags: $LAST_5_TAGS"
    fi

    if [[ $DRY_RUN -eq 1 ]]; then
        echo "  Wuerde aendern: <mod.version.history>${LAST_5_TAGS}</mod.version.history>"
    else
        sed -i "s|<mod.version.history>.*</mod.version.history>|<mod.version.history>${LAST_5_TAGS}</mod.version.history>|" "$POM"
        echo "  - <mod.version.history>${LAST_5_TAGS}</mod.version.history> gesetzt"
    fi
else
    echo "  - Kein .git/ vorhanden, ueberspringe Tag-basierte History-Regeneration"
fi

# ── 4. git add + commit + tag ────────────────────────────────────────

echo ""
echo -e "${CYAN}[4/5]${NC} git-Operationen..."

if [[ $DRY_RUN -eq 1 ]]; then
    echo "  Wuerde ausfuehren:"
    echo "    git add $POM $CHANGELOG"
    echo "    git commit -m \"$COMMIT_MSG\""
    if [[ $NO_TAG -eq 0 ]]; then
        echo "    git tag v${NEW_VERSION}"
    fi
else
    if [[ $NO_COMMIT -eq 1 ]]; then
        echo "  - --no-commit: ueberspringe git add/commit/tag"
    else
        git add "$POM" "$CHANGELOG"
        echo "  - git add $POM $CHANGELOG"

        git commit -m "$COMMIT_MSG"
        echo "  - git commit -m \"$COMMIT_MSG\""

        if [[ $NO_TAG -eq 0 ]]; then
            # Pruefe ob Tag bereits existiert
            if git rev-parse "v${NEW_VERSION}" >/dev/null 2>&1; then
                echo -e "  ${YELLOW}WARNUNG: Tag v${NEW_VERSION} existiert bereits - ueberspringe${NC}" >&2
            else
                git tag "v${NEW_VERSION}"
                printf '  - \033[0;32mgit tag v%s\033[0m\n' "${NEW_VERSION}"
            fi
        else
            echo "  - --no-tag: uebersprungen"
        fi
    fi
fi

# ── 5. _Info.txt Sync-Status (pom.xml ↔ deployed artifact) ─────────
#
# _Info.txt am Repo-Root ist ein Maven-Filter-Template mit ${...}-Platzhaltern.
# 'mvn package' generiert daraus die deployed-Kopie unter
# target/out/SyxEconomyMod/_Info.txt. pom.xml-Aenderungen machen die
# deployed-Kopie erst nach 'mvn package' sichtbar.
#
# bump-version.sh REGENERIERT die deployed-Kopie NICHT selbst (mvn ist
# extern), sondern PRUEFT nur:
#   5a. _Info.txt-Placeholder ↔ pom.xml-Properties (drift-detect)
#   5b. target/out/SyxEconomyMod/_Info.txt VERSION freshness (warn)
#
# Geteilte Logik in tools/lib/_info-txt-sync.sh.

echo ""
echo -e "${CYAN}[5/5]${NC} _Info.txt Sync-Status..."

SCRIPT_DIR_BV="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/_info-txt-sync.sh
. "${SCRIPT_DIR_BV}/lib/_info-txt-sync.sh"

# 5a. Template ↔ pom.xml Properties (informational — keine Hard-Fail,
#      weil Hard-Fail bereits in verify-version-consistency.sh passiert)
sync_info_txt_template_report "warn" "$POM" || true

# 5b. Deployed-Copy freshness check (warn if stale vs NEW_VERSION)
sync_info_txt_deployed_report "$NEW_VERSION" || true

# ── Zusammenfassung ──────────────────────────────────────────────────

echo ""
echo "=============================================================="
printf '  \033[0;32mBUMP ABGESCHLOSSEN\033[0m\n'
echo "=============================================================="
echo ""
echo "  v${CURRENT_VERSION} -> v${NEW_VERSION}"
echo "  CHANGELOG.md:   +1 Eintrag"
echo "  pom.xml:        <version>, <mod.info>, <mod.changelog>, <mod.version.history>"
echo "  _Info.txt:      Template ↔ pom.xml + deployed-Copy geprueft (Schritt 5/5)"
[[ $NO_COMMIT -eq 0 ]] && echo "  git:            commit + tag v${NEW_VERSION}"
echo ""
echo "Verifikation:"
echo "  bash tools/verify-version-consistency.sh"
echo "  bash tools/docs-truth-consistency.sh"
echo "  cat target/out/SyxEconomyMod/_Info.txt  # deployed nach 'mvn package'"