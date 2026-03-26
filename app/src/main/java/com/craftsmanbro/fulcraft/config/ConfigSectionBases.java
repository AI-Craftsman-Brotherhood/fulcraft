package com.craftsmanbro.fulcraft.config;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** Internal base implementations extracted from {@link Config} to reduce aggregate complexity. */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
final class ConfigSectionBases {

  private static final String FORMAT_MARKDOWN = "markdown";

  private static final String FORMAT_HUMAN = "human";

  private ConfigSectionBases() {
    // Utility holder for nested config section base classes.
  }

  @Getter
  @Setter
  public static class ProjectConfig {

    @JsonProperty("id")
    private String id;

    @JsonProperty("root")
    private String root;

    @JsonProperty("docs_output")
    private String docsOutput;

    @JsonProperty("repo_url")
    private String repoUrl;

    @JsonProperty("commit")
    private String commit;

    @JsonProperty("build_tool")
    private String buildTool;

    @JsonProperty("build_command")
    private String buildCommand;

    // A2: Paths to exclude from analysis (relative or absolute)
    @JsonProperty("exclude_paths")
    private List<String> excludePaths = new ArrayList<>();

    // Paths to include in analysis (relative or absolute). If empty, all files are
    // included.
    @JsonProperty("include_paths")
    private List<String> includePaths = new ArrayList<>();

    // --- Pattern C: defensive copy / unmodifiable overrides ---

    public List<String> getExcludePaths() {
      if (excludePaths == null) {
        return List.of();
      }
      return Collections.unmodifiableList(excludePaths);
    }

    public void setExcludePaths(final List<String> excludePaths) {
      this.excludePaths =
          (excludePaths == null) ? new ArrayList<>() : new ArrayList<>(excludePaths);
    }

    public List<String> getIncludePaths() {
      if (includePaths == null) {
        return List.of();
      }
      return Collections.unmodifiableList(includePaths);
    }

    public void setIncludePaths(final List<String> includePaths) {
      this.includePaths =
          (includePaths == null) ? new ArrayList<>() : new ArrayList<>(includePaths);
    }
  }

  @Getter
  @Setter
  public static class LlmConfig {

    private static final boolean DEFAULT_FALLBACK_STUB_ENABLED = true;

    private static final boolean DEFAULT_JAVAC_VALIDATION = false;

    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 30;

    private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 300;

    private static final int DEFAULT_MAX_RESPONSE_LENGTH = 50_000;

    private static final long DEFAULT_RETRY_INITIAL_DELAY_MS = 2000L;

    private static final double DEFAULT_RETRY_BACKOFF_MULTIPLIER = 2.0;

    private static final int DEFAULT_CIRCUIT_BREAKER_THRESHOLD = 5;

    private static final long DEFAULT_CIRCUIT_BREAKER_RESET_MS = 30_000L;

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("max_retries")
    private Integer maxRetries;

    @JsonProperty("fix_retries")
    private Integer fixRetries;

    // Local / Python config
    @JsonProperty("url")
    private String url;

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("api_key")
    private String apiKey;

    // Azure OpenAI
    @JsonProperty("azure_deployment")
    private String azureDeployment;

    @JsonProperty("azure_api_version")
    private String azureApiVersion;

    // Vertex AI (Gemini on Vertex)
    @JsonProperty("vertex_project")
    private String vertexProject;

    @JsonProperty("vertex_location")
    private String vertexLocation;

    @JsonProperty("vertex_publisher")
    private String // e.g. google
        vertexPublisher;

    @JsonProperty("vertex_model")
    private String // e.g. gemini-1.5-pro
        vertexModel;

    // AWS Bedrock
    @JsonProperty("aws_access_key_id")
    private String awsAccessKeyId;

    @JsonProperty("aws_secret_access_key")
    private String awsSecretAccessKey;

    @JsonProperty("aws_session_token")
    private String awsSessionToken;

    @JsonProperty("aws_region")
    private String awsRegion;

