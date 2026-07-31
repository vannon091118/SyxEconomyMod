#!/usr/bin/env bash
# tools/verify-audit-claims.sh — Audit-Claims Verification (Gate 11, Rule 3.2)
# ================================================================
# Sprint v0.13.128+Audit-Claims-Verification: scannt docs/*AUDIT*.md + docs/*SPEC*.md
# nach [PM-OK: <file>:<metric>=<value>]-Tags, vergleicht jeden Tag-Wert gegen
# python3 tools/god-class-guard/parse_metrics.py Live-Output. Drift = FAIL.
#
# Tag-Syntax: [PM-OK: <Filename>:<metric>=<value>]
#             - <Filename> ohne Pfad (z.B. "WindowState.java"), find-resolution dynamisch
#             - <metric> ∈ {loc, fields, pubM, imports}
#             - <value> ganzzahlig
#             - detached-Strings ("method=format") werden ignoriert
#
# [HYP] / [HYP: <reason>]-Tags sind Soft-Warn (kein block).

set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

PARSE_METRICS="tools/god-class-guard/parse_metrics.py"

# Scopes (verified-only): docs/*_AUDIT*.md and docs/*_SPEC*.md
# Hypothesis-only docs (e.g. UI_GRID_LAYOUT_SPEC.md design-specs) erlauben [HYP]
# werden aber mitgezählt als Warnung wenn keine PM-OK existiert.
SCOPE_DOCS=()
while IFS= read -r doc_file; do
    [ -n "$doc_file" ] && SCOPE_DOCS+=("$doc_file")
done < <(find docs -type f -name '*.md' \( -iname '*_AUDIT*.md' -o -iname '*_SPEC*.md' \) 2>/dev/null | sort)

