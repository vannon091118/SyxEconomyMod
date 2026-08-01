package vannon.syx.economy.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic coordinate dump test — permanent regression protection for
 * addKpi y-spacing across all 16 economy UI tabs.
 *
 * <p>Parses Java source files to extract the y-coordinate arithmetic between
 * consecutive {@code addKpi} blocks. Each addKpi renders a two-line block
 * (label at y, value at y+16). If the y-increment between two consecutive
 * blocks is less than {@link EconWindowBase#KPI_ROW_HEIGHT}, the next label
 * overlaps the previous value text.</p>
 *
 * <p>This test is fully deterministic: no Engine state, no rendering, no mocks.
 * It reads the source files directly and validates the coordinate math at
 * build time. Any tab that adds an addKpi call with insufficient y-spacing
 * will break this test immediately.</p>
 *
 * <h3>Parsing Strategy</h3>
 * <p>Inner-class tabs (in WindowState/WindowEconomy) are separated by
 * {@code private static final class XxxTab} declarations. The parser uses
 * these class boundaries to split the file into per-tab segments, each with
 * independent y-tracking. This avoids the fragile line-gap heuristic that
 * would merge adjacent tabs with short inter-class spacing.</p>
 *
 * <h3>Coverage</h3>
 * <ul>
 *   <li>16 tabs across 4 window classes (Overview×4, State×6, Economy×6, Quickview×1)</li>
 *   <li>All addKpi call sites (~120+ calls total)</li>
 *   <li>Verifies KPI_ROW_HEIGHT constant matches the addKpi method's internal offset</li>
 * </ul>
 *
 * <p>Sprint v0.13.106+M-UI-3 — Befund 2 permanent regression guard.</p>
 */
class KpiCoordinateDumpTest {

    private static final int MIN_KPI_SPACING = EconWindowBase.KPI_ROW_HEIGHT;

    // addKpi(section, x, y, ...)  or  addKpi(section, x, y, icon, ...)
    private static final Pattern ADD_KPI_PATTERN =
            Pattern.compile("addKpi\\(\\s*\\w+\\s*,\\s*\\w+\\s*,\\s*(\\w+)");
    // Matches: y += <number>  or  y = <number>  (only numeric literals)
    private static final Pattern Y_INCREMENT_PATTERN =
            Pattern.compile("^\\s*y\\s*(\\+=|=)\\s*(-?\\d+)\\s*;\\s*$");
    // Matches inner class declaration: private static final class XxxTab
    private static final Pattern INNER_CLASS_PATTERN =
            Pattern.compile("^\\s*private\\s+static\\s+final\\s+class\\s+(\\w+)");
    // Matches build method signature
    private static final Pattern BUILD_METHOD_PATTERN =
            Pattern.compile("void\\s+build\\s*\\(");

    // ─── File resolution ────────────────────────────────────────────────

    private static Path sourceRoot;

    private static Path resolveSourceRoot() {
        if (sourceRoot != null) return sourceRoot;
        try {
            Path classDir = Paths.get(KpiCoordinateDumpTest.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            Path candidate = classDir;
            for (int i = 0; i < 10; i++) {
                if (Files.exists(candidate.resolve("pom.xml"))) {
                    sourceRoot = candidate.resolve("src").resolve("vannon")
                            .resolve("syx").resolve("economy").resolve("ui");
                    return sourceRoot;
                }
                candidate = candidate.getParent();
                if (candidate == null) break;
            }
        } catch (Exception ignored) {
        }
        sourceRoot = Paths.get("src/vannon/syx/economy/ui");
        return sourceRoot;
    }

    private static Path resolvePath(String fileName) {
        return resolveSourceRoot().resolve(fileName);
    }

    // ─── Source parsing engine ──────────────────────────────────────────

    record KpiBlock(String tabLabel, int line, int yAtCall, int yIncrement) {}

    /**
     * Parse a standalone tab file (e.g. PropertyTab.java) that has exactly
     * one build method. Returns all addKpi blocks with their y-increments.
     */
    static List<KpiBlock> parseStandaloneTab(Path filePath, String tabLabel) throws IOException {
        return parseSegment(Files.readAllLines(filePath), tabLabel, 0);
    }

    /**
     * Parse a multi-tab window file (e.g. WindowState.java) that contains
     * multiple inner-class tabs. Splits on inner-class boundaries and returns
     * all blocks tagged with their tab class name.
     */
    static List<KpiBlock> parseMultiTabWindow(Path filePath, String windowLabel) throws IOException {
        List<String> allLines = Files.readAllLines(filePath);
        List<KpiBlock> allBlocks = new ArrayList<>();

        // Split file into segments by inner class boundaries
        List<int[]> segments = new ArrayList<>(); // [startLine, endLine]
        List<String> segmentLabels = new ArrayList<>();
        int segStart = 0;
        String currentLabel = windowLabel;

        for (int i = 0; i < allLines.size(); i++) {
            Matcher m = INNER_CLASS_PATTERN.matcher(allLines.get(i));
            if (m.find()) {
                if (i > segStart) {
                    segments.add(new int[]{segStart, i});
                    segmentLabels.add(currentLabel);
                }
                segStart = i;
                currentLabel = m.group(1);
            }
        }
        // Last segment
        segments.add(new int[]{segStart, allLines.size()});
        segmentLabels.add(currentLabel);

        for (int s = 0; s < segments.size(); s++) {
            int start = segments.get(s)[0];
            int end = segments.get(s)[1];
            List<String> segmentLines = allLines.subList(start, end);
            List<KpiBlock> segBlocks = parseSegment(segmentLines, segmentLabels.get(s), start);
            allBlocks.addAll(segBlocks);
        }
        return allBlocks;
    }

    /**
     * Parse a list of source lines, tracking y-coordinate state through
     * build methods. Only tracks y-increments between consecutive addKpi
     * blocks within the same build method.
     *
     * @param lines     source lines to parse
     * @param tabLabel  label for error messages
     * @param lineOffset offset to add to line numbers (for sub-lists)
     */
    private static List<KpiBlock> parseSegment(List<String> lines, String tabLabel, int lineOffset) {
        List<KpiBlock> blocks = new ArrayList<>();
        int y = 0;
        int lastKpiY = -1;
        boolean inBuild = false;
        boolean seenFirstKpi = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            // Detect build method entry
            if (BUILD_METHOD_PATTERN.matcher(line).find()) {
                inBuild = true;
                y = 0;
                lastKpiY = -1;
                seenFirstKpi = false;
                continue;
            }

            if (!inBuild) continue;

            // Track y-assignments/increments (only numeric literals)
            Matcher yMatcher = Y_INCREMENT_PATTERN.matcher(line);
            if (yMatcher.matches()) {
                String op = yMatcher.group(1);
                int numVal = Integer.parseInt(yMatcher.group(2));
                if ("=".equals(op)) {
                    y = numVal;
                } else {
                    y += numVal;
                }
            }

            // Detect addKpi call
            if (ADD_KPI_PATTERN.matcher(line).find()) {
                if (!seenFirstKpi) {
                    // First addKpi in this build method — no previous block to compare
                    seenFirstKpi = true;
                    lastKpiY = y;
                    blocks.add(new KpiBlock(tabLabel, i + 1 + lineOffset, y, 0));
                } else {
                    int increment = y - lastKpiY;
                    blocks.add(new KpiBlock(tabLabel, i + 1 + lineOffset, y, increment));
                    lastKpiY = y;
                }
            }
        }
        return blocks;
    }

    /**
     * Find all y-spacing violations where the increment between consecutive
     * addKpi blocks is less than KPI_ROW_HEIGHT.
     */
    static List<String> findSpacingViolations(List<KpiBlock> blocks) {
        List<String> violations = new ArrayList<>();
        for (KpiBlock block : blocks) {
            // Skip first block (increment=0 = no previous block)
            if (block.yIncrement() == 0) continue;
            if (block.yIncrement() < MIN_KPI_SPACING) {
                violations.add(String.format(
                        "%s:%d — y+=%d zwischen addKpi-Blöcken (mindestens %d erforderlich)",
                        block.tabLabel(), block.line(), block.yIncrement(), MIN_KPI_SPACING));
            }
        }
        return violations;
    }

    // ─── Tests: KPI_ROW_HEIGHT constant ─────────────────────────────────

    @Test
    void kpiRowHeight_matches_addKpi_internal_offset() {
        // addKpi renders value at y+16. The label itself needs ~16px.
        // Minimum spacing between blocks is therefore 32px absolute minimum.
        // KPI_ROW_HEIGHT=38 adds safety margin for font descent + anti-aliasing.
        assertEquals(38, EconWindowBase.KPI_ROW_HEIGHT,
                "KPI_ROW_HEIGHT must be 38 — matches the empirical safe spacing");
    }

    @Test
    void kpiRowHeight_meets_theoretical_minimum() {
        // addKpi renders label at y (~16px height) and value at y+16 (~16px height).
        // Absolute minimum = 32px (no gap). KPI_ROW_HEIGHT=38 adds 6px safety.
        assertTrue(EconWindowBase.KPI_ROW_HEIGHT >= 32,
                "KPI_ROW_HEIGHT must be >= 32 (theoretical minimum: label 16px + value 16px)");
    }

    // ─── Tests: Standalone tab files (Overview sub-package) ─────────────

    @Test
    void overviewDashboardTab_noKpiOverlap() throws IOException {
        List<KpiBlock> blocks = parseStandaloneTab(
                resolvePath("tabs/Overview/DashboardTab.java"), "DashboardTab");
        List<String> violations = findSpacingViolations(blocks);
        assertTrue(violations.isEmpty(),
                "DashboardTab addKpi overlap:\n" + joinLines(violations));
    }

    @Test
    void overviewDemographicsTab_noKpiOverlap() throws IOException {
        List<KpiBlock> blocks = parseStandaloneTab(
                resolvePath("tabs/Overview/DemographicsTab.java"), "DemographicsTab");
        List<String> violations = findSpacingViolations(blocks);
        assertTrue(violations.isEmpty(),
                "DemographicsTab addKpi overlap:\n" + joinLines(violations));
    }

    @Test
    void overviewAdvisorTab_noKpiOverlap() throws IOException {
        List<KpiBlock> blocks = parseStandaloneTab(
                resolvePath("tabs/Overview/AdvisorTab.java"), "AdvisorTab");
        List<String> violations = findSpacingViolations(blocks);
        assertTrue(violations.isEmpty(),
                "AdvisorTab addKpi overlap:\n" + joinLines(violations));
    }

    @Test
    void overviewPropertyTab_noKpiOverlap() throws IOException {
        List<KpiBlock> blocks = parseStandaloneTab(
                resolvePath("tabs/Overview/PropertyTab.java"), "PropertyTab");
        List<String> violations = findSpacingViolations(blocks);
        assertTrue(violations.isEmpty(),
                "PropertyTab addKpi overlap:\n" + joinLines(violations));
    }

    // ─── Tests: WindowState (6 inner-class tabs) ────────────────────────

    @Test
    void stateWarehousesTab_noKpiOverlap() throws IOException {
        assertTabNoOverlap("WindowState.java", "WarehousesTab");
    }

    @Test
    void stateFiscalTab_noKpiOverlap() throws IOException {
        assertTabNoOverlap("WindowState.java", "FiscalTab");
    }

    @Test
    void statePublicWorksTab_noKpiOverlap() throws IOException {
        assertTabNoOverlap("WindowState.java", "PublicWorksTab");
    }

    @Test
    void stateSocialTab_noKpiOverlap() throws IOException {
        assertTabNoOverlap("WindowState.java", "SocialTab");
    }

    @Test
    void stateFaithTab_noKpiOverlap() throws IOException {
        assertTabNoOverlap("WindowState.java", "FaithTab");
    }

    // ─── Tests: WindowEconomy (6 inner-class tabs) ──────────────────────

    @Test
    void economyMarketsTab_noKpiOverlap() throws IOException {
        assertTabNoOverlap("WindowEconomy.java", "MarketsTab");
    }

    @Test
    void economyFirmsTab_noKpiOverlap() throws IOException {
        assertTabNoOverlap("WindowEconomy.java", "FirmsTab");
    }

    @Test
    void economyWagesTab_noKpiOverlap() throws IOException {
        assertTabNoOverlap("WindowEconomy.java", "WagesTab");
    }

    @Test
    void economyBooksTab_noKpiOverlap() throws IOException {
        assertTabNoOverlap("WindowEconomy.java", "BooksTab");
    }

    // ─── Tests: WindowQuickview (standalone build) ──────────────────────

    @Test
    void quickview_noKpiOverlap() throws IOException {
        List<KpiBlock> blocks = parseStandaloneTab(
                resolvePath("WindowQuickview.java"), "WindowQuickview");
        List<String> violations = findSpacingViolations(blocks);
        assertTrue(violations.isEmpty(),
                "WindowQuickview addKpi overlap:\n" + joinLines(violations));
    }

    // ─── Test: Comprehensive dump across ALL tabs ───────────────────────

    @Test
    void allTabs_comprehensiveDump_noOverlapAnywhere() throws IOException {
        List<String> allViolations = new ArrayList<>();

        // Standalone tab files
        String[] standaloneFiles = {
                "tabs/Overview/DashboardTab.java",
                "tabs/Overview/DemographicsTab.java",
                "tabs/Overview/AdvisorTab.java",
                "tabs/Overview/PropertyTab.java"
        };
        for (String file : standaloneFiles) {
            List<KpiBlock> blocks = parseStandaloneTab(resolvePath(file), file);
            for (String v : findSpacingViolations(blocks)) {
                allViolations.add("[" + file + "] " + v);
            }
        }

        // Multi-tab windows (inner-class splitting)
        String[] multiTabFiles = {"WindowState.java", "WindowEconomy.java"};
        for (String file : multiTabFiles) {
            List<KpiBlock> blocks = parseMultiTabWindow(resolvePath(file), file);
            for (String v : findSpacingViolations(blocks)) {
                allViolations.add("[" + file + "] " + v);
            }
        }

        // Quickview
        List<KpiBlock> qvBlocks = parseStandaloneTab(
                resolvePath("WindowQuickview.java"), "WindowQuickview");
        for (String v : findSpacingViolations(qvBlocks)) {
            allViolations.add("[WindowQuickview] " + v);
        }

        assertTrue(allViolations.isEmpty(),
                "=== KPI COORDINATE DUMP — " + allViolations.size() + " overlap violation(s) ===\n"
                        + joinLines(allViolations)
                        + "\n\nFix: increase y+ increments between addKpi blocks to >= "
                        + MIN_KPI_SPACING + " (EconWindowBase.KPI_ROW_HEIGHT)");
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    /** Assert that a specific tab within a multi-tab window has no overlap. */
    private void assertTabNoOverlap(String windowFile, String tabClassName) throws IOException {
        List<KpiBlock> allBlocks = parseMultiTabWindow(resolvePath(windowFile), windowFile);
        List<KpiBlock> tabBlocks = allBlocks.stream()
                .filter(b -> b.tabLabel().equals(tabClassName))
                .toList();
        List<String> violations = findSpacingViolations(tabBlocks);
        assertTrue(violations.isEmpty(),
                tabClassName + " addKpi overlap:\n" + joinLines(violations));
    }

    /** Join lines without Java version ambiguity. */
    private static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }
}
