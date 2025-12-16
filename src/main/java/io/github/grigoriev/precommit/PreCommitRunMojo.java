package io.github.grigoriev.precommit;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes pre-commit hooks on specified files.
 *
 * <p>Example usage in pom.xml (single hook):</p>
 * <pre>{@code
 * <configuration>
 *     <hookId>pretty-format-json</hookId>
 *     <files>
 *         <file>docs/openapi.json</file>
 *     </files>
 * </configuration>
 * }</pre>
 *
 * <p>Example with multiple hooks:</p>
 * <pre>{@code
 * <configuration>
 *     <hooks>
 *         <hook>mixed-line-ending</hook>
 *         <hook>pretty-format-json</hook>
 *     </hooks>
 *     <files>
 *         <file>docs/openapi.json</file>
 *     </files>
 * </configuration>
 * }</pre>
 */
@Mojo(name = "run", defaultPhase = LifecyclePhase.NONE, threadSafe = true)
public class PreCommitRunMojo extends AbstractMojo {

    private static final String HOOK_PREFIX = "Hook '";

    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    private File basedir;

    /**
     * Single hook ID to run. Use this OR {@code hooks}, not both.
     */
    @Parameter(property = "precommit.hookId")
    private String hookId;

    /**
     * List of hook IDs to run sequentially. Use this OR {@code hookId}, not both.
     * Hooks are executed in the order specified.
     */
    @Parameter(property = "precommit.hooks")
    private List<String> hooks;

    @Parameter(property = "precommit.files")
    private List<String> files;

    @Parameter(property = "precommit.skipIfHookNotFound", defaultValue = "true")
    private boolean skipIfHookNotFound;

    @Parameter(property = "precommit.skipIfConfigNotFound", defaultValue = "true")
    private boolean skipIfConfigNotFound;

    @Parameter(property = "precommit.skipIfNotInstalled", defaultValue = "true")
    private boolean skipIfNotInstalled;

    @Parameter(property = "precommit.failOnModification", defaultValue = "false")
    private boolean failOnModification;

    @Parameter(property = "precommit.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "precommit.executable", defaultValue = "pre-commit")
    private String preCommitExecutable;

    /**
     * Additional environment variables to pass to the pre-commit process.
     * Useful for setting Git configuration, e.g.:
     * <pre>{@code
     * <environmentVariables>
     *     <GIT_CONFIG_PARAMETERS>'core.autocrlf=false'</GIT_CONFIG_PARAMETERS>
     * </environmentVariables>
     * }</pre>
     */
    @Parameter
    private Map<String, String> environmentVariables;

    private final PreCommitConfigParser configParser;
    private final PreCommitRunner runner;

    public PreCommitRunMojo() {
        this.configParser = new PreCommitConfigParser();
        this.runner = new PreCommitRunner();
    }

    PreCommitRunMojo(PreCommitConfigParser configParser, PreCommitRunner runner) {
        this.configParser = configParser;
        this.runner = runner;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping pre-commit hook execution");
            return;
        }

        List<String> effectiveHooks = getEffectiveHooks();
        if (effectiveHooks.isEmpty()) {
            throw new MojoExecutionException("Either 'hookId' or 'hooks' must be specified");
        }

        if (!checkPreCommitInstalled()) {
            return;
        }

        File configFile = getConfigFileIfExists();
        if (configFile == null) {
            return;
        }

        List<String> configuredHooks = filterConfiguredHooks(configFile, effectiveHooks);
        if (configuredHooks.isEmpty()) {
            return;
        }

        List<File> resolvedFiles = resolveAndValidateFiles();
        if (resolvedFiles.isEmpty()) {
            return;
        }

        runHooksAndHandleResults(configuredHooks, resolvedFiles);
    }

    private List<String> getEffectiveHooks() {
        if (hooks != null && !hooks.isEmpty()) {
            return hooks;
        }
        if (hookId != null && !hookId.isEmpty()) {
            return List.of(hookId);
        }
        return List.of();
    }

    private boolean checkPreCommitInstalled() throws MojoFailureException {
        if (runner.isPreCommitInstalled(preCommitExecutable)) {
            return true;
        }
        if (skipIfNotInstalled) {
            getLog().warn("pre-commit is not installed or not in PATH, skipping execution");
            return false;
        }
        throw new MojoFailureException("pre-commit is not installed or not in PATH");
    }

