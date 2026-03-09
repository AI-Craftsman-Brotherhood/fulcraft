# ログ設定（Unified Logging）

FUL は、CLI/TUI/パイプラインで共通のロギング基盤を使います。  
このドキュメントは現行実装（`app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/logging/Logger.java`）に合わせた内容です。

## 1. 構成（最新版）

- UI 層（CLI/TUI）は `UiLogger` を入口として利用します。
  - `app/src/main/java/com/craftsmanbro/fulcraft/ui/cli/UiLogger.java`
  - `app/src/main/java/com/craftsmanbro/fulcraft/ui/tui/UiLogger.java`
- カーネル層は `LoggerPort` を使用し、起動時に `KernelLoggerFactoryAdapter` で実装へ接続されます。
  - `app/src/main/java/com/craftsmanbro/fulcraft/logging/LoggerPortProvider.java`
  - `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/logging/KernelLoggerFactoryAdapter.java`
- 実体は SLF4J/Logback で、`Logger` が設定適用・MDC・マスキングを管理します。
- レイアウトは 3 種類です。
  - `MaskedPatternLayout`（human）
  - `JsonLayout`（json）
  - `YamlLayout`（yaml）
- 機密情報は `SecretMasker` でマスクされます。
  - `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/security/SecretMasker.java`

## 2. 設定（config.json）

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

| キー | 値 | 説明 |
|---|---|---|
| `log.format` | `human` / `json` / `yaml` | ログ形式 |
| `log.level` | `debug` / `info` / `warn` / `error` | ログレベル |
| `log.output` | `console` / `file` / `both` | 出力先 |
| `log.color` | `auto` / `on` / `off` | 色制御（`cli.color` 未指定時のフォールバック） |
| `log.file_path` | 文字列 | 出力ファイル名（実行時に run ディレクトリ配下へ再配置） |
| `log.include_timestamp` | bool | human 形式のコンソール出力で時刻を付加 |
| `log.include_thread` | bool | human 形式で thread 名を付加 |
| `log.include_logger` | bool | human 形式で logger 名を付加 |
| `log.max_message_length` | 整数 | `trimLargeContent` 利用時の上限（`0` は無制限） |
| `log.enable_mdc` | bool | MDC の有効/無効 |

## 3. 上書き（CLI / 環境変数）

CLI オプション:

- `--log-format <human|json|yaml>`
- `--json`（`--log-format json` の短縮）
- `--color <on|off|auto>`

環境変数:

- `FUL_LOG_FORMAT`（`log.format` を上書き）
- `FUL_COLOR`（`cli.color` を上書き）
- `NO_COLOR`（`FUL_COLOR` が未指定の場合に `cli.color=off`）

補足:

- `-v, --verbose` はスタックトレース表示制御であり、`log.level` は変更しません。
- 実装上の適用順は「設定 + CLI 上書き」→「環境変数上書き」です。

## 4. 出力先と run ディレクトリ

`execution.logs_root`（既定: `.ful/runs`）配下に run 単位でログを書きます。

- run ルート: `<execution.logs_root>/<runId>/`
- ログディレクトリ: `<execution.logs_root>/<runId>/logs/`
- アプリログ: `<execution.logs_root>/<runId>/logs/<file_name>`
- LLM ログ: `<execution.logs_root>/<runId>/logs/llm.log`

`log.file_path` は最終的にファイル名部分のみ使われます。  
例: `logs/custom.log` を指定しても、実体は `<runId>/logs/custom.log` になります。

## 5. 出力モード

- `console`: INFO 以下は stdout、WARN/ERROR は stderr
- `file`: コンソール appender を外し、ファイル出力のみ
- `both`: コンソール + ファイルの両方

## 6. フォーマット

### human

```text
[INFO] analysis started
[WARN] unresolved symbol: ...
```

### json（NDJSON）

```json
{"timestamp":"2026-02-11T06:30:00Z","level":"INFO","thread":"main","logger":"utgenerator","message":"analysis started","runId":"20260211-063000-abc123","traceId":"1a2b3c4d"}
```

### yaml（YAML stream）

```yaml
---
timestamp: "2026-02-11T06:30:00Z"
level: "INFO"
message: "analysis started"
```

## 7. MDC キー

`enable_mdc=true` の場合、次のキーを扱えます。

| キー | 説明 |
|---|---|
| `runId` | 実行 ID |
| `traceId` | トレース ID |
| `subsystem` | サブシステム名 |
| `stage` | ステージ名 |
| `targetClass` | 対象クラス |
| `taskId` | タスク ID |

補足:

- `runId` は `configureRunLogging(...)` 実行時に設定されます。
- `traceId` は初期化時に生成されます。
- `subsystem` / `stage` / `targetClass` / `taskId` は必要に応じて明示設定します。

## 8. シークレットマスキング

ログ出力時に `SecretMasker` が以下を `****` へ置換します。

- PEM 形式の private key
- Authorization ヘッダー値
- `api_key` / `token` / `password` などのキー付き値
- JWT 形式トークン
- 長いランダムトークン（Hex/Base64 など）

## 9. 環境診断ログ

`Main` 起動時に `EnvironmentLogger.logStartupEnvironment()` が呼ばれます。  
出力は DEBUG レベルで、`[Environment]` プレフィックス付きで以下を記録します。

- Java version
- Gradle version
- Toolchain version
- project root / source
- current working directory

## 10. 運用の推奨

1. UI からは `UiLogger` を利用する
2. カーネル層は `LoggerPortProvider.getLogger(...)` を利用する
3. CI や集約用途では `log.format: json` を使う
4. 機密情報をログメッセージへ直接含めない
