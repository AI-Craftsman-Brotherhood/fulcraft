# Auto Review Automation

`scripts/ci/auto_review_fix.py` runs LLM-based review and patch generation for Java sources.

The runner always uses Codex (`codex exec` is called from the script).
Ensure `codex` is installed and authenticated.

## Goals

- Keep code style and structure consistent across many files.
- Run readability, maintainability, and consistency passes separately.
- Apply only patches that pass `git apply --check`.

## Quick Start

```bash
# Readability pass (dry-run)
python3 scripts/ci/auto_review_fix.py --focus readability --max-files 20

# Readability pass (apply patches)
python3 scripts/ci/auto_review_fix.py --focus readability --max-files 20 --apply

# Maintainability pass
python3 scripts/ci/auto_review_fix.py --focus maintainability --max-files 20 --apply

# Consistency check pass (fail if drift is found)
python3 scripts/ci/auto_review_fix.py --focus consistency --max-files 20 --fail-on-patch-ready

# Folder-by-folder pass (explicit folders)
python3 scripts/ci/auto_review_fix.py \
  --focus consistency \
  --folder-mode per-folder \
  --folder com/craftsmanbro/fulcraft/plugins \
  --folder com/craftsmanbro/fulcraft/kernel \
  --max-files 15 \
  --fail-on-patch-ready

# Only files directly under ui/cli/command (exclude run/ and support/)
python3 scripts/ci/auto_review_fix.py \
  --focus readability \
  --source-root app/src/main/java/com/craftsmanbro/fulcraft/ui/cli/command \
  --non-recursive \
  --max-files 50
```

Default report output:

- `.ful/review-fix/readability-report.jsonl`
- `.ful/review-fix/maintainability-report.jsonl`
- `.ful/review-fix/consistency-report.jsonl`
- `.ful/review-fix/<focus>-run-metadata.json`
- `.ful/review-fix/<focus>-backlog.md` (when `--backlog` is enabled)

## Prompt Files

Default prompt directory:

- `scripts/prompts/auto-review/system.txt`
- `scripts/prompts/auto-review/readability.txt`
- `scripts/prompts/auto-review/maintainability.txt`
- `scripts/prompts/auto-review/consistency.txt`

You can override prompt files:

```bash
python3 scripts/ci/auto_review_fix.py \
  --focus consistency \
  --system-prompt-file /path/to/system.txt \
  --focus-prompt-file /path/to/consistency.txt \
  --print-prompt-paths
```

Relative prompt and output paths are resolved against `--repo-root`.

The focus prompt can use:

- `{target_path}`
- `{source_code}`
- `{focus}`

## Codex Options

- `--codex-bin <path>`: codex binary path
- `--codex-sandbox read-only|workspace-write|danger-full-access`: sandbox mode for `codex exec`
- `--model <name>`: optional codex model override

## Readability Debt Backlog

Run readability in dry-run mode and emit backlog entries from `patch_ready` results:

```bash
python3 scripts/ci/auto_review_fix.py \
  --focus readability \
  --max-files 10000 \
  --backlog
```

Backlog controls:

- `--backlog`: enable backlog generation
- `--backlog-output <path>`: output destination, with format inferred from extension (`.md`, `.json`, `.jsonl`, `.csv`)
- `--backlog-status <status>`: repeatable status filter, defaulting to `patch_ready`

Backlog entries include line hints and compact diff summaries when available.

## Consistency Gate and Guardrails

- `--consistency-gate`: run a consistency dry-run against the same file after apply
  - If consistency proposes additional changes, the result is `gate_failed`
- `--consistency-timeout-sec <n>`: timeout for the consistency gate. `0` reuses `--timeout-sec`
- `--max-total-line-changes <n>`: cap for `added + deleted` per file. `0` disables the guardrail
- `--max-comment-deletion-ratio <r>`: max allowed comment deletion ratio. `-1` disables it
- `--revert-on-gate-fail`: restore the original file when the gate fails
- `--fail-on-gate-fail`: exit with status `1` when any `gate_failed` or `gate_error` occurs
- To preserve comments aggressively, `--max-comment-deletion-ratio 0.02` to `0.05` is a reasonable starting point

Example:

```bash
python3 scripts/ci/auto_review_fix.py \
  --focus maintainability \
  --max-files 20 \
  --apply \
  --consistency-gate \
  --max-total-line-changes 120 \
  --max-comment-deletion-ratio 0.15 \
  --revert-on-gate-fail \
  --fail-on-gate-fail
```

## Folder-by-Folder Mode

- `--folder-mode whole` (default): process all target files as one batch
- `--folder-mode per-folder`: process each folder independently
- `--non-recursive`: scan only direct children of each target root
- `--folder <path>` can be repeated
  - Relative paths are resolved against `--source-root`, then `--repo-root`
  - In `per-folder` mode, each `--folder` is processed separately
- If `--folder-mode per-folder` is used without `--folder`, the script auto-discovers folders that directly contain matching files and processes them one by one

## Recommended OSS Workflow

1. Run readability in dry-run, then apply.
2. Run maintainability in dry-run, then apply.
3. Enable `--consistency-gate` and guardrails for apply runs. `--revert-on-gate-fail` is recommended.
4. Run consistency with `--fail-on-patch-ready` to detect remaining style drift.
5. Run tests after each batch.

Example:

```bash
./gradlew :app:test :app:integrationTest
```
