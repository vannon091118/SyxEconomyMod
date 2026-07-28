#!/usr/bin/env python3
# SyxEconomyMod — God-Class-Guard: run_check.py
# ==============================================
# Hauptrunner. Walked src/**/*.java, parsed Metriken via parse_metrics,
# klassifiziert via parse_yaml.classify, aggregiert Ergebnisse.
#
# Aufruf:
#   python3 run_check.py [--mode=dry|soft|hard] [--json]
#
# Modus-Semantik:
#   --mode=dry   (default) Report everything; exit 0 unconditional.
#                       Sinnvoll fuer Sprint-Planning oder neue Files betrachten.
#   --mode=soft  Report PASS/WARN/BLOCK; exit 0/1/2 wie Block-Status.
#                       Sinnvoll fuer lokale Entwicklung.
#   --mode=hard  WARN wird zu BLOCK (exit 2). Sinnvoll fuer CI und Gate.
#
# Exit-Codes:
#   0 = Alles PASS
#   1 = mind. 1 WARN (Mode != hard)
#   2 = mind. 1 BLOCKER (oder mind. 1 WARN in Mode=hard)

from __future__ import annotations
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from parse_metrics import parse_java_metrics  # noqa: E402
from parse_yaml import load_baselines, classify  # noqa: E402


# ANSI-Farben (subprocess.stdout may strip)
_USE_COLOR = sys.stdout.isatty()
RED = '\033[0;31m' if _USE_COLOR else ''
YELLOW = '\033[1;33m' if _USE_COLOR else ''
GREEN = '\033[0;32m' if _USE_COLOR else ''
NC = '\033[0m' if _USE_COLOR else ''


def scan(baselines: dict) -> dict:
    results = {'pass': [], 'warn': [], 'block': []}
    for path in sorted(Path('src').rglob('*.java')):
        p = str(path)
        m = parse_java_metrics(path)
        c = classify(p, m, baselines)
        bucket = c['status']
        if bucket not in results:
            results[bucket] = []
        results[bucket].append({
            'path': p,
            'metrics': m,
            'result': c,
        })
    return results


def render_text(results: dict, mode: str) -> int:
    """Returnt exit code (0/1/2)."""
    block_n = len(results['block'])
    warn_n = len(results['warn'])
    pass_n = len(results['pass'])
    total = block_n + warn_n + pass_n

    print(f"  Scanning {total} .java files in src/...\n")
    for bucket, color in (('block', RED), ('warn', YELLOW), ('pass', GREEN)):
        for r in results[bucket]:
            m = r['metrics']
            exempt = f" {GREEN}[exempt: {r['result'].get('exempted_by')}]{NC}" if r['result'].get('exempted_by') else ''
            legacy = f" {YELLOW}[legacy drift > baseline]{NC}" if r['result'].get('is_legacy') else ''
            const_dump = (f" {YELLOW}[constants-dump]{NC}"
                          if r['result'].get('is_constants_dump') else '')
            print(f"  {color}{bucket.upper():5s}{NC} {r['path']:62s} "
                  f"loc={m['loc']:4d} pubM={m['pubM']:3d} fields={m['fields']:3d} "
                  f"imp={m['imports']:3d}{exempt}{legacy}{const_dump}")
            reasons = r['result'].get('reasons', [])
            for reason in reasons:
                print(f"           → {reason}")

    print()
    print(f"  {GREEN}PASS{NC}: {pass_n}  {YELLOW}WARN{NC}: {warn_n}  {RED}BLOCK{NC}: {block_n}  "
          f"(mode={mode})")

    # Exit-Logik
    if block_n > 0:
        return 2
    if mode == 'hard' and warn_n > 0:
        return 2
    if warn_n > 0 or mode == 'soft':
        return 1
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--mode', default='dry',
                    choices=['dry', 'soft', 'hard'],
                    help='Operating mode (default: dry)')
    ap.add_argument('--json', action='store_true',
                    help='Emit JSON output for CI')
    args = ap.parse_args()

    baselines, warnings = load_baselines()
    results = scan(baselines)

    for w in warnings:
        print(f'  warn: {w}', file=sys.stderr)

    if args.json:
        block_n = len(results['block'])
        warn_n = len(results['warn'])
        pass_n = len(results['pass'])
        print(json.dumps({
            'mode': args.mode,
            'warnings': warnings,
            'totals': {'pass': pass_n, 'warn': warn_n, 'block': block_n,
                       'total': pass_n + warn_n + block_n},
            'results': results,
        }, indent=2))
        # Exit-Logik für JSON-Modus identisch
        if block_n > 0 or (args.mode == 'hard' and warn_n > 0):
            sys.exit(2)
        if warn_n > 0 or args.mode == 'soft':
            sys.exit(1)
        sys.exit(0)

    exit_code = render_text(results, args.mode)
    sys.exit(exit_code)


if __name__ == '__main__':
    main()
