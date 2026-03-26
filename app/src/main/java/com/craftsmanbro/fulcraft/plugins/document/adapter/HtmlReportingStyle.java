package com.craftsmanbro.fulcraft.plugins.document.adapter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Centralized style definitions for HTML reports. */
public final class HtmlReportingStyle {

  private static final String CSS_RESOURCE = "styles/html_reporting.css";

  private static final String FALLBACK_CSS =
      """
      :root {
        --bg-body: #f2f6fb;
        --bg-card: #ffffff;
        --text-primary: #13233d;
        --primary-500: #0f9f8c;
        --border-color: #d5e1ef;
      }

      * {
        box-sizing: border-box;
      }

      body {
        margin: 0;
        padding: 2rem;
        font-family: "Avenir Next", "Segoe UI", sans-serif;
        background: var(--bg-body);
        color: var(--text-primary);
      }

      .container {
        max-width: 1120px;
        margin: 0 auto;
      }

      table {
        width: 100%;
        border-collapse: collapse;
        background: var(--bg-card);
      }

      th,
      td {
        padding: 0.6rem 0.8rem;
        border: 1px solid var(--border-color);
        text-align: left;
      }
      """;

  private static final String CSS = loadCss();

  private HtmlReportingStyle() {
    // Utility class
  }

  public static String css() {
    return CSS;
  }

  private static String loadCss() {
    final InputStream resourceStream =
        HtmlReportingStyle.class.getClassLoader().getResourceAsStream(CSS_RESOURCE);
    if (resourceStream == null) {
      return FALLBACK_CSS;
    }
    try (InputStream input = resourceStream) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return FALLBACK_CSS;
    }
  }
}
