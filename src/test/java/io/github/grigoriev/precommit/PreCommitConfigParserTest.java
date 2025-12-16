package io.github.grigoriev.precommit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PreCommitConfigParserTest {

    private PreCommitConfigParser parser;

    @BeforeEach
    void setUp() {
        parser = new PreCommitConfigParser();
    }

    @Test
    void isHookConfigured_shouldFindExistingHook() {
        String yaml = """
                repos:
                  - repo: https://github.com/pre-commit/pre-commit-hooks
                    rev: v4.4.0
                    hooks:
                      - id: trailing-whitespace
                      - id: end-of-file-fixer
                  - repo: https://github.com/pre-commit/mirrors-prettier
                    rev: v3.0.0
                    hooks:
                      - id: prettier
                """;

        assertThat(parser.isHookConfigured(toInputStream(yaml), "trailing-whitespace")).isTrue();
        assertThat(parser.isHookConfigured(toInputStream(yaml), "end-of-file-fixer")).isTrue();
        assertThat(parser.isHookConfigured(toInputStream(yaml), "prettier")).isTrue();
    }

    @Test
    void isHookConfigured_shouldReturnFalseForMissingHook() {
        String yaml = """
                repos:
                  - repo: https://github.com/pre-commit/pre-commit-hooks
                    rev: v4.4.0
                    hooks:
                      - id: trailing-whitespace
                """;

        assertThat(parser.isHookConfigured(toInputStream(yaml), "non-existent-hook")).isFalse();
    }

    @Test
    void isHookConfigured_shouldHandleEmptyConfig() {
        assertThat(parser.isHookConfigured(toInputStream(""), "any-hook")).isFalse();
        assertThat(parser.isHookConfigured(toInputStream("repos:"), "any-hook")).isFalse();
    }

    @Test
    void isHookConfigured_shouldHandleNullInputs() {
        assertThat(parser.isHookConfigured((java.io.InputStream) null, "hook")).isFalse();
        assertThat(parser.isHookConfigured(toInputStream("repos:"), null)).isFalse();
        assertThat(parser.isHookConfigured(toInputStream("repos:"), "")).isFalse();
    }

    @Test
    void isHookConfigured_shouldHandleInvalidYaml() {
        String invalidYaml = "not: valid: yaml: content";
        assertThat(parser.isHookConfigured(toInputStream(invalidYaml), "hook")).isFalse();
    }

    @Test
    void isHookConfigured_shouldHandleMalformedStructure() {
        // repos is not a list
        String yaml1 = "repos: not-a-list";
        assertThat(parser.isHookConfigured(toInputStream(yaml1), "hook")).isFalse();

        // hooks is not a list
        String yaml2 = """
                repos:
                  - repo: https://example.com
                    hooks: not-a-list
                """;
        assertThat(parser.isHookConfigured(toInputStream(yaml2), "hook")).isFalse();

        // repo entry is not a map
        String yaml3 = """
                repos:
                  - just-a-string
                """;
        assertThat(parser.isHookConfigured(toInputStream(yaml3), "hook")).isFalse();
    }

    @Test
    void isHookConfigured_withFile_shouldWork(@TempDir Path tempDir) throws IOException {
        String yaml = """
                repos:
                  - repo: https://github.com/pre-commit/pre-commit-hooks
                    rev: v4.4.0
                    hooks:
                      - id: pretty-format-json
                        args: [--autofix, --indent, '2']
                """;

        File configFile = tempDir.resolve(".pre-commit-config.yaml").toFile();
        Files.writeString(configFile.toPath(), yaml);

        assertThat(parser.isHookConfigured(configFile, "pretty-format-json")).isTrue();
        assertThat(parser.isHookConfigured(configFile, "other-hook")).isFalse();
    }

    @Test
    void isHookConfigured_withNonExistentFile_shouldReturnFalse(@TempDir Path tempDir) {
        File nonExistent = tempDir.resolve("non-existent.yaml").toFile();
        assertThat(parser.isHookConfigured(nonExistent, "hook")).isFalse();
    }

    @Test
    void isHookConfigured_withNullFile_shouldReturnFalse() {
        assertThat(parser.isHookConfigured((File) null, "hook")).isFalse();
    }

    @Test
    void isHookConfigured_shouldHandleHookNotBeingMap() {
        // hook entry is not a map (just a string)
        String yaml = """
                repos:
                  - repo: https://example.com
                    hooks:
                      - just-a-string
                      - id: valid-hook
                """;
        assertThat(parser.isHookConfigured(toInputStream(yaml), "just-a-string")).isFalse();
        assertThat(parser.isHookConfigured(toInputStream(yaml), "valid-hook")).isTrue();
    }

    @Test
    void isHookConfigured_shouldHandleEmptyRepos() {
        String yaml = "repos: []";
        assertThat(parser.isHookConfigured(toInputStream(yaml), "hook")).isFalse();
    }

    @Test
    void isHookConfigured_shouldHandleEmptyHooks() {
        String yaml = """
                repos:
                  - repo: https://example.com
                    hooks: []
                """;
        assertThat(parser.isHookConfigured(toInputStream(yaml), "hook")).isFalse();
    }

    @Test
    void isHookConfigured_shouldHandleRepoWithoutHooks() {
        String yaml = """
                repos:
                  - repo: https://example.com
                    rev: v1.0.0
                """;
        assertThat(parser.isHookConfigured(toInputStream(yaml), "hook")).isFalse();
    }

    @Test
    void isHookConfigured_shouldHandleIOException(@TempDir Path tempDir) {
        // Create a directory with the same name - reading it will cause IOException
        File configDir = tempDir.resolve(".pre-commit-config.yaml").toFile();
        configDir.mkdir();

        assertThat(parser.isHookConfigured(configDir, "hook")).isFalse();
    }

    @Test
    void isHookConfigured_shouldFindHookByAlias() {
        String yaml = """
                repos:
                  - repo: https://github.com/pre-commit/pre-commit-hooks
                    rev: v5.0.0
                    hooks:
                      - id: pretty-format-json
                        alias: pretty-format-openapi-json
                        args: [--autofix]
                      - id: mixed-line-ending
                        alias: mixed-line-ending-openapi
                        args: [--fix=lf]
                """;

        // Should find by alias
        assertThat(parser.isHookConfigured(toInputStream(yaml), "pretty-format-openapi-json")).isTrue();
        assertThat(parser.isHookConfigured(toInputStream(yaml), "mixed-line-ending-openapi")).isTrue();

        // Should still find by id
        assertThat(parser.isHookConfigured(toInputStream(yaml), "pretty-format-json")).isTrue();
        assertThat(parser.isHookConfigured(toInputStream(yaml), "mixed-line-ending")).isTrue();

        // Should not find non-existent
        assertThat(parser.isHookConfigured(toInputStream(yaml), "non-existent")).isFalse();
    }

    @Test
    void isHookConfigured_shouldHandleHookWithoutAlias() {
        String yaml = """
                repos:
                  - repo: https://github.com/pre-commit/pre-commit-hooks
                    rev: v5.0.0
                    hooks:
                      - id: trailing-whitespace
                """;

        assertThat(parser.isHookConfigured(toInputStream(yaml), "trailing-whitespace")).isTrue();
        assertThat(parser.isHookConfigured(toInputStream(yaml), "some-alias")).isFalse();
    }

    private ByteArrayInputStream toInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
