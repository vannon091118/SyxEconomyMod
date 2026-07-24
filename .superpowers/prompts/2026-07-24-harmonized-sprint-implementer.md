# Harmonized Sprint — Implementer Prompt

> **Mission:** Execute the UI-Refactor (Plan A) and the Redundancy-Elimination + Modularization (Plan B) in a single coherent sprint. No shortcuts. No soft fixes. Every compile must be clean and every test must pass without catching failures.

## Context

You are working on `SyxEconomyMod`, a Java 21 Songs of Syx V71.44 mod located at `/home/vannon/Schreibtisch/SyxEconomyMod_Workspace`.

Two plans must be harmonized:

1. **Plan A — 3-Window UI Refactor:** `docs/superpowers/plans/2026-07-24-3-window-ux-refactor.md`
   - Replace the 3,081-LOC `EconomyWindow.java` god-file with three focused `Interrupter` windows (`WindowOverview`, `WindowEconomy`, `WindowState`).
   - Use the Vanilla Songs-of-Syx UI widgets (`GButt`, `GSliderInt`, `GChart`, `GText`, `GuiSection`, `InterGuisection`) wherever possible.
   - Do not reimplement what the engine already provides.

2. **Plan B — Redundancy-Elimination + Modularization:** pasted by the user.
   - P0: Snapshot current work.
   - P1: Safe cleanups (`mmm.zip`, `target/`, `diagnostics_plots/`, `.freebuff/`).
   - P2: Add regression tests for `ChunkedSave`, `FoodRollbackKernel`, `ExchangeKernel`, `AuditKernel`, `EscrowKernel`, `FoodGateKernel`, `FirmEconomyKernel`.
   - P3: Extract `EconomyPhase` interface and split `EconomySim.update()` into phases.
   - P4: Introduce `SaveLoadSubsystem` registry and refactor `saveChunked`/`loadChunked`.

## Hard Rules (Non-Negotiable)

1. **Zero Warnings Tolerance**
   - `mvn compile -q` must produce **BUILD SUCCESS** with **zero** compiler warnings.
   - A warning is a failure. Do not ignore it, do not suppress it without explicit reason, do not classify it as "expected".
   - Treat `-Xlint:unchecked`, deprecation, and raw-type warnings as hard blockers.

2. **No Soft Fixes**
   - Do not "just make it compile". Every change must be correct by construction.
   - Do not add `catch (Throwable)` to silence exceptions.
   - Do not add empty `catch` blocks.
   - Do not use `@SuppressWarnings` unless the warning is truly unavoidable and you document why.
   - Do not leave `TODO`, `FIXME`, or `XXX` in committed code.

3. **Tests Must Pass Without Catching Failures**
   - `mvn test` must pass with all tests green.
   - Tests must assert expected behavior, not catch exceptions to hide failures.
   - If a test needs setup, provide it. If a class is hard to test, refactor it — do not write a placeholder test.
   - No `try { ... } catch (Exception e) { fail(e); }` patterns. Tests should fail naturally when preconditions are wrong.

4. **Preserve Engine Compatibility**
   - Do **not** change `Interrupter` lifecycle methods (`show/hide/render/mouseClick/hover`).
   - Do **not** change `CHUNKED_VERSION = 33` or `CHUNK_MAGIC = 0xEC0FEC0F`.
   - Do **not** change the `ChunkedSave` byte layout.
   - Do **not** change any `ISyx*` adapter interface.
   - Do **not** introduce `catch (Throwable)` outside `src/vannon/syx/economy/adapter/`.
   - All new files must be in `vannon.syx.economy.*` packages.

5. **Long-Term Maintainability**
   - Replace custom immediate-mode widgets with Vanilla widgets (`GButt`, `GSliderInt`, `GChart`).
   - Never initialize UI assets (`UI.FONT()`, `GText`, `GButt`, etc.) in `static` fields or blocks.
   - Prefer `GuiSection` composition over manual coordinate math.
   - Keep state explicit, not hidden in `IdentityHashMap<String, Object>`.

6. **Validation Gate After Every Sub-Task**
   - After every file change or sub-task, run the appropriate validation commands.
   - Do not proceed to the next sub-task while any validation fails.
   - Final gate for the whole sprint: `mvn clean test` and `bash tools/phase47-shield.sh`.

## Execution Order

Execute strictly in this order. Do not skip, do not parallelize across phases.

### Phase 0 — Snapshot & Baseline

1. Check `git status --short`. Commit all uncommitted files with a descriptive message.
2. Tag the commit: `git tag pre-sprint-2026-07-24`.
3. Run `mvn test` and `bash tools/phase47-shield.sh`. All must pass before continuing.

### Phase 1 — Safe Cleanups

1. Delete `mmm.zip`.
2. Run `mvn clean` to remove `target/`.
3. Delete `diagnostics_plots/`.
4. Delete `.freebuff/`.
5. Verify `.gitignore` contains: `target/`, `diagnostics_plots/`, `.freebuff/`, `*.zip`.
6. Validation: `git status --short` must not show the deleted files; `mvn compile -q` must succeed.

### Phase 2 — UI Refactor Completion

