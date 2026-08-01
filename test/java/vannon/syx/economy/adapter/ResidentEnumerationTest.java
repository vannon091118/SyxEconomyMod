package vannon.syx.economy.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import settlement.entity.humanoid.Humanoid;
import vannon.syx.economy.headless.MockWorldState;
import vannon.syx.economy.headless.StubHumanoidAccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;

/**
 * Adapter-level Clean-Room-Test für {@link IHumanoidAccess#getResidentCount()}
 * und {@link IHumanoidAccess#forEachResident(Consumer)} (Sprint v0.13.129+
 * ResidentImportFix). Isolation von {@link HeadlessIntegrationTest} —
 * dieser Test prüft nur Adapter-Vertrag und Stub-State-Maschine, nicht
 * die komplette Sim-Lifecycle.
 *
 * <p>Test-Methoden:
 * <ol>
 *   <li>{@link #countReturnsConfiguredPopulation()} — getResidentCount()
 *       reflektiert MockWorldState.citizenCount 1:1 (0, 7, 50, 1000).</li>
 *   <li>{@link #iterationWithNoFactoryIsNoOp()} — forEachResident() ohne
 *       Factory ist no-op (Backward-Compat).</li>
 *   <li>{@link #iterationWithFactoryCallsConsumerExactlyNTimes()} — Mockito
 *       Consumer-Verify: factory.get() liefert size=N, Consumer wird
 *       genau N-mal aufgerufen (Mockito.verify times(N)).</li>
 *   <li>{@link #iterationWithFactoryNullThrowsNothing()} — Factory liefert
 *       null → no-op, kein NPE.</li>
 *   <li>{@link #iterationSurvivesSingleVisitorException()} — Visitor wirft
 *       RuntimeException → Iteration läuft weiter, andere Residents werden
 *       trotzdem besucht (graceful Skip-and-Continue).</li>
 *   <li>{@link #nullActionIsNoOp()} — Null-Action wirft keine NPE.</li>
 *   <li>{@link #iterationWithEmptyListCallsZeroTimes()} — Factory liefert
 *       leere Liste → exakt 0 Consumer-Calls.</li>
 * </ol></p>
 *
 * <p>Wichtig: kein Mockito-Mock auf {@link Humanoid} für Count-Tests, weil
 * Mockito mock(Humanoid.class) ohne RETURNS_DEEP_STUBS sofort NPE wirft wenn
 * {@code .indu()} aufgerufen wird. Für Count-Tests reicht der int-Wert aus
 * {@code MockWorldState.citizenCount}.</p>
 */
@DisplayName("ResidentEnumerationTest — Sprint v0.13.129+ (Adapter-Vertrag)")
final class ResidentEnumerationTest {

    private MockWorldState state;
    private StubHumanoidAccess stub;

    @BeforeEach
    void setup() {
        state = new MockWorldState(50, 10, 42L);
        stub = new StubHumanoidAccess(state);
    }

    private static StubHumanoidAccess freshStub(int citizenCount) {
        return new StubHumanoidAccess(new MockWorldState(citizenCount, 10, 42L));
    }

    @Test
    @DisplayName("getResidentCount reflektiert MockWorldState.citizenCount 1:1")
    void countReturnsConfiguredPopulation() {
        for (int n : new int[]{0, 1, 7, 50, 1000}) {
            MockWorldState s = new MockWorldState(n, 10, 42L);
            StubHumanoidAccess a = new StubHumanoidAccess(s);
            assertEquals(n, a.getResidentCount(),
                "getResidentCount() muss MockWorldState.citizenCount liefern für n=" + n);
        }
    }

    @Test
    @DisplayName("forEachResident ohne Factory ist no-op (Backward-Compat)")
    void iterationWithNoFactoryIsNoOp() {
        AtomicInteger callCount = new AtomicInteger();
        Consumer<Humanoid> tracking = h -> callCount.incrementAndGet();
        // Kein setResidentFactory() aufgerufen → Default null
        stub.forEachResident(tracking);
        assertEquals(0, callCount.get(),
            "Ohne Factory darf kein Resident geliefert werden");
    }

    @Test
    @DisplayName("forEachResident mit Factory ruft Consumer genau N-mal auf")
    void iterationWithFactoryCallsConsumerExactlyNTimes() {
        final int N = 50;
        final List<Humanoid> mocks = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            mocks.add(mock(Humanoid.class)); // simple mocks; iteration prüft nur Zählung
        }
        stub.setResidentFactory(() -> mocks);

        @SuppressWarnings("unchecked")
        Consumer<Humanoid> consumer = mock(Consumer.class);
        stub.forEachResident(consumer);

        verify(consumer, times(N)).accept(any(Humanoid.class));
    }

    @Test
    @DisplayName("Factory liefert null → forEachResident ist no-op, kein NPE")
    void iterationWithFactoryNullThrowsNothing() {
        StubHumanoidAccess fresh = freshStub(50);
        fresh.setResidentFactory(() -> null);
        @SuppressWarnings("unchecked")
        Consumer<Humanoid> consumer = mock(Consumer.class);
        assertDoesNotThrow(() -> fresh.forEachResident(consumer),
            "Factory-null darf keinen NPE werfen");
        verify(consumer, times(0)).accept(any(Humanoid.class));
    }

    @Test
    @DisplayName("Visitor-Exception bricht Iteration nicht ab (Skip-and-Continue)")
    void iterationSurvivesSingleVisitorException() {
        final int N = 5;
        StubHumanoidAccess fresh = freshStub(N);
        final List<Humanoid> mocks = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            mocks.add(mock(Humanoid.class));
        }
        fresh.setResidentFactory(() -> mocks);

        AtomicInteger seenAfterFirstException = new AtomicInteger();
        Consumer<Humanoid> exploding = new Consumer<Humanoid>() {
            private int call = 0;
            @Override
            public void accept(Humanoid h) {
                call++;
                if (call == 2) throw new RuntimeException("intentional-veto");
                seenAfterFirstException.incrementAndGet();
            }
        };

        // Iteration MUSS alle 5 Residents aufrufen, auch nach Exception
        assertDoesNotThrow(() -> fresh.forEachResident(exploding),
            "Visitor-Exception darf Iteration nicht abbrechen");
        assertEquals(N - 1, seenAfterFirstException.get(),
            "N-1 Visitor-Calls (nicht der explodierende call). Total=" + N);
    }

    @Test
    @DisplayName("Null-Action wird ignoriert ohne NPE")
    void nullActionIsNoOp() {
        StubHumanoidAccess fresh = freshStub(42);
        assertDoesNotThrow(() -> fresh.forEachResident(null));
        assertEquals(42, fresh.getResidentCount(),
            "Null-Action ändert getResidentCount-Verhalten nicht");
    }

    @Test
    @DisplayName("Factory liefert leere Liste → exakt 0 Consumer-Calls")
    void iterationWithEmptyListCallsZeroTimes() {
        StubHumanoidAccess fresh = freshStub(50);
        fresh.setResidentFactory(Collections::emptyList);
        @SuppressWarnings("unchecked")
        Consumer<Humanoid> consumer = mock(Consumer.class);
        fresh.forEachResident(consumer);
        verify(consumer, times(0)).accept(any(Humanoid.class));
    }
}