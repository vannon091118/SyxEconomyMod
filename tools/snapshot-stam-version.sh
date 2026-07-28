#!/usr/bin/env bash
# SyxEconomyMod — Stam-Version Snapshot
# ======================================
# Anti-Regression-Tool gegen den post-install-patch-bump-Phantom.
#
# Motivation
# ----------
# `agents.md` Rule 3 dokumentiert die 5(+2)-Schritt-Drift-Resolution
# zwischen pom.xml und 7 Stam-Docs. Der manuelle sed-Schritt ist by
# design (Rule 3 friction). Aber: zwischen *Resolution* und *nächstem
# commit* kann pom.xml erneut gebumpt haben — durch mvn install,
# mvn verify install Folge-Runs, hook-interne mvn-Calls. Wenn dann
# der pre-commit-Hook feuert, sieht Gate 1 (Stam-Doku-Sync) die Drift
# zwar, aber die Message ist relativ generisch. Gate 0 hier
# fail-fastet ENGER: pom != snapshot = Phantom-Bump seit dem letzten
# Rule-3-Capture.
#
# Workflow
# --------
#   1. Nach Rule-3-Resolution (sed step 3 + verify-doc-sync PASS):
#      `bash tools/snapshot-stam-version.sh capture`
#      → schreibt pom.xml <version> in .git/hooks/.stam-version-snapshot
#   2. Vor jedem `git commit`:
#      Pre-Commit-Hook Gate 0 ruft `check` auf.
#      pom.xml version vs. snapshot. Wenn ungleich → fail-fast,
#      Hinweis auf Rule 3 + recapture.
#   3. `reset` löscht den Snapshot (fresh-init / not-in-session).
#   4. `show` druckt pom.xml und Snapshot-Versions side-by-side.
#
# Storage
# -------
# .git/hooks/.stam-version-snapshot — IMMER ausserhalb des Repos
# (in .git/), landet nicht im `git diff`. Komplementär zu Rule 3, dessen
# sed-Sequenz ja im git diff sichtbar sein soll. Diese Trennung ist
# Absicht: kein Skript maskiert Drift, aber lokale Hook-Zustände
# gehoeren nicht in die Repo-History.

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

SNAPSHOT_FILE=".git/hooks/.stam-version-snapshot"
POM="pom.xml"

usage() {
    cat <<EOF
Usage: bash tools/snapshot-stam-version.sh <subcommand>

Subcommands:
  capture   Reads current pom.xml <version> and writes it to
            $SNAPSHOT_FILE. Run AFTER Rule-3-Resolution complete
            (sed-step-3 + verify-doc-sync PASS).
  check     Compares current pom.xml <version> to snapshot.
            Exit 0 if match OR snapshot absent OR malformed-tolerated.
            Exit 1 if mismatch (Phantom-Bump detektiert) — with
            remediation hint pointing to agents.md Rule 3.
  reset     Removes the snapshot file (fresh init / switch branches).
  show      Prints pom.xml version AND snapshot version side-by-side.
  help      This help text.

By design, this script does NOT auto-capture. Capture is an
explicit user action post-Resolution, per agents.md Rule 3.
EOF
}

require_pom_readable() {
    if [ ! -f "$POM" ]; then
        echo -e "${RED}FEHLER: $POM nicht gefunden — bist du im Projekt-Root?${NC}" >&2
        exit 2
    fi
}

read_pom_version() {
    local v_raw v_norm
    # Suche nach einem KOMPLETTEN <version>...</version>-Element (beide Tags
    # müssen vorhanden sein). Verhindert false-positives auf:
    #   - <modelVersion>4.0.0</modelVersion> (Substring-Match wegen 4.0.0-Pattern)
    #   - <version> in XML-Kommentaren die kein echtes Element sind
    #   - <otherVersion>...</otherVersion> und ähnliche Verwandte
    v_raw=$(grep -m1 -E '<version>[^<]*</version>' "$POM" | sed -n 's|.*<version>\([^<]*\)</version>.*|\1|p' 2>/dev/null || true)
    if [ -z "$v_raw" ]; then
        echo -e "${RED}FEHLER: $POM enthält kein gültiges <version>...</version>-Element — kaputte pom.xml oder falsche Datei?${NC}" >&2
        echo -e "${RED}FIX:${NC}    mvn validate zeigt Maven-eigene Diagnose; oder pom.xml gegen Maven-Super-POM-Schema abgleichen." >&2
        exit 2
    fi
    # Normalisiere auf MAJOR.MINOR.PATCH. || true verhindert pipefail-Propagation
    # aus grep (no-match) in den Caller; das explizite Empty-Check danach
    # unterscheidet "kaputte pom" von "drift" und exit 2 bleibt erhalten.
    v_norm=$(echo "$v_raw" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || true)
    if [ -z "$v_norm" ]; then
        echo -e "${RED}FEHLER: <version>-Wert '$v_raw' ist kein MAJOR.MINOR.PATCH-Format${NC}" >&2
        echo -e "${RED}FIX:${NC}    <version>-Tag auf \"MAJOR.MINOR.PATCH\"-Format korrigieren (z.B. 0.13.76, keine Suffixe, kein Snapshot-Stamp mit Buchstaben). Sonst funktioniert das Stam-Doc-Sync nicht." >&2
        exit 2
    fi
    echo "$v_norm"
}

