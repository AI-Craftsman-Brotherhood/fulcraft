package com.craftsmanbro.fulcraft.plugins.analysis.reporting.core.service.quality;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

/** Represents calculated quality score for analysis results. */
public record QualityScore(
    @JsonProperty("score") int score,
    @JsonProperty("type_resolution_rate") double typeResolutionRate,
    @JsonProperty("dynamic_feature_score") int dynamicFeatureScore,
    @JsonProperty("classpath") ClasspathStatus classpath,
    @JsonProperty("preprocess") PreprocessStatus preprocess,
    @JsonProperty("penalties") Map<String, Integer> penalties) {

  private static final int SCORE_PERCENT_SCALE = 100;

  private static final int MAX_DYNAMIC_FEATURE_PENALTY = 30;

  private static final int MIN_SCORE = 0;

  private static final String PENALTY_DYNAMIC_FEATURES = "dynamic_features";

  private static final String PENALTY_PREPROCESS = "preprocess";

  private static final String PENALTY_CLASSPATH = "classpath";

  private static final String CLASSPATH_STATUS_UNKNOWN = "UNKNOWN";

  private static final int DEFAULT_CLASSPATH_ENTRIES = 0;

  private static final String PREPROCESS_MODE_OFF = "OFF";

  private static final String PREPROCESS_STATUS_SKIPPED = "SKIPPED";

  /** Classpath status info. */
  public record ClasspathStatus(
      @JsonProperty("javaparser") String javaparser,
      // Number of resolved references used as a proxy for classpath effectiveness.
      @JsonProperty("spoon") String spoon,
      @JsonProperty("entries") int entries) {}

  /** Preprocess status info. */
  public record PreprocessStatus(
      @JsonProperty("mode") String mode,
      @JsonProperty("tool_used") String toolUsed,
      @JsonProperty("status") String status) {}

  /** Builder for quality score calculation. */
  public static class Builder {

    private double typeResolutionRate;

    private int dynamicFeatureScore;

    private ClasspathStatus classpath;

    private PreprocessStatus preprocess;

    private int preprocessPenalty;

    private int classpathPenalty;

    public Builder typeResolutionRate(final double rate) {
      this.typeResolutionRate = rate;
      return this;
    }

    public Builder dynamicFeatureScore(final int score) {
      this.dynamicFeatureScore = score;
      return this;
    }

    public Builder classpath(final String javaparser, final String spoon, final int entries) {
      this.classpath = new ClasspathStatus(javaparser, spoon, entries);
      return this;
    }

    public Builder preprocess(final String mode, final String toolUsed, final String status) {
      this.preprocess = new PreprocessStatus(mode, toolUsed, status);
      return this;
    }

    public Builder preprocessPenalty(final int penalty) {
      this.preprocessPenalty = penalty;
      return this;
    }

    public Builder classpathPenalty(final int penalty) {
      this.classpathPenalty = penalty;
      return this;
    }

    public QualityScore build() {
      // Calculate score
      // Base: type_resolution_rate * 100
      // Minus: min(dynamic_feature_score, 30)
      // Minus: penalties
      final int baseScore = (int) Math.round(typeResolutionRate * SCORE_PERCENT_SCALE);
      final int dynamicPenalty = Math.min(dynamicFeatureScore, MAX_DYNAMIC_FEATURE_PENALTY);
      final int totalScore =
          Math.max(MIN_SCORE, baseScore - dynamicPenalty - preprocessPenalty - classpathPenalty);
      final Map<String, Integer> penalties = new LinkedHashMap<>();
      if (dynamicPenalty > 0) {
        penalties.put(PENALTY_DYNAMIC_FEATURES, dynamicPenalty);
      }
      if (preprocessPenalty > 0) {
        penalties.put(PENALTY_PREPROCESS, preprocessPenalty);
      }
      if (classpathPenalty > 0) {
        penalties.put(PENALTY_CLASSPATH, classpathPenalty);
      }
      final ClasspathStatus classpathStatus =
          classpath != null
              ? classpath
              : new ClasspathStatus(
                  CLASSPATH_STATUS_UNKNOWN, CLASSPATH_STATUS_UNKNOWN, DEFAULT_CLASSPATH_ENTRIES);
      final PreprocessStatus preprocessStatus =
          preprocess != null
              ? preprocess
              : new PreprocessStatus(PREPROCESS_MODE_OFF, null, PREPROCESS_STATUS_SKIPPED);
      return new QualityScore(
          totalScore,
          typeResolutionRate,
          dynamicFeatureScore,
          classpathStatus,
          preprocessStatus,
          penalties);
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
