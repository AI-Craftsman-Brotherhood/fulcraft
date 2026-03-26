package com.craftsmanbro.fulcraft.infrastructure.coverage.model;

import java.nio.file.Path;
import java.util.Objects;

/** Resolved coverage report path plus whether it came from explicit user config. */
public record CoverageReportReference(Path path, boolean explicitlyConfigured) {

  public CoverageReportReference {
    Objects.requireNonNull(path, pathArgumentNullMessage());
  }

  private static String pathArgumentNullMessage() {
    return com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
        "infra.common.error.argument_null", "path");
  }
}
