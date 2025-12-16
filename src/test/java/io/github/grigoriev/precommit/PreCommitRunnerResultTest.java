package io.github.grigoriev.precommit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the PreCommitRunner.Result record.
 * These are pure Java tests that work on all platforms.
 */
class PreCommitRunnerResultTest {

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
}
