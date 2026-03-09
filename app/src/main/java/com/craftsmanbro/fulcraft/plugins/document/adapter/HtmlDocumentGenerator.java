package com.craftsmanbro.fulcraft.plugins.document.adapter;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.document.contract.DocumentGenerator;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates HTML documentation from analysis results.
 *
 * <p>This generator first creates Markdown content using MarkdownDocumentGenerator, then converts
 * it to HTML. This approach reuses existing Markdown generation logic and avoids code duplication.
 *
 * <p>Note: For production use, consider using flexmark-java for proper Markdown to HTML conversion.
 * The current implementation provides a simplified HTML wrapper.
 */
public class HtmlDocumentGenerator implements DocumentGenerator {

  private static final String FORMAT = "html";

  private static final String EXTENSION = ".html";

  private static final String HTML_TEMPLATE_RESOURCE = "templates/document/html_document.html.tmpl";

  private static final String STYLE_PLACEHOLDER = "{{STYLE}}";

  private static final String HTML_TEMPLATE = loadHtmlTemplate();

  private final MarkdownDocumentGenerator markdownGenerator;

  public HtmlDocumentGenerator() {
    this.markdownGenerator = new MarkdownDocumentGenerator();
  }

  public HtmlDocumentGenerator(final MarkdownDocumentGenerator markdownGenerator) {
    this.markdownGenerator = markdownGenerator;
  }

  @Override
  public int generate(final AnalysisResult result, final Path outputDir, final Config config)
      throws IOException {
    Files.createDirectories(outputDir);
    int count = 0;
    for (final ClassInfo classInfo : result.getClasses()) {
      final String markdown = markdownGenerator.generateClassDocument(classInfo);
      final String html = convertToHtml(markdown, classInfo);
      final String relativePath =
          DocumentUtils.generateSourceAlignedReportPath(classInfo, EXTENSION);
      final Path outputFile = outputDir.resolve(relativePath);
      final Path parent = outputFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(outputFile, html, StandardCharsets.UTF_8);
      removeLegacyFlatOutput(outputDir, classInfo, outputFile);
      count++;
    }
    Logger.info(MessageSource.getMessage("report.docs.html.generated", count));
    return count;
  }

  private void removeLegacyFlatOutput(
      final Path outputDir, final ClassInfo classInfo, final Path resolvedOutputFile)
      throws IOException {
    final String legacyName = DocumentUtils.generateFileName(classInfo.getFqn(), EXTENSION);
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
    return EXTENSION;
  }

