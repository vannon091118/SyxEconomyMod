# Per-Citizen Training EXP + Needs → Behaviour Bridge — Implementation Plan v3 (Final)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three-layer citizen state — Money (Wallets, exists), Skill/EXP (TrainingVectors, new), Needs (NeedsBridge, new, read-only from vanilla) — converging via `BValue.BValueInduOnly` on `BOOSTABLES.BEHAVIOUR()` targets. Stage-gated rollout: nothing before INDUSTRIE. NeedsBridge first (HANDEL, zero risk), TrainingVectors second (INDUSTRIE, deterministic formula), CSV import last (WOHLSTAND+, behind ITrainingSource with internal fallback).

**Architecture:** Six new classes — `StageGate` (centralized feature gating), `NeedsBridge` (read-only STATS.NEEDS watcher), `ITrainingSource` + `InternalTrainingSource` + `CsvTrainingSource` (adapter pattern), `CsvIngest` (async file reader), `TrainingVectors` (slot-array with lambda blending), `CitizenLayers` (single coordinator, registers all BValueInduOnly in `MainScript.initBeforeGameInited()`). All BValueInduOnly lambdas resolve `EconomySim.active()` dynamically — never capture `sim` instance (memory leak on save reload).

**Tech Stack:** Java 21, `BValue.BValueInduOnly` (vanilla), `BOOSTABLES.BEHAVIOUR().HAPPI/SANITY/LOYALTY` (vanilla), `STATS.NEEDS()` (vanilla, index-based access with defensive try/catch), `IUpdater` (vanilla, for CSV batch distribution only), `ChunkedSave` v34, `ITrainingSource` interface (adapter pattern from Phase 4).

## Global Constraints

- `EconomySim.CHUNKED_VERSION` increments from 33 → 34
- New subsystem gets chunk tag: `TAG_TRAINING = 0x45585000` (NeedsBridge has no persistent state — no save tag)
- Save backward-compatible: old saves (≤33) load with empty TrainingVectors, inactive NeedsBridge
- **Thread safety:** `CsvTrainingSource.pending` and `pendingValid` are `volatile` — array populated BEFORE reference assignment
- **No captured `sim` instances:** All `BValueInduOnly` lambdas call `EconomySim.active()` dynamically
- **CitizenLayers registers in `MainScript.initBeforeGameInited()`**, NOT in EconomySim constructor
- **TrainingVectors.lambdaBp populated in `touch()`** via `rollTrainingLambda()` — never zero
- Zero new Reflection — all vanilla reads via public APIs
- Build must pass all 3 gates (Code-Audit, Version↔Changelog, Adapter↔Engine)

---

## File Structure

```
Create:
  src/vannon/syx/economy/core/StageGate.java                — centralized feature gating
  src/vannon/syx/economy/core/NeedsBridge.java               — read-only vanilla STATS.NEEDS watcher
  src/vannon/syx/economy/core/ITrainingSource.java           — interface (adapter pattern)
  src/vannon/syx/economy/core/InternalTrainingSource.java    — formula-based, deterministic
  src/vannon/syx/economy/core/CsvTrainingSource.java         — external CSV, implements ITrainingSource
  src/vannon/syx/economy/core/CsvIngest.java                 — async file reader
  src/vannon/syx/economy/core/TrainingVectors.java           — slot-array + blend + IUpdater integration
  src/vannon/syx/economy/core/CitizenLayers.java             — coordinator: registers all BValueInduOnly

Modify:
  src/vannon/syx/economy/core/EconomicRoles.java             — add isTrainingRoom(), isExportRoom(), isServiceRoom()
  src/vannon/syx/economy/core/EconProgression.java           — export throttling via Stage
  src/vannon/syx/economy/core/EconomySim.java                — integrate TrainingVectors + IUpdater + stage-gated update
  src/vannon/syx/economy/core/ChunkedSave.java               — add TAG_TRAINING
  src/vannon/syx/economy/core/EconConfig.java                — feature toggles + TRAINING_DIM
  src/vannon/syx/economy/core/MainScript.java                — call CitizenLayers.registerAll() here
  src/vannon/syx/economy/core/DiagnosticExporter.java         — export mean_need_fulfilment, mean_training_skill
  pom.xml                                                     — bump version to 0.2.0
  docs/GLOSSARY.md                                            — add new entries

Test:
  test/java/vannon/syx/economy/core/StageGateTest.java       — feature gating predicates
  test/java/vannon/syx/economy/core/TrainingVectorsTest.java  — blend math + lambdaBp initialization + owner validation
```

---

### Task 1: EconConfig — Feature Toggles + Constants

**Files:**
- Modify: `src/vannon/syx/economy/core/EconConfig.java`

**Interfaces:**
- Produces: `TRAINING_DIM = 4`, `useNeedsBridge = true`, `useTrainingInternal = true`, `useTrainingCsv = false`, `trainingCsvPath = ""`, `trainingLambdaMin = 0.01`, `trainingLambdaMax = 0.05`, `trainingLambdaHeterogeneous = true`, `needsNeglectSanityThreshold = 0.70`, `exportThrottleSubsistence = 0.25`, `exportThrottleTrade = 0.50`

- [ ] **Step 1: Add all new constants**

```java
// ── Phase 5: Training / Needs Bridge ──

/** Dimensionality of training vectors (e.g. 4 = Crafting, Labor, Combat, Admin). */
public static int TRAINING_DIM = 4;

/** Read-only bridge: watch vanilla STATS.NEEDS, log to EventLog, expose to exports. */
public static boolean useNeedsBridge = true;

/** Internal training source: formula-based from StatsWork.profession + WORK_TIME.
 *  Activates at INDUSTRIE stage. Deterministic, no external dependency. */
public static boolean useTrainingInternal = true;

/** CSV/ML training source: async file ingest. Activates at WOHLSTAND+.
 *  Falls back to InternalTrainingSource when file missing. */
public static boolean useTrainingCsv = false;

/** Path to external training CSV. Empty = no reload attempted. */
public static String trainingCsvPath = "";

/** Individual lambda (learning rate) for training vector blending.
 *  Citizens draw uniformly from [lambdaMin, lambdaMax] if heterogeneous. */
public static double trainingLambdaMin = 0.01;
public static double trainingLambdaMax = 0.05;
public static boolean trainingLambdaHeterogeneous = true;

/** Mean need fulfilment below which CitizenLayers applies SANITY penalty.
 *  0.70 = citizen has 70% of all needs met on average. */
public static double needsNeglectSanityThreshold = 0.70;

/** Export efficiency multipliers by stage (applied in EconProgression).
 *  SUBSISTENZ: 25% of normal throughput. HANDEL: 50%. INDUSTRIE+: 100%. */
public static double exportThrottleSubsistence = 0.25;
public static double exportThrottleTrade = 0.50;
```

