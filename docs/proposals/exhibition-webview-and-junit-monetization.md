# 展示会向け WebView 管理画面 / JUnit 統合 / 有料化モデル 検討まとめ

> 作成: 2026-05-13
> 対象リポジトリ: `/home/wada/dist/workspaces/fulcraft`
> 取り込み元 (JUnit): `/home/wada/workspaces/fulcraft`
> ステータス: 構想（実装着手前）

---

## 0. ゴール

1. 展示会で fulcraft を視覚的に魅せるための **Web 管理画面 (FUL Console)** を実装する
2. JUnit 生成プラグイン（別リポにある実装）を本リポに統合し、Console から扱えるようにする
3. JUnit 機能を **Pro（有料）** として切り出すためのコード配置 / ライセンス / 配布戦略を確定する

---

## 1. 現状機能棚卸（展示会観点）

| 領域 | 現状 | 展示会での課題 |
|---|---|---|
| 入口 | CLI (`ful run/analyze/explore/report/document`) | 黒い画面はインパクト弱い |
| 対話UI | Lanterna 製 TUI (`MainMenuScreen`, `ConfigEditorScreen`, `ExecutionRunningScreen` 等) | 端末必須・遠目で見えない |
| 解析エンジン | JavaParser + Spoon、複雑度／重複／デッドコード／サイクル検出 | グラフ系の見せ場が眠っている |
| LLM | OpenAI / Anthropic / Gemini / Azure / Vertex / Bedrock / Ollama 切替 | 切替の凄さが伝わらない |
| 出力 | `.ful/runs/<runId>/{analysis,report,docs,logs}/`、`analysis_visual.html` は既にビジュアル化 | 1 Run = 1 ファイル、複数 Run の俯瞰や履歴比較ができない |
| パイプライン | `ANALYZE → DOCUMENT → REPORT → EXPLORE` DAG、Resilience4j / OpenTelemetry | 内部で何が動いているか観客に見えない |
| 設定 | `config.json` 手動編集 + `config.schema.json` | デモ中に切替えづらい |

**結論**: 解析エンジン・LLM 多段・パイプラインなど裏側は強いが、観客の動線が CLI/TUI 止まりで刺さりにくい。

---

## 2. FUL Console（Web 管理画面）構想

### 2.1 コンセプト
> **「Fulcraft の司令塔」** — 解析を 起動する／可視化する／比較する／LLM を使い分ける、すべてを 1 画面に。

CLI/TUI は温存（CI・開発者用途）、WebView はデモと運用ダッシュボードを兼ねる位置付け。

### 2.2 画面（Tier 別）

#### Tier-1: Minimum（必須）
1. **Run 履歴ダッシュボード** — `.ful/runs/*` を一覧化（日時、対象、Step、所要時間、LLM、コスト）。行クリックで既存 `analysis_visual.html` / `report.md` を埋め込み表示
2. **新規 Run 起動フォーム** — プロジェクト・対象パッケージ・Step・LLM プロバイダを GUI 選択 → 裏で `ful run` を subprocess 実行
3. **ログ・ストリーミング** — 実行中の `logs/` を WebSocket/SSE で配信

#### Tier-2: Standard（展示映え）
4. **複雑度ヒートマップ & 依存グラフ** — パッケージ→クラス→メソッドのドリルダウン、サイクル赤・デッドコード薄色
5. **Run 間 Diff ビュー** — 2 Run を並べて Hotspot/Risky/カバレッジ差分を表示（リファクタ前後デモに最適）
6. **設定エディタ** — `config.schema.json` から自動生成、既存 TUI `ConfigEditorScreen` の Web 移植
7. **LLM プロバイダ・スイッチャー** — 同一対象を 3 プロバイダ並列実行 → ドキュメント品質を 3 カラム比較

