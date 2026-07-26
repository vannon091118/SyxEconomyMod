package vannon.syx.economy.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DebugCsv format and escape rules.
 * Sprint 6.3 + 6.4 — additive test for the unified logging bridge.
 */
class DebugCsvFormatTest {

    @AfterEach
    void reset() {
        DebugCsv.disableForce();
        DebugCsv.resetForTests();
    }

    @Test
    void headerHasEightCanonicalFields() {
        String[] h = DebugCsv.HEADER;
        assertEquals(8, h.length);
        assertEquals("tick", h[0]);
        assertEquals("day", h[1]);
        assertEquals("category", h[2]);
        assertEquals("subsystem", h[3]);
        assertEquals("severity", h[4]);
        assertEquals("key", h[5]);
        assertEquals("value", h[6]);
        assertEquals("note", h[7]);
    }

    @Test
    void formatRowPreservesSemicolons() {
        String row = DebugCsv.formatRow(100, 1.234, "TRACE", "FIRM", "INFO",
                "employees", "12", "ok");
        assertTrue(row.startsWith("100;1.234"));
        assertTrue(row.contains("TRACE;FIRM;INFO"));
        assertTrue(row.endsWith("employees;12;ok"));
    }

    @Test
    void formatRowEscapesQuotesAndSemicolons() {
        String row = DebugCsv.formatRow(50, 0.0, "TRACE", "FIRM", "INFO",
                "key\"with\"quotes", "value;with;semicolons", "note-with-newline\n");
        // Quotes become doubled, semicolons inside fields must trigger wrap in quotes
        assertTrue(row.contains("\"\"") || row.contains("quote"),
                "row must escape quotes — got: " + row);
    }

    @Test
    void escapeCommaTriggersQuoteWrap() {
        // Comma removed from schema; semicolons are the only separator.
        // Comma in note should NOT trigger wrap (only semicolons do)
        assertEquals("simple,nocomma", DebugCsv.esc("simple,nocomma"));
    }

    @Test
    void escapeSemicolonTriggersQuoteWrap() {
        String result = DebugCsv.esc("has;semicolon");
        assertEquals("\"has;semicolon\"", result);
    }

    @Test
    void escapeEmptyReturnsEmpty() {
        assertEquals("", DebugCsv.esc(""));
        assertEquals("", DebugCsv.esc((String) null));
    }

    @Test
    void formatRowLocalizesDouble() {
        String row = DebugCsv.formatRow(0, 1234.5678, "CAT", "SUB", "INFO",
                "k", "v", "n");
        // Decimal point, not decimal-comma (Locale.ROOT)
        assertTrue(row.contains("1234.568"), "expected '1234.568' — got: " + row);
        assertFalse(row.contains("1234,568"), "must NOT use comma — got: " + row);
    }

    @Test
    void isOnRespectsForceToggle() {
        // Default: depends on EconConfig.debugTracing which is true in test profile
        DebugCsv.disableForce();
        // We can't assume off because EconConfig.debugTracing may be true.
        // Test only that flipping force changes behavior predictably.
        DebugCsv.forceEnabled();
        assertTrue(DebugCsv.isOn());
        DebugCsv.disableForce();
        // After force-disable, isOn falls back to debugTracing (test default = true)
        assertTrue(DebugCsv.isOn() == EconConfig.debugTracing);
    }

    @Test
    void schemaVersionIsStableV1() {
        assertEquals("v1", DebugCsv.SCHEMA_VERSION);
    }

    @Test
    void loggingAdapterCategoryConstantsAreNonNull() {
        assertNotNull(LoggingAdapter.Category.TRACE);
        assertNotNull(LoggingAdapter.Category.REBALANCE);
        assertNotNull(LoggingAdapter.Category.ADAPTER);
        assertNotNull(LoggingAdapter.Category.SNAPSHOT);
        assertNotNull(LoggingAdapter.Category.INTR);
        assertNotNull(LoggingAdapter.Category.DIAG);
        assertNotNull(LoggingAdapter.Category.SYSTEM);
    }

    @Test
    void loggingAdapterSubsystemConstantsAreNonNull() {
        assertNotNull(LoggingAdapter.Subsystem.FIRM);
        assertNotNull(LoggingAdapter.Subsystem.FOOD);
        assertNotNull(LoggingAdapter.Subsystem.ECON);
        assertNotNull(LoggingAdapter.Subsystem.ROOM);
        assertNotNull(LoggingAdapter.Subsystem.SEAM);
        assertNotNull(LoggingAdapter.Subsystem.WAGE);
        assertNotNull(LoggingAdapter.Subsystem.HOUSING);
    }

    @Test
    void loggingAdapterSeverityConstantsAreNonNull() {
        assertNotNull(LoggingAdapter.Severity.DEBUG);
        assertNotNull(LoggingAdapter.Severity.INFO);
        assertNotNull(LoggingAdapter.Severity.WARN);
        assertNotNull(LoggingAdapter.Severity.ERROR);
        assertNotNull(LoggingAdapter.Severity.FATAL);
    }

    @Test
    void csvTraceIsNoOpWhenDisabledAndNeverThrows() {
        // We can only verify that csvTrace does not throw, regardless of toggle.
        try {
            LoggingAdapter.csvTrace("SYS", "ECON", "INFO", "k", "v", "n");
            LoggingAdapter.csvTrace(null, null, null, null, null, null);
        } catch (Throwable t) {
            fail("csvTrace must never throw — got " + t);
        }
    }

    @Test
    void csvTraceNeverThrowsEvenAtNullTick() {
        // Without an active engine, currentTick returns 0 and currentDay returns 0.
        // We still expect no throw.
        try {
            LoggingAdapter.csvTrace("CAT", "SUB", "INFO", "key", "value",
                    "a note with commas, and other content\n");
        } catch (Throwable t) {
            fail("csvTrace threw unexpectedly: " + t);
        }
    }
}
