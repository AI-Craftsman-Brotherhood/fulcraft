#!/usr/bin/env bash
set -euo pipefail

JACOCO_XML="app/build/reports/jacoco/test/jacocoTestReport.xml"
JUNIT_XML_GLOB="app/build/test-results/test/*.xml"
CHECKSTYLE_XML="app/build/reports/checkstyle/main.xml"
SPOTBUGS_XML_GLOB="app/build/reports/spotbugs/*.xml"
OUTPUT_PATH="reports/quality_snapshot.md"

format_count() {
  if [[ -n "${1:-}" ]]; then
    printf "%s" "$1"
  else
    printf "N/A"
  fi
}

notes=()

mkdir -p "$(dirname "$OUTPUT_PATH")"

jacoco_coverage=""
jacoco_missed=""
jacoco_covered=""
if [[ -f "$JACOCO_XML" ]]; then
  read -r jacoco_missed jacoco_covered jacoco_found < <(
    awk -F'"' '
      /<counter / && /type="INSTRUCTION"/ {
        missed = ""
        covered = ""
        for (i = 1; i <= NF; i++) {
          if ($i == "missed") missed = $(i + 2)
          if ($i == "covered") covered = $(i + 2)
        }
        if (missed != "" && covered != "") {
          m = missed
          c = covered
          found = 1
        }
      }
      END {
        if (found == 1) {
          printf "%s %s 1", m, c
        } else {
          printf "0 0 0"
        }
      }
    ' "$JACOCO_XML"
  )
  jacoco_found=${jacoco_found:-0}

  if [[ "${jacoco_found}" -eq 1 ]]; then
    total=$((jacoco_missed + jacoco_covered))
    if ((total > 0)); then
      jacoco_coverage=$(awk -v covered="$jacoco_covered" -v total="$total" 'BEGIN {printf "%.2f", (covered/total)*100}')
    else
      notes+=("JaCoCo INSTRUCTION counter total is zero")
    fi
  else
    notes+=("JaCoCo INSTRUCTION counter not found")
  fi
else
  notes+=("JaCoCo XML not found")
fi

junit_tests=""
junit_failures=""
junit_errors=""
junit_skipped=""
test_status="N/A"

shopt -s nullglob
junit_files=($JUNIT_XML_GLOB)
shopt -u nullglob

if ((${#junit_files[@]} > 0)); then
  read -r junit_tests junit_failures junit_errors junit_skipped junit_seen < <(
    awk -F'"' '
      /<testsuite[ >]/ {
        seen = 1
        for (i = 1; i <= NF; i++) {
          if ($i == "tests") tests += $(i + 2)
          if ($i == "failures") failures += $(i + 2)
          if ($i == "errors") errors += $(i + 2)
          if ($i == "skipped") skipped += $(i + 2)
        }
      }
      END {
        printf "%d %d %d %d %d", tests, failures, errors, skipped, seen
      }
    ' "${junit_files[@]}"
  )
  junit_seen=${junit_seen:-0}

  if [[ "${junit_seen}" -eq 1 ]]; then
    failures_total=$((junit_failures + junit_errors))
    if ((failures_total == 0)); then
      test_status="PASS"
    else
      test_status="FAIL (${failures_total})"
    fi
  else
    notes+=("JUnit testsuite element not found")
    junit_tests=""
    junit_failures=""
    junit_errors=""
    junit_skipped=""
  fi
else
  notes+=("JUnit XML not found")
fi

checkstyle_warnings=""
if [[ -f "$CHECKSTYLE_XML" ]]; then
  checkstyle_warnings=$(awk 'BEGIN {count = 0} /<error[ >]/ {count++} END {print count}' "$CHECKSTYLE_XML")
else
  notes+=("Checkstyle XML not found")
fi

spotbugs_warnings=""
shopt -s nullglob
spotbugs_files=($SPOTBUGS_XML_GLOB)
shopt -u nullglob

if ((${#spotbugs_files[@]} > 0)); then
  spotbugs_warnings=$(awk 'BEGIN {count = 0} /<BugInstance[ >]/ {count++} END {print count}' "${spotbugs_files[@]}")
else
  notes+=("SpotBugs XML not found")
fi

{
  echo "# Quality Snapshot"
  echo ""
  echo "## Summary"
  echo ""
  if [[ -n "$jacoco_coverage" ]]; then
    echo "- Coverage (INSTRUCTION): ${jacoco_coverage}%"
  else
    echo "- Coverage (INSTRUCTION): N/A"
  fi
  echo "- Test status: ${test_status}"
  echo "- Tests: $(format_count "$junit_tests")"
  echo "- Failures: $(format_count "$junit_failures")"
  echo "- Errors: $(format_count "$junit_errors")"
  echo "- Skipped: $(format_count "$junit_skipped")"
  echo "- Checkstyle warnings: $(format_count "$checkstyle_warnings")"
  echo "- SpotBugs warnings: $(format_count "$spotbugs_warnings")"

  if ((${#notes[@]} > 0)); then
    echo ""
    echo "## Notes"
    echo ""
    for note in "${notes[@]}"; do
      echo "- ${note}"
    done
  fi

  echo ""
} > "$OUTPUT_PATH"
