package com.craftsmanbro.fulcraft.config;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

// Intentionally kept as a top-level aggregate for external Config.* type compatibility.
public class Config {

  private static final String PROVIDER_GEMINI = "gemini";

  private static final String PROVIDER_OPENAI = "openai";

  private static final String PROVIDER_ANTHROPIC = "anthropic";

  @JsonProperty("schema_version")
  @Getter
  @Setter
  private String schemaVersion;

  @JsonProperty("AppName")
  @Getter
  @Setter
  private String appName;

  @JsonProperty("version")
  @Getter
  @Setter
  private String version;

  @JsonProperty("project")
  @Getter
  @Setter
  private ProjectConfig project;

  @JsonProperty("selection_rules")
  @Getter
  @Setter
  private SelectionRules selectionRules;

  @JsonProperty("llm")
  @Getter
  @Setter
  private LlmConfig llm;

  @JsonProperty("execution")
  @Getter
  @Setter
  private ExecutionConfig execution;

  @JsonProperty("analysis")
  @Getter
  @Setter
  private AnalysisConfig analysis;

  @JsonProperty("brittle_test_rules")
  @Getter
  @Setter
  private BrittleTestRulesConfig brittleTestRules;

  @JsonProperty("context_awareness")
  private ContextAwarenessConfig contextAwareness;

  @JsonProperty("generation")
  private GenerationConfig generation;

  @JsonProperty("mocking")
  private MockingConfig mocking;

  @JsonProperty("governance")
  private GovernanceConfig governance;

  @JsonProperty("audit")
  private AuditConfig audit;

  @JsonProperty("quota")
  private QuotaConfig quota;

  @JsonProperty("local_fix")
  private LocalFixConfig localFix;

  @JsonProperty("output")
  private OutputConfig output;

  @JsonProperty("log")
  private LogConfig log;

  @JsonProperty("docs")
  @Getter
  @Setter
  private DocsConfig docs;

  @JsonProperty("quality_gate")
  private QualityGateConfig qualityGate;

  @JsonProperty("cache")
  private CacheConfig cache;

  @JsonProperty("cli")
  @Getter
  @Setter
  private CliConfig cli;

  @JsonProperty("interceptors")
  @Getter
  @Setter
  private InterceptorsConfig interceptors;

  @JsonProperty("verification")
  private VerificationConfig verification;

  @JsonProperty("pipeline")
  private PipelineConfig pipeline;

  /**
   * Creates a default configuration with sensible defaults. API keys are read from environment
   * variables.
   *
   * @return a new Config with default values
   */
  public static Config createDefault() {
    final Config config = new Config();

    final ProjectConfig project = new ProjectConfig();
    project.setId("default");
    config.setProject(project);

    final SelectionRules rules = new SelectionRules();
    rules.setExcludeGettersSetters(true);
    rules.setMethodMinLoc(3);
    rules.setMethodMaxLoc(1000);
    rules.setClassMinLoc(10);
    rules.setClassMinMethodCount(1);
    config.setSelectionRules(rules);

    final LlmConfig llm = new LlmConfig();
    llm.setProvider(detectDefaultProvider());
    llm.setApiKey(detectApiKey(llm.getProvider()));
    llm.setModelName(detectDefaultModel(llm.getProvider()));
    llm.setMaxRetries(3);
    llm.setFixRetries(2);
    config.setLlm(llm);

    final ExecutionConfig execution = new ExecutionConfig();
    config.setExecution(execution);

    final AnalysisConfig analysis = new AnalysisConfig();
    final AnalysisConfig.SpoonConfig spoon = new AnalysisConfig.SpoonConfig();
    spoon.setNoClasspath(false);
    analysis.setSpoon(spoon);
    analysis.setClasspath(new AnalysisConfig.ClasspathConfig());
    config.setAnalysis(analysis);

    final OutputConfig outputConfig = new OutputConfig();
    outputConfig.getFormat().setTasks("json");
    outputConfig.getFormat().setReport("json");
    config.setOutput(outputConfig);
    return config;
  }

  public static class AnalysisConfig
      extends com.craftsmanbro.fulcraft.plugins.analysis.config.AnalysisConfig {

    public boolean isNoClasspathEnabled() {
      final com.craftsmanbro.fulcraft.plugins.analysis.config.AnalysisConfig.SpoonConfig spoon =
          getSpoon();
      return spoon != null && Boolean.TRUE.equals(spoon.getNoClasspath());
    }
  }

  /** Detects the default LLM provider based on available environment variables. */
  private static String detectDefaultProvider() {
    if (System.getenv("GEMINI_API_KEY") != null) {
      return PROVIDER_GEMINI;
    }
    if (System.getenv("OPENAI_API_KEY") != null) {
      return PROVIDER_OPENAI;
    }
    if (System.getenv("ANTHROPIC_API_KEY") != null) {
      return PROVIDER_ANTHROPIC;
    }
    return PROVIDER_GEMINI;
  }

