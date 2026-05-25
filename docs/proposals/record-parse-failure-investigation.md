# fulcraft 解析エンジン Java Record パース失敗 調査レポート

| 項目             | 内容                                                       |
| ---------------- | ---------------------------------------------------------- |
| 作成日           | 2026-05-20                                                 |
| 対象ブランチ     | `feat/improve-analysis-visual-report` (commit `b3d080e`)   |
| 解析エンジン     | fulcraft `app/` モジュール                                 |
| ステータス       | 調査完了 / 修正未実施（提案レポート）                      |
| 影響範囲         | Java 14+ 機能を使用したコードベース全般                    |
| 想定深刻度       | **High**（解析カバレッジが大幅低下し、解析 JSON が欠落）   |

---

## 0. エグゼクティブサマリ（先に結論）

- **根因**: `JavaParserAnalyzer.createParser()`（メインの JavaParser ファクトリ）が
  `ParserConfiguration` を `setLanguageLevel(...)` 未指定で生成しているため、
  既定の `POPULAR`（Java 8/9 相当）でパースされ、`record`／`sealed`／パターンマッチング
  などの **Java 14+ 構文を全て認識できない**。
- **影響**: Composite エンジン経由で JavaParser が失敗し、該当ファイルの
  `analysis_errors` だけが積み上がる。結果として型解決率が 73% まで低下、
  Record を含むファイルの `analysis_*.json` が生成されない。
- **整合性**: 同じリポジトリ内で **Spoon は `setComplianceLevel(17)`、フォーマッタ系の
  JavaParser は `BLEEDING_EDGE`** を設定済み。解析パスだけが取り残されている。
- **短期修正**: `JavaParserAnalyzer.createParser()` の `ParserConfiguration` に
  `setLanguageLevel(LanguageLevel.JAVA_21)` を 1 行追加するだけで再現症状は解消可能。
- **中期修正**: `analysis.language_level` を `config.json` スキーマに追加し、
  Spoon と JavaParser の双方に注入できる単一ノブを公開する。
- **再発防止**: Record / sealed / pattern matching を含むフィクスチャを
  `JavaParserAnalyzer` 系の統合テストに追加し、`POPULAR` への退行を CI で検出する。

---

## 1. 事象再現の確認結果

### 1.1 ユーザー報告

- 解析対象: 外部 Java 21 プロジェクト（Record / sealed / pattern matching を使用）
- コマンド:
  ```bash
  $FUL_HOME/scripts/ful analyze \
    -d src/main/java/com/example/sample --exclude-tests
  ```
- 症状:
  - Quality Score 68 / 100
  - Type Resolution Rate 73.0%（Unresolved 948 / 3519）
  - `.ful/runs/.../analysis/com/example/sample/analysis_SampleRepository.json` の
    `analysis_errors` に **`Record Declarations are not supported. ... 'JAVA_14' language level`**
    が多数記録
  - Record 値オブジェクトを含む各 `analysis_*.json` が生成されず（解析結果から欠落）

### 1.2 エラー文言と JavaParser のソース

JavaParser 3.25.x はパース時に `LanguageLevel` を強制し、未満の構文をエラー化する。
ユーザーが目撃したメッセージは JavaParser 内部の `LanguageLevelValidator` 系から
そのまま出ているもので、出典は明確：

```
[(line 19,col 1) Record Declarations are not supported.
 Pay attention that this feature is supported starting from 'JAVA_14' language level.
 If you need that feature the language level must be configured in the configuration
 before parsing the source files.]
```

→ **JavaParser 側にのみ起きる**事象。Spoon 由来のエラーではない。

### 1.3 ユーザー設定の確認

対象プロジェクトの `config.json` に `analysis.engine` が **未指定**。
このため、CLI ワイヤリング (`DefaultServiceFactory#createDefaultAnalysisPort`) は
`CompositeAnalysisPort`（JavaParser + Spoon 両方実行）を選択する。
Composite 配下の JavaParser パスが Record で失敗し、`analysis_errors` を積む。

---

