# Quality Gates

This document describes the rollout plan for static analysis, code quality enforcement, and vulnerability checks across the FUL project repository.

## Goal

- Raise the minimum quality bar while keeping the project stable and build times reasonable.
- Make violations visible and actionable for all contributors.
- Prevent regressions by gating pull requests and tracking baseline measurements.
- Cover secret scanning, dependency vulnerability scanning, and automated dependency updates.

## Phased Rollout

### Phase 1: Baseline Tracking (Current)

- Run Spotless, Checkstyle, SpotBugs, PMD, and Dependency-Check via Gradle (`./gradlew check`) in CI and generate local reports.
- Compare static analysis results against the baseline using the provided script `scripts/ci/compare_static_analysis.sh` (warning-only).
- Generate a suppression inventory from Java annotations/comments (`@SuppressWarnings`) and track it via `scripts/ci/suppression_inventory.sh` and `scripts/ci/compare_suppression_baseline.sh`.
- Use the baseline to track overall progress while continuously burning down existing issues.
- Run secret scanning locally with your preferred tooling and address any findings before opening a PR.

### Phase 2: Strict Enforcement

- Remove `continue-on-error: true` from the static analysis and Dependency-Check CI workflow steps.
- Fail the build on any new violations so regressions do not land on the `main` branch.
- Treat tool violations as release blockers unless explicitly waived or suppressed with a proper documented reason.
- Enforce the Dependency-Check fail threshold at CVSS 8.0+.
- Require secret scanning to pass for all commits and pull requests.
- Turn on strict baseline enforcement: run `scripts/ci/compare_static_analysis.sh --fail-on-new`.
- Fail CI when new suppressions are added without proper justification (`scripts/ci/compare_suppression_baseline.sh --fail-on-new`).

## Baseline Updates

The legacy code is checked against established baselines to prevent further decay.

- Baseline JSON files (e.g., `checkstyle_baseline.json`, `pmd_baseline.json`, `spotbugs_baseline.json`) live in the `baseline/` directory.
- Update baselines **only** on the `main` branch after explicitly regenerating reports and resolving issues:
  ```bash
  bash scripts/ci/update_static_analysis_baseline.sh
  ```
- The suppression baseline is stored in `baseline/suppressions_baseline.json`.
- Update the suppression baseline **only** on the `main` branch:
  ```bash
  bash scripts/ci/update_suppression_baseline.sh
  ```
- **Important**: Pull Requests should only compare against the baseline; they should *never* refresh or overwrite it, unless the PR's explicit purpose is to burn down existing technical debt.

## Quality Gate Alignment

- **SAFE mode**: Can keep `allow_warnings: true` and baseline comparison as "warning-only".
- **FULL mode**: Can set `allow_warnings: false` and use `--fail-on-new` to enforce the baseline aggressively.

## Dependency Updates

- Dependabot runs regularly to propose Gradle, dependency, and GitHub Actions updates.
- High and Critical security advisories flagged by Dependabot or Dependency-Check should be triaged and addressed within one week.

## Relevant Documentation

Refer to the following documents for more details on resolving specific issues:

- `docs/test-quality-guidelines.md` – Strategies for writing hermetic, valuable tests and improving code coverage.
- `docs/security-scanning.md` – Details on how security vulnerabilities are detected in the pipeline.
- `CONTRIBUTING.md` – How to apply Spotless and standard formatting rules locally before committing.

## How to Apply Changes

- Update `.github/workflows/ci.yml` (and other workflow files) to adjust thresholds or switch between Phase 1 and Phase 2.
- Ensure that CI configurations reflect the conventions defined and checked locally through the Gradle build files within `/app`.
