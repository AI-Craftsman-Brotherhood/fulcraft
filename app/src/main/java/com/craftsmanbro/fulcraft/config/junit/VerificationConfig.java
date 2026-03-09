package com.craftsmanbro.fulcraft.config.junit;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class VerificationConfig {

  @JsonProperty("flaky_detection")
  private FlakyDetectionConfig flakyDetection;

  @JsonProperty("test_execution")
  private TestExecutionConfig testExecution;

  public FlakyDetectionConfig getFlakyDetection() {
    return flakyDetection != null ? flakyDetection : new FlakyDetectionConfig();
  }

  public void setFlakyDetection(final FlakyDetectionConfig flakyDetection) {
    this.flakyDetection = flakyDetection;
  }

  public TestExecutionConfig getTestExecution() {
    return testExecution != null ? testExecution : new TestExecutionConfig();
  }

  public boolean hasFlakyDetectionConfig() {
    return flakyDetection != null;
  }

  public void setTestExecution(final TestExecutionConfig testExecution) {
    this.testExecution = testExecution;
  }

  /** Configuration for flaky test detection. */
  public static class FlakyDetectionConfig {

    /** Whether to enable flaky test detection. */
    @JsonProperty("enabled")
    private Boolean enabled = true;

    /** Number of times to rerun failed tests to detect flakiness. */
    @JsonProperty("rerun_count")
    private Integer rerunCount = 0;

    /** Strategy for determining if a test is flaky. */
    @JsonProperty("strategy")
    private String strategy = FlakyStrategy.ANY_PASS.name();

    /** Minimum number of passes required to consider a test flaky. */
    @JsonProperty("min_passes_for_flaky")
    private Integer minPassesForFlaky = 1;

    /** Whether to fail the build if flaky tests are detected. */
    @JsonProperty("fail_on_flaky")
    private Boolean failOnFlaky = false;

    public enum FlakyStrategy {

      /** Consider test flaky if any rerun passes. */
      ANY_PASS,
      /** Consider test flaky if majority of reruns differ from initial. */
      MAJORITY,
      /** Consider test flaky only if all reruns differ from initial. */
      ALL_DIFFER
    }

    public boolean isEnabled() {
      return enabled == null || enabled;
    }

    public void setEnabled(final Boolean enabled) {
      this.enabled = enabled;
    }

    public int getRerunCount() {
      return rerunCount != null ? rerunCount : 0;
    }

    public void setRerunCount(final Integer rerunCount) {
      this.rerunCount = rerunCount;
    }

    public String getStrategy() {
      return strategy;
    }

    public FlakyStrategy getStrategyEnum() {
      if (strategy == null || strategy.isBlank()) {
        return FlakyStrategy.ANY_PASS;
      }
      try {
        return FlakyStrategy.valueOf(strategy.trim().toUpperCase(java.util.Locale.ROOT));
      } catch (IllegalArgumentException e) {
        return FlakyStrategy.ANY_PASS;
      }
    }

    public void setStrategy(final String strategy) {
      this.strategy = strategy;
    }

    public int getMinPassesForFlaky() {
      return minPassesForFlaky != null ? minPassesForFlaky : 1;
    }

    public void setMinPassesForFlaky(final Integer minPassesForFlaky) {
      this.minPassesForFlaky = minPassesForFlaky;
    }

    public boolean isFailOnFlaky() {
      return failOnFlaky != null && failOnFlaky;
    }

    public void setFailOnFlaky(final Boolean failOnFlaky) {
      this.failOnFlaky = failOnFlaky;
    }
  }

  /** Configuration for test execution behavior. */
  public static class TestExecutionConfig {

    /** Maximum time in seconds to wait for test execution. */
    @JsonProperty("timeout_seconds")
    private Integer timeoutSeconds = 600;

    /** Whether to run tests in parallel. */
    @JsonProperty("parallel")
    private Boolean parallel = false;

    /** Maximum number of parallel test workers. */
    @JsonProperty("max_workers")
    private Integer maxWorkers = 1;

    /** Whether to stop on first test failure. */
    @JsonProperty("fail_fast")
    private Boolean failFast = false;

    /** Whether to continue pipeline execution if tests fail. */
    @JsonProperty("continue_on_failure")
    private Boolean continueOnFailure = true;

    /** Additional JVM arguments for test execution. */
    @JsonProperty("jvm_args")
    private List<String> jvmArgs = new ArrayList<>();

    public int getTimeoutSeconds() {
      return timeoutSeconds != null ? timeoutSeconds : 600;
    }

    public void setTimeoutSeconds(final Integer timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isParallel() {
      return parallel != null && parallel;
    }

    public void setParallel(final Boolean parallel) {
      this.parallel = parallel;
    }

    public int getMaxWorkers() {
      return maxWorkers != null ? maxWorkers : 1;
    }

    public void setMaxWorkers(final Integer maxWorkers) {
      this.maxWorkers = maxWorkers;
    }

    public boolean isFailFast() {
      return failFast != null && failFast;
    }

    public void setFailFast(final Boolean failFast) {
      this.failFast = failFast;
    }

    public boolean isContinueOnFailure() {
      return continueOnFailure == null || continueOnFailure;
    }

    public void setContinueOnFailure(final Boolean continueOnFailure) {
      this.continueOnFailure = continueOnFailure;
    }

    public List<String> getJvmArgs() {
      return jvmArgs != null ? jvmArgs : new ArrayList<>();
    }

    public void setJvmArgs(final List<String> jvmArgs) {
      this.jvmArgs = jvmArgs;
    }
  }
}
