# Troubleshooting

This page summarizes common runtime problems in FUL based on the current CLI implementation.

## First Checks

```bash
java -version
./scripts/ful --help
./scripts/ful steps
```

- Confirm that `java -version` is 21 or later.
- Confirm that `./scripts/ful --help` works. If `ful.jar` has not been built yet, it usually fails here.
- Confirm available steps with `./scripts/ful steps`.

## Java / JDK Errors

| Symptom | Typical message | Resolution |
|---|---|---|
| Java not found | `Error: Java is not installed or not in PATH` | Install Java 21+ and set `PATH` and `JAVA_HOME` |
| Version mismatch | `UnsupportedClassVersionError` | Upgrade the runtime JDK to 21+ |
| Wrapper warning | `Warning: Java 21 or later is recommended` | Execution continues, but upgrading to Java 21+ is recommended |
| Gradle JDK detection warning | `Path for java installation '/usr/lib/jvm/openjdk-21' ... does not contain a java executable` | Point `JAVA_HOME` to a real JDK 21 path and enable `org.gradle.java.installations.fromEnv=JAVA_HOME` |

Recommended local Gradle settings:

```properties
org.gradle.java.installations.auto-detect=false
org.gradle.java.installations.fromEnv=JAVA_HOME
org.gradle.java.installations.auto-download=true
```

## LLM API Key Errors

Required fields vary by `llm.provider`.

| Provider | Typical error | Required fields |
|---|---|---|
| `gemini` | `'llm.api_key' or GEMINI_API_KEY env var is required for gemini provider` | `llm.api_key` or `GEMINI_API_KEY` |
| `openai` / `openai-compatible` | `'llm.api_key' or OPENAI_API_KEY ...` / `'llm.model_name' is required ...` | `api_key` and `model_name` |
| `anthropic` | `'llm.model_name' is required ...` / `'llm.api_key' or ANTHROPIC_API_KEY ...` | `api_key` and `model_name` |
| `azure-openai` | `'llm.azure_deployment' is required ...` | `url`, `azure_deployment`, `azure_api_version`, `api_key` |
| `vertex` | `'llm.vertex_project' is required ...` | `vertex_project`, `vertex_location`, `vertex_model`, `api_key` as OAuth token |
| `bedrock` | `'llm.aws_access_key_id' or AWS_ACCESS_KEY_ID ...` | `model_name`, AWS credentials, `aws_region` |
| `local` / `ollama` | `'llm.url' is required for local/ollama provider` | `url`, `model_name` |

## Configuration File Errors

### Loading Order

1. Path passed with `-c/--config`
2. `config.json`
3. `.ful/config.json`

### Common Errors

| Symptom | Typical message | Resolution |
|---|---|---|
| Explicit config file missing | `Configuration file not found: ...` | Fix the `-c` path |
| JSON Schema mismatch | `Config schema validation failed for: ...` | Check against `docs/config-schema.md` |
| Unknown keys | `Unknown keys in '<section>': ...` | Match key names with `config.example.json` |
| Wrong section type | `'<section>' section must be an object/mapping.` | Convert the section to an object |
| Invalid provider value | `'llm.provider' must be one of: ...` | Use a supported provider name |

## Project Path / Analysis Errors

| Symptom | Typical message | Resolution |
|---|---|---|
| Wrong `-p` path | `Project root must be an existing directory: ...` | Point `-p` at a real directory |
| Analysis root missing | `Source directory not found in project: ...` | Verify paths like `src/main/java` or set `analysis.source_root_paths` |
| Build tool detection failed | `Gradle wrapper not found` / `Maven wrapper or mvn not found` | Make `gradlew`, `mvnw`, or `mvn` available in the target project |

## Report / Run ID Errors

| Symptom | Typical message | Resolution |
|---|---|---|
| Run directory missing | `Runs directory not found: ...` | Run `ful run` first or pass a tasks file with `--input` |
| Specified run missing | `Run directory not found: ...` | Recheck `--run-id` |
| Tasks file missing | `Tasks file not found in: ...` | Check `.ful/runs/<runId>/plan/` |
| Wrong run for analysis-only report | `analysis_report.error.run_dir_not_found` / `analysis_dir_not_found` | Verify the run ID used with `ful report --run-id <id>` |

Example for finding the latest run:

```bash
ls -1t .ful/runs | head -1
```

## Interactive Mode

| Symptom | Typical message | Resolution |
|---|---|---|
| Want an interactive CLI | - | Use `ful tut` |
| Want to return to a visual session | - | Use `ful resume` to reopen an existing TUI session |

## Cache and Re-runs

```bash
# Remove only the cache
rm -rf .ful/cache

# Remove run history too, only when needed
rm -rf .ful/runs
```

A slow first run is often expected because dependency resolution is still warming up.

## Additional Links

- [Quickstart](quickstart.md)
- [CLI Guide](cli.md)
- [Configuration Reference](config.md)
- [Config Schema](config-schema.md)
- [Quality Gates](../QUALITY_GATES.md)
