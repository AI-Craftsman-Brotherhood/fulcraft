package com.craftsmanbro.fulcraft.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.ui.cli.wiring.DefaultServiceFactory;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Smoke integration test for the composite analysis pipeline (JavaParser + Spoon merged). Validates
 * the source set wiring and exercises record extraction plus cross-engine merge de-duplication.
 */
@DisplayName("Composite analysis pipeline (integration)")
class AnalysisPipelineIntegrationTest {

  @Test
  @DisplayName("analyzes a project with a class and a record via the composite engine")
  void analyzesClassAndRecord(@TempDir final Path projectRoot) throws IOException {
    writeSource(
        projectRoot,
        "com/demo/Greeter.java",
        """
        package com.demo;

        public class Greeter {
          public String greet(String name) {
            return "hi " + name;
          }
        }
        """);
    writeSource(
        projectRoot,
        "com/demo/Point.java",
        """
        package com.demo;

        public record Point(int x, int y) {
          int sum() {
            return x + y;
          }
        }
        """);

    final Config config = config(projectRoot);
    final AnalysisPort port =
        new DefaultServiceFactory(GlobalOpenTelemetry.getTracer("integration-test"))
            .createAnalysisPort("composite");

    final AnalysisResult result = port.analyze(projectRoot, config);

    final List<String> fqns = result.getClasses().stream().map(ClassInfo::getFqn).toList();
    assertThat(fqns).contains("com.demo.Greeter", "com.demo.Point");

    final ClassInfo point = classByFqn(result, "com.demo.Point");
    final List<String> methodNames = point.getMethods().stream().map(MethodInfo::getName).toList();
    assertThat(methodNames).contains("<init>", "sum", "x", "y");
    assertThat(methodNames).doesNotHaveDuplicates();
  }

  private static void writeSource(final Path root, final String relativePath, final String content)
      throws IOException {
    final Path file = root.resolve("src/main/java").resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private static Config config(final Path projectRoot) {
    final Config config = new Config();
    final Config.ProjectConfig project = new Config.ProjectConfig();
    project.setId("integration-test");
    project.setRoot(projectRoot.toString());
    project.setIncludePaths(List.of("src/main/java"));
    config.setProject(project);
    final Config.AnalysisConfig analysis = new Config.AnalysisConfig();
    analysis.setSourceRootMode("AUTO");
    analysis.setSourceRootPaths(List.of("src/main/java"));
    analysis.setLanguageLevel("JAVA_21");
    // The temp project has no build output; run Spoon without a classpath to keep the test
    // deterministic across environments.
    analysis.setSpoon(
        new com.craftsmanbro.fulcraft.plugins.analysis.config.AnalysisConfig.SpoonConfig());
    config.setAnalysis(analysis);
    return config;
  }

  private static ClassInfo classByFqn(final AnalysisResult result, final String fqn) {
    return result.getClasses().stream()
        .filter(c -> fqn.equals(c.getFqn()))
        .findFirst()
        .orElseThrow();
  }
}
