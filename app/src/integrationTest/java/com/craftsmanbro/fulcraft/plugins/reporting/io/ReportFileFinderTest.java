package com.craftsmanbro.fulcraft.plugins.reporting.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportFileFinderTest {

  private final ReportFileFinder finder = new ReportFileFinder();

  @Test
  void findReportFilePrefersStandardPattern(@TempDir Path tempDir) throws Exception {
    Path report = tempDir.resolve("TEST-com.example.Foo_barGeneratedTest.xml");
    Files.writeString(report, "<testsuite/>");

    var result =
        finder.findReportFile(tempDir, "com.example.Foo_barGeneratedTest", "Foo_barGeneratedTest");

    assertTrue(result.isPresent());
    assertEquals(report, result.get());
  }

  @Test
  void findReportFileFallsBackToSuffix(@TempDir Path tempDir) throws Exception {
    Path report = tempDir.resolve("TEST-com.example.Foo_barGeneratedTest_2.xml");
    Files.writeString(report, "<testsuite/>");

    var result =
        finder.findReportFile(tempDir, "com.example.Foo_barGeneratedTest", "Foo_barGeneratedTest");

    assertTrue(result.isPresent());
    assertEquals(report, result.get());
  }

  @Test
  void hasAnyReportFileDetectsXml(@TempDir Path tempDir) throws Exception {
    Files.writeString(tempDir.resolve("TEST-Sample.xml"), "<testsuite/>");

    assertTrue(finder.hasAnyReportFile(tempDir));
  }

  @Test
  void resolveReportDir_prefersGradleDirectoryWhenPresent(@TempDir Path tempDir) throws Exception {
    Path gradleDir = tempDir.resolve("build/test-results/test");
    Path mavenDir = tempDir.resolve("target/surefire-reports");
    Files.createDirectories(gradleDir);
    Files.createDirectories(mavenDir);

    Path resolved = finder.resolveReportDir(tempDir);

    assertEquals(gradleDir, resolved);
  }

  @Test
  void resolveReportDir_fallsBackToMavenPathWhenGradleDirectoryMissing(@TempDir Path tempDir) {
    Path resolved = finder.resolveReportDir(tempDir);

    assertEquals(tempDir.resolve("target/surefire-reports"), resolved);
  }

  @Test
  void findReportFileFallsBackToSimpleClassName(@TempDir Path tempDir) throws Exception {
    Path report = tempDir.resolve("TEST-Foo_barGeneratedTest.xml");
    Files.writeString(report, "<testsuite/>");

    var result =
        finder.findReportFile(tempDir, "com.example.Foo_barGeneratedTest", "Foo_barGeneratedTest");

    assertTrue(result.isPresent());
    assertEquals(report, result.get());
  }

  @Test
  void findReportFileFallsBackToNoPrefixPattern(@TempDir Path tempDir) throws Exception {
    Path report = tempDir.resolve("com.example.Foo_barGeneratedTest.xml");
    Files.writeString(report, "<testsuite/>");

    var result =
        finder.findReportFile(tempDir, "com.example.Foo_barGeneratedTest", "Foo_barGeneratedTest");

    assertTrue(result.isPresent());
    assertEquals(report, result.get());
  }

  @Test
  void findReportFileFallsBackToGlobAndUsesStableOrder(@TempDir Path tempDir) throws Exception {
    Path later = tempDir.resolve("zzz-Foo_barGeneratedTest-report.xml");
    Path earlier = tempDir.resolve("aaa-Foo_barGeneratedTest-report.xml");
    Files.writeString(later, "<testsuite/>");
    Files.writeString(earlier, "<testsuite/>");

    var result =
        finder.findReportFile(tempDir, "com.example.Foo_barGeneratedTest", "Foo_barGeneratedTest");

    assertTrue(result.isPresent());
    assertEquals(earlier, result.get());
  }

  @Test
  void findReportFileReturnsEmptyWhenDirectoryMissing(@TempDir Path tempDir) {
    var result =
        finder.findReportFile(
            tempDir.resolve("missing"), "com.example.Foo_barGeneratedTest", "Foo_barGeneratedTest");

    assertTrue(result.isEmpty());
  }

  @Test
  void hasAnyReportFileReturnsFalseWhenDirectoryContainsNoXml(@TempDir Path tempDir)
      throws Exception {
    Files.writeString(tempDir.resolve("README.txt"), "no reports");

    assertFalse(finder.hasAnyReportFile(tempDir));
  }
}
