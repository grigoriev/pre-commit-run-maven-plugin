package io.github.grigoriev.precommit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobPatternExpanderTest {

    @TempDir
    Path tempDir;

    private GlobPatternExpander expander;
    private List<String> warnings;

    @BeforeEach
    void setUp() {
        warnings = new ArrayList<>();
        expander = new GlobPatternExpander(warnings::add);
    }

    @Test
    void isGlobPattern_shouldDetectAsterisk() {
        assertThat(expander.isGlobPattern("*.java")).isTrue();
        assertThat(expander.isGlobPattern("src/**/*.java")).isTrue();
    }

    @Test
    void isGlobPattern_shouldDetectQuestionMark() {
        assertThat(expander.isGlobPattern("file?.txt")).isTrue();
    }

    @Test
    void isGlobPattern_shouldDetectBrackets() {
        assertThat(expander.isGlobPattern("file[0-9].txt")).isTrue();
    }

    @Test
    void isGlobPattern_shouldReturnFalseForRegularPath() {
        assertThat(expander.isGlobPattern("src/main/java/File.java")).isFalse();
        assertThat(expander.isGlobPattern("docs/openapi.json")).isFalse();
    }

    @Test
    void expand_shouldMatchSingleFile() throws IOException {
        createFile("test.java");

        List<File> result = expander.expand("*.java", tempDir.toFile());

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).hasName("test.java");
    }

    @Test
    void expand_shouldMatchMultipleFiles() throws IOException {
        createFile("File1.java");
        createFile("File2.java");
        createFile("readme.md");

        List<File> result = expander.expand("*.java", tempDir.toFile());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(File::getName)
                .containsExactlyInAnyOrder("File1.java", "File2.java");
    }

    @Test
    void expand_shouldMatchRecursively() throws IOException {
        createFile("src/main/java/App.java");
        createFile("src/test/java/AppTest.java");
        createFile("docs/readme.md");

        List<File> result = expander.expand("src/**/*.java", tempDir.toFile());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(File::getName)
                .containsExactlyInAnyOrder("App.java", "AppTest.java");
    }

    @Test
    void expand_shouldReturnEmptyListForNoMatches() throws IOException {
        createFile("test.txt");

        List<File> result = expander.expand("*.java", tempDir.toFile());

        assertThat(result).isEmpty();
    }

    @Test
    void expand_shouldReturnEmptyForNonExistentBaseDir() {
        // Try to expand on a non-existent directory
        File nonExistentDir = new File(tempDir.toFile(), "nonexistent");

        List<File> result = expander.expand("*.java", nonExistentDir);

        // Should return empty list (walkFileTree handles non-existent dirs gracefully or throws)
        assertThat(result).isEmpty();
    }

    @Test
    void expand_shouldLogWarningOnIOException() {
        // Create a subclass that throws IOException from walkFileTree
        GlobPatternExpander throwingExpander = new GlobPatternExpander(warnings::add) {
            @Override
            protected void walkFileTree(Path startDir, java.nio.file.PathMatcher matcher, List<File> matchedFiles) throws IOException {
                throw new IOException("Simulated file system error");
            }
        };

        List<File> result = throwingExpander.expand("*.java", tempDir.toFile());

        assertThat(result).isEmpty();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("Failed to expand glob pattern");
        assertThat(warnings.get(0)).contains("Simulated file system error");
    }

    @Test
    void defaultConstructor_shouldCreateInstance() {
        GlobPatternExpander defaultExpander = new GlobPatternExpander();
        assertThat(defaultExpander).isNotNull();
        assertThat(defaultExpander.isGlobPattern("*.java")).isTrue();
    }

    @Test
    void defaultConstructor_shouldHaveNoOpWarningLogger() {
        // Create a subclass using default constructor that throws IOException
        GlobPatternExpander throwingExpander = new GlobPatternExpander() {
            @Override
            protected void walkFileTree(Path startDir, java.nio.file.PathMatcher matcher, List<File> matchedFiles) throws IOException {
                throw new IOException("Test error");
            }
        };

        // This should not throw and should silently ignore the warning (no-op logger)
        List<File> result = throwingExpander.expand("*.java", tempDir.toFile());
        assertThat(result).isEmpty();
    }

    @Test
    void expand_withDefaultConstructor_shouldWork() throws IOException {
        GlobPatternExpander defaultExpander = new GlobPatternExpander();
        createFile("test.java");

        List<File> result = defaultExpander.expand("*.java", tempDir.toFile());

        assertThat(result).hasSize(1);
    }

    private void createFile(String path) throws IOException {
        Path filePath = tempDir.resolve(path);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, "content");
    }
}
