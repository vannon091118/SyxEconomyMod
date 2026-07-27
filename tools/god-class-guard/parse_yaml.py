#!/usr/bin/env python3
# SyxEconomyMod — God-Class-Guard: parse_yaml.py
# ===============================================
# Laedt tools/god-class-baselines.yml und entscheidet pro Datei, ob sie
# PASS / WARN / BLOCK ist. Drei-Schicht-Resolution in Reihenfolge:
#
#   1. Heuristic constants-dump    (fields>=50 AND pubM==0)
#   2. Pattern-Exempt              (exempt_patterns Regex-Match auf Pfad)
#   3. Legacy-Baseline-Drift       (legacy_baselines + Drift-Pct-Toleranzen)
#   4. Hard-Limits                 (loc/pubM/fields gegen block/warn-Schwellen)
#
# Aufruf:
#   python3 parse_yaml.py                    # drucke effektive Default-Config + Merge
#   python3 parse_yaml.py dump               # gleiches wie oben
#   python3 parse_yaml.py classify PATH     # klassifiziere einzelne Datei nach stdout
#
# Exit: 0 ok

from __future__ import annotations
import re
import sys
from pathlib import Path
from typing import Optional

import yaml

# ── Defaults (hartcodiert, gelten wenn tools/god-class-baselines.yml fehlt) ─
DEFAULTS: dict = {
    'version': '1.0',
    'auto_generated': False,
    'limits': {
        'loc_warn': 600,
        'loc_block': 800,
        'pubM_warn': 25,
        'pubM_block': 35,
        'fields_warn': 18,
        'fields_block': 24,
        'imports_warn': 25,
        'imports_block': 40,
    },
    'drift': {
        'loc_pct': 0.05,
        'pubM_pct': 0.10,
        'fields_pct': 0.10,
    },
    'exempt_patterns': [
        {'regex': r'^src/.+/ui/Window[^/]*\.java$',
         'rule': 'Rule 6 (UI-Window sakrosankt)'},
        {'regex': r'^src/.+/adapter/seam/[^/]*\.java$',
         'rule': 'Rule 9 (BypassGate SDK)'},
        {'regex': r'^src/.+/benchmark/[^/]*\.java$',
         'rule': 'Benchmark-Bundle'},
        {'regex': r'^src/.+/settlement/.*\.java$',
         'rule': 'Heilbringer-Mod-Code'},
    ],
    'heuristic_exemptions': [
        {'id': 'constants-dump',
         'rule': 'fields>=50 AND pubM==0',
         'applies_to': 'fields_cap_only'},
    ],
    'legacy_baselines': {},
}


