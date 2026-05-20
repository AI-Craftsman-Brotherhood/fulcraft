# fulcraft-pro リポ立ち上げ用プロンプト (M2 モデル)

> 作成: 2026-05-14
> 用途: 新規 private リポ `fulcraft-pro` を立ち上げる別セッションへ渡すプロンプト
> 関連: [`exhibition-webview-and-junit-monetization.md`](./exhibition-webview-and-junit-monetization.md) §5.3〜5.5

---

## 確定事項（このプロンプトの前提）

- **コード配置モデル**: M2（別リポ + ビルド時バンドル）
- **Community リポ**: `github.com/AI-Craftsman-Brotherhood/fulcraft`（公開, 現状リポ, 本リポジトリ）
- **Pro リポ**: 新規 private リポ `fulcraft-pro`（このプロンプトで立ち上げる対象）
- **JUnit 実装の取り込み元**: `/home/wada/workspaces/fulcraft`（既存実装、Pro リポへ移管）

---

## 別セッションへ渡すプロンプト本文

以下を新しい Claude Code セッションのプロンプトとしてそのまま投入してください。

````markdown
# Task: fulcraft-pro private リポジトリの初期構築

あなたは fulcraft プロジェクトの Pro 版（有料）リポジトリを新規に立ち上げるエンジニアです。
以下の前提と要件に従い、`fulcraft-pro` リポジトリの初期コミット相当のファイル一式を生成してください。

## 前提

### Community 側（参照のみ・改変禁止）
- 場所: `/home/wada/dist/workspaces/fulcraft`
- 公開リポ: `github.com/AI-Craftsman-Brotherhood/fulcraft`
- 提供物: analyze / report / document / explore + WebView 基本
- ライセンス: Source Available / Proprietary（個人・非商用無料、商用有料、改変・再配布禁止）
- ビルド: Java 21, Gradle 8.5 (Kotlin DSL), 単一モジュール `app/`, fat JAR は `:app:shadowJar`
- Entry point: `com.craftsmanbro.fulcraft.Main`
- プラグイン読み込み: Java SPI (`META-INF/services/com.craftsmanbro.fulcraft.kernel.plugin.ActionPlugin`)
- レイヤー: `flow → core → contract ← adapter → io → infrastructure`

### Pro 取り込み元（移管対象）
- 場所: `/home/wada/workspaces/fulcraft`
- 取り込む差分:
  - `plugins/junit/` 一式（`command/` + `suite/{select,generate,report,shared}/`、201 Java / 約 39k LOC）
  - `app/src/main/resources/prompts/` の追加 8 ファイル + `few_shot/` ディレクトリ
    - `default_generation.txt`, `default_fix.txt`, `default_runtime_fix.txt`
    - `high_complexity_generation.txt`
    - `split_phase1_*.txt`, `split_phase2_*.txt`, `split_phase3_*.txt`
    - `fallback_stub.txt`
    - `few_shot/`（ディレクトリごと）
  - `META-INF/services/...ActionPlugin` への 3 行追記
    - `JUnitSelectPlugin`
    - `JUnitGeneratePlugin`
    - `JUnitBrittleCheckPlugin`
  - `ui/cli/command/RunCommand.java` の差分（`--steps SELECT,GENERATE,REPORT` の引数差分のみ。Community 側の最新版をベースに、JUnit 関連の引数追加だけをチェリーピック）
- **取り込まないもの（Community 側が先行・上書き禁止）**:
  - `plugins/exploration/flow/ExploreFlow.java`
  - `plugins/reporting/adapter/AnalysisVisualReportWriter.java`

## 採用方針: M2（別リポ + ビルド時バンドル）

```
github.com/AI-Craftsman-Brotherhood/fulcraft           ← Community（公開・現状リポ）
  └─ analyze + report + document + explore + WebView 基本

private/fulcraft-pro                                    ← 本タスクで作成（非公開）
  ├─ plugins/junit/                                     ← 取り込み元から移管
  ├─ plugins/console-pro/                               ← Pro 限定 WebView 拡張（スケルトンのみ）
  ├─ build.gradle.kts                                   ← Community を Maven 依存として参照
  └─ shadowJar → ful-pro-X.Y.Z.jar                      ← Community + Pro を内包
```

### 依存関係
- Community を `mavenLocal()` または GitHub Packages（private registry）に publish
- Pro 側は `implementation("com.craftsmanbro:fulcraft-app:<version>")` で参照
- `shadowJar` で全部入りビルド
- SPI 経由で JUnit プラグインが自動ロード（kernel 側コード変更なし）

