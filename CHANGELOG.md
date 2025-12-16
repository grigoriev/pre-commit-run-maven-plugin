# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2024-12-16

### Added
- Support for hook aliases - run hooks by alias instead of ID
- Integration tests for line ending handling

### Changed
- **BREAKING**: Removed `hookId` parameter, use `hooks` list instead
- Simplified configuration with unified `hooks` parameter

### Migration from 0.x

Replace `hookId` with `hooks`:

```xml
<!-- Before (0.x) -->
<configuration>
    <hookId>pretty-format-json</hookId>
    <files>
        <file>docs/openapi.json</file>
    </files>
</configuration>

<!-- After (1.0.0) -->
<configuration>
    <hooks>
        <hook>pretty-format-json</hook>
    </hooks>
    <files>
        <file>docs/openapi.json</file>
    </files>
</configuration>
```

## [0.2.0] - 2024-12-15

### Added
- Support for running multiple hooks sequentially with `hooks` parameter
- Support for passing environment variables to pre-commit process
- Automatic version bump to SNAPSHOT after release

### Fixed
- Stream handling blocking issue in PreCommitRunner
- Windows temp directory cleanup issues in tests

## [0.1.0] - 2024-12-14

### Added
- Initial release
- Run pre-commit hooks on specified files during Maven build
- Configurable behavior for file modifications
- Graceful degradation when pre-commit is not installed
- Validation of hook existence in `.pre-commit-config.yaml`
- Support for custom pre-commit executable path

[Unreleased]: https://github.com/grigoriev/pre-commit-run-maven-plugin/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/grigoriev/pre-commit-run-maven-plugin/compare/v0.2.0...v1.0.0
[0.2.0]: https://github.com/grigoriev/pre-commit-run-maven-plugin/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/grigoriev/pre-commit-run-maven-plugin/releases/tag/v0.1.0
