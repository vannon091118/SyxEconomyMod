#!/usr/bin/env bash
# SyxEconomyMod — Stam-Doku-Sync Gate
# =====================================
# Stellt vor jedem `mvn compile` (validate-Phase) sicher, dass die
# 7 Stam-Dokumente dieselbe Versions-Information tragen wie `pom.xml`.
#
# Stam-Dokumente (seit Doku-Restruktur unter Doku/):
#   1. Doku/README.md                  — **Version:** vX.Y.Z
#   2. Doku/CHANGELOG.md               — Erstes `## vX.Y.Z` Heading + Kopfzeile
#   3. Doku/ARCHITECTURE.md            — > **Version:** vX.Y.Z
#   4. Doku/ROADMAP.md                 — > **Version:** vX.Y.Z
#   5. Doku/GLOSSARY.md                — > **Version:** vX.Y.Z
#   6. pom.xml                    (root) — Truth of Record
#   7. _Info.txt                  (root) — Maven-Filter-Template
#   8. tools/vanilla-schema.yaml  — Schema-Version + YAML↔Java-Feld-Abgleich
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

# Doku/README.md: "**Version:** vX.Y.Z"
check_doc "Doku/README.md" \
    '(\*\*Version:\*\*|Version:)[[:space:]]*v?[0-9]+\.[0-9]+\.[0-9]+' \
    "**Version:** vX.Y.Z  ODER  Version: vX.Y.Z"

# Doku/CHANGELOG.md: erstes "## vX.Y.Z" Heading
check_doc "Doku/CHANGELOG.md" \
    '^##[[:space:]]+v[0-9]+\.[0-9]+\.[0-9]+' \
    "## vX.Y.Z als erstes Release-Heading"

# Doku/ARCHITECTURE.md: "> **Version:** vX.Y.Z"
check_doc "Doku/ARCHITECTURE.md" \
    '>\s*\*\*Version:\*\*[[:space:]]*v?[0-9]+\.[0-9]+\.[0-9]+' \
    "> **Version:** vX.Y.Z"

# Doku/ROADMAP.md: "> **Version:** vX.Y.Z"
check_doc "Doku/ROADMAP.md" \
    '>\s*\*\*Version:\*\*[[:space:]]*v?[0-9]+\.[0-9]+\.[0-9]+' \
    "> **Version:** vX.Y.Z"

# Doku/GLOSSARY.md: "> **Version:** vX.Y.Z" oder "Version: vX.Y.Z"
check_doc "Doku/GLOSSARY.md" \
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

# Doku/CHANGELOG.md Kopfzeile: "> **Version:** vX.Y.Z" (zusätzlich zum ## v-Heading)
check_doc "Doku/CHANGELOG.md" \
    '>\s*\*\*Version:\*\*[[:space:]]*v?[0-9]+\.[0-9]+\.[0-9]+' \
    "> **Version:** vX.Y.Z in CHANGELOG-Kopfzeile"

# _Info.txt Template-Placeholder muss existieren
if grep -q '\${mod.version}' _Info.txt; then
    printf '  %sOK%s    %-30s  Template referenziert ${mod.version} (Maven-Filter korrekt)\n' "$GREEN" "$NC" "_Info.txt"
    CHECKED=$((CHECKED + 1))
else
    printf '  %sFAIL%s  %-30s  Template-Placeholder ${mod.version} fehlt\n' "$RED" "$NC" "_Info.txt"
    FAILED=1
fi

