package io.github.grigoriev.precommit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PreCommitRunner that use Windows-specific commands.
 * These tests only run on Windows.
 */
@EnabledOnOs(OS.WINDOWS)
class PreCommitRunnerWindowsTest {

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
        // 'where' command exists on Windows and returns 0 when finding itself
        assertThat(runner.isPreCommitInstalled("where")).isTrue();
    }

    // runHook tests

    @Test
    void runHook_shouldReturnResultWithOutput() {
        // Use 'cmd /c echo' as a fake pre-commit that outputs something
        PreCommitRunner.Result result = runner.runHook("cmd", "/c echo test-hook", List.of(), tempDir.toFile());

        assertThat(result.getExitCode()).isZero();
    }

    @Test
    void runHook_shouldReturnErrorForInvalidCommand() {
        PreCommitRunner.Result result = runner.runHook("non-existent-command-xyz", "hook", List.of(), tempDir.toFile());

        assertThat(result.getExitCode()).isEqualTo(-1);
        assertThat(result.getOutput()).contains("Failed to execute pre-commit");
    }

    // Timeout tests

    @Test
    void isPreCommitInstalled_shouldReturnFalseOnTimeout() throws IOException {
        // Create a batch script that sleeps using ping
        Path script = tempDir.resolve("slow_version.bat");
        Files.writeString(script, "@echo off\nping -n 11 127.0.0.1 > nul\n");

        PreCommitRunner shortTimeoutRunner = new PreCommitRunner(1, 1);

        boolean result = shortTimeoutRunner.isPreCommitInstalled(script.toString());

        assertThat(result).isFalse();
    }

    @Test
    void runHook_shouldReturnTimeoutResultOnTimeout() throws IOException {
        // Create a batch script that sleeps using ping
        Path script = tempDir.resolve("slow_hook.bat");
        Files.writeString(script, "@echo off\nping -n 11 127.0.0.1 > nul\n");

        PreCommitRunner shortTimeoutRunner = new PreCommitRunner(1, 1);

        PreCommitRunner.Result result = shortTimeoutRunner.runHook(script.toString(), "hook", List.of(), tempDir.toFile());

        assertThat(result.getExitCode()).isEqualTo(-1);
        assertThat(result.getOutput()).contains("timed out");
    }

    // InterruptedException tests

    @Test
    void isPreCommitInstalled_shouldHandleInterruption() throws Exception {
        Thread testThread = new Thread(() -> {
            Thread.currentThread().interrupt();
            boolean result = runner.isPreCommitInstalled("ping");
            assertThat(result).isFalse();
        });
        testThread.start();
        testThread.join(5000);
    }

    @Test
    void runHook_shouldHandleInterruption() throws Exception {
        Thread testThread = new Thread(() -> {
            Thread.currentThread().interrupt();
            PreCommitRunner.Result result = runner.runHook("ping", "-n 10 127.0.0.1", List.of(), tempDir.toFile());
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
    void runHook_shouldBeThreadSafe() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<PreCommitRunner.Result>> futures = new ArrayList<>();

        // Submit tasks that all start at the same time
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                startLatch.await();
                return runner.runHook("cmd", "/c echo hook-" + index, List.of(), tempDir.toFile());
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
        for (PreCommitRunner.Result result : results) {
            assertThat(result.getExitCode()).isZero();
        }
    }

    @Test
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
                return localRunner.runHook("cmd", "/c echo instance-" + index, List.of(), tempDir.toFile());
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
        for (PreCommitRunner.Result result : results) {
            assertThat(result.getExitCode()).isZero();
        }
    }
}
