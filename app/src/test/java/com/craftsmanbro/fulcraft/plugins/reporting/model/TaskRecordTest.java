package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskRecordTest {

  @Test
  void fieldsRoundTrip() {
    TaskRecord task = new TaskRecord();

    task.setTaskId("task-1");
    task.setProjectId("project-a");
    task.setClassFqn("com.example.Foo");
    task.setFilePath("src/main/java/com/example/Foo.java");
    task.setMethodName("run");
    task.setMethodSignature("run(String)");
    task.setTestClassName("FooTest");
    task.setSelected(Boolean.TRUE);
    task.setExclusionReason("none");
    task.setComplexityStrategy("strict");
    task.setHighComplexity(Boolean.FALSE);
    task.setFeasibilityScore(0.75);

    assertEquals("task-1", task.getTaskId());
    assertEquals("project-a", task.getProjectId());
    assertEquals("com.example.Foo", task.getClassFqn());
    assertEquals("src/main/java/com/example/Foo.java", task.getFilePath());
    assertEquals("run", task.getMethodName());
    assertEquals("run(String)", task.getMethodSignature());
    assertEquals("FooTest", task.getTestClassName());
    assertEquals(Boolean.TRUE, task.getSelected());
    assertEquals("none", task.getExclusionReason());
    assertEquals("strict", task.getComplexityStrategy());
    assertEquals(Boolean.FALSE, task.getHighComplexity());
    assertEquals(0.75, task.getFeasibilityScore(), 0.0001);
  }

  @Test
  void feasibilityBreakdownIsSortedAndDefensivelyCopied() {
    TaskRecord task = new TaskRecord();
    Map<String, Object> source = new HashMap<>();
    source.put("b", 2);
    source.put("a", 1);

    task.setFeasibilityBreakdown(source);
    source.put("c", 3);

    Map<String, Object> snapshot = task.getFeasibilityBreakdown();

    assertEquals(List.of("a", "b"), new ArrayList<>(snapshot.keySet()));
    assertFalse(snapshot.containsKey("c"));

    snapshot.put("x", 9);
    assertFalse(task.getFeasibilityBreakdown().containsKey("x"));
  }

  @Test
  void feasibilityBreakdownNullInputReturnsEmptyMap() {
    TaskRecord task = new TaskRecord();
    task.setFeasibilityBreakdown(null);

    assertTrue(task.getFeasibilityBreakdown().isEmpty());
  }
}
