# Config Schema ガイド

このドキュメントは、`config.json` の JSON Schema とバージョン運用ルールをまとめたものです。
スキーマは設定の型・必須項目・許可値を明示し、設定変更時の不整合を防ぎます。

---

## 現行スキーマとファイル一覧

| スキーマバージョン | ファイル | 備考 |
|---|---|---|
| 1.0.0 | `app/src/main/resources/schema/config-schema-v1.json` | 最新・推奨 |
| legacy | `app/src/main/resources/config.schema.json` | 旧式・互換用（新規追加はしない） |

---

## トップレベルのキー（概要）

`schema_version` は `1` または `1.0.0` のように指定できます。`schemaVersion` も互換キーとして受け付けます。

| キー | 型 | 必須 | 説明 |
|---|---|---|---|
| `schema_version` / `schemaVersion` | String/Integer | 任意 | スキーマのバージョン |
| `project` | Object | 必須 | プロジェクト情報 |
| `pipeline` | Object | 任意 | パイプラインステージ制御 |
| `analysis` | Object | 任意 | 解析設定 |
| `selection_rules` | Object | 必須 | 選定ルール |
| `llm` | Object | 必須 | LLM 設定 |
| `execution` | Object | 任意 | 実行制御設定 |
| `output` | Object | 任意 | 出力フォーマット設定 |
| `cli` | Object | 任意 | CLI 表示設定 |
| `audit` | Object | 任意 | 監査ログ設定 |
| `quota` | Object | 任意 | 使用量制限 |
| `context_awareness` | Object | 任意 | 既存テストからの学習設定 |
| `generation` | Object | 任意 | 生成挙動設定 |
| `governance` | Object | 任意 | ガバナンス/セキュリティ |
| `mocking` | Object | 任意 | モック生成設定 |
| `local_fix` | Object | 任意 | ローカル修正ルール |
| `log` | Object | 任意 | ログ出力設定 |
| `docs` | Object | 任意 | ドキュメント生成設定 |
| `quality_gate` | Object | 任意 | 品質ゲート設定 |
| `cache` | Object | 任意 | キャッシュ設定 |
| `interceptors` | Object | 任意 | インターセプター設定 |
| `verification` | Object | 任意 | 検証ステージ設定 |
| `brittle_test_rules` | Object | 任意 | 壊れやすいテストの検出ルール |

> **注意:** スキーマは `additionalProperties: false` のため、未定義のキーはエラーになります。

---

## カテゴリ別キー詳細

### project

| キー | 型 | 説明 |
|---|---|---|
| `id` | String | プロジェクト識別子（必須） |
| `root` | String | プロジェクトルートパス |
| `docs_output` | String | `ful document` の出力先 |
| `repo_url` | String | リポジトリ URL |
| `commit` | String | コミットハッシュ |
| `build_tool` | String | ビルドツール名 |
| `build_command` | String | テスト実行コマンド |
| `exclude_paths` | Array[String] | 解析から除外するパス |
| `include_paths` | Array[String] | 解析対象に含めるパス |

### pipeline

| キー | 型 | 説明 |
|---|---|---|
| `stages` | Array[String] | 実行するステージのリスト（`analyze`, `generate`, `report`, `document`, `explore`）。`select` / `brittle_check` は `generate` のターゲット alias として扱われ、workflow ノードで実行されます。`document` / `explore` はオプションで、既定値には含まれません |
| `workflow_file` | String | workflow 定義 JSON へのパス。相対パスは project root 基準で解決されます。指定時はその workflow を使用し、未指定時は classpath の既定 workflow（`workflows/default-workflow.json`）を使用します |

### analysis

| キー | 型 | 説明 |
|---|---|---|
| `engine` | String | 解析エンジン |
| `source_root_mode` | Enum | `AUTO`, `STRICT` |
| `source_root_paths` | Array[String] | ソースルートパス |
| `source_charset` | String | 文字コード（例: `UTF-8`） |
| `dump_file_list` | Boolean | 解析ファイル一覧出力 |
| `classpath.mode` | Enum | `AUTO`, `STRICT`, `OFF` |
| `spoon.no_classpath` | Boolean | Spoon の noClasspath 切替 |
| `preprocess.mode` | Enum | `OFF`, `AUTO`, `STRICT` |
| `preprocess.tool` | Enum | `AUTO`, `DELOMBOK`, `JAVAC_APT` |
| `preprocess.work_dir` | String | 前処理作業ディレクトリ |
| `preprocess.clean_work_dir` | Boolean | 作業ディレクトリ掃除 |
| `preprocess.include_generated` | Boolean | 生成ソースを解析対象に含める |
| `preprocess.delombok.enabled` | Boolean | Delombok 有効化 |
| `preprocess.delombok.lombok_jar_path` | String | Lombok JAR の明示指定 |
| `enable_interprocedural_resolution` | Boolean | 手続き間解析 |
| `interprocedural_callsite_limit` | Integer | 手続き間解析の呼び出し制限 |
| `external_config_resolution` | Boolean | 外部設定値の解決 |
| `debug_dynamic_resolution` | Boolean | 動的解決のデバッグログ |
| `experimental_candidate_enum` | Boolean | 候補列挙の実験設定 |
| `exclude_tests` | Boolean | テストコードを解析対象から除外 |

