package com.craftsmanbro.fulcraft.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class InfrastructureKernelDependencyGuardTest {

  private static final Path INFRA_ROOT =
      Path.of("src/main/java/com/craftsmanbro/fulcraft/infrastructure");

  @Test
  void infrastructureLayerDoesNotImportKernelPackages() throws IOException {
    List<String> violations = findViolations("import com.craftsmanbro.fulcraft.kernel.");

    assertTrue(
        violations.isEmpty(),
        () ->
            "infrastructure layer must not import kernel packages:\n"
                + String.join("\n", violations));
  }

  @Test
  void infrastructureLayerDoesNotImportCoreFeaturePluginPackages() throws IOException {
    List<String> violations = new ArrayList<>();
    violations.addAll(findViolations("import com.craftsmanbro.fulcraft.plugins.analysis."));
    violations.addAll(findViolations("import com.craftsmanbro.fulcraft.plugins.reporting."));
    violations.addAll(findViolations("import com.craftsmanbro.fulcraft.plugins.document."));
    violations.addAll(findViolations("import com.craftsmanbro.fulcraft.plugins.exploration."));

    assertTrue(
        violations.isEmpty(),
        () ->
            "infrastructure layer must not import core feature plugin packages:\n"
                + String.join("\n", violations));
  }

  private List<String> findViolations(String forbiddenImport) throws IOException {
    List<String> violations = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(INFRA_ROOT)) {
      stream
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(file -> collectViolations(file, forbiddenImport, violations));
    }
    return violations;
  }

  private void collectViolations(Path file, String forbiddenImport, List<String> violations) {
    try {
      List<String> lines = Files.readAllLines(file);
      String relativePath = INFRA_ROOT.relativize(file).toString().replace('\\', '/');
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        if (line.contains(forbiddenImport)) {
          violations.add(relativePath + ":" + (i + 1) + " -> " + line.trim());
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read " + file, e);
    }
  }
}
