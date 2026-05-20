package com.craftsmanbro.fulcraft.infrastructure.config.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("v1 schema accepts every top-level key the Config POJO supports")
class SchemaTopLevelKeysTest {

  private static final String MINIMAL_CONFIG =
      """
      {
        "schema_version": 1,
        "project": {"id": "demo"},
        "selection_rules": {
          "class_min_loc": 10,
          "class_min_method_count": 1,
          "method_min_loc": 3,
          "method_max_loc": 2000,
          "max_methods_per_class": 50,
          "exclude_getters_setters": true
        },
        "llm": {"provider": "gemini"}
      }
      """;

  @Test
  @DisplayName("a minimal config passes v1 schema validation")
  void minimalConfig_passes() {
    final JsonSchemaValidator validator = JsonSchemaValidator.loadFromResource(1);
    final JsonNode node = new ObjectMapper().readTree(MINIMAL_CONFIG);

    final List<JsonSchemaValidator.SchemaError> errors = validator.validate(node);

    assertThat(errors).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "brittle_test_rules",
        "mocking",
        "local_fix",
        "log",
        "docs",
        "cache",
        "interceptors",
        "verification",
        "analysis",
        "context_awareness",
        "generation",
        "governance",
        "audit",
        "quota",
        "execution",
        "quality_gate",
        "output",
        "cli",
        "pipeline"
      })
  @DisplayName("every Config POJO top-level key is accepted")
  void topLevelKey_isAccepted(final String key) {
    final JsonSchemaValidator validator = JsonSchemaValidator.loadFromResource(1);
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode node = (ObjectNode) mapper.readTree(MINIMAL_CONFIG);
    node.set(key, mapper.createObjectNode());

    final List<JsonSchemaValidator.SchemaError> errors = validator.validate(node);

    assertThat(errors).as("v1 schema rejected key %s", key).isEmpty();
  }

  @Test
  @DisplayName("unknown top-level keys are still rejected (additionalProperties: false)")
  void unknownKey_isRejected() {
    final JsonSchemaValidator validator = JsonSchemaValidator.loadFromResource(1);
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode node = (ObjectNode) mapper.readTree(MINIMAL_CONFIG);
    node.set("totally_made_up_key", mapper.createObjectNode());

    final List<JsonSchemaValidator.SchemaError> errors = validator.validate(node);

    assertThat(errors).isNotEmpty();
  }

  @Test
  @DisplayName("analysis.language_level is accepted")
  void analysisLanguageLevel_isAccepted() {
    final JsonSchemaValidator validator = JsonSchemaValidator.loadFromResource(1);
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode node = (ObjectNode) mapper.readTree(MINIMAL_CONFIG);
    final ObjectNode analysis = mapper.createObjectNode();
    analysis.put("language_level", "JAVA_17");
    node.set("analysis", analysis);

    final List<JsonSchemaValidator.SchemaError> errors = validator.validate(node);

    assertThat(errors).isEmpty();
  }
}