#### Tier-3: Wow（来場者を立ち止まらせる）
8. **パイプライン・ライブビュー** — DAG ノードが脈打つアニメーション、OpenTelemetry スパンをそのまま視覚化
9. **AI Q&A ペイン** — EXPLORE 強化、コード該当行ハイライト付き応答
10. **品質スコアカード（KPI 大画面）** — 大型ディスプレイ向けリアルタイム KPI

### 2.3 アーキテクチャ選択

| 案 | 概要 | 工数 | 推奨度 |
|---|---|---|---|
| **A. JVM 内 Javalin/Spark** | `ful serve` サブコマンド、`ui/web/` パッケージ新設 | 中 | ★★★ |
| B. 別プロセス Next.js + REST | フロント自由だが連携増 | 大 | ★★ |
| C. 静的サイト強化のみ | report HTML を強化 | 小 | ★ |

**推奨は A**。理由：既存の `kernel/pipeline`, `ui/cli/wiring`, `infrastructure/llm` を注入可能。新規 `plugins/web/`（HTTP アダプタ）として閉じれば、CLAUDE.md の「layer structure」「pipeline は plugins を参照しない」原則を維持できる。

### 2.4 ディレクトリ案

```
ui/web/                                # Javalin ルーティング、SSE、静的配信
plugins/console/                       # Run 集約・Diff 計算ロジック（core/contract/adapter）
app/src/main/resources/web/            # SPA ビルド成果物（Vite + Vue/React）
```

### 2.5 展示会特有の追加考慮

- **キオスクモード**: ESC で操作ロック解除、来場者操作後にデモプロジェクト状態を自動巻き戻す
- **オフライン耐性**: Ollama (`provider=local`) をフォールバック設定
- **API キー保護**: `.env` ベース、画面非表示（CLAUDE.md セキュリティ方針準拠）
- **多言語**: 既存 `i18n/` で JA/EN 切替
- **触らせる仕掛け**: タッチパネル前提のボタンサイズ

---

## 3. 同一リポ vs 別リポ（WebView 単体）

結論：**「同一リポジトリ・別 Gradle サブモジュール」を推奨**。

| 観点 | 同一リポ（推奨） | 別リポ |
|---|---|---|
| 配布 | `ful` 1 コマンド・1 ZIP で完結 | バージョン同期テストが必要 |
| 契約共有 | `config.schema.json` / Run JSON をビルド時参照可 | 型生成 or 手動同期でドリフトする |
| リリース | 1 タグで `ful-X.Y.Z.zip` | フロント・バックの version skew |
| ライセンス | プロプライエタリ一本（LICENSE と一致） | 別ライセンス可 |
| CI | 既存 Gradle/Spotless/Checkstyle/SpotBugs/OWASP に乗る | 別パイプライン必要 |

### 推奨構成

```
fulcraft/
├── app/                    # 既存（JVM）
├── webview/                # 新規 Gradle サブプロジェクト
│   ├── build.gradle.kts    # com.github.node-gradle.node プラグイン
│   ├── package.json        # Vite + React/Vue
│   ├── src/
│   └── tsconfig.json
├── settings.gradle.kts     # include("app", "webview")
```

ビルドフロー：
1. `:webview:npmBuild` → `webview/dist/` 生成
2. Gradle タスクで `app/src/main/resources/web/` にコピー
3. `:app:shadowJar` が SPA を fat JAR に同梱
4. Javalin/Spark で `/` から静的配信 ＝ `ful serve` 単一バイナリ配布

### 注意点

- `.gitignore` に `webview/node_modules`, `webview/dist`, `app/src/main/resources/web/` 追加
- Spotless/Checkstyle 対象から `webview/` 除外
- OWASP dependency-check と並走で `npm audit` を 1 ジョブ追加
- ESLint/Prettier は `webview/` 内で完結
- `dependencyCheckAnalyze` のヒープ（4g）はそのまま、npm 側は別ジョブ

---

## 4. JUnit 生成プラグイン取り込み（差分の実体）

### 4.1 差分（本リポ ← `/home/wada/workspaces/fulcraft`）

