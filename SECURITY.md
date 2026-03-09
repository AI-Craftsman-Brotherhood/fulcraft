# Security Policy

Thank you for helping keep the FUL project secure. Please follow the guidance below to report vulnerabilities responsibly.

## Reporting a Vulnerability

**Preferred channel**:
- **GitHub Security Advisories**: Use the repository's "Report a vulnerability" button under the Security tab.

**Alternative channel**:
- **Email**: support@craftsmann-bro.com

If you are unsure whether your issue is security-related, please use the preferred channel so we can triage it appropriately.

## What to Include

Please include as much of the following as possible when reporting an issue:
- A clear description of the issue and its impact.
- Steps to reproduce (including a minimal Proof of Concept if available).
- Affected versions or commit hashes.
- Any relevant logs, stack traces, or environment details.
- Suggested remediation or mitigation (if known).

## Response Targets (SLA)

We aim to:
- Acknowledge receipt within **48 hours**.
- Provide an initial assessment within **7 days**.
- Share a remediation plan or request more information as needed.

These timelines may vary for complex issues, but we will keep you informed of progress.

## Access Control

- MFA is required for all members with repository access (contributors, reviewers, maintainers).
- Protected branches require pull requests; direct pushes are strictly prohibited.

## Roles and Least Privilege

We follow a least-privilege policy:

- **Contributor**: Read access, able to open PRs, no direct push to protected branches.
- **Reviewer**: Read access plus PR review rights.
- **Maintainer**: Write access for releases, CI settings, and repository administration.

Access should be granted only as needed and reviewed periodically.

## Protected Branches and Required Checks

Protected branches (e.g., `main`) enforce the following constraints:

- No direct pushes; changes must be made via Pull Requests.
- Minimum of **two approvals** for changes to release, security, or CI configurations.
- Required status checks must pass before merging, including:
  - `CI / Build`
  - `CI / Check` (Static Analysis)
  - `Dependency Review` / `Dependency-Check`

See `docs/governance.md` for our overarching security workflow and checklist.

## Review Process

- All changes require review by at least one maintainer.
- Security-related changes should have at least two approvals whenever possible.
- CI quality gates must pass before merge, including vulnerability and secret scanning validations (see `QUALITY_GATES.md`).

## Secret Management

- **Do not** commit secrets, credentials, or API keys to the repository.
- Store secrets in environment variables or a secret manager (e.g., GitHub Actions Secrets for CI).
- If there is any risk of exposure, rotate credentials immediately.

## Responsible Disclosure

We follow a coordinated disclosure process:
- Please allow us time to investigate and release a fix before public disclosure.
- We target a 90-day disclosure window from the initial report, where feasible.
- If timelines need to be adjusted, we will coordinate with you promptly.

## Supported Versions

We provide security updates for the following:

| Version | Supported |
| ------- | --------- |
| `latest`| ✅ Yes    |
| `< latest`| ❌ No     |

If you are using an older version, please upgrade to the latest release to receive security fixes.

## Safe Harbor

We support good-faith security research. We will not pursue legal action against researchers who:
- Make a good-faith effort to avoid privacy violations, data destruction, and service disruption.
- Only access data necessary to demonstrate the issue.
- Do not exploit the vulnerability beyond what is required for a proof of concept.
- Give us a reasonable time to remediate the vulnerability before public disclosure.

If you have concerns about the scope of this policy, please contact us via the preferred channel before starting your research.
