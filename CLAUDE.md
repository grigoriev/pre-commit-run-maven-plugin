# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Maven plugin for running pre-commit hooks during Maven build lifecycle. Allows executing any pre-commit hook on specified files at any build phase.

## Build Commands

```bash
# Build and run tests
mvn clean test

# Build with coverage report
mvn clean verify

# Build and install locally
mvn clean install

# Run a single test class
mvn test -Dtest=PreCommitConfigParserTest

# Run a single test method
mvn test -Dtest=PreCommitRunMojoTest#execute_shouldSkipWhenSkipIsTrue

# Generate coverage report
mvn jacoco:report
# Report at: target/site/jacoco/index.html

# Package without tests
mvn package -DskipTests
```

## Architecture

### Main Components

- `PreCommitRunMojo` - Main Maven plugin goal (`pre-commit:run`). Configures and orchestrates hook execution.
- `PreCommitRunner` - Executes pre-commit commands via ProcessBuilder, handles timeouts (configurable) and output capture.
- `PreCommitConfigParser` - Parses `.pre-commit-config.yaml` using SnakeYAML to verify hook existence.

### Exit Code Handling

- `0` - Hook passed, no changes made
- `1` - Hook modified files (configurable to fail or succeed)
- `>1` - Hook failed

### Plugin Configuration Options

Key parameters in `PreCommitRunMojo`:
- `hookId` (required) - The pre-commit hook ID to run
- `files` (required) - List of files to run the hook on (relative paths). Execution skipped if empty.
- `failOnModification` - Whether to fail build when hook modifies files (default: false)
- `skipIfHookNotFound`, `skipIfConfigNotFound`, `skipIfNotInstalled` - Graceful degradation options (default: true)
- `preCommitExecutable` - Path to pre-commit executable (default: "pre-commit")

## Testing

Tests use JUnit 5, Mockito for mocking, and AssertJ for assertions. Coverage is measured with JaCoCo.

Key test classes:
- `PreCommitRunMojoTest` - Tests plugin execution logic with mocked dependencies
- `PreCommitRunnerTest` - Tests process execution including timeouts and interrupts
- `PreCommitConfigParserTest` - Tests YAML parsing edge cases
