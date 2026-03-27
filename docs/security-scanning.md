# Security Scanning

FUL follows a shift-left approach and runs automated security checks throughout the development lifecycle.
It also includes runtime protection features for sensitive data.

## 1. Automated CI Scanning

FUL uses GitHub Actions to run a layered security pipeline.

### SCA: Dependency Checks

These checks look for known vulnerabilities in OSS dependencies.

- **OWASP Dependency-Check**
  - Schedule: weekly in `dependency-check.yml`, plus manual runs
  - Behavior: compares dependencies against NVD data
  - Policy: `runtimeClasspath` is enforced in PR/main CI with CVSS 8.0 and above causing failure
  - Policy: build tooling configurations (`checkstyle`, `spotbugs`, `pmd`) are scanned in a separate weekly/manual job with independent reports
- **Dependabot**
  - Schedule: weekly
  - Behavior: opens update PRs for Gradle and GitHub Actions dependencies
  - **Dependency Review** blocks risky dependency changes during PR review

### Secret Scanning Prevention

This repository does not currently bundle a local pre-commit secret scanning setup.
If needed, add tools such as `git-secrets` or `detect-secrets` according to your local or organizational policy.

### SAST: Static Application Security Testing

These checks analyze source code directly for potential security issues.

- **GitHub CodeQL**
  - Language: Java
  - Detects: injection, unsafe deserialization, hardcoded credentials, and similar issues
  - Reporting: integrated into the GitHub Security tab
  - Execution: `codeql` job in `.github/workflows/ci.yml`
- **SpotBugs**
  - Detects: potential bugs, resource leaks, and null-related issues, including but not limited to security findings

### Code Hygiene

These checks support the code quality baseline that security depends on.

- **Spotless**: enforces formatting
- **Checkstyle**: enforces coding conventions
- **PMD**: detects dead code and inefficient implementations

---

## 2. Runtime Protections

FUL also protects sensitive user data at runtime.
For LLM transmission policy and prompt redaction, see [governance.md](governance.md).

### Secret Masking

Sensitive data in logs and CLI error output is automatically replaced with `****`.

Masked patterns include:

- API keys and bearer tokens (e.g. `x-api-key`, `Authorization: Bearer ...`)
- PEM private keys
- JWT-like dot-separated Base64 tokens
- High-entropy long random-looking strings

### Sensitive Logic Detection

Before the generate phase, FUL scans analyzed methods for security-sensitive operations and warns the user, especially when code might be sent to an external LLM.

- **Detection criteria**:
  - Method names containing terms such as `password`, `secret`, `token`, `credential`, `encrypt`, `decrypt`, `authenticate`, `authorize`, `apikey`, `privatekey`, `signin`, `signout`, `login`, `logout`
  - Annotations such as `@PreAuthorize`, `@PostAuthorize`, `@Secured`, `@RolesAllowed`, `@PermitAll`, `@DenyAll`, `@Encrypt`, `@Decrypt`
- **Behavior**: matching methods are flagged and warnings are emitted in logs and run output

---

## 3. Vulnerability Response Process

This is the standard flow when automated scanning detects a vulnerability.

1. **Detection and triage**
   - Review CI logs or the GitHub Security tab.
   - Determine whether the issue affects production code or only test dependencies.
2. **Remediation**
   - Upgrade to a fixed version when available.
   - Evaluate a safer replacement when no fix exists.
   - Add justified suppressions such as `suppression.xml` only when the finding is non-impacting or false positive.
3. **Emergency response**
   - If the CVSS score is high and the issue is practically exploitable, release a hotfix immediately.

---

## Related Files

- Workflow definitions: `.github/workflows/`
  - `build.yml`
  - `ci.yml`
  - `dependency-check.yml`
  - `dependency-review.yml`
  - `release.yml`
