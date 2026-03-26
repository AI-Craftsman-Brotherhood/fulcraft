package com.craftsmanbro.fulcraft.plugins.analysis.io;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.json.contract.JsonServicePort;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.DefaultJsonService;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic.DynamicFeatureDetector;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic.DynamicResolutionApplier;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic.DynamicResolver;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.index.ProjectSymbolIndex;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess.SourcePreprocessor;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ResolutionStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service responsible for saving analysis results.
 *
 * <p>This service handles persistence of:
 *
 * <ul>
 *   <li>Analysis results (JSON per class)
 *   <li>Analyzed file lists
 *   <li>Type resolution summaries
 *   <li>Dynamic features
 *   <li>Dynamic resolutions
 *   <li>Preprocess results
 * </ul>
 */
public class AnalysisResultWriter {

  private static final String TYPE_RESOLUTION_SUMMARY_FILE = "type_resolution_summary.json";

  private static final String ANALYSIS_FILE_PREFIX = "analysis_";

  private static final String ANALYSIS_FILE_SUFFIX = ".json";

  private static final String SOURCE_JAVAPARSER = "javaparser";

  private static final String SOURCE_SPOON = "spoon";

  private static final String SOURCE_OTHER = "other";

  private static final String KEY_RESOLVED = "resolved";

  private static final String KEY_UNRESOLVED = "unresolved";

  private static final String KEY_AMBIGUOUS = "ambiguous";

  private static final String KEY_RESOLUTION_RATE = "resolution_rate";

  private static final String KEY_COUNT = "count";

  private final JsonServicePort jsonService;

  public AnalysisResultWriter() {
    this.jsonService = new DefaultJsonService();
  }

  public AnalysisResultWriter(final JsonServicePort jsonService) {
    this.jsonService =
        Objects.requireNonNull(
            jsonService,
            MessageSource.getMessage(
                "analysis.common.error.argument_null", "jsonService must not be null"));
  }

  /**
   * Saves analysis result to JSON files (one per class).
   *
   * @param result the analysis result to save
   * @param outputDir the analysis output directory
   * @param projectRoot the project root directory
   * @param projectId the project ID for organizing output
   */
  public void saveAnalysisResult(
      final AnalysisResult result,
      final Path outputDir,
      final Path projectRoot,
      final String projectId) {
    if (!saveAnalysisShardsInternal(result, outputDir, projectId)) {
      return;
    }
    // Save additional artifacts
    saveTypeResolutionSummary(result, outputDir, projectRoot);
  }

  /**
   * Saves analysis result shards only (without updating summary artifacts).
   *
   * @param result the analysis result to save
   * @param outputDir the analysis output directory
   * @param projectId the project ID for organizing output
   */
  public void saveAnalysisShards(
      final AnalysisResult result, final Path outputDir, final String projectId) {
    saveAnalysisShardsInternal(result, outputDir, projectId);
  }

  private boolean saveAnalysisShardsInternal(
      final AnalysisResult result, final Path outputDir, final String projectId) {
    try {
      Files.createDirectories(outputDir);
      final var classes = result.getClasses();
      if (classes.isEmpty()) {
        Logger.info(MessageSource.getMessage("analysis.io.writer.no_classes_to_save"));
        return false;
      }
      for (final var clazz : classes) {
        if (clazz == null) {
          continue;
        }
        final var singleResult = new AnalysisResult(projectId);
        singleResult.setCommitHash(result.getCommitHash());
        singleResult.setClasses(List.of(clazz));
        singleResult.setAnalysisErrors(result.getAnalysisErrors());
        final Path outputFile = buildAnalysisShardPath(outputDir, clazz);
        final Path parent = outputFile.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        jsonService.writeToFile(outputFile, singleResult);
      }
      Logger.debug(
          MessageSource.getMessage(
              "analysis.io.writer.analysis_shards_saved", classes.size(), outputDir));
      return true;
    } catch (IOException e) {
      Logger.warn(
          MessageSource.getMessage(
              "analysis.io.writer.save_analysis_result_failed", e.getMessage()));
      return false;
    }
  }

