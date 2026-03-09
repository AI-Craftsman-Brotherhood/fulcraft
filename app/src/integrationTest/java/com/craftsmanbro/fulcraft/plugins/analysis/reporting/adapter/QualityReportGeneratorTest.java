package com.craftsmanbro.fulcraft.plugins.analysis.reporting.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class QualityReportGeneratorTest {

  private final ObjectMapper objectMapper = JsonMapperFactory.createPrettyPrinter();
  private Locale originalLocale;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    originalLocale = MessageSource.getLocale();
    MessageSource.setLocale(Locale.ENGLISH);
  }

  @AfterEach
  void tearDown() {
    MessageSource.setLocale(originalLocale);
  }

  @Test
  void generate_returnsFalseWhenSummaryMissing() throws Exception {
    QualityReportGenerator generator = new QualityReportGenerator();

    assertFalse(generator.generate(analysisDir()));
    assertFalse(Files.exists(analysisDir().resolve("quality_report.json")));
    assertFalse(Files.exists(analysisDir().resolve("quality_report.md")));
  }

  @Test
  void includesDynamicRecommendationsAndFocusTargets() throws Exception {
    Map<String, Object> summary = buildSummary(0.9, 10, 0, 5, 0, 25, "AUTO", "DELOMBOK", "SUCCESS");
    List<Map<String, Object>> topFiles = new ArrayList<>();
    topFiles.add(buildDynamicEntry("src/main/java/Foo.java", 12));
    topFiles.add(buildDynamicEntry("src/main/java/Bar.java", 4));
    getMap(summary, "dynamic_features").put("top_files", topFiles);
    List<Object> ambiguousExamples = new ArrayList<>();
    ambiguousExamples.add("Foo#bar()");
    ambiguousExamples.add(123);
    ambiguousExamples.add(null);
    summary.put("top_ambiguous_examples", ambiguousExamples);

    writeSummary(summary);

    QualityReportGenerator generator = new QualityReportGenerator();
    assertTrue(generator.generate(analysisDir()));

    Map<String, Object> report = loadReport();
    List<String> recommendations = getList(report, "recommendations");

    assertTrue(recommendations.stream().anyMatch(r -> r.contains("High dynamic feature score")));
    assertTrue(
        recommendations.stream()
            .anyMatch(
                r ->
                    r.contains(
                        "Focus testing on dynamic hotspots: src/main/java/Foo.java, src/main/java/Bar.java.")));

    assertEquals(
        List.of("src/main/java/Foo.java", "src/main/java/Bar.java"),
        getList(report, "focus_test_targets"));
    assertEquals(List.of("Foo#bar()", "123"), getList(report, "top_ambiguous_examples"));
  }

  @Test
  void addsLombokRecommendationWhenPreprocessOff() throws Exception {
    Map<String, Object> summary = buildSummary(0.95, 5, 0, 0, 0, 0, "OFF", null, "SKIPPED");
    Map<String, Object> annotations = new LinkedHashMap<>();
    List<Map<String, Object>> top = new ArrayList<>();
    top.add(buildAnnotationEntry("lombok.Data", 2));
    annotations.put("top", top);
    summary.put("annotations", annotations);

    writeSummary(summary);

    QualityReportGenerator generator = new QualityReportGenerator();
    assertTrue(generator.generate(analysisDir()));

    List<String> recommendations = getList(loadReport(), "recommendations");
    assertTrue(
        recommendations.stream()
            .anyMatch(r -> r.contains("Lombok annotations detected but preprocessing is OFF")));
  }

  @Test
  void addsDefaultRecommendationWhenNoIssues() throws Exception {
    Map<String, Object> summary = buildSummary(0.95, 5, 0, 3, 0, 0, "AUTO", "DELOMBOK", "SUCCESS");
    writeSummary(summary);

    QualityReportGenerator generator = new QualityReportGenerator();
    assertTrue(generator.generate(analysisDir()));

    assertEquals(
        List.of("Analysis quality is good. No immediate improvements needed."),
        getList(loadReport(), "recommendations"));
  }

  @Test
  void appliesPenaltiesAndRecommendationsForFallbackFailureAndNoClasspath() throws Exception {
    Map<String, Object> summary =
        buildSummary(0.6, 0, 5, 0, 5, 5, "AUTO", "DELOMBOK", "FAILED_FALLBACK");
    writeSummary(summary);

    QualityReportGenerator generator = new QualityReportGenerator();
    assertTrue(generator.generate(analysisDir()));

    Map<String, Object> report = loadReport();
    Map<String, Object> quality = getMap(report, "quality");
    Map<String, Object> penalties = getMap(quality, "penalties");

    assertEquals(45, getInt(quality, "score"));
    assertEquals(5, getInt(penalties, "dynamic_features"));
    assertEquals(5, getInt(penalties, "preprocess"));
    assertEquals(5, getInt(penalties, "classpath"));

    List<String> recommendations = getList(report, "recommendations");
    assertTrue(
        recommendations.stream().anyMatch(r -> r.contains("Type resolution rate is low (60.0%)")));
    assertTrue(recommendations.stream().anyMatch(r -> r.contains("fell back to original sources")));
    assertTrue(
        recommendations.stream().anyMatch(r -> r.contains("No classpath resolution detected")));
  }

  @Test
  void addsStrictPreprocessRecommendationAndPenalty() throws Exception {
    Map<String, Object> summary = buildSummary(0.9, 3, 1, 0, 0, 0, "STRICT", "DELOMBOK", "FAILED");
    writeSummary(summary);

    QualityReportGenerator generator = new QualityReportGenerator();
    assertTrue(generator.generate(analysisDir()));

    Map<String, Object> quality = getMap(loadReport(), "quality");
    Map<String, Object> penalties = getMap(quality, "penalties");
    assertEquals(80, getInt(quality, "score"));
    assertEquals(10, getInt(penalties, "preprocess"));

    List<String> recommendations = getList(loadReport(), "recommendations");
    assertTrue(recommendations.stream().anyMatch(r -> r.contains("failed in STRICT mode")));
  }

  @Test
  void writesMarkdownWithTopDynamicTypesAndAmbiguousDetails() throws Exception {
    Map<String, Object> summary = buildSummary(0.92, 4, 0, 1, 0, 8, "AUTO", "DELOMBOK", "SUCCESS");
    getMap(summary, "dynamic_features")
        .put("top_subtypes", List.of(buildSubtypeEntry("reflection.method.invoke", 3)));
    getMap(summary, "type_resolution").put("ambiguous", 2);
    summary.put("top_ambiguous_examples", List.of("Foo#bar(java.lang.Object)"));
    writeSummary(summary);

    QualityReportGenerator generator = new QualityReportGenerator();
    assertTrue(generator.generate(analysisDir()));

    String markdown = loadMarkdown();
    assertTrue(markdown.contains("## Top Dynamic Types"));
    assertTrue(markdown.contains("| reflection.method.invoke | 3 |"));
    assertTrue(markdown.contains("## Type Resolution Details"));
    assertTrue(markdown.contains("| Ambiguous | 2 |"));
    assertTrue(markdown.contains("## Top Ambiguous Examples"));
    assertTrue(markdown.contains("- Foo#bar(java.lang.Object)"));
  }

  @Test
  void generate_usesDefaultsWhenSummarySectionsAreMalformed() throws Exception {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("type_resolution", "invalid");
    summary.put("per_source", "invalid");
    summary.put("dynamic_features", "invalid");
    summary.put("preprocess", "invalid");
    summary.put("top_ambiguous_examples", "invalid");
    writeSummary(summary);

    QualityReportGenerator generator = new QualityReportGenerator();
    assertTrue(generator.generate(analysisDir()));

    Map<String, Object> report = loadReport();
    Map<String, Object> quality = getMap(report, "quality");
    Map<String, Object> classpath = getMap(quality, "classpath");
    Map<String, Object> preprocess = getMap(quality, "preprocess");

    assertEquals(0, getInt(quality, "score"));
    assertEquals("NO", getString(classpath, "javaparser"));
    assertEquals("NO", getString(classpath, "spoon"));
    assertEquals(0, getInt(classpath, "entries"));
    assertEquals("OFF", getString(preprocess, "mode"));
    assertEquals("SKIPPED", getString(preprocess, "status"));
    assertEquals(List.of(), getList(report, "focus_test_targets"));
    assertEquals(List.of(), getList(report, "top_ambiguous_examples"));

    List<String> recommendations = getList(report, "recommendations");
    assertTrue(
        recommendations.stream().anyMatch(r -> r.contains("Type resolution rate is low (0.0%)")));
    assertTrue(
        recommendations.stream().anyMatch(r -> r.contains("No classpath resolution detected")));
  }

  private void writeSummary(Map<String, Object> summary) throws Exception {
    Files.createDirectories(analysisDir());
    Path summaryPath = analysisDir().resolve("type_resolution_summary.json");
    objectMapper.writeValue(summaryPath.toFile(), summary);
  }

  private Map<String, Object> loadReport() throws Exception {
    Path reportPath = analysisDir().resolve("quality_report.json");
    return objectMapper.readValue(
        reportPath.toFile(), new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  private Path analysisDir() {
    return tempDir.resolve("analysis");
  }

  private Map<String, Object> buildSummary(
      double resolutionRate,
      int jpResolved,
      int jpUnresolved,
      int spoonResolved,
      int spoonUnresolved,
      int dynamicScore,
      String preprocessMode,
      String preprocessTool,
      String preprocessStatus) {
    Map<String, Object> summary = new LinkedHashMap<>();

    Map<String, Object> typeResolution = new LinkedHashMap<>();
    int total = jpResolved + jpUnresolved + spoonResolved + spoonUnresolved;
    typeResolution.put("total", total);
    typeResolution.put("resolved", jpResolved + spoonResolved);
    typeResolution.put("unresolved", jpUnresolved + spoonUnresolved);
    typeResolution.put("resolution_rate", resolutionRate);
    summary.put("type_resolution", typeResolution);

    Map<String, Object> perSource = new LinkedHashMap<>();
    perSource.put("javaparser", buildSourceMetrics(jpResolved, jpUnresolved));
    perSource.put("spoon", buildSourceMetrics(spoonResolved, spoonUnresolved));
    summary.put("per_source", perSource);

    Map<String, Object> dynamicFeatures = new LinkedHashMap<>();
    dynamicFeatures.put("dynamic_score", dynamicScore);
    dynamicFeatures.put("top_files", new ArrayList<>());
    dynamicFeatures.put("top_subtypes", new ArrayList<>());
    summary.put("dynamic_features", dynamicFeatures);

    Map<String, Object> preprocess = new LinkedHashMap<>();
    preprocess.put("mode", preprocessMode);
    preprocess.put("tool_used", preprocessTool);
    preprocess.put("status", preprocessStatus);
    summary.put("preprocess", preprocess);

    return summary;
  }

  private Map<String, Object> buildSourceMetrics(int resolved, int unresolved) {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("resolved", resolved);
    source.put("unresolved", unresolved);
    int total = resolved + unresolved;
    source.put("resolution_rate", total > 0 ? (double) resolved / total : 0.0);
    return source;
  }

  private Map<String, Object> buildDynamicEntry(String file, int count) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("file", file);
    entry.put("count", count);
    return entry;
  }

  private Map<String, Object> buildAnnotationEntry(String name, int count) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("name", name);
    entry.put("count", count);
    return entry;
  }

  private Map<String, Object> buildSubtypeEntry(String subtype, int count) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("subtype", subtype);
    entry.put("count", count);
    return entry;
  }

  private Map<String, Object> getMap(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value instanceof Map) {
      return (Map<String, Object>) value;
    }
    return new LinkedHashMap<>();
  }

  private List<String> getList(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value instanceof List) {
      return (List<String>) value;
    }
    return List.of();
  }

  private int getInt(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    return 0;
  }

  private String getString(Map<String, Object> map, String key) {
    Object value = map.get(key);
    return value != null ? value.toString() : null;
  }

  private String loadMarkdown() throws Exception {
    return Files.readString(analysisDir().resolve("quality_report.md"));
  }
}
