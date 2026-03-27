# FUL Quickstart Guide

This guide walks through FUL setup and a first analysis run.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Initial Setup](#initial-setup)
3. [Interactive Setup with `ful init`](#interactive-setup-with-ful-init)
4. [Running FUL](#running-ful)
5. [Interactive Mode](#interactive-mode)
6. [Troubleshooting](#troubleshooting)
7. [Cache Management](#cache-management)

---

## Prerequisites

### JDK 21

FUL requires **JDK 21 or later**.

```bash
java -version
```

Expected output:

```text
openjdk version "21.0.x" ...
```

Example installation with SDKMAN:

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.5-tem
sdk use java 21.0.5-tem
```

Also verify that `JAVA_HOME` points to the selected JDK.

### Gradle 8.x

FUL builds with **Gradle 8.x**, but you normally do not need to install it separately because the repository includes the Gradle Wrapper (`gradlew`).

---

## Initial Setup

### 1. Clone the Repository

```bash
git clone https://github.com/AI-Craftsman-Brotherhood/fulcraft.git
cd fulcraft
```

### 2. Build FUL

```bash
./gradlew :app:shadowJar
```

Check the generated JAR:

```bash
ls -la app/build/libs/ful-*.jar
```

### 3. Check the Wrapper Script

```bash
./scripts/ful --version
```

It is convenient to set `FUL_HOME` for later commands:

```bash
export FUL_HOME="/path/to/fulcraft"
```

### 4. Configure API Keys

Set provider API keys through environment variables when using hosted LLMs.

```bash
export GEMINI_API_KEY="your-gemini-api-key"
export OPENAI_API_KEY="your-openai-api-key"
export ANTHROPIC_API_KEY="your-anthropic-api-key"
```

Notes:

- Local LLM usage does not require an API key.
- Azure OpenAI, Vertex AI, and Bedrock require provider-specific configuration in `config.json`.
- See [LLM Provider Configuration](llm-configuration.md) for details.

---

## Using FUL Against a Target Project

### Gradle Project

```bash
# Build FUL
cd fulcraft
./gradlew :app:shadowJar

# Run against a target Gradle project
java -jar /path/to/fulcraft/app/build/libs/ful-*.jar run --project-root /path/to/your-gradle-project

# Or move into the target project first
cd /path/to/your-gradle-project
/path/to/fulcraft/scripts/ful init
/path/to/fulcraft/scripts/ful run
```

FUL uses the target project's `gradlew` to resolve the classpath automatically.

### Maven Project

```bash
cd fulcraft
./gradlew :app:shadowJar
java -jar /path/to/fulcraft/app/build/libs/ful-*.jar run --project-root /path/to/your-maven-project
```

FUL uses the target project's `mvnw` or `mvn` to resolve the classpath automatically.

---

## Interactive Setup With `ful init`

Run `ful init` in the Java project you want to analyze.

```bash
cd /path/to/your-java-project
$FUL_HOME/scripts/ful init
```

If `FUL_HOME` is not set, replace it with the full path to `/path/to/fulcraft/scripts/ful`.

Follow the wizard to choose the LLM provider and model. FUL generates `.ful/config.json`.

The wizard supports `gemini`, `openai`, `anthropic`, and `local` directly. For other providers, edit `.ful/config.json` manually.

---

## Running FUL

The following commands assume you run them from the target project root. If not, pass `-p, --project-root`.

### 1. Default `ful run`

The default pipeline runs stages defined in `default-workflow.json` as a DAG:

```text
ANALYZE ─┬─→ REPORT
         └─→ DOCUMENT ──→ EXPLORE
```

`REPORT` and `DOCUMENT` both depend on `ANALYZE` and may run in parallel.

```bash
$FUL_HOME/scripts/ful run

# Analyze a specific directory
$FUL_HOME/scripts/ful run -d src/main/java/com/example/core/

# Dry-run without writing files
$FUL_HOME/scripts/ful run --dry-run
```

### 2. Advanced Pipeline Control

```bash
# Analysis only
$FUL_HOME/scripts/ful run --steps ANALYZE

# Analysis and reporting
$FUL_HOME/scripts/ful run --steps ANALYZE,REPORT

# Analysis through documentation
$FUL_HOME/scripts/ful run --to DOCUMENT
```

Use `ful steps` to inspect the available step list.

You can also define default active stages in `config.json`:

```json
{
  "pipeline": {
    "stages": ["analyze", "document", "report"]
  }
}
```

### Checking Results

```bash
RUN_ID=$(ls -1t .ful/runs | head -1)
cat ".ful/runs/${RUN_ID}/report/report.md"
```

Artifacts are stored under `.ful/runs/<runId>/actions/<pluginId>/<nodeId>/`. Common locations:

- Analysis results: `.ful/runs/<runId>/actions/analyze-builtin/analyze/`
- Documentation: `.ful/runs/<runId>/actions/document-builtin/document/`
- Reports: `.ful/runs/<runId>/actions/report-builtin/report/`
- Exploration: `.ful/runs/<runId>/actions/explore-builtin/explore/`
- Logs: `.ful/runs/<runId>/logs/`

If you want JSON reporting, set `output.format.report: "json"` and inspect the report artifact directory.

---

## Interactive Mode

Use `tut` for the non-visual interactive mode.

```bash
$FUL_HOME/scripts/ful tut
```

- `tut`: line-based interactive CLI in a Codex-style workflow

---

## Troubleshooting

Common issues:

- `UnsupportedClassVersionError`: you are probably running on a JDK below 21
- Missing API key: the required environment variable is not set
- `ful.jar not found`: run `./gradlew :app:shadowJar` and confirm that `app/build/libs/ful-*.jar` exists

For more, see the [Troubleshooting Guide](troubleshooting.md).

---

## Cache Management

FUL caches generated results under `.ful/cache` to reduce repeat execution costs.

```bash
rm -rf .ful/cache
```

---

## Next Steps

- [Configuration Guide](config.md)
- [Security and Governance](governance.md)
