package com.craftsmanbro.fulcraft.infrastructure.config.validator.llm;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigSectionValidator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class LlmSectionValidator implements ConfigSectionValidator {

  private static final String LLM = "llm";

  private static final String PROVIDER_GEMINI = "gemini";
  private static final String PROVIDER_LOCAL = "local";
  private static final String PROVIDER_OLLAMA = "ollama";
  private static final String PROVIDER_OPENAI = "openai";
  private static final String PROVIDER_OPENAI_COMPATIBLE = "openai-compatible";
  private static final String PROVIDER_OPENAI_COMPATIBLE_UNDERSCORE = "openai_compatible";
  private static final String PROVIDER_AZURE_OPENAI = "azure-openai";
  private static final String PROVIDER_AZURE_OPENAI_UNDERSCORE = "azure_openai";
  private static final String PROVIDER_ANTHROPIC = "anthropic";
  private static final String PROVIDER_BEDROCK = "bedrock";
  private static final String PROVIDER_VERTEX = "vertex";
  private static final String PROVIDER_VERTEX_AI = "vertex-ai";
  private static final String PROVIDER_VERTEX_AI_UNDERSCORE = "vertex_ai";
  private static final String PROVIDER_MOCK = "mock";

  private final Map<String, LlmConfigValidator> validators;

  private final String allowedProvidersDescription;

  public LlmSectionValidator() {
    this(createDefaultValidators());
  }

  public LlmSectionValidator(final Map<String, LlmConfigValidator> validators) {
    this.validators = Collections.unmodifiableMap(new HashMap<>(validators));
    final List<String> sortedKeys = new ArrayList<>(this.validators.keySet());
    Collections.sort(sortedKeys);
    this.allowedProvidersDescription = String.join(", ", sortedKeys);
  }

  @Override
  public String sectionKey() {
    return LLM;
  }

  @Override
  public boolean isRequired() {
    return true;
  }

  @Override
  public void validateParsedConfig(final Config config, final List<String> errors) {
    if (config.getLlm() == null) {
      errors.add("'llm' section is required");
      return;
    }
    validateLlmConfig(config.getLlm(), errors);
    validateLlmAllowList(config.getLlm(), errors);
    validateAllowedModels(config.getLlm(), errors);
  }

  private static Map<String, LlmConfigValidator> createDefaultValidators() {
    final Map<String, LlmConfigValidator> v = new HashMap<>();
    v.put(PROVIDER_GEMINI, new GeminiValidator());
    v.put(PROVIDER_LOCAL, new LocalLlmValidator());
    v.put(PROVIDER_OLLAMA, new LocalLlmValidator());
    v.put(PROVIDER_OPENAI, new OpenAiValidator());
    v.put(PROVIDER_OPENAI_COMPATIBLE, new OpenAiValidator());
    v.put(PROVIDER_OPENAI_COMPATIBLE_UNDERSCORE, new OpenAiValidator());
    v.put(PROVIDER_AZURE_OPENAI, new AzureOpenAiValidator());
    v.put(PROVIDER_AZURE_OPENAI_UNDERSCORE, new AzureOpenAiValidator());
    v.put(PROVIDER_ANTHROPIC, new AnthropicValidator());
    v.put(PROVIDER_BEDROCK, new BedrockValidator());
    v.put(PROVIDER_VERTEX, new VertexAiValidator());
    v.put(PROVIDER_VERTEX_AI, new VertexAiValidator());
    v.put(PROVIDER_VERTEX_AI_UNDERSCORE, new VertexAiValidator());
    v.put(PROVIDER_MOCK, new MockValidator());
    return v;
  }

  private void validateLlmConfig(final Config.LlmConfig llmConfig, final List<String> errors) {
    if (StringUtils.isBlank(llmConfig.getProvider())) {
      errors.add("'llm.provider' is required (" + allowedProvidersDescription + ")");
      return;
    }
    final String normalizedProvider = normalizeProviderName(llmConfig.getProvider());
    final LlmConfigValidator validator = validators.get(normalizedProvider);
    if (validator == null) {
      errors.add("'llm.provider' must be one of: " + allowedProvidersDescription);
      return;
    }
    validator.validate(llmConfig, errors);
  }

  private void validateLlmAllowList(final Config.LlmConfig llmConfig, final List<String> errors) {
    final List<String> allowedProviders = llmConfig.getAllowedProviders();
    if (allowedProviders == null) {
      return;
    }
    if (allowedProviders.isEmpty()) {
      errors.add("'llm.allowed_providers' must include at least one provider.");
      return;
    }
    final String provider = llmConfig.getProvider();
    if (StringUtils.isBlank(provider)) {
      return;
    }
    final String normalizedProvider = normalizeProviderName(provider);
    final boolean allowed =
        allowedProviders.stream()
            .filter(StringUtils::isNotBlank)
            .map(this::normalizeProviderName)
            .anyMatch(value -> value.equals(normalizedProvider));
    if (!allowed) {
      errors.add(
          "'llm.provider' is not permitted by 'llm.allowed_providers': "
              + String.join(", ", allowedProviders));
    }
  }

  private void validateAllowedModels(final Config.LlmConfig llmConfig, final List<String> errors) {
    final Map<String, List<String>> allowedModels = llmConfig.getAllowedModels();
    if (allowedModels == null) {
      return;
    }
    if (allowedModels.isEmpty()) {
      errors.add("'llm.allowed_models' must include at least one provider.");
      return;
    }
    final Map<String, List<String>> normalized = normalizeAllowedModels(allowedModels, errors);
    if (normalized.isEmpty()) {
      return;
    }
    final String provider = llmConfig.getProvider();
    final String modelName = llmConfig.getModelName();
    if (StringUtils.isBlank(provider) || StringUtils.isBlank(modelName)) {
      return;
    }
    validateAllowedModelSelection(provider, modelName, normalized, errors);
  }

  private String normalizeProviderName(final String value) {
    return value.trim().toLowerCase(java.util.Locale.ROOT).replace("_", "-");
  }

  private Map<String, List<String>> normalizeAllowedModels(
      final Map<String, List<String>> allowedModels, final List<String> errors) {
    final Map<String, List<String>> normalized = new HashMap<>();
    for (final Map.Entry<String, List<String>> entry : allowedModels.entrySet()) {
      final String key = entry.getKey();
      if (StringUtils.isBlank(key)) {
        errors.add("'llm.allowed_models' keys must be non-empty strings.");
        return Collections.emptyMap();
      }
      final List<String> models = entry.getValue();
      if (models == null || models.isEmpty()) {
        errors.add("'llm.allowed_models' values must be non-empty lists of model names.");
        return Collections.emptyMap();
      }
      final List<String> normalizedModels = new ArrayList<>();
      for (final String model : models) {
        if (StringUtils.isBlank(model)) {
          errors.add("'llm.allowed_models' values must be non-empty strings.");
          return Collections.emptyMap();
        }
        normalizedModels.add(model.trim());
      }
      normalized.put(normalizeProviderName(key), normalizedModels);
    }
    return normalized;
  }

  private void validateAllowedModelSelection(
      final String provider,
      final String modelName,
      final Map<String, List<String>> normalized,
      final List<String> errors) {
    final String normalizedProvider = normalizeProviderName(provider);
    final List<String> allowedModels = normalized.get(normalizedProvider);
    if (allowedModels == null) {
      errors.add("'llm.allowed_models' does not include provider '" + provider + "'.");
      return;
    }
    final String trimmedModel = modelName.trim();
    if (allowedModels.stream().noneMatch(trimmedModel::equals)) {
      errors.add(
          "'llm.model_name' is not permitted by 'llm.allowed_models' for provider '"
              + provider
              + "': "
              + String.join(", ", allowedModels));
    }
  }
}
