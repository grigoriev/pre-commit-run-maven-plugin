# Security policy

## Reporting a vulnerability

Report a vulnerability privately through GitHub:
https://github.com/grigoriev/pre-commit-run-maven-plugin/security/advisories/new
(the **Security** tab, **Report a vulnerability**). Do not open a public issue for it.

We answer within a week. The fix goes into the next release, and its release notes name it.

## Supported versions

Only the latest release gets fixes.

## Scope

The plugin code in `src/main`, the build files, the scripts and the workflows belong to this
repository.

Vulnerabilities in upstream software (Maven, SnakeYAML, pre-commit and the hooks it runs) belong
to the upstream project. Tell us as well if this project is affected, so we can release a fix
when the upstream fix is out.
