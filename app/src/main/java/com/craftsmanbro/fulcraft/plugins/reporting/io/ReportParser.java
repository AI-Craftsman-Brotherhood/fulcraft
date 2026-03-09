package com.craftsmanbro.fulcraft.plugins.reporting.io;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.kernel.pipeline.model.ReportTaskResult;
import com.craftsmanbro.fulcraft.plugins.reporting.core.util.AggregatorUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Helper class for parsing JUnit XML test reports. */
public class ReportParser {

  private static final String TAG_FAILURE = "failure";

  private static final String TAG_ERROR = "error";

  private static final String TAG_TESTCASE = "testcase";

  private static final String TAG_TESTSUITE = "testsuite";

  /**
   * Parses a JUnit XML report file and populates the result.
   *
   * @return true if parsing succeeded, false otherwise
   */
  public boolean parseReport(final Path xmlFile, final ReportTaskResult result) {
    final var factory = createSecureXmlFactory();
    XMLStreamReader reader = null;
    try (var fis = Files.newInputStream(xmlFile)) {
      reader = factory.createXMLStreamReader(fis);
      final var ctx = new ParseContext();
      while (reader.hasNext()) {
        final int event = reader.next();
        switch (event) {
          case XMLStreamConstants.START_ELEMENT -> handleStartElement(reader, result, ctx);
          case XMLStreamConstants.CHARACTERS -> handleCharacters(reader, ctx);
          case XMLStreamConstants.END_ELEMENT -> handleEndElement(reader, result, ctx);
          default -> {
            // no-op
          }
        }
      }
      applyFailureCountsIfMissing(result, ctx);
      return true;
    } catch (java.io.IOException | XMLStreamException e) {
      Logger.error(
          MessageSource.getMessage(
              "report.parser.parse_failed", xmlFile.toAbsolutePath(), e.getMessage()));
      return false;
    } finally {
      closeReader(reader);
    }
  }

  private XMLInputFactory createSecureXmlFactory() {
    final var factory = XMLInputFactory.newInstance();
    // Security: Disable external entities
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    return factory;
  }

  private void handleStartElement(
      final XMLStreamReader reader, final ReportTaskResult result, final ParseContext ctx) {
    final var currentElement = reader.getLocalName();
    final boolean isTestSuite = TAG_TESTSUITE.equals(currentElement);
    if (isTestSuite && ctx.testsuiteDepth == 0) {
      parseTestSuiteAttributes(reader, result);
    }
    if (isTestSuite) {
      ctx.testsuiteDepth++;
      ctx.currentSuiteName = reader.getAttributeValue(null, "name");
    } else if (TAG_TESTCASE.equals(currentElement)) {
      ctx.currentTestMethod = reader.getAttributeValue(null, "name");
    } else if (isFailureOrError(currentElement)) {
      ctx.inFailureOrError = true;
      ctx.failureTag = currentElement;
      ctx.failureMessage = reader.getAttributeValue(null, "message");
      ctx.clearFailureText();
    }
  }

  private void parseTestSuiteAttributes(
      final XMLStreamReader reader, final ReportTaskResult result) {
    final int tests = AggregatorUtils.parseIntSafe(reader.getAttributeValue(null, "tests"));
    final int failures = AggregatorUtils.parseIntSafe(reader.getAttributeValue(null, "failures"));
    final int errors = AggregatorUtils.parseIntSafe(reader.getAttributeValue(null, "errors"));
    final int skipped = AggregatorUtils.parseIntSafe(reader.getAttributeValue(null, "skipped"));
    result.setTestsRun(result.getTestsRun() + tests);
    result.setTestsFailed(result.getTestsFailed() + failures);
    result.setTestsError(result.getTestsError() + errors);
    result.setTestsSkipped(result.getTestsSkipped() + skipped);
  }

  private void handleEndElement(
      final XMLStreamReader reader, final ReportTaskResult result, final ParseContext ctx) {
    final var endElement = reader.getLocalName();
    if (isFailureOrError(endElement) && ctx.inFailureOrError) {
      final var detail = createFailureDetail(ctx);
      result.addFailureDetail(detail);
      if (TAG_FAILURE.equals(ctx.failureTag)) {
        ctx.failureCount++;
      } else if (TAG_ERROR.equals(ctx.failureTag)) {
        ctx.errorCount++;
      }
      ctx.reset();
    } else if (TAG_TESTCASE.equals(endElement)) {
      ctx.currentTestMethod = null;
    } else if (TAG_TESTSUITE.equals(endElement)) {
      ctx.testsuiteDepth = Math.max(0, ctx.testsuiteDepth - 1);
    }
  }

  private void handleCharacters(final XMLStreamReader reader, final ParseContext ctx) {
    if (ctx.inFailureOrError) {
      ctx.appendFailureText(reader.getText());
    }
  }

  private boolean isFailureOrError(final String element) {
    return TAG_FAILURE.equals(element) || TAG_ERROR.equals(element);
  }

  private ReportTaskResult.FailureDetail createFailureDetail(final ParseContext ctx) {
    final var detail = new ReportTaskResult.FailureDetail();
    String testMethod = ctx.currentTestMethod;
    if (testMethod == null || testMethod.isBlank()) {
      testMethod =
          (ctx.currentSuiteName != null && !ctx.currentSuiteName.isBlank())
              ? ctx.currentSuiteName
              : MessageSource.getMessage("report.parser.unknown_test");
    }
    detail.setTestMethod(testMethod);
    final var text = ctx.failureText().trim();
    final var head =
        (ctx.failureMessage != null && !ctx.failureMessage.isBlank()) ? ctx.failureMessage : text;
    if (head.isBlank()) {
      detail.setMessageHead(
          MessageSource.getMessage("report.parser.unknown_failure_tag", ctx.failureTag));
    } else {
      detail.setMessageHead(head.split("\n")[0]);
    }
    return detail;
  }

  private void closeReader(final XMLStreamReader reader) {
    if (reader != null) {
      try {
        reader.close();
      } catch (Exception e) {
        Logger.debug(MessageSource.getMessage("report.parser.close_reader_failed", e.getMessage()));
      }
    }
  }

  /** Internal context for tracking parse state. */
  private static final class ParseContext {

    int testsuiteDepth;

    String currentTestMethod;

    String currentSuiteName;

    boolean inFailureOrError;

    String failureTag = "";

    String failureMessage;

    final List<String> failureText = new ArrayList<>();

    int failureCount;

    int errorCount;

    void reset() {
      inFailureOrError = false;
      failureMessage = null;
      clearFailureText();
    }

    void clearFailureText() {
      failureText.clear();
    }

    void appendFailureText(final String text) {
      if (text != null && !text.isEmpty()) {
        failureText.add(text);
      }
    }

    String failureText() {
      return String.join("", failureText);
    }
  }

  private void applyFailureCountsIfMissing(final ReportTaskResult result, final ParseContext ctx) {
    if (result.getTestsFailed() == 0
        && result.getTestsError() == 0
        && (ctx.failureCount > 0 || ctx.errorCount > 0)) {
      result.setTestsFailed(ctx.failureCount);
      result.setTestsError(ctx.errorCount);
    }
    final int minimumRun = ctx.failureCount + ctx.errorCount;
    if (result.getTestsRun() == 0 && minimumRun > 0) {
      result.setTestsRun(minimumRun);
    }
  }
}