| 項目 | 内容 | 規模 |
|---|---|---|
| **新規プラグイン** | `plugins/junit/` (`command/` + `suite/{select,generate,report,shared}/`) | 201 Java / 約 39k LOC |
| **SPI 登録追記** | `META-INF/services/...ActionPlugin` に 3 行追加 (`JUnitSelectPlugin` / `JUnitGeneratePlugin` / `JUnitBrittleCheckPlugin`) | 3 行 |
| **プロンプト** | `resources/prompts/` に `default_generation.txt`, `default_fix.txt`, `default_runtime_fix.txt`, `high_complexity_generation.txt`, `split_phase{1,2,3}_*.txt`, `fallback_stub.txt`, `few_shot/` | 8 ファイル + 1 ディレクトリ |
| **CLI 修正** | `ui/cli/command/RunCommand.java` 差分（GENERATE ステップ露出） | 1 ファイル |
| **その他差分** | `plugins/exploration/flow/ExploreFlow.java`, `plugins/reporting/adapter/AnalysisVisualReportWriter.java` | 2 ファイル（本リポ側を優先） |
| **既存パリティ** | `config/junit/` は同一 ✓ | — |

### 4.2 注意点

- 本リポは git 履歴上 `plugins/junit` を一度も持っていない（過去削除ではなく別系統リポ由来）
- `RELEASE.md` / `README.md` の特性表に JUnit 生成が含まれていない → 文書更新が必要
- `ExploreFlow.java` / `AnalysisVisualReportWriter.java` は直近の `9a34eee` で本リポ側だけ進んでいる → JUnit 統合では**上書きしない**

### 4.3 統合方針（3 フェーズ）

#### Phase A — JUnit プラグインの取り込み

1. コピー対象：`plugins/junit/` 一式、`prompts/` 8 ファイル + `few_shot/`
2. SPI 追記（既存 4 行は維持、3 行追加）
3. `RunCommand.java`：本リポをベースに `--steps SELECT,GENERATE,REPORT` の引数差分のみチェリーピック
4. テスト移植：相手リポの `test`, `integrationTest`, `e2eTest` から `junit/` 配下
5. `baseline/` 更新（Checkstyle/SpotBugs/PMD の新規違反分）
6. **DO NOT TOUCH**: `ExploreFlow.java` / `AnalysisVisualReportWriter.java`

#### Phase B — WebView 基盤（Tier-1+2、JUnit 拡張前提で API 設計）

```
GET  /api/runs                          # 履歴一覧
GET  /api/runs/:id/analysis             # 既存
GET  /api/runs/:id/report               # 既存
GET  /api/runs/:id/docs                 # 既存
POST /api/runs                          # 起動: steps[] に SELECT/GENERATE 含可

# JUnit 専用
GET  /api/runs/:id/junit/selected       # SelectPlugin の出力
GET  /api/runs/:id/junit/generated      # 生成テストファイル一覧
GET  /api/runs/:id/junit/file?path=...  # 生成テストのソース
GET  /api/runs/:id/junit/results        # コンパイル/実行結果
SSE  /api/runs/:id/stream               # ライブログ
POST /api/runs/:id/junit/regenerate     # 失敗メソッドだけ再生成
```

#### Phase C — JUnit 特化の WebView 画面（展示の主役）

1. **Select → Generate → Verify ライブパイプライン** — クラスツリー + 選定理由 + 生成中アニメ + 差分表示
2. **テストカード** — 1 メソッド = 1 カード（`@Test` 名、コンパイル結果、Pass/Fail、self-heal 履歴、Brittle 警告）
3. **プロンプト切替デモ** — `default_generation` vs `high_complexity_generation` vs `split_phase{1,2,3}` 並列比較、`few_shot/` 有無トグル
4. **LLM × 戦略マトリクス** — 行 = LLM、列 = 複雑度戦略 (warn/skip/split/specialized_prompt)、セルに成功率・コスト・所要時間
5. **カバレッジ Before/After** — JaCoCo 連携、複雑度ヒートマップに重ね表示
6. **自己治癒ビュー** — コンパイル失敗 → `default_fix.txt` 起動 → 再コンパイル のループをアニメ化

