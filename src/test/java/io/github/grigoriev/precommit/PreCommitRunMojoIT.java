package io.github.grigoriev.precommit;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for PreCommitRunMojo that require real pre-commit installation.
 * These tests are skipped if pre-commit is not available.
 */
class PreCommitRunMojoIT {

    private static boolean preCommitAvailable;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void checkPreCommitInstalled() {
        PreCommitRunner checkRunner = new PreCommitRunner();
        preCommitAvailable = checkRunner.isPreCommitInstalled("pre-commit");
    }

    @BeforeEach
    void setUp() {
        assumeTrue(preCommitAvailable, "pre-commit is not installed, skipping integration tests");
    }

    @Test
    void execute_shouldUseLazyInitializedGlobExpander() throws Exception {
        // Setup git repo with pre-commit config
        initGitRepo();
        createPreCommitConfig();

        // Create files matching glob pattern
        createFile("src/File1.java", "// java file\n");
        createFile("src/File2.java", "// another java file\n");

        // Use default constructor which has lazy initialization for GlobExpander
        PreCommitRunMojo mojo = new PreCommitRunMojo();
        mojo.setLog(mock(Log.class));
        mojo.setBasedir(tempDir.toFile());
        mojo.setHooks(java.util.List.of("trailing-whitespace"));
        mojo.setPreCommitExecutable("pre-commit");
        mojo.setFiles(java.util.List.of("src/*.java")); // Glob pattern!
        mojo.setSkipIfNotInstalled(false); // Fail if not installed

        // Execute - this will trigger lazy initialization of GlobExpander
        mojo.execute();

        // If we get here without exception, the lazy init worked
        assertThat(mojo).isNotNull();
    }

    @Test
    void execute_shouldRunHookWithDefaultConstructor() throws Exception {
        // Setup git repo with pre-commit config
        initGitRepo();
        createPreCommitConfig();

        // Create a file with trailing whitespace
        createFile("test.txt", "hello world   \n");

        // Use default constructor
        PreCommitRunMojo mojo = new PreCommitRunMojo();
        mojo.setLog(mock(Log.class));
        mojo.setBasedir(tempDir.toFile());
        mojo.setHooks(java.util.List.of("trailing-whitespace"));
        mojo.setPreCommitExecutable("pre-commit");
        mojo.setFiles(java.util.List.of("test.txt"));
        mojo.setSkipIfNotInstalled(false); // Fail if not installed

        // Execute
        mojo.execute();

        // Verify file was modified (trailing whitespace removed)
        String content = Files.readString(tempDir.resolve("test.txt"));
        assertThat(content).doesNotContain("   \n");
    }

    private void initGitRepo() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("git", "init");
        pb.directory(tempDir.toFile());
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git init interrupted", e);
        }
    }

    private void createPreCommitConfig() throws IOException {
        String config = """
                repos:
                  - repo: https://github.com/pre-commit/pre-commit-hooks
                    rev: v5.0.0
                    hooks:
                      - id: trailing-whitespace
                """;
        Files.writeString(tempDir.resolve(".pre-commit-config.yaml"), config);

        // Install the hooks
        ProcessBuilder pb = new ProcessBuilder("pre-commit", "install-hooks");
        pb.directory(tempDir.toFile());
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("pre-commit install-hooks interrupted", e);
        }
    }

    private void createFile(String path, String content) throws IOException {
        Path filePath = tempDir.resolve(path);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}
