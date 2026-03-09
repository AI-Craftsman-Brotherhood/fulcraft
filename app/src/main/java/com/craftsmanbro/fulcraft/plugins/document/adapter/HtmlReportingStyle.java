package com.craftsmanbro.fulcraft.plugins.document.adapter;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Centralized style definitions for HTML reports. */
public final class HtmlReportingStyle {

  private static final String CSS_RESOURCE = "styles/html_reporting.css";
  private static final String CSS = loadCss();

  private HtmlReportingStyle() {
    // Utility class
  }

  public static String css() {
    return CSS;
  }

  private static String loadCss() {
    try (InputStream input =
        HtmlReportingStyle.class.getClassLoader().getResourceAsStream(CSS_RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException(
            MessageSource.getMessage("document.resource.html_style.not_found", CSS_RESOURCE));
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(
          MessageSource.getMessage("document.resource.html_style.load_failed", CSS_RESOURCE), e);
    }
  }
}
