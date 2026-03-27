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
| Unknown provider names | Blocked and treated as external |

### Example Error Message

```text
governance.external_transmission=deny により外部LLM送信は禁止されています。
Provider: gemini. 外部送信を許可するには governance.external_transmission を 'allow' に設定してください。
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

FUL can detect, mask, or block sensitive content before prompt data is sent to an LLM.
The detector chain has three families: `regex`, `dictionary`, and `ml`.

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
craftsman-bro.com
support@craftsman-bro.com
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

## 3. OSS Maintainer Vulnerability Response Flow

See [security-scanning.md](security-scanning.md) for the broader operating model.
This section defines how to respond when a vulnerability is reported externally.

### 3.1 End-to-End Flow

1. Intake
2. Triage
3. Reproduction
4. Impact assessment
5. Remediation
6. Release
7. Advisory publication

### 3.2 Severity Guide

- Baseline: CVSS v3.1
- Rough categories:
  - Critical: RCE, authentication bypass, or large-scale leakage
  - High: privilege escalation, tenant escape, or reliable DoS
  - Medium: limited information exposure or high attack difficulty
  - Low: narrow impact

### 3.3 Non-Disclosure Before Fix

- Do not put details or PoCs in public issues or PRs.
- Do not discuss exploit details on public channels.
- Do not publish logs or artifacts that contain sensitive data.

### 3.4 Branch / Advisory Handling

- Option A: fix in a private branch and merge right before disclosure
- Option B: use a GitHub Security Advisory draft and a private fork

### 3.5 Advisory Template

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

### 3.6 Branch Protection

See [SECURITY.md](../SECURITY.md#protected-branches-and-required-checks) for branch protection rules and required CI checks.
