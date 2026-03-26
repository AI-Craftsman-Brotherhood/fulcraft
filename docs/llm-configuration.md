# LLM Provider Configuration Guide

This document summarizes how to configure the LLM providers supported by FUL.
Use the `llm` section in `config.json`.

## Supported Providers

| Provider ID | Description | Model field | Main required fields |
|---|---|---|---|
| `gemini` | Google Gemini API | `model_name` optional | `api_key` |
| `openai`, `openai-compatible`, `openai_compatible` | OpenAI API or OpenAI-compatible endpoint | `model_name` | `api_key` |
| `anthropic` | Anthropic Claude API | `model_name` | `api_key` |
| `azure-openai`, `azure_openai` | Azure OpenAI Service | `azure_deployment` | `url`, `azure_api_version`, `api_key` |
| `vertex`, `vertex-ai`, `vertex_ai` | Google Vertex AI | `vertex_model` | `vertex_project`, `vertex_location`, `api_key` |
| `bedrock` | AWS Bedrock | `model_name` | `aws_region`, `aws_access_key_id`, `aws_secret_access_key` |
| `local`, `ollama`, `vllm` | Local OpenAI-compatible LLM endpoint | `model_name` | `url` |
| `mock` | Test-only mock provider | - | - |

Notes:

- `openai-compatible` and `openai_compatible` are aliases of `openai`.
- `azure_openai` is an alias of `azure-openai`.
- `vertex-ai` and `vertex_ai` are aliases of `vertex`.
- `ollama` and `vllm` are aliases of `local`.
- LM Studio and similar tools should use `provider: local`.

## Common Configuration

```json
{
  "llm": {
    "provider": "gemini",
    "model_name": "gemini-2.0-flash-exp",
    "api_key": "${GEMINI_API_KEY}",
    "request_timeout": 300,
    "max_retries": 3,
    "fix_retries": 2,
    "deterministic": true,
    "seed": 42,
    "temperature": 0.2,
    "max_tokens": 4096
  }
}
```

Important keys:

- `provider`: provider ID, required
- `model_name`: model name for Gemini, OpenAI, Anthropic, Bedrock, and local providers. Optional for Gemini
- `api_key`: API key. Environment variables are recommended
- `url`: endpoint override, used by compatible providers and some hosted providers
- `custom_headers`: extra HTTP headers for proxies or internal gateways
- `connect_timeout`, `request_timeout`: timeout values in seconds
- `max_response_length`: maximum response length in characters
- `max_retries`: max retries for API failures
- `fix_retries`: retry count for automated post-generation repair
- `deterministic`: reproducibility mode
- `seed`: random seed used when deterministic mode is active
- `system_message`: provider-dependent system message override
- `temperature`: sampling temperature, ignored when `deterministic: true`
- `max_tokens`: max generated token count

### `deterministic` and `seed`

- `deterministic: true` is the default. FUL forces temperature to `0.0`, and uses seed `42` when none is set.
- `deterministic: false` uses the configured temperature or the default `0.2`, and applies `seed` only when explicitly provided.

## Provider-Specific Examples

### 1. Google Gemini

```json
{
  "llm": {
    "provider": "gemini",
    "model_name": "gemini-2.0-flash-exp",
    "api_key": "${GEMINI_API_KEY}"
  }
}
```

### 2. OpenAI / OpenAI-Compatible

```json
{
  "llm": {
    "provider": "openai",
    "model_name": "gpt-4o-mini",
    "api_key": "${OPENAI_API_KEY}"
  }
}
```

Set `llm.url` when targeting a compatible endpoint.

### 3. Anthropic

```json
{
  "llm": {
    "provider": "anthropic",
    "model_name": "claude-3-5-sonnet-20240620",
    "api_key": "${ANTHROPIC_API_KEY}"
  }
}
```

### 4. Azure OpenAI

```json
{
  "llm": {
    "provider": "azure-openai",
    "url": "https://my-resource.openai.azure.com",
    "azure_deployment": "my-gpt4-deployment",
    "azure_api_version": "2024-02-15-preview",
    "api_key": "${AZURE_OPENAI_API_KEY}"
  }
}
```

### 5. Vertex AI

```json
{
  "llm": {
    "provider": "vertex",
    "vertex_project": "my-gcp-project",
    "vertex_location": "us-central1",
    "vertex_model": "gemini-1.5-pro",
    "vertex_publisher": "google",
    "api_key": "${VERTEX_AI_ACCESS_TOKEN}"
  }
}
```

`vertex_publisher` defaults to `google`.

### 6. AWS Bedrock

```json
{
  "llm": {
    "provider": "bedrock",
    "model_name": "anthropic.claude-3-5-sonnet-20240620-v1:0",
    "aws_region": "us-east-1",
    "aws_access_key_id": "${AWS_ACCESS_KEY_ID}",
    "aws_secret_access_key": "${AWS_SECRET_ACCESS_KEY}"
  }
}
```

Set `aws_session_token` too when you use temporary credentials.

### 7. Local LLM

```json
{
  "llm": {
    "provider": "local",
    "url": "http://localhost:11434/v1",
    "model_name": "qwen2.5:14b"
  }
}
```

`url` may point either to `/v1` or `/v1/chat/completions`; FUL normalizes it automatically.
`local` is not treated as external transmission even when `governance.external_transmission: deny` is set.

### 8. Mock Provider

```json
{
  "llm": {
    "provider": "mock"
  }
}
```

## Recommended Environment Variables

Prefer environment variables instead of hardcoding credentials into `config.json`.

| Environment variable | Provider |
|---|---|
| `GEMINI_API_KEY` | Gemini |
| `OPENAI_API_KEY` | OpenAI |
| `ANTHROPIC_API_KEY` | Anthropic |
| `AZURE_OPENAI_API_KEY` | Azure OpenAI |
| `VERTEX_AI_ACCESS_TOKEN` | Vertex AI |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` | Bedrock |
| `AWS_REGION`, `AWS_DEFAULT_REGION` | Bedrock |

## Advanced Configuration

```json
{
  "llm": {
    "custom_headers": { "X-Org-Id": "example" },
    "fallback_stub_enabled": true,
    "javac_validation": false,
    "retry_initial_delay_ms": 2000,
    "retry_backoff_multiplier": 2.0,
    "rate_limit_qps": 2.0,
    "circuit_breaker_threshold": 5,
    "circuit_breaker_reset_ms": 30000,
    "allowed_providers": ["gemini", "openai"],
    "allowed_models": {
      "openai": ["gpt-4o", "gpt-4o-mini"]
    },
    "smart_retry": {
      "same_error_max_retries": 1,
      "total_retry_budget_per_task": 3,
      "non_recoverable_max_retries": 0
    }
  }
}
```

Notes:

- `allowed_providers` and `allowed_models` are useful for governance and audit constraints.
- `seed` and `system_message` may not be supported by every provider. FUL warns when the provider ignores them.

## Switching Providers

Switch providers by updating `config.json` or by passing another config file with `-c`.

```bash
./scripts/ful run -c config-openai.json
```