  /**
   * Saves the list of analyzed files for reproducibility verification.
   *
   * @param result the analysis result
   * @param outputDir the analysis output directory
   * @param projectRoot the project root directory
   */
  public void saveAnalyzedFileList(
      final AnalysisResult result, final Path outputDir, final Path projectRoot) {
    try {
      Files.createDirectories(outputDir);
      final Path fileListPath = outputDir.resolve("analysis_files.txt");
      final List<String> filePaths =
          result.getClasses().stream()
              .map(ClassInfo::getFilePath)
              .filter(f -> f != null && !"unknown".equals(f))
              .distinct()
              .sorted()
              .map(
                  f ->
                      projectRoot
                          .resolve("src/main/java")
                          .resolve(f)
                          .toAbsolutePath()
                          .normalize()
                          .toString())
              .toList();
      Files.write(fileListPath, filePaths);
      Logger.debug(MessageSource.getMessage("analysis.io.writer.file_list_saved", fileListPath));
    } catch (IOException e) {
      Logger.warn(
          MessageSource.getMessage(
              "analysis.io.writer.save_analyzed_file_list_failed", e.getMessage()));
    }
  }

  /**
   * Saves type resolution summary for observability.
   *
   * @param result the analysis result
   * @param outputDir the analysis output directory
   * @param projectRoot the project root directory
   */
  public void saveTypeResolutionSummary(
      final AnalysisResult result, final Path outputDir, final Path projectRoot) {
    try {
      final TypeResolutionStats stats = collectTypeResolutionStats(result);
      final Path summaryPath = writeTypeResolutionSummary(stats, outputDir, projectRoot);
      Logger.debug(
          MessageSource.getMessage(
              "analysis.io.writer.type_resolution_saved",
              summaryPath,
              stats.getTotalResolved(),
              stats.getTotalUnresolved(),
              String.format("%.2f", stats.getResolutionRate())));
    } catch (IOException e) {
      Logger.warn(
          MessageSource.getMessage(
              "analysis.io.writer.save_type_resolution_summary_failed", e.getMessage()));
    }
  }

  /**
   * Saves dynamic features detected from analysis result.
   *
   * @param result the analysis result
   * @param outputDir the analysis output directory
   * @param projectRoot the project root directory
   */
  public void saveDynamicFeatures(
      final AnalysisResult result, final Path outputDir, final Path projectRoot) {
    try {
      final var detector = new DynamicFeatureDetector();
      detector.detectFromAnalysisResult(result.getClasses(), projectRoot);
      final var events = detector.getEvents();
      Files.createDirectories(outputDir);
      // Write events to JSONL
      final Path jsonlPath = outputDir.resolve("dynamic_features.jsonl");
      try (var writer = Files.newBufferedWriter(jsonlPath)) {
        for (final var event : events) {
          writer.write(jsonService.toJson(event));
          writer.newLine();
        }
      }
      Logger.debug(
          MessageSource.getMessage(
              "analysis.io.writer.dynamic_features_saved", jsonlPath, events.size()));
      // Update summary with dynamic features
      updateSummaryWithDynamicFeatures(outputDir, detector, events.size());
    } catch (IOException e) {
      Logger.warn(
          MessageSource.getMessage(
              "analysis.io.writer.save_dynamic_features_failed", e.getMessage()));
    }
  }