# ── YAML-Schema Sync (vanilla-schema.yaml) ──────────────────────────
SCHEMA_YAML="tools/vanilla-schema.yaml"
if [ -f "$SCHEMA_YAML" ]; then
    # Schema-Version (Zeile 2: "mod: SyxEconomyMod vX.Y.Z")
    YAML_VER=$(grep -m1 'SyxEconomyMod v' "$SCHEMA_YAML" | grep -oE 'v[0-9]+\.[0-9]+\.[0-9]+' | head -1 || true)
    if [ -n "$YAML_VER" ]; then
        if [ "$YAML_VER" = "$TRUTH_V" ]; then
            printf '  %sOK%s    %-30s  schema_version=%s (truth=%s)\n' "$GREEN" "$NC" "$SCHEMA_YAML" "$YAML_VER" "$TRUTH"
            CHECKED=$((CHECKED + 1))
        else
            printf '  %sFAIL%s  %-30s  schema_version=%s expected=%s\n' "$RED" "$NC" "$SCHEMA_YAML" "$YAML_VER" "$TRUTH"
            FAILED=1
        fi
    fi

    # YAML↔Java Feld-Abgleich: vanilla-schema.yaml fields vs. AdapterDispatcher.register*
    DISPATCHER="src/vannon/syx/economy/adapter/AdapterDispatcher.java"
    if [ -f "$DISPATCHER" ]; then
        # Zähle registerField/registerMethod/registerClass calls im Dispatcher
        JAVA_REG=$(grep -cE 'register(Field|Method|Class)\(' "$DISPATCHER" 2>/dev/null || echo 0)
        # Zähle fields:-Einträge im YAML (alle fields-Blöcke)
        YAML_FLD=$(grep -cE '^\s+\w+:' "$SCHEMA_YAML" 2>/dev/null || echo 0)
        # Grober Abgleich: Beide sollten > 0 sein (existieren)
        if [ "$JAVA_REG" -gt 0 ] && [ "$YAML_FLD" -gt 0 ]; then
            printf '  %sOK%s    %-30s  YAML↔Java: %d schema-fields ↔ %d dispatcher-registers\n' "$GREEN" "$NC" "$SCHEMA_YAML" "$YAML_FLD" "$JAVA_REG"
            CHECKED=$((CHECKED + 1))
        else
            printf '  %sFAIL%s  %-30s  YAML↔Java mismatch: %d fields vs %d registers\n' "$RED" "$NC" "$SCHEMA_YAML" "$YAML_FLD" "$JAVA_REG"
            FAILED=1
        fi
    else
        printf '  %sINFO%s  %-30s  AdapterDispatcher.java nicht gefunden — YAML↔Java-Check übersprungen\n' "$CYAN" "$NC" "$SCHEMA_YAML"
    fi
else
    printf '  %sFAIL%s  %-30s  Datei fehlt — Schema-SSoT nicht vorhanden\n' "$RED" "$NC" "$SCHEMA_YAML"
    FAILED=1
fi

