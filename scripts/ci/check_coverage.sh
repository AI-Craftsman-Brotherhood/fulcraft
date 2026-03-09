#!/usr/bin/env bash
set -euo pipefail

XML_PATH="${1:-app/build/reports/jacoco/test/jacocoTestReport.xml}"
THRESHOLD="${2:-80}"
HTML_PATH="app/build/reports/jacoco/test/html/index.html"
GAPS_PATH="${3:-app/build/reports/jacoco/test/coverage-gaps.txt}"

if [[ ! -f "$XML_PATH" ]]; then
  echo "::warning::JaCoCo XML report not found at ${XML_PATH}"
  exit 0
fi

python - <<'PY' "$XML_PATH" "$THRESHOLD" "$HTML_PATH" "$GAPS_PATH"
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

xml_path = sys.argv[1]
threshold = float(sys.argv[2])
html_path = sys.argv[3]
gaps_path = sys.argv[4]

tree = ET.parse(xml_path)
root = tree.getroot()
def find_instruction_counter(node):
    for counter in node.findall("counter"):
        if counter.attrib.get("type") == "INSTRUCTION":
            return counter
    return None


counter = find_instruction_counter(root)

if counter is None:
    print("::warning::INSTRUCTION counter not found in JaCoCo XML report")
    sys.exit(0)

missed = int(counter.attrib.get("missed", "0"))
covered = int(counter.attrib.get("covered", "0"))
total = missed + covered
if total == 0:
    print("::warning::JaCoCo INSTRUCTION counter has zero total instructions")
    sys.exit(0)

coverage = covered / total * 100.0
print(f"Coverage (INSTRUCTION): {coverage:.2f}% (covered={covered}, missed={missed})")
print(f"HTML report: {html_path}")
print(f"Coverage gaps report: {gaps_path}")

def collect_coverage(entries, label, output):
    if not entries:
        return
    line = f"{label} below threshold {threshold:.2f}%:"
    print(line)
    output.write(line + "\n")
    for name, cov, cov_covered, cov_missed in entries:
        line = f"- {name}: {cov:.2f}% (covered={cov_covered}, missed={cov_missed})"
        print(line)
        output.write(line + "\n")


def to_coverage(counter_node):
    if counter_node is None:
        return None
    missed = int(counter_node.attrib.get("missed", "0"))
    covered = int(counter_node.attrib.get("covered", "0"))
    total = missed + covered
    if total == 0:
        return None
    return covered / total * 100.0, covered, missed


package_entries = []
class_entries = []
gap_lines = []

for pkg in root.findall("package"):
    pkg_name = pkg.attrib.get("name", "") or "(default)"
    pkg_name = pkg_name.replace("/", ".")
    pkg_counter = find_instruction_counter(pkg)
    pkg_coverage = to_coverage(pkg_counter)
    if pkg_coverage is not None and pkg_coverage[0] < threshold:
        package_entries.append((pkg_name, *pkg_coverage))
    for cls in pkg.findall("class"):
        class_name = cls.attrib.get("name", "") or "(anonymous)"
        class_name = class_name.replace("/", ".")
        class_counter = find_instruction_counter(cls)
        class_coverage = to_coverage(class_counter)
        if class_coverage is not None and class_coverage[0] < threshold:
            class_entries.append((class_name, *class_coverage))
            class_path = class_name.split("$", 1)[0].replace(".", "/") + ".java"
            gap_lines.append(f"Missing coverage in: app/src/main/java/{class_path}")

package_entries.sort(key=lambda entry: entry[1])
class_entries.sort(key=lambda entry: entry[1])
gap_lines = sorted(set(gap_lines))

Path(gaps_path).parent.mkdir(parents=True, exist_ok=True)
with open(gaps_path, "w", encoding="utf-8") as output:
    if gap_lines:
        output.write("Missing coverage targets:\n")
        for line in gap_lines:
            output.write(line + "\n")
        output.write("\n")
    collect_coverage(package_entries, "Packages", output)
    collect_coverage(class_entries, "Classes", output)

if coverage < threshold:
    print(f"::error::Coverage {coverage:.2f}% is below threshold {threshold:.2f}%")
    sys.exit(1)
PY
