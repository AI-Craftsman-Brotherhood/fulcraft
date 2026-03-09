package com.craftsmanbro.fulcraft.i18n;

import java.util.Locale;

/**
 * Port for accessing internationalization services.
 *
 * <p>Use this abstraction in components that should not depend on the static {@link MessageSource}
 * API directly.
 */
public interface MessagePort {
  /**
   * Gets a message by key.
   *
   * @param key the message key
   * @return the localized message
   */
  String getMessage(String key);

  /**
   * Gets a message by key with arguments.
   *
   * @param key the message key
   * @param args formatting arguments
   * @return the formatted localized message
   */
  String getMessage(String key, Object... args);

  /**
   * Gets the current locale.
   *
   * @return the current locale
   */
  Locale getLocale();
}