def load_baselines(path: Optional[Path] = None) -> tuple:
    """Lade baselines.yml (oder Default wenn nicht vorhanden).

    Returns:
        (baselines, warnings) — die Baselines-Dict plus Liste von Warn-Strings,
        die der Caller ausgeben sollte. Warnings sind non-fatal.
    """
    warnings: list[str] = []
    if path is None:
        path = Path('tools/god-class-baselines.yml')
    if not path.exists():
        # Vollstaendiger Fallback: kein File → alle Defaults
        warnings.append(f"baselines-File {path} nicht gefunden; DEFAULTS aktiv")
        return {**DEFAULTS,
                'limits': {**DEFAULTS['limits']},
                'drift': {**DEFAULTS['drift']},
                'exempt_patterns': [dict(p) for p in DEFAULTS['exempt_patterns']],
                'heuristic_exemptions': [dict(p) for p in DEFAULTS['heuristic_exemptions']],
                'legacy_baselines': {}}, warnings

    try:
        with open(path) as f:
            data = yaml.safe_load(f) or {}
    except yaml.YAMLError as e:
        warnings.append(f"baselines-File {path} ist ungueltiges YAML ({e}); DEFAULTS aktiv")
        return {**DEFAULTS,
                'limits': {**DEFAULTS['limits']},
                'drift': {**DEFAULTS['drift']},
                'exempt_patterns': [dict(p) for p in DEFAULTS['exempt_patterns']],
                'heuristic_exemptions': [dict(p) for p in DEFAULTS['heuristic_exemptions']],
                'legacy_baselines': {}}, warnings

    # Deep-Merge: DEFAULTS werden ueberschrieben wo data Schluessel hat
    merged = {k: (data.get(k) or DEFAULTS.get(k)) for k in DEFAULTS.keys()}
    for inner in ('limits', 'drift'):
        merged[inner] = {**DEFAULTS[inner], **(merged.get(inner) or {})}

    # Pre-Compile exempt_patterns regexes + Validation (HIGH Issue #3)
    compiled_patterns = []
    for pat in merged.get('exempt_patterns', []):
        regex_str = pat.get('regex') or ''  # None → '' (sicherer Default)
        if not regex_str:
            # Regression-Fix: leerer regex = re.match() matched alles
            warnings.append(
                "exempt_patterns Eintrag ohne 'regex'-Key (oder leer); "
                "Pattern wird IGNORIERT — File faellt auf Hard-Limit "
                "(leerer regex wuerde sonst alle Files exempten)"
            )
            continue
        try:
            compiled = re.compile(regex_str)
            compiled_patterns.append({**pat, '_compiled': compiled})
        except re.error as e:
            warnings.append(
                f"exempt_patterns Eintrag hat ungueltigen regex ({regex_str!r}): {e}; "
                f"Pattern wird IGNORIERT — File faellt auf Hard-Limit"
            )
    merged['exempt_patterns'] = compiled_patterns

    return merged, warnings


# ── Constants-Dump-Heuristik (Punkt 1 der Resolution) ──────────────────
def _is_constants_dump(metrics: dict) -> bool:
    return metrics.get('fields', 0) >= 50 and metrics.get('pubM', 0) == 0


# ── Pattern-Exempt (Punkt 2) ───────────────────────────────────────────
def _match_exempt_pattern(path: str, patterns: list) -> Optional[str]:
    """Matches gegen vorkompilierte Regex; Fallback auf pat['regex']-String wenn uncompiled."""
    for pat in patterns:
        compiled = pat.get('_compiled')
        if compiled is None:
            # Fallback wenn nicht pre-compiled (z.B. Tests, die DEFAULTS inlinen)
            try:
                compiled = re.compile(pat['regex'])
            except re.error:
                continue
        if compiled.match(path):
            return pat.get('rule', pat['regex'])
    return None


# ── Drift-Check (Punkt 3) ──────────────────────────────────────────────
def _check_drift(metrics: dict, legacy: dict, drift_cfg: dict) -> dict:
    reasons = []
    status = 'pass'
    drift_details = {}

    for key, pct_key in (('loc', 'loc_pct'), ('pubM', 'pubM_pct'), ('fields', 'fields_pct')):
        if key not in legacy:
            continue
        base = legacy[key]
        curr = metrics.get(key, 0)
        cap = base * (1.0 + drift_cfg.get(pct_key, 0.10))
        drift_details[key] = {'baseline': base, 'current': curr, 'cap': round(cap, 1)}
        if curr > cap:
            pct_over = ((curr - cap) / cap) * 100 if cap > 0 else 0
            reasons.append(
                f"{key} drift: {curr} > cap {round(cap, 1)} "
                f"(baseline {base} + {drift_cfg.get(pct_key, 0.10) * 100:.0f}%, "
                f"overshooting by {pct_over:.1f}%)"
            )
            status = 'block'

    return {
        'status': status,
        'is_legacy': True,
        'baseline': legacy,
        'drift_details': drift_details,
        'reasons': reasons,
    }


