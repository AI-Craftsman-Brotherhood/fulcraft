# Plugin Architecture

FUL plugin execution is centered on the `ActionPlugin` SPI and a workflow DAG.
In the current standard execution path, **`PipelineFactory#create()` registers workflow nodes directly into `Pipeline`**.

## 1. Execution Model

### 1.1 Overall Flow

For `ful run` commands, plugin execution is prepared in this order:

1. `PluginRegistryLoader` collects `ActionPlugin` implementations
2. `WorkflowLoader` loads the workflow definition
3. `WorkflowPlanResolver` resolves plugins and validates the DAG
4. `PipelineFactory` registers node-specific `Stage` instances (`WorkflowNodeStage`) into `Pipeline`
5. `Pipeline` executes nodes in dependency order

### 1.2 Loading Workflow Definitions

`WorkflowLoader` reads from `pipeline.workflow_file`.

- If `pipeline.workflow_file` is set, it reads JSON relative to project root or from an absolute path.
- If it is not set, it reads `workflows/default-workflow.json` from the classpath.

Main workflow node keys:

- `id`
- `plugin`
- `depends_on`
- `with`
- `on_failure` (`STOP`, `CONTINUE`, `SKIP_DOWNSTREAM`)
- `retry` (`max`, `backoff_ms`)
- `timeout_sec`
- `enabled`

### 1.3 Workflow Resolution and Validation

`WorkflowPlanResolver` validates:

1. only enabled nodes
2. required node shape, especially `id` and `plugin`
3. missing `depends_on` references and dependency cycles
4. plugin resolution through `PluginRegistry.findById(...)`
5. that the resolved plugin has `PluginKind.WORKFLOW`

Only `WORKFLOW` plugins can be executed as workflow nodes.

### 1.4 How `pipeline.stages` Works

`pipeline.stages` is not treated as a fixed top-level stage list. It filters workflow nodes.

- `analyze` -> `Step.ANALYZE`
- `generate`, `select`, `brittle_check` -> `Step.GENERATE`
- `report` -> `Step.REPORT`
- `document` -> `Step.DOCUMENT`
- `explore` -> `Step.EXPLORE`

Node-to-step mapping is implemented in `PipelineFactory#stepForNode(...)`.
Dependencies required by selected steps are added recursively.

### 1.5 Failure Handling During Node Execution

`WorkflowNodeStage` implements:

- retries with `retry.max` and `retry.backoff_ms`
- execution timeout via `timeout_sec`
- failure policy via `on_failure`
  - `STOP`
  - `CONTINUE`
  - `SKIP_DOWNSTREAM`

## 2. Plugin Loading

`PluginRegistryLoader` searches for `ActionPlugin` implementations in this order:

1. the main application classpath through standard `ServiceLoader`
2. additional classpaths passed through `--plugin-classpath`

`--plugin-classpath` uses the OS path separator.

- Linux and macOS: `:`
- Windows: `;`

Rules:

- Invalid or missing entries are skipped with warnings.
- Duplicate plugin IDs are skipped with warnings.
- `PluginRegistry` also validates ID uniqueness.

## 3. SPI Contract

### 3.1 `ActionPlugin`

Implementation requirements:

- `id()` returns a unique plugin ID
- `kind()` returns a `PluginKind`
- `execute(PluginContext)` performs the work

Registration:

- Add the implementation FQCN to `META-INF/services/com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin`

### 3.2 `PluginKind`

Available values:

- `ANALYZE`
- `GENERATE`
- `RUN`
- `REPORT`
- `WORKFLOW`

The standard workflow path executes `WORKFLOW` plugins.

### 3.3 `PluginContext`

In workflow execution, `PluginContext` contains:

- `RunContext`
- `ArtifactStore` scoped to the node
- `PluginConfig`
- `nodeId`
- `nodeConfig` from workflow `with`
- `ServiceRegistry`

`ServiceRegistry` is injected by the composition root, `PipelineFactory`.
Bundled plugins currently obtain services such as `AnalysisPort` and `DocumentFlow` through it.

### 3.4 `PluginResult`

`PluginResult` carries at least:

- `pluginId`
- `success`
- `message`
- `error`

Plugin kind is determined from `ActionPlugin.kind()`, not from `PluginResult`.

## 4. Configuration and Artifacts

Per-plugin configuration lives under:

- `.ful/plugins/<plugin-id>/config/config.json`
- `.ful/plugins/<plugin-id>/config/schema.json` (optional)

`PluginConfigLoader` reads `config/config.json` and validates it with `config/schema.json` when present.
The legacy layout `.ful/plugins/<plugin-id>/config.json` and `schema.json` is still accepted for backward compatibility.
Artifacts are isolated per node under `actions/<pluginId>/<nodeId>`.

## 5. Default Workflow and Bundled Plugins

The default workflow in `workflows/default-workflow.json` contains:

- `analyze` (`analyze-builtin`)
- `select` (`junit-select`) depends on `analyze`
- `generate` (`junit-generate`) depends on `select`
- `brittle_check` (`junit-brittle-check`) depends on `generate`
- `report` (`report-builtin`) depends on `brittle_check`
- `document` (`document-builtin`) depends on `analyze`
- `explore` (`explore-builtin`) depends on `document`

`META-INF/services/...ActionPlugin` also registers `noop-generate` with `PluginKind.GENERATE`.
It is not usable through the standard workflow path because workflow execution requires `WORKFLOW` kind.

## 6. Adding External Plugins

1. Implement `ActionPlugin`
2. Return `PluginKind.WORKFLOW` from `kind()` if it will be used from a workflow
3. Register the implementation FQCN in `META-INF/services/...ActionPlugin`
4. Reference the plugin ID in `nodes[].plugin` inside the workflow JSON
5. Add `.ful/plugins/<plugin-id>/config/config.json` if needed
6. Pass `--plugin-classpath` when running external JARs

Example on Linux or macOS:

```bash
./scripts/ful run \
  --plugin-classpath "/path/to/my-plugin.jar:/path/to/plugin-classes" \
  -c config.json
```

## 7. Notes

- `PluginHookedStage` still exists in code, but the standard `run` assembly path inside `PipelineFactory#create()` uses direct workflow node registration through `WorkflowNodeStage`.
- Phase-level extension hooks are also available through the `PhaseInterceptor` SPI in `META-INF/services/com.craftsmanbro.fulcraft.kernel.pipeline.interceptor.PhaseInterceptor`.
- For JUnit-specific details, see [JUnit Plugin Documents](junit/index.md).
