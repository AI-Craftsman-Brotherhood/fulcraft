package com.craftsmanbro.fulcraft.i18n;

import java.util.Locale;

/**
 * Thin adapter that bridges {@link MessagePort} to the shared {@link MessageSource}.
 *
 * <p>This keeps callers on the port abstraction while reusing the application-wide message source.
 */
public final class MessagePortAdapter implements MessagePort {

  @Override
  public String getMessage(final String key) {
    return MessageSource.getMessage(key);
  }

  @Override
  public String getMessage(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }

  @Override
  public Locale getLocale() {
    return MessageSource.getLocale();
  }
}
