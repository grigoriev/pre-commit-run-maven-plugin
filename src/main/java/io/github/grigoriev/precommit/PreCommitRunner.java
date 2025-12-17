package io.github.grigoriev.precommit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes pre-commit commands.
 */
public class PreCommitRunner {

    private static final long DEFAULT_TIMEOUT_SECONDS = 300;
    private static final long DEFAULT_INSTALL_CHECK_TIMEOUT_SECONDS = 10;

    private final long timeoutSeconds;
    private final long installCheckTimeoutSeconds;

    /**
     * Creates a PreCommitRunner with default timeouts.
     */
    public PreCommitRunner() {
        this(DEFAULT_TIMEOUT_SECONDS, DEFAULT_INSTALL_CHECK_TIMEOUT_SECONDS);
    }

    PreCommitRunner(long timeoutSeconds, long installCheckTimeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        this.installCheckTimeoutSeconds = installCheckTimeoutSeconds;
    }

    /**
     * Checks if pre-commit is installed and available.
     *
     * @param executable the pre-commit executable name or path
     * @return true if pre-commit is available
     */
    public boolean isPreCommitInstalled(String executable) {
        try {
            ProcessBuilder pb = new ProcessBuilder(executable, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Drain stream in background thread to prevent blocking
            Thread drainThread = new Thread(() -> drainStream(process.getInputStream()));
            drainThread.start();

            boolean finished = process.waitFor(installCheckTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                drainThread.join(1000);
                return false;
            }
            drainThread.join(1000);
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void drainStream(InputStream inputStream) {
        try {
            inputStream.transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            // Ignore - process may have been killed
        }
    }

    /**
     * Runs a pre-commit hook on the specified files.
     *
     * @param executable the pre-commit executable
     * @param hookId     the hook ID to run
     * @param files      the files to run the hook on (can be empty to run on all files)
     * @param workingDir the working directory
     * @return the result containing exit code and output
     */
    public Result runHook(String executable, String hookId, List<File> files, File workingDir) {
        return runHook(executable, hookId, files, workingDir, null);
    }

    /**
     * Runs a pre-commit hook on the specified files with custom environment variables.
     *
     * @param executable           the pre-commit executable
     * @param hookId               the hook ID to run
     * @param files                the files to run the hook on (can be empty to run on all files)
     * @param workingDir           the working directory
     * @param environmentVariables additional environment variables (can be null)
     * @return the result containing exit code and output
     */
    public Result runHook(String executable, String hookId, List<File> files, File workingDir,
                          Map<String, String> environmentVariables) {
        List<String> command = buildCommand(executable, hookId, files);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir);
            pb.redirectErrorStream(true);

            if (environmentVariables != null && !environmentVariables.isEmpty()) {
                pb.environment().putAll(environmentVariables);
            }

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            Thread outputReader = createOutputReaderThread(process, output);
            outputReader.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            outputReader.join(5000);

            if (!finished) {
                process.destroyForcibly();
                return new Result(-1, "Process timed out after " + timeoutSeconds + " seconds");
            }

            return new Result(process.exitValue(), output.toString());
        } catch (IOException e) {
            return new Result(-1, "Failed to execute pre-commit: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, "Execution interrupted");
        }
    }

    private List<String> buildCommand(String executable, String hookId, List<File> files) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("run");
        command.add(hookId);

        if (files != null && !files.isEmpty()) {
            command.add("--files");
            for (File file : files) {
                command.add(file.getAbsolutePath());
            }
        }
        return command;
    }

    private Thread createOutputReaderThread(Process process, StringBuilder output) {
        return new Thread(() -> readProcessOutput(process.getInputStream(), output));
    }

    void readProcessOutput(InputStream inputStream, StringBuilder output) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!output.isEmpty()) {
                    output.append(System.lineSeparator());
                }
                output.append(line);
            }
        } catch (IOException e) {
            output.append("Error reading output: ").append(e.getMessage());
        }
    }

    /**
     * Result of a pre-commit hook execution.
     *
     * @param exitCode the process exit code
     * @param output   the process output
     */
    public record Result(int exitCode, String output) {

        /**
         * Returns the exit code of the pre-commit process.
         *
         * @return the exit code
         */
        public int getExitCode() {
            return exitCode;
        }

        /**
         * Returns the output of the pre-commit process.
         *
         * @return the process output
         */
        public String getOutput() {
            return output;
        }

        /**
         * Returns true if the hook passed without modifications (exit code 0).
         *
         * @return true if passed
         */
        public boolean isPassed() {
            return exitCode == 0;
        }

        /**
         * Returns true if the hook modified files (exit code 1).
         *
         * @return true if files were modified
         */
        public boolean isModified() {
            return exitCode == 1;
        }

        /**
         * Returns true if the hook failed (exit code &gt; 1 or &lt; 0).
         *
         * @return true if failed
         */
        public boolean isFailed() {
            return exitCode > 1 || exitCode < 0;
        }
    }
}
