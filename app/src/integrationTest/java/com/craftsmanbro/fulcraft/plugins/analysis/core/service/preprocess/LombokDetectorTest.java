package com.craftsmanbro.fulcraft.plugins.analysis.core.service.preprocess;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LombokDetectorTest {

  @TempDir Path tempDir;

  @Test
  void detectLombok_returnsTrue_whenSourceUsesLombok() throws IOException {
    Path sourceRoot = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(sourceRoot);
    Files.writeString(
        sourceRoot.resolve("Example.java"),
        "package com.example;\n"
            + "\n"
            + "import lombok.Getter;\n"
            + "\n"
            + "@Getter\n"
            + "public class Example {}\n",
        StandardCharsets.UTF_8);

    boolean detected =
        new LombokDetector().detectLombok(tempDir, List.of(tempDir.resolve("src/main/java")));

    assertThat(detected).isTrue();
  }

  @Test
  void detectLombok_returnsTrue_whenBuildFileMentionsLombok() throws IOException {
    Files.writeString(
        tempDir.resolve("build.gradle"),
        "dependencies { compileOnly 'org.projectlombok:lombok:1.18.30' }\n",
        StandardCharsets.UTF_8);

    boolean detected =
        new LombokDetector().detectLombok(tempDir, List.of(tempDir.resolve("missing")));

    assertThat(detected).isTrue();
  }

  @Test
  void detectLombok_returnsFalse_whenNoSignals() throws IOException {
    Path sourceRoot = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(sourceRoot);
    Files.writeString(
        sourceRoot.resolve("Plain.java"),
        "package com.example;\n" + "\n" + "public class Plain {}\n",
        StandardCharsets.UTF_8);

    boolean detected =
        new LombokDetector().detectLombok(tempDir, List.of(tempDir.resolve("src/main/java")));

    assertThat(detected).isFalse();
  }

  @Test
  void detectLombok_returnsTrue_whenQualifiedAnnotationIsUsed() throws IOException {
    Path sourceRoot = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(sourceRoot);
    Files.writeString(
        sourceRoot.resolve("Qualified.java"),
        "package com.example;\n"
            + "\n"
            + "@lombok.Builder\n"
            + "public class Qualified {\n"
            + "  private final String value;\n"
            + "}\n",
        StandardCharsets.UTF_8);

    boolean detected = new LombokDetector().detectLombok(tempDir);

    assertThat(detected).isTrue();
  }

  @Test
  void detectLombok_returnsFalse_whenLombokMarkerIsBeyondScanLimit() throws IOException {
    Path sourceRoot = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(sourceRoot);
    for (int i = 0; i < 100; i++) {
      Files.writeString(
          sourceRoot.resolve("Sample%03d.java".formatted(i)),
          "package com.example;\n" + "\n" + "public class Sample%03d {}\n".formatted(i),
          StandardCharsets.UTF_8);
    }
    Files.writeString(
        sourceRoot.resolve("ZzzLombok.java"),
        "package com.example;\n"
            + "\n"
            + "import lombok.Getter;\n"
            + "\n"
            + "@Getter\n"
            + "public class ZzzLombok {\n"
            + "  private String name;\n"
            + "}\n",
        StandardCharsets.UTF_8);

    boolean detected =
        new LombokDetector().detectLombok(tempDir, List.of(tempDir.resolve("src/main/java")));

    assertThat(detected).isFalse();
  }

  @Test
  void detectLombok_returnsFalse_whenSourceRootsAreNull() {
    boolean detected = new LombokDetector().detectLombok(tempDir, null);

    assertThat(detected).isFalse();
  }
}
