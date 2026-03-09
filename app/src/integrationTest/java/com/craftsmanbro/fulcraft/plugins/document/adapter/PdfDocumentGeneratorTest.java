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

class PdfDocumentGeneratorTest {

  @BeforeAll
  static void setUpLocale() {
    MessageSource.setLocale(Locale.JAPANESE);
  }

  @AfterAll
  static void resetLocale() {
    MessageSource.initialize();
  }

  @TempDir Path tempDir;

  private PdfDocumentGenerator generator;
  private Config config;

  @BeforeEach
  void setUp() {
    generator = new PdfDocumentGenerator();
    config = Config.createDefault();
  }

  @Test
  void generate_shouldCreatePdfReadyHtmlFiles() throws IOException {
    AnalysisResult result = createTestAnalysisResult();

    int count = generator.generate(result, tempDir, config);

    assertThat(count).isEqualTo(1);
    Path outputFile = tempDir.resolve("src/main/java/com/example/TestClass_print.html");
    assertThat(Files.exists(outputFile)).isTrue();

    String content = Files.readString(outputFile);
    String pageTitle = MessageSource.getMessage("document.page.title", "TestClass");
    assertThat(content).contains("<!DOCTYPE html>");
    assertThat(content).contains("<title>" + pageTitle + "</title>");
    assertThat(content).contains("<h1>TestClass</h1>");
    assertThat(content).contains("class=\"header\"");
    assertThat(content).contains(MessageSource.getMessage("pdf.template.footer"));
  }

  @Test
  void generate_shouldRemoveLegacyFlatPdfReadyFile() throws IOException {
    AnalysisResult result = createTestAnalysisResult();
    Path legacyFlatPath = tempDir.resolve("com_example_TestClass_print.html");
    Files.createFile(legacyFlatPath);

    int count = generator.generate(result, tempDir, config);

    assertThat(count).isEqualTo(1);
    assertThat(Files.exists(tempDir.resolve("src/main/java/com/example/TestClass_print.html")))
        .isTrue();
    assertThat(Files.exists(legacyFlatPath)).isFalse();
  }

  @Test
  void generate_shouldKeepLegacyFlatPathWhenSourcePathIsMissing() throws IOException {
    AnalysisResult result = createTestAnalysisResult();
    result.getClasses().get(0).setFilePath(null);

    int count = generator.generate(result, tempDir, config);

    assertThat(count).isEqualTo(1);
    assertThat(Files.exists(tempDir.resolve("com_example_TestClass_print.html"))).isTrue();
  }

  @Test
  void convertToPdfReadyHtml_shouldConvertMarkdownElements() {
    ClassInfo classInfo = createTestClassInfo();
    String markdown =
        "# Header\n"
            + "\n"
            + "- Item 1\n"
            + "\n"
            + "| Name | Value |\n"
            + "|------|-------|\n"
            + "| foo  | bar   |\n"
            + "\n"
            + "```java\n"
            + "int value = 1;\n"
            + "```\n"
            + "\n"
            + "**Bold** and `code`";

    String html = generator.convertToPdfReadyHtml(markdown, classInfo);

    assertThat(html).contains("<h1>Header</h1>");
    assertThat(html).contains("<ul>");
    assertThat(html).contains("<li>Item 1</li>");
    assertThat(html).contains("<table>");
    assertThat(html).contains("<th>Name</th>");
    assertThat(html).contains("<td>foo</td>");
    assertThat(html).contains("<pre><code>");
    assertThat(html).contains("<strong>Bold</strong>");
    assertThat(html).contains("<code>code</code>");
  }

  @Test
  void convertToPdfReadyHtml_shouldRenderWarningsAndHorizontalRule() {
    ClassInfo classInfo = createTestClassInfo();
    String markdown = ">  ⚠️ **警告**\n---";

    String html = generator.convertToPdfReadyHtml(markdown, classInfo);

    assertThat(html).contains("<div class=\"warning\">");
    assertThat(html).contains("<strong>警告</strong>");
    assertThat(html).contains("<hr>");
  }

  @Test
  void convertToPdfReadyHtml_shouldCloseOpenCodeBlockWhenFenceIsUnterminated() {
    ClassInfo classInfo = createTestClassInfo();
    String markdown = "```java\nint value = 1;";

    String html = generator.convertToPdfReadyHtml(markdown, classInfo);

    assertThat(html).contains("<pre><code>");
    assertThat(html).contains("int value = 1;");
    assertThat(html).contains("</code></pre>");
  }

  @Test
  void getFormat_shouldReturnPdf() {
    assertThat(generator.getFormat()).isEqualTo("pdf");
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
