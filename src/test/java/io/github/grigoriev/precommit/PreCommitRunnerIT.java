package io.github.grigoriev.precommit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests that require real pre-commit installation.
 * These tests are skipped if pre-commit is not available.
 */
class PreCommitRunnerIT {

    private static boolean preCommitAvailable;

    private PreCommitRunner runner;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void checkPreCommitInstalled() {
        PreCommitRunner checkRunner = new PreCommitRunner();
        preCommitAvailable = checkRunner.isPreCommitInstalled("pre-commit");
    }

    @BeforeEach
    void setUp() {
        assumeTrue(preCommitAvailable, "pre-commit is not installed, skipping integration tests");
        runner = new PreCommitRunner();
    }

    @Test
    void isPreCommitInstalled_shouldReturnTrueForRealPreCommit() {
        assertThat(runner.isPreCommitInstalled("pre-commit")).isTrue();
    }

    @Test
    void runHook_shouldRunTrailingWhitespaceHook() throws IOException {
        // Setup: create git repo with pre-commit config
        initGitRepo();
        createPreCommitConfig("trailing-whitespace");

        // Create a file with trailing whitespace
        File testFile = createFile("test.txt", "hello world   \n");

        // Run the hook
        PreCommitRunner.Result result = runner.runHook("pre-commit", "trailing-whitespace", List.of(testFile), tempDir.toFile());

        // trailing-whitespace hook returns 1 when it fixes files
        assertThat(result.getExitCode()).isIn(0, 1);
        assertThat(result.getOutput()).contains("trailing whitespace");
    }

    @Test
    void runHook_shouldRunCheckYamlHook() throws IOException {
        // Setup
        initGitRepo();
        createPreCommitConfig("check-yaml");

        // Create a valid YAML file
        File testFile = createFile("test.yaml", "key: value\nlist:\n  - item1\n  - item2\n");

        // Run the hook
        PreCommitRunner.Result result = runner.runHook("pre-commit", "check-yaml", List.of(testFile), tempDir.toFile());

        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void runHook_shouldFailOnInvalidYaml() throws IOException {
        // Setup
        initGitRepo();
        createPreCommitConfig("check-yaml");

        // Create an invalid YAML file (unclosed bracket)
        File testFile = createFile("invalid.yaml", "key: [unclosed\n");

        // Run the hook - pre-commit returns exit code 1 for validation errors
        PreCommitRunner.Result result = runner.runHook("pre-commit", "check-yaml", List.of(testFile), tempDir.toFile());

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getExitCode()).isEqualTo(1);
    }

    @Test
    void runHook_shouldRunCheckJsonHook() throws IOException {
        // Setup
        initGitRepo();
        createPreCommitConfig("check-json");

        // Create a valid JSON file
        File testFile = createFile("test.json", "{\"key\": \"value\"}\n");

        // Run the hook
        PreCommitRunner.Result result = runner.runHook("pre-commit", "check-json", List.of(testFile), tempDir.toFile());

        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void runHook_shouldFailOnInvalidJson() throws IOException {
        // Setup
        initGitRepo();
        createPreCommitConfig("check-json");

        // Create an invalid JSON file
        File testFile = createFile("invalid.json", "{key: value}\n");

        // Run the hook - pre-commit returns exit code 1 for validation errors
        PreCommitRunner.Result result = runner.runHook("pre-commit", "check-json", List.of(testFile), tempDir.toFile());

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getExitCode()).isEqualTo(1);
    }

    @Test
    void runHook_shouldNotModifyProperlyFormattedJsonWithLfLineEndings() throws IOException {
        // Setup
        initGitRepo();
        createPreCommitConfig("pretty-format-json");

        // Copy properly formatted JSON from resources (has LF line endings, sorted keys, 2-space indent)
        File testFile = copyResourceToTempDir("/integration/formatted.json", "formatted.json");

        // Verify file has LF endings before running hook
        byte[] contentBefore = Files.readAllBytes(testFile.toPath());
        assertThat(new String(contentBefore, StandardCharsets.UTF_8)).doesNotContain("\r\n");

        // Run the hook
        PreCommitRunner.Result result = runner.runHook("pre-commit", "pretty-format-json", List.of(testFile), tempDir.toFile());

        // Verify hook passed without modifications (exit code 0 = no changes)
        assertThat(result.isPassed())
                .as("Hook should pass without modifications. Output: %s", result.getOutput())
                .isTrue();

        // Verify file content is exactly the same (including line endings)
        byte[] contentAfter = Files.readAllBytes(testFile.toPath());
        assertThat(contentAfter)
                .as("File content should not be modified")
                .isEqualTo(contentBefore);

        // Double-check LF endings are preserved
        assertThat(new String(contentAfter, StandardCharsets.UTF_8)).doesNotContain("\r\n");
    }

