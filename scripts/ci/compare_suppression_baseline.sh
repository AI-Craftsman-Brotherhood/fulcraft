#!/usr/bin/env bash
set -euo pipefail

BASELINE_FILE="${BASELINE_FILE:-baseline/suppressions_baseline.json}"
CURRENT_OUTPUT=""
FAIL_ON_NEW=false
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<'USAGE'
Usage: scripts/ci/compare_suppression_baseline.sh [--fail-on-new] [--baseline-file <path>] [--current-output <path>]

Compares current Java suppression inventory with baseline JSON.
By default it reports differences without failing; pass --fail-on-new to fail on additions.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --fail-on-new)
      FAIL_ON_NEW=true
      shift
      ;;
    --baseline-file)
      BASELINE_FILE="${2:-}"
      shift 2
      ;;
    --current-output)
      CURRENT_OUTPUT="${2:-}"
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

args=(compare --baseline "${BASELINE_FILE}")
if [[ "${FAIL_ON_NEW}" == "true" ]]; then
  args+=(--fail-on-new)
fi
if [[ -n "${CURRENT_OUTPUT}" ]]; then
  args+=(--output-current "${CURRENT_OUTPUT}")
fi

bash "${SCRIPT_DIR}/suppression_inventory.sh" "${args[@]}"
