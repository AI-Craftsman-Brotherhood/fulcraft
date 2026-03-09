package com.craftsmanbro.fulcraft.plugins.analysis.reporting.core.service.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QualityScoreTest {

  @Test
  void build_defaultsToUnknownStatusAndEmptyPenalties() {
    QualityScore score = QualityScore.builder().build();

    assertEquals(0, score.score());
    assertEquals(0.0, score.typeResolutionRate());
    assertEquals(0, score.dynamicFeatureScore());
    assertEquals("UNKNOWN", score.classpath().javaparser());
    assertEquals("UNKNOWN", score.classpath().spoon());
    assertEquals(0, score.classpath().entries());
    assertEquals("OFF", score.preprocess().mode());
    assertNull(score.preprocess().toolUsed());
    assertEquals("SKIPPED", score.preprocess().status());
    assertTrue(score.penalties().isEmpty());
  }

  @Test
  void build_calculatesScoreWithRoundingAndPenaltyOrder() {
    QualityScore score =
        QualityScore.builder()
            .typeResolutionRate(0.805)
            .dynamicFeatureScore(10)
            .classpath("OK", "OK", 12)
            .preprocess("ON", "prep-tool", "DONE")
            .preprocessPenalty(3)
            .classpathPenalty(4)
            .build();

    assertEquals(64, score.score());
    assertEquals(0.805, score.typeResolutionRate());
    assertEquals(10, score.dynamicFeatureScore());
    assertEquals("OK", score.classpath().javaparser());
    assertEquals("OK", score.classpath().spoon());
    assertEquals(12, score.classpath().entries());
    assertEquals("ON", score.preprocess().mode());
    assertEquals("prep-tool", score.preprocess().toolUsed());
    assertEquals("DONE", score.preprocess().status());

    List<String> keys = new ArrayList<>(score.penalties().keySet());
    assertEquals(List.of("dynamic_features", "preprocess", "classpath"), keys);
    assertEquals(10, score.penalties().get("dynamic_features"));
    assertEquals(3, score.penalties().get("preprocess"));
    assertEquals(4, score.penalties().get("classpath"));
  }

  @Test
  void build_capsDynamicPenaltyAndNeverBelowZero() {
    QualityScore score =
        QualityScore.builder()
            .typeResolutionRate(0.1)
            .dynamicFeatureScore(45)
            .preprocessPenalty(50)
            .build();

    assertEquals(0, score.score());
    assertEquals(45, score.dynamicFeatureScore());
    assertEquals(30, score.penalties().get("dynamic_features"));
    assertEquals(50, score.penalties().get("preprocess"));

    List<String> keys = new ArrayList<>(score.penalties().keySet());
    assertEquals(List.of("dynamic_features", "preprocess"), keys);
  }

  @Test
  void build_recordsOnlyPositivePenalties() {
    QualityScore score =
        QualityScore.builder()
            .typeResolutionRate(0.72)
            .dynamicFeatureScore(0)
            .preprocessPenalty(0)
            .classpathPenalty(2)
            .build();

    assertEquals(70, score.score());
    assertEquals(List.of("classpath"), new ArrayList<>(score.penalties().keySet()));
    assertEquals(2, score.penalties().get("classpath"));
  }

  @Test
  void build_usesDefaultPreprocessWhenOnlyClasspathIsProvided() {
    QualityScore score = QualityScore.builder().classpath("OK", "WARN", 3).build();

    assertEquals("OK", score.classpath().javaparser());
    assertEquals("WARN", score.classpath().spoon());
    assertEquals(3, score.classpath().entries());
    assertEquals("OFF", score.preprocess().mode());
    assertNull(score.preprocess().toolUsed());
    assertEquals("SKIPPED", score.preprocess().status());
  }

  @Test
  void build_usesDefaultClasspathWhenOnlyPreprocessIsProvided() {
    QualityScore score = QualityScore.builder().preprocess("ON", "delombok", "DONE").build();

    assertEquals("UNKNOWN", score.classpath().javaparser());
    assertEquals("UNKNOWN", score.classpath().spoon());
    assertEquals(0, score.classpath().entries());
    assertEquals("ON", score.preprocess().mode());
    assertEquals("delombok", score.preprocess().toolUsed());
    assertEquals("DONE", score.preprocess().status());
  }

  @Test
  void build_serializesWithConfiguredJsonPropertyNames() throws Exception {
    QualityScore score =
        QualityScore.builder()
            .typeResolutionRate(0.82)
            .dynamicFeatureScore(8)
            .classpath("OK", "WARN", 20)
            .preprocess("ON", "delombok", "DONE")
            .build();

    ObjectMapper mapper = new ObjectMapper();
    Map<?, ?> root = mapper.readValue(mapper.writeValueAsString(score), Map.class);
    Map<?, ?> classpath = (Map<?, ?>) root.get("classpath");
    Map<?, ?> preprocess = (Map<?, ?>) root.get("preprocess");

    assertEquals(74, root.get("score"));
    assertEquals(0.82, root.get("type_resolution_rate"));
    assertEquals(8, root.get("dynamic_feature_score"));
    assertEquals("OK", classpath.get("javaparser"));
    assertEquals("WARN", classpath.get("spoon"));
    assertEquals(20, classpath.get("entries"));
    assertEquals("ON", preprocess.get("mode"));
    assertEquals("delombok", preprocess.get("tool_used"));
    assertEquals("DONE", preprocess.get("status"));
    assertTrue(root.containsKey("penalties"));
  }
}