# ── Hard-Limits (Punkt 4) ──────────────────────────────────────────────
def _check_limits(metrics: dict, limits: dict, constants_exempt: bool) -> dict:
    """Vergleicht Metriken gegen Hard-Limits. Liefert Status + Reasons.

    Status-Logik: 'pass' < 'warn' < 'block'. Ein einzelner Block-Wert
    eskaliert sofort auf 'block'; sonst der hoechste Severitaets-Treffer.
    """
    reasons = []
    status = 'pass'

    def _eval(key: str, block_th: int, warn_th: int, value: int) -> None:
        nonlocal status
        if value > block_th:
            reasons.append(f"{key} {value} > block {block_th}")
            status = 'block'
        elif value > warn_th and status == 'pass':
            reasons.append(f"{key} {value} > warn {warn_th}")
            status = 'warn'

    _eval('loc', limits['loc_block'], limits['loc_warn'], metrics.get('loc', 0))
    _eval('pubM', limits['pubM_block'], limits['pubM_warn'], metrics.get('pubM', 0))

    if not constants_exempt:
        _eval('fields', limits['fields_block'], limits['fields_warn'], metrics.get('fields', 0))

    # Soft-Warning fuer Imports (Coupling-Indicator; kein Hard-Block bei sowieso block)
    if metrics.get('imports', 0) > limits.get('imports_block', 40):
        reasons.append(
            f"imports {metrics['imports']} > block {limits['imports_block']} (coupling density)"
        )
        status = 'block'
    elif metrics.get('imports', 0) > limits.get('imports_warn', 25):
        reasons.append(
            f"imports {metrics['imports']} > warn {limits['imports_warn']} (coupling density)"
        )

    return {
        'status': status,
        'is_legacy': False,
        'reasons': reasons,
        'is_constants_dump': constants_exempt,
    }


# ── Haupt-Classifier ───────────────────────────────────────────────────
def classify(path: str, metrics: dict, baselines: dict) -> dict:
    """Drei-Schicht-Resolution. Liefert immer ein Dict mit 'status'.

    Status ist eines von: 'pass', 'warn', 'block'.
    """
    # 1. Heuristic Constants-Dump — block-check bleibt aktiv, Fields-Cap entfaellt
    is_constants = _is_constants_dump(metrics)

    # 2. Pattern-Exempt
    exempt_rule = _match_exempt_pattern(path, baselines.get('exempt_patterns', []))
    if exempt_rule:
        return {
            'status': 'pass',
            'exempted_by': exempt_rule,
            'reasons': ['pattern-exempt'],
            'is_legacy': False,
            'is_constants_dump': is_constants,
        }

    # 3. Legacy-Drift
    legacy = baselines.get('legacy_baselines', {}).get(path)
    if legacy:
        return _check_drift(metrics, legacy, baselines.get('drift', DEFAULTS['drift']))

    # 4. Hard-Limits (default fuer "neue" Klassen)
    return _check_limits(metrics, baselines.get('limits', DEFAULTS['limits']), is_constants)


def main():
    args = sys.argv[1:]
    if not args or args[0] in ('dump', '-d', '--dump'):
        baselines, warnings = load_baselines()
        import yaml
        print(yaml.dump(baselines, default_flow_style=False, sort_keys=False))
        for w in warnings:
            print(f'# warn: {w}', file=sys.stderr)
        return

    if args[0] in ('classify', '-c', '--classify') and len(args) >= 2:
        metrics_path = Path(args[1])
        if not metrics_path.exists():
            print(f'file not found: {metrics_path}', file=sys.stderr)
            sys.exit(1)
        sys.path.insert(0, str(Path(__file__).parent))
        from parse_metrics import parse_java_metrics
        baselines, warnings = load_baselines()
        for w in warnings:
            print(f'# warn: {w}', file=sys.stderr)
        result = classify(str(metrics_path), parse_java_metrics(metrics_path), baselines)
        result['path'] = str(metrics_path)
        import json
        print(json.dumps(result, indent=2))
        return

    print(f'usage: {sys.argv[0]} [dump | classify <path.java>]', file=sys.stderr)
    sys.exit(1)


if __name__ == '__main__':
    main()
