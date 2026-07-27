#!/usr/bin/env python3
# SyxEconomyMod — God-Class-Guard: parse_metrics.py
# ====================================================
# Per-File-Metrik-Parser. Liest eine .java-Quelldatei und liefert
# vier Kern-Metriken, die der Guard gegen Schwellwerte vergleicht.
#
# Output: JSON-Liste, ein Element pro Input-Datei.
#
# Metriken (RFC M-3 §3):
#   loc      Effective Lines of Code (gesamt - Leerzeilen - reine Kommentarzeilen)
#   pubM     Public Methods, exkl. Konstruktoren und Standard-Getter/Setter
#   fields   Klassen-Attribute (public/private/protected, mit/ohne static/final)
#   imports  Import-Statements
#
# Aufruf:
#   python3 parse_metrics.py <path.java> [<path.java> ...]
#
# Exit-Code: 0 bei Erfolg, 1 wenn ein Input nicht existiert oder keine .java.

from __future__ import annotations
import json
import re
import sys
from pathlib import Path

# ── Regex-Spec (Python `re`, multiline wo noetig) ──────────────────────
# pubM: `public [static|final|abstract]* TYPE methodName(`
# methodName MUSS mit Kleinbuchstaben beginnen (Konvention: camelCase),
# Grossbuchstabe am Anfang = Konstruktor oder Klasse
# Erlaubt is*, get*, set* Pattern: rausgefiltert im Python
# pubM: `public [static|final|abstract]* TYPE methodName(`
# methodName MUSS mit Kleinbuchstaben beginnen (Konvention: camelCase),
# Grossbuchstabe am Anfang = Konstruktor oder Klasse.
# Erlaubt Annotation-Prefix vor `public`: `@Override`, `@SuppressWarnings`.
# MEDIUM Issue #4 Fix (Code-Review M-3).
_PUBM_RE = re.compile(
    r'^\s*(?:@[A-Za-z_][\w.]*(?:\([^)]*\))?\s+)*'
    r'public\s+(?:final\s+|static\s+|abstract\s+|synchronized\s+|native\s+)*'
    r'[A-Za-z<>,\[\]?]+\s+([a-z][a-zA-Z_0-9]*)\s*\(',
    re.MULTILINE,
)
_GETTER_RE = re.compile(r'^(is|get|set|init|reset|clear|load|save)[A-Z_]')

# fields: Sichtbarkeit [static|final]* TYPE name [=|;]
_FIELD_RE = re.compile(
    r'^\s*(?:public|private|protected)\s+'
    r'(?:static\s+|final\s+|volatile\s+|transient\s+)*'
    r'[A-Za-z<>,\[\]?]+\s+[a-zA-Z_]\w*\s*(?:=\s*[^;]+|[^=]*)\s*[;{]',
    re.MULTILINE,
)

# imports: `import foo.bar.Baz;`
_IMPORT_RE = re.compile(r'^\s*import\s+\w+(?:\.\w+)*(?:\.\*)?\s*;', re.MULTILINE)


# ── Heuristik: zaehle "effective" LoC ─────────────────────────────────
def _effective_loc(text: str) -> int:
    """Effective LOC = total lines minus blank lines minus pure-comment lines.

    Pure-Comment = Zeile, die (nach Whitespace-Strip) nur aus `// ...`, `/*`,
    `*`, oder einem Block-Comment-Footer besteht. Inline-Kommentare werden
    NICHT abgezogen (die Zeile enthaelt auch Code).
    """
    effective = 0
    in_block = False  # State fuer /* ... */ mehrzeilige Kommentare

    for line in text.splitlines():
        stripped = line.strip()

        # Block-Comment-Tracking
        if in_block:
            # Suche ob der Block-Comment in dieser Zeile endet
            close = stripped.find('*/')
            if close >= 0:
                in_block = False
                # Code kann nach */ noch da sein
                rest = stripped[close + 2:].strip()
                if rest and not rest.startswith('//') and not rest.startswith('*') and not rest.startswith('/*'):
                    effective += 1
            continue  # ganze Zeile ist im Block, nichts zahlen

        # Block-Comment-Start pruefen
        if stripped.startswith('/*'):
            # Wenn die gleiche Zeile auch endet, ist es single-line block comment
            if '*/' in stripped[2:]:
                continue
            in_block = True
            continue

        # Leerzeile
        if not stripped:
            continue

        # Single-line Kommentar
        if stripped.startswith('//') or stripped.startswith('*'):
            continue

        effective += 1

    return effective


def parse_java_metrics(path: Path) -> dict:
    """Liest eine .java-Datei und gibt die Metriken zurueck."""
    text = path.read_text(encoding='utf-8', errors='replace')

    # LOC
    loc = _effective_loc(text)

    # pubM: alle public-method-Deklarationen, minus Getter/Setter-Boilerplate
    pubM_names = []
    for m in _PUBM_RE.finditer(text):
        name = m.group(1)
        if _GETTER_RE.match(name):
            continue
        pubM_names.append(name)
    pubM = len(pubM_names)

    # Fields
    fields = len(_FIELD_RE.findall(text))

    # Imports
    imports = len(_IMPORT_RE.findall(text))

    return {
        'path': str(path),
        'loc': loc,
        'pubM': pubM,
        'fields': fields,
        'imports': imports,
        'pubM_names_sample': pubM_names[:8],  # nur Anriss fuer Debug
    }


def main():
    if len(sys.argv) < 2:
        print(json.dumps({'error': 'usage: parse_metrics.py <path.java> [...]'}), file=sys.stderr)
        sys.exit(1)

    results = []
    for arg in sys.argv[1:]:
        p = Path(arg)
        if not p.exists():
            print(json.dumps({'error': f'file not found: {arg}'}), file=sys.stderr)
            sys.exit(1)
        if p.suffix != '.java':
            continue
        results.append(parse_java_metrics(p))

    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
