package io.github.grigoriev.precommit;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Executes a pre-commit hook on specified files.
 *
 * <p>Example usage in pom.xml:</p>
 * <pre>{@code
 * <plugin>
 *     <groupId>io.github.grigoriev</groupId>
 *     <artifactId>pre-commit-run-maven-plugin</artifactId>
 *     <version>0.1.0-SNAPSHOT</version>
 *     <executions>
 *         <execution>
 *             <id>format-openapi-json</id>
 *             <phase>process-resources</phase>
 *             <goals>
 *                 <goal>run</goal>
 *             </goals>
 *             <configuration>
 *                 <hookId>pretty-format-json</hookId>
 *                 <files>
 *                     <file>docs/openapi.json</file>
 *                 </files>
 *             </configuration>
 *         </execution>
 *     </executions>
 * </plugin>
 * }</pre>
 */
@Mojo(name = "run", defaultPhase = LifecyclePhase.NONE, threadSafe = true)
public class PreCommitRunMojo extends AbstractMojo {

    private static final String HOOK_PREFIX = "Hook '";

    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    private File basedir;

    @Parameter(property = "precommit.hookId", required = true)
    private String hookId;

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

        if (!checkPreCommitInstalled()) {
            return;
        }

        File configFile = getConfigFileIfExists();
        if (configFile == null) {
            return;
        }

        if (!checkHookConfigured(configFile)) {
            return;
        }

        List<File> resolvedFiles = resolveAndValidateFiles();
        if (resolvedFiles.isEmpty()) {
            return;
        }

        runHookAndHandleResult(resolvedFiles);
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

    private boolean checkHookConfigured(File configFile) throws MojoFailureException {
        if (configParser.isHookConfigured(configFile, hookId)) {
            return true;
        }
        if (skipIfHookNotFound) {
            getLog().info(HOOK_PREFIX + hookId + "' not found in .pre-commit-config.yaml, skipping execution");
            return false;
        }
        throw new MojoFailureException(HOOK_PREFIX + hookId + "' not found in .pre-commit-config.yaml");
    }

    private List<File> resolveAndValidateFiles() {
        List<File> resolvedFiles = resolveFiles();
        for (File file : resolvedFiles) {
            if (!file.exists()) {
                getLog().info("File not found: " + file.getPath() + ", skipping");
                return Collections.emptyList();
            }
        }
        return resolvedFiles;
    }

    private List<File> resolveFiles() {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .map(path -> new File(basedir, path))
                .toList();
    }

    private void runHookAndHandleResult(List<File> resolvedFiles) throws MojoFailureException {
        logHookStart(resolvedFiles);
        PreCommitRunner.Result result = runner.runHook(preCommitExecutable, hookId, resolvedFiles, basedir);
        logOutput(result);
        handleExitCode(result.getExitCode());
    }

    private void logHookStart(List<File> resolvedFiles) {
        getLog().info("Running pre-commit hook '" + hookId + "' on " + resolvedFiles.size() + " file(s)");
    }

    private void logOutput(PreCommitRunner.Result result) {
        String output = result.getOutput();
        if (output != null && !output.isEmpty()) {
            for (String line : output.split("\n")) {
                getLog().info(line);
            }
        }
    }

    private void handleExitCode(int exitCode) throws MojoFailureException {
        if (exitCode == 0) {
            getLog().info(HOOK_PREFIX + hookId + "' passed (no changes needed)");
            return;
        }
        if (exitCode == 1) {
            handleModification();
            return;
        }
        throw new MojoFailureException(HOOK_PREFIX + hookId + "' failed with exit code " + exitCode);
    }

    private void handleModification() throws MojoFailureException {
        if (failOnModification) {
            throw new MojoFailureException(HOOK_PREFIX + hookId + "' modified files. " +
                    "Review the changes and commit them, or set failOnModification=false");
        }
        getLog().info(HOOK_PREFIX + hookId + "' modified files");
    }

    void setBasedir(File basedir) {
        this.basedir = basedir;
    }

    void setHookId(String hookId) {
        this.hookId = hookId;
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
}
