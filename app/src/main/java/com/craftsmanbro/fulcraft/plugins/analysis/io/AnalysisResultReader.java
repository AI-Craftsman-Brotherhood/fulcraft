package com.craftsmanbro.fulcraft.plugins.analysis.io;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.json.contract.JsonServicePort;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.DefaultJsonService;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic.DynamicResolutionApplier;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Loads analysis artifacts from disk.
 *
 * <p>Supports aggregated analysis.json and per-class analysis_*.json files (including nested
 * directories).
 */
public final class AnalysisResultReader {

  private static final String ANALYSIS_FILE = "analysis.json";

  private static final String ANALYSIS_PREFIX = "analysis_";

  private static final String ANALYSIS_SUFFIX = ".json";

  private static final String DYNAMIC_RESOLUTIONS_FILE = "dynamic_resolutions.jsonl";

  private final JsonServicePort jsonService;

  public AnalysisResultReader() {
    this.jsonService = new DefaultJsonService();
  }

  public Optional<AnalysisResult> readFrom(final Path analysisDir) {
    if (analysisDir == null || !Files.exists(analysisDir)) {
      return Optional.empty();
    }
    final Path aggregateFile = analysisDir.resolve(ANALYSIS_FILE);
    if (Files.isRegularFile(aggregateFile)) {
      return enrichFromDynamicResolutions(analysisDir, readSingle(aggregateFile));
    }
    try (var stream = Files.walk(analysisDir)) {
      final List<Path> files =
          stream
              .filter(Files::isRegularFile)
              .filter(
                  p -> {
                    final Path fileName = p.getFileName();
                    return fileName != null && isAnalysisShard(fileName.toString());
                  })
              .sorted(Comparator.comparing(Path::toString))
              .toList();
      if (files.isEmpty()) {
        return Optional.empty();
      }
      AnalysisResult merged = null;
      for (final Path file : files) {
        final AnalysisResult part = jsonService.readFromFile(file, AnalysisResult.class);
        if (merged == null) {
          merged = new AnalysisResult();
          merged.setProjectId(part.getProjectId());
          merged.setCommitHash(part.getCommitHash());
          merged.setAnalysisErrors(part.getAnalysisErrors());
        }
        merged.getClasses().addAll(part.getClasses());
      }
      return enrichFromDynamicResolutions(analysisDir, Optional.ofNullable(merged));
    } catch (IOException e) {
      Logger.warn(
          MessageSource.getMessage("analysis.io.reader.read_artifacts_failed", e.getMessage()));
      return Optional.empty();
    }
  }

  private Optional<AnalysisResult> readSingle(final Path file) {
    try {
      return Optional.ofNullable(jsonService.readFromFile(file, AnalysisResult.class));
    } catch (IOException e) {
      Logger.warn(
          MessageSource.getMessage("analysis.io.reader.read_file_failed", file, e.getMessage()));
      return Optional.empty();
    }
  }

  private boolean isAnalysisShard(final String fileName) {
    return fileName.startsWith(ANALYSIS_PREFIX) && fileName.endsWith(ANALYSIS_SUFFIX);
  }

  private Optional<AnalysisResult> enrichFromDynamicResolutions(
      final Path analysisDir, final Optional<AnalysisResult> loaded) {
    if (loaded.isEmpty()) {
      return loaded;
    }
    final AnalysisResult result = loaded.get();
    if (hasAnyMethodLevelDynamicResolution(result)) {
      return loaded;
    }
    final Path resolutionsPath = analysisDir.resolve(DYNAMIC_RESOLUTIONS_FILE);
    if (!Files.isRegularFile(resolutionsPath)) {
      return loaded;
    }
    final List<DynamicResolution> resolutions = readDynamicResolutions(resolutionsPath);
    if (resolutions.isEmpty()) {
      return loaded;
    }
    DynamicResolutionApplier.apply(result, resolutions);
    return Optional.of(result);
  }

  private boolean hasAnyMethodLevelDynamicResolution(final AnalysisResult result) {
    if (result == null || result.getClasses().isEmpty()) {
      return false;
    }
    for (final var classInfo : result.getClasses()) {
      if (classInfo == null) {
        continue;
      }
      for (final var method : classInfo.getMethods()) {
        if (method != null && !method.getDynamicResolutions().isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  private List<DynamicResolution> readDynamicResolutions(final Path resolutionsPath) {
    final List<DynamicResolution> resolutions = new ArrayList<>();
    try (var lines = Files.lines(resolutionsPath, StandardCharsets.UTF_8)) {
      lines.forEach(
          line -> {
            if (line == null || line.isBlank()) {
              return;
            }
            try {
              resolutions.add(jsonService.fromJson(line, DynamicResolution.class));
            } catch (IOException e) {
              Logger.warn(
                  MessageSource.getMessage(
                      "analysis.io.reader.dynamic_resolution_entry_parse_failed",
                      resolutionsPath,
                      e.getMessage()));
            }
          });
    } catch (IOException e) {
      Logger.warn(
          MessageSource.getMessage(
              "analysis.io.reader.dynamic_resolutions_load_failed",
              resolutionsPath,
              e.getMessage()));
    }
    return resolutions;
  }
}
