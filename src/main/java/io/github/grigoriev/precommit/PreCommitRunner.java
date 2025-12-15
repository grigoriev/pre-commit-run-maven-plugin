package io.github.grigoriev.precommit;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes pre-commit commands.
 */
public class PreCommitRunner {

    private static final long DEFAULT_TIMEOUT_SECONDS = 300;

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

            drainStream(process.getInputStream());

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void drainStream(InputStream inputStream) throws IOException {
        inputStream.transferTo(OutputStream.nullOutputStream());
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
        List<String> command = buildCommand(executable, hookId, files);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            Thread outputReader = createOutputReaderThread(process, output);
            outputReader.start();

            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            outputReader.join(5000);

            if (!finished) {
                process.destroyForcibly();
                return new Result(-1, "Process timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds");
            }

            return new Result(process.exitValue(), output.toString().trim());
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
        return new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (IOException e) {
                output.append("Error reading output: ").append(e.getMessage()).append("\n");
            }
        });
    }

    /**
     * Result of a pre-commit hook execution.
     */
    public static class Result {
        private final int exitCode;
        private final String output;

        public Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getOutput() {
            return output;
        }

        public boolean isPassed() {
            return exitCode == 0;
        }

        public boolean isModified() {
            return exitCode == 1;
        }

        public boolean isFailed() {
            return exitCode > 1 || exitCode < 0;
        }
    }
}
