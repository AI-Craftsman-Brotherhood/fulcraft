# Unified Logging

FUL uses a shared logging foundation across the CLI, TUI, and pipeline.
This document reflects the current implementation centered on `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/logging/Logger.java`.

## 1. Architecture

- The UI layer uses `UiLogger` as the main entry point.
  - `app/src/main/java/com/craftsmanbro/fulcraft/ui/cli/UiLogger.java`
  - `app/src/main/java/com/craftsmanbro/fulcraft/ui/tui/UiLogger.java`
- The kernel layer uses `LoggerPort`, wired through `KernelLoggerFactoryAdapter` during startup.
  - `app/src/main/java/com/craftsmanbro/fulcraft/logging/LoggerPortProvider.java`
  - `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/logging/KernelLoggerFactoryAdapter.java`
- The runtime backend is SLF4J plus Logback, with `Logger` handling configuration, MDC, and masking.
- Available layouts:
  - `MaskedPatternLayout` for human-readable logs
  - `JsonLayout` for JSON
  - `YamlLayout` for YAML
- Sensitive data is masked by `SecretMasker`.

## 2. Configuration

```json
{
  "log": {
    "format": "human",
    "level": "info",
    "output": "console",
    "color": "auto",
    "file_path": "ful.log",
    "include_timestamp": false,
    "include_thread": false,
    "include_logger": false,
    "max_message_length": 0,
    "enable_mdc": true
  }
}
```

| Key | Values | Description |
|---|---|---|
| `log.format` | `human`, `json`, `yaml` | Log format |
| `log.level` | `debug`, `info`, `warn`, `error` | Log level |
| `log.output` | `console`, `file`, `both` | Output destination |
| `log.color` | `auto`, `on`, `off` | Color control, used as a fallback when `cli.color` is not set |
| `log.file_path` | string | Output file name, later relocated under the run directory |
| `log.include_timestamp` | bool | Adds timestamps in human console mode |
| `log.include_thread` | bool | Adds thread names in human mode |
| `log.include_logger` | bool | Adds logger names in human mode |
| `log.max_message_length` | integer | Cap used by `trimLargeContent`; `0` means unlimited |
| `log.enable_mdc` | bool | Enables MDC |

## 3. Overrides

CLI options:

- `--log-format <human|json|yaml>`
- `--json`
- `--color <on|off|auto>`

Environment variables:

- `FUL_LOG_FORMAT` overrides `log.format`
- `FUL_COLOR` overrides `cli.color`
- `NO_COLOR` forces `cli.color=off` when `FUL_COLOR` is not set

Notes:

- `-v, --verbose` controls stack trace verbosity and does not change `log.level`.
- The current application order is: configuration plus CLI overrides, then environment variable overrides.

## 4. Output Paths and Run Directories

Logs are written per run under `execution.logs_root` (default: `.ful/runs`).

- Run root: `<execution.logs_root>/<runId>/`
- Log directory: `<execution.logs_root>/<runId>/logs/`
- App log: `<execution.logs_root>/<runId>/logs/<file_name>`
- LLM log: `<execution.logs_root>/<runId>/logs/llm.log`

Only the file name part of `log.file_path` is used in the final location.
For example, `logs/custom.log` still becomes `<runId>/logs/custom.log`.

## 5. Output Modes

- `console`: INFO and below go to stdout, WARN and ERROR go to stderr
- `file`: disables console appenders and writes only to file
- `both`: writes to both console and file

## 6. Formats

### Human

```text
[INFO] analysis started
[WARN] unresolved symbol: ...
```

### JSON (NDJSON)

```json
{"timestamp":"2026-02-11T06:30:00Z","level":"INFO","thread":"main","logger":"utgenerator","message":"analysis started","runId":"20260211-063000-abc123","traceId":"1a2b3c4d"}
```

### YAML (stream)

```yaml
---
timestamp: "2026-02-11T06:30:00Z"
level: "INFO"
message: "analysis started"
```

## 7. MDC Keys

When `enable_mdc=true`, these keys may be used:

| Key | Description |
|---|---|
| `runId` | Run ID |
| `traceId` | Trace ID |
| `subsystem` | Subsystem name |
| `stage` | Stage name |
| `targetClass` | Target class |
| `taskId` | Task ID |

Notes:

- `runId` is set during `configureRunLogging(...)`.
- `traceId` is created during initialization.
- `subsystem`, `stage`, `targetClass`, and `taskId` are set only when needed.

## 8. Secret Masking

At log-write time, `SecretMasker` replaces the following with `****`:

- PEM private keys
- Authorization header values
- Keyed values such as `api_key`, `token`, and `password`
- JWT-like tokens
- Long random-looking tokens such as Hex or Base64 material

## 9. Environment Diagnostics

At startup, `Main` calls `EnvironmentLogger.logStartupEnvironment()`.
These entries are logged at DEBUG level with an `[Environment]` prefix.

It records:

- Java version
- Gradle version
- Toolchain version
- Project root and source
- Current working directory

## 10. Recommended Usage

1. Use `UiLogger` from the UI layer.
2. Use `LoggerPortProvider.getLogger(...)` from the kernel layer.
3. Use `log.format: json` in CI and aggregation workflows.
4. Do not put secrets directly into log messages.
