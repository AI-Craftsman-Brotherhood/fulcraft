package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynamicFeaturesTest {

  @TempDir Path tempDir;

  @Test
  void detect_delegatesAndReturnsEmptyWhenNoSignals() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.Empty");
    classInfo.setMethods(List.of());

    DynamicFeatures features = new DynamicFeatures();
    features.detect(List.of(classInfo), tempDir);

    assertThat(features.getEvents()).isEmpty();
    assertThat(features.calculateScore()).isEqualTo(0);
    assertThat(features.countByType()).isEmpty();
    assertThat(features.countBySeverity()).isEmpty();
    assertThat(features.getTopFiles(1)).isEmpty();
    assertThat(features.getTopFiles(0)).isEmpty();
    assertThat(features.getTopSubtypes(1)).isEmpty();
    assertThat(features.getAnnotationCounts()).isEmpty();
    assertThat(features.getTopAnnotations(1)).isEmpty();
    assertThat(features.unwrap()).isNotNull();
  }
}