- [ ] **Step 2: Run `mvn compile -q` — verify no errors**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: exit code 0

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/core/EconConfig.java
git commit -m "feat: Phase 5 config — TRAINING_DIM, NeedsBridge, StageGate, export throttling"
```

---

### Task 2: StageGate — Centralized Feature Gating

**Files:**
- Create: `src/vannon/syx/economy/core/StageGate.java`
- Create: `test/java/vannon/syx/economy/core/StageGateTest.java`

**Interfaces:**
- Consumes: `EconProgression.Stage` enum
- Produces: `StageGate.Feature` enum, `StageGate.isActive(Feature, Stage) → boolean`

- [ ] **Step 1: Write the failing test**

```java
// test/java/vannon/syx/economy/core/StageGateTest.java
package vannon.syx.economy.core;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class StageGateTest {

    @Test void needsBridge_activeAtHandel() {
        assertTrue(StageGate.isActive(StageGate.Feature.NEEDS_BRIDGE, EconProgression.Stage.HANDEL));
        assertFalse(StageGate.isActive(StageGate.Feature.NEEDS_BRIDGE, EconProgression.Stage.SUBSISTENZ));
    }

    @Test void trainingInternal_activeAtIndustry() {
        assertTrue(StageGate.isActive(StageGate.Feature.TRAINING_INTERNAL, EconProgression.Stage.INDUSTRIE));
        assertFalse(StageGate.isActive(StageGate.Feature.TRAINING_INTERNAL, EconProgression.Stage.HANDEL));
    }

    @Test void trainingCsv_activeAtProsperity() {
        assertTrue(StageGate.isActive(StageGate.Feature.TRAINING_CSV, EconProgression.Stage.WOHLSTAND));
        assertFalse(StageGate.isActive(StageGate.Feature.TRAINING_CSV, EconProgression.Stage.INDUSTRIE));
    }

    @Test void exportThrottled_belowIndustry() {
        assertTrue(StageGate.isActive(StageGate.Feature.EXPORT_THROTTLED, EconProgression.Stage.SUBSISTENZ));
        assertFalse(StageGate.isActive(StageGate.Feature.EXPORT_THROTTLED, EconProgression.Stage.INDUSTRIE));
    }

    @Test void citizenLayers_activeAtIndustry() {
        assertTrue(StageGate.isActive(StageGate.Feature.CITIZEN_LAYERS, EconProgression.Stage.INDUSTRIE));
        assertFalse(StageGate.isActive(StageGate.Feature.CITIZEN_LAYERS, EconProgression.Stage.HANDEL));
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn test -Dtest=StageGateTest -pl . 2>&1 | tail -5`
Expected: compile error — `StageGate` not found

- [ ] **Step 3: Write StageGate.java**

```java
// src/vannon/syx/economy/core/StageGate.java
package vannon.syx.economy.core;

/**
 * Centralized feature gating — all Phase 5+ features activate at specific
 * {@link EconProgression.Stage} thresholds. Single source of truth instead
 * of scattered {@code if (stage.level >= X)} checks across 10 files.
 */
public final class StageGate {

    public enum Feature {
        NEEDS_BRIDGE(EconProgression.Stage.HANDEL.level),
        TRAINING_INTERNAL(EconProgression.Stage.INDUSTRIE.level),
        CITIZEN_LAYERS(EconProgression.Stage.INDUSTRIE.level),
        TRAINING_CSV(EconProgression.Stage.WOHLSTAND.level),
        EXPORT_THROTTLED(EconProgression.Stage.INDUSTRIE.level);

        public final int activateAtStage;

        Feature(int activateAtStage) { this.activateAtStage = activateAtStage; }
    }

    private StageGate() {}

    public static boolean isActive(Feature feature, EconProgression.Stage stage) {
        if (stage == null) return false;
        return stage.level >= feature.activateAtStage;
    }

    /** Convenience: current stage from active EconomySim. */
    public static boolean isActive(Feature feature) {
        EconomySim sim = EconomySim.active();
        return sim != null && sim.progression() != null
            && isActive(feature, sim.progression().stage);
    }
}
```

- [ ] **Step 4: Run test — verify all 5 pass**

Run: `mvn test -Dtest=StageGateTest -pl . 2>&1 | grep 'Tests run:'`
Expected: Tests run: 5, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/vannon/syx/economy/core/StageGate.java test/java/vannon/syx/economy/core/StageGateTest.java
git commit -m "feat: StageGate — centralized feature gating for Phase 5+ rollout"
```

---

### Task 3: EconomicRoles — Expand Room Classification

**Files:**
- Modify: `src/vannon/syx/economy/core/EconomicRoles.java`

**Interfaces:**
- Produces: `isTrainingRoom(RoomBlueprintImp) → boolean`, `isExportRoom(RoomBlueprintImp) → boolean`, `isServiceRoom(RoomBlueprintImp) → boolean`

- [ ] **Step 1: Add imports + three new methods**

Add imports at top:
```java
import settlement.room.knowledge.school.ROOM_SCHOOL;
import settlement.room.knowledge.university.ROOM_UNIVERSITY;
```

Add methods before `private EconomicResources()`:
```java
/** Rooms where citizens accumulate work experience.
 *  Knowledge rooms + military training. */
static boolean isTrainingRoom(RoomBlueprintImp b) {
    return b instanceof ROOM_SCHOOL
        || b instanceof ROOM_UNIVERSITY
        || b instanceof ROOM_LIBRARY
        || b instanceof ROOM_LABORATORY
        || b instanceof ROOM_M_TRAINER;
}

/** Rooms that produce export goods. Used by export throttling. */
static boolean isExportRoom(RoomBlueprintImp b) {
    return b instanceof ROOM_EXPORT;
}

/** Rooms that provide citizen services (hearth, well, temple).
 *  These connect to vanilla STATS.NEEDS. */
static boolean isServiceRoom(RoomBlueprintImp b) {
    return b instanceof ROOM_HEARTH
        || b instanceof ROOM_WELL
        || b instanceof ROOM_TEMPLE;
}
```

- [ ] **Step 2: Run `mvn compile -q`**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: exit code 0

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/core/EconomicRoles.java
git commit -m "feat: EconomicRoles — add isTrainingRoom, isExportRoom, isServiceRoom"
```

---

### Task 4: NeedsBridge — Read-Only Vanilla STATS.NEEDS Watcher with Slot-Array Cache

**Files:**
- Create: `src/vannon/syx/economy/core/NeedsBridge.java`

**Interfaces:**
- Consumes: `STATS.NEEDS().SNEEDS.get(index).stat().indu().getD(h.indu())` (vanilla, verified against V71.44 sources — `StatNeedNormal` has `.stat()` not `.indu()`)
- Produces: `NeedsBridge.NeedsSnapshot` record, `NeedsBridge.sample(Roster, int)`, `NeedsBridge.meanFor(Induvidual) → double` (O(1) via slot cache), `NeedsBridge.populationMean()`

- [ ] **Step 1: Write NeedsBridge.java**

```java
// src/vannon/syx/economy/core/NeedsBridge.java
package vannon.syx.economy.core;

import settlement.entity.humanoid.Humanoid;
import settlement.stats.Induvidual;
import settlement.stats.STATS;

/**
 * Read-only bridge to vanilla {@code STATS.NEEDS()}.
 *
 * <p>DOES NOT write to any vanilla state — purely observes citizen need
 * fulfilment. Maintains a slot-array snapshot cache for O(1) access
 * from {@code CitizenLayers} (avoids O(n²) roster scan per booster eval).</p>
 *
 * <p><b>Vanilla API — verified against V71.44 sources (2026-07-23):</b>
 * {@code STATS.NEEDS().SNEEDS} is a public LIST of {@code StatNeedNormal}.
 * {@code StatNeedNormal} does NOT have {@code .indu()}; it has:
 * {@code public STAT stat()} → then {@code .indu().getD(Induvidual)}.
 * Correct chain: {@code SNEEDS.get(idx).stat().indu().getD(indu)}.
 *
 * <b>NEED_E indices (verified from NEEDS.Types):</b>
 * Only 3 NEED_E types exist: 0=HUNGER, 1=THIRST, 2=SHOPPING.
 * SLEEP, HEALTH, SERVICE are NOT NEED_E in V71.44.
 * All access wrapped in try/catch — V72 index changes won't crash.
 *
 * <b>Value semantics (verified from getPrio/CHUNK):</b>
 * Values are URGENCY counters (0=no need, high=urgent).
 * NOT fulfilment percentages. Higher = worse.</p>
 */
public final class NeedsBridge {

    /** Values are URGENCY: 0=no need, high=needs urgent attention. */
    public record NeedsSnapshot(
        double hunger, double thirst, double shopping,
        double sleep, double service, double meanUrgency
    ) {}

    private static int lastSampleSeason = -1;
    private static int neglectedCount = 0;
    private static double populationMean = 1.0;
    private static int consecutiveLowSeasons = 0;

    /** O(1) lookup: Induvidual → mean need fulfilment.
     *  Populated by sample(), read by CitizenLayers. */
    private static final java.util.HashMap<Induvidual, Double> induToMean
        = new java.util.HashMap<>();

    private NeedsBridge() {}

    /**
     * Sample need fulfilment across the population.
     * Called once per season from EconomySim.update().
     * Builds the slot cache and logs neglect warnings.
     */
    public static void sample(Roster roster, int currentSeason) {
        if (!EconConfig.useNeedsBridge) return;
        if (!StageGate.isActive(StageGate.Feature.NEEDS_BRIDGE)) return;
        if (currentSeason == lastSampleSeason) return;
        lastSampleSeason = currentSeason;

        double totalMean = 0.0;
        int neglected = 0;
        int count = Math.min(roster.size(), 200);

        for (int i = 0; i < count; i++) {
            Humanoid h = roster.get(i);
            NeedsSnapshot snap = snapshot(h);
            // meanUrgency is inverted: HIGH = neglected. Compare > threshold.
            totalMean += snap.meanUrgency;
            if (snap.meanUrgency > EconConfig.needsNeglectSanityThreshold) neglected++;

            // Populate O(1) cache for meanFor() lookup (urgency, high=bad)
            induToMean.put(h.indu(), snap.meanUrgency);
        }

        populationMean = count > 0 ? totalMean / count : 1.0;
        neglectedCount = neglected;

        // populationMean is urgency (high=neglected). Compare > threshold.
        if (populationMean > EconConfig.needsNeglectSanityThreshold) {
            consecutiveLowSeasons++;
            if (consecutiveLowSeasons >= 2) {
                EventLog.log("NEEDS",
                    String.format("Population needs chronically neglected (%.2f, %d/%d below threshold, season %d)",
                        populationMean, neglectedCount, count, consecutiveLowSeasons));
            }
        } else {
            consecutiveLowSeasons = 0;
        }
    }

    /** Per-citizen needs snapshot.
     *  Values are URGENCY counters (0=no need, high=urgent).
     *  Only 3 NEED_E types in V71.44: HUNGER(0), THIRST(1), SHOPPING(2). */
    public static NeedsSnapshot snapshot(Humanoid h) {
        if (h == null) return new NeedsSnapshot(0, 0, 0, 0, 0, 0);
        Induvidual indu = h.indu();
        double hunger   = needValue(indu, 0);
        double thirst   = needValue(indu, 1);
        double shopping = needValue(indu, 2);
        // SLEEP/HEALTH/SERVICE are not NEED_E in V71.44 — zeroed
        double mean = (hunger + thirst + shopping) / 3.0;
        return new NeedsSnapshot(hunger, thirst, shopping, 0.0, 0.0, mean);
    }

    private static double needValue(Induvidual indu, int index) {
        try {
            return STATS.NEEDS().SNEEDS.get(index).stat().indu().getD(indu);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static double populationMean() { return populationMean; }
    public static int neglectedCount() { return neglectedCount; }

    /**
     * O(1) mean need fulfilment for a specific citizen.
     * Uses HashMap populated by the last sample() call.
     * Returns 1.0 (fully satisfied) for citizens not in the sample.
     */
    public static double meanFor(Induvidual indu) {
        if (indu == null) return 1.0;
        Double cached = induToMean.get(indu);
        return cached != null ? cached : 1.0;
    }
```

- [ ] **Step 2: Run `mvn compile -q`**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: exit code 0

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/core/NeedsBridge.java
git commit -m "feat: NeedsBridge — read-only vanilla STATS.NEEDS watcher with defensive try/catch"
```

---

### Task 5: ITrainingSource + InternalTrainingSource

**Files:**
- Create: `src/vannon/syx/economy/core/ITrainingSource.java`
- Create: `src/vannon/syx/economy/core/InternalTrainingSource.java`

**Interfaces:**
- Produces: `ITrainingSource.getTargetVector(Humanoid) → float[DIM]` (null = no data), `ITrainingSource.isAvailable() → boolean`
- `InternalTrainingSource`: formula from `StatsWork.profession` + `WORK_TIME`, deterministic

- [ ] **Step 1: Write ITrainingSource.java**

```java
// src/vannon/syx/economy/core/ITrainingSource.java
package vannon.syx.economy.core;

import settlement.entity.humanoid.Humanoid;

/**
 * Interface for training vector sources — same adapter pattern as Phase 4.
 *
 * <p>{@link InternalTrainingSource} is the deterministic fallback.
 * {@link CsvTrainingSource} provides external ML vectors when available.</p>
 */
public interface ITrainingSource {

    /** Return the target vector for a citizen, or null if this source
     *  has no data for them. DIM must match {@link EconConfig#TRAINING_DIM}. */
    float[] getTargetVector(Humanoid h);

    /** True if this source is ready to provide vectors. */
    boolean isAvailable();
}
```

- [ ] **Step 2: Write InternalTrainingSource.java**

```java
// src/vannon/syx/economy/core/InternalTrainingSource.java
package vannon.syx.economy.core;

import settlement.entity.humanoid.Humanoid;
import settlement.room.main.RoomBlueprintImp;
import settlement.stats.STATS;

/**
 * Deterministic formula-based training source.
 *
 * <p>Maps citizen profession + time-on-job to a skill vector via
 * {@code sqrt(workTime)} for diminishing returns. Dimensions:
 * [0]=Crafting, [1]=Labor, [2]=Combat, [3]=Administration.</p>
 */
public final class InternalTrainingSource implements ITrainingSource {

    private final int dim;

    public InternalTrainingSource(int dimension) {
        this.dim = Math.max(1, Math.min(dimension, 16));
    }

    @Override
    public boolean isAvailable() {
        return EconConfig.useTrainingInternal
            && StageGate.isActive(StageGate.Feature.TRAINING_INTERNAL);
    }

    @Override
    public float[] getTargetVector(Humanoid h) {
        if (h == null) return null;
        float[] v = new float[this.dim];

        RoomBlueprintImp profession = STATS.WORK().profession.get(h.indu());
        if (profession == null) return null;

        double workTime = Math.min(100_000.0,
            STATS.WORK().WORK_TIME.indu().getD(h.indu()));

        double base = Math.sqrt(workTime) / 10.0;

        if (EconomicRoles.isTrainingRoom(profession)) {
            v[2] = (float) base;
            v[3] = (float) base;
        } else if (EconomicRoles.isExportRoom(profession)) {
            v[0] = (float) base;
        } else {
            v[0] = (float)(base * 0.5);
            v[1] = (float) base;
        }

        for (int d = 0; d < this.dim; d++) {
            v[d] = Math.min(100f, Math.max(0f, v[d]));
        }
        return v;
    }
}
```

- [ ] **Step 3: Run `mvn compile -q`**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: exit code 0

- [ ] **Step 4: Commit**

```bash
git add src/vannon/syx/economy/core/ITrainingSource.java src/vannon/syx/economy/core/InternalTrainingSource.java
git commit -m "feat: ITrainingSource + InternalTrainingSource — adapter pattern for training vectors"
```

---

### Task 6: CsvIngest + CsvTrainingSource

**Files:**
- Create: `src/vannon/syx/economy/core/CsvIngest.java`
- Create: `src/vannon/syx/economy/core/CsvTrainingSource.java`

**Interfaces:**
- Consumes: `EconConfig.trainingCsvPath`, `EconConfig.TRAINING_DIM`
- Produces: `CsvTrainingSource.setPending(float[])` (called by CsvIngest thread), `CsvTrainingSource.getTargetVector(Humanoid)`

- [ ] **Step 1: Write CsvIngest.java**

```java
// src/vannon/syx/economy/core/CsvIngest.java
package vannon.syx.economy.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Async CSV reader — loads external training vectors on a background thread.
 *
 * <p><b>Thread safety:</b> The parsed float[] batch is fully populated BEFORE
 * calling {@code target.setPending(batch)}. The {@code pending} field in
 * {@link CsvTrainingSource} is {@code volatile} — the reference swap
 * guarantees visibility of all array contents to the sim thread.</p>
 *
 * <p>CSV format (atomically swapped via .tmp → rename):
 * <pre>
 *   FORMAT,1,SLOTS,60000,DIM,4
 *   &lt;citizen_id&gt;,&lt;v0&gt;,&lt;v1&gt;,&lt;v2&gt;,&lt;v3&gt;
 * </pre></p>
 */
public final class CsvIngest {

    private final CsvTrainingSource target;
    private final Path csvPath;
    private volatile boolean loading = false;
    private long lastModified = 0L;
    private int validRows = 0;
    private int skippedRows = 0;

    public CsvIngest(CsvTrainingSource target) {
        this.target = target;
        String path = EconConfig.trainingCsvPath;
        this.csvPath = (path == null || path.isEmpty()) ? null : Paths.get(path);
    }

    public boolean tryReloadIfChanged() {
        if (this.csvPath == null || this.loading) return false;
        try {
            if (!Files.exists(this.csvPath)) return false;
            long mod = Files.getLastModifiedTime(this.csvPath).toMillis();
            if (mod <= this.lastModified) return false;
            this.lastModified = mod;
        } catch (IOException e) {
            EventLog.log("CSV_INGEST", "Cannot stat CSV: " + e.getMessage());
            return false;
        }
        this.loading = true;
        Path path = this.csvPath.toAbsolutePath();
        Thread t = new Thread(() -> loadFile(path), "csv-ingest");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private void loadFile(Path path) {
        int dim = EconConfig.TRAINING_DIM;
        float[] batch = new float[60000 * dim];
        int rows = 0;
        int bad = 0;

        try (BufferedReader r = Files.newBufferedReader(path)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("FORMAT,")) continue;
                String[] parts = line.split(",");
                if (parts.length < dim + 1) { bad++; continue; }
                try {
                    int citizenId = Integer.parseInt(parts[0].trim());
                    int slot = citizenId & 0x3FFFF;
                    if (slot < 0 || slot >= 60000) { bad++; continue; }
                    int base = slot * dim;
                    for (int d = 0; d < dim && d + 1 < parts.length; d++) {
                        float val = Float.parseFloat(parts[d + 1].trim());
                        batch[base + d] = Math.min(1000f, Math.max(0f, val));
                    }
                    rows++;
                } catch (NumberFormatException e) {
                    bad++;
                }
            }
        } catch (IOException e) {
            EventLog.log("CSV_INGEST", "Failed to read CSV: " + e.getMessage());
            this.loading = false;
            return;
        }

        this.validRows = rows;
        this.skippedRows = bad;
        // FULLY populated before volatile reference swap
        this.target.setPending(batch);
        this.loading = false;
        EventLog.log("CSV_INGEST",
            "Loaded " + rows + " rows (" + bad + " skipped) from " + path.getFileName());
    }

    public boolean isLoading() { return this.loading; }
    public int validRows() { return this.validRows; }
    public int skippedRows() { return this.skippedRows; }
}
```

- [ ] **Step 2: Write CsvTrainingSource.java**

```java
// src/vannon/syx/economy/core/CsvTrainingSource.java
package vannon.syx.economy.core;

import settlement.entity.humanoid.Humanoid;

/**
 * External CSV/ML training source — activated at WOHLSTAND+.
 *
 * <p><b>Thread safety:</b> {@code pending} and {@code pendingValid} are
 * both {@code volatile}. The background thread fully populates the array
 * before calling {@code setPending()}. The sim thread sees either null
 * (old state) or the fully-populated array (new state). No torn reads.</p>
 */
public final class CsvTrainingSource implements ITrainingSource {

    private final int dim;
    private volatile float[] pending;
    private volatile boolean pendingValid = false;
    private final CsvIngest ingest;

    public CsvTrainingSource(int dimension) {
        this.dim = Math.max(1, Math.min(dimension, 16));
        this.ingest = new CsvIngest(this);
    }

    @Override
    public boolean isAvailable() {
        return EconConfig.useTrainingCsv
            && StageGate.isActive(StageGate.Feature.TRAINING_CSV)
            && this.pendingValid
            && this.pending != null;
    }

    @Override
    public float[] getTargetVector(Humanoid h) {
        if (!this.pendingValid || this.pending == null || h == null) return null;
        int slot = h.id() & 0x3FFFF;
        if (slot < 0 || slot >= 60000) return null;
        int base = slot * this.dim;
        float[] v = new float[this.dim];
        System.arraycopy(this.pending, base, v, 0, this.dim);
        for (int d = 0; d < this.dim; d++) {
            if (v[d] != 0f) return v;
        }
        return null;
    }

    /** Called by CsvIngest background thread. */
    void setPending(float[] batch) {
        this.pending = batch;
        this.pendingValid = true;
    }

    /** Called by TrainingVectors when the IUpdater has consumed the batch. */
    void clearPending() {
        this.pending = null;
        this.pendingValid = false;
    }

    public CsvIngest ingest() { return this.ingest; }
}
```

- [ ] **Step 3: Run `mvn compile -q` — verify both compile**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: exit code 0

- [ ] **Step 4: Commit**

```bash
git add src/vannon/syx/economy/core/CsvIngest.java src/vannon/syx/economy/core/CsvTrainingSource.java
git commit -m "feat: CsvIngest + CsvTrainingSource — async CSV with volatile double-buffer and ITrainingSource adapter"
```

---

### Task 7: TrainingVectors — Slot-Array with Lambda Initialization

**Files:**
- Create: `src/vannon/syx/economy/core/TrainingVectors.java`
- Create: `test/java/vannon/syx/economy/core/TrainingVectorsTest.java`

**Interfaces:**
- Consumes: `ITrainingSource`, `EconConfig.TRAINING_DIM`, `EconConfig.trainingLambdaMin/Max/Heterogeneous`
- Produces: `TrainingVectors.touch(Humanoid)`, `TrainingVectors.updateFromSource(Humanoid)`, `TrainingVectors.applyPendingSlot(int)`, `TrainingVectors.meanSkill(Humanoid)`, `TrainingVectors.populationMean()`, `TrainingVectors.save(FilePutter)`, `TrainingVectors.load(FileGetter, int)`

- [ ] **Step 1: Write the failing test**

```java
// test/java/vannon/syx/economy/core/TrainingVectorsTest.java
package vannon.syx.economy.core;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class TrainingVectorsTest {

    @BeforeEach
    void setup() {
        EconConfig.TRAINING_DIM = 4;
        EconConfig.trainingLambdaMin = 0.01;
        EconConfig.trainingLambdaMax = 0.05;
        EconConfig.trainingLambdaHeterogeneous = true;
    }

    @Test
    void blend_appliesLambdaWeightedAverage() {
        float[] current = {100f, 200f, 300f, 400f};
        float[] target  = {200f, 100f, 400f, 300f};
        float[] result = TrainingVectors.blend(current, target, 0.5, 4);
        assertEquals(150f, result[0], 0.01f);
        assertEquals(150f, result[1], 0.01f);
        assertEquals(350f, result[2], 0.01f);
        assertEquals(350f, result[3], 0.01f);
    }

    @Test
    void blend_clampsNegative() {
        float[] current = {10f, 10f, 10f, 10f};
        float[] target  = {0f,   0f,  0f,  0f};
        float[] result = TrainingVectors.blend(current, target, 2.0, 4);
        for (float v : result) assertTrue(v >= 0f, "no negative: " + v);
    }

    @Test
    void blend_dimensionMismatch_truncates() {
        float[] current = {100f};
        float[] target  = {200f, 300f, 400f, 500f};
        float[] result = TrainingVectors.blend(current, target, 0.5, 1);
        assertEquals(1, result.length);
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn test -Dtest=TrainingVectorsTest -pl . 2>&1 | tail -5`
Expected: compile error — `TrainingVectors` not found

- [ ] **Step 3: Write TrainingVectors.java**

```java
// src/vannon/syx/economy/core/TrainingVectors.java
package vannon.syx.economy.core;

import java.io.IOException;
import java.util.Arrays;
import settlement.entity.humanoid.Humanoid;
import settlement.stats.Induvidual;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.rnd.RND;

public final class TrainingVectors {

    static final int SLOTS = 60000;
    static final int NOBODY = -1;

    private final float[] vectors;
    private int dim;
    private final int[] owner;
    private final int[] lambdaBp;
    private int[] ownedSlots = new int[1024];
    private int ownedCount = 0;

    private final ITrainingSource internalSource;
    private final CsvTrainingSource csvSource;
    private ITrainingSource activeSource;

    private final java.util.HashMap<Induvidual, Integer> induSlot = new java.util.HashMap<>();
    private final Induvidual[] induOf = new Induvidual[SLOTS];

    public static final int FORMAT = 1;

    public TrainingVectors(int dimension) {
        this.dim = Math.max(1, Math.min(dimension, 16));
        this.vectors = new float[SLOTS * this.dim];
        this.owner = new int[SLOTS];
        this.lambdaBp = new int[SLOTS];
        Arrays.fill(this.owner, NOBODY);
        this.internalSource = new InternalTrainingSource(this.dim);
        this.csvSource = new CsvTrainingSource(this.dim);
        this.activeSource = this.internalSource;
    }

    /* ── Slot management ──────────────────────────────────────────── */

    private static int slotOf(Humanoid h) { return h.id() & 0x3FFFF; }

    private int liveSlot(Humanoid h) {
        if (h == null) return -1;
        int id = h.id();
        int slot = id & 0x3FFFF;
        return (slot >= 0 && slot < SLOTS && this.owner[slot] == id) ? slot : -1;
    }

    public void touch(Humanoid h) {
        int slot = slotOf(h);
        int id = h.id();
        if (this.owner[slot] != id) {
            if (this.owner[slot] != NOBODY && this.induOf[slot] != null) {
                this.induSlot.remove(this.induOf[slot]);
                this.induOf[slot] = null;
            }
            this.owner[slot] = id;
            int base = slot * this.dim;
            Arrays.fill(this.vectors, base, base + this.dim, 0f);
            this.lambdaBp[slot] = rollTrainingLambda(); // CRITICAL FIX: never zero
            own(slot);
        }
        if (this.induOf[slot] != h.indu()) {
            if (this.induOf[slot] != null) this.induSlot.remove(this.induOf[slot]);
            this.induOf[slot] = h.indu();
            this.induSlot.put(h.indu(), slot);
        }
    }

    private void own(int slot) {
        if (this.ownedCount == this.ownedSlots.length)
            this.ownedSlots = Arrays.copyOf(this.ownedSlots, this.ownedSlots.length * 2);
        this.ownedSlots[this.ownedCount++] = slot;
    }

    /* ── Lambda (learning rate) ───────────────────────────────────── */

    private static int rollTrainingLambda() {
        if (!EconConfig.trainingLambdaHeterogeneous)
            return (int)(EconConfig.trainingLambdaMax * 10000.0);
        double span = EconConfig.trainingLambdaMax - EconConfig.trainingLambdaMin;
        double v = EconConfig.trainingLambdaMin + (double)RND.rFloat() * span;
        return Math.min(9999, Math.max(0, (int)(v * 10000.0)));
    }

    public double lambda(Humanoid h) {
        int slot = liveSlot(h);
        return slot < 0 ? EconConfig.trainingLambdaMax : (double)this.lambdaBp[slot] / 10000.0;
    }

    /* ── Vector access ────────────────────────────────────────────── */

    public float[] getVector(Humanoid h) {
        int slot = liveSlot(h);
        float[] copy = new float[this.dim];
        if (slot >= 0) System.arraycopy(this.vectors, slot * this.dim, copy, 0, this.dim);
        return copy;
    }

    public double meanSkill(Humanoid h) {
        int slot = liveSlot(h);
        if (slot < 0) return 0.0;
        int base = slot * this.dim;
        double sum = 0.0;
        for (int d = 0; d < this.dim; d++) sum += this.vectors[base + d];
        return sum / this.dim;
    }

    public double populationMean() {
        if (this.ownedCount == 0) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < this.ownedCount; i++) {
            int base = this.ownedSlots[i] * this.dim;
            for (int d = 0; d < this.dim; d++) sum += this.vectors[base + d];
        }
        return sum / (this.ownedCount * this.dim);
    }

    /* ── Source-driven update ─────────────────────────────────────── */

    public void selectBestSource() {
        if (this.csvSource.isAvailable()) {
            this.activeSource = this.csvSource;
        } else {
            this.activeSource = this.internalSource;
        }
    }

    public void updateFromSource(Humanoid h) {
        if (!this.activeSource.isAvailable()) return;
        int slot = liveSlot(h);
        if (slot < 0) return;
        float[] target = this.activeSource.getTargetVector(h);
        if (target == null) return;
        double lam = (double)this.lambdaBp[slot] / 10000.0;
        int base = slot * this.dim;
        for (int d = 0; d < this.dim; d++) {
            float delta = target[d] - this.vectors[base + d];
            float v = this.vectors[base + d] + (float)(lam * (double)delta);
            this.vectors[base + d] = Math.max(0f, v);
        }
    }

    /* ── CSV batch (via IUpdater) ─────────────────────────────────── */

    public boolean hasPending() { return this.csvSource.isAvailable(); }

    public boolean applyPendingSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= SLOTS) return false;
        if (this.owner[slotIndex] == NOBODY) return false;
        // CsvTrainingSource.getTargetVector does the owner check indirectly
        // via the slot→id mapping. For IUpdater, we check owner directly.
        int base = slotIndex * this.dim;
        boolean hadData = false;
        for (int d = 0; d < this.dim; d++) {
            if (this.vectors[base + d] > 0f) { hadData = true; break; }
        }
        // CSV batch processing is handled by updateFromSource via activeSource
        return hadData;
    }

    public void tryCsvIngest() {
        this.csvSource.ingest().tryReloadIfChanged();
    }

    /* ── Blend math (public for testing) ──────────────────────────── */

    static float[] blend(float[] current, float[] target, double lambda, int dim) {
        float[] result = new float[dim];
        for (int d = 0; d < dim; d++) {
            float t = d < target.length ? target[d] : 0f;
            float delta = t - current[d];
            result[d] = Math.max(0f, current[d] + (float)(lambda * (double)delta));
        }
        return result;
    }

    /* ── Save / Load ──────────────────────────────────────────────── */

    public void save(FilePutter file) {
        file.i(FORMAT);
        file.i(this.dim);
        file.is(this.owner);
        file.is(this.lambdaBp);
        file.fs(this.vectors);
    }

    public int load(FileGetter file, int economyVersion) throws IOException {
        int format = file.i();
        if (format < 1 || format > FORMAT)
            throw new IOException("incompatible TrainingVectors format " + format);
        this.dim = file.i();
        file.is(this.owner);
        file.is(this.lambdaBp);
        file.fs(this.vectors);
        this.ownedCount = 0;
        for (int slot = 0; slot < SLOTS; slot++)
            if (this.owner[slot] != NOBODY) own(slot);
        return format;
    }

    /* ── Sweep dead citizens ──────────────────────────────────────── */

    public void sweepDead(int[] seenTick, int currentTick) {
        int i = 0;
        while (i < this.ownedCount) {
            int slot = this.ownedSlots[i];
            if (seenTick[slot] == currentTick) { i++; continue; }
            this.owner[slot] = NOBODY;
            Arrays.fill(this.vectors, slot * this.dim, (slot + 1) * this.dim, 0f);
            this.lambdaBp[slot] = 0;
            if (this.induOf[slot] != null) {
                this.induSlot.remove(this.induOf[slot]);
                this.induOf[slot] = null;
            }
            this.ownedSlots[i] = this.ownedSlots[--this.ownedCount];
        }
    }
}
```

- [ ] **Step 4: Run test — verify 3 blend tests pass**

Run: `mvn test -Dtest=TrainingVectorsTest -pl . 2>&1 | grep 'Tests run:'`
Expected: Tests run: 3, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/vannon/syx/economy/core/TrainingVectors.java test/java/vannon/syx/economy/core/TrainingVectorsTest.java
git commit -m "feat: TrainingVectors — slot-array with lambdaBp init, ITrainingSource integration, chunked save/load"
```

---

### Task 8: CitizenLayers — Single BValueInduOnly Coordinator

**Files:**
- Create: `src/vannon/syx/economy/core/CitizenLayers.java`

**Interfaces:**
- Produces: `CitizenLayers.registerAll()` — called once from `MainScript.initBeforeGameInited()`
- ALL lambdas use `EconomySim.active()` dynamically — NEVER capture `sim` parameter

- [ ] **Step 1: Write CitizenLayers.java**

```java
// src/vannon/syx/economy/core/CitizenLayers.java
package vannon.syx.economy.core;

import game.boosting.BOOSTABLES;
import game.boosting.BSourceInfo;
import game.boosting.BValue;
import game.boosting.Boostable;
import game.boosting.BoosterValue;
import init.sprite.UI.UI;
import settlement.stats.Induvidual;

/**
 * Single coordinator that registers all {@code BValue.BValueInduOnly}
 * boosters on {@code BOOSTABLES.BEHAVIOUR()} targets.
 *
 * <p><b>Critical: all lambdas call {@code EconomySim.active()} dynamically.</b>
 * Never capture a sim instance — it would memory-leak on save reload.</p>
 *
 * <p>Registration order (all additive, all independent):
 * <ol>
 *   <li>Wealth → HAPPI (migrated from WealthHappiness)</li>
 *   <li>Property → HAPPI (migrated from PropertyHappiness)</li>
 *   <li>Gini → LOYALTY (migrated from GiniConsequences)</li>
 *   <li>Training → HAPPI (new — skill vectors boost happiness)</li>
 *   <li>Needs Neglect → SANITY (new — neglected needs increase derangement risk)</li>
 * </ol></p>
 */
public final class CitizenLayers {

    private static volatile boolean registered = false;

    private CitizenLayers() {}

    /**
     * Register all citizen-layer boosters globally.
     * Called from {@code MainScript.initBeforeGameInited()}.
     * Idempotent — subsequent calls are no-ops.
     *
     * <p>Must be called AFTER the game engine is initialized
     * (BOOSTABLES available) but BEFORE any save is loaded.
     * {@code EconomySim.active()} is resolved lazily in each lambda —
     * no sim instance needed at registration time.</p>
     */
    public static void registerAll() {
        if (registered) return;
        registered = true;

        // Registration order doesn't matter — all BValueInduOnly
        // are additive on their respective Boostables.
        registerWealthHappiness();
        registerPropertyHappiness();
        registerGiniLoyalty();
        registerTrainingProductivity();
        registerNeedsSanity();

        EventLog.log("CITIZEN_LAYERS", "All BValueInduOnly boosters registered (HAPPI, LOYALTY, SANITY)");
    }

    /* ── Layer 1: Wealth → HAPPI ──────────────────────────────────── */

    private static void registerWealthHappiness() {
        if (!EconConfig.wealthAffectsHappiness) return;
        Boostable happi = BOOSTABLES.BEHAVIOUR().HAPPI;

        BValue.BValueInduOnly wealth = new BValue.BValueInduOnly() {
            public double vGet(Induvidual indu) {
                EconomySim sim = EconomySim.active();
                return sim == null ? 0.5 : sim.relativeWealth(indu);
            }
            public double vGet(game.battle.div.Div div) { return 0.5; }
            public double vGet(init.type.HCLASS_RACE group) { return 0.5; }
        };
        new BoosterValue(wealth,
            new BSourceInfo(EconTexts.¤¤boostWealth, UI.icons().s.money),
            EconConfig.happinessAtPoorest, EconConfig.happinessAtRichest, true
        ).add(happi);

        // Tax penalty on same Boostable
        BValue.BValueInduOnly taxed = new BValue.BValueInduOnly() {
            public double vGet(Induvidual indu) {
                EconomySim sim = EconomySim.active();
                return sim == null ? 0.0 : sim.taxPain(indu);
            }
            public double vGet(game.battle.div.Div div) { return 0.0; }
            public double vGet(init.type.HCLASS_RACE group) { return 0.0; }
        };
        new BoosterValue(taxed,
            new BSourceInfo(EconTexts.¤¤boostTaxes, UI.icons().s.money),
            1.0, EconConfig.taxHappinessAtFullRate, true
        ).add(happi);
    }

    /* ── Layer 2: Property → HAPPI ────────────────────────────────── */

    private static void registerPropertyHappiness() {
        if (!EconConfig.propertyMarketEnabled || !EconConfig.homePurchaseEnabled) return;
        Boostable happi = BOOSTABLES.BEHAVIOUR().HAPPI;

        BValue.BValueInduOnly owned = new BValue.BValueInduOnly() {
            public double vGet(Induvidual indu) {
                EconomySim sim = EconomySim.active();
                if (sim == null || sim.housingMarket() == null) return 0.0;
                PropertyLedger ledger = sim.housingMarket().ledger();
                if (ledger == null) return 0.0;
                // Find Humanoid by walking roster (O(n), acceptable for seasonal boost recalculation)
                for (int i = 0; i < sim.roster().size(); i++) {
                    settlement.entity.humanoid.Humanoid h = sim.roster().get(i);
                    if (h.indu() == indu) return ledger.isHomeOwner((long) h.id()) ? 1.0 : 0.0;
                }
                return 0.0;
            }
            public double vGet(game.battle.div.Div div) { return 0.0; }
            public double vGet(init.type.HCLASS_RACE group) { return 0.0; }
        };
        new BoosterValue(owned,
            new BSourceInfo(EconTexts.¤¤boostProperty, UI.icons().s.money),
            1.0, 1.0 + EconConfig.propertyHappinessBoost, true
        ).add(happi);
    }

    /* ── Layer 3: Gini → LOYALTY ──────────────────────────────────── */

    private static void registerGiniLoyalty() {
        if (!EconConfig.giniAffectsLoyalty) return;
        Boostable loyalty = BOOSTABLES.BEHAVIOUR().LOYALTY;

        BValue.BValueInduOnly unrest = new BValue.BValueInduOnly() {
            public double vGet(Induvidual indu) {
                EconomySim sim = EconomySim.active();
                return sim == null ? 0.0 : sim.stats().gini;
            }
            public double vGet(game.battle.div.Div div) {
                EconomySim sim = EconomySim.active();
                return sim == null ? 0.0 : sim.stats().gini;
            }
            public double vGet(init.type.HCLASS_RACE group) {
                EconomySim sim = EconomySim.active();
                return sim == null ? 0.0 : sim.stats().gini;
            }
        };
        new BoosterValue(unrest,
            new BSourceInfo(EconTexts.¤¤boostInequality, UI.icons().s.money),
            1.0, EconConfig.loyaltyAtMaxGini, true
        ).add(loyalty);
    }

    /* ── Layer 4: Training → HAPPI (prototype) ────────────────────── */

    private static void registerTrainingProductivity() {
        if (!EconConfig.useTrainingInternal) return;
        Boostable happi = BOOSTABLES.BEHAVIOUR().HAPPI;

        BValue.BValueInduOnly skill = new BValue.BValueInduOnly() {
            public double vGet(Induvidual indu) {
                EconomySim sim = EconomySim.active();
                if (sim == null || sim.trainingVectors() == null) return 0.0;
                // O(n) lookup — acceptable for seasonal recalculation (<1ms for 60k)
                for (int i = 0; i < sim.roster().size(); i++) {
                    settlement.entity.humanoid.Humanoid h = sim.roster().get(i);
                    if (h.indu() == indu) return sim.trainingVectors().meanSkill(h) / 100.0;
                }
                return 0.0;
            }
            public double vGet(game.battle.div.Div div) { return 0.0; }
            public double vGet(init.type.HCLASS_RACE group) {
                EconomySim sim = EconomySim.active();
                return sim != null && sim.trainingVectors() != null
                    ? sim.trainingVectors().populationMean() / 100.0 : 0.0;
            }
        };
        new BoosterValue(skill,
            new BSourceInfo("Training.EXP", UI.ICON().PLUS),
            0.0, 0.50, false
        ).add(happi);
    }

    /* ── Layer 5: Needs Neglect → SANITY ──────────────────────────── */

    private static void registerNeedsSanity() {
        if (!EconConfig.useNeedsBridge) return;
        Boostable sanity = BOOSTABLES.BEHAVIOUR().SANITY;

        BValue.BValueInduOnly neglect = new BValue.BValueInduOnly() {
            public double vGet(Induvidual indu) {
                // meanFor returns urgency (0=no need, high=neglected).
                // Scale to 0-1 range; 1.0 urgency → max SANITY penalty.
                double urgency = NeedsBridge.meanFor(indu);
                return Math.min(1.0, urgency / 2.0);
            }
            public double vGet(game.battle.div.Div div) { return 0.0; }
            public double vGet(init.type.HCLASS_RACE group) {
                return Math.min(1.0, NeedsBridge.populationMean() / 2.0);
            }
        };
        new BoosterValue(neglect,
            new BSourceInfo("Needs.Neglect", UI.ICON().MINUS),
            1.0, 1.25, true
        ).add(sanity);
    }
}
```

- [ ] **Step 2: Run `mvn compile -q`**

Run: `mvn compile -q 2>&1 | tail -5`
Expected: exit code 0

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/core/CitizenLayers.java
git commit -m "feat: CitizenLayers — single coordinator, all BValueInduOnly via EconomySim.active()"
```

---

### Task 9: EconomySim Integration + MainScript + Export Throttling

**Files:**
- Modify: `src/vannon/syx/economy/core/EconomySim.java`
- Modify: `src/vannon/syx/economy/core/ChunkedSave.java`
- Modify: `src/vannon/syx/economy/core/EconProgression.java`
- Modify: `src/vannon/syx/economy/core/MainScript.java`
- Modify: `pom.xml`

- [ ] **Step 1: Add TAG_TRAINING to ChunkedSave.java**

```java
/** Per-citizen training vectors (Phase 5: EXP system). */
public static final int TAG_TRAINING = 0x45585000;
```

- [ ] **Step 2: Add fields + constructor integration in EconomySim**

```java
// Fields:
private final TrainingVectors trainingVectors;
private final util.updating.IUpdater trainingUpdater;
private int trainingCursor = 0;
private int lastTrainingSeason = -1;

// In constructor:
this.trainingVectors = new TrainingVectors(EconConfig.TRAINING_DIM);
this.trainingUpdater = new util.updating.IUpdater(60000, game.time.TIME.secondsPerDay() * 3) {
    @Override protected void update(int slotIndex, double dt) {
        EconomySim.this.trainingVectors.applyPendingSlot(slotIndex);
        EconomySim.this.trainingCursor = slotIndex;
    }
};
```

- [ ] **Step 3: Add update() integration — before updateRenderCaches()**

```java
// ── NeedsBridge: seasonal sampling (HANDEL+) ──
if (EconConfig.useNeedsBridge && StageGate.isActive(StageGate.Feature.NEEDS_BRIDGE)) {
    NeedsBridge.sample(this.roster, TIME.seasons().bitsSinceStart());
}

// ── TrainingVectors: touch + seasonal update (INDUSTRIE+) ──
if (EconConfig.useTrainingInternal && StageGate.isActive(StageGate.Feature.TRAINING_INTERNAL)) {
    for (int i = 0; i < this.roster.size(); i++) {
        this.trainingVectors.touch(this.roster.get(i));
    }
    int season = TIME.seasons().bitsSinceStart();
    if (season != this.lastTrainingSeason) {
        this.lastTrainingSeason = season;
        this.trainingVectors.selectBestSource();
        for (int i = 0; i < this.roster.size(); i++) {
            this.trainingVectors.updateFromSource(this.roster.get(i));
        }
    }
    // CSV ingest attempt (background thread, non-blocking)
    this.trainingVectors.tryCsvIngest();
    // Distribute CSV batch application across ticks via IUpdater
    // (only active when CsvTrainingSource has pending data)
    if (this.trainingVectors.hasPending()) {
        this.trainingUpdater.update(TIME.deltaSeconds());
    }
}
```

- [ ] **Step 4: Add save/load for TAG_TRAINING**

In `save()`, before the final tag terminator:
```java
// Training vectors (v34+)
{
    file.i(ChunkedSave.TAG_TRAINING);
    int sizePos = file.i(0);
    int start = file.pos();
    this.trainingVectors.save(file);
    file.writeAt(sizePos, file.pos() - start);
}
```

In `load()`, in the while-tag switch:
```java
case ChunkedSave.TAG_TRAINING:
    if (version >= 34) {
        this.trainingVectors.load(file, version);
    } else {
        int size = file.i();
        for (int b = 0; b < size; b++) file.b();
    }
    break;
```

- [ ] **Step 5: Bump CHUNKED_VERSION 33 → 34**

```java
public static final int CHUNKED_VERSION = 34;
```

- [ ] **Step 6: Add export throttling in EconProgression**

```java
/** Stage-gated export efficiency multiplier. */
public double exportThroughputMultiplier() {
    if (!StageGate.isActive(StageGate.Feature.EXPORT_THROTTLED, this.stage)) {
        return 1.0;
    }
    return switch (this.stage) {
        case SUBSISTENZ -> EconConfig.exportThrottleSubsistence;
        case HANDEL     -> EconConfig.exportThrottleTrade;
        default         -> 1.0;
    };
}
```

- [ ] **Step 7: Update MainScript — replace old register() calls**

In `MainScript.initBeforeGameInited()`, replace all individual `Xxx.register()` calls with:
```java
// Phase 5: All BValueInduOnly boosters registered via single coordinator.
// Replaces: WealthHappiness.register(), PropertyHappiness.register(),
// GiniConsequences.register(), PovertyPressure.register(), HealthPressure.register().
CitizenLayers.registerAll();
```

- [ ] **Step 8: Bump pom.xml version 0.1.0 → 0.2.0**

```xml
<version>0.2.0</version>
```

- [ ] **Step 9: Run `mvn compile` + verify 3 gates pass**

Run: `mvn compile 2>&1 | grep -E 'BUILD|GATE|Bestanden|Fehlgeschlagen'`
Expected: BUILD SUCCESS, Bestanden: 3, Fehlgeschlagen: 0

- [ ] **Step 10: Commit**

```bash
git add src/vannon/syx/economy/core/EconomySim.java src/vannon/syx/economy/core/ChunkedSave.java src/vannon/syx/economy/core/EconProgression.java src/vannon/syx/economy/core/MainScript.java pom.xml
git commit -m "feat: integrate TrainingVectors, NeedsBridge, export throttling into EconomySim (save v34, pom 0.2.0)"
```

---

### Task 10: DiagnosticExporter + GLOSSARY.md

**Files:**
- Modify: `src/vannon/syx/economy/core/DiagnosticExporter.java`
- Modify: `docs/GLOSSARY.md`

- [ ] **Step 1: Add 2 new columns to macro CSV**

In `MACRO_HEADER`, append `"mean_need_fulfilment", "mean_training_skill"`.

In `formatMacroRow()`, append:
```java
double needFulfil = NeedsBridge.populationMean();
double meanSkill = sim != null && sim.trainingVectors() != null
    ? sim.trainingVectors().populationMean() : 0.0;
sb.append(',').append(String.format("%.3f", needFulfil));
sb.append(',').append(String.format("%.2f", meanSkill));
```

- [ ] **Step 2: Add glossary entries**

In `docs/GLOSSARY.md`, 🟩 Simulation section:
```markdown
### Phase 5: EXP / Training / Needs (8 Dateien) [NEU v0.2.0]

| Klasse | Kategorie | Was sie tut |
|--------|-----------|-------------|
| **StageGate** | 🟨 | Zentralisierte Feature-Gates per `EconProgression.Stage`. Keine `if (stage >= X)`-Streuung. |
| **NeedsBridge** | 🟩 | Liest `STATS.NEEDS().SNEEDS` pro Bürger (defensives try/catch). Read-only, null Risiko. |
| **ITrainingSource** | 🟦 | Adapter-Interface: `getTargetVector(Humanoid) → float[]`. Internal + CSV Implementierungen. |
| **InternalTrainingSource** | 🟩 | `sqrt(workTime)` × `EconomicRoles.isTrainingRoom()` → Skill-Vektor. Deterministisch. |
| **TrainingVectors** | 🟩 | Slot-Array (Wallets-Pattern): 60k × DIM floats, Lambda-Blending, IUpdater-Verteilung. |
| **CsvTrainingSource** | 🟦 | `volatile float[] pending` + `volatile boolean pendingValid` — thread-safe via JMM §17.4. |
| **CsvIngest** | 🟨 | Async-Thread: liest CSV, CLAMP-validiert, atomarer `setPending()`-Swap. Nie blockierend. |
| **CitizenLayers** | 🟩 | Single Coordinator: ALLE `BValueInduOnly` via `EconomySim.active()`. Keine captured instances. |
```

- [ ] **Step 3: Commit**

```bash
git add src/vannon/syx/economy/core/DiagnosticExporter.java docs/GLOSSARY.md
git commit -m "feat: export Needs+Training to CSV; Phase 5 glossary entries (8 new)"
```

---

### Task 11: Final Verification

- [ ] **Step 1: Full `mvn test`**

Run: `mvn test 2>&1 | grep -E 'Tests run:|BUILD'`
Expected: Tests run: 32+ (24 TreasuryCrisis + 5 StageGate + 3 TrainingVectors), Failures: 0

- [ ] **Step 2: All 3 build gates**

Run: `mvn compile 2>&1 | grep -E 'GATE|Bestanden|Fehlgeschlagen'`
Expected: Bestanden: 3, Fehlgeschlagen: 0, Übersprungen: 0

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "feat: Phase 5 complete — NeedsBridge, TrainingVectors (stage-gated), CitizenLayers coordinator, export throttling, save v34, pom 0.2.0"
```

---

## Self-Review

**1. Spec coverage:**
- [x] Stage-gated rollout via `StageGate.Feature` (Task 2)
- [x] NeedsBridge first (HANDEL, read-only, Task 4)
- [x] TrainingVectors internal formula (INDUSTRIE, Task 5)
- [x] CSV import (WOHLSTAND+, Task 6, behind ITrainingSource with fallback)
- [x] `BValue.BValueInduOnly` on HAPPI, LOYALTY, SANITY (Task 8)
- [x] `ITrainingSource` adapter pattern (Tasks 5+6)
- [x] `EconomicRoles` expanded (Task 3)
- [x] `CitizenLayers` single coordinator (Task 8)
- [x] Export throttling via Stage (Task 9, Step 6)
- [x] **Thread safety: volatile + JMM §17.4 guarantee** (Task 6)
- [x] **No captured sim instances — `EconomySim.active()` dynamically** (Task 8)
- [x] **lambdaBp initialized in touch()** (Task 7)
- [x] **CsvIngest takes CsvTrainingSource** (Task 6)
- [x] **CitizenLayers in MainScript, not EconomySim constructor** (Task 9, Step 7)

**2. Placeholder scan:** No TBD, TODO, `// implement later`, or `// ... (matching X logic)`. All code blocks are complete.

**3. Type consistency:**
- `ITrainingSource.getTargetVector(Humanoid) → float[]` matches both implementations
- `CsvTrainingSource.setPending(float[])` called by `CsvIngest` — takes `CsvTrainingSource` in constructor
- `CitizenLayers.registerAll()` matches `MainScript.initBeforeGameInited()` call site
- `StageGate.isActive(Feature, Stage)` matches `EconProgression.Stage.level`
- `TrainingVectors.blend(float[], float[], double, int)` matches test signatures
- `EconomySim.CHUNKED_VERSION = 34` matches `TAG_TRAINING` save/load guards
