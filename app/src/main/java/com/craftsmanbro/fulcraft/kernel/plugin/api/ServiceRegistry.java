package com.craftsmanbro.fulcraft.kernel.plugin.api;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Type-safe service registry for plugin dependency injection. */
public final class ServiceRegistry {

  private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

  public <T> void register(final Class<T> type, final T instance) {
    final Class<T> requiredType = requireType(type);
    final Object requiredInstance =
        Objects.requireNonNull(
            instance, MessageSource.getMessage("kernel.common.error.argument_null", "instance"));
    services.put(requiredType, castForRegistration(requiredType, requiredInstance));
  }

  public <T> Optional<T> lookup(final Class<T> type) {
    final Class<T> requiredType = requireType(type);
    final Object service = services.get(requiredType);
    if (service == null) {
      return Optional.empty();
    }
    return Optional.of(castStoredService(requiredType, service));
  }

  public <T> T require(final Class<T> type) {
    final Class<T> requiredType = requireType(type);
    final Object service = services.get(requiredType);
    if (service == null) {
      throw new IllegalStateException(
          MessageSource.getMessage(
              "kernel.plugin.service_registry.error.required_service_not_found",
              requiredType.getName()));
    }
    return castStoredService(requiredType, service);
  }

  private static <T> Class<T> requireType(final Class<T> type) {
    return Objects.requireNonNull(
        type, MessageSource.getMessage("kernel.common.error.argument_null", "type"));
  }

  private static <T> T castForRegistration(final Class<T> type, final Object instance) {
    try {
      return type.cast(instance);
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(typeMismatchMessage(type, instance), e);
    }
  }

  private static <T> T castStoredService(final Class<T> type, final Object service) {
    try {
      return type.cast(service);
    } catch (ClassCastException e) {
      throw new IllegalStateException(typeMismatchMessage(type, service), e);
    }
  }

  private static String typeMismatchMessage(final Class<?> expectedType, final Object actualValue) {
    return MessageSource.getMessage(
        "kernel.plugin.service_registry.error.service_type_mismatch",
        expectedType.getName(),
        actualValue.getClass().getName());
  }
}
