#!/usr/bin/env python3
"""
SyxEconomyMod Change Detector — v0.13.67

ZWEI Modi, zwei Schemas:

  --debug-csv  (default)  debug.csv: tick;day;category;subsystem;severity;key;value;note
    → EventLog-State-Transitions (TREASURY STUFE 1→5→erholt, ACCESS disabled→recovered,
      SEAM failures), diag_export day jumps, trace_* tick jumps.
    → Macht aus 39.6M Zeilen ~200-500 Events.

  --resources             rebalance_resources*.csv: game_day,season,resource,anchor_price,...
    → COVERAGE_CRASH, PRICE_SPIKE, SUPPLY/DEMAND_TOGGLE, STARVING.
    → Macht aus 6.4K Zeilen ~45 Events.

Usage:
    python3 tools/change_detector.py --debug-csv                     # default
    python3 tools/change_detector.py --resources                     # rebalance
    python3 tools/change_detector.py --categories                    # Kategorie-Verteilung
    python3 tools/change_detector.py --sample 100                    # erste N Non-Spam-Zeilen
    python3 tools/change_detector.py -i custom.csv -o out.json       # custom paths
"""

import csv
import json
import re
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

# ─── Treasury-Crisis-Tier-Parser ─────────────────────────────
# Extrahiert den Krisen-Level aus deutschen EventLog-Notes.

_TIER_PATTERNS = [
    (re.compile(r"STUFE\s*5"), 5),
    (re.compile(r"STUFE\s*4"), 4),
    (re.compile(r"STUFE\s*3"), 3),
    (re.compile(r"STUFE\s*2"), 2),
    (re.compile(r"STUFE\s*1"), 1),
    (re.compile(r"erholt|deaktiviert|zur.ckgesetzt"), 0),
]


def parse_treasury_tier(note_text: str) -> int:
    """Extrahiert den TreasuryCrisis-Tier aus dem deutschen Note-Text.
    Returns -1 wenn kein Treasury-Event erkannt wurde."""
    for pat, tier in _TIER_PATTERNS:
        if pat.search(note_text):
            return tier
    return -1


def extract_event_key(note_text: str) -> str:
    """Extrahiert eine stabile Kurzform des Events aus dem Note-Text.
    Enfernt variable Zahlenwerte, behält Struktur."""
    # Ersetze Zahlen durch #
    cleaned = re.sub(r"\d+", "#", note_text)
    # Kürze auf max 40 Zeichen
    return cleaned[:40] if cleaned else "?"


# ─── Thresholds für rebalance_resources (unverändert) ────────

THRESHOLDS = {
    "gini":             lambda old, new: abs(new - old) >= 0.03,
    "treasury":         lambda old, new: abs(new - old) / max(abs(old), 1) >= 0.25,
    "mean_wage":        lambda old, new: abs(new - old) / max(abs(old), 1) >= 0.15,
    "actual_mean_wage": lambda old, new: abs(new - old) / max(abs(old), 1) >= 0.15,
    "wage_share":       lambda old, new: abs(new - old) >= 0.05,
    "unpaid_ratio":     lambda old, new: (old == 0) != (new == 0),
    "head_tax":         lambda old, new: (old == 0) != (new == 0),
    "food_basket_price": lambda old, new: abs(new - old) / max(abs(old), 1) >= 0.20,
    "food_days":        lambda old, new: any(
        (old > t) != (new > t) for t in [30, 14, 7, 3, 1, 0]
    ),
    "audit_delta":      lambda old, new: abs(new - old) >= 5000,
    "population":       lambda old, new: abs(new - old) >= 5,
    "deaths":           lambda old, new: new > old,
    "market_price":     lambda old, new: abs(new - old) / max(abs(old), 1) >= 0.25,
    "coverage":         lambda old, new: abs(new - old) >= 0.15,
    "supply_per_day":   lambda old, new: (old == 0) != (new == 0),
    "demand_per_day":   lambda old, new: (old == 0) != (new == 0),
    "days_of_supply":   lambda old, new: any(
        (old > t) != (new > t) for t in [30, 14, 7, 1, 0]
    ),
    "starving_signal":  lambda old, new: new > 0 and old == 0,
}

SNAPSHOT_FIELDS = [
    "gini", "treasury", "mean_wage", "food_basket_price", "food_days",
    "market_price", "coverage", "supply_per_day", "demand_per_day",
    "days_of_supply", "starving_signal", "population", "deaths",
    "head_tax", "unpaid_ratio", "audit_delta", "wage_share",
    "actual_mean_wage",
]

# ─── Helpers ─────────────────────────────────────────────────

def find_latest(pattern: str, directory: Path = None):
    d = directory or DIAG_DIR
    files = sorted(d.glob(pattern))
    return files[-1] if files else None


