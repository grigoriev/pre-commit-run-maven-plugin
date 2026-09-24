# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- The version bump moves the Unreleased entries of this changelog into a section for
  the new version. The GitHub release takes its notes from that section.
- The release workflow tests and publishes the same tree: `mvn clean deploy` runs the unit
  and integration tests before the upload, instead of a separate test job and a deploy
  with `-DskipTests`.
- Align the repository with the shared baseline: workflow values reach `run:` through
  `env:`, CI jobs have time limits, the version bumps push without stored credentials,
  a release run fails when the release exists already, and all text files use LF.
- Renovate updates the hashed pre-commit requirements of CI again: the file header
  uses only options Renovate supports, and the shared preset finds the file.

## [1.1.0] - 2026-09-24

### Added
- Checkstyle integration with Google style (4-space indentation, 160 char line limit)
- Commitizen configuration for conventional commits
- CONTRIBUTING.md with development guidelines
- Disclaimer section in the README
- Test on Java 25 next to 17 and 21

### Changed
- Suppressed log output during tests for cleaner test runs
- Create the GitHub release with `gh release create` instead of a third-party action
- Renovate takes its common rules from the shared preset `github>grigoriev/renovate-config`, which also turns on OSV vulnerability alerts.

### Fixed
- Run CI once per commit on Renovate branches: `renovate/**` is no longer in the push
  trigger, so a failing push run of the sonar check no longer blocks automerge
- A patch release publishes the development version as it stands, instead of skipping
  a version
- A patch release after a failed snapshot bump takes the next version instead of
  failing on the existing tag
- A rerun of the release workflow uploads the files to the existing release instead
  of failing
- Name the JDK 25 setup steps after the version they install

### Security
- Harden the GitHub Actions workflows: least-privilege token permissions, no persisted
  checkout credentials, template values passed through `env:`, no dependency cache in the
  release workflow
- Add an actionlint and zizmor `lint` job to CI
- Install pre-commit in CI from a hash-pinned requirements file
- Add the OpenSSF Scorecard workflow and badge
- Let Renovate pin GitHub Actions by commit digest
- Attach the pom and a signed build provenance bundle (`*.intoto.jsonl`) to each GitHub release

## [1.0.0] - 2025-12-16

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

## [0.2.0] - 2025-12-16

### Added
- Support for running multiple hooks sequentially with `hooks` parameter
- Support for passing environment variables to pre-commit process
- Automatic version bump to SNAPSHOT after release

### Fixed
- Stream handling blocking issue in PreCommitRunner
- Windows temp directory cleanup issues in tests

## [0.1.1] - 2025-12-15

### Added
- Initial release
- Run pre-commit hooks on specified files during Maven build
- Configurable behavior for file modifications
- Graceful degradation when pre-commit is not installed
- Validation of hook existence in `.pre-commit-config.yaml`
- Support for custom pre-commit executable path

[Unreleased]: https://github.com/grigoriev/pre-commit-run-maven-plugin/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/grigoriev/pre-commit-run-maven-plugin/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/grigoriev/pre-commit-run-maven-plugin/compare/v0.2.0...v1.0.0
[0.2.0]: https://github.com/grigoriev/pre-commit-run-maven-plugin/compare/v0.1.1...v0.2.0
[0.1.1]: https://github.com/grigoriev/pre-commit-run-maven-plugin/releases/tag/v0.1.1
