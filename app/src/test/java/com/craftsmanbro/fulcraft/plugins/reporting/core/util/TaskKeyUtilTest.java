package com.craftsmanbro.fulcraft.plugins.reporting.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.model.TaskRecord;
import org.junit.jupiter.api.Test;

class TaskKeyUtilTest {

  @Test
  void buildKey_returnsUnknownWhenTaskNull() {
    assertThat(TaskKeyUtil.buildKey((TaskRecord) null)).isEqualTo("unknown");
  }

  @Test
  void buildKey_usesTaskIdWhenPresent() {
    TaskRecord task = new TaskRecord();
    task.setTaskId("Task-123");
    task.setClassFqn("com.example.Foo");
    task.setMethodName("bar");

    assertThat(TaskKeyUtil.buildKey(task)).isEqualTo("Task-123");
  }

  @Test
  void buildKey_fallsBackToClassAndMethodLowercased() {
    TaskRecord task = new TaskRecord();
    task.setTaskId(" ");
    task.setClassFqn("Com.Example.Foo");
    task.setMethodName("Bar");

    assertThat(TaskKeyUtil.buildKey(task)).isEqualTo("com.example.foo#bar");
  }

  @Test
  void buildKey_usesUnknownPartsWhenMissing() {
    TaskRecord task = new TaskRecord();
    task.setTaskId("");

    assertThat(TaskKeyUtil.buildKey(task)).isEqualTo("unknown#unknown");
  }

  @Test
  void buildKey_usesUnknownForMissingClassOnly() {
    TaskRecord task = new TaskRecord();
    task.setTaskId("");
    task.setMethodName("DoWork");

    assertThat(TaskKeyUtil.buildKey(task)).isEqualTo("unknown#dowork");
  }

  @Test
  void buildKey_returnsUnknownWhenResultNull() {
    assertThat(TaskKeyUtil.buildKey((GenerationTaskResult) null)).isEqualTo("unknown");
  }

  @Test
  void buildKey_usesResultTaskIdWhenPresent() {
    GenerationTaskResult result = new GenerationTaskResult();
    result.setTaskId("result-456");
    result.setClassFqn("com.example.Foo");
    result.setMethodName("bar");

    assertThat(TaskKeyUtil.buildKey(result)).isEqualTo("result-456");
  }

  @Test
  void buildKey_fallsBackToResultClassAndMethodLowercased() {
    GenerationTaskResult result = new GenerationTaskResult();
    result.setTaskId(" ");
    result.setClassFqn("Com.Example.Foo");
    result.setMethodName("Bar");

    assertThat(TaskKeyUtil.buildKey(result)).isEqualTo("com.example.foo#bar");
  }

  @Test
  void buildKey_usesUnknownPartsWhenResultMissingDetails() {
    GenerationTaskResult result = new GenerationTaskResult();
    result.setTaskId("");

    assertThat(TaskKeyUtil.buildKey(result)).isEqualTo("unknown#unknown");
  }

  @Test
  void buildKey_usesUnknownForMissingResultMethodOnly() {
    GenerationTaskResult result = new GenerationTaskResult();
    result.setTaskId("");
    result.setClassFqn("com.example.Worker");

    assertThat(TaskKeyUtil.buildKey(result)).isEqualTo("com.example.worker#unknown");
  }
}
