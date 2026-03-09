package com.craftsmanbro.fulcraft.infrastructure.system.impl;

import com.craftsmanbro.fulcraft.infrastructure.system.contract.EnvironmentLookupPort;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Provides access to system environment variables with test-time override capability.
 *
 * <p>This class wraps {@link System#getenv(String)} to allow tests to control environment variable
 * values without relying on the actual host environment.
 *
 * <p>Usage in production code:
 *
 * <pre>{@code
 * String apiKey = Env.get("API_KEY");
 * }</pre>
 *
 * <p>Usage in tests:
 *
 * <pre>{@code
 * &#64;BeforeEach
 * void setUp() {
 *   Env.setForTest(name -> switch(name) {
 *     case "API_KEY" -> "test-key";
 *     default -> null;
 *   });
 * }
 *
 * &#64;AfterEach
 * void tearDown() {
 *   Env.reset();
 * }
 * }</pre>
 */
public final class Env implements EnvironmentLookupPort {

  private static final String ARGUMENT_NULL_MESSAGE_KEY = "infra.common.error.argument_null";
  private static final UnaryOperator<String> SYSTEM_ENV_RESOLVER = System::getenv;
  private static final AtomicReference<UnaryOperator<String>> RESOLVER =
      new AtomicReference<>(SYSTEM_ENV_RESOLVER);

  private static final Env INSTANCE = new Env();

  private Env() {}

  /**
   * Gets an environment variable value.
   *
   * @param name the environment variable name
   * @return the value, or null if not set
   */
  public static String get(final String name) {
    return INSTANCE.resolve(name);
  }

  /**
   * Gets an environment variable value with a default.
   *
   * @param name the environment variable name
   * @param defaultValue the default value if not set
   * @return the value, or the default if not set or blank
   */
  public static String getOrDefault(final String name, final String defaultValue) {
    return INSTANCE.resolveOrDefault(name, defaultValue);
  }

  /**
   * Sets a custom resolver for testing purposes.
   *
   * <p><b>Warning:</b> This method is for testing only. Always call {@link #reset()} in
   * {@code @AfterEach} to restore the system resolver.
   *
   * @param testResolver the test resolver function
   */
  public static void setForTest(final UnaryOperator<String> testResolver) {
    setResolver(requireNonNullArgument(testResolver, "Test resolver must not be null"));
  }

  /** Resets to the default system environment resolver. */
  public static void reset() {
    setResolver(SYSTEM_ENV_RESOLVER);
  }

  @Override
  public String resolve(final String name) {
    return RESOLVER.get()
        .apply(requireNonNullArgument(name, "Environment variable name must not be null"));
  }

  @Override
  public String resolveOrDefault(final String name, final String defaultValue) {
    return resolveVariable(name).orDefault(defaultValue);
  }

  public static EnvironmentLookupPort port() {
    return INSTANCE;
  }

  private static void setResolver(final UnaryOperator<String> resolver) {
    RESOLVER.set(resolver);
  }

  private static <T> T requireNonNullArgument(final T value, final String fallbackMessage) {
    return Objects.requireNonNull(
        value,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            ARGUMENT_NULL_MESSAGE_KEY, fallbackMessage));
  }
}