## 成果物（このタスクで作るもの）

### 1. リポジトリ骨格

```
fulcraft-pro/
├── README.md                                    # Pro リポ説明（非公開前提）
├── LICENSE                                      # FUL Pro License（Community より厳格）
├── CLAUDE.md                                    # Claude Code 向けガイダンス
├── .gitignore                                   # build/, .gradle/, .ful/, *.jar, IDE
├── .gitattributes
├── settings.gradle.kts                          # rootProject.name = "fulcraft-pro"
├── build.gradle.kts                             # ルートビルド
├── gradle/
│   └── wrapper/                                 # Gradle 8.5 wrapper
├── gradlew / gradlew.bat
├── gradle.properties                            # JVM heap (4g), org.gradle.parallel=true
├── app-pro/
│   ├── build.gradle.kts                         # shadowJar 設定、Community 依存
│   └── src/
│       ├── main/
│       │   ├── java/com/craftsmanbro/fulcraft/
│       │   │   ├── plugins/junit/              # 取り込み元から移管
│       │   │   └── plugins/consolepro/         # Pro 限定 WebView 拡張（空スケルトン）
│       │   └── resources/
│       │       ├── META-INF/services/
│       │       │   └── com.craftsmanbro.fulcraft.kernel.plugin.ActionPlugin
│       │       └── prompts/                     # 取り込み元から 8 ファイル + few_shot/
│       ├── test/
│       ├── integrationTest/
│       └── e2eTest/
├── baseline/                                    # Checkstyle/SpotBugs/PMD 新規違反分
├── scripts/
│   └── ful-pro                                  # Pro バイナリ起動スクリプト
└── docs/
    ├── PRO_FEATURES.md                          # JUnit 生成・Pro WebView 機能カタログ
    ├── LICENSE_KEY.md                           # ライセンスキー検証方針（M2 では任意）
    └── INTEGRATION.md                           # Community 連携手順
```

### 2. ビルド設定（重要ファイル中身）

#### `settings.gradle.kts`
```kotlin
rootProject.name = "fulcraft-pro"
include("app-pro")
```

#### `app-pro/build.gradle.kts`
- `com.gradleup.shadow` プラグインで fat JAR
- Community と同じ Java 21、Spotless（google-java-format）、Checkstyle、SpotBugs、PMD を同等構成で適用
- 依存:
  - `implementation("com.craftsmanbro:fulcraft-app:<community-version>")` （Maven 経由）
  - 取り込み元の追加依存（あれば）をそのまま継承
- ソースセット: `main`, `test`, `integrationTest`, `e2eTest`（Community と同じ命名）
- タスク: `:app-pro:shadowJar` で `ful-pro-<version>.jar` 出力
- Main-Class: `com.craftsmanbro.fulcraft.Main`（Community を再利用、SPI で Pro プラグインが追加ロードされる）

#### `META-INF/services/com.craftsmanbro.fulcraft.kernel.plugin.ActionPlugin`
```
com.craftsmanbro.fulcraft.plugins.junit.suite.select.JUnitSelectPlugin
com.craftsmanbro.fulcraft.plugins.junit.suite.generate.JUnitGeneratePlugin
com.craftsmanbro.fulcraft.plugins.junit.suite.generate.JUnitBrittleCheckPlugin
```

### 3. README.md

- 「これは fulcraft の Pro 版（有料・非公開）リポです」と明示
- Community との関係図
- ビルド手順（Community を mavenLocal にインストール → Pro をビルド）
- 配布物: `ful-pro-X.Y.Z.zip`(Community + Pro 同梱)
- 機能カタログ(JUnit 生成、WebView Pro 拡張)

### 4. LICENSE(FUL Pro License)

- Community LICENSE をベースに以下を強化:
  - 「Pro 版バイナリの再配布は明示的に禁止」
  - 「リバースエンジニアリング禁止」
  - 「使用許諾は契約単位、契約終了時にバイナリ削除義務」
  - 30 日トライアル条項(展示会デモビルド向け)
- 法律事務所レビュー前提のドラフトとして作成し、冒頭に `> DRAFT — legal review required` と明記

### 5. CLAUDE.md

- Community CLAUDE.md と整合させつつ、Pro 固有の以下を追記:
  - 「Pro リポの変更は公開してはならない」
  - 「Community のコードを直接編集してはならない(依存先として参照のみ)」
  - 「LICENSE 配布条項の変更は法務確認必須」
  - ビルド・テストコマンド(`./gradlew :app-pro:shadowJar` 等)

