package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon.SpoonAnalyzer;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ClassInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClasspathResolutionIntegrationTest {

  @TempDir Path tempDir;

  @Test
  void resolvesExternalTypesWithGradleClasspath() throws Exception {
    Path projectRoot = tempDir.resolve("gradle-project");
    Files.createDirectories(projectRoot);
    Path jar = createGuavaStubJar(projectRoot.resolve("lib"));

    writeSource(
        projectRoot,
        "src/main/java/com/example/OrderService.java",
        """
            package com.example;

            import com.google.common.base.Preconditions;

            public class OrderService {
              public void validate(int count) {
                Preconditions.checkArgument(count > 0);
              }
            }
            """);

    Files.writeString(projectRoot.resolve("build.gradle"), "plugins { id 'java' }");
    writeGradleWrapper(projectRoot.resolve("gradlew"), jar);

    JavaParserAnalyzer analyzer = new JavaParserAnalyzer(GlobalOpenTelemetry.getTracer("test"));
    Config config = baseConfig("gradle");
    AnalysisResult result = analyzer.analyze(config, projectRoot);

    MethodInfo method = findMethod(result, "com.example.OrderService", "validate");
    assertTrue(
        method.getCalledMethods().stream()
            .anyMatch(m -> m.startsWith("com.google.common.base.Preconditions#checkArgument")),
        "Expected Guava Preconditions to be resolved with FQN");
  }

  @Test
  void resolvesExternalTypesWithMavenClasspath() throws Exception {
    Path projectRoot = tempDir.resolve("maven-project");
    Files.createDirectories(projectRoot);
    Path jar = createGuavaStubJar(projectRoot.resolve("lib"));

    writeSource(
        projectRoot,
        "src/main/java/com/example/OrderService.java",
        """
            package com.example;

            import com.google.common.base.Preconditions;

            public class OrderService {
              public void validate(int count) {
                Preconditions.checkArgument(count > 0);
              }
            }
            """);

    Files.writeString(
        projectRoot.resolve("pom.xml"),
        """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>sample</artifactId>
              <version>1.0.0</version>
            </project>
            """);
    writeMavenWrapper(projectRoot.resolve("mvnw"), jar);

    SpoonAnalyzer analyzer = new SpoonAnalyzer();
    Config config = baseConfig("maven");
    AnalysisResult result = analyzer.analyze(config, projectRoot);

    MethodInfo method = findMethod(result, "com.example.OrderService", "validate");
    assertTrue(
        method.getCalledMethods().stream()
            .anyMatch(m -> m.startsWith("com.google.common.base.Preconditions#checkArgument")),
        "Expected Guava Preconditions to be resolved with FQN");
  }

  private static Config baseConfig(String buildTool) {
    Config config = Config.createDefault();
    config.getProject().setId("test-project");
    config.getProject().setBuildTool(buildTool);
    Config.AnalysisConfig analysis = config.getAnalysis();
    if (analysis == null) {
      analysis = new Config.AnalysisConfig();
      config.setAnalysis(analysis);
    }
    Config.AnalysisConfig.ClasspathConfig classpath = new Config.AnalysisConfig.ClasspathConfig();
    classpath.setMode("STRICT");
    analysis.setClasspath(classpath);
    return config;
  }

  private static MethodInfo findMethod(AnalysisResult result, String classFqn, String methodName) {
    ClassInfo info =
        result.getClasses().stream()
            .filter(c -> classFqn.equals(c.getFqn()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Class not found: " + classFqn));
    return info.getMethods().stream()
        .filter(m -> methodName.equals(m.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Method not found: " + methodName));
  }

  private static Path createGuavaStubJar(Path dir) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    Assumptions.assumeTrue(compiler != null, "JDK compiler is required for this test");

    Path srcDir = dir.resolve("src");
    Path pkgDir = srcDir.resolve("com/google/common/base");
    Files.createDirectories(pkgDir);
    Path sourceFile = pkgDir.resolve("Preconditions.java");
    Files.writeString(
        sourceFile,
        """
            package com.google.common.base;

            public final class Preconditions {
              private Preconditions() {}

              public static void checkArgument(boolean expression) {
                if (!expression) {
                  throw new IllegalArgumentException();
                }
              }
            }
            """);

    Path classesDir = dir.resolve("classes");
    Files.createDirectories(classesDir);
    if (compiler == null) {
      // Should be covered by assumption, but for static analysis safety:
      throw new IllegalStateException("JDK compiler is required for this test");
    }
    int exit = compiler.run(null, null, null, "-d", classesDir.toString(), sourceFile.toString());
    if (exit != 0) {
      throw new IllegalStateException("Failed to compile stub class");
    }

    Path jarPath = dir.resolve("guava-stub.jar");
    Files.createDirectories(jarPath.getParent());
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
      Path classFile = classesDir.resolve("com/google/common/base/Preconditions.class");
      jar.putNextEntry(new JarEntry("com/google/common/base/Preconditions.class"));
      jar.write(Files.readAllBytes(classFile));
      jar.closeEntry();
    }
    return jarPath;
  }

  private static void writeSource(Path root, String relativePath, String content)
      throws IOException {
    Path file = root.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private static void writeGradleWrapper(Path gradlew, Path jarPath) throws IOException {
    Files.writeString(
        gradlew,
        """
            #!/bin/sh
            for arg in "$@"; do
              if [ "$arg" = "printClasspath" ]; then
                echo "%s"
              fi
            done
            exit 0
            """
            .formatted(jarPath.toAbsolutePath()));
    gradlew.toFile().setExecutable(true);
  }

  private static void writeMavenWrapper(Path mvnw, Path jarPath) throws IOException {
    Files.writeString(
        mvnw,
        """
            #!/bin/sh
            output=""
            for arg in "$@"; do
              case "$arg" in
                -Dmdep.outputFile=*)
                  output="${arg#-Dmdep.outputFile=}"
                  ;;
              esac
            done
            if [ -n "$output" ]; then
              echo "%s" > "$output"
            fi
            exit 0
            """
            .formatted(jarPath.toAbsolutePath()));
    mvnw.toFile().setExecutable(true);
  }
}
