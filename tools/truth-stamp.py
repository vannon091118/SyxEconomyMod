#!/usr/bin/env python3
"""
tools/truth-stamp.py — Update ## Stand: lines in docs/reports/TRUTH_REPORT.md
based on changed docs/ files (stamp mode) or insert initial registries+stands
(init mode).

Modes:
  stamp  Update sections whose <!-- truth-tracks: ... --> registry matches
         any file changed in the last commit (per `git diff HEAD~1 HEAD`).
         Increments the section's | checks: N counter.
  init   Insert initial <!-- truth-tracks: ... --> + ## Stand: ... lines
         for any section that lacks them, using the curated
         SECTION_TRACKS mapping below.

Usage:
  python3 tools/truth-stamp.py stamp REPORT_PATH TIMESTAMP SHORT_HASH
  python3 tools/truth-stamp.py init  REPORT_PATH

Convention (expected in TRUTH_REPORT.md):
  ## N. Title
  <!-- truth-tracks: file1, file2, ... -->
  ## Stand: YYYY-MM-DD HH:MM | commit <hash> | checks: <N>

Why Python: the project's existing tools/ directory is bash-driven for the
*gate/audit* tools (build-gate.sh, code-audit.sh) but bash+Awk breaks down
for multi-line markdown section splitting + regex matching across thousands
of chars in a single buffer. Python with `re` does this cleanly in ~40 LOC.
The bash wrapper in tools/truth-stamp.sh handles preflight + git context.
"""

import sys
import re
import subprocess
from pathlib import Path

# ── Section-to-tracked-files mapping (manually curated) ─────────────
# Each entry is a path-prefix or exact path. The stamp engine matches a
# tracked entry `t` against a changed file `c` if `c == t or c.startswith(t)`.
# Update this dict when adding a new section to TRUTH_REPORT.md.
SECTION_TRACKS = {
    "1":  ["src/vannon/", "pom.xml", "tools/", "CHANGELOG.md", "README.md",
           "docs/ROADMAP.md", "docs/GLOSSARY.md", "docs/ARCHITECTURE.md"],
    "2":  ["src/vannon/syx/economy/core/TreasuryCrisis.java", "README.md",
           "CHANGELOG.md", "docs/ROADMAP.md", "docs/SESSION_SUMMARY_2026-07-23.md"],
    "3":  ["src/vannon/syx/economy/core/DiagnosticExporter.java", "CHANGELOG.md",
           "README.md", "docs/SESSION_SUMMARY_2026-07-23.md"],
    "4":  ["test/", "docs/ROADMAP.md"],
    "5":  ["CHANGELOG.md", "IMPLEMENTATION_PLAN.md", "docs/superpowers/plans/"],
    "6":  ["pom.xml", "CHANGELOG.md"],
    "7":  ["docs/superpowers/plans/", "docs/ROADMAP.md", "README.md"],
    "8":  ["src/vannon/syx/economy/core/DiagnosticExporter.java"],
    "9":  ["CHANGELOG.md", "README.md", "docs/ROADMAP.md",
           "docs/SESSION_SUMMARY_2026-07-23.md", "docs/CHANGELOG.md",
           "docs/README.md", "IMPLEMENTATION_PLAN.md"],
    "10": ["pom.xml", "tools/"],
    "11": ["tools/docs-truth-consistency.sh"],
    "12": ["docs/GLOSSARY.md", "docs/ARCHITECTURE.md",
           "docs/SESSION_SUMMARY_2026-07-23.md", "CHANGELOG.md",
           "docs/HISTORICAL_SEMANTIC_DIFF.md"],
    "13": ["docs/HISTORICAL_", "docs/ARCHITECTURE.md", "docs/README.md",
           "docs/reports/"],
    "14": ["tools/", "docs/"],
    "15": ["tools/docs-truth-consistency.sh"],
    "16": ["tools/bump-version.sh", "tools/post-commit-pom-watchdog.sh",
           "tools/install-hooks.sh"],
}

# Lines used during init when there's no existing stand line.
INIT_TIMESTAMP = "2026-07-23 17:00"
INIT_HASH = "initial"


def _split_sections(content: str):
    """Split markdown content into (preamble, [section_id, section_text, ...]).

    Sections are delimited by H2 headers matching `^## N. ` (numbered sections).
    The preamble (header of file plus all content before the first numbered
    section) is returned first.
    """
    header_re = re.compile(r"^## (\d+)\. ", re.MULTILINE)
    parts = header_re.split(content)
    preamble = parts[0]
    sections = []
    # parts[1::2] = section IDs, parts[2::2] = section body (post-header)
    for i in range(1, len(parts), 2):
        num = parts[i]
        body = parts[i + 1]
        section = f"## {num}. " + body
        sections.append((num, section))
    return preamble, sections


def stamp(report_path: Path, ts: str, short_hash: str) -> int:
    """Stamp mode: update sections matching last commit's docs/-changes."""
    try:
        raw = subprocess.check_output(
            ["git", "diff", "--name-only", "HEAD~1", "HEAD"],
            text=True, stderr=subprocess.DEVNULL,
        )
    except subprocess.CalledProcessError:
        print("Kein voriger Commit - exit")
        return 0

    changed = sorted(set(line for line in raw.splitlines() if line.startswith("docs/")))
    if not changed:
        print("Keine docs/-Dateien im Commit - nichts zu stempeln")
        return 0

    print(f"Geaenderte docs/-Dateien: {len(changed)}")
    for c in changed:
        print(f"  - {c}")

    return _process_stamps(report_path, ts, short_hash, changed)


