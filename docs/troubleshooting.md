# トラブルシューティング

FUL の実行時に起きやすい問題を、現在の CLI 実装に合わせて整理しています。

## まず確認すること

```bash
java -version
./scripts/ful --help
./scripts/ful steps
```

- `java -version` が 21 以上か確認
- `./scripts/ful --help` が実行できるか確認（`ful.jar` 未生成時はここで止まります）
- `./scripts/ful steps` で利用可能ステップを確認

## Java / JDK 関連のエラー

| 症状 | 代表メッセージ | 対処 |
|---|---|---|
| Java が見つからない | `Error: Java is not installed or not in PATH` | Java 21+ をインストールし、`PATH`/`JAVA_HOME` を設定 |
| バージョン不一致 | `UnsupportedClassVersionError` | Java 実行環境を 21+ に更新 |
| ラッパー実行時の警告 | `Warning: Java 21 or later is recommended` | 実行は継続しますが、Java 21+ への更新を推奨 |
| Gradle の JDK 検出警告 | `Path for java installation '/usr/lib/jvm/openjdk-21' ... does not contain a java executable` | `JAVA_HOME` を実在する JDK 21 パスへ設定し、`~/.gradle/gradle.properties` で `org.gradle.java.installations.fromEnv=JAVA_HOME` を有効化 |

推奨設定（ローカル環境）:

```properties
org.gradle.java.installations.auto-detect=false
org.gradle.java.installations.fromEnv=JAVA_HOME
org.gradle.java.installations.auto-download=true
```

## LLM API Key 関連のエラー

`llm.provider` ごとに必須項目が異なります。以下は代表例です。

| provider | 代表エラー | 必須項目 |
|---|---|---|
| `gemini` | `'llm.api_key' or GEMINI_API_KEY env var is required for gemini provider` | `llm.api_key` または `GEMINI_API_KEY` |
| `openai` / `openai-compatible` | `'llm.api_key' or OPENAI_API_KEY ...` / `'llm.model_name' is required ...` | `api_key` + `model_name` |
| `anthropic` | `'llm.model_name' is required ...` / `'llm.api_key' or ANTHROPIC_API_KEY ...` | `api_key` + `model_name` |
| `azure-openai` | `'llm.azure_deployment' is required ...` など | `url`, `azure_deployment`, `azure_api_version`, `api_key` |
| `vertex` | `'llm.vertex_project' is required ...` など | `vertex_project`, `vertex_location`, `vertex_model`, `api_key`(OAuth token) |
| `bedrock` | `'llm.aws_access_key_id' or AWS_ACCESS_KEY_ID ...` など | `model_name`, AWS認証情報, `aws_region` |
| `local` / `ollama` | `'llm.url' is required for local/ollama provider` | `url`, `model_name` (`api_key` は不要) |

## `ful run` でテストが生成されない

`ful run` の既定ステップは `pipeline.workflow_file` の有無で変わります。  

- `pipeline.workflow_file` 未設定時: `ANALYZE -> DOCUMENT -> REPORT -> EXPLORE`
- `pipeline.workflow_file` 設定時: `GENERATE`（workflow ノード実行モード）

`--steps` / `--from` / `--to` を指定した場合は、上記既定より CLI 指定が優先されます。  
テスト生成を含める場合は、必要なステップを明示指定してください。

```bash
# 生成まで
./scripts/ful run --to GENERATE

# 解析 + 生成 + レポート
./scripts/ful run --steps ANALYZE,GENERATE,REPORT
```

## 設定ファイル関連のエラー

### 読み込み順

1. `-c/--config` で指定したパス
2. `config.json`
3. `.ful/config.json`

### よくあるエラー

| 症状 | 代表メッセージ | 対処 |
|---|---|---|
| 明示指定した設定ファイルがない | `Configuration file not found: ...` | `-c` のパスを修正 |
| JSON Schema 不整合 | `Config schema validation failed for: ...` | `docs/config-schema.md` と照合 |
| 未知キー | `Unknown top-level configuration keys: ...` | `config.example.json` のキー名に合わせる |
| セクション型不正 | `'<section>' section must be an object/mapping.` | オブジェクト形式へ修正 |
| プロバイダー値不正 | `'llm.provider' must be one of: ...` | サポート済み provider 名へ修正 |

## プロジェクトパス / 解析関連のエラー

| 症状 | 代表メッセージ | 対処 |
|---|---|---|
| `-p` が誤っている | `プロジェクトルートは実在するディレクトリである必要があります: ...` | `-p` を実在ディレクトリに修正 |
| 解析ルートが見つからない | `Source directory not found in project: ...` | `src/main/java` などがあるか確認。必要なら `analysis.source_root_paths` を設定 |
| ビルドツール検出失敗 | `Gradle wrapper not found` / `Maven wrapper or mvn not found` | 対象プロジェクトの `gradlew`/`mvnw` または `mvn` を利用可能にする |

## レポート / Run ID 関連のエラー

| 症状 | 代表メッセージ | 対処 |
|---|---|---|
| Run ディレクトリがない | `Runsディレクトリが見つかりません: ...` | 先に `ful run` を実行するか `--input` で tasks ファイルを指定 |
| 指定 run がない | `Runディレクトリが見つかりません: ...` | `--run-id` を見直す |
| tasks ファイルがない | `タスクファイルが見つかりません: ...` | `.ful/runs/<runId>/plan/` 配下を確認 |
| 解析専用 report で run 指定ミス | `analysis_report.error.run_dir_not_found` / `analysis_dir_not_found` | `ful report --run-id <id>` の対象 run を確認 |

最新 run を確認する例:

```bash
ls -1t .ful/runs | head -1
```

## Interactive (TUT) 関連

| 症状 | 代表メッセージ | 対処 |
|---|---|---|
| 対話CLIを起動したい | - | `ful tut`（非画面）を使用 |
| 画面付きセッションに戻りたい | - | `ful resume` で既存 TUI セッションを再開 |

## キャッシュと再実行

```bash
# キャッシュのみ削除
rm -rf .ful/cache

# 実行履歴を含めて削除（必要時のみ）
rm -rf .ful/runs
```

初回実行が遅い場合は依存関係解決の影響が大きく、通常動作です。

## 追加リンク

- [Quickstart](quickstart.md)
- [CLI ガイド](cli.md)
- [設定リファレンス](config.md)
- [JSON Schema](config-schema.md)
- [LLM プロバイダー設定](llm-configuration.md)
- [Quality Gates](../QUALITY_GATES.md)
