# Plugin Architecture

FUL のプラグインは `ActionPlugin` SPI を `ServiceLoader` で読み込んで実行します。  
現行の標準実行経路は **workflow 駆動（`WorkflowNodeStage`）** です。

## 1. 現在の実行モデル

### 1.1 トップレベルのステージ実行

`PipelineFactory` はトップレベルとして `analyze / generate / report / document / explore` を登録します。  
`select` と `brittle_check` はトップレベル別ステージではなく、`generate` と同義 alias として扱われます。

- `PipelineFactory#create()` は workflow を解決し、ノードごとの `WorkflowNodeStage` を `Pipeline` へ登録します
- `PluginHookedStage` クラスは存在しますが、現行の `PipelineFactory` では配線していません（未使用）

### 1.2 Workflow 実行（`WorkflowNodeStage`）

`PipelineFactory` が workflow 定義を読み込み、ノードを依存順（DAG）に `WorkflowNodeStage` として実行します。  
`pipeline.workflow_file` は任意で、未設定時は classpath の既定 workflow（`workflows/default-workflow.json`）が使用されます。

workflow ノードで使う主なキー:

- `id`
- `plugin`
- `depends_on`
- `with`
- `on_failure` (`STOP` / `CONTINUE` / `SKIP_DOWNSTREAM`)
- `retry` (`max`, `backoff_ms`)
- `timeout_sec`
- `enabled`

### 1.3 workflow ノードのプラグイン解決

`WorkflowPlanResolver` は以下を行います。

1. `plugin`（plugin id）を `PluginRegistry.findById(...)` で解決
2. 解決した plugin の `kind()` が `PluginKind.WORKFLOW` であることを検証

このため、workflow ノードで実行できるのは `WORKFLOW` kind の plugin のみです。

## 2. PluginKind の扱い

`PluginKind` の列挙値:

- `ANALYZE`
- `GENERATE`
- `RUN`
- `REPORT`
- `WORKFLOW`

現行の標準実行経路では、workflow ノード経由で `WORKFLOW` を使う設計が中心です。  
`ANALYZE / GENERATE / RUN / REPORT` は列挙値として残っていますが、標準の `PipelineFactory` では hook 実行経路を配線していません。

## 3. プラグインのロード

`PluginRegistryLoader` は次の順で `ActionPlugin` を探索します。

1. アプリ本体 classpath（通常の `ServiceLoader`）
2. `--plugin-classpath` で追加指定された classpath（jar / directory）

`--plugin-classpath` は OS の path separator 区切りで複数指定できます。

- Linux / macOS: `:`
- Windows: `;`

動作ルール:

- 不正または存在しない classpath エントリは warning を出してスキップ
- plugin id 重複時は後続を warning 付きでスキップ
- `PluginRegistry` では id の一意性を検証

## 4. SPI

### 4.1 ActionPlugin

実装要件:

- `id()` で一意な plugin id を返す
- `kind()` で `PluginKind` を返す
- `execute(PluginContext)` で処理を実装する

登録方法:

- `META-INF/services/com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin` に実装クラス FQCN を列挙

### 4.2 PluginContext / PluginResult

`PluginContext` では次を参照できます。

- `RunContext`
- `ArtifactStore`
- `PluginConfig`
- workflow 実行時の `nodeId` / `nodeConfig`

`PluginResult` は `pluginId`・`success`・`message`・`error` を保持します。  
plugin kind は `PluginResult` ではなく `ActionPlugin.kind()` 側で扱います。

### 4.3 PhaseInterceptor

`ActionPlugin` とは別に、フェーズ前後処理は `PhaseInterceptor` で拡張できます。  
`META-INF/services/com.craftsmanbro.fulcraft.kernel.pipeline.interceptor.PhaseInterceptor` に登録します。

## 5. プラグイン設定と成果物

plugin ごとの設定配置:

- `.ful/plugins/<plugin-id>/config/config.json`
- `.ful/plugins/<plugin-id>/config/schema.json`（任意）

`PluginConfigLoader` が `config/config.json` を読み込み、`config/schema.json` があればバリデーションします。  
(旧配置 `.ful/plugins/<plugin-id>/config.json` / `schema.json` は後方互換として読み取り可能です)  
workflow 実行時の成果物は node 単位で分離され、`actions/<pluginId>/<nodeId>` 配下に出力されます。

## 6. プラグイン追加手順（workflow 前提）

1. `ActionPlugin` 実装クラスを作成する
2. `kind()` は `PluginKind.WORKFLOW` を返す
3. plugin jar（またはアプリ本体）に `META-INF/services/...ActionPlugin` を登録する
4. workflow JSON の `nodes[].plugin` に plugin id を記述する
5. 必要に応じて `config.json` で `pipeline.workflow_file` を設定する（未設定時は既定 workflow）
6. 外部 jar の場合は必要に応じて `--plugin-classpath` を指定する

実行例（Linux / macOS）:

```bash
./scripts/ful run \
  --plugin-classpath "/path/to/my-plugin.jar:/path/to/plugin-classes" \
  -c config.json
```

## 7. 同梱プラグイン

現行の同梱 workflow プラグインは JUnit スイート（`junit-select` / `junit-generate` / `junit-brittle-check`）です。  
詳細は [JUnit Plugin Documents](junit/index.md) を参照してください。
