package com.craftsmanbro.fulcraft.plugins.reporting.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.Config.ProjectConfig;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.reporting.contract.ReportWriteException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AnalysisVisualReportWriterTest {

  @TempDir Path tempDir;

  private AnalysisVisualReportWriter writer;
  private RunContext context;
  private Config config;
  private final ObjectMapper objectMapper = JsonMapperFactory.create();

  @BeforeEach
  void setUp() {
    writer = new AnalysisVisualReportWriter();
    config = new Config();
    ProjectConfig projectConfig = new ProjectConfig();
    projectConfig.setId("test-project");
    config.setProject(projectConfig);

    context = new RunContext(tempDir, config, "run-123");
  }

  @Test
  void writeReport_ShouldGenerateFile() throws Exception {
    // Arrange
    AnalysisResult result = new AnalysisResult();
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn("com.example.TestClass");
    classInfo.setFilePath("src/main/java/com/example/TestClass.java");
    classInfo.setLoc(100);

    MethodInfo methodInfo = new MethodInfo();
    methodInfo.setName("testMethod");
    methodInfo.setCyclomaticComplexity(5);

    MethodInfo implicitConstructor = new MethodInfo();
    implicitConstructor.setName("TestClass");
    implicitConstructor.setSignature("TestClass()");
    implicitConstructor.setParameterCount(0);
    implicitConstructor.setLoc(0);

    classInfo.setMethods(List.of(implicitConstructor, methodInfo));

    result.setClasses(List.of(classInfo));
    AnalysisResultContext.set(context, result);

    // Act
    Path reportPath = writer.writeReport(result, context, tempDir, config);

    // Assert
    assertThat(reportPath).exists();
    assertThat(Files.readString(reportPath))
        .contains(MessageSource.getMessage("report.visual.title", "test-project"))
        .contains("com.example.TestClass")
        .contains("\"detailLink\":\"src/main/java/com/example/TestClass.html\"")
        .contains("\"filePath\":\"src/main/java/com/example/TestClass.java\"")
        .contains("\"methodCount\":1");
  }

  @Test
  void writeReport_ShouldHandleEmptyResult() throws Exception {
    AnalysisResult result = new AnalysisResult();
    result.setClasses(Collections.emptyList());

    Path reportPath = writer.writeReport(result, context, tempDir, config);

    assertThat(reportPath).exists();
    assertThat(Files.readString(reportPath)).contains("\"classes\":[]");
  }

  @Test
  void writeReport_ShouldEmbedMarkdownReport_WhenReportMarkdownExists() throws Exception {
    Files.writeString(
        tempDir.resolve("report.md"),
        "# Report Heading\n\n```java\nSystem.out.println(\"ok\");\n```\n",
        StandardCharsets.UTF_8);

    AnalysisResult result = new AnalysisResult();
    result.setClasses(Collections.emptyList());

    Path reportPath = writer.writeReport(result, context, tempDir, config);

    String html = Files.readString(reportPath);
    assertThat(html)
        .contains("analysis-report-markdown")
        .contains("Report Heading")
        .contains("System.out.println");
  }

  @Test
  void writeReport_ShouldUseExistingHtmlReportAsRawLink_WhenMarkdownDoesNotExist()
      throws Exception {
    Files.writeString(
        tempDir.resolve("report.html"), "<html><body>ok</body></html>", StandardCharsets.UTF_8);

    AnalysisResult result = new AnalysisResult();
    result.setClasses(Collections.emptyList());

    Path reportPath = writer.writeReport(result, context, tempDir, config);

    String html = Files.readString(reportPath);
    assertThat(html)
        .contains("id=\"report-raw-link\"")
        .contains("href=\"report.html\"")
        .contains("id=\"report-frame\"");
  }

  @Test
  void writeReport_ShouldFallbackRawLinkToMarkdown_WhenNoRawReportExists() throws Exception {
    AnalysisResult result = new AnalysisResult();
    result.setClasses(Collections.emptyList());

    Path reportPath = writer.writeReport(result, context, tempDir, config);

    String html = Files.readString(reportPath);
    assertThat(html).contains("id=\"report-raw-link\"").contains("href=\"report.md\"");
  }

  @Test
  void writeReport_ShouldLoadTemplate_WhenPathStartsWithSlash() throws Exception {
    Config slashConfig = new Config();
    ProjectConfig projectConfig = new ProjectConfig();
    projectConfig.setId("slash-project");
    slashConfig.setProject(projectConfig);

    Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
    analysisConfig.setVisualReportTemplate("/templates/report/analysis_visual.html.tmpl");
    slashConfig.setAnalysis(analysisConfig);

    RunContext slashContext = new RunContext(tempDir, slashConfig, "run-456");
    AnalysisResult result = new AnalysisResult();
    result.setClasses(Collections.emptyList());

    Path reportPath = writer.writeReport(result, slashContext, tempDir, slashConfig);

    assertThat(reportPath).exists();
    assertThat(Files.readString(reportPath))
        .contains(MessageSource.getMessage("report.visual.title", "slash-project"));
  }

  @Test
  void writeReport_ShouldThrowReportWriteException_WhenTemplateNotFound() {
    Config invalidConfig = new Config();
    ProjectConfig projectConfig = new ProjectConfig();
    projectConfig.setId("invalid-project");
    invalidConfig.setProject(projectConfig);

    Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
    analysisConfig.setVisualReportTemplate("templates/report/missing-template.tmpl");
    invalidConfig.setAnalysis(analysisConfig);

    RunContext invalidContext = new RunContext(tempDir, invalidConfig, "run-789");
    AnalysisResult result = new AnalysisResult();
    result.setClasses(Collections.emptyList());

    assertThatThrownBy(() -> writer.writeReport(result, invalidContext, tempDir, invalidConfig))
        .isInstanceOf(ReportWriteException.class)
        .hasMessage(MessageSource.getMessage("report.visual.write_failed"));
  }

  @Test
  void writeReport_ShouldRenderEdgesCoverageAndLocaleFallback() throws Exception {
    Locale previousLocale = MessageSource.getLocale();
    MessageSource.setLocale(Locale.ROOT);
    try {
      config.getProject().setId("   ");
      Path coveragePath = tempDir.resolve("jacoco.xml");
      writeCoverageReport(coveragePath, "com/example/source/RootService", 80, 20);
      Config.QualityGateConfig qualityGate = new Config.QualityGateConfig();
      qualityGate.setCoverageReportPath(coveragePath.toString());
      config.setQualityGate(qualityGate);

      Files.writeString(tempDir.resolve("report.json"), "{\"ok\":true}", StandardCharsets.UTF_8);

      List<ClassInfo> classes = new ArrayList<>();
      ClassInfo sourceClass =
          newClassInfo(
              "com.example.source.RootService",
              tempDir.resolve("src/main/java/com/example/source/RootService.java").toString(),
              40,
              List.of());

      List<CalledMethodRef> refs = new ArrayList<>();
      refs.add(newCalledMethodRef("com.example.source.RootService#doWork()", null));
      for (int i = 0; i < 15; i++) {
        String targetFqn = "com.example.target" + i + ".Target" + i;
        refs.add(newCalledMethodRef(targetFqn + "#call()", null));
        classes.add(
            newClassInfo(
                targetFqn,
                "src/main/java/com/example/target" + i + "/Target" + i + ".java",
                10 + i,
                List.of(newMethodInfo("m" + i, 1, List.of()))));
      }
      refs.add(newCalledMethodRef("org.acme.http.Client#send", null));
      refs.add(newCalledMethodRef("org.acme.http.Client#send", null));
      refs.add(newCalledMethodRef("java.util.List#add", null));
      refs.add(newCalledMethodRef(null, "DuplicateService#run"));
      refs.add(new CalledMethodRef());

      sourceClass.setMethods(List.of(newMethodInfo("entry", 7, refs)));
      classes.add(sourceClass);
      classes.add(
          newClassInfo(
              "com.foo.DuplicateService",
              "src/main/java/com/foo/DuplicateService.java",
              12,
              List.of(newMethodInfo("run", 1, List.of()))));
      classes.add(
          newClassInfo(
              "com.bar.DuplicateService",
              "src/main/java/com/bar/DuplicateService.java",
              13,
              List.of(newMethodInfo("run", 1, List.of()))));

      AnalysisResult result = new AnalysisResult();
      result.setClasses(classes);
      AnalysisResultContext.set(context, result);

      Path reportPath = writer.writeReport(result, context, tempDir, config);
      String html = Files.readString(reportPath);
      JsonNode data = extractAnalysisData(html);

      assertThat(html).contains("<html lang=\"en\">");
      assertThat(html).contains("id=\"report-raw-link\"").contains("href=\"report.json\"");
      assertThat(data.path("projectId").asText())
          .isEqualTo(MessageSource.getMessage("report.value.unknown_word"));
      assertThat(data.path("classEdges").size()).isEqualTo(12);
      assertThat(data.toString()).contains("\"id\":\"org.acme\"");
      assertThat(data.toString()).contains("\"lineCoverage\":80.0");
    } finally {
      MessageSource.setLocale(previousLocale);
    }
  }

  @Test
  void writeReport_ShouldCapPackageEdgesAtGlobalLimit() throws Exception {
    List<ClassInfo> classes = new ArrayList<>();
    ClassInfo sourceClass =
        newClassInfo(
            "com.example.source.Root", "src/main/java/com/example/source/Root.java", 30, List.of());

    List<CalledMethodRef> refs = new ArrayList<>();
    for (int i = 0; i < 220; i++) {
      String targetFqn = "com.example.pkg" + i + ".Target" + i;
      refs.add(newCalledMethodRef(targetFqn + "#run()", null));
      classes.add(
          newClassInfo(
              targetFqn,
              "src/main/java/com/example/pkg" + i + "/Target" + i + ".java",
              5,
              List.of(newMethodInfo("run", 1, List.of()))));
    }
    sourceClass.setMethods(List.of(newMethodInfo("root", 3, refs)));
    classes.add(sourceClass);

    AnalysisResult result = new AnalysisResult();
    result.setClasses(classes);

    Path reportPath = writer.writeReport(result, context, tempDir, config);
    JsonNode data = extractAnalysisData(Files.readString(reportPath));

    assertThat(data.path("packageEdges").size()).isEqualTo(200);
    assertThat(data.path("classEdges").size()).isEqualTo(12);
    assertThat(data.path("fileEdges").size()).isEqualTo(12);
  }

  @Test
  void writeReport_ShouldThrowReportWriteException_WhenOutputDirIsAFile() throws Exception {
    Path outputFile = tempDir.resolve("occupied-output");
    Files.writeString(outputFile, "not-a-directory", StandardCharsets.UTF_8);

    AnalysisResult result = new AnalysisResult();
    result.setClasses(Collections.emptyList());

    assertThatThrownBy(() -> writer.writeReport(result, context, outputFile, config))
        .isInstanceOf(ReportWriteException.class)
        .hasMessage(MessageSource.getMessage("report.visual.write_failed"));
  }

  private MethodInfo newMethodInfo(String name, int complexity, List<CalledMethodRef> refs) {
    MethodInfo methodInfo = new MethodInfo();
    methodInfo.setName(name);
    methodInfo.setCyclomaticComplexity(complexity);
    methodInfo.setCalledMethodRefs(refs);
    return methodInfo;
  }

  private ClassInfo newClassInfo(String fqn, String filePath, int loc, List<MethodInfo> methods) {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn(fqn);
    classInfo.setFilePath(filePath);
    classInfo.setLoc(loc);
    classInfo.setMethods(methods);
    return classInfo;
  }

  private CalledMethodRef newCalledMethodRef(String resolved, String raw) {
    CalledMethodRef ref = new CalledMethodRef();
    ref.setResolved(resolved);
    ref.setRaw(raw);
    return ref;
  }

  private JsonNode extractAnalysisData(String html) throws Exception {
    String startTag = "<script type=\"application/json\" id=\"analysis-data\">";
    int start = html.indexOf(startTag);
    assertThat(start).isGreaterThanOrEqualTo(0);

    int contentStart = start + startTag.length();
    int end = html.indexOf("</script>", contentStart);
    assertThat(end).isGreaterThan(contentStart);

    String json = html.substring(contentStart, end).trim();
    return objectMapper.readTree(json);
  }

  private void writeCoverageReport(
      Path coveragePath, String className, int lineCovered, int lineMissed) throws Exception {
    String content =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <report name="test">
          <package name="com/example/source">
            <class name="%s">
              <counter type="LINE" missed="%d" covered="%d"/>
            </class>
          </package>
        </report>
        """
            .formatted(className, lineMissed, lineCovered);
    Files.writeString(coveragePath, content, StandardCharsets.UTF_8);
  }
}
