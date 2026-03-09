package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaParserAnalyzerEdgeCaseTest {

  @TempDir Path projectRoot;

  @Test
  void shouldFilterSources_whenIncludePathsProvided() throws Exception {
    writeSource(
        "src/main/java/com/example/include/Keep.java",
        """
        package com.example.include;
        public class Keep {}
        """);
    writeSource(
        "src/main/java/com/example/exclude/Drop.java",
        """
        package com.example.exclude;
        public class Drop {}
        """);

    JavaParserAnalyzer analyzer = new JavaParserAnalyzer(GlobalOpenTelemetry.getTracer("test"));
    Config config = new Config();
    Config.ProjectConfig project = new Config.ProjectConfig();
    project.setId("proj");
    project.setCommit("c");
    project.setIncludePaths(List.of("com/example/include"));
    config.setProject(project);

    AnalysisResult result = analyzer.analyze(config, projectRoot);

    assertTrue(
        result.getClasses().stream().anyMatch(c -> "com.example.include.Keep".equals(c.getFqn())));
    assertTrue(
        result.getClasses().stream().noneMatch(c -> "com.example.exclude.Drop".equals(c.getFqn())));
  }

  @Test
  void shouldRecordAnalysisError_whenParseFails() throws Exception {
    writeSource(
        "src/main/java/com/example/Broken.java",
        "package com.example; public class Broken { void m( }");

    JavaParserAnalyzer analyzer = new JavaParserAnalyzer(GlobalOpenTelemetry.getTracer("test"));
    Config config = new Config();
    Config.ProjectConfig project = new Config.ProjectConfig();
    project.setId("proj");
    project.setCommit("c");
    config.setProject(project);

    AnalysisResult result = analyzer.analyze(config, projectRoot);

    assertFalse(result.getAnalysisErrors().isEmpty());
    assertTrue(
        result.getAnalysisErrors().stream()
            .anyMatch(e -> "src/main/java/com/example/Broken.java".equals(e.filePath())));
  }

  private void writeSource(String relativePath, String content) throws Exception {
    Path path = projectRoot.resolve(relativePath);
    Files.createDirectories(path.getParent());
    Files.writeString(path, content);
  }
}
