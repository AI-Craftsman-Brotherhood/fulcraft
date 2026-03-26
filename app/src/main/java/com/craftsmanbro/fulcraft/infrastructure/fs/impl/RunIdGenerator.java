package com.craftsmanbro.fulcraft.infrastructure.fs.impl;

import com.craftsmanbro.fulcraft.infrastructure.fs.contract.RunIdGeneratorPort;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Generates stable, sortable run identifiers. */
public final class RunIdGenerator implements RunIdGeneratorPort {

  private static final DateTimeFormatter RUN_ID_TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
  private static final int RANDOM_SUFFIX_BOUND = 0x10000;
  private static final RunIdGenerator INSTANCE = new RunIdGenerator();

  private RunIdGenerator() {}

  public static RunIdGeneratorPort port() {
    return INSTANCE;
  }

  public static String newRunId() {
    return INSTANCE.generateRunId();
  }

  @Override
  public String generateRunId() {
    final String runTimestamp = LocalDateTime.now().format(RUN_ID_TIMESTAMP_FORMATTER);
    final int randomSuffix = ThreadLocalRandom.current().nextInt(RANDOM_SUFFIX_BOUND);
    return runTimestamp + "_" + String.format(Locale.ROOT, "%04X", randomSuffix);
  }
}
