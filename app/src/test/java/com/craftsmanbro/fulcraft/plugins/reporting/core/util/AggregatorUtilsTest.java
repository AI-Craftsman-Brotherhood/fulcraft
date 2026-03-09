package com.craftsmanbro.fulcraft.plugins.reporting.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import org.junit.jupiter.api.Test;

class AggregatorUtilsTest {

  @Test
  void parseIntSafe_returnsParsedValue() {
    assertThat(AggregatorUtils.parseIntSafe("42")).isEqualTo(42);
  }

  @Test
  void parseIntSafe_returnsZeroOnNullOrInvalid() {
    assertThat(AggregatorUtils.parseIntSafe(null)).isZero();
    assertThat(AggregatorUtils.parseIntSafe("")).isZero();
    assertThat(AggregatorUtils.parseIntSafe("not-a-number")).isZero();
    assertThat(AggregatorUtils.parseIntSafe("2147483648")).isZero();
  }

  @Test
  void safeClassFqn_throwsWhenTaskNull() {
    assertThatThrownBy(() -> AggregatorUtils.safeClassFqn(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("task must not be null");
  }

  @Test
  void safeClassFqn_usesClassFqnWhenPresent() {
    TaskRecord task = new TaskRecord();
    task.setClassFqn("com.example.Foo");
    task.setFilePath("src/main/java/com/example/Foo.java");

    assertThat(AggregatorUtils.safeClassFqn(task)).isEqualTo("com.example.Foo");
  }

  @Test
  void safeClassFqn_usesFilePathWhenClassFqnBlank() {
    TaskRecord task = new TaskRecord();
    task.setClassFqn(" ");
    task.setFilePath("src/main/java/com/example/Foo.java");

    assertThat(AggregatorUtils.safeClassFqn(task)).isEqualTo("src.main.java.com.example.Foo");
  }

  @Test
  void safeClassFqn_normalizesWindowsFilePath() {
    TaskRecord task = new TaskRecord();
    task.setClassFqn("");
    task.setFilePath("src\\main\\java\\com\\example\\Foo.java");

    assertThat(AggregatorUtils.safeClassFqn(task)).isEqualTo("src.main.java.com.example.Foo");
  }

  @Test
  void safeClassFqn_keepsNonJavaExtension() {
    TaskRecord task = new TaskRecord();
    task.setClassFqn("");
    task.setFilePath("src/main/kotlin/com/example/Foo.kt");

    assertThat(AggregatorUtils.safeClassFqn(task)).isEqualTo("src.main.kotlin.com.example.Foo.kt");
  }

  @Test
  void safeClassFqn_returnsUnknownWhenFilePathBlank() {
    TaskRecord task = new TaskRecord();
    task.setFilePath(" ");

    assertThat(AggregatorUtils.safeClassFqn(task)).isEqualTo(ClassInfo.UNKNOWN_CLASS);
  }

  @Test
  void safeClassFqn_returnsUnknownWhenNormalizedEmpty() {
    TaskRecord task = new TaskRecord();
    task.setFilePath(".java");

    assertThat(AggregatorUtils.safeClassFqn(task)).isEqualTo(ClassInfo.UNKNOWN_CLASS);
  }
}