### selection_rules

| キー | 型 | 説明 |
|---|---|---|
| `class_min_loc` | Integer | クラス最小 LOC |
| `class_min_method_count` | Integer | メソッド数下限 |
| `max_targets` | Integer | 最大ターゲット数 |
| `strategy` | String | 選定戦略 (デフォルト: SMART) |
| `selection_engine` | String | 選定エンジン (core/rule_based) |
| `method_min_loc` | Integer | メソッド最小 LOC |
| `method_max_loc` | Integer | メソッド最大 LOC |
| `max_methods_per_class` | Integer | クラスあたり最大数 |
| `exclude_getters_setters` | Boolean | getter/setter を除外 |
| `exclude_dead_code` | Boolean | デッドコード除外 |
| `max_methods_per_package` | Integer or null | パッケージ上限 |
| `exclude_annotations` | Array[String] | 除外アノテーション |
| `deprioritize_annotations` | Array[String] | 優先度下げ対象 |
| `priority_annotations` | Array[String] | 優先アノテーション |
| `complexity.max_cyclomatic` | Integer | 複雑度上限 |
| `complexity.strategy` | Enum | `skip`, `warn`, `split`, `specialized_prompt` |
| `complexity.expected_tests_per_complexity` | Number | 複雑度あたりの期待テスト数 |
| `complexity.max_expected_tests` | Integer | 期待テスト数の上限 |
| `removal_boost` | Number | 削除/非推奨 API 使用時の加点 |
| `deprioritize_factor` | Number | 低優先の倍率 (0.0-1.0) |
| `feasibility_penalties.*` | Number | 各種ペナルティ設定 (external_io, etc.) |
| `scoring_weights.*` | Number | スコアリングの重み設定 |
| `version_history.enabled` | Boolean | Git 履歴ベースの選定 |
| `enable_dynamic_selection` | Boolean | 動的選定の有効化 |
| `min_dynamic_confidence` | Number | 動的選定の信頼度閾値 |
| `unresolved_penalty` | Number | 未解決参照のペナルティ |

### llm

| キー | 型 | 説明 |
|---|---|---|
| `provider` | Enum | `gemini`, `openai`, `anthropic`, etc. |
| `allowed_providers` | Array[String] | 利用可能プロバイダーの許可リスト |
| `allowed_models` | Map[String, List[String]] | プロバイダー別モデル許可リスト |
| `max_retries` | Integer | 生成リトライ回数 |
| `fix_retries` | Integer | 修復リトライ回数 |
| `model_name` | String | モデル名 |
| `api_key` | String | API キー |
| `url` | String | エンドポイント |
| `azure_deployment` | String | Azure OpenAI のデプロイ名 |
| `azure_api_version` | String | Azure OpenAI の API バージョン |
| `vertex_project` | String | Vertex AI の GCP プロジェクト |
| `vertex_location` | String | Vertex AI リージョン |
| `aws_access_key_id` | String | AWS アクセスキー ID |
| `connect_timeout` | Integer | 接続タイムアウト（秒） |
| `request_timeout` | Integer | リクエストタイムアウト（秒） |
| `max_response_length` | Integer | レスポンス長上限（文字数） |
| `custom_headers` | Map[String, String] | 追加 HTTP ヘッダー |
| `fallback_stub_enabled` | Boolean | フォールバックスタブ生成 |
| `javac_validation` | Boolean | javac 検証 |
| `retry_initial_delay_ms` | Integer | リトライ初期遅延（ms） |
| `rate_limit_qps` | Number | レート制限（QPS） |
| `circuit_breaker_threshold` | Integer | サーキットブレーカ閾値 |
| `deterministic` | Boolean | 決定論モード |
| `seed` | Integer | 決定論モード用シード |
| `temperature` | Number | 生成の温度パラメータ |
| `max_tokens` | Integer | 最大トークン数 |
| `system_message` | String | システムメッセージ |
| `smart_retry.*` | Object | スマートリトライ設定 |

### execution

| キー | 型 | 説明 |
|---|---|---|
| `per_task_isolation` | Boolean | タスク分離実行 |
| `logs_root` | String | 実行成果物のルートディレクトリ |
| `runtime_fix_retries` | Integer | 実行時修復リトライ回数 |
| `flaky_reruns` | Integer | Flaky 判定の再実行回数 |
| `unresolved_policy` | Enum | `skip`, `minimal` |
| `test_stability_policy` | Enum | `strict`, `standard`, `relaxed` |

