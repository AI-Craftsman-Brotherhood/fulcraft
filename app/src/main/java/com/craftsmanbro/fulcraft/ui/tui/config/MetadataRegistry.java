package com.craftsmanbro.fulcraft.ui.tui.config;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.tui.PathParser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MetadataRegistry {

  private static final MetadataRegistry DEFAULT = buildDefault();

  private static final String VAL_STRICT = "STRICT";

  private static final String VAL_AUTO = "AUTO";

  private static final String VAL_OFF = "OFF";

  private static final String VAL_TRUE = "true";

  private static final String VAL_DELOMBOK = "DELOMBOK";

  private static final String KEY_ANALYSIS_PREPROCESS_MODE = "analysis.preprocess.mode";

  private static final String KEY_ANALYSIS_PREPROCESS_TOOL = "analysis.preprocess.tool";

  private static final String KEY_LLM_PROVIDER = "llm.provider";

  private static final String KEY_CONTEXT_AWARENESS_ENABLED = "context_awareness.enabled";

  private static final String KEY_GENERATION_MARKER_ENABLED = "generation.marker.enabled";

  private static final String PROV_AZURE_OPENAI = "azure-openai";

  private static final String PROV_AZURE_OPENAI_ALT = "azure_openai";

  private static final String PROV_VERTEX = "vertex";

  private static final String PROV_VERTEX_AI = "vertex-ai";

  private static final String PROV_VERTEX_AI_ALT = "vertex_ai";

  private static final String PROV_BEDROCK = "bedrock";

  private final Map<String, ConfigKeyMetadata> entries;

  private MetadataRegistry(final Map<String, ConfigKeyMetadata> entries) {
    this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
  }

  public static MetadataRegistry getDefault() {
    return DEFAULT;
  }

  public Optional<ConfigKeyMetadata> find(final String path) {
    if (path == null || path.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(entries.get(path));
  }

  public List<ConfigKeyMetadata> search(final String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return List.copyOf(entries.values());
    }
    final String needle = keyword.toLowerCase(java.util.Locale.ROOT);
    final List<ConfigKeyMetadata> matches = new ArrayList<>();
    for (final ConfigKeyMetadata metadata : entries.values()) {
      if (metadata.path().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
        matches.add(metadata);
        continue;
      }
      final String description = metadata.localizedDescription();
      if (description != null && description.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
        matches.add(metadata);
      }
    }
    return matches;
  }

  public Collection<ConfigKeyMetadata> all() {
    return entries.values();
  }

  public enum ValueType {
    BOOLEAN,
    INTEGER,
    FLOAT,
    STRING,
    LIST,
    OBJECT
  }

  public enum ConditionOperator {
    ANY_OF,
    NONE_OF
  }

  public record VisibilityCondition(
      List<ConfigEditor.PathSegment> path, ConditionOperator operator, List<String> values) {

    public boolean matches(final ConfigEditor editor) {
      if (editor == null || path == null || path.isEmpty()) {
        return false;
      }
      final Object current = editor.getValue(path);
      if (current == null) {
        return false;
      }
      final String actual = String.valueOf(current).trim();
      boolean match = false;
      for (final String value : values) {
        if (value.equalsIgnoreCase(actual)) {
          match = true;
          break;
        }
      }
      if (operator == ConditionOperator.ANY_OF) {
        return match;
      }
      return !match;
    }
  }

  public record ConfigKeyMetadata(
      String path,
      ValueType type,
      ValueType elementType,
      List<String> enumOptions,
      List<VisibilityCondition> visibilityConditions,
      String description,
      String descriptionKey) {

    public ConfigKeyMetadata {
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(type, "type");
      enumOptions = enumOptions == null ? List.of() : List.copyOf(enumOptions);
      visibilityConditions =
          visibilityConditions == null ? List.of() : List.copyOf(visibilityConditions);
      descriptionKey = descriptionKey == null ? "" : descriptionKey;
    }

    public boolean isList() {
      return type == ValueType.LIST;
    }

    public boolean isEnum() {
      return enumOptions != null && !enumOptions.isEmpty();
    }

    public boolean isVisible(final ConfigEditor editor) {
      if (visibilityConditions == null || visibilityConditions.isEmpty()) {
        return true;
      }
      for (final VisibilityCondition condition : visibilityConditions) {
        if (!condition.matches(editor)) {
          return false;
        }
      }
      return true;
    }

    public String localizedDescription() {
      if (descriptionKey != null && !descriptionKey.isBlank()) {
        final String localized = MessageSource.getMessage(descriptionKey);
        if (!localized.equals(descriptionKey)) {
          return localized;
        }
      }
      return description;
    }
  }

  private static final class Builder {

    private final List<EntryBuilder> entries = new ArrayList<>();

    public EntryBuilder add(final String path, final ValueType type, final String description) {
      final EntryBuilder builder = new EntryBuilder(path, type, description);
      entries.add(builder);
      return builder;
    }

    public EntryBuilder addList(
        final String path, final ValueType elementType, final String description) {
      final EntryBuilder builder = new EntryBuilder(path, ValueType.LIST, description);
      builder.elementType(elementType);
      entries.add(builder);
      return builder;
    }

    public MetadataRegistry build() {
      final Map<String, ConfigKeyMetadata> map = new LinkedHashMap<>();
      for (final EntryBuilder entry : entries) {
        final ConfigKeyMetadata metadata = entry.build();
        map.put(metadata.path(), metadata);
      }
      return new MetadataRegistry(map);
    }
  }

  private static final class EntryBuilder {

    private final String path;

    private final ValueType type;

    private ValueType elementType;

    private List<String> enumOptions = List.of();

    private final List<VisibilityCondition> conditions = new ArrayList<>();

    private final String description;

    private final String descriptionKey;

    private EntryBuilder(final String path, final ValueType type, final String description) {
      this.path = Objects.requireNonNull(path, "path");
      this.type = Objects.requireNonNull(type, "type");
      this.description = description;
      this.descriptionKey = "config.desc." + path;
    }

    public EntryBuilder elementType(final ValueType type) {
      this.elementType = type;
      return this;
    }

    public EntryBuilder enumOptions(final String... options) {
      if (options == null || options.length == 0) {
        return this;
      }
      this.enumOptions = List.of(options);
      return this;
    }

    public EntryBuilder visibleWhen(final String path, final String... values) {
      return addCondition(path, ConditionOperator.ANY_OF, values);
    }

    private EntryBuilder addCondition(
        final String path, final ConditionOperator operator, final String... values) {
      if (path == null || path.isBlank() || values == null || values.length == 0) {
        return this;
      }
      final List<ConfigEditor.PathSegment> segments = PathParser.parse(path).segments();
      conditions.add(new VisibilityCondition(segments, operator, List.of(values)));
      return this;
    }

    public ConfigKeyMetadata build() {
      return new ConfigKeyMetadata(
          path, type, elementType, enumOptions, conditions, description, descriptionKey);
    }
  }

  private static MetadataRegistry buildDefault() {
    final Builder builder = new Builder();
    registerProjectKeys(builder);
    registerAnalysisKeys(builder);
    registerSelectionRuleKeys(builder);
    registerLlmKeys(builder);
    registerContextAndGenerationKeys(builder);
    registerGovernanceAuditQuotaKeys(builder);
    registerQualityGateKeys(builder);
    registerExecutionAndOutputKeys(builder);
    return builder.build();
  }

  private static void registerProjectKeys(final Builder builder) {
    builder.add("AppName", ValueType.STRING, "Application name");
    builder.add("version", ValueType.STRING, "Application version");
    builder.add("project.id", ValueType.STRING, "Project identifier");
    builder.add("project.root", ValueType.STRING, "Project root path");
    builder.add("project.docs_output", ValueType.STRING, "Documentation output directory");
    builder.add("project.repo_url", ValueType.STRING, "Repository URL");
    builder.add("project.commit", ValueType.STRING, "Commit or ref");
    builder
        .add("project.build_tool", ValueType.STRING, "Build tool name")
        .enumOptions("gradle", "maven");
    builder.add("project.build_command", ValueType.STRING, "Build command");
    builder.addList("project.exclude_paths", ValueType.STRING, "Paths to exclude from analysis");
    builder.addList("project.include_paths", ValueType.STRING, "Paths to include in analysis");
  }

  private static void registerAnalysisKeys(final Builder builder) {
    builder.add("analysis.spoon.no_classpath", ValueType.BOOLEAN, "Force Spoon noClasspath mode");
    builder
        .add("analysis.classpath.mode", ValueType.STRING, "Classpath resolution mode")
        .enumOptions(VAL_AUTO, VAL_STRICT, VAL_OFF);
    builder
        .add("analysis.source_root_mode", ValueType.STRING, "Source root resolution mode")
        .enumOptions(VAL_AUTO, VAL_STRICT);
    builder.addList("analysis.source_root_paths", ValueType.STRING, "Configured source root paths");
    builder.add("analysis.source_charset", ValueType.STRING, "Source file charset");
    builder.add("analysis.dump_file_list", ValueType.BOOLEAN, "Write analyzed file list");
    builder
        .add(KEY_ANALYSIS_PREPROCESS_MODE, ValueType.STRING, "Preprocess mode")
        .enumOptions(VAL_OFF, VAL_AUTO, VAL_STRICT);
    builder
        .add(KEY_ANALYSIS_PREPROCESS_TOOL, ValueType.STRING, "Preprocess tool")
        .enumOptions(VAL_AUTO, VAL_DELOMBOK)
        .visibleWhen(KEY_ANALYSIS_PREPROCESS_MODE, VAL_AUTO, VAL_STRICT);
    builder
        .add("analysis.preprocess.work_dir", ValueType.STRING, "Preprocess working directory")
        .visibleWhen(KEY_ANALYSIS_PREPROCESS_MODE, VAL_AUTO, VAL_STRICT);
    builder
        .add("analysis.preprocess.clean_work_dir", ValueType.BOOLEAN, "Clean preprocess directory")
        .visibleWhen(KEY_ANALYSIS_PREPROCESS_MODE, VAL_AUTO, VAL_STRICT);
    builder
        .add(
            "analysis.preprocess.include_generated", ValueType.BOOLEAN, "Include generated sources")
        .visibleWhen(KEY_ANALYSIS_PREPROCESS_MODE, VAL_AUTO, VAL_STRICT);
    builder
        .add("analysis.preprocess.delombok.enabled", ValueType.BOOLEAN, "Enable delombok")
        .visibleWhen(KEY_ANALYSIS_PREPROCESS_MODE, VAL_AUTO, VAL_STRICT)
        .visibleWhen(KEY_ANALYSIS_PREPROCESS_TOOL, VAL_AUTO, VAL_DELOMBOK);
    builder
        .add("analysis.preprocess.delombok.lombok_jar_path", ValueType.STRING, "Path to lombok.jar")
        .visibleWhen(KEY_ANALYSIS_PREPROCESS_MODE, VAL_AUTO, VAL_STRICT)
        .visibleWhen(KEY_ANALYSIS_PREPROCESS_TOOL, VAL_AUTO, VAL_DELOMBOK);
    builder.add(
        "analysis.external_config_resolution", ValueType.BOOLEAN, "Resolve external config values");
    builder.add(
        "analysis.enable_interprocedural_resolution",
        ValueType.BOOLEAN,
        "Enable interprocedural resolution");
    builder.add(
        "analysis.interprocedural_callsite_limit",
        ValueType.INTEGER,
        "Interprocedural callsite limit");
    builder.add(
        "analysis.debug_dynamic_resolution",
        ValueType.BOOLEAN,
        "Enable dynamic resolution debug output");
    builder.add(
        "analysis.experimental_candidate_enum",
        ValueType.BOOLEAN,
        "Enable experimental candidate enum");
  }

  private static void registerSelectionRuleKeys(final Builder builder) {
    builder.add("selection_rules.class_min_loc", ValueType.INTEGER, "Min class LOC");
    builder.add(
        "selection_rules.class_min_method_count", ValueType.INTEGER, "Min class method count");
    builder.add("selection_rules.method_min_loc", ValueType.INTEGER, "Min method LOC");
    builder.add("selection_rules.method_max_loc", ValueType.INTEGER, "Max method LOC");
    builder.add("selection_rules.max_targets", ValueType.INTEGER, "Max targets to select");
    builder.add(
        "selection_rules.max_methods_per_class", ValueType.INTEGER, "Max methods per class");
    builder
        .add("selection_rules.selection_engine", ValueType.STRING, "Selection engine")
        .enumOptions("rule_based");
    builder.add(
        "selection_rules.exclude_getters_setters", ValueType.BOOLEAN, "Exclude getters/setters");
    builder.add(
        "selection_rules.max_methods_per_package", ValueType.INTEGER, "Max methods per package");
    builder.addList(
        "selection_rules.exclude_annotations", ValueType.STRING, "Excluded annotations");
    builder.addList(
        "selection_rules.deprioritize_annotations", ValueType.STRING, "Deprioritized annotations");
    builder.addList(
        "selection_rules.priority_annotations", ValueType.STRING, "Priority annotations");
    builder.add(
        "selection_rules.complexity.max_cyclomatic",
        ValueType.INTEGER,
        "Max cyclomatic complexity");
    builder
        .add("selection_rules.complexity.strategy", ValueType.STRING, "Complexity strategy")
        .enumOptions("skip", "warn", "split", "specialized_prompt");
    builder.add(
        "selection_rules.complexity.expected_tests_per_complexity",
        ValueType.FLOAT,
        "Expected tests per complexity");
    builder.add(
        "selection_rules.complexity.max_expected_tests", ValueType.INTEGER, "Max expected tests");
    builder.add("selection_rules.removal_boost", ValueType.FLOAT, "Removal boost score");
    builder.add(
        "selection_rules.deprioritize_factor", ValueType.FLOAT, "Deprioritize score factor");
    builder.add(
        "selection_rules.feasibility_penalties.external_io",
        ValueType.FLOAT,
        "External IO penalty");
    builder.add(
        "selection_rules.feasibility_penalties.di_dependency",
        ValueType.FLOAT,
        "DI dependency penalty");
    builder.add(
        "selection_rules.feasibility_penalties.unsupported_param",
        ValueType.FLOAT,
        "Unsupported param penalty");
    builder.add(
        "selection_rules.feasibility_penalties.high_param_count",
        ValueType.FLOAT,
        "High parameter count penalty");
    builder.add(
        "selection_rules.feasibility_penalties.high_complexity",
        ValueType.FLOAT,
        "High complexity penalty");
    builder.add(
        "selection_rules.feasibility_penalties.unresolved_calls",
        ValueType.FLOAT,
        "Unresolved call penalty");
    builder.add(
        "selection_rules.feasibility_penalties.static_utility_bonus",
        ValueType.FLOAT,
        "Static utility bonus");
  }

  private static void registerLlmKeys(final Builder builder) {
    builder
        .add(KEY_LLM_PROVIDER, ValueType.STRING, "LLM provider")
        .enumOptions(
            "gemini",
            "local",
            "ollama",
            "openai",
            "openai-compatible",
            "openai_compatible",
            PROV_AZURE_OPENAI,
            PROV_AZURE_OPENAI_ALT,
            "anthropic",
            PROV_BEDROCK,
            PROV_VERTEX,
            PROV_VERTEX_AI,
            PROV_VERTEX_AI_ALT,
            "mock");
    builder.addList("llm.allowed_providers", ValueType.STRING, "Allowed providers");
    builder.add("llm.allowed_models", ValueType.OBJECT, "Allowed models by provider");
    builder.add("llm.max_retries", ValueType.INTEGER, "Max retry attempts");
    builder.add("llm.fix_retries", ValueType.INTEGER, "Max fix attempts");
    builder.add("llm.model_name", ValueType.STRING, "Model name");
    builder.add("llm.api_key", ValueType.STRING, "API key");
    builder.add("llm.url", ValueType.STRING, "Provider base URL");
    builder.add("llm.custom_headers", ValueType.OBJECT, "Custom HTTP headers");
    builder.add("llm.connect_timeout", ValueType.INTEGER, "Connect timeout (ms)");
    builder.add("llm.request_timeout", ValueType.INTEGER, "Request timeout (ms)");
    builder.add("llm.max_response_length", ValueType.INTEGER, "Max response length");
    builder.add("llm.fallback_stub_enabled", ValueType.BOOLEAN, "Enable fallback stub");
    builder.add("llm.javac_validation", ValueType.BOOLEAN, "Enable javac validation");
    builder.add("llm.retry_initial_delay_ms", ValueType.INTEGER, "Retry initial delay (ms)");
    builder.add("llm.retry_backoff_multiplier", ValueType.FLOAT, "Retry backoff multiplier");
    builder.add("llm.rate_limit_qps", ValueType.FLOAT, "Rate limit (QPS)");
    builder.add("llm.circuit_breaker_threshold", ValueType.INTEGER, "Circuit breaker threshold");
    builder.add("llm.circuit_breaker_reset_ms", ValueType.INTEGER, "Circuit breaker reset (ms)");
    builder
        .add("llm.azure_deployment", ValueType.STRING, "Azure deployment name")
        .visibleWhen(KEY_LLM_PROVIDER, PROV_AZURE_OPENAI, PROV_AZURE_OPENAI_ALT);
    builder
        .add("llm.azure_api_version", ValueType.STRING, "Azure API version")
        .visibleWhen(KEY_LLM_PROVIDER, PROV_AZURE_OPENAI, PROV_AZURE_OPENAI_ALT);
    builder
        .add("llm.vertex_project", ValueType.STRING, "Vertex project id")
        .visibleWhen(KEY_LLM_PROVIDER, PROV_VERTEX, PROV_VERTEX_AI, PROV_VERTEX_AI_ALT);
    builder
        .add("llm.vertex_location", ValueType.STRING, "Vertex location")
        .visibleWhen(KEY_LLM_PROVIDER, PROV_VERTEX, PROV_VERTEX_AI, PROV_VERTEX_AI_ALT);
    builder
        .add("llm.vertex_publisher", ValueType.STRING, "Vertex publisher")
        .visibleWhen(KEY_LLM_PROVIDER, PROV_VERTEX, PROV_VERTEX_AI, PROV_VERTEX_AI_ALT);
    builder
        .add("llm.vertex_model", ValueType.STRING, "Vertex model name")
        .visibleWhen(KEY_LLM_PROVIDER, PROV_VERTEX, PROV_VERTEX_AI, PROV_VERTEX_AI_ALT);
    builder
        .add("llm.aws_access_key_id", ValueType.STRING, "AWS access key id")
        .visibleWhen(KEY_LLM_PROVIDER, PROV_BEDROCK);
    builder
        .add("llm.aws_secret_access_key", ValueType.STRING, "AWS secret access key")
        .visibleWhen(KEY_LLM_PROVIDER, PROV_BEDROCK);
    builder
        .add("llm.aws_session_token", ValueType.STRING, "AWS session token")
        .visibleWhen(KEY_LLM_PROVIDER, PROV_BEDROCK);
    builder
        .add("llm.aws_region", ValueType.STRING, "AWS region")
        .visibleWhen(KEY_LLM_PROVIDER, PROV_BEDROCK);
    builder.add("llm.smart_retry.same_error_max_retries", ValueType.INTEGER, "Smart retry limit");
    builder.add(
        "llm.smart_retry.total_retry_budget_per_task", ValueType.INTEGER, "Smart retry budget");
    builder.add(
        "llm.smart_retry.non_recoverable_max_retries",
        ValueType.INTEGER,
        "Non-recoverable retry limit");
    builder.add("llm.deterministic", ValueType.BOOLEAN, "Deterministic generation");
    builder.add("llm.seed", ValueType.INTEGER, "Deterministic seed");
    builder.add("llm.system_message", ValueType.STRING, "System message");
  }

  private static void registerContextAndGenerationKeys(final Builder builder) {
    builder.add(KEY_CONTEXT_AWARENESS_ENABLED, ValueType.BOOLEAN, "Enable context awareness");
    builder
        .addList("context_awareness.test_dirs", ValueType.STRING, "Test directories to scan")
        .visibleWhen(KEY_CONTEXT_AWARENESS_ENABLED, VAL_TRUE);
    builder
        .addList("context_awareness.include_globs", ValueType.STRING, "Include glob patterns")
        .visibleWhen(KEY_CONTEXT_AWARENESS_ENABLED, VAL_TRUE);
    builder
        .addList("context_awareness.exclude_globs", ValueType.STRING, "Exclude glob patterns")
        .visibleWhen(KEY_CONTEXT_AWARENESS_ENABLED, VAL_TRUE);
    builder
        .add("context_awareness.max_files", ValueType.INTEGER, "Max files to scan")
        .visibleWhen(KEY_CONTEXT_AWARENESS_ENABLED, VAL_TRUE);
    builder
        .add("context_awareness.max_injected_chars", ValueType.INTEGER, "Max injected chars")
        .visibleWhen(KEY_CONTEXT_AWARENESS_ENABLED, VAL_TRUE);
    builder
        .add(
            "context_awareness.exclude_generated_tests",
            ValueType.BOOLEAN,
            "Exclude generated tests")
        .visibleWhen(KEY_CONTEXT_AWARENESS_ENABLED, VAL_TRUE);
    builder
        .add(
            "context_awareness.generated_output_dir",
            ValueType.STRING,
            "Generated output directory")
        .visibleWhen(KEY_CONTEXT_AWARENESS_ENABLED, VAL_TRUE);
    builder.add(KEY_GENERATION_MARKER_ENABLED, ValueType.BOOLEAN, "Enable test markers");
    builder
        .add("generation.marker.tag", ValueType.STRING, "Marker tag")
        .visibleWhen(KEY_GENERATION_MARKER_ENABLED, VAL_TRUE);
    builder
        .add("generation.marker.scan_first_lines", ValueType.INTEGER, "Marker scan line count")
        .visibleWhen(KEY_GENERATION_MARKER_ENABLED, VAL_TRUE);
  }

  private static void registerGovernanceAuditQuotaKeys(final Builder builder) {
    builder
        .add("governance.external_transmission", ValueType.STRING, "External transmission policy")
        .enumOptions("allow", "deny");
    builder
        .add("governance.redaction.mode", ValueType.STRING, "Redaction mode")
        .enumOptions("off", "report", "enforce");
    builder.add("governance.redaction.denylist_path", ValueType.STRING, "Redaction denylist path");
    builder.add(
        "governance.redaction.allowlist_path", ValueType.STRING, "Redaction allowlist path");
    builder.addList("governance.redaction.detectors", ValueType.STRING, "Redaction detectors");
    builder.add("governance.redaction.mask_threshold", ValueType.FLOAT, "Redaction mask threshold");
    builder.add(
        "governance.redaction.block_threshold", ValueType.FLOAT, "Redaction block threshold");
    builder.add(
        "governance.redaction.ml_endpoint_url", ValueType.STRING, "Redaction ML endpoint URL");
    builder.add("audit.enabled", ValueType.BOOLEAN, "Enable audit logging");
    builder.add("audit.log_path", ValueType.STRING, "Audit log path");
    builder.add("audit.include_raw", ValueType.BOOLEAN, "Include raw audit payloads");
    builder.add("quota.max_tasks", ValueType.INTEGER, "Max tasks");
    builder.add("quota.max_llm_calls", ValueType.INTEGER, "Max LLM calls");
    builder
        .add("quota.on_exceed", ValueType.STRING, "Quota exceed policy")
        .enumOptions("warn", "block");
  }

  private static void registerQualityGateKeys(final Builder builder) {
    builder.add("quality_gate.enabled", ValueType.BOOLEAN, "Enable quality gate evaluation");
    builder.add(
        "quality_gate.coverage_threshold", ValueType.FLOAT, "Minimum line coverage threshold");
    builder.add(
        "quality_gate.branch_coverage_threshold",
        ValueType.FLOAT,
        "Minimum branch coverage threshold");
    builder.add(
        "quality_gate.block_blocker_findings", ValueType.BOOLEAN, "Block on blocker findings");
    builder.add(
        "quality_gate.block_critical_findings", ValueType.BOOLEAN, "Block on critical findings");
    builder.add(
        "quality_gate.max_major_findings", ValueType.INTEGER, "Maximum allowed major findings");
    builder.add(
        "quality_gate.allow_warnings", ValueType.BOOLEAN, "Allow warnings without blocking");
    builder.add(
        "quality_gate.apply_to_new_code_only",
        ValueType.BOOLEAN,
        "Apply quality gate only to new code");
    builder.add("quality_gate.min_pass_rate", ValueType.FLOAT, "Minimum test pass rate");
    builder.add("quality_gate.min_compile_rate", ValueType.FLOAT, "Minimum compile success rate");
    builder
        .add("quality_gate.coverage_tool", ValueType.STRING, "Coverage tool")
        .enumOptions("jacoco", "cobertura");
    builder
        .addList("quality_gate.static_analysis_tools", ValueType.STRING, "Static analysis tools")
        .enumOptions("spotbugs", "pmd", "errorprone", "checkstyle");
    builder.add("quality_gate.coverage_report_path", ValueType.STRING, "Coverage report file path");
    builder.addList(
        "quality_gate.static_analysis_report_paths",
        ValueType.STRING,
        "Static analysis report file paths");
  }

  private static void registerExecutionAndOutputKeys(final Builder builder) {
    builder.add("execution.per_task_isolation", ValueType.BOOLEAN, "Enable per-task isolation");
    builder.add("execution.logs_root", ValueType.STRING, "Runs root directory");
    builder
        .add("execution.runtime_fix_retries", ValueType.INTEGER, "Runtime fix retries")
        .visibleWhen("execution.per_task_isolation", VAL_TRUE);
    builder.add("execution.flaky_reruns", ValueType.INTEGER, "Flaky test reruns");
    builder
        .add("execution.unresolved_policy", ValueType.STRING, "Unresolved resolution policy")
        .enumOptions("skip", "minimal");
    builder
        .add("execution.test_stability_policy", ValueType.STRING, "Generated test stability policy")
        .enumOptions("strict", "standard", "relaxed");
    builder
        .add("output.format.tasks", ValueType.STRING, "Tasks output format")
        .enumOptions("json", "yaml", "yml", "jsonl");
    builder
        .add("output.format.report", ValueType.STRING, "Report output format")
        .enumOptions("json", "yaml", "yml", "markdown");
    builder.add("cli.color", ValueType.STRING, "CLI color mode").enumOptions("auto", "on", "off");
    builder.add("cli.interactive.enabled", ValueType.BOOLEAN, "Enable interactive CLI");
    builder
        .addList("pipeline.stages", ValueType.STRING, "Enabled pipeline stages")
        .enumOptions(
            "analyze", "select", "generate", "brittle_check", "report", "document", "explore");
    builder.add("pipeline.workflow_file", ValueType.STRING, "Workflow definition file path");
  }
}
