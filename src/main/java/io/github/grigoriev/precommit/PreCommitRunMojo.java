package io.github.grigoriev.precommit;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.List;

/**
 * Executes a pre-commit hook on specified files.
 *
 * <p>Example usage in pom.xml:</p>
 * <pre>{@code
 * <plugin>
 *     <groupId>io.github.grigoriev</groupId>
 *     <artifactId>pre-commit-run-maven-plugin</artifactId>
 *     <version>1.0.0-SNAPSHOT</version>
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

    /**
     * The base directory of the project.
     */
    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    private File basedir;

    /**
     * The pre-commit hook ID to run.
     */
    @Parameter(property = "precommit.hookId", required = true)
    private String hookId;

    /**
     * Files to run the hook on. Paths are relative to the project base directory.
     */
    @Parameter(property = "precommit.files")
    private List<String> files;

    /**
     * Whether to skip execution if the hook is not found in .pre-commit-config.yaml.
     * If false, the build will fail when the hook is not configured.
     */
    @Parameter(property = "precommit.skipIfHookNotFound", defaultValue = "true")
    private boolean skipIfHookNotFound;

    /**
     * Whether to skip execution if .pre-commit-config.yaml is not found.
     */
    @Parameter(property = "precommit.skipIfConfigNotFound", defaultValue = "true")
    private boolean skipIfConfigNotFound;

    /**
     * Whether to skip execution if pre-commit is not installed.
     */
    @Parameter(property = "precommit.skipIfNotInstalled", defaultValue = "true")
    private boolean skipIfNotInstalled;

    /**
     * Whether to fail the build if the hook modifies files (exit code 1).
     * By default, file modifications are treated as success.
     */
    @Parameter(property = "precommit.failOnModification", defaultValue = "false")
    private boolean failOnModification;

    /**
     * Whether to skip the entire execution.
     */
    @Parameter(property = "precommit.skip", defaultValue = "false")
    private boolean skip;

    /**
     * The pre-commit executable name or path.
     */
    @Parameter(property = "precommit.executable", defaultValue = "pre-commit")
    private String preCommitExecutable;

    private final PreCommitConfigParser configParser;
    private final PreCommitRunner runner;

    public PreCommitRunMojo() {
        this.configParser = new PreCommitConfigParser();
        this.runner = new PreCommitRunner();
    }

    // Constructor for testing
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

        // Check if pre-commit is installed
        if (!runner.isPreCommitInstalled(preCommitExecutable)) {
            if (skipIfNotInstalled) {
                getLog().warn("pre-commit is not installed or not in PATH, skipping execution");
                return;
            }
            throw new MojoFailureException("pre-commit is not installed or not in PATH");
        }

        // Check for .pre-commit-config.yaml
        File configFile = new File(basedir, ".pre-commit-config.yaml");
        if (!configFile.exists()) {
            if (skipIfConfigNotFound) {
                getLog().info("No .pre-commit-config.yaml found, skipping execution");
                return;
            }
            throw new MojoFailureException(".pre-commit-config.yaml not found in " + basedir);
        }

        // Check if hook is configured
        if (!configParser.isHookConfigured(configFile, hookId)) {
            if (skipIfHookNotFound) {
                getLog().info("Hook '" + hookId + "' not found in .pre-commit-config.yaml, skipping execution");
                return;
            }
            throw new MojoFailureException("Hook '" + hookId + "' not found in .pre-commit-config.yaml");
        }

        // Resolve file paths
        List<File> resolvedFiles = resolveFiles();

        // Check if files exist
        for (File file : resolvedFiles) {
            if (!file.exists()) {
                getLog().info("File not found: " + file.getPath() + ", skipping");
                return;
            }
        }

        // Run the hook
        getLog().info("Running pre-commit hook '" + hookId + "'" +
                (resolvedFiles.isEmpty() ? "" : " on " + resolvedFiles.size() + " file(s)"));

        PreCommitRunner.Result result = runner.runHook(preCommitExecutable, hookId, resolvedFiles, basedir);

        // Log output if present
        if (result.getOutput() != null && !result.getOutput().isEmpty()) {
            for (String line : result.getOutput().split("\n")) {
                getLog().info(line);
            }
        }

        // Handle exit code
        handleExitCode(result.getExitCode());
    }

    private List<File> resolveFiles() {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        return files.stream()
                .map(path -> new File(basedir, path))
                .toList();
    }

    private void handleExitCode(int exitCode) throws MojoFailureException {
        switch (exitCode) {
            case 0:
                getLog().info("Hook '" + hookId + "' passed (no changes needed)");
                break;
            case 1:
                if (failOnModification) {
                    throw new MojoFailureException("Hook '" + hookId + "' modified files. " +
                            "Review the changes and commit them, or set failOnModification=false");
                }
                getLog().info("Hook '" + hookId + "' modified files");
                break;
            default:
                throw new MojoFailureException("Hook '" + hookId + "' failed with exit code " + exitCode);
        }
    }

    // Setters for testing
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