## 2. JavaParser 初期化箇所一覧

`grep -rn "new ParserConfiguration\|new JavaParser\b\|LanguageLevel" app/src/main/java` の結果と
ソースコードレビューを突き合わせた一覧。

### 2.1 `LanguageLevel` を **明示設定している** 箇所（安全）

| # | ファイル | 行 | 設定値 | 用途 |
| - | - | - | - | - |
| A1 | `infrastructure/formatter/impl/TestCodeFormatter.java` | 42–45 | `BLEEDING_EDGE` | 生成テストコードのフォーマット |
| A2 | `infrastructure/parser/impl/javaparser/JavaParserBrittleTestChecker.java` | 31–33 | `BLEEDING_EDGE` | brittle test の構文解析 |
| A3 | `plugins/analysis/core/service/metric/BranchSummaryExtractor.java` | 88–90 | `BLEEDING_EDGE` | 分岐サマリ抽出 |

### 2.2 `LanguageLevel` **未設定** の箇所（潜在バグ）

| # | ファイル | 行 | 構築方法 | 影響度 |
| - | - | - | - | - |
| B1 | `infrastructure/parser/impl/javaparser/JavaParserAnalyzer.java` | **229** | `new ParserConfiguration()` のみ。`setSymbolResolver` だけ呼び `setLanguageLevel` を呼ばない | **致命的（メイン analyze 経路）** |
| B2 | `infrastructure/parser/impl/javaparser/JavaParserAnalyzer.java` | 337 | B1 で得た `ParserConfiguration` を `new JavaParser(config)` に再利用 | B1 と同根（症状の表れ場所） |
| B3 | `infrastructure/parser/impl/javaparser/CodeValidator.java` | 313 | `new JavaParser().parse(code)` | 中（生成コード検証で Record を含むとパース失敗） |
| B4 | `infrastructure/cache/impl/CacheRevalidator.java` | 182 | `new JavaParser().parse(codeWithoutFences)` | 中（キャッシュ再検証で Record スニペット未対応） |
| B5 | `plugins/analysis/core/service/dynamic/DynamicResolver.java` | 152 | `private final JavaParser javaParser = new JavaParser();` | 中（動的解決で Record 含む式が落ちる） |
| B6 | `plugins/analysis/core/service/analyzer/CommonMethodAnalyzer.java` | 82 | `new JavaParser().parse(sourceCode)` | 中（メソッド解析サブ経路） |
| B7 | `plugins/analysis/core/service/index/ProjectSymbolIndexBuilder.java` | 32 | `private final JavaParser parser = new JavaParser();` | 中（シンボル索引構築） |

`AbstractJavaParserBrittleRule` 系のルールは `JavaParserBrittleTestChecker` から
渡された `JavaParser` を共有するため A2 経由で `BLEEDING_EDGE`。一覧から除外。

### 2.3 JavaParser のデフォルト `LanguageLevel`

JavaParser 3.25.7（`gradle/libs.versions.toml:5`）の `ParserConfiguration` デフォルトは
`LanguageLevel.POPULAR`。`POPULAR` は概ね **Java 8〜9** をターゲットとし、
Records（Java 14 preview / 16 GA）、sealed（17）、パターンマッチング for switch（21）、
text block の一部、Unnamed patterns / `_` の新用法などを**サポートしない**。
B1〜B7 はすべて Java 14+ 機能を含むファイルで例外を投げる潜在経路。

---

## 3. 設定経路の現状図

```
config.json (analysis.engine 未指定)
        │
        ▼
DefaultServiceFactory.createAnalysisPort(null)              ※ ui/cli/wiring
        │
        ▼
createDefaultAnalysisPort()  →  CompositeAnalysisPort
        │                                  │
        │                                  ├─ JavaParserAnalysisAdapter
        │                                  │     └─ JavaParserAnalyzer
        │                                  │           └─ createParser()    ← ★ B1
        │                                  │                 new ParserConfiguration()
        │                                  │                 // setLanguageLevel なし
        │                                  │
        │                                  └─ SpoonAnalysisAdapter
        │                                        └─ SpoonAnalyzer
        │                                              └─ buildModel()
        │                                                    launcher.setComplianceLevel(17)  ← OK
        ▼
analysis_errors[] に Record パース失敗が蓄積
ファイル単位の analysis_*.json が JavaParser 経路では生成されない
（Spoon 経路は別系統だが現状 per-file JSON は JavaParser 起点）
```

