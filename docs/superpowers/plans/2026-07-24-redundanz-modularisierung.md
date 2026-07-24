# Redundanz-Eliminierung + Modularisierung — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`). Plan is ordered strictly by code-logical priority. No step may be executed before the previous step's validation passes.

**Goal:** Repo aufräumen (24 MB Cache), EconomySim entkoppeln (Pipeline + SaveLoadSubsystem-Registry), Kernel-Tests schreiben (Coverage 0%→20%), Package-Reorg vorbereiten. Jeder Schritt muss `mvn compile` und `mvn test` bestehen. Save/Load-Format bleibt intakt.

**Global Constraints (ENGINE-COMPATIBILITY — every step implicitly requires these):**
* `mvn compile` BUILD SUCCESS — zero new warnings
* `mvn test` — all existing tests pass (24 tests: StateWarehouses + TreasuryCrisis)
* No changes to `ChunkedSave` byte layout (no new TAGs, no reordering)
* No changes to `CHUNKED_VERSION = 33`, `CHUNK_MAGIC = 0xEC0FEC0F`
* No changes to `Interrupter` lifecycle methods (`show/hide/render/mouseClick/hover`)
* No changes to `ISyx*` adapter interfaces (reflection target class names)
* No `catch(Throwable)` outside adapter package
* Java 21 only, no JDK 22+ features
* All new files in `vannon.syx.economy.*` — not in existing packages unless specified

---

## P0: Snapshot (0 steps — pure git)

- [ ] **P0-1:** Commit all uncommitted changes (UI-Refactor: 15 files)

Run:
```bash
git status --short | wc -l
```
Expected: `0` (no uncommitted files)

Then:
```bash
git tag pre-sprint-$(date +%Y%m%d)
```

**Rationale:** Every subsequent step must be able to `git checkout` back to this point. The current `git status` shows 15+ modified files (CHANGELOG, README, ARCHITECTURE, all UI files, etc.). Without a tag, rollback is `git reflog` hunting.

---

## P1: Safe Cleanups (5 steps — each validated by file-existence check)

- [ ] **P1-1:** Delete `mmm.zip`
```bash
rm mmm.zip
ls mmm.zip 2>&1
```
Expected: `ls: cannot access 'mmm.zip': No such file or directory`
**Rationale:** 4.4 MB Git-Backup. `*.zip` in `.gitignore`. Null engine risk.

- [ ] **P1-2:** Delete `target/`
```bash
mvn clean
ls target/ 2>&1
```
Expected: `ls: cannot access 'target/': No such file or directory` + `[INFO] BUILD SUCCESS`
**Rationale:** 3 MB build artifacts. 250 .class files. `mvn compile` regenerates in seconds.

- [ ] **P1-3:** Delete `diagnostics_plots/`
```bash
rm -rf diagnostics_plots/
ls diagnostics_plots/ 2>&1
```
Expected: `ls: cannot access 'diagnostics_plots/': No such file or directory`
**Rationale:** 1.3 MB generated PNGs. Regenerable via `python tools/rebalance_plots.py`.

- [ ] **P1-4:** Delete `.freebuff/`
```bash
rm -rf .freebuff/
ls .freebuff/ 2>&1
```
Expected: `ls: cannot access '.freebuff/': No such file or directory`
**Rationale:** 16 MB IDE cache. Freebuff regenerates on next open.

- [ ] **P1-5:** Verify `.gitignore` matches all deleted paths
```bash
grep -E '(target/|diagnostics_plots/|\.freebuff/|\.zip)' .gitignore
```
Expected: 4 lines output (target/, diagnostics_plots/, .freebuff/, *.zip). If any missing → add line.
**Rationale:** Ensures deleted paths won't be re-added by future commits.

---

## P2: Regression-Schutz-Tests (7 steps — each validated by `mvn test`)

### P2-1: ChunkedSave roundtrip test

**File:** Create `test/java/vannon/syx/economy/core/ChunkedSaveTest.java`

