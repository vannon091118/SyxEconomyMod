#!/usr/bin/env bash
# tools/phase47-shield.sh — Phase-4.7 CI guard.
#
# Verhindert dass die Bug-Klassen die wir gerade fixen
# (IdentityHashMap-Datenverlust, EngineSeams-Reflection-Lock-in,
# verschluckte Exceptions) wieder in den Source-Tree zurückgebaut werden.
#
# Zwei-Schwellen-Strategie (verbindlich ab v0.13.129+Mode-Selection-Sprint):
#   THRESHOLD: aktueller Stand v0.1.4 (PASS = keine Regression,
#              FAIL = neue Violation hinzugekommen).
#   TARGET:    post-Phase-4.7 Ziel. Über --strict-target aktivierbar.
#
# Sub-Rule 15.1 ab v0.13.129+ — Mode-Selection (Sprint v0.13.131+Doc-Diet:
# absolute ist NICHT mehr Default — siehe MODE-Initialisierung weiter unten):
#   --mode=absolute (default): misst current-counts vs THRESHOLD/TARGET.
#                              Regression-Detection: JEDE Drift nach oben failt.
#                              Entry-Point für Pre-Production-Sweeps und Pre-Commit-Hook.
#   --mode=delta-only         : misst current-counts - baseline-counts > 0 als Block.
#                              Routinen-Sprint-Modus: PRE-EXISTING violations IGNORIEREN.
#                              Erfordert `.git/hooks/.phase47-baseline` (siehe
#                              `tools/phase47-baseline.sh capture`).
#
# Exit codes:
#   0 = alle aktiven Gates grün
#   1 = Drift erkannt (THRESHOLD oder TARGET überschritten, oder Delta-Regression)
#   2 = Tool-/Setup-Fehler

set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Arg-Parsing — `while [[ $# -gt 0 ]]` umgeht bash-Quirks mit leerem $@.
STRICT_TARGET=0
MODE="delta-only"   # Sprint v0.13.131+Doc-Diet: Default-Flip; Pre-existing grandfathered
while [[ $# -gt 0 ]]; do
    case "$1" in
        --strict-target) STRICT_TARGET=1; shift ;;
        --mode=absolute) MODE="absolute"; shift ;;
        --mode=delta-only) MODE="delta-only"; shift ;;
        -h|--help)
            cat <<'EOF'
Aufruf: bash tools/phase47-shield.sh [--strict-target] [--mode=absolute|--mode=delta-only]
Default:                delta-only — pre-existing Counts ignoriert, nur NEUE Drift failt.
                        Ohne Baseline-File automatischer Fallback auf absolute + WARN.
--strict-target          fail zusätzlich wenn post-Phase-4.7 Ziel nicht erreicht.
--mode=absolute          counts vs THRESHOLD/TARGET (Phase-4.7-Stand-Prüfung).
--mode=delta-only        counts > baseline_counts = REGRESSION.
EOF
            exit 0
            ;;
        *)
            echo "[FAIL] unbekanntes Argument: $1" >&2
            exit 2
            ;;
    esac
done

# Allow-list (Phase-4.7: hier sitzen die erlaubten IdentityHashMap-Verwendungen).
ALLOW_REGISTRY="IdentityMapRegistry.java"
ALLOW_KEYS="IdentityKeys.java"

# THRESHOLDS — auf/über v0.1.4-Real-Wahrheit. Drift = neue Violation.
MAX_CATCH_THROWABLE=10
MAX_DIRECT_ENGINESEAMS=55
MAX_IDENTITYHASH_NONREGISTRY=12
MAX_PRINTSTACKTRACE=0

# TARGETS — post-Phase-4.7. Bei --strict-target: fail bis hier.
TGT_CATCH_THROWABLE=0
TGT_DIRECT_ENGINESEAMS=0
TGT_IDENTITYHASH_NONREGISTRY=0
TGT_PRINTSTACKTRACE=0

# Baseline-File (shared mit post-commit-shield.sh).
BASELINE=".git/hooks/.phase47-baseline"

fail=0
mode_label="$MODE"
if (( STRICT_TARGET == 1 )); then
    mode_label="${MODE}+strict-target"
fi

echo "[phase47-shield] $(date '+%Y-%m-%d %H:%M:%S') — mode=$mode_label"

if [[ ! -d src/vannon/syx/economy/core ]]; then
    echo "[FAIL] src/vannon/syx/economy/core/ fehlt — Projektstruktur-Erkennung versagt" >&2
    exit 2
fi

