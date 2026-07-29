package vannon.syx.economy.core;

import static org.junit.jupiter.api.Assertions.*;

import init.resources.RESOURCE;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Sprint 9 — PriorityRegistry Test.
 *
 * <p>Testet den Singleton-Zustand, die Tick-Guards in recompute(), die
 * score()-Berechnung mit echten RESOURCE-Instanzen, und die Zustands-Isolation
 * zwischen Tests. Nutzt package-private Fields (pressure, lastRecomputeTick)
 * direkt — genau das Pattern das in agents.md Rule 14 als Test-Hook
 * dokumentiert ist.</p>
 *
 * <p><b>RESOURCE-Erzeugung:</b> RESOURCE erbt von {@code util.info.INFO} —
 * Mockito ByteBuddy kann die sealed class hierarchy nicht instrumentieren.
 * Stattdessen wird {@code sun.misc.Unsafe.allocateInstance()} verwendet, das
 * den Constructor komplett umgeht. Das {@code key}-Feld wird via Reflection
 * gesetzt. Die RESOURCE-Instanzen sind echte Objekte (keine Mocks), daher
 * funktioniert HashMap.get() und PriorityRegistry.score() korrekt.</p>
 *
 * <p><b>Einschränkung:</b> FlowMeter.Snapshot ist {@code public static final}
 * — nicht mockbar. recompute()-Tests decken nur die Tick-Guards und den
 * Null-Snapshot-Pfad ab. Die coverage-based Druckberechnung (stock/demand)
 * wird in einem separaten Integrationstest mit Engine-Bootstrap abgedeckt.</p>
 */
class PriorityRegistryMockitoTest {

    @BeforeEach
    void cleanState() {
        PriorityRegistry reg = PriorityRegistry.instance();
        reg.pressure.clear();
        reg.lastRecomputeTick = -1L;
    }

    // ─── Singleton Pattern ─────────────────────────────────────────

    @Test
    void instance_returnsNonNull() {
        assertNotNull(PriorityRegistry.instance());
    }

    @Test
    void instance_returnsSameObjectAcrossCalls() {
        PriorityRegistry a = PriorityRegistry.instance();
        PriorityRegistry b = PriorityRegistry.instance();
        assertSame(a, b, "PriorityRegistry must be a singleton");
    }

    // ─── Initial State ─────────────────────────────────────────────

    @Test
    void initialState_lastRecomputeTickIsNegativeOne() {
        assertEquals(-1L, PriorityRegistry.instance().lastRecomputeTick);
    }

    @Test
    void initialState_pressureMapIsEmpty() {
        assertTrue(PriorityRegistry.instance().pressure.isEmpty());
    }

    // ─── @BeforeEach Isolation ─────────────────────────────────────

    @Test
    void cleanState_resetsBetweenTests() {
        PriorityRegistry reg = PriorityRegistry.instance();
        reg.lastRecomputeTick = 999L;
        reg.pressure.put(createResource("SENTINEL"), 0.42);
        assertEquals(999L, reg.lastRecomputeTick);
        assertFalse(reg.pressure.isEmpty());
        // The next test (initialState_*) will fail if @BeforeEach doesn't work
    }

    // ─── recompute() — Tick Guard ──────────────────────────────────

    @Test
    void recompute_sameTickIsNoOp() {
        PriorityRegistry reg = PriorityRegistry.instance();
        reg.lastRecomputeTick = 42L;
        reg.pressure.put(createResource("WOOD"), 0.5);

        reg.recompute(null, 42L); // same tick → no-op

        assertFalse(reg.pressure.isEmpty(), "pressure should NOT be cleared — recompute was a no-op");
        assertEquals(42L, reg.lastRecomputeTick);
    }

    @Test
    void recompute_differentTickWithNullSnapshotClearsPressure() {
        PriorityRegistry reg = PriorityRegistry.instance();
        reg.lastRecomputeTick = 10L;
        reg.pressure.put(createResource("IRON"), 0.8);

        reg.recompute(null, 11L);

        assertTrue(reg.pressure.isEmpty(), "null snapshot should clear pressure");
        assertEquals(11L, reg.lastRecomputeTick);
    }

