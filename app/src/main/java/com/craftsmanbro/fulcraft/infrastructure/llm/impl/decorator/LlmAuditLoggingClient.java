package com.craftsmanbro.fulcraft.infrastructure.llm.impl.decorator;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.audit.contract.AuditLogPort;
import com.craftsmanbro.fulcraft.infrastructure.audit.model.AuditEvent;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.TokenUsageAware;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.RedactionContext;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.RedactionReport;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import com.craftsmanbro.fulcraft.infrastructure.usage.model.TokenUsage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class LlmAuditLoggingClient implements LlmClientPort, TokenUsageAware {

  private static final String HASH_ALGORITHM = "SHA-256";
  private static final String STATUS_SUCCESS = "success";
  private static final String STATUS_FAILURE = "failure";
  private static final String UNKNOWN_ERROR_TYPE = "UnknownError";

  private final LlmClientPort delegate;

  private final AuditLogPort auditLogger;

  public LlmAuditLoggingClient(final LlmClientPort delegate, final AuditLogPort auditLogger) {
    this.delegate =
        Objects.requireNonNull(
            delegate,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "delegate must not be null"));
    this.auditLogger =
        Objects.requireNonNull(
            auditLogger,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "auditLogger must not be null"));
  }

  @Override
  public String generateTest(final String prompt, final Config.LlmConfig llmConfig) {
    if (!auditLogger.isEnabled()) {
      clearRedactionContext();
      return delegate.generateTest(prompt, llmConfig);
    }
    final String promptRaw = RedactionContext.consumePrompt();
    final RedactionReport redactionReport = RedactionContext.consumeReport();
    final String promptHash = sha256(prompt);
    final long requestChars = charCount(prompt);
    try {
      final String response = delegate.generateTest(prompt, llmConfig);
      final TokenUsage usage = resolveTokenUsage();
      logSuccess(
          llmConfig, prompt, promptRaw, response, promptHash, requestChars, usage, redactionReport);
      return response;
    } catch (final RuntimeException error) {
      logFailure(llmConfig, prompt, promptRaw, promptHash, requestChars, redactionReport, error);
      throw error;
    }
  }

  @Override
  public boolean isHealthy() {
    return delegate.isHealthy();
  }

  @Override
  public ProviderProfile profile() {
    return delegate.profile();
  }

  @Override
  public void clearContext() {
    delegate.clearContext();
  }

  @Override
  public Optional<TokenUsage> getLastUsage() {
    return resolveDelegateUsage();
  }

  private void logSuccess(
      final Config.LlmConfig llmConfig,
      final String prompt,
      final String promptRaw,
      final String response,
      final String promptHash,
      final long requestChars,
      final TokenUsage usage,
      final RedactionReport redactionReport) {
    final long responseChars = charCount(response);
    final String responseHash = sha256(response);
    auditLogger.logExchange(
        buildAuditEvent(
            llmConfig,
            prompt,
            promptRaw,
            response,
            promptHash,
            responseHash,
            requestChars,
            responseChars,
            usage,
            redactionReport,
            STATUS_SUCCESS,
            null));
  }

  private void logFailure(
      final Config.LlmConfig llmConfig,
      final String prompt,
      final String promptRaw,
      final String promptHash,
      final long requestChars,
      final RedactionReport redactionReport,
      final RuntimeException error) {
    final String errorType = error != null ? error.getClass().getSimpleName() : UNKNOWN_ERROR_TYPE;
    auditLogger.logExchange(
        buildAuditEvent(
            llmConfig,
            prompt,
            promptRaw,
            null,
            promptHash,
            null,
            requestChars,
            0L,
            null,
            redactionReport,
            STATUS_FAILURE,
            errorType));
  }

  private AuditEvent buildAuditEvent(
      final Config.LlmConfig llmConfig,
      final String prompt,
      final String promptRaw,
      final String response,
      final String promptHash,
      final String responseHash,
      final long requestChars,
      final long responseChars,
      final TokenUsage usage,
      final RedactionReport redactionReport,
      final String status,
      final String errorType) {
    return new AuditEvent(
        providerOf(llmConfig),
        modelOf(llmConfig),
        prompt,
        promptRaw,
        response,
        promptHash,
        responseHash,
        requestChars,
        responseChars,
        usage,
        redactionReport,
        status,
        errorType);
  }

  private TokenUsage resolveTokenUsage() {
    return resolveDelegateUsage().orElse(null);
  }

  private Optional<TokenUsage> resolveDelegateUsage() {
    return usageAwareDelegate().flatMap(TokenUsageAware::getLastUsage);
  }

  private long charCount(final String value) {
    return value != null ? value.length() : 0L;
  }

  private String providerOf(final Config.LlmConfig llmConfig) {
    return llmConfigValue(llmConfig, Config.LlmConfig::getProvider);
  }

  private String modelOf(final Config.LlmConfig llmConfig) {
    return llmConfigValue(llmConfig, Config.LlmConfig::getModelName);
  }

  private String llmConfigValue(
      final Config.LlmConfig llmConfig, final Function<Config.LlmConfig, String> extractor) {
    if (llmConfig == null) {
      return "";
    }
    return nullToEmpty(extractor.apply(llmConfig));
  }

  private String nullToEmpty(final String value) {
    return value == null ? "" : value;
  }

  private String sha256(final String value) {
    return hash(value, HASH_ALGORITHM);
  }

  private String hash(final String value, final String algorithm) {
    if (value == null) {
      return null;
    }
    try {
      final MessageDigest digest = MessageDigest.getInstance(algorithm);
      final byte[] digestBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digestBytes);
    } catch (final NoSuchAlgorithmException error) {
      return null;
    }
  }

  private Optional<TokenUsageAware> usageAwareDelegate() {
    if (!(delegate instanceof TokenUsageAware aware)) {
      return Optional.empty();
    }
    return Optional.of(aware);
  }

  private void clearRedactionContext() {
    RedactionContext.consumeReport();
    RedactionContext.consumePrompt();
  }
}