    // H: Timeout settings (in seconds)
    @JsonProperty("connect_timeout")
    private Integer connectTimeout = DEFAULT_CONNECT_TIMEOUT_SECONDS;

    @JsonProperty("request_timeout")
    private Integer requestTimeout = DEFAULT_REQUEST_TIMEOUT_SECONDS;

    // R: Response length limits
    @JsonProperty("max_response_length")
    private Integer maxResponseLength = DEFAULT_MAX_RESPONSE_LENGTH;

    // H: Custom headers (for authentication, etc.)
    @JsonProperty("custom_headers")
    private Map<String, String> customHeaders = new HashMap<>();

    // C: Fallback stub configuration
    @JsonProperty("fallback_stub_enabled")
    private Boolean fallbackStubEnabled = DEFAULT_FALLBACK_STUB_ENABLED;

    // B: Lightweight javac validation
    @JsonProperty("javac_validation")
    private Boolean javacValidation = DEFAULT_JAVAC_VALIDATION;

    // E: Retry policy configuration
    @JsonProperty("retry_initial_delay_ms")
    private Long retryInitialDelayMs = DEFAULT_RETRY_INITIAL_DELAY_MS;

    @JsonProperty("retry_backoff_multiplier")
    private Double retryBackoffMultiplier = DEFAULT_RETRY_BACKOFF_MULTIPLIER;

    // Q: Rate Limiting & Circuit Breaker
    @JsonProperty("rate_limit_qps")
    private Double rateLimitQps;

    @JsonProperty("circuit_breaker_threshold")
    private Integer circuitBreakerThreshold = DEFAULT_CIRCUIT_BREAKER_THRESHOLD;

    @JsonProperty("circuit_breaker_reset_ms")
    private Long circuitBreakerResetMs = DEFAULT_CIRCUIT_BREAKER_RESET_MS;

    // Deterministic mode for reproducible generation
    @JsonProperty("deterministic")
    private Boolean deterministic = true;

    @JsonProperty("seed")
    private Integer seed;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("system_message")
    private String systemMessage;

    @JsonProperty("allowed_providers")
    private List<String> allowedProviders;

    @JsonProperty("allowed_models")
    private Map<String, List<String>> allowedModels;

    @JsonProperty("smart_retry")
    private SmartRetryConfig smartRetry;

    // --- Pattern B: null-guarded getters/setters for fields with defaults ---

    public Integer getConnectTimeout() {
      return connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT_SECONDS;
    }

    public void setConnectTimeout(final Integer connectTimeout) {
      this.connectTimeout =
          connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT_SECONDS;
    }

    public Integer getRequestTimeout() {
      return requestTimeout != null ? requestTimeout : DEFAULT_REQUEST_TIMEOUT_SECONDS;
    }

    public void setRequestTimeout(final Integer requestTimeout) {
      this.requestTimeout =
          requestTimeout != null ? requestTimeout : DEFAULT_REQUEST_TIMEOUT_SECONDS;
    }

    public Integer getMaxResponseLength() {
      return maxResponseLength != null ? maxResponseLength : DEFAULT_MAX_RESPONSE_LENGTH;
    }

    public void setMaxResponseLength(final Integer maxResponseLength) {
      this.maxResponseLength =
          maxResponseLength != null ? maxResponseLength : DEFAULT_MAX_RESPONSE_LENGTH;
    }

    public Boolean getFallbackStubEnabled() {
      return fallbackStubEnabled != null ? fallbackStubEnabled : DEFAULT_FALLBACK_STUB_ENABLED;
    }

    public void setFallbackStubEnabled(final Boolean fallbackStubEnabled) {
      this.fallbackStubEnabled =
          fallbackStubEnabled != null ? fallbackStubEnabled : DEFAULT_FALLBACK_STUB_ENABLED;
    }

    public Boolean getJavacValidation() {
      return javacValidation != null ? javacValidation : DEFAULT_JAVAC_VALIDATION;
    }