    @Test
    void runHook_shouldConvertCrlfToLfInJson() throws IOException {
        // Setup
        initGitRepo();
        createPreCommitConfig("mixed-line-ending", List.of("--fix=lf"));

        // Copy properly formatted JSON and convert LF to CRLF (simulating Windows line endings)
        File testFile = copyResourceToTempDir("/integration/formatted.json", "crlf.json");
        convertLfToCrlf(testFile.toPath());

        // Verify file has CRLF endings before running hook
        byte[] contentBefore = Files.readAllBytes(testFile.toPath());
        assertThat(new String(contentBefore, StandardCharsets.UTF_8)).contains("\r\n");

        // Run the mixed-line-ending hook to fix CRLF -> LF
        PreCommitRunner.Result result = runner.runHook("pre-commit", "mixed-line-ending", List.of(testFile), tempDir.toFile());

        // Hook should modify the file (exit code 1 = files were modified)
        assertThat(result.isModified())
                .as("Hook should modify the file. Output: %s", result.getOutput())
                .isTrue();

        // Verify file now has LF endings (CRLF converted to LF)
        byte[] contentAfter = Files.readAllBytes(testFile.toPath());
        assertThat(new String(contentAfter, StandardCharsets.UTF_8))
                .as("File should have LF endings after hook execution")
                .doesNotContain("\r\n");
    }

    @Test
    void runHook_shouldRunEndOfFileFixerHook() throws IOException {
        // Setup
        initGitRepo();
        createPreCommitConfig("end-of-file-fixer");

        // Create a file without trailing newline
        File testFile = createFile("test.txt", "hello world");

        // Run the hook
        PreCommitRunner.Result result = runner.runHook("pre-commit", "end-of-file-fixer", List.of(testFile), tempDir.toFile());

        // end-of-file-fixer returns 1 when it modifies files
        assertThat(result.getExitCode()).isIn(0, 1);
    }

    @Test
    void runHook_shouldHandleMultipleFiles() throws IOException {
        // Setup
        initGitRepo();
        createPreCommitConfig("check-yaml");

        // Create multiple valid YAML files
        File file1 = createFile("file1.yaml", "key1: value1\n");
        File file2 = createFile("file2.yaml", "key2: value2\n");

        // Run the hook on multiple files
        PreCommitRunner.Result result = runner.runHook("pre-commit", "check-yaml", List.of(file1, file2), tempDir.toFile());

        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void runHook_shouldReturnErrorForNonExistentHook() throws IOException {
        // Setup
        initGitRepo();
        createPreCommitConfig("check-yaml");

        File testFile = createFile("test.txt", "content\n");

        // Run a non-existent hook - pre-commit returns exit code 1 for unknown hooks
        PreCommitRunner.Result result = runner.runHook("pre-commit", "non-existent-hook-12345", List.of(testFile), tempDir.toFile());

        assertThat(result.isPassed()).isFalse();
    }

    private void initGitRepo() throws IOException {
        // Initialize a git repository (required for pre-commit to work)
        ProcessBuilder pb = new ProcessBuilder("git", "init");
        pb.directory(tempDir.toFile());
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git init interrupted", e);
        }
    }

    private void createPreCommitConfig(String hookId) throws IOException {
        createPreCommitConfig(hookId, null);
    }

    private void createPreCommitConfig(String hookId, List<String> args) throws IOException {
        StringBuilder config = new StringBuilder();
        config.append("""
                repos:
                  - repo: https://github.com/pre-commit/pre-commit-hooks
                    rev: v5.0.0
                    hooks:
                      - id: %s
                """.formatted(hookId));

        if (args != null && !args.isEmpty()) {
            config.append("        args: [");
            config.append(String.join(", ", args.stream().map(a -> "'" + a + "'").toList()));
            config.append("]\n");
        }

        Files.writeString(tempDir.resolve(".pre-commit-config.yaml"), config.toString());

        // Install the hooks
        ProcessBuilder pb = new ProcessBuilder("pre-commit", "install-hooks");
        pb.directory(tempDir.toFile());
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("pre-commit install-hooks interrupted", e);
        }
    }

    private File createFile(String name, String content) throws IOException {
        Path filePath = tempDir.resolve(name);
        Files.writeString(filePath, content);
        return filePath.toFile();
    }

    /**
     * Copies a resource file to the temp directory, preserving exact byte content (including line endings).
     */
    private File copyResourceToTempDir(String resourcePath, String targetName) throws IOException {
        Path targetPath = tempDir.resolve(targetName);
        try (var inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.copy(inputStream, targetPath);
        }
        return targetPath.toFile();
    }

    /**
     * Converts LF line endings to CRLF (simulating Windows line endings).
     */
    private void convertLfToCrlf(Path filePath) throws IOException {
        byte[] content = Files.readAllBytes(filePath);
        String text = new String(content, StandardCharsets.UTF_8);
        // Replace LF with CRLF (but not existing CRLF)
        String crlfText = text.replace("\r\n", "\n").replace("\n", "\r\n");
        Files.write(filePath, crlfText.getBytes(StandardCharsets.UTF_8));
    }
}
