package com.craftsmanbro.fulcraft.plugins.reporting.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportParserTest {

  private final ReportParser parser = new ReportParser();

  @Test
  void parseReportPopulatesCountsAndFailures(@TempDir Path tempDir) throws Exception {
    Path xmlFile = tempDir.resolve("TEST-sample.xml");
    Files.writeString(
        xmlFile,
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <testsuite name="Suite" tests="2" failures="1" errors="0" skipped="0">
          <testcase name="testA"/>
          <testcase name="testB">
            <failure message="Boom">line1
        line2</failure>
          </testcase>
        </testsuite>
        """);

    ReportTaskResult result = new ReportTaskResult();

    assertTrue(parser.parseReport(xmlFile, result));
    assertEquals(2, result.getTestsRun());
    assertEquals(1, result.getTestsFailed());
    assertEquals(0, result.getTestsError());
    assertEquals(0, result.getTestsSkipped());
    assertEquals(1, result.getFailureDetails().size());
    assertEquals("testB", result.getFailureDetails().get(0).getTestMethod());
    assertEquals("Boom", result.getFailureDetails().get(0).getMessageHead());
  }

  @Test
  void parseReportReturnsFalseOnInvalidXml(@TempDir Path tempDir) throws Exception {
    Path xmlFile = tempDir.resolve("TEST-bad.xml");
    Files.writeString(xmlFile, "<testsuite><testcase></testsuite>");

    ReportTaskResult result = new ReportTaskResult();

    assertFalse(parser.parseReport(xmlFile, result));
  }

  @Test
  void parseReportAggregatesMultipleSuites(@TempDir Path tempDir) throws Exception {
    Path xmlFile = tempDir.resolve("TEST-multi.xml");
    Files.writeString(
        xmlFile,
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <testsuites>
          <testsuite name="SuiteA" tests="2" failures="1" errors="0" skipped="0">
            <testcase name="testA"/>
            <testcase name="testB">
              <failure message="Fail A">boom</failure>
            </testcase>
          </testsuite>
          <testsuite name="SuiteB" tests="1" failures="0" errors="1" skipped="0">
            <testcase name="testC">
              <error message="Err B">crash</error>
            </testcase>
          </testsuite>
        </testsuites>
        """);

    ReportTaskResult result = new ReportTaskResult();

    assertTrue(parser.parseReport(xmlFile, result));
    assertEquals(3, result.getTestsRun());
    assertEquals(1, result.getTestsFailed());
    assertEquals(1, result.getTestsError());
    assertEquals(0, result.getTestsSkipped());
    assertEquals(2, result.getFailureDetails().size());
  }

  @Test
  void parseReportCapturesSuiteLevelFailure(@TempDir Path tempDir) throws Exception {
    Path xmlFile = tempDir.resolve("TEST-suite-failure.xml");
    Files.writeString(
        xmlFile,
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <testsuite name="SuiteRoot">
          <failure message="Setup failed">init</failure>
        </testsuite>
        """);

    ReportTaskResult result = new ReportTaskResult();

    assertTrue(parser.parseReport(xmlFile, result));
    assertEquals(1, result.getFailureDetails().size());
    assertEquals("SuiteRoot", result.getFailureDetails().get(0).getTestMethod());
    assertEquals("Setup failed", result.getFailureDetails().get(0).getMessageHead());
    assertEquals(1, result.getTestsFailed());
    assertEquals(1, result.getTestsRun());
  }

  @Test
  void parseReportUsesFailureTextWhenMessageAttributeMissing(@TempDir Path tempDir)
      throws Exception {
    Path xmlFile = tempDir.resolve("TEST-failure-text.xml");
    Files.writeString(
        xmlFile,
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <testsuite name="Suite">
          <testcase name="testWithText">
            <failure>first line
        second line</failure>
          </testcase>
        </testsuite>
        """);

    ReportTaskResult result = new ReportTaskResult();

    assertTrue(parser.parseReport(xmlFile, result));
    assertEquals(1, result.getFailureDetails().size());
    assertEquals("testWithText", result.getFailureDetails().get(0).getTestMethod());
    assertEquals("first line", result.getFailureDetails().get(0).getMessageHead());
  }

  @Test
  void parseReportAppliesFallbackWhenNamesAndMessagesAreMissing(@TempDir Path tempDir)
      throws Exception {
    Path xmlFile = tempDir.resolve("TEST-unknowns.xml");
    Files.writeString(
        xmlFile,
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <testsuites>
          <testcase>
            <failure></failure>
          </testcase>
        </testsuites>
        """);

    ReportTaskResult result = new ReportTaskResult();

    assertTrue(parser.parseReport(xmlFile, result));
    assertEquals(1, result.getFailureDetails().size());
    ReportTaskResult.FailureDetail detail = result.getFailureDetails().get(0);
    assertEquals(MessageSource.getMessage("report.parser.unknown_test"), detail.getTestMethod());
    assertEquals(
        MessageSource.getMessage("report.parser.unknown_failure_tag", "failure"),
        detail.getMessageHead());
    assertEquals(1, result.getTestsFailed());
    assertEquals(1, result.getTestsRun());
  }

  @Test
  void parseReportCountsFailuresAndErrorsWhenSuiteAttributesMissing(@TempDir Path tempDir)
      throws Exception {
    Path xmlFile = tempDir.resolve("TEST-no-attrs.xml");
    Files.writeString(
        xmlFile,
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <testsuite name="AttrLessSuite">
          <testcase name="testFailure">
            <failure message="failure">boom</failure>
          </testcase>
          <testcase name="testError">
            <error message="error">boom</error>
          </testcase>
        </testsuite>
        """);

    ReportTaskResult result = new ReportTaskResult();

    assertTrue(parser.parseReport(xmlFile, result));
    assertEquals(2, result.getFailureDetails().size());
    assertEquals(1, result.getTestsFailed());
    assertEquals(1, result.getTestsError());
    assertEquals(2, result.getTestsRun());
  }

  @Test
  void parseReportReturnsFalseWhenFileDoesNotExist(@TempDir Path tempDir) {
    Path xmlFile = tempDir.resolve("missing.xml");
    ReportTaskResult result = new ReportTaskResult();

    assertFalse(parser.parseReport(xmlFile, result));
    assertNotNull(result.getFailureDetails());
    assertTrue(result.getFailureDetails().isEmpty());
  }
}