# ---- Gate 1: IdentityHashMap in core/ ausserhalb Allow-List ----
drift_files=$(
    { grep -rln 'new IdentityHashMap' src/vannon/syx/economy/core/ 2>/dev/null \
        | grep -vE "$ALLOW_REGISTRY|$ALLOW_KEYS" || true; }
)
drift_count=0
if [[ -n "$drift_files" ]]; then
    drift_count=$(printf '%s\n' "$drift_files" | wc -l)
fi

# ---- Gate 2: Direkte EngineSeams.-Method-Calls in core/ ----
direct_calls=$(
    { grep -rEn '\bEngineSeams\.[a-zA-Z][a-zA-Z0-9_]*\(' src/vannon/syx/economy/core/ 2>/dev/null \
        || true; } | wc -l
)

# ---- Gate 3: catch (Throwable) in core/ ----
cthrows=$(
    { grep -rEn 'catch \(Throwable' src/vannon/syx/economy/core/ 2>/dev/null \
        || true; } | wc -l
)

# ---- Gate 4: printStackTrace() in core/ ----
pstrace=$(
    { grep -rEn 'printStackTrace\(\)' src/vannon/syx/economy/core/ 2>/dev/null \
        | grep -v '//' || true; } | wc -l
)

# ---- Delta-only: Baseline laden, Deltas berechnen, Block wenn >0 ----
b_IH=0
b_ES=0
b_CT=0
b_PS=0
b_SHA="(none)"
if [[ "$MODE" == "delta-only" ]]; then
    if [[ ! -f "$BASELINE" ]]; then
        echo "[HINT] Kein Baseline-File vorhanden, delta-only nicht möglich. Fallback auf absolute." >&2
        echo "[HINT] Optional eigene Baseline setzen: phase47-baseline.sh capture" >&2
        MODE="absolute"
    else
        # shellcheck source=/dev/null
        source "$BASELINE"
        b_IH="${IDENTITYHASH:-0}"
        b_ES="${ENGINESEAMS:-0}"
        b_CT="${CATCH_THROWABLE:-0}"
        b_PS="${PRINTSTACKTRACE:-0}"
        b_SHA="${BASELINE_SHA:-unknown}"
    fi
fi

d_IH=$((drift_count - b_IH))
d_ES=$((direct_calls - b_ES))
d_CT=$((cthrows - b_CT))
d_PS=$((pstrace - b_PS))

# ---- Threshold-Check (nur im absolute-Modus) ----
# Im delta-only-Modus werden nur die Delta-Checks unten ausgefuehrt.
# Pre-existing Violations werden ignoriert — nur NEUE zaehlen.
if [[ "$MODE" == "absolute" ]]; then
    if (( drift_count > MAX_IDENTITYHASH_NONREGISTRY )); then
        echo "[FAIL][threshold] $drift_count Dateien mit 'new IdentityHashMap' (Limit $MAX_IDENTITYHASH_NONREGISTRY):"
        while IFS= read -r f; do
            [[ -n "$f" ]] && echo "         - $f"
        done <<< "$drift_files"
        fail=1
    fi
    if (( direct_calls > MAX_DIRECT_ENGINESEAMS )); then
        echo "[FAIL][threshold] $direct_calls direkte EngineSeams.-Method-Calls in core/ (Limit $MAX_DIRECT_ENGINESEAMS)"
        fail=1
    fi
    if (( cthrows > MAX_CATCH_THROWABLE )); then
        echo "[FAIL][threshold] $cthrows 'catch (Throwable)' in core/ (Limit $MAX_CATCH_THROWABLE)"
        fail=1
    fi
    if (( pstrace > MAX_PRINTSTACKTRACE )); then
        echo "[FAIL][threshold] $pstrace 'printStackTrace()' in core/ (Limit $MAX_PRINTSTACKTRACE — war 124 in v0.1.2!)"
        fail=1
    fi
fi

# ---- Delta-only-Check ----
# Block wenn current_counts > baseline_counts (= neue Violations seit Sprint-Start).
if [[ "$MODE" == "delta-only" ]]; then
    if (( d_IH > 0 )); then
        echo "[FAIL][delta] IdentityHashMap: $drift_count > $b_IH (delta=+$d_IH NEUE Violations)"
        fail=1
    fi
    if (( d_ES > 0 )); then
        echo "[FAIL][delta] EngineSeams.-calls: $direct_calls > $b_ES (delta=+$d_ES NEUE Violations)"
        fail=1
    fi
    if (( d_CT > 0 )); then
        echo "[FAIL][delta] catch(Throwable): $cthrows > $b_CT (delta=+$d_CT NEUE Violations)"
        fail=1
    fi
    if (( d_PS > 0 )); then
        echo "[FAIL][delta] printStackTrace(): $pstrace > $b_PS (delta=+$d_PS NEUE Violations)"
        fail=1
    fi
fi

