package com.craftsmanbro.fulcraft.kernel.pipeline.context;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.RunDirectories;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Holds the context and intermediate results across pipeline stages.
 *
 * <p>This class acts as a shared context that is passed between stages, allowing each stage to read
 * results from previous stages and store its own results for subsequent stages.
 *
 * <p>Thread-safety: This class is not thread-safe and is intended to be used from a single thread.
 */
public class RunContext {

  private final Path projectRoot;

  private final Config config;

  private final String runId;

  private final Path runDirectory;

  private final RunOptions options = new RunOptions();

  private final RunArtifacts artifacts = new RunArtifacts();

  private final RunMetadata metadata = new RunMetadata();

  private final RunDiagnostics diagnostics = new RunDiagnostics();

  /**
   * Create a new run context.
   *
   * @param projectRoot The root directory of the project
   * @param config The application configuration
   * @param runId A unique identifier for this run
   */
  public RunContext(final Path projectRoot, final Config config, final String runId) {
    this(projectRoot, config, runId, null);
  }

  /**
   * Create a new run context with a pre-resolved run directory.
   *
   * @param projectRoot The root directory of the project
   * @param config The application configuration
   * @param runId A unique identifier for this run
   * @param runDirectory The resolved run output directory (optional)
   */
  public RunContext(
      final Path projectRoot, final Config config, final String runId, final Path runDirectory) {
    this.projectRoot =
        Objects.requireNonNull(
            projectRoot,
            MessageSource.getMessage("kernel.common.error.argument_null", "projectRoot"));
    this.config =
        Objects.requireNonNull(
            config, MessageSource.getMessage("kernel.common.error.argument_null", "config"));
    this.runId =
        Objects.requireNonNull(
            runId, MessageSource.getMessage("kernel.common.error.argument_null", "runId"));
    this.runDirectory =
        runDirectory != null
            ? runDirectory
            : RunDirectories.resolveRunRoot(config, projectRoot, runId);
  }

  // --- Getters ---
  public Path getProjectRoot() {
    return projectRoot;
  }

  public Config getConfig() {
    return config;
  }

  public String getRunId() {
    return runId;
  }

  public Path getRunDirectory() {
    return runDirectory;
  }

  public boolean isDryRun() {
    return options.isDryRun();
  }

  public boolean isFailFast() {
    return options.isFailFast();
  }

  public boolean isShowSummary() {
    return options.isShowSummary();
  }

  public boolean isBrittlenessDetected() {
    return artifacts.isBrittlenessDetected();
  }

  public List<ReportTaskResult> getReportTaskResults() {
    return artifacts.getReportTaskResults();
  }

  public Map<String, Object> getMetadata() {
    return metadata.snapshot();
  }

  public List<String> getErrors() {
    return diagnostics.getErrors();
  }

  public List<String> getWarnings() {
    return diagnostics.getWarnings();
  }

  // --- Setters / Builders ---
  public RunContext withDryRun(final boolean dryRun) {
    options.setDryRun(dryRun);
    return this;
  }

  public RunContext withFailFast(final boolean failFast) {
    options.setFailFast(failFast);
    return this;
  }

  public RunContext withShowSummary(final boolean showSummary) {
    options.setShowSummary(showSummary);
    return this;
  }

  public void setBrittlenessDetected(final boolean brittlenessDetected) {
    artifacts.setBrittlenessDetected(brittlenessDetected);
  }

  public void setReportTaskResults(final List<ReportTaskResult> reportTaskResults) {
    artifacts.setReportTaskResults(reportTaskResults);
  }

  public void putMetadata(final String key, final Object value) {
    metadata.put(key, value);
  }

  public void removeMetadata(final String key) {
    metadata.remove(key);
  }

  public <T> Optional<T> getMetadata(final String key, final Class<T> type) {
    final Optional<T> typedValue = metadata.get(key, type);
    if (typedValue.isPresent()) {
      return typedValue;
    }
    final Object raw = metadataValue(key);
    if (raw != null) {
      warnMetadataTypeMismatch(key, type, raw.getClass());
    }
    return Optional.empty();
  }

  public <T> Optional<List<T>> getMetadataList(final String key, final Class<T> elementType) {
    Objects.requireNonNull(
        elementType, MessageSource.getMessage("kernel.common.error.argument_null", "elementType"));
    final Object raw = metadataValue(key);
    if (raw == null) {
      return Optional.empty();
    }
    if (!(raw instanceof List<?> values)) {
      warnMetadataTypeMismatch(key, List.class, raw.getClass());
      return Optional.empty();
    }
    final List<T> typedValues = new ArrayList<>(values.size());
    for (int i = 0; i < values.size(); i++) {
      final Object element = values.get(i);
      if (!elementType.isInstance(element)) {
        warnMetadataCollectionElementTypeMismatch(
            key, "list", elementType, i, describeMetadataElementType(element));
        return Optional.empty();
      }
      typedValues.add(elementType.cast(element));
    }
    return Optional.of(List.copyOf(typedValues));
  }

  public <T> Optional<Set<T>> getMetadataSet(final String key, final Class<T> elementType) {
    Objects.requireNonNull(
        elementType, MessageSource.getMessage("kernel.common.error.argument_null", "elementType"));
    final Object raw = metadataValue(key);
    if (raw == null) {
      return Optional.empty();
    }
    if (!(raw instanceof Set<?> values)) {
      warnMetadataTypeMismatch(key, Set.class, raw.getClass());
      return Optional.empty();
    }
    final Set<T> typedValues = new LinkedHashSet<>(Math.max(values.size(), 1));
    int index = 0;
    for (final Object element : values) {
      if (!elementType.isInstance(element)) {
        warnMetadataCollectionElementTypeMismatch(
            key, "set", elementType, index, describeMetadataElementType(element));
        return Optional.empty();
      }
      typedValues.add(elementType.cast(element));
      index++;
    }
    return Optional.of(Collections.unmodifiableSet(new LinkedHashSet<>(typedValues)));
  }

  public void addError(final String error) {
    diagnostics.addError(error);
  }

  public void addWarning(final String warning) {
    diagnostics.addWarning(warning);
  }

  public boolean hasErrors() {
    return diagnostics.hasErrors();
  }

  public boolean hasWarnings() {
    return diagnostics.hasWarnings();
  }

  public RunOptions options() {
    return options;
  }

  public RunArtifacts artifacts() {
    return artifacts;
  }

  public RunMetadata metadata() {
    return metadata;
  }

  public RunDiagnostics diagnostics() {
    return diagnostics;
  }

  private Object metadataValue(final String key) {
    return metadata.rawValue(key);
  }

  private void warnMetadataTypeMismatch(
      final String key, final Class<?> expectedType, final Class<?> actualType) {
    addWarning(
        MessageSource.getMessage(
            "kernel.run_context.warn.metadata_type_mismatch",
            key,
            expectedType.getName(),
            actualType.getName()));
  }

  private void warnMetadataCollectionElementTypeMismatch(
      final String key,
      final String collectionType,
      final Class<?> expectedElementType,
      final int index,
      final String actualElementType) {
    addWarning(
        MessageSource.getMessage(
            "kernel.run_context.warn.metadata_collection_element_type_mismatch",
            collectionType,
            key,
            index,
            expectedElementType.getName(),
            actualElementType));
  }

  private String describeMetadataElementType(final Object element) {
    return element == null ? "null" : element.getClass().getName();
  }
}
