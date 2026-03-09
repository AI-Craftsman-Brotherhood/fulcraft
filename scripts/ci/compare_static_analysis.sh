#!/usr/bin/env bash
set -euo pipefail

BASELINE_DIR="${BASELINE_DIR:-baseline}"
FAIL_ON_NEW=false

usage() {
  cat <<'USAGE'
Usage: scripts/ci/compare_static_analysis.sh [--fail-on-new] [--baseline-dir <path>]

Compares Checkstyle, SpotBugs, and PMD report counts against baseline JSON files.
By default it only warns; pass --fail-on-new to exit non-zero when new findings appear.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --fail-on-new)
      FAIL_ON_NEW=true
      shift
      ;;
    --baseline-dir)
      BASELINE_DIR="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

count_xml_metrics() {
  local file="$1"
  local tool="$2"
  if [[ ! -f "$file" ]]; then
    # Return "MISSING MISSING" for blocking and advisory
    echo "MISSING MISSING"
    return 0
  fi
  python3 - "$file" "$tool" <<'PY'
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1]
tool = sys.argv[2]
try:
    root = ET.parse(path).getroot()
except Exception:
    print("ERROR ERROR")
    sys.exit(0)

def local_name(name: str) -> str:
    return name.split("}", 1)[-1] if "}" in name else name

blocking = 0
advisory = 0

if tool == "Checkstyle":
    for elem in root.iter():
        if local_name(elem.tag) == "error":
            severity = elem.get("severity", "info")
            if severity == "error":
                blocking += 1
            else:
                advisory += 1
elif tool == "PMD":
    for elem in root.iter():
        if local_name(elem.tag) == "violation":
            try:
                priority = int(elem.get("priority", "3"))
                if priority <= 2:
                    blocking += 1
                else:
                    advisory += 1
            except ValueError:
                advisory += 1
elif tool == "SpotBugs":
    for elem in root.iter():
        if local_name(elem.tag) == "BugInstance":
            try:
                priority = int(elem.get("priority", "3"))
                if priority <= 2:
                    blocking += 1
                else:
                    advisory += 1
            except ValueError:
                advisory += 1

print(f"{blocking} {advisory}")
PY
}

read_baseline_count() {
  local file="$1"
  local key="$2"
  if [[ ! -f "$file" ]]; then
    echo "MISSING"
    return 0
  fi
  python3 - "$file" "$key" <<'PY'
import json
import sys

path = sys.argv[1]
key = sys.argv[2]
try:
    data = json.load(open(path))
except Exception:
    print("ERROR")
    sys.exit(0)
counts = data.get("counts", {})
value = counts.get(key)
if value is None:
    print("MISSING")
else:
    print(value)
PY
}

warn() {
  echo "WARN: $*" >&2
}

compare_tool() {
  local tool="$1"
  local baseline_file="$2"
  local main_report="$3"
  local test_report="$4"

  local main_metrics test_metrics
  main_metrics="$(count_xml_metrics "$main_report" "$tool")"
  test_metrics="$(count_xml_metrics "$test_report" "$tool")"

  local main_blocking="0"
  local main_advisory="0"
  local test_blocking="0"
  local test_advisory="0"

  if [[ "$main_metrics" == "MISSING MISSING" ]]; then
    warn "$tool main report missing: $main_report"
  elif [[ "$main_metrics" == "ERROR ERROR" ]]; then
    warn "$tool main report could not be parsed: $main_report"
  else
    read -r main_blocking main_advisory <<< "$main_metrics"
  fi

  if [[ "$test_metrics" == "MISSING MISSING" ]]; then
    warn "$tool test report missing: $test_report"
  elif [[ "$test_metrics" == "ERROR ERROR" ]]; then
    warn "$tool test report could not be parsed: $test_report"
  else
    read -r test_blocking test_advisory <<< "$test_metrics"
  fi

  local baseline_main="$(read_baseline_count "$baseline_file" "main")"
  local baseline_test="$(read_baseline_count "$baseline_file" "test")"

  if [[ "$baseline_main" == "MISSING" || "$baseline_main" == "ERROR" ]]; then
    warn "$tool baseline main missing or unreadable: $baseline_file"
    baseline_main=0
  fi
  if [[ "$baseline_test" == "MISSING" || "$baseline_test" == "ERROR" ]]; then
    warn "$tool baseline test missing or unreadable: $baseline_file"
    baseline_test=0
  fi

  local total_blocking=$((main_blocking + test_blocking))
  local total_advisory=$((main_advisory + test_advisory))
  local baseline_total_advisory=$((baseline_main + baseline_test))

  local new_main_advisory=$((main_advisory - baseline_main))
  local new_test_advisory=$((test_advisory - baseline_test))

  if (( new_main_advisory < 0 )); then new_main_advisory=0; fi
  if (( new_test_advisory < 0 )); then new_test_advisory=0; fi

  echo "$tool: BLOCKING: $total_blocking (main $main_blocking, test $test_blocking) | ADVISORY: total $total_advisory (main $main_advisory, test $test_advisory) [baseline $baseline_total_advisory]"

  local failed=0
  
  if (( total_blocking > 0 )); then
    warn "$tool has $total_blocking blocking findings! Blocking findings MUST be fixed."
    failed=1
  fi

  if (( new_main_advisory > 0 || new_test_advisory > 0 )); then
    warn "New $tool advisory findings: +$new_main_advisory main, +$new_test_advisory test"
    failed=1
  fi
  
  return $failed
}

new_findings=0

compare_tool "Checkstyle" \
  "${BASELINE_DIR}/checkstyle_baseline.json" \
  "app/build/reports/checkstyle/main.xml" \
  "app/build/reports/checkstyle/test.xml" || new_findings=1

compare_tool "SpotBugs" \
  "${BASELINE_DIR}/spotbugs_baseline.json" \
  "app/build/reports/spotbugs/main.xml" \
  "app/build/reports/spotbugs/test.xml" || new_findings=1

compare_tool "PMD" \
  "${BASELINE_DIR}/pmd_baseline.json" \
  "app/build/reports/pmd/main.xml" \
  "app/build/reports/pmd/test.xml" || new_findings=1

if (( new_findings > 0 )); then
  if [[ "$FAIL_ON_NEW" == "true" ]]; then
    echo "Static analysis criteria failed (blocking violations found or advisory baseline exceeded)."
    exit 1
  fi
  echo "Static analysis criteria failed (warning-only)."
fi