# ---- Target-Gap-Check (nur bei --strict-target) ----
gap_i=$((drift_count - TGT_IDENTITYHASH_NONREGISTRY))
gap_e=$((direct_calls - TGT_DIRECT_ENGINESEAMS))
gap_c=$((cthrows - TGT_CATCH_THROWABLE))
gap_p=$((pstrace - TGT_PRINTSTACKTRACE))
gap_total=$((gap_i + gap_e + gap_c + gap_p))

if (( STRICT_TARGET == 1 )); then
    if (( drift_count > TGT_IDENTITYHASH_NONREGISTRY )); then
        echo "[FAIL][target] IdentityHashMap: $drift_count → $TGT_IDENTITYHASH_NONREGISTRY ($gap_i offen)"
        fail=1
    fi
    if (( direct_calls > TGT_DIRECT_ENGINESEAMS )); then
        echo "[FAIL][target] EngineSeams-Method-Calls: $direct_calls → $TGT_DIRECT_ENGINESEAMS ($gap_e offen)"
        fail=1
    fi
    if (( cthrows > TGT_CATCH_THROWABLE )); then
        echo "[FAIL][target] catch (Throwable): $cthrows → $TGT_CATCH_THROWABLE ($gap_c offen)"
        fail=1
    fi
    if (( pstrace > TGT_PRINTSTACKTRACE )); then
        echo "[FAIL][target] printStackTrace: $pstrace → $TGT_PRINTSTACKTRACE ($gap_p offen)"
        fail=1
    fi
fi

# ---- Report ----
glyph_open="WARTE_AUF_PHASE_4.7"
echo ""
echo "[phase47-shield] Messung:"
echo "    IdentityHashMap (ausserhalb Allow-List): $drift_count / Threshold $MAX_IDENTITYHASH_NONREGISTRY / Target $TGT_IDENTITYHASH_NONREGISTRY"
echo "    EngineSeams.-Method-Calls in core/      : $direct_calls / Threshold $MAX_DIRECT_ENGINESEAMS / Target $TGT_DIRECT_ENGINESEAMS"
echo "    catch (Throwable) in core/                : $cthrows / Threshold $MAX_CATCH_THROWABLE / Target $TGT_CATCH_THROWABLE"
echo "    printStackTrace() in core/                : $pstrace / Threshold $MAX_PRINTSTACKTRACE / Target $TGT_PRINTSTACKTRACE"

# Delta-only: Baseline + Delta-Anzeige
if [[ "$MODE" == "delta-only" ]]; then
    echo ""
    echo "[phase47-shield] Delta-Check (baseline-SHA=$b_SHA):"
    fmt() {
        local label="$1" cur="$2" base="$3" delta="$4"
        if (( delta > 0 )); then
            echo "    $label: $base → $cur  (delta=+$delta)  ❌ NEUE Violations"
        elif (( delta < 0 )); then
            echo "    $label: $base → $cur  (delta=$delta)  ✓ verbessert"
        else
            echo "    $label: $base → $cur  (delta=0)   ✓ unverändert"
        fi
    }
    fmt "IdentityHashMap"   "$drift_count"   "$b_IH"   "$d_IH"
    fmt "EngineSeams-Calls" "$direct_calls"  "$b_ES"   "$d_ES"
    fmt "catch(Throwable)"  "$cthrows"       "$b_CT"   "$d_CT"
    fmt "printStackTrace"   "$pstrace"       "$b_PS"   "$d_PS"
fi

if (( STRICT_TARGET == 1 )); then
    echo ""
    echo "[phase47-shield] Target-Gap (post-Phase-4.7):"
    echo "    IdentityHashMap:    $([[ $gap_i -eq 0 ]] && echo OK || echo "$glyph_open (gap=$gap_i)")"
    echo "    EngineSeams-Calls:  $([[ $gap_e -eq 0 ]] && echo OK || echo "$glyph_open (gap=$gap_e)")"
    echo "    catch (Throwable):  $([[ $gap_c -eq 0 ]] && echo OK || echo "$glyph_open (gap=$gap_c)")"
    echo "    printStackTrace:   $([[ $gap_p -eq 0 ]] && echo OK || echo "$glyph_open (gap=$gap_p)")"
fi

# ---- Exit ----
echo ""
if (( fail == 0 )); then
    if (( STRICT_TARGET == 1 )) && (( gap_total == 0 )); then
        echo "[phase47-shield] PASS — Phase 4.7 fertig."
    else
        echo "[phase47-shield] PASS — keine Drift."
    fi
    exit 0
else
    echo "[phase47-shield] FAIL — Details oben." >&2
    echo "[phase47-shield] Hinweis: Sprint-Start → bash tools/phase47-baseline.sh capture setzt Baseline" >&2
    exit 1
fi
