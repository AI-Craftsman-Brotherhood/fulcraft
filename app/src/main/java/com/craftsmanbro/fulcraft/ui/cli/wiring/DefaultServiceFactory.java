package com.craftsmanbro.fulcraft.ui.cli.wiring;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.audit.contract.AuditLogPort;
import com.craftsmanbro.fulcraft.infrastructure.audit.impl.AuditLogger;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.contract.BuildToolPort;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.DefaultBuildTool;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.config.ProviderConfigValidator;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.config.ProviderConfigValidator.Level;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.config.ProviderConfigValidator.ValidationMessage;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator.LlmAuditLoggingClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator.LlmGovernanceEnforcingClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator.LlmQuotaEnforcingClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator.LlmUsageTrackingClient;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator.OverrideAwareLlmClient;
import com.craftsmanbro.fulcraft.infrastructure.usage.impl.LocalFileUsageStore;
import com.craftsmanbro.fulcraft.infrastructure.usage.impl.TokenUsageEstimator;
import com.craftsmanbro.fulcraft.plugins.analysis.adapter.llm.LlmContractAdapter;
import com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser.JavaParserAnalysisAdapter;
import com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser.SpoonAnalysisAdapter;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.CompositeAnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.ResultMerger;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.ResultVerifier;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import io.opentelemetry.api.trace.Tracer;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** Default wiring for production CLI. */
public final class DefaultServiceFactory implements ServiceFactory {

  private static final String ENGINE_SPOON = "spoon";

  private static final String ENGINE_JAVAPARSER = "javaparser";

  private final Tracer tracer;

  public DefaultServiceFactory(final Tracer tracer) {
    this.tracer = Objects.requireNonNull(tracer, "tracer must not be null");
  }

  private AnalysisPort createJavaParserAnalyzer() {
    return new JavaParserAnalysisAdapter(tracer);
  }

  @Override
  public AnalysisPort createAnalysisPort(final String engineType) {
    if (engineType == null) {
      return createDefaultAnalysisPort();
    }
    final String normalizedEngineType = engineType.trim();
    if (normalizedEngineType.isEmpty()) {
      return createDefaultAnalysisPort();
    }
    return switch (normalizedEngineType.toLowerCase(Locale.ROOT)) {
      case ENGINE_SPOON -> createSpoonAnalyzer();
      case ENGINE_JAVAPARSER -> createJavaParserAnalyzer();
      default -> createDefaultAnalysisPort();
    };
  }

  private AnalysisPort createDefaultAnalysisPort() {
    return new CompositeAnalysisPort(
        List.of(createJavaParserAnalyzer(), createSpoonAnalyzer()), createResultMerger());
  }

  private AnalysisPort createSpoonAnalyzer() {
    return new SpoonAnalysisAdapter();
  }

  @Override
  public ResultMerger createResultMerger() {
    return new ResultMerger();
  }

  @Override
  public ResultVerifier createResultVerifier() {
    return new ResultVerifier(tracer);
  }

  @Override
  public BuildToolPort createBuildTool() {
    return new DefaultBuildTool(tracer);
  }

  @Override
  public LlmClientPort createLlmClient(final Config config) {
    return LlmContractAdapter.toFeature(createInfrastructureLlmClient(config));
  }

  @Override
  public LlmClientPort createDecoratedLlmClient(final Config config, final Path projectRoot) {
    Objects.requireNonNull(config, "config must not be null");
    Objects.requireNonNull(projectRoot, "projectRoot must not be null");
    com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort client =
        createInfrastructureLlmClient(config);
    final var usageStore = new LocalFileUsageStore(projectRoot);
    final var usageEstimator = new TokenUsageEstimator();
    client = new LlmUsageTrackingClient(client, usageStore, usageEstimator);
    client = new LlmQuotaEnforcingClient(client, usageStore, config.getQuota());
    final AuditLogPort auditLogger = new AuditLogger(config, projectRoot);
    client = new LlmAuditLoggingClient(client, auditLogger);
    return LlmContractAdapter.toFeature(client);
  }

  private com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort
      createInfrastructureLlmClient(final Config config) {
    Objects.requireNonNull(config, "config must not be null");
    final com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort baseClient =
        new OverrideAwareLlmClient(config.getLlm());
    final com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort client =
        new LlmGovernanceEnforcingClient(baseClient, config.getGovernance());
    validateProviderConfig(config, client.profile());
    return client;
  }

  private void validateProviderConfig(
      final Config config,
      final com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile profile) {
    final List<ValidationMessage> messages = ProviderConfigValidator.validate(config, profile);
    final List<ValidationMessage> errors =
        messages.stream().filter(m -> m.level() == Level.ERROR).toList();
    final List<ValidationMessage> warnings =
        messages.stream().filter(m -> m.level() == Level.WARN).toList();
    for (final var warn : warnings) {
      UiLogger.warn(warn.message());
    }
    if (!errors.isEmpty()) {
      final String errorMessages =
          errors.stream().map(ValidationMessage::message).collect(Collectors.joining("; "));
      throw new IllegalStateException("Provider configuration errors: " + errorMessages);
    }
  }
}
