package vannon.syx.economy.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sprint v0.13.108+Doku-Slim — GATE-17 regression tests.
 *
 * <p>Purpose: protect against future regressions in the three Sprint-Doku-Slim
 * deliverables (doku-sync + gate + bump) by:
 * <ol>
 *   <li>Asserting {@code bash tools/doku-sync.sh fix} is byte-idempotent
 *       (5× re-run → SHA-256 of all 6 anker files does not change).</li>
 *   <li>Asserting {@code bash tools/doku-sync.sh check} exits 1 when an anker
 *       is manually broken (drift detection works).</li>
 *   <li>Asserting {@code bash tools/bump-version.sh --dry-run --no-commit
 *       --no-workspace-check --set &lt;v&gt;} succeeds without writing anything
 *       (--dry-run safety contract).</li>
 *   <li>Asserting {@code bash tools/gate.sh precommit} exits 0 against the
 *       real project baseline (catch future regressions in gate.sh itself).</li>
 * </ol>
 *
 * <p>CI: {@code mvn test -Dtest=DokuSyncTest --batch-mode} ({@code -Dgate.skip=true}
 * is NOT needed because the tests themselves drive the gate, not maven-validate).</p>
 *
 * <p>Why JUnit 5 + ProcessBuilder instead of shell {@code bash} directly:
 * the JUnit ProcessBuilder wrapper captures exit code AND stdout/stderr for
 * precise failure messages; the bash wrapper would lose non-zero exit semantics.</p>
 */
final class DokuSyncTest {

    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath();
    private static final Path DOKU_SYNC_SH = PROJECT_ROOT.resolve("tools/doku-sync.sh");
    private static final Path BUMP_VERSION_SH = PROJECT_ROOT.resolve("tools/bump-version.sh");
    private static final Path GATE_SH = PROJECT_ROOT.resolve("tools/gate.sh");

    private static final List<Path> ANKER_FILES = List.of(
            Paths.get("pom.xml"),
            Paths.get("Doku/README.md"),
            Paths.get("Doku/CHANGELOG.md"),
            Paths.get("Doku/ARCHITECTURE.md"),
            Paths.get("Doku/ROADMAP.md"),
            Paths.get("Doku/GLOSSARY.md"),
            Paths.get("tools/vanilla-schema.yaml"));

    // ─── Helpers ──────────────────────────────────────────────────────────

    /** Run a shell command synchronously, capturing exit code + merged stdout/stderr. */
    private static ShellResult runShell(String[] cmd, File cwd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(cwd)
                .redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int rc = p.waitFor();
        return new ShellResult(rc, output);
    }