if [ ${#SCOPE_DOCS[@]} -eq 0 ]; then
    echo -e "${YELLOW}WARN${NC}  Keine Audit/Spec-Dokumente in docs/ gefunden — Gate 11 no-op."
    exit 0
fi

echo -e "${CYAN}>>> Gate 11: Audit-Claims Verification (Rule 3.2)${NC}"
echo ""

pm_ok_total=0
pm_ok_drift_fail=0
hyp_total=0
hyp_unknown_file=0

# Cache: file -> metric -> live_value
declare -A LIVE_CACHE

check_pm_ok_tag() {
    local doc_file="$1"
    local file_name="$2"
    local metric="$3"
    local tag_value="$4"
    local line_no="$5"

    pm_ok_total=$((pm_ok_total + 1))

    # Cache-Key: "<file_name>:<metric>"
    local cache_key="${file_name}:${metric}"
    local live_value="${LIVE_CACHE[$cache_key]:-}"

    if [ -z "$live_value" ]; then
        # File-Resolution: find src/ -name <file_name>
        local found_path
        found_path=$(find src -name "$file_name" -type f 2>/dev/null | head -1 || true)
        if [ -z "$found_path" ]; then
            echo -e "${RED}  FAIL${NC}  ${doc_file}:${line_no}  [PM-OK: ${file_name}:${metric}=${tag_value}]  → file nicht gefunden"
            pm_ok_drift_fail=$((pm_ok_drift_fail + 1))
            LIVE_CACHE["$cache_key"]="__NOTFOUND__"
            return 1
        fi
        # parse_metrics-Aufruf, JSON-Output grep metric
        local metric_line
        metric_line=$(python3 "$PARSE_METRICS" "$found_path" 2>/dev/null | grep -E "\"${metric}\"" | head -1 || true)
        if [ -z "$metric_line" ]; then
            echo -e "${RED}  FAIL${NC}  ${doc_file}:${line_no}  [PM-OK: ${file_name}:${metric}=${tag_value}]  → parse_metrics liefert kein '${metric}'"
            pm_ok_drift_fail=$((pm_ok_drift_fail + 1))
            LIVE_CACHE["$cache_key"]="__PARSEFAIL__"
            return 1
        fi
        # JSON-Parsing: extrahiere Wert aus "metric": N,
        live_value=$(echo "$metric_line" | sed -E "s/.*\"${metric}\":[[:space:]]*([0-9]+).*/\1/")
        LIVE_CACHE["$cache_key"]="$live_value"
    fi

    if [ "$live_value" = "__NOTFOUND__" ] || [ "$live_value" = "__PARSEFAIL__" ]; then
        pm_ok_drift_fail=$((pm_ok_drift_fail + 1))
        return 1
    fi

    if [ "$tag_value" != "$live_value" ]; then
        echo -e "${RED}  DRIFT${NC} ${doc_file}:${line_no}  [PM-OK: ${file_name}:${metric}=${tag_value}]  → live=${live_value} (DIFF!)"
        pm_ok_drift_fail=$((pm_ok_drift_fail + 1))
        return 1
    fi

    echo -e "${GREEN}  OK${NC}   ${doc_file}:${line_no}  [PM-OK: ${file_name}:${metric}=${tag_value}] == live=${live_value}"
    return 0
}

check_hyp_tag() {
    local doc_file="$1"
    local reason="$2"
    local line_no="$3"
    hyp_total=$((hyp_total + 1))
    if [ -z "$reason" ]; then
        echo -e "${YELLOW}  HYP${NC}  ${doc_file}:${line_no}  [HYP]  (no reason specified)"
    else
        echo -e "${YELLOW}  HYP${NC}  ${doc_file}:${line_no}  [HYP: ${reason}]"
    fi
}

# Regex-Iter (pro Doc): extrahiere [PM-OK: ...] und [HYP: ...] / [HYP]
# Sprint v0.13.128+ Fix: pipe-Pattern removed → process-substitution < <() damit
# Counter pm_ok_total / pm_ok_drift_fail / hyp_total im parent-shell propagieren.
for doc_file in "${SCOPE_DOCS[@]}"; do
    # [PM-OK: <file>:<metric>=<num>]   (detached metadata wie method= werden akzeptiert aber gewarnet)
    while IFS= read -r line; do
        [ -z "$line" ] && continue
        line_no=$(echo "$line" | cut -d: -f1)
        rest=$(echo "$line" | cut -d: -f2-)
        # Erfasse Tag bis erstes `]`
        tag_content=$(echo "$rest" | grep -oE '\[PM-OK: [^]]*\]' | head -1)
        if [ -z "$tag_content" ]; then
            continue
        fi
        # Strip brackets
        inner=$(echo "$tag_content" | sed -E 's/^\[PM-OK: //; s/\]$//')
        # Format:  <Filename>:<metric>=<value>
        if ! echo "$inner" | grep -qE '^[A-Za-z][A-Za-z0-9_.]*\.java:[a-zA-Z]+=[0-9]+$'; then
            # Akzeptiere auch custom-method= als Warnung
            if echo "$inner" | grep -qE '^[A-Za-z][A-Za-z0-9_.]*\.java:method='; then
                echo -e "${YELLOW}WARN${NC}  ${doc_file}:${line_no}  [PM-OK-with-method-tag]  $tag_content (method= detached, skipped from verification)"
                continue
            fi
            echo -e "${RED}WARN${NC} ${doc_file}:${line_no}  [PM-OK-malformed]  $tag_content"
            continue
        fi
        file_name=$(echo "$inner" | cut -d: -f1)
        metric=$(echo "$inner" | cut -d: -f2 | cut -d= -f1)
        tag_value=$(echo "$inner" | cut -d= -f2)
        check_pm_ok_tag "$doc_file" "$file_name" "$metric" "$tag_value" "$line_no" || true
    done < <(grep -nE '\[PM-OK:' "$doc_file" 2>/dev/null)

    # [HYP] und [HYP: reason]
    while IFS= read -r line; do
        [ -z "$line" ] && continue
        line_no=$(echo "$line" | cut -d: -f1)
        reason=$(echo "$line" | grep -oE '\[HYP: [^]]*\]' | head -1 | sed -E 's/^\[HYP: //; s/\]$//')
        check_hyp_tag "$doc_file" "$reason" "$line_no" || true
    done < <(grep -nE '\[HYP:' "$doc_file" 2>/dev/null)
done

# ===============================================================
# Sprint v0.13.128+Anti-Regression-Audit-Decay:
# Unparsed-Claim-Detection (Soft-WARN heute, HARD-BLOCK ab v0.13.130+)
# ===============================================================
# Scannt jedes Scope-Doc auf numerische Claims (SLOC/LOC/fields/pubM/imports
# und <File>.java:<line>-Patterns) die KEIN [PM-OK:…] oder [HYP:…]-Tag tragen.
#
# Heute (v0.13.128+Anti-Regression): nur Soft-WARN-Report — keine Hard-Block.
# Ab v0.13.130+: HARD-BLOCK wenn `unparsed_claim_warn > 0` (Future-Stagger, siehe agents.md Rule 3.3).
#
# Wichtig: \b in POSIX ERE grep -E ist nicht portabel (GNU-Extension). Wir
# benutzen POSIX-only Patterns ohne \b, dafür mit character-class
# Excludes für Wort-Ränder. Das macht die Detection robust gegen alle
# grep-Implementationen (GNU, BSD, busybox).
unparsed_claim_warn=0
unparsed_claim_lines_total=0

# Pattern A — Metric-Claim: numerische Zahl gefolgt von Unit-Keyword.
# Beispiele die matchen: "612 SLOC", "257 fields", "33 imports", "75 pubM",
# "100 public methods", "120.5 SLOC".
# Pattern B — <File>.java:<line>: Java-File-Reference mit Line-Number.
# Beispiele: "WindowState.java:269", "CompactNumber.java:20".
PATTERN_NUMERIC='[0-9]+(\.[0-9]+)?[[:space:]]+(SLOC|LOC|fields|pubM|imports|public methods)(s|[[:space:]]|$|,|\.|;)'
PATTERN_FILE_LINEREF='[A-Za-z][A-Za-z0-9_]*\.java:[0-9]+'

for doc_file in "${SCOPE_DOCS[@]}"; do
    # Hole alle Kandidaten-Zeilen (mit Line-Number)
    candidates=$(grep -nE "${PATTERN_NUMERIC}|${PATTERN_FILE_LINEREF}" "$doc_file" 2>/dev/null || true)
    # Filter: keine Tag-Träger-Zeilen, keine Markdown-Bullet-Zeilen
    filtered=$(echo "$candidates" | grep -vE '\[PM-OK:|\[HYP:|^[0-9]+:[[:space:]]*[-#>*]' || true)
    [ -z "$filtered" ] && continue

    unparsed_claim_lines_total=$((unparsed_claim_lines_total + $(echo "$filtered" | wc -l)))

    # Pro Kandidat-Zeile: zeige ersten Match-Substring + Line-Number
    printed=0
    while IFS= read -r line; do
        [ -z "$line" ] && continue
        line_no=$(echo "$line" | cut -d: -f1)
        # Claim-Substring extrahieren (erstes Vorkommen)
        claim_text=$(echo "$line" | grep -oE "${PATTERN_FILE_LINEREF}|[0-9]+(\.[0-9]+)?[[:space:]]+(SLOC|LOC|fields|pubM|imports|public methods)" | head -1)
        [ -z "$claim_text" ] && continue
        echo -e "${YELLOW}  UNPARSED-CLAIM${NC} ${doc_file}:${line_no}  \`${claim_text}\`"
        unparsed_claim_warn=$((unparsed_claim_warn + 1))
        printed=$((printed + 1))
        if [ "$printed" -ge 50 ]; then
            break
        fi
    done < <(echo "$filtered")
done

if [ "$unparsed_claim_warn" -gt 0 ]; then
    echo -e "${YELLOW}  NOTE${NC}  $unparsed_claim_warn unparsed claims detected (Soft-WARN, heute kein Block)"
    echo "  TODO-List: Autor MUSS in Folge-Sprint (v0.13.129+) jeden Eintrag mit [PM-OK:…] oder [HYP:…] taggen oder loeschen."
    if [ "$unparsed_claim_warn" -ge 50 ]; then
        echo "  [showing first 50 of $unparsed_claim_warn — Counter ist genauer]"
    fi
fi

echo ""
echo -e "${CYAN}==============================================================${NC}"
echo -e "Stats: PM-OK tags=${pm_ok_total} (drift-fail=${pm_ok_drift_fail}), HYP tags=${hyp_total}, unparsed-claims=${unparsed_claim_warn}"
if [ "$pm_ok_drift_fail" -gt 0 ]; then
    echo -e "${RED}  FAIL${NC}  — ${pm_ok_drift_fail} [PM-OK]-Tags sind gedriftet (parse_metrics liefert anderen Wert)."
    echo "  Fix: Tag-Wert updaten oder regenerate via Tag-Autofix-Script (siehe Sprint v0.13.128+ Memory-Save)."
    exit 1
fi
echo -e "${GREEN}  PASS${NC}  — alle [PM-OK]-Tags reproduzierbar; HYP-Tags dokumentiert; ${unparsed_claim_warn} unparsed claims als TODO-List (Soft-WARN, HARD-BLOCK ab v0.13.130+)."
exit 0
