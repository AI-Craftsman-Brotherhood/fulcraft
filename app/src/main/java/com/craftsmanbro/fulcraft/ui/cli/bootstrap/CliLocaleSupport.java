package com.craftsmanbro.fulcraft.ui.cli.bootstrap;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.Locale;
import java.util.ResourceBundle;

final class CliLocaleSupport {

  static final String MESSAGE_BUNDLE = "messages";

  private CliLocaleSupport() {}

  static void applyFromArgs(final String... args) {
    apply(extractLanguageTag(args), true);
  }

  static boolean applyOption(final String languageTag) {
    return apply(languageTag, false);
  }

  static ResourceBundle getMessages() {
    return ResourceBundle.getBundle(MESSAGE_BUNDLE, Locale.getDefault());
  }

  private static boolean apply(final String languageTag, final boolean rejectUndefinedTag) {
    if (languageTag == null || languageTag.isBlank()) {
      return false;
    }
    final Locale locale = Locale.forLanguageTag(languageTag.trim().replace('_', '-'));
    if (rejectUndefinedTag && "und".equals(locale.toLanguageTag())) {
      return false;
    }
    Locale.setDefault(locale);
    MessageSource.setLocale(locale);
    return true;
  }

  private static String extractLanguageTag(final String... args) {
    if (args == null || args.length == 0) {
      return null;
    }
    for (int i = 0; i < args.length; i++) {
      final String arg = args[i];
      if (arg == null) {
        continue;
      }
      if ("--lang".equals(arg) || "-l".equals(arg)) {
        if (i + 1 < args.length) {
          return args[i + 1];
        }
        return null;
      }
      if (arg.startsWith("--lang=")) {
        return arg.substring("--lang=".length());
      }
      if (arg.startsWith("-l") && arg.length() > 2) {
        return arg.substring(2);
      }
    }
    return null;
  }
}
