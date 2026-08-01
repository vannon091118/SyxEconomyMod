# bench-baseline.save — Snapshot Reference

Ths file describes how to generate the canonical benchmark starting-point that
`SyxBenchmarkHarness` (in `src/vannon/syx/economy/benchmark/`) expects.

## Why a placeholder doc and not an actual .save?

A `.save` file is a Songs-of-Syx **binary chunked save** that contains the entire
`GameSpec`, `WorldGen` seed, world tiles, NPC layouts, settlement initial state
and `EconomySim.CHUNKED_VERSION=33` chunk payload. Hand-authoring such bytes is
infeasible without the engine's exact serializer; even a one-byte drift would
fail the magic-number check in `EconomySaveLoad.loadChunked`. So the canonical
strategy is "generate once with vanilla, ship the bytes":

## Generation steps

1. Launch **vanilla Songs of Syx V71.44** (no mods). Make sure no `bench-config.json`
   / `-Dbench.enabled=true` is in effect, otherwise `SyxBenchmarkHarness` will boot
   too.
2. From the main menu, choose **"Zufälliges Spiel"** (Custom Game).
3. Set the **world generation seed** to **42** (matches `BenchConfig.seed`).
4. Place the settler centre and grant yourself the 3 bootstrap buildings
   demanded by Sprint v0.13.108 (1× Tischler / CARPENTER, 1× Lagerhaus /
   STOCKPILE, 1× Wohnung / DWELLING). This is the canonical `bench-baseline`
   starting state.
5. **Save the game immediately** with name `bench-baseline`. The file lands in
   `<SongsOfSyx>/saves/bench-baseline.save` (or your user-saves dir).
6. Copy it into this directory:

       cp "~/.../saves/bench-baseline.save" tools/bench-baseline.save

   (or wherever your build script expects it).

## How the harness uses it

`SyxBenchmarkHarness` does **not itself read `bench-baseline.save`** at boot —
the vanilla game's save-loader does, and `SCRIPT_INSTANCE.update()` runs on
top of the loaded world. The snapshot is therefore a **doc/contract anchor**:
"if you start the harness from this save with seed 42, every benchmark run is
byte-comparable with every other".

To diff two harness runs:

    diff <(sort run-A.out.csv) <(sort run-B.out.csv)

A blank diff ⇒ identical game evolution ⇒ benchmark passed.

## Why seed 42?

`BenchConfig.defaults()` uses 42 because it is the canonical "answer to life"
seed (`Hitchhiker's Guide`). Reproducibility > novelty.

## If you really want to commit a save file

It is **allowed** to commit `bench-baseline.save` if your CI environment is
capable of regenerating it deterministically. The file is small (< 1 MB
because the world is empty at game-start). Do NOT commit saves > 5 MB without
a strong reason (see `Doku/CHANGELOG.md` rule on binary artifacts).