    @Test
    void recompute_nullSnapshotSetsTickEvenWhenPressureEmpty() {
        PriorityRegistry reg = PriorityRegistry.instance();
        assertEquals(-1L, reg.lastRecomputeTick);

        reg.recompute(null, 500L);

        assertTrue(reg.pressure.isEmpty());
        assertEquals(500L, reg.lastRecomputeTick);
    }

    @Test
    void recompute_firstCallFromNegativeOneTick() {
        PriorityRegistry reg = PriorityRegistry.instance();
        assertEquals(-1L, reg.lastRecomputeTick);

        reg.recompute(null, 0L);

        assertEquals(0L, reg.lastRecomputeTick);
        assertTrue(reg.pressure.isEmpty());
    }

    // ─── recompute() — Tick Monotonicity ───────────────────────────

    @Test
    void recompute_tickMonotonicity_lastTickUpdated() {
        PriorityRegistry reg = PriorityRegistry.instance();

        reg.recompute(null, 1L);
        assertEquals(1L, reg.lastRecomputeTick);

        reg.recompute(null, 1L); // same tick → no-op
        assertEquals(1L, reg.lastRecomputeTick);

        reg.recompute(null, 2L); // new tick → updates
        assertEquals(2L, reg.lastRecomputeTick);
    }

    // ─── score() — Null / Empty Guards ─────────────────────────────

    @Test
    void score_nullOutputs_returnsZero() {
        assertEquals(0.0, PriorityRegistry.instance().score(null));
    }

    @Test
    void score_emptyOutputs_returnsZero() {
        assertEquals(0.0, PriorityRegistry.instance().score(new RESOURCE[0]));
    }

    @Test
    void score_allNullsInArray_returnsZero() {
        // score() has `if (r == null) continue;` — nulls are skipped
        assertEquals(0.0, PriorityRegistry.instance().score(new RESOURCE[]{null, null}));
    }

    @Test
    void score_emptyPressureMap_returnsZero() {
        PriorityRegistry reg = PriorityRegistry.instance();
        RESOURCE wood = createResource("WOOD");
        assertTrue(reg.pressure.isEmpty());
        assertEquals(0.0, reg.score(new RESOURCE[]{wood}), 1e-9);
    }

    // ─── score() — With Real RESOURCE Instances ────────────────────

    @Test
    void score_singleResource_returnsPressure() {
        PriorityRegistry reg = PriorityRegistry.instance();
        RESOURCE wood = createResource("WOOD");
        reg.pressure.put(wood, 0.75);

        assertEquals(0.75, reg.score(new RESOURCE[]{wood}), 1e-9);
    }

    @Test
    void score_multipleResources_returnsMaxPressure() {
        PriorityRegistry reg = PriorityRegistry.instance();
        RESOURCE wood = createResource("WOOD");
        RESOURCE iron = createResource("IRON");
        RESOURCE bread = createResource("BREAD");
        reg.pressure.put(wood, 0.3);
        reg.pressure.put(iron, 0.9);
        reg.pressure.put(bread, 0.6);

        assertEquals(0.9, reg.score(new RESOURCE[]{wood, iron, bread}), 1e-9);
    }

    @Test
    void score_unknownResource_returnsZero() {
        PriorityRegistry reg = PriorityRegistry.instance();
        RESOURCE known = createResource("WOOD");
        RESOURCE unknown = createResource("UNKNOWN");
        reg.pressure.put(known, 0.5);

        // unknown is not in pressure map → skipped
        assertEquals(0.0, reg.score(new RESOURCE[]{unknown}), 1e-9);
    }

    @Test
    void score_nullElementInArray_skipped() {
        PriorityRegistry reg = PriorityRegistry.instance();
        RESOURCE wood = createResource("WOOD");
        reg.pressure.put(wood, 0.8);

        // null elements are skipped by `if (r == null) continue;`
        assertEquals(0.8, reg.score(new RESOURCE[]{null, wood, null}), 1e-9);
    }

