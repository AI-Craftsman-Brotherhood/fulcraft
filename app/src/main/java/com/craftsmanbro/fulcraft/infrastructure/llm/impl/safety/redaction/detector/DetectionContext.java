package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Context for sensitive data detection operations.
 *
 * <p>Contains configuration, enabled detectors, thresholds, and additional metadata needed for
 * detection. This class is mutable and should be created per detection operation.
 */
public final class DetectionContext {

  /** Redaction mode: how to handle detected sensitive data. */
  public enum Mode {

    /** Disabled: no detection or redaction. */
    OFF,
    /** Report only: detect and log, but don't block or mask. */
    REPORT,
    /** Enforce: detect, mask, and block based on thresholds. */
    ENFORCE
  }

  private static final double DEFAULT_MASK_THRESHOLD = 0.60;
  private static final double DEFAULT_BLOCK_THRESHOLD = 0.90;

  private Mode mode = Mode.ENFORCE;

  private List<String> enabledDetectors = List.of("regex", "dictionary", "ml");

  private double maskThreshold = DEFAULT_MASK_THRESHOLD;

  private double blockThreshold = DEFAULT_BLOCK_THRESHOLD;

  private Path denylistPath;

  private Path allowlistPath;

  private Set<String> allowlistTerms = Set.of();

  private String mlEndpointUrl;

  private final Map<String, Object> attributes = new HashMap<>();

  /**
   * Creates a context from configuration.
   *
   * @param mode Redaction mode
   * @param enabledDetectors List of enabled detector names
   * @param maskThreshold Confidence threshold for masking
   * @param blockThreshold Confidence threshold for blocking
   * @return Configured context
   */
  public static DetectionContext of(
      final Mode mode,
      final List<String> enabledDetectors,
      final double maskThreshold,
      final double blockThreshold) {
    final var context = new DetectionContext();
    context.setMode(mode);
    context.setEnabledDetectors(enabledDetectors);
    context.setMaskThreshold(maskThreshold);
    context.setBlockThreshold(blockThreshold);
    return context;
  }

  public Mode getMode() {
    return mode;
  }

  public void setMode(final Mode mode) {
    this.mode = mode != null ? mode : Mode.ENFORCE;
  }

  public List<String> getEnabledDetectors() {
    return enabledDetectors;
  }

  public void setEnabledDetectors(final List<String> enabledDetectors) {
    this.enabledDetectors = enabledDetectors != null ? List.copyOf(enabledDetectors) : List.of();
  }

  /**
   * Checks if a specific detector is enabled.
   *
   * @param detectorName Name of the detector
   * @return true if enabled
   */
  public boolean isDetectorEnabled(final String detectorName) {
    return enabledDetectors.stream().anyMatch(detector -> detector.equalsIgnoreCase(detectorName));
  }

  public double getMaskThreshold() {
    return maskThreshold;
  }

  public void setMaskThreshold(final double maskThreshold) {
    this.maskThreshold = normalizeThreshold(maskThreshold, DEFAULT_MASK_THRESHOLD);
    // Keep invariants: block threshold must be >= mask threshold.
    if (this.blockThreshold < this.maskThreshold) {
      this.blockThreshold = this.maskThreshold;
    }
  }

  public double getBlockThreshold() {
    return blockThreshold;
  }

  public void setBlockThreshold(final double blockThreshold) {
    this.blockThreshold = normalizeThreshold(blockThreshold, DEFAULT_BLOCK_THRESHOLD);
    if (this.blockThreshold < maskThreshold) {
      this.blockThreshold = maskThreshold;
    }
  }

  public Optional<Path> getDenylistPath() {
    return Optional.ofNullable(denylistPath);
  }

  public void setDenylistPath(final Path denylistPath) {
    this.denylistPath = denylistPath;
  }

  public Optional<Path> getAllowlistPath() {
    return Optional.ofNullable(allowlistPath);
  }

