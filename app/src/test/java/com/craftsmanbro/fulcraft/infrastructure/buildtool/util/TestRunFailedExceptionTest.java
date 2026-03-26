package com.craftsmanbro.fulcraft.infrastructure.buildtool.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.buildtool.impl.util.TestRunFailedException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TestRunFailedException}.
 *
 * <p>Verifies exception field storage, message propagation, and null validation.
 */
class TestRunFailedExceptionTest {

  @Test
  void constructor_storesAllFieldsCorrectly() {
    Path logsDir = Path.of("/logs");
    Path reportDir = Path.of("/logs/reports");

    TestRunFailedException ex =
        new TestRunFailedException("run-123", logsDir, reportDir, "Test execution failed");

    assertEquals("run-123", ex.getRunId());
    assertEquals(logsDir, ex.getLogsDir());
    assertEquals(reportDir, ex.getReportDir());
    assertEquals("Test execution failed", ex.getMessage());
  }

  @Test
  void constructor_withNullRunId_throwsNullPointerException() {
    Path logsDir = Path.of("/logs");
    Path reportDir = Path.of("/logs/reports");

    NullPointerException ex =
        assertThrows(
            NullPointerException.class,
            () -> {
              throw new TestRunFailedException(null, logsDir, reportDir, "message");
            });
    assertTrue(ex.getMessage().contains("runId"));
  }

  @Test
  void constructor_withNullLogsDir_throwsNullPointerException() {
    Path reportDir = Path.of("/logs/reports");

    NullPointerException ex =
        assertThrows(
            NullPointerException.class,
            () -> {
              throw new TestRunFailedException("run-123", null, reportDir, "message");
            });
    assertTrue(ex.getMessage().contains("logsDir"));
  }

  @Test
  void constructor_withNullReportDir_throwsNullPointerException() {
    Path logsDir = Path.of("/logs");

    NullPointerException ex =
        assertThrows(
            NullPointerException.class,
            () -> {
              throw new TestRunFailedException("run-123", logsDir, null, "message");
            });
    assertTrue(ex.getMessage().contains("reportDir"));
  }

  @Test
  void constructor_withNullMessage_throwsNullPointerException() {
    Path logsDir = Path.of("/logs");
    Path reportDir = Path.of("/logs/reports");

    NullPointerException ex =
        assertThrows(
            NullPointerException.class,
            () -> {
              throw new TestRunFailedException("run-123", logsDir, reportDir, null);
            });
    assertTrue(ex.getMessage().contains("message"));
  }

  @Test
  void exception_isInstanceOfIOException() {
    Path logsDir = Path.of("/logs");
    Path reportDir = Path.of("/logs/reports");

    TestRunFailedException ex =
        new TestRunFailedException("run-123", logsDir, reportDir, "Test failed");

    assertEquals(java.io.IOException.class, ex.getClass().getSuperclass());
  }

  @Test
  void getRunId_returnsStoredValue() {
    TestRunFailedException ex =
        new TestRunFailedException("unique-run-id", Path.of("/logs"), Path.of("/reports"), "error");

    assertEquals("unique-run-id", ex.getRunId());
  }

  @Test
  void getLogsDir_returnsStoredPath() {
    Path logsDir = Path.of("/custom/logs/path");
    TestRunFailedException ex =
        new TestRunFailedException("run", logsDir, Path.of("/reports"), "error");

    assertEquals(logsDir, ex.getLogsDir());
  }

  @Test
  void getReportDir_returnsStoredPath() {
    Path reportDir = Path.of("/custom/reports/path");
    TestRunFailedException ex =
        new TestRunFailedException("run", Path.of("/logs"), reportDir, "error");

    assertEquals(reportDir, ex.getReportDir());
  }

  @Test
  void getMessage_returnsProvidedMessage() {
    String expectedMessage = "Detailed error description with context";
    TestRunFailedException ex =
        new TestRunFailedException("run", Path.of("/logs"), Path.of("/reports"), expectedMessage);

    assertEquals(expectedMessage, ex.getMessage());
  }

  @Test
  void serialization_keepsRunIdAndMessage_butClearsTransientPaths() throws Exception {
    TestRunFailedException original =
        new TestRunFailedException(
            "run-serialized", Path.of("/logs"), Path.of("/reports"), "serialized message");

    TestRunFailedException deserialized = serializeAndDeserialize(original);

    assertEquals("run-serialized", deserialized.getRunId());
    assertEquals("serialized message", deserialized.getMessage());
    assertNull(deserialized.getLogsDir());
    assertNull(deserialized.getReportDir());
  }

  private TestRunFailedException serializeAndDeserialize(TestRunFailedException exception)
      throws Exception {
    byte[] data;
    try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(buffer)) {
      output.writeObject(exception);
      data = buffer.toByteArray();
    }

    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(data))) {
      return (TestRunFailedException) input.readObject();
    }
  }
}
