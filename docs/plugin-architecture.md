# Plugin Architecture

FUL のプラグイン実行は `ActionPlugin` SPI + workflow DAG を中心に構成されています。  
現行の標準実行経路は **`PipelineFactory#create()` が workflow ノードを `Pipeline` に直接登録する方式** です。

## 1. 実行モデル（現行）

### 1.1 全体フロー

`ful run` 系コマンドでは、次の順でプラグイン実行準備が行われます。

1. `PluginRegistryLoader` で `ActionPlugin` を収集
2. `WorkflowLoader` で workflow 定義をロード
3. `WorkflowPlanResolver` で plugin 解決と DAG 検証
4. `PipelineFactory` がノードごとの `Stage`（`WorkflowNodeStage`）を `Pipeline` に登録
5. `Pipeline` が依存順でノードを実行

### 1.2 workflow 定義のロード

`WorkflowLoader` の入力は `pipeline.workflow_file` です。

- `pipeline.workflow_file` 指定あり: project root 相対（または絶対）で JSON を読む
- `pipeline.workflow_file` 指定なし: classpath の `workflows/default-workflow.json` を読む

workflow ノードで使用する主キー:

- `id`
- `plugin`
- `depends_on`
- `with`
- `on_failure` (`STOP` / `CONTINUE` / `SKIP_DOWNSTREAM`)
- `retry` (`max`, `backoff_ms`)
- `timeout_sec`
- `enabled`

### 1.3 workflow 解決とバリデーション

`WorkflowPlanResolver` は次を検証します。

1. enabled ノードのみを対象化
2. ノード形状（`id` と `plugin` 必須）を検証
3. `depends_on` の欠落参照と循環依存を検証
4. `plugin` を `PluginRegistry.findById(...)` で解決
5. 解決 plugin の `kind()` が `PluginKind.WORKFLOW` であることを検証

このため、workflow ノードで実行できるのは `WORKFLOW` kind の plugin のみです。

### 1.4 `pipeline.stages` の扱い

`pipeline.stages` は「トップレベル固定ステージ列」ではなく、workflow ノードのフィルタとして使われます。

- `analyze` -> `Step.ANALYZE`
- `generate` / `select` / `brittle_check` -> `Step.GENERATE`
- `report` -> `Step.REPORT`
- `document` -> `Step.DOCUMENT`
- `explore` -> `Step.EXPLORE`

ノード側の `Step` 判定は `PipelineFactory#stepForNode(...)` で行われます。

- `pluginId == analyze-builtin` または `nodeId == analyze` -> `Step.ANALYZE`
- `pluginId == report-builtin` または `nodeId == report` -> `Step.REPORT`
- `pluginId == document-builtin` または `nodeId == document` -> `Step.DOCUMENT`
- `pluginId == explore-builtin` または `nodeId == explore` -> `Step.EXPLORE`
- それ以外 -> `Step.GENERATE`

対象 `Step` のノードだけでなく、依存ノードは再帰的に自動補完されます。

### 1.5 ノード実行時の失敗制御

`WorkflowNodeStage`（`PipelineFactory` 内部クラス）で以下を実装しています。

- `retry.max` / `retry.backoff_ms` による再試行
- `timeout_sec` による実行タイムアウト
- `on_failure` の制御
- `STOP`: 失敗で停止
- `CONTINUE`: 警告化して継続
- `SKIP_DOWNSTREAM`: 下流依存ノードをスキップ

## 2. プラグインロード

`PluginRegistryLoader` は `ActionPlugin` を次の順で探索します。

1. アプリ本体 classpath（通常の `ServiceLoader`）
2. `--plugin-classpath` で指定された追加 classpath

`--plugin-classpath` は OS の path separator 区切りです。

- Linux / macOS: `:`
- Windows: `;`

ルール:

- 不正・不存在エントリは warning を出してスキップ
- plugin id 重複は後続を warning 付きでスキップ
- `PluginRegistry` でも id の一意性を検証

