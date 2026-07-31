#!/usr/bin/env python3
"""
Gini-Episode-Analyzer fuer SyxEconomyMod rebalance_macro*.csv

Filtert Gini > 0.60 Episoden und korreliert mit:
- Steuerrate         (head_tax, direkt aus CSV)
- Subventionen       (subsidy_proxy = max(0, -treasury_delta)  — Treasury-Verbrauch)
- Markt-Steuer-Aktivitaet (market_steu = market_receipts + warehouse_bought + warehouse_sold)
- Migrations-Welle   (migration_in = inherited, migration_out = emigrations+deaths)

Output:
- Tabellen-Report nach stdout
- tools/gini_episode_report.txt (persistenter Report)

Verwendung:
    python3 tools/gini_episode_analyzer.py [threshold]
        threshold default = 0.60
"""

import csv
import math
import statistics
import sys
from pathlib import Path

DIAG_DIR = Path.home() / ".local" / "share" / "songsofsyx" / "mods" / "SyxEconomyMod" / "diagnostics"
REPORT_PATH = Path(__file__).parent / "gini_episode_report.txt"


def latest_csv(prefix: str):
    files = sorted(DIAG_DIR.glob(f"{prefix}*.csv"))
    return files[-1] if files else None


def parse_csv(path: Path):
    """Returns list[dict] mit automatischer Zahlen-Konvertierung."""
    rows = []
    with path.open("r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for raw in reader:
            row = {}
            for k, v in raw.items():
                try:
                    row[k] = float(v) if v not in (None, "") else 0.0
                except ValueError:
                    row[k] = v  # String belassen
            rows.append(row)
    return rows


def compute_proxies(rows):
    """Berechnet Steuer-/Subsidien-/Markt-/Migrations-Proxies pro Tag."""
    for i, r in enumerate(rows):
        prev = rows[i - 1] if i > 0 else rows[i]
        r["treasury_delta"] = r.get("treasury", 0.0) - prev.get("treasury", 0.0)
        r["subsidy_proxy"] = max(0.0, -r["treasury_delta"])
        r["market_steu"] = r.get("market_receipts", 0.0)  # Steuereinnahmen Markt
        r["market_vol"] = (  # Markt-Volumen Total (zu Diagnose-Zwecken behalten)
            r.get("market_receipts", 0.0)
            + r.get("warehouse_bought", 0.0)
            + r.get("warehouse_sold", 0.0)
        )
        r["migration_in"] = r.get("inherited", 0.0)
        r["migration_out"] = r.get("emigrations", 0.0) + r.get("deaths", 0.0)
        r["migration_balance"] = r["migration_in"] - r["migration_out"]
    return rows


def find_gini_episodes(rows, threshold):
    """Liefert [(start_idx, end_idx, mean_gini, max_gini)]."""
    episodes = []
    in_e = False
    start = 0
    for i, r in enumerate(rows):
        g = r.get("gini", 0.0)
        if g > threshold and not in_e:
            start = i
            in_e = True
        if (g <= threshold or i == len(rows) - 1) and in_e:
            chunk = rows[start : i + 1]
            mean_g = sum(x.get("gini", 0.0) for x in chunk) / len(chunk)
            max_g = max(x.get("gini", 0.0) for x in chunk)
            episodes.append((start, i, mean_g, max_g))
            in_e = False
    return episodes


def summarize(rows, lo, hi, var):
    arr = [r.get(var, 0.0) or 0.0 for r in rows[lo : hi + 1]]
    if not arr:
        return None
    return {
        "mean": statistics.mean(arr),
        "sum": sum(arr),
        "min": min(arr),
        "max": max(arr),
        "n": len(arr),
    }


def pearson(a, b):
    """Pearson-Korrelation; None wenn undefiniert."""
    n = min(len(a), len(b))
    if n < 5:
        return None
    a = a[:n]
    b = b[:n]
    ma = sum(a) / n
    mb = sum(b) / n
    cov = sum((a[i] - ma) * (b[i] - mb) for i in range(n)) / n
    sa = math.sqrt(sum((x - ma) ** 2 for x in a) / n)
    sb = math.sqrt(sum((x - mb) ** 2 for x in b) / n)
    if sa == 0 or sb == 0:
        return None
    return cov / (sa * sb)


def cross_correlation(rows, var_a, var_b, lag):
    """Cross-correlation bei Lag (positiv: a fuehrt b; negativ: b fuehrt a)."""
    a = [r.get(var_a, 0.0) or 0.0 for r in rows]
    b = [r.get(var_b, 0.0) or 0.0 for r in rows]
    if lag > 0:
        a = a[:-lag]
        b = b[lag:]
    elif lag < 0:
        a_with = a[-lag:]
        b_with = b[:lag] if lag != 0 else b[:]
        a = a_with
        b = b_with
    return pearson(a, b)


def render_table_eps(episodes, rows):
    lines = []
    lines.append("=" * 100)
    lines.append("Gini > 0.60 EPISODEN — Was passiert gleichzeitig?")
    lines.append("=" * 100)
    lines.append(
        f"{'#':>2} {'day_range':<14} {'N':>2} {'mean_gini':>10} {'max_gini':>10} | "
        f"{'mean_tax':>9} {'mean_subs':>10} {'mean_mkt':>10} | "
        f"{'mean_in':>9} {'mean_out':>10} {'mean_treas':>10}"
    )
    lines.append("-" * 100)
    for i, (s, e, mg, xg) in enumerate(episodes):
        day_s = rows[s].get("game_day", s)
        day_e = rows[e].get("game_day", e)
        tax = summarize(rows, s, e, "head_tax")
        sub = summarize(rows, s, e, "subsidy_proxy")
        mkt = summarize(rows, s, e, "market_vol")
        mi = summarize(rows, s, e, "migration_in")
        mo = summarize(rows, s, e, "migration_out")
        tr = summarize(rows, s, e, "treasury_delta")
        line = (
            f"{i+1:>2} {str(day_s)+'-'+str(day_e):<14} {e-s+1:>2} {mg:>10.3f} {xg:>10.3f} | "
            f"{tax['mean']:>9.1f} {sub['mean']:>10.1f} {mkt['mean']:>10.1f} | "
            f"{mi['mean']:>9.2f} {mo['mean']:>10.2f} {tr['mean']:>10.0f}"
        )
        lines.append(line)
    return lines


def render_correlation_table(rows, variables):
    lines = []
    lines.append("")
    lines.append("=" * 90)
    lines.append("CROSS-CORRELATION: Gini vs Variable (Lag -7..+7 Tage)")
    lines.append("Pos. Lag = Variable kommt NACH Gini; Neg. Lag = Gini kommt NACH Variable")
    lines.append("=" * 90)
    header = f"{'variable':<20} "
    header += " ".join(f"{('+' + str(l)) if l >= 0 else str(l):>6}" for l in range(-7, 8))
    lines.append(header)
    lines.append("-" * 90)
    for var in variables:
        corrs = []
        for lag in range(-7, 8):
            c = cross_correlation(rows, "gini", var, lag)
            corrs.append(f"{c:>+6.2f}" if c is not None else f"{'   -':>6}")
        lines.append(f"{var:<20} " + " ".join(corrs))
    return lines


def render_baseline(rows, episodes, variables):
    lines = []
    lines.append("")
    lines.append("=" * 80)
    lines.append("BASELINE-VERGLEICH: Episode-Tage vs Nicht-Episode-Tage")
    lines.append("=" * 80)
    ep_idx = set()
    for s, e, _, _ in episodes:
        ep_idx.update(range(s, e + 1))
    base_idx = [i for i in range(len(rows)) if i not in ep_idx]
    lines.append(f"{'variable':<22} {'baseline':>14} {'episode':>14} {'delta':>14}")
    lines.append("-" * 80)
    for var in variables:
        b = [rows[i].get(var, 0.0) or 0.0 for i in base_idx]
        e = [rows[i].get(var, 0.0) or 0.0 for i in sorted(ep_idx)]
        if not b or not e:
            continue
        mb = statistics.mean(b)
        me = statistics.mean(e)
        lines.append(f"{var:<22} {mb:>14.3f} {me:>14.3f} {(me - mb):>+14.3f}")
    return lines


def render_top_correlations(rows):
    # Höchste |r|-Korrelationen bei allen Lags aggregiert
    variables = ["head_tax", "subsidy_proxy", "market_steu",
                 "migration_in", "migration_out", "migration_balance",
                 "market_receipts", "treasury_delta", "warehouse_bought",
                 "warehouse_sold", "housing_evictions_last_tick",
                 "unpaid_ratio", "actual_mean_wage", "food_basket_price",
                 "food_days", "audit_delta"]
    lines = []
    lines.append("")
    lines.append("=" * 80)
    lines.append("TOP-ABS-KORRELATIONEN |r|>0.20 (über alle Lags)")
    lines.append("=" * 80)
    lines.append(f"{'variable':<28} {'best_lag':>10} {'|r|':>6} {'r':>7}")
    lines.append("-" * 80)
    hits = []
    for var in variables:
        best = (0.0, 0)
        for lag in range(-14, 15):
            c = cross_correlation(rows, "gini", var, lag)
            if c is not None and abs(c) > abs(best[0]):
                best = (c, lag)
        if abs(best[0]) >= 0.20:
            hits.append((var, best[1], abs(best[0]), best[0]))
    hits.sort(key=lambda x: -x[2])
    for var, lag, ar, r in hits[:20]:
        lines.append(f"{var:<28} {lag:>+10d} {ar:>6.2f} {r:>+7.2f}")
    if not hits:
        lines.append("  (keine Korrelation |r|>=0.20 gefunden — CSV zu kurz oder kein lineares Signal)")
    return lines


def write_report(lines):
    with REPORT_PATH.open("w", encoding="utf-8") as f:
        f.write("\n".join(lines))
        f.write("\n")


def main():
    threshold = float(sys.argv[1]) if len(sys.argv) > 1 else 0.60
    path = latest_csv("rebalance_macro")
    if path is None:
        print("ERROR: keine rebalance_macro*.csv in", DIAG_DIR, file=sys.stderr)
        sys.exit(1)
    print(f"Lese: {path.name}")
    rows = parse_csv(path)
    print(f"  {len(rows)} Zeilen")
    if len(rows) < 5:
        print("ERROR: zu wenig Zeilen fuer Korrelation (>= 5 noetig)", file=sys.stderr)
        sys.exit(2)
    rows = compute_proxies(rows)
    episodes = find_gini_episodes(rows, threshold)
    print(f"\n-> {len(episodes)} Gini-{threshold}-Plus-Episoden gefunden\n")
    variables = ["head_tax", "subsidy_proxy", "market_steu",
                 "migration_in", "migration_out"]
    lines = [f"Gini-Episode-Report — Schwelle {threshold} — Datei: {path.name}"]
    lines += render_table_eps(episodes, rows)
    lines += render_correlation_table(rows, variables)
    lines += render_baseline(rows, episodes, variables)
    lines += render_top_correlations(rows)
    out = "\n".join(lines)
    print(out)
    write_report(lines)
    print(f"\nReport persistiert nach: {REPORT_PATH}")


if __name__ == "__main__":
    main()
