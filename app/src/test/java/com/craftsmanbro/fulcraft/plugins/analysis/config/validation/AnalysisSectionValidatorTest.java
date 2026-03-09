package com.craftsmanbro.fulcraft.plugins.analysis.config.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnalysisSectionValidatorTest {

  private final AnalysisSectionValidator validator = new AnalysisSectionValidator();

  @Test
  void sectionKey_returnsAnalysis() {
    assertEquals("analysis", validator.sectionKey());
  }

  @Test
  void validateRawSection_rejectsUnknownKeys() {
    Map<String, Object> analysis = new HashMap<>();
    analysis.put("unknown", true);

    List<String> errors = new ArrayList<>();
    validator.validateRawSection(analysis, errors);

    assertEquals(1, errors.size());
    assertTrue(errors.get(0).contains("Unknown keys in 'analysis'"));
  }

  @Test
  void validateRawSection_rejectsBlankSourceRootPaths() {
    Map<String, Object> analysis = new HashMap<>();
    analysis.put("source_root_paths", List.of(" "));

    List<String> errors = new ArrayList<>();
    validator.validateRawSection(analysis, errors);

    assertEquals(1, errors.size());
    assertTrue(errors.get(0).contains("analysis.source_root_paths"));
  }

  @Test
  void validateRawSection_acceptsValidSourceRootPaths() {
    Map<String, Object> analysis = new HashMap<>();
    analysis.put("source_root_paths", List.of("src/main/java"));

    List<String> errors = new ArrayList<>();
    validator.validateRawSection(analysis, errors);

    assertTrue(errors.isEmpty());
  }

  @Test
  void validateRawSection_skipsSchemaCoveredChecks() {
    Map<String, Object> analysis = new HashMap<>();
    analysis.put("source_root_mode", 1);
    analysis.put("interprocedural_callsite_limit", 0);
    analysis.put("classpath", "AUTO");
    analysis.put("spoon", "yes");

    List<String> errors = new ArrayList<>();
    validator.validateRawSection(analysis, errors);

    assertTrue(errors.isEmpty());
  }
}