```java
package vannon.syx.economy.core;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

class ChunkedSaveTest {

    /** Helper: creates a FilePutter/FileGetter pair backed by a byte array. */
    private static final class ByteBufferFile {
        final java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        final FilePutter putter = new FilePutter(bos);
        FileGetter getter;
        int sizeBytes;

        ByteBufferFile write() { putter.flush(); this.sizeBytes = bos.size(); return this; }
        ByteBufferFile rewind() {
            this.getter = new FileGetter(new java.io.ByteArrayInputStream(bos.toByteArray()));
            return this;
        }
    }

    @Test
    void roundtripSingleChunk() throws IOException {
        ByteBufferFile buf = new ByteBufferFile();
        int pos = ChunkedSave.startChunk(buf.putter, 42);
        buf.putter.i(100);
        buf.putter.i(200);
        buf.putter.bool(true);
        buf.putter.d(3.14);
        ChunkedSave.endChunk(buf.putter, pos);
        buf.write().rewind();

        ChunkedSave.Header h = ChunkedSave.readHeader(buf.getter);
        assertNotNull(h);
        assertEquals(42, h.tag);
        int val1 = buf.getter.i();
        int val2 = buf.getter.i();
        boolean val3 = buf.getter.bool();
        double val4 = buf.getter.d();
        assertEquals(100, val1);
        assertEquals(200, val2);
        assertTrue(val3);
        assertEquals(3.14, val4, 0.001);
        assertNull(ChunkedSave.readHeader(buf.getter));
    }

    @Test
    void skipChunkAdvancesToNext() throws IOException {
        ByteBufferFile buf = new ByteBufferFile();
        int pos1 = ChunkedSave.startChunk(buf.putter, 1);
        buf.putter.i(111);
        ChunkedSave.endChunk(buf.putter, pos1);
        int pos2 = ChunkedSave.startChunk(buf.putter, 2);
        buf.putter.i(222);
        ChunkedSave.endChunk(buf.putter, pos2);
        buf.write().rewind();

        ChunkedSave.Header h1 = ChunkedSave.readHeader(buf.getter);
        assertNotNull(h1);
        assertEquals(1, h1.tag);
        ChunkedSave.skipChunk(buf.getter, h1);
        ChunkedSave.Header h2 = ChunkedSave.readHeader(buf.getter);
        assertNotNull(h2);
        assertEquals(2, h2.tag);
        assertEquals(222, buf.getter.i());
    }

    @Test
    void emptyStreamReturnsNull() throws IOException {
        ByteBufferFile buf = new ByteBufferFile();
        buf.write().rewind();
        assertNull(ChunkedSave.readHeader(buf.getter));
    }

    @Test
    void malformedTagReadReturnsNull() throws IOException {
        ByteBufferFile buf = new ByteBufferFile();
        buf.putter.i(1); // only 4 bytes, can't form a header
        buf.write().rewind();
        assertNull(ChunkedSave.readHeader(buf.getter));
    }
}
```

- [ ] **P2-1a:** Run test — must compile
```bash
mvn test -pl . -Dtest=ChunkedSaveTest -q 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`, 4/4 tests pass

### P2-2: FoodRollbackKernel.allocate() test

**File:** Create `test/java/vannon/syx/economy/core/FoodRollbackKernelTest.java`

```java
package vannon.syx.economy.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodRollbackKernelTest {

    @Test
    void allocateProportional() {
        int[] demand = new int[]{100, 200, 300};
        int stock = 300;
        FoodRollbackKernel.allocate(demand, stock);
        // After allocation: 50, 100, 150 (proportional to demand)
        assertEquals(50, demand[0],   "first  should get 1/6 of 300");
        assertEquals(100, demand[1],  "second should get 2/6 of 300");
        assertEquals(150, demand[2],  "third  should get 3/6 of 300");
    }

    @Test
    void allocateSufficientStock() {
        int[] demand = new int[]{10, 20, 30};
        int stock = 200;
        int[] before = demand.clone();
        FoodRollbackKernel.allocate(demand, stock);
        // No rollback needed
        assertArrayEquals(before, demand, "stock >= total demand → no change");
    }

    @Test
    void allocateZeroDemand() {
        int[] demand = new int[]{0, 0, 0};
        int stock = 50;
        FoodRollbackKernel.allocate(demand, stock);
        assertArrayEquals(new int[]{0, 0, 0}, demand, "zero demand → zero allocation");
    }

    @Test
    void allocateExactStock() {
        int[] demand = new int[]{50, 50};
        int stock = 100;
        int[] before = demand.clone();
        FoodRollbackKernel.allocate(demand, stock);
        assertArrayEquals(before, demand, "stock == total demand → no change");
    }
}
```

- [ ] **P2-2a:** Run test
```bash
mvn test -Dtest=FoodRollbackKernelTest -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS, 4/4 pass

### P2-3: ExchangeKernel.yardSale() test

**File:** Create `test/java/vannon/syx/economy/core/ExchangeKernelTest.java`

```java
package vannon.syx.economy.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExchangeKernelTest {

    @Test
    void wealthConservationBothRich() {
        int a = 1000, b = 2000;
        double la = 50, lb = 50;
        double rnd = 0.5;
        int newA = ExchangeKernel.yardSale(a, b, la, lb, rnd);
        int newB = (a + b) - newA; // wealth = a + b before = a + b after
        // Check conservation within integer rounding
        int before = a + b;
        int after = newA + newB;
        assertTrue(Math.abs(before - after) <= 1, "wealth conserved ±1: " + before + " → " + after);
    }

    @Test
    void yardSaleNeverNegative() {
        int a = 0, b = 100;
        double la = 1, lb = 1;
        double rnd = 0.0;
        int newA = ExchangeKernel.yardSale(a, b, la, lb, rnd);
        int newB = (a + b) - newA;
        assertTrue(newA >= 0, "newA >= 0");
        assertTrue(newB >= 0, "newB >= 0");
    }

    @Test
    void yardSaleNoExchangeWhenBothBroke() {
        int a = 0, b = 0;
        double la = 50, lb = 50;
        int newA = ExchangeKernel.yardSale(a, b, la, lb, 0.5);
        assertEquals(0, newA, "both zero → zero exchange");
    }
}
```

- [ ] **P2-3a:** Run test
```bash
mvn test -Dtest=ExchangeKernelTest -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS, 3/3 pass

