#!/usr/bin/env bash
# tools/tests/test_scenario_snapshot.sh — smoke-test for scenario_snapshot
#
# 6 furniture-pieces tested:
#   1. --help exits 0
#   2. generate-verify on default fixture produces bench-baseline.save with
#      all required ScriptGuide fields
#   3. custom mod override works
#   4. determinism: same args twice → byte-identical file
#   5. missing custom-dir → verify exit 2
#   6. malformed Pre-Spec (corrupted YAML) → verify exit 1
#
# All scenarios use a sandbox TMP dir; no fixtures left in real mods/saves.
#
# Run: bash tools/tests/test_scenario_snapshot.sh
# Exit 0 if all 6 cases pass.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SNAP_PY="$ROOT/tools/scenario_snapshot.py"
VER_PY="$ROOT/tools/scenario_verify.py"
SNAP_SH="$ROOT/tools/scenario-snapshot.sh"
TMP="${TMPDIR:-/tmp}"
SANDBOX="$TMP/snx_bench"
PASS_COUNT=0
FAIL_COUNT=0

assert_equal() {
    local name="$1" expected="$2" actual="$3"
    if [ "$expected" = "$actual" ]; then
        echo "  PASS: $name (exit=$actual)"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo "  FAIL: $name — expected=$expected actual=$actual"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
}

mkdir -p "$SANDBOX/custom"
trap 'rm -rf "$SANDBOX"' EXIT

# --- 1. --help ----------------------------------------------------------------
RC=$(bash "$SNAP_SH" --help >/dev/null 2>&1; echo $?)
assert_equal "1_help_exits_0" 0 "$RC"

# --- 2. default generate+verify ----------------------------------------------
OUT="$SANDBOX/custom/bench-baseline.save"
RC=$(python3 "$SNAP_PY" --out "$OUT" >/tmp/snap_default.txt 2>&1; echo $?)
assert_equal "2a_default_generate" 0 "$RC"
RC=$(python3 "$VER_PY" "$SANDBOX/custom" >/tmp/verify_default.txt 2>&1; echo $?)
assert_equal "2b_default_verify" 0 "$RC"
if [ -f "$OUT" ]; then
    size=$(wc -c < "$OUT" | tr -d ' ')
    echo "  INFO: bench-baseline.save = $size bytes"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo "  FAIL: 2c_default_file_exists"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi
# Verify required fields present
if python3 -c "
with open('$OUT') as f: content = f.read()
required = ['population: 50', 'world_x: 128', 'world_y: 64',
            'seed: 1392191', '- id: SyxEconomyMod', 'note:']
missing = [r for r in required if r not in content]
if missing:
    print('MISSING:', missing); exit(2)
print('OK: required fields present')
" >/tmp/verify_default_fields.txt 2>&1; then
    echo "  PASS: 2d_required_fields_in_yaml"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo "  FAIL: 2d_required_fields_in_yaml"
    cat /tmp/verify_default_fields.txt
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# --- 3. custom mod override ---------------------------------------------------
OUT2="$SANDBOX/custom/custom-bench.save"
RC=$(python3 "$SNAP_PY" --out "$OUT2" \
    --mods MyMod:1.2.3 OtherMod:0.5 \
    --population 75 --seed 42 \
    >/tmp/snap_custom.txt 2>&1; echo $?)
assert_equal "3a_custom_mod_generate" 0 "$RC"
if python3 -c "
with open('$OUT2') as f: content = f.read()
checks = ['population: 75', 'seed: 42', '- id: MyMod', 'version: \"1.2.3\"',
          '- id: OtherMod', 'version: \"0.5\"']
missing = [c for c in checks if c not in content]
if missing: print('MISSING:', missing); exit(2)
print('OK')
" >/dev/null 2>&1; then
    echo "  PASS: 3b_custom_mod_in_yaml"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo "  FAIL: 3b_custom_mod_in_yaml"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# --- 4. determinism: same params twice → byte-identical ----------------------
OUT3="$SANDBOX/custom/determinism-A.save"
OUT4="$SANDBOX/custom/determinism-B.save"
python3 "$SNAP_PY" --out "$OUT3" --seed 999 >/dev/null 2>&1
python3 "$SNAP_PY" --out "$OUT4" --seed 999 >/dev/null 2>&1
if cmp -s "$OUT3" "$OUT4"; then
    echo "  PASS: 4_byte_determinism_same_seed"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo "  FAIL: 4_byte_determinism_same_seed"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# --- 5. missing custom-dir → verify exit 2 -----------------------------------
RC=$(python3 "$VER_PY" "$SANDBOX/nonexistent-dir" >/dev/null 2>&1; echo $?)
assert_equal "5_missing_custom_dir_exits_2" 2 "$RC"

# --- 6. malformed Pre-Spec → verify exit 1 -----------------------------------
BAD="$SANDBOX/custom/baseline-corrupted.save"
cp "$OUT" "$BAD"
# Corrupt: remove YAML end marker → unparseable
python3 -c "
data = open('$BAD', 'rb').read()
b'---END-YAML-SPEC-V1---\\n'.encode() if False else b'---END-YAML-SPEC-V1---\\n'
end = b'---END-YAML-SPEC-V1---\n'
idx = data.index(end)
open('$BAD', 'wb').write(data[:idx] + b'CORRUPTED_PAYLOAD\n')
"
RC=$(python3 "$VER_PY" "$SANDBOX/custom" --target baseline-corrupted.save \
    >/dev/null 2>&1; echo $?)
assert_equal "6_corrupted_yaml_exits_1" 1 "$RC"

# --- 7. python3 available gate -----------------------------------------------
if command -v python3 >/dev/null 2>&1; then
    echo "  PASS: 7_python3_available"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo "  FAIL: 7_python3_available"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

echo ""
echo "═══ Results: ${PASS_COUNT} passed, ${FAIL_COUNT} failed ═══"
[ "$FAIL_COUNT" -gt 0 ] && exit 1 || exit 0