  /** Detects the API key from environment variables based on provider. */
  private static String detectApiKey(final String provider) {
    return switch (provider) {
      case PROVIDER_GEMINI -> System.getenv("GEMINI_API_KEY");
      case PROVIDER_OPENAI -> System.getenv("OPENAI_API_KEY");
      case PROVIDER_ANTHROPIC -> System.getenv("ANTHROPIC_API_KEY");
      default -> null;
    };
  }

  /** Gets the default model name for the given provider. */
  private static String detectDefaultModel(final String provider) {
    return switch (provider) {
      case PROVIDER_GEMINI -> "gemini-2.0-flash-exp";
      case PROVIDER_OPENAI -> "gpt-4o";
      case PROVIDER_ANTHROPIC -> "claude-sonnet-4-20250514";
      default -> null;
    };
  }

  public static class ProjectConfig extends ConfigSectionBases.ProjectConfig {}

  /**
   * Backward-compatible alias for selection-rules config.
   *
   * <p>Actual implementation lives in plugin package to keep plugin-owned settings close to the
   * owning plugin while preserving Config.SelectionRules references.
   */
  public static class SelectionRules
      extends com.craftsmanbro.fulcraft.plugins.analysis.config.SelectionRulesConfig {}

  public static class LlmConfig extends ConfigSectionBases.LlmConfig {}

  public static class ExecutionConfig extends ConfigSectionBases.ExecutionConfig {}

  /**
   * Configuration for brittle test detection rules.
   *
   * <p>These settings control which patterns are detected in generated tests and whether they
   * should cause warnings or failures.
   */
  public static class BrittleTestRulesConfig
      extends com.craftsmanbro.fulcraft.config.junit.BrittleTestRulesConfig {}

  public VerificationConfig getVerification() {
    if (verification == null) {
      verification = new VerificationConfig();
    }
    return verification;
  }

  public boolean hasVerificationConfig() {
    return verification != null;
  }

  public void setVerification(final VerificationConfig verification) {
    this.verification = verification;
  }

  /**
   * Gets the pipeline configuration, or a default if not set.
   *
   * @return the pipeline configuration
   */
  public PipelineConfig getPipeline() {
    if (pipeline == null) {
      pipeline = new PipelineConfig();
    }
    return pipeline;
  }

  public void setPipeline(final PipelineConfig pipeline) {
    this.pipeline = pipeline;
  }

  /**
   * Configuration for the verification phase of the pipeline.
   *
   * <p>This includes settings for test execution, flaky test detection, and test result analysis.
   */
  public static class VerificationConfig
      extends com.craftsmanbro.fulcraft.config.junit.VerificationConfig {}

  public ContextAwarenessConfig getContextAwareness() {
    if (contextAwareness == null) {
      contextAwareness = new ContextAwarenessConfig();
    }
    return contextAwareness;
  }

  public void setContextAwareness(final ContextAwarenessConfig contextAwareness) {
    this.contextAwareness = contextAwareness;
  }

  /**
   * Configuration for automatic context awareness.
   *
   * <p>This feature analyzes existing tests in the project to learn the project's testing style
   * (assertion library, mocking patterns, base test classes, etc.) and injects this information
   * into LLM prompts to generate tests that match the project's conventions.
   */
  public static class ContextAwarenessConfig
      extends com.craftsmanbro.fulcraft.config.junit.ContextAwarenessConfig {}

  public GenerationConfig getGeneration() {
    if (generation == null) {
      generation = new GenerationConfig();
    }
    return generation;
  }

  public void setGeneration(final GenerationConfig generation) {
    this.generation = generation;
  }

  public GovernanceConfig getGovernance() {
    if (governance == null) {
      governance = new GovernanceConfig();
    }
    return governance;
  }

  public void setGovernance(final GovernanceConfig governance) {
    this.governance = governance;
  }

  public AuditConfig getAudit() {
    if (audit == null) {
      audit = new AuditConfig();
    }
    return audit;
  }

  public void setAudit(final AuditConfig audit) {
    this.audit = audit;
  }

  public QuotaConfig getQuota() {
    if (quota == null) {
      quota = new QuotaConfig();
    }
    return quota;
  }

  public void setQuota(final QuotaConfig quota) {
    this.quota = quota;
  }

  /**
   * Configuration for test generation behavior.
   *
   * <p>Controls marker insertion, prompt templates, and other generation-related settings.
   */
  public static class GenerationConfig
      extends com.craftsmanbro.fulcraft.config.junit.GenerationConfig {}

