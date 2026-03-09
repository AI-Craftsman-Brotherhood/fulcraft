package com.craftsmanbro.fulcraft.plugins.analysis.reporting.adapter;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.collection.contract.CollectionServicePort;
import com.craftsmanbro.fulcraft.infrastructure.collection.impl.DefaultCollectionService;
import com.craftsmanbro.fulcraft.infrastructure.json.contract.JsonServicePort;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.DefaultJsonService;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.reporting.core.service.quality.QualityScore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generates quality reports from analysis summary. */
public class QualityReportGenerator {

  private static final int PREPROCESS_FAILED_PENALTY = 10;

  private static final int PREPROCESS_FALLBACK_PENALTY = 5;

  private static final int NO_CLASSPATH_PENALTY = 5;

  private static final String KEY_DYNAMIC_FEATURES = "dynamic_features";

  private static final String KEY_TOP_AMBIGUOUS_EXAMPLES = "top_ambiguous_examples";

  private final JsonServicePort jsonService;

  private final CollectionServicePort collectionService;

  public QualityReportGenerator() {
    this.jsonService = new DefaultJsonService();
    this.collectionService = new DefaultCollectionService();
  }

  /**
   * Generates quality report from analysis summary.
   *
   * @param analysisDir Analysis output directory
   */
  public boolean generate(final Path analysisDir) throws IOException {
    final Path summaryPath = analysisDir.resolve("type_resolution_summary.json");
    if (!Files.exists(summaryPath)) {
      Logger.warn(MessageSource.getMessage("analysis.quality_report.summary_missing", summaryPath));
      return false;
    }
    // Load summary
    final Map<String, Object> summary = jsonService.readMapFromFile(summaryPath);
    // Calculate quality score
    final QualityScore score = calculateScore(summary);
    // Extract weak files
    final List<Map<String, Object>> topDynamicFiles = extractTopDynamicFiles(summary);
    final List<Map<String, Object>> topDynamicTypes = extractTopDynamicTypes(summary);
    final List<String> topAmbiguousExamples = extractTopAmbiguousExamples(summary);
    // Generate recommendations
    final List<String> recommendations = generateRecommendations(score, summary);
    // Build report
    final Map<String, Object> report = new LinkedHashMap<>();
    report.put("generated_at", java.time.Instant.now().toString());
    report.put("quality", score);
    report.put("top_dynamic_files", topDynamicFiles);
    report.put("top_dynamic_types", topDynamicTypes);
    report.put(KEY_TOP_AMBIGUOUS_EXAMPLES, topAmbiguousExamples);
    report.put("focus_test_targets", extractFocusTargets(topDynamicFiles));
    report.put("recommendations", recommendations);
    // Write JSON report
    final Path jsonPath = analysisDir.resolve("quality_report.json");
    jsonService.writeToFile(jsonPath, report);
    Logger.debug(MessageSource.getMessage("analysis.quality_report.json_saved", jsonPath));
    // Write Markdown report
    final Path mdPath = analysisDir.resolve("quality_report.md");
    final String markdown =
        generateMarkdown(score, topDynamicFiles, topDynamicTypes, recommendations, summary);
    Files.writeString(mdPath, markdown);
    Logger.debug(MessageSource.getMessage("analysis.quality_report.markdown_saved", mdPath));
    return true;
  }

  private QualityScore calculateScore(final Map<String, Object> summary) {
    final QualityScore.Builder builder = QualityScore.builder();
    applyTypeResolutionRate(builder, summary);
    applyDynamicFeatureScore(builder, summary);
    applyClasspathInfo(builder, summary);
    applyPreprocessInfo(builder, summary);
    return builder.build();
  }

  private void applyTypeResolutionRate(
      final QualityScore.Builder builder, final Map<String, Object> summary) {
    final Map<String, Object> typeResolution = getMap(summary, "type_resolution");
    final Object resolutionRate = typeResolution.get("resolution_rate");
    if (resolutionRate instanceof Number number) {
      builder.typeResolutionRate(number.doubleValue());
    }
  }

  private void applyDynamicFeatureScore(
      final QualityScore.Builder builder, final Map<String, Object> summary) {
    final Map<String, Object> dynamicFeatures = getMap(summary, KEY_DYNAMIC_FEATURES);
    final Object dynamicScore = dynamicFeatures.get("dynamic_score");
    if (dynamicScore instanceof Number number) {
      builder.dynamicFeatureScore(number.intValue());
    }
  }

