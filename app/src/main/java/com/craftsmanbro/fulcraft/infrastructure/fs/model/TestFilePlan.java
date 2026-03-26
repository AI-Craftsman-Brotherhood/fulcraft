package com.craftsmanbro.fulcraft.infrastructure.fs.model;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable plan describing where and under what name a test class should be generated. */
public record TestFilePlan(Path testFile, String testClassName) {

  private static final String ARGUMENT_NULL_MESSAGE_KEY = "infra.common.error.argument_null";

  public TestFilePlan {
    Objects.requireNonNull(testFile, argumentNullMessage("testFile must not be null"));
    Objects.requireNonNull(testClassName, argumentNullMessage("testClassName must not be null"));
  }

  private static String argumentNullMessage(final String argumentDescription) {
    return com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
        ARGUMENT_NULL_MESSAGE_KEY, argumentDescription);
  }
}
