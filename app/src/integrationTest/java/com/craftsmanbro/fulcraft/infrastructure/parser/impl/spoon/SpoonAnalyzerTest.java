package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ClassInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpoonAnalyzerTest {

  @TempDir Path projectRoot;

  @Test
  void returnsErrorWhenProjectRootMissing() {
    SpoonAnalyzer analyzer = new SpoonAnalyzer();
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    AnalysisResult result = analyzer.analyze(projectRoot.resolve("missing"), config);

    assertTrue(result.getClasses().isEmpty(), "Classes should be empty when root is missing");
    assertEquals(1, result.getAnalysisErrors().size(), "Should report missing project root");
    assertTrue(result.getAnalysisErrors().get(0).message().contains("not found"));
  }

  @Test
  void returnsErrorWhenSourceDirectoryMissing() throws Exception {
    Path emptyRoot = projectRoot.resolve("empty-project");
    Files.createDirectories(emptyRoot);

    SpoonAnalyzer analyzer = new SpoonAnalyzer();
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    AnalysisResult result = analyzer.analyze(emptyRoot, config);

    assertTrue(
        result.getClasses().isEmpty(), "Classes should be empty when source directory is missing");
    assertEquals(1, result.getAnalysisErrors().size(), "Should report missing source directory");
    assertTrue(result.getAnalysisErrors().get(0).message().contains("Source directory not found"));
  }

  @Test
  void collectsClassAndMethodInfoIncludingAnonymous() throws Exception {
    writeSource(
        "src/main/java/com/example/Sample.java",
        """
                        package com.example;

                        public class Sample {
                            private int value;

                            public Sample(int value) {
                                this.value = value;
                            }

                            public int add(int x) {
                                if (x > 0) {
                                    return value + x;
                                }
                                return value;
                            }

                            void helper() {
                                Runnable r = new Runnable() {
                                    @Override
                                    public void run() { }
                                };
                                r.run();
                            }
                        }
                        """);

    SpoonAnalyzer analyzer = new SpoonAnalyzer();
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    AnalysisResult result = analyzer.analyze(projectRoot, config);

    ClassInfo sample =
        result.getClasses().stream()
            .filter(c -> "com.example.Sample".equals(c.getFqn()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Sample class not analyzed"));

    assertFalse(sample.isInterface());
    assertEquals("com/example/Sample.java", sample.getFilePath());
    assertEquals(1, sample.getFields().size(), "Expected one field");

    MethodInfo add =
        sample.getMethods().stream()
            .filter(m -> "add".equals(m.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Method add not captured"));
    assertTrue(add.getCyclomaticComplexity() >= 2, "add should count the if branch");
    assertFalse(add.isDeadCode(), "public method should not be marked dead");

    MethodInfo helper =
        sample.getMethods().stream()
            .filter(m -> "helper".equals(m.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Method helper not captured"));
    assertTrue(helper.isDeadCode(), "package-private helper with no callers should be dead code");

    boolean hasAnonymous = result.getClasses().stream().anyMatch(c -> c.isAnonymous());
    assertTrue(hasAnonymous, "Anonymous inner class should be captured");
  }

  @Test
  void respectsConfiguredIncludePaths() throws Exception {
    writeSource(
        "src/main/java/com/example/include/Keep.java",
        """
                        package com.example.include;
                        public class Keep {}
                        """);
    writeSource(
        "src/main/java/com/example/excluded/Ignore.java",
        """
                        package com.example.excluded;
                        public class Ignore {}
                        """);

    SpoonAnalyzer analyzer = new SpoonAnalyzer();
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    config.getProject().setIncludePaths(List.of("src/main/java/com/example/include"));
    AnalysisResult result = analyzer.analyze(projectRoot, config);

    assertTrue(
        result.getClasses().stream().anyMatch(c -> "com.example.include.Keep".equals(c.getFqn())),
        "Class under include path should be analyzed");
    assertTrue(
        result.getClasses().stream()
            .noneMatch(c -> "com.example.excluded.Ignore".equals(c.getFqn())),
        "Class outside include path should be skipped");
  }

  @Test
  void respectsConfiguredExcludePaths() throws Exception {
    writeSource(
        "src/main/java/com/example/Keep.java",
        """
                        package com.example;
                        public class Keep {}
                        """);
    writeSource(
        "src/main/java/com/example/excluded/Ignore.java",
        """
                        package com.example.excluded;
                        public class Ignore {}
                        """);

    SpoonAnalyzer analyzer = new SpoonAnalyzer();
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    config.getProject().setExcludePaths(List.of("src/main/java/com/example/excluded"));
    AnalysisResult result = analyzer.analyze(projectRoot, config);

    assertTrue(
        result.getClasses().stream().anyMatch(c -> "com.example.Keep".equals(c.getFqn())),
        "Included class should be analyzed");
    assertTrue(
        result.getClasses().stream()
            .noneMatch(c -> "com.example.excluded.Ignore".equals(c.getFqn())),
        "Excluded class should be skipped");
  }

  @Test
  void supportsRecognizesKnownSourceLayouts() throws Exception {
    SpoonAnalyzer analyzer = new SpoonAnalyzer();

    assertFalse(analyzer.supports(null));
    assertFalse(analyzer.supports(projectRoot.resolve("missing")));

    Path plainRoot = projectRoot.resolve("plain");
    Files.createDirectories(plainRoot);
    assertFalse(analyzer.supports(plainRoot));

    Path mavenRoot = projectRoot.resolve("maven");
    Files.createDirectories(mavenRoot.resolve("src/main/java"));
    assertTrue(analyzer.supports(mavenRoot));

    Path appRoot = projectRoot.resolve("app-project");
    Files.createDirectories(appRoot.resolve("app/src/main/java"));
    assertTrue(analyzer.supports(appRoot));

    Path srcRoot = projectRoot.resolve("src-project");
    Files.createDirectories(srcRoot.resolve("src"));
    assertTrue(analyzer.supports(srcRoot));
  }

  @Test
  void returnedAnalysisResultIsFrozen() throws Exception {
    writeSource(
        "src/main/java/com/example/Foo.java",
        """
                        package com.example;
                        public class Foo { public void a() {} }
                        """);
    SpoonAnalyzer analyzer = new SpoonAnalyzer();
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());
    AnalysisResult result = analyzer.analyze(projectRoot, config);

    List<ClassInfo> classes = result.getClasses();
    ClassInfo newClass = new ClassInfo();
    assertThrows(UnsupportedOperationException.class, () -> classes.add(newClass));
    ClassInfo foo = result.getClasses().get(0);
    List<MethodInfo> methods = foo.getMethods();
    MethodInfo newMethod = new MethodInfo();
    assertThrows(UnsupportedOperationException.class, () -> methods.add(newMethod));
  }

  @Test
  void analyzeRejectsNullArguments() {
    SpoonAnalyzer analyzer = new SpoonAnalyzer();
    Config config = new Config();
    config.setProject(new Config.ProjectConfig());

    assertThrows(NullPointerException.class, () -> analyzer.analyze(null, config));
    assertThrows(NullPointerException.class, () -> analyzer.analyze(projectRoot, null));
  }

  private void writeSource(String relativePath, String content) throws Exception {
    Path path = projectRoot.resolve(relativePath);
    Files.createDirectories(path.getParent());
    Files.writeString(path, content);
  }
}
