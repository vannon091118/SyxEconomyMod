#!/usr/bin/env python3
"""
tools/benchmark_compare.py — Per-column tolerance gate for benchmark CSVs.

Usage:
    python3 tools/benchmark_compare.py BASELINE.csv RUN.csv
        [--gini-tol 0.1] [--money-tol 1.0] [--price-tol 2.0]
        [--gini-abs-floor 0.0001] [--money-abs-floor 100] [--price-abs-floor 1]
        [--show-top N] [--strict]
        [--csv-out DRIFT_REPORT.csv]

Input format (4 columns, header order-flexible):
    day,gini,money_supply,median_price
    0,0.1234,50000,75
    5,0.1356,50100,75
    ...

Per-column relative tolerance:
    abs(run - baseline) <= tol_pct/100 * max(|baseline|, abs_floor)
Special case: baseline == run == 0 → trivially tolerant (drift=0).
Special case: baseline == 0, run != 0 → always a drift; pct reported as ∞.

Exit codes:
    0  all day-rows within tolerance
    1  at least one day-row drifted beyond tolerance
    2  input error (missing file, malformed CSV, bad args)

Output:
    Human-readable diff-report annotated with per-day PASS/DRIFT plus summary.
    With --csv-out, an additional machine-readable drift CSV is written.

Pure-stdlib (csv, argparse, sys, math). No external deps. Requires Python 3.8+.
"""
from __future__ import annotations

import argparse
import csv
import math
import os
import sys
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple


# ── Data containers ─────────────────────────────────────────────────────────

@dataclass
class Row:
    """One parsed CSV row. day is the join key; the other three are KPIs."""
    day: int
    gini: float
    money_supply: long  # noqa: A003 — name kept to mirror CSV header
    median_price: int


@dataclass
class Drift:
    """Per-day diff between baseline and run. pct values are relative in percent."""
    day: int
    gini_delta: float
    money_delta: long
    price_delta: int  # noqa: A003
    gini_drifted: bool
    money_drifted: bool
    price_drifted: bool
    gini_pct: float  # may be math.inf
    money_pct: float  # may be math.inf
    price_pct: float  # may be math.inf


@dataclass
class Report:
    """Aggregated comparison result."""
    baseline_path: str
    run_path: str
    parsed_baseline: int = 0
    parsed_run: int = 0
    joined_days: int = 0
    drifts: List[Drift] = field(default_factory=list)
    missing_in_run: List[int] = field(default_factory=list)
    missing_in_baseline: List[int] = field(default_factory=list)

    @property
    def drift_count(self) -> int:
        return sum(1 for d in self.drifts if d.gini_drifted
                   or d.money_drifted or d.price_drifted)

    def _max_finite_pct(self, attr: str) -> float:
        """Max finite percent across drifts (inf values skipped for display)."""
        vals = [getattr(d, attr) for d in self.drifts
                if math.isfinite(getattr(d, attr))]
        return max(vals, default=0.0)

    @property
    def max_gini_pct(self) -> float:
        return self._max_finite_pct("gini_pct")

    @property
    def max_money_pct(self) -> float:
        return self._max_finite_pct("money_pct")

    @property
    def max_price_pct(self) -> float:
        return self._max_finite_pct("price_pct")


# ── CSV parsing ─────────────────────────────────────────────────────────────

REQUIRED_HEADER = {"day", "gini", "money_supply", "median_price"}


def parse_csv(path: str) -> Tuple[Dict[int, Row], int]:
    """Parse a 4-column CSV into {day: Row}. Returns (rows, count).

    Header is matched by column name (order-flexible). Strict on missing
    columns; lenient on blank middle-lines (skip).
    """
    if not os.path.isfile(path):
        sys.stderr.write(f"ERROR: file not found: {path}\n")
        sys.exit(2)
    rows: Dict[int, Row] = {}
    with open(path, newline="") as fh:
        reader = csv.reader(fh)
        try:
            header = next(reader)
        except StopIteration:
            sys.stderr.write(f"ERROR: empty CSV: {path}\n")
            sys.exit(2)
        header_set = set(c.strip() for c in header)
        missing = REQUIRED_HEADER - header_set
        if missing:
            sys.stderr.write(
                f"ERROR: missing required column(s) in {path}: {sorted(missing)}. "
                f"Expected {sorted(REQUIRED_HEADER)} (any order).\n"
            )
            sys.exit(2)
        idx_day = header.index("day")
        idx_gini = header.index("gini")
        idx_money = header.index("money_supply")
        idx_price = header.index("median_price")
        for line_no, raw in enumerate(reader, start=2):
            if not raw or all(c == "" for c in raw):
                continue
            try:
                day = int(raw[idx_day])
                gini = float(raw[idx_gini])
                money = int(raw[idx_money])
                price = int(raw[idx_price])
            except (ValueError, IndexError) as e:
                sys.stderr.write(
                    f"ERROR: malformed row at {path}:{line_no}: {raw} ({e})\n"
                )
                sys.exit(2)
            rows[day] = Row(day, gini, money, price)
    return rows, len(rows)


