package com.craftsmanbro.fulcraft.infrastructure.reporting.contract;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.reporting.model.PdfTemplateContext;
import java.util.Objects;

/** Contract for rendering PDF-ready HTML templates. */
public interface PdfTemplatePort {

  String template();

  default String render(final PdfTemplateContext context) {
    Objects.requireNonNull(
        context, MessageSource.getMessage("infra.common.error.argument_null", "context"));
    return renderTemplate(template(), context);
  }

  private static String renderTemplate(
      final String htmlTemplate, final PdfTemplateContext context) {
    return htmlTemplate
        .replace("{{LANG}}", context.languageTag())
        .replace("{{PAGE_TITLE}}", context.pageTitle())
        .replace("{{HEADER_TEXT}}", context.headerText())
        .replace("{{FOOTER_TEXT}}", context.footerText())
        .replace("{{CONTENT}}", context.contentHtml());
  }
}