### 4.4 実行順

| 段階 | 内容 | 期間目安 |
|---|---|---|
| 0 | 取り込み計画レビュー | — |
| 1 | Phase A：取り込み・テスト緑化・非破壊確認 | 2–3 日 |
| 2 | `webview/` 立ち上げ＋Run 履歴/起動 API | 1 週 |
| 3 | JUnit 専用 API + テストカード/差分ビュー | 1 週 |
| 4 | プロンプト切替・LLM 比較・自己治癒ビュー | 1–2 週 |
| 5 | Before/After カバレッジ＋キオスクモード | 1 週 |

---

## 5. JUnit 有料化のためのコード配置／ライセンス／配布

### 5.1 現状ライセンス前提

本リポ `LICENSE` の特性（README より）：
- **Source Available / Proprietary**
- 個人・学生・非商用：自環境利用のみ可
- 法人・商用：**原則禁止**（書面許可必須）
- 改変・再配布：禁止

つまり「無料」ではなく**既に有料モデル**。JUnit 追加は「無料 vs 有料」ではなく **「ベース有料」 vs 「Pro 有料（更に高額／別契約）」** の二段階。

### 5.2 3 モデル比較

| | M1: 同一リポ + ライセンスキーゲート | M2: 別リポ + ビルド時バンドル | M3: 別リポ + ランタイムプラグイン |
|---|---|---|---|
| ソース露出 | JUnit コードが本リポに露出 | 完全分離（買った人だけ読める） | 完全分離 |
| 配布物 | 1 JAR、キーで機能アンロック | `ful-community.zip` / `ful-pro.zip` の 2 系統 | base JAR + plugin JAR を別配布 |
| 開発体験 | ◎ 単一 PR / CI | △ クロスリポ PR | △ SPI 契約維持コスト |
| 展示会デモ | ◎ 同一バイナリでキー差し替え | ◎ Pro 版を持参 | ○ Plugin 同梱 |
| IP プロテクション | △ 法的のみ | ◎ 物理 + 法的 | ◎ 物理 + 法的 |
| 将来の OSS 化柔軟性 | × ベースだけ OSS 化困難 | ◎ Community 側を MIT 化可 | ◎ 同上 |
| キー検証実装コスト | 必要 | 任意 | 任意 |
| 海賊版リスク | 高 | 低 | 中 |

参考事例：
- **M1**: JetBrains IDE、Docker Desktop
- **M2**: GitLab CE/EE、Sentry OSS/SaaS、HashiCorp Terraform/TFE
- **M3**: Grafana + Enterprise plugins、Elasticsearch + X-Pack（旧）

### 5.3 推奨：M2（別リポ＋ビルド時バンドル）

理由：

1. **既存ライセンス文言と整合** — 「Modification 禁止／Redistribution 禁止」は本来バイナリ前提の条文。ソース公開状態だと弱い保護にしかならない
2. **値付けが明確** — Pro 契約 = ZIP ダウンロード権、で物理的に表現できる（監査・経理が単純）
3. **退却路がある** — 将来 Community 側だけ OSS 化に踏み切りたくなったときに JUnit を引きずらない
4. **展示会との両立** — Pro 版バイナリを持参してデモ、来場者には Community ZIP の QR コード配布で商談導線になる

### 5.4 リポ構成（M2）

```
github.com/AI-Craftsman-Brotherhood/fulcraft           ← 公開（現状）
  └─ Community: analyze + report + document + explore + WebView(基本)

private/fulcraft-pro                                    ← 非公開
  ├─ plugins/junit/                                     ← 取り込み元の中身
  ├─ plugins/console-pro/                               ← Pro 限定 WebView 機能
  ├─ build.gradle.kts: dependencies { implementation("com.craftsmanbro:fulcraft-app:X.Y.Z") }
  └─ shadowJar → ful-pro-X.Y.Z.jar（Community + Pro を内包）
```

