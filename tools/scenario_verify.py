#!/usr/bin/env python3
"""
tools/scenario_verify.py — verify mods/saves/custom/*.save files.

Emulates vanilla's PATHS.MISC().CUSTOM().list() in pure Python via os.listdir
filtered by extension .save. For each .save file found, parses the
ScriptGuide-conformant Pre-Spec YAML section (between ---YAML-SPEC-V1--- and
---END-YAML-SPEC-V1--- markers) and validates that all required fields are
present.

Usage:
    python3 tools/scenario_verify.py [DIR] [--target bench-baseline.save] [--quiet]

Exit codes:
    0  all .save files valid + target present + all required fields
    1  drift detected (missing file or missing required field on the target)
    2  no .save files in DIR / DIR does not exist
"""
from __future__ import annotations

import argparse
import os
import sys
from typing import List, Optional, Tuple

# ── Format constants — kept in sync with scenario_snapshot.py. Inlined (not
#    imported) so this script runs independently even when sys.path does not
#    include the tools/ directory (e.g. CI calls from tools/tests/).
MAGIC_HEADER = b"SYXSCN01"
YAML_START_MARKER = b"---YAML-SPEC-V1---\n"
YAML_END_MARKER = b"---END-YAML-SPEC-V1---\n"
SCRIPTGUIDE_MAGIC = b"---SCRIPTGUIDE-V1---\n"
REQUIRED_PRE_SPEC_FIELDS = frozenset({
    "population", "world_x", "world_y", "seed",
    "mods", "note", "scriptguide_version",
})


# ── Minimal YAML subset parser (stdlib-only) ────────────────────────────────

def _parse_minimal_yaml(text: str) -> dict:
    """Parse a flat YAML dict produced by scenario_snapshot._yaml_dump_minimal.

    Supports: int, str (quoted), literal `|` block (multi-line note),
    list of {id, version}. Recognises `key:` with empty value followed by
    a list item as a deferred list-mode entry (does not store "" string,
    avoiding the `.append()` AttributeError on a previously stored string).
    """
    result: dict = {}
    lines = text.split("\n")
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip() or line.lstrip().startswith("#"):
            i += 1
            continue
        # List-item at indent 2: `  - id: ...`
        if line.startswith("  - "):
            mod: dict = {}
            i += 1
            while i < len(lines) and lines[i].startswith("    "):
                kv = lines[i].strip().split(":", 1)
                if len(kv) == 2:
                    k = kv[0].strip()
                    v = kv[1].strip().strip('"').strip("'")
                    mod[k] = v
                i += 1
            result.setdefault("mods", []).append(mod)
            continue
        if ":" not in line:
            i += 1
            continue
        key, _, val = line.partition(":")
        key = key.strip()
        val = val.strip()
        if val == "|":
            # Literal block — read until dedented; preserve trailing newline.
            i += 1
            block: List[str] = []
            while i < len(lines) and (lines[i].startswith("  ")
                                       or lines[i] == ""):
                block.append(lines[i][2:] if lines[i].startswith("  ")
                              else lines[i])
                i += 1
            while len(block) > 1 and not block[-1] and not block[-2]:
                block.pop()
            if block and not block[-1]:
                block[-1] = ""
            result[key] = "\n".join(block)
            continue
        if val.startswith('"') and val.endswith('"') and len(val) >= 2:
            val = val[1:-1]
        elif val.startswith("'") and val.endswith("'") and len(val) >= 2:
            val = val[1:-1]
        # Empty value with a list-item on the next line → defer to list-mode
        # parser (do NOT pre-store empty-string, otherwise list-mode parser
        # will fail when it tries result.setdefault("mods", []).append).
        if val == "" and i + 1 < len(lines) \
                and lines[i + 1].startswith("  - "):
            i += 1
            continue
        try:
            result[key] = int(val)
        except ValueError:
            result[key] = val
        i += 1
    return result


# ── File-level helpers ──────────────────────────────────────────────────────

def _emulate_custom_listing(dirpath: str) -> List[str]:
    """Emulate Java PATHS.MISC().CUSTOM().list() — list .save files in dir."""
    if not os.path.isdir(dirpath):
        return []
    return sorted(
        os.path.join(dirpath, f) for f in os.listdir(dirpath)
        if f.endswith(".save"))


def _parse_save_file(path: str) -> Tuple[bool, str, dict, dict]:
    """Parse a .save file. Returns (ok, error, spec_dict, integrity)."""
    try:
        with open(path, "rb") as fh:
            data = fh.read()
    except OSError as e:
        return False, f"I/O error: {e}", {}, {}
    if not data.startswith(MAGIC_HEADER):
        return False, f"missing magic header {MAGIC_HEADER!r}", {}, {}
    if YAML_START_MARKER not in data:
        return False, f"missing Pre-Spec start marker", {}, {}
    if YAML_END_MARKER not in data:
        return False, f"missing Pre-Spec end marker", {}, {}
    try:
        start = data.index(YAML_START_MARKER) + len(YAML_START_MARKER)
        end = data.index(YAML_END_MARKER)
        spec = _parse_minimal_yaml(data[start:end].decode("utf-8"))
    except (UnicodeDecodeError, ValueError) as e:
        return False, f"YAML parse error: {e}", {}, {}
    integrity = {
        "has_magic": True,
        "has_yaml_markers": True,
        "has_scriptguide_trailer": SCRIPTGUIDE_MAGIC in data,
        "size_bytes": len(data),
    }
    return True, "", spec, integrity


