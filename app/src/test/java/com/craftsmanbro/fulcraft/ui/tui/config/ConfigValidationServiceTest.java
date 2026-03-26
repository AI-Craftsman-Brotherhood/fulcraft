package com.craftsmanbro.fulcraft.ui.tui.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigValidationServiceTest {

  @Test
  void validateReturnsSchemaIssuesForInvalidBounds() {
    ConfigValidationService service = new ConfigValidationService();
    String yaml = minimalConfig("1", "0");

    List<ConfigValidationService.ValidationIssue> issues = service.validate(yaml);

    assertThat(issues).isNotEmpty();
    assertThat(issues)
        .anyMatch(
            issue ->
                issue.path().equals("selection_rules.method_max_loc")
                    && issue.message().contains(">="));
  }

  @Test
  void validateReturnsRawIssuesForCrossFieldErrors() {
    ConfigValidationService service = new ConfigValidationService();
    String yaml = minimalConfig("5", "3");

    List<ConfigValidationService.ValidationIssue> issues = service.validate(yaml);

    assertThat(issues)
        .anyMatch(
            issue ->
                issue.path().equals("selection_rules.method_min_loc")
                    && issue.message().contains("cannot be greater"));
  }

  @Test
  void validateSubstitutesSystemProperties() {
    ConfigValidationService service = new ConfigValidationService();
    String property = "UTGEN_TUI_TEST_MIN_LOC";
    System.setProperty(property, "2");
    try {
      String yaml = minimalConfig("${" + property + "}", "3");

      List<ConfigValidationService.ValidationIssue> issues = service.validate(yaml);

      assertThat(issues).isEmpty();
    } finally {
      System.clearProperty(property);
    }
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
}
