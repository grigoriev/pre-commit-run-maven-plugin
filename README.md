# pre-commit-run-maven-plugin

Maven plugin for running [pre-commit](https://pre-commit.com/) hooks during the Maven build lifecycle.

## Features

- Run any pre-commit hook on specified files at any Maven build phase
- Graceful degradation when pre-commit is not installed
- Configurable behavior for file modifications
- Validates hook existence in `.pre-commit-config.yaml`

## Usage

```xml
<plugin>
    <groupId>io.github.grigoriev</groupId>
    <artifactId>pre-commit-run-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <id>format-openapi-json</id>
            <phase>process-resources</phase>
            <goals>
                <goal>run</goal>
            </goals>
            <configuration>
                <hookId>pretty-format-json</hookId>
                <files>
                    <file>docs/openapi.json</file>
                </files>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Configuration Options

| Parameter | Default | Description |
|-----------|---------|-------------|
| `hookId` | (required) | The pre-commit hook ID to run |
| `files` | (empty) | List of files to run the hook on (relative to project root). If empty, runs on all files |
| `skip` | `false` | Skip execution entirely |
| `failOnModification` | `false` | Fail the build if the hook modifies files |
| `skipIfHookNotFound` | `true` | Skip if hook is not in `.pre-commit-config.yaml` |
| `skipIfConfigNotFound` | `true` | Skip if `.pre-commit-config.yaml` doesn't exist |
| `skipIfNotInstalled` | `true` | Skip if pre-commit is not installed |
| `preCommitExecutable` | `pre-commit` | Path to pre-commit executable |

## Exit Codes

The plugin interprets pre-commit exit codes as follows:

- `0` - Hook passed, no changes made
- `1` - Hook modified files (success unless `failOnModification=true`)
- `>1` - Hook failed (always fails the build)

## Requirements

- Java 17+
- Maven 3.6+
- [pre-commit](https://pre-commit.com/) installed and in PATH

## License

MIT
