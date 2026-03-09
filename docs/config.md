# FUL 設定リファレンス

このドキュメントでは、FUL の設定ファイル (`config.json`) の構造と、各設定項目の詳細を解説します。
設定は JSON Schema に基づいてバリデーションされます。

---

## 目次

1. [設定ファイルの基本](#設定ファイルの基本)
2. [トップレベルカテゴリ一覧](#トップレベルカテゴリ一覧)
3. [カテゴリ別設定リファレンス](#カテゴリ別設定リファレンス)
   - [project](#project)
   - [analysis](#analysis)
   - [llm](#llm)
   - [selection_rules](#selection_rules)
   - [pipeline](#pipeline)
   - [execution](#execution)
   - [verification](#verification)
   - [context_awareness](#context_awareness)
   - [generation](#generation)
   - [brittle_test_rules](#brittle_test_rules)
   - [mocking](#mocking)
   - [output](#output)
   - [log](#log)
   - [governance](#governance)
   - [audit](#audit)
   - [quota](#quota)
   - [local_fix](#local_fix)
   - [docs](#docs)
   - [quality_gate](#quality_gate)
   - [cache](#cache)
   - [cli](#cli)
   - [interceptors](#interceptors)
4. [LLMプロバイダー別設定](#llmプロバイダー別設定)
5. [/config TUI エディタの操作ガイド](#config-tui-エディタの操作ガイド)

---

## 設定ファイルの基本

### 設定ファイルの場所

デフォルトではプロジェクトルートの `config.json` を読み込み、存在しない場合は `.ful/config.json` を参照します。

```
<ProjectRoot>/config.json
# または
<ProjectRoot>/.ful/config.json
```

CLIオプション `-c, --config <path>` で明示的に指定することも可能です。

### 設定ファイルの作成

```bash
# インタラクティブに作成
ful init
```

### 基本構造

```json
{
  "project": { "id": "my-project" },
  "analysis": {},
  "selection_rules": {},
  "llm": {},
  "output": {
    "format": {
      "tasks": "json",
      "report": "markdown"
    }
  },
  "pipeline": {
    "stages": ["analyze", "generate", "report"]
  }
}
```

---

## トップレベルカテゴリ一覧

| カテゴリ | 説明 | 必須 |
|---------|------|------|
| `project` | プロジェクト基本情報 | ✅ |
| `analysis` | ソースコード解析設定 | |
| `selection_rules` | テスト対象選択ルール | ✅ |
| `llm` | LLMプロバイダー・モデル設定 | ✅ |
| `pipeline` | パイプラインステージ制御 | |
| `execution` | 実行制御設定 | |
| `verification` | テスト実行・Flaky検出設定 | |
| `context_awareness` | 既存テストスタイル学習 | |
| `generation` | テスト生成オプション | |
| `brittle_test_rules` | 壊れやすいテスト（Refleciton/Sleep等）の検出ルール | |
| `mocking` | モック/スタブ設定 | |
| `output` | 出力フォーマット設定 | |
| `log` | ログ出力設定 | |
| `governance` | セキュリティ・外部送信制御 | |
| `audit` | 監査ログ設定 | |
| `quota` | 使用量制限 | |
| `local_fix` | ローカル自動修復設定 | |
| `docs` | ドキュメント生成設定 | |
| `quality_gate` | 品質ゲート（カバレッジ等）設定 | |
| `cache` | 解析・生成キャッシュ設定 | |
| `cli` | CLI 表示設定 | |
| `interceptors` | 処理フェーズへのインターセプター設定 | |

---

## カテゴリ別設定リファレンス

### project

プロジェクトの基本情報を設定します。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `id` | String | `"default"` | プロジェクト識別子 |
| `root` | String | (カレント) | プロジェクトルートパス |
| `docs_output` | String | (未設定) | `ful document` 出力先（未設定の場合は `.ful/docs`） |
| `repo_url` | String | - | リポジトリ URL |
| `commit` | String | - | コミットハッシュ |
| `build_tool` | String | `gradle` | `gradle` または `maven` |
| `build_command` | String | - | テスト実行コマンド |
| `include_paths` | List[String] | `[]` | 解析対象パス（空なら全体） |
| `exclude_paths` | List[String] | `[]` | 解析除外パス |

### analysis

ソースコード解析の動作を制御します。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `engine` | String | (未設定) | 解析エンジンを指定 |
| `source_root_mode` | Enum | `"AUTO"` | `AUTO`, `STRICT` |
| `source_root_paths` | List[String] | `["src/main/java", "app/src/main/java"]` | ソースルートパス |
| `source_charset` | String | `"UTF-8"` | 文字コード |
| `dump_file_list` | Boolean | `false` | 解析ファイル一覧の出力 |
| `exclude_tests` | Boolean | `true` | テストコードを解析対象から除外 |
| `spoon.no_classpath` | Boolean | `true` | Spoon の noClasspath 切替 |
| `classpath.mode` | Enum | `"AUTO"` | `AUTO`, `STRICT`, `OFF` |
| `preprocess.mode` | Enum | `"OFF"` | `OFF`, `AUTO`, `STRICT` |
| `preprocess.tool` | Enum | `"AUTO"` | `AUTO`, `DELOMBOK`, `JAVAC_APT` |
| `preprocess.work_dir` | String | `".utg/preprocess"` | 前処理作業ディレクトリ |
| `preprocess.clean_work_dir` | Boolean | `true` | 作業ディレクトリ掃除 |
| `preprocess.include_generated` | Boolean | `false` | 生成ソースの解析対象化 |
| `preprocess.delombok.enabled` | Boolean | `true` | Delombok 有効化 |
| `preprocess.delombok.lombok_jar_path` | String | (未設定) | Lombok JAR の明示指定 |
| `enable_interprocedural_resolution` | Boolean | `false` | 手続き間解析の有効化 |
| `interprocedural_callsite_limit` | Integer | `20` | 手続き間解析の呼び出し制限 |
| `external_config_resolution` | Boolean | `false` | 外部設定値の解決 |
| `debug_dynamic_resolution` | Boolean | `false` | 動的解決のデバッグログ |
| `experimental_candidate_enum` | Boolean | `false` | 候補列挙の実験設定 |

### llm

LLMプロバイダーとモデルの設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `provider` | Enum | `"gemini"` | `gemini`, `openai`, `azure-openai`, `anthropic`, `vertex`, `bedrock`, `local` 等 |
| `allowed_providers` | List[String] | (未設定) | 利用可能プロバイダーの許可リスト |
| `allowed_models` | Map[String, List] | (未設定) | プロバイダー別モデル許可リスト |
| `model_name` | String | - | モデル名（例: `gpt-4o`, `gemini-2.0-flash-exp`） |
| `api_key` | String | - | 環境変数の利用を推奨 |
| `url` | String | (未設定) | エンドポイント（Local/OpenAI Compatible用） |
| `azure_deployment` | String | (未設定) | Azure OpenAI のデプロイ名 |
| `azure_api_version` | String | (未設定) | Azure OpenAI の API バージョン |
| `vertex_project` | String | (未設定) | Vertex AI の GCP プロジェクト |
| `vertex_location` | String | (未設定) | Vertex AI リージョン |
| `vertex_publisher` | String | (未設定) | Vertex AI パブリッシャー (例: `google`) |
| `vertex_model` | String | (未設定) | Vertex AI モデル |
| `aws_access_key_id` | String | (未設定) | AWS アクセスキー |
| `aws_secret_access_key` | String | (未設定) | AWS シークレットキー |
| `aws_session_token` | String | (未設定) | AWS セッショントークン |
| `aws_region` | String | (未設定) | AWS リージョン |
| `max_retries` | Integer | `3` | 生成失敗時のリトライ回数 |
| `fix_retries` | Integer | `2` | 自動修復のリトライ回数 |
| `connect_timeout` | Integer | `30` | 接続タイムアウト（秒） |
| `request_timeout` | Integer | `300` | リクエストタイムアウト（秒） |
| `max_response_length` | Integer | `50000` | レスポンス長上限（文字数） |
| `custom_headers` | Map | `{}` | 追加 HTTP ヘッダー |
| `fallback_stub_enabled` | Boolean | `true` | 生成失敗時のスタブ生成 |
| `javac_validation` | Boolean | `false` | 軽量 javac 検証 |
| `retry_initial_delay_ms` | Integer | `2000` | リトライ初期遅延（ms） |
| `retry_backoff_multiplier` | Number | `2.0` | リトライバックオフ倍率 |
| `rate_limit_qps` | Number | (未設定) | レート制限（QPS） |
| `circuit_breaker_threshold` | Integer | `5` | サーキットブレーカ閾値 |
| `circuit_breaker_reset_ms` | Number | `30000` | サーキットブレーカ復帰時間（ms） |
| `deterministic` | Boolean | `true` | 決定論的出力を行うか |
| `seed` | Integer | `42` | 決定論モードのシード |
| `temperature` | Number | (未設定) | 生成温度パラメータ |
| `max_tokens` | Integer | (未設定) | 最大生成トークン数 |
| `system_message` | String | (未設定) | システムメッセージ上書き |
| `smart_retry.same_error_max_retries` | Integer | `1` | 同一エラー繰り返しの許容回数 |
| `smart_retry.total_retry_budget_per_task` | Integer | `3` | タスクあたりの修復予算 |
| `smart_retry.non_recoverable_max_retries` | Integer | `0` | 回復不能エラー時のリトライ |

### selection_rules

テスト対象メソッドの選択・優先順位付けルールです。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `max_targets` | Integer | `50` | 最大テスト生成対象数 |
| `strategy` | String | `"SMART"` | 戦略 (`SMART`, etc) |
| `selection_engine` | String | `"rule_based"` | 選定エンジン (`core`, `rule_based`) |
| `class_min_loc` | Integer | `10` | クラス最小 LOC |
| `class_min_method_count` | Integer | `1` | クラス内メソッド数下限 |
| `method_min_loc` | Integer | `3` | メソッド最小行数 |
| `method_max_loc` | Integer | `1000` | メソッド最大行数 |
| `exclude_getters_setters` | Boolean | `true` | Getter/Setter を除外 |
| `exclude_dead_code` | Boolean | `false` | デッドコードを除外 |
| `max_methods_per_class` | Integer | (未設定) | 1クラスあたりの最大生成数 |
| `max_methods_per_package` | Integer | (未設定) | パッケージあたりの最大生成数 |
| `exclude_annotations` | List[String] | `[]` | 除外アノテーション |
| `deprioritize_annotations` | List[String] | `[]` | 優先度を下げるアノテーション |
| `priority_annotations` | List[String] | `[]` | 優先度を上げるアノテーション |
| `scoring_weights` | Object | | スコアリングの重み設定 |
| `complexity.max_cyclomatic` | Integer | `20` | 循環的複雑度の上限 |
| `complexity.strategy` | Enum | `"warn"` | 高複雑度時の戦略 (`skip`, `warn`, `split`, `specialized_prompt`) |
| `complexity.expected_tests_per_complexity` | Number | `1.5` | 複雑度あたりの期待テスト数 |
| `complexity.max_expected_tests` | Integer | `20` | 期待テスト数の上限 |
| `removal_boost` | Number | `50.0` | 削除/非推奨 API 使用時の加点 |
| `deprioritize_factor` | Number | `0.5` | 優先度低下の倍率 |
| `feasibility_penalties` | Object | | 各種ペナルティ設定 |
| `version_history.enabled` | Boolean | `false` | Git履歴ベースの選定有効化 |
| `enable_dynamic_selection` | Boolean | `false` | 動的選定（Phase 4）有効化 |
| `dynamic_selection_dry_run` | Boolean | `true` | 動的選定のドライラン |
| `min_dynamic_confidence` | Number | `0.5` | 動的選定の信頼度閾値 |
| `unresolved_penalty` | Number | `0.2` | 未解決参照ペナルティ |
| `skip_threshold` | Number | `0.5` | スキップ閾値 (Phase 4) |
| `penalty_low_confidence` | Number | `0.4` | 低信頼度時のペナルティ |
| `penalty_unresolved_each` | Number | `0.1` | 個別の未解決参照ごとのペナルティ |
| `penalty_external_each` | Number | `0.2` | 外部依存ごとのペナルティ |
| `penalty_service_loader_low` | Number | `0.2` | ServiceLoader 低優先度ペナルティ |
| `evaluation_max_excluded_rate` | Number | `0.20` | 評価：許容する最大除外率 |
| `evaluation_wasted_potential_ratio` | Number | `0.30` | 評価：無駄なポテンシャルの許容比率 |
| `evaluation_failure_concentration` | Number | `0.50` | 評価：失敗の集中度許容値 |
| `evaluation_min_failure_rate` | Number | `0.15` | 評価：最小失敗率 |

### pipeline

パイプラインの実行ステージを制御します。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `stages` | List[String] | 互換デフォルト | 実行ステージ (`analyze`, `generate`, `report`, `document`, `explore`)。`select` / `brittle_check` は `generate` のターゲット alias として扱われ、workflow ノード（例: `junit-select` / `junit-brittle-check`）で実行されます。`document` / `explore` はオプションで、既定値（`analyze`, `generate`, `report`）には含まれません |
| `workflow_file` | String | (未設定) | workflow 定義 JSON へのパス。相対パスは project root 基準で解決されます。指定時はその workflow を使用し、未指定時は classpath の既定 workflow（`workflows/default-workflow.json`）を使用します |

### execution

実行制御に関する設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `per_task_isolation` | Boolean | `false` | タスク分離実行 |
| `logs_root` | String | `".ful/runs"` | 実行ログルート |
| `runtime_fix_retries` | Integer | (未設定) | 実行時修復リトライ回数 |
| `flaky_reruns` | Integer | `0` | Flaky 判定のための再実行回数 |
| `unresolved_policy` | Enum | `"skip"` | 未解決参照ポリシー (`skip`, `minimal`) |
| `test_stability_policy` | Enum | `"standard"` | 安定性ポリシー (`strict`, `standard`, `relaxed`) |

### verification

テスト実行および検証フェーズの設定です。

**flaky_detection (Flakyテスト検出)**

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `enabled` | Boolean | `true` | 有効化 |
| `rerun_count` | Integer | `0` | 再実行回数 |
| `strategy` | Enum | `"any_pass"` | 判定基準 (`any_pass`, `majority`, `all_differ`) |
| `min_passes_for_flaky` | Integer | `1` | Flaky 判定に必要な最小パス数 |
| `fail_on_flaky` | Boolean | `false` | Flaky 時に失敗とするか |

**test_execution (テスト実行)**

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `timeout_seconds` | Integer | `600` | タイムアウト（秒） |
| `parallel` | Boolean | `false` | 並列実行 |
| `max_workers` | Integer | `1` | 最大ワーカー数 |
| `fail_fast` | Boolean | `false` | 最初の失敗で中断 |
| `continue_on_failure` | Boolean | `true` | 失敗時も継続 |
| `jvm_args` | List[String] | `[]` | JVM 引数 |

### context_awareness

既存テストコードからのスタイル学習設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `enabled` | Boolean | `true` | 有効化 |
| `test_dirs` | List[String] | `["src/test/java"]` | 参照先ディレクトリ |
| `include_globs` | List[String] | `["**/*Test.java"]` | インクルードパターン |
| `exclude_globs` | List[String] | (デフォルト除外設定) | `IT.java`, `IntegrationTest` などを除外 |
| `max_files` | Integer | `200` | 学習最大ファイル数 |
| `max_injected_chars` | Integer | `1200` | プロンプト注入最大文字数 |
| `exclude_generated_tests` | Boolean | `true` | 生成済みテストを学習から除外 |
| `generated_output_dir` | String | (未設定) | 生成出力先判別用 |

### generation

テスト生成の挙動設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `marker.enabled` | Boolean | `true` | 生成マーカー(`FUL:GENERATED_TEST`)の付与 |
| `marker.tag` | String | `"FUL:GENERATED_TEST"` | 使用するタグ |
| `marker.scan_first_lines` | Integer | `20` | スキャン行数 |
| `prompt_template_path` | String | - | プロンプトテンプレートパス |
| `few_shot.enabled` | Boolean | `true` | Few-shot 学習の有効化 |
| `few_shot.examples_dir` | String | - | 例示ファイルディレクトリ |
| `few_shot.max_examples` | Integer | `3` | 最大利用例示数 |
| `few_shot.use_class_type_detection` | Boolean | `true` | クラスタイプによる例示切り替え |
| `temperature` | Number | (未設定) | 温度パラメータ |
| `max_tokens` | Integer | (未設定) | 最大トークン数 |
| `default_model` | String | (未設定) | デフォルトモデル |

### brittle_test_rules

壊れやすいテストコードの検出ルール設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `enabled` | Boolean | `true` | 検出有効化 |
| `fail_on_reflection` | Boolean | `true` | リフレクション使用時に失敗 |
| `fail_on_sleep` | Boolean | `true` | `Thread.sleep` 使用時に失敗 |
| `warn_on_time` | Boolean | `true` | 時間依存時に警告 |
| `warn_on_random` | Boolean | `true` | 乱数依存時に警告 |
| `max_mocks_warn` | Integer | `3` | モック数警告閾値 |
| `max_mocks_fail` | Integer | `6` | モック数失敗閾値 |
| `allowlist_patterns` | List[String] | `[]` | 許可パターン |
| `count_static_mocks` | Boolean | `false` | Staticモックを数に含める |
| `count_stubs` | Boolean | `false` | スタブを数に含める |

### mocking

モック生成に関する設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `enable_static` | Boolean | `false` | Staticメソッドのモック化許可 |
| `enable_external` | Boolean | `false` | 外部連携のモック化許可 |
| `http_stub` | String | `"none"` | HTTPスタブ戦略 |
| `db_stub` | String | `"none"` | DBスタブ戦略 |
| `framework` | String | `"mockito"` | モックフレームワーク |
| `count_static_mocks` | Boolean | `false` | Staticモック数のカウント |
| `count_stubs` | Boolean | `false` | スタブ数のカウント |

### output

出力フォーマット設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `format.tasks` | Enum | `"jsonl"` | タスク出力 (`json`, `yaml`, `jsonl` 等) |
| `format.report` | Enum | `"markdown"` | レポート出力 (`markdown`, `json`, `yaml` 等) |

### log

ログ出力設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `level` | Enum | `"info"` | ログレベル (`debug`, `info`, `warn`, `error`) |
| `format` | Enum | `"human"` | 形式 (`human`, `json`, `yaml`) |
| `output` | Enum | `"console"` | 出力先 (`console`, `file`, `both`) |
| `color` | Enum | `"auto"` | カラー (`auto`, `on`, `off`) |
| `file_path` | String | `"logs/ful.log"` | ファイルパス |
| `include_timestamp` | Boolean | `false` | タイムスタンプ付与 |
| `include_thread` | Boolean | `false` | スレッド名付与 |
| `include_logger` | Boolean | `false` | ロガー名付与 |
| `enable_mdc` | Boolean | `true` | Trace ID等のMDC有効化 |

### governance

ガバナンス・セキュリティ設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `external_transmission` | Enum | `"allow"` | 外部LLMへの送信ポリシー (`allow`, `deny`) |
| `redaction.mode` | Enum | `"enforce"` | 機密情報の秘匿モード (`off`, `report`, `enforce`) |
| `redaction.denylist_path` | String | - | 禁止ワードリスト |
| `redaction.allowlist_path` | String | - | 許可ワードリスト |
| `redaction.detectors` | List[String] | `["regex", "dictionary", "ml"]` | 使用する検出器 |
| `redaction.mask_threshold` | Number | `0.60` | マスク閾値 |
| `redaction.block_threshold` | Number | `0.90` | ブロック閾値 |
| `redaction.ml_endpoint_url` | String | - | ML検出エンドポイント |

### audit

監査ログ設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `enabled` | Boolean | `false` | 監査ログ有効化 |
| `log_path` | String | - | ログパス |
| `include_raw` | Boolean | `false` | 生データを含めるか |

### quota

リソース使用量の制限設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `max_tasks` | Integer | - | 最大タスク数 |
| `max_llm_calls` | Integer | - | 最大LLM呼び出し回数 |
| `on_exceed` | Enum | `"warn"` | 超過時の動作 (`warn`, `block`) |

### local_fix

ローカルでのコード自動修復機能の設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `enable_generics` | Boolean | `false` | ジェネリクス警告の修正 |
| `enable_builder_fix` | Boolean | `false` | Builderパターンの修正 |
| `enable_record_accessor` | Boolean | `false` | Recordアクセサの修正 |
| `enable_redundant_cast_removal` | Boolean | `false` | 冗長キャスト削除 |
| `enable_extended_public_removal` | Boolean | `false` | 不要なpublic修飾子削除 |

### docs

ドキュメント生成設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `format` | Enum | `"markdown"` | 出力形式 (`markdown`, `html`, `pdf`, `all`) |
| `diagram` | Boolean | `false` | 図の生成 |
| `include_tests` | Boolean | `false` | テストリンクを含める |
| `use_llm` | Boolean | `false` | LLMによる解説生成 |
| `diagram_format` | Enum | `"mermaid"` | 図の形式 (`mermaid`, `plantuml`) |
| `single_file` | Boolean | `false` | 単一ファイルにまとめる |
| `test_output_root` | String | - | テスト出力ルートパス |

### quality_gate

品質ゲート（CI/CD連携用）の設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `enabled` | Boolean | `true` | 有効化 |
| `coverage_threshold` | Number | - | カバレッジ閾値 (0.0 - 1.0) |
| `branch_coverage_threshold` | Number | - | 分岐カバレッジ閾値 |
| `block_blocker_findings` | Boolean | `true` | Blocker 指摘で失敗 |
| `block_critical_findings` | Boolean | `true` | Critical 指摘で失敗 |
| `max_major_findings` | Integer | - | Major 指摘の許容数 |
| `allow_warnings` | Boolean | `true` | 警告のみでブロックしない |
| `apply_to_new_code_only` | Boolean | `false` | 新規コードのみ対象 |
| `min_pass_rate` | Number | - | 最低テスト通過率 |
| `min_compile_rate` | Number | - | 最低コンパイル成功率 |
| `coverage_tool` | String | `"jacoco"` | カバレッジツール |
| `static_analysis_tools` | List[String] | `[]` | 静的解析ツール名 |
| `coverage_report_path` | String | - | レポートパス |
| `static_analysis_report_paths` | List[String] | `[]` | 静的解析レポートパス |

### cache

キャッシュ機能の設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `enabled` | Boolean | `true` | キャッシュ有効化 |
| `ttl_days` | Integer | - | 有効期限（日） |
| `evict_on_init` | Boolean | `true` | 初期化時に期限切れを掃除 |
| `version_check` | Boolean | `false` | バージョンチェックを行う |
| `include_lockfile_hash` | Boolean | `false` | ロックファイルハッシュを含む |
| `revalidate` | Boolean | `false` | キャッシュ使用前に再検証 |
| `encrypt` | Boolean | `false` | キャッシュ暗号化 |
| `encryption_key_env` | String | `"FUL_CACHE_KEY"` | 暗号化キー環境変数 |
| `max_size_mb` | Integer | - | 最大キャッシュサイズ (MB) |

### cli

CLIの動作設定です。

| キー | 型 | デフォルト | 説明 |
|------|-----|-----------|------|
| `color` | Enum | `"auto"` | カラー出力設定 |
| `interactive.enabled` | Boolean | `true` | 対話モード有効化 |
| `autocomplete.enabled` | Boolean | `true` | コマンド補完有効化 |

### interceptors

各パイプラインフェーズの前後に任意の処理を挟むインターセプターの設定です。
ステージキー (`ANALYZE`, `GENERATE`, `REPORT`) ごとに設定可能です。`RUN_TESTS` / `RUN` は旧 RUN ステージ互換キーとして受理されます（レガシー）。

**構造例:**

```json
{
  "interceptors": {
    "GENERATE": {
      "pre": [
        {
          "class": "com.example.MyPreInterceptor",
          "enabled": true,
          "order": 10
        }
      ],
      "post": [
        { "class": "com.example.MyPostInterceptor" }
      ]
    }
  }
}
```

---

## LLMプロバイダー別設定

### Gemini (推奨)
```json
{
  "llm": {
    "provider": "gemini",
    "model_name": "gemini-2.0-flash-exp",
    "api_key": "${GEMINI_API_KEY}"
  }
}
```

### OpenAI
```json
{
  "llm": {
    "provider": "openai",
    "model_name": "gpt-4o",
    "api_key": "${OPENAI_API_KEY}"
  }
}
```

### Local (Ollama)
```json
{
  "llm": {
    "provider": "local",
    "url": "http://localhost:11434/v1",
    "model_name": "qwen2.5:7b"
  }
}
```

---

## /config TUI エディタの操作ガイド

`ful tut` は非画面モードのため、設定操作は `/config set|search|validate` を使用します。

### 基本操作
- **数字キー**: カテゴリ選択、項目選択
- **Spaceキー**: Boolean 値の切替
- **Enterキー**: 値の確定
- **Sキー**: 保存 (Save)
- **Qキー**: 終了 (Quit)

インタラクティブに設定を変更し、即座に保存・適用することができます。
