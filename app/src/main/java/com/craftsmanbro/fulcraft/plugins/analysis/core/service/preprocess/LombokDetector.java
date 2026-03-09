package com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Detects Lombok usage in a project. Used to determine if delombok preprocessing is needed. */
public class LombokDetector {

  // Lombok import patterns
  private static final Pattern LOMBOK_IMPORT_PATTERN =
      Pattern.compile("^\\s*import\\s+lombok\\.", Pattern.MULTILINE);

  // Lombok dependency identifiers in build files
  private static final String LOMBOK_DEPENDENCY = "lombok";

  private static final String LOMBOK_GROUP_ID = "org.projectlombok";

  // Common Lombok annotations
  private static final long SOURCE_SCAN_LIMIT = 100L;

  private static final List<String> LOMBOK_ANNOTATIONS =
      List.of(
          "Getter",
          "Setter",
          "Data",
          "Value",
          "Builder",
          "AllArgsConstructor",
          "NoArgsConstructor",
          "RequiredArgsConstructor",
          "ToString",
          "EqualsAndHashCode",
          "Slf4j",
          "Log",
          "Log4j",
          "Log4j2",
          "CommonsLog",
          "With",
          "Cleanup",
          "SneakyThrows",
          "Synchronized");

  private static final Pattern LOMBOK_ANNOTATION_PATTERN =
      Pattern.compile("@(?:" + String.join("|", LOMBOK_ANNOTATIONS) + ")\\b");

  private static final Pattern LOMBOK_QUALIFIED_ANNOTATION_PATTERN =
      Pattern.compile("@lombok\\.");

  /**
   * Detects if Lombok is used in the project.
   *
   * @param projectRoot Project root directory
   * @return true if Lombok usage is detected
   */
  public boolean detectLombok(final Path projectRoot) {
    return detectLombok(projectRoot, List.of(projectRoot.resolve("src/main/java")));
  }

  /**
   * Detects if Lombok is used in the project.
   *
   * @param projectRoot Project root directory
   * @param sourceRoots Source roots to scan for Lombok usage
   * @return true if Lombok usage is detected
   */
  public boolean detectLombok(final Path projectRoot, final List<Path> sourceRoots) {
    // Check source files for imports/annotations
    if (detectInSources(sourceRoots)) {
      Logger.info(MessageSource.getMessage("analysis.lombok.detected.sources"));
      return true;
    }
    // Check build files for lombok dependency
    if (detectInBuildFiles(projectRoot)) {
      Logger.info(MessageSource.getMessage("analysis.lombok.detected.build"));
      return true;
    }
    Logger.debug(MessageSource.getMessage("analysis.lombok.none_detected"));
    return false;
  }

  private boolean detectInSources(final List<Path> sourceRoots) {
    if (sourceRoots == null || sourceRoots.isEmpty()) {
      return false;
    }
    for (final Path sourceRoot : sourceRoots) {
      if (sourceRoot == null || !Files.exists(sourceRoot)) {
        continue;
      }
      if (detectInSourceRoot(sourceRoot)) {
        return true;
      }
    }
    return false;
  }

  private boolean detectInSourceRoot(final Path sourceRoot) {
    try (Stream<Path> files = Files.walk(sourceRoot)) {
      return files
          .filter(p -> p.toString().endsWith(".java"))
          .sorted(Comparator.comparing(Path::toString))
          .limit(SOURCE_SCAN_LIMIT)
          .anyMatch(this::hasLombokUsage);
    } catch (IOException e) {
      Logger.debug(MessageSource.getMessage("analysis.lombok.scan_error", e.getMessage()));
      return false;
    }
  }

  private boolean hasLombokUsage(final Path javaFile) {
    try {
      final String content = Files.readString(javaFile);
      return LOMBOK_IMPORT_PATTERN.matcher(content).find()
          || LOMBOK_QUALIFIED_ANNOTATION_PATTERN.matcher(content).find()
          || LOMBOK_ANNOTATION_PATTERN.matcher(content).find();
    } catch (IOException e) {
      Logger.debug(
          MessageSource.getMessage("analysis.lombok.read_source_failed", javaFile, e.getMessage()));
    }
    return false;
  }

  private boolean detectInBuildFiles(final Path projectRoot) {
    return checkBuildFile(projectRoot, "build.gradle")
        || checkBuildFile(projectRoot, "build.gradle.kts")
        || checkBuildFile(projectRoot, "pom.xml");
  }

  private boolean checkBuildFile(final Path projectRoot, final String fileName) {
    final Path buildFile = projectRoot.resolve(fileName);
    if (!Files.exists(buildFile)) {
      return false;
    }
    try {
      final String content = Files.readString(buildFile);
      return content.contains(LOMBOK_DEPENDENCY) || content.contains(LOMBOK_GROUP_ID);
    } catch (IOException e) {
      return false;
    }
  }
}
