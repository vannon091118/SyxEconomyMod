#!/usr/bin/env bash
# SyxEconomyMod — God-Class-Guard Meta-Tests
# ===========================================
# Erzeugt 4 synthetische .java-Stubs in einem temp-dir und prüft, dass
# der Guard die erwarteten Exit-Codes (PASS/WARN/BLOCK) liefert.
#
# Test-Set:
#   T1_tiny_god        viele effective LOC + 50 pubM → BLOCK (HARD-LIMIT)
#   T2_exempt_window   viele effective LOC + 50 pubM, in ui/Window* → PASS
#                      (PATTERN-EXEMPT durch saemliches File-Prefix)
#   T3_constants_dump  ~600 effective LOC + 60 fields + 0 pubM → PASS
#                      (HEURISTIC-EXEMPT)
#   T4_legacy_drift    220 effective LOC, baseline 200 LOC → BLOCK (DRIFT)
#
# Erwarteter Exit-Code: 2 (Hard-Mode bei T1+T4 BLOCK, T2+T3 PASS)

set -eo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GUARD_DIR="$SCRIPT_DIR/../../god-class-guard"

[ -d "$GUARD_DIR" ] || { echo "FATAL: $GUARD_DIR not found"; exit 2; }

# ── Setup temp-dir als fake-repo ────────────────────────────────────────
TMPDIR=$(mktemp -d)
trap "rm -rf $TMPDIR" EXIT

mkdir -p "$TMPDIR/tools/god-class-guard"
cp -r "$GUARD_DIR"/* "$TMPDIR/tools/god-class-guard/"

mkdir -p "$TMPDIR/src/vannon/syx/economy/core"
mkdir -p "$TMPDIR/src/vannon/syx/economy/ui"
mkdir -p "$TMPDIR/src/vannon/syx/economy/benchmark"

# ── Helper: schreibe 950 effective LOC als Method-Bodies (NICHT Kommentare)
#         damit _effective_loc() sie als Code zaehlt ──────────────────
write_effective_loc() {
    local target=$1
    local file=$2
    local class_name=$3
    local public_methods=$4
    local private_fields=$5

    {
        echo "public class ${class_name} {"
        # Fuelle Body einer einzigen grossen Methode auf, damit ~950 effective LOC entstehen
        # 1 Methode = 1 Statement pro Zeile (zusaetzlich oeffnende/schliessende Klammern)
        echo "    public int bigBody(int seed) {"
        local needed=$((target - public_methods - private_fields - 6))
        [ "$needed" -lt 0 ] && needed=0
        for i in $(seq 1 "$needed"); do
            echo "        int x$i = seed + $i; if (x$i % 2 == 0) { x$i = x$i * 2; }"
        done
        echo "        return seed;"
        echo "    }"
        # zusaetzliche public methods, jeweils reguläres Format
        for i in $(seq 1 "$public_methods"); do
            echo "    public int method$i(int x) { return x + $i; }"
        done
        for i in $(seq 1 "$private_fields"); do
            echo "    private int field$i;"
        done
        echo "}"
    } > "$file"
}

# ── T1: Tiny God — ~1000 effective LOC, 50 pubM → BLOCK (HARD-LIMIT) ──
write_effective_loc 1000 \
    "$TMPDIR/src/vannon/syx/economy/core/T1_TinyGod.java" \
    "T1_TinyGod" 50 18

# ── T2: Exempt Window — ~1000 effective LOC, 50 pubM, in WindowFensterX.java → PASS
# File muss Window-Prefix haben damit das Pattern '^src/.+/ui/Window[^/]*\.java$' matcht
write_effective_loc 1000 \
    "$TMPDIR/src/vannon/syx/economy/ui/WindowT2_Exempt.java" \
    "WindowT2_Exempt" 50 5

# ── T3: Constants-Dump — ~60 effective LOC, 60 fields, 0 pubM → PASS (HEURISTIC)
{
    echo "public class T3_ConstantsDump {"
    for i in $(seq 1 60); do echo "    public static final int CONSTANT_$i = $i;"; done
    echo "}"
} > "$TMPDIR/src/vannon/syx/economy/core/T3_ConstantsDump.java"

# ── T4: Legacy Drift — 220 effective LOC, baseline 200 → BLOCK (DRIFT)
write_effective_loc 220 \
    "$TMPDIR/src/vannon/syx/economy/core/T4_LegacyDrift.java" \
    "T4_LegacyDrift" 2 2

# ── Baselines-YAML mit T4 grandfathered (LOC=200, Drift-Cap = 200 + 5%) ─
cat > "$TMPDIR/tools/god-class-baselines.yml" <<'YAML'
version: 1.0
exempt_patterns:
  - {regex: '^src/.+/ui/Window[^/]*\.java$', rule: 'Rule 6 (UI-Windows sakrosankt)'}
  - {regex: '^src/.+/benchmark/[^/]*\.java$', rule: 'Benchmark-Bundle'}
heuristic_exemptions:
  - {id: 'constants-dump', rule: 'fields>=50 AND pubM==0', applies_to: 'fields_cap_only'}
legacy_baselines:
  'src/vannon/syx/economy/core/T4_LegacyDrift.java':
    loc: 200
    pubM: 2
    fields: 2
    imports: 0
YAML

# ── Run guard in temp-dir ──────────────────────────────────────────────
echo "[meta-test] running tools/god-class-guard in temp-dir: $TMPDIR"
(cd "$TMPDIR" && python3 tools/god-class-guard/run_check.py --mode=hard)
exit_code=$?

# ── Validation ──────────────────────────────────────────────────────────
[ "$exit_code" -eq 2 ] || {
    echo ""
    echo "[meta-test] FAIL: Expected exit=2, got $exit_code"
    echo "Erwartung: T1+T4 BLOCK, T2+T3 PASS"
    exit 1
}

echo ""
echo "[meta-test] PASS — exit=2 wie erwartet (T1+T4 BLOCK; T2+T3 PASS)"
exit 0
