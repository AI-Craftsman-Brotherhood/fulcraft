package com.craftsmanbro.fulcraft.plugins.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PluginLayerDependencyGuardTest {

  private static final Path PLUGIN_ROOT =
      Path.of("src/main/java/com/craftsmanbro/fulcraft/plugins");
  private static final List<String> CORE_PLUGIN_PATH_PREFIXES =
      Arrays.asList("analysis/", "reporting/", "document/", "exploration/");
  private static final Path SHARED_BRITTLE_SUMMARY_FILE =
      PLUGIN_ROOT.resolve("shared/model/BrittleTestSummary.java");

  private static final Set<String> ALLOWED_INFRA_TASKIO_IMPORT_PATHS =
      Set.of("reporting/taskio/DefaultTaskEntriesSource.java");

  @Test
  void pluginLayerDoesNotImportJUnitSuitePackagesOutsideApprovedContracts() throws IOException {
    List<String> violations =
        findViolations(
            line ->
                line.contains("import com.craftsmanbro.fulcraft.plugins.junit.")
                    && !line.contains(
                        "import com.craftsmanbro.fulcraft.plugins.junit.suite.report."));

    assertTrue(
        violations.isEmpty(),
        () ->
            "plugin layer must not import junit suite packages outside approved reporting contracts:\n"
                + String.join("\n", violations));
  }

  @Test
  void pluginLayerOnlyUsesInfrastructureTaskIoImportsViaDedicatedAdapter() throws IOException {
    List<String> violations =
        findViolations(
            line ->
                line.contains(
                        "import com.craftsmanbro.fulcraft.infrastructure.io.model.TasksFileEntry")
                    || line.contains(
                        "import com.craftsmanbro.fulcraft.infrastructure.io.contract.TasksFileFormat")
                    || line.contains(
                        "import com.craftsmanbro.fulcraft.infrastructure.io.contract.TasksFileReader")
                    || line.contains(
                        "import com.craftsmanbro.fulcraft.infrastructure.io.impl.TasksFileFormatFactory")
                    || line.contains(
                        "import com.craftsmanbro.fulcraft.infrastructure.io.impl.JsonlTasksFileFormat"));

    assertTrue(
        violations.isEmpty(),
        () ->
            "plugin layer must access infrastructure task I/O only via reporting adapter:\n"
                + String.join("\n", violations));
  }

  @Test
  void pluginSharedDoesNotContainBrittleTestSummaryModel() {
    assertTrue(
        Files.notExists(SHARED_BRITTLE_SUMMARY_FILE),
        () -> "plugins/shared must not define JUnit-specific BrittleTestSummary model");
  }

  @Test
  void pluginLayerDoesNotReferenceBrittleTestSummarySymbol() throws IOException {
    List<String> violations = findViolations(line -> line.contains("BrittleTestSummary"));

    assertTrue(
        violations.isEmpty(),
        () ->
            "plugin layer must not reference JUnit-specific BrittleTestSummary symbol:\n"
                + String.join("\n", violations));
  }

  private List<String> findViolations(LinePredicate predicate) throws IOException {
    List<String> violations = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(PLUGIN_ROOT)) {
      stream
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(
              file -> {
                String relativePath = PLUGIN_ROOT.relativize(file).toString().replace('\\', '/');
                if (!isCorePluginPath(relativePath)) {
                  return;
                }
                if (ALLOWED_INFRA_TASKIO_IMPORT_PATHS.contains(relativePath)) {
                  return;
                }
                collectViolations(file, relativePath, predicate, violations);
              });
    }
    return violations;
  }

  private static void collectViolations(
      Path file, String relativePath, LinePredicate predicate, List<String> violations) {
    try {
      List<String> lines = Files.readAllLines(file);
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        if (predicate.matches(line)) {
          violations.add(relativePath + ":" + (i + 1) + " -> " + line.trim());
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read " + file, e);
    }
  }

  private static boolean isCorePluginPath(final String relativePath) {
    for (final String prefix : CORE_PLUGIN_PATH_PREFIXES) {
      if (relativePath.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  @FunctionalInterface
  private interface LinePredicate {
    boolean matches(String line);
  }
}
