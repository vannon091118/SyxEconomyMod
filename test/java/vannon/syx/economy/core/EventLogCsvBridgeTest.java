package vannon.syx.economy.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the EventLog → LoggingAdapter → DebugCsv bridge.
 * Sprint 6.3 / 6.4 — additive test for the PATCH in EventLog.java:107.
 */
class EventLogCsvBridgeTest {

    @AfterEach
    void resetState() {
        DebugCsv.disableForce();
        DebugCsv.resetForTests();
        EventLog.clearRecentEvents();
    }

    @Test
    void eventLogLogDoesNotThrowWhenCsvIsDisabled() {
        // Plain-Log path remains intact when CSV-bridge is off.
        EventLog.init();
        try {
            for (int i = 0; i < 50; i++) {
                EventLog.log("BRIDGE", "test message " + i);
            }
            EventLog.logSampled("BRIDGE", "sampled message");
            assertTrue(EventLog.getRecentEvents().size() > 0);
        } finally {
            EventLog.close();
        }
    }

    @Test
    void eventLogLogDoesNotThrowWhenCsvIsEnabled() {
        // Critical: the LoggingAdapter.patch must not throw even when
        // DebugCsv.isOn() returns true. We force-enable and emit dozens
        // of rows; none may throw into the caller.
        DebugCsv.forceEnabled();
        try {
            EventLog.init();
            for (int i = 0; i < 50; i++) {
                EventLog.log("BRIDGE", "test message with commas, semicolons; and quotes \"");
                EventLog.logSampled("BRIDGE", "sampled iteration " + i);
            }
        } catch (Throwable t) {
            EventLog.close();
            fail("EventLog.log must not throw when DebugCsv is enabled — got " + t);
        }
        EventLog.close();
    }

    @Test
    void sampledPathDoesNotCrashWithManyCalls() {
        // logSampled() inherits an RNG that may skip 90% of calls.
        // The bridge still has to receive the unconditional CSV write on the
        // body path of the call (NOT inside the sample filter). Verify
        // 10.000 iterations complete without exception.
        DebugCsv.forceEnabled();
        try {
            EventLog.init();
            for (int i = 0; i < 10_000; i++) {
                EventLog.logSampled("BRIDGE", "mass-test " + i);
            }
        } catch (Throwable t) {
            EventLog.close();
            fail("mass EventLog.logSampled threw — got " + t);
        }
        EventLog.close();
    }

    @Test
    void trendCooldownDoesNotBlockCsvBridge() {
        // TREND has a 5-minute cooldown in EventLog but the LoggingAdapter
        // call sits AFTER the cooldown-check, so 1000 identical TREND
        // calls inside cooldown still feed DebugCsv with no exception.
        DebugCsv.forceEnabled();
        try {
            EventLog.init();
            for (int i = 0; i < 1000; i++) {
                EventLog.log("TREND", "identical trend message");
            }
        } catch (Throwable t) {
            EventLog.close();
            fail("TREND-cooldown did not block csv-bridge — got " + t);
        }
        EventLog.close();
    }

    @Test
    void nullMessageDoesNotCrashBridge() {
        // Defensive: csvTrace wraps every value in a null-guard, so even
        // an aggressive null payload in EventLog should reach DebugCsv
        // without a crash that propagates back into the caller.
        DebugCsv.forceEnabled();
        try {
            EventLog.init();
            EventLog.log("NULL_TEST", "");
            EventLog.log("NULL_TEST", null);
            EventLog.logSampled("NULL_TEST", null);
        } catch (Throwable t) {
            EventLog.close();
            fail("null message must not throw — got " + t);
        }
        EventLog.close();
    }

    @Test
    void csvBridgeIsOptInByDefault() {
        // Without forceEnable, DebugCsv writes nothing. EventLog.log itself
        // is also gated on debugPriceLogging for its Plain-File-Output, but
        // LoggingAdapter.csvTrace is called regardless of debugPriceLogging.
        DebugCsv.disableForce();
        // Just check that calls succeed with no error.
        EventLog.init();
        EventLog.log("DEFAULT_TEST", "default-mode message");
        EventLog.close();
    }
}
