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
- `.ful/review-fix/<focus>-backlog.md` (when `--backlog`)

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

Relative prompt/output paths are resolved against `--repo-root`.

The focus prompt can use:

- `{target_path}`
- `{source_code}`
- `{focus}`

## Codex Options

- `--codex-bin <path>`: codex binary path.
- `--codex-sandbox read-only|workspace-write|danger-full-access`: sandbox mode for `codex exec`.
- `--model <name>`: optional codex model override.

## Readability Debt Backlog

Run readability in dry-run and emit backlog entries from `patch_ready` results:

```bash
python3 scripts/ci/auto_review_fix.py \
  --focus readability \
  --max-files 10000 \
  --backlog
```

Backlog controls:

- `--backlog`: backlog file generationを有効化
- `--backlog-output <path>`: 出力先（拡張子で format を判定: `.md` / `.json` / `.jsonl` / `.csv`）
- `--backlog-status <status>`: バックログに含める status（repeatable, default: `patch_ready`)

Backlog entries include line hints and compact diff summary when available (for `patch_ready` / `applied` / patch-related failures).

## Consistency Gate and Guardrails

- `--consistency-gate`: apply 後に同一ファイルへ consistency dry-run を実行。
  - consistency が追加差分を提案した場合は `gate_failed`。
- `--consistency-timeout-sec <n>`: consistency gate の timeout。`0` なら `--timeout-sec` を再利用。
- `--max-total-line-changes <n>`: 1ファイルあたりの `added + deleted` 上限。`0` で無効。
- `--max-comment-deletion-ratio <r>`: コメント削除率上限。`-1` で無効、`0.0..1.0` で有効。
- `--revert-on-gate-fail`: gate失敗時に該当ファイルを適用前の内容へ復元。
- `--fail-on-gate-fail`: `gate_failed` / `gate_error` が1件でもあれば終了コード `1`。
- コメント保持を重視する場合は `--max-comment-deletion-ratio 0.02` 〜 `0.05` を推奨。

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

- `--folder-mode whole` (default): process target files as one batch.
- `--folder-mode per-folder`: process each folder independently.
- `--non-recursive`: scan only direct children of each target root.
  - Useful when `--source-root` points at a directory like `ui/cli/command` and you want to exclude nested folders such as `run/` and `support/`.
- `--folder <path>` can be repeated.
  - Relative paths are resolved against `--source-root`, then `--repo-root`.
  - In `per-folder` mode, each `--folder` is processed separately.
- If `--folder-mode per-folder` is used without `--folder`, the script auto-discovers folders that directly contain matching files and processes them one by one.

## Recommended OSS Workflow

1. Run readability pass in dry-run, then apply.
2. Run maintainability pass in dry-run, then apply.
3. Enable `--consistency-gate` + guardrails for apply runs (`--revert-on-gate-fail` 推奨)。
4. Run consistency pass with `--fail-on-patch-ready` to detect remaining style drift.
5. Run tests after each batch.

Example:

```bash
./gradlew :app:test :app:integrationTest
```