### verification

| キー | 型 | 説明 |
|---|---|---|
| `flaky_detection.enabled` | Boolean | Flaky 検知の有効化 |
| `flaky_detection.rerun_count` | Integer | 再実行回数 |
| `flaky_detection.strategy` | Enum | `ANY_PASS`, `MAJORITY`, `ALL_DIFFER` |
| `flaky_detection.fail_on_flaky` | Boolean | Flaky 時のビルド失敗可否 |
| `test_execution.timeout_seconds` | Integer | テスト実行タイムアウト |
| `test_execution.parallel` | Boolean | 並列実行 |
| `test_execution.max_workers` | Integer | 並列ワーカー数 |
| `test_execution.fail_fast` | Boolean | 初回失敗で中断 |
| `test_execution.continue_on_failure` | Boolean | 失敗時の継続可否 |
| `test_execution.jvm_args` | Array[String] | JVM 引数 |

### brittle_test_rules

| キー | 型 | 説明 |
|---|---|---|
| `enabled` | Boolean | ルールの有効化 |
| `fail_on_reflection` | Boolean | リフレクション使用時の失敗設定 |
| `fail_on_sleep` | Boolean | sleep 使用時の失敗設定 |
| `warn_on_time` | Boolean | 時間依存の警告設定 |
| `warn_on_random` | Boolean | ランダム値使用の警告設定 |
| `max_mocks_warn` | Integer | モック過多警告の閾値 |
| `max_mocks_fail` | Integer | モック過多失敗の閾値 |
| `allowlist_patterns` | Array[String] | 許可パターン |
| `count_static_mocks` | Boolean | static モックのカウント有無 |
| `count_stubs` | Boolean | スタブのカウント有無 |

### context_awareness

| キー | 型 | 説明 |
|---|---|---|
| `enabled` | Boolean | 機能の有効化 |
| `test_dirs` | Array[String] | テストディレクトリ一覧 |
| `include_globs` | Array[String] | インクルードパターン |
| `exclude_globs` | Array[String] | 除外パターン |
| `max_files` | Integer | 最大参照ファイル数 |
| `max_injected_chars` | Integer | プロンプト注入最大文字数 |
| `exclude_generated_tests` | Boolean | 生成されたテストの除外 |
| `generated_output_dir` | String | 生成出力先ディレクトリ |

### generation

| キー | 型 | 説明 |
|---|---|---|
| `marker.enabled` | Boolean | マーカーコメントの有効化 |
| `marker.tag` | String | マーカータグ |
| `prompt_template_path` | String | プロンプトテンプレートパス |
| `few_shot.enabled` | Boolean | Few-shot の有効化 |
| `few_shot.max_examples` | Integer | 最大例示数 |
| `temperature` | Number | 生成温度 |
| `max_tokens` | Integer | 最大トークン数 |
| `default_model` | String | デフォルトモデル |

### mocking

| キー | 型 | 説明 |
|---|---|---|
| `enable_static` | Boolean | Static モックの有効化 |
| `enable_external` | Boolean | 外部サービスモックの有効化 |
| `http_stub` | String | HTTP スタブ設定 |
| `db_stub` | String | DB スタブ設定 |
| `framework` | String | モックフレームワーク (mockito等) |
| `count_static_mocks` | Boolean | 静的モックのカウント |
| `count_stubs` | Boolean | スタブのカウント |

### governance

| キー | 型 | 説明 |
|---|---|---|
| `external_transmission` | Enum | `allow`, `deny` |
| `redaction.mode` | Enum | `off`, `report`, `enforce` |
| `redaction.denylist_path` | String | 禁止ワードリストパス |
| `redaction.allowlist_path` | String | 許可ワードリストパス |
| `redaction.detectors` | Array[String] | 検出器リスト |
| `redaction.ml_endpoint_url` | String | ML検出エンドポイント |

### audit

| キー | 型 | 説明 |
|---|---|---|
| `enabled` | Boolean | 有効化 |
| `log_path` | String | ログパス |
| `include_raw` | Boolean | 生データを含めるか |

### quota

| キー | 型 | 説明 |
|---|---|---|
| `max_tasks` | Integer | 最大タスク数 |
| `max_llm_calls` | Integer | 最大 LLM 呼び出し数 |
| `on_exceed` | Enum | `warn`, `block` |

### local_fix

| キー | 型 | 説明 |
|---|---|---|
| `enable_generics` | Boolean | ジェネリクス修正の有効化 |
| `enable_builder_fix` | Boolean | Builder 修正の有効化 |
| `enable_record_accessor` | Boolean | Record アクセサ修正の有効化 |
| `enable_redundant_cast_removal` | Boolean | 冗長キャスト削除 |
| `enable_extended_public_removal` | Boolean | 拡張 public 削除 |

