# FUL Quickstart ガイド

このガイドでは、FUL のセットアップから初回テスト生成までを詳しく解説します。

---

## 目次

1. [必要条件](#必要条件)
2. [初期セットアップ](#初期セットアップ)
3. [インタラクティブセットアップ](#インタラクティブセットアップ-ful-init)
4. [テスト生成と実行](#テスト生成と実行)
5. [TUI モード](#tui-モード)
6. [トラブルシューティング](#トラブルシューティング)
7. [キャッシュ管理](#キャッシュ管理)

---

## 必要条件

### JDK 21

FUL は **JDK 21 以上**が必要です。

```bash
# バージョン確認
java -version
```

**期待される出力:**
```
openjdk version "21.0.x" ...
```

**インストール方法 (SDKMAN 推奨):**

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.5-tem
sdk use java 21.0.5-tem
```

`JAVA_HOME` が正しく設定されていることも確認してください。

### Gradle 8.x（オプション）

FUL のビルドには **Gradle 8.x** が必要ですが、プロジェクトに同梱されている **Gradle Wrapper (`gradlew`)** を使用するため、別途インストールの必要はありません。

---

## 初期セットアップ

### Step 1: リポジトリをクローン

```bash
git clone https://github.com/your-org/fulcraft.git
cd fulcraft
```

### Step 2: 初回ビルド

```bash
./gradlew :app:shadowJar
```

**ビルド成功の確認:**
```bash
ls -la app/build/libs/ful-*.jar
```

### Step 3: ラッパースクリプトの確認

```bash
./scripts/ful --version
```

以降のコマンドで同じラッパーを使うため、`FUL_HOME` を設定しておくと便利です。

```bash
export FUL_HOME="/path/to/fulcraft"
```

### Step 4: APIキーの設定

LLM プロバイダーの API キーを環境変数として設定します（推奨）。

```bash
# Gemini（デフォルト推奨）
export GEMINI_API_KEY="your-gemini-api-key"

# OpenAI
export OPENAI_API_KEY="your-openai-api-key"

# Anthropic
export ANTHROPIC_API_KEY="your-anthropic-api-key"
```

> **注意:** Local LLM を使用する場合は API キーは不要です。Azure OpenAI / Vertex AI / Bedrock などは設定ファイルで指定するため、詳細は [LLM Provider Configuration](llm-configuration.md) を参照してください。

---

## 対象プロジェクトでの利用

### Gradle プロジェクトでの利用

```bash
# 1. FULをビルド
cd fulcraft
./gradlew :app:shadowJar

# 2. 対象プロジェクトでテスト生成
java -jar /path/to/fulcraft/app/build/libs/ful-*.jar run --project-root /path/to/your-gradle-project

# または、対象プロジェクトに移動してから実行
cd /path/to/your-gradle-project
/path/to/fulcraft/scripts/ful init   # 設定ファイル作成
/path/to/fulcraft/scripts/ful run --to GENERATE
```

> **Note:** FUL は対象プロジェクトの `gradlew` を使用してクラスパスを自動解決します。

### Maven プロジェクトでの利用

```bash
# 1. FULをビルド
cd fulcraft
./gradlew :app:shadowJar

# 2. 対象プロジェクトでテスト生成
java -jar /path/to/fulcraft/app/build/libs/ful-*.jar run --project-root /path/to/your-maven-project
```

> **Note:** FUL は対象プロジェクトの `mvnw` または `mvn` を使用してクラスパスを自動解決します。

---

## インタラクティブセットアップ (`ful init`)

テスト生成対象のプロジェクト（あなたのJavaプロジェクト）で `ful init` を実行し、設定ファイルを作成します。

```bash
# 対象プロジェクトへ移動
cd /path/to/your-java-project

# 初期化を実行
$FUL_HOME/scripts/ful init
```

`FUL_HOME` を設定していない場合は、`/path/to/fulcraft/scripts/ful` に置き換えてください。

ウィザードに従い、LLMプロバイダーやモデルを選択してください。設定ファイル `.ful/config.json` が生成されます。

`ful init` のウィザードは `gemini` / `openai` / `anthropic` / `local` を選択できます。その他のプロバイダーは `.ful/config.json` を手動で編集してください。

---

## テスト生成と実行

FUL には主に2つの実行モードがあります。
以下のコマンドは対象プロジェクトのルートで実行します（別の場所から実行する場合は `-p, --project-root` を指定）。

### 1. `ful run --to GENERATE` (生成のみ)

解析 (ANALYZE) と生成 (GENERATE) を実行し、テストファイルを作成して終了します。

```bash
# 全てのソースファイルを対象にテスト生成
$FUL_HOME/scripts/ful run --to GENERATE

# 特定のファイルのみ
$FUL_HOME/scripts/ful run --to GENERATE -f MyService.java

# ドライラン（ファイル変更なし）
$FUL_HOME/scripts/ful run --to GENERATE --dry-run
```

### 2. `ful run` (基本実行)

`ful run` の既定動作は `pipeline.workflow_file` の有無で変わります。

```bash
$FUL_HOME/scripts/ful run
```

- `pipeline.workflow_file` 未設定時:
  - 既定ステップは `ANALYZE` → `DOCUMENT` → `REPORT` → `EXPLORE`
- `pipeline.workflow_file` 設定時:
  - 既定ステップは `GENERATE`（workflow ノード実行モード）
- `--steps` / `--from` / `--to` を指定した場合:
  - 上記既定より CLI 指定が優先されます

テスト生成やレポート出力を含めたい場合は `--steps` で明示指定します（例: `ANALYZE,GENERATE,REPORT`）。

### 3. パイプライン制御 (高度な実行)

特定のステージのみ実行したり、範囲を指定したりできます。

```bash
# 解析のみ
$FUL_HOME/scripts/ful run --steps ANALYZE

# 解析 → 生成まで（generate と同等）
$FUL_HOME/scripts/ful run --to GENERATE

# 解析 → 生成 → レポートまで（document をスキップ）
$FUL_HOME/scripts/ful run --steps ANALYZE,GENERATE,REPORT
```

`GENERATE` 以降は `ANALYZE` の成果物が必要なので、`--steps` 指定時は `ANALYZE` を含めてください。ステップ一覧は `ful steps` で確認できます。

また、`config.json` でデフォルトの有効ステージを制御できます。

```json
{
  "pipeline": {
    "stages": ["analyze", "generate", "report"]
  }
}
```

### 生成結果の確認

生成されたファイルは標準で `src/test/java` に配置されます。

```bash
# レポートの確認 (ful run 実行時)
RUN_ID=$(ls -1t .ful/runs | head -1)
cat ".ful/runs/${RUN_ID}/report/report.md"
```

主な成果物の場所:
- 解析結果: `.ful/runs/<runId>/analysis/`
- タスクファイル: `.ful/runs/<runId>/plan/tasks.*`
- レポート: `.ful/runs/<runId>/report/report.md` / `.ful/runs/<runId>/report/summary.json`
- ログ: `.ful/runs/<runId>/logs/ful.log` / `.ful/runs/<runId>/logs/llm.log`

JSON で出力したい場合は `output.format.report: "json"` を設定し、`.ful/runs/<runId>/report/summary.json` を確認してください。

---

## 対話モード

対話モードは `tut`（非画面）で起動できます。

```bash
# 非画面の対話CLI (TUT)
$FUL_HOME/scripts/ful tut
```

- `tut`: Codex風の行ベース対話（非画面）

---

## トラブルシューティング

**よくあるエラー:**

- **`UnsupportedClassVersionError`**: JDK 21未満で実行している可能性があります。`java -version` を確認してください。
- **`Missing API Key`**: 環境変数が設定されていません。
- **`ful.jar not found`**: ビルド (`./gradlew :app:shadowJar`) を実行し、`app/build/libs/ful-*.jar` が生成されているか確認してください。

詳細は [トラブルシューティングガイド](troubleshooting.md) を参照してください。

---

## キャッシュ管理

FUL は生成結果を `.ful/cache` にキャッシュし、再実行時のコストを削減します。

```bash
# キャッシュをクリアして強制再生成
rm -rf .ful/cache
```

---

## 次のステップ

- **詳細設定**: [Configuration Guide](config.md)
- **セキュリティ**: [Docs: Security & Governance](governance.md)
- **アーキテクチャ理解**: [Unified Structure Guide](design/unified-structure-guide.md)