  /**
   * Resolves and saves dynamic resolutions (Class.forName, getMethod, ServiceLoader).
   *
   * @param result the analysis result
   * @param outputDir the analysis output directory
   * @param projectRoot the project root directory
   * @param config the configuration
   * @param symbolIndex the project symbol index
   * @param externalConfigValues external config values for resolution
   */
  public void saveDynamicResolutions(
      final AnalysisResult result,
      final Path outputDir,
      final Path projectRoot,
      final Config config,
      final ProjectSymbolIndex symbolIndex,
      final Map<String, String> externalConfigValues) {
    try {
      final var resolver = new DynamicResolver();
      if (symbolIndex != null) {
        resolver.setProjectSymbolIndex(symbolIndex);
      }
      if (externalConfigValues != null && !externalConfigValues.isEmpty()) {
        resolver.setExternalConfigValues(externalConfigValues);
      }
      // Extract config values
      boolean enableInterprocedural = false;
      int callsiteLimit = 20;
      boolean debugDynamicResolution = false;
      boolean experimentalCandidateEnum = false;
      if (config.getAnalysis() != null) {
        enableInterprocedural = config.getAnalysis().getEnableInterproceduralResolution();
        callsiteLimit = config.getAnalysis().getInterproceduralCallsiteLimit();
        debugDynamicResolution = config.getAnalysis().getDebugDynamicResolution();
        experimentalCandidateEnum = config.getAnalysis().getExperimentalCandidateEnum();
      }
      resolver.resolve(
          result,
          projectRoot,
          enableInterprocedural,
          callsiteLimit,
          debugDynamicResolution,
          experimentalCandidateEnum);
      final var resolutions = resolver.getResolutions();
      // Attach resolutions to MethodInfo
      attachResolutionsToMethods(result, resolutions);
      // Save resolutions to file
      Files.createDirectories(outputDir);
      final Path jsonlPath = outputDir.resolve("dynamic_resolutions.jsonl");
      try (var writer = Files.newBufferedWriter(jsonlPath)) {
        for (final var resolution : resolutions) {
          writer.write(jsonService.toJson(resolution));
          writer.newLine();
        }
      }
      Logger.debug(
          MessageSource.getMessage(
              "analysis.io.writer.dynamic_resolutions_saved", jsonlPath, resolutions.size()));
      // Update summary with resolutions
      updateSummaryWithResolutions(outputDir, resolver, resolutions.size());
    } catch (IOException e) {
      Logger.warn(
          MessageSource.getMessage(
              "analysis.io.writer.save_dynamic_resolutions_failed", e.getMessage()));
    }
  }

  /**
   * Saves preprocess result to analysis summary.
   *
   * @param preprocessResult the preprocessing result
   * @param outputDir the analysis output directory
   * @param projectRoot the project root directory
   * @param config the configuration
   */
  public void savePreprocessResult(
      final SourcePreprocessor.Result preprocessResult,
      final Path outputDir,
      final Path projectRoot,
      final Config config) {
    try {
      Files.createDirectories(outputDir);
      final Path summaryPath = outputDir.resolve(TYPE_RESOLUTION_SUMMARY_FILE);
      final LinkedHashMap<String, Object> summary = loadOrCreateSummary(summaryPath);
      // Get preprocess config
      String mode = "OFF";
      String workDir = ".utg/preprocess";
      if (config.getAnalysis() != null && config.getAnalysis().getPreprocess() != null) {
        mode = config.getAnalysis().getPreprocess().getMode();
        workDir = config.getAnalysis().getPreprocess().getWorkDir();
      }
      summary.put("preprocess", preprocessResult.toMap(mode, projectRoot.resolve(workDir)));
      jsonService.writeToFile(summaryPath, summary);
      Logger.debug(
          MessageSource.getMessage(
              "analysis.io.writer.preprocess_summary_saved",
              preprocessResult.getStatus(),
              preprocessResult.getToolUsed(),
              preprocessResult.getDurationMs()));
    } catch (IOException e) {
      Logger.warn(
          MessageSource.getMessage(
              "analysis.io.writer.save_preprocess_result_failed", e.getMessage()));
    }
  }

  // Private helper methods
  private TypeResolutionStats collectTypeResolutionStats(final AnalysisResult result) {
    final TypeResolutionStats stats = new TypeResolutionStats();
    for (final var clazz : result.getClasses()) {
      if (clazz == null) {
        continue;
      }
      for (final var method : clazz.getMethods()) {
        for (final var ref : method.getCalledMethodRefs()) {
          stats.update(ref);
        }
      }
    }
    return stats;
  }