  private void applyClasspathInfo(
      final QualityScore.Builder builder, final Map<String, Object> summary) {
    final Map<String, Object> perSource = getMap(summary, "per_source");
    final int javaparserResolved = getResolvedCount(perSource, "javaparser");
    final int spoonResolved = getResolvedCount(perSource, "spoon");
    final String javaparserStatus = javaparserResolved > 0 ? "WITH" : "NO";
    final String spoonStatus = spoonResolved > 0 ? "WITH" : "NO";
    builder.classpath(javaparserStatus, spoonStatus, javaparserResolved + spoonResolved);
    if ("NO".equals(javaparserStatus) && "NO".equals(spoonStatus)) {
      builder.classpathPenalty(NO_CLASSPATH_PENALTY);
    }
  }

  private void applyPreprocessInfo(
      final QualityScore.Builder builder, final Map<String, Object> summary) {
    final Map<String, Object> preprocess = getMap(summary, "preprocess");
    final String mode = getString(preprocess, "mode", "OFF");
    final String toolUsed = getString(preprocess, "tool_used", null);
    final String status = getString(preprocess, "status", "SKIPPED");
    builder.preprocess(mode, toolUsed, status);
    if ("FAILED".equals(status)) {
      builder.preprocessPenalty(PREPROCESS_FAILED_PENALTY);
    } else if ("FAILED_FALLBACK".equals(status)) {
      builder.preprocessPenalty(PREPROCESS_FALLBACK_PENALTY);
    }
  }

  private List<Map<String, Object>> extractTopDynamicFiles(final Map<String, Object> summary) {
    final Map<String, Object> dynamicFeatures = getMap(summary, KEY_DYNAMIC_FEATURES);
    final Object topFiles = dynamicFeatures.get("top_files");
    if (!(topFiles instanceof List<?>)) {
      return new ArrayList<>();
    }
    return new ArrayList<>(collectionService.toMapList(topFiles));
  }

  private List<Map<String, Object>> extractTopDynamicTypes(final Map<String, Object> summary) {
    final Map<String, Object> dynamicFeatures = getMap(summary, KEY_DYNAMIC_FEATURES);
    final Object topDynamicSubtypes = dynamicFeatures.get("top_subtypes");
    if (!(topDynamicSubtypes instanceof List<?>)) {
      return new ArrayList<>();
    }
    return new ArrayList<>(collectionService.toMapList(topDynamicSubtypes));
  }

  private List<String> extractTopAmbiguousExamples(final Map<String, Object> summary) {
    final Object examples = summary.get(KEY_TOP_AMBIGUOUS_EXAMPLES);
    if (examples instanceof List<?> list) {
      final List<String> results = new ArrayList<>();
      for (final Object entry : list) {
        if (entry != null) {
          results.add(entry.toString());
        }
      }
      return results;
    }
    return new ArrayList<>();
  }

  private List<String> generateRecommendations(
      final QualityScore score, final Map<String, Object> summary) {
    final List<String> recommendations = new ArrayList<>();
    checkTypeResolution(recommendations, score);
    checkDynamicScore(recommendations, score, summary);
    checkLombokAnnotations(recommendations, score, summary);
    checkPreprocessStatus(recommendations, score);
    checkClasspathDetection(recommendations, score);
    if (recommendations.isEmpty()) {
      recommendations.add(MessageSource.getMessage("analysis.quality_report.recommendation.none"));
    }
    return recommendations;
  }

  private void checkTypeResolution(final List<String> recommendations, final QualityScore score) {
    if (score.typeResolutionRate() < 0.8) {
      recommendations.add(
          MessageSource.getMessage(
              "analysis.quality_report.recommendation.low_type_resolution",
              String.format("%.1f%%", score.typeResolutionRate() * 100)));
    }
  }

  private void checkDynamicScore(
      final List<String> recommendations,
      final QualityScore score,
      final Map<String, Object> summary) {
    if (score.dynamicFeatureScore() > 20) {
      recommendations.add(
          MessageSource.getMessage(
              "analysis.quality_report.recommendation.high_dynamic_score",
              score.dynamicFeatureScore()));
      final List<String> focusTargets = extractFocusTargets(extractTopDynamicFiles(summary));
      if (!focusTargets.isEmpty()) {
        recommendations.add(
            MessageSource.getMessage(
                "analysis.quality_report.recommendation.focus_dynamic_hotspots",
                String.join(", ", focusTargets)));
      }
    }
  }

  private void checkLombokAnnotations(
      final List<String> recommendations,
      final QualityScore score,
      final Map<String, Object> summary) {
    if ("OFF".equals(score.preprocess().mode())) {
      final Map<String, Object> annotations = getMap(summary, "annotations");
      final Object top = annotations.get("top");
      if (top instanceof List<?> topList) {
        // Safe check without unchecked cast
        final boolean hasLombok =
            topList.stream()
                .filter(Map.class::isInstance)
                .map(obj -> (Map<?, ?>) obj)
                .anyMatch(
                    a -> {
                      final Object name = a.get("name");
                      return name != null && name.toString().startsWith("lombok.");
                    });
        if (hasLombok) {
          recommendations.add(
              MessageSource.getMessage(
                  "analysis.quality_report.recommendation.lombok_preprocess_off"));
        }
      }
    }
  }

