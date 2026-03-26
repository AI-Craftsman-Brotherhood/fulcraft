package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.plugins.reporting.model.FixErrorHistory.FixAttempt;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixErrorHistoryTest {

  @Test
  void constructorRejectsNullTaskId() {
    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> new FixErrorHistory(null));

    assertTrue(exception.getMessage().endsWith("taskId"), exception.getMessage());
  }

  @Test
  void addAttemptAssignsSequentialNumberAndTimestamp() {
    FixErrorHistory history = new FixErrorHistory("task-1");
    long start = System.currentTimeMillis();

    history.addAttempt("SYNTAX_ERROR", "missing ;");
    history.addAttempt("TYPE_ERROR", "type mismatch");

    long end = System.currentTimeMillis();

    assertEquals(2, history.getAttempts().size());

    FixAttempt first = history.getAttempts().get(0);
    assertEquals(1, first.getAttemptNumber());
    assertEquals("SYNTAX_ERROR", first.getErrorCategory());
    assertEquals("missing ;", first.getErrorMessage());
    assertTrue(first.getTimestampMs() >= start);
    assertTrue(first.getTimestampMs() <= end);

    FixAttempt second = history.getAttempts().get(1);
    assertEquals(2, second.getAttemptNumber());
    assertEquals("TYPE_ERROR", second.getErrorCategory());
    assertEquals("type mismatch", second.getErrorMessage());

    assertFalse(history.isConverged());
    history.markConverged();
    assertTrue(history.isConverged());
  }

  @Test
  void attemptsAreDefensivelyCopiedAndReadOnly() {
    FixErrorHistory history = new FixErrorHistory("task-2");
    List<FixAttempt> source = new ArrayList<>();
    source.add(new FixAttempt(1, "ERROR", "boom"));

    history.setAttempts(source);
    source.add(new FixAttempt(2, "ERROR2", "boom2"));

    assertEquals(1, history.getAttempts().size());
    assertThrows(
        UnsupportedOperationException.class, () -> history.getAttempts().add(new FixAttempt()));
  }

  @Test
  void setAttemptsRejectsNull() {
    FixErrorHistory history = new FixErrorHistory("task-3");

    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> history.setAttempts(null));

    assertTrue(exception.getMessage().endsWith("attempts"), exception.getMessage());
  }
}
