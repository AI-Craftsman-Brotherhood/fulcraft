package com.craftsmanbro.fulcraft.plugins.reporting.model;

import com.craftsmanbro.fulcraft.infrastructure.json.contract.JsonServicePort;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.DefaultJsonService;
import java.util.ArrayList;
import java.util.List;

/** Converts external task/result payloads into feature-layer neutral models. */
public final class FeatureModelMapper {

  private static final JsonServicePort JSON_SERVICE = new DefaultJsonService();

  private FeatureModelMapper() {
    // Utility class.
  }

  public static TaskRecord toTaskRecord(final Object raw) {
    return convert(raw, TaskRecord.class);
  }

  public static GenerationTaskResult toGenerationTaskResult(final Object raw) {
    return convert(raw, GenerationTaskResult.class);
  }

  public static GenerationSummary toGenerationSummary(final Object raw) {
    return convert(raw, GenerationSummary.class);
  }

  public static DynamicSelectionReport toDynamicSelectionReport(final Object raw) {
    return convert(raw, DynamicSelectionReport.class);
  }

  public static FixErrorHistory toFixErrorHistory(final Object raw) {
    return convert(raw, FixErrorHistory.class);
  }

  public static List<TaskRecord> toTaskRecords(final Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    return toTaskRecords(list);
  }

  public static List<TaskRecord> toTaskRecords(final List<?> list) {
    final List<TaskRecord> converted = new ArrayList<>();
    for (final Object entry : list) {
      final TaskRecord task = toTaskRecord(entry);
      if (task != null) {
        converted.add(task);
      }
    }
    return List.copyOf(converted);
  }

  public static List<GenerationTaskResult> toGenerationTaskResults(final List<?> list) {
    final List<GenerationTaskResult> converted = new ArrayList<>();
    for (final Object entry : list) {
      final GenerationTaskResult result = toGenerationTaskResult(entry);
      if (result != null) {
        converted.add(result);
      }
    }
    return List.copyOf(converted);
  }

  private static <T> T convert(final Object raw, final Class<T> type) {
    return JSON_SERVICE.convert(raw, type);
  }
}
