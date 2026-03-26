package com.craftsmanbro.fulcraft.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Provides internationalized messages for the application.
 *
 * <p>This class is a process-wide static message source. Changing the locale updates the shared
 * bundle used by all callers in the current JVM.
 *
 * <p>Supports English and Japanese locales. The locale can be configured via:
 *
 * <ol>
 *   <li>Environment variable: FUL_LANG (e.g., "ja", "en")
 *   <li>System property: ful.lang
 *   <li>Default system locale
 * </ol>
 */
public final class MessageSource {

  private static final String BUNDLE_NAME = "messages";
  private static final Locale DEFAULT_LOCALE = Locale.JAPANESE;

  private static ResourceBundle bundle;

  private static Locale currentLocale;

  private MessageSource() {}

  static {
    initialize();
  }

  /** Initializes or re-initializes the message source with the configured locale. */
  public static synchronized void initialize() {
    loadBundle(detectLocale());
  }

  /**
   * Sets the locale explicitly and reloads the resource bundle.
   *
   * @param locale the locale to use
   */
  public static synchronized void setLocale(final Locale locale) {
    loadBundle(resolveLocale(locale));
  }

  /**
   * Gets the current locale.
   *
   * @return the current locale
   */
  public static synchronized Locale getLocale() {
    return currentLocale;
  }

  /**
   * Gets a message by key.
   *
   * @param key the message key
   * @return the localized message, or the key itself if not found
   */
  public static synchronized String getMessage(final String key) {
    if (key == null) {
      return "";
    }
    try {
      return bundle.getString(key);
    } catch (MissingResourceException e) {
      return key;
    }
  }

  /**
   * Gets a message by key with formatting arguments.
   *
   * @param key the message key
   * @param args formatting arguments
   * @return the formatted localized message using the current shared locale, or the raw pattern
   *     when formatting fails
   */
  public static String getMessage(final String key, final Object... args) {
    final String pattern = getMessage(key);
    if (args == null || args.length == 0) {
      return pattern;
    }
    try {
      final Locale messageLocale = resolveLocale(getLocale());
      final MessageFormat formatter = new MessageFormat(pattern, messageLocale);
      return formatter.format(args);
    } catch (IllegalArgumentException e) {
      return pattern;
    }
  }

  private static void loadBundle(final Locale locale) {
    currentLocale = locale;
    try {
      bundle = ResourceBundle.getBundle(BUNDLE_NAME, currentLocale);
    } catch (MissingResourceException e) {
      // Keep bundle loading resilient when the requested locale bundle is unavailable.
      bundle = ResourceBundle.getBundle(BUNDLE_NAME, DEFAULT_LOCALE);
      currentLocale = DEFAULT_LOCALE;
    }
  }

  private static Locale resolveLocale(final Locale locale) {
    return locale == null ? DEFAULT_LOCALE : locale;
  }

  private static Locale detectLocale() {
    // 1. Check environment variable
    final String environmentLanguageTag = System.getenv("FUL_LANG");
    if (environmentLanguageTag != null && !environmentLanguageTag.isBlank()) {
      return parseLanguageTag(environmentLanguageTag);
    }
    // 2. Check system property
    final String systemPropertyLanguageTag = System.getProperty("ful.lang");
    if (systemPropertyLanguageTag != null && !systemPropertyLanguageTag.isBlank()) {
      return parseLanguageTag(systemPropertyLanguageTag);
    }
    // 3. Use system default
    return Locale.getDefault();
  }

  private static Locale parseLanguageTag(final String languageTag) {
    return Locale.forLanguageTag(languageTag.replace('_', '-'));
  }
}
