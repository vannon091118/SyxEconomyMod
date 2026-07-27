#!/usr/bin/env python3
"""
SyxEconomyMod Change Detector — v0.13.67

Reduziert debug.csv (39.6M Zeilen, 2.1 GB) auf ein anomaly_catalogue.json
mit ~200-2.000 signifikanten State-Change-Events.

Pipeline:
  1. Chunked read (50k rows/chunk)
  2. Per-entity state tracking (nur die letzten Werte)
  3. Threshold-basierte Change-Detection pro Feld
  4. Output: anomaly_catalogue.json (KB, nicht GB)

Usage:
    python3 tools/change_detector.py [--input debug.csv] [--output anomaly_catalogue.json]
    python3 tools/change_detector.py --categories    # nur Kategorie-Verteilung zeigen
    python3 tools/change_detector.py --sample 100     # erste 100 Events ausgeben

Thresholds sind pro Spalte definiert. Bei Überschreitung wird das Event
mit Zeilennummer (row_ref) gespeichert — kein Rohdaten-Export, nur Referenz.
"""

import csv
import json
import sys
from pathlib import Path

# ─── Default paths ──────────────────────────────────────────
DIAG_DIR = (
    Path.home()
    / ".local"
    / "share"
    / "songsofsyx"
    / "mods"
    / "SyxEconomyMod"
    / "diagnostics"
)

# ─── Thresholds: Wann ist eine Änderung "signifikant"? ──────
# Jede Funktion bekommt (old_value, new_value) und gibt True zurück
# wenn sich der State relevant geändert hat.

THRESHOLDS = {
    # Makro-Kennzahlen (rebalance_macro*.csv)
    "gini":             lambda old, new: abs(new - old) >= 0.03,
    "treasury":         lambda old, new: abs(new - old) / max(abs(old), 1) >= 0.25,
    "mean_wage":        lambda old, new: abs(new - old) / max(abs(old), 1) >= 0.15,
    "actual_mean_wage": lambda old, new: abs(new - old) / max(abs(old), 1) >= 0.15,
    "wage_share":       lambda old, new: abs(new - old) >= 0.05,
    "unpaid_ratio":     lambda old, new: (old == 0) != (new == 0),  # NULL → non-NULL toggle
    "head_tax":         lambda old, new: (old == 0) != (new == 0),
    "food_basket_price": lambda old, new: abs(new - old) / max(abs(old), 1) >= 0.20,
    "food_days":        lambda old, new: any(
        (old > t) != (new > t) for t in [30, 14, 7, 3, 1, 0]
    ),
    "audit_delta":      lambda old, new: abs(new - old) >= 5000,
    "population":       lambda old, new: abs(new - old) >= 5,
    "deaths":           lambda old, new: new > old,  # JEDER neue Todesfall

    # Ressourcen-Kennzahlen (rebalance_resources*.csv)
    "market_price":     lambda old, new: abs(new - old) / max(abs(old), 1) >= 0.25,
    "coverage":         lambda old, new: abs(new - old) >= 0.15,
    "supply_per_day":   lambda old, new: (old == 0) != (new == 0),
    "demand_per_day":   lambda old, new: (old == 0) != (new == 0),
    "days_of_supply":   lambda old, new: any(
        (old > t) != (new > t) for t in [30, 14, 7, 1, 0]
    ),
    "starving_signal":  lambda old, new: new > 0 and old == 0,  # Hunger-Spike
}

# Felder die wir im Snapshot speichern (kurzer Kontext, keine komplette Row)
SNAPSHOT_FIELDS = [
    "gini", "treasury", "mean_wage", "food_basket_price", "food_days",
    "market_price", "coverage", "supply_per_day", "demand_per_day",
    "days_of_supply", "starving_signal", "population", "deaths",
    "head_tax", "unpaid_ratio", "audit_delta", "wage_share",
    "actual_mean_wage",
]

# ─── Helpers ─────────────────────────────────────────────────

def find_latest(pattern: str, directory: Path = None):
    """Findet das neueste File das auf pattern matcht."""
    d = directory or DIAG_DIR
    files = sorted(d.glob(pattern))
    return files[-1] if files else None


