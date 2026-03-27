# FUL Configuration Reference

This document explains the structure of FUL configuration files (`config.json`) and the main settings in each section.
Validation is driven by the JSON Schema.

---

## Table of Contents

1. [Basics](#basics)
2. [Top-Level Categories](#top-level-categories)
3. [Section Reference](#section-reference)
4. [Provider-Specific LLM Examples](#provider-specific-llm-examples)
5. [Interactive Config Editing](#interactive-config-editing)

---

## Basics

### Config File Locations

By default, FUL reads `config.json` from the project root. If it does not exist, it falls back to `.ful/config.json`.

```text
<ProjectRoot>/config.json
# or
<ProjectRoot>/.ful/config.json
```

You can also pass a config path explicitly with `-c, --config <path>`.

### Creating a Config File

```bash
ful init
```

### Minimal Structure

```json
{
  "schema_version": 1,
  "project": { "id": "my-project" },
  "selection_rules": {
    "class_min_loc": 10,
    "class_min_method_count": 1,
    "method_min_loc": 1,
    "method_max_loc": 1000,
    "max_methods_per_class": 5,
    "exclude_getters_setters": false
  },
  "llm": {
    "provider": "gemini"
  },
  "pipeline": {
    "stages": ["analyze", "generate", "report"]
  }
}
```

---

## Top-Level Categories

| Category | Purpose | Required |
|---|---|---|
| `schema_version` | Config schema version (integer or SemVer string, must be >= 1) | No |
| `project` | Project metadata and root paths | Yes |
| `analysis` | Source-code analysis behavior | No |
| `selection_rules` | Rules for choosing test targets | Yes |
| `llm` | LLM provider and model settings | Yes |
| `pipeline` | Pipeline or workflow-stage control | No |
| `execution` | Runtime execution behavior | No |
| `verification` | Test execution and flaky detection | No |
| `context_awareness` | Learn style from existing tests | No |
| `generation` | Test-generation behavior | No |
| `brittle_test_rules` | Fragile-test detection settings | No |
| `mocking` | Mock and stub behavior | No |
| `output` | Output format settings | No |
| `log` | Logging settings | No |
| `governance` | Security and external-transmission policy | No |
| `audit` | Audit logging | No |
| `quota` | Usage limits | No |
| `local_fix` | Local automatic fix rules | No |
| `docs` | Documentation generation | No |
| `quality_gate` | Coverage and quality gate rules | No |
| `cache` | Analysis and generation cache settings | No |
| `cli` | CLI display behavior | No |
| `interceptors` | Per-phase interceptor wiring | No |

---

## Section Reference

### `project`

Project identity and path settings.

| Key | Type | Default | Description |
|---|---|---|---|
| `id` | string | `default` | Project identifier |
| `root` | string | current directory | Project root path |
| `docs_output` | string | unset | Output path for `ful document`; falls back to `.ful/docs` |
| `repo_url` | string | - | Repository URL |
| `commit` | string | - | Commit hash |
| `build_tool` | string | `gradle` | `gradle` or `maven` |
| `build_command` | string | - | Test execution command |
| `include_paths` | array of string | `[]` | Included analysis paths |
| `exclude_paths` | array of string | `[]` | Excluded paths |

### `analysis`

Controls analysis behavior.

| Key | Type | Default | Description |
|---|---|---|---|
| `engine` | string | unset | Analysis engine |
| `source_root_mode` | enum | `AUTO` | `AUTO`, `STRICT` |
| `source_root_paths` | array of string | `src/main/java`, `app/src/main/java` | Source roots |
| `source_charset` | string | `UTF-8` | Source encoding |
| `dump_file_list` | boolean | `false` | Emit a file list for analysis |
| `exclude_tests` | boolean | `true` | Exclude tests from analysis |
| `spoon.no_classpath` | boolean | `true` | Spoon noClasspath toggle |
| `classpath.mode` | enum | `AUTO` | `AUTO`, `STRICT`, `OFF` |
| `preprocess.mode` | enum | `OFF` | `OFF`, `AUTO`, `STRICT` |
| `preprocess.tool` | enum | `AUTO` | `AUTO`, `DELOMBOK`, `JAVAC_APT` |
| `preprocess.work_dir` | string | `.utg/preprocess` | Preprocess work directory |
| `preprocess.clean_work_dir` | boolean | `true` | Clean the work directory |
| `preprocess.include_generated` | boolean | `false` | Include generated sources |
| `preprocess.delombok.enabled` | boolean | `true` | Enable Delombok |
| `preprocess.delombok.lombok_jar_path` | string | unset | Explicit Lombok JAR path |
| `enable_interprocedural_resolution` | boolean | `false` | Enable interprocedural resolution |
| `interprocedural_callsite_limit` | integer | `20` | Interprocedural call limit |
| `external_config_resolution` | boolean | `false` | Resolve external config values |
| `debug_dynamic_resolution` | boolean | `false` | Dynamic-resolution debug logging |
| `experimental_candidate_enum` | boolean | `false` | Experimental candidate enumeration |

### `llm`

Provider and model settings.

| Key | Type | Default | Description |
|---|---|---|---|
| `provider` | enum | `gemini` | Provider ID. Supported values: `gemini`, `openai`, `openai-compatible` (alias: `openai_compatible`), `anthropic`, `azure-openai` (alias: `azure_openai`), `vertex` (aliases: `vertex-ai`, `vertex_ai`), `bedrock`, `local` (aliases: `ollama`, `vllm`), `mock` |
| `allowed_providers` | array of string | unset | Allow-list of providers |
| `allowed_models` | map | unset | Allow-list of models per provider |
| `model_name` | string | - | Model name |
| `api_key` | string | - | Prefer environment-variable injection |
| `url` | string | unset | Endpoint override |
| `azure_deployment`, `azure_api_version` | string | unset | Azure OpenAI settings |
| `vertex_project`, `vertex_location`, `vertex_publisher`, `vertex_model` | string | unset | Vertex AI settings |
| `aws_access_key_id`, `aws_secret_access_key`, `aws_session_token`, `aws_region` | string | unset | Bedrock settings |
| `max_retries` | integer | `3` | API retry count |
| `fix_retries` | integer | `2` | Post-generation repair retries |
| `connect_timeout` | integer | `30` | Connection timeout in seconds |
| `request_timeout` | integer | `300` | Request timeout in seconds |
| `max_response_length` | integer | `50000` | Max response length |
| `custom_headers` | map | `{}` | Extra HTTP headers |
| `fallback_stub_enabled` | boolean | `true` | Generate a fallback stub on failure |
| `javac_validation` | boolean | `false` | Lightweight `javac` validation |
| `retry_initial_delay_ms` | integer | `2000` | Initial retry delay |
| `retry_backoff_multiplier` | number | `2.0` | Retry backoff factor |
| `rate_limit_qps` | number | unset | Rate limit in QPS |
| `circuit_breaker_threshold` | integer | `5` | Circuit-breaker threshold |
| `circuit_breaker_reset_ms` | number | `30000` | Circuit-breaker reset time |
| `deterministic` | boolean | `true` | Reproducible generation mode |
| `seed` | integer | unset | Seed for deterministic mode |
| `temperature` | number | unset | Sampling temperature |
| `max_tokens` | integer | unset | Max generation tokens |
| `system_message` | string | unset | System-message override |
| `smart_retry.*` | object | see schema | Fine-grained retry controls |

### `selection_rules`

Rules and scoring for selecting test targets.

| Key group | Description |
|---|---|
| `max_targets`, `max_methods_per_class`, `max_methods_per_package` | Output caps |
| `strategy`, `selection_engine` | Main selection behavior |
| `class_min_loc`, `class_min_method_count`, `method_min_loc`, `method_max_loc` | Size thresholds |
| `exclude_getters_setters`, `exclude_dead_code` | Exclusion toggles |
| `exclude_annotations`, `deprioritize_annotations`, `priority_annotations` | Annotation-based selection rules |
| `scoring_weights.*` | Score weighting |
| `complexity.*` | Complexity thresholds and strategies |
| `removal_boost`, `deprioritize_factor`, `feasibility_penalties.*` | Scoring adjustments |
| `version_history.enabled` | Git-history-based selection |
| `enable_dynamic_selection`, `dynamic_selection_dry_run`, `min_dynamic_confidence`, `unresolved_penalty`, `skip_threshold` | Dynamic selection controls |
| `evaluation_*` | Evaluation thresholds for the selection process |

### `pipeline`

Controls pipeline stage selection and workflow loading.

| Key | Type | Default | Description |
|---|---|---|---|
| `stages` | array of string | compatibility default | Stage filter. Supported labels are `analyze`, `generate`, `report`, `document`, `explore`; `select` and `brittle_check` act as generate-target aliases |
| `workflow_file` | string | unset | Workflow definition JSON; relative paths resolve from project root |

### `execution`

Execution control.

| Key | Type | Default | Description |
|---|---|---|---|
| `per_task_isolation` | boolean | `false` | Isolate tasks from each other |
| `logs_root` | string | `.ful/runs` | Root path for run artifacts |
| `runtime_fix_retries` | integer | unset | Runtime-fix retry count |
| `flaky_reruns` | integer | `0` | Extra reruns for flaky detection |
| `unresolved_policy` | enum | `skip` | `skip` or `minimal` |
| `test_stability_policy` | enum | `standard` | `strict`, `standard`, `relaxed` |

### `verification`

Verification and test-execution settings.

| Key group | Description |
|---|---|
| `flaky_detection.enabled`, `rerun_count`, `strategy`, `min_passes_for_flaky`, `fail_on_flaky` | Flaky-test detection behavior |
| `test_execution.timeout_seconds`, `parallel`, `max_workers`, `fail_fast`, `continue_on_failure`, `jvm_args` | Test execution behavior |

### `context_awareness`

Learn style from existing test code.

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Enable style learning |
| `test_dirs` | array of string | `src/test/java` | Reference directories |
| `include_globs`, `exclude_globs` | array of string | schema defaults | Filters |
| `max_files` | integer | `200` | Max reference files |
| `max_injected_chars` | integer | `1200` | Prompt-injection cap |
| `exclude_generated_tests` | boolean | `true` | Ignore generated tests |
| `generated_output_dir` | string | unset | Used to identify generated tests |

### `generation`

Generation behavior.

| Key group | Description |
|---|---|
| `marker.*` | Generated-test marker behavior |
| `prompt_template_path` | Prompt template path |
| `few_shot.*` | Few-shot example settings |
| `temperature`, `max_tokens`, `default_model` | Generation defaults |

### Remaining Sections

| Section | Main purpose |
|---|---|
| `brittle_test_rules` | Detects reflection, sleeps, time dependence, and excessive mocks |
| `mocking` | Controls mock framework and stub strategies |
| `output` | Controls task and report formats |
| `log` | Controls logging format, destination, and MDC |
| `governance` | Controls external transmission and prompt redaction |
| `audit` | Controls audit-log output |
| `quota` | Caps tasks and LLM calls |
| `local_fix` | Enables local code-fix helpers |
| `docs` | Controls documentation generation |
| `quality_gate` | Coverage thresholds and report-based blocking |
| `cache` | Controls cache TTL, encryption, and revalidation |
| `cli` | Controls color and interactive features |
| `interceptors` | Adds pre/post interceptors per stage |

---

## Provider-Specific LLM Examples

### Gemini

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

## Interactive Config Editing

`ful tut` is non-visual, so configuration tasks are usually handled through commands such as `/config set`, `/config search`, and `/config validate`.

Typical workflow:

1. Search for the key you need.
2. Set or update the value.
3. Validate the config.
4. Save and rerun the relevant FUL command.