  public MockingConfig getMocking() {
    if (mocking == null) {
      mocking = new MockingConfig();
    }
    return mocking;
  }

  public void setMocking(final MockingConfig mocking) {
    this.mocking = mocking;
  }

  /** Configuration for mocking behavior. */
  public static class MockingConfig extends com.craftsmanbro.fulcraft.config.junit.MockingConfig {}

  /**
   * Configuration for governance policies.
   *
   * <p>Controls security-related policies such as external LLM transmission control.
   */
  @Setter
  public static class GovernanceConfig {

    private static final String POLICY_ALLOW = "allow";

    private static final String MODE_ENFORCE = "enforce";

    /**
     * External transmission policy: 'allow' or 'deny'. When 'deny', all external LLM API calls are
     * blocked at the central gateway.
     */
    @JsonProperty("external_transmission")
    private String externalTransmission = POLICY_ALLOW;

    /** Redaction configuration for sensitive data detection and masking. */
    @JsonProperty("redaction")
    private RedactionConfig redaction;

    public String getExternalTransmission() {
      return externalTransmission != null ? externalTransmission : POLICY_ALLOW;
    }

    /** Returns true if external transmission is denied by policy. */
    public boolean isExternalTransmissionDenied() {
      return "deny".equalsIgnoreCase(getExternalTransmission());
    }

    /** Returns true if external transmission is allowed. */
    public boolean isExternalTransmissionAllowed() {
      return POLICY_ALLOW.equalsIgnoreCase(getExternalTransmission());
    }

    public RedactionConfig getRedaction() {
      if (redaction == null) {
        redaction = new RedactionConfig();
      }
      return redaction;
    }

    /**
     * Configuration for sensitive data redaction.
     *
     * <p>Controls detector chain, thresholds, and dictionary paths for prompt redaction before LLM
     * transmission.
     */
    @Setter
    public static class RedactionConfig {

      /** Redaction mode: 'off', 'report', or 'enforce'. */
      @JsonProperty("mode")
      private String mode = MODE_ENFORCE;

      /** Path to denylist file (relative to project root). */
      @JsonProperty("denylist_path")
      @Getter
      private String denylistPath;

      /** Path to allowlist file (relative to project root). */
      @JsonProperty("allowlist_path")
      @Getter
      private String allowlistPath;

      /** List of enabled detectors in execution order. */
      @JsonProperty("detectors")
      private java.util.List<String> detectors = java.util.List.of("regex", "dictionary", "ml");

      /** Confidence threshold for masking (0.0-1.0). */
      @JsonProperty("mask_threshold")
      private Double maskThreshold = 0.60;

      /** Confidence threshold for blocking (0.0-1.0). */
      @JsonProperty("block_threshold")
      private Double blockThreshold = 0.90;

      /** URL of ML NER service for ML detector. */
      @JsonProperty("ml_endpoint_url")
      @Getter
      private String mlEndpointUrl;

      public String getMode() {
        return mode != null ? mode.toLowerCase(java.util.Locale.ROOT) : MODE_ENFORCE;
      }

      /** Returns true if redaction mode is 'off'. */
      public boolean isOff() {
        return "off".equalsIgnoreCase(getMode());
      }

      /** Returns true if redaction mode is 'report' (detect but don't block). */
      public boolean isReportOnly() {
        return "report".equalsIgnoreCase(getMode());
      }

      /** Returns true if redaction mode is 'enforce' (detect, mask, and block). */
      public boolean isEnforce() {
        return MODE_ENFORCE.equalsIgnoreCase(getMode());
      }

      public java.util.List<String> getDetectors() {
        return detectors != null ? detectors : java.util.List.of("regex", "dictionary", "ml");
      }

      public double getMaskThreshold() {
        return maskThreshold != null ? maskThreshold : 0.60;
      }

      public double getBlockThreshold() {
        return blockThreshold != null ? blockThreshold : 0.90;
      }
    }
  }

  public static class AuditConfig extends ConfigSectionBases.AuditConfig {}

  public static class QuotaConfig extends ConfigSectionBases.QuotaConfig {}

  public LocalFixConfig getLocalFix() {
    if (localFix == null) {
      localFix = new LocalFixConfig();
    }
    return localFix;
  }

  public void setLocalFix(final LocalFixConfig localFix) {
    this.localFix = localFix;
  }

  public OutputConfig getOutput() {
    if (output == null) {
      output = new OutputConfig();
    }
    return output;
  }

  public void setOutput(final OutputConfig output) {
    this.output = output;
  }

  /**
   * Configuration for local auto-fix patterns.
   *
   * <p>Controls which extended fix patterns are enabled. All patterns are disabled by default for
   * safety and can be individually enabled as needed.
   */
  public static class LocalFixConfig
      extends com.craftsmanbro.fulcraft.config.junit.LocalFixConfig {}