def is_header_row(row):
    return row and len(row) > 0 and row[0] == "tick"


def is_eventlog_key(key: str) -> bool:
    return key.startswith("eventlog_")


def is_data_row(row):
    """Echte Datenzeile (kein leaked header, min. 8 Felder)."""
    return len(row) >= 8 and not is_header_row(row)


# ══════════════════════════════════════════════════════════════
# MODE 1: --debug-csv  (default)
# Schema: tick;day;category;subsystem;severity;key;value;note
# ══════════════════════════════════════════════════════════════

def process_debug_csv(input_path, output_path, max_events=5000):
    """
    debug.csv-Change-Detection: key/note-basiert.

    Erkennt:
      - TreasuryCrisis-Tier-Transitions (STUFE 1→5, erholt)
      - ACCESS Recovery/Disable-Events
      - SEAM-Adapter-Failures
      - EventLog-Note-Änderungen (State-Transitions)
      - diag_export Day-Jumps
      - trace_*-Tick-Jumps
      - Neue Event-Typen (first occurrence)

    MIRROR/TRACE wird komplett übersprungen (98.9% des Volumens).
    """
    state = {}          # category:key → {note_text, value, tier}
    events = []
    category_counts = {}
    total_rows = 0

    with open(input_path, "r", encoding="utf-8", newline="") as f:
        reader = csv.reader(f, delimiter=";")
        next(reader)  # header
        total_rows = 1

        for row in reader:
            total_rows += 1
            if not is_data_row(row):
                continue

            tick, day, category, subsystem, severity, key, value, note = row[0:8]
            cat = category.strip() if category else "(empty)"
            category_counts[cat] = category_counts.get(cat, 0) + 1

            # 98.9% Spam: MIRROR TRACE
            if severity == "TRACE" and cat == "MIRROR":
                continue

            # BRIDGE events: nur echte EventLog-Einträge (nicht Tests mit "test" im Note)
            if cat == "BRIDGE":
                note_lower = note.lower()
                if any(w in note_lower for w in ("test message", "sampled iteration")):
                    continue

            key_clean = key.strip()
            entity = f"{cat}:{key_clean}"
            prev = state.get(entity, {})

            try:
                val_float = float(value)
            except (ValueError, TypeError):
                val_float = 0.0

            note_clean = note.strip().replace('"', '') if note else ""
            change_detected = False
            event_type = "STATE_CHANGE"
            context = ""

            # ── Treasury Crisis Tier Detection ─────────────────────
            if cat in ("TREASURY", "BRIDGE") and is_eventlog_key(key_clean):
                tier = parse_treasury_tier(note_clean)
                if tier >= 0:
                    prev_tier = prev.get("tier", -1)
                    if tier != prev_tier:
                        change_detected = True
                        event_type = ("CRISIS_END" if tier == 0
                                      else f"CRISIS_TIER_{tier}")
                        context = note_clean
                        prev["tier"] = tier

            # ── ACCESS Events ──────────────────────────────────────
            if cat in ("ACCESS", "BRIDGE") and is_eventlog_key(key_clean):
                if "deaktiviert" in note_clean or "disabled" in note_lower:
                    prev_note = prev.get("note", "")
                    if prev_note != note_clean:
                        change_detected = True
                        event_type = "ACCESS_DISABLED"
                        context = note_clean
                        prev["note"] = note_clean
                elif "recovered" in note_lower or "erholt" in note_clean or "reaktiviert" in note_clean:
                    prev_note = prev.get("note", "")
                    if prev_note != note_clean:
                        change_detected = True
                        event_type = "ACCESS_RECOVERED"
                        context = note_clean
                        prev["note"] = note_clean

            # ── SEAM Events ────────────────────────────────────────
            if cat == "SEAM":
                prev_note = prev.get("note", "")
                if prev_note != note_clean:
                    change_detected = True
                    event_type = "SEAM_EVENT"
                    context = note_clean
                    prev["note"] = note_clean

            # ── SYSTEM Events ──────────────────────────────────────
            if cat == "SYSTEM":
                prev_note = prev.get("note", "")
                if prev_note != note_clean:
                    change_detected = True
                    event_type = "SYSTEM_EVENT"
                    context = note_clean
                    prev["note"] = note_clean

            # ── SNAPSHOT / diag_export ─────────────────────────────
            if cat == "SNAPSHOT":
                prev_val = prev.get("value", -1)
                if prev_val != val_float:
                    change_detected = True
                    event_type = "DIAG_EXPORT"
                    context = f"day={val_float}" if note_clean else ""
                    prev["value"] = val_float

            # ── TRACE events ───────────────────────────────────────
            if cat == "TRACE" and not is_eventlog_key(key_clean):
                prev_val = prev.get("value", -1)
                if prev_val != val_float:
                    change_detected = True
                    event_type = "TRACE_EVENT"
                    context = f"tick={val_float} {note_clean[:80]}" if note_clean else f"tick={val_float}"
                    prev["value"] = val_float

            # ── REBALANCE Events ───────────────────────────────────
            if cat == "REBALANCE":
                prev_note = prev.get("note", "")
                cleaned = extract_event_key(note_clean)
                prev_cleaned = prev.get("note_cleaned", "")
                if prev_cleaned != cleaned:
                    change_detected = True
                    event_type = "REBALANCE_ALERT"
                    context = note_clean
                    prev["note"] = note_clean
                    prev["note_cleaned"] = cleaned

            # ── Sonstige Kategorien: Note-Change-Detection ──────────
            if not change_detected and cat not in ("MIRROR", "BRIDGE", "TRACE",
                    "TREASURY", "ACCESS", "SEAM", "SYSTEM", "SNAPSHOT", "REBALANCE"):
                prev_note = prev.get("note", "")
                if prev_note != note_clean:
                    change_detected = True
                    event_type = f"{cat}_EVENT"
                    context = note_clean
                    prev["note"] = note_clean

            if change_detected:
                events.append({
                    "entity": entity,
                    "day": day,
                    "tick": tick,
                    "row_ref": total_rows,
                    "category": cat,
                    "subsystem": subsystem,
                    "severity": severity,
                    "event_type": event_type,
                    "value": val_float,
                    "context": context[:200],
                })

                if len(events) >= max_events:
                    break

            state[entity] = prev

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump({
            "source": str(input_path),
            "mode": "debug-csv",
            "total_rows": total_rows,
            "events_found": len(events),
            "category_distribution": category_counts,
            "events": events,
        }, f, indent=2, default=str, ensure_ascii=False)

    return len(events), total_rows, category_counts