  public void setAllowlistPath(final Path allowlistPath) {
    this.allowlistPath = allowlistPath;
  }

  public Set<String> getAllowlistTerms() {
    return allowlistTerms;
  }

  public void setAllowlistTerms(final Set<String> allowlistTerms) {
    this.allowlistTerms = allowlistTerms != null ? Set.copyOf(allowlistTerms) : Set.of();
  }

  /**
   * Checks if a term is in the allowlist.
   *
   * @param term Term to check
   * @return true if allowlisted
   */
  public boolean isAllowlisted(final String term) {
    if (term == null || allowlistTerms.isEmpty()) {
      return false;
    }
    // Case-insensitive check.
    final String normalizedTerm = normalizeForComparison(term);
    return allowlistTerms.stream()
        .map(this::normalizeForComparison)
        .anyMatch(allowlistTerm -> allowlistTerm.equals(normalizedTerm));
  }

  /**
   * Normalizes a string for allowlist/denylist comparison. Handles case-insensitivity and
   * full-width/half-width conversion.
   */
  private String normalizeForComparison(final String text) {
    if (text == null) {
      return "";
    }
    // Convert to lowercase and normalize full-width to half-width
    final StringBuilder normalized = new StringBuilder();
    for (final char c : text.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
      // Full-width alphanumeric to half-width
      if (c >= 'Ａ' && c <= 'Ｚ') {
        normalized.append((char) (c - 'Ａ' + 'a'));
      } else if (c >= 'ａ' && c <= 'ｚ') {
        normalized.append((char) (c - 'ａ' + 'a'));
      } else if (c >= '０' && c <= '９') {
        normalized.append((char) (c - '０' + '0'));
      } else {
        normalized.append(c);
      }
    }
    return normalized.toString();
  }

  private double normalizeThreshold(final double value, final double defaultValue) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return defaultValue;
    }
    if (value < 0.0) {
      return 0.0;
    }
    if (value > 1.0) {
      return 1.0;
    }
    return value;
  }

  public Optional<String> getMlEndpointUrl() {
    return Optional.ofNullable(mlEndpointUrl);
  }

  public void setMlEndpointUrl(final String mlEndpointUrl) {
    this.mlEndpointUrl = mlEndpointUrl;
  }

  /**
   * Returns true if masking should be applied based on confidence.
   *
   * @param confidence Confidence score
   * @return true if confidence meets or exceeds mask threshold
   */
  public boolean shouldMask(final double confidence) {
    return mode == Mode.ENFORCE && confidence >= maskThreshold && confidence < blockThreshold;
  }

  /**
   * Returns true if blocking should be applied based on confidence.
   *
   * @param confidence Confidence score
   * @return true if confidence meets or exceeds block threshold
   */
  public boolean shouldBlock(final double confidence) {
    return mode == Mode.ENFORCE && confidence >= blockThreshold;
  }

  /**
   * Sets a custom attribute.
   *
   * @param key Attribute key
   * @param value Attribute value
   */
  public void setAttribute(final String key, final Object value) {
    attributes.put(key, value);
  }

  /**
   * Gets a custom attribute.
   *
   * @param key Attribute key
   * @param type Expected type
   * @param <T> Type parameter
   * @return Optional value
   */
  public <T> Optional<T> getAttribute(final String key, final Class<T> type) {
    final Object value = attributes.get(key);
    if (value == null || !type.isInstance(value)) {
      return Optional.empty();
    }
    return Optional.of(type.cast(value));
  }

  /**
   * Returns true if detection is enabled (mode is not OFF).
   *
   * @return true if detection should be performed
   */
  public boolean isDetectionEnabled() {
    return mode != Mode.OFF;
  }

  /**
   * Returns true if blocking is enabled (mode is ENFORCE).
   *
   * @return true if blocking is enabled
   */
  public boolean isBlockingEnabled() {
    return mode == Mode.ENFORCE;
  }
}