### output

| キー | 型 | 説明 |
|---|---|---|
| `format.tasks` | Enum | `json`, `yaml`, `yml`, `jsonl` |
| `format.report` | Enum | `markdown`, `json`, `yaml`, `yml` |

### log

| キー | 型 | 説明 |
|---|---|---|
| `format` | Enum | `human`, `json`, `yaml` |
| `level` | Enum | `debug`, `info`, `warn`, `error` |
| `output` | Enum | `console`, `file`, `both` |
| `color` | Enum | `auto`, `on`, `off` |
| `file_path` | String | ログファイルパス |
| `include_timestamp` | Boolean | タイムスタンプ付与 |
| `include_thread` | Boolean | スレッド名付与 |
| `include_logger` | Boolean | ロガー名付与 |
| `enable_mdc` | Boolean | MDC (Trace ID等) の有効化 |

### docs

| キー | 型 | 説明 |
|---|---|---|
| `format` | Enum | `markdown`, `html`, `pdf`, `all` |
| `diagram` | Boolean | 図生成の有効化 |
| `include_tests` | Boolean | テストリンクを含めるか |
| `use_llm` | Boolean | LLM による文書生成 |
| `diagram_format` | Enum | `mermaid`, `plantuml` |
| `single_file` | Boolean | 単一ファイルへの統合 |
| `test_output_root` | String | テスト出力ルート |

### quality_gate

| キー | 型 | 説明 |
|---|---|---|
| `enabled` | Boolean | 有効化 |
| `coverage_threshold` | Number | カバレッジ閾値 (0.0-1.0) |
| `branch_coverage_threshold` | Number | 分岐カバレッジ閾値 |
| `block_blocker_findings` | Boolean | Blocker 指摘でブロック |
| `block_critical_findings` | Boolean | Critical 指摘でブロック |
| `max_major_findings` | Integer | Major 指摘の許容数 |
| `allow_warnings` | Boolean | 警告のみでブロックしない |
| `apply_to_new_code_only` | Boolean | 新規コードのみ対象 |
| `min_pass_rate` | Number | 最低テスト通過率 |
| `min_compile_rate` | Number | 最低コンパイル成功率 |
| `coverage_tool` | String | `jacoco` or `cobertura` |
| `static_analysis_tools` | Array[String] | 静的解析ツール名リスト |
| `coverage_report_path` | String | レポートパス |
| `static_analysis_report_paths` | Array[String] | レポートパスリスト |

### cache

| キー | 型 | 説明 |
|---|---|---|
| `enabled` | Boolean | 有効化 |
| `ttl_days` | Integer | 有効期限（日） |
| `evict_on_init` | Boolean | 初期化時の掃除 |
| `version_check` | Boolean | バージョンチェック |
| `include_lockfile_hash` | Boolean | ロックファイルハッシュを含む |
| `revalidate` | Boolean | 使用前の再検証 |
| `encrypt` | Boolean | 暗号化 |
| `encryption_key_env` | String | 暗号化キー環境変数名 |
| `max_size_mb` | Integer | 最大キャッシュサイズ (MB) |

### cli

| キー | 型 | 説明 |
|---|---|---|
| `color` | String | カラー設定 |
| `interactive.enabled` | Boolean | 対話モードの有効化 |
| `autocomplete.enabled` | Boolean | 補完の有効化 |

### interceptors

| キー | 型 | 説明 |
|---|---|---|
| `ANALYZE` | Object | Analyze ステージのインターセプター設定 |

| `GENERATE` | Object | Generate ステージのインターセプター設定 |
| `RUN_TESTS` | Object | 旧 RUN ステージ互換のインターセプター設定（レガシー） |
| `RUN` | Object | 旧 RUN ステージ互換のインターセプター設定（レガシー） |

| `REPORT` | Object | Report ステージのインターセプター設定 |

*(各ステージ配下に `pre`, `post` のリストを設定可。要素は `class`, `enabled`, `order`)*

---

## スキーマ導入・バージョン運用ルール

- `schema_version` は **SemVer 形式** (`MAJOR.MINOR.PATCH`) もしくは整数（`1`）を使用します。
- `schema_version` の **メジャー番号**に対応する `config-schema-v{major}.json` を読み込みます。
- **古いスキーマは削除せずに残します。** 既存の設定ファイルの検証に利用します。

### スキーマ更新手順

1. **JSON Schema を編集**: `app/src/main/resources/schema/config-schema-vX.json`
2. **モデル同期**: `Config.java` およびバリデーターとの整合性を確認。
3. **ドキュメント更新**: このファイルおよび `cli-options.md` 等。
4. **サンプル更新**: `config.example.json` の反映。
