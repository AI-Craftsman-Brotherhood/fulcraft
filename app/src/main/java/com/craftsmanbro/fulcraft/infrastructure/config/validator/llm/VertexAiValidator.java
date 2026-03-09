package com.craftsmanbro.fulcraft.infrastructure.config.validator.llm;

import com.craftsmanbro.fulcraft.config.Config;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/** Validator for Vertex AI LLM configurations. */
public class VertexAiValidator extends AbstractLlmValidator {

  private static final String ENV_VERTEX_AI_ACCESS_TOKEN = "VERTEX_AI_ACCESS_TOKEN";

  @Override
  public void validate(final Config.LlmConfig config, final List<String> errors) {
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "config"));
    Objects.requireNonNull(
        errors,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "errors"));
    if (StringUtils.isBlank(config.getVertexProject())) {
      errors.add("'llm.vertex_project' is required for vertex provider");
    }
    if (StringUtils.isBlank(config.getVertexLocation())) {
      errors.add("'llm.vertex_location' is required for vertex provider");
    }
    if (StringUtils.isBlank(config.getVertexModel())) {
      errors.add("'llm.vertex_model' is required for vertex provider");
    }
    validateApiKey(config, ENV_VERTEX_AI_ACCESS_TOKEN, "vertex", errors);
  }
}
