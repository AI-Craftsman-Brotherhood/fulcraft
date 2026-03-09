package com.craftsmanbro.fulcraft.infrastructure.reporting.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PdfTemplateContextTest {

  @Test
  void constructor_normalizesNullValues() {
    PdfTemplateContext context = new PdfTemplateContext(null, null, null, null, null);

    assertThat(context.languageTag()).isEqualTo("en");
    assertThat(context.pageTitle()).isEmpty();
    assertThat(context.headerText()).isEmpty();
    assertThat(context.footerText()).isEmpty();
    assertThat(context.contentHtml()).isEmpty();
  }
}
