package com.craftsmanbro.fulcraft.plugins.analysis.core.service.detector;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BrittlenessSignal;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service responsible for detecting brittleness signals in code.
 *
 * <p>This service uses heuristics to detect non-deterministic code patterns that may cause flaky
 * tests, including:
 *
 * <ul>
 *   <li>Time-dependent code (System.currentTimeMillis, Instant.now)
 *   <li>Random number generation
 *   <li>Environment-dependent code
 *   <li>Concurrency patterns
 *   <li>I/O operations
 *   <li>Collection ordering issues
 * </ul>
 */
public class BrittlenessDetectionService {

  private static final int TOP_BRITTLE_METHOD_LOG_LIMIT = 5;

  private final BrittlenessHeuristics heuristics;

  public BrittlenessDetectionService() {
    this.heuristics = new BrittlenessHeuristics();
  }

  /** Constructor for testing with mock heuristics. */
  BrittlenessDetectionService(final BrittlenessHeuristics heuristics) {
    this.heuristics = Objects.requireNonNull(heuristics);
  }

  /**
   * Detects brittleness signals in the analysis result.
   *
   * @param result the analysis result to scan
   * @return true if any brittleness was detected
   */
  public boolean detectBrittleness(final AnalysisResult result) {
    Objects.requireNonNull(
        result,
        MessageSource.getMessage("analysis.common.error.argument_null", "result must not be null"));
    return heuristics.apply(result);
  }

  /**
   * Gets a summary of brittleness detection results.
   *
   * @param result the analysis result to analyze
   * @return the brittleness summary
   */
  public BrittlenessSummary getSummary(final AnalysisResult result) {
    Objects.requireNonNull(
        result,
        MessageSource.getMessage("analysis.common.error.argument_null", "result must not be null"));
    final List<BrittleMethod> brittleMethods = new ArrayList<>();
    int totalMethods = 0;
    for (final ClassInfo clazz : result.getClasses()) {
      if (clazz == null) {
        continue;
      }
      for (final MethodInfo method : clazz.getMethods()) {
        if (method == null) {
          continue;
        }
        totalMethods++;
        final List<BrittlenessSignal> signals = method.getBrittlenessSignals();
        if (signals != null && !signals.isEmpty()) {
          brittleMethods.add(
              new BrittleMethod(clazz.getFqn(), method.getName(), method.getSignature(), signals));
        }
      }
    }
    return new BrittlenessSummary(brittleMethods.size(), totalMethods, brittleMethods);
  }

  /**
   * Logs brittleness detection results.
   *
   * @param result the analysis result
   */
  public void logDetectionResults(final AnalysisResult result) {
    final BrittlenessSummary summary = getSummary(result);
    if (summary.brittleMethodCount() == 0) {
      Logger.info(MessageSource.getMessage("analysis.brittleness.none"));
      return;
    }
    Logger.warn(
        MessageSource.getMessage(
            "analysis.brittleness.summary",
            summary.brittleMethodCount(),
            summary.totalMethodCount()));
    final List<BrittleMethod> brittleMethods = summary.brittleMethods();
    // Log top 5 brittle methods
    final int logCount = Math.min(TOP_BRITTLE_METHOD_LOG_LIMIT, brittleMethods.size());
    for (int i = 0; i < logCount; i++) {
      final BrittleMethod method = brittleMethods.get(i);
      Logger.warn(
          MessageSource.getMessage(
              "analysis.brittleness.method_entry",
              method.classFqn(),
              method.methodName(),
              method.signals()));
    }
    if (brittleMethods.size() > TOP_BRITTLE_METHOD_LOG_LIMIT) {
      Logger.warn(
          MessageSource.getMessage(
              "analysis.brittleness.more", brittleMethods.size() - TOP_BRITTLE_METHOD_LOG_LIMIT));
    }
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
