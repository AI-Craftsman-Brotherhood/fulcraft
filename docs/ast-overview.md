# AST 概要と FUL での位置づけ

このドキュメントは、AST（Abstract Syntax Tree: 抽象構文木）の基本概念と、FUL の現行実装での利用箇所を整理したものです。  
対象は主に `ANALYZE` ステージです。JUnit 自動生成に関する内容は扱いません。

## AST とは

- **AST（抽象構文木）**は、ソースコードを構文要素の木構造に変換した中間表現です。
- 宣言・式・制御構造を階層的に扱えるため、単純な文字列検索よりも構造を保った解析ができます。
- FUL では AST を使って、呼び出し関係、複雑度、分岐要約、動的要素の解決を行います。

## FUL の主要利用箇所（最新版）

### 1) 解析エンジン（JavaParser / Spoon）

- `AnalyzeStage` は `AnalysisFlow` 経由で解析を実行します。
- CLI 既定の `--engine` は `composite` で、`JavaParserAnalyzer` と `SpoonAnalyzer` の結果を `CompositeAnalysisPort` + `ResultMerger` で統合します。
- 解析時は `SourcePathResolver` と `PathExcluder` で入力対象を決め、`analysis.exclude_tests` などの設定を反映します。

主な実装:

- `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/stage/AnalyzeStage.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/ui/cli/wiring/DefaultServiceFactory.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/core/service/CompositeAnalysisPort.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/parser/javaparser/JavaParserAnalyzer.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/parser/spoon/SpoonAnalyzer.java`

### 2) 呼び出し関係と後処理

- JavaParser 側は `DependencyGraphBuilder` が AST からメソッド呼び出し・コンストラクタ呼び出し・メソッド参照を抽出します。
- 抽出時に呼び出し引数のリテラル（`argument_literals`）も収集します。
- Spoon 側も `DependencyGraphBuilder` で呼び出し関係を収集します。
- 収集後、`CommonPostProcessor` が `called_method_refs` を確定し、`GraphAnalyzer` が循環参照（cycle）をマーキングします。

主な実装:

- `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/parser/javaparser/DependencyGraphBuilder.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/parser/spoon/DependencyGraphBuilder.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/parser/common/CommonPostProcessor.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/parser/common/GraphAnalyzer.java`

### 3) 分岐要約・代表パスの抽出

- `composite` 実行時は `ResultMerger` の後処理で、`MethodDerivedMetricsComputer` と `BranchSummaryExtractor` が `MethodInfo` を補強します。
- `BranchSummaryExtractor` は `source_code` を JavaParser で解析し、`branch_summary` と `representative_paths` を生成します。
- これらは後続のレポート/ドキュメント生成で利用されます。

主な実装:

- `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/core/util/ResultMerger.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/core/service/metric/BranchSummaryExtractor.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/core/service/metric/MethodDerivedMetricsComputer.java`

### 4) 動的要素の解決（reflection / ServiceLoader など）

- `DynamicResolver` が JavaParser ベースでソースを走査し、静的に確定可能な動的ターゲットを推定します。
- 解決結果は `dynamic_resolutions.jsonl` に保存されるだけでなく、`MethodInfo.dynamic_resolutions` に再付与されます。
- 併せて `DynamicFeatureDetector` により `dynamic_features.jsonl` も出力されます。

主な実装:

- `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/core/service/dynamic/DynamicResolver.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/io/AnalysisResultWriter.java`

### 5) 複雑度計算ユーティリティ

- `MetricsCalculator` は JavaParser/Spoon/JDT の AST API を扱える共通ユーティリティです。
- 現行の解析パイプラインで主に使うのは JavaParser/Spoon です。

主な実装:

- `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/metrics/MetricsCalculator.java`

## 出力物（ANALYZE）

`AnalyzeStage` 実行時、主に `.ful/runs/<runId>/analysis/` 配下へ次を出力します。

- `analysis_*.json`（クラス単位の解析シャード）
- `type_resolution_summary.json`
- `dynamic_features.jsonl`
- `dynamic_resolutions.jsonl`
- `analysis_files.txt`（設定で有効時）

## 参考フロー（簡略）

```text
ソースコード
  -> AST 解析（JavaParser / Spoon）
  -> 呼び出し関係・メトリクス抽出
  -> 結果統合（ResultMerger）
  -> branch_summary / representative_paths 生成
  -> dynamic_features / dynamic_resolutions 生成
  -> .ful/runs/<runId>/analysis へ保存
```

## 関連ドキュメント

- [pipeline_architecture.md](design/pipeline_design_spec.md)
- [architecture.md](architecture.md)
- [unified-structure-guide.md](design/unified-structure-guide.md)
