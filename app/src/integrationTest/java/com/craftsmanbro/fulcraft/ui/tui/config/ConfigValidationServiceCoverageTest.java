package com.craftsmanbro.fulcraft.ui.tui.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigValidationServiceCoverageTest {

  @TempDir Path tempDir;

  @Test
  void validateWithSchemaRequiresExistingSchemaPath() {
    ConfigValidationService service = new ConfigValidationService();

    List<ConfigValidationService.ValidationIssue> noPath = service.validateWithSchema("{}", null);
    List<ConfigValidationService.ValidationIssue> missingPath =
        service.validateWithSchema("{}", tempDir.resolve("missing-schema.json"));

    assertThat(noPath).hasSize(1);
    assertThat(noPath.get(0).message()).contains("Schema file is required.");
    assertThat(missingPath).hasSize(1);
    assertThat(missingPath.get(0).message()).contains("Schema file not found");
  }

  @Test
  void validateWithSchemaHandlesInvalidInputAndInvalidSchemaFile() throws IOException {
    ConfigValidationService service = new ConfigValidationService();
    Path validSchema =
        writeSchema(
            "simple-schema.json",
            """
            {
              "type": "object"
            }
            """);
    Path invalidSchema = writeSchema("invalid-schema.json", "{");

    List<ConfigValidationService.ValidationIssue> badInput =
        service.validateWithSchema("{\"count\":", validSchema);
    List<ConfigValidationService.ValidationIssue> badSchema =
        service.validateWithSchema("{}", invalidSchema);

    assertThat(badInput).hasSize(1);
    assertThat(badInput.get(0).message()).contains("Failed to parse configuration");
    assertThat(badSchema).hasSize(1);
    assertThat(badSchema.get(0).message()).contains("Failed to read schema file");
  }

  @Test
  void validateWithSchemaReturnsSchemaViolationsAndSuccess() throws IOException {
    ConfigValidationService service = new ConfigValidationService();
    Path schemaPath =
        writeSchema(
            "count-schema.json",
            """
            {
              "type": "object",
              "properties": {
                "count": { "type": "integer", "minimum": 2 }
              },
              "required": ["count"],
              "additionalProperties": false
            }
            """);

    List<ConfigValidationService.ValidationIssue> violations =
        service.validateWithSchema("{\"count\":1,\"extra\":true}", schemaPath);
    List<ConfigValidationService.ValidationIssue> valid =
        service.validateWithSchema("{\"count\":2}", schemaPath);

    assertThat(violations)
        .anyMatch(issue -> issue.path().equals("count") && issue.message().contains(">= 2"));
    assertThat(violations)
        .anyMatch(
            issue -> issue.path().equals("extra") && issue.message().contains("Unknown field"));
    assertThat(valid).isEmpty();
  }

  @Test
  void validateReturnsSchemaVersionAndVariableSubstitutionIssues() {
    ConfigValidationService service = new ConfigValidationService();

    List<ConfigValidationService.ValidationIssue> schemaVersionIssues =
        service.validate(
            """
            schema_version: 0
            project:
              id: "demo"
            llm:
              provider: "mock"
            """);

    String missingVar = "UTG_CONFIG_VAR_DOES_NOT_EXIST";
    List<ConfigValidationService.ValidationIssue> unresolvedVariableIssues =
        service.validate(minimalConfig("${" + missingVar + "}", "3"));

    assertThat(schemaVersionIssues)
        .anyMatch(
            issue ->
                issue.path().equals("schema_version") && issue.message().contains("must be >= 1"));
    assertThat(unresolvedVariableIssues)
        .anyMatch(
            issue ->
                issue.path().equals("selection_rules.method_min_loc")
                    && issue.message().contains("type does not match schema"));
  }

  @Test
  void validateHandlesNullInputAndNonObjectRoot() {
    ConfigValidationService service = new ConfigValidationService();

    List<ConfigValidationService.ValidationIssue> nullInputIssues = service.validate(null);
    List<ConfigValidationService.ValidationIssue> arrayRootIssues = service.validate("[]");

    assertThat(nullInputIssues).hasSize(1);
    assertThat(nullInputIssues.get(0).message())
        .contains("Configuration file is empty or invalid.");
    assertThat(arrayRootIssues).hasSize(1);
    assertThat(arrayRootIssues.get(0).message()).contains("Configuration root must be an object.");
  }

  @Test
  void privateHelpersCoverFallbackIssueParsingBranches() throws Exception {
    ConfigValidationService service = new ConfigValidationService();

    assertThat(invokeParseIssues(service, "single line without bullet")).isEmpty();
    assertThat(invokeExtractPath(service, null)).isEmpty();
    assertThat(invokeExtractPath(service, "Invalid value at 'project.id'")).isEqualTo("project.id");

    List<ConfigValidationService.ValidationIssue> schemaIssues = new ArrayList<>();
    boolean schemaValid = invokeRunSchemaValidation(service, "{invalid", schemaIssues);
    assertThat(schemaValid).isFalse();
    assertThat(schemaIssues).hasSize(1);
    assertThat(schemaIssues.get(0).message()).contains("Failed to parse configuration");

    List<ConfigValidationService.ValidationIssue> parsedIssues = new ArrayList<>();
    invokeRunParsedValidation(service, null, parsedIssues);
    assertThat(parsedIssues).hasSize(1);
    assertThat(parsedIssues.get(0).message()).contains("Configuration file is empty after parsing");
  }

  private Path writeSchema(String fileName, String json) throws IOException {
    Path schemaPath = tempDir.resolve(fileName);
    Files.writeString(schemaPath, json);
    return schemaPath;
  }

  private String minimalConfig(String methodMinLoc, String methodMaxLoc) {
    return ("""
        schema_version: 1
        project:
          id: "proj"
        selection_rules:
          class_min_loc: 1
          class_min_method_count: 1
          method_min_loc: %s
          method_max_loc: %s
        llm:
          provider: "mock"
        """)
        .formatted(methodMinLoc, methodMaxLoc);
  }

  private static boolean invokeRunSchemaValidation(
      ConfigValidationService service,
      String jsonText,
      List<ConfigValidationService.ValidationIssue> issues)
      throws Exception {
    Method method =
        ConfigValidationService.class.getDeclaredMethod(
            "runSchemaValidation", String.class, List.class);
    method.setAccessible(true);
    return (boolean) method.invoke(service, jsonText, issues);
  }

  private static void invokeRunParsedValidation(
      ConfigValidationService service,
      Config config,
      List<ConfigValidationService.ValidationIssue> issues)
      throws Exception {
    Method method =
        ConfigValidationService.class.getDeclaredMethod(
            "runParsedValidation", Config.class, List.class);
    method.setAccessible(true);
    method.invoke(service, config, issues);
  }

  private static List<ConfigValidationService.ValidationIssue> invokeParseIssues(
      ConfigValidationService service, String message) throws Exception {
    Method method = ConfigValidationService.class.getDeclaredMethod("parseIssues", String.class);
    method.setAccessible(true);
    return (List<ConfigValidationService.ValidationIssue>) method.invoke(service, message);
  }

  private static String invokeExtractPath(ConfigValidationService service, String error)
      throws Exception {
    Method method = ConfigValidationService.class.getDeclaredMethod("extractPath", String.class);
    method.setAccessible(true);
    return (String) method.invoke(service, error);
  }
}
