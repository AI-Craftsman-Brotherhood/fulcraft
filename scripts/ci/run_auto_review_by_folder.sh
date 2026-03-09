#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PY_SCRIPT="${SCRIPT_DIR}/auto_review_fix.py"

FOCUS="${FOCUS:-consistency}"
SOURCE_ROOT="${SOURCE_ROOT:-app/src/main/java}"
INCLUDE_PATTERN="${INCLUDE_PATTERN:-*.java}"
MAX_FILES="${MAX_FILES:-30}"
SLEEP_MS="${SLEEP_MS:-300}"
TIMEOUT_SEC="${TIMEOUT_SEC:-120}"
CODEX_BIN="${CODEX_BIN:-codex}"
CODEX_SANDBOX="${CODEX_SANDBOX:-read-only}"
MODEL="${MODEL:-}"
CONSISTENCY_GATE="${CONSISTENCY_GATE:-0}"
CONSISTENCY_TIMEOUT_SEC="${CONSISTENCY_TIMEOUT_SEC:-0}"
MAX_TOTAL_LINE_CHANGES="${MAX_TOTAL_LINE_CHANGES:-0}"
MAX_COMMENT_DELETION_RATIO="${MAX_COMMENT_DELETION_RATIO:--1}"
REVERT_ON_GATE_FAIL="${REVERT_ON_GATE_FAIL:-0}"
FAIL_ON_GATE_FAIL="${FAIL_ON_GATE_FAIL:-0}"

APPLY_FLAG=0
FAIL_ON_PATCH_READY=0
STOP_ON_ERROR=0
LIST_ONLY=0

EXTRA_ARGS=()

