package com.craftsmanbro.fulcraft.plugins.analysis.core;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser.JavaParserAnalysisAdapter;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyzerTest {

  @TempDir Path projectRoot;

  @Test
  void throwsWhenProjectRootMissing() {
    JavaParserAnalysisAdapter analyzer =
        new JavaParserAnalysisAdapter(GlobalOpenTelemetry.getTracer("test"));
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    // Analyzer.analyze(Config, Path) throws IllegalArgumentException if projectRoot
    // is null (checked before)
    // or nothing if it doesn't exist?
    // Files.walk will throw IOException if root doesn't exist.
    assertThrows(IOException.class, () -> analyzer.analyze(config, projectRoot.resolve("missing")));
  }

  @Test
  void extractsClassMethodFieldsAndFlagsDeadCode() throws Exception {
    writeSource(
        "src/main/java/com/example/Sample.java",
        """
                        package com.example;

                        import java.util.List;

                        public class Sample {
                            private int value;

                            public Sample(int v) {
                                this.value = v;
                                helper();
                            }

                            public int add(int x) {
                                if (x > 0) {
                                    return value + x;
                                }
                                return value;
                            }

                            void helper() {
                                add(1);
                            }
                        }
                        """);

    JavaParserAnalysisAdapter analyzer =
        new JavaParserAnalysisAdapter(GlobalOpenTelemetry.getTracer("test"));
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    AnalysisResult result = analyzer.analyze(config, projectRoot);

    ClassInfo sample =
        result.getClasses().stream()
            .filter(c -> "com.example.Sample".equals(c.getFqn()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Sample class not found"));

    assertEquals("src/main/java/com/example/Sample.java", sample.getFilePath());
    assertFalse(sample.isInterface());
    assertEquals(1, sample.getFields().size(), "Should capture field metadata");

    MethodInfo add =
        sample.getMethods().stream()
            .filter(m -> "add".equals(m.getName()))
            .findFirst()
            .orElseThrow();
    assertTrue(add.getCyclomaticComplexity() >= 2, "add should count branching");
    assertFalse(add.isDeadCode(), "public method should not be dead");

    MethodInfo helper =
        sample.getMethods().stream()
            .filter(m -> "helper".equals(m.getName()))
            .findFirst()
            .orElseThrow();
    assertFalse(helper.isDeadCode(), "helper is package-private but has incoming call");
  }

  @Test
  void marksMethodsDeadWhenNoIncomingAndNonPublic() throws Exception {
    writeSource(
        "src/main/java/com/example/Lonely.java",
        """
                        package com.example;
                        class Lonely {
                            void unused() {}
                        }
                        """);

    JavaParserAnalysisAdapter analyzer =
        new JavaParserAnalysisAdapter(GlobalOpenTelemetry.getTracer("test"));
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    AnalysisResult result = analyzer.analyze(config, projectRoot);

    MethodInfo unused =
        result.getClasses().stream()
            .filter(c -> "com.example.Lonely".equals(c.getFqn()))
            .flatMap(c -> c.getMethods().stream())
            .filter(m -> "unused".equals(m.getName()))
            .findFirst()
            .orElseThrow();

    assertTrue(unused.isDeadCode(), "Non-public, uncalled method should be marked dead");
  }

  @Test
  void respectsExcludePaths() throws Exception {
    writeSource(
        "src/main/java/com/example/Keep.java",
        """
                        package com.example;
                        public class Keep {}
                        """);
    writeSource(
        "src/main/java/com/example/excluded/Skip.java",
        """
                        package com.example.excluded;
                        public class Skip {}
                        """);

    JavaParserAnalysisAdapter analyzer =
        new JavaParserAnalysisAdapter(GlobalOpenTelemetry.getTracer("test"));
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    config.getProject().setExcludePaths(List.of("src/main/java/com/example/excluded"));
    AnalysisResult result = analyzer.analyze(config, projectRoot);

    assertTrue(
        result.getClasses().stream().anyMatch(c -> "com.example.Keep".equals(c.getFqn())),
        "Included class should be analyzed");
    assertTrue(
        result.getClasses().stream().noneMatch(c -> "com.example.excluded.Skip".equals(c.getFqn())),
        "Excluded class should be skipped");
  }

  @Test
  void returnedAnalysisResultIsFrozen() throws Exception {
    writeSource(
        "src/main/java/com/example/Foo.java",
        """
                        package com.example;
                        public class Foo { public void a() {} }
                        """);
    JavaParserAnalysisAdapter analyzer =
        new JavaParserAnalysisAdapter(GlobalOpenTelemetry.getTracer("test"));
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    AnalysisResult result = analyzer.analyze(config, projectRoot);

    List<ClassInfo> classes = result.getClasses();
    ClassInfo newClassInfo = new ClassInfo();
    assertThrows(UnsupportedOperationException.class, () -> classes.add(newClassInfo));
    ClassInfo foo = result.getClasses().get(0);
    List<MethodInfo> methods = foo.getMethods();
    MethodInfo newMethodInfo = new MethodInfo();
    assertThrows(UnsupportedOperationException.class, () -> methods.add(newMethodInfo));
  }

  private void writeSource(String relativePath, String content) throws Exception {
    Path path = projectRoot.resolve(relativePath);
    Files.createDirectories(path.getParent());
    Files.writeString(path, content);
  }
}
