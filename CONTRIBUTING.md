# Contributing

Issues and pull requests are welcome.

## Prerequisites

- Java 17+
- Maven 3.6+
- [pre-commit](https://pre-commit.com/) (for the integration tests)

## Build and test

```sh
mvn clean verify                      # build, unit and integration tests, coverage report
mvn clean package -DskipTests         # build without tests
mvn test -Dtest=PreCommitRunMojoTest  # run a single test class
mvn checkstyle:check                  # check the code style
```

The coverage report is at `target/site/jacoco/index.html`. The integration tests skip themselves
when pre-commit is not installed.

CI runs actionlint, zizmor, the tests on Linux and Windows with Java 17, 21 and 25, the
integration tests and SonarCloud for every pull request.

## Pull requests

1. Branch from the default branch as `type/description`, for example `fix/empty-title`.
2. Keep one change per pull request. New behavior comes with tests; a bug fix adds a test that
   fails without it.
3. Write commit messages as [Conventional Commits](https://www.conventionalcommits.org/) without a
   scope: `feat: ...`, `fix: ...`, `docs: ...`, `refactor: ...`, `test: ...`, `build: ...`,
   `ci: ...`, `chore: ...`. A commitizen pre-commit hook checks them; install it with
   `pre-commit install --hook-type commit-msg`.
4. Sign your commits. The default branch accepts verified signatures only.
5. Add an entry under `## [Unreleased]` in `CHANGELOG.md`, written for users: the release notes
   quote it. Update the README when behavior or configuration changes.

Pull requests are squash-merged once all required checks are green.

## Code style

Checkstyle with a customized Google style: 4-space indentation, 160 character line limit. Follow
the existing code patterns, keep new code at 100% test coverage, and add Javadoc for public APIs.

## Releases

A maintainer runs the Bump Version workflow. It moves the Unreleased entries into a versioned
section, tags the release, and the Release workflow tests the tagged tree, publishes it to Maven
Central and creates the GitHub release with signed build provenance.