# ── Tolerance check ─────────────────────────────────────────────────────────

def within_tolerance(baseline: float, run: float, tol_pct: float,
                     abs_floor: float) -> Tuple[bool, float]:
    """Return (within_tol, abs_pct_drift).

    Tolerance is relative to baseline (or abs_floor if baseline is too small).
        delta_threshold = (tol_pct / 100) * max(|baseline|, abs_floor)
        within_tol = |run - baseline| <= delta_threshold

    Special cases:
        baseline == run == 0 → within_tol=True, drift=0
        baseline == 0, run != 0 → within_tol=False, drift=math.inf
    """
    delta = abs(run - baseline)
    if baseline == 0 and run == 0:
        return True, 0.0
    if baseline == 0:
        return False, math.inf
    denom = max(abs(baseline), abs_floor)
    abs_pct_drift = (delta / denom) * 100.0
    threshold = (tol_pct / 100.0) * denom
    return delta <= threshold, abs_pct_drift


# ── Comparison ──────────────────────────────────────────────────────────────

@dataclass
class Tolerances:
    gini_tol: float
    money_tol: float
    price_tol: float
    gini_abs_floor: float
    money_abs_floor: float
    price_abs_floor: float


def compare(baseline_rows: Dict[int, Row], run_rows: Dict[int, Row],
            tols: Tolerances) -> Report:
    report = Report(
        baseline_path="",  # caller fills in
        run_path="",
        parsed_baseline=len(baseline_rows),
        parsed_run=len(run_rows),
    )
    common_days = sorted(set(baseline_rows.keys()) & set(run_rows.keys()))
    report.joined_days = len(common_days)
    for d in sorted(set(baseline_rows.keys()) - set(run_rows.keys())):
        report.missing_in_run.append(d)
    for d in sorted(set(run_rows.keys()) - set(baseline_rows.keys())):
        report.missing_in_baseline.append(d)
    for day in common_days:
        b = baseline_rows[day]
        r = run_rows[day]
        g_ok, g_pct = within_tolerance(b.gini, r.gini, tols.gini_tol,
                                       tols.gini_abs_floor)
        m_ok, m_pct = within_tolerance(b.money_supply, r.money_supply,
                                       tols.money_tol, tols.money_abs_floor)
        p_ok, p_pct = within_tolerance(b.median_price, r.median_price,
                                       tols.price_tol, tols.price_abs_floor)
        report.drifts.append(Drift(
            day=day,
            gini_delta=r.gini - b.gini,
            money_delta=r.money_supply - b.money_supply,
            price_delta=r.median_price - b.median_price,
            gini_drifted=not g_ok,
            money_drifted=not m_ok,
            price_drifted=not p_ok,
            gini_pct=g_pct,
            money_pct=m_pct,
            price_pct=p_pct,
        ))
    return report


# ── Reporting ───────────────────────────────────────────────────────────────

def _fmt_pct(p: float) -> str:
    return "∞" if math.isinf(p) else f"{p:.4f}"


def render_report(report: Report, tols: Tolerances, show_top: int,
                  strict: bool) -> str:
    out: List[str] = []
    bar = "\u2500" * 78
    out.append(bar)
    out.append(f"BENCHMARK CSV-COMPARE  baseline: {report.baseline_path}")
    out.append(f"                       run:      {report.run_path}")
    out.append(bar)
    out.append(f"  Parsed rows:   baseline={report.parsed_baseline}"
               f"  run={report.parsed_run}  joined={report.joined_days}")
    if report.missing_in_run:
        out.append(f"  Days only in baseline (drift-detector blind spot): "
                   f"{report.missing_in_run[:10]}"
                   f"{'…' if len(report.missing_in_run) > 10 else ''}")
    if report.missing_in_baseline:
        out.append(f"  Days only in run (no baseline reference): "
                   f"{report.missing_in_baseline[:10]}"
                   f"{'…' if len(report.missing_in_baseline) > 10 else ''}")
    out.append("")
    out.append(f"  Tolerances:    gini={tols.gini_tol}% (floor={tols.gini_abs_floor})  "
               f"money_supply={tols.money_tol}% (floor={tols.money_abs_floor})  "
               f"median_price={tols.price_tol}% (floor={tols.price_abs_floor})")
    out.append("")
    drifted = [d for d in report.drifts
               if d.gini_drifted or d.money_drifted or d.price_drifted]
    out.append(f"  Day-rows: {len(report.drifts)} total  "
               f"drifted: {len(drifted)}  passed: "
               f"{len(report.drifts) - len(drifted)}")
    if report.drifts:
        out.append(f"  Max finite pct-drift: gini={report.max_gini_pct:.4f}%  "
                   f"money_supply={report.max_money_pct:.4f}%  "
                   f"median_price={report.max_price_pct:.4f}%")
    out.append("")
    if drifted:
        out.append(f"  Top-{min(show_top, len(drifted))} drifted rows "
                   "(day, gini Δpp, money Δ%, price Δ%):")
        # Sort by max finite pct, inf sorted last (still surfaced but at the tail)
        drifted.sort(
            key=lambda d: tuple(
                -getattr(d, attr) if math.isfinite(getattr(d, attr)) else math.inf
                for attr in ("gini_pct", "money_pct", "price_pct")
            ),
            reverse=True,
        )
        for d in drifted[:show_top]:
            tags = []
            if d.gini_drifted: tags.append("GINI")
            if d.money_drifted: tags.append("MONEY")
            if d.price_drifted: tags.append("PRICE")
            out.append(f"    day={d.day:<5} "
                       f"GINI%={_fmt_pct(d.gini_pct):>9}  "
                       f"MONEY%={_fmt_pct(d.money_pct):>9}  "
                       f"PRICE%={_fmt_pct(d.price_pct):>9}  "
                       f"[{','.join(tags)}]")
    out.append("")
    if report.drift_count > 0:
        out.append("  >>> FAIL — drift beyond tolerance <<<")
    elif strict and (report.missing_in_run or report.missing_in_baseline):
        out.append(f"  >>> STRICT-FAIL — missing day coverage "
                   f"({len(report.missing_in_run)} days missing in run, "
                   f"{len(report.missing_in_baseline)} days missing in baseline) <<<")
    else:
        out.append("  >>> PASS — all rows within tolerance <<<")
    out.append(bar)
    return "\n".join(out)