### 3.1 注意すべきデフォルト戦略の不一致

| 経路                               | デフォルトエンジン | 出典                                              |
| ---------------------------------- | ------------------ | ------------------------------------------------- |
| `DefaultServiceFactory`（CLI ワイヤ） | `COMPOSITE`        | `DefaultServiceFactory.java:67–69`               |
| `AnalysisPortFactory`（静的ヘルパー） | `SPOON`            | `AnalysisPortFactory.java:19`, `:111–112`         |

実際の `ful analyze` は CLI 経由なので **Composite** が走る。JavaParser を回避するだけ
であれば `analysis.engine: "spoon"` を `config.json` に追加することで応急回避が可能。
ただし、Composite が選ばれる前提でレポーティング指標が組まれている可能性があり、
本質対応は **JavaParser 側の言語レベルを正す** こと。

### 3.2 `language_level` を露出する設定キーの不在

- `app/src/main/resources/config.schema.json` 内に
  `grep -n "language\|Level\|JAVA_2\|JAVA_1"` で **ヒット 0 件**。
- `AnalysisConfig` には `engine`, `spoon.no_classpath`, `source_charset`,
  `classpath.mode`, `preprocess.*`, `external_config_resolution` などはあるが、
  **`language_level` / `java_version` に相当するフィールドは存在しない**。
- 環境変数や CLI フラグ経由の言語レベル上書き経路も無い
  （`Main` / Picocli コマンド定義を走査済み、該当オプション無し）。

つまり現状は **完全ハードコード** で、利用者側からは是正不能。

---

## 4. 推奨修正

### 4.1 短期修正 A（即時 / 最小差分）

**目的**: 解析エンジンを Java 21 に追随させ、Record/sealed/パターンマッチを通す。

**ファイル**: `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/parser/impl/javaparser/JavaParserAnalyzer.java`

```diff
@@ private JavaParser createParser(final Path srcPath, final Path projectRoot, final Config config) {
     final CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
     combinedTypeSolver.add(new ReflectionTypeSolver());
     combinedTypeSolver.add(
         new com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver(
             srcPath));
     addDependencyJarsToTypeSolver(combinedTypeSolver, projectRoot, config);
     final com.github.javaparser.symbolsolver.JavaSymbolSolver symbolSolver =
         new com.github.javaparser.symbolsolver.JavaSymbolSolver(combinedTypeSolver);
     final ParserConfiguration parserConfiguration = new ParserConfiguration();
+    // Match Spoon's compliance level (17) at minimum so Records/sealed/pattern-matching
+    // are recognized. JavaParser's default POPULAR (~Java 9) breaks on Java 14+ syntax.
+    parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
     parserConfiguration.setSymbolResolver(symbolSolver);
     return new JavaParser(parserConfiguration);
   }
```

備考:
- `JAVA_21` を選んだ理由: Spoon 側が `complianceLevel=17` で運用されており、
  解析対象が Java 21 プロジェクトであることを想定。`BLEEDING_EDGE` でも動作するが、
  バージョンを跨いだ実験的構文の挙動差を避けるため LTS 固定が無難。
- `JavaParserAnalyzer.java:337` の `new JavaParser(config)` は `config` を
  `getParserConfiguration()` から取り直すため、上記 1 行で自動的に伝播する。

### 4.2 短期修正 B（同パス系の取りこぼし防止）

**目的**: B3〜B7 の ad-hoc `new JavaParser()` 経路も Record 構文を許容させる。

#### B3 `CodeValidator.java`

```diff
- return new JavaParser().parse(code);
+ return new JavaParser(
+         new ParserConfiguration()
+             .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21))
+     .parse(code);
```

