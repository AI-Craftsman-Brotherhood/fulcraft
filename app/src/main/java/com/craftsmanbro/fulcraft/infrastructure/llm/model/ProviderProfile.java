package com.craftsmanbro.fulcraft.infrastructure.llm.model;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ProviderProfile(
    String providerName, Set<Capability> capabilities, Optional<String> notes) {

  private static final String ARGUMENT_NULL_MESSAGE_KEY = "infra.common.error.argument_null";

  public ProviderProfile(
      final String providerName, final Set<Capability> capabilities, final Optional<String> notes) {
    this.providerName = requireNonNullArgument(providerName, "providerName must not be null");
    this.capabilities =
        Set.copyOf(requireNonNullArgument(capabilities, "capabilities must not be null"));
    this.notes =
        requireNonNullArgument(notes, "notes must not be null (use Optional.empty() instead)");
  }

  private static <T> T requireNonNullArgument(final T value, final String argumentDescription) {
    return Objects.requireNonNull(value, argumentNullMessage(argumentDescription));
  }

  private static String argumentNullMessage(final String argumentDescription) {
    return MessageSource.getMessage(ARGUMENT_NULL_MESSAGE_KEY, argumentDescription);
  }

  public boolean supports(final Capability capability) {
    return capabilities.contains(capability);
  }
}