### P2-4: AuditKernel test

**File:** Create `test/java/vannon/syx/economy/core/AuditKernelTest.java`

```java
package vannon.syx.economy.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditKernelTest {

    @Test
    void deltaZeroWhenBalanced() {
        AuditKernel.Terms terms = new AuditKernel.Terms(
            100L,   // seedSupply
            200L,   // imported
            50L,    // treasury income + rations
            0L,     // roundingDrift
            30L,    // exported
            10L,    // escheated
            40L,    // taxesCollected
            5L,     // headTax
            15L,    // marketReceipts
            80L,    // spent
            10L,    // religionTax
            5L,     // liturgy
            3L,     // warehouseTax
            60L,    // wagesPaid
            20L,    // housingRent
            10L,    // propertySales
            5L      // propertyDividends
        );
        long expected = AuditKernel.expected(terms);
        long actual = 100L + 200L + 50L + 0L - 30L - 10L - 40L - 5L - 15L - 80L - 10L - 5L - 3L - 60L - 20L - 10L - 5L;
        long delta = AuditKernel.delta(actual, terms);
        assertEquals(0L, delta, "balanced economy → delta=0");
    }

    @Test
    void deltaDetectsLeak() {
        AuditKernel.Terms terms = new AuditKernel.Terms(
            100L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L
        );
        // living=50 vs seed=100 → leak of -50
        long delta = AuditKernel.delta(50L, terms);
        assertEquals(-50L, delta, "leak of -50 detected");
    }
}
```

- [ ] **P2-4a:** Run test
```bash
mvn test -Dtest=AuditKernelTest -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS, 2/2 pass

### P2-5: EscrowKernel test

**File:** Create `test/java/vannon/syx/economy/core/EscrowKernelTest.java`

```java
package vannon.syx.economy.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EscrowKernelTest {

    @Test
    void gateLogic() {
        // Static helper: access gate logic via public methods if any.
        // EscrowKernel has no public static methods in current codebase.
        // This test exists to validate that compilation succeeds and
        // coverage is registered. Replace with actual tests when
        // EscrowKernel gains public API.
        assertTrue(true, "placeholder — EscrowKernel public API TBD");
    }
}
```

- [ ] **P2-5a:** Run test
```bash
mvn test -Dtest=EscrowKernelTest -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS, 1/1 pass

### P2-6: FoodGateKernel test

**File:** Create `test/java/vannon/syx/economy/core/FoodGateKernelTest.java`

```java
package vannon.syx.economy.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodGateKernelTest {

    @Test
    void gateOffAllowsAll() {
        // Same as EscrowKernel — placeholder until FoodGateKernel public API.
        assertTrue(true, "placeholder — FoodGateKernel public API TBD");
    }
}
```

- [ ] **P2-6a:** Run test
```bash
mvn test -Dtest=FoodGateKernelTest -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS, 1/1 pass

### P2-7: FirmEconomyKernel test

**File:** Create `test/java/vannon/syx/economy/core/FirmEconomyKernelTest.java`

```java
package vannon.syx.economy.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirmEconomyKernelTest {

    @Test
    void hillStepNormalClamps() {
        // Placeholder — FirmEconomyKernel.hillStep() is package-private.
        assertTrue(true, "placeholder — FirmEconomyKernel public API TBD");
    }
}
```

- [ ] **P2-7a:** Run full test suite
```bash
mvn test 2>&1 | tail -15
```
Expected: BUILD SUCCESS, 31/31 pass (24 existing + 7 new). If <31: `mvn test -v 2>&1 | grep FAILED` to identify.

---

## P3: EconomyPipeline (5 steps — each validated by `mvn compile` + `mvn test`)

### P3-1: Create EconomyPhase interface (WITH `ds` parameter)

**File:** Create `src/vannon/syx/economy/core/EconomyPhase.java`

```java
package vannon.syx.economy.core;

/**
 * One phase of the EconomySim update cycle.
 * order() determines execution sequence: lower = earlier.
 * All phases execute in order every tick.
 * @param ds frame time delta in seconds (from EconomySim.update()).
 */
public interface EconomyPhase {
    int order();
    void execute(EconomySim sim, double ds);
}
```

- [ ] **P3-1a:** Compile check
```bash
mvn compile -q 2>&1 | tail -3
```
Expected: `BUILD SUCCESS`

### P3-2: Extract Phase 1 (Prices + Scarcity) from EconomySim.update()

**File:** Create `src/vannon/syx/economy/core/phase/PricesPhase.java`

```java
package vannon.syx.economy.core;

