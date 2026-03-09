package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LlmDocumentBatchProcessorTest {

  @TempDir Path tempDir;

  private final LlmDocumentBatchProcessor processor = new LlmDocumentBatchProcessor();

  @Test
  void generate_shouldWriteSourceAlignedFilesAndDeleteLegacyFlatOutput() throws IOException {
    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(classInfo("com.example.Foo", "src/main/java/com/example/Foo.java")));

    Path outputDir = tempDir.resolve("docs");
    Files.createDirectories(outputDir);
    Files.writeString(outputDir.resolve("com_example_Foo.md"), "legacy", StandardCharsets.UTF_8);

    LlmDocumentBatchProcessor.BatchResult batchResult =
        processor.generate(
            result,
            outputDir,
            new Config.LlmConfig(),
            Set.of("knownMethod"),
            ".md",
            (classInfo, llmConfig, knownMethods) -> "# " + classInfo.getFqn());

    Path alignedOutput = outputDir.resolve("src/main/java/com/example/Foo.md");

    assertThat(batchResult.totalCount()).isEqualTo(1);
    assertThat(batchResult.generatedCount()).isEqualTo(1);
    assertThat(batchResult.failedClassNames()).isEmpty();
    assertThat(Files.readString(alignedOutput, StandardCharsets.UTF_8))
        .isEqualTo("# com.example.Foo");
    assertThat(outputDir.resolve("com_example_Foo.md")).doesNotExist();
  }

  @Test
  void generate_shouldContinueWhenSingleClassRenderingFails() throws IOException {
    AnalysisResult result = new AnalysisResult();
    ClassInfo success =
        classInfo("com.example.SuccessDoc", "src/main/java/com/example/SuccessDoc.java");
    ClassInfo fail = classInfo("com.example.FailDoc", "src/main/java/com/example/FailDoc.java");
    result.setClasses(List.of(success, fail));

    Path outputDir = tempDir.resolve("reports");

    LlmDocumentBatchProcessor.BatchResult batchResult =
        processor.generate(
            result,
            outputDir,
            new Config.LlmConfig(),
            Set.of(),
            ".md",
            (classInfo, llmConfig, knownMethods) -> {
              if ("com.example.FailDoc".equals(classInfo.getFqn())) {
                throw new IllegalStateException("boom");
              }
              return "# ok";
            });

    assertThat(batchResult.totalCount()).isEqualTo(2);
    assertThat(batchResult.generatedCount()).isEqualTo(1);
    assertThat(batchResult.failedClassNames()).containsExactly("FailDoc");
    assertThat(outputDir.resolve("src/main/java/com/example/SuccessDoc.md")).exists();
    assertThat(outputDir.resolve("src/main/java/com/example/FailDoc.md")).doesNotExist();
  }

  @Test
  void generate_shouldRetainFallbackLegacyPathWhenAlignedPathEqualsLegacy() throws IOException {
    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(classInfo("com.example.LegacyOnly", null)));

    Path outputDir = tempDir.resolve("legacy");
    Path legacyPath = outputDir.resolve("com_example_LegacyOnly.md");
    Files.createDirectories(outputDir);
    Files.writeString(legacyPath, "old", StandardCharsets.UTF_8);

    processor.generate(
        result,
        outputDir,
        new Config.LlmConfig(),
        Set.of(),
        ".md",
        (classInfo, llmConfig, knownMethods) -> "# replaced");

    assertThat(Files.readString(legacyPath, StandardCharsets.UTF_8)).isEqualTo("# replaced");
  }

  @Test
  void generate_shouldRejectNullRequiredArguments() {
    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of());

    assertThatThrownBy(
            () ->
                processor.generate(
                    null,
                    tempDir,
                    new Config.LlmConfig(),
                    Set.of(),
                    ".md",
                    (classInfo, llmConfig, knownMethods) -> ""))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(
            () ->
                processor.generate(
                    result,
                    null,
                    new Config.LlmConfig(),
                    Set.of(),
                    ".md",
                    (classInfo, llmConfig, knownMethods) -> ""))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(
            () ->
                processor.generate(
                    result, tempDir, new Config.LlmConfig(), Set.of(), null, (a, b, c) -> ""))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(
            () ->
                processor.generate(result, tempDir, new Config.LlmConfig(), Set.of(), ".md", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void batchResult_shouldDefensivelyCopyFailedClassNames() {
    List<String> failed = new java.util.ArrayList<>(List.of("A"));
    LlmDocumentBatchProcessor.BatchResult batchResult =
        new LlmDocumentBatchProcessor.BatchResult(2, 1, failed);

    failed.add("B");

    assertThat(batchResult.failedClassNames()).containsExactly("A");
    assertThatThrownBy(() -> batchResult.failedClassNames().add("C"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private ClassInfo classInfo(String fqn, String filePath) {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn(fqn);
    classInfo.setFilePath(filePath);
    return classInfo;
  }
}