  private void checkPreprocessStatus(final List<String> recommendations, final QualityScore score) {
    final String status = score.preprocess().status();
    if ("FAILED".equals(status)) {
      recommendations.add(
          MessageSource.getMessage(
              "analysis.quality_report.recommendation.preprocess_failed_strict"));
    } else if ("FAILED_FALLBACK".equals(status)) {
      recommendations.add(
          MessageSource.getMessage(
              "analysis.quality_report.recommendation.preprocess_failed_fallback"));
    }
  }

  private void checkClasspathDetection(
      final List<String> recommendations, final QualityScore score) {
    if ("NO".equals(score.classpath().javaparser()) && "NO".equals(score.classpath().spoon())) {
      recommendations.add(
          MessageSource.getMessage("analysis.quality_report.recommendation.no_classpath"));
    }
  }

  private String generateMarkdown(
      final QualityScore score,
      final List<Map<String, Object>> topDynamicFiles,
      final List<Map<String, Object>> topDynamicTypes,
      final List<String> recommendations,
      final Map<String, Object> summary) {
    final StringBuilder md = new StringBuilder();
    md.append("# ").append(msg("analysis.quality_report.md.title")).append("\n\n");
    md.append(msg("analysis.quality_report.md.generated"))
        .append(": ")
        .append(java.time.Instant.now())
        .append("\n\n");
    appendSummary(md, score);
    appendPenalties(md, score);
    appendTopFiles(md, topDynamicFiles);
    appendTopSubtypes(md, topDynamicTypes);
    appendFocusTargets(md, topDynamicFiles);
    appendRecommendations(md, recommendations);
    appendTypeResolutionDetails(md, summary);
    appendTopAmbiguousExamples(md, summary);
    return md.toString();
  }

  private void appendSummary(final StringBuilder md, final QualityScore score) {
    md.append("## ").append(msg("analysis.quality_report.md.summary")).append("\n\n");
    md.append("| ")
        .append(msg("analysis.quality_report.md.metric"))
        .append(" | ")
        .append(msg("analysis.quality_report.md.value"))
        .append(" |\n");
    md.append("|--------|-------|\n");
    md.append("| **")
        .append(msg("analysis.quality_report.md.quality_score"))
        .append("** | ")
        .append(score.score())
        .append(" / 100 |\n");
    md.append("| ")
        .append(msg("analysis.quality_report.md.type_resolution_rate"))
        .append(" | ")
        .append(String.format("%.1f%%", score.typeResolutionRate() * 100))
        .append(" |\n");
    md.append("| ")
        .append(msg("analysis.quality_report.md.dynamic_feature_score"))
        .append(" | ")
        .append(score.dynamicFeatureScore())
        .append(" |\n");
    md.append("| ")
        .append(msg("analysis.quality_report.md.preprocess"))
        .append(" | ")
        .append(score.preprocess().mode())
        .append(" (")
        .append(score.preprocess().status())
        .append(") |\n");
    md.append("| ")
        .append(msg("analysis.quality_report.md.classpath"))
        .append(" | ")
        .append(score.classpath().javaparser())
        .append(" / ")
        .append(score.classpath().spoon())
        .append(" |\n");
    md.append("| ")
        .append(msg("analysis.quality_report.md.resolved_entries"))
        .append(" | ")
        .append(score.classpath().entries())
        .append(" |\n");
    md.append("\n");
  }

  private void appendPenalties(final StringBuilder md, final QualityScore score) {
    if (!score.penalties().isEmpty()) {
      md.append("### ").append(msg("analysis.quality_report.md.penalties")).append("\n\n");
      for (final var entry : score.penalties().entrySet()) {
        md.append("- ").append(entry.getKey()).append(": -").append(entry.getValue()).append("\n");
      }
      md.append("\n");
    }
  }

  private void appendTopFiles(
      final StringBuilder md, final List<Map<String, Object>> topDynamicFiles) {
    if (!topDynamicFiles.isEmpty()) {
      md.append("## ").append(msg("analysis.quality_report.md.top_dynamic_files")).append("\n\n");
      md.append("| ")
          .append(msg("analysis.quality_report.md.file"))
          .append(" | ")
          .append(msg("analysis.quality_report.md.count"))
          .append(" |\n");
      md.append("|------|-------|\n");
      for (final Map<String, Object> file : topDynamicFiles) {
        md.append("| ")
            .append(file.get("file"))
            .append(" | ")
            .append(file.get("count"))
            .append(" |\n");
      }
      md.append("\n");
    }
  }