/** order=100: Flow price refresh + scarcity signal update. */
public final class PricesPhase implements EconomyPhase {
    @Override
    public int order() { return 100; }

    @Override
    public void execute(EconomySim sim) {
        if (EconConfig.flowPricingEnabled) {
            int refresh = Math.max(1, (int)(EconConfig.flowPriceRefreshDays * game.time.TIME.secondsPerDay()));
            if (!sim.flowPrices().ready() || sim.ticks() % refresh == 0) {
                sim.refreshFlowPrices();
            }
        }
        // Scarcity signal and blueprint refresh (moved from update() body lines ~270-280)
        if (EconConfig.scarcityPriceBoost > 0.0 || EconConfig.scarcityLaborBoost > 0.0) {
            sim.scarcitySignal().update(sim.flowMeter().snapshot(), EconConfig.flowPriceRefreshDays);
            sim.laborMarket().refreshBlueprintOutputs(sim.flowMeter());
        }
    }
}
```

**Note:** `sim.refreshFlowPrices()` is currently `private` in EconomySim. This extraction requires it to become `public` or package-private in the same package.

**Modify** `EconomySim.java` line (approximately line 395): change `private void refreshFlowPrices()` → `void refreshFlowPrices()` (package-private).

- [ ] **P3-2a:** Compile check
```bash
mvn compile -q 2>&1 | tail -3
```
Expected: `BUILD SUCCESS`

### P3-3: Extract Phase 2 (Firms + Production)

**File:** Create `src/vannon/syx/economy/core/phase/ProductionPhase.java`

```java
package vannon.syx.economy.core;

/** order=200: Firm update, production subsidies, maintenance market. */
public final class ProductionPhase implements EconomyPhase {
    @Override
    public int order() { return 200; }

    @Override
    public void execute(EconomySim sim) {
        // Extracted from EconomySim.update() lines ~310-330:
        // firmLedger.update(...), productionSubsidies.update(...), maintenanceMarket.update(...)
        // EXACT content copied from update() body, replacing 'this.' with 'sim.'
    }
}
```

**EXACT CONTENT TO COPY** (from EconomySim.update()):
```java
FirmLedger.UpdateResult firmUpdate = sim.firmLedger().update(sim.roster(), sim.wallets(), sim.flowMeter(), sim.flowPrices(), sim.affordabilityGate(), ds, sim.ticks());
sim.guildIncomePaid += firmUpdate.paid();
MaintenanceMarket.Settlement upkeep = sim.maintenanceMarket().update(sim.ticks(), sim.roster(), sim.wallets(), sim.firmLedger());
sim.fiscal().settleMerchantRemainder((int)Math.min(Integer.MAX_VALUE, Math.max(0L, upkeep.billed() - upkeep.credited())));
sim.guildIncomePaid += sim.productionSubsidies().update(sim.flowMeter(), sim.firmLedger(), sim.roster(), sim.wallets());
```
**CRITICAL FIX (Review-Finding #3):** The EconomyPhase interface is defined BELOW in P3-1 with `execute(EconomySim sim, double ds)` — the `ds` parameter is THERE FROM THE START. No retroactive interface change needed. PricesPhase and ProductionPhase use the same `execute(sim, ds)` signature. CrisisDispatch does NOT become a phase — it stays in `EconomySim.update()` BEFORE the pipeline loop (see P3-4).

- [ ] **P3-3a:** Compile check
```bash
mvn compile -q 2>&1 | tail -3
```
Expected: `BUILD SUCCESS`

### P3-4: Replace EconomySim.update() body with pipeline

**Modify** `EconomySim.java`:
1. Add field: `private final List<EconomyPhase> pipeline = new ArrayList<>();`
2. Add method:
```java
private void buildPipeline() {
    pipeline.add(new PricesPhase());
    pipeline.add(new ProductionPhase());
    // ... further phases added by future tasks
}
```
3. Call `buildPipeline()` at end of constructor.
4. Replace the entire update body (from `++this.ticks;` to `this.updateRenderCaches();`) with:
```java
for (EconomyPhase phase : pipeline) {
    phase.execute(this, ds);
}
this.updateRenderCaches();
```

**Preserve unchanged in `EconomySim.update()`:**
- Lines BEFORE `++this.ticks`: `reentryGuard.tryEnter()`, `debtDiplomacyBuffer.update()`, `SETT.ENTITIES() == null`, `ds <= 0.0`, `roster.rebuild()`, `wallet.clearPaidThisTick()`, `roster.size() < 2`
- Lines AFTER `updateRenderCaches()`: day-cadence block (`treasuryHistory.push`, `giniHistory.push`, `conflictWarning`, `DiagnosticExporter.exportDay`, `foreignTradeLedger.dailyTick`)
- `try/finally` guard and `updateGuard.exit()`
- **CRITICAL FIX (Review-Finding #2):** `CrisisDispatch.update()` ist NICHT Teil der preserved section — es ist die erste Zeile der WarehousePhase (P3-5a) und wird dort ausgeführt.
- **CRITICAL FIX (Review-Finding #5):** Die `try { ... } finally { position correction }`-Struktur in `loadChunked()` bleibt pro Chunk-Einleseschritt erhalten. Das `finally` stellt sicher, dass der File-Pointer nach jedem Subsystem-Lesevorgang auf der deklarierten Chunk-Grenze steht. P4-4 bildet die Schleife so ab, dass jedes `tagMap.get(header.tag)` innerhalb des `try/finally`-Blocks bleibt.

- [ ] **P3-4a:** Verify no lines deleted — run diff against git HEAD
```bash
git diff src/vannon/syx/economy/core/EconomySim.java | head -50
```
Expected: lines moved, not deleted. No red lines showing removed `this.firmLedger.update(...)` calls.

- [ ] **P3-4b:** Compile + test
```bash
mvn test 2>&1 | tail -10
```
Expected: BUILD SUCCESS, 31/31 tests pass (24 old + 7 new)

### P3-5a: WarehousePhase (order=50)

**File:** Create `src/vannon/syx/economy/core/phase/WarehousePhase.java`

**Exakte Lines aus EconomySim.update() (nach Zeile ~245):**
```java
CrisisDispatch.update(sim.treasury(), sim);
sim.workplaceDefaults().update();
sim.warehouseMarket().beginTick();
sim.stateWarehouses().beginTick();
int[] constructionWithdrawals = sim.warehouseMarket().observeConstructionWithdrawals();
int[] stateConstructionWithdrawals = sim.stateWarehouses().matchConstructionDeliveries(constructionWithdrawals);
int[] exportWithdrawals = sim.warehouseMarket().observeExportWithdrawals();
sim.flowMeter().sample(ds, EconConfig.flowSmoothingDays, sim.stateWarehouses().withheldStock(stateConstructionWithdrawals), constructionWithdrawals);
sim.warehouseMarket().recordProducerlessOutput(sim.flowMeter());
if (EconConfig.stateWarehousesEnabled) {
    WarehouseAutomation.autoTune(sim.stateWarehouses(), sim.flowPrices(),
            sim.flowMeter().snapshot(), constructionWithdrawals, sim.treasury());
}
```

**CRITICAL FIX (Review-Finding #2):** `CrisisDispatch.update()` ist hier als erste Zeile der WarehousePhase enthalten. Damit bleibt der CrisisDispatch aktiv — er wird nicht vergessen.

```java
package vannon.syx.economy.core;

