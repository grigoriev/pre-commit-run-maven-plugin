# TODO

## High Priority

### Testing
- [x] Add integration tests with real pre-commit installation
- [x] Add Windows CI/CD testing (current tests use Unix commands)

### Features
- [ ] Support glob patterns for files (e.g., `src/**/*.java`)
- [ ] Support environment variables for subprocess

### Documentation
- [ ] Add troubleshooting section to README

## Medium Priority

### Code Quality
- [x] Fix silent exception swallowing in `PreCommitConfigParser.java` - add logging or return detailed error info
- [x] Fix early return on first missing file in `PreCommitRunMojo.java:148-156` - collect and report all missing files
- [ ] Add configurable timeout parameters to Mojo

### CI/CD
- [ ] Add SBOM (Software Bill of Materials) generation to release workflow

### Testing
- [x] Add concurrent execution tests (plugin is marked `threadSafe=true` but not tested)

## Low Priority

### Code Quality
- [x] Fix double newlines in output handling (`PreCommitRunner.java:128`)
- [x] Convert `Result` class to Java record for immutability

### Documentation
- [ ] Add use case examples to README (formatting JSON/YAML, running linters, multi-hook execution)
- [ ] Add performance documentation (timeout defaults, tuning guidance)
