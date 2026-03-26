# Test Quality Guidelines

These guidelines help keep tests in the FUL repository readable, resilient, and maintainable.

## Goals

- Detect regressions quickly when behavior changes.
- Make failures easy to diagnose.
- Keep the test suite reproducible in CI.

## Scope

- Primary target: `app/src/test/java/**`
- Fixtures: `sample-legacy-project/` or the smallest possible inputs created inside tests
- Main command: `./gradlew :app:test`

## Core Principles

- Verify observable behavior, not implementation details.
- Prefer one responsibility per test so failures are easy to interpret.
- Avoid order dependence, time dependence, and environment dependence.

## Naming Conventions

- Test class names should use `*Test.java`.
- Test method names should follow `doesX_whenY` or `shouldX_whenY`.
- Include both the condition and the expected outcome in the name.

## Test Case Design

- Cover at least one happy path, boundary case, and failure case.
- For analysis and reporting flows, prioritize these failure modes:
  - Missing input files
  - Invalid formats
  - Parse failures
  - Incomplete configuration
- CLI command tests should verify exit codes and major messages.

## External Dependencies

- Do not make real network calls or invoke external services.
- Replace dependencies with mocks, stubs, or fakes.
- Avoid waits based on `Thread.sleep()`.
- Use temporary directories such as `@TempDir` for file I/O.

## Assertion Policy

- Prioritize key result fields such as counts, status, and identifiers.
- Avoid full-string matches on long messages; assert meaningful fragments instead.
- Do not overload a single test with too many assertions.

## Quality Checks

Routine local checks:

```bash
./gradlew :app:test
./gradlew :app:spotlessApply :app:spotbugsMain :app:spotbugsTest
```

Report generation check when run artifacts already exist:

```bash
./gradlew :app:run --args="junit-report -p <project-root> --run-id <runId> --format json"
```

- `junit-report` aggregates reports from existing run artifacts such as tasks, results, and summary files.
- Coverage loading is integrated into the `report` package and can use `config.json` via `quality_gate.coverage_report_path`.
- If `--run-id` is omitted, the latest run is used.

## Pre-PR Checklist

1. Add or update tests for the changed behavior.
2. Ensure the tests pass locally in a reproducible way.
3. Leave a reason when changing existing test names, expectations, or I/O formats.
4. Explain the impact when changing report or quality-related settings such as `quality_gate.coverage_report_path`.

## Related Links

- [Quality Gates](../QUALITY_GATES.md)
- [Configuration Reference (`quality_gate`)](config.md)
- [CLI Guide](cli.md)
- [Troubleshooting](troubleshooting.md)