#### B4 `CacheRevalidator.java`

```diff
- final ParseResult<CompilationUnit> parseResult = new JavaParser().parse(codeWithoutFences);
+ final ParseResult<CompilationUnit> parseResult =
+     new JavaParser(
+             new ParserConfiguration()
+                 .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21))
+         .parse(codeWithoutFences);
```

#### B5 `DynamicResolver.java`

```diff
- private final JavaParser javaParser = new JavaParser();
+ private final JavaParser javaParser =
+     new JavaParser(
+         new ParserConfiguration()
+             .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
```

#### B6 `CommonMethodAnalyzer.java`

```diff
- final var parseResult = new JavaParser().parse(sourceCode);
+ final var parseResult =
+     new JavaParser(
+             new ParserConfiguration()
+                 .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21))
+         .parse(sourceCode);
```

#### B7 `ProjectSymbolIndexBuilder.java`

```diff
- private final JavaParser parser = new JavaParser();
+ private final JavaParser parser =
+     new JavaParser(
+         new ParserConfiguration()
+             .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
```

> 同じパターンが 5 箇所で繰り返されているのは中期修正で集約したい
> （後述 4.3 のファクトリ化）。

### 4.3 中期修正（設定スキーマの拡張 + ファクトリ集約）

**目的**: Spoon と JavaParser を **同一の "Java 言語レベル"** ノブで揃え、
利用者が `config.json` から制御できるようにする。

#### 4.3.1 設定スキーマ拡張

`app/src/main/resources/config.schema.json` の `analysis` セクションに以下を追加:

```jsonc
"language_level": {
  "type": "string",
  "description": "Source Java language level used by both Spoon and JavaParser. Default: JAVA_21.",
  "enum": ["JAVA_8", "JAVA_11", "JAVA_17", "JAVA_21", "BLEEDING_EDGE"],
  "default": "JAVA_21"
}
```

`AnalysisConfig.java` に対応するフィールド:

```java
@JsonProperty("language_level")
private String languageLevel = "JAVA_21";

public String getLanguageLevel() {
  return valueOrDefault(languageLevel, "JAVA_21");
}
```

#### 4.3.2 ファクトリ集約

新規ユーティリティ `infrastructure/parser/impl/javaparser/JavaParserFactory.java`
を導入し、`ParserConfiguration` 作成を一元化:

```java
public final class JavaParserFactory {
  private JavaParserFactory() {}

  public static ParserConfiguration newConfiguration(final Config config) {
    final ParserConfiguration pc = new ParserConfiguration();
    pc.setLanguageLevel(resolveLanguageLevel(config));
    return pc;
  }

  public static JavaParser newParser(final Config config) {
    return new JavaParser(newConfiguration(config));
  }

  private static ParserConfiguration.LanguageLevel resolveLanguageLevel(final Config config) {
    if (config == null || config.getAnalysis() == null) {
      return ParserConfiguration.LanguageLevel.JAVA_21;
    }
    return switch (config.getAnalysis().getLanguageLevel().toUpperCase(Locale.ROOT)) {
      case "JAVA_8"  -> ParserConfiguration.LanguageLevel.JAVA_8;
      case "JAVA_11" -> ParserConfiguration.LanguageLevel.JAVA_11;
      case "JAVA_17" -> ParserConfiguration.LanguageLevel.JAVA_17;
      case "BLEEDING_EDGE" -> ParserConfiguration.LanguageLevel.BLEEDING_EDGE;
      default        -> ParserConfiguration.LanguageLevel.JAVA_21;
    };
  }
}
```

Spoon 側も同じ `language_level` を解釈し、`launcher.getEnvironment().setComplianceLevel(...)`
に変換するヘルパー（例: `SpoonComplianceLevels#fromConfig`）を `SpoonAnalyzer` で利用。
**ノブが 1 つになるので Spoon/JavaParser のドリフトを起こさない**。

#### 4.3.3 既存呼び出しの移行

