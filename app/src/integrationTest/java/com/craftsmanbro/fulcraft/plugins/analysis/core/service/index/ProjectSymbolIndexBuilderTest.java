package com.craftsmanbro.fulcraft.plugins.analysis.core.service.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectSymbolIndexBuilderTest {

  @TempDir Path tempDir;

  @Test
  void build_collectsClassesMethodsAndFields() throws Exception {
    Path srcRoot = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(srcRoot);
    Path file = srcRoot.resolve("Foo.java");
    Files.writeString(
        file,
        """
        package com.example;
        class Foo {
          private int count;
          String name;
          Foo() {}
          void run(String arg) {}
          static class Inner {
            long id;
            void ping() {}
          }
        }
        """);

    ProjectSymbolIndex index =
        new ProjectSymbolIndexBuilder().build(List.of(tempDir.resolve("src/main/java")));

    assertThat(index.hasClass("com.example.Foo")).isTrue();
    assertThat(index.hasClass("Foo")).isTrue();
    assertThat(index.hasClass("com.example.Foo.Inner")).isTrue();
    assertThat(index.hasMethod("com.example.Foo", "run")).isTrue();
    assertThat(index.hasMethodArity("com.example.Foo", "run", 1)).isTrue();
    assertThat(index.hasField("com.example.Foo", "count")).isTrue();
    assertThat(index.hasMethod("com.example.Foo.Inner", "ping")).isTrue();
  }

  @Test
  void build_skipsLocalClassesInInitializer() throws Exception {
    Path srcRoot = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(srcRoot);
    Path file = srcRoot.resolve("Foo.java");
    Files.writeString(
        file,
        """
        package com.example;
        class Foo {
          static {
            class Local {
              void init() {}
            }
          }
        }
        """);

    ProjectSymbolIndex index =
        new ProjectSymbolIndexBuilder().build(List.of(tempDir.resolve("src/main/java")));

    assertThat(index.hasClass("com.example.Foo")).isTrue();
    assertThat(index.hasClass("Local")).isFalse();
  }

  @Test
  void build_skipsLocalClassesInMethodAndConstructor() throws Exception {
    Path srcRoot = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(srcRoot);
    Files.writeString(
        srcRoot.resolve("Foo.java"),
        """
        package com.example;
        class Foo {
          Foo() {
            class InCtor {
              void ctorLocal() {}
            }
          }
          void run() {
            class InMethod {
              void methodLocal() {}
            }
          }
        }
        """);

    ProjectSymbolIndex index =
        new ProjectSymbolIndexBuilder().build(List.of(tempDir.resolve("src/main/java")));

    assertThat(index.hasClass("com.example.Foo")).isTrue();
    assertThat(index.hasClass("InCtor")).isFalse();
    assertThat(index.hasClass("InMethod")).isFalse();
  }

  @Test
  void build_collectsEnumMembers() throws Exception {
    Path srcRoot = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(srcRoot);
    Files.writeString(
        srcRoot.resolve("DomainTypes.java"),
        """
        package com.example;
        enum Status {
          ACTIVE;
          int level;
          void normalize() {}
        }
        """);

    ProjectSymbolIndex index =
        new ProjectSymbolIndexBuilder().build(List.of(tempDir.resolve("src/main/java")));

    assertThat(index.hasClass("com.example.Status")).isTrue();
    assertThat(index.hasMethod("Status", "normalize")).isTrue();
    assertThat(index.hasField("Status", "level")).isTrue();
  }

  @Test
  void update_parsesSourceFileIntoExistingIndex() throws Exception {
    Path javaRoot = tempDir.resolve("src/main/java");
    Files.createDirectories(javaRoot);
    ProjectSymbolIndexBuilder builder = new ProjectSymbolIndexBuilder();
    ProjectSymbolIndex index = builder.build(List.of(javaRoot));

    Path sourceFile = javaRoot.resolve("com/example/LateAdded.java");
    Files.createDirectories(sourceFile.getParent());
    Files.writeString(
        sourceFile,
        """
        package com.example;
        class LateAdded {
          private String id;
          void sync(int retry) {}
        }
        """);

    builder.update(index, sourceFile);

    assertThat(index.hasClass("com.example.LateAdded")).isTrue();
    assertThat(index.hasMethodArity("LateAdded", "sync", 1)).isTrue();
    assertThat(index.hasField("LateAdded", "id")).isTrue();
  }

  @Test
  void update_ignoresNullAndNonRegularFiles() throws Exception {
    ProjectSymbolIndex index = new ProjectSymbolIndex();
    ProjectSymbolIndexBuilder builder = new ProjectSymbolIndexBuilder();
    Path directory = tempDir.resolve("src/main/java");
    Files.createDirectories(directory);

    builder.update(index, null);
    builder.update(null, directory.resolve("Foo.java"));
    builder.update(index, directory);

    assertThat(index.hasClass("com.example.Foo")).isFalse();
  }

  @Test
  void build_reusesCacheForSameRootsRegardlessOfOrder() throws Exception {
    Path rootA = tempDir.resolve("module-a/src/main/java/com/example");
    Path rootB = tempDir.resolve("module-b/src/main/java/com/example");
    Files.createDirectories(rootA);
    Files.createDirectories(rootB);
    Files.writeString(rootA.resolve("A.java"), "package com.example; class A {}");
    Files.writeString(rootB.resolve("B.java"), "package com.example; class B {}");

    Path sourceRootA = tempDir.resolve("module-a/src/main/java");
    Path sourceRootB = tempDir.resolve("module-b/src/main/java");
    ProjectSymbolIndexBuilder builder = new ProjectSymbolIndexBuilder();

    ProjectSymbolIndex first = builder.build(List.of(sourceRootA, sourceRootB));
    ProjectSymbolIndex second =
        new ProjectSymbolIndexBuilder().build(List.of(sourceRootB, sourceRootA));

    assertThat(second).isSameAs(first);
    assertThat(second.hasClass("com.example.A")).isTrue();
    assertThat(second.hasClass("com.example.B")).isTrue();
  }

  @Test
  void build_ignoresNullAndNonDirectoryRoots() {
    Path missingRoot = tempDir.resolve("missing/src/main/java");

    ProjectSymbolIndex index =
        new ProjectSymbolIndexBuilder().build(Arrays.asList(null, missingRoot));

    assertThat(index.hasClass("Anything")).isFalse();
    assertThat(index.findClassCandidates("Anything", 10)).isEmpty();
  }
}
