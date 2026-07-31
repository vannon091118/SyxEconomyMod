package vannon.syx.economy.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import vannon.syx.economy.ui.tabs.Overview.OverviewHelpers;

/**
 * Sprint v0.13.106+M-UI-3 — OverviewHelpers Pure-Logic Tests.
 *
 * <p>Wählt den einfachsten-möglichen Testansatz (analog {@link KpiSectionTest}):
 * nur statische Helper mit deterministischer Pure-Logic-Signatur. Engine-
 * abhängige Tab.build() Smoke-Tests bleiben Sprint M-UI-3.1 vorbehalten —
 * sobald die EngineMirror-Mock-Fixture verfügbar ist (analog T-COV-9).</p>
 *
 * <p>Coverage-Lücken (für Sprint M-UI-3.1 markiert):</p>
 * <ul>
 *   <li>{@link OverviewHelpers#buildAdvice} — erfordert EconomySim-Mock mit
 *       firmLedger/housingMarket/scarcitySignal/ioMatrix/flowPrices.</li>
 *   <li>{@link OverviewHelpers#buildWarningChains} — erfordert dieselben Mocks.</li>
 *   <li>Tab.build() rendering — erfordert GuiSection-Stub aus snake2d-Lib.</li>
 * </ul>
 */
class OverviewTabsTest {

    // ─── CHAIN_IMPACT_THRESHOLD Constant ────────────────────────────────

    @Test
    void chainImpactThreshold_is_zero_one() {
        // L[i,j] > 0.1 bedeutet: pro 1 Einheit mehr von res[j] werden ≥0.1
        // Einheiten von res[i] über die Kette benötigt. Default 0.1 = 10%.
        assertEquals(0.1, OverviewHelpers.CHAIN_IMPACT_THRESHOLD, 0.0);
    }

    // ─── countLines() Pure-Logic Tests ────────────────────────────────

    @Test
    void countLines_null_returns_zero() {
        assertEquals(0, OverviewHelpers.countLines(null));
    }

    @Test
    void countLines_empty_returns_zero() {
        assertEquals(0, OverviewHelpers.countLines(""));
    }

    @Test
    void countLines_single_line_returns_one() {
        assertEquals(1, OverviewHelpers.countLines("Hello"));
    }

    @Test
    void countLines_two_lines_returns_two() {
        assertEquals(2, OverviewHelpers.countLines("foo\nbar"));
    }

    @Test
    void countLines_five_lines_returns_five() {
        // 4 newlines → 5 lines
        assertEquals(5, OverviewHelpers.countLines("a\nb\nc\nd\ne"));
    }

    // ─── Anti-instantiation guard (private constructor) ───────────────

    @Test
    void overviewHelpers_constructor_is_private_via_reflection() throws Exception {
        java.lang.reflect.Constructor<OverviewHelpers> ctor =
                OverviewHelpers.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertTrue((ctor.getModifiers() & java.lang.reflect.Modifier.PRIVATE) != 0,
                "Constructor must be private to prevent instantiation");
        // Calling the private constructor is safe (no Engine-side-effects)
        ctor.newInstance();
    }

}
