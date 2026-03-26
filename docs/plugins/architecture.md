# Plugin Architecture

FUL plugins are loaded through the `ActionPlugin` SPI using `ServiceLoader`.
The current standard execution path is **workflow-driven execution through `WorkflowNodeStage`**.

## 1. Current Execution Model

### 1.1 Top-Level Stage Execution

`PipelineFactory` registers `analyze`, `generate`, `report`, `document`, and `explore` as top-level stages.
`select` and `brittle_check` are not separate top-level stages; they are treated as aliases under `generate`.

- `PipelineFactory#create()` resolves the workflow and registers node-specific `WorkflowNodeStage` instances into `Pipeline`
- `PluginHookedStage` still exists in code, but the current `PipelineFactory` does not wire it into the standard path

### 1.2 Workflow Execution (`WorkflowNodeStage`)

`PipelineFactory` loads the workflow definition and executes nodes as `WorkflowNodeStage` instances in dependency order.
`pipeline.workflow_file` is optional. If it is not set, the default classpath workflow `workflows/default-workflow.json` is used.

Main workflow node keys:

- `id`
- `plugin`
- `depends_on`
- `with`
- `on_failure` (`STOP`, `CONTINUE`, `SKIP_DOWNSTREAM`)
- `retry` (`max`, `backoff_ms`)
- `timeout_sec`
- `enabled`

### 1.3 Plugin Resolution for Workflow Nodes

`WorkflowPlanResolver` does the following:

1. Resolves `plugin` values through `PluginRegistry.findById(...)`
2. Verifies that the resolved plugin returns `PluginKind.WORKFLOW` from `kind()`

Because of this, only plugins of kind `WORKFLOW` can run as workflow nodes.

## 2. How `PluginKind` Is Used

Available `PluginKind` values:

- `ANALYZE`
- `GENERATE`
- `RUN`
- `REPORT`
- `WORKFLOW`

In the current standard execution path, the design is centered on `WORKFLOW` plugins executed through workflow nodes.
`ANALYZE`, `GENERATE`, `RUN`, and `REPORT` remain as enum values, but the standard `PipelineFactory` does not wire the older hook-based execution path.

## 3. Plugin Loading

`PluginRegistryLoader` searches for `ActionPlugin` implementations in this order:

1. The application classpath through the standard `ServiceLoader`
2. Additional classpath entries passed through `--plugin-classpath` as JARs or directories

`--plugin-classpath` can contain multiple entries separated by the OS path separator.

- Linux / macOS: `:`
- Windows: `;`

Behavior rules:

- Invalid or missing classpath entries are skipped with a warning
- Duplicate plugin IDs are skipped with a warning for the later entry
- `PluginRegistry` also validates plugin ID uniqueness

## 4. SPI

### 4.1 `ActionPlugin`

Implementation requirements:

- `id()` must return a unique plugin ID
- `kind()` must return a `PluginKind`
- `execute(PluginContext)` must implement the plugin behavior

Registration:

- Add the implementation FQCN to `META-INF/services/com.craftsmanbro.fulcraft.kernel.plugin.api.ActionPlugin`

### 4.2 `PluginContext` / `PluginResult`

`PluginContext` provides access to:

- `RunContext`
- `ArtifactStore`
- `PluginConfig`
- `nodeId` and `nodeConfig` during workflow execution

`PluginResult` carries `pluginId`, `success`, `message`, and `error`.
Plugin kind is determined from `ActionPlugin.kind()`, not from `PluginResult`.

### 4.3 `PhaseInterceptor`

Separate from `ActionPlugin`, phase-level pre/post behavior can be extended through `PhaseInterceptor`.
Register implementations in `META-INF/services/com.craftsmanbro.fulcraft.kernel.pipeline.interceptor.PhaseInterceptor`.

## 5. Plugin Configuration and Artifacts

Per-plugin configuration is stored under:

- `.ful/plugins/<plugin-id>/config/config.json`
- `.ful/plugins/<plugin-id>/config/schema.json` (optional)

`PluginConfigLoader` reads `config/config.json` and validates against `config/schema.json` when present.
The legacy layout `.ful/plugins/<plugin-id>/config.json` and `.ful/plugins/<plugin-id>/schema.json` is still accepted for backward compatibility.
During workflow execution, artifacts are separated per node under `actions/<pluginId>/<nodeId>`.

## 6. Adding a Plugin for Workflow Execution

1. Create an `ActionPlugin` implementation class
2. Return `PluginKind.WORKFLOW` from `kind()`
3. Register the plugin in `META-INF/services/...ActionPlugin`
4. Put the plugin ID in `nodes[].plugin` inside the workflow JSON
5. Set `pipeline.workflow_file` in `config.json` if needed; otherwise the default workflow is used
6. For an external JAR, pass `--plugin-classpath` when required

Example on Linux / macOS:

```bash
./scripts/ful run \
  --plugin-classpath "/path/to/my-plugin.jar:/path/to/plugin-classes" \
  -c config.json
```

## 7. Bundled Plugins

The currently bundled workflow plugins are the JUnit suite plugins: `junit-select`, `junit-generate`, and `junit-brittle-check`.
For details, see [JUnit Plugin Documents](junit/index.md).
