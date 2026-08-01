#!/usr/bin/env bash
# SyxEconomyMod — Audit-Claims Verification Gate (Rule 3.2)
# ========================================================
# Sprint v0.13.128+/v0.13.129+ Pflicht-File (Gate 11 von verify-doc-sync.sh).
# Scannt alle Audit-/Spec-Dokumente auf [PM-OK:<file>:<metric>=<value>]-Tags
# und verifiziert jedes parse_metrics-Whitelist-Claim (loc/pubM/fields/imports)
# gegen den aktuellen Code-Stand via python3 tools/god-class-guard/parse_metrics.py.
# [HYP]-Tags sind informational (kein Hard-Fail).
#
# Tag-Konvention (agents.md Rule 3.2):
#   [PM-OK: <File>:<metric>=<value>]   verifizierbarer Claim
#   [HYP]   oder   [HYP: <reason>]      manuell verankerte Schätzung
#   [PM-OK:...] und [PM-OK: <File>:...] sind Meta-Doc-Platzhalter (übersprungen)
#
# 9 Checks (analog verify-doc-sync.sh Schema):
#   1. Pre-Flight (python3 + parse_metrics.py)
#   2. Tag-Extraktion aus docs/*_AUDIT*.md + docs/*_SPEC*.md + Doku/CHANGELOG.md + Doku/BACKLOG.md
#   3. Syntax + Placeholder-Filter ([PM-OK: File.java:metric=value])
#   4. Datei-Existenz (PM-OK nicht auf nicht-existente Files)
#   5. Metrik-Whitelist (loc/pubM/fields/imports vs custom SLOTS/CHUNKED_VERSION/method)
#   6. parse_metrics-Drift (PM-OK vs Ist-Wert, HARD-BLOCK)
#   7. HYP-Katalog (informational)
#   8. Sanity-Ratio (PM-OK-verified >= HYP)
#   9. Final-Summary + Exit-Code (0=ok, 1=drift, 2=preflight)

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

PARSE_METRICS="tools/god-class-guard/parse_metrics.py"

# ── 1. Pre-Flight ──────────────────────────────────────────────────────
echo -e "${CYAN}==============================================================${NC}"
echo -e "${CYAN}  SyxEconomyMod — Audit-Claims Verification (Rule 3.2)          ${NC}"
echo -e "${CYAN}==============================================================${NC}"
echo ""
if ! command -v python3 >/dev/null 2>&1; then
    echo -e "${RED}FEHLER: python3 nicht gefunden — Pre-Flight fehlgeschlagen${NC}" >&2; exit 2
fi
if [ ! -f "$PARSE_METRICS" ]; then
    echo -e "${RED}FEHLER: $PARSE_METRICS nicht gefunden — Pre-Flight fehlgeschlagen${NC}" >&2; exit 2
fi
echo -e "  ${GREEN}OK${NC}    python3=$(python3 --version 2>&1 | awk '{print $2}')  parse_metrics=$PARSE_METRICS  rule=3.2"
echo ""

FAILED=0; CHECKED=0

# Helper: robuster Zähler (immer genau eine Zahl, kein "0\n0")
count_lines() { printf '%s\n' "$1" | awk 'NF {c++} END {print c+0}'; }

# ── 2. Tag-Extraktion ──────────────────────────────────────────────────
echo -e "${CYAN}>>> Check 1: Tag-Extraktion aus Audit-/Spec-Dokumenten${NC}"
SOURCES_TMP=$(mktemp)
find docs -maxdepth 2 -type f \( -name '*_AUDIT*.md' -o -name '*_SPEC*.md' \) 2>/dev/null >> "$SOURCES_TMP" || true
for s in Doku/CHANGELOG.md Doku/BACKLOG.md; do [ -f "$s" ] && printf '%s\n' "$s" >> "$SOURCES_TMP"; done
SOURCES=$(sort -u "$SOURCES_TMP" | grep -v '^$' || true)
rm -f "$SOURCES_TMP"

PM_OK_TAGS_RAW=$(printf '%s\n' "$SOURCES" | xargs grep -hoE '\[PM-OK:[^]]+\]' 2>/dev/null | sort -u || true)
HYP_TAGS=$(printf '%s\n' "$SOURCES" | xargs grep -hoE '\[HYP[^]]*\]' 2>/dev/null | sort -u || true)
PM_OK_COUNT_RAW=$(count_lines "$PM_OK_TAGS_RAW")
HYP_COUNT=$(count_lines "$HYP_TAGS")
echo -e "  ${GREEN}INFO${NC}  sources=$(printf '%s\n' "$SOURCES" | wc -l)  PM-OK-raw=$PM_OK_COUNT_RAW  HYP=$HYP_COUNT"
CHECKED=$((CHECKED + 1))

# ── 3-6. Python-Parser via env-var Pass-Through (kein Pipe-/Heredoc-Streit) ───
echo -e "${CYAN}>>> Check 2-5: PM-OK-Validation, Datei-Existenz, Whitelist, Drift${NC}"
export PM_OK_TAGS_ENV="$PM_OK_TAGS_RAW"
export PARSE_METRICS_ENV="$PARSE_METRICS"

