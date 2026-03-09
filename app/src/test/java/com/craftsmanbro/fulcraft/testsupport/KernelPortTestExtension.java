package com.craftsmanbro.fulcraft.testsupport;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.KernelLoggerFactoryAdapter;
import com.craftsmanbro.fulcraft.logging.LoggerPortProvider;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Initializes shared providers for tests. */
public final class KernelPortTestExtension implements BeforeAllCallback {

  @Override
  public void beforeAll(ExtensionContext context) {
    LoggerPortProvider.setFactory(new KernelLoggerFactoryAdapter());
  }
}
