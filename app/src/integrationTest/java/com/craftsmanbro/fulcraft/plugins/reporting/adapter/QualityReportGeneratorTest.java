package com.craftsmanbro.fulcraft.plugins.reporting.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.plugins.analysis.reporting.adapter.QualityReportGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class QualityReportGeneratorTest {

  private final ObjectMapper objectMapper = JsonMapperFactory.createPrettyPrinter();

  @TempDir Path tempDir;

  @Test
  void appliesPreprocessFailedPenaltyAndRecommendation() throws Exception {
    writeSummary(buildSummary(0.9, 5, 0, 0, 5, 0, "STRICT", "DELOMBOK", "FAILED"));

    QualityReportGenerator generator = new QualityReportGenerator();
    assertTrue(generator.generate(analysisDir()));

    Map<String, Object> quality = loadQuality();
    Map<String, Object> penalties = getMap(quality, "penalties");

    assertEquals(10, ((Number) penalties.get("preprocess")).intValue());

    List<String> recommendations = getList(loadReport(), "recommendations");
    assertTrue(
        recommendations.stream()
            .anyMatch(
                r ->
                    r.contains(
                        MessageSource.getMessage(
                            "analysis.quality_report.recommendation.preprocess_failed_strict"))));
  }

  @Test
  void appliesClasspathPenaltyWhenNoResolution() throws Exception {
    writeSummary(buildSummary(0.0, 0, 5, 0, 5, 0, "OFF", null, "SKIPPED"));

    QualityReportGenerator generator = new QualityReportGenerator();
    assertTrue(generator.generate(analysisDir()));

    Map<String, Object> quality = loadQuality();
    Map<String, Object> penalties = getMap(quality, "penalties");

    assertEquals(5, ((Number) penalties.get("classpath")).intValue());

    List<String> recommendations = getList(loadReport(), "recommendations");
    assertTrue(
        recommendations.stream()
            .anyMatch(
                r ->
                    r.contains(
                        MessageSource.getMessage(
                            "analysis.quality_report.recommendation.no_classpath"))));
  }

  @Test
  void doesNotApplyClasspathPenaltyWhenSpoonResolves() throws Exception {
    writeSummary(buildSummary(0.5, 0, 5, 3, 2, 0, "OFF", null, "SKIPPED"));

    QualityReportGenerator generator = new QualityReportGenerator();
    assertTrue(generator.generate(analysisDir()));

    Map<String, Object> quality = loadQuality();
    Map<String, Object> penalties = getMap(quality, "penalties");
    assertFalse(penalties.containsKey("classpath"));

    Map<String, Object> classpath = getMap(quality, "classpath");
    assertEquals(3, ((Number) classpath.get("entries")).intValue());
  }

  private void writeSummary(Map<String, Object> summary) throws Exception {
    Path analysisDir = analysisDir();
    Files.createDirectories(analysisDir);
    Path summaryPath = analysisDir.resolve("type_resolution_summary.json");
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

  private Map<String, Object> loadQuality() throws Exception {
    return getMap(loadReport(), "quality");
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
}