B1〜B7 と A1〜A3 を `JavaParserFactory.newParser(config)` 経由に統一。
`Config` を受け取れない短命オブジェクト（テスト用フォーマッタなど）は
`JavaParserFactory.newParser()` の引数なしオーバーロード（既定: JAVA_21）を使う。

### 4.4 即時の利用者側回避策（修正前にユーザーが取れる手段）

| 手段 | 期待効果 | 副作用 |
| - | - | - |
| `config.json` に `"analysis": { "engine": "spoon" }` を追加 | JavaParser 経路を完全に外し、Spoon の `complianceLevel=17` でパースさせる | 一部の per-file 指標が JavaParser 由来の場合、レポート粒度が変わる可能性 |
| `--engine spoon` 相当の CLI フラグ | （現状未確認）あれば同上 | 同上 |
| 対象パッケージから Record ファイルを `--exclude` で除外 | エラー件数は減るが本質解決ではない | 解析カバレッジが下がる |

> 修正前の段階では「`analysis.engine: spoon` を一時的に指定する」が最も安全な回避策。

---

## 5. 統合テストの過不足

### 5.1 既存テストの状況

- `app/src/integrationTest/` `app/src/e2eTest/` 直下にディレクトリ存在を確認したが、
  Records / sealed / pattern matching を**フィクスチャに含む統合テストは未検出**
  （`find ... | xargs grep -l 'record\s\+\w('` の結果 0 件）。
- 単体テストでヒットした `record` 利用は **テスト自身が record DTO を使っている** だけで、
  「解析対象として record を含むファイルを食わせる」テストではない。
- 既存 `DefaultServiceFactoryTest` `AnalysisPortFactoryTest` はエンジン選択ロジックの
  分岐網羅を見ているのみで、**`createParser` の `ParserConfiguration` をアサート
  していない**。
- `BranchSummaryExtractor` だけは `BLEEDING_EDGE` 設定が必要なほどの分岐構文を
  扱うため明示的に設定済み。逆に「Records を含まないコードしか食わなかったから
  これまで露見しなかった」ことが推測できる。

### 5.2 追加すべきテスト（提案）

新規フィクスチャを `app/src/integrationTest/resources/fixtures/java21/`（仮）配下に
配置し、以下を含める:

| フィクスチャ | カバー対象 |
| - | - |
| `SampleRecord.java`（Record 宣言） | Java 14+ Records |
| `SealedPayment.java` + `CreditCard.java` 等の permits | Java 17 sealed/permits |
| `Switch21.java`（`switch` パターン + record deconstruction） | Java 21 pattern matching in switch |
| `TextBlockSql.java` | Java 15 text blocks |
| `InstanceofPattern.java` | Java 16 pattern matching for `instanceof` |

統合テストケース:

1. `JavaParserAnalyzerIT#analyze_recordFixture_doesNotEmitRecordParseError`
   - フィクスチャを `analyze()` に通し、`AnalysisResult.getAnalysisErrors()` に
     `Record Declarations are not supported` を含まないことをアサート。
2. `JavaParserAnalyzerIT#analyze_sealedFixture_producesPerFileJson`
   - Sealed/permits を含むファイルの per-file JSON が生成されることを検証。
3. `JavaParserFactoryConfigurationTest`（短期修正後）
   - `createParser(...)` の戻り値が `LanguageLevel.JAVA_21`（または config 経由値）
     であることをアサート（リフレクション不要、`getParserConfiguration().getLanguageLevel()`）。
4. `LanguageLevelDriftGuardTest`
   - **ガードレール**: 主要パスの `JavaParser` インスタンスが必ず JAVA_14 以上の
     `LanguageLevel` を持つことを ArchUnit などで検査
     （JAVA_8/9/10/11/13 にされていたら失敗）。

### 5.3 ArchUnit による退行検知

既に ArchUnit を使っているので、以下のような `ArchTest` を 1 本追加するだけで
B 系の取りこぼしを CI レベルでブロックできる:

