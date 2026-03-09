package com.craftsmanbro.fulcraft.ui.tui.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeFixApplierTest {

  @TempDir private Path tempDir;

  @Test
  @DisplayName("disableTest inserts @Disabled annotation and import")
  void disableTestInsertsAnnotationAndImport() throws IOException {
    Path testFile =
        writeTestFile(
            "src/test/java/com/example/MyTest.java",
            String.join(
                "\n",
                "package com.example;",
                "",
                "import org.junit.jupiter.api.Test;",
                "",
                "class MyTest {",
                "  @Test",
                "  void myMethod() {",
                "  }",
                "}",
                ""));

    SafeFixApplier applier = new SafeFixApplier(tempDir, configWithTestDirs("src/test/java"));

    boolean updated = applier.disableTest("com.example.MyTest", "myMethod");

    assertTrue(updated);
    String content = Files.readString(testFile);
    assertTrue(content.contains("import org.junit.jupiter.api.Disabled;"));
    assertTrue(content.contains("@Disabled(\"FUL safe fix: failing test\")"));
    assertTrue(content.indexOf("@Disabled") < content.indexOf("@Test"));
    assertEquals(1, countOccurrences(content, "import org.junit.jupiter.api.Disabled;"));
  }

  @Test
  @DisplayName("disableTest returns false when method is missing and keeps file unchanged")
  void disableTestReturnsFalseWhenMethodMissing() throws IOException {
    String original =
        String.join(
            "\n",
            "package com.example;",
            "",
            "import org.junit.jupiter.api.Test;",
            "",
            "class MyTest {",
            "  @Test",
            "  void otherMethod() {",
            "  }",
            "}",
            "");
    Path testFile = writeTestFile("src/test/java/com/example/MyTest.java", original);

    SafeFixApplier applier = new SafeFixApplier(tempDir, configWithTestDirs("src/test/java"));

    boolean updated = applier.disableTest("com.example.MyTest", "myMethod");

    assertFalse(updated);
    assertEquals(original, Files.readString(testFile));
  }

  @Test
  @DisplayName("disableTest returns false when @Disabled already present and keeps file unchanged")
  void disableTestReturnsFalseWhenAlreadyDisabled() throws IOException {
    String original =
        String.join(
            "\n",
            "package com.example;",
            "",
            "import org.junit.jupiter.api.Disabled;",
            "import org.junit.jupiter.api.Test;",
            "",
            "class MyTest {",
            "  @Disabled(\"Existing\")",
            "  @Test",
            "  void myMethod() {",
            "  }",
            "}",
            "");
    Path testFile = writeTestFile("src/test/java/com/example/MyTest.java", original);

    SafeFixApplier applier = new SafeFixApplier(tempDir, configWithTestDirs("src/test/java"));

    boolean updated = applier.disableTest("com.example.MyTest", "myMethod");

    assertFalse(updated);
    assertEquals(original, Files.readString(testFile));
  }

  @Test
  @DisplayName("disableTest ignores commented out method signatures")
  void disableTestIgnoresCommentedOutMethodSignatures() throws IOException {
    String original =
        String.join(
            "\n",
            "package com.example;",
            "",
            "class CommentOnlyTest {",
            "  // void myMethod() {",
            "  // }",
            "  void otherMethod() {",
            "  }",
            "}",
            "");
    Path testFile = writeTestFile("src/test/java/com/example/CommentOnlyTest.java", original);

    SafeFixApplier applier = new SafeFixApplier(tempDir, configWithTestDirs("src/test/java"));

    boolean updated = applier.disableTest("com.example.CommentOnlyTest", "myMethod");

    assertFalse(updated);
    assertEquals(original, Files.readString(testFile));
  }

  @Test
  @DisplayName("disableTest returns false for blank inputs")
  void disableTestReturnsFalseForBlankInputs() {
    SafeFixApplier applier = new SafeFixApplier(tempDir, configWithTestDirs("src/test/java"));

    assertFalse(applier.disableTest(" ", "testMethod"));
    assertFalse(applier.disableTest("com.example.MyTest", " "));
    assertFalse(applier.disableTest(null, "testMethod"));
    assertFalse(applier.disableTest("com.example.MyTest", null));
  }

  @Test
  @DisplayName("disableTest resolves custom test roots from config")
  void disableTestResolvesCustomTestRoots() throws IOException {
    Path testFile =
        writeTestFile(
            "custom-tests/com/example/CustomTest.java",
            String.join(
                "\n",
                "package com.example;",
                "",
                "import org.junit.jupiter.api.Test;",
                "",
                "class CustomTest {",
                "  @Test",
                "  void customMethod() {",
                "  }",
                "}",
                ""));

    SafeFixApplier applier = new SafeFixApplier(tempDir, configWithTestDirs("custom-tests"));

    boolean updated = applier.disableTest("com.example.CustomTest", "customMethod");

    assertTrue(updated);
    String content = Files.readString(testFile);
    assertTrue(content.contains("@Disabled(\"FUL safe fix: failing test\")"));
  }

  @Test
  @DisplayName("disableTest returns false when test file is not found")
  void disableTestReturnsFalseWhenTestFileIsNotFound() {
    SafeFixApplier applier = new SafeFixApplier(tempDir, configWithTestDirs("src/test/java"));

    boolean updated = applier.disableTest("com.example.MissingTest", "missingMethod");

    assertFalse(updated);
  }

  @Test
  @DisplayName("disableTest inserts import after package when no other imports exist")
  void disableTestInsertsImportAfterPackageWhenNoOtherImports() throws IOException {
    Path testFile =
        writeTestFile(
            "src/test/java/com/example/PackageOnlyTest.java",
            String.join(
                "\n",
                "package com.example;",
                "",
                "class PackageOnlyTest {",
                "  void myMethod() {",
                "  }",
                "}",
                ""));

    SafeFixApplier applier = new SafeFixApplier(tempDir, configWithTestDirs("src/test/java"));
    boolean updated = applier.disableTest("com.example.PackageOnlyTest", "myMethod");

    assertTrue(updated);
    String content = Files.readString(testFile);
    assertTrue(
        content.indexOf("package com.example;")
            < content.indexOf("import org.junit.jupiter.api.Disabled;"));
  }

  @Test
  @DisplayName("disableTest inserts import at file start when package and imports are missing")
  void disableTestInsertsImportAtTopWhenPackageAndImportsMissing() throws IOException {
    Path testFile =
        writeTestFile(
            "src/test/java/com/example/NoPackageTest.java",
            String.join("\n", "class NoPackageTest {", "  void myMethod() {", "  }", "}", ""));

    SafeFixApplier applier = new SafeFixApplier(tempDir, configWithTestDirs("src/test/java"));
    boolean updated = applier.disableTest("com.example.NoPackageTest", "myMethod");

    assertTrue(updated);
    List<String> lines = Files.readAllLines(testFile);
    assertEquals("import org.junit.jupiter.api.Disabled;", lines.getFirst());
  }

  @Test
  @DisplayName(
      "disableTest falls back to loose method match when strict void signature does not match")
  void disableTestFallsBackToLooseMethodMatch() throws IOException {
    Path testFile =
        writeTestFile(
            "src/test/java/com/example/NonVoidTest.java",
            String.join(
                "\n",
                "package com.example;",
                "",
                "class NonVoidTest {",
                "  int myMethod() {",
                "    return 1;",
                "  }",
                "}",
                ""));

    SafeFixApplier applier = new SafeFixApplier(tempDir, configWithTestDirs("src/test/java"));
    boolean updated = applier.disableTest("com.example.NonVoidTest", "myMethod");

    assertTrue(updated);
    String content = Files.readString(testFile);
    assertTrue(content.contains("@Disabled(\"FUL safe fix: failing test\")"));
  }

  @Test
  @DisplayName("disableTest falls back to default test roots when configured roots are blank")
  void disableTestFallsBackToDefaultTestRootsWhenConfiguredRootsBlank() throws IOException {
    Path testFile =
        writeTestFile(
            "app/src/test/java/com/example/FallbackRootTest.java",
            String.join(
                "\n",
                "package com.example;",
                "",
                "class FallbackRootTest {",
                "  void myMethod() {",
                "  }",
                "}",
                ""));

    Config config = Config.createDefault();
    Config.ContextAwarenessConfig contextAwareness = new Config.ContextAwarenessConfig();
    contextAwareness.setTestDirs(List.of(" ", ""));
    config.setContextAwareness(contextAwareness);

    SafeFixApplier applier = new SafeFixApplier(tempDir, config);
    boolean updated = applier.disableTest("com.example.FallbackRootTest", "myMethod");

    assertTrue(updated);
    String content = Files.readString(testFile);
    assertTrue(content.contains("@Disabled(\"FUL safe fix: failing test\")"));
  }

  @Test
  @DisplayName("disableTest uses default roots when config is null")
  void disableTestUsesDefaultRootsWhenConfigNull() throws IOException {
    Path testFile =
        writeTestFile(
            "src/test/java/com/example/NullConfigTest.java",
            String.join(
                "\n",
                "package com.example;",
                "",
                "class NullConfigTest {",
                "  void myMethod() {",
                "  }",
                "}",
                ""));

    SafeFixApplier applier = new SafeFixApplier(tempDir, null);
    boolean updated = applier.disableTest("com.example.NullConfigTest", "myMethod");

    assertTrue(updated);
    String content = Files.readString(testFile);
    assertTrue(content.contains("@Disabled(\"FUL safe fix: failing test\")"));
  }

  private Config configWithTestDirs(String... testDirs) {
    Config config = Config.createDefault();
    Config.ContextAwarenessConfig contextAwareness = new Config.ContextAwarenessConfig();
    contextAwareness.setTestDirs(List.of(testDirs));
    config.setContextAwareness(contextAwareness);
    return config;
  }

  private Path writeTestFile(String relativePath, String content) throws IOException {
    Path file = tempDir.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
    return file;
  }

  private int countOccurrences(String content, String needle) {
    int count = 0;
    int index = 0;
    while (index >= 0) {
      index = content.indexOf(needle, index);
      if (index >= 0) {
        count++;
        index += needle.length();
      }
    }
    return count;
  }
}
