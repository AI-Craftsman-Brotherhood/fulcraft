package com.craftsmanbro.fulcraft.infrastructure.fs.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.fs.contract.RunIdGeneratorPort;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RunIdGeneratorTest {

  private static final Pattern RUN_ID_PATTERN = Pattern.compile("^\\d{8}_\\d{6}_[0-9A-F]{4}$");

  @Test
  void port_returnsSingleton() {
    RunIdGeneratorPort first = RunIdGenerator.port();
    RunIdGeneratorPort second = RunIdGenerator.port();

    assertSame(first, second);
  }

  @Test
  void newRunIdHasExpectedFormat() {
    String runId = RunIdGenerator.newRunId();

    assertEquals(20, runId.length());
    assertTrue(RUN_ID_PATTERN.matcher(runId).matches());
  }

  @Test
  void newRunIdContainsParseableTimestampAndHexSuffix() {
    String runId = RunIdGenerator.newRunId();

    String timestamp = runId.substring(0, 15);
    String suffix = runId.substring(16);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    assertDoesNotThrow(() -> LocalDateTime.parse(timestamp, formatter));
    int value = assertDoesNotThrow(() -> Integer.parseInt(suffix, 16));
    assertTrue(value >= 0);
    assertTrue(value < 0x10000);
  }

  @Test
  void generateRunId_fromPortHasExpectedFormat() {
    String runId = RunIdGenerator.port().generateRunId();

    assertEquals(20, runId.length());
    assertTrue(RUN_ID_PATTERN.matcher(runId).matches());
  }
}