def init_mode(report_path: Path) -> int:
    """Init mode: insert initial registry + stand for any uninitialised section."""
    content = report_path.read_text()
    preamble, sections = _split_sections(content)

    new_sections = []
    init_count = 0
    skipped = []

    for num, section in sections:
        tracks = SECTION_TRACKS.get(num)
        if not tracks:
            # No mapping defined: leave section untouched (skip silently).
            new_sections.append(section)
            skipped.append(num)
            continue

        has_reg = re.search(r"<!-- *truth-tracks: *([^>]+?) *-->", section)
        has_stand = re.search(r"^## Stand: ", section, re.MULTILINE)

        if has_reg and has_stand:
            # Already initialised.
            new_sections.append(section)
            continue

        modified = section

        if not has_reg:
            # Insert registry comment after first newline (= section header line).
            first_nl = modified.find("\n")
            registry_line = (
                "\n<!-- truth-tracks: " + ", ".join(tracks) + " -->"
            )
            modified = modified[:first_nl] + registry_line + modified[first_nl:]

        if not has_stand:
            # Insert stand block after registry (or after header if no registry).
            reg_match = re.search(r"<!-- *truth-tracks: *[^>]+? *-->", modified)
            if reg_match:
                ins_at = reg_match.end()
            else:
                ins_at = modified.find("\n")
            nl_at = modified.find("\n", ins_at)
            stand_line = (
                f"\n\n## Stand: {INIT_TIMESTAMP} | commit {INIT_HASH}"
                f" | checks: 1\n"
            )
            if nl_at >= 0:
                modified = modified[:nl_at + 1] + stand_line + modified[nl_at + 1:]
            else:
                modified = modified + stand_line

        new_sections.append(modified)
        init_count += 1

    new_content = preamble + "".join(new_sections)
    report_path.write_text(new_content)

    print(f"Retrofit: {init_count} Sektion(en) initialisiert.")
    if skipped:
        print(f"Ohne Mapping uebersprungen: {', '.join(skipped)}")
    return 0


def _process_stamps(report_path: Path, ts: str, short_hash: str,
                   changed_files) -> int:
    """Walk sections, update Stand: lines for sections whose registry matches."""
    content = report_path.read_text()
    preamble, sections = _split_sections(content)

    new_sections = []
    stamped = []

    for num, section in sections:
        m = re.search(r"<!-- *truth-tracks: *([^>]+?) *-->", section)
        if not m:
            # No registry: skip (the section hasn't been retrofitted yet).
            new_sections.append(section)
            continue

        tracks = [t.strip() for t in m.group(1).split(",") if t.strip()]

        # Match if any tracked entry matches any changed file.
        if not any(
            any(c == t or c.startswith(t) for c in changed_files)
            for t in tracks
        ):
            new_sections.append(section)
            continue

        # Build new stand line, preserving existing check counter.
        prev = re.search(r"\|\s*checks:\s*(\d+)", section)
        prev_count = int(prev.group(1)) if prev else 0
        new_stand = (
            f"## Stand: {ts} | commit {short_hash} | checks: {prev_count + 1}\n"
        )

        stand_pat = re.compile(r"^## Stand: .*?\n", re.MULTILINE)
        if stand_pat.search(section):
            section = stand_pat.sub(new_stand, section)
        else:
            # Stand line missing despite registry: insert after registry.
            reg_end = m.end()
            nl = section.find("\n", reg_end)
            if nl != -1:
                section = section[:nl + 1] + "\n" + new_stand + section[nl + 1:]
            else:
                section = section + "\n" + new_stand

        new_sections.append(section)
        stamped.append(num)

    new_content = preamble + "".join(new_sections)
    report_path.write_text(new_content)

    if stamped:
        print(f"\nSection(s) gestempelt: {len(stamped)}")
        for s in stamped:
            print(f"  - ## {s}")
        # Stage the change so the user can amend or follow-up commit.
        result = subprocess.run(
            ["git", "add", str(report_path)],
            capture_output=True, text=True,
        )
        if result.returncode != 0:
            print(
                f"WARNUNG: git add exit={result.returncode}",
                file=sys.stderr,
            )
            if result.stderr:
                print(f"  stderr: {result.stderr.strip()}", file=sys.stderr)
        print("\nTRUTH_REPORT.md gestaged. Empfehlung:")
        print("  git commit --amend   # Stamp in letzten Commit einbacken")
        print("  git commit -m 'docs: truth-stamp'")
    else:
        print("\nKeine Sektion matchte - TRUTH_REPORT.md unveraendert")
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: truth-stamp.py [stamp|init] ...", file=sys.stderr)
        sys.exit(2)

    mode = sys.argv[1]
    if mode == "stamp":
        if len(sys.argv) < 5:
            print("Usage: stamp REPORT TS HASH", file=sys.stderr)
            sys.exit(2)
        sys.exit(stamp(Path(sys.argv[2]), sys.argv[3], sys.argv[4]))
    elif mode == "init":
        if len(sys.argv) < 3:
            print("Usage: init REPORT", file=sys.stderr)
            sys.exit(2)
        sys.exit(init_mode(Path(sys.argv[2])))
    else:
        print(f"Unbekannter Modus: {mode}", file=sys.stderr)
        sys.exit(2)
