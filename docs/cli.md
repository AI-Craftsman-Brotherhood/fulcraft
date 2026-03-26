# FUL CLI Guide

This page summarizes the CLI and TUT commands provided by FUL.
It intentionally excludes detailed JUnit generation internals.

## Command List

| Command | Description | Main output |
|---|---|---|
| `ful run` | Executes the whole pipeline or selected steps | `.ful/runs/<runId>/**` |
| `ful analyze` | Runs analysis only (`ANALYZE`) | `.ful/runs/<runId>/analysis/**` |
| `ful explore` | Produces exploration artifacts (`ANALYZE -> DOCUMENT -> REPORT -> EXPLORE`) | `.ful/runs/<runId>/explore/*` |
| `ful report` | Generates analysis reports | `.ful/runs/<runId>/report/*` |
| `ful document` (`doc`) | Generates source documentation | `.ful/runs/<runId>/docs/*` |
| `ful steps` | Prints available pipeline steps | stdout |
| `ful init` | Generates a config file | `config.json` or `.ful/config.json` |
| `ful init-ci` | Generates CI workflow templates | `.github/workflows/*.yml` |
| `ful tut` | Starts the non-visual interactive CLI | interactive prompt |
| `ful resume` | Reopens a previous TUI session | interactive UI |

## Shared Options

These options are common to pipeline-oriented commands such as `run`, `analyze`, `explore`, `report`, and `document`.

| Option | Description | Default |
|---|---|---|
| `-c, --config <path>` | Config file path | `config.json`, then `.ful/config.json` |
| `-p, --project-root <path>` | Project root; can also be passed positionally | `.` |
| `-h, --help` | Show help | - |
| `-V, --version` | Show version | - |
| `-v, --verbose` | Enable verbose output | - |
| `--dry-run` | Show the plan without writing or executing | - |
| `--color <on|off|auto>` | Color mode | `auto` |
| `--log-format <human|json|yaml>` | Log format | `human` |
| `--json` | Shortcut for `--log-format json` | - |
| `--version-history` | Use Git history in selection logic | `false` |
| `--unresolved-policy <skip|minimal>` | Policy for unresolved references | - |
| `--max-cyclomatic <int>` | Complexity threshold for strategy handling | - |
| `--complexity-strategy <warn|skip|split>` | Strategy for highly complex methods | - |
| `--process-isolation` | Run in process-isolated mode | `false` |
| `--tasks-format <json|jsonl>` | Task-file output format | - |
| `--cache-ttl <int>` | Cache TTL in days | - |
| `--cache-revalidate` | Revalidate cache before use | `false` |
| `--cache-encrypt` | Encrypt cache data | `false` |
| `--cache-key-env <name>` | Environment variable for cache encryption key | - |

## `run`

`ful run [OPTIONS]`

| Option | Description |
|---|---|
| `--steps <steps>` | Explicit comma-separated step list |
| `--from <step>` | Start from a specific step |
| `--to <step>` | Stop after a specific step |
| `--fail-fast` | Stop immediately on failure |
| `--summary` / `--no-summary` | Toggle end-of-run summary output |
| `--format <markdown|html|pdf|all>` | Document output format for DOCUMENT and EXPLORE |
| `--llm` | Enables `docs.use_llm` and propagates LLM usage into run stages |

Notes:

- `--steps` cannot be combined with `--from` or `--to`.
- If `pipeline.workflow_file` is not set, the default path is `ANALYZE -> DOCUMENT -> REPORT -> EXPLORE`.
- If `pipeline.workflow_file` is set, the default path is `GENERATE` in workflow-node mode.
- Explicit CLI step options always take precedence over defaults.

## `analyze`

`ful analyze [OPTIONS]`

| Option | Description |
|---|---|
| `--engine <composite|javaparser|spoon>` | Analysis engine |
| `-f, --files <paths>` | Comma-separated target files |
| `-d, --dirs <paths>` | Comma-separated target directories |
| `--exclude-tests[=<bool>]` | Exclude test code from analysis |
| `--debug-dynamic-resolution` | Enable debug output for dynamic resolution |

## `explore`

`ful explore [OPTIONS]`

