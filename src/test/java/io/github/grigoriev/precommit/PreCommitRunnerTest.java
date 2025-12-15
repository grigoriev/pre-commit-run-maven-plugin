package io.github.grigoriev.precommit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreCommitRunnerTest {

    private PreCommitRunner runner;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        runner = new PreCommitRunner();
    }

    // isPreCommitInstalled tests

    @Test
    void isPreCommitInstalled_shouldReturnFalseForInvalidExecutable() {
        assertThat(runner.isPreCommitInstalled("non-existent-command-12345")).isFalse();
    }

    @Test
    void isPreCommitInstalled_shouldReturnTrueForValidCommand() {
        // 'true' command exists on Unix and always returns 0
        assertThat(runner.isPreCommitInstalled("true")).isTrue();
    }

    @Test
    void isPreCommitInstalled_shouldReturnFalseForCommandWithNonZeroExit() {
        // 'false' command exists on Unix and always returns 1
        assertThat(runner.isPreCommitInstalled("false")).isFalse();
    }

    // runHook tests

    @Test
    void runHook_shouldReturnResultWithOutput() {
        // Use 'echo' as a fake pre-commit that outputs something
        PreCommitRunner.Result result = runner.runHook("echo", "test-hook", List.of(), tempDir.toFile());

        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getOutput()).contains("run");
        assertThat(result.getOutput()).contains("test-hook");
    }

    @Test
    void runHook_shouldPassFilesToCommand() throws IOException {
        File testFile = createTestFile("test.txt");

        PreCommitRunner.Result result = runner.runHook("echo", "hook-id", List.of(testFile), tempDir.toFile());

        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getOutput()).contains("--files");
        assertThat(result.getOutput()).contains(testFile.getAbsolutePath());
    }

    @Test
    void runHook_shouldHandleEmptyFilesList() {
        PreCommitRunner.Result result = runner.runHook("echo", "hook-id", List.of(), tempDir.toFile());

        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getOutput()).doesNotContain("--files");
    }

    @Test
    void runHook_shouldHandleNullFilesList() {
        PreCommitRunner.Result result = runner.runHook("echo", "hook-id", null, tempDir.toFile());

        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getOutput()).doesNotContain("--files");
    }

    @Test
    void runHook_shouldReturnNonZeroExitCode() {
        // 'false' always returns exit code 1
        PreCommitRunner.Result result = runner.runHook("false", "hook-id", List.of(), tempDir.toFile());

        assertThat(result.getExitCode()).isNotEqualTo(0);
    }

    @Test
    void runHook_shouldReturnErrorForInvalidCommand() {
        PreCommitRunner.Result result = runner.runHook("non-existent-command-xyz", "hook", List.of(), tempDir.toFile());

        assertThat(result.getExitCode()).isEqualTo(-1);
        assertThat(result.getOutput()).contains("Failed to execute pre-commit");
    }

    @Test
    void runHook_shouldHandleMultipleFiles() throws IOException {
        File file1 = createTestFile("file1.txt");
        File file2 = createTestFile("file2.txt");

        PreCommitRunner.Result result = runner.runHook("echo", "hook", List.of(file1, file2), tempDir.toFile());

        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getOutput()).contains(file1.getAbsolutePath());
        assertThat(result.getOutput()).contains(file2.getAbsolutePath());
    }

    // Result class tests

    @Test
    void result_isPassed_shouldReturnTrueForExitCode0() {
        PreCommitRunner.Result result = new PreCommitRunner.Result(0, "output");

        assertThat(result.isPassed()).isTrue();
        assertThat(result.isModified()).isFalse();
        assertThat(result.isFailed()).isFalse();
    }

    @Test
    void result_isModified_shouldReturnTrueForExitCode1() {
        PreCommitRunner.Result result = new PreCommitRunner.Result(1, "output");

        assertThat(result.isPassed()).isFalse();
        assertThat(result.isModified()).isTrue();
        assertThat(result.isFailed()).isFalse();
    }

    @Test
    void result_isFailed_shouldReturnTrueForExitCodeGreaterThan1() {
        PreCommitRunner.Result result = new PreCommitRunner.Result(2, "output");

        assertThat(result.isPassed()).isFalse();
        assertThat(result.isModified()).isFalse();
        assertThat(result.isFailed()).isTrue();
    }

    @Test
    void result_isFailed_shouldReturnTrueForNegativeExitCode() {
        PreCommitRunner.Result result = new PreCommitRunner.Result(-1, "error");

        assertThat(result.isPassed()).isFalse();
        assertThat(result.isModified()).isFalse();
        assertThat(result.isFailed()).isTrue();
    }

    @Test
    void result_shouldStoreExitCodeAndOutput() {
        PreCommitRunner.Result result = new PreCommitRunner.Result(42, "test output");

        assertThat(result.getExitCode()).isEqualTo(42);
        assertThat(result.getOutput()).isEqualTo("test output");
    }

    // Timeout tests

    @Test
    void isPreCommitInstalled_shouldReturnFalseOnTimeout() throws IOException {
        // Create a script that ignores arguments and sleeps
        Path script = tempDir.resolve("slow_version.sh");
        Files.writeString(script, "#!/bin/bash\nsleep 10\n");
        script.toFile().setExecutable(true);

        PreCommitRunner shortTimeoutRunner = new PreCommitRunner(1, 0);

        boolean result = shortTimeoutRunner.isPreCommitInstalled(script.toString());

        assertThat(result).isFalse();
    }

    @Test
    void runHook_shouldReturnTimeoutResultOnTimeout() throws IOException {
        // Create a script that sleeps
        Path script = tempDir.resolve("slow_hook.sh");
        Files.writeString(script, "#!/bin/bash\nsleep 10\n");
        script.toFile().setExecutable(true);

        PreCommitRunner shortTimeoutRunner = new PreCommitRunner(0, 1);

        PreCommitRunner.Result result = shortTimeoutRunner.runHook(script.toString(), "hook", List.of(), tempDir.toFile());

        assertThat(result.getExitCode()).isEqualTo(-1);
        assertThat(result.getOutput()).contains("timed out");
    }

    // InterruptedException tests

    @Test
    void isPreCommitInstalled_shouldHandleInterruption() throws Exception {
        Thread testThread = new Thread(() -> {
            Thread.currentThread().interrupt();
            boolean result = runner.isPreCommitInstalled("sleep");
            assertThat(result).isFalse();
        });
        testThread.start();
        testThread.join(5000);
    }

    @Test
    void runHook_shouldHandleInterruption() throws Exception {
        Thread testThread = new Thread(() -> {
            Thread.currentThread().interrupt();
            PreCommitRunner.Result result = runner.runHook("sleep", "10", List.of(), tempDir.toFile());
            assertThat(result.getExitCode()).isEqualTo(-1);
            assertThat(result.getOutput()).contains("interrupted");
        });
        testThread.start();
        testThread.join(5000);
    }

    // IOException in output reader test

    @Test
    void readProcessOutput_shouldHandleIOException() {
        InputStream failingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Test IO error");
            }
        };
        StringBuilder output = new StringBuilder();

        runner.readProcessOutput(failingStream, output);

        assertThat(output.toString()).contains("Error reading output");
        assertThat(output.toString()).contains("Test IO error");
    }

    private File createTestFile(String name) throws IOException {
        Path filePath = tempDir.resolve(name);
        Files.writeString(filePath, "test content");
        return filePath.toFile();
    }
}
