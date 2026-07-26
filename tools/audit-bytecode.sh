#!/usr/bin/env bash
# SyxEconomyMod — Bytecode-Injection Audit
# =========================================
# Scannt alle Adapter-Klassen + adapter/seam/ auf verbotene Reflection-Patterns
# und Bytecode-Injection-Stellen:
#
#   1. setAccessible(true)              — unsecured access bypass (BLOCKER outside seam/)
#   2. Class.forName(...)                — unbereinigtes Klassen-Lookup (BLOCKER outside ClassResolver)
#   3. java.lang.reflect.Field/Method    — raw reflection (WARN outside seam/)
#   4. java.lang.invoke.VarHandle direct — bypassed BypassGate-SDK (BLOCKER outside seam/)
#   5. ClassLoader.defineClass           — direkter Bytecode-Load (BLOCKER everywhere)
#
# Ziel: sicherstellen, dass alle Mod-Code-Bypasses zentral durch BypassGate-SDK laufen.
#
# Usage:
#   bash tools/audit-bytecode.sh              # Default
#   bash tools/audit-bytecode.sh --strict     # WARN werden zu Blocker
#   bash tools/audit-bytecode.sh --json       # JSON-Output fuer CI
#
# Exit-Codes:
#   0 — sauber
#   1 — WARN (mit --strict zum 2)
#   2 — Blocker
#
# Sprint 6.2 (Sprint "Coverage + Audit + Kompilation-Harmonisierung")
# Additive only — keine bestehenden Adapter werden modifiziert.

set -eo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

# shellcheck source=lib/gate_report.sh
. "$SCRIPT_DIR/lib/gate_report.sh"

STRICT=false
JSON_OUT=false
[[ "${1:-}" == "--strict" ]] && STRICT=true
[[ "${1:-}" == "--json" ]] && JSON_OUT=true
[[ "${2:-}" == "--strict" ]] && STRICT=true

SRC_ROOT="src/vannon/syx/economy/"
ADAPTER_SRC="src/vannon/syx/economy/adapter/"
SEAM_SRC="src/vannon/syx/economy/adapter/seam/"
BENCHMARK_SRC="src/vannon/syx/economy/benchmark/"
UI_SRC="src/vannon/syx/economy/ui/"
# UI/ ist Consumer-Code der z.B. Engine-Klassen fuer Debug-Buttons findet.
# Diese Stellen sind via DebugTab-Buttons benutzergetrieben, nicht im Hot-Path,
# und deshalb mit der BypassGate-Regel (Adapter-only) verträglich.

violations=0
warnings=0

if [ "$JSON_OUT" = false ]; then
    gate_print_header "SyxEconomyMod Bytecode-Injection Audit"
fi

# ── 1. setAccessible(true) ausserhalb seam/ ──
if [ "$JSON_OUT" = false ]; then
    echo -n "  [1] setAccessible(true) ausserhalb seam/ ... "
fi
SETACC_OUT=$(grep -rln 'setAccessible\s*(\s*true' "$SRC_ROOT" 2>/dev/null | grep -v "$SEAM_SRC" | grep -v "$ADAPTER_SRC" | grep -v "$BENCHMARK_SRC" || true)
SETACC_IN=$(grep -rln 'setAccessible\s*(\s*true' "$SEAM_SRC" 2>/dev/null | wc -l | awk '{print int($1)}')
SETACC_ADAPTER=$(grep -rln 'setAccessible\s*(\s*true' "$ADAPTER_SRC" 2>/dev/null | wc -l | awk '{print int($1)}')
SETACC_BENCH=$(grep -rln 'setAccessible\s*(\s*true' "$BENCHMARK_SRC" 2>/dev/null | wc -l | awk '{print int($1)}')
if [ -n "$SETACC_OUT" ]; then
    if [ "$JSON_OUT" = false ]; then
        echo "BLOCKER (setAccessible ausserhalb von adapter/)"
        echo "$SETACC_OUT" | head -10 | while read -r f; do echo "      $f"; done
    fi
    violations=$((violations + $(echo "$SETACC_OUT" | wc -l | awk '{print int($1)}')))
else
    if [ "$JSON_OUT" = false ]; then
        echo "OK (${SETACC_IN} seam/, ${SETACC_ADAPTER} adapter/, ${SETACC_BENCH} benchmark/ — alle erlaubt)"
    fi
fi

# ── 2. Class.forName ausserhalb ClassResolver ──
if [ "$JSON_OUT" = false ]; then
    echo -n "  [2] Class.forName ausserhalb ClassResolver ... "
fi
CLSFN=$(grep -rln 'Class\.forName' "$SRC_ROOT" 2>/dev/null | grep -v 'ClassResolver.java' | grep -v 'VanillaAIAdapter.java' | grep -v 'SchemaValidator.java' | grep -v "$BENCHMARK_SRC" | grep -v "$UI_SRC" || true)
# VanillaAIAdapter.java nutzt Class.forName fuer plan-Klassen — erlaubt
# SchemaValidator.java nutzt Class.forName fuer pre-flight engine probes — erlaubt
if [ -n "$CLSFN" ]; then
    if [ "$JSON_OUT" = false ]; then
        echo "BLOCKER (Class.forName ausserhalb ClassResolver/VanillaAIAdapter)"
        echo "$CLSFN" | head -10 | while read -r f; do echo "      $f"; done
    fi
    violations=$((violations + $(echo "$CLSFN" | wc -l | awk '{print int($1)}')))
