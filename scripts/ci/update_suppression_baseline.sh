#!/usr/bin/env bash
set -euo pipefail

BASELINE_FILE="${BASELINE_FILE:-baseline/suppressions_baseline.json}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --baseline-file)
      BASELINE_FILE="${2:-}"
      shift 2
      ;;
    -h|--help)
      cat <<'USAGE'
Usage: scripts/ci/update_suppression_baseline.sh [--baseline-file <path>]

Scans Java source roots and rewrites suppression baseline JSON.
When an existing baseline file is present, manual fields
(reason/alternative/revisit_condition/next_review_date) are preserved.
USAGE
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

bash "${SCRIPT_DIR}/suppression_inventory.sh" scan \
  --output "${BASELINE_FILE}" \
  --merge-manual-from "${BASELINE_FILE}"

echo "Suppression baseline updated: ${BASELINE_FILE}"
