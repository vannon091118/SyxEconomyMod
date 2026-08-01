#!/usr/bin/env python3
"""
tools/scenario_snapshot.py — Generate a Song-of-Syx custom-scenario Snapshot
                              save file with reproducible YAML Pre-Spec.

The vanilla game lists custom scenario files via PATHS.MISC().CUSTOM().list()
— any .save file in mods/saves/custom/ shows up in the "Zufälliges Spiel" /
"Zufaelliges Spiel" menu. The vanilla loader then attempts to parse the file
as a binary chunked save (GameSpec header + chunked body); hand-authoring the
binary portion is infeasible without engine boot (see
tools/bench-baseline-snapshot.md).

This tool produces a custom-listable save file with a YAML Pre-Spec section
that is the SSoT for spec values (population, wx, wy, seed, mods, note).
Same params → byte-identical file (deterministic by construction: no
timestamps, no PIDs, no UUIDs, fixed key order).

Usage:
    python3 tools/scenario_snapshot.py
        [--out PATH]
        [--population N] [--world-x N] [--world-y N] [--seed N]
        [--mods ID:VERSION ...]
        [--note "free text"]
        [--print-only] [--verify-hash SHA256]

Exit codes:
    0  file written (or printed) + byte-determinism preserved
    1  invalid parameters / validation failure
    2  I/O error (directory creation or file write failed)
"""
from __future__ import annotations

import argparse
import hashlib
import os
import sys
from typing import List, Optional, Tuple

# ── Format constants — keep stable for byte-determinism ──────────────────────

MAGIC_HEADER = b"SYXSCN01"                       # 8 bytes ASCII
YAML_START_MARKER = b"---YAML-SPEC-V1---\n"      # 18 bytes
YAML_END_MARKER = b"---END-YAML-SPEC-V1---\n"    # 21 bytes
SCRIPTGUIDE_MAGIC = b"---SCRIPTGUIDE-V1---\n"     # 20 bytes (trailer)

# The trailer is encoded as a regular UTF-8 Python string (so the umlaut
# ä and arrow → live as literal source bytes, avoiding Python 3.14
# SyntaxWarning on \uXXXX escapes in raw byte-strings).
SCRIPTGUIDE_TRAILER_NOTE = (
    "# ScriptGuide Trailer V1\n"
    "# This trailer block is reserved for future integration with vanilla's\n"
    "# GameSpec section. The Pre-Spec YAML above is the SSoT for tooling.\n"
    "# Vanilla can list this file via PATHS.MISC().CUSTOM().list(); engine\n"
    "# will refuse to load until the binary GameSpec section is filled in via\n"
    "# the canonical 'Zufälliges Spiel → Save' workflow (see\n"
    "# tools/bench-baseline-snapshot.md).\n"
).encode("utf-8")

DEFAULT_OUT = "mods/saves/custom/bench-baseline.save"
DEFAULT_POPULATION = 50
DEFAULT_WORLD_X = 128
DEFAULT_WORLD_Y = 64
DEFAULT_SEED = 1_392_191
DEFAULT_MODS = ["SyxEconomyMod:0.13.108"]
DEFAULT_NOTE = (
    "Bench-baseline scenario for SyxBenchmarkHarness + EconomyMock + "
    "HeadlessBenchTest + benchmark-compare.sh. Deterministic by seed; "
    "rerun this tool with same params for byte-identical output."
)

REQUIRED_PRE_SPEC_FIELDS = frozenset({
    "population", "world_x", "world_y", "seed",
    "mods", "note", "scriptguide_version",
})


# ── YAML emitter (stdlib-only, hand-rolled) ─────────────────────────────────

def _yaml_dump_minimal(spec: dict) -> str:
    """Hand-rolled minimal YAML emitter — byte-deterministic by construction.

    Fixed key order, fixed indentation (2 spaces for list, 4 for list-items),
    multi-line notes use literal-block `|` symbols preserving internal
    newlines. No timestamps, no PIDs, no UUIDs.
    """
    out: List[str] = []
    order = ["scriptguide_version", "world_x", "world_y", "population",
             "seed", "mods", "note"]
    for key in order:
        if key not in spec:
            continue
        val = spec[key]
        if key == "mods":
            out.append("mods:")
            for m in val:
                out.append(f"  - id: {m['id']}")
                out.append(f"    version: \"{m['version']}\"")
            continue
        if isinstance(val, str):
            # Literal-block if multi-line OR very long
            if "\n" in val or len(val) > 80:
                out.append(f"{key}: |")
                for subline in val.split("\n"):
                    out.append(f"  {subline}")
            else:
                out.append(f'{key}: "{val}"')
            continue
        out.append(f"{key}: {val}")
    return "\n".join(out) + "\n"