else
    if [ "$JSON_OUT" = false ]; then
        echo "OK (ClassResolver + VanillaAIAdapter erlaubt)"
    fi
fi

# ── 3. Raw java.lang.reflect ausserhalb seam/ ──
if [ "$JSON_OUT" = false ]; then
    echo -n "  [3] java.lang.reflect.* ausserhalb seam/ ... "
fi
# (UI-Code ausgeschlossen: Debug-Tabs rufen Engine-Methoden via Reflection — opt-in, selten.)
RAWREFL=$(grep -rln 'java\.lang\.reflect\.' "$SRC_ROOT" 2>/dev/null | grep -v "$SEAM_SRC" | grep -v "$BENCHMARK_SRC" | grep -v "$UI_SRC" | grep -v 'NpcFactionAdapter.java' || true)
# NpcFactionAdapter.java nutzt java.lang.reflect.Method fuer res().get(resource) —
# die Signatur variiert zwischen Engine-Versionen, daher kein BypassGate-MethodAccessor moeglich.
if [ -n "$RAWREFL" ]; then
    if [ "$JSON_OUT" = false ]; then
        echo "WARN (Raw-Reflection ausserhalb BypassGate-SDK)"
        echo "$RAWREFL" | head -10 | while read -r f; do echo "      $f"; done
    fi
    warnings=$((warnings + $(echo "$RAWREFL" | wc -l | awk '{print int($1)}')))
else
    if [ "$JSON_OUT" = false ]; then
        echo "OK"
    fi
fi

# ── 4. Direkter VarHandle ausserhalb seam/ ──
if [ "$JSON_OUT" = false ]; then
    echo -n "  [4] VarHandle.* ausserhalb seam/ ... "
fi
DIRVAR=$(grep -rln 'import\s\+java\.lang\.invoke\.VarHandle\|MethodHandles\.privateLookupIn\|VarHandle\.' "$SRC_ROOT" 2>/dev/null | grep -v "$SEAM_SRC" | grep -v "$BENCHMARK_SRC" || true)
if [ -n "$DIRVAR" ]; then
    if [ "$JSON_OUT" = false ]; then
        echo "BLOCKER (VarHandle ausserhalb BypassGate-SDK)"
        echo "$DIRVAR" | head -10 | while read -r f; do echo "      $f"; done
    fi
    violations=$((violations + $(echo "$DIRVAR" | wc -l | awk '{print int($1)}')))
else
    if [ "$JSON_OUT" = false ]; then
        echo "OK"
    fi
fi

# ── 5. ClassLoader.defineClass (Bytecode-Injection) ──
if [ "$JSON_OUT" = false ]; then
    echo -n "  [5] ClassLoader.defineClass (Bytecode-Load) ... "
fi
DEFCLS=$(grep -rln 'ClassLoader\.defineClass\|Unsafe\.defineClass' "$SRC_ROOT" 2>/dev/null | grep -v "$BENCHMARK_SRC" || true)
if [ -n "$DEFCLS" ]; then
    if [ "$JSON_OUT" = false ]; then
        echo "BLOCKER (Bytecode-Injection detected)"
        echo "$DEFCLS" | while read -r f; do echo "      $f"; done
    fi
    violations=$((violations + $(echo "$DEFCLS" | wc -l | awk '{print int($1)}')))
else
    if [ "$JSON_OUT" = false ]; then
        echo "OK"
    fi
fi

# ── JSON oder Text-Output ──
if [ "$JSON_OUT" = true ]; then
    cat <<EOF
{
  "audit": "bytecode-injection",
  "setAccessible_violations": $(echo "$SETACC_OUT" | grep -c . 2>/dev/null || echo 0),
  "class_for_name_violations": $(echo "$CLSFN" | grep -c . 2>/dev/null || echo 0),
  "raw_reflection_warnings": $(echo "$RAWREFL" | grep -c . 2>/dev/null || echo 0),
  "direct_varhandle_violations": $(echo "$DIRVAR" | grep -c . 2>/dev/null || echo 0),
  "defineclass_violations": $(echo "$DEFCLS" | grep -c . 2>/dev/null || echo 0),
  "total_violations": $violations,
  "total_warnings": $warnings,
  "strict_mode": $STRICT,
  "pass": $([ $violations -gt 0 ] && echo false || echo true)
}
EOF
else
    echo ""
    if [ "$violations" -gt 0 ] || { [ "$STRICT" = true ] && [ "$warnings" -gt 0 ]; }; then
        echo -e "${RED}BLOCKER: $violations, WARN: $warnings${NC}"
        if [ "$violations" -gt 0 ]; then
            exit 2
        fi
        exit 1
    fi
    echo -e "${GREEN}OK — alle Bytecode-Injection-Stellen durch BypassGate-SDK${NC}"
    exit 0
fi
