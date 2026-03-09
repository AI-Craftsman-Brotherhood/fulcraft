package com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourcePreprocessorTest {

  @TempDir Path tempDir;

  private SourcePreprocessor preprocessor;
  private Locale originalLocale;

  @BeforeEach
  void setUp() {
    originalLocale = MessageSource.getLocale();
    MessageSource.setLocale(Locale.ENGLISH);
    preprocessor = new SourcePreprocessor();
  }

  @AfterEach
  void tearDown() {
    MessageSource.setLocale(originalLocale);
  }

  @Test
  void preprocess_skipsWhenModeOff() {
    Config config = configWith(preprocessConfig("OFF", "AUTO", null));
    List<Path> roots = List.of(tempDir.resolve("src/main/java"));

    SourcePreprocessor.Result result = preprocessor.preprocess(tempDir, roots, config, tempDir);

    assertThat(result.getStatus()).isEqualTo(SourcePreprocessor.Status.SKIPPED);
    assertThat(result.getToolUsed()).isNull();
    assertThat(result.getSourceRootsBefore()).containsExactlyElementsOf(roots);
    assertThat(result.getSourceRootsAfter()).containsExactlyElementsOf(roots);
    assertThat(result.getDurationMs()).isZero();
  }

  @Test
  void preprocess_skipsWhenDelombokDisabled() {
    Config.AnalysisConfig.DelombokConfig delombokConfig =
        new Config.AnalysisConfig.DelombokConfig();
    delombokConfig.setEnabled(false);
    Config config = configWith(preprocessConfig("STRICT", "DELOMBOK", delombokConfig));
    List<Path> roots = List.of(tempDir.resolve("src/main/java"));

    SourcePreprocessor.Result result = preprocessor.preprocess(tempDir, roots, config, tempDir);

    assertThat(result.getStatus()).isEqualTo(SourcePreprocessor.Status.SKIPPED);
    assertThat(result.getToolUsed()).isNull();
  }

  @Test
  void preprocess_skipsWhenAutoModeAndNoLombokDetected() throws IOException {
    Path sourceRoot = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(sourceRoot);
    Files.writeString(
        sourceRoot.resolve("Plain.java"),
        "package com.example;\n" + "public class Plain {}\n",
        StandardCharsets.UTF_8);

    Config config = configWith(preprocessConfig("AUTO", "AUTO", null));
    List<Path> roots = List.of(tempDir.resolve("src/main/java"));

    SourcePreprocessor.Result result = preprocessor.preprocess(tempDir, roots, config, tempDir);

    assertThat(result.getStatus()).isEqualTo(SourcePreprocessor.Status.SKIPPED);
    assertThat(result.getToolUsed()).isNull();
  }

  @Test
  void preprocess_returnsSkipped_whenToolIsUnknown() {
    Config config = configWith(preprocessConfig("STRICT", "UNKNOWN_TOOL", null));
    List<Path> roots = List.of(tempDir.resolve("src/main/java"));

    SourcePreprocessor.Result result = preprocessor.preprocess(tempDir, roots, config, tempDir);

    assertThat(result.getStatus()).isEqualTo(SourcePreprocessor.Status.SKIPPED);
    assertThat(result.getToolUsed()).isNull();
  }

  @Test
  void preprocess_returnsFailed_whenStrictModeAndDelombokFails() throws IOException {
    Path sourceRoot = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(sourceRoot);
    Files.writeString(
        sourceRoot.resolve("Sample.java"),
        "package com.example; class Sample {}",
        StandardCharsets.UTF_8);

    Path lombokJar = createFakeLombokJar(tempDir.resolve("strict-fail"), "System.exit(3);");
    Config config = configWith(preprocessConfig("STRICT", "DELOMBOK", delombokWithJar(lombokJar)));
    List<Path> roots = List.of(tempDir.resolve("src/main/java"));

    SourcePreprocessor.Result result =
        preprocessor.preprocess(tempDir, roots, config, tempDir.resolve("analysis"));

    assertThat(result.getStatus()).isEqualTo(SourcePreprocessor.Status.FAILED);
    assertThat(result.getToolUsed()).isEqualTo("DELOMBOK");
    assertThat(result.getFailureReason())
        .isEqualTo(MessageSource.getMessage("analysis.delombok.error.exit_code", 3));
  }

  @Test
  void preprocess_returnsFailedFallback_whenAutoModeAndDelombokFails() throws IOException {
    Path sourceRoot = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(sourceRoot);
    Files.writeString(
        sourceRoot.resolve("Sample.java"),
        "package com.example;\n"
            + "\n"
            + "import lombok.Getter;\n"
            + "\n"
            + "@Getter\n"
            + "class Sample { private String value; }\n",
        StandardCharsets.UTF_8);

    Path lombokJar = createFakeLombokJar(tempDir.resolve("auto-fail"), "System.exit(5);");
    Config config = configWith(preprocessConfig("AUTO", "DELOMBOK", delombokWithJar(lombokJar)));
    List<Path> roots = List.of(tempDir.resolve("src/main/java"));

    SourcePreprocessor.Result result =
        preprocessor.preprocess(tempDir, roots, config, tempDir.resolve("analysis"));

    assertThat(result.getStatus()).isEqualTo(SourcePreprocessor.Status.FAILED_FALLBACK);
    assertThat(result.getToolUsed()).isEqualTo("DELOMBOK");
    assertThat(result.getFailureReason())
        .isEqualTo(MessageSource.getMessage("analysis.delombok.error.exit_code", 5));
  }

  @Test
  void preprocess_returnsSuccess_whenDelombokSucceeds() throws IOException {
    Path sourceRoot = tempDir.resolve("src/generated");
    Files.createDirectories(sourceRoot);
    Files.writeString(
        sourceRoot.resolve("Sample.java"),
        "package com.example;\nclass Sample {}\n",
        StandardCharsets.UTF_8);

    Path lombokJar =
        createFakeLombokJar(
            tempDir.resolve("success"),
            """
            Files.createDirectories(outputDir.resolve("generated"));
            Files.writeString(outputDir.resolve("generated/Generated.java"), "class Generated {}\\n");
            """);
    Config.AnalysisConfig.DelombokConfig delombokConfig = delombokWithJar(lombokJar);
    Config.AnalysisConfig.PreprocessConfig preprocess =
        preprocessConfig("STRICT", "DELOMBOK", delombokConfig);
    preprocess.setIncludeGenerated(false);
    Config config = configWith(preprocess);

    SourcePreprocessor.Result result =
        preprocessor.preprocess(
            tempDir,
            List.of(tempDir.resolve("src/generated")),
            config,
            tempDir.resolve("analysis"));

    assertThat(result.getStatus()).isEqualTo(SourcePreprocessor.Status.SUCCESS);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.shouldUsePreprocessed()).isTrue();
    assertThat(result.getToolUsed()).isEqualTo("DELOMBOK");
    assertThat(result.getSourceRootsAfter()).contains(tempDir.resolve(".utg/preprocess"));
    assertThat(result.getSourceRootsAfter())
        .noneMatch(
            path ->
                path.getFileName() != null && "generated".equals(path.getFileName().toString()));
  }

  @Test
  void preprocess_includesGeneratedRoot_whenConfigured() throws IOException {
    Path sourceRoot = tempDir.resolve("src/generated");
    Files.createDirectories(sourceRoot);
    Files.writeString(
        sourceRoot.resolve("Sample.java"),
        "package com.example;\nclass Sample {}\n",
        StandardCharsets.UTF_8);

    Path lombokJar =
        createFakeLombokJar(
            tempDir.resolve("include-generated"),
            """
            Files.createDirectories(outputDir.resolve("generated"));
            Files.writeString(outputDir.resolve("generated/Generated.java"), "class Generated {}\\n");
            """);
    Config.AnalysisConfig.DelombokConfig delombokConfig = delombokWithJar(lombokJar);
    Config.AnalysisConfig.PreprocessConfig preprocess =
        preprocessConfig("STRICT", "DELOMBOK", delombokConfig);
    preprocess.setIncludeGenerated(true);
    Config config = configWith(preprocess);

    SourcePreprocessor.Result result =
        preprocessor.preprocess(
            tempDir,
            List.of(tempDir.resolve("src/generated")),
            config,
            tempDir.resolve("analysis"));

    assertThat(result.getStatus()).isEqualTo(SourcePreprocessor.Status.SUCCESS);
    assertThat(result.getSourceRootsAfter()).contains(tempDir.resolve(".utg/preprocess/generated"));
  }

  @Test
  void resultToMap_includesFailureReasonOnlyWhenPresent() {
    SourcePreprocessor.Result failed =
        new SourcePreprocessor.Result(
            SourcePreprocessor.Status.FAILED,
            List.of(tempDir.resolve("before")),
            List.of(tempDir.resolve("after")),
            "DELOMBOK",
            "boom",
            12);

    Map<String, Object> failedMap = failed.toMap("STRICT", tempDir);

    assertThat(failedMap).containsEntry("failure_reason", "boom");
    assertThat(failedMap).containsEntry("tool_used", "DELOMBOK");

    SourcePreprocessor.Result ok =
        new SourcePreprocessor.Result(
            SourcePreprocessor.Status.SUCCESS,
            List.of(tempDir.resolve("before")),
            List.of(tempDir.resolve("after")),
            "DELOMBOK",
            null,
            12);

    Map<String, Object> okMap = ok.toMap("AUTO", tempDir);

    assertThat(okMap).doesNotContainKey("failure_reason");
  }

  private Config configWith(Config.AnalysisConfig.PreprocessConfig preprocess) {
    Config config = new Config();
    Config.AnalysisConfig analysisConfig = new Config.AnalysisConfig();
    analysisConfig.setPreprocess(preprocess);
    config.setAnalysis(analysisConfig);
    return config;
  }

  private Config.AnalysisConfig.PreprocessConfig preprocessConfig(
      String mode, String tool, Config.AnalysisConfig.DelombokConfig delombokConfig) {
    Config.AnalysisConfig.PreprocessConfig preprocess =
        new Config.AnalysisConfig.PreprocessConfig();
    preprocess.setMode(mode);
    preprocess.setTool(tool);
    preprocess.setDelombok(delombokConfig);
    return preprocess;
  }

  private Config.AnalysisConfig.DelombokConfig delombokWithJar(Path lombokJar) {
    Config.AnalysisConfig.DelombokConfig delombokConfig =
        new Config.AnalysisConfig.DelombokConfig();
    delombokConfig.setLombokJarPath(lombokJar.toString());
    return delombokConfig;
  }

  private static Path createFakeLombokJar(Path dir, String body) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      Assumptions.assumeTrue(false, "JDK compiler is required for this test");
      return null;
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
