package com.craftsmanbro.fulcraft.plugins.document.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for HtmlDocumentGenerator. */
class HtmlDocumentGeneratorTest {

  @BeforeAll
  static void setUpLocale() {
    MessageSource.setLocale(Locale.JAPANESE);
  }

  @AfterAll
  static void resetLocale() {
    MessageSource.initialize();
  }

  @TempDir Path tempDir;

  private HtmlDocumentGenerator generator;
  private Config config;

  @BeforeEach
  void setUp() {
    generator = new HtmlDocumentGenerator();
    config = Config.createDefault();
  }

  @Test
  void generate_shouldCreateHtmlFiles() throws IOException {
    // Given
    AnalysisResult result = createTestAnalysisResult();

    // When
    int count = generator.generate(result, tempDir, config);

    // Then
    assertThat(count).isEqualTo(1);
    Path outputFile = tempDir.resolve("src/main/java/com/example/TestClass.html");
    assertThat(Files.exists(outputFile)).isTrue();

    String content = Files.readString(outputFile);
    assertThat(content).contains("<!DOCTYPE html>");
    assertThat(content).contains("<title>TestClass - ドキュメント</title>");
    assertThat(content).contains("<h1>TestClass</h1>");
  }

  @Test
  void generate_shouldRemoveLegacyFlatHtmlFile() throws IOException {
    AnalysisResult result = createTestAnalysisResult();
    Path legacyFlatPath = tempDir.resolve("com_example_TestClass.html");
    Files.createFile(legacyFlatPath);

    int count = generator.generate(result, tempDir, config);

    assertThat(count).isEqualTo(1);
    assertThat(Files.exists(tempDir.resolve("src/main/java/com/example/TestClass.html"))).isTrue();
    assertThat(Files.exists(legacyFlatPath)).isFalse();
  }

  @Test
  void generate_shouldKeepLegacyFlatPathWhenSourcePathIsMissing() throws IOException {
    AnalysisResult result = createTestAnalysisResult();
    result.getClasses().get(0).setFilePath(null);

    int count = generator.generate(result, tempDir, config);

    assertThat(count).isEqualTo(1);
    assertThat(Files.exists(tempDir.resolve("com_example_TestClass.html"))).isTrue();
  }

  @Test
  void convertToHtml_shouldIncludeModernStyling() {
    // Given
    MarkdownDocumentGenerator mdGenerator = new MarkdownDocumentGenerator();
    ClassInfo classInfo = createTestClassInfo();
    String markdown = mdGenerator.generateClassDocument(classInfo);

    // When
    String html = generator.convertToHtml(markdown, classInfo);

    // Then
    assertThat(html).contains("<style>");
    assertThat(html).contains("--bg-body:");
    assertThat(html).contains("--bg-card:");
    assertThat(html).contains("--text-primary:");
    assertThat(html).contains("--primary-500:");
    assertThat(html).doesNotContain("{{STYLE}}");
  }

  @Test
  void convertToHtml_shouldConvertMarkdownElements() {
    // Given
    ClassInfo classInfo = createTestClassInfo();

    // When
    String html = generator.convertToHtml("# Header\n\n**Bold** and `code`", classInfo);

    // Then
    assertThat(html).contains(">Header</h1>"); // Header ID is auto-generated
    assertThat(html).contains("<strong>Bold</strong>");
    assertThat(html).contains("<code>code</code>");
  }

  @Test
  void convertToHtml_shouldConvertTables() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    String markdown = "| Name | Value |\n|------|-------|\n| foo  | bar   |";

    // When
    String html = generator.convertToHtml(markdown, classInfo);

