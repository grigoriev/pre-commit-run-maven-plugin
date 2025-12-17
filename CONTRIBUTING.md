# Contributing

Thank you for your interest in contributing to pre-commit-run-maven-plugin!

## Development Setup

### Prerequisites

- Java 17+
- Maven 3.6+
- [pre-commit](https://pre-commit.com/) (for integration tests)

### Building

```bash
# Build and run tests
mvn clean verify

# Build without tests
mvn clean package -DskipTests

# Run a single test
mvn test -Dtest=PreCommitRunMojoTest
```

### Code Coverage

```bash
# Generate coverage report
mvn clean verify
# Report at: target/site/jacoco/index.html
```

## Pull Request Process

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Ensure tests pass (`mvn clean verify`)
5. Commit your changes (`git commit -m 'feat: add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

## Commit Messages

This project uses [Commitizen](https://commitizen-tools.github.io/commitizen/) with [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` - new feature
- `fix:` - bug fix
- `docs:` - documentation changes
- `test:` - adding or updating tests
- `refactor:` - code refactoring
- `chore:` - maintenance tasks

Commit messages are validated by pre-commit hook. Install hooks:

```bash
pre-commit install --hook-type commit-msg
```

## Code Style

This project uses Checkstyle with a customized Google style:

- 4-space indentation
- 160 character line limit
- Run `mvn checkstyle:check` to verify

Additional guidelines:
- Follow existing code patterns
- Maintain 100% test coverage for new code
- Add Javadoc for public APIs

## Reporting Issues

- Use GitHub Issues
- Include steps to reproduce
- Include Maven and Java versions
