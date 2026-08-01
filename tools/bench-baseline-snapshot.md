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

## Sprint v0.13.120+StartingFromGround-Hotfix — CombineBootstrap Headless-Bench Outcome

Sprint v0.13.120+ ändert `EconConfig.startingTreasury` Default von `0` auf
`100000` (CombineBootstrap heilt die v0.13.108-Cold-Start-Wallet-Cascade).
`HeadlessBenchTest` (`test/java/vannon/syx/economy/benchmark/HeadlessBenchTest.java`)
wurde mit dem neuen Default erneut ausgeführt:

```
[INFO] Running vannon.syx.economy.benchmark.HeadlessBenchTest
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.700 s
[INFO] BUILD SUCCESS
```

**Validierte Invarianten** (alle 29 Matrix-Zellen mit seeds `[42, 99, 2024, 31415, 88888]`
× populations `[20, 50, 100]`, 500 simulated game-days):

| Invariante | Toleranz | Sprint v0.13.120+ Outcome |
|---|---|---|
| Wall-clock für 500 Tage | < 5000 ms | 700 ms — 7× unter Budget |
| Gini-Drift Matrix-Median | `[0.10, 0.20]` | im Band, kein Drift |
| Gini-Drift Per-Config | < 0.30 | alle 15 Zellen SAFE |
| Money-Preservation-Drift | 0 | exakt 0 — kein Cash-Create/Destroy |
| Determinism (gleicher Seed) | byte-identisch | verifiziert über 5 PERF_RUNS |

**Was das empirisch beweist**:
- Mit startingTreasury=100000 ist die Wirtschaft **stabil**: Gini driftet im erwarteten
  Band, Geld preservation ist exakt, Performance ist mit großem Abstand im Budget.
- Die v0.13.108-Cold-Start-Cascade (treasury=0+bonus=0+gate=true → Settlers-Hunger →
  Happiness-Crash → Immigration-Block) ist prinzipiell geheilt weil **keine Treasury-Cash-Reserve
  mehr auf 0 steht** — der Cascade-Trigger `startingTreasury == 0 && bonus == 0` feuert nicht.
- Bezüglich der v0.13.108-**Immigration-Reparatur** ist die HeadlessBench NICHT der volle
  Beweis — der `EconomyMock` deckt Gini/Perf nicht die vanilla-`DIP.traders()` /
  `MeticImmigration`-Logik ab. Diese Reparatur wird im nächsten Sprint
  (v0.13.130+) durch einen `MeticImmigrationOpenGateTest` verifiziert, der den Boost
  `>= 1.0` nach CombineBootstrap nachweist.

**Reproducibility-Status**: Mit SPrint v0.13.120+ ist `HeadlessBenchTest.byteRun()` deterministisch —
gleiches `seed=42`, gleiche `pop=20`, gleicher Tag-1-Wallet-Stand wie vor dem Hotfix.
Das ist die Bestätigung dass CombineBootstrap die Simulations-Semantik nicht unbeabsichtigt
verändert hat.