  private Path buildAnalysisShardPath(final Path outputDir, final ClassInfo clazz) {
    String fqn = clazz == null ? null : clazz.getFqn();
    if (fqn == null || fqn.isBlank()) {
      fqn = ClassInfo.UNKNOWN_CLASS;
    }
    final String[] parts = fqn.split("\\.");
    Path dir = outputDir;
    if (parts.length > 1) {
      for (int i = 0; i < parts.length - 1; i++) {
        final String segment = sanitizePathSegment(parts[i]);
        if (!segment.isEmpty()) {
          dir = dir.resolve(segment);
        }
      }
    }
    String classSegment = sanitizePathSegment(parts[parts.length - 1]);
    if (classSegment.isEmpty()) {
      classSegment = ClassInfo.UNKNOWN_CLASS;
    }
    return dir.resolve(ANALYSIS_FILE_PREFIX + classSegment + ANALYSIS_FILE_SUFFIX);
  }

  private String sanitizePathSegment(final String segment) {
    if (segment == null || segment.isBlank()) {
      return "";
    }
    return segment.replace('/', '_').replace('\\', '_');
  }

  private Path writeTypeResolutionSummary(
      final TypeResolutionStats stats, final Path outputDir, final Path projectRoot)
      throws IOException {
    Files.createDirectories(outputDir);
    final Path summaryPath = outputDir.resolve(TYPE_RESOLUTION_SUMMARY_FILE);
    final var summary = buildTypeResolutionSummary(stats, projectRoot);
    jsonService.writeToFile(summaryPath, summary);
    return summaryPath;
  }

  private LinkedHashMap<String, Object> buildTypeResolutionSummary(
      final TypeResolutionStats stats, final Path projectRoot) {
    final var summary = new LinkedHashMap<String, Object>();
    summary.put("timestamp", java.time.Instant.now().toString());
    summary.put("project_root", projectRoot.toAbsolutePath().toString());
    final var typeResolution = new LinkedHashMap<String, Object>();
    typeResolution.put("total", stats.getTotal());
    typeResolution.put(KEY_RESOLVED, stats.getTotalResolved());
    typeResolution.put(KEY_UNRESOLVED, stats.getTotalUnresolved());
    typeResolution.put(KEY_AMBIGUOUS, stats.getTotalAmbiguous());
    typeResolution.put("partial", 0);
    typeResolution.put(KEY_RESOLUTION_RATE, Math.round(stats.getResolutionRate() * 100.0) / 100.0);
    summary.put("type_resolution", typeResolution);
    summary.put("per_source", buildPerSourceMetrics(stats));
    summary.put("top_unresolved_examples", stats.getUnresolvedExamples());
    summary.put("top_ambiguous_examples", stats.getAmbiguousExamples());
    return summary;
  }

  private LinkedHashMap<String, Object> buildPerSourceMetrics(final TypeResolutionStats stats) {
    final var perSource = new LinkedHashMap<String, Object>();
    perSource.put(
        SOURCE_JAVAPARSER,
        buildSourceMetrics(
            stats.getJavaparserResolved(),
            stats.getJavaparserUnresolved(),
            stats.getJavaparserAmbiguous()));
    perSource.put(
        SOURCE_SPOON,
        buildSourceMetrics(
            stats.getSpoonResolved(), stats.getSpoonUnresolved(), stats.getSpoonAmbiguous()));
    if (stats.getOtherResolved() > 0
        || stats.getOtherUnresolved() > 0
        || stats.getOtherAmbiguous() > 0) {
      perSource.put(
          SOURCE_OTHER,
          buildSourceMetrics(
              stats.getOtherResolved(), stats.getOtherUnresolved(), stats.getOtherAmbiguous()));
    }
    return perSource;
  }

  private LinkedHashMap<String, Object> buildSourceMetrics(
      final int resolved, final int unresolved, final int ambiguous) {
    final var sourceMetrics = new LinkedHashMap<String, Object>();
    sourceMetrics.put(KEY_RESOLVED, resolved);
    sourceMetrics.put(KEY_UNRESOLVED, unresolved);
    sourceMetrics.put(KEY_AMBIGUOUS, ambiguous);
    final int total = resolved + unresolved + ambiguous;
    sourceMetrics.put(
        KEY_RESOLUTION_RATE,
        total > 0 ? Math.round((double) resolved / total * 100.0) / 100.0 : 0.0);
    return sourceMetrics;
  }