    // Then
    assertThat(html).contains("<table>");
    assertThat(html).contains("<th>Name</th>");
    assertThat(html).contains("<th>Value</th>");
    assertThat(html).contains("<td>foo</td>");
    assertThat(html).contains("<td>bar</td>");
  }

  @Test
  void convertToHtml_shouldHandleEscapedPipesInTableCells() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    String markdown =
        "| ID | 条件 | 期待 |\n"
            + "|----|------|------|\n"
            + "| path-1 | orderId == null \\|\\| orderId.isBlank() | error \\| warning |";

    // When
    String html = generator.convertToHtml(markdown, classInfo);

    // Then
    assertThat(html).contains("<th>ID</th>");
    assertThat(html).contains("<th>条件</th>");
    assertThat(html).contains("<th>期待</th>");
    assertThat(html).contains("<td>orderId == null || orderId.isBlank()</td>");
    assertThat(html).contains("<td>error | warning</td>");
  }

  @Test
  void convertToHtml_shouldNormalizeTableCellsForMissingAndExtraColumns() {
    ClassInfo classInfo = createTestClassInfo();
    String markdown = "| A | B | C |\n" + "|---|---|---|\n" + "| 1 | 2 |\n" + "| x | y | z | w |";

    String html = generator.convertToHtml(markdown, classInfo);

    assertThat(html).contains("<td>1</td><td>2</td><td></td>");
    assertThat(html).contains("<td>x</td><td>y</td><td>z | w</td>");
  }

  @Test
  void convertToHtml_shouldConvertLists() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    String markdown = "- Item 1\n- Item 2";

    // When
    String html = generator.convertToHtml(markdown, classInfo);

    // Then
    assertThat(html).contains("<ul>");
    assertThat(html).contains("<li>Item 1</li>");
    assertThat(html).contains("<li>Item 2</li>");
  }

  @Test
  void convertToHtml_shouldConvertCodeBlocks() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    String markdown = "```java\npublic void test() {}\n```";

    // When
    String html = generator.convertToHtml(markdown, classInfo);

    // Then
    assertThat(html).contains("class=\"code-panel\"");
    assertThat(html).contains("<div class=\"code-header\">JAVA</div>");
    assertThat(html).contains("<pre><code class=\"language-java\">");
    assertThat(html).contains("class=\"code-line-no\">1</span>");
    assertThat(html).contains("public void test()");
    assertThat(html).contains("</code></pre></div>");
  }

  @Test
  void convertToHtml_shouldUseTextLanguageWhenCodeFenceDoesNotSpecifyLanguage() {
    ClassInfo classInfo = createTestClassInfo();
    String markdown = "```\nplain text\n```";

    String html = generator.convertToHtml(markdown, classInfo);

    assertThat(html).contains("<div class=\"code-header\">TEXT</div>");
    assertThat(html).contains("<pre><code class=\"language-text\">");
  }

  @Test
  void convertToHtml_shouldConvertWarningBlocks() {
    // Given
    ClassInfo classInfo = createTestClassInfo();
    String markdown = "> ⚠️ **警告**: This is a warning";

    // When
    String html = generator.convertToHtml(markdown, classInfo);

    // Then
    assertThat(html).contains("class=\"card\"");
    assertThat(html).contains("style=\"border-left: 4px solid var(--warning-color);");
    assertThat(html).contains("警告");
  }

  @Test
  void convertToHtml_shouldGenerateStableHeadingIdsForDuplicatesAndNonAsciiHeadings() {
    ClassInfo classInfo = createTestClassInfo();
    String markdown = "# Header\n# Header\n# 日本語見出し";

    String html = generator.convertToHtml(markdown, classInfo);

    assertThat(html).contains("<h1 id=\"header\">Header</h1>");
    assertThat(html).contains("<h1 id=\"header-2\">Header</h1>");
    assertThat(html).contains("<h1 id=\"section-2\">日本語見出し</h1>");
  }

  @Test
  void getFormat_shouldReturnHtml() {
    assertThat(generator.getFormat()).isEqualTo("html");
  }

  @Test
  void getFileExtension_shouldReturnHtml() {
    assertThat(generator.getFileExtension()).isEqualTo(".html");
  }

  private AnalysisResult createTestAnalysisResult() {
    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(createTestClassInfo()));
    return result;
  }

  private ClassInfo createTestClassInfo() {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");
    classInfo.setFilePath("src/main/java/com/example/TestClass.java");
    classInfo.setLoc(50);
    classInfo.setMethodCount(1);

    MethodInfo method = new MethodInfo();
    method.setName("testMethod");
    method.setSignature("public void testMethod()");
    method.setVisibility("public");
    method.setLoc(10);
    method.setCyclomaticComplexity(3);
    method.setMaxNestingDepth(1);
    method.setParameterCount(0);
    classInfo.setMethods(List.of(method));

    return classInfo;
  }
}