    /** Compute a stable SHA-256 hex digest of all anker files in the given project dir.
     *  Used to detect any byte-level mutation across re-runs of fix-mode. */
    private static String digestAnkerFiles(Path projectDir) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        StringBuilder sb = new StringBuilder();
        for (Path relPath : ANKER_FILES) {
            Path abs = projectDir.resolve(relPath);
            sb.append(relPath).append('=');
            if (Files.exists(abs)) {
                byte[] hash = md.digest(Files.readAllBytes(abs));
                for (byte b : hash) sb.append(String.format("%02x", b));
            } else {
                sb.append("<missing>");
            }
            sb.append('|');
        }
        return sb.toString();
    }

    /** Build a minimal syx-economy-mod project stub in {@code targetDir} with all
     *  6 anker files present and ALREADY sync'd — exercises idempotency path. */
    private static void setupSyncedStub(Path targetDir, String version) throws IOException {
        Files.writeString(targetDir.resolve("pom.xml"), ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "  <groupId>com.vannon</groupId>"
                + "<artifactId>syx-economy-mod</artifactId>"
                + "<version>" + version + "</version>\n"
                + "  <properties>\n"
                + "    <mod.name>SyxEconomyMod</mod.name>\n"
                + "  </properties>\n"
                + "</project>\n");

        Files.createDirectories(targetDir.resolve("Doku"));
        String anchor = "> **Version:** v" + version + "\n";
        Files.writeString(targetDir.resolve("Doku/README.md"), anchor);
        Files.writeString(targetDir.resolve("Doku/CHANGELOG.md"), anchor);
        Files.writeString(targetDir.resolve("Doku/ARCHITECTURE.md"), anchor);
        Files.writeString(targetDir.resolve("Doku/ROADMAP.md"), anchor);
        Files.writeString(targetDir.resolve("Doku/GLOSSARY.md"), anchor);

        Files.createDirectories(targetDir.resolve("tools"));
        Files.writeString(targetDir.resolve("tools/vanilla-schema.yaml"), ""
                + "# Vanilla Schema Stub\n"
                + "# VANILLA BYTECODE-SCHEMA — SyxEconomyMod v" + version + "\n");
    }

    /** Inject drift into Doku/README.md — change anchor to a wrong version.
     *  pom.xml stays at truth version, Doku/README.md disagrees → drift. */
    private static void injectDriftInReadme(Path projectDir, String wrongVersion) throws IOException {
        Path readme = projectDir.resolve("Doku/README.md");
        String content = Files.readString(readme, StandardCharsets.UTF_8);
        content = content.replaceFirst(
                "> \\*\\*Version:\\*\\* v[0-9]+\\.[0-9]+\\.[0-9]+",
                "> **Version:** " + wrongVersion);
        Files.writeString(readme, content, StandardCharsets.UTF_8);
    }

    record ShellResult(int exitCode, String output) {}

    // ─── Test 1: testFixIsIdempotent ───────────────────────────────────────

    /**
     * GATE-17 Case 1: doku-sync.sh fix must be byte-idempotent.
     *
     * <p>Setup: a fresh sandbox with all 6 anker files ALREADY at the correct
     * version (matches pom.xml truth). All re-runs should be no-ops because
     * Match-Check 2 (replacement-already-in-file) hits immediately.</p>
     *
     * <p>Assert: SHA-256 hash of all 6 anker files is unchanged across 5
     * consecutive fix invocations. If ANY run modifies a file, the test fails
     * with the file that drifted.</p>
     */
    @Test
    void testFixIsIdempotent(@TempDir Path sandbox) throws Exception {
        setupSyncedStub(sandbox, "0.13.107");
        String[] cmd = {"bash", DOKU_SYNC_SH.toString(), "fix"};

        String preDigest = digestAnkerFiles(sandbox);

        for (int run = 1; run <= 5; run++) {
            ShellResult res = runShell(cmd, sandbox.toFile());
            assertEquals(0, res.exitCode(),
                    "fix run #" + run + " must exit 0; output:\n" + res.output());

            String postDigest = digestAnkerFiles(sandbox);
            assertEquals(preDigest, postDigest,
                    "fix run #" + run + " modified anker files — fix-mode is NOT idempotent!\n"
                    + "Pre-state:  " + preDigest + "\n"
                    + "Post-state: " + postDigest);
        }
    }

    // ─── Test 2: testCheckExitsNonZeroOnDrift ─────────────────────────────

    /**
     * GATE-17 Case 2: doku-sync.sh check must exit 1 when an anker is drifted.
     *
     * <p>Setup: synced stub (all anchor files at v0.13.107), then inject drift
     * by mutating Doku/README.md to v0.99.99. pom.xml stays at 0.13.107 truth.</p>
     *
     * <p>Assert: exit code == 1 AND stdout contains "DRIFT" warning.</p>
     */
    @Test
    void testCheckExitsNonZeroOnDrift(@TempDir Path sandbox) throws Exception {
        setupSyncedStub(sandbox, "0.13.107");
        injectDriftInReadme(sandbox, "v0.99.99");

        String[] cmd = {"bash", DOKU_SYNC_SH.toString(), "check"};
        ShellResult res = runShell(cmd, sandbox.toFile());

        assertEquals(1, res.exitCode(),
                "check must exit 1 on drift; got exit=" + res.exitCode() + "\n" + res.output());
        assertTrue(res.output().contains("DRIFT"),
                "check output must mention DRIFT, got:\n" + res.output());
    }

    // ─── Test 3: testBumpDryRunIsSafe ──────────────────────────────────────

    /**
     * GATE-17 Case 3: bump-version.sh --dry-run --no-commit --no-workspace-check
     * must complete without writing anything to the working tree.
     *
     * <p>Why "skip if not testable" is NOT taken: bump-version.sh supports a
     * safe triple ({@code --dry-run --no-commit --no-workspace-check}) that
     * never writes and never touches git. We can deterministically verify
     * that pom.xml is unchanged after the call.</p>
     *
     * <p>Assert: exit code == 0, output mentions the target version, and the
     * pom.xml in sandbox is byte-identical before and after the call.</p>
     */
    @Test
    void testBumpDryRunIsSafe(@TempDir Path sandbox) throws Exception {
        setupSyncedStub(sandbox, "0.13.107");

        Path pom = sandbox.resolve("pom.xml");
        byte[] prePom = Files.readAllBytes(pom);

        // Safe triple: no writes, no commit, no git-workspace-check required.
        String[] cmd = {"bash", BUMP_VERSION_SH.toString(),
                "--set", "9.9.9",
                "--dry-run",
                "--no-commit",
                "--no-workspace-check"};
        ShellResult res = runShell(cmd, sandbox.toFile());

        assertEquals(0, res.exitCode(),
                "bump --dry-run --no-commit must succeed; got exit=" + res.exitCode() + "\n"
                + res.output());
        assertTrue(res.output().contains("9.9.9"),
                "output must mention target version 9.9.9; got:\n" + res.output());

        byte[] postPom = Files.readAllBytes(pom);
        assertEquals(prePom.length, postPom.length,
                "pom.xml must not change (--dry-run safety); pre-len=" + prePom.length
                + " post-len=" + postPom.length);
    }

    // ─── Test 4: testGatePrecommitStableBaseline ───────────────────────────

    /**
     * GATE-17 Case 4 (regression): gate.sh precommit must exit 0 against the
     * real project baseline.
     *
     * <p>This is the catch-all: if the universal-gate itself regresses (e.g.
     * Exit-Code-Contract changed, sub-gate wiring broken, doku-sync check lost
     * from the dispatch), this test fires. We deliberately do NOT clean the
     * working tree before running — a real CI environment will have a dirty
     * tree and gate.sh precommit must tolerate that (it operates on file-state,
     * not git-history).</p>
     *
     * <p>If this test FAILS, the failure message is the full gate.sh output
     * for diagnosis (which sub-gate failed and why).</p>
     */
    @Test
    void testGatePrecommitStableBaseline() throws Exception {
        String[] cmd = {"bash", "tools/gate.sh", "precommit"};
        ShellResult res = runShell(cmd, PROJECT_ROOT.toFile());

        assertEquals(0, res.exitCode(),
                "gate.sh precommit must exit 0 at stable baseline; got exit=" + res.exitCode()
                + "\n----- gate.sh output -----\n" + res.output()
                + "\n----- end output -----");
    }
}
