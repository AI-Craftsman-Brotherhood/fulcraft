package com.craftsmanbro.fulcraft.plugins.analysis.core.service.detector;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BrittlenessSignal;
import java.util.List;
import java.util.Objects;

/**
 * Facade for brittleness detection within the analysis package.
 *
 * <p>This class provides a stable API for detecting non-deterministic code patterns that may cause
 * flaky tests.
 */
public final class BrittlenessDetector {

  private static final List<BrittlenessSignal> ALL_SIGNAL_TYPES =
      List.of(BrittlenessSignal.values());

  private final BrittlenessHeuristics heuristics;

  private final BrittlenessDetectionService detectionService;

  public BrittlenessDetector() {
    this(new BrittlenessHeuristics());
  }

  BrittlenessDetector(final BrittlenessHeuristics heuristics) {
    this.heuristics =
        Objects.requireNonNull(
            heuristics,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "analysis.common.error.argument_null", "heuristics must not be null"));
    this.detectionService = new BrittlenessDetectionService(this.heuristics);
  }

  /**
   * Applies brittleness heuristics to the analysis result.
   *
   * @param result the analysis result
   * @return true if any brittleness was detected
   */
  public boolean apply(final AnalysisResult result) {
    Objects.requireNonNull(
        result,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "result must not be null"));
    return this.detectionService.detectBrittleness(result);
  }

  /** Known brittleness signal types. */
  public static List<BrittlenessSignal> getAllSignalTypes() {
    return ALL_SIGNAL_TYPES;
  }

  /**
   * Gets the underlying heuristics for compatibility.
   *
   * @return the underlying heuristics
   */
  public BrittlenessHeuristics unwrap() {
    return this.heuristics;
  }
}
