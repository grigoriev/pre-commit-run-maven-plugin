package io.github.grigoriev.precommit;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        mojo.setHookId("test-hook");
        mojo.setPreCommitExecutable("pre-commit");
    }

    @Test
    void execute_shouldSkipWhenSkipIsTrue() throws MojoExecutionException, MojoFailureException {
        mojo.setSkip(true);

        mojo.execute();

        verify(runner, never()).isPreCommitInstalled(anyString());
        verify(runner, never()).runHook(anyString(), anyString(), anyList(), any(File.class));
    }

    @Test
    void execute_shouldSkipWhenPreCommitNotInstalled() throws Exception {
        mojo.setSkipIfNotInstalled(true);
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(false);

        mojo.execute();

        verify(runner, never()).runHook(anyString(), anyString(), anyList(), any(File.class));
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
        verify(runner, never()).runHook(anyString(), anyString(), anyList(), any(File.class));
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

        verify(runner, never()).runHook(anyString(), anyString(), anyList(), any(File.class));
    }

    @Test
    void execute_shouldFailWhenHookNotFoundAndNotSkipping() throws Exception {
        createConfigFile();
        mojo.setSkipIfHookNotFound(false);
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(false);

        assertThatThrownBy(() -> mojo.execute())
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Hook 'test-hook' not found");
    }

    @Test
    void execute_shouldRunHookSuccessfully() throws Exception {
        createConfigFile();
        File targetFile = createTargetFile("test.json");
        mojo.setFiles(List.of("test.json"));
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), eq("test-hook"), anyList(), any(File.class)))
                .thenReturn(new PreCommitRunner.Result(0, "Passed"));

        mojo.execute();

        verify(runner).runHook(eq("pre-commit"), eq("test-hook"), eq(List.of(targetFile)), any(File.class));
    }

    @Test
    void execute_shouldHandleHookModification() throws Exception {
        createConfigFile();
        File targetFile = createTargetFile("test.json");
        mojo.setFiles(List.of("test.json"));
        mojo.setFailOnModification(false);
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), eq("test-hook"), anyList(), any(File.class)))
                .thenReturn(new PreCommitRunner.Result(1, "Modified"));

        mojo.execute();

        // Should not throw
        verify(runner).runHook(eq("pre-commit"), eq("test-hook"), eq(List.of(targetFile)), any(File.class));
    }

    @Test
    void execute_shouldFailOnModificationWhenConfigured() throws Exception {
        createConfigFile();
        createTargetFile("test.json");
        mojo.setFiles(List.of("test.json"));
        mojo.setFailOnModification(true);
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), eq("test-hook"), anyList(), any(File.class)))
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
        when(runner.runHook(eq("pre-commit"), eq("test-hook"), anyList(), any(File.class)))
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

        verify(runner, never()).runHook(anyString(), anyString(), anyList(), any(File.class));
    }

    @Test
    void execute_shouldRunHookOnSpecificFiles() throws Exception {
        createConfigFile();
        File targetFile = createTargetFile("docs/openapi.json");
        mojo.setFiles(List.of("docs/openapi.json"));

        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), eq("test-hook"), anyList(), any(File.class)))
                .thenReturn(new PreCommitRunner.Result(0, "Passed"));

        mojo.execute();

        verify(runner).runHook(eq("pre-commit"), eq("test-hook"), eq(List.of(targetFile)), any(File.class));
    }

    @Test
    void execute_shouldRunHookWithEmptyOutput() throws Exception {
        createConfigFile();
        File targetFile = createTargetFile("test.json");
        mojo.setFiles(List.of("test.json"));
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), eq("test-hook"), anyList(), any(File.class)))
                .thenReturn(new PreCommitRunner.Result(0, ""));

        mojo.execute();

        verify(runner).runHook(eq("pre-commit"), eq("test-hook"), eq(List.of(targetFile)), any(File.class));
    }

    @Test
    void execute_shouldRunHookWithNullOutput() throws Exception {
        createConfigFile();
        File targetFile = createTargetFile("test.json");
        mojo.setFiles(List.of("test.json"));
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);
        when(runner.runHook(eq("pre-commit"), eq("test-hook"), anyList(), any(File.class)))
                .thenReturn(new PreCommitRunner.Result(0, null));

        mojo.execute();

        verify(runner).runHook(eq("pre-commit"), eq("test-hook"), eq(List.of(targetFile)), any(File.class));
    }

    @Test
    void execute_shouldSkipWhenNoFilesConfigured() throws Exception {
        createConfigFile();
        mojo.setFiles(null);
        when(runner.isPreCommitInstalled("pre-commit")).thenReturn(true);
        when(configParser.isHookConfigured(any(File.class), eq("test-hook"))).thenReturn(true);

        mojo.execute();

        verify(runner, never()).runHook(anyString(), anyString(), anyList(), any(File.class));
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
