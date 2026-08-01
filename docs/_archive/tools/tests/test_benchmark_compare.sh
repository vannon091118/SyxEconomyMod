#!/usr/bin/env bash
# tools/tests/test_benchmark_compare.sh — smoke-test for benchmark_compare
#
# Generates synthetic CSV pairs at 5 drift levels and validates exit codes:
#   1. Identical CSVs → exit 0 (PASS)
#   2. Within-tolerance drift (< tolerance) → exit 0 (PASS)
#   3. Just-out-of-tolerance drift (gini +0.0011 absolute on gini=0.5 → 0.22%) → exit 1
#   4. Large drift → exit 1 (FAIL)
#   5. Missing file → exit 2 (ERROR)
#
# Run: bash tools/tests/test_benchmark_compare.sh
# Exit 0 if all 5 scenarios pass.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPARE_SH="$ROOT/tools/benchmark-compare.sh"
COMPARE_PY="$ROOT/tools/benchmark_compare.py"
TMP="${TMPDIR:-/tmp}"
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

# -- fixtures -----------------------------------------------------------------
mkdir -p "$TMP/bct"
cat > "$TMP/bct/baseline.csv" <<'EOF'
day,gini,money_supply,median_price
0,0.1000,50000,75
5,0.1234,50123,80
10,0.1500,50300,85
15,0.1760,50500,90
20,0.2000,50600,95
EOF

cat > "$TMP/bct/run_within.csv" <<'EOF'
day,gini,money_supply,median_price
0,0.1001,50009,75
5,0.1235,50125,81
10,0.1501,50310,86
15,0.1761,50510,90
20,0.2001,50610,96
EOF

cat > "$TMP/bct/run_just_drift.csv" <<'EOF'
day,gini,money_supply,median_price
0,0.1012,50100,75
5,0.1246,50233,81
10,0.1512,50400,87
15,0.1772,50600,91
20,0.2012,50700,97
EOF

cat > "$TMP/bct/run_huge_drift.csv" <<'EOF'
day,gini,money_supply,median_price
0,0.1000,50000,75
5,0.3000,80000,200
10,0.7000,200000,500
15,0.9000,400000,1500
20,0.9500,800000,3000
EOF

cat > "$TMP/bct/run_bad_header.csv" <<'EOF'
day,gini,money,bogus
0,0.10,50000,75
5,0.12,50123,80
EOF

# -- run tests ----------------------------------------------------------------
echo "═══ tools/tests/test_benchmark_compare.sh ═══"
echo ""

# 1. Identical → 0
RC=$(bash "$COMPARE_SH" "$TMP/bct/baseline.csv" "$TMP/bct/baseline.csv" >/dev/null 2>&1; echo $?)
assert_equal "1_identical_csvs" 0 "$RC"

# 2. Within tolerance → 0
RC=$(bash "$COMPARE_SH" "$TMP/bct/baseline.csv" "$TMP/bct/run_within.csv" >/dev/null 2>&1; echo $?)
assert_equal "2_within_tolerance_drift" 0 "$RC"

# 3. Just-out-of-tolerance (gini +0.0012 abs on gini=0.1-0.2 → 0.6%-1.2% abs) → 1
RC=$(bash "$COMPARE_SH" "$TMP/bct/baseline.csv" "$TMP/bct/run_just_drift.csv" >/dev/null 2>&1; echo $?)
assert_equal "3_just_out_of_tolerance" 1 "$RC"

# 4. Huge drift → 1
RC=$(bash "$COMPARE_SH" "$TMP/bct/baseline.csv" "$TMP/bct/run_huge_drift.csv" >/dev/null 2>&1; echo $?)
assert_equal "4_huge_drift" 1 "$RC"

# 5. Missing file → 2
RC=$(bash "$COMPARE_SH" "$TMP/bct/baseline.csv" "$TMP/bct/nofile.csv" >/dev/null 2>&1; echo $?)
assert_equal "5_missing_file" 2 "$RC"

# 6. Missing column (bad CSV header) → 2
RC=$(bash "$COMPARE_SH" "$TMP/bct/baseline.csv" "$TMP/bct/run_bad_header.csv" >/dev/null 2>&1; echo $?)
assert_equal "6_missing_column" 2 "$RC"

# 7. Strict mode on a dataset with full coverage should still pass
RC=$(bash "$COMPARE_SH" "$TMP/bct/baseline.csv" "$TMP/bct/run_within.csv" --strict >/dev/null 2>&1; echo $?)
assert_equal "7_strict_strict_mode_passes" 0 "$RC"

# 8. Direct python call with a slightly different setup
RC=$(python3 "$COMPARE_PY" "$TMP/bct/baseline.csv" "$TMP/bct/run_huge_drift.csv" --quiet; echo $?)
assert_equal "8_python_direct_drift" 1 "$RC"

# 9. Help is reachable
RC=$(bash "$COMPARE_SH" --help >/dev/null 2>&1; echo $?)
assert_equal "9_help_exits_0" 0 "$RC"

# 10. python3 is available (otherwise skip)
if command -v python3 >/dev/null 2>&1; then
    echo "  PASS: python3 available"
    PASS_COUNT=$((PASS_COUNT + 1))
else
    echo "  FAIL: python3 missing"
    FAIL_COUNT=$((FAIL_COUNT + 1))
fi

echo ""
echo "═══ Results: ${PASS_COUNT} passed, ${FAIL_COUNT} failed ═══"
rm -rf "$TMP/bct"
[ "$FAIL_COUNT" -gt 0 ] && exit 1 || exit 0
