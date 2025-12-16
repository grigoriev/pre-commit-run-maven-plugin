package io.github.grigoriev.precommit;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreCommitRunMojoTest {

    @Mock
    private PreCommitConfigParser configParser;

    @Mock
    private PreCommitRunner runner;

    private PreCommitRunMojo mojo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mojo = new PreCommitRunMojo(configParser, runner);
        mojo.setBasedir(tempDir.toFile());
        mojo.setHooks(List.of("test-hook"));
        mojo.setPreCommitExecutable("pre-commit");
    }

    @Test
    void execute_shouldSkipWhenSkipIsTrue() throws MojoExecutionException, MojoFailureException {
        mojo.setSkip(true);

        mojo.execute();

        verify(runner, never()).isPreCommitInstalled(anyString());
        verify(runner, never()).runHook(anyString(), anyString(), anyList(), any(File.class), any());
    }

    @Test
    void execute_shouldSkipWhenPreCommitNotInstalled() throws Exception {
        mojo.setSkipIfNotInstalled(true);
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(false);

        mojo.execute();

        verify(runner, never()).runHook(anyString(), anyString(), anyList(), any(File.class), any());
    }

    @Test
    void execute_shouldFailWhenPreCommitNotInstalledAndNotSkipping() {
        mojo.setSkipIfNotInstalled(false);
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(false);

        assertThatThrownBy(() -> mojo.execute())
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("pre-commit is not installed");
    }

    @Test
    void execute_shouldSkipWhenConfigNotFound() throws Exception {
        mojo.setSkipIfConfigNotFound(true);
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);

        mojo.execute();

        verify(configParser, never()).isHookConfigured(any(File.class), anyString());
        verify(runner, never()).runHook(anyString(), anyString(), anyList(), any(File.class), any());
    }

    @Test
    void execute_shouldFailWhenConfigNotFoundAndNotSkipping() {
        mojo.setSkipIfConfigNotFound(false);
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);

        assertThatThrownBy(() -> mojo.execute())
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining(".pre-commit-config.yaml not found");
    }

    @Test
    void execute_shouldSkipWhenHookNotFound() throws Exception {
        createConfigFile();
        mojo.setSkipIfHookNotFound(true);
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(false);

        mojo.execute();

        verify(runner, never()).runHook(anyString(), anyString(), anyList(), any(File.class), any());
    }

    @Test
    void execute_shouldFailWhenHookNotFoundAndNotSkipping() throws Exception {
        createConfigFile();
        mojo.setSkipIfHookNotFound(false);
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(false);

        assertThatThrownBy(() -> mojo.execute())
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("not found in .pre-commit-config.yaml")
                .hasMessageContaining("test-hook");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Passed"})
    void execute_shouldRunHookSuccessfullyWithVariousOutputs(String output) throws Exception {
        createConfigFile();
        File targetFile = createTargetFile("test.json");
        mojo.setFiles(List.of("test.json"));
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), eq("test-hook"), anyList(), any(File.class), isNull()))
                .thenReturn(new PreCommitRunner.Result(0, output));

        mojo.execute();

        verify(runner).runHook(eq("pre-commit"), eq("test-hook"), eq(List.of(targetFile)), any(File.class), isNull());
    }

    @Test
    void execute_shouldHandleHookModification() throws Exception {
        createConfigFile();
        File targetFile = createTargetFile("test.json");
        mojo.setFiles(List.of("test.json"));
        mojo.setFailOnModification(false);
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), eq("test-hook"), anyList(), any(File.class), isNull()))
                .thenReturn(new PreCommitRunner.Result(1, "Modified"));

        mojo.execute();

        // Should not throw
        verify(runner).runHook(eq("pre-commit"), eq("test-hook"), eq(List.of(targetFile)), any(File.class), isNull());
    }

    @Test
    void execute_shouldFailOnModificationWhenConfigured() throws Exception {
        createConfigFile();
        createTargetFile("test.json");
        mojo.setFiles(List.of("test.json"));
        mojo.setFailOnModification(true);
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), eq("test-hook"), anyList(), any(File.class), isNull()))
                .thenReturn(new PreCommitRunner.Result(1, "Modified"));

        assertThatThrownBy(() -> mojo.execute())
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("modified files");
    }

    @Test
    void execute_shouldFailOnHookError() throws Exception {
        createConfigFile();
        createTargetFile("test.json");
        mojo.setFiles(List.of("test.json"));
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), eq("test-hook"), anyList(), any(File.class), isNull()))
                .thenReturn(new PreCommitRunner.Result(2, "Error"));

        assertThatThrownBy(() -> mojo.execute())
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("failed with exit code 2");
    }

    @Test
    void execute_shouldSkipWhenFileNotFound() throws Exception {
        createConfigFile();
        mojo.setFiles(List.of("non-existent-file.json"));
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);

        mojo.execute();

        verify(runner, never()).runHook(anyString(), anyString(), anyList(), any(File.class), any());
    }

    @Test
    void execute_shouldRunHookOnSpecificFiles() throws Exception {
        createConfigFile();
        File targetFile = createTargetFile("docs/openapi.json");
        mojo.setFiles(List.of("docs/openapi.json"));

        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), eq("test-hook"), anyList(), any(File.class), isNull()))
                .thenReturn(new PreCommitRunner.Result(0, "Passed"));

        mojo.execute();

        verify(runner).runHook(eq("pre-commit"), eq("test-hook"), eq(List.of(targetFile)), any(File.class), isNull());
    }

    @Test
    void execute_shouldSkipWhenFilesIsNull() throws Exception {
        createConfigFile();
        mojo.setFiles(null);
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);

        mojo.execute();

        verify(runner, never()).runHook(anyString(), anyString(), anyList(), any(File.class), any());
    }

    @Test
    void execute_shouldSkipWhenFilesIsEmptyList() throws Exception {
        createConfigFile();
        mojo.setFiles(List.of());
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);

        mojo.execute();

        verify(runner, never()).runHook(anyString(), anyString(), anyList(), any(File.class), any());
    }

    @Test
    void execute_shouldPassEnvironmentVariablesToRunner() throws Exception {
        createConfigFile();
        File targetFile = createTargetFile("test.json");
        mojo.setFiles(List.of("test.json"));
        Map<String, String> envVars = Map.of("GIT_CONFIG_PARAMETERS", "'core.autocrlf=false'");
        mojo.setEnvironmentVariables(envVars);

        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), eq("test-hook"), anyList(), any(File.class), eq(envVars)))
                .thenReturn(new PreCommitRunner.Result(0, "Passed"));

        mojo.execute();

        verify(runner).runHook(eq("pre-commit"), eq("test-hook"), eq(List.of(targetFile)), any(File.class), eq(envVars));
    }

    @Test
    void execute_shouldRunMultipleHooksSequentially() throws Exception {
        createConfigFile();
        File targetFile = createTargetFile("test.json");
        mojo.setHooks(List.of("hook1", "hook2", "hook3"));
        mojo.setFiles(List.of("test.json"));

        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("hook1"))).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("hook2"))).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("hook3"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), anyString(), anyList(), any(File.class), isNull()))
                .thenReturn(new PreCommitRunner.Result(0, "Passed"));

        mojo.execute();

        verify(runner).runHook(eq("pre-commit"), eq("hook1"), eq(List.of(targetFile)), any(File.class), isNull());
        verify(runner).runHook(eq("pre-commit"), eq("hook2"), eq(List.of(targetFile)), any(File.class), isNull());
        verify(runner).runHook(eq("pre-commit"), eq("hook3"), eq(List.of(targetFile)), any(File.class), isNull());
    }

    @Test
    void execute_shouldSkipMissingHooksWhenConfigured() throws Exception {
        createConfigFile();
        createTargetFile("test.json");
        mojo.setHooks(List.of("hook1", "missing-hook", "hook2"));
        mojo.setFiles(List.of("test.json"));
        mojo.setSkipIfHookNotFound(true);

        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("hook1"))).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("missing-hook"))).thenReturn(false);
        when(configParser.isHookConfigured(any(File.class), eq("hook2"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), anyString(), anyList(), any(File.class), isNull()))
                .thenReturn(new PreCommitRunner.Result(0, "Passed"));

        mojo.execute();

        // Only configured hooks should run
        verify(runner).runHook(eq("pre-commit"), eq("hook1"), anyList(), any(File.class), isNull());
        verify(runner).runHook(eq("pre-commit"), eq("hook2"), anyList(), any(File.class), isNull());
        verify(runner, never()).runHook(eq("pre-commit"), eq("missing-hook"), anyList(), any(File.class), isNull());
    }

    @Test
    void execute_shouldStopOnFirstFailingHook() throws Exception {
        createConfigFile();
        createTargetFile("test.json");
        mojo.setHooks(List.of("hook1", "hook2"));
        mojo.setFiles(List.of("test.json"));

        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), anyString())).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), eq("hook1"), anyList(), any(File.class), isNull()))
                .thenReturn(new PreCommitRunner.Result(2, "Error"));

        assertThatThrownBy(() -> mojo.execute())
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("hook1")
                .hasMessageContaining("failed");

        // hook2 should not run because hook1 failed
        verify(runner, never()).runHook(eq("pre-commit"), eq("hook2"), anyList(), any(File.class), isNull());
    }

    @Test
    void execute_shouldFailWhenNoHooksSpecified() {
        mojo.setHooks(null);

        assertThatThrownBy(() -> mojo.execute())
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("'hooks' must be specified");
    }

    @Test
    void execute_shouldFailWhenEmptyHooks() {
        mojo.setHooks(List.of());

        assertThatThrownBy(() -> mojo.execute())
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("'hooks' must be specified");
    }

    @Test
    void execute_shouldPassEmptyEnvironmentVariables() throws Exception {
        createConfigFile();
        File targetFile = createTargetFile("test.json");
        mojo.setFiles(List.of("test.json"));
        Map<String, String> emptyEnvVars = Map.of();
        mojo.setEnvironmentVariables(emptyEnvVars);

        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), eq("test-hook"), anyList(), any(File.class), eq(emptyEnvVars)))
                .thenReturn(new PreCommitRunner.Result(0, "Passed"));

        mojo.execute();

        verify(runner).runHook(eq("pre-commit"), eq("test-hook"), eq(List.of(targetFile)), any(File.class), eq(emptyEnvVars));
    }

    @Test
    void defaultConstructor_shouldCreateInstance() {
        PreCommitRunMojo defaultMojo = new PreCommitRunMojo();
        // Just verify it doesn't throw
        assertThat(defaultMojo).isNotNull();
    }

    private void createConfigFile() throws IOException {
        Files.writeString(tempDir.resolve(".pre-commit-config.yaml"), "repos: []");
    }

    private File createTargetFile(String path) throws IOException {
        Path filePath = tempDir.resolve(path);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, "{}");
        return filePath.toFile();
    }
}
