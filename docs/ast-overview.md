# AST Overview and Its Role in FUL

This document summarizes the basic idea of an AST (Abstract Syntax Tree) and where the current FUL implementation uses it.
The main focus is the `ANALYZE` stage. It does not cover JUnit generation details.

## What an AST Is

- An AST is an intermediate tree representation of source code.
- It preserves declarations, expressions, and control structures in a structured form.
- FUL uses ASTs to analyze call relationships, complexity, branch summaries, and partially resolvable dynamic behavior.

## Primary Usage in FUL

### 1. Analysis Engines (JavaParser / Spoon)

- `AnalyzeStage` executes analysis through `AnalysisFlow`.
- The default CLI engine is `composite`, which merges results from `JavaParserAnalyzer` and `SpoonAnalyzer` through `CompositeAnalysisPort` and `ResultMerger`.
- `SourcePathResolver` and `PathExcluder` choose analysis inputs and reflect settings such as `analysis.exclude_tests`.

Key implementations:

- `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/stage/AnalyzeStage.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/ui/cli/wiring/DefaultServiceFactory.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/core/service/CompositeAnalysisPort.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/parser/javaparser/JavaParserAnalyzer.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/parser/spoon/SpoonAnalyzer.java`

### 2. Call Graph Extraction and Post-Processing

- On the JavaParser side, `DependencyGraphBuilder` extracts method calls, constructor calls, and method references from the AST.
- It also captures literal call arguments in `argument_literals`.
- Spoon builds call relationships with its own `DependencyGraphBuilder`.
- After collection, `CommonPostProcessor` finalizes `called_method_refs`, and `GraphAnalyzer` marks cycles.

Key implementations:

- `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/parser/javaparser/DependencyGraphBuilder.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/parser/spoon/DependencyGraphBuilder.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/parser/common/CommonPostProcessor.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/parser/common/GraphAnalyzer.java`

### 3. Branch Summaries and Representative Paths

- In `composite` mode, post-processing inside `ResultMerger` enriches `MethodInfo` using `MethodDerivedMetricsComputer` and `BranchSummaryExtractor`.
- `BranchSummaryExtractor` parses `source_code` with JavaParser and generates `branch_summary` and `representative_paths`.
- Those outputs are later consumed by reporting and documentation flows.

Key implementations:

- `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/core/util/ResultMerger.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/core/service/metric/BranchSummaryExtractor.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/core/service/metric/MethodDerivedMetricsComputer.java`

### 4. Resolving Dynamic Behavior

- `DynamicResolver` scans source with JavaParser and estimates dynamic targets that can still be inferred statically.
- Results are saved to `dynamic_resolutions.jsonl` and reattached to `MethodInfo.dynamic_resolutions`.
- `DynamicFeatureDetector` also emits `dynamic_features.jsonl`.

Key implementations:

- `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/core/service/dynamic/DynamicResolver.java`
- `app/src/main/java/com/craftsmanbro/fulcraft/feature/analysis/io/AnalysisResultWriter.java`

### 5. Complexity Utility

- `MetricsCalculator` is a shared utility that can work with JavaParser, Spoon, and JDT AST APIs.
- The current pipeline primarily relies on JavaParser and Spoon.

Key implementation:

- `app/src/main/java/com/craftsmanbro/fulcraft/infrastructure/metrics/MetricsCalculator.java`

## ANALYZE Outputs

When `AnalyzeStage` runs, FUL mainly writes these artifacts under `.ful/runs/<runId>/analysis/`:

- `analysis_*.json`
- `type_resolution_summary.json`
- `dynamic_features.jsonl`
- `dynamic_resolutions.jsonl`
- `analysis_files.txt` when enabled by configuration

## Simplified Flow

```text
Source code
  -> AST parsing (JavaParser / Spoon)
  -> Call relationship and metric extraction
  -> Result merge (ResultMerger)
  -> branch_summary / representative_paths generation
  -> dynamic_features / dynamic_resolutions generation
  -> Saved under .ful/runs/<runId>/analysis
```

## Related Documents

- [Pipeline Design Specification](design/pipeline_design_spec.md)
- [Architecture Overview](architecture.md)
- [Unified Structure Guide](design/unified-structure-guide.md)
