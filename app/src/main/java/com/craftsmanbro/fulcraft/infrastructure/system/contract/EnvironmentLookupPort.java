package com.craftsmanbro.fulcraft.infrastructure.system.contract;

import com.craftsmanbro.fulcraft.infrastructure.system.model.EnvironmentVariable;

/** Contract for resolving environment variables. */
public interface EnvironmentLookupPort {

  String resolve(String name);

  default String resolveOrDefault(final String name, final String defaultValue) {
    return resolveVariable(name).orDefault(defaultValue);
  }

  default EnvironmentVariable resolveVariable(final String name) {
    return new EnvironmentVariable(name, resolve(name));
  }
}