### 6. .gitignore

Community の `.gitignore` をベースに以下追加:
- `*.license` / `*.key`(ライセンスキー流出防止)
- `.ful/`(実行成果物)

### 7. baseline/

取り込み元の baseline ファイル(あれば)を移管。なければ初回ビルド時に生成する旨を `docs/INTEGRATION.md` に記載。

## 実行手順

1. **取り込み元の構造を読み取る**
   - `/home/wada/workspaces/fulcraft/app/src/main/java/com/craftsmanbro/fulcraft/plugins/junit/` 配下の Java ファイル一覧を取得
   - `/home/wada/workspaces/fulcraft/app/src/main/resources/prompts/` 配下のファイル一覧を取得
   - Community 側 (`/home/wada/dist/workspaces/fulcraft`) と差分があるファイルを特定

2. **Community 側のバージョン情報を取得**
   - `app/build.gradle.kts` から version, Java version, 依存バージョンを抽出

3. **fulcraft-pro リポを `/home/wada/dist/workspaces/fulcraft-pro` に新規作成**
   - `git init` は人間が実行する前提。Claude はファイル生成のみ
   - 上記成果物セクションのファイル群を生成
   - 取り込み元から `plugins/junit/` と `prompts/` をコピー(Java の `package` 宣言は変更不要、SPI 経由でロードされる)

4. **動作確認手順を `docs/INTEGRATION.md` にまとめる**
   - Community を `./gradlew publishToMavenLocal` する
   - Pro を `./gradlew :app-pro:shadowJar` する
   - `java -jar app-pro/build/libs/ful-pro-*.jar run --steps SELECT,GENERATE,REPORT ...` で JUnit ステップが見えることを確認
   - SPI 経由でプラグインがロードされていることをログで確認

5. **コミット相当のチェックリストを `docs/INTEGRATION.md` 末尾に記載**
   - [ ] LICENSE 法務レビュー依頼
   - [ ] Community のリリース版にバージョン固定
   - [ ] GitHub Packages(または mavenLocal)への publish 動線整備
   - [ ] CI(GitHub Actions)テンプレ作成
   - [ ] 展示会向け 30 日トライアルビルド設定

## 制約

- **Community 側のファイルは一切編集しない**。参照のみ。
- **ライセンスキー検証機構(JWT/署名)は本タスクではスコープ外**。`docs/LICENSE_KEY.md` に将来計画として記載するのみ。
- **コードコメントは英語、ドキュメントは日本語**(Community CLAUDE.md と同様)。
- **取り込み元の `ExploreFlow.java` / `AnalysisVisualReportWriter.java` は移管しない**。Community 側の最新が正。
- **`RunCommand.java` は完全移管せず**、JUnit 関連の引数差分のみ Pro 側で SPI 経由オーバーライドする設計(実装スケルトンのみで OK、詳細は後続タスク)。
- **`plugins/console-pro/` は空スケルトン**(README とパッケージ階層のみ)。実装は後続タスク。

## 完了条件

1. `/home/wada/dist/workspaces/fulcraft-pro/` 配下に上記成果物が揃っている
2. `docs/INTEGRATION.md` を読めば人間がビルドを通せる
3. Community 側のファイルは 1 バイトも変更されていない
4. 取り込み元 (`/home/wada/workspaces/fulcraft`) も改変していない(読み取りのみ)

着手前に成果物一覧と取り込み元のファイル数(特に `plugins/junit/` のファイル数と総 LOC)を報告し、Plan Mode で計画を提示してください。
````

---

## このプロンプトの使い方

1. 新しい Claude Code セッションを開く
2. 上記コードブロック内(` ```markdown ` から閉じ ` ``` ` まで)をコピペして投入
3. Plan Mode で計画レビュー → 承認 → 実装
4. 完了後、`/home/wada/dist/workspaces/fulcraft-pro/` を `git init` して private リポへ push(人間操作)

## 補足: このプロンプトでカバーしない事項

以下は別タスク扱い:

- **WebView 基盤の実装**(`webview/` Gradle サブモジュール、Javalin/Spark、SPA) → Community 側で先行着手
- **JUnit 特化 WebView 画面**(テストカード、プロンプト切替比較等) → Pro リポに `plugins/console-pro/` として後続実装
- **ライセンスキー検証機構**(JWT 署名 + 公開鍵検証) → M2 では任意。導入する場合は別プロンプト
- **30 日トライアルビルド**の透かし機構 → 展示会前に別タスク
- **GitHub Packages 公開設定**の認証情報 → 人間が手動設定
