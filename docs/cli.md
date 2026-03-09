# FUL CLI ガイド

FUL の CLI/TUI コマンドの使い方をまとめます。

このページでは **JUnit 自動生成に関する説明は扱いません**。

## コマンド一覧

| コマンド | 説明 | 主な出力 |
|---|---|---|
| `ful run` | パイプライン全体または指定ステップを実行 | `.ful/runs/<runId>/**` |
| `ful analyze` | 解析のみ実行 (`ANALYZE`) | `.ful/runs/<runId>/analysis/**` |
| `ful explore` | 探索用アーティファクトを生成 (`ANALYZE -> DOCUMENT -> REPORT -> EXPLORE`) | `.ful/runs/<runId>/explore/*` |
| `ful report` | 解析レポートを生成 | `.ful/runs/<runId>/report/*` |
| `ful document` (`doc`) | ソースコードドキュメントを生成 | `.ful/runs/<runId>/docs/*` |
| `ful steps` | 利用可能なパイプラインステップを表示 | 標準出力 |
| `ful init` | 設定ファイルを生成 | `config.json` |
| `ful init-ci` | CI 向けワークフロー生成 | `.github/workflows/*.yml` |
| `ful tut` | TUT 対話CLI起動（非画面） | 対話プロンプト |
| `ful resume` | 以前の TUI セッションを再開 | インタラクティブ画面 |

## 共通オプション

パイプライン実行系コマンド（`run`, `analyze`, `explore`, `report`, `document`）で共通して使うオプションです。

| オプション | 説明 | デフォルト |
|---|---|---|
| `-c, --config <path>` | 設定ファイルパス | `config.json`（なければ `.ful/config.json` を探索） |
| `-p, --project-root <path>` | プロジェクトルート（位置引数でも指定可） | `.` |
| `-h, --help` | ヘルプ表示 | - |
| `-V, --version` | バージョン表示 | - |
| `-v, --verbose` | 詳細ログ出力 | - |
| `--dry-run` | 実行や書き込みを行わずプランのみ表示 | - |
| `--color <on|off|auto>` | カラー出力モード | `auto` |
| `--log-format <human|json|yaml>` | ログ形式 | `human` |
| `--json` | `--log-format json` の短縮 | - |
| `--version-history` | バージョン履歴（Git）を考慮した選定を行う | `false` |
| `--unresolved-policy <skip|minimal>` | 未解決参照の処理ポリシー | - |
| `--max-cyclomatic <int>` | 循環的複雑度の上限（これを超えると戦略適用） | - |
| `--complexity-strategy <warn|skip|split>` | 高複雑度メソッドへの戦略 | - |
| `--process-isolation` | プロセス分離モードで実行 | `false` |
| `--tasks-format <json|jsonl>` | タスクファイルの出力形式 | - |
| `--cache-ttl <int>` | キャッシュ有効期限（日） | - |
| `--cache-revalidate` | キャッシュ使用前に再検証を行う | `false` |
| `--cache-encrypt` | キャッシュを暗号化する | `false` |
| `--cache-key-env <name>` | キャッシュ暗号化キーの環境変数名 | - |

## `run` オプション

`ful run [OPTIONS]`

| オプション | 説明 |
|---|---|
| `--steps <steps>` | 実行するステップを明示指定（カンマ区切り） |
| `--from <step>` | 開始ステップを指定 |
| `--to <step>` | 終了ステップを指定 |
| `--fail-fast` | 失敗時に即時終了 |
| `--summary` / `--no-summary` | 実行後サマリー表示の切替 |
| `--format <markdown|html|pdf|all>` | DOCUMENT/EXPLORE のドキュメント出力形式 |
| `--llm` | `docs.use_llm` を有効化し、run 実行ステージへ LLM 利用設定を伝播 |

補足:

- `--steps` と `--from`/`--to` は併用できません。
- `ful run` の既定ステップは `pipeline.workflow_file` 未設定時は `ANALYZE -> DOCUMENT -> REPORT -> EXPLORE`、設定時は `GENERATE` です（`--steps`/`--from`/`--to` 指定時はそちらを優先）。
- 本ガイドでは JUnit 自動生成関連ステップの詳細は扱いません。

## `analyze` オプション

`ful analyze [OPTIONS]`

| オプション | 説明 |
|---|---|
| `--engine <composite|javaparser|spoon>` | 解析エンジン指定 |
| `-f, --files <paths>` | 対象ファイル指定（カンマ区切り） |
| `-d, --dirs <paths>` | 対象ディレクトリ指定（カンマ区切り） |
| `--exclude-tests[=<bool>]` | テストコードを解析対象から除外 |
| `--debug-dynamic-resolution` | 動的解決のデバッグ出力 |

## `explore` オプション

`ful explore [OPTIONS]`