  public static class OutputConfig extends ConfigSectionBases.OutputConfig {}

  public LogConfig getLog() {
    if (log == null) {
      log = new LogConfig();
    }
    return log;
  }

  public void setLog(final LogConfig log) {
    this.log = log;
  }

  public static class LogConfig extends ConfigSectionBases.LogConfig {}

  /**
   * Configuration for document generation.
   *
   * <p>Controls output format (markdown/html/pdf), diagram generation, and test link inclusion.
   */
  public static class DocsConfig
      extends com.craftsmanbro.fulcraft.plugins.document.config.DocsConfig {}

  public QualityGateConfig getQualityGate() {
    if (qualityGate == null) {
      qualityGate = new QualityGateConfig();
    }
    return qualityGate;
  }

  public void setQualityGate(final QualityGateConfig qualityGate) {
    this.qualityGate = qualityGate;
  }

  public CacheConfig getCache() {
    if (cache == null) {
      cache = new CacheConfig();
    }
    return cache;
  }

  public void setCache(final CacheConfig cache) {
    this.cache = cache;
  }

  public static class QualityGateConfig extends ConfigSectionBases.QualityGateConfig {}

  public static class CacheConfig extends ConfigSectionBases.CacheConfig {}

  /** Configuration for CLI behavior. */
  public static class CliConfig extends ConfigSectionBases.CliConfig {}

  /**
   * Configuration for pipeline phase interceptors.
   *
   * <p>This allows users to configure which interceptors are enabled, their order, and any
   * interceptor-specific settings.
   */
  public static class InterceptorsConfig {

    private static final String STEP_RUN = "RUN";
    private static final String STEP_RUN_TESTS = "RUN_TESTS";

    private final java.util.Map<String, PhaseInterceptorsConfig> phaseConfigs =
        new java.util.LinkedHashMap<>();

    @JsonAnySetter
    public void setForStep(final String stepName, final PhaseInterceptorsConfig phaseConfig) {
      final String normalizedStepName = normalizeStepName(stepName);
      if (normalizedStepName == null || phaseConfig == null) {
        return;
      }
      phaseConfigs.put(normalizedStepName, phaseConfig);
    }

    @JsonAnyGetter
    public java.util.Map<String, PhaseInterceptorsConfig> phaseConfigs() {
      return phaseConfigs;
    }

    /**
     * Gets the configuration for a specific phase by step name.
     *
     * @param stepName the step name (e.g., "ANALYZE", "SELECT")
     * @return the phase interceptor config, or null if not configured
     */
    public PhaseInterceptorsConfig getForStep(final String stepName) {
      final String normalizedStepName = normalizeStepName(stepName);
      if (normalizedStepName == null) {
        return null;
      }
      final PhaseInterceptorsConfig directConfig = phaseConfigs.get(normalizedStepName);
      if (directConfig != null) {
        return directConfig;
      }
      if (STEP_RUN.equals(normalizedStepName)) {
        return phaseConfigs.get(STEP_RUN_TESTS);
      }
      return null;
    }

    public java.util.Collection<PhaseInterceptorsConfig> configuredPhaseConfigs() {
      return phaseConfigs.values();
    }

    private static String normalizeStepName(final String stepName) {
      if (stepName == null || stepName.isBlank()) {
        return null;
      }
      return stepName.trim().toUpperCase(java.util.Locale.ROOT);
    }
  }

  /** Configuration for interceptors within a single phase. */
  @Setter
  public static class PhaseInterceptorsConfig {

    @JsonProperty("pre")
    private java.util.List<InterceptorEntryConfig> pre = new java.util.ArrayList<>();

    @JsonProperty("post")
    private java.util.List<InterceptorEntryConfig> post = new java.util.ArrayList<>();

    public java.util.List<InterceptorEntryConfig> getPre() {
      if (pre == null) {
        pre = new java.util.ArrayList<>();
      }
      return pre;
    }

    public java.util.List<InterceptorEntryConfig> getPost() {
      if (post == null) {
        post = new java.util.ArrayList<>();
      }
      return post;
    }
  }

  /** Configuration for a single interceptor entry. */
  @Setter
  public static class InterceptorEntryConfig {

    @JsonProperty("class")
    @Getter
    private String className;

    @JsonProperty("enabled")
    private Boolean enabled = true;

    @JsonProperty("order")
    @Getter
    private Integer order;

    public Boolean getEnabled() {
      return enabled == null || enabled;
    }
  }

  /** Configuration for pipeline stage control. */
  public static class PipelineConfig extends ConfigSectionBases.PipelineConfig {}
}