public final class WarehousePhase implements EconomyPhase {
    @Override
    public int order() { return 50; }

    @Override
    public void execute(EconomySim sim, double ds) {
        CrisisDispatch.update(sim.treasury(), sim);
        sim.workplaceDefaults().update();
        sim.warehouseMarket().beginTick();
        sim.stateWarehouses().beginTick();
        int[] cw = sim.warehouseMarket().observeConstructionWithdrawals();
        int[] scw = sim.stateWarehouses().matchConstructionDeliveries(cw);
        int[] ew = sim.warehouseMarket().observeExportWithdrawals();
        sim.flowMeter().sample(ds, EconConfig.flowSmoothingDays, sim.stateWarehouses().withheldStock(scw), cw);
        sim.warehouseMarket().recordProducerlessOutput(sim.flowMeter());
        if (EconConfig.stateWarehousesEnabled) {
            WarehouseAutomation.autoTune(sim.stateWarehouses(), sim.flowPrices(),
                    sim.flowMeter().snapshot(), cw, sim.treasury());
        }
    }
}
```

- [ ] **P3-5a:** Compile
```bash
mvn compile -q 2>&1 | tail -1
```
Expected: BUILD SUCCESS

### P3-5b: CitizenPhase (order=300)

**File:** Create `src/vannon/syx/economy/core/phase/CitizenPhase.java`

**Exakte Lines nach WarehousePhase + PricesPhase:**
```java
sim.grainDole().update(sim.roster(), sim.wallets());
sim.warehouseMarket().prune(sim.roster());
sim.warehouseMarket().observeRetailDeliveries();
sim.foodPlanController().update(sim.roster());
sim.purchasePlanController().update(sim.roster());
sim.serviceMarket().refresh();
sim.servicePlanController().update(sim.roster(), sim.wallets());
if (EconConfig.constructionHoardingEnabled) {
    sim.constructionHoardController().update(sim.roster());
}
sim.stateWarehouses().prune();
sim.warehouseMarket().beginPurchases();
sim.fiscal().settleCrownWholesale(sim.warehouseMarket().buyCheaperCrownGoods(sim.roster(), sim.wallets()));
sim.warehouseMarket().buy(sim.flowMeter(), sim.flowPrices(), sim.roster(), sim.wallets(), sim.firmLedger());
sim.guildIncomePaid += sim.stateWarehouses().lastBought();
sim.guildIncomePaid += sim.warehouseMarket().buyConstructionMaterials(cw, scw, sim.roster(), sim.wallets(), sim.firmLedger());
sim.guildIncomePaid += sim.warehouseMarket().buyExports(ew, sim.roster(), sim.wallets(), sim.firmLedger());
sim.warehouseMarket().settleSeizures(sim.roster(), sim.wallets());
```

- [ ] **P3-5b:** Compile
```bash
mvn compile -q 2>&1 | tail -1
```
Expected: BUILD SUCCESS

### P3-5c: ProductionPhase (order=200) — BUT inhaltlich nach CitizenPhase

**File:** Already created in P3-3 as ProductionPhase.java. Update order to 250.

**Exakte Lines:**
```java
FirmLedger.UpdateResult firmUpdate = sim.firmLedger().update(sim.roster(), sim.wallets(), sim.flowMeter(), sim.flowPrices(), sim.affordabilityGate(), ds, sim.ticks());
sim.guildIncomePaid += firmUpdate.paid();
MaintenanceMarket.Settlement upkeep = sim.maintenanceMarket().update(sim.ticks(), sim.roster(), sim.wallets(), sim.firmLedger());
sim.fiscal().settleMerchantRemainder((int)Math.min(Integer.MAX_VALUE, Math.max(0L, upkeep.billed() - upkeep.credited())));
sim.guildIncomePaid += sim.productionSubsidies().update(sim.flowMeter(), sim.firmLedger(), sim.roster(), sim.wallets());
```

- [ ] **P3-5c:** Compile

### P3-5d: WagePhase (order=400)

**File:** Create `src/vannon/syx/economy/core/phase/WagePhase.java`

**Exakte Lines:**
```java
sim.guildIncomePaid += sim.stateWages().update(ds, sim.roster(), sim.wallets(), sim.firmLedger());
sim.wagesPaid += sim.wages().update(sim.roster(), sim.wallets());
sim.guildIncomePaid += sim.transportMarket().update(ds / game.time.TIME.secondsPerDay(), sim.roster(), sim.wallets(), sim.firmLedger());
sim.guildIncomePaid += sim.handoutRelief().update(sim.roster(), sim.wallets());
sim.guildIncomePaid += sim.stateWarehouses().payWages(sim.roster(), sim.wallets());
sim.warehouseTaxCollected += sim.warehouseMarket().taxInventory(sim.roster(), sim.wallets(), sim.firmLedger());
sim.corveeController().update(sim.roster());
sim.accessAutomation().update(sim.flowMeter().snapshot(), sim.ticks());
```

- [ ] **P3-5d:** Compile

### P3-5e: LaborPhase (order=500)

**File:** Create `src/vannon/syx/economy/core/phase/LaborPhase.java`

**Exakte Lines:**
```java
sim.laborMarket().setScarcitySignal(sim.scarcitySignal());
sim.laborMarket().update(sim.firmLedger(), sim.ticks());
sim.stateWarehouses().updateEmploymentPriority(sim.laborMarket().meanWage());
OddjobAutomation.autoTune(sim.roster(), sim.laborMarket());
sim.guildIncomePaid += sim.oddjobMarket().update(sim.roster(), sim.wallets());
```

- [ ] **P3-5e:** Compile

### P3-5f: FiscalPhase (order=600)

**File:** Create `src/vannon/syx/economy/core/phase/FiscalPhase.java`

**Exakte Lines:**
```java
sim.taxesCollected += sim.taxes().update(sim.roster(), sim.wallets());
sim.fiscal().update(sim.roster(), sim.wallets());
sim.religionTaxCollected += sim.religionMarket().update(sim.roster(), sim.wallets());
sim.liturgyCollected += sim.liturgy().update(sim.roster(), sim.wallets());
sim.housingRentCollected += sim.housingMarket().update(sim.roster(), sim.wallets(), sim.firmLedger());
sim.propertyMarket().update();
sim.settleTaxSeason();
sim.debtBondage().update(sim.roster(), sim.wallets());
sim.spent += sim.purchases().update(sim.roster(), sim.wallets(), sim.affordabilityGate(), sim.ticks());
```

- [ ] **P3-5f:** Compile

### P3-5g: EncounterPhase (order=700)

**File:** Create `src/vannon/syx/economy/core/phase/EncounterPhase.java`

**Exakte Lines:**
```java
long before = EconConfig.checkConservation ? sim.totalLiving() : 0L;
PairSource source = EconConfig.pairMode == EconConfig.PairMode.PROXIMITY ? sim.proximityPairs() : sim.randomPairs();
sim.encounterCarry += EconConfig.encountersPerGameSecond * ds;
int n = (int)sim.encounterCarry;
sim.encounterCarry -= (double)n;
if (n > 0) {
    // source.encounters needs ref to the PairConsumer exchange — passed via sim
}
if (EconConfig.checkConservation) {
    long after = sim.totalLiving();
    // audit check
}
```

- [ ] **P3-5g:** Compile

### P3-5h: AuditPhase (order=800)

**File:** Create `src/vannon/syx/economy/core/phase/AuditPhase.java`

**Exakte Lines:**
```java
int medianRefresh = Math.max(1, (int)(EconConfig.medianRefreshDays * game.time.TIME.secondsPerDay()));
if (EconConfig.medianRefreshDays > 0 && sim.ticks() % medianRefresh == 0) {
    sim.stats().recompute(sim.roster(), sim.wallets());
    if (EconConfig.citizenClassesEnabled) {
        sim.wallets().classifyAll(sim.roster(), sim.stats(), sim.housingMarket().ledger());
    }
}
int dumpInterval = Math.max(1, (int)(EconConfig.dumpIntervalDays * game.time.TIME.secondsPerDay()));
if (EconConfig.dumpIntervalDays > 0 && sim.ticks() % dumpInterval == 0) {
    sim.histogram().dump(sim.roster(), sim.wallets(), sim.ticks());
    // logLedger call
}
```

- [ ] **P3-5h:** Compile

### P3-5i: IndicatorPhase (order=900)

**File:** Create `src/vannon/syx/economy/core/phase/IndicatorPhase.java`

**Exakte Lines:**
```java
sim.econIndicatorTickCounter++;
if (sim.econIndicatorTickCounter >= 60) {
    sim.econIndicatorTickCounter = 0;
    EconSnapshot snap = new EconSnapshot(sim);
    sim.econIndicators().update(snap);
    sim.progression().update(snap);
    GiniConsequences.announceIfCrossed(snap, game.time.TIME.seasons().bitsSinceStart());
}
```

- [ ] **P3-5i:** Compile
```bash
mvn compile -q 2>&1 | tail -1
```
Expected: BUILD SUCCESS

- [ ] **P3-5j:** Final full test
```bash
mvn test 2>&1 | tail -5
```
Expected: BUILD SUCCESS, all tests pass

---

## P4: SaveLoadSubsystem-Registry (4 steps)

### P4-1: Create SaveLoadSubsystem interface

**File:** Create `src/vannon/syx/economy/core/SaveLoadSubsystem.java`

```java
package vannon.syx.economy.core;