def is_header_row(row):
    """Erkennt leaked header: erste Spalte == 'tick'."""
    return row and row[0] == "tick"


def is_eventlog(row):
    """EventLog-Bridge-Einträge die keine State-Changes sind."""
    return len(row) >= 7 and row[5].startswith("eventlog_")


# ─── Main Pipeline ───────────────────────────────────────────

def process_debug_csv(input_path, output_path, chunk_size=50000, max_events=5000):
    """
    Liest debug.csv chunked, detektiert State-Changes, schreibt Katalog.

    Returns (event_count, total_rows, category_counts).
    """
    state = {}          # entity_key → {field: last_value}
    events = []         # anomaly catalogue
    category_counts = {}
    total_rows = 0

    with open(input_path, "r", encoding="utf-8", newline="") as f:
        reader = csv.reader(f, delimiter=";")
        header = next(reader)
        total_rows = 1  # header

        for row in reader:
            total_rows += 1

            # Skip leaked headers und EventLog-Spam
            if is_header_row(row):
                continue
            if is_eventlog(row):
                continue
            if len(row) < 8:
                continue

            tick, day, category, subsystem, severity, key, value, note = row[0:8]

            # Kategorie zählen
            cat = category.strip() if category else "(empty)"
            category_counts[cat] = category_counts.get(cat, 0) + 1

            # TRACE-Level MIRROR-Einträge sind 96% des Volumens:
            # entities_available, room_*, humanoid_* — keine State-Changes,
            # nur Access-Logs. Überspringen.
            if severity == "TRACE" and category == "MIRROR":
                continue

            # Entity-Key: category + key (z.B. "TREASURY:crisis_tier")
            entity_key = f"{cat}:{key.strip()}"

            try:
                val_float = float(value)
            except (ValueError, TypeError):
                val_float = 0.0

            prev = state.get(entity_key, {})

            # Check thresholds
            triggered = []
            for field, threshold_fn in THRESHOLDS.items():
                if field not in key.lower():
                    continue
                old_val = prev.get(field, val_float)
                try:
                    if threshold_fn(old_val, val_float):
                        triggered.append({
                            "field": field,
                            "from": old_val,
                            "to": val_float,
                        })
                except (TypeError, ZeroDivisionError):
                    pass

            if triggered:
                events.append({
                    "entity": entity_key,
                    "day": day,
                    "tick": tick,
                    "row_ref": total_rows,
                    "category": cat,
                    "subsystem": subsystem,
                    "changes": triggered,
                    "snapshot": {k: val_float for k in SNAPSHOT_FIELDS
                                 if k in key.lower()},
                    "note": note[:200] if note else "",
                })

                if len(events) >= max_events:
                    break

            # State updaten (nur was wir tracken)
            for field in THRESHOLDS:
                if field in key.lower():
                    prev[field] = val_float
            state[entity_key] = prev

    # Write catalogue
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump({
            "source": str(input_path),
            "total_rows": total_rows,
            "events_found": len(events),
            "category_distribution": category_counts,
            "events": events,
        }, f, indent=2, default=str)

    return len(events), total_rows, category_counts


