package com.craftsmanbro.fulcraft.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes canonical temporary Java source trees used by end-to-end CLI tests. */
public final class ProjectFixtures {

  private ProjectFixtures() {
    // utility
  }

  /**
   * Writes a small project under {@code projectRoot/src/main/java} that exercises classes,
   * interfaces, a record, an enum, and an internal class→class dependency edge. This is the
   * standard fixture for analysis/report assertions (records/enums, no duplicate methods, package
   * edges).
   *
   * @param projectRoot the project root directory
   * @throws IOException if a source file cannot be written
   */
  public static void writeMultiTypeProject(final Path projectRoot) throws IOException {
    write(
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
    write(
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
    write(
        projectRoot,
        "com/demo/Color.java",
        """
        package com.demo;

        public enum Color {
          RED,
          GREEN,
          BLUE;

          public boolean isPrimary() {
            return this == RED || this == BLUE;
          }
        }
        """);
    write(
        projectRoot,
        "com/demo/App.java",
        """
        package com.demo;

        public class App {
          private final Greeter greeter = new Greeter();

          public String run(String name) {
            return greeter.greet(name);
          }
        }
        """);
  }

  /** Writes a single source file at {@code projectRoot/src/main/java/<relativePath>}. */
  public static void write(final Path projectRoot, final String relativePath, final String content)
      throws IOException {
    final Path file = projectRoot.resolve("src/main/java").resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
