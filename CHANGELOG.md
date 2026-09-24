# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Checkstyle integration with Google style (4-space indentation, 160 char line limit)
- Commitizen configuration for conventional commits
- CONTRIBUTING.md with development guidelines
- Disclaimer section in the README
- Test on Java 25 next to 17 and 21

### Changed
- Suppressed log output during tests for cleaner test runs
- Create the GitHub release with `gh release create` instead of a third-party action

### Fixed
- Run CI once per commit on Renovate branches: `renovate/**` is no longer in the push
  trigger, so a failing push run of the sonar check no longer blocks automerge
- A patch release publishes the development version as it stands, instead of skipping
  a version
- A patch release after a failed snapshot bump takes the next version instead of
  failing on the existing tag
- Name the JDK 25 setup steps after the version they install

### Security
- Harden the GitHub Actions workflows: least-privilege token permissions, no persisted
  checkout credentials, template values passed through `env:`, no dependency cache in the
  release workflow
- Add an actionlint and zizmor `lint` job to CI
- Add the OpenSSF Scorecard workflow and badge
- Let Renovate pin GitHub Actions by commit digest

## [1.0.0] - 2024-12-16

### Added
- Support for glob patterns in file paths (e.g., `src/**/*.java`)
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
