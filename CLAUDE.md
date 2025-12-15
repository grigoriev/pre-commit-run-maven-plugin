# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Maven plugin for running pre-commit hooks during Maven build lifecycle. Allows executing any pre-commit hook on specified files at any build phase.

## Build Commands

```bash
# Build and run tests
mvn clean test

# Build and install locally
mvn clean install

# Run a single test class
mvn test -Dtest=PreCommitConfigParserTest

# Run a single test method
mvn test -Dtest=PreCommitRunMojoTest#execute_shouldSkipWhenSkipIsTrue

# Package without tests
mvn package -DskipTests
```

## Architecture

### Main Components

- `PreCommitRunMojo` - Main Maven plugin goal (`pre-commit:run`). Configures and orchestrates hook execution.
- `PreCommitRunner` - Executes pre-commit commands via ProcessBuilder, handles timeouts and output capture.
- `PreCommitConfigParser` - Parses `.pre-commit-config.yaml` using SnakeYAML to verify hook existence.

### Exit Code Handling

- `0` - Hook passed, no changes made
- `1` - Hook modified files (configurable to fail or succeed)
- `>1` - Hook failed

### Plugin Configuration Options

Key parameters in `PreCommitRunMojo`:
- `hookId` (required) - The pre-commit hook ID to run
- `files` - List of files to run the hook on (relative paths)
- `failOnModification` - Whether to fail build when hook modifies files (default: false)
- `skipIfHookNotFound`, `skipIfConfigNotFound`, `skipIfNotInstalled` - Graceful degradation options (default: true)
