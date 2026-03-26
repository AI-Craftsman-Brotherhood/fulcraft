package com.craftsmanbro.fulcraft.plugins.document.adapter;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.reporting.impl.PdfTemplates;
import com.craftsmanbro.fulcraft.infrastructure.reporting.model.PdfTemplateContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.document.contract.DocumentGenerator;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates PDF documentation from analysis results.
 *
 * <p>This generator first creates HTML content using HtmlDocumentGenerator. In a full
 * implementation, it would convert HTML to PDF using a library like OpenHTMLtoPDF. The current
 * implementation generates HTML with PDF-friendly styling as a placeholder.
 *
 * <p>Note: For production use, add OpenHTMLtoPDF dependency and implement actual PDF conversion.
 */
public class PdfDocumentGenerator implements DocumentGenerator {

  private static final String FORMAT = "pdf";

  // Placeholder, would be .pdf with OpenHTMLtoPDF
  private static final String EXTENSION = ".html";

  private final MarkdownDocumentGenerator markdownGenerator;

  public PdfDocumentGenerator() {
    this.markdownGenerator = new MarkdownDocumentGenerator();
  }

  public PdfDocumentGenerator(final MarkdownDocumentGenerator markdownGenerator) {
    this.markdownGenerator = markdownGenerator;
  }

  @Override
  public int generate(final AnalysisResult result, final Path outputDir, final Config config)
      throws IOException {
    Files.createDirectories(outputDir);
    int count = 0;
    for (final ClassInfo classInfo : result.getClasses()) {
      final String markdown = markdownGenerator.generateClassDocument(classInfo);
      final String html = convertToPdfReadyHtml(markdown, classInfo);
      // Note: In production, convert HTML to PDF using OpenHTMLtoPDF here
      // For now, output PDF-ready HTML that can be printed to PDF
      final String relativePath =
          DocumentUtils.generateSourceAlignedReportPath(classInfo, "_print" + EXTENSION);
      final Path outputFile = outputDir.resolve(relativePath);
      final Path parent = outputFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(outputFile, html, StandardCharsets.UTF_8);
      removeLegacyFlatOutput(outputDir, classInfo, outputFile);
      count++;
    }
    Logger.info(MessageSource.getMessage("report.docs.pdf.generated", count));
    Logger.info(MessageSource.getMessage("report.docs.pdf.hint"));
    return count;
  }

  private void removeLegacyFlatOutput(
      final Path outputDir, final ClassInfo classInfo, final Path resolvedOutputFile)
      throws IOException {
    final String legacyName =
        DocumentUtils.generateFileName(classInfo.getFqn(), "_print" + EXTENSION);
    final Path legacyPath = outputDir.resolve(legacyName);
    if (legacyPath.equals(resolvedOutputFile)) {
      return;
    }
    Files.deleteIfExists(legacyPath);
  }

  @Override
  public String getFormat() {
    return FORMAT;
  }

  @Override
  public String getFileExtension() {
    // PDF-ready HTML until actual PDF conversion is implemented
    return EXTENSION;
  }

  /**
   * Converts Markdown to PDF-ready HTML with print-friendly styling.
   *
   * @param markdown the Markdown content
   * @param classInfo the class information
   * @return PDF-ready HTML content
   */
  public String convertToPdfReadyHtml(final String markdown, final ClassInfo classInfo) {
    final String title = DocumentUtils.getSimpleName(classInfo.getFqn());
    final String timestamp = java.time.LocalDateTime.now().toString();
    final String pageTitle = msg("document.page.title", title);
    final String headerText = msg("pdf.template.header", timestamp);
    final String footerText = msg("pdf.template.footer");
    final String htmlContent = simpleMarkdownToHtml(markdown);
    final PdfTemplateContext context =
        new PdfTemplateContext(getLanguageTag(), pageTitle, headerText, footerText, htmlContent);
    return PdfTemplates.port().render(context);
  }

  // Reuse similar HTML conversion logic from HtmlDocumentGenerator
  // In production, consider extracting this to a shared utility
  private String simpleMarkdownToHtml(final String markdown) {
    if (markdown == null || markdown.isEmpty()) {
      return "";
    }
    final StringBuilder html = new StringBuilder();
    final MarkdownParseState state = new MarkdownParseState();
    for (final String line : markdown.split("\n")) {
      processMarkdownLine(html, line, state);
    }
    closeOpenBlocks(html, state);
    return html.toString();
  }

  private void processMarkdownLine(
      final StringBuilder html, final String line, final MarkdownParseState state) {
    if (handleCodeFence(html, line, state)) {
      return;
    }
    if (state.inCodeBlock) {
      appendCodeLine(html, line);
      return;
    }
    closeOpenBlocksIfNeeded(html, line, state);
    if (handleHeading(html, line)) {
      return;
    }
    if (handleWarningLine(html, line)) {
      return;
    }
    if (handleTableLine(html, line, state)) {
      return;
    }
    if (handleListLine(html, line, state)) {
      return;
    }
    if (handleHorizontalRule(html, line)) {
      return;
    }
    if (handleBoldLine(html, line)) {
      return;
    }
    if (line.isBlank()) {
      return;
    }
    appendParagraph(html, line);
  }

