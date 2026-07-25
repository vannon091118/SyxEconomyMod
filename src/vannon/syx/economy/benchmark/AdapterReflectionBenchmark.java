package vannon.syx.economy.benchmark;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Standalone Microbenchmark der BypassGate-SDK-Zugriffspfade.
 *
 * <p>Seit Phase F (v0.13.8) nutzen alle 5 Adapter den BypassGate-SDK
 * ({@code adapter/seam/}) mit auto-select VarHandle/MethodHandle vs. Reflection.
 * Dieser Benchmark misst die zugrundeliegenden Zugriffsmuster.</p>
 *
 * <h3>Ausführung</h3>
 * <pre>
 *   java -cp target/classes:&lt;game-jar&gt; \
 *        vannon.syx.economy.benchmark.AdapterReflectionBenchmark
 * </pre>
 */
public final class AdapterReflectionBenchmark {

    private static final int WARMUP_ITERS = 5_000;
    private static final int MEASURE_ITERS = 50_000;
    private static final String[] PLAN_PROXY_CLASSES = {
        "java.util.ArrayList", "java.util.LinkedList", "java.util.HashSet",
        "java.util.TreeMap",  "java.util.HashMap",  "java.util.ArrayDeque",
    };

    /* ── Benchmark-Target (float/double/int-Felder) ───────────────── */

    @SuppressWarnings("unused")
    private static final class Target {
        float distance = 42.5f;
        Object payload = "test";
        double power = 1234.5;
        int index = 7;
        @SuppressWarnings("unused")
        boolean toggle(boolean state) { return !state; }
    }

    /* ═══════════════════════════════════════════════════════════════
       STATISTIK
       ═══════════════════════════════════════════════════════════════ */

    private static double mean(long[] nanos) {
        long sum = 0;
        for (long n : nanos) sum += n;
        return (double) sum / nanos.length;
    }

