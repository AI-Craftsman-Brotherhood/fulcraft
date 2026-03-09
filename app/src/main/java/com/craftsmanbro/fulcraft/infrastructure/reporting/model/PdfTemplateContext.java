package com.craftsmanbro.fulcraft.infrastructure.reporting.model;

/** Immutable values required to render a PDF-ready HTML template. */
public record PdfTemplateContext(
    String languageTag,
    String pageTitle,
    String headerText,
    String footerText,
    String contentHtml) {
  private static final String DEFAULT_LANGUAGE_TAG = "en";
  private static final String DEFAULT_TEXT = "";

  public PdfTemplateContext {
    languageTag = normalize(languageTag, DEFAULT_LANGUAGE_TAG);
    pageTitle = normalizeOrEmpty(pageTitle);
    headerText = normalizeOrEmpty(headerText);
    footerText = normalizeOrEmpty(footerText);
    contentHtml = normalizeOrEmpty(contentHtml);
  }

  private static String normalizeOrEmpty(final String value) {
    return normalize(value, DEFAULT_TEXT);
  }

  private static String normalize(final String value, final String defaultValue) {
    return value == null ? defaultValue : value;
  }
}