ビルド依存：
- Community を `mavenLocal()` または GitHub Packages（private registry）に publish
- Pro 側は Maven 依存として参照、`shadowJar` で全部入りビルド
- SPI 経由で JUnit プラグインが自動ロード（kernel 側コード変更不要）

### 5.5 配布

| 製品 | 中身 | 価格モデル |
|---|---|---|
| Community ZIP | analyze/report/document/explore + WebView 基本 | 個人=無料、商用=年額 |
| **Pro ZIP** | + JUnit 生成 + WebView Pro 機能 + サポート | 商用のみ・別料金 |
| 展示会デモビルド | Pro + 30 日トライアル透かし | 配布なし（QR で問い合わせ誘導） |

### 5.6 WebView の Tier 分割

| 機能 | Community | Pro |
|---|---|---|
| Run 履歴・起動・ログ | ✓ | ✓ |
| 依存グラフ・複雑度ヒートマップ | ✓ | ✓ |
| 既存 HTML レポート閲覧 | ✓ | ✓ |
| JUnit テストカード | — | ✓ |
| プロンプト切替比較 | — | ✓ |
| LLM × 戦略マトリクス | — | ✓ |
| Before/After カバレッジ | — | ✓ |
| 自己治癒ビュー | — | ✓ |
| AI Q&A（チャット） | 制限付き（回数/日） | 無制限 |

### 5.7 M1 でも実害が小さい条件

以下に**全部**当てはまるなら M1（同一リポ）でも可：
- 数年単位で OSS 化する予定が無い
- 顧客がエンタープライズ寡占で、契約書ベースの統制が効く
- 開発リソースが極小で、別リポ CI 運用コストを払えない
- ライセンスキー機構を作る覚悟がある（JWT 署名 + 公開鍵検証、`infrastructure/security/` に同居）

### 5.8 判断フロー

```
① 1 年以内に展示会で来場者にバイナリを渡す予定がある？
   Yes → M2 / M3 ほぼ確定
   No  → ②へ

② 3 年以内に Community 部分の OSS 化を検討する？
   Yes → M2 強く推奨
   No  → ③へ

③ ライセンスキー検証機構（JWT/署名）を実装・保守できる体制がある？
   Yes → M1 でも可
   No  → M2
```

---

## 6. 決定事項リスト（次セッションで合意したい）

1. **コード配置モデル**: M1 / M2 / M3 のいずれを採用するか
2. **Pro リポの扱い**: 新規 private リポを切るか、既存 `/home/wada/workspaces/fulcraft` を Pro リポに昇格させるか
3. **取り込み元コミット**: HEAD 固定か、特定タグ／コミット指定か
4. **ライセンス整合**: 取り込み元リポも同じ FUL License か、社内別ライセンスか（混在 NG）
5. **`ExploreFlow.java` / `AnalysisVisualReportWriter.java`**: 本リポ側保持で確定して良いか
6. **既存 baseline**: JUnit プラグイン分の違反を新規ベースラインとして許容するか、即時修正するか
7. **WebView の Tier 境界**（§5.6 の表）: 確定か、さらに細分化するか

---

## 7. 参考：取り込み元の JUnit プラグイン構造

```
plugins/junit/
├── command/
│   ├── GenerateCommand.java
│   ├── ReportCommand.java
│   └── SelectCommand.java
└── suite/
    ├── select/
    │   ├── adapter/ context/ contract/ core/ flow/ io/ model/ stage/
    │   └── JUnitSelectPlugin.java
    ├── generate/
    │   ├── adapter/ config/ contract/ core/ flow/ io/ model/ stage/
    │   ├── JUnitGeneratePlugin.java
    │   └── JUnitBrittleCheckPlugin.java
    ├── report/
    │   └── adapter/ config/ contract/ core/ flow/ io/ model/ stage/
    └── shared/
        ├── result/ task/
        └── JUnitPluginSupport.java
```

LOC: 201 Java ファイル / 約 39,000 行