    private static long percentile(long[] sorted, double pct) {
        if (sorted.length == 0) return 0;
        int idx = (int) Math.ceil(pct / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    /* ═══════════════════════════════════════════════════════════════
       WARMUP-CHARAKTERISTIK
       ═══════════════════════════════════════════════════════════════ */

    /** Misst die ersten N Aufrufe und zeigt wie die Latenz mit JIT-Warmup sinkt. */
    private static void warmupProfile(String label, Runnable op, int totalCalls, int batchSize) {
        System.out.printf("  %-50s %s%n", label, "JIT-Warmup (erste 10 Batches)");
        for (int batch = 0; batch < totalCalls / batchSize && batch < 10; batch++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < batchSize; i++) op.run();
            long t1 = System.nanoTime();
            double avgNs = (double) (t1 - t0) / batchSize;
            System.out.printf("    Batch %2d: %,8.0f ns/call%n", batch + 1, avgNs);
        }
    }

    /* ═══════════════════════════════════════════════════════════════
       BENCHMARK-RUNNER
       ═══════════════════════════════════════════════════════════════ */

    private static void bench(String label, Runnable warmup, Runnable measure, int warmupIters, int measureIters) {
        // Warmup
        for (int i = 0; i < warmupIters; i++) warmup.run();

        // Measurement
        long[] times = new long[measureIters];
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long t0 = System.nanoTime();
        for (int i = 0; i < measureIters; i++) {
            long tOp0 = System.nanoTime();
            measure.run();
            times[i] = System.nanoTime() - tOp0;
        }
        long t1 = System.nanoTime();
        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.gc(); // Hint für nächste Messung
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        long[] sorted = times.clone();
        Arrays.sort(sorted);

        double avgNs = mean(times);
        long p50 = percentile(sorted, 50);
        long p99 = percentile(sorted, 99);
        long p999 = percentile(sorted, 99.9);
        double allocMbPerSec = (double) (memAfter - memBefore) / ((t1 - t0) / 1_000_000_000.0) / (1024 * 1024);

        System.out.printf("  %-50s avg=%8.0f ns | p50=%6d ns | p99=%6d ns | p99.9=%6d ns | alloc=%.2f MB/s%n",
                label, avgNs, p50, p99, p999, allocMbPerSec);
    }

    /* ═══════════════════════════════════════════════════════════════
       MAIN
       ═══════════════════════════════════════════════════════════════ */

    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws Throwable {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println("  SyxEconomyMod — BypassGate SDK Benchmark (Phase F)");
        System.out.println("  Java: " + System.getProperty("java.version")
                + " | VM: " + System.getProperty("java.vm.name"));
        System.out.println("  Warmup: " + WARMUP_ITERS + " calls | Measure: " + MEASURE_ITERS + " calls");
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println();

        /* ── Setup ──────────────────────────────────────────────────── */

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Target target = new Target();

        // Reflection
        ArrayList<?> aiProxy = new ArrayList<>();
        Object notAiProxy = "not a list";
        Class<?> cachedClass = ArrayList.class;
        Field distField = Target.class.getDeclaredField("distance");
        distField.setAccessible(true);
        Field refField = Target.class.getDeclaredField("payload");
        refField.setAccessible(true);
        Field doubleField = Target.class.getDeclaredField("power");
        doubleField.setAccessible(true);
        Field intField = Target.class.getDeclaredField("index");
        intField.setAccessible(true);
        Method toggleMethod = Target.class.getDeclaredMethod("toggle", boolean.class);
        toggleMethod.setAccessible(true);

        // MethodHandle / VarHandle
        MethodHandle mhIsInstance = lookup.findVirtual(Class.class, "isInstance",
                MethodType.methodType(boolean.class, Object.class)).bindTo(cachedClass);
        MethodHandle mhIsInstanceNeg = mhIsInstance; // same handle, different arg
        VarHandle vhDist = MethodHandles.privateLookupIn(Target.class, lookup)
                .findVarHandle(Target.class, "distance", float.class);
        VarHandle vhRef = MethodHandles.privateLookupIn(Target.class, lookup)
                .findVarHandle(Target.class, "payload", Object.class);
        VarHandle vhDouble = MethodHandles.privateLookupIn(Target.class, lookup)
                .findVarHandle(Target.class, "power", double.class);
        VarHandle vhInt = MethodHandles.privateLookupIn(Target.class, lookup)
                .findVarHandle(Target.class, "index", int.class);
        MethodHandle mhToggle = lookup.findVirtual(Target.class, "toggle",
                MethodType.methodType(boolean.class, boolean.class)).bindTo(target);

        /* ═══════════════════════════════════════════════════════════════
           SECTION 1 — Constructor-Time (einmalig pro Adapter-Init)
           ═══════════════════════════════════════════════════════════════ */

        System.out.println("── 1. CONSTRUCTOR-TIME (einmalig pro Adapter-Init) ──");
        System.out.println();

        bench("Class.forName (6×, cached)",
            () -> { try { for (String n : PLAN_PROXY_CLASSES) Class.forName(n); } catch (Exception e) {} },
            () -> { try { for (String n : PLAN_PROXY_CLASSES) Class.forName(n); } catch (Exception e) {} },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("Class.forName (missing class → Fallback)",
            () -> { try { Class.forName("com.nonexistent.V72Plan"); } catch (Exception e) {} },
            () -> { try { Class.forName("com.nonexistent.V72Plan"); } catch (Exception e) {} },
            WARMUP_ITERS / 10, MEASURE_ITERS / 10);

        bench("getDeclaredField + setAccessible (once, cached)",
            () -> { try { Target.class.getDeclaredField("distance"); } catch (Exception e) {} },
            () -> { try { Target.class.getDeclaredField("distance"); } catch (Exception e) {} },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("getDeclaredMethod + setAccessible (once, cached)",
            () -> { try { Target.class.getDeclaredMethod("toggle", boolean.class); } catch (Exception e) {} },
            () -> { try { Target.class.getDeclaredMethod("toggle", boolean.class); } catch (Exception e) {} },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("getDeclaredFields-Iteration (Boosting-Adapter)",
            () -> { for (Field f : Target.class.getDeclaredFields()) { f.getName(); f.getType(); } },
            () -> { for (Field f : Target.class.getDeclaredFields()) { f.getName(); f.getType(); } },
            WARMUP_ITERS, MEASURE_ITERS);

        /* ═══════════════════════════════════════════════════════════════
           SECTION 2 — Runtime Hot-Path (per-Tick)
           ═══════════════════════════════════════════════════════════════ */

        System.out.println();
        System.out.println("── 2. RUNTIME HOT-PATH — Reflection (aktueller Adapter-Code) ──");
        System.out.println();

        bench("Class.isInstance() — positive (AI-Adapter ★★★)",
            () -> { boolean v = cachedClass.isInstance(aiProxy); },
            () -> { boolean v = cachedClass.isInstance(aiProxy); },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("Class.isInstance() — negative (häufigster Fall)",
            () -> { boolean v = cachedClass.isInstance(notAiProxy); },
            () -> { boolean v = cachedClass.isInstance(notAiProxy); },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("Field.getFloat() (Transport-Adapter ★★)",
            () -> { try { distField.getFloat(target); } catch (Exception e) {} },
            () -> { try { distField.getFloat(target); } catch (Exception e) {} },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("Field.get() — Object (Diplomacy-Adapter ★★)",
            () -> { try { refField.get(target); } catch (Exception e) {} },
            () -> { try { refField.get(target); } catch (Exception e) {} },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("Field.setDouble() (Diplomacy-Adapter ★★)",
            () -> { try { doubleField.setDouble(target, 9999.9); } catch (Exception e) {} },
            () -> { try { doubleField.setDouble(target, 9999.9); } catch (Exception e) {} },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("Field.setInt() (Diplomacy-Adapter ★★)",
            () -> { try { intField.setInt(target, 42); } catch (Exception e) {} },
            () -> { try { intField.setInt(target, 42); } catch (Exception e) {} },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("Method.invoke(boolean) (Warehouse-Adapter ★)",
            () -> { try { toggleMethod.invoke(target, true); } catch (Exception e) {} },
            () -> { try { toggleMethod.invoke(target, true); } catch (Exception e) {} },
            WARMUP_ITERS, MEASURE_ITERS);

        /* ═══════════════════════════════════════════════════════════════
           SECTION 3 — Runtime Hot-Path — MethodHandle / VarHandle
           ═══════════════════════════════════════════════════════════════ */

        System.out.println();
        System.out.println("── 3. RUNTIME HOT-PATH — MethodHandle / VarHandle (optimiert) ──");
        System.out.println();

        bench("MH.isInstance() — positive",
            () -> { try { mhIsInstance.invokeExact((Object) aiProxy); } catch (Throwable e) {} },
            () -> { try { mhIsInstance.invokeExact((Object) aiProxy); } catch (Throwable e) {} },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("MH.isInstance() — negative",
            () -> { try { mhIsInstanceNeg.invokeExact((Object) notAiProxy); } catch (Throwable e) {} },
            () -> { try { mhIsInstanceNeg.invokeExact((Object) notAiProxy); } catch (Throwable e) {} },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("VH.get() — float (Transport-Adapter)",
            () -> { float v = (float) vhDist.get(target); },
            () -> { float v = (float) vhDist.get(target); },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("VH.get() — Object (Diplomacy-Adapter)",
            () -> { Object v = vhRef.get(target); },
            () -> { Object v = vhRef.get(target); },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("VH.set() — double",
            () -> { vhDouble.set(target, 9999.9); },
            () -> { vhDouble.set(target, 9999.9); },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("VH.set() — int",
            () -> { vhInt.set(target, 42); },
            () -> { vhInt.set(target, 42); },
            WARMUP_ITERS, MEASURE_ITERS);

        bench("MH.invokeExact(boolean) → boolean",
            () -> { try { mhToggle.invokeExact(true); } catch (Throwable e) {} },
            () -> { try { mhToggle.invokeExact(true); } catch (Throwable e) {} },
            WARMUP_ITERS, MEASURE_ITERS);

        /* ═══════════════════════════════════════════════════════════════
           SECTION 4 — Warmup-Profile (JIT-Charakteristik)
           ═══════════════════════════════════════════════════════════════ */

        System.out.println();
        System.out.println("── 4. JIT-WARMUP-CHARAKTERISTIK ──");
        System.out.println("  (Aufruf-Latenz sinkt durch C1/C2-Kompilierung)");
        System.out.println();

        warmupProfile("Class.isInstance() — Reflection",
                () -> { boolean v = cachedClass.isInstance(aiProxy); }, 5000, 500);
        System.out.println();
        warmupProfile("Class.isInstance() — MethodHandle",
                () -> { try { mhIsInstance.invokeExact((Object) aiProxy); } catch (Throwable e) {} }, 5000, 500);
        System.out.println();
        warmupProfile("Field.getFloat() — Reflection",
                () -> { try { distField.getFloat(target); } catch (Exception e) {} }, 5000, 500);
        System.out.println();
        warmupProfile("Field.getFloat() — VarHandle",
                () -> { float v = (float) vhDist.get(target); }, 5000, 500);

        /* ═══════════════════════════════════════════════════════════════
           SECTION 5 — Fazit
           ═══════════════════════════════════════════════════════════════ */

        System.out.println();
        System.out.println("── 5. FAZIT & MIGRATIONS-EMPFEHLUNG ──");
        System.out.println();
        System.out.println("  Die obigen Zahlen zeigen den Speedup von MethodHandle/VarHandle");
        System.out.println("  gegenüber java.lang.reflect.* für die Hot-Pfade der 5 Adapter.");
        System.out.println();
        System.out.println("  Typische Erwartungswerte auf JDK 21+ mit C2-JIT:");
        System.out.println("    Class.isInstance()         ~3-5 ns  (MH: ~2-3 ns, minimaler Gewinn)");
        System.out.println("    Field.getFloat()           ~5-8 ns  (VH: ~1-2 ns, 3-5× Speedup)");
        System.out.println("    Field.get() / setDouble()  ~6-10 ns (VH: ~1-2 ns, 4-6× Speedup)");
        System.out.println("    Method.invoke(boolean)     ~8-12 ns (MH: ~2-3 ns, 3-5× Speedup)");
        System.out.println();
        System.out.println("  EMPFEHLUNG:");
        System.out.println("    Alle 5 Adapter nutzen jetzt BypassGate-SDK (auto-select VarHandle/MethodHandle).");
        System.out.println("    EconConfig.useMethodHandleAdapters wurde gelöscht (Phase F).");
        System.out.println("    Dieser Benchmark dient als Referenz für zukünftige Engine-Updates.");
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════════");
        System.out.println();
    }

    private AdapterReflectionBenchmark() {}
}
