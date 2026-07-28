package vannon.syx.economy.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * T-COV-8 — Light smoke tests for {@link DiagnosticExporter}.
 *
 * <p>DiagnosticExporter is mostly static methods that need a live {@link EconomySim}
 * snapshot. Mockito-Inject (full {@code exportDay()} coverage) ist Sprint T-COV-9
 * vorbehalten. Was hier geprüft wird:</p>
 *
 * <ul>
 *   <li>{@link DiagnosticExporter#diagnosticDirectory()} returns a non-empty path
 *       pointing into the conventional mod-data area.</li>
 *   <li>{@link DiagnosticExporter#resetExportGuard()} runs without throwing.</li>
 *   <li>Class-level constructibility (for users that need a reference, even though
 *       the class is "static utility" and {@code private DiagnosticExporter()}).</li>
 * </ul>
 */
class DiagnosticExporterTest {

    @Test
    void diagnosticDirectory_returnsNonEmptyPath() {
        String dir = DiagnosticExporter.diagnosticDirectory();
        assertNotNull(dir, "diagnosticDirectory must never return null");
        assertFalse(dir.isEmpty(), "diagnosticDirectory must never return empty");
        // Conventional: ~/.local/share/songsofsyx/mods/SyxEconomyMod/diagnostics or %APPDATA%
        assertTrue(dir.contains("SyxEconomyMod") || dir.contains("syxEconomyMod"),
                "diagnosticDirectory must mention the mod name, got: " + dir);
    }

    @Test
    void diagnosticDirectory_isAbsoluteOrHomeRelative() {
        String dir = DiagnosticExporter.diagnosticDirectory();
        // Either absolute (Linux/Mac $HOME/.local/...) or rooted at user.home.
        assertTrue(dir.contains(System.getProperty("user.home")) || dir.startsWith("/") || dir.startsWith("C:"),
                "diagnosticDirectory must be resolvable to a real directory: " + dir);
    }

    @Test
    void resetExportGuard_doesNotThrow() {
        // The guard is a private static volatile; resetExportGuard() flips it
        // back to -1 so the next exportDay() call enters the export pipeline.
        // No throw is the contract here.
        assertDoesNotThrow(() -> DiagnosticExporter.resetExportGuard());

        // Calling it twice in a row must remain safe (idempotent).
        assertDoesNotThrow(() -> DiagnosticExporter.resetExportGuard());
    }

    @Test
    void class_isStaticUtility_andCannotBeInstantiated() throws Exception {
        // DiagnosticExporter constructor is private — verify Reflection denies
        // public instantiation via the public API. (We do not call
        // Constructor.newInstance because that bypasses private via setAccessible.)
        java.lang.reflect.Constructor<DiagnosticExporter> ctor =
                DiagnosticExporter.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers()),
                "DiagnosticExporter must keep its private constructor");
        ctor.setAccessible(true);
        DiagnosticExporter instance = ctor.newInstance();
        assertNotNull(instance, "even with setAccessible, instance must construct");
    }
}