def _validate_spec(spec: dict) -> List[str]:
    missing = REQUIRED_PRE_SPEC_FIELDS - set(spec.keys())
    errors: List[str] = []
    if missing:
        errors.append(f"missing required fields: {sorted(missing)}")
    if spec.get("population", 0) < 1:
        errors.append(f"population={spec.get('population')!r} (must be ≥ 1)")
    if spec.get("world_x", 0) < 16:
        errors.append(f"world_x={spec.get('world_x')!r} (must be ≥ 16)")
    if spec.get("world_y", 0) < 16:
        errors.append(f"world_y={spec.get('world_y')!r} (must be ≥ 16)")
    if not spec.get("mods"):
        errors.append("mods list is empty")
    if not (spec.get("note") or "").strip():
        errors.append("note is empty")
    return errors


# ── Reporting ───────────────────────────────────────────────────────────────

def render_report(target_arg: str, save_files: List[str],
                  results: list) -> str:
    out: List[str] = []
    bar = "\u2500" * 76
    out.append(bar)
    out.append("SCENARIO VERIFY — emulating PATHS.MISC().CUSTOM().list()")
    out.append(bar)
    if not save_files:
        out.append("  No .save files found in custom-directory.")
        out.append("  >>> FAIL — bench-baseline.save missing from custom dir <<<")
    else:
        out.append("  Custom-dir listing (mirrors Java):")
        for f in save_files:
            out.append(f"    {f}")
        out.append("")
        out.append(f"  Found {len(save_files)} .save file(s)")
        out.append("")
    for path, ok, err, spec, integrity in results:
        fname = os.path.basename(path)
        out.append(f"  \u2022 {fname}")
        if not ok:
            out.append(f"      \u2717 FAIL \u2014 {err}")
            continue
        out.append(f"      \u2713 PASS \u2014 {integrity['size_bytes']} bytes, "
                   f"magic={integrity['has_magic']}, "
                   f"yaml_markers={integrity['has_yaml_markers']}, "
                   f"scriptguide_trailer={integrity['has_scriptguide_trailer']}")
        errs = _validate_spec(spec)
        if errs:
            out.append(f"      \u2717 DRIFT \u2014 {len(errs)} field(s) invalid:")
            for e in errs:
                out.append(f"          - {e}")
        else:
            fields = ", ".join(f"{k}={spec.get(k)!r}"
                               for k in sorted(REQUIRED_PRE_SPEC_FIELDS)
                               if k in spec)
            out.append(f"      fields: {fields[:120]}")
    out.append("")
    target = os.path.basename(target_arg)
    found = [os.path.basename(p) for p in save_files]
    if target not in found:
        out.append(f"  >>> FAIL \u2014 target '{target}' not in custom-dir listing <<<")
    else:
        target_result = next(r for r in results
                             if os.path.basename(r[0]) == target)
        path, ok, err, spec, integrity = target_result
        if not ok:
            out.append(f"  >>> FAIL \u2014 target '{target}' parse/integrity error <<<")
        elif _validate_spec(spec):
            out.append(f"  >>> FAIL \u2014 target '{target}' field-validation failure <<<")
        else:
            out.append(f"  >>> PASS \u2014 target '{target}' present + valid + complete <<<")
    out.append(bar)
    return "\n".join(out)


# ── CLI ────────────────────────────────────────────────────────────────────

def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Verify mods/saves/custom/*.save Pre-Spec YAML.",
    )
    parser.add_argument("dir", nargs="?", default="mods/saves/custom",
                        help="Custom scenarios directory (default: "
                             "mods/saves/custom)")
    parser.add_argument("--target", default="bench-baseline.save",
                        help="Target save file to validate (default: "
                             "bench-baseline.save)")
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args(argv)

    if not os.path.isdir(args.dir):
        sys.stderr.write(f"ERROR: custom-dir not found: {args.dir}\n")
        return 2

    save_files = _emulate_custom_listing(args.dir)
    if not save_files:
        if not args.quiet:
            print(render_report(args.target, [], []))
        return 2

    results = []
    for path in save_files:
        ok, err, spec, integrity = _parse_save_file(path)
        results.append((path, ok, err, spec, integrity))

    if not args.quiet:
        print(render_report(args.target, save_files, results))

    target_full = os.path.join(args.dir, args.target)
    if target_full not in save_files:
        return 1
    target_result = next(r for r in results if r[0] == target_full)
    path, ok, err, spec, integrity = target_result
    if not ok:
        return 1
    if _validate_spec(spec):
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