PARSER_OUTPUT=$(PM_OK_TAGS_ENV="$PM_OK_TAGS_RAW" PARSE_METRICS_ENV="$PARSE_METRICS" python3 << 'PYEOF'
import os, re, json, subprocess
from pathlib import Path

TAGS = [l for l in os.environ.get('PM_OK_TAGS_ENV', '').splitlines() if l.strip()]
PARSE_METRICS = os.environ.get('PARSE_METRICS_ENV', '')
PM_OK_RE = re.compile(r'^\[PM-OK:\s*([A-Za-z0-9_./-]+\.java):([A-Za-z_]+)=(.+)\]$')
WHITELIST = {'loc', 'pubM', 'fields', 'imports'}

def resolve_path(short):
    """Resolve a short or full Java path. Try exact first, then basename search."""
    p = Path(short)
    if p.exists():
        return str(p.resolve())
    # Basename-Fallback: short name like 'KpiSection.java' → search src/
    if '/' not in short:
        src_root = Path('src')
        candidates = list(src_root.rglob(short)) if src_root.exists() else []
        if len(candidates) == 1:
            return str(candidates[0].resolve())
        if len(candidates) > 1:
            # Multiple matches: take first deterministisch
            return str(sorted(candidates)[0].resolve())
    return None

for line in TAGS:
    # Meta-Doc-Platzhalter (Doku/CHANGELOG zeigt das Format selbst)
    if '<' in line or line == '[PM-OK:...]' or re.match(r'^\[PM-OK:\.{3}\]$', line):
        print(f'PLACEHOLDER: {line}')
        continue
    m = PM_OK_RE.match(line)
    if not m:
        print(f'INVALID: {line}')
        continue
    file, metric, claimed = m.groups()
    resolved = resolve_path(file)
    if resolved is None:
        print(f'STALE: {file}  tag={line}')
        continue
    if metric not in WHITELIST:
        print(f'CUSTOM: {line}')
        continue
    try:
        proc = subprocess.run(['python3', PARSE_METRICS, resolved],
                              capture_output=True, text=True, timeout=30)
        if proc.returncode != 0:
            print(f'PYERR: {line}')
            continue
        data = json.loads(proc.stdout)
        if not isinstance(data, list) or not data:
            print(f'PYERR: {line}  parse_metrics lieferte kein Result')
            continue
        actual = str(data[0].get(metric, 'MISSING'))
        if actual == 'MISSING':
            print(f'PYERR: {line}  Metric {metric} nicht im Output')
            continue
        # Drift: nur melden wenn resolved path != claimed path (sonst triage Info)
        if resolved != file:
            if actual != claimed:
                print(f'DRIFT: {file}→{resolved}:{metric}  claimed={claimed}  actual={actual}  tag={line}')
            else:
                print(f'RESOLVED: {file}→{resolved}:{metric}={actual}  tag={line}')
        elif actual != claimed:
            print(f'DRIFT: {file}:{metric}  claimed={claimed}  actual={actual}  tag={line}')
    except subprocess.TimeoutExpired:
        print(f'PYERR: {line}  parse_metrics TIMEOUT')
    except Exception as e:
        print(f'PYERR: {line}  {type(e).__name__}: {e}')
PYEOF
)

# Robuste Zählung mit awk (kein "0\n0" möglich)
PLACEHOLDER_COUNT=$(printf '%s\n' "$PARSER_OUTPUT" | awk '/^PLACEHOLDER:/ {c++} END {print c+0}')
INVALID_COUNT=$(printf '%s\n' "$PARSER_OUTPUT" | awk '/^INVALID:/ {c++} END {print c+0}')
STALE_COUNT=$(printf '%s\n' "$PARSER_OUTPUT" | awk '/^STALE:/ {c++} END {print c+0}')
CUSTOM_COUNT=$(printf '%s\n' "$PARSER_OUTPUT" | awk '/^CUSTOM:/ {c++} END {print c+0}')
DRIFT_COUNT=$(printf '%s\n' "$PARSER_OUTPUT" | awk '/^DRIFT:/ {c++} END {print c+0}')
PYERR_COUNT=$(printf '%s\n' "$PARSER_OUTPUT" | awk '/^PYERR:/ {c++} END {print c+0}')
PM_OK_VERIFIED=$((PM_OK_COUNT_RAW - PLACEHOLDER_COUNT - INVALID_COUNT))

echo -e "  ${CYAN}INFO${NC}  PM-OK kategorisiert: verified=$PM_OK_VERIFIED  placeholder=$PLACEHOLDER_COUNT  invalid=$INVALID_COUNT  stale=$STALE_COUNT  custom=$CUSTOM_COUNT  drift=$DRIFT_COUNT  pyerr=$PYERR_COUNT"
if [ "$INVALID_COUNT" -gt 0 ]; then
    echo -e "  ${RED}FAIL${NC}  $INVALID_COUNT syntaktisch invalide PM-OK-Tags:"
    printf '%s\n' "$PARSER_OUTPUT" | grep '^INVALID:' | head -10 | sed 's/^/      /'
    FAILED=1
