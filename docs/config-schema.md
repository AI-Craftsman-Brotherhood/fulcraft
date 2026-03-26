# Config Schema Guide

This document summarizes the JSON Schema used for `config.json` and the versioning rules around it.
The schema defines types, required fields, and allowed values so configuration drift is caught early.

---

## Current Schema Files

| Schema version | File | Notes |
|---|---|---|
| 1.0.0 | `app/src/main/resources/schema/config-schema-v1.json` | Current and recommended |
| legacy | `app/src/main/resources/config.schema.json` | Compatibility only; do not add new fields here |

---

## Top-Level Keys

`schema_version` can be given as `1` or `1.0.0`. `schemaVersion` is also accepted for compatibility.

| Key | Type | Required | Description |
|---|---|---|---|
| `schema_version`, `schemaVersion` | string or integer | No | Schema version |
| `project` | object | Yes | Project information |
| `pipeline` | object | No | Pipeline stage control |
| `analysis` | object | No | Analysis settings |
| `selection_rules` | object | Yes | Selection rules |
| `llm` | object | Yes | LLM settings |
| `execution` | object | No | Execution control |
| `output` | object | No | Output formatting |
| `cli` | object | No | CLI display settings |
| `audit` | object | No | Audit logging |
| `quota` | object | No | Usage limits |
| `context_awareness` | object | No | Learning from existing tests |
| `generation` | object | No | Generation behavior |
| `governance` | object | No | Governance and security |
| `mocking` | object | No | Mock generation |
| `local_fix` | object | No | Local auto-fix rules |
| `log` | object | No | Logging |
| `docs` | object | No | Documentation generation |
| `quality_gate` | object | No | Quality gate settings |
| `cache` | object | No | Cache settings |
| `interceptors` | object | No | Pipeline interceptors |
| `verification` | object | No | Verification-stage settings |
| `brittle_test_rules` | object | No | Fragile-test detection rules |

> The schema uses `additionalProperties: false`, so unknown keys are rejected.

---

## Key Groups

### `project`

| Key | Type | Description |
|---|---|---|
| `id` | string | Project identifier; required |
| `root` | string | Project root path |
| `docs_output` | string | Output directory for `ful document` |
| `repo_url` | string | Repository URL |
| `commit` | string | Commit hash |
| `build_tool` | string | Build tool name |
| `build_command` | string | Test execution command |
| `exclude_paths` | array of string | Paths excluded from analysis |
| `include_paths` | array of string | Paths explicitly included |

### `pipeline`

| Key | Type | Description |
|---|---|---|
| `stages` | array of string | Workflow-stage filter. Valid labels include `analyze`, `generate`, `report`, `document`, and `explore`. `select` and `brittle_check` map into `generate` targets |
| `workflow_file` | string | Path to workflow definition JSON; relative paths are resolved from project root |

### `analysis`

| Key | Type | Description |
|---|---|---|
| `engine` | string | Analysis engine |
| `source_root_mode` | enum | `AUTO`, `STRICT` |
| `source_root_paths` | array of string | Source-root paths |
| `source_charset` | string | Source encoding such as `UTF-8` |
| `dump_file_list` | boolean | Emit analyzed file list |
| `classpath.mode` | enum | `AUTO`, `STRICT`, `OFF` |
| `spoon.no_classpath` | boolean | Spoon noClasspath switch |
| `preprocess.mode` | enum | `OFF`, `AUTO`, `STRICT` |
| `preprocess.tool` | enum | `AUTO`, `DELOMBOK`, `JAVAC_APT` |
| `preprocess.work_dir` | string | Preprocess working directory |
| `preprocess.clean_work_dir` | boolean | Clean preprocess work directory |
| `preprocess.include_generated` | boolean | Include generated sources in analysis |
| `preprocess.delombok.enabled` | boolean | Enable Delombok |
| `preprocess.delombok.lombok_jar_path` | string | Explicit Lombok JAR path |
| `enable_interprocedural_resolution` | boolean | Interprocedural analysis |
| `interprocedural_callsite_limit` | integer | Interprocedural call-site limit |
| `external_config_resolution` | boolean | Resolve external config values |
| `debug_dynamic_resolution` | boolean | Debug logging for dynamic resolution |
| `experimental_candidate_enum` | boolean | Experimental candidate enumeration |
| `exclude_tests` | boolean | Exclude test sources from analysis |

### `selection_rules`

| Key | Type | Description |
|---|---|---|
| `max_targets` | integer | Max selected targets |
| `strategy` | string | Selection strategy |
| `selection_engine` | string | Selection engine |
| `class_min_loc`, `class_min_method_count` | integer | Class-level minimum thresholds |
| `method_min_loc`, `method_max_loc` | integer | Method-level LOC thresholds |
| `max_methods_per_class`, `max_methods_per_package` | integer or null | Output caps |
| `exclude_getters_setters`, `exclude_dead_code` | boolean | Exclusion toggles |
| `exclude_annotations`, `deprioritize_annotations`, `priority_annotations` | array of string | Annotation-based controls |
| `complexity.max_cyclomatic` | integer | Complexity threshold |
| `complexity.strategy` | enum | `skip`, `warn`, `split`, `specialized_prompt` |
| `complexity.expected_tests_per_complexity`, `complexity.max_expected_tests` | number / integer | Complexity-based test planning |
| `removal_boost`, `deprioritize_factor` | number | Scoring adjustments |
| `feasibility_penalties.*`, `scoring_weights.*` | number | Fine-grained scoring controls |
| `version_history.enabled` | boolean | Git-history-based selection |
| `enable_dynamic_selection`, `min_dynamic_confidence`, `unresolved_penalty` | number / boolean | Dynamic selection controls |

