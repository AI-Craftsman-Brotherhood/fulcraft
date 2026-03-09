package com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class DelombokExecutorTest {

  @TempDir Path tempDir;

  @Test
  @ResourceLock(value = "user.home", mode = ResourceAccessMode.READ_WRITE)
  void execute_returnsFailure_whenLombokJarMissing() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Files.createDirectories(projectRoot);
    Path workDir = projectRoot.resolve("work");
    Path logFile = projectRoot.resolve("logs/delombok.log");

    Config.AnalysisConfig.PreprocessConfig preprocess =
        new Config.AnalysisConfig.PreprocessConfig();
    Config.AnalysisConfig.DelombokConfig delombokConfig =
        new Config.AnalysisConfig.DelombokConfig();
    delombokConfig.setLombokJarPath(tempDir.resolve("missing/lombok.jar").toString());
    preprocess.setDelombok(delombokConfig);

    String originalHome = System.getProperty("user.home");
    Path fakeHome = tempDir.resolve("fake-home");
    Files.createDirectories(fakeHome);
    System.setProperty("user.home", fakeHome.toString());
    try {
      DelombokExecutor.Result result =
          new DelombokExecutor()
              .execute(
                  projectRoot,
                  List.of(projectRoot.resolve("src/main/java")),
                  workDir,
                  preprocess,
                  logFile);

      assertThat(result.success()).isFalse();
      assertThat(result.errorMessage())
          .isEqualTo(MessageSource.getMessage("analysis.delombok.error.lombok_jar_not_found"));
    } finally {
      if (originalHome == null) {
        System.clearProperty("user.home");
      } else {
        System.setProperty("user.home", originalHome);
      }
    }
  }

  @Test
  void execute_returnsFailure_whenDelombokProcessExitsNonZero() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Path sourceRoot = projectRoot.resolve("src/main/java");
    Files.createDirectories(sourceRoot);
    Path workDir = projectRoot.resolve("work");
    Path logFile = projectRoot.resolve("logs/delombok.log");
    Files.createDirectories(logFile.getParent());

    Path lombokJar = createFakeLombokJar(projectRoot.resolve("tools"), "System.exit(7);");

    DelombokExecutor.Result result =
        new DelombokExecutor()
            .execute(
                projectRoot,
                List.of(sourceRoot),
                workDir,
                preprocessConfigWithJar(lombokJar.toString()),
                logFile);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage())
        .isEqualTo(MessageSource.getMessage("analysis.delombok.error.exit_code", 7));
  }

  @Test
  void execute_returnsFailure_whenOutputContainsNoJavaFile() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Path sourceRoot = projectRoot.resolve("src/main/java");
    Files.createDirectories(sourceRoot);
    Path workDir = projectRoot.resolve("work");
    Path logFile = projectRoot.resolve("logs/delombok.log");
    Files.createDirectories(logFile.getParent());

    Path lombokJar =
        createFakeLombokJar(projectRoot.resolve("tools"), "Files.createDirectories(outputDir);");

    DelombokExecutor.Result result =
        new DelombokExecutor()
            .execute(
                projectRoot,
                List.of(sourceRoot),
                workDir,
                preprocessConfigWithJar(lombokJar.toString()),
                logFile);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage())
        .isEqualTo(MessageSource.getMessage("analysis.delombok.error.no_output"));
  }

  @Test
  void execute_returnsSuccess_andCleansWorkDir_whenJavaOutputProduced() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Path sourceRoot = projectRoot.resolve("src/main/java");
    Files.createDirectories(sourceRoot);
    Path workDir = projectRoot.resolve("work");
    Files.createDirectories(workDir);
    Files.writeString(workDir.resolve("stale.txt"), "stale", StandardCharsets.UTF_8);
    Path logFile = projectRoot.resolve("logs/delombok.log");
    Files.createDirectories(logFile.getParent());

    Path lombokJar =
        createFakeLombokJar(
            projectRoot.resolve("tools"),
            """
            Files.createDirectories(outputDir.resolve("java"));
            Files.writeString(outputDir.resolve("java/Generated.java"), "class Generated {}\\n");
            """);

    DelombokExecutor.Result result =
        new DelombokExecutor()
            .execute(
                projectRoot,
                List.of(sourceRoot),
                workDir,
                preprocessConfigWithJar(lombokJar.toString()),
                logFile);

    assertThat(result.success()).isTrue();
    assertThat(result.outputDir()).isEqualTo(workDir);
    assertThat(result.errorMessage()).isNull();
    assertThat(Files.exists(workDir.resolve("stale.txt"))).isFalse();
    assertThat(Files.exists(workDir.resolve("java/Generated.java"))).isTrue();
  }

  @Test
  void execute_usesRelativeJarPathFromProjectRoot_whenConfigured() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Path sourceRoot = projectRoot.resolve("src/main/java");
    Files.createDirectories(sourceRoot);
    Path workDir = projectRoot.resolve("work");
    Path logFile = projectRoot.resolve("logs/delombok.log");
    Files.createDirectories(logFile.getParent());

    Path lombokJar =
        createFakeLombokJar(projectRoot.resolve("tools"), "Files.createDirectories(outputDir);");
    String relativeJarPath = projectRoot.relativize(lombokJar).toString();

    DelombokExecutor.Result result =
        new DelombokExecutor()
            .execute(
                projectRoot,
                List.of(sourceRoot),
                workDir,
                preprocessConfigWithJar(relativeJarPath),
                logFile);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage())
        .isEqualTo(MessageSource.getMessage("analysis.delombok.error.no_output"));
  }

  @Test
  void execute_usesProjectLocalLombokJar_whenPathNotConfigured() throws IOException {
    Path projectRoot = tempDir.resolve("project");
    Path sourceRoot = projectRoot.resolve("src/main/java");
    Files.createDirectories(sourceRoot);
    Path workDir = projectRoot.resolve("work");
    Path logFile = projectRoot.resolve("logs/delombok.log");
    Files.createDirectories(logFile.getParent());

    createFakeLombokJar(projectRoot, "Files.createDirectories(outputDir);");

    Config.AnalysisConfig.PreprocessConfig preprocess =
        new Config.AnalysisConfig.PreprocessConfig();

    DelombokExecutor.Result result =
        new DelombokExecutor()
            .execute(projectRoot, List.of(sourceRoot), workDir, preprocess, logFile);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage())
        .isEqualTo(MessageSource.getMessage("analysis.delombok.error.no_output"));
  }

  private static Config.AnalysisConfig.PreprocessConfig preprocessConfigWithJar(String jarPath) {
    Config.AnalysisConfig.PreprocessConfig preprocess =
        new Config.AnalysisConfig.PreprocessConfig();
    Config.AnalysisConfig.DelombokConfig delombokConfig =
        new Config.AnalysisConfig.DelombokConfig();
    delombokConfig.setLombokJarPath(jarPath);
    preprocess.setDelombok(delombokConfig);
    return preprocess;
  }

  private static Path createFakeLombokJar(Path dir, String body) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    Assumptions.assumeTrue(compiler != null, "JDK compiler is required for this test");
    if (compiler == null) {
      throw new IllegalStateException("JDK compiler is required for this test");
    }

    Files.createDirectories(dir);
    Path srcDir = dir.resolve("src");
    Files.createDirectories(srcDir);
    Path sourceFile = srcDir.resolve("FakeDelombok.java");
    Files.writeString(
        sourceFile,
        """
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.nio.file.Paths;

            public class FakeDelombok {
              public static void main(String[] args) throws Exception {
                Path outputDir = null;
                for (int i = 0; i < args.length - 1; i++) {
                  if ("-d".equals(args[i])) {
                    outputDir = Paths.get(args[i + 1]);
                  }
                }
                if (outputDir == null) {
                  throw new IllegalArgumentException("Missing -d option");
                }
            %s
              }
            }
            """
            .formatted(body),
        StandardCharsets.UTF_8);

    Path classesDir = dir.resolve("classes");
    Files.createDirectories(classesDir);
    int exit = compiler.run(null, null, null, "-d", classesDir.toString(), sourceFile.toString());
    if (exit != 0) {
      throw new IllegalStateException("Failed to compile fake lombok jar");
    }

    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "FakeDelombok");

    Path jarPath = dir.resolve("lombok.jar");
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      Path classFile = classesDir.resolve("FakeDelombok.class");
      jar.putNextEntry(new JarEntry("FakeDelombok.class"));
      jar.write(Files.readAllBytes(classFile));
      jar.closeEntry();
    }
    return jarPath;
  }
}