# ── CLI ────────────────────────────────────────────────────────────────────

def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Per-column tolerance gate for benchmark CSVs",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Exit codes: 0 within tol · 1 drift · 2 input error\n"
            "Tolerances are RELATIVE percentages of baseline (or abs_floor "
            "if baseline smaller). Per-column floors prevent bounded values "
            "(gini \u2208 [0,1]) from being compared against over-large denominators."
        ),
    )
    parser.add_argument("baseline", help="Path to baseline CSV")
    parser.add_argument("run", help="Path to run CSV")
    parser.add_argument("--gini-tol", type=float, default=0.1,
                        help="Gini tolerance in PERCENT (default 0.1)")
    parser.add_argument("--money-tol", type=float, default=1.0,
                        help="money_supply tolerance in PERCENT (default 1.0)")
    parser.add_argument("--price-tol", type=float, default=2.0,
                        help="median_price tolerance in PERCENT (default 2.0)")
    parser.add_argument("--gini-abs-floor", type=float, default=0.0001,
                        help="Absolute floor for gini denominator "
                             "(default 0.0001; protects against div-by-near-zero "
                             "on small gini values, does NOT mask real drift)"
                        )
    parser.add_argument("--money-abs-floor", type=float, default=100.0,
                        help="Absolute floor for money_supply denominator "
                             "(default 100; a 99 D drift on 50000 D = 0.2 percent, "
                             "well within 1 percent threshold)")
    parser.add_argument("--price-abs-floor", type=float, default=1.0,
                        help="Absolute floor for median_price denominator "
                             "(default 1; sub-Denari noise tolerance)")
    parser.add_argument("--show-top", type=int, default=10,
                        help="Show top-N drifted rows in report (default 10)")
    parser.add_argument("--strict", action="store_true",
                        help="Treat missing day-coverage as a failure.")
    parser.add_argument("--csv-out", metavar="PATH", default=None,
                        help="Optional machine-readable drift CSV output path.")
    parser.add_argument("--quiet", action="store_true",
                        help="Suppress textual report on stdout (still exits with "
                             "the right code).")
    args = parser.parse_args(argv)

    baseline_rows, _ = parse_csv(args.baseline)
    run_rows, _ = parse_csv(args.run)

    tols = Tolerances(
        gini_tol=args.gini_tol,
        money_tol=args.money_tol,
        price_tol=args.price_tol,
        gini_abs_floor=args.gini_abs_floor,
        money_abs_floor=args.money_abs_floor,
        price_abs_floor=args.price_abs_floor,
    )
    report = compare(baseline_rows, run_rows, tols)
    report.baseline_path = args.baseline
    report.run_path = args.run

    if not args.quiet:
        print(render_report(report, tols, args.show_top, args.strict))

    if args.csv_out:
        with open(args.csv_out, "w", newline="") as fh:
            w = csv.writer(fh)
            w.writerow(["day", "gini_delta", "money_delta", "price_delta",
                        "gini_pct", "money_pct", "price_pct",
                        "drifted_columns"])
            for d in report.drifts:
                tags = []
                if d.gini_drifted: tags.append("gini")
                if d.money_drifted: tags.append("money_supply")
                if d.price_drifted: tags.append("median_price")
                w.writerow([
                    d.day,
                    f"{d.gini_delta:.6f}",
                    d.money_delta,
                    d.price_delta,
                    _fmt_pct(d.gini_pct),
                    _fmt_pct(d.money_pct),
                    _fmt_pct(d.price_pct),
                    ";".join(tags) if tags else "",
                ])

    if report.drift_count > 0:
        return 1
    if args.strict and (report.missing_in_run or report.missing_in_baseline):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