# ══════════════════════════════════════════════════════════════
# MODE 2: --resources
# Schema: game_day,season,resource,anchor_price,market_price,...
# ══════════════════════════════════════════════════════════════

def process_rebalance_resources(input_path, output_path, max_events=3000):
    state = {}
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
            "mode": "resources",
            "total_rows": total_rows,
            "events_found": len(events),
            "events": events,
        }, f, indent=2, default=str)

    return len(events), total_rows


# ══════════════════════════════════════════════════════════════
# CLI
# ══════════════════════════════════════════════════════════════

def print_category_report(input_path):
    counts = {}
    total = 0
    with open(input_path, "r", encoding="utf-8", newline="") as f:
        reader = csv.reader(f, delimiter=";")
        next(reader)
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
    with open(input_path, "r", encoding="utf-8", newline="") as f:
        reader = csv.reader(f, delimiter=";")
        next(reader)
        count = 0
        for row in reader:
            if count >= n:
                break
            if is_header_row(row):
                continue
            if len(row) < 8:
                continue
            cat, sev, key, val, note = row[2], row[4], row[5], row[6], row[7] if len(row) > 7 else ""
            if sev == "TRACE" and cat == "MIRROR":
                continue
            # Zeige auch eventlog-Einträge mit ihrem Note-Text
            if key.startswith("eventlog_"):
                event_name = key.replace("eventlog_", "")
                print(f"[{cat}/{sev}] {event_name}: {note[:100]}")
            else:
                print(f"[{cat}/{sev}] {key} = {val[:60]}")
            count += 1


def main():
    import argparse
    parser = argparse.ArgumentParser(
        description="SyxEconomyMod Change Detector — 39.6M → ~500 Events")
    parser.add_argument("--input", "-i",
                        help="Path to debug.csv or rebalance_resources*.csv")
    parser.add_argument("--output", "-o", default="anomaly_catalogue.json",
                        help="Output JSON path")
    parser.add_argument("--categories", action="store_true",
                        help="Show category distribution and exit")
    parser.add_argument("--sample", type=int, default=0,
                        help="Print first N non-spam events and exit")
    parser.add_argument("--max-events", type=int, default=5000,
                        help="Max events to extract (default: 5000)")
    parser.add_argument("--resources", action="store_true",
                        help="Process rebalance_resources*.csv (Schema: game_day,resource,...)")
    parser.add_argument("--debug-csv", action="store_true", default=True,
                        help="Process debug.csv (Schema: tick;day;category;...;key;value;note) [default]")
    args = parser.parse_args()

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
        n, total, cats = process_debug_csv(input_path, args.output, max_events=args.max_events)

    print(f"\nDone: {total:,} rows → {n} events in {args.output}")
    out_size = Path(args.output).stat().st_size
    reduction = total / max(n, 1)
    print(f"Output: {out_size / 1024:.1f} KB (reduction: {reduction:,.0f}x)")


if __name__ == "__main__":
    main()
