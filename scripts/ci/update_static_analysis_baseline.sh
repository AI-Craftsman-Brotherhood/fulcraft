#!/usr/bin/env bash
set -euo pipefail

BASELINE_DIR="${BASELINE_DIR:-baseline}"
DATE_STAMP="$(date -u +%Y-%m-%d)"

mkdir -p "$BASELINE_DIR"

write_baseline() {
  local tool="$1"
  local tag="$2"
  local main_report="$3"
  local test_report="$4"
  local out_file="$5"

  python3 - "$DATE_STAMP" "$tool" "$tag" "$main_report" "$test_report" "$out_file" <<'PY'
import json
import sys
import xml.etree.ElementTree as ET

date_stamp, tool, tag, main_report, test_report, out_file = sys.argv[1:7]

def local_name(name):
    return name.split("}", 1)[-1] if "}" in name else name

def count(path, tag_name):
    if not path or not os.path.isfile(path):
        return 0, f"No report found at {path}"
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return 0, f"Failed to parse report at {path}"
    return sum(1 for elem in root.iter() if local_name(elem.tag) == tag_name), None

import os

main_count, main_note = count(main_report, tag)
test_count, test_note = count(test_report, tag)
note_parts = [n for n in (main_note, test_note) if n]
payload = {
    "tool": tool,
    "generated_at": date_stamp,
    "reports": {
        "main": main_report,
        "test": test_report,
    },
    "counts": {
        "main": main_count,
        "test": test_count,
        "total": main_count + test_count,
    },
}
if note_parts:
    payload["note"] = "; ".join(note_parts)

with open(out_file, "w", encoding="utf-8") as handle:
    json.dump(payload, handle, indent=2)
    handle.write("\n")
PY
}

write_baseline "checkstyle" "error" \
  "app/build/reports/checkstyle/main.xml" \
  "app/build/reports/checkstyle/test.xml" \
  "${BASELINE_DIR}/checkstyle_baseline.json"

write_baseline "spotbugs" "BugInstance" \
  "app/build/reports/spotbugs/main.xml" \
  "app/build/reports/spotbugs/test.xml" \
  "${BASELINE_DIR}/spotbugs_baseline.json"

write_baseline "pmd" "violation" \
  "app/build/reports/pmd/main.xml" \
  "app/build/reports/pmd/test.xml" \
  "${BASELINE_DIR}/pmd_baseline.json"

echo "Static analysis baseline updated in ${BASELINE_DIR}"
