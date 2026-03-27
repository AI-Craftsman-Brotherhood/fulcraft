# Quality Gates

This document describes the rollout plan for static analysis, code quality enforcement, and vulnerability checks across the fulcraft project repository.

## Goal

- Raise the minimum quality bar while keeping the project stable and build times reasonable.
- Make violations visible and actionable for all team members.
- Prevent regressions by gating pull requests and tracking baseline measurements.
- Cover secret scanning, dependency vulnerability scanning, and automated dependency updates.

## Phased Rollout

### Phase 1: Baseline Tracking (Complete)

- Run Spotless, Checkstyle, SpotBugs, PMD, and Dependency-Check via Gradle (`./gradlew check`) in CI and generate local reports.
- Baseline JSON files in the `baseline/` directory track legacy violations and enable progress measurement.
- Use the baseline to track overall progress while continuously burning down existing issues.
- Run secret scanning locally with your preferred tooling and address any findings before opening a PR.

### Phase 2: Strict Enforcement (Current)

- Static analysis steps (Spotless, Checkstyle, SpotBugs, PMD) fail the CI build on any violation.
- Dependency-Check runs without `continue-on-error`, failing the build on vulnerability findings.
- Treat tool violations as release blockers unless explicitly waived or suppressed with a proper documented reason.
- Enforce the Dependency-Check fail threshold at CVSS 8.0+.
- Require secret scanning to pass for all commits and pull requests.

### Phase 3: Automated Baseline Enforcement (Planned)

- Implement CI scripts for automated baseline comparison (e.g., `scripts/ci/compare_static_analysis.sh --fail-on-new`).
- Implement suppression inventory tracking (e.g., `scripts/ci/suppression_inventory.sh`, `scripts/ci/compare_suppression_baseline.sh --fail-on-new`).
- Fail CI when new suppressions are added without proper justification.

## Baseline Updates

The legacy code is checked against established baselines to prevent further decay.

- Baseline JSON files (`checkstyle_baseline.json`, `pmd_baseline.json`, `spotbugs_baseline.json`) live in the `baseline/` directory.
- The suppression baseline is stored in `baseline/suppressions_baseline.json`.
- Update baselines **only** on the `main` branch after explicitly regenerating reports and resolving issues.
- **Important**: Pull Requests should only compare against the baseline; they should *never* refresh or overwrite it, unless the PR's explicit purpose is to burn down existing technical debt.

## Quality Gate Alignment

- **SAFE mode**: Baseline comparison as "warning-only"; static analysis violations do not block CI.
- **FULL mode** (current): Static analysis violations fail CI; baseline enforcement blocks regressions.

## Dependency Updates

- Dependabot runs regularly to propose Gradle, dependency, and GitHub Actions updates.
- High and Critical security advisories flagged by Dependabot or Dependency-Check should be triaged and addressed within one week.

## Relevant Documentation

Refer to the following documents for more details on resolving specific issues:

- `docs/security-scanning.md` – Details on how security vulnerabilities are detected in the pipeline.
- `CONTRIBUTING.md` – How to apply Spotless and standard formatting rules locally before committing.

## How to Apply Changes

- Update `.github/workflows/ci.yml` (and other workflow files) to adjust thresholds or toggle between SAFE and FULL modes.
- Ensure that CI configurations reflect the conventions defined and checked locally through the Gradle build files within `/app`.
