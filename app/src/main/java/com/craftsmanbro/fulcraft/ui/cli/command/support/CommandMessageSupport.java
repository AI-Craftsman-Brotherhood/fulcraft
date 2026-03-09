package com.craftsmanbro.fulcraft.ui.cli.command.support;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

/** Shared helper for command-side ResourceBundle resolution and message formatting. */
public final class CommandMessageSupport {

  private static final String MESSAGE_BUNDLE = "messages";

  private CommandMessageSupport() {}

  public static ResourceBundle resolve(final ResourceBundle resourceBundle) {
    if (resourceBundle != null) {
      return resourceBundle;
    }
    try {
      return ResourceBundle.getBundle(MESSAGE_BUNDLE, Locale.getDefault());
    } catch (MissingResourceException e) {
      return null;
    }
  }

  public static String message(
      final ResourceBundle resourceBundle, final String key, final Object... args) {
    Objects.requireNonNull(key, "key must not be null");
    if (resourceBundle == null) {
      return key;
    }
    try {
      final String pattern = resourceBundle.getString(key);
      if (args.length == 0) {
        return pattern;
      }
      return MessageFormat.format(pattern, args);
    } catch (MissingResourceException e) {
      return key;
    }
  }
}
