package com.craftsmanbro.fulcraft.infrastructure.telemetry.contract;

import io.opentelemetry.api.trace.Tracer;

/** Contract for application telemetry and tracer access. */
public interface TelemetryPort extends AutoCloseable {

  /** Returns tracer used for application spans. */
  Tracer getTracer();

  /** Flushes and stops telemetry resources. */
  void shutdown();

  /** Keeps close semantics delegated to {@link #shutdown()} for one lifecycle path. */
  @Override
  default void close() {
    shutdown();
  }
}