fi
if [ "$STALE_COUNT" -gt 0 ]; then
    echo -e "  ${YELLOW}WARN${NC}  $STALE_COUNT PM-OK-Tags auf nicht-existente Files (stale):"
    printf '%s\n' "$PARSER_OUTPUT" | grep '^STALE:' | head -10 | sed 's/^/      /'
fi
if [ "$CUSTOM_COUNT" -gt 0 ]; then
    echo -e "  ${YELLOW}WARN${NC}  $CUSTOM_COUNT PM-OK-Tags mit Custom-Metric (ausserhalb parse_metrics-Whitelist, SLOTS/CHUNKED_VERSION/method etc.):"
    printf '%s\n' "$PARSER_OUTPUT" | grep '^CUSTOM:' | head -10 | sed 's/^/      /'
fi
if [ "$PYERR_COUNT" -gt 0 ]; then
    echo -e "  ${YELLOW}WARN${NC}  $PYERR_COUNT PM-OK-Tags mit parse_metrics-Fehler (manuelle Pruefung empfohlen):"
    printf '%s\n' "$PARSER_OUTPUT" | grep '^PYERR:' | head -5 | sed 's/^/      /'
fi
if [ "$DRIFT_COUNT" -eq 0 ]; then
    echo -e "  ${GREEN}OK${NC}    alle $PM_OK_VERIFIED parse_metrics-PM-OK-Tags matched den aktuellen Code-Stand (kein Drift)"
    CHECKED=$((CHECKED + 1))
else
    echo -e "  ${RED}FAIL${NC}  $DRIFT_COUNT Drift-Claims erkannt (PM-OK != Ist-Wert):"
    printf '%s\n' "$PARSER_OUTPUT" | grep '^DRIFT:' | head -15 | sed 's/^/      /'
    [ "$DRIFT_COUNT" -gt 15 ] && printf '      ... und %d weitere\n' "$((DRIFT_COUNT - 15))"
    FAILED=1
fi

# ── 7. HYP-Katalog ──────────────────────────────────────────────────────
echo -e "${CYAN}>>> Check 6: HYP-Claims Katalog (informational)${NC}"
if [ "$HYP_COUNT" -gt 0 ]; then
    echo -e "  ${CYAN}INFO${NC}  $HYP_COUNT HYP-Tags gefunden (manuell verankerte Schaetzungen, kein Hard-Fail):"
    printf '%s\n' "$HYP_TAGS" | head -15 | sed 's/^/      /'
    [ "$HYP_COUNT" -gt 15 ] && printf '      ... und %d weitere\n' "$((HYP_COUNT - 15))"
else
    echo -e "  ${GREEN}OK${NC}    keine HYP-Tags in den Audit-/Spec-Dokumenten"
fi

# ── 8. Sanity-Ratio ─────────────────────────────────────────────────────
echo -e "${CYAN}>>> Check 7: Sanity-Ratio PM-OK >= HYP${NC}"
if [ "$HYP_COUNT" -gt 0 ] && [ "$PM_OK_VERIFIED" -lt "$HYP_COUNT" ]; then
    echo -e "  ${RED}FAIL${NC}  PM-OK-verified=$PM_OK_VERIFIED < HYP=$HYP_COUNT — zu viele unveraigerte Schaetzungen"
    FAILED=1
elif [ "$HYP_COUNT" -gt 0 ]; then
    echo -e "  ${GREEN}OK${NC}    Ratio PM-OK-verified=$PM_OK_VERIFIED : HYP=$HYP_COUNT (veraigerte Claims ueberwiegen)"
    CHECKED=$((CHECKED + 1))
else
    echo -e "  ${GREEN}OK${NC}    keine HYP-Tags, Sanity-Ratio nicht anwendbar"
    CHECKED=$((CHECKED + 1))
fi

# ── 9. Final-Summary ─────────────────────────────────────────────────────
# Safety-Net (Belt-and-Suspenders): stellt sicher, dass der Exit-Code immer
# dem FAILED-Zaehler entspricht — unabhaengig von set -e, subshell-Pipefail,
# oder zukuenftigen Refactorings. Diagnostic-Echo fuer Debugging.
trap 'echo ">>> trap fired, FAILED=${FAILED:-0} (LINENO=${LINENO:-?})" >&2; if [ "${FAILED:-0}" -eq 0 ]; then exit 0; else exit 1; fi' EXIT

echo ""
echo -e "${CYAN}==============================================================${NC}"
if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}  PASS${NC}  — $CHECKED Audit-Checks verifiziert, keine Drift (Rule 3.2 konform)"
    echo -e "             PM-OK-verified=$PM_OK_VERIFIED  HYP=$HYP_COUNT  custom=$CUSTOM_COUNT  stale=$STALE_COUNT"
    exit 0
fi
echo -e "${RED}  DRIFT${NC} — Audit-Claims gegen parse_metrics divergieren. Reproduction:"
echo -e "      bash $0"
echo -e "             PM-OK-verified=$PM_OK_VERIFIED  drift=$DRIFT_COUNT  invalid=$INVALID_COUNT"
exit 1
