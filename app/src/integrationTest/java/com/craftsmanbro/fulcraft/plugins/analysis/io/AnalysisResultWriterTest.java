package com.craftsmanbro.fulcraft.plugins.analysis.io;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess.SourcePreprocessor;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class AnalysisResultWriterTest {

  @TempDir Path tempDir;

  @Test
  void saveAnalysisResult_writesClassShardsAndSummary() throws Exception {
    AnalysisResult result = new AnalysisResult("ignored");
    result.setCommitHash("commit-123");
    result.setClasses(
        List.of(
            createClass("com.example.Foo", "com/example/Foo.java"),
            createClass("com.example.sub.Bar", "com/example/sub/Bar.java")));

    AnalysisResultWriter writer = new AnalysisResultWriter();
    Path outputDir = tempDir.resolve("out");
    Path projectRoot = tempDir.resolve("project");

    writer.saveAnalysisResult(result, outputDir, projectRoot, "project-1");

    Path fooFile = outputDir.resolve("com/example/analysis_Foo.json");
    Path barFile = outputDir.resolve("com/example/sub/analysis_Bar.json");
    assertThat(fooFile).exists();
    assertThat(barFile).exists();
    assertThat(outputDir.resolve("type_resolution_summary.json")).exists();

    ObjectMapper mapper = JsonMapperFactory.create();
    AnalysisResult stored = mapper.readValue(fooFile.toFile(), AnalysisResult.class);
    assertThat(stored.getProjectId()).isEqualTo("project-1");
    assertThat(stored.getCommitHash()).isEqualTo("commit-123");
    assertThat(stored.getClasses()).hasSize(1);
    assertThat(stored.getClasses().get(0).getFqn()).isEqualTo("com.example.Foo");
  }

  @Test
  void saveAnalysisResult_whenNoClasses_doesNotWriteSummary() {
    AnalysisResult result = new AnalysisResult("ignored");
    result.setClasses(List.of());

    AnalysisResultWriter writer = new AnalysisResultWriter();
    Path outputDir = tempDir.resolve("out-empty");
    Path projectRoot = tempDir.resolve("project");

    writer.saveAnalysisResult(result, outputDir, projectRoot, "project-1");

    assertThat(outputDir.resolve("type_resolution_summary.json")).doesNotExist();
  }

  @Test
  void saveAnalysisShards_writesClassShardsOnly() throws Exception {
    AnalysisResult result = new AnalysisResult("ignored");
    result.setCommitHash("commit-123");
    result.setClasses(List.of(createClass("com.example.Foo", "com/example/Foo.java")));

    AnalysisResultWriter writer = new AnalysisResultWriter();
    Path outputDir = tempDir.resolve("out-shards-only");

    writer.saveAnalysisShards(result, outputDir, "project-1");

    Path fooFile = outputDir.resolve("com/example/analysis_Foo.json");
    assertThat(fooFile).exists();
    assertThat(outputDir.resolve("type_resolution_summary.json")).doesNotExist();
  }

  @Test
  void saveAnalysisShards_sanitizesPathSegmentsFromClassFqn() {
    AnalysisResult result = new AnalysisResult("ignored");
    result.setClasses(
        List.of(createClass("com/example.bad\\pkg.My/Class", "com/example/Foo.java")));

    AnalysisResultWriter writer = new AnalysisResultWriter();
    Path outputDir = tempDir.resolve("out-sanitized");

    writer.saveAnalysisShards(result, outputDir, "project-1");

    assertThat(outputDir.resolve("com_example/bad_pkg/analysis_My_Class.json")).exists();
  }

  @Test
  void saveAnalysisShards_usesUnknownClassWhenFqnIsBlank() {
    AnalysisResult result = new AnalysisResult("ignored");
    result.setClasses(List.of(createClass(" ", "com/example/Foo.java")));

    AnalysisResultWriter writer = new AnalysisResultWriter();
    Path outputDir = tempDir.resolve("out-unknown");

    writer.saveAnalysisShards(result, outputDir, "project-1");

    assertThat(outputDir.resolve("analysis_UnknownClass.json")).exists();
  }

  @Test
  void saveAnalyzedFileList_filtersUnknownAndDedupes() throws Exception {
    AnalysisResult result = new AnalysisResult("project");
    List<ClassInfo> classes = new ArrayList<>();
    classes.add(createClass("com.example.Foo", "com/example/Foo.java"));
    classes.add(createClass("com.example.Bar", "com/example/Bar.java"));
    classes.add(createClass("com.example.Unknown", "unknown"));
    classes.add(createClass("com.example.Missing", null));
    classes.add(createClass("com.example.FooDup", "com/example/Foo.java"));
    result.setClasses(classes);

    AnalysisResultWriter writer = new AnalysisResultWriter();
    Path outputDir = tempDir.resolve("out");
    Path projectRoot = tempDir.resolve("project");

    writer.saveAnalyzedFileList(result, outputDir, projectRoot);

    Path listPath = outputDir.resolve("analysis_files.txt");
    List<String> lines = Files.readAllLines(listPath);
    List<String> expected =
        List.of(
            projectRoot
                .resolve("src/main/java")
                .resolve("com/example/Bar.java")
                .toAbsolutePath()
                .normalize()
                .toString(),
            projectRoot
                .resolve("src/main/java")
                .resolve("com/example/Foo.java")
                .toAbsolutePath()
                .normalize()
                .toString());

    assertThat(lines).containsExactlyElementsOf(expected);
  }

  @Test
  void saveTypeResolutionSummary_countsAndGroups() throws Exception {
    MethodInfo method = new MethodInfo();
    method.setSignature("test()");
    method.setCalledMethodRefs(
        List.of(
            createRef("A#foo", "A#foo", ResolutionStatus.RESOLVED, "javaparser"),
            createRef("B#bar", null, ResolutionStatus.UNRESOLVED, "spoon"),
            createRef("C#baz", null, ResolutionStatus.AMBIGUOUS, "custom")));

    ClassInfo clazz = createClass("com.example.Foo", "com/example/Foo.java");
    clazz.setMethods(List.of(method));

    AnalysisResult result = new AnalysisResult("project");
    result.setClasses(List.of(clazz));

    AnalysisResultWriter writer = new AnalysisResultWriter();
    Path outputDir = tempDir.resolve("out");
    Path projectRoot = tempDir.resolve("project");

    writer.saveTypeResolutionSummary(result, outputDir, projectRoot);

    Map<String, Object> summary = readSummary(outputDir.resolve("type_resolution_summary.json"));
    Map<String, Object> typeResolution = asMap(summary.get("type_resolution"));
    assertThat(((Number) typeResolution.get("total")).intValue()).isEqualTo(3);
    assertThat(((Number) typeResolution.get("resolved")).intValue()).isEqualTo(1);
    assertThat(((Number) typeResolution.get("unresolved")).intValue()).isEqualTo(1);
    assertThat(((Number) typeResolution.get("ambiguous")).intValue()).isEqualTo(1);
    assertThat(((Number) typeResolution.get("resolution_rate")).doubleValue()).isEqualTo(0.33);

    Map<String, Object> perSource = asMap(summary.get("per_source"));
    Map<String, Object> javaparser = asMap(perSource.get("javaparser"));
    Map<String, Object> spoon = asMap(perSource.get("spoon"));
    Map<String, Object> other = asMap(perSource.get("other"));

    assertThat(((Number) javaparser.get("resolved")).intValue()).isEqualTo(1);
    assertThat(((Number) javaparser.get("resolution_rate")).doubleValue()).isEqualTo(1.0);
    assertThat(((Number) spoon.get("unresolved")).intValue()).isEqualTo(1);
    assertThat(((Number) spoon.get("resolution_rate")).doubleValue()).isEqualTo(0.0);
    assertThat(((Number) other.get("ambiguous")).intValue()).isEqualTo(1);

    assertThat(asStringList(summary.get("top_unresolved_examples"))).contains("B#bar");
    assertThat(asStringList(summary.get("top_ambiguous_examples"))).contains("C#baz");
  }

  @Test
  void saveDynamicFeatures_writesJsonlAndUpdatesSummary() throws Exception {
    MethodInfo method = new MethodInfo();
    method.setSignature("test()");
    method.setCalledMethodRefs(
        List.of(
            createRef(
                "java.lang.Class#forName",
                "java.lang.Class#forName",
                ResolutionStatus.RESOLVED,
                "javaparser")));

    ClassInfo clazz = createClass("com.example.Foo", "com/example/Foo.java");
    clazz.setMethods(List.of(method));

    AnalysisResult result = new AnalysisResult("project");
    result.setClasses(List.of(clazz));

    AnalysisResultWriter writer = new AnalysisResultWriter();
    Path outputDir = tempDir.resolve("out");
    Path projectRoot = tempDir.resolve("project");

    writer.saveDynamicFeatures(result, outputDir, projectRoot);

    Path jsonlPath = outputDir.resolve("dynamic_features.jsonl");
    assertThat(jsonlPath).exists();
    assertThat(Files.readAllLines(jsonlPath)).hasSize(1);

    Map<String, Object> summary = readSummary(outputDir.resolve("type_resolution_summary.json"));
    Map<String, Object> dynamicFeatures = asMap(summary.get("dynamic_features"));
    assertThat(((Number) dynamicFeatures.get("total_events")).intValue()).isEqualTo(1);
    assertThat(((Number) dynamicFeatures.get("dynamic_score")).intValue()).isEqualTo(2);

    Map<String, Object> byType = asMap(dynamicFeatures.get("by_type"));
    Map<String, Object> bySeverity = asMap(dynamicFeatures.get("by_severity"));
    assertThat(((Number) byType.get("REFLECTION")).intValue()).isEqualTo(1);
    assertThat(((Number) bySeverity.get("MEDIUM")).intValue()).isEqualTo(1);

    List<Map<String, Object>> topFiles = asMapList(dynamicFeatures.get("top_files"));
    assertThat(topFiles.get(0).get("file")).isEqualTo("com/example/Foo.java");
    assertThat(((Number) topFiles.get(0).get("count")).intValue()).isEqualTo(1);

    List<Map<String, Object>> topSubtypes = asMapList(dynamicFeatures.get("top_subtypes"));
    assertThat(topSubtypes.get(0).get("subtype")).isEqualTo("CLASS_FORNAME");
    assertThat(((Number) topSubtypes.get(0).get("count")).intValue()).isEqualTo(1);

    Map<String, Object> annotations = asMap(summary.get("annotations"));
    assertThat(((Number) annotations.get("unique_count")).intValue()).isEqualTo(0);
    assertThat(asMapList(annotations.get("top"))).isEmpty();
  }

  @Test
  void saveDynamicResolutions_writesSummaryWhenNoResolutions() throws Exception {
    AnalysisResult result = new AnalysisResult("project");
    result.setClasses(List.of());

    AnalysisResultWriter writer = new AnalysisResultWriter();
    Path outputDir = tempDir.resolve("out");
    Path projectRoot = tempDir.resolve("project");

    writer.saveDynamicResolutions(result, outputDir, projectRoot, new Config(), null, Map.of());

    Path jsonlPath = outputDir.resolve("dynamic_resolutions.jsonl");
    assertThat(jsonlPath).exists();
    assertThat(Files.readAllLines(jsonlPath)).isEmpty();

    Map<String, Object> summary = readSummary(outputDir.resolve("type_resolution_summary.json"));
    Map<String, Object> dynamicResolutions = asMap(summary.get("dynamic_resolutions"));
    assertThat(((Number) dynamicResolutions.get("total")).intValue()).isEqualTo(0);
    assertThat(((Number) dynamicResolutions.get("average_confidence")).doubleValue())
        .isEqualTo(0.0);
  }

  @Test
  void savePreprocessResult_writesPreprocessSummary() throws Exception {
    Config config = new Config();
    Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
    Config.AnalysisConfig.PreprocessConfig preprocessConfig =
        new Config.AnalysisConfig.PreprocessConfig();
    preprocessConfig.setMode("STRICT");
    preprocessConfig.setWorkDir("build/preprocess");
    analysisConfig.setPreprocess(preprocessConfig);
    config.setAnalysis(analysisConfig);

    Path projectRoot = tempDir.resolve("project");
    List<Path> roots = List.of(projectRoot.resolve("src/main/java"));
    SourcePreprocessor.Result preprocessResult =
        new SourcePreprocessor.Result(
            SourcePreprocessor.Status.SUCCESS, roots, roots, "DELOMBOK", null, 123L);

    AnalysisResultWriter writer = new AnalysisResultWriter();
    Path outputDir = tempDir.resolve("out");
    writer.savePreprocessResult(preprocessResult, outputDir, projectRoot, config);

    Map<String, Object> summary = readSummary(outputDir.resolve("type_resolution_summary.json"));
    Map<String, Object> preprocess = asMap(summary.get("preprocess"));
    assertThat(preprocess.get("mode")).isEqualTo("STRICT");
    assertThat(preprocess.get("tool_used")).isEqualTo("DELOMBOK");
    assertThat(preprocess.get("status")).isEqualTo("SUCCESS");
    assertThat(preprocess.get("work_dir"))
        .isEqualTo(projectRoot.resolve("build/preprocess").toString());
    assertThat(asStringList(preprocess.get("source_roots_before")))
        .containsExactly(roots.get(0).toString());
  }

  @Test
  void savePreprocessResult_usesDefaultModeAndWorkDirWhenConfigMissing() throws Exception {
    Config config = new Config();

    Path projectRoot = tempDir.resolve("project");
    List<Path> roots = List.of(projectRoot.resolve("src/main/java"));
    SourcePreprocessor.Result preprocessResult =
        new SourcePreprocessor.Result(
            SourcePreprocessor.Status.SKIPPED, roots, roots, null, null, 0L);

    AnalysisResultWriter writer = new AnalysisResultWriter();
    Path outputDir = tempDir.resolve("out-default-preprocess");
    writer.savePreprocessResult(preprocessResult, outputDir, projectRoot, config);

    Map<String, Object> summary = readSummary(outputDir.resolve("type_resolution_summary.json"));
    Map<String, Object> preprocess = asMap(summary.get("preprocess"));
    assertThat(preprocess.get("mode")).isEqualTo("OFF");
    assertThat(preprocess.get("work_dir"))
        .isEqualTo(projectRoot.resolve(".utg/preprocess").toString());
    assertThat(preprocess.get("status")).isEqualTo("SKIPPED");
  }

  private static ClassInfo createClass(String fqn, String filePath) {
    ClassInfo clazz = new ClassInfo();
    clazz.setFqn(fqn);
    clazz.setFilePath(filePath);
    return clazz;
  }

  private static CalledMethodRef createRef(
      String raw, String resolved, ResolutionStatus status, String source) {
    CalledMethodRef ref = new CalledMethodRef();
    ref.setRaw(raw);
    ref.setResolved(resolved);
    ref.setStatus(status);
    ref.setSource(source);
    return ref;
  }

  private static Map<String, Object> readSummary(Path summaryPath) throws Exception {
    ObjectMapper mapper = JsonMapperFactory.create();
    return mapper.readValue(summaryPath.toFile(), new TypeReference<>() {});
  }

  private static Map<String, Object> asMap(Object value) {
    return (Map<String, Object>) value;
  }

  private static List<Map<String, Object>> asMapList(Object value) {
    return (List<Map<String, Object>>) value;
  }

  private static List<String> asStringList(Object value) {
    return (List<String>) value;
  }
}