cmd_capture() {
    require_pom_readable
    local version
    version=$(read_pom_version)
    if [ -z "$version" ]; then
        echo -e "${RED}FEHLER: read_pom_version lieferte leeren String${NC}" >&2
        exit 2
    fi
    mkdir -p "$(dirname "$SNAPSHOT_FILE")" 2>/dev/null || true  # .git/hooks/ existiert meist schon
    cat > "$SNAPSHOT_FILE" <<EOF
# Stam-Version Snapshot — geschrieben von tools/snapshot-stam-version.sh capture
# Repräsentiert den zuletzt aligned-State nach agents.md Rule 3 Resolution.
# Format: <key>=<value> — menschenlesbar + grep-tauglich.
pom_version=${version}
captured_at=$(date -Iseconds 2>/dev/null || date +%Y-%m-%dT%H:%M:%S%z)
EOF
    echo -e "${GREEN}✓ Snapshot erfasst:${NC}  $SNAPSHOT_FILE → pom_version=${version}"
}

cmd_check() {
    require_pom_readable
    if [ ! -f "$SNAPSHOT_FILE" ]; then
        # Frischer Zustand (noch kein Capture seit session-start) — nicht fail-fast.
        echo -e "${CYAN}⊘ SKIP${NC}  Kein Snapshot vorhanden ($SNAPSHOT_FILE) — first run toleriert"
        exit 0
    fi
    local pom_v snap_v
    pom_v=$(read_pom_version)
    snap_v=$(grep -m1 '^pom_version=' "$SNAPSHOT_FILE" | cut -d= -f2- || true)
    if [ -z "$snap_v" ]; then
        echo -e "${YELLOW}⚠ WARN${NC}  Snapshot-File vorhanden aber leer/malformed: $SNAPSHOT_FILE"
        echo "           Behandle als 'kein Snapshot'. Tipp: bash tools/snapshot-stam-version.sh reset && ... capture"
        exit 0
    fi
    if [ "$pom_v" = "$snap_v" ]; then
        echo -e "${GREEN}✓ PASS${NC}  pom.xml ${pom_v} == snapshot ${snap_v} — kein Phantom-Bump seit Capture"
        exit 0
    fi
    # Drift
    echo -e "${RED}✗ DRIFT${NC}  pom.xml = ${pom_v}  ≠  snapshot = ${snap_v} (Phantom-Bump seit letzter Rule-3-Capture)"
    echo ""
    echo "  Mögliche Ursachen (in absteigender Wahrscheinlichkeit):"
    echo "    - post-install-patch-bump feuerte zwischenzeitlich (mvn install / mvn verify install / mvn package Re-Run)"
    echo "    - Hook-interne mvn-Calls während pre-commit/post-commit-Hook-Ausführung"
    echo "    - Manuelle pom.xml-Edit ausserhalb des Rule-3-Workflows"
    echo ""
    echo "  Auflösung (agents.md Rule 3, Schritte 1-5):"
    echo "    bash tools/verify-doc-sync.sh                       # → FAIL mit File-Liste"
    echo "    # sed-Sequenz (7 stam-docs) aus agents.md Rule 3 step 3 manuell"
    echo "    bash tools/verify-doc-sync.sh                       # → PASS"
    echo "    bash tools/snapshot-stam-version.sh capture         # neuen Snapshot ziehen"
    echo "    git add <paths> && git commit -m \"drift: align stam-docs to v\${NEW_V}\""
    exit 1
}

cmd_reset() {
    if [ -f "$SNAPSHOT_FILE" ]; then
        rm -f "$SNAPSHOT_FILE"
        echo -e "${GREEN}✓ Snapshot entfernt:${NC} $SNAPSHOT_FILE"
    else
        echo -e "${CYAN}⊘${NC} Kein Snapshot vorhanden ($SNAPSHOT_FILE) — nichts zu tun"
    fi
}

cmd_show() {
    require_pom_readable
    local pom_v snap_v="(no snapshot)" snap_at="(no snapshot)"
    pom_v=$(read_pom_version)
    if [ -f "$SNAPSHOT_FILE" ]; then
        local stored stored_at
        stored=$(grep -m1 '^pom_version=' "$SNAPSHOT_FILE" | cut -d= -f2- 2>/dev/null || true)
        stored_at=$(grep -m1 '^captured_at=' "$SNAPSHOT_FILE" | cut -d= -f2- 2>/dev/null || true)
        if [ -n "$stored" ]; then
            snap_v="$stored"
        fi
        if [ -n "$stored_at" ]; then
            snap_at="$stored_at"
        fi
    fi
    echo "  pom.xml version        = ${pom_v}"
    echo "  snapshot version       = ${snap_v}"
    echo "  snapshot captured_at   = ${snap_at}"
    echo "  snapshot file location = $SNAPSHOT_FILE"
}

case "${1:-help}" in
    capture) cmd_capture ;;
    check)   cmd_check ;;
    reset)   cmd_reset ;;
    show)    cmd_show ;;
    help|-h|--help) usage ;;
    *)
        echo -e "${RED}FEHLER: unbekannter Subcommand '$1'${NC}" >&2
        usage >&2
        exit 2
        ;;
esac