| オプション | 説明 |
|---|---|
| `--engine <composite|javaparser|spoon>` | 解析エンジン指定 |
| `-f, --files <paths>` | 対象ファイル指定（カンマ区切り） |
| `-d, --dirs <paths>` | 対象ディレクトリ指定（カンマ区切り） |
| `--exclude-tests[=<bool>]` | テストコードを解析対象から除外 |
| `--debug-dynamic-resolution` | 動的解決のデバッグ出力 |
| `--format <markdown|html|pdf|all>` | ドキュメント形式。`html/all` 時は report 形式にも引き継ぐ |
| `--llm` | `docs.use_llm` を有効化して、explore 内の document 生成に LLM を使う |

補足:

- `ful explore` は内部的に `ANALYZE -> DOCUMENT -> REPORT -> EXPLORE` を実行します。
- `docs.format` が未指定または `markdown` の場合、実行時に `html` へ切り替えて探索ビュー向けの出力を優先します。
- `--format` 未指定でも、上記の `html` 切替に連動して report 形式（`output.format.report`）は `html` に引き継がれます（`yaml` など明示値は維持）。

## `report` オプション

`ful report [OPTIONS]`

| オプション | 説明 |
|---|---|
| `--run-id <id>` | 対象 run ID（省略時は最新） | - |
| `--engine <composite|javaparser|spoon>` | 解析エンジン指定 | `composite` |
| `--exclude-tests` | テストコードを解析・レポート対象から除外 | `config.analysis.exclude_tests` |
| `--format <markdown|html|json|yaml>` | 出力形式 | - |
| `-o, --output <path>` | 出力先 | - |
| `-f, --files <paths>` | 対象ファイル指定 | - |
| `-d, --dirs <paths>` | 対象ディレクトリ指定 | - |

## `document` (`doc`) オプション

`ful document [OPTIONS]`

| オプション | 説明 |
|---|---|
| `-o, --output <path>` | 出力先ディレクトリ |
| `-f, --files <paths>` | 対象ファイル指定 |
| `-d, --dirs <paths>` | 対象ディレクトリ指定 |
| `--format <markdown|html|pdf|all>` | 出力形式 | - |
| `--single-file` | 全クラスを1ファイルへ統合 | `false` |
| `--diagram` | 依存関係ダイアグラム生成 | `false` |
| `--diagram-format <mermaid|plantuml>` | 図の形式 | `mermaid` |
| `--include-tests` | テストコードへのリンクを含める | `false` |
| `--llm` | LLM を使った詳細化を有効化 |

## `init` / `init-ci`

### `init`

`ful init [OPTIONS]`

| オプション | 説明 |
|---|---|
| `-d, --directory <path>` | 設定ファイル生成先ディレクトリ |
| `-f, --force` | 既存設定の上書き |

### `init-ci`

`ful init-ci [OPTIONS]`

| オプション | 説明 |
|---|---|
| `--github-actions` | GitHub Actions 用テンプレートを使用 | - |
| `-o, --output <path>` | 出力先ファイル | - |
| `-f, --force` | 既存ファイル上書き | `false` |
| `--dry-run` | ファイル出力せず内容のみ表示 | `false` |
| `--comment` / `--no-comment` | PRへのコメント投稿設定を含めるか | `true` |
| `--quality-gate` / `--no-quality-gate` | 品質ゲートジョブを含めるか | `true` |
| `--coverage-threshold <int>` | カバレッジ閾値（%） | - |
| `--coverage-tool <name>` | カバレッジツール指定 | `jacoco` |
| `--static-analysis <tools>` | 静的解析ツール指定（カンマ区切り） | - |

補足:
- `ful quality-gate` コマンドは廃止済みです。
- `--quality-gate` は生成する CI ワークフロー内の品質チェックジョブ有無を制御します。

## 対話モード

### `tut`

| オプション | 説明 |
|---|---|
| `-p, --project-root <path>` | プロジェクトルート |

補足:
- `tut` は非画面REPL（TUTモード）です。
- TUIセッション再開（画面モード）が必要な場合は `resume` を使用してください。

### `resume`

| オプション | 説明 |
|---|---|
| `-l, --list` | セッション一覧のみ表示 |
| `-i, --id <id>` | セッション ID 指定で再開 |
| `-p, --project-root <path>` | プロジェクトルート |

## 環境変数

| 環境変数 | 説明 |
|---|---|
| `FUL_COLOR` | カラー出力（`on`, `off`, `auto`） |
| `FUL_LOG_FORMAT` | ログ形式（`human`, `json`, `yaml`） |
| `NO_COLOR` | 設定時にカラー無効化 |
| `FUL_LANG` | 言語指定（例: `ja`, `en`） |

## よく使う例

```bash
# 解析のみ
./scripts/ful analyze -d src/main/java/com/example/core/

# 解析レポート生成
./scripts/ful report --run-id latest --format html

# ドキュメント生成
./scripts/ful document -d src/main/java/com/example/core/ --format markdown

# CI ワークフローテンプレートを確認（ファイルは出力しない）
./scripts/ful init-ci --github-actions --dry-run
```

## 関連ドキュメント

- [ログ設定ガイド](logging.md)
- [設定リファレンス](config.md)
- [プラグインアーキテクチャ](plugins/architecture.md)
