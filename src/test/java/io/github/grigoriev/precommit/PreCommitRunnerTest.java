package io.github.grigoriev.precommit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PreCommitRunner that require Unix shell commands.
 * Result class tests are in PreCommitRunnerResultTest.
 */
@DisabledOnOs(OS.WINDOWS)
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
    @DisabledOnOs(OS.WINDOWS)
    void isPreCommitInstalled_shouldReturnFalseForInvalidExecutable() {
        assertThat(runner.isPreCommitInstalled("non-existent-command-12345")).isFalse();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void isPreCommitInstalled_shouldReturnTrueForValidCommand() {
        // 'true' command exists on Unix and always returns 0
        assertThat(runner.isPreCommitInstalled("true")).isTrue();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void isPreCommitInstalled_shouldReturnFalseForCommandWithNonZeroExit() {
        // 'false' command exists on Unix and always returns 1
        assertThat(runner.isPreCommitInstalled("false")).isFalse();
    }

    // runHook tests

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runHook_shouldReturnResultWithOutput() {
        // Use 'echo' as a fake pre-commit that outputs something
        PreCommitRunner.Result result = runner.runHook("echo", "test-hook", List.of(), tempDir.toFile());

        assertThat(result.getExitCode()).isZero();
        assertThat(result.getOutput()).contains("run");
        assertThat(result.getOutput()).contains("test-hook");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runHook_shouldPassFilesToCommand() throws IOException {
        File testFile = createTestFile("test.txt");

        PreCommitRunner.Result result = runner.runHook("echo", "hook-id", List.of(testFile), tempDir.toFile());

        assertThat(result.getExitCode()).isZero();
        assertThat(result.getOutput()).contains("--files");
        assertThat(result.getOutput()).contains(testFile.getAbsolutePath());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runHook_shouldHandleEmptyFilesList() {
        PreCommitRunner.Result result = runner.runHook("echo", "hook-id", List.of(), tempDir.toFile());

        assertThat(result.getExitCode()).isZero();
        assertThat(result.getOutput()).doesNotContain("--files");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runHook_shouldHandleNullFilesList() {
        PreCommitRunner.Result result = runner.runHook("echo", "hook-id", null, tempDir.toFile());

        assertThat(result.getExitCode()).isZero();
        assertThat(result.getOutput()).doesNotContain("--files");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runHook_shouldReturnNonZeroExitCode() {
        // 'false' always returns exit code 1
        PreCommitRunner.Result result = runner.runHook("false", "hook-id", List.of(), tempDir.toFile());

        assertThat(result.getExitCode()).isNotZero();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runHook_shouldReturnErrorForInvalidCommand() {
        PreCommitRunner.Result result = runner.runHook("non-existent-command-xyz", "hook", List.of(), tempDir.toFile());

        assertThat(result.getExitCode()).isEqualTo(-1);
        assertThat(result.getOutput()).contains("Failed to execute pre-commit");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runHook_shouldHandleMultipleFiles() throws IOException {
        File file1 = createTestFile("file1.txt");
        File file2 = createTestFile("file2.txt");

        PreCommitRunner.Result result = runner.runHook("echo", "hook", List.of(file1, file2), tempDir.toFile());

        assertThat(result.getExitCode()).isZero();
        assertThat(result.getOutput()).contains(file1.getAbsolutePath());
        assertThat(result.getOutput()).contains(file2.getAbsolutePath());
    }

    // Environment variables tests

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runHook_shouldPassEnvironmentVariables() throws IOException {
        // Create a script that prints the environment variable
        Path script = tempDir.resolve("echo_env.sh");
        Files.writeString(script, "#!/bin/bash\necho \"VAR=$MY_TEST_VAR\"\n");
        script.toFile().setExecutable(true);

        Map<String, String> envVars = Map.of("MY_TEST_VAR", "test_value_123");

        PreCommitRunner.Result result = runner.runHook(script.toString(), "hook-id", List.of(), tempDir.toFile(), envVars);

        assertThat(result.getExitCode()).isZero();
        assertThat(result.getOutput()).contains("VAR=test_value_123");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runHook_shouldWorkWithEmptyEnvironmentVariables() {
        Map<String, String> emptyEnvVars = Map.of();

        PreCommitRunner.Result result = runner.runHook("echo", "hook-id", List.of(), tempDir.toFile(), emptyEnvVars);

        assertThat(result.getExitCode()).isZero();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runHook_shouldWorkWithNullEnvironmentVariables() {
        PreCommitRunner.Result result = runner.runHook("echo", "hook-id", List.of(), tempDir.toFile(), null);

        assertThat(result.getExitCode()).isZero();
    }

    // Timeout tests

    @Test
    @DisabledOnOs(OS.WINDOWS)
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
    @DisabledOnOs(OS.WINDOWS)
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
    @DisabledOnOs(OS.WINDOWS)
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
    @DisabledOnOs(OS.WINDOWS)
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

    private File createTestFile(String name) throws IOException {
        Path filePath = tempDir.resolve(name);
        Files.writeString(filePath, "test content");
        return filePath.toFile();
    }

    // Concurrent execution tests

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runHook_shouldBeThreadSafe() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<PreCommitRunner.Result>> futures = new ArrayList<>();

        // Submit tasks that all start at the same time
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                startLatch.await(); // Wait for all threads to be ready
                return runner.runHook("echo", "hook-" + index, List.of(), tempDir.toFile());
            }));
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Collect results
        List<PreCommitRunner.Result> results = new ArrayList<>();
        for (Future<PreCommitRunner.Result> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // All executions should succeed
        assertThat(results).hasSize(threadCount);
        for (int i = 0; i < threadCount; i++) {
            assertThat(results.get(i).getExitCode()).isZero();
            assertThat(results.get(i).getOutput()).contains("hook-" + i);
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void isPreCommitInstalled_shouldBeThreadSafe() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return runner.isPreCommitInstalled("non-existent-command-xyz");
            }));
        }

        startLatch.countDown();

        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // All should return false (command doesn't exist)
        assertThat(results).hasSize(threadCount).containsOnly(false);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void multipleRunnerInstances_shouldWorkConcurrently() throws Exception {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<PreCommitRunner.Result>> futures = new ArrayList<>();

        // Each thread uses its own PreCommitRunner instance
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                PreCommitRunner localRunner = new PreCommitRunner();
                startLatch.await();
                return localRunner.runHook("echo", "instance-" + index, List.of(), tempDir.toFile());
            }));
        }

        startLatch.countDown();

        List<PreCommitRunner.Result> results = new ArrayList<>();
        for (Future<PreCommitRunner.Result> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(results).hasSize(threadCount);
        for (int i = 0; i < threadCount; i++) {
            assertThat(results.get(i).getExitCode()).isZero();
            assertThat(results.get(i).getOutput()).contains("instance-" + i);
        }
    }
}
