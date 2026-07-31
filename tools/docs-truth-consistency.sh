#!/usr/bin/env bash
# SyxEconomyMod — Documentation Truth Consistency Gate
# =====================================================
# Prueft vor jedem Commit, ob die aktive Doku gegen den Code-Stand konsistent ist.
# Exit 0 = PASS, Exit 1 = Drift gefunden, Exit 2 = Skript-Umgebungsfehler.
#
# 4 Checks:
#   1. Java-File-Count-Drift: keine "108" als aktive Datei-Count-Aussage (Truth: live-count in src/vannon/).
#      ACHTUNG: Nur Count-Kontext matchen ("108 Dateien" / "108 Java-Dateien"), KEINE Task-IDs (T-108)
#      oder Sprint-Tags (v0.13.108+) — `\b108\b` allein ist zu breit (False-Positives).
#   2. TreasuryCrisis-Tier-Count: keine "3-stufige"/"6-stufige" als Hauptbeschreibung
#      (Truth: 5 Tier-Stufen + Hard-Floor in Tier 5)
#   3. Phase-5-Klassen: 0/8 implementiert - solange der Plan in docs/superpowers/plans/
#      den Status "to implement" hat. Sobald eine implementiert ist, muss der Plan-
#      Status angepasst werden, sonst FAILS dieses Gate.
#   4. Stale paths: keine `docs/HISTORICAL_*` als aktive Pfade. Diese liegen jetzt
#      unter `docs/archive/`. Verweise darauf muessen `docs/archive/HISTORICAL_*` sein.
#
# Allowlist (dokumentieren Drift intentional):
#   - docs/reports/TRUTH_REPORT.md (Audit-Bericht)
#   - docs/README.md (Truth-Status-Tabelle listet alte vs. neue Werte)
#   - docs/CHANGELOG.md (Redirect-Notice mit historischem Inhalt; Pre-v0.1.0 Duplikat)
#
# Uebersprungen:
#   - docs/archive/ - eingefroren, keine Drift-Pruefung
#
# Installation als Pre-Commit-Hook:
#   cp tools/docs-truth-consistency.sh .git/hooks/pre-commit
#   chmod +x .git/hooks/pre-commit
#
# Oder als manueller Check:
#   bash tools/docs-truth-consistency.sh
#
# Exit-Codes:
#   0 - alle 4 Checks PASS
#   1 - mindestens ein Check FAIL (Drift gefunden)
#   2 - Skript-Umgebungsfehler (z. B. nicht im Repo-Root)

set -eo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Preflight: grep muss verfuegbar sein, sonst wuerde das Gate stillschweigend PASSen.
if ! command -v grep >/dev/null 2>&1; then
    echo "FEHLER: 'grep' nicht im PATH - Script kann nicht verlaesslich pruefen." >&2
    exit 2
fi

# Hinweis: kein Git-Repo -> Pre-Commit-Hook nicht installierbar.
if [[ ! -d .git ]]; then
    echo "Hinweis: kein .git/ gefunden - Pre-Commit-Hook muss spaeter manuell installiert werden" >&2
    echo "         (siehe Installationsanleitung im Script-Header)" >&2
    echo "" >&2
fi

# --------------------------------------------------------------------
# Konfiguration
# --------------------------------------------------------------------

# Dateien, die Drift intentional dokumentieren duerfen (z. B. Audit-Tabellen).
DRIFT_DOCS=(
    "docs/reports/TRUTH_REPORT.md"
    "docs/README.md"
    "docs/CHANGELOG.md"   # Redirect-Notice mit historischem Inhalt (Pre-v0.1.0 Duplikat)
)

# Archiv-Verzeichnis: komplett uebersprungen.
ARCHIVE_DIR="docs/archive"

# Phase-5 Klassen gemaess docs/superpowers/plans/2026-07-23-per-citizen-training-exp.md
PHASE5_CLASSES=(
    "vannon.syx.economy.core.StageGate"
    "vannon.syx.economy.core.NeedsBridge"
    "vannon.syx.economy.core.ITrainingSource"
    "vannon.syx.economy.core.InternalTrainingSource"
    "vannon.syx.economy.core.CsvTrainingSource"
    "vannon.syx.economy.core.CsvIngest"
    "vannon.syx.economy.core.TrainingVectors"
    "vannon.syx.economy.core.CitizenLayers"
)

# --------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------

FAIL=0

print_pass() {
    printf '  \033[0;32mPASS\033[0m %s\n' "$1"
}

print_fail() {
    printf '  \033[0;31mFAIL\033[0m %s\n' "$1"
    if [[ -n "$2" ]]; then
        printf '%s\n' "$2" | sed 's/^/         /'
    fi
    FAIL=1
}

# Aktive Dateien fuer die Suche zusammenstellen
# (alles ausser Allowlist + Archiv).
ACTIVE_FILES=()
while IFS= read -r f; do
    case "$f" in
        "${ARCHIVE_DIR}/"*) continue ;;
    esac
    skip=0
    for d in "${DRIFT_DOCS[@]}"; do
        if [[ "$f" == "./$d" || "$f" == "$d" ]]; then
            skip=1
            break
        fi
    done
    [[ $skip -eq 0 ]] && ACTIVE_FILES+=("$f")
done < <(
    {
        find . -maxdepth 1 -name '*.md' -type f 2>/dev/null
        find docs -maxdepth 2 -name '*.md' -type f 2>/dev/null
    } | sort -u
)

