package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Handles class-by-class LLM document file generation and accounting. */
public final class LlmDocumentBatchProcessor {

  public BatchResult generate(
      final AnalysisResult result,
      final Path outputDir,
      final Config.LlmConfig llmConfig,
      final Set<String> crossClassKnownMethodNames,
      final String extension,
      final ClassDocumentRenderer renderer)
      throws IOException {
    Objects.requireNonNull(
        result,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "result must not be null"));
    Objects.requireNonNull(
        outputDir,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "outputDir must not be null"));
    Objects.requireNonNull(
        renderer,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "renderer must not be null"));
    Objects.requireNonNull(
        extension,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "extension must not be null"));
    Files.createDirectories(outputDir);
    int processedCount = 0;
    int generatedCount = 0;
    final int totalCount = result.getClasses().size();
    final List<String> failedClassNames = new ArrayList<>();
    for (final ClassInfo classInfo : result.getClasses()) {
      processedCount++;
      final String simpleName = DocumentUtils.getSimpleName(classInfo.getFqn());
      Logger.info(
          MessageSource.getMessage(
              "report.docs.llm.processing", processedCount, totalCount, simpleName));
      try {
        final String document = renderer.generate(classInfo, llmConfig, crossClassKnownMethodNames);
        final String relativePath =
            DocumentUtils.generateSourceAlignedReportPath(classInfo, extension);
        final Path outputFile = outputDir.resolve(relativePath);
        final Path parent = outputFile.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        Files.writeString(outputFile, document, StandardCharsets.UTF_8);
        removeLegacyFlatOutput(outputDir, classInfo, extension, outputFile);
        generatedCount++;
      } catch (Exception e) {
        failedClassNames.add(simpleName);
        Logger.warn(MessageSource.getMessage("report.docs.llm.failed", simpleName, e.getMessage()));
      }
    }
    return new BatchResult(totalCount, generatedCount, failedClassNames);
  }

  private static void removeLegacyFlatOutput(
      final Path outputDir,
      final ClassInfo classInfo,
      final String extension,
      final Path resolvedOutputFile)
      throws IOException {
    final String legacyName = DocumentUtils.generateFileName(classInfo.getFqn(), extension);
    final Path legacyPath = outputDir.resolve(legacyName);
    if (legacyPath.equals(resolvedOutputFile)) {
      return;
    }
    Files.deleteIfExists(legacyPath);
  }

  @FunctionalInterface
  public interface ClassDocumentRenderer {

    String generate(
        ClassInfo classInfo, Config.LlmConfig llmConfig, Set<String> crossClassKnownMethodNames);
  }

  public record BatchResult(int totalCount, int generatedCount, List<String> failedClassNames) {

    public BatchResult {
      failedClassNames = List.copyOf(failedClassNames);
    }
  }
}
