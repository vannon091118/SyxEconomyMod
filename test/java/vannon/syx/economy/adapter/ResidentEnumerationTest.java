package vannon.syx.economy.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import settlement.entity.humanoid.Humanoid;
import vannon.syx.economy.headless.MockWorldState;
import vannon.syx.economy.headless.StubHumanoidAccess;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;

/**
 * Adapter-level Clean-Room-Test für {@link IHumanoidAccess#getResidentCount()}
 * und {@link IHumanoidAccess#forEachResident(Consumer)} (Sprint v0.13.129+
 * ResidentImportFix).
 *
 * <p>Tests 3-4 ({@code iterationWithFactoryCallsConsumerExactlyNTimes} und
 * {@code iterationSurvivesSingleVisitorException}) erfordern
 * {@code mock(Humanoid.class)} — das schlägt auf JVM 25 fehl weil die
 * Humanoid-Supertyp-Initialisierung den Game-Engine-Kontext braucht
 * (settlement.entity.humanoid.Humanoid extends Klassen die STATICS triggern).
 * Diese Tests sind {@code @Disabled} bis ein headless-safe Humanoid-Stub
 * existiert.</p>
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
        for (int n : new int[]{1, 7, 50, 1000}) {
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
        stub.forEachResident(tracking);
        assertEquals(0, callCount.get(),
            "Ohne Factory darf kein Resident geliefert werden");
    }

    @Test
    @Disabled("JVM 25: Humanoid class initializer fails without game engine — "
            + "mock(Humanoid.class) + Unsafe.allocateInstance both trigger static init")
    @DisplayName("forEachResident mit Factory ruft Consumer genau N-mal auf")
    void iterationWithFactoryCallsConsumerExactlyNTimes() {
        // Requires Humanoid instances — see @Disabled reason
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
    @Disabled("JVM 25: Humanoid class initializer fails without game engine — "
            + "see iterationWithFactoryCallsConsumerExactlyNTimes")
    @DisplayName("Visitor-Exception bricht Iteration nicht ab (Skip-and-Continue)")
    void iterationSurvivesSingleVisitorException() {
        // Requires Humanoid instances — see @Disabled reason
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