    public void setJavacValidation(final Boolean javacValidation) {
      this.javacValidation = javacValidation != null ? javacValidation : DEFAULT_JAVAC_VALIDATION;
    }

    public Long getRetryInitialDelayMs() {
      return retryInitialDelayMs != null ? retryInitialDelayMs : DEFAULT_RETRY_INITIAL_DELAY_MS;
    }

    public void setRetryInitialDelayMs(final Long retryInitialDelayMs) {
      this.retryInitialDelayMs =
          retryInitialDelayMs != null ? retryInitialDelayMs : DEFAULT_RETRY_INITIAL_DELAY_MS;
    }

    public Double getRetryBackoffMultiplier() {
      return retryBackoffMultiplier != null
          ? retryBackoffMultiplier
          : DEFAULT_RETRY_BACKOFF_MULTIPLIER;
    }

    public void setRetryBackoffMultiplier(final Double retryBackoffMultiplier) {
      this.retryBackoffMultiplier =
          retryBackoffMultiplier != null
              ? retryBackoffMultiplier
              : DEFAULT_RETRY_BACKOFF_MULTIPLIER;
    }

    public Integer getCircuitBreakerThreshold() {
      return circuitBreakerThreshold != null
          ? circuitBreakerThreshold
          : DEFAULT_CIRCUIT_BREAKER_THRESHOLD;
    }

    public void setCircuitBreakerThreshold(final Integer circuitBreakerThreshold) {
      this.circuitBreakerThreshold =
          circuitBreakerThreshold != null
              ? circuitBreakerThreshold
              : DEFAULT_CIRCUIT_BREAKER_THRESHOLD;
    }

    public Long getCircuitBreakerResetMs() {
      return circuitBreakerResetMs != null
          ? circuitBreakerResetMs
          : DEFAULT_CIRCUIT_BREAKER_RESET_MS;
    }

    public void setCircuitBreakerResetMs(final Long circuitBreakerResetMs) {
      this.circuitBreakerResetMs =
          circuitBreakerResetMs != null ? circuitBreakerResetMs : DEFAULT_CIRCUIT_BREAKER_RESET_MS;
    }

    // --- Pattern C: custom logic ---

    public Map<String, String> getCustomHeaders() {
      if (customHeaders == null) {
        return Map.of();
      }
      return Collections.unmodifiableMap(customHeaders);
    }

    public void setCustomHeaders(final Map<String, String> customHeaders) {
      this.customHeaders = (customHeaders == null) ? new HashMap<>() : new HashMap<>(customHeaders);
    }

    public Boolean getDeterministic() {
      return deterministic != null && deterministic;
    }

    /** Configuration for smart retry strategy that optimizes LLM calls. */
    public static class SmartRetryConfig {

      private static final int DEFAULT_SAME_ERROR_MAX_RETRIES = 1;

      private static final int DEFAULT_TOTAL_RETRY_BUDGET = 3;

      private static final int DEFAULT_NON_RECOVERABLE_MAX_RETRIES = 0;

      /** Maximum retries when the same error type is encountered repeatedly. */
      @JsonProperty("same_error_max_retries")
      private Integer sameErrorMaxRetries = DEFAULT_SAME_ERROR_MAX_RETRIES;

      /** Total retry budget per task across all fix types (static + runtime). */
      @JsonProperty("total_retry_budget_per_task")
      private Integer totalRetryBudgetPerTask = DEFAULT_TOTAL_RETRY_BUDGET;

      /**
       * Maximum retries for errors classified as non-recoverable. Default 0 means abort immediately
       * without retry.
       */
      @JsonProperty("non_recoverable_max_retries")
      private Integer nonRecoverableMaxRetries = DEFAULT_NON_RECOVERABLE_MAX_RETRIES;

      public Integer getSameErrorMaxRetries() {
        return sameErrorMaxRetries != null ? sameErrorMaxRetries : DEFAULT_SAME_ERROR_MAX_RETRIES;
      }