| Option | Description |
|---|---|
| `--engine <composite|javaparser|spoon>` | Analysis engine |
| `-f, --files <paths>` | Comma-separated target files |
| `-d, --dirs <paths>` | Comma-separated target directories |
| `--exclude-tests[=<bool>]` | Exclude test code from analysis |
| `--debug-dynamic-resolution` | Enable debug output for dynamic resolution |
| `--format <markdown|html|pdf|all>` | Document format; `html` and `all` also affect report format |
| `--llm` | Enables LLM usage for document generation inside `explore` |

Notes:

- `ful explore` internally runs `ANALYZE -> DOCUMENT -> REPORT -> EXPLORE`.
- If `docs.format` is missing or set to `markdown`, FUL promotes it to `html` at runtime to favor exploration output.
- When `--format` is omitted, report format follows that promotion unless an explicit report format is already configured.

## `report`

`ful report [OPTIONS]`

| Option | Description | Default |
|---|---|---|
| `--run-id <id>` | Target run ID; latest if omitted | latest |
| `--engine <composite|javaparser|spoon>` | Analysis engine | `composite` |
| `--exclude-tests` | Exclude test code from analysis and reporting | `config.analysis.exclude_tests` |
| `--format <markdown|html|json|yaml>` | Output format | - |
| `-o, --output <path>` | Output path | - |
| `-f, --files <paths>` | Target files | - |
| `-d, --dirs <paths>` | Target directories | - |

## `document` / `doc`

`ful document [OPTIONS]`

| Option | Description | Default |
|---|---|---|
| `-o, --output <path>` | Output directory | - |
| `-f, --files <paths>` | Target files | - |
| `-d, --dirs <paths>` | Target directories | - |
| `--format <markdown|html|pdf|all>` | Output format | - |
| `--single-file` | Merge all classes into one file | `false` |
| `--diagram` | Generate dependency diagrams | `false` |
| `--diagram-format <mermaid|plantuml>` | Diagram format | `mermaid` |
| `--include-tests` | Include links to test code | `false` |
| `--llm` | Enable LLM-based enrichment | - |

## `init` and `init-ci`

### `init`

`ful init [OPTIONS]`

| Option | Description |
|---|---|
| `-d, --directory <path>` | Directory where the config file will be generated |
| `-f, --force` | Overwrite existing configuration |

### `init-ci`

`ful init-ci [OPTIONS]`

| Option | Description | Default |
|---|---|---|
| `--github-actions` | Use the GitHub Actions template | - |
| `-o, --output <path>` | Output file path | - |
| `-f, --force` | Overwrite existing file | `false` |
| `--dry-run` | Print content without writing files | `false` |
| `--comment` / `--no-comment` | Include PR comment settings | `true` |
| `--quality-gate` / `--no-quality-gate` | Include quality-gate jobs | `true` |
| `--coverage-threshold <int>` | Coverage threshold in percent | - |
| `--coverage-tool <name>` | Coverage tool | `jacoco` |
| `--static-analysis <tools>` | Comma-separated static analysis tools | - |

Notes:

- `ful quality-gate` has been removed.
- `--quality-gate` only controls whether the generated CI workflow contains quality-check jobs.

## Interactive Modes

### `tut`

| Option | Description |
|---|---|
| `-p, --project-root <path>` | Project root |

Notes:

- `tut` is a non-visual REPL-style mode.
- Use `resume` when you need to reopen an existing visual TUI session.

### `resume`

| Option | Description |
|---|---|
| `-l, --list` | List sessions only |
| `-i, --id <id>` | Reopen a specific session ID |
| `-p, --project-root <path>` | Project root |

## Environment Variables

| Variable | Description |
|---|---|
| `FUL_COLOR` | Color mode: `on`, `off`, `auto` |
| `FUL_LOG_FORMAT` | Log format: `human`, `json`, `yaml` |
| `NO_COLOR` | Disables color output |
| `FUL_LANG` | Language selection, for example `ja` or `en` |

## Common Examples

```bash
# Analysis only
./scripts/ful analyze -d src/main/java/com/example/core/

# Generate an analysis report
./scripts/ful report --run-id latest --format html

# Generate documentation
./scripts/ful document -d src/main/java/com/example/core/ --format markdown

# Preview a CI workflow template without writing files
./scripts/ful init-ci --github-actions --dry-run
```

## Related Documents

- [Logging](logging.md)
- [Configuration Reference](config.md)
- [Plugin Architecture](plugin-architecture.md)
