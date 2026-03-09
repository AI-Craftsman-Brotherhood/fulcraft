package com.craftsmanbro.fulcraft.infrastructure.parser.xml;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.xml.JUnitXmlReportParser;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.failure.FailureAnalysisService;
import com.craftsmanbro.fulcraft.infrastructure.buildtool.failure.util.AssertionMismatchExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JUnitXmlReportParserTest {

  @TempDir Path tempDir;

  @Test
  void parseTestResultsExtractsAssertionDetailsAndReportContent() throws Exception {
    Path reportDir = tempDir.resolve("reports");
    Files.createDirectories(reportDir);
    Path reportFile = reportDir.resolve("TEST-com.example.SampleTest.xml");
    Files.writeString(
        reportFile,
        """
            <testsuite name="com.example.SampleTest" tests="1" failures="1">
              <testcase name="shouldFail" classname="com.example.SampleTest">
                <failure type="org.opentest4j.AssertionFailedError"
                         message="expected: &lt;1&gt; but was: &lt;2&gt;">
                  org.opentest4j.AssertionFailedError: expected: &lt;1&gt; but was: &lt;2&gt;
                  at com.example.SampleTest.shouldFail(SampleTest.java:10)
                </failure>
              </testcase>
            </testsuite>
            """);

    JUnitXmlReportParser parser = new JUnitXmlReportParser();
    List<JUnitXmlReportParser.TestFailure> failures = parser.parseTestResults(reportDir);

    assertEquals(1, failures.size());
    JUnitXmlReportParser.TestFailure failure = failures.get(0);
    assertEquals("com.example.SampleTest", failure.testClass());
    assertEquals("shouldFail", failure.testMethod());
    assertNotNull(failure.reportContent());
    assertTrue(failure.reportContent().contains("<testcase"));

    // Test analysis via FailureAnalysisService (moved from JUnitXmlReportParser)
    FailureAnalysisService analyzer = new FailureAnalysisService();
    AssertionMismatchExtractor.MismatchDetails details =
        analyzer.analyzeMismatch(failure.failureMessage(), failure.stackTrace());
    assertNotNull(details);
    assertEquals("1", details.expected());
    assertEquals("2", details.actual());

    String formatted =
        analyzer.formatForExchange(
            failure.testClass(),
            failure.testMethod(),
            failure.failureType(),
            failure.failureMessage(),
            failure.stackTrace());
    assertTrue(formatted.contains("Expected: 1"));
    assertTrue(formatted.contains("Actual: 2"));
    assertTrue(formatted.contains("Stack Trace (first 5 lines):"));
  }

  @Test
  void parseFailuresIsAliasForParseTestResults() throws Exception {
    Path reportDir = tempDir.resolve("reports2");
    Files.createDirectories(reportDir);
    Path reportFile = reportDir.resolve("TEST-AnotherTest.xml");
    Files.writeString(
        reportFile,
        """
            <testsuite name="com.example.AnotherTest" tests="1" failures="1">
              <testcase name="testSomething" classname="com.example.AnotherTest">
                <failure message="boom">stack trace here</failure>
              </testcase>
            </testsuite>
            """);

    JUnitXmlReportParser parser = new JUnitXmlReportParser();
    List<JUnitXmlReportParser.TestFailure> failures = parser.parseFailures(reportDir);

    assertEquals(1, failures.size());
    assertEquals("com.example.AnotherTest", failures.get(0).testClass());
    assertEquals("testSomething", failures.get(0).testMethod());
  }

  @Test
  void parseTestSummaryCountsFromTestcases() throws Exception {
    Path reportDir = tempDir.resolve("reports-summary");
    Files.createDirectories(reportDir);
    Path reportFile = reportDir.resolve("TEST-Summary.xml");
    Files.writeString(
        reportFile,
        """
            <testsuite name="com.example.SummaryTest">
              <testcase name="passes" classname="com.example.SummaryTest" />
              <testcase name="fails" classname="com.example.SummaryTest">
                <failure message="boom">stack</failure>
              </testcase>
              <testcase name="errors" classname="com.example.SummaryTest">
                <error message="err">stack</error>
              </testcase>
              <testcase name="skipped" classname="com.example.SummaryTest">
                <skipped />
              </testcase>
            </testsuite>
            """);

    JUnitXmlReportParser parser = new JUnitXmlReportParser();
    JUnitXmlReportParser.TestRunSummary summary = parser.parseTestSummary(reportDir);

    assertNotNull(summary);
    assertEquals(4, summary.total());
    assertEquals(1, summary.failures());
    assertEquals(1, summary.errors());
    assertEquals(1, summary.skipped());
    assertEquals(1, summary.passed());
  }

  @Test
  void parseTestSummaryCountsFromAttributesWhenNoTestcases() throws Exception {
    Path reportDir = tempDir.resolve("reports-summary-attr");
    Files.createDirectories(reportDir);
    Path reportFile = reportDir.resolve("TEST-Attr.xml");
    Files.writeString(
        reportFile,
        """
            <testsuite name="com.example.AttrTest" tests="5" failures="1" errors="1" skipped="2" />
            """);

    JUnitXmlReportParser parser = new JUnitXmlReportParser();
    JUnitXmlReportParser.TestRunSummary summary = parser.parseTestSummary(reportDir);

    assertNotNull(summary);
    assertEquals(5, summary.total());
    assertEquals(1, summary.failures());
    assertEquals(1, summary.errors());
    assertEquals(2, summary.skipped());
    assertEquals(1, summary.passed());
  }

  @Test
  void parseTestResultsReturnsEmptyWhenDirectoryMissing() {
    JUnitXmlReportParser parser = new JUnitXmlReportParser();

    List<JUnitXmlReportParser.TestFailure> failures =
        parser.parseTestResults(tempDir.resolve("missing-dir"));

    assertTrue(failures.isEmpty());
  }

  @Test
  void parseTestResultsSkipsBrokenXmlAndContinues() throws Exception {
    Path reportDir = tempDir.resolve("reports-broken");
    Files.createDirectories(reportDir);
    Files.writeString(
        reportDir.resolve("TEST-valid.xml"),
        """
            <testsuite name="com.example.ValidTest">
              <testcase name="fails">
                <failure message="boom">boom</failure>
              </testcase>
            </testsuite>
            """);
    Files.writeString(reportDir.resolve("TEST-broken.xml"), "<testsuite><testcase>");

    JUnitXmlReportParser parser = new JUnitXmlReportParser();
    List<JUnitXmlReportParser.TestFailure> failures = parser.parseTestResults(reportDir);

    assertEquals(1, failures.size());
    assertEquals("fails", failures.get(0).testMethod());
  }

  @Test
  void parseTestResultsUsesSuiteNameWhenClassnameIsMissing() throws Exception {
    Path reportDir = tempDir.resolve("reports-suite-fallback");
    Files.createDirectories(reportDir);
    Files.writeString(
        reportDir.resolve("TEST-suite.xml"),
        """
            <testsuite name="com.example.SuiteNamedClass">
              <testcase name="failsWithoutClassname">
                <failure message="boom">boom</failure>
              </testcase>
            </testsuite>
            """);

    JUnitXmlReportParser parser = new JUnitXmlReportParser();
    List<JUnitXmlReportParser.TestFailure> failures = parser.parseTestResults(reportDir);

    assertEquals(1, failures.size());
    assertEquals("com.example.SuiteNamedClass", failures.get(0).testClass());
  }

  @Test
  void parseTestResultsHandlesTestsuitesRoot() throws Exception {
    Path reportDir = tempDir.resolve("reports-testsuites-root");
    Files.createDirectories(reportDir);
    Files.writeString(
        reportDir.resolve("TEST-testsuites.xml"),
        """
            <testsuites>
              <testsuite name="com.example.FirstSuite">
                <testcase name="firstFailure">
                  <failure message="boom">boom</failure>
                </testcase>
              </testsuite>
              <testsuite name="com.example.SecondSuite">
                <testcase name="secondError">
                  <error message="crash">crash</error>
                </testcase>
              </testsuite>
            </testsuites>
            """);

    JUnitXmlReportParser parser = new JUnitXmlReportParser();
    List<JUnitXmlReportParser.TestFailure> failures = parser.parseTestResults(reportDir);

    assertEquals(2, failures.size());
    assertEquals("com.example.FirstSuite", failures.get(0).testClass());
    assertEquals("firstFailure", failures.get(0).testMethod());
    assertEquals("com.example.SecondSuite", failures.get(1).testClass());
    assertEquals("secondError", failures.get(1).testMethod());
  }

  @Test
  void parseTestResultsExtractsMessageFromStackTraceWhenMessageAttributeIsMissing()
      throws Exception {
    Path reportDir = tempDir.resolve("reports-message-fallback");
    Files.createDirectories(reportDir);
    Files.writeString(
        reportDir.resolve("TEST-message.xml"),
        """
            <testsuite name="com.example.MessageTest">
              <testcase name="fails">
                <failure>java.lang.AssertionError: top line message
                  at com.example.MessageTest.fails(MessageTest.java:10)
                </failure>
              </testcase>
            </testsuite>
            """);

    JUnitXmlReportParser parser = new JUnitXmlReportParser();
    List<JUnitXmlReportParser.TestFailure> failures = parser.parseTestResults(reportDir);

    assertEquals(1, failures.size());
    assertEquals("java.lang.AssertionError: top line message", failures.get(0).failureMessage());
    assertEquals("AssertionError", failures.get(0).failureType());
  }

  @Test
  void parseTestSummaryCountsStatusBasedSkippedAndDisabled() throws Exception {
    Path reportDir = tempDir.resolve("reports-summary-status");
    Files.createDirectories(reportDir);
    Files.writeString(
        reportDir.resolve("TEST-status.xml"),
        """
            <testsuite name="com.example.StatusTest">
              <testcase name="pass" />
              <testcase name="skippedByStatus" status="skipped" />
              <testcase name="disabledByStatus" status="disabled" />
              <testcase name="skippedByTag"><skipped /></testcase>
            </testsuite>
            """);

    JUnitXmlReportParser parser = new JUnitXmlReportParser();
    JUnitXmlReportParser.TestRunSummary summary = parser.parseTestSummary(reportDir);

    assertNotNull(summary);
    assertEquals(4, summary.total());
    assertEquals(3, summary.skipped());
    assertEquals(1, summary.passed());
  }

  @Test
  void parseTestSummaryParsesDisabledAttributeAndNormalizesInvalidCounts() throws Exception {
    Path reportDir = tempDir.resolve("reports-summary-attr-disabled");
    Files.createDirectories(reportDir);
    Files.writeString(
        reportDir.resolve("TEST-AttrDisabled.xml"),
        """
            <testsuite tests="5" failures="-3" errors="oops" skipped="1" disabled="2" />
            """);

    JUnitXmlReportParser parser = new JUnitXmlReportParser();
    JUnitXmlReportParser.TestRunSummary summary = parser.parseTestSummary(reportDir);

    assertNotNull(summary);
    assertEquals(5, summary.total());
    assertEquals(0, summary.failures());
    assertEquals(0, summary.errors());
    assertEquals(3, summary.skipped());
    assertEquals(2, summary.passed());
  }

  @Test
  void parseTestSummaryCombinesTestcaseAndAttributeSuites() throws Exception {
    Path reportDir = tempDir.resolve("reports-summary-mixed");
    Files.createDirectories(reportDir);
    Files.writeString(
        reportDir.resolve("TEST-Mixed.xml"),
        """
            <testsuites>
              <testsuite name="com.example.CaseSuite">
                <testcase name="passes" />
                <testcase name="fails">
                  <failure message="boom">stack</failure>
                </testcase>
              </testsuite>
              <testsuite name="com.example.AttributeSuite" tests="3" failures="0" errors="1" skipped="1" />
            </testsuites>
            """);

    JUnitXmlReportParser parser = new JUnitXmlReportParser();
    JUnitXmlReportParser.TestRunSummary summary = parser.parseTestSummary(reportDir);

    assertNotNull(summary);
    assertEquals(5, summary.total());
    assertEquals(1, summary.failures());
    assertEquals(1, summary.errors());
    assertEquals(1, summary.skipped());
    assertEquals(2, summary.passed());
  }

  @Test
  void parseTestSummaryUsesTestsuitesRootAttributesWhenNoChildSuitesExist() throws Exception {
    Path reportDir = tempDir.resolve("reports-summary-testsuites-attrs");
    Files.createDirectories(reportDir);
    Files.writeString(
        reportDir.resolve("TEST-RootAttrs.xml"),
        """
            <testsuites tests="6" failures="1" errors="2" skipped="1" disabled="1" />
            """);

    JUnitXmlReportParser parser = new JUnitXmlReportParser();
    JUnitXmlReportParser.TestRunSummary summary = parser.parseTestSummary(reportDir);

    assertNotNull(summary);
    assertEquals(6, summary.total());
    assertEquals(1, summary.failures());
    assertEquals(2, summary.errors());
    assertEquals(2, summary.skipped());
    assertEquals(1, summary.passed());
  }

  @Test
  void parseTestSummaryReturnsNullWhenDirectoryHasNoParsableReports() throws Exception {
    Path reportDir = tempDir.resolve("reports-empty");
    Files.createDirectories(reportDir);
    Files.writeString(reportDir.resolve("README.txt"), "not an xml report");

    JUnitXmlReportParser parser = new JUnitXmlReportParser();

    assertNull(parser.parseTestSummary(reportDir));
    assertNull(parser.parseTestSummary(tempDir.resolve("missing-summary-dir")));
  }
}
