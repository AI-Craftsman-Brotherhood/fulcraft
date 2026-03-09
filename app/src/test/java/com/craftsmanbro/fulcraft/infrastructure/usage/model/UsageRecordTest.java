package com.craftsmanbro.fulcraft.infrastructure.usage.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UsageRecordTest {

  @Test
  void defaultConstructorInitializesCountsToZero() {
    UsageRecord record = new UsageRecord();

    assertEquals(0, record.getRequestCount());
    assertEquals(0, record.getTokenCount());
  }

  @Test
  void settersUpdateFields() {
    UsageRecord record = new UsageRecord();

    record.setRequestCount(7);
    record.setTokenCount(33);

    assertEquals(7, record.getRequestCount());
    assertEquals(33, record.getTokenCount());
  }

  @Test
  void addAccumulatesOnlyNonNegativeDeltas() {
    UsageRecord record = new UsageRecord(1, 2);

    record.add(3, 5);
    assertEquals(4, record.getRequestCount());
    assertEquals(7, record.getTokenCount());

    record.add(0, 0);
    assertEquals(4, record.getRequestCount());
    assertEquals(7, record.getTokenCount());
  }

  @Test
  void addIgnoresNegativeDeltas() {
    UsageRecord record = new UsageRecord(1, 2);

    record.add(-5, 10);
    assertEquals(1, record.getRequestCount());
    assertEquals(12, record.getTokenCount());

    record.add(3, -7);
    assertEquals(4, record.getRequestCount());
    assertEquals(12, record.getTokenCount());
  }
}
