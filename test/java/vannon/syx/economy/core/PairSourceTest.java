package vannon.syx.economy.core;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import settlement.entity.humanoid.Humanoid;

/**
 * T-COV-7 — Behavior tests for {@link RandomPairSource} and {@link ProximityPairSource}.
 *
 * <p>{@link ProximityPairSource} ist engine-coupled ({@code SETT.ENTITIES()} +
 * {@code BodyCoord}); nur der size&lt;2-Short-Circuit und die Instanziiertheit werden geprüft.</p>
 *
 * <p>{@link Roster} ist {@code public final class}. Wir setzen die privaten
 * Felder {@code count} und {@code people} via Reflection, um einen Roster mit
 * definierter Größe zu simulieren — der {@link Roster#get(int) get(i)}-Aufruf
 * liefert in diesem Stub {@code null}, was für die Pair-Count-Logik irrelevant ist.</p>
 */
class PairSourceTest {

    @Test
    void randomPairSource_emptyRoster_emitsZeroPairs() {
        RandomPairSource src = new RandomPairSource();
        Roster empty = rosterOfSize(0);
        CountingConsumer consumer = new CountingConsumer();
        src.encounters(empty, 100, consumer);
        assertEquals(0, consumer.count(), "size=0 must short-circuit");
    }

    @Test
    void randomPairSource_sizeOneRoster_emitsZeroPairs() {
        RandomPairSource src = new RandomPairSource();
        Roster one = rosterOfSize(1);
        CountingConsumer consumer = new CountingConsumer();
        src.encounters(one, 100, consumer);
        assertEquals(0, consumer.count(), "size<2 must short-circuit");
    }

    @Test
    void randomPairSource_sizeTwoRoster_emitsSomePairs() {
        RandomPairSource src = new RandomPairSource();
        Roster two = rosterOfSize(2);
        CountingConsumer consumer = new CountingConsumer();
        // 200 encounters: P(ia!=ib)=0.5 → expected ~100 pairs. σ = sqrt(200*0.5*0.5) ≈ 7.
        // We assert ±5σ (50-150) tolerance — flake-rate < ~6e-7.
        src.encounters(two, 200, consumer);
        int n = consumer.count();
        assertTrue(n >= 50 && n <= 150,
                "expected ~100 pairs over 200 encounters with size=2 (±5σ) — got " + n);
    }

    @Test
    void randomPairSource_sizeTwo_emitsLessThanFullEncounters() {
        // Replaces a former "neverEmitsSelfPair"-Test that was broken by the
        // reflection-stub returning null for every get(i) call: a Consumer
        // comparing a==b always saw p=null, null==null == TRUE, biasing counter.
        // This structural-invariant assertion verifies:
        //   1. SOME pairs emit (ia≠ib branch fires)
        //   2. NOT every encounter yields a pair (ia==ib continue-skip prunes ~50%)
        RandomPairSource src = new RandomPairSource();
        Roster two = rosterOfSize(2);
        CountingConsumer consumer = new CountingConsumer();
        // For size=2: P(per-encounter pair emitted) = 0.5 (ia≠ib branch).
        // 500 encounters: E[count] = 250, σ = sqrt(500*0.5*0.5) ≈ 11.18.
        // 5σ tolerance = 195–305 (flake-rate ~5.7e-7). CI-default snake2d.util.rnd.RND
        // is global-static with no public seed() in the versions we ship — Sprint 5
        // will pin via reflection-stub if a seeded RND becomes available.
        src.encounters(two, 500, consumer);
        int n = consumer.count();
        assertTrue(n >= 195 && n <= 305,
                "size=2 with 500 encounters should emit 250±55 pairs (±5σ) — got " + n);
        assertTrue(n < 500,
                "the ia==ib continue-branch must prune some encounters");
    }

    @Test
    void randomPairSource_zeroEncounters_emitsZeroPairs() {
        RandomPairSource src = new RandomPairSource();
        Roster ten = rosterOfSize(10);
        CountingConsumer consumer = new CountingConsumer();
        src.encounters(ten, 0, consumer);
        assertEquals(0, consumer.count());
    }

    // ── ProximityPairSource: nur size-Check + Instanziiertheit ────────

    @Test
    void proximityPairSource_emptyRoster_emitsZeroPairs() {
        ProximityPairSource src = new ProximityPairSource();
        Roster empty = rosterOfSize(0);
        CountingConsumer consumer = new CountingConsumer();
        // size<2 → short-circuit. SETT.ENTITIES() is never queried.
        src.encounters(empty, 100, consumer);
        assertEquals(0, consumer.count());
    }

    @Test
    void proximityPairSource_sizeOneRoster_emitsZeroPairs() {
        ProximityPairSource src = new ProximityPairSource();
        Roster one = rosterOfSize(1);
        CountingConsumer consumer = new CountingConsumer();
        src.encounters(one, 100, consumer);
        assertEquals(0, consumer.count());
    }

    @Test
    void proximityPairSource_canBeInstantiated() {
        ProximityPairSource src = new ProximityPairSource();
        assertNotNull(src);
    }

    // ── stub helpers ─────────────────────────────────────────────────

    @Test
    void proximityPairSource_nearArray_startsAtCapacity64() {
        ProximityPairSource src = new ProximityPairSource();
        Field f;
        try {
            f = ProximityPairSource.class.getDeclaredField("near");
            f.setAccessible(true);
            Object arr = f.get(src);
            assertNotNull(arr);
            assertEquals(Humanoid[].class, arr.getClass());
            assertEquals(64, ((Humanoid[]) arr).length,
                    "near buffer starts at 64 capacity");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Builds a Roster with {@code size} entries (all returning {@code null} on get()).
     * Uses reflection on the private fields.
     */
    private static Roster rosterOfSize(int size) {
        Roster r = new Roster();
        try {
            Field countField = Roster.class.getDeclaredField("count");
            countField.setAccessible(true);
            countField.setInt(r, size);
            Field peopleField = Roster.class.getDeclaredField("people");
            peopleField.setAccessible(true);
            peopleField.set(r, new Humanoid[Math.max(size, 1)]);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return r;
    }

    private static final class CountingConsumer implements PairSource.PairConsumer {
        int count = 0;

        @Override
        public void pair(Humanoid a, Humanoid b) {
            count++;
        }

        int count() {
            return count;
        }
    }
}