# Grep-Wrapper: nimmt ACTIVE_FILES, gibt Treffer oder leeren String zurueck.
safe_grep() {
    local pattern="$1"
    local result=""
    if [[ ${#ACTIVE_FILES[@]} -gt 0 ]]; then
        result=$(grep -nHE "$pattern" "${ACTIVE_FILES[@]}" 2>/dev/null) || result=""
    fi
    echo "$result"
}

# --------------------------------------------------------------------
# Header
# --------------------------------------------------------------------

echo "=============================================================="
echo "  SyxEconomyMod - Documentation Truth Gate"
echo "=============================================================="
echo ""
echo "Gepruefter Scope: ${#ACTIVE_FILES[@]} aktive Markdown-Dateien"
echo "Allowlist (drift-dokumentierend): ${DRIFT_DOCS[*]}"
echo "Uebersprungen (Archiv): $ARCHIVE_DIR/"
echo ""

# --------------------------------------------------------------------
# Check 1: Java-File-Count-Drift (108 -> 112)
# --------------------------------------------------------------------

echo "--- Check 1: Java-File-Count-Drift ---"
SRC_JAVA=$(find src -name '*.java' 2>/dev/null | wc -l)
echo "  Truth (live):  src/vannon/ hat $SRC_JAVA .java-Dateien"
echo "  Erwartung:     aktiver Live-Wert ($SRC_JAVA). Keine '108'-Count-Aussage mehr."
echo ""

# Nur Datei-Count-Kontext matchen: '108 Dateien', '108 Java-Dateien', '108 files'.
# NICHT: Task-IDs (T-108), Sprint-Tags (v0.13.108+), Versionszahlen, Zeilennummern.
HITS_108=$(safe_grep '\b108\b[^A-Za-z0-9_+.-]*(Java[[:space:]-]*)?(Dateien|Datei|Files|files|\\.java)')
if [[ -z "$HITS_108" ]]; then
    print_pass "Keine '108'-Datei-Behauptung in aktiven Docs"
else
    print_fail "'108' als aktive Aussage in Docs gefunden:" "$HITS_108"
fi
echo ""

# --------------------------------------------------------------------
# Check 2: TreasuryCrisis Tier-Count (3-/6-stufige)
# --------------------------------------------------------------------

echo "--- Check 2: TreasuryCrisis Tier-Count ---"
echo "  Truth:          5 Tier-Stufen + Hard-Floor (Tier 5)"
echo "  Verboten aktiv: '3-stufige' oder '6-stufige' als Hauptbeschreibung"
echo ""

HITS_TIER=$(safe_grep '3-stufige|3 stufige|6-stufige|6 stufige')
if [[ -z "$HITS_TIER" ]]; then
    print_pass "Keine 3-/6-stufige-Behauptung in aktiven Docs"
else
    print_fail "Falsche Tier-Zahl in Docs gefunden:" "$HITS_TIER"
fi
echo ""

# --------------------------------------------------------------------
# Check 3: Phase-5 Klassen-Implementierung
# --------------------------------------------------------------------

echo "--- Check 3: Phase-5 Klassen-Implementierung ---"
echo "  Plan: docs/superpowers/plans/2026-07-23-per-citizen-training-exp.md"
echo "  Erwartet: 0/8 implementiert solange Plan 'to implement' sagt."
echo ""

IMPL_COUNT=0
IMPL_LIST=""
for cls in "${PHASE5_CLASSES[@]}"; do
    rel="${cls//.//}.java"
    if [[ -f "src/${rel}" ]]; then
        IMPL_COUNT=$((IMPL_COUNT + 1))
        IMPL_LIST="${IMPL_LIST}         - ${cls}\n"
    fi
done

if [[ "$IMPL_COUNT" -eq 0 ]]; then
    print_pass "Phase-5: 0/8 Klassen implementiert (Plan-State konsistent)"
else
    print_fail "Phase-5: ${IMPL_COUNT}/8 Klassen implementiert - Plan-Status muss aktualisiert werden" \
        "$(printf '%b' "$IMPL_LIST")"
fi
echo ""

# --------------------------------------------------------------------
# Check 4: Stale docs/HISTORICAL_* Pfade
# --------------------------------------------------------------------

echo "--- Check 4: Stale docs/HISTORICAL_* Pfade ---"
echo "  Pfade wie docs/HISTORICAL_X.md nur unter docs/archive/ erlaubt"
echo ""

HISTS=$(safe_grep 'docs/HISTORICAL_[A-Z_0-9]+\.(md|txt)')
if [[ -z "$HISTS" ]]; then
    print_pass "Keine stale docs/HISTORICAL_*-Pfade in aktiven Docs"
else
    print_fail "Veraltete Pfade (Dateien sind nach docs/archive/ verschoben):" "$HISTS"
fi
echo ""

# --------------------------------------------------------------------
# Zusammenfassung
# --------------------------------------------------------------------

echo "=============================================================="
if [[ "$FAIL" -eq 0 ]]; then
    printf '\033[0;32m  PASS - Doku konsistent mit Code-Stand\033[0m\n'
    exit 0
else
    printf '\033[0;31m  DRIFT GEFUNDEN - siehe Details oben\033[0m\n'
    echo ""
    echo "Fix-Vorschlaege:"
    echo "  - Aktualisiere die driftenden Stellen in aktiven Docs (Datei:Zeile)"
    echo "  - Fuer historische Dokumentation: docs/reports/TRUTH_REPORT.md zeigt korrekte Wahrheit"
    echo "  - Allowlist-Files (TRUTH_REPORT.md, docs/README.md) duerfen Drift intentional dokumentieren"
    echo "  - Bei Phase-5-Klassen: Plan-Status auf 'X/8 implementiert' setzen"
    echo ""
    echo "Manuell ausfuehren fuer Details:"
    echo "  bash tools/docs-truth-consistency.sh"
    exit 1
fi