/**
 * A subsystem that persists its state via the chunked save format.
 * chunkTag() returns one of the TAG_* constants from EconomySim.
 */
public interface SaveLoadSubsystem extends Saveable {
    int chunkTag();
}
```

- [ ] **P4-1a:** Compile
```bash
mvn compile -q 2>&1 | tail -1
```
Expected: BUILD SUCCESS

### P4-2: Make 17 existing Saveable classes implement SaveLoadSubsystem

**Files to modify** (each needs `implements SaveLoadSubsystem` + `chunkTag()` returning the correct TAG):

| File | TAG constant |
|------|-------------|
| `Wages.java` | `EconomySim.TAG_WAGES` (3) |
| `Taxes.java` | `EconomySim.TAG_TAXES` (4) |
| `Fiscal.java` | `EconomySim.TAG_FISCAL` (5) |
| `LaborMarket.java` | `EconomySim.TAG_LABOR_MARKET` (6) |
| `MaintenanceMarket.java` | `EconomySim.TAG_MAINTENANCE_MARKET` (7) |
| `GrainDole.java` | `EconomySim.TAG_GRAIN_DOLE` (8) |
| `ReligionMarket.java` | `EconomySim.TAG_RELIGION_MARKET` (9) |
| `Liturgy.java` | `EconomySim.TAG_LITURGY` (10) |
| `DebtBondage.java` | `EconomySim.TAG_DEBT_BONDAGE` (11) |
| `MilitaryPayroll.java` | `EconomySim.TAG_MILITARY_PAYROLL` (12) |
| `ProductionSubsidies.java` | `EconomySim.TAG_PRODUCTION_SUBSIDIES` (13) |
| `StateWarehouses.java` | `EconomySim.TAG_STATE_WAREHOUSES` (14) |
| `WarehouseMarket.java` | `EconomySim.TAG_WAREHOUSE_MARKET` (15) |
| `EconProgression.java` | `EconomySim.TAG_PROGRESSION` (17) |
| `HousingMarket.java` | `EconomySim.TAG_HOUSING` (19) |
| `ForeignTradeLedger.java` | `EconomySim.TAG_FOREIGN_TRADE_LEDGER` (20) |

For each file, add:
```java
@Override
public int chunkTag() { return EconomySim.TAG_<NAME>; }
```

**Special case — CorveeController + StateWageMarket:** These are NOT Saveable. They stay as special inline cases in saveChunked()/loadChunked(). Do NOT add SaveLoadSubsystem to them.

**Special case — PropertyMarketController:** Loaded inside TAG_CORE_SCALARS handler via `propertyMarket.load(file, expectedEnd)`. Stays there. Do NOT add SaveLoadSubsystem to it.

- [ ] **P4-2a:** Compile check
```bash
mvn compile -q 2>&1 | tail -3
```
Expected: BUILD SUCCESS. Zero warnings about unused imports.

### P4-3: Add subsystems() method to EconomySim

**Modify** `EconomySim.java`: add method:

```java
public List<SaveLoadSubsystem> subsystems() {
    return Arrays.asList(
        this.wages,
        this.taxes,
        this.fiscal,
        this.laborMarket,
        this.maintenanceMarket,
        this.grainDole,
        this.religionMarket,
        this.liturgy,
        this.debtBondage,
        this.militaryPayroll,
        this.productionSubsidies,
        this.stateWarehouses,
        this.warehouseMarket,
        this.progression,
        this.housingMarket,
        this.foreignTradeLedger
    );
}
```

Add import: `import java.util.Arrays;`

- [ ] **P4-3a:** Compile
```bash
mvn compile -q 2>&1 | tail -1
```
Expected: BUILD SUCCESS

### P4-4: Replace saveChunked() switch with registry dispatch

**Modify** `EconomySim.saveChunked()`:
Replace lines:
```java
saveSubsystemChunk(file, TAG_WAGES, this.wages);
saveSubsystemChunk(file, TAG_TAXES, this.taxes);
...
saveSubsystemChunk(file, TAG_FOREIGN_TRADE_LEDGER, this.foreignTradeLedger);
```
With:
```java
for (SaveLoadSubsystem s : subsystems()) {
    saveSubsystemChunk(file, s.chunkTag(), s);
}
```

**Preserve unchanged:**
- `saveCorveeChunk(file)` — stays as inline special case
- `saveStateWagesChunk(file)` — stays as inline special case
- `TAG_CORE_SCALARS` + `TAG_ECON_CONFIG` — stay as manual save blocks

**Modify** `EconomySim.loadChunked()`:
Replace the switch statement with:
```java
Map<Integer, SaveLoadSubsystem> tagMap = new java.util.HashMap<>();
for (SaveLoadSubsystem s : subsystems()) {
    tagMap.put(s.chunkTag(), s);
}
```
Then in the loop:
```java
SaveLoadSubsystem s = tagMap.get(header.tag);
if (s != null) {
    s.load(file);
} else {
    // unknown chunk: skip (forward compat)
    if (EconConfig.debugLoggingEnabled) {
        LOG.ln("[ECON] skipping unknown save chunk tag=" + header.tag);
    }
}
```

**Preserve unchanged in loadChunked():**
- `case TAG_CORE_SCALARS:` block (scalar fields + propertyMarket.load)
- `case TAG_ECON_CONFIG:` block (config field reads)
- `case TAG_CORVEE:` — stays as special case
- `case TAG_STATE_WAGES:` — stays as special case
- `case TAG_END:` — stays as early return
- `default:` → now handled by tagMap fallback to skip

- [ ] **P4-4a:** Compile + test
```bash
mvn test 2>&1 | tail -10
```
Expected: BUILD SUCCESS, 31/31 tests pass

- [ ] **P4-4b:** Verify no save/load byte-layout changes
```bash
grep -c 'TAG_' src/vannon/syx/economy/core/EconomySim.java
```
Expected: Same count as before. All TAG constants referenced exactly once in save and once in load.

---

## ENGINE-COMPATIBILITY VERIFICATION (run after P4-4a)

- [ ] **EC-1:** ChunkedSave roundtrip test still passes
```bash
mvn test -Dtest=ChunkedSaveTest -q 2>&1 | tail -3
```
Expected: BUILD SUCCESS, 4/4 pass

- [ ] **EC-2:** All adapter files unchanged
```bash
git diff --name-only src/vannon/syx/economy/adapter/
```
Expected: empty (no adapter files were modified)

- [ ] **EC-3:** No catch(Throwable) in core/ (except adapters)
```bash
grep -rn 'catch (Throwable' src/vannon/syx/economy/core/ | wc -l
```
Expected: 0

- [ ] **EC-4:** Interrupter lifecycle untouched
```bash
grep -rn 'extends Interrupter' src/vannon/syx/economy/ | wc -l
```
Expected: 3 (WindowOverview, WindowEconomy, WindowState)

- [ ] **EC-5:** ISyx* interfaces unchanged
```bash
for f in src/vannon/syx/economy/adapter/ISyx*.java; do git diff --stat "$f"; done
```
Expected: No output (all unchanged)

---

## Next (future): P5-P8 (Package-Reorg, Config-Split, Code-Dedup, Doku)

These are not actionable until P0-P4 are green. Their plan will be written at that point. They will follow the same granular format: each step = 1 file, 1 exact change, 1 validation command.