def process_rebalance_resources(input_path, output_path, max_events=3000):
    """
    Liest rebalance_resources*.csv (6.4K rows, 11 columns).
    Detektiert Coverage-Collapse, Preis-Spikes, Supply-Toggles.
    """
    state = {}  # resource → {field: last_value}
    events = []
    total_rows = 0

    with open(input_path, "r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            total_rows += 1
            resource = row.get("resource", "UNKNOWN")
            day = row.get("game_day", "?")

            prev = state.get(resource, {})
            triggered = []

            for field in ["market_price", "coverage", "supply_per_day",
                          "demand_per_day", "days_of_supply", "starving_signal"]:
                if field not in row:
                    continue
                try:
                    new_val = float(row[field])
                except (ValueError, TypeError):
                    continue
                old_val = prev.get(field, new_val)
                if field in THRESHOLDS:
                    try:
                        if THRESHOLDS[field](old_val, new_val):
                            triggered.append({
                                "field": field,
                                "from": old_val,
                                "to": new_val,
                            })
                    except (TypeError, ZeroDivisionError):
                        pass
                prev[field] = new_val

            if triggered:
                events.append({
                    "entity": resource,
                    "day": day,
                    "row_ref": total_rows,
                    "changes": triggered,
                    "snapshot": {k: row.get(k, "") for k in SNAPSHOT_FIELDS
                                 if k in row},
                })

                if len(events) >= max_events:
                    break

            state[resource] = prev

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump({
            "source": str(input_path),
            "total_rows": total_rows,
            "events_found": len(events),
            "events": events,
        }, f, indent=2, default=str)

    return len(events), total_rows


# ─── CLI ─────────────────────────────────────────────────────

def print_category_report(input_path):
    """Schnelle Kategorie-Verteilung ohne Full-Scan."""
    counts = {}
    total = 0
    with open(input_path, "r", encoding="utf-8", newline="") as f:
        reader = csv.reader(f, delimiter=";")
        next(reader)  # skip header
        for i, row in enumerate(reader):
            if i >= 500000:
                break
            if is_header_row(row):
                continue
            if len(row) < 3:
                continue
            cat = row[2].strip() if row[2] else "(empty)"
            counts[cat] = counts.get(cat, 0) + 1
            total += 1

    print(f"Category distribution (sample: {total} rows):")
    for cat, n in sorted(counts.items(), key=lambda x: -x[1]):
        pct = 100.0 * n / total if total > 0 else 0
        bar = "█" * int(pct / 2)
        print(f"  {cat:<20} {n:>8} ({pct:>5.1f}%) {bar}")


def print_sample_events(input_path, n=20):
    """Gibt die ersten N Non-TRACE-Events lesbar aus."""
    with open(input_path, "r", encoding="utf-8", newline="") as f:
        reader = csv.reader(f, delimiter=";")
        next(reader)  # header
        count = 0
        for row in reader:
            if count >= n:
                break
            if is_header_row(row):
                continue
            if len(row) < 8:
                continue
            cat, sev, key, val = row[2], row[4], row[5], row[6]
            if sev == "TRACE" and cat == "MIRROR":
                continue  # skip spam
            print(f"[{cat}/{sev}] {key} = {val[:60]}")
            count += 1


def main():
    import argparse
    parser = argparse.ArgumentParser(
        description="SyxEconomyMod Change Detector — 39.6M → ~2K Events")
    parser.add_argument("--input", "-i",
                        help="Path to debug.csv or rebalance_resources*.csv")
    parser.add_argument("--output", "-o", default="anomaly_catalogue.json",
                        help="Output JSON path (default: anomaly_catalogue.json)")
    parser.add_argument("--categories", action="store_true",
                        help="Show category distribution and exit")
    parser.add_argument("--sample", type=int, default=0,
                        help="Print first N non-spam events and exit")
    parser.add_argument("--max-events", type=int, default=5000,
                        help="Max events to extract (default: 5000)")
    parser.add_argument("--resources", action="store_true",
                        help="Process rebalance_resources*.csv instead of debug.csv")
    args = parser.parse_args()

    # Auto-detect input path
    if args.input:
        input_path = Path(args.input)
    elif args.resources:
        input_path = find_latest("rebalance_resources*.csv")
    else:
        input_path = Path("/home/vannon/.local/share/songsofsyx/mods/debug.csv")

    if not input_path or not input_path.exists():
        print(f"ERROR: Input file not found: {input_path}", file=sys.stderr)
        sys.exit(1)

    print(f"Source: {input_path} ({input_path.stat().st_size / 1e9:.1f} GB)")

    if args.categories:
        print_category_report(input_path)
        return

    if args.sample:
        print_sample_events(input_path, args.sample)
        return

    if args.resources:
        n, total = process_rebalance_resources(input_path, args.output, args.max_events)
    else:
        n, total, cats = process_debug_csv(input_path, args.output,
                                           max_events=args.max_events)

    print(f"\nDone: {total:,} rows → {n} events in {args.output}")
    out_size = Path(args.output).stat().st_size
    print(f"Output: {out_size / 1024:.1f} KB (reduction: {total / max(n, 1):,.0f}x)")


if __name__ == "__main__":
    main()
