package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chain of sensitive data detectors.
 *
 * <p>Executes multiple detectors in sequence and merges their findings. When findings overlap, the
 * one with higher confidence is retained. The chain supports dynamic detector loading via
 * ServiceLoader for extensibility.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * DetectorChain chain = DetectorChain.builder()
 *         .add(new RegexDetector())
 *         .add(new DictionaryDetector())
 *         .add(new MlNerDetector(endpoint))
 *         .build();
 *
 * DetectionResult result = chain.detect(text, context);
 * }</pre>
 */
public final class DetectorChain {

  private static final Logger LOG = LoggerFactory.getLogger(DetectorChain.class);

  private final List<SensitiveDetector> detectors;

  private DetectorChain(final List<SensitiveDetector> detectors) {
    this.detectors = List.copyOf(detectors);
  }

  /**
   * Creates a builder for constructing a detector chain.
   *
   * @return New builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a chain with the standard detectors in default order.
   *
   * @return Default chain with regex and dictionary detectors
   */
  public static DetectorChain defaultChain() {
    return builder().addDefaults().build();
  }

  /**
   * Creates a chain by loading detectors via ServiceLoader.
   *
   * <p>This allows custom detectors to be provided as plugins via META-INF/services.
   *
   * @return Chain with all discovered detectors
   */
  public static DetectorChain fromServiceLoader() {
    final ServiceLoader<SensitiveDetector> loader = ServiceLoader.load(SensitiveDetector.class);
    final Builder chainBuilder = builder();
    for (final SensitiveDetector detector : loader) {
      LOG.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.redaction.detector_chain.debug.loaded_via_service_loader",
              detector.getName()));
      chainBuilder.add(detector);
    }
    return chainBuilder.build();
  }

  /**
   * Runs all enabled detectors and returns merged findings.
   *
   * @param text Text to analyze
   * @param ctx Detection context
   * @return Merged detection result
   */
  public DetectionResult detect(final String text, final DetectionContext ctx) {
    if (text == null || text.isEmpty() || !ctx.isDetectionEnabled()) {
      return DetectionResult.EMPTY;
    }
    final List<Finding> allFindings = new ArrayList<>();
    for (final SensitiveDetector detector : detectors) {
      if (!detector.isEnabled(ctx)) {
        LOG.debug(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.redaction.detector_chain.debug.skipping_disabled", detector.getName()));
        continue;
      }
      try {
        final DetectionResult detectorResult = detector.detect(text, ctx);
        if (detectorResult.hasFindings()) {
          allFindings.addAll(detectorResult.findings());
          LOG.debug(
              com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                  "infra.redaction.detector_chain.debug.findings_count",
                  detector.getName(),
                  detectorResult.findingsCount(),
                  detectorResult.maxConfidence()));
        }
      } catch (RuntimeException e) {
        LOG.warn(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.redaction.detector_chain.warn.detector_exception",
                detector.getName(),
                e.getMessage()));
        // Continue with other detectors
      }
    }
    if (allFindings.isEmpty()) {
      return DetectionResult.EMPTY;
    }
    // Merge overlapping findings, keeping higher confidence ones
    final List<Finding> mergedFindings = mergeOverlappingFindings(allFindings);
    return DetectionResult.of(mergedFindings);
  }

  /**
   * Merges overlapping findings, retaining the one with higher confidence.
   *
   * <p>For overlapping findings, the one with higher confidence wins. If confidences are equal, the
   * first one (by position) is retained.
   */
  private List<Finding> mergeOverlappingFindings(final List<Finding> findings) {
    if (findings.size() <= 1) {
      return findings;
    }
    // Sort by start position, then by confidence (descending)
    final List<Finding> sorted = new ArrayList<>(findings);
    sorted.sort(
        Comparator.comparingInt(Finding::start)
            .thenComparing(Comparator.comparingDouble(Finding::confidence).reversed()));
    final List<Finding> mergedFindings = new ArrayList<>();
    for (final Finding current : sorted) {
      if (mergedFindings.isEmpty()) {
        mergedFindings.add(current);
        continue;
      }
      final Finding last = mergedFindings.get(mergedFindings.size() - 1);
      if (!current.overlaps(last)) {
        mergedFindings.add(current);
        continue;
      }
      // Keep the one with higher confidence; equal confidence keeps earlier position.
      if (current.confidence() > last.confidence()) {
        mergedFindings.set(mergedFindings.size() - 1, current);
      }
    }
    return mergedFindings;
  }

  /**
   * Returns the number of detectors in the chain.
   *
   * @return Detector count
   */
  public int size() {
    return detectors.size();
  }

  /**
   * Returns the list of detector names.
   *
   * @return List of detector names
   */
  public List<String> getDetectorNames() {
    return detectors.stream().map(SensitiveDetector::getName).toList();
  }

  /** Builder for constructing detector chains. */
  public static final class Builder {

    private final List<SensitiveDetector> detectors = new ArrayList<>();

    private Builder() {}

    /**
     * Adds a detector to the chain.
     *
     * @param detector Detector to add
     * @return This builder
     */
    public Builder add(final SensitiveDetector detector) {
      Objects.requireNonNull(
          detector,
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.argument_null", "detector must not be null"));
      detectors.add(detector);
      return this;
    }

    /**
     * Adds all default detectors to the chain.
     *
     * @return This builder
     */
    public Builder addDefaults() {
      detectors.add(new RegexDetector());
      detectors.add(new DictionaryDetector());
      return this;
    }

    /**
     * Builds the detector chain.
     *
     * @return Configured detector chain
     */
    public DetectorChain build() {
      return new DetectorChain(detectors);
    }
  }
}
