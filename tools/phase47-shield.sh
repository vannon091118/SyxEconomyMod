#!/usr/bin/env bash
# tools/phase47-shield.sh — Phase-4.7 CI guard.
#
# Verhindert dass die Bug-Klassen die wir gerade fixen
# (IdentityHashMap-Datenverlust, EngineSeams-Reflection-Lock-in,
# verschluckte Exceptions) wieder in den Source-Tree zurückgebaut werden.
#
# Zwei-Schwellen-Strategie:
#   THRESHOLD: aktueller Stand v0.1.4 (PASS = keine Regression,
#              FAIL = neue Violation hinzugekommen). Default-Modus.
#   TARGET:    post-Phase-4.7 Ziel. Über --strict-target aktivierbar
#              (FAIL bis v0.2.0-Ziele erreicht sind). Tracking-Modus.
#
# Exit codes:
#   0 = alle aktiven Gates grün
#   1 = Drift erkannt (THRESHOLD oder TARGET überschritten)
#   2 = Tool-/Setup-Fehler

set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Arg-Parsing — `while [[ $# -gt 0 ]]` umgeht bash-Quirks mit leerem $@.
STRICT_TARGET=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --strict-target) STRICT_TARGET=1; shift ;;
        -h|--help)
            cat <<'EOF'
Aufruf: bash tools/phase47-shield.sh [--strict-target]
Default (ohne Flag): Regression-Guard — fail nur bei Threshold-Drift.
--strict-target: zusätzlich fail wenn post-Phase-4.7 Ziel nicht erreicht.
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
MAX_CATCH_THROWABLE=2
MAX_DIRECT_ENGINESEAMS=40
MAX_IDENTITYHASH_NONREGISTRY=10
MAX_PRINTSTACKTRACE=0

# TARGETS — post-Phase-4.7. Bei --strict-target: fail bis hier.
TGT_CATCH_THROWABLE=0
TGT_DIRECT_ENGINESEAMS=0
TGT_IDENTITYHASH_NONREGISTRY=0
TGT_PRINTSTACKTRACE=0

fail=0

mode_label="regression-guard"
if (( STRICT_TARGET == 1 )); then
    mode_label="strict-target"
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
# Zählt alle method-calls 'EngineSeams.identifier(' — Uppercase UND lowercase
# (Java-Konvention: lowercase Häufiger als Helper, Uppercase als Type/Enum-Const).
# Kommentare/Javadoc ohne '(' werden ignoriert.
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

# ---- Threshold-Check ----
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
echo "    EngineSeams.-Method-Calls in core/   : $direct_calls / Threshold $MAX_DIRECT_ENGINESEAMS / Target $TGT_DIRECT_ENGINESEAMS"    echo "    catch (Throwable) in core/             : $cthrows / Threshold $MAX_CATCH_THROWABLE / Target $TGT_CATCH_THROWABLE"
    echo "    printStackTrace() in core/              : $pstrace / Threshold $MAX_PRINTSTACKTRACE / Target $TGT_PRINTSTACKTRACE"

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
        echo "[phase47-shield] PASS — keine Threshold-Drift."
    fi
    exit 0
else
    echo "[phase47-shield] FAIL — Details oben." >&2
    exit 1
fi