  /**
   * Converts Markdown content to HTML.
   *
   * <p>This is a simplified conversion. For production use, consider using flexmark-java or similar
   * libraries for proper Markdown parsing.
   *
   * @param markdown the Markdown content
   * @param classInfo the class information for the title
   * @return the HTML content
   */
  public String convertToHtml(final String markdown, final ClassInfo classInfo) {
    final String title = DocumentUtils.getSimpleName(classInfo.getFqn());
    final String pageTitle = msg("document.page.title", title);
    final String htmlContent = simpleMarkdownToHtml(markdown);
    return HTML_TEMPLATE
        .replace("{{LANG}}", getLanguageTag())
        .replace("{{PAGE_TITLE}}", pageTitle)
        .replace("{{BADGE_LABEL}}", msg("document.html.badge"))
        .replace("{{FOOTER_TEXT}}", msg("document.html.footer"))
        .replace("{{TITLE}}", title)
        .replace("{{CONTENT}}", htmlContent);
  }

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
      appendCodeLine(html, line, state);
      return;
    }
    closeOpenBlocksIfNeeded(html, line, state);
    if (handleHeading(html, line, state)) {
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
      html.append("</code></pre></div>\n");
      state.inCodeBlock = false;
      state.codeLineNumber = 0;
    } else {
      final String language = resolveCodeLanguage(line);
      html.append("<div class=\"code-panel\">\n");
      html.append("  <div class=\"code-header\">")
          .append(formatCodeLanguageLabel(language))
          .append("</div>\n");
      html.append("  <pre><code class=\"language-")
          .append(normalizeCodeLanguageClass(language))
          .append("\">");
      state.inCodeBlock = true;
      state.codeLineNumber = 1;
    }
    return true;
  }

  private void appendCodeLine(
      final StringBuilder html, final String line, final MarkdownParseState state) {
    html.append("<span class=\"code-line\"><span class=\"code-line-no\">")
        .append(state.codeLineNumber++)
        .append("</span><span class=\"code-line-text\">")
        .append(DocumentUtils.escapeHtml(line))
        .append("</span></span>\n");
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
      state.tableColumnCount = 0;
    }
  }

  private boolean handleHeading(
      final StringBuilder html, final String line, final MarkdownParseState state) {
    if (line.startsWith("### ")) {
      appendHeading(html, line.substring(4), "h3", state);
      return true;
    }
    if (line.startsWith("## ")) {
      appendHeading(html, line.substring(3), "h2", state);
      return true;
    }
    if (line.startsWith("# ")) {
      appendHeading(html, line.substring(2), "h1", state);
      return true;
    }
    return false;
  }

  private void appendHeading(
      final StringBuilder html,
      final String text,
      final String tagName,
      final MarkdownParseState state) {
    final String id = buildHeadingId(text, state.headingCounts, state.headingIndex++);
    html.append("<")
        .append(tagName)
        .append(" id=\"")
        .append(id)
        .append("\">")
        .append(processInlineElements(text))
        .append("</")
        .append(tagName)
        .append(">\n");
  }

  private boolean handleWarningLine(final StringBuilder html, final String line) {
    if (line.startsWith("> ⚠️") || line.startsWith(">  ⚠️")) {
      appendWarningCard(html, line, "⚠️", "warning-color");
      return true;
    }
    if (line.startsWith("> 🔴")) {
      appendWarningCard(html, line, "🔴", "failure-color");
      return true;
    }
    return false;
  }

  private void appendWarningCard(
      final StringBuilder html,
      final String line,
      final String marker,
      final String colorVariable) {
    html.append("<div class=\"card\" style=\"border-left: 4px solid var(--")
        .append(colorVariable)
        .append("); padding: 1rem;\">")
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
    final List<String> cells = parseTableCells(line);
    if (cells.isEmpty()) {
      return true;
    }
    if (state.inTable) {
      html.append("<tr>");
      appendTableCells(html, normalizeTableCells(cells, state.tableColumnCount), "td");
      html.append("</tr>\n");
    } else {
      html.append("<table><thead><tr>");
      appendTableCells(html, cells, "th");
      html.append("</tr></thead><tbody>\n");
      state.inTable = true;
      state.tableColumnCount = cells.size();
    }
    return true;
  }

  private void appendTableCells(
      final StringBuilder html, final List<String> cells, final String cellTag) {
    for (final String cell : cells) {
      html.append("<")
          .append(cellTag)
          .append(">")
          .append(processInlineElements(cell))
          .append("</")
          .append(cellTag)
          .append(">");
    }
  }

  private List<String> parseTableCells(final String line) {
    final List<String> rawCells = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    int index = 0;
    while (index < line.length()) {
      final char ch = line.charAt(index);
      if (ch == '\\' && index + 1 < line.length() && line.charAt(index + 1) == '|') {
        current.append('|');
        index += 2;
        continue;
      }
      if (ch == '|') {
        rawCells.add(current.toString().trim());
        current.setLength(0);
        index++;
        continue;
      }
      current.append(ch);
      index++;
    }
    rawCells.add(current.toString().trim());
    if (!rawCells.isEmpty() && rawCells.get(0).isEmpty()) {
      rawCells.remove(0);
    }
    if (!rawCells.isEmpty() && rawCells.get(rawCells.size() - 1).isEmpty()) {
      rawCells.remove(rawCells.size() - 1);
    }
    return rawCells;
  }

  private List<String> normalizeTableCells(
      final List<String> cells, final int expectedColumnCount) {
    if (expectedColumnCount <= 0 || cells.size() == expectedColumnCount) {
      return cells;
    }
    if (cells.size() < expectedColumnCount) {
      final List<String> normalized = new ArrayList<>(cells);
      while (normalized.size() < expectedColumnCount) {
        normalized.add("");
      }
      return normalized;
    }
    final List<String> normalized = new ArrayList<>(expectedColumnCount);
    final int fixedCells = Math.max(0, expectedColumnCount - 1);
    for (int i = 0; i < fixedCells; i++) {
      normalized.add(cells.get(i));
    }
    normalized.add(String.join(" | ", cells.subList(fixedCells, cells.size())));
    return normalized;
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
      state.tableColumnCount = 0;
    }
    if (state.inCodeBlock) {
      html.append("</code></pre></div>\n");
    }
  }

  private String resolveCodeLanguage(final String line) {
    if (line.length() <= 3) {
      return "text";
    }
    final String language = line.substring(3).trim();
    return language.isBlank() ? "text" : language;
  }

  private String normalizeCodeLanguageClass(final String language) {
    String normalized = language.toLowerCase(java.util.Locale.ROOT).trim();
    normalized = normalized.replaceAll("[^a-z0-9_-]+", "-");
    normalized = normalized.replaceAll("^-+", "");
    normalized = normalized.replaceAll("-+$", "");
    return normalized.isBlank() ? "text" : normalized;
  }

  private String formatCodeLanguageLabel(final String language) {
    final String normalized = language == null ? "text" : language.trim();
    return DocumentUtils.escapeHtml(normalized.toUpperCase(java.util.Locale.ROOT));
  }

  private String buildHeadingId(
      final String text, final Map<String, Integer> counts, final int index) {
    final String base = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
    String slug = base.replaceAll("[^a-z0-9]+", "-");
    slug = slug.replaceAll("^-+", "");
    slug = slug.replaceAll("-+$", "");
    if (slug.isEmpty()) {
      slug = "section-" + index;
    }
    final int current = counts.getOrDefault(slug, 0);
    final int count = current + 1;
    counts.put(slug, count);
    return count == 1 ? slug : slug + "-" + count;
  }

  private String processInlineElements(final String text) {
    if (text == null) {
      return "";
    }
    String normalized = DocumentUtils.escapeHtml(text);
    // Process inline code
    normalized = normalized.replaceAll("`([^`]+)`", "<code>$1</code>");
    // Process bold
    normalized = normalized.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
    // Process links [text](#anchor)
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

  private static String loadHtmlTemplate() {
    try (InputStream input =
        HtmlDocumentGenerator.class.getClassLoader().getResourceAsStream(HTML_TEMPLATE_RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException(
            MessageSource.getMessage(
                "document.resource.html_template.not_found", HTML_TEMPLATE_RESOURCE));
      }
      final String template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      return template.replace(STYLE_PLACEHOLDER, HtmlReportingStyle.css());
    } catch (IOException e) {
      throw new UncheckedIOException(
          MessageSource.getMessage(
              "document.resource.html_template.load_failed", HTML_TEMPLATE_RESOURCE),
          e);
    }
  }

  private static final class MarkdownParseState {

    private boolean inCodeBlock;

    private boolean inTable;

    private boolean inList;

    private int tableColumnCount;

    private int codeLineNumber;

    private final Map<String, Integer> headingCounts = new HashMap<>();

    private int headingIndex;
  }
}