  private void updateSummaryWithDynamicFeatures(
      final Path outputDir, final DynamicFeatureDetector detector, final int eventCount)
      throws IOException {
    final Path summaryPath = outputDir.resolve(TYPE_RESOLUTION_SUMMARY_FILE);
    final LinkedHashMap<String, Object> summary = loadOrCreateSummary(summaryPath);
    final var dynamicFeatures = new LinkedHashMap<String, Object>();
    dynamicFeatures.put("total_events", eventCount);
    dynamicFeatures.put("dynamic_score", detector.calculateDynamicScore());
    // by_type
    final var byType = new LinkedHashMap<String, Long>();
    for (final var entry : detector.countByType().entrySet()) {
      byType.put(entry.getKey().wireName(), entry.getValue());
    }
    dynamicFeatures.put("by_type", byType);
    // by_severity
    final var bySeverity = new LinkedHashMap<String, Long>();
    for (final var entry : detector.countBySeverity().entrySet()) {
      bySeverity.put(entry.getKey().name(), entry.getValue());
    }
    dynamicFeatures.put("by_severity", bySeverity);
    // top_files
    final var topFiles = new ArrayList<Map<String, Object>>();
    for (final var entry : detector.getTopFiles(5)) {
      final var file = new LinkedHashMap<String, Object>();
      file.put("file", entry.getKey());
      file.put(KEY_COUNT, entry.getValue());
      topFiles.add(file);
    }
    dynamicFeatures.put("top_files", topFiles);
    // top_subtypes
    final var topSubtypes = new ArrayList<Map<String, Object>>();
    for (final var entry : detector.getTopSubtypes(5)) {
      final var subtype = new LinkedHashMap<String, Object>();
      subtype.put("subtype", entry.getKey());
      subtype.put(KEY_COUNT, entry.getValue());
      topSubtypes.add(subtype);
    }
    dynamicFeatures.put("top_subtypes", topSubtypes);
    summary.put("dynamic_features", dynamicFeatures);
    // Annotations summary
    final var annotations = new LinkedHashMap<String, Object>();
    final var annotationCounts = detector.getAnnotationCounts();
    annotations.put("unique_count", annotationCounts.size());
    final var topAnnotations = new ArrayList<Map<String, Object>>();
    for (final var entry : detector.getTopAnnotations(20)) {
      final var ann = new LinkedHashMap<String, Object>();
      ann.put("name", entry.getKey());
      ann.put(KEY_COUNT, entry.getValue());
      topAnnotations.add(ann);
    }
    annotations.put("top", topAnnotations);
    summary.put("annotations", annotations);
    jsonService.writeToFile(summaryPath, summary);
    Logger.debug(
        MessageSource.getMessage(
            "analysis.io.writer.dynamic_features_summary_added",
            eventCount,
            detector.calculateDynamicScore()));
  }

  private void updateSummaryWithResolutions(
      final Path outputDir, final DynamicResolver resolver, final int resolutionCount)
      throws IOException {
    final Path summaryPath = outputDir.resolve(TYPE_RESOLUTION_SUMMARY_FILE);
    final LinkedHashMap<String, Object> summary = loadOrCreateSummary(summaryPath);
    final var dynamicResolutions = new LinkedHashMap<String, Object>();
    dynamicResolutions.put("total", resolutionCount);
    final var bySubtype = new LinkedHashMap<String, Long>();
    for (final var entry : resolver.countBySubtype().entrySet()) {
      bySubtype.put(entry.getKey(), entry.getValue());
    }
    dynamicResolutions.put("by_subtype", bySubtype);
    final var byTrustLevel = new LinkedHashMap<String, Long>();
    for (final var entry : resolver.countByTrustLevel().entrySet()) {
      byTrustLevel.put(entry.getKey(), entry.getValue());
    }
    dynamicResolutions.put("by_trust_level", byTrustLevel);
    dynamicResolutions.put(
        "average_confidence", Math.round(resolver.getAverageConfidence() * 100.0) / 100.0);
    summary.put("dynamic_resolutions", dynamicResolutions);
    jsonService.writeToFile(summaryPath, summary);
    Logger.debug(
        MessageSource.getMessage(
            "analysis.io.writer.dynamic_resolutions_summary_added",
            resolutionCount,
            resolver.getAverageConfidence()));
  }

