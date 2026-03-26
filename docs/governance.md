# Governance

FUL governance features enforce policy controls around safe LLM usage.
This document covers only behavior that is active in the current implementation.

## Scope

- External LLM transmission control (`governance.external_transmission`)
- Prompt redaction (`governance.redaction`)
- Vulnerability response flow for OSS maintainers

## 1. External LLM Transmission Control

### Overview

If you set `governance.external_transmission: deny`, FUL blocks requests to external LLM providers.
Enforcement is handled by `LlmGovernanceEnforcingClient`.

- Implementation: `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/llm/decorator/LlmGovernanceEnforcingClient.java`
- Provider classification: `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/llm/LlmProviderRegistry.java`

### Behavior When Set to `deny`

| Provider | Behavior |
|---|---|
| `openai`, `openai-compatible`, `openai_compatible` | Blocked |
| `gemini` | Blocked |
| `anthropic` | Blocked |
| `azure-openai`, `azure_openai` | Blocked |
| `vertex`, `vertex-ai`, `vertex_ai` | Blocked |
| `bedrock` | Blocked |
| `local`, `ollama`, `vllm` | Allowed |
| `mock` | Allowed |
| Unknown provider names | Blocked and treated as external |

### Example Error Message

```text
governance.external_transmission=deny prohibits external LLM transmission.
Provider: gemini. Set governance.external_transmission to 'allow' to permit external transmission.
```

### Example Configuration

```json
{
  "governance": {
    "external_transmission": "deny"
  },
  "llm": {
    "provider": "local",
    "url": "http://localhost:11434/v1",
    "model_name": "codellama:7b"
  }
}
```

## 2. Prompt Redaction

### Overview

`PromptRedactionService` detects, masks, or blocks sensitive content before prompt data is sent to an LLM.
The detector chain has three families: `regex`, `dictionary`, and `ml`.

- Implementation: `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/llm/safety/redaction/PromptRedactionService.java`
- Detectors:
  - `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/llm/safety/redaction/detector/RegexDetector.java`
  - `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/llm/safety/redaction/detector/DictionaryDetector.java`
  - `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/llm/safety/redaction/detector/MlNerDetector.java`

### Modes

| Mode | Behavior |
|---|---|
| `off` | Disables the detector chain and uses the legacy redactor for compatibility |
| `report` | Detects only; usually does not modify the prompt body |
| `enforce` | Masks or blocks based on configured thresholds |

### Thresholds

- Detections at or above `mask_threshold` become mask candidates.
- Detections at or above `block_threshold` raise `RedactionException` and stop transmission.
- Default values:
  - `mask_threshold`: `0.60`
  - `block_threshold`: `0.90`

### When Detectors Are Effective

- `regex`: always available
- `dictionary`: effectively active only when `denylist_path` is configured
- `ml`: effectively active only when `ml_endpoint_url` is configured

### Example Configuration

```json
{
  "governance": {
    "redaction": {
      "mode": "enforce",
      "detectors": ["regex", "dictionary", "ml"],
      "denylist_path": ".ful/redaction/denylist.txt",
      "allowlist_path": ".ful/redaction/allowlist.txt",
      "mask_threshold": 0.60,
      "block_threshold": 0.90,
      "ml_endpoint_url": "http://localhost:8080/ner"
    }
  }
}
```

### Example Dictionary Files

`denylist.txt`:

```text
# comments
internal-only
confidential
project-alpha
internal-api-key
```

`allowlist.txt`:

```text
# false-positive suppression
craftsmann-bro.com
support@craftsmann-bro.com
localhost
```

### Example Blocked Exception

```text
Sensitive data blocked by redaction policy.
Detector: regex, Type: PEM_KEY, Confidence: 1.00 (threshold: 0.90).
Found 1 high-confidence items.
```

### Expected ML NER Format

Request:

```json
{"text":"Hello, John Doe from Acme Corp."}
```

Response:

```json
{
  "entities": [
    {"text": "John Doe", "label": "PERSON", "start": 7, "end": 15, "score": 0.95},
    {"text": "Acme Corp", "label": "ORG", "start": 21, "end": 30, "score": 0.88}
  ]
}
```

## 3. Runtime Enforcement Points

External transmission control and redaction are mainly applied in LLM execution paths.

- Transmission control decorator:
  - `DefaultServiceFactory` applies `LlmGovernanceEnforcingClient`
  - Example: `app/src/main/java/com/craftsmanbro/fulcraft/ui/cli/wiring/DefaultServiceFactory.java`
- Redaction:
  - `PromptRedactionService` is applied inside LLM call flows
  - Examples:
    - `app/src/main/java/com/craftsmanbro/fulcraft/feature/document/flow/DocumentFlow.java`
    - `app/src/main/java/com/craftsmanbro/fulcraft/feature/document/adapter/LlmDocumentGenerator.java`

## 4. OSS Maintainer Vulnerability Response Flow

See [security-scanning.md](security-scanning.md) for the broader operating model.
This section defines how to respond when a vulnerability is reported externally.

### 4.1 End-to-End Flow

1. Intake
2. Triage
3. Reproduction
4. Impact assessment
5. Remediation
6. Release
7. Advisory publication

### 4.2 Severity Guide

- Baseline: CVSS v3.1
- Rough categories:
  - Critical: RCE, authentication bypass, or large-scale leakage
  - High: privilege escalation, tenant escape, or reliable DoS
  - Medium: limited information exposure or high attack difficulty
  - Low: narrow impact

### 4.3 Non-Disclosure Before Fix

- Do not put details or PoCs in public issues or PRs.
- Do not discuss exploit details on public channels.
- Do not publish logs or artifacts that contain sensitive data.

### 4.4 Branch / Advisory Handling

- Option A: fix in a private branch and merge right before disclosure
- Option B: use a GitHub Security Advisory draft and a private fork

### 4.5 Advisory Template

```text
Title: [Project] [Vulnerability class] in [Component]

Summary:
Impact:
Affected Versions:
Fixed Versions:
Mitigations / Workarounds:
Detection:
Credits:
Timeline:
```

### 4.6 Recommended Branch Protection

- PRs required, no direct push
- Security, CI, and release changes require approval from at least two people
- Required checks: `CI / build`, `CI / codeql`, `Dependency Review`
- Require conversation resolution before merge
