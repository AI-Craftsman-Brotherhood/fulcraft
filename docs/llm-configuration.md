# LLM プロバイダー設定ガイド

このドキュメントでは、FUL で使用可能な LLM プロバイダーの設定方法をまとめます。設定は `config.json` の `llm` セクションで行います。

## サポートされるプロバイダー一覧

| プロバイダーID | 説明 | モデル指定 | 主な必須項目 |
|---|---|---|---|
| `gemini` | Google Gemini API | `model_name`（省略可） | `api_key` |
| `openai` / `openai-compatible` / `openai_compatible` | OpenAI API / OpenAI互換 | `model_name` | `api_key` |
| `anthropic` | Anthropic Claude API | `model_name` | `api_key` |
| `azure-openai` / `azure_openai` | Azure OpenAI Service | `azure_deployment` | `url`, `azure_api_version`, `api_key` |
| `vertex` / `vertex-ai` / `vertex_ai` | Google Vertex AI | `vertex_model` | `vertex_project`, `vertex_location`, `api_key` |
| `bedrock` | AWS Bedrock | `model_name` | `aws_region`, `aws_access_key_id`, `aws_secret_access_key` |
| `local` / `ollama` / `vllm` | ローカル LLM (Ollama/vLLM/LM Studio などのOpenAI互換) | `model_name` | `url` |
| `mock` | テスト用モック | - | - |

メモ:
- `openai-compatible` / `openai_compatible` は `openai` のエイリアスです。
- `azure_openai` は `azure-openai` のエイリアスです。
- `vertex-ai` / `vertex_ai` は `vertex` のエイリアスです。
- `ollama` / `vllm` は `local` のエイリアスです。LM Studio などは `provider: local` を使用してください。

---

## 共通設定 (Common Configuration)

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

主なキー:
- `provider`: プロバイダーID（必須）
- `model_name`: モデル名（Gemini/OpenAI/Anthropic/Bedrock/Local で使用、Gemini は省略可）
- `api_key`: API キー（環境変数参照を推奨）
- `url`: API エンドポイントの上書き（OpenAI互換/Anthropic/Local/Vertex/Bedrock/Azure で使用）
- `custom_headers`: 追加ヘッダー（プロキシ/社内ゲートウェイ等）
- `connect_timeout` / `request_timeout`: 接続/リクエストのタイムアウト秒数
- `max_response_length`: レスポンス最大長（文字数）
- `max_retries`: API エラー時の最大リトライ回数
- `fix_retries`: 生成後の自動修復リトライ回数
- `deterministic`: 再現性モード（`true` で温度 0.0 固定）
- `seed`: 乱数シード（deterministic 時は未指定なら 42）
- `system_message`: システムメッセージ（対応プロバイダーのみ）
- `temperature`: 生成時の温度パラメータ（0.0〜1.0程度、`deterministic: true` の場合は無視されます）
- `max_tokens`: 生成される最大トークン数

### deterministic / seed の挙動

- `deterministic: true` (デフォルト): `temperature` は 0.0 に固定され、`seed` は未指定時に 42 が利用されます。
- `deterministic: false`: `temperature` は指定値または既定値 0.2 を使用し、`seed` は指定時のみ有効です。

---

## プロバイダー別設定詳細

### 1. Google Gemini (推奨)

```json
{
  "llm": {
    "provider": "gemini",
    "model_name": "gemini-2.0-flash-exp",
    "api_key": "${GEMINI_API_KEY}"
  }
}
```

### 2. OpenAI / OpenAI互換

```json
{
  "llm": {
    "provider": "openai",
    "model_name": "gpt-4o-mini",
    "api_key": "${OPENAI_API_KEY}"
  }
}
```
互換エンドポイントに接続する場合は `llm.url` を指定してください。

### 3. Anthropic (Claude)

```json
{
  "llm": {
    "provider": "anthropic",
    "model_name": "claude-3-5-sonnet-20240620",
    "api_key": "${ANTHROPIC_API_KEY}"
  }
}
```
必要に応じて `llm.url` を指定できます。

### 4. Azure OpenAI Service

Azure のリソースエンドポイント (`https://<resource>.openai.azure.com`) とデプロイメント名が必須です。

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

### 5. Google Vertex AI

`api_key` は OAuth アクセストークンとして使用されます。

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
`vertex_publisher` は省略時に `google` が使用されます。

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
一時クレデンシャルを使う場合は `aws_session_token` も設定します。

### 7. Local LLM (Ollama / vLLM / LM Studio)

OpenAI 互換のローカルエンドポイントを指定します。

```json
{
  "llm": {
    "provider": "local",
    "url": "http://localhost:11434/v1",
    "model_name": "qwen2.5:14b"
  }
}
```

`url` は `/v1` または `/v1/chat/completions` のいずれでも指定可能です（自動で補正されます）。
`governance.external_transmission: deny` の設定下でも `local` は外部送信扱いになりません。

### 8. Mock (テスト用)

```json
{
  "llm": {
    "provider": "mock"
  }
}
```

---

## 認証と推奨環境変数

`config.json` に API キーを直接書かず、環境変数から参照することを推奨します。

| 環境変数名 | 対応プロバイダー |
|---|---|
| `GEMINI_API_KEY` | Gemini |
| `OPENAI_API_KEY` | OpenAI |
| `ANTHROPIC_API_KEY` | Anthropic |
| `AZURE_OPENAI_API_KEY` | Azure OpenAI |
| `VERTEX_AI_ACCESS_TOKEN` | Vertex AI |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_SESSION_TOKEN` | Bedrock |
| `AWS_REGION` / `AWS_DEFAULT_REGION` | Bedrock |

---

## 高度な設定

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

メモ:
- `allowed_providers` / `allowed_models` はガバナンスや監査用途で利用します。
- `seed` や `system_message` はプロバイダーによって非対応の場合があります（警告が出ます）。

---

## プロバイダーの切り替え

プロバイダーを切り替える場合は `config.json` を更新するか、別の設定ファイルを `-c` で指定します。

```bash
# 例: 別の設定ファイルを使う
./scripts/ful run -c config-openai.json
```