def _parse_mod_string(s: str) -> dict:
    """Parse 'modId:version' into {'id':..., 'version':...}."""
    parts = s.split(":", 1)
    mod_id = parts[0].strip()
    version = parts[1].strip() if len(parts) == 2 else "0.0.0"
    return {"id": mod_id, "version": version}


def _build_spec_dict(args) -> dict:
    return {
        "scriptguide_version": 1,
        "world_x": args.world_x,
        "world_y": args.world_y,
        "population": args.population,
        "seed": args.seed,
        "mods": [_parse_mod_string(m) for m in args.mods],
        "note": args.note,
    }


def validate_spec(spec: dict) -> List[str]:
    """Return a list of validation errors (empty list = valid)."""
    errors: List[str] = []
    if REQUIRED_PRE_SPEC_FIELDS - set(spec.keys()):
        errors.append(f"missing required Pre-Spec fields: "
                      f"{sorted(REQUIRED_PRE_SPEC_FIELDS - set(spec.keys()))}")
    if spec.get("population", 0) < 1:
        errors.append(f"population must be ≥ 1 (got {spec.get('population')})")
    if spec.get("world_x", 0) < 16:
        errors.append(f"world_x must be ≥ 16 (got {spec.get('world_x')})")
    if spec.get("world_y", 0) < 16:
        errors.append(f"world_y must be ≥ 16 (got {spec.get('world_y')})")
    if not spec.get("mods"):
        errors.append("at least one mod is required")
    if not (spec.get("note") or "").strip():
        errors.append("note must be non-empty")
    return errors


def build_file_content(spec: dict) -> bytes:
    parts = [
        MAGIC_HEADER,
        b"\n",
        YAML_START_MARKER,
        _yaml_dump_minimal(spec).encode("utf-8"),
        YAML_END_MARKER,
        SCRIPTGUIDE_MAGIC,
        SCRIPTGUIDE_TRAILER_NOTE,
    ]
    return b"".join(parts)


def write_atomic(path: str, content: bytes) -> None:
    parent = os.path.dirname(path) or "."
    os.makedirs(parent, exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "wb") as fh:
        fh.write(content)
        fh.flush()
        os.fsync(fh.fileno())
    os.replace(tmp, path)


def verify_byte_determinism(content: bytes, expected_hash: Optional[str]
                             ) -> Tuple[bool, str]:
    sha = hashlib.sha256(content).hexdigest()
    if expected_hash and sha != expected_hash:
        return False, sha
    return True, sha


# ── CLI ────────────────────────────────────────────────────────────────────

def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description="Generate Song-of-Syx custom-scenario Snapshot save "
                    "file with reproducible YAML Pre-Spec.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--out", default=DEFAULT_OUT,
                        help=f"Output path (default: {DEFAULT_OUT})")
    parser.add_argument("--population", type=int, default=DEFAULT_POPULATION)
    parser.add_argument("--world-x", type=int, default=DEFAULT_WORLD_X)
    parser.add_argument("--world-y", type=int, default=DEFAULT_WORLD_Y)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--mods", nargs="+", default=DEFAULT_MODS,
                        help="Mod list as 'id:version' strings (default: "
                             f"{' '.join(DEFAULT_MODS)})")
    parser.add_argument("--note", default=DEFAULT_NOTE)
    parser.add_argument("--print-only", action="store_true",
                        help="Print file content to stdout instead of "
                             "writing to disk (useful for diffing).")
    parser.add_argument("--verify-hash", metavar="SHA256", default=None,
                        help="Compare SHA-256 against this hash after write; "
                             "exit 1 on mismatch.")
    args = parser.parse_args(argv)

    spec = _build_spec_dict(args)
    errors = validate_spec(spec)
    if errors:
        sys.stderr.write("ERROR: invalid spec:\n")
        for e in errors:
            sys.stderr.write(f"  - {e}\n")
        return 1

    content = build_file_content(spec)
    ok, sha = verify_byte_determinism(content, args.verify_hash)
    if not ok:
        sys.stderr.write(f"ERROR: SHA-256 mismatch: got {sha} "
                         f"expected {args.verify_hash}\n")
        return 1

    if args.print_only:
        sys.stdout.write(content.decode("utf-8"))
        return 0

    try:
        write_atomic(args.out, content)
    except OSError as e:
        sys.stderr.write(f"ERROR: I/O failure writing {args.out}: {e}\n")
        return 2

    print(f"Wrote {args.out}  ({len(content)} bytes, SHA-256={sha[:16]}\u2026)")
    print(f"  population={spec['population']} world_x={spec['world_x']} "
          f"world_y={spec['world_y']} seed={spec['seed']} "
          f"mods={spec['mods']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