1. Refactor `EconWindowBase`:
   - Remove manual frame/tab/KPI rendering.
   - Use a root `GuiSection` with a title, dynamic tab bar, KPI header, and content section.
   - `render()` must return `false` (do not block the world background).
   - Remove the `pendingLeftClick` boolean; delegate click handling to Vanilla widgets.
   - Make tab width dynamic: `(w - 16) / tabs.size()`.

2. Replace `EconWidgets` with `EconWidgetFactory`:
   - `button(CharSequence)` returns `GButt.Panel`.
   - `toggle(CharSequence)` returns `GButt.Checkbox`.
   - `slider(int min, int max, int steps)` returns `GSliderInt`.
   - `text(CharSequence)` returns `GText`.
   - No static recycled `GText` instances.

3. Update `EconTab` interface:
   - `CharSequence title()`
   - `GuiSection createSection(EconomySim sim)`
   - `void onOpen()`
   - Remove `render(EconContext, int)` and `click(EconContext, MButt)`.

4. Rewrite each tab class in `OverviewTabs`, `EconomyTabs`, `StateTabs` to build a `GuiSection` using `EconWidgetFactory` and Vanilla widgets.

5. Update `WindowOverview`, `WindowEconomy`, `WindowState` to use the new `EconTab` interface.

6. Remove `EconContext.java` if it is no longer needed; otherwise reduce it to a data-only record.

7. Validation:
   - `mvn compile -q` — zero warnings.
   - `mvn test` — all tests pass.
   - `bash tools/phase47-shield.sh` — PASS.

### Phase 3 — Tests (Plan B P2)

Create the following test files under `test/java/vannon/syx/economy/core/`:

1. `ChunkedSaveTest.java` — roundtrip single chunk, skip chunk, empty stream, malformed tag.
2. `FoodRollbackKernelTest.java` — proportional allocation, sufficient stock, zero demand, exact stock.
3. `ExchangeKernelTest.java` — wealth conservation, never negative, no exchange when broke.
4. `AuditKernelTest.java` — delta zero when balanced, delta detects leak.
5. `EscrowKernelTest.java` — placeholder only if public API missing; otherwise real tests.
6. `FoodGateKernelTest.java` — placeholder only if public API missing; otherwise real tests.
7. `FirmEconomyKernelTest.java` — placeholder only if public API missing; otherwise real tests.

Validation:
- `mvn test -Dtest=<EachTest>` passes individually.
- `mvn test` passes with the full suite.

### Phase 4 — EconomyPhase Refactor (Plan B P3)

1. Create `EconomyPhase.java` interface with `int order()` and `void execute(EconomySim sim, double ds)`.
2. Create package `vannon.syx/economy/core/phase/`.
3. Extract phases in order:
   - `WarehousePhase` (order=50)
   - `PricesPhase` (order=100)
   - `ProductionPhase` (order=250)
   - `CitizenPhase` (order=300)
   - `WagePhase` (order=400)
   - `LaborPhase` (order=500)
   - `FiscalPhase` (order=600)
   - `EncounterPhase` (order=700)
   - `AuditPhase` (order=800)
   - `IndicatorPhase` (order=900)
4. Replace the body of `EconomySim.update()` with a loop over the sorted pipeline.
5. Preserve exact behavior: same order, same method calls, same arguments.
6. Validation: `mvn test` and `phase47-shield.sh` must pass.

### Phase 5 — SaveLoadSubsystem Registry (Plan B P4)

1. Create `SaveLoadSubsystem.java` interface extending `Saveable` with `int chunkTag()`.
2. Make the 17 saveable classes implement `SaveLoadSubsystem` and return their `TAG_*` constants.
3. Add `EconomySim.subsystems()` returning an ordered `List<SaveLoadSubsystem>`.
4. Refactor `saveChunked()` to iterate over `subsystems()` and call `saveSubsystemChunk(file, s.chunkTag(), s)`.
5. Refactor `loadChunked()` to build a `Map<Integer, SaveLoadSubsystem>` and dispatch via the map.
6. Keep inline special cases (`TAG_CORVEE`, `TAG_STATE_WAGES`, `TAG_CORE_SCALARS`, `TAG_ECON_CONFIG`) unchanged.
7. Validation:
   - `mvn compile -q` zero warnings.
   - `mvn test` all pass.
   - `phase47-shield.sh` PASS.
   - `grep` confirms no save/load byte layout changes.

## Validation Commands (Run After Every Phase)

```bash
# Compile — zero warnings
mvn compile -q 2>&1 | tail -5

# Full test suite
mvn test 2>&1 | tail -10

# Phase 4.7 shield
bash tools/phase47-shield.sh 2>&1 | tail -20

# Final clean build
mvn clean test 2>&1 | tail -10
```

## Success Criteria

- `mvn clean test` → BUILD SUCCESS, all tests pass.
- `bash tools/phase47-shield.sh` → PASS.
- `git status --short` only shows intended changes.
- No `catch (Throwable)` in `src/vannon/syx/economy/core/`.
- No `TODO`/`FIXME` in new or modified files.
- No compiler warnings treated as expected.

## Failure Protocol

If any validation fails:
1. Stop immediately.
2. Identify the exact file and line causing the failure.
3. Fix the root cause — do not paper over it.
4. Re-run the validation.
5. Only proceed when green.

Do not ask the user for permission to skip validation. Do not proceed with a known failure.
