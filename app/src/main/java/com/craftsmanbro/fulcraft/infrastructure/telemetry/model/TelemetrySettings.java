package com.craftsmanbro.fulcraft.infrastructure.telemetry.model;

/** Immutable telemetry settings resolved from runtime configuration. */
public record TelemetrySettings(String instrumentationName, boolean loggingExporterEnabled) {

  private static final String DEFAULT_INSTRUMENTATION_NAME = "com.craftsmanbro.fulcraft";

  public TelemetrySettings {
    instrumentationName = normalizeInstrumentationName(instrumentationName);
  }

  public static TelemetrySettings defaults() {
    return ofLoggingExporter(false);
  }

  public static TelemetrySettings ofLoggingExporter(final boolean enabled) {
    return new TelemetrySettings(DEFAULT_INSTRUMENTATION_NAME, enabled);
  }

  private static String normalizeInstrumentationName(final String value) {
    if (value == null || value.isBlank()) {
      return DEFAULT_INSTRUMENTATION_NAME;
    }
    return value.trim();
  }
}