### `llm`

| Key | Type | Description |
|---|---|---|
| `provider` | enum | Supported provider ID |
| `allowed_providers` | array of string | Provider allow-list |
| `allowed_models` | map | Per-provider model allow-list |
| `model_name`, `api_key`, `url` | string | Main provider settings |
| `azure_deployment`, `azure_api_version` | string | Azure OpenAI settings |
| `vertex_project`, `vertex_location`, `vertex_publisher`, `vertex_model` | string | Vertex AI settings |
| `aws_access_key_id`, `aws_secret_access_key`, `aws_session_token`, `aws_region` | string | Bedrock settings |
| `max_retries`, `fix_retries` | integer | Retry counts |
| `connect_timeout`, `request_timeout` | integer | Timeouts in seconds |
| `max_response_length` | integer | Maximum response size |
| `custom_headers` | map | Extra HTTP headers |
| `fallback_stub_enabled`, `javac_validation` | boolean | Failure and validation behavior |
| `retry_initial_delay_ms`, `retry_backoff_multiplier` | integer / number | Retry timing |
| `rate_limit_qps` | number | Request rate limit |
| `circuit_breaker_threshold`, `circuit_breaker_reset_ms` | integer / number | Circuit-breaker controls |
| `deterministic`, `seed`, `temperature`, `max_tokens`, `system_message` | boolean / integer / number / string | Generation controls |
| `smart_retry.*` | object | Smart retry sub-configuration |

### `execution`

| Key | Type | Description |
|---|---|---|
| `per_task_isolation` | boolean | Run tasks in isolation |
| `logs_root` | string | Root directory for run artifacts and logs |
| `runtime_fix_retries` | integer | Runtime-fix retry count |
| `flaky_reruns` | integer | Reruns used for flaky detection |
| `unresolved_policy` | enum | `skip`, `minimal` |
| `test_stability_policy` | enum | `strict`, `standard`, `relaxed` |

### `verification`

| Key | Type | Description |
|---|---|---|
| `flaky_detection.*` | object | Flaky-test detection settings |
| `test_execution.timeout_seconds` | integer | Test timeout |
| `test_execution.parallel` | boolean | Parallel test execution |
| `test_execution.max_workers` | integer | Max worker count |
| `test_execution.fail_fast`, `test_execution.continue_on_failure` | boolean | Failure-handling policy |
| `test_execution.jvm_args` | array of string | JVM arguments |

### `brittle_test_rules`

| Key | Type | Description |
|---|---|---|
| `enabled` | boolean | Enables brittle-test detection |
| `fail_on_reflection`, `fail_on_sleep` | boolean | Hard-fail rules |
| `warn_on_time`, `warn_on_random` | boolean | Warning rules |
| `max_mocks_warn`, `max_mocks_fail` | integer | Mock-count thresholds |
| `allowlist_patterns` | array of string | Allowed patterns |
| `count_static_mocks`, `count_stubs` | boolean | Counting rules |

### `context_awareness`

| Key | Type | Description |
|---|---|---|
| `enabled` | boolean | Enables style learning from existing tests |
| `test_dirs` | array of string | Source directories for reference tests |
| `include_globs`, `exclude_globs` | array of string | File filters |
| `max_files`, `max_injected_chars` | integer | Input limits |
| `exclude_generated_tests` | boolean | Skip generated tests |
| `generated_output_dir` | string | Output directory used to detect generated tests |

### `generation`

| Key | Type | Description |
|---|---|---|
| `marker.enabled`, `marker.tag`, `marker.scan_first_lines` | boolean / string / integer | Generated-test marker settings |
| `prompt_template_path` | string | Prompt template path |
| `few_shot.enabled`, `few_shot.examples_dir`, `few_shot.max_examples`, `few_shot.use_class_type_detection` | mixed | Few-shot settings |
| `temperature`, `max_tokens`, `default_model` | mixed | Generation defaults |

### Remaining Sections

| Section | Typical keys |
|---|---|
| `mocking` | `enable_static`, `enable_external`, `http_stub`, `db_stub`, `framework` |
| `governance` | `external_transmission`, `redaction.*` |
| `audit` | `enabled`, `log_path`, `include_raw` |
| `quota` | `max_tasks`, `max_llm_calls`, `on_exceed` |
| `local_fix` | `enable_generics`, `enable_builder_fix`, `enable_record_accessor`, and related toggles |
| `output` | `format.tasks`, `format.report` |
| `log` | `format`, `level`, `output`, `color`, `file_path`, MDC toggles |
| `docs` | `format`, `diagram`, `include_tests`, `use_llm`, `diagram_format`, `single_file` |
| `quality_gate` | coverage thresholds, blocking rules, pass-rate checks, report paths |
| `cache` | `enabled`, `ttl_days`, `revalidate`, `encrypt`, `max_size_mb` |
| `cli` | `color`, `interactive.enabled`, `autocomplete.enabled` |
| `interceptors` | per-stage `pre` and `post` interceptor lists |

---

## Schema Versioning Rules

- `schema_version` can be an integer or a semantic version string.
- FUL loads the file matching the major version, such as `config-schema-v1.json`.
- Older schemas are kept for compatibility and should not be deleted when they are still needed for existing configs.

### Schema Update Procedure

1. Update the JSON Schema file under `app/src/main/resources/schema/`.
2. Keep `Config.java` and validators aligned with the schema.
3. Update documentation such as this page.
4. Reflect the change in `config.example.json`.
