package com.craftsmanbro.fulcraft.kernel.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import org.junit.jupiter.api.Test;

class ServiceRegistryTest {

  @Test
  void registerAndLookup_returnTypedService() {
    ServiceRegistry registry = new ServiceRegistry();
    Runnable service = () -> {};

    registry.register(Runnable.class, service);

    assertThat(registry.lookup(Runnable.class)).containsSame(service);
    assertThat(registry.require(Runnable.class)).isSameAs(service);
  }

  @Test
  void require_throwsWhenServiceIsMissing() {
    ServiceRegistry registry = new ServiceRegistry();

    assertThatThrownBy(() -> registry.require(Runnable.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            MessageSource.getMessage(
                "kernel.plugin.service_registry.error.required_service_not_found",
                Runnable.class.getName()));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Test
  void register_rejectsIncompatibleInstanceWhenRawTypesBypassGenerics() {
    ServiceRegistry registry = new ServiceRegistry();

    assertThatThrownBy(() -> registry.register((Class) Runnable.class, "not-a-runnable"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            MessageSource.getMessage(
                "kernel.plugin.service_registry.error.service_type_mismatch",
                Runnable.class.getName(),
                String.class.getName()));
  }

  @Test
  void nullArgumentsAreRejected() {
    ServiceRegistry registry = new ServiceRegistry();
    Runnable service = () -> {};

    assertThatThrownBy(() -> registry.register(null, service))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(MessageSource.getMessage("kernel.common.error.argument_null", "type"));
    assertThatThrownBy(() -> registry.register(Runnable.class, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(MessageSource.getMessage("kernel.common.error.argument_null", "instance"));
    assertThatThrownBy(() -> registry.lookup(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(MessageSource.getMessage("kernel.common.error.argument_null", "type"));
    assertThatThrownBy(() -> registry.require(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(MessageSource.getMessage("kernel.common.error.argument_null", "type"));
  }
}