  private void attachResolutionsToMethods(
      final AnalysisResult result, final List<DynamicResolution> resolutions) {
    DynamicResolutionApplier.apply(result, resolutions);
  }

  private LinkedHashMap<String, Object> loadOrCreateSummary(final Path summaryPath)
      throws IOException {
    return jsonService.readMapFromFile(summaryPath);
  }

  /** Helper class for collecting type resolution statistics. */
  private static final class TypeResolutionStats {

    private int javaparserResolved;

    private int javaparserUnresolved;

    private int javaparserAmbiguous;

    private int spoonResolved;

    private int spoonUnresolved;

    private int spoonAmbiguous;

    private int otherResolved;

    private int otherUnresolved;

    private int otherAmbiguous;

    private final List<String> unresolvedExamples = new ArrayList<>();

    private final List<String> ambiguousExamples = new ArrayList<>();

    void update(final CalledMethodRef ref) {
      if (ref == null || ref.getStatus() == null) {
        return;
      }
      final String source = ref.getSource();
      if (ResolutionStatus.RESOLVED.equals(ref.getStatus())) {
        incrementResolved(source);
        return;
      }
      if (ResolutionStatus.AMBIGUOUS.equals(ref.getStatus())) {
        incrementAmbiguous(source);
        if (ambiguousExamples.size() < 10 && ref.getRaw() != null) {
          ambiguousExamples.add(ref.getRaw());
        }
        return;
      }
      if (ResolutionStatus.UNRESOLVED.equals(ref.getStatus())) {
        incrementUnresolved(source);
        if (unresolvedExamples.size() < 10 && ref.getRaw() != null) {
          unresolvedExamples.add(ref.getRaw());
        }
      }
    }

    int getJavaparserResolved() {
      return javaparserResolved;
    }

    int getJavaparserUnresolved() {
      return javaparserUnresolved;
    }

    int getJavaparserAmbiguous() {
      return javaparserAmbiguous;
    }

    int getSpoonResolved() {
      return spoonResolved;
    }

    int getSpoonUnresolved() {
      return spoonUnresolved;
    }

    int getSpoonAmbiguous() {
      return spoonAmbiguous;
    }

    int getOtherResolved() {
      return otherResolved;
    }

    int getOtherUnresolved() {
      return otherUnresolved;
    }

    int getOtherAmbiguous() {
      return otherAmbiguous;
    }

    int getTotalResolved() {
      return javaparserResolved + spoonResolved + otherResolved;
    }

    int getTotalUnresolved() {
      return javaparserUnresolved + spoonUnresolved + otherUnresolved;
    }

    int getTotalAmbiguous() {
      return javaparserAmbiguous + spoonAmbiguous + otherAmbiguous;
    }

    int getTotal() {
      return getTotalResolved() + getTotalUnresolved() + getTotalAmbiguous();
    }

    double getResolutionRate() {
      final int total = getTotal();
      return total > 0 ? (double) getTotalResolved() / total : 0.0;
    }

    List<String> getUnresolvedExamples() {
      return unresolvedExamples;
    }

    List<String> getAmbiguousExamples() {
      return ambiguousExamples;
    }

    private void incrementResolved(final String source) {
      if (SOURCE_JAVAPARSER.equalsIgnoreCase(source)) {
        javaparserResolved++;
      } else if (SOURCE_SPOON.equalsIgnoreCase(source)) {
        spoonResolved++;
      } else {
        otherResolved++;
      }
    }

    private void incrementUnresolved(final String source) {
      if (SOURCE_JAVAPARSER.equalsIgnoreCase(source)) {
        javaparserUnresolved++;
      } else if (SOURCE_SPOON.equalsIgnoreCase(source)) {
        spoonUnresolved++;
      } else {
        otherUnresolved++;
      }
    }

    private void incrementAmbiguous(final String source) {
      if (SOURCE_JAVAPARSER.equalsIgnoreCase(source)) {
        javaparserAmbiguous++;
      } else if (SOURCE_SPOON.equalsIgnoreCase(source)) {
        spoonAmbiguous++;
      } else {
        otherAmbiguous++;
      }
    }
  }
}
