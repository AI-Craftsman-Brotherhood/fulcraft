package com.craftsmanbro.fulcraft.plugins.analysis.flow;

import com.craftsmanbro.fulcraft.plugins.analysis.core.service.detector.BrittlenessDetectionService;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BrittlenessSignal;
import java.util.List;
import java.util.Objects;

/**
 * Flow responsible for detecting brittleness signals in code.
 *
 * <p>This flow delegates to {@link BrittlenessDetectionService} and keeps the pipeline wiring
 * stable.
 */
public class BrittlenessDetectionFlow {

  private final BrittlenessDetectionService brittlenessDetectionService;

  public BrittlenessDetectionFlow() {
    this.brittlenessDetectionService = new BrittlenessDetectionService();
  }

  /** Constructor for testing with a custom service. */
  BrittlenessDetectionFlow(final BrittlenessDetectionService brittlenessDetectionService) {
    this.brittlenessDetectionService = Objects.requireNonNull(brittlenessDetectionService);
  }

  /**
   * Detects brittleness signals in the analysis result.
   *
   * @param result the analysis result to scan
   * @return true if any brittleness was detected
   */
  public boolean detectBrittleness(final AnalysisResult result) {
    return brittlenessDetectionService.detectBrittleness(result);
  }

  /**
   * Gets a summary of brittleness detection results.
   *
   * @param result the analysis result to analyze
   * @return the brittleness summary
   */
  public BrittlenessSummary getSummary(final AnalysisResult result) {
    final BrittlenessDetectionService.BrittlenessSummary summary =
        brittlenessDetectionService.getSummary(result);
    return new BrittlenessSummary(
        summary.brittleMethodCount(),
        summary.totalMethodCount(),
        summary.brittleMethods().stream()
            .map(
                method ->
                    new BrittleMethod(
                        method.classFqn(),
                        method.methodName(),
                        method.signature(),
                        method.signals()))
            .toList());
  }

  /**
   * Logs brittleness detection results.
   *
   * @param result the analysis result
   */
  public void logDetectionResults(final AnalysisResult result) {
    brittlenessDetectionService.logDetectionResults(result);
  }

  /** Represents a method with brittleness signals. */
  public record BrittleMethod(
      String classFqn, String methodName, String signature, List<BrittlenessSignal> signals) {

    public BrittleMethod {
      signals = signals == null ? List.of() : List.copyOf(signals);
    }
  }

  /** Represents a summary of brittleness detection. */
  public record BrittlenessSummary(
      int brittleMethodCount, int totalMethodCount, List<BrittleMethod> brittleMethods) {

    public BrittlenessSummary {
      brittleMethods = brittleMethods == null ? List.of() : List.copyOf(brittleMethods);
    }

    public double brittlenessRate() {
      if (totalMethodCount == 0) {
        return 0.0;
      }
      return (double) brittleMethodCount / totalMethodCount;
    }

    public boolean hasBrittleness() {
      return brittleMethodCount > 0;
    }
  }
}
