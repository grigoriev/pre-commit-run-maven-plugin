# pre-commit-run-maven-plugin

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=grigoriev_pre-commit-run-maven-plugin&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=grigoriev_pre-commit-run-maven-plugin)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=grigoriev_pre-commit-run-maven-plugin&metric=coverage)](https://sonarcloud.io/summary/new_code?id=grigoriev_pre-commit-run-maven-plugin)

Maven plugin for running [pre-commit](https://pre-commit.com/) hooks during the Maven build lifecycle.

## Features

- Run any pre-commit hook on specified files at any Maven build phase
- Support for glob patterns in file paths (e.g., `src/**/*.java`)
- Support for hook aliases (run hooks by alias instead of ID)
- Graceful degradation when pre-commit is not installed
- Configurable behavior for file modifications
- Validates hook existence in `.pre-commit-config.yaml`

## Usage

```xml
<plugin>
    <groupId>io.github.grigoriev</groupId>
    <artifactId>pre-commit-run-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <id>format-openapi-json</id>
            <phase>process-resources</phase>
            <goals>
                <goal>run</goal>
            </goals>
            <configuration>
                <hooks>
                    <hook>mixed-line-ending</hook>
                    <hook>pretty-format-json</hook>
                </hooks>
                <files>
                    <file>docs/openapi.json</file>
                </files>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Hooks are executed in order. If any hook fails, subsequent hooks are skipped.

### Using Glob Patterns

You can use glob patterns to match multiple files:

```xml
<configuration>
    <hooks>
        <hook>trailing-whitespace</hook>
    </hooks>
    <files>
        <file>src/**/*.java</file>
        <file>docs/*.md</file>
    </files>
</configuration>
```

Supported glob patterns:
- `*` - matches any characters except path separator
- `**` - matches any characters including path separators (recursive)
- `?` - matches a single character
- `[abc]` - matches any character in brackets

### Using Hook Aliases

You can run hooks by their alias instead of ID. This is useful when you have multiple configurations of the same hook:

```yaml
# .pre-commit-config.yaml
repos:
  - repo: https://github.com/pre-commit/pre-commit-hooks
    rev: v5.0.0
    hooks:
      - id: pretty-format-json
        alias: pretty-format-openapi
        args: [--autofix, '--top-keys=openapi,info,servers,paths,components']
        files: docs/openapi.json
      - id: mixed-line-ending
        alias: mixed-line-ending-openapi
        args: [--fix=lf]
        files: docs/openapi.json
```

```xml
<configuration>
    <hooks>
        <hook>mixed-line-ending-openapi</hook>
        <hook>pretty-format-openapi</hook>
    </hooks>
    <files>
        <file>docs/openapi.json</file>
    </files>
</configuration>
```

### With Environment Variables

Useful for controlling Git behavior on Windows (line endings issue):

```xml
<configuration>
    <hooks>
        <hook>pretty-format-json</hook>
    </hooks>
    <files>
        <file>docs/openapi.json</file>
    </files>
    <environmentVariables>
        <GIT_CONFIG_PARAMETERS>'core.autocrlf=false'</GIT_CONFIG_PARAMETERS>
    </environmentVariables>
</configuration>
```

## Configuration Options

| Parameter | Default | Description |
|-----------|---------|-------------|
| `hooks` | (required) | List of hook IDs or aliases to run sequentially |
| `files` | (required) | List of files or glob patterns to run the hook on (relative to project root) |
| `skip` | `false` | Skip execution entirely |
| `failOnModification` | `false` | Fail the build if the hook modifies files |
| `skipIfHookNotFound` | `true` | Skip if hook is not in `.pre-commit-config.yaml` |
| `skipIfConfigNotFound` | `true` | Skip if `.pre-commit-config.yaml` doesn't exist |
| `skipIfNotInstalled` | `true` | Skip if pre-commit is not installed |
| `preCommitExecutable` | `pre-commit` | Path to pre-commit executable |
| `environmentVariables` | (none) | Additional environment variables for the pre-commit process |

## Exit Codes

The plugin interprets pre-commit exit codes as follows:

- `0` - Hook passed, no changes made
- `1` - Hook modified files (success unless `failOnModification=true`)
- `>1` - Hook failed (always fails the build)

## Building

```bash
# Build and run tests
mvn clean verify

# Install locally
mvn clean install
```

## Requirements

- Java 17+
- Maven 3.6+
- [pre-commit](https://pre-commit.com/) installed and in PATH

## License

MIT
