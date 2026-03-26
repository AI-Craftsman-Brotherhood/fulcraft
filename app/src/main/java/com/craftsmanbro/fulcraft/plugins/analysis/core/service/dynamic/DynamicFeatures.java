package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Facade for dynamic feature detection within the analysis package.
 *
 * <p>This class provides a stable API for detecting dynamic features (Reflection, Proxy, DI, etc.)
 * in analyzed code.
 */
public final class DynamicFeatures {

  private final DynamicFeatureDetector detector;

  public DynamicFeatures() {
    this.detector = new DynamicFeatureDetector();
  }

  /**
   * Detects dynamic features from analyzed classes.
   *
   * @param classes the analyzed classes
   * @param projectRoot the project root directory
   */
  public void detect(final List<ClassInfo> classes, final Path projectRoot) {
    Objects.requireNonNull(
        classes,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "classes must not be null"));
    Objects.requireNonNull(
        projectRoot,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "analysis.common.error.argument_null", "projectRoot must not be null"));
    detector.reset();
    detector.detectFromAnalysisResult(classes, projectRoot);
  }

  /**
   * Gets the detected events.
   *
   * @return list of dynamic feature events
   */
  public List<DynamicFeatureEvent> getEvents() {
    return detector.getEvents();
  }

  /**
   * Calculates the dynamic score (higher = more dynamic features).
   *
   * @return the dynamic score
   */
  public int calculateScore() {
    return detector.calculateDynamicScore();
  }

  /**
   * Gets counts by feature type.
   *
   * @return map of type to count
   */
  public Map<DynamicFeatureType, Long> countByType() {
    return detector.countByType();
  }

  /**
   * Gets counts by severity.
   *
   * @return map of severity to count
   */
  public Map<DynamicFeatureSeverity, Long> countBySeverity() {
    return detector.countBySeverity();
  }

  /**
   * Gets top files by dynamic feature count.
   *
   * @param limit maximum number of files to return
   * @return list of file-count pairs
   */
  public List<Map.Entry<String, Long>> getTopFiles(final int limit) {
    return detector.getTopFiles(limit);
  }

  /**
   * Gets top subtypes by count.
   *
   * @param limit maximum number of subtypes to return
   * @return list of subtype-count pairs
   */
  public List<Map.Entry<String, Long>> getTopSubtypes(final int limit) {
    return detector.getTopSubtypes(limit);
  }

  /**
   * Gets annotation counts.
   *
   * @return map of annotation name to count
   */
  public Map<String, Integer> getAnnotationCounts() {
    return detector.getAnnotationCounts();
  }

  /**
   * Gets top annotations by count.
   *
   * @param limit maximum number of annotations to return
   * @return list of annotation-count pairs
   */
  public List<Map.Entry<String, Integer>> getTopAnnotations(final int limit) {
    return detector.getTopAnnotations(limit);
  }

  /**
   * Gets the underlying detector for compatibility.
   *
   * @return the underlying detector
   */
  public DynamicFeatureDetector unwrap() {
    return detector;
  }
}