## 3. SPI 契約

### 3.1 `ActionPlugin`

実装要件:

- `id()` で一意な plugin id を返す
- `kind()` で `PluginKind` を返す
- `execute(PluginContext)` で処理を実装する

登録方法:

- `META-INF/services/com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin` に実装クラス FQCN を列挙

### 3.2 `PluginKind`

列挙値:

- `ANALYZE`
- `GENERATE`
- `RUN`
- `REPORT`
- `WORKFLOW`

標準の workflow 実行経路で実行対象になるのは `WORKFLOW` です。

### 3.3 `PluginContext`

workflow 実行時の `PluginContext` には以下が入ります。

- `RunContext`
- `ArtifactStore`（node スコープ）
- `PluginConfig`
- `nodeId`
- `nodeConfig`（workflow の `with`）
- `ServiceRegistry`

`ServiceRegistry` は composition root（`PipelineFactory`）で注入されます。  
同梱 plugin では `AnalysisPort` と `DocumentFlow` をここから取得しています。

### 3.4 `PluginResult`

`PluginResult` は以下の最小情報を持ちます。

- `pluginId`
- `success`
- `message`
- `error`

plugin kind 判定は `PluginResult` ではなく `ActionPlugin.kind()` で扱います。

## 4. 設定と成果物

plugin ごとの設定配置:

- `.ful/plugins/<plugin-id>/config/config.json`
- `.ful/plugins/<plugin-id>/config/schema.json`（任意）

`PluginConfigLoader` は `config/config.json` を読み込み、`config/schema.json` があれば JSON Schema で検証します。  
(旧配置 `.ful/plugins/<plugin-id>/config.json` / `schema.json` は後方互換として読み取り可能です)  
成果物は node 単位で分離され、`actions/<pluginId>/<nodeId>` 配下に出力されます。

## 5. デフォルト workflow と同梱 plugin

デフォルト workflow（`workflows/default-workflow.json`）は以下のノードを持ちます。

- `analyze` (`analyze-builtin`)
- `select` (`junit-select`) depends on `analyze`
- `generate` (`junit-generate`) depends on `select`
- `brittle_check` (`junit-brittle-check`) depends on `generate`
- `report` (`report-builtin`) depends on `brittle_check`
- `document` (`document-builtin`) depends on `analyze`
- `explore` (`explore-builtin`) depends on `document`

`META-INF/services/...ActionPlugin` には上記に加えて `noop-generate`（`PluginKind.GENERATE`）も登録されています。  
ただし workflow ノード実行では `WORKFLOW` kind 必須のため、`noop-generate` は標準 workflow では参照できません。

## 6. 拡張手順（外部 plugin）

1. `ActionPlugin` 実装を作成する
2. workflow で使う場合は `kind()` で `PluginKind.WORKFLOW` を返す
3. `META-INF/services/...ActionPlugin` に実装 FQCN を登録する
4. workflow JSON の `nodes[].plugin` に plugin id を記述する
5. 必要に応じて `.ful/plugins/<plugin-id>/config/config.json` を配置する
6. 外部 jar の場合は `--plugin-classpath` を指定して実行する

実行例（Linux / macOS）:

```bash
./scripts/ful run \
  --plugin-classpath "/path/to/my-plugin.jar:/path/to/plugin-classes" \
  -c config.json
```

## 7. 補足

- `PluginHookedStage` はコード上に存在しますが、標準 `run` の組み立て（`PipelineFactory#create()`）では workflow ノード直登録（`WorkflowNodeStage`）を使用します。
- フェーズ前後の拡張は `PhaseInterceptor` SPI（`META-INF/services/com.craftsmanbro.fulcraft.kernel.pipeline.interceptor.PhaseInterceptor`）で利用できます。
- JUnit 詳細は [JUnit Plugin Documents](junit/index.md) を参照してください。
