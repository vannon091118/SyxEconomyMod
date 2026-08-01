#!/usr/bin/env python3
# SyxEconomyMod — God-Class-Guard: emit_yaml.py
# ===============================================
# Generiert tools/god-class-baselines.yml aus dem aktuellen Repo-State.
#
# Logik:
#   - Walk alle src/**/*.java
#   - Parse Metriken pro File
#   - Klassifiziere via parse_yaml.classify
#   - Capture grandfathered Files: alle die aktuell "warn" oder "block"
#     sind UND nicht durch Pattern/Heuristik exemptet
#   - Output: vollständige god-class-baselines.yml mit Defaults + grandfathers
#
# Aufruf:
#   python3 emit_yaml.py           # schreibt tools/god-class-baselines.yml
#   python3 emit_yaml.py --dry     # druckt YAML nach stdout statt zu schreiben
#
# Exit: 0 ok, 2 wenn keine grandfathered Files gefunden
#
# Wichtig: das Output-File ist GENERATED. Hand-Edits sind erlaubt
# fuer rationale-Texte, aber die Metrics-Zahlen werden beim naechsten
# Sprit-Planning wieder ueberschrieben.

from __future__ import annotations
import datetime
import sys
from pathlib import Path

# Sibling-Import (Tools im selben Verzeichnis)
sys.path.insert(0, str(Path(__file__).parent))
import yaml  # noqa: E402

from parse_metrics import parse_java_metrics  # noqa: E402
from parse_yaml import DEFAULTS, classify  # noqa: E402


def find_java_files(roots: list[str] | None = None) -> list[Path]:
    if roots is None:
        roots = ['src']
    files = []
    for root in roots:
        files.extend(Path(root).rglob('*.java'))
    return sorted(files)


def emit(roots: list[str] | None = None) -> dict:
    roots = roots or ['src']
    files = find_java_files(roots)

    legacy: dict[str, dict] = {}
    for f in files:
        path_str = str(f)
        metrics = parse_java_metrics(f)
        result = classify(path_str, metrics, DEFAULTS)

        # Skip wenn pattern-exempt (UI Windows etc.)
        if result.get('exempted_by'):
            continue
        # Skip Constants-Dump (Mega-Field Dumps mit 0 PubM)
        if result.get('is_constants_dump') and metrics.get('fields', 0) >= 50:
            continue
        # Capture grandfathered
        if result['status'] in ('warn', 'block'):
            legacy[path_str] = {
                'loc': metrics['loc'],
                'pubM': metrics['pubM'],
                'fields': metrics['fields'],
                'imports': metrics['imports'],
                'status_at_emit': result['status'],
                'reason_at_emit': '; '.join(result.get('reasons', [])) or 'no-reason',
            }

    return {
        'version': DEFAULTS['version'],
        'auto_generated': True,
        'generated_at': datetime.datetime.now().isoformat(timespec='seconds'),
        'generator': 'tools/god-class-guard/emit_yaml.py',
        'limits': DEFAULTS['limits'],
        'drift': DEFAULTS['drift'],
        'exempt_patterns': DEFAULTS['exempt_patterns'],
        'heuristic_exemptions': DEFAULTS['heuristic_exemptions'],
        'legacy_baselines': legacy,
    }


def main():
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument('--dry', action='store_true', help='Print to stdout, do not write file.')
    ap.add_argument('--out', default='tools/god-class-baselines.yml',
                    help='Output path (default: tools/god-class-baselines.yml).')
    args = ap.parse_args()

    doc = emit()
    text = yaml.dump(doc, default_flow_style=False, sort_keys=False)
    if args.dry:
        print(text)
        return

    out_path = Path(args.out)
    out_path.write_text(text)
    legacy_count = len(doc['legacy_baselines'])
    excluded_count = len(find_java_files())
    print(f'Wrote {out_path} with {legacy_count} grandfathered entries')
    print(f'(scanned {excluded_count} .java files total)')
    if legacy_count == 0:
        print('NOTE: no grandfathered entries. All .java files are within new-class limits.')
        sys.exit(2)  # signal that auto-gen found nothing — may not need a sprint


if __name__ == '__main__':
    main()