    @Test
    void score_mixedKnownAndUnknown_returnsMaxOfKnown() {
        PriorityRegistry reg = PriorityRegistry.instance();
        RESOURCE wood = createResource("WOOD");
        RESOURCE iron = createResource("IRON");
        RESOURCE unknown = createResource("UNKNOWN");
        reg.pressure.put(wood, 0.3);
        reg.pressure.put(iron, 0.7);

        // unknown not in map → 0.0, wood=0.3, iron=0.7 → max=0.7
        assertEquals(0.7, reg.score(new RESOURCE[]{unknown, wood, iron}), 1e-9);
    }

    // ─── score() — After recompute(null) Clears State ──────────────

    @Test
    void score_afterRecomputeNull_clearsToZero() {
        PriorityRegistry reg = PriorityRegistry.instance();
        RESOURCE wood = createResource("WOOD");
        reg.pressure.put(wood, 0.9);
        assertEquals(0.9, reg.score(new RESOURCE[]{wood}), 1e-9);

        reg.recompute(null, 200L);

        assertEquals(0.0, reg.score(new RESOURCE[]{wood}), 1e-9);
    }

    // ─── Pressure Map — Direct Manipulation (simulates recompute output) ──

    @Test
    void pressure_stockZero_pressureOne() {
        PriorityRegistry reg = PriorityRegistry.instance();
        RESOURCE wood = createResource("WOOD");
        reg.pressure.put(wood, 1.0);

        assertEquals(1.0, reg.score(new RESOURCE[]{wood}), 1e-9);
    }

    @Test
    void pressure_halfCoverage_pressureHalf() {
        PriorityRegistry reg = PriorityRegistry.instance();
        RESOURCE bread = createResource("BREAD");
        reg.pressure.put(bread, 0.5);

        assertEquals(0.5, reg.score(new RESOURCE[]{bread}), 1e-9);
    }

    @Test
    void pressure_fullCoverage_pressureZero() {
        PriorityRegistry reg = PriorityRegistry.instance();
        RESOURCE stone = createResource("STONE");
        reg.pressure.put(stone, 0.0);

        assertEquals(0.0, reg.score(new RESOURCE[]{stone}), 1e-9);
    }

    @Test
    void pressure_multipleResources_independentPressure() {
        PriorityRegistry reg = PriorityRegistry.instance();
        RESOURCE wood = createResource("WOOD");
        RESOURCE iron = createResource("IRON");
        reg.pressure.put(wood, 1.0);  // stock=0 → max scarcity
        reg.pressure.put(iron, 0.1);  // stock=900, demand=1000

        assertEquals(1.0, reg.score(new RESOURCE[]{wood}), 1e-9);
        assertEquals(0.1, reg.score(new RESOURCE[]{iron}), 1e-9);
        assertEquals(1.0, reg.score(new RESOURCE[]{wood, iron}), 1e-9); // max
    }

    // ─── RESOURCE Key Uniqueness ───────────────────────────────────

    @Test
    void createResource_differentKeys_differentInstances() {
        RESOURCE a = createResource("WOOD");
        RESOURCE b = createResource("IRON");
        assertNotSame(a, b);
        assertEquals("WOOD", a.key);
        assertEquals("IRON", b.key);
    }

    @Test
    void createResource_sameKey_differentInstances() {
        RESOURCE a = createResource("WOOD");
        RESOURCE b = createResource("WOOD");
        assertNotSame(a, b, "Each createResource call returns a new instance");
        assertEquals(a.key, b.key);
    }

    // ─── Helper: Unsafe-based RESOURCE instantiation ───────────────

    /**
     * Erzeugt eine echte RESOURCE-Instanz via sun.misc.Unsafe.allocateInstance().
     * Umgeht den Constructor komplett — funktioniert auf jeder Klasse egal ob
     * final, sealed, oder private Constructor. Setzt das {@code key}-Feld via
     * Reflection.
     */
    private static RESOURCE createResource(String key) {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);

            RESOURCE r = (RESOURCE) unsafe.allocateInstance(RESOURCE.class);

            Field keyField = RESOURCE.class.getField("key");
            keyField.setAccessible(true);
            keyField.set(r, key);

            return r;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test RESOURCE: " + key, e);
        }
    }
}
