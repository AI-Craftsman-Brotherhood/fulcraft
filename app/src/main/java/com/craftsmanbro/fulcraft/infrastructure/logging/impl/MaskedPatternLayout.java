package com.craftsmanbro.fulcraft.infrastructure.logging.impl;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.craftsmanbro.fulcraft.infrastructure.security.impl.SecretMasker;

/** Logback pattern layout that masks secrets after formatting. */
public class MaskedPatternLayout extends PatternLayout {

  @Override
  public String doLayout(final ILoggingEvent event) {
    final String formatted = super.doLayout(event);
    return SecretMasker.mask(formatted);
  }
}