# ── 3.X Rule 3.1 Audit — mod.info global sync-invariant (Sprint v0.13.127+) ──
# Rule 3.1 (agents.md) verbietet hardcoded Versions-Strings in <mod.info>.
# AKzeptabel: <mod.info>SyxEconomyMod v${project.version}</mod.info>
# Verboten:   <mod.info>SyxEconomyMod v0.13.31-alpha: ...</mod.info>
#
# Wenn hardcoded Version gefunden: FAIL (Drift-Risk per Rule 3.1).
MOD_INFO_COUNT=$(python3 -c "
import xml.etree.ElementTree as ET
import sys
try:
    tree = ET.parse('$POM')
    ns = '{http://maven.apache.org/POM/4.0.0}'
    count = sum(1 for e in tree.iter() if e.tag == ns + 'info')
    print(count)
except Exception:
    # Fallback: strip XML comments first, then count
    import subprocess
    result = subprocess.run(
        ['sed', '-E', '-z', 's/<!--[^-]*(-->|(-[^-])|(-[^-][^-]))//g', '$POM'],
        capture_output=True, text=True, timeout=5
    )
    if result.returncode == 0:
        print(result.stdout.count('<mod.info>'))
    else:
        print(0)
" 2>/dev/null || echo 0)
if [ "$MOD_INFO_COUNT" -gt 1 ]; then
    printf '  %sFAIL%s  %-30s  <mod.info> appears %d times — expected 1 (XML-duplicate-attribute, Rule 3.1 violation)\n' "$RED" "$NC" "$POM:mod.info" "$MOD_INFO_COUNT"
    FAILED=1
fi
MOD_INFO=$(grep -oE '<mod.info>[^<]*</mod.info>' "$POM" | head -1 || true)
if [ -z "$MOD_INFO" ]; then
    printf '  %sINFO%s  %-30s  <mod.info> tag empty/missing — Rule 3.1 Audit uebersprungen\n' "$CYAN" "$NC" "$POM:mod.info"
elif echo "$MOD_INFO" | grep -qE '<mod\.info>[[:space:]]*</mod\.info>'; then
    printf '  %sFAIL%s  %-30s  <mod.info> tag empty content — Rule 3.1 violation (missing version)\n' "$RED" "$NC" "$POM:mod.info"
    FAILED=1
elif echo "$MOD_INFO" | grep -qE '\${project\.version}'; then
    printf '  %sOK%s    %-30s  <mod.info> binds to ${project.version} (Rule 3.1 compliant)\n' "$GREEN" "$NC" "$POM:mod.info"
    CHECKED=$((CHECKED + 1))
elif echo "$MOD_INFO" | grep -qE 'v[0-9]+\.[0-9]+\.[0-9]+'; then
    HARDCODED=$(echo "$MOD_INFO" | grep -oE 'v[0-9]+\.[0-9]+\.[0-9]+' | head -1 || true)
    printf '  %sFAIL%s  %-30s  <mod.info> hardcoded version=%s — Rule 3.1 violation (agents.md)\n' "$RED" "$NC" "$POM:mod.info" "$HARDCODED"
    FAILED=1
fi

# ── 3.X MD-Tool-Reference Sync (Gate 10) ──────────────────────────────
# Sprint 9 Audit-Lesson (Run 3 — final): Live-Invocation-Pattern statt alle
# md-tool-Refs. Eine zu breite Regex (Run 2) hat drei Klassen von False-
# Positives erzeugt: forward-planning-Refs (Sprint 9 plant balance-smoke.sh),
# historical-audit-Refs (scarcity_sim.py wurde in Sprint 8 geloescht) und
# meta-prohibition-Refs in agents.md (DO NOT add sync-doc-anchors.sh).
# Gate 10 final: greift nur 'python3 tools/X.py' / 'python tools/X.py' /
# 'bash tools/X.sh' (live invocation). Worktrees + .git ausgeschlossen.
# Process-Substitution statt Word-Splitting (Robustheit).
MD_INVOKE_PATTERN='(python3?|python|bash)[[:space:]]+tools/[A-Za-z0-9_.-]+\.(py|sh)([^A-Za-z0-9_.-]|$)'
MD_INVOKE_HITS=$(grep -roE "$MD_INVOKE_PATTERN" --include='*.md' \
    --exclude-dir='.freebuff' --exclude-dir='.git' --exclude-dir='docs' . 2>/dev/null \
    | grep -oE 'tools/[A-Za-z0-9_.-]+\.(py|sh)' | sort -u || true)

if [ -z "$MD_INVOKE_HITS" ]; then
    printf '  %sOK%s    %-30s  no md-tool-refs found\n' "$GREEN" "$NC" "tools/verify-doc-sync.sh:Gate10"
    CHECKED=$((CHECKED + 1))
else
    STALE_REFS=""
    while IFS= read -r ref; do
        [ -z "$ref" ] && continue
        if [ ! -f "$ref" ]; then
            STALE_REFS="${STALE_REFS} ${ref}"
        fi
    done < <(printf '%s\n' "$MD_INVOKE_HITS")
    if [ -n "$STALE_REFS" ]; then
        printf '  %sFAIL%s  %-30s  md-tool-stale-refs:%s\n' "$RED" "$NC" "tools/verify-doc-sync.sh:Gate10" "$STALE_REFS"
        FAILED=1
    else
        OK_COUNT=$(printf '%s\n' "$MD_INVOKE_HITS" | grep -cE '.+' 2>/dev/null || echo 0)
        printf '  %sOK%s    %-30s  %s md-tool-refs all resolve\n' "$GREEN" "$NC" "tools/verify-doc-sync.sh:Gate10" "${OK_COUNT:-0}"
        CHECKED=$((CHECKED + 1))
    fi
fi

# ── 3.X Version-Consolidation (Sprint v0.13.118+Governance-Diät merge) ──
# Single-Source-of-Truth: verify-doc-sync.sh konsolidiert jetzt ALLE Version-Checks.
# verify-version-consistency.sh ist DEPRECATED Thin-Wrapper fuer Backward-Compat
# mit install-hooks.sh (reserviert Bash-Skript-Namen). Die Logik hier drinnen
# ist die kanonische.

# 3a. mod.version.history aus letzten 5 git-tags regenerieren (work-on-pom Side-Effect)
if command -v git &>/dev/null && git rev-parse --git-dir &>/dev/null 2>&1; then
    TAGS=$(git tag --sort=-creatordate 2>/dev/null | head -5 | paste -sd ';' - || true)
    if [ -n "$TAGS" ] && grep -q '<mod.version.history>' "$POM"; then
        sed -i "s|<mod.version.history>.*</mod.version.history>|<mod.version.history>${TAGS}</mod.version.history>|" "$POM"
        printf '  %sOK%s    %-30s  mod.version.history aus git-tags aktualisiert: %s
' "$GREEN" "$NC" "$POM:mod.version.history" "$TAGS"
        CHECKED=$((CHECKED + 1))
    fi
fi

# 3b. mod.changelog first-entry vs pom.xml <version> (Rule 2 Stam-Sync)
CHANGELOG_FIRST=$(grep -m1 '<mod.changelog>' "$POM" | sed 's/.*<mod.changelog>//' | sed 's/;.*//' | grep -oP 'v?[0-9]+\.[0-9]+\.[0-9]+' | head -1 || true)
if [ -n "$CHANGELOG_FIRST" ]; then
    CHANGELOG_NUM=$(echo "$CHANGELOG_FIRST" | sed 's/^v//')
    if [ "$CHANGELOG_NUM" = "$POM_VERSION" ]; then
        printf '  %sOK%s    %-30s  mod.changelog first entry v%s == pom.xml %s
' "$GREEN" "$NC" "$POM:mod.changelog" "$CHANGELOG_NUM" "$POM_VERSION"
        CHECKED=$((CHECKED + 1))
    else
        printf '  %sFAIL%s  %-30s  mod.changelog first entry v%s != pom.xml %s — Rule-2 Stam-Sync drift
' "$RED" "$NC" "$POM:mod.changelog" "$CHANGELOG_NUM" "$POM_VERSION"
        FAILED=1
    fi
else
    printf '  %sWARN%s  %-30s  kein mod.changelog first-entry gefunden — uebersprungen
' "$YELLOW" "$NC" "$POM:mod.changelog"
fi

# 3c. _Info.txt Template ↔ pom.xml properties (strict via lib)
SCRIPT_DIR_VDS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_VDS="${SCRIPT_DIR_VDS}/lib/_info-txt-sync.sh"
if [ -f "$LIB_VDS" ]; then
    # shellcheck source=lib/_info-txt-sync.sh
    . "$LIB_VDS"
    if ! sync_info_txt_template_report "strict" "$POM"; then
        FAILED=1
    fi
fi

# 3d. _Info.txt deployed freshness (warn-only, non-blocking)
if [ -f "$LIB_VDS" ]; then
    sync_info_txt_deployed_report "$POM_VERSION" || true
fi

# ─────────────────────────────────────────────────────────────────────
# Gate 11: Audit-Claims Verification (Rule 3.2 — Sprint v0.13.128+)
# Scan docs/*_AUDIT*.md + docs/*_SPEC*.md auf [PM-OK: ...]-Tags und
# verifiziere sie gegen python3 tools/god-class-guard/parse_metrics.py.
# [HYP]-Tags sind Soft-Warn. Drift auf PM-OK = HARD-BLOCK.
# ─────────────────────────────────────────────────────────────────────
echo ""
echo -e "${CYAN}>>> Gate 11: Audit-Claims Verification (Rule 3.2)${NC}"
if [ -f "tools/verify-audit-claims.sh" ]; then
    if ! bash tools/verify-audit-claims.sh; then
        echo -e "${RED}  FAIL${NC}  Gate 11: Audit-Claims Verification — siehe tools/verify-audit-claims.sh output"
        FAILED=1
    fi
else
    echo -e "${YELLOW}  WARN${NC}  Gate 11 nicht ausgeführt: tools/verify-audit-claims.sh existiert nicht (Sprint v0.13.128+ Pflicht-File)."
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