usage() {
  cat <<'USAGE'
Usage:
  scripts/ci/run_auto_review_by_folder.sh [options] [-- extra auto_review_fix.py args]

Options:
  --focus <name>                review focus (default: consistency)
  --source-root <path>          source root scanned for folders (default: app/src/main/java)
  --include <glob>              file pattern used for folder discovery (default: *.java)
  --max-files <n>               max files per folder passed to auto_review_fix.py
  --sleep-ms <n>                sleep between files passed to auto_review_fix.py
  --timeout-sec <n>             timeout passed to auto_review_fix.py
  --codex-bin <path>            codex binary path (default: codex)
  --codex-sandbox <mode>        codex sandbox: read-only/workspace-write/danger-full-access
  --model <name>                codex model name (optional)
  --consistency-gate            run post-apply consistency gate
  --consistency-timeout-sec <n> timeout for consistency gate (0: use timeout-sec)
  --max-total-line-changes <n>  guardrail: max added+deleted lines (0 disables)
  --max-comment-deletion-ratio <r> guardrail: max deleted-comment ratio (-1 disables)
  --revert-on-gate-fail         restore file on gate failure
  --fail-on-gate-fail           fail when gate_failed/gate_error exists
  --apply                        enable patch apply mode
  --fail-on-patch-ready         fail if patch_ready exists in a folder run
  --stop-on-error               stop at first folder error (default: continue)
  --list-only                   only print discovered folders, do not invoke python
  -h, --help                    show this help

Environment overrides:
  FOCUS, SOURCE_ROOT, INCLUDE_PATTERN, MAX_FILES, SLEEP_MS, TIMEOUT_SEC, CODEX_BIN, CODEX_SANDBOX, MODEL,
  CONSISTENCY_GATE, CONSISTENCY_TIMEOUT_SEC, MAX_TOTAL_LINE_CHANGES, MAX_COMMENT_DELETION_RATIO,
  REVERT_ON_GATE_FAIL, FAIL_ON_GATE_FAIL
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --focus)
      FOCUS="$2"
      shift 2
      ;;
    --source-root)
      SOURCE_ROOT="$2"
      shift 2
      ;;
    --include)
      INCLUDE_PATTERN="$2"
      shift 2
      ;;
    --max-files)
      MAX_FILES="$2"
      shift 2
      ;;
    --sleep-ms)
      SLEEP_MS="$2"
      shift 2
      ;;
    --timeout-sec)
      TIMEOUT_SEC="$2"
      shift 2
      ;;
    --codex-bin)
      CODEX_BIN="$2"
      shift 2
      ;;
    --codex-sandbox)
      CODEX_SANDBOX="$2"
      shift 2
      ;;
    --model)
      MODEL="$2"
      shift 2
      ;;
    --consistency-gate)
      CONSISTENCY_GATE=1
      shift
      ;;
    --consistency-timeout-sec)
      CONSISTENCY_TIMEOUT_SEC="$2"
      shift 2
      ;;
    --max-total-line-changes)
      MAX_TOTAL_LINE_CHANGES="$2"
      shift 2
      ;;
    --max-comment-deletion-ratio)
      MAX_COMMENT_DELETION_RATIO="$2"
      shift 2
      ;;
    --revert-on-gate-fail)
      REVERT_ON_GATE_FAIL=1
      shift
      ;;
    --fail-on-gate-fail)
      FAIL_ON_GATE_FAIL=1
      shift
      ;;
    --apply)
      APPLY_FLAG=1
      shift
      ;;
    --fail-on-patch-ready)
      FAIL_ON_PATCH_READY=1
      shift
      ;;
    --stop-on-error)
      STOP_ON_ERROR=1
      shift
      ;;
    --list-only)
      LIST_ONLY=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      while [[ $# -gt 0 ]]; do
        EXTRA_ARGS+=("$1")
        shift
      done
      ;;
    *)
      EXTRA_ARGS+=("$1")
      shift
      ;;
  esac
done

if [[ ! -f "$PY_SCRIPT" ]]; then
  echo "ERROR: Python runner not found: $PY_SCRIPT" >&2
  exit 2
fi

SOURCE_ROOT_ABS="${REPO_ROOT}/${SOURCE_ROOT}"
if [[ ! -d "$SOURCE_ROOT_ABS" ]]; then
  echo "ERROR: source root not found: $SOURCE_ROOT_ABS" >&2
  exit 2
fi

mapfile -t FOLDER_ABS_LIST < <(
  find "$SOURCE_ROOT_ABS" -type f -name "$INCLUDE_PATTERN" -printf '%h\n' | sort -u
)

if [[ ${#FOLDER_ABS_LIST[@]} -eq 0 ]]; then
  echo "No folders found under ${SOURCE_ROOT} for pattern '${INCLUDE_PATTERN}'."
  exit 0
fi

FOLDER_REL_LIST=()
for folder_abs in "${FOLDER_ABS_LIST[@]}"; do
  if [[ "$folder_abs" == "$SOURCE_ROOT_ABS" ]]; then
    folder_rel="."
  else
    folder_rel="${folder_abs#"$SOURCE_ROOT_ABS"/}"
  fi
  FOLDER_REL_LIST+=("$folder_rel")
done

echo "Discovered ${#FOLDER_REL_LIST[@]} folder(s) under ${SOURCE_ROOT}:"
for folder_rel in "${FOLDER_REL_LIST[@]}"; do
  echo "  - ${folder_rel}"
done

if [[ "$LIST_ONLY" -eq 1 ]]; then
  exit 0
fi

failures=()
total="${#FOLDER_REL_LIST[@]}"
index=0

for folder_rel in "${FOLDER_REL_LIST[@]}"; do
  index=$((index + 1))
  echo ""
  echo "[${index}/${total}] folder=${folder_rel}"

  cmd=(
    python3 "$PY_SCRIPT"
    --repo-root "$REPO_ROOT"
    --source-root "$SOURCE_ROOT"
    --focus "$FOCUS"
    --codex-bin "$CODEX_BIN"
    --codex-sandbox "$CODEX_SANDBOX"
    --folder "$folder_rel"
    --max-files "$MAX_FILES"
    --sleep-ms "$SLEEP_MS"
    --timeout-sec "$TIMEOUT_SEC"
    --include "$INCLUDE_PATTERN"
    --consistency-timeout-sec "$CONSISTENCY_TIMEOUT_SEC"
    --max-total-line-changes "$MAX_TOTAL_LINE_CHANGES"
    --max-comment-deletion-ratio "$MAX_COMMENT_DELETION_RATIO"
  )
  if [[ -n "$MODEL" ]]; then
    cmd+=(--model "$MODEL")
  fi
  if [[ "$CONSISTENCY_GATE" -eq 1 ]]; then
    cmd+=(--consistency-gate)
  fi
  if [[ "$REVERT_ON_GATE_FAIL" -eq 1 ]]; then
    cmd+=(--revert-on-gate-fail)
  fi
  if [[ "$FAIL_ON_GATE_FAIL" -eq 1 ]]; then
    cmd+=(--fail-on-gate-fail)
  fi

  if [[ "$APPLY_FLAG" -eq 1 ]]; then
    cmd+=(--apply)
  fi
  if [[ "$FAIL_ON_PATCH_READY" -eq 1 ]]; then
    cmd+=(--fail-on-patch-ready)
  fi
  if [[ ${#EXTRA_ARGS[@]} -gt 0 ]]; then
    cmd+=("${EXTRA_ARGS[@]}")
  fi

  if ! "${cmd[@]}"; then
    failures+=("$folder_rel")
    if [[ "$STOP_ON_ERROR" -eq 1 ]]; then
      break
    fi
  fi
done

echo ""
echo "Run summary:"
echo "- total_folders: ${total}"
echo "- failed_folders: ${#failures[@]}"

if [[ ${#failures[@]} -gt 0 ]]; then
  echo "- failures:"
  for folder_rel in "${failures[@]}"; do
    echo "  - ${folder_rel}"
  done
  exit 1
fi

echo "- status: success"