      public void setSameErrorMaxRetries(final Integer sameErrorMaxRetries) {
        this.sameErrorMaxRetries =
            sameErrorMaxRetries != null ? sameErrorMaxRetries : DEFAULT_SAME_ERROR_MAX_RETRIES;
      }

      public Integer getTotalRetryBudgetPerTask() {
        return totalRetryBudgetPerTask != null
            ? totalRetryBudgetPerTask
            : DEFAULT_TOTAL_RETRY_BUDGET;
      }

      public void setTotalRetryBudgetPerTask(final Integer totalRetryBudgetPerTask) {
        this.totalRetryBudgetPerTask =
            totalRetryBudgetPerTask != null ? totalRetryBudgetPerTask : DEFAULT_TOTAL_RETRY_BUDGET;
      }

      public Integer getNonRecoverableMaxRetries() {
        return nonRecoverableMaxRetries != null
            ? nonRecoverableMaxRetries
            : DEFAULT_NON_RECOVERABLE_MAX_RETRIES;
      }

      public void setNonRecoverableMaxRetries(final Integer nonRecoverableMaxRetries) {
        this.nonRecoverableMaxRetries =
            nonRecoverableMaxRetries != null
                ? nonRecoverableMaxRetries
                : DEFAULT_NON_RECOVERABLE_MAX_RETRIES;
      }
    }
  }

  @Getter
  @Setter
  public static class ExecutionConfig {

    public static final String DEFAULT_LOGS_ROOT = ".ful/runs";

    @JsonProperty("per_task_isolation")
    private boolean perTaskIsolation;

    @JsonProperty("logs_root")
    private String logsRoot;

    @JsonProperty("runtime_fix_retries")
    private Integer runtimeFixRetries;

    @JsonProperty("flaky_reruns")
    private Integer flakyReruns = 0;

    public Integer getFlakyReruns() {
      return flakyReruns != null ? flakyReruns : 0;
    }

    public void setFlakyReruns(final Integer flakyReruns) {
      this.flakyReruns = flakyReruns;
    }

    @JsonProperty("unresolved_policy")
    private String unresolvedPolicy = UnresolvedPolicy.SKIP.name();

    @JsonProperty("test_stability_policy")
    private String testStabilityPolicy = StabilityPolicy.STANDARD.name();

    public enum UnresolvedPolicy {
      SKIP,
      MINIMAL;

      public static UnresolvedPolicy fromString(final String value) {
        if (value == null || value.isBlank()) {
          return SKIP;
        }
        try {
          return UnresolvedPolicy.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
          return SKIP;
        }
      }
    }

    public enum StabilityPolicy {
      STRICT,
      STANDARD,
      RELAXED;

      public static StabilityPolicy fromString(final String value) {
        if (value == null || value.isBlank()) {
          return STANDARD;
        }
        final String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
          case "STRICT" -> STRICT;
          case "STANDARD" -> STANDARD;
          case "RELAXED" -> RELAXED;
          default ->
              throw new IllegalArgumentException(
                  MessageSource.getMessage(
                      "config.execution.stability_policy.error.invalid", value));
        };
      }
    }

    // --- Pattern C: convenience methods ---

    public String getEffectiveLogsRoot() {
      if (logsRoot == null || logsRoot.isBlank()) {
        return DEFAULT_LOGS_ROOT;
      }
      return logsRoot;
    }

    public UnresolvedPolicy getUnresolvedPolicyEnum() {
      return UnresolvedPolicy.fromString(unresolvedPolicy);
    }