```java
@ArchTest
static final ArchRule no_default_JavaParser_in_analysis_paths =
    noClasses()
        .that().resideInAPackage("..infrastructure.parser.impl.javaparser..")
        .or().resideInAPackage("..plugins.analysis..")
        .should().callConstructor(JavaParser.class)
        .because("Use JavaParserFactory.newParser(...) to ensure LanguageLevel is set.");
```

---

## 6. 付録: 検証ログ抜粋

### 6.1 grep 結果（`setLanguageLevel` / `LanguageLevel.` 明示箇所）

```
TestCodeFormatter.java:44:           configuration.setLanguageLevel(LanguageLevel.BLEEDING_EDGE);
JavaParserBrittleTestChecker.java:32: configuration.setLanguageLevel(LanguageLevel.BLEEDING_EDGE);
BranchSummaryExtractor.java:90:      .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
```

### 6.2 grep 結果（`new JavaParser` / `new ParserConfiguration` 全箇所）

```
TestCodeFormatter.java:42:           new ParserConfiguration();
TestCodeFormatter.java:45:           new JavaParser(configuration);
CodeValidator.java:313:              new JavaParser().parse(code);
JavaParserBrittleTestChecker.java:31:new ParserConfiguration();
JavaParserBrittleTestChecker.java:33:new JavaParser(configuration);
JavaParserAnalyzer.java:229:         new ParserConfiguration();         ← ★ root cause
JavaParserAnalyzer.java:231:         new JavaParser(parserConfiguration);
JavaParserAnalyzer.java:337:         new JavaParser(config);            ← 上の config を流用
CacheRevalidator.java:182:           new JavaParser().parse(...);
DynamicResolver.java:152:            new JavaParser();
CommonMethodAnalyzer.java:82:        new JavaParser().parse(sourceCode);
BranchSummaryExtractor.java:88-90:   new JavaParser(new ParserConfiguration().setLanguageLevel(...))
ProjectSymbolIndexBuilder.java:32:   new JavaParser();
```

### 6.3 Spoon 側（参考）

```
SpoonAnalyzer.java:271:  launcher.getEnvironment().setComplianceLevel(17);
```

### 6.4 ライブラリバージョン

```
gradle/libs.versions.toml:5: javaparser = "3.25.7"
```

JavaParser 3.25.x の `LanguageLevel` enum は
`JAVA_8 / 9 / 10 / 11 / 12 / 13 / 14 / 15 / 16 / 17 / 18 / 19 / 20 / 21 / POPULAR / BLEEDING_EDGE`
を提供。`JAVA_21` 指定は安全。

---

## 7. 制約と注意事項

- 本レポートは **調査・提案のみ**。コードコミット / PR は作成していない。
- ライセンス（FUL Proprietary）の Modification 禁止条項を踏まえ、
  社内コードレビューで承認を得たうえで実装担当に渡すこと。
- 既存 Spoon 経路（`SpoonAnalyzer`）およびキャッシュ機構（`CacheRevalidator` 含む）の
  挙動を壊さない方針: 4.2 の B4 修正は「より厳しい構文を受理できるようになる」だけで、
  既存の正常ケースを退化させない。
- `BLEEDING_EDGE` ではなく `JAVA_21` を推奨する理由は
  「（a）Spoon の `complianceLevel=17` と齟齬を最小化、
   （b）将来 JavaParser が新たな preview 構文を不安定な状態で取り込んでも
   解析挙動が変わらないことを担保」のため。

---

## 8. 次のアクション（提案順）

1. **本レポートをメンテナレビューに回す**。
2. 承認後、**4.1 の 1 行修正をホットフィックスとして mainline に投入**。
3. ホットフィックス merge と同時に **4.4 の利用者向け回避策（`analysis.engine: spoon`）を
   release-notes / FAQ に追記**（既にユーザーが踏んでいるため）。
4. 続いて **4.2 の B3〜B7 を一括修正**（小 PR 5 件 or 1 PR にバンドル）。
5. 中期で **4.3 の `JavaParserFactory` 集約 + `analysis.language_level` スキーマ拡張**。
6. **5.2 / 5.3 の統合テストを `integrationTest` ソースセットに追加**して退行防止。
