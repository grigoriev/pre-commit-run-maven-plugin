# TODO

## Completed

### Testing
- [x] Add integration tests with real pre-commit installation
- [x] Add Windows CI/CD testing (current tests use Unix commands)
- [x] Achieve 100% test line coverage
- [x] Add concurrent execution tests (plugin is marked `threadSafe=true` but not tested)

### Features
- [x] Support glob patterns for files (e.g., `src/**/*.java`)
- [x] Support environment variables for subprocess
- [x] Support multiple hooks in single execution (`<hooks>` parameter)
- [x] Add configurable timeout parameters to Mojo

### Code Quality
- [x] Fix silent exception swallowing in `PreCommitConfigParser.java` - add logging
- [x] Fix early return on first missing file - collect and report all missing files
- [x] Fix double newlines in output handling
- [x] Convert `Result` class to Java record for immutability

### Documentation
- [x] Add troubleshooting section to README
- [x] Add use case examples to README
- [x] Add performance documentation (timeout defaults, tuning guidance)

## Future Improvements

### Code Quality
- [x] Add Checkstyle for code style enforcement

### Features
- [ ] Add `verbose` parameter for detailed output
- [ ] Add `help` goal (`mvn pre-commit:help`)

### Documentation
- [x] Add CHANGELOG.md with version history
- [x] Add CONTRIBUTING.md for contributors