    private File getConfigFileIfExists() throws MojoFailureException {
        File configFile = new File(basedir, ".pre-commit-config.yaml");
        if (configFile.exists()) {
            return configFile;
        }
        if (skipIfConfigNotFound) {
            getLog().info("No .pre-commit-config.yaml found, skipping execution");
            return null;
        }
        throw new MojoFailureException(".pre-commit-config.yaml not found in " + basedir);
    }

    private List<String> filterConfiguredHooks(File configFile, List<String> hooksToCheck) throws MojoFailureException {
        List<String> configuredHooks = new ArrayList<>();
        List<String> missingHooks = new ArrayList<>();

        for (String hook : hooksToCheck) {
            if (configParser.isHookConfigured(configFile, hook)) {
                configuredHooks.add(hook);
            } else {
                missingHooks.add(hook);
            }
        }

        if (!missingHooks.isEmpty()) {
            String missingList = String.join(", ", missingHooks);
            if (skipIfHookNotFound) {
                getLog().info("Hook(s) not found in .pre-commit-config.yaml, skipping: " + missingList);
            } else {
                throw new MojoFailureException("Hook(s) not found in .pre-commit-config.yaml: " + missingList);
            }
        }

        return configuredHooks;
    }

    private List<File> resolveAndValidateFiles() {
        List<File> resolvedFiles = resolveFiles();
        List<File> existingFiles = new ArrayList<>();
        List<String> missingFiles = new ArrayList<>();

        for (File file : resolvedFiles) {
            if (file.exists()) {
                existingFiles.add(file);
            } else {
                missingFiles.add(file.getPath());
            }
        }

        if (!missingFiles.isEmpty()) {
            getLog().warn("Files not found: " + String.join(", ", missingFiles));
        }

        return existingFiles;
    }

    private List<File> resolveFiles() {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .map(path -> new File(basedir, path))
                .toList();
    }

    private void runHooksAndHandleResults(List<String> hooksToRun, List<File> resolvedFiles) throws MojoFailureException {
        for (String hook : hooksToRun) {
            runSingleHook(hook, resolvedFiles);
        }
    }

    private void runSingleHook(String hook, List<File> resolvedFiles) throws MojoFailureException {
        getLog().info("Running pre-commit hook '" + hook + "' on " + resolvedFiles.size() + " file(s)");
        PreCommitRunner.Result result = runner.runHook(preCommitExecutable, hook, resolvedFiles, basedir, environmentVariables);
        logOutput(result);
        handleExitCode(hook, result.getExitCode());
    }

    private void logOutput(PreCommitRunner.Result result) {
        String output = result.getOutput();
        if (output != null && !output.isEmpty()) {
            for (String line : output.split("\n")) {
                getLog().info(line);
            }
        }
    }

    private void handleExitCode(String hook, int exitCode) throws MojoFailureException {
        if (exitCode == 0) {
            getLog().info(HOOK_PREFIX + hook + "' passed (no changes needed)");
            return;
        }
        if (exitCode == 1) {
            handleModification(hook);
            return;
        }
        throw new MojoFailureException(HOOK_PREFIX + hook + "' failed with exit code " + exitCode);
    }

    private void handleModification(String hook) throws MojoFailureException {
        if (failOnModification) {
            throw new MojoFailureException(HOOK_PREFIX + hook + "' modified files. " +
                    "Review the changes and commit them, or set failOnModification=false");
        }
        getLog().info(HOOK_PREFIX + hook + "' modified files");
    }

    void setBasedir(File basedir) {
        this.basedir = basedir;
    }

    void setHookId(String hookId) {
        this.hookId = hookId;
    }

    void setHooks(List<String> hooks) {
        this.hooks = hooks;
    }

    void setFiles(List<String> files) {
        this.files = files;
    }

    void setSkipIfHookNotFound(boolean skipIfHookNotFound) {
        this.skipIfHookNotFound = skipIfHookNotFound;
    }

    void setSkipIfConfigNotFound(boolean skipIfConfigNotFound) {
        this.skipIfConfigNotFound = skipIfConfigNotFound;
    }

    void setSkipIfNotInstalled(boolean skipIfNotInstalled) {
        this.skipIfNotInstalled = skipIfNotInstalled;
    }

    void setFailOnModification(boolean failOnModification) {
        this.failOnModification = failOnModification;
    }

    void setSkip(boolean skip) {
        this.skip = skip;
    }

    void setPreCommitExecutable(String preCommitExecutable) {
        this.preCommitExecutable = preCommitExecutable;
    }

    void setEnvironmentVariables(Map<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }
}