  private boolean handleCodeFence(
      final StringBuilder html, final String line, final MarkdownParseState state) {
    if (!line.startsWith("```")) {
      return false;
    }
    if (state.inCodeBlock) {
      html.append("</code></pre>\n");
      state.inCodeBlock = false;
    } else {
      html.append("<pre><code>");
      state.inCodeBlock = true;
    }
    return true;
  }

  private void appendCodeLine(final StringBuilder html, final String line) {
    html.append(DocumentUtils.escapeHtml(line)).append("\n");
  }

  private void closeOpenBlocksIfNeeded(
      final StringBuilder html, final String line, final MarkdownParseState state) {
    if (state.inList && !line.startsWith("- ")) {
      html.append("</ul>\n");
      state.inList = false;
    }
    if (state.inTable && !line.startsWith("|")) {
      html.append("</tbody></table>\n");
      state.inTable = false;
    }
  }

  private boolean handleHeading(final StringBuilder html, final String line) {
    if (line.startsWith("### ")) {
      appendHeading(html, "h3", line.substring(4));
      return true;
    }
    if (line.startsWith("## ")) {
      appendHeading(html, "h2", line.substring(3));
      return true;
    }
    if (line.startsWith("# ")) {
      appendHeading(html, "h1", line.substring(2));
      return true;
    }
    return false;
  }

  private void appendHeading(final StringBuilder html, final String tagName, final String text) {
    html.append("<")
        .append(tagName)
        .append(">")
        .append(processInlineElements(text))
        .append("</")
        .append(tagName)
        .append(">\n");
  }

  private boolean handleWarningLine(final StringBuilder html, final String line) {
    if (line.startsWith("> ⚠️") || line.startsWith(">  ⚠️")) {
      appendWarningCard(html, line, "warning", "⚠️");
      return true;
    }
    if (line.startsWith("> 🔴")) {
      appendWarningCard(html, line, "critical", "🔴");
      return true;
    }
    return false;
  }

  private void appendWarningCard(
      final StringBuilder html, final String line, final String className, final String marker) {
    html.append("<div class=\"")
        .append(className)
        .append("\">")
        .append(processInlineElements(line.substring(line.indexOf(marker))))
        .append("</div>\n");
  }

  private boolean handleTableLine(
      final StringBuilder html, final String line, final MarkdownParseState state) {
    if (!line.startsWith("|")) {
      return false;
    }
    if (line.contains("---")) {
      return true;
    }
    final String[] cells = line.split("\\|");
    if (state.inTable) {
      html.append("<tr>");
      appendTableCells(html, cells, "td");
      html.append("</tr>\n");
    } else {
      html.append("<table><thead><tr>");
      appendTableCells(html, cells, "th");
      html.append("</tr></thead><tbody>\n");
      state.inTable = true;
    }
    return true;
  }

  private void appendTableCells(
      final StringBuilder html, final String[] cells, final String cellTag) {
    for (int i = 1; i < cells.length - 1; i++) {
      html.append("<")
          .append(cellTag)
          .append(">")
          .append(processInlineElements(cells[i].trim()))
          .append("</")
          .append(cellTag)
          .append(">");
    }
  }

  private boolean handleListLine(
      final StringBuilder html, final String line, final MarkdownParseState state) {
    if (!line.startsWith("- ")) {
      return false;
    }
    if (!state.inList) {
      html.append("<ul>\n");
      state.inList = true;
    }
    html.append("<li>").append(processInlineElements(line.substring(2))).append("</li>\n");
    return true;
  }

  private boolean handleHorizontalRule(final StringBuilder html, final String line) {
    if (!"---".equals(line) && !"---\n".equals(line)) {
      return false;
    }
    html.append("<hr>\n");
    return true;
  }

  private boolean handleBoldLine(final StringBuilder html, final String line) {
    if (!line.startsWith("**") || !line.contains("**:")) {
      return false;
    }
    html.append("<p>").append(processInlineElements(line)).append("</p>\n");
    return true;
  }

  private void appendParagraph(final StringBuilder html, final String line) {
    html.append("<p>").append(processInlineElements(line)).append("</p>\n");
  }

  private void closeOpenBlocks(final StringBuilder html, final MarkdownParseState state) {
    if (state.inList) {
      html.append("</ul>\n");
    }
    if (state.inTable) {
      html.append("</tbody></table>\n");
    }
    if (state.inCodeBlock) {
      html.append("</code></pre>\n");
    }
  }

  private String processInlineElements(final String text) {
    if (text == null) {
      return "";
    }
    String normalized = DocumentUtils.escapeHtml(text);
    normalized = normalized.replaceAll("`([^`]+)`", "<code>$1</code>");
    normalized = normalized.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
    normalized = normalized.replaceAll("\\[([^]]+)]\\(#([^)]+)\\)", "<a href=\"#$2\">$1</a>");
    return normalized;
  }

  private String getLanguageTag() {
    final java.util.Locale locale = MessageSource.getLocale();
    if (locale == null) {
      return "ja";
    }
    return locale.toLanguageTag();
  }

  private String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }

  private static final class MarkdownParseState {

    private boolean inCodeBlock;

    private boolean inTable;

    private boolean inList;
  }
}
