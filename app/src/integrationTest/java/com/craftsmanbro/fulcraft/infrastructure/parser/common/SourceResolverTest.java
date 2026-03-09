package com.craftsmanbro.fulcraft.infrastructure.parser.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.SourcePathResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceResolverTest {

  @TempDir Path tempDir;
  private final SourcePathResolver resolver = new SourcePathResolver();

  @Test
  void returnsMainAndTestWhenStandardLayout() throws IOException {
    Path main = tempDir.resolve("src/main/java");
    Path test = tempDir.resolve("src/test/java");
    Files.createDirectories(main);
    Files.createDirectories(test);
    Files.createFile(main.resolve("Foo.java"));
    Files.createFile(test.resolve("FooTest.java"));

    var dirs = resolver.resolve(tempDir);

    assertTrue(dirs.mainSource().isPresent());
    assertEquals(main, dirs.mainSource().get());
    assertTrue(dirs.testSource().isPresent());
    assertEquals(test, dirs.testSource().get());
  }

  @Test
  void doesNotTreatTestOnlySrcAsMain() throws IOException {
    Path test = tempDir.resolve("src/test/java");
    Files.createDirectories(test);
    Files.createFile(test.resolve("OnlyTest.java"));

    var dirs = resolver.resolve(tempDir);

    assertTrue(dirs.mainSource().isEmpty());
    assertTrue(dirs.testSource().isPresent());
    assertEquals(test, dirs.testSource().get());
  }

  @Test
  void fallsBackToRootWhenTopLevelJava() throws IOException {
    Files.createFile(tempDir.resolve("TopLevel.java"));

    var dirs = resolver.resolve(tempDir);

    assertTrue(dirs.mainSource().isPresent());
    assertEquals(tempDir, dirs.mainSource().get());
    assertTrue(dirs.testSource().isEmpty());
  }

  @Test
  void findsNestedSrc() throws IOException {
    Path nested = tempDir.resolve("sub-project/src");
    Files.createDirectories(nested);
    Files.createFile(nested.resolve("Service.java"));

    var dirs = resolver.resolve(tempDir);

    assertTrue(dirs.mainSource().isPresent());
    assertEquals(nested, dirs.mainSource().get());
  }

  @Test
  void honorsGradleSourceSetsWhenPresent() throws IOException {
    Path main = tempDir.resolve("custom/src/main/java");
    Path test = tempDir.resolve("custom/src/test/java");
    Files.createDirectories(main);
    Files.createDirectories(test);
    Files.writeString(
        tempDir.resolve("build.gradle"),
        """
            sourceSets {
              main {
                java {
                  srcDirs = ['custom/src/main/java']
                }
              }
              test {
                java {
                  srcDirs = ['custom/src/test/java']
                }
              }
            }
            """);
    Files.createFile(main.resolve("App.java"));
    Files.createFile(test.resolve("AppTest.java"));

    var dirs = resolver.resolve(tempDir);

    assertTrue(dirs.mainSource().isPresent());
    assertEquals(main, dirs.mainSource().get());
    assertTrue(dirs.testSource().isPresent());
    assertEquals(test, dirs.testSource().get());
  }

  @Test
  void honorsMavenSourceDirectoriesWhenPresent() throws IOException {
    Path main = tempDir.resolve("source/main/java");
    Path test = tempDir.resolve("source/test/java");
    Files.createDirectories(main);
    Files.createDirectories(test);
    Files.writeString(
        tempDir.resolve("pom.xml"),
        """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <build>
                <sourceDirectory>source/main/java</sourceDirectory>
                <testSourceDirectory>source/test/java</testSourceDirectory>
              </build>
            </project>
            """);
    Files.createFile(main.resolve("MavenApp.java"));
    Files.createFile(test.resolve("MavenAppTest.java"));

    var dirs = resolver.resolve(tempDir);

    assertTrue(dirs.mainSource().isPresent());
    assertEquals(main, dirs.mainSource().get());
    assertTrue(dirs.testSource().isPresent());
    assertEquals(test, dirs.testSource().get());
  }
}
