package com.craftsmanbro.fulcraft.infrastructure.reporting.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.infrastructure.reporting.contract.PdfTemplatePort;
import com.craftsmanbro.fulcraft.infrastructure.reporting.model.PdfTemplateContext;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class PdfTemplatesTest {

  @Test
  void pdfReadyHtmlTemplate_containsRequiredPlaceholders() {
    String template = PdfTemplates.pdfReadyHtmlTemplate();

    assertThat(template)
        .contains(
            "{{LANG}}", "{{PAGE_TITLE}}", "{{HEADER_TEXT}}", "{{FOOTER_TEXT}}", "{{CONTENT}}");
  }

  @Test
  void pdfReadyHtmlTemplate_containsPrintFriendlyStructure() {
    String template = PdfTemplates.pdfReadyHtmlTemplate();

    assertThat(template).contains("<!DOCTYPE html>");
    assertThat(template).contains("<html lang=\"{{LANG}}\">");
    assertThat(template).contains("<title>{{PAGE_TITLE}}</title>");
    assertThat(template).contains("@page");
    assertThat(template).contains("size: A4;");
    assertThat(template).contains(".warning");
    assertThat(template).contains(".critical");
    assertThat(template).contains(".page-break");
    assertThat(template).contains("class=\"header\"");
    assertThat(template).contains("class=\"footer\"");
    assertThat(template).containsSubsequence("class=\"header\"", "{{CONTENT}}", "class=\"footer\"");
  }

  @Test
  void pdfReadyHtmlTemplate_returnsStableStringInstance() {
    String first = PdfTemplates.pdfReadyHtmlTemplate();
    String second = PdfTemplates.pdfReadyHtmlTemplate();

    assertThat(second).isSameAs(first);
  }

  @Test
  void port_rendersTemplateWithContextValues() {
    PdfTemplatePort port = PdfTemplates.port();
    PdfTemplateContext context =
        new PdfTemplateContext("ja", "Title", "Header", "Footer", "<h1>content</h1>");

    String rendered = port.render(context);

    assertThat(rendered).contains("<html lang=\"ja\">");
    assertThat(rendered).contains("<title>Title</title>");
    assertThat(rendered).contains("Header");
    assertThat(rendered).contains("Footer");
    assertThat(rendered).contains("<h1>content</h1>");
    assertThat(rendered).doesNotContain("{{PAGE_TITLE}}");
    assertThat(rendered).doesNotContain("{{CONTENT}}");
  }

  @Test
  void pdfTemplates_followsUtilityClassConventions() throws Exception {
    assertThat(Modifier.isFinal(PdfTemplates.class.getModifiers())).isTrue();

    Constructor<PdfTemplates> constructor = PdfTemplates.class.getDeclaredConstructor();
    assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();

    constructor.setAccessible(true);
    PdfTemplates instance = constructor.newInstance();
    assertThat(instance).isNotNull();
  }
}
