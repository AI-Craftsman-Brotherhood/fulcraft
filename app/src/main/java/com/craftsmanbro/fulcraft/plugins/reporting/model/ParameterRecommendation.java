package com.craftsmanbro.fulcraft.plugins.reporting.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a recommendation for a single Selection parameter.
 *
 * @param parameterName Name of the parameter (e.g. "skip_threshold")
 * @param currentValue Current configured value
 * @param recommendedValue Recommended value to try
 * @param reason Human-readable explanation for the recommendation
 * @param triggerReason The reason key that triggered this recommendation (e.g. "low_conf")
 */
public record ParameterRecommendation(
    @JsonProperty("parameter_name") String parameterName,
    @JsonProperty("current_value") double currentValue,
    @JsonProperty("recommended_value") double recommendedValue,
    @JsonProperty("reason") String reason,
    @JsonProperty("trigger_reason") String triggerReason) {

  /**
   * Creates a recommendation to increase a parameter value.
   *
   * @param name Parameter name
   * @param current Current value
   * @param increase Amount to increase by
   * @param reason Explanation
   * @param trigger Trigger reason key
   * @return New recommendation
   */
  public static ParameterRecommendation increase(
      final String name,
      final double current,
      final double increase,
      final String reason,
      final String trigger) {
    return new ParameterRecommendation(name, current, current + increase, reason, trigger);
  }

  /**
   * Creates a recommendation to decrease a parameter value.
   *
   * @param name Parameter name
   * @param current Current value
   * @param decrease Amount to decrease by
   * @param reason Explanation
   * @param trigger Trigger reason key
   * @return New recommendation
   */
  public static ParameterRecommendation decrease(
      final String name,
      final double current,
      final double decrease,
      final String reason,
      final String trigger) {
    return new ParameterRecommendation(
        name, current, Math.max(0.0, current - decrease), reason, trigger);
  }

  /** Returns the change amount (positive = increase, negative = decrease). */
  public double change() {
    return recommendedValue - currentValue;
  }

  /** Returns true if this is an increase recommendation. */
  public boolean isIncrease() {
    return change() > 0;
  }
}
