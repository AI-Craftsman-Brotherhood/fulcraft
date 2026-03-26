package com.craftsmanbro.fulcraft.infrastructure.telemetry.impl;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.telemetry.contract.TelemetryPort;
import com.craftsmanbro.fulcraft.infrastructure.telemetry.model.TelemetrySettings;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Locale;
import java.util.Objects;

/** Manages OpenTelemetry initialization and Tracer retrieval. */
public final class Telemetry implements TelemetryPort {

  private static final String ENV_TELEMETRY_EXPORTER = "FUL_TELEMETRY_EXPORTER";

  private static final String PROP_TELEMETRY_EXPORTER = "ful.telemetry.exporter";

  private static final String EXPORTER_LOGGING = "logging";

  private static final String EXPORTER_CONSOLE = "console";

  private static final String EXPORTER_STDOUT = "stdout";

  private static final String EXPORTER_TRUE = "true";

  private static final Telemetry INSTANCE = new Telemetry();

  private static final String DEFAULT_INSTRUMENTATION_NAME = "utgenerator";

  private static final TelemetryPort INSTANCE_VIEW = new SingletonTelemetryView(INSTANCE);

  private final TelemetrySettings settings;

  private final Tracer tracer;

  private final SdkTracerProvider tracerProvider;

  private OpenTelemetrySdk openTelemetrySdk;

  /** Initializes the OpenTelemetry SDK with an optional console LoggingSpanExporter. */
  Telemetry() {
    this(resolveSettingsFromEnvironment());
  }

  Telemetry(final TelemetrySettings settings) {
    this.settings = Objects.requireNonNullElseGet(settings, TelemetrySettings::defaults);
    this.tracerProvider = buildTracerProvider(this.settings);
    this.tracer = initializeTracer(resolveInstrumentationName(this.settings));
  }

  private static SdkTracerProvider buildTracerProvider(final TelemetrySettings settings) {
    final var tracerProviderBuilder = SdkTracerProvider.builder();
    if (settings.loggingExporterEnabled()) {
      tracerProviderBuilder.addSpanProcessor(Objects.requireNonNull(createLoggingSpanProcessor()));
    }
    return Objects.requireNonNull(tracerProviderBuilder.build());
  }

  private static SpanProcessor createLoggingSpanProcessor() {
    final var spanExporter = Objects.requireNonNull(LoggingSpanExporter.create());
    return Objects.requireNonNull(SimpleSpanProcessor.create(spanExporter));
  }

  private static String resolveInstrumentationName(final TelemetrySettings settings) {
    return Objects.requireNonNullElse(settings.instrumentationName(), DEFAULT_INSTRUMENTATION_NAME);
  }

  private Tracer initializeTracer(final String instrumentationName) {
    try {
      this.openTelemetrySdk =
          OpenTelemetrySdk.builder()
              .setTracerProvider(Objects.requireNonNull(tracerProvider))
              .buildAndRegisterGlobal();
      return this.openTelemetrySdk.getTracer(Objects.requireNonNull(instrumentationName));
    } catch (final IllegalStateException ignored) {
      // Global SDK is already registered; reuse it to avoid test init failures.
      this.openTelemetrySdk = null;
      return GlobalOpenTelemetry.getTracer(Objects.requireNonNull(instrumentationName));
    }
  }

  private static boolean isLoggingExporterEnabled() {
    return isLoggingExporterAlias(resolveTelemetryExporterValue());
  }

  private static String resolveTelemetryExporterValue() {
    final String envValue = System.getenv(ENV_TELEMETRY_EXPORTER);
    if (envValue != null && !envValue.isBlank()) {
      return normalizeExporterValue(envValue);
    }
    final String propertyValue = System.getProperty(PROP_TELEMETRY_EXPORTER, "");
    return normalizeExporterValue(propertyValue);
  }

  private static String normalizeExporterValue(final String exporterValue) {
    return exporterValue.trim().toLowerCase(Locale.ROOT);
  }

  private static boolean isLoggingExporterAlias(final String exporterValue) {
    return EXPORTER_LOGGING.equals(exporterValue)
        || EXPORTER_CONSOLE.equals(exporterValue)
        || EXPORTER_STDOUT.equals(exporterValue)
        || EXPORTER_TRUE.equals(exporterValue);
  }

  private static TelemetrySettings resolveSettingsFromEnvironment() {
    return TelemetrySettings.ofLoggingExporter(isLoggingExporterEnabled());
  }

  public static TelemetryPort getInstance() {
    return INSTANCE_VIEW;
  }

  public TelemetrySettings settings() {
    return settings;
  }

  /** Returns a Tracer for the application. */
  @Override
  public Tracer getTracer() {
    return tracer;
  }

  @Override
  public void shutdown() {
    if (openTelemetrySdk != null) {
      openTelemetrySdk.close();
      return;
    }
    tracerProvider.close();
  }

  @Override
  public void close() {
    shutdown();
  }

  private static final class SingletonTelemetryView implements TelemetryPort {

    private final Telemetry delegate;

    private SingletonTelemetryView(final Telemetry delegate) {
      this.delegate =
          Objects.requireNonNull(
              delegate, MessageSource.getMessage("infra.common.error.argument_null", "delegate"));
    }

    @Override
    public Tracer getTracer() {
      return delegate.getTracer();
    }

    @Override
    public void shutdown() {
      delegate.shutdown();
    }
  }
}