    public StabilityPolicy getTestStabilityPolicyEnum() {
      return StabilityPolicy.fromString(testStabilityPolicy);
    }
  }

  @Getter
  @Setter
  public static class AuditConfig {

    @JsonProperty("enabled")
    private Boolean enabled = false;

    @JsonProperty("log_path")
    private String logPath;

    @JsonProperty("include_raw")
    private Boolean includeRaw = false;

    // --- Pattern C: boolean convenience ---

    public boolean isEnabled() {
      return enabled != null && enabled;
    }
  }

  @Getter
  @Setter
  public static class QuotaConfig {

    @JsonProperty("max_tasks")
    private Integer maxTasks;

    @JsonProperty("max_llm_calls")
    private Integer maxLlmCalls;

    @JsonProperty("on_exceed")
    private String onExceed;

    // --- Pattern C: normalization convenience ---

    public String resolveOnExceed() {
      if (onExceed == null || onExceed.isBlank()) {
        return "warn";
      }
      final String normalized = onExceed.trim().toLowerCase(java.util.Locale.ROOT);
      if ("warn".equals(normalized) || "block".equals(normalized)) {
        return normalized;
      }
      return "warn";
    }
  }

  public static class OutputConfig {

    @JsonProperty("format")
    @Setter
    private FormatConfig format;

    public FormatConfig getFormat() {
      if (format == null) {
        format = new FormatConfig();
      }
      return format;
    }

    // --- Pattern C: normalization convenience ---

    public String getTasksFormat() {
      final String value = format != null ? format.getTasks() : null;
      if (value == null || value.isBlank()) {
        return "jsonl";
      }
      return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public String getReportFormat() {
      final String value = format != null ? format.getReport() : null;
      if (value == null || value.isBlank()) {
        return FORMAT_MARKDOWN;
      }
      return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Getter
    @Setter
    public static class FormatConfig {

      @JsonProperty("tasks")
      private String tasks;

      @JsonProperty("report")
      private String report;
    }
  }

  @Setter
  public static class LogConfig {

    /**
     * Log format: "human" (default) for CLI-friendly output, "json" or "yaml" for structured
     * output.
     */
    @JsonProperty("format")
    private String format = FORMAT_HUMAN;

    /** Log level: "debug", "info" (default), "warn", or "error". */
    @JsonProperty("level")
    private String level = "info";

    /** Output destination: "console" (default), "file", or "both". */
    @JsonProperty("output")
    private String output = "console";

    /** Color mode: "auto" (default, detect TTY), "on" (always), or "off" (never). */
    @JsonProperty("color")
    private String color = "auto";

    /** Log file path when output is "file" or "both". */
    @JsonProperty("file_path")
    @JsonSetter(nulls = Nulls.SKIP)
    private String filePath = "logs/ful.log";

    /** Include timestamp in console output (default: false for clean CLI output). */
    @JsonProperty("include_timestamp")
    private Boolean includeTimestamp = false;

    /** Include thread name in log output. */
    @JsonProperty("include_thread")
    private Boolean includeThread = false;

    /** Include logger name (subsystem) in log output. */
    @JsonProperty("include_logger")
    private Boolean includeLogger = false;

    /** Maximum length for log messages before truncation (0 = no limit). */
    @JsonProperty("max_message_length")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer maxMessageLength = 0;

    /** Enable MDC (Mapped Diagnostic Context) for trace IDs and context info. */
    @JsonProperty("enable_mdc")
    private Boolean enableMdc = true;

    // --- Pattern C: all getters have normalization logic ---

    public String getFormat() {
      return format != null ? format.toLowerCase(java.util.Locale.ROOT) : FORMAT_HUMAN;
    }

    public String getLevel() {
      return level != null ? level.toLowerCase(java.util.Locale.ROOT) : "info";
    }

    public String getOutput() {
      return output != null ? output.toLowerCase(java.util.Locale.ROOT) : "console";
    }

    public String getColor() {
      return color != null ? color.toLowerCase(java.util.Locale.ROOT) : "auto";
    }

    public String getFilePath() {
      return filePath != null ? filePath : "logs/ful.log";
    }

    public boolean isIncludeTimestamp() {
      return includeTimestamp != null && includeTimestamp;
    }

    public boolean isIncludeThread() {
      return includeThread != null && includeThread;
    }

    public boolean isIncludeLogger() {
      return includeLogger != null && includeLogger;
    }

    public int getMaxMessageLength() {
      return maxMessageLength != null ? maxMessageLength : 0;
    }

    public boolean isEnableMdc() {
      return enableMdc == null || enableMdc;
    }

    // --- Convenience Methods ---

    /** Returns true if JSON format is configured. */
    public boolean isJsonFormat() {
      return "json".equalsIgnoreCase(getFormat());
    }

    /** Returns true if YAML format is configured. */
    public boolean isYamlFormat() {
      return "yaml".equalsIgnoreCase(getFormat());
    }

    /** Returns true if human-readable format is configured. */
    public boolean isHumanFormat() {
      return FORMAT_HUMAN.equalsIgnoreCase(getFormat());
    }

    /** Returns true if color output should be enabled. */
    public boolean shouldEnableColor() {
      final String colorMode = getColor();
      if ("on".equalsIgnoreCase(colorMode)) {
        return true;
      }
      if ("off".equalsIgnoreCase(colorMode)) {
        return false;
      }
      // Auto: detect TTY
      return System.console() != null;
    }

    /** Returns true if debug level is enabled. */
    public boolean isDebugEnabled() {
      return "debug".equalsIgnoreCase(getLevel());
    }

    /** Converts the configured level to a Logback Level. */
    public ch.qos.logback.classic.Level toLogbackLevel() {
      return switch (getLevel()) {
        case "debug" -> ch.qos.logback.classic.Level.DEBUG;
        case "warn" -> ch.qos.logback.classic.Level.WARN;
        case "error" -> ch.qos.logback.classic.Level.ERROR;
        default -> ch.qos.logback.classic.Level.INFO;
      };
    }
  }

  @Setter
  public static class QualityGateConfig {

    /** Line coverage threshold (0.0 - 1.0). PR fails if coverage is below this value. */
    @JsonProperty("coverage_threshold")
    @Getter
    private Double coverageThreshold;

    /** Branch coverage threshold (0.0 - 1.0). Optional secondary threshold. */
    @JsonProperty("branch_coverage_threshold")
    @Getter
    private Double branchCoverageThreshold;

    /** If true, block PR when blocker-level static analysis findings exist. */
    @JsonProperty("block_blocker_findings")
    private Boolean blockBlockerFindings = true;

    /** If true, block PR when critical-level static analysis findings exist. */
    @JsonProperty("block_critical_findings")
    private Boolean blockCriticalFindings = true;

    /** Maximum number of major findings allowed before blocking. 0 or null = unlimited. */
    @JsonProperty("max_major_findings")
    @Getter
    private Integer maxMajorFindings;

    /** If true, warnings are generated but PR is not blocked for major/minor findings. */
    @JsonProperty("allow_warnings")
    private Boolean allowWarnings = true;

    /** If true, only evaluate coverage for newly added/modified code lines. */
    @JsonProperty("apply_to_new_code_only")
    private Boolean applyToNewCodeOnly = false;

    /** Minimum test pass rate (0.0 - 1.0) for quality gate. */
    @JsonProperty("min_pass_rate")
    @Getter
    private Double minPassRate;

    /** Minimum compile success rate (0.0 - 1.0) for quality gate. */
    @JsonProperty("min_compile_rate")
    @Getter
    private Double minCompileRate;

    /** Enable/disable quality gate evaluation entirely. */
    @JsonProperty("enabled")
    private Boolean enabled = true;

    /** Coverage tool to use: jacoco (default), cobertura. */
    @JsonProperty("coverage_tool")
    @JsonSetter(nulls = Nulls.SKIP)
    private String coverageTool = "jacoco";

    /** Static analysis tools to use: spotbugs, pmd, errorprone, checkstyle. */
    @JsonProperty("static_analysis_tools")
    private List<String> staticAnalysisTools;

    /** Path to Jacoco XML report file. */
    @JsonProperty("coverage_report_path")
    @Getter
    private String coverageReportPath;

    /** Paths to static analysis report files. */
    @JsonProperty("static_analysis_report_paths")
    private List<String> staticAnalysisReportPaths;

    // --- Pattern C: boolean convenience with null-safe defaults ---

    public boolean isBlockBlockerFindings() {
      return blockBlockerFindings == null || blockBlockerFindings;
    }

    public boolean isBlockCriticalFindings() {
      return blockCriticalFindings == null || blockCriticalFindings;
    }

    public boolean isAllowWarnings() {
      return allowWarnings == null || allowWarnings;
    }

    public boolean isApplyToNewCodeOnly() {
      return Boolean.TRUE.equals(applyToNewCodeOnly);
    }

    public boolean isEnabled() {
      return enabled == null || enabled;
    }

    public String getCoverageTool() {
      return coverageTool != null ? coverageTool : "jacoco";
    }

    public List<String> getStaticAnalysisTools() {
      if (staticAnalysisTools == null) {
        return List.of();
      }
      return Collections.unmodifiableList(staticAnalysisTools);
    }

    public void setStaticAnalysisTools(final List<String> staticAnalysisTools) {
      this.staticAnalysisTools =
          staticAnalysisTools != null ? new ArrayList<>(staticAnalysisTools) : null;
    }

    public List<String> getStaticAnalysisReportPaths() {
      if (staticAnalysisReportPaths == null) {
        return List.of();
      }
      return Collections.unmodifiableList(staticAnalysisReportPaths);
    }

    public void setStaticAnalysisReportPaths(final List<String> staticAnalysisReportPaths) {
      this.staticAnalysisReportPaths =
          staticAnalysisReportPaths != null ? new ArrayList<>(staticAnalysisReportPaths) : null;
    }

    // === Convenience Methods ===
    /** Returns true if coverage threshold is configured. */
    public boolean hasCoverageThreshold() {
      return coverageThreshold != null && coverageThreshold > 0;
    }

    /** Returns true if branch coverage threshold is configured. */
    public boolean hasBranchCoverageThreshold() {
      return branchCoverageThreshold != null && branchCoverageThreshold > 0;
    }

    /** Returns true if pass rate threshold is configured. */
    public boolean hasMinPassRate() {
      return minPassRate != null && minPassRate > 0;
    }

    /** Returns true if compile rate threshold is configured. */
    public boolean hasMinCompileRate() {
      return minCompileRate != null && minCompileRate > 0;
    }
  }

  @Getter
  @Setter
  public static class CacheConfig {

    /**
     * Cache TTL in days. Null or unset means unlimited (no expiration). Valid values: 1-365 (days).
     * Default: null (unlimited).
     */
    @JsonProperty("ttl_days")
    private Integer ttlDays;

    /** Whether to enable cache. Default is true. */
    @JsonProperty("enabled")
    private Boolean enabled = true;

    /** Whether to run eviction on initialization. Default is true. */
    @JsonProperty("evict_on_init")
    private Boolean evictOnInit = true;

    /**
     * Whether to include version information in cache key. When enabled, cache entries are
     * invalidated when: - Application version changes - Config file content changes - Optionally,
     * dependency lockfile changes Default: false (maintains current behavior).
     */
    @JsonProperty("version_check")
    private Boolean versionCheck = false;

    /**
     * Whether to include dependency lockfile hash in version check. Only applies when version_check
     * is true. Default: false (only app version and config hash are checked).
     */
    @JsonProperty("include_lockfile_hash")
    private Boolean includeLockfileHash = false;

    /**
     * Whether to revalidate cached code before use. When enabled, cached code is validated (syntax
     * check, basic static analysis) before being returned. If validation fails, the cache entry is
     * invalidated and the code is regenerated. Default: false (maintains current behavior for
     * performance).
     */
    @JsonProperty("revalidate")
    private Boolean revalidate = false;

    /** Whether to encrypt cache data on disk. Default: false. */
    @JsonProperty("encrypt")
    private Boolean encrypt = false;

    /** Environment variable name to retrieve the encryption key. Default: FUL_CACHE_KEY. */
    @JsonProperty("encryption_key_env")
    @JsonSetter(nulls = Nulls.SKIP)
    private String encryptionKeyEnv = "FUL_CACHE_KEY";

    /**
     * Maximum cache size in megabytes. If the cache exceeds this size, least recently used entries
     * will be evicted. Default: null (unlimited).
     */
    @JsonProperty("max_size_mb")
    private Integer maxSizeMb;

    // --- Pattern C: boolean convenience with null-safe defaults ---

    public Boolean isEnabled() {
      return enabled == null || enabled;
    }

    public Boolean isEvictOnInit() {
      return evictOnInit == null || evictOnInit;
    }

    public Boolean isVersionCheck() {
      return Boolean.TRUE.equals(versionCheck);
    }

    public Boolean isIncludeLockfileHash() {
      return Boolean.TRUE.equals(includeLockfileHash);
    }

    public Boolean isRevalidate() {
      return Boolean.TRUE.equals(revalidate);
    }

    public Boolean isEncrypt() {
      return Boolean.TRUE.equals(encrypt);
    }

    // === Convenience Methods ===

    /**
     * Returns true if TTL is configured.
     *
     * @return true if ttlDays is set and greater than 0
     */
    public boolean hasTtl() {
      return ttlDays != null && ttlDays > 0;
    }

    /**
     * Gets TTL in milliseconds for comparison with timestamps.
     *
     * @return TTL in milliseconds, or Long.MAX_VALUE if no TTL is configured
     */
    public long getTtlMillis() {
      if (!hasTtl()) {
        return Long.MAX_VALUE;
      }
      return ttlDays * 24L * 60L * 60L * 1000L;
    }
  }

  @Getter
  @Setter
  public static class CliConfig {

    @JsonProperty("autocomplete")
    private AutocompleteConfig autocomplete;

    @JsonProperty("interactive")
    private InteractiveConfig interactive;

    @JsonProperty("color")
    private String color;

    public static class AutocompleteConfig {

      @JsonProperty("enabled")
      @Setter
      private Boolean enabled = true;

      public Boolean getEnabled() {
        return enabled == null || enabled;
      }
    }

    /** Configuration for interactive TUI mode. */
    public static class InteractiveConfig {

      @JsonProperty("enabled")
      @Setter
      private Boolean enabled = true;

      public Boolean getEnabled() {
        return enabled == null || enabled;
      }
    }
  }

  @Setter
  public static class PipelineConfig {

    @JsonProperty("stages")
    private List<String> stages;

    @JsonProperty("workflow_file")
    @Getter
    private String workflowFile;

    // --- Pattern C: normalization logic ---

    /**
     * Gets the configured stages.
     *
     * <p>When stages are not specified, returns an empty list. Callers should treat an empty result
     * as "no stage filter", meaning all enabled workflow nodes are eligible.
     *
     * @return list of stage names (lowercase)
     */
    public List<String> getStages() {
      if (stages == null || stages.isEmpty()) {
        return List.of();
      }
      final List<String> normalizedStages = new ArrayList<>();
      for (final String stage : stages) {
        if (stage == null || stage.isBlank()) {
          continue;
        }
        normalizedStages.add(stage.trim().toLowerCase(java.util.Locale.ROOT));
      }
      return List.copyOf(normalizedStages);
    }

    /**
     * Returns a set of enabled stages for quick lookup.
     *
     * @return set of enabled stage names (lowercase)
     */
    public java.util.Set<String> enabledStagesSet() {
      return new java.util.HashSet<>(getStages());
    }

    /**
     * Checks if a specific stage is enabled.
     *
     * @param stageName the stage name (case-insensitive)
     * @return true if the stage is enabled
     */
    public boolean isStageEnabled(final String stageName) {
      if (stageName == null) {
        return false;
      }
      return enabledStagesSet().contains(stageName.toLowerCase(java.util.Locale.ROOT));
    }
  }
}
