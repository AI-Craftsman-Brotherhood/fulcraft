# Governance (ガバナンス)

FUL におけるガバナンス機能は、LLM 利用時の安全性を担保するためのポリシー制御です。  
このドキュメントは、現行実装で有効な機能のみを対象に整理しています。

## 対象範囲

- 外部 LLM 送信制御（`governance.external_transmission`）
- プロンプト機密情報レダクション（`governance.redaction`）
- OSS メンテナ向けの脆弱性対応フロー

## 1. 外部 LLM 送信制御

### 概要

`governance.external_transmission: deny` を設定すると、外部 LLM への送信を禁止します。  
判定とブロックは `LlmGovernanceEnforcingClient` で強制されます。

- 実装: `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/llm/decorator/LlmGovernanceEnforcingClient.java`
- 外部判定: `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/llm/LlmProviderRegistry.java`

### `deny` 時の扱い

| Provider | 動作 |
|---|---|
| `openai`, `openai-compatible`, `openai_compatible` | ブロック |
| `gemini` | ブロック |
| `anthropic` | ブロック |
| `azure-openai`, `azure_openai` | ブロック |
| `vertex`, `vertex-ai`, `vertex_ai` | ブロック |
| `bedrock` | ブロック |
| `local`, `ollama`, `vllm` | 許可 |
| `mock` | 許可 |
| 未知の provider 名 | ブロック（外部扱い） |

### 例外メッセージ（例）

```text
governance.external_transmission=deny により外部LLM送信は禁止されています。
Provider: gemini. 外部送信を許可するには governance.external_transmission を 'allow' に設定してください。
```

### 設定例

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

## 2. プロンプト機密情報レダクション

### 概要

`PromptRedactionService` は、LLM 送信前のプロンプトに対して検出・マスク・ブロックを行います。  
検出器チェーンは `regex` / `dictionary` / `ml` の 3 系統です。

- 実装: `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/llm/safety/redaction/PromptRedactionService.java`
- 検出器:
  - `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/llm/safety/redaction/detector/RegexDetector.java`
  - `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/llm/safety/redaction/detector/DictionaryDetector.java`
  - `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/llm/safety/redaction/detector/MlNerDetector.java`

### モード

| モード | 動作 |
|---|---|
| `off` | detector chain を無効化し、互換目的の legacy redactor を使用（ブロックなし） |
| `report` | 検出のみ（通常は本文を変更しない） |
| `enforce` | 閾値に応じてマスク/ブロック |

### しきい値

- `mask_threshold` 以上の検出はマスク候補
- `block_threshold` 以上の検出がある場合は `RedactionException` を送出して送信中断
- デフォルト値:
  - `mask_threshold`: `0.60`
  - `block_threshold`: `0.90`

### detector の有効条件

- `regex`: 常時利用可能
- `dictionary`: `denylist_path` 指定時のみ実質有効
- `ml`: `ml_endpoint_url` 指定時のみ実質有効

### 設定例

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

### 辞書ファイル例

`denylist.txt`:

```text
# comments
社外秘
機密情報
project-alpha
internal-api-key
```

`allowlist.txt`:

```text
# false positive suppression
craftsmann-bro.com
support@craftsmann-bro.com
localhost
```

### ブロック時の例外（例）

```text
Sensitive data blocked by redaction policy.
Detector: regex, Type: PEM_KEY, Confidence: 1.00 (threshold: 0.90).
Found 1 high-confidence items.
```

### ML NER 連携の期待フォーマット

リクエスト:

```json
{"text":"Hello, John Doe from Acme Corp."}
```

レスポンス:

```json
{
  "entities": [
    {"text": "John Doe", "label": "PERSON", "start": 7, "end": 15, "score": 0.95},
    {"text": "Acme Corp", "label": "ORG", "start": 21, "end": 30, "score": 0.88}
  ]
}
```

## 3. 実行時の適用ポイント

外部送信制御とレダクションは、主に LLM 呼び出し経路で適用されます。

- 送信制御デコレータ:
  - `DefaultServiceFactory` が `LlmGovernanceEnforcingClient` を適用
  - 例: `app/src/main/java/com/craftsmanbro/fulcraft/ui/cli/wiring/DefaultServiceFactory.java`
- レダクション:
  - 各 LLM 呼び出しフローで `PromptRedactionService` を適用
  - 例:
    - `app/src/main/java/com/craftsmanbro/fulcraft/feature/document/flow/DocumentFlow.java`
    - `app/src/main/java/com/craftsmanbro/fulcraft/feature/document/adapter/LlmDocumentGenerator.java`

## 4. OSS メンテナ向け脆弱性対応フロー

運用全体は [security-scanning.md](security-scanning.md) を参照してください。  
ここでは外部報告を受けた際の対応手順を定義します。

### 4.1 End-to-End Flow

1. 受付 (Intake)
2. トリアージ
3. 再現
4. 影響評価
5. 修正
6. リリース
7. アドバイザリ公開

### 4.2 重大度ガイド

- 基準: CVSS v3.1
- 目安:
  - Critical: RCE / 認証回避 / 大規模漏えい
  - High: 権限昇格 / テナント越境 / 確実な DoS
  - Medium: 限定的な情報露出 / 攻撃難易度高
  - Low: 影響限定

### 4.3 修正前の非開示ルール

- 公開 Issue/PR に詳細や PoC を書かない
- 公開チャネルで詳細議論しない
- 機密を含むログや成果物を公開しない

### 4.4 ブランチ/アドバイザリ運用

- Option A: 非公開ブランチで修正し公開直前にマージ
- Option B: GitHub Security Advisory のドラフト + private fork で修正

### 4.5 アドバイザリ雛形

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

### 4.6 保護ブランチ設定（推奨）

- PR 必須（直接 push 禁止）
- セキュリティ/CI/リリース変更は 2 名以上承認
- 必須チェック: `CI / build`, `CI / codeql`, `Dependency Review`
- 会話解決の必須化