  private void appendTopSubtypes(
      final StringBuilder md, final List<Map<String, Object>> topDynamicTypes) {
    if (!topDynamicTypes.isEmpty()) {
      md.append("## ").append(msg("analysis.quality_report.md.top_dynamic_types")).append("\n\n");
      md.append("| ")
          .append(msg("analysis.quality_report.md.subtype"))
          .append(" | ")
          .append(msg("analysis.quality_report.md.count"))
          .append(" |\n");
      md.append("|---------|-------|\n");
      for (final Map<String, Object> subtype : topDynamicTypes) {
        md.append("| ")
            .append(subtype.get("subtype"))
            .append(" | ")
            .append(subtype.get("count"))
            .append(" |\n");
      }
      md.append("\n");
    }
  }

  private void appendFocusTargets(
      final StringBuilder md, final List<Map<String, Object>> topDynamicFiles) {
    final List<String> focusTestTargets = extractFocusTargets(topDynamicFiles);
    if (!focusTestTargets.isEmpty()) {
      md.append("## ").append(msg("analysis.quality_report.md.focus_test_targets")).append("\n\n");
      for (final String target : focusTestTargets) {
        md.append("- ").append(target).append("\n");
      }
      md.append("\n");
    }
  }

  private void appendRecommendations(final StringBuilder md, final List<String> recommendations) {
    md.append("## ").append(msg("analysis.quality_report.md.recommendations")).append("\n\n");
    for (final String rec : recommendations) {
      md.append("- ").append(rec).append("\n");
    }
    md.append("\n");
  }

  private void appendTypeResolutionDetails(
      final StringBuilder md, final Map<String, Object> summary) {
    final Map<String, Object> typeResolution = getMap(summary, "type_resolution");
    if (!typeResolution.isEmpty()) {
      md.append("## ")
          .append(msg("analysis.quality_report.md.type_resolution_details"))
          .append("\n\n");
      md.append("| ")
          .append(msg("analysis.quality_report.md.metric"))
          .append(" | ")
          .append(msg("analysis.quality_report.md.value"))
          .append(" |\n");
      md.append("|--------|-------|\n");
      md.append("| ")
          .append(msg("analysis.quality_report.md.total"))
          .append(" | ")
          .append(typeResolution.get("total"))
          .append(" |\n");
      md.append("| ")
          .append(msg("analysis.quality_report.md.resolved"))
          .append(" | ")
          .append(typeResolution.get("resolved"))
          .append(" |\n");
      md.append("| ")
          .append(msg("analysis.quality_report.md.unresolved"))
          .append(" | ")
          .append(typeResolution.get("unresolved"))
          .append(" |\n");
      if (typeResolution.containsKey("ambiguous")) {
        md.append("| ")
            .append(msg("analysis.quality_report.md.ambiguous"))
            .append(" | ")
            .append(typeResolution.get("ambiguous"))
            .append(" |\n");
      }
      md.append("\n");
    }
  }

  private void appendTopAmbiguousExamples(
      final StringBuilder md, final Map<String, Object> summary) {
    final Object examples = summary.get(KEY_TOP_AMBIGUOUS_EXAMPLES);
    if (examples instanceof List<?> list && !list.isEmpty()) {
      md.append("## ")
          .append(msg("analysis.quality_report.md.top_ambiguous_examples"))
          .append("\n\n");
      for (final Object example : list) {
        md.append("- ").append(example).append("\n");
      }
      md.append("\n");
    }
  }

  private Map<String, Object> getMap(final Map<String, Object> map, final String key) {
    final Object value = map.get(key);
    return collectionService.toMap(value);
  }

  private int getResolvedCount(final Map<String, Object> perSource, final String key) {
    final Map<String, Object> source = getMap(perSource, key);
    final Object resolved = source.get("resolved");
    if (!(resolved instanceof Number number)) {
      return 0;
    }
    return number.intValue();
  }

  private String getString(
      final Map<String, Object> map, final String key, final String defaultValue) {
    final Object value = map.get(key);
    return value != null ? value.toString() : defaultValue;
  }

  private List<String> extractFocusTargets(final List<Map<String, Object>> topDynamicFiles) {
    if (topDynamicFiles.isEmpty()) {
      return List.of();
    }
    final List<String> targets = new ArrayList<>();
    for (final Map<String, Object> entry : topDynamicFiles) {
      final Object file = entry.get("file");
      if (file instanceof String filePath && !filePath.isBlank()) {
        targets.add(filePath);
      }
    }
    return targets;
  }

  private String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
