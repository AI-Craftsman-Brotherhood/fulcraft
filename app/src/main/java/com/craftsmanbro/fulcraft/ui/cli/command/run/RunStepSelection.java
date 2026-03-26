package com.craftsmanbro.fulcraft.ui.cli.command.run;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

/** Value object for resolved run node options and related validations. */
public final class RunStepSelection {

  private final List<String> specificSteps;

  private final String fromStep;

  private final String toStep;

  private RunStepSelection(
      final List<String> specificSteps, final String fromStep, final String toStep) {
    this.specificSteps = specificSteps != null ? List.copyOf(specificSteps) : null;
    this.fromStep = fromStep;
    this.toStep = toStep;
  }

  public static RunStepSelection resolve(
      final List<String> specificSteps,
      final String fromStep,
      final String toStep,
      final List<String> defaultSteps) {
    List<String> resolvedSpecificSteps = normalizeList(specificSteps);
    final String normalizedFrom = normalizeNodeId(fromStep);
    final String normalizedTo = normalizeNodeId(toStep);
    if ((resolvedSpecificSteps == null || resolvedSpecificSteps.isEmpty())
        && normalizedFrom == null
        && normalizedTo == null) {
      resolvedSpecificSteps = normalizeList(defaultSteps);
    }
    return new RunStepSelection(resolvedSpecificSteps, normalizedFrom, normalizedTo);
  }

  public List<String> specificSteps() {
    return specificSteps;
  }

  public String fromStep() {
    return fromStep;
  }

  public String toStep() {
    return toStep;
  }

  public boolean isAnalysisReportOnly() {
    return hasExactSteps(SetConstants.ANALYZE_REPORT);
  }

  public boolean isAnalysisReportDocumentOnly() {
    return hasExactSteps(SetConstants.ANALYZE_REPORT_DOCUMENT);
  }

  private boolean hasExactSteps(final List<String> expectedSteps) {
    if (specificSteps == null || specificSteps.isEmpty()) {
      return false;
    }
    if (fromStep != null || toStep != null) {
      return false;
    }
    final LinkedHashSet<String> steps = new LinkedHashSet<>(specificSteps);
    return steps.size() == expectedSteps.size() && steps.containsAll(expectedSteps);
  }

  public void validate(final CommandSpec spec, final List<String> stepOrder) {
    Objects.requireNonNull(spec, "@Spec is required");
    Objects.requireNonNull(stepOrder, "stepOrder is required");
    if (specificSteps != null && (fromStep != null || toStep != null)) {
      throw new ParameterException(
          spec.commandLine(), MessageSource.getMessage("run.error.steps_from_to_conflict"));
    }
    if (fromStep != null && !stepOrder.contains(fromStep)) {
      throw new ParameterException(
          spec.commandLine(), MessageSource.getMessage("run.error.unsupported_from", fromStep));
    }
    if (toStep != null && !stepOrder.contains(toStep)) {
      throw new ParameterException(
          spec.commandLine(), MessageSource.getMessage("run.error.unsupported_to", toStep));
    }
    if (fromStep != null && toStep != null) {
      final int fromIndex = stepOrder.indexOf(fromStep);
      final int toIndex = stepOrder.indexOf(toStep);
      if (fromIndex > toIndex) {
        throw new ParameterException(
            spec.commandLine(), MessageSource.getMessage("run.error.from_after_to"));
      }
    }
    if (specificSteps != null && !specificSteps.isEmpty()) {
      for (final String nodeId : specificSteps) {
        if (!stepOrder.contains(nodeId)) {
          throw new ParameterException(
              spec.commandLine(), MessageSource.getMessage("run.error.unsupported_step", nodeId));
        }
      }
    }
  }

  public List<String> resolveNodesToRun(final List<String> stepOrder) {
    Objects.requireNonNull(stepOrder, "stepOrder is required");
    if (specificSteps != null && !specificSteps.isEmpty()) {
      return orderSpecificSteps(stepOrder);
    }
    if (fromStep == null && toStep == null) {
      return List.copyOf(stepOrder);
    }
    if (stepOrder.isEmpty()) {
      return List.of();
    }
    final String from = fromStep != null ? fromStep : stepOrder.get(0);
    final String to = toStep != null ? toStep : stepOrder.get(stepOrder.size() - 1);
    final int fromIndex = stepOrder.indexOf(from);
    final int toIndex = stepOrder.indexOf(to);
    if (fromIndex < 0 || toIndex < 0 || fromIndex > toIndex) {
      return List.of();
    }
    return List.copyOf(stepOrder.subList(fromIndex, toIndex + 1));
  }

  private List<String> orderSpecificSteps(final List<String> stepOrder) {
    if (stepOrder.isEmpty()) {
      return List.copyOf(specificSteps);
    }
    final LinkedHashSet<String> remaining = new LinkedHashSet<>(specificSteps);
    final List<String> ordered = new ArrayList<>(specificSteps.size());
    for (final String step : stepOrder) {
      if (remaining.remove(step)) {
        ordered.add(step);
      }
    }
    ordered.addAll(remaining);
    return List.copyOf(ordered);
  }

  private static List<String> normalizeList(final List<String> rawValues) {
    if (rawValues == null || rawValues.isEmpty()) {
      return null;
    }
    final LinkedHashSet<String> values = new LinkedHashSet<>();
    for (final String raw : rawValues) {
      final String normalized = normalizeNodeId(raw);
      if (normalized != null) {
        values.add(normalized);
      }
    }
    if (values.isEmpty()) {
      return null;
    }
    return List.copyOf(values);
  }

  private static String normalizeNodeId(final String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim().toLowerCase(Locale.ROOT);
  }

  private static final class SetConstants {
    private static final List<String> ANALYZE_REPORT = List.of("analyze", "report");
    private static final List<String> ANALYZE_REPORT_DOCUMENT =
        List.of("analyze", "report", "document");

    private SetConstants() {}
  }
}
