package com.craftsmanbro.fulcraft.infrastructure.parser.impl.xml;

import com.craftsmanbro.fulcraft.infrastructure.fs.impl.PathOrder;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.xml.contract.XmlSecurityFactoryPort;
import com.craftsmanbro.fulcraft.infrastructure.xml.impl.DefaultXmlSecurityFactory;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/**
 * Parses JUnit XML test results from build/test-results.
 *
 * <p>This class is responsible for parsing JUnit XML report files and extracting detailed
 * information about test failures. It supports both standard JUnit XML format and various
 * extensions used by build tools.
 *
 * <h2>Capabilities</h2>
 *
 * <ul>
 *   <li>Parse test failures from JUnit XML files
 *   <li>Extract assertion mismatch details (expected vs actual)
 *   <li>Categorize failure types for targeted fix suggestions
 *   <li>Format failure information for LLM-based repair
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * JUnitXmlReportParser parser = new JUnitXmlReportParser();
 * List<TestFailure> failures = parser.parseTestResults(
 *     Path.of("build/test-results/test"));
 *
 * for (TestFailure failure : failures) {
 *   System.out.println(parser.formatForExchange(failure));
 *   System.out.println(parser.getFixSuggestion(failure));
 * }
 * }</pre>
 *
 * @see
 *     com.craftsmanbro.fulcraft.infrastructure.buildtool.failure.util.AssertionMismatchExtractor
 * @see
 *     com.craftsmanbro.fulcraft.infrastructure.buildtool.failure.util.FixSuggestionTemplates
 */
public class JUnitXmlReportParser {

  private static final String SKIPPED_LITERAL = "skipped";

  private static final String DISABLED_LITERAL = "disabled";

  private final XmlSecurityFactoryPort xmlSecurityFactory;

  public JUnitXmlReportParser() {
    this(new DefaultXmlSecurityFactory());
  }

  public JUnitXmlReportParser(final XmlSecurityFactoryPort xmlSecurityFactory) {
    this.xmlSecurityFactory =
        Objects.requireNonNull(
            xmlSecurityFactory,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "xmlSecurityFactory"));
  }

  /**
   * Summary of a test run aggregated from JUnit XML.
   *
   * @param total total number of tests
   * @param failures number of assertion failures
   * @param errors number of errors
   * @param skipped number of skipped tests
   */
  public record TestRunSummary(int total, int failures, int errors, int skipped) {

    public int passed() {
      return Math.max(0, total - failures - errors - skipped);
    }

    public int failed() {
      return failures + errors;
    }
  }

  /**
   * Test failure record containing all extracted information.
   *
   * @param testClass the fully qualified test class name
   * @param testMethod the test method name
   * @param failureType the type of failure (e.g., AssertionError, Error)
   * @param failureMessage the failure message
   * @param stackTrace the full stack trace
   * @param mismatchDetails extracted mismatch details (if available)
   * @param reportContent the raw XML content of the testcase element
   */
  public record TestFailure(
      @JsonProperty("test_class") String testClass,
      @JsonProperty("test_method") String testMethod,
      @JsonProperty("failure_type") String failureType,
      @JsonProperty("failure_message") String failureMessage,
      @JsonProperty("stack_trace") String stackTrace,
      @JsonProperty("report_content") String reportContent) {}

  /**
   * Parse test failures from JUnit XML files in a directory.
   *
   * <p>This is the main entry point for parsing test results. It scans the specified directory for
   * XML files and extracts failure information from each one.
   *
   * @param testResultsDir the directory containing JUnit XML report files
   * @return list of test failures found (empty list if directory doesn't exist)
   */
  public List<TestFailure> parseTestResults(final Path testResultsDir) {
    final List<TestFailure> failures = new ArrayList<>();
    if (!Files.exists(testResultsDir)) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "[JUnitXmlParser] Test results directory not found: " + testResultsDir));
      return failures;
    }
    try (var paths = Files.list(testResultsDir)) {
      paths
          .filter(p -> p.toString().endsWith(".xml"))
          .sorted(PathOrder.STABLE)
          .forEach(
              xmlFile -> {
                try {
                  failures.addAll(parseXmlFile(xmlFile));
                } catch (IOException | ParserConfigurationException | SAXException e) {
                  Logger.warn(
                      com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                          "infra.common.log.message",
                          "[JUnitXmlParser] Error parsing " + xmlFile + ": " + e.getMessage()));
                }
              });
    } catch (IOException e) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "[JUnitXmlParser] Error listing test results: " + e.getMessage()));
    }
    Logger.info(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message",
            "[JUnitXmlParser] Found " + failures.size() + " test failures"));
    return failures;
  }

  /**
   * Alias for parseTestResults for API compatibility.
   *
   * @param xmlReportDir the directory containing JUnit XML report files
   * @return list of test failures found
   */
  public List<TestFailure> parseFailures(final Path xmlReportDir) {
    return parseTestResults(xmlReportDir);
  }

  /**
   * Parse aggregate test counts from JUnit XML files in a directory.
   *
   * @param testResultsDir the directory containing JUnit XML report files
   * @return test run summary, or null if no reports are found
   */
  public TestRunSummary parseTestSummary(final Path testResultsDir) {
    if (!Files.exists(testResultsDir)) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "[JUnitXmlParser] Test results directory not found: " + testResultsDir));
      return null;
    }
    int total = 0;
    int failures = 0;
    int errors = 0;
    int skipped = 0;
    boolean foundReport = false;
    try (var paths = Files.list(testResultsDir)) {
      final List<Path> reportFiles =
          paths.filter(p -> p.toString().endsWith(".xml")).sorted(PathOrder.STABLE).toList();
      for (final Path xmlFile : reportFiles) {
        final TestRunSummary summary = parseSummaryXmlFileSafely(xmlFile);
        if (summary != null) {
          foundReport = true;
          total += summary.total();
          failures += summary.failures();
          errors += summary.errors();
          skipped += summary.skipped();
        }
      }
    } catch (IOException e) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "[JUnitXmlParser] Error listing test results: " + e.getMessage()));
      return null;
    }
    if (!foundReport) {
      return null;
    }
    return new TestRunSummary(total, failures, errors, skipped);
  }

  private List<TestFailure> parseXmlFile(final Path xmlFile)
      throws IOException, ParserConfigurationException, SAXException {
    final List<TestFailure> failures = new ArrayList<>();
    final DocumentBuilderFactory factory = xmlSecurityFactory.createDocumentBuilderFactory();
    final DocumentBuilder builder = factory.newDocumentBuilder();
    final Document doc = builder.parse(xmlFile.toFile());
    final Element root = doc.getDocumentElement();
    if (root == null) {
      return failures;
    }
    if (isTagNamed(root, "testsuite")) {
      collectFailuresFromSuite(root, failures, root.getAttribute("name"));
      return failures;
    }
    for (final Element testsuite : directChildElements(root, "testsuite")) {
      collectFailuresFromSuite(testsuite, failures, testsuite.getAttribute("name"));
    }
    return failures;
  }

  private void collectFailuresFromSuite(
      final Element testsuite, final List<TestFailure> failures, final String inheritedSuiteName) {
    final String suiteClassName =
        firstNonBlank(testsuite.getAttribute("name"), inheritedSuiteName);
    for (final Element testcase : directChildElements(testsuite, "testcase")) {
      final String methodName = testcase.getAttribute("name");
      final String className = firstNonBlank(testcase.getAttribute("classname"), suiteClassName);
      addFailureIfPresent(testcase, className, methodName, "failure", "AssertionError", failures);
      addFailureIfPresent(testcase, className, methodName, "error", "Error", failures);
    }
    for (final Element nestedSuite : directChildElements(testsuite, "testsuite")) {
      collectFailuresFromSuite(nestedSuite, failures, suiteClassName);
    }
  }

  private void addFailureIfPresent(
      final Element testcase,
      final String className,
      final String methodName,
      final String tagName,
      final String defaultType,
      final List<TestFailure> failures) {
    final Element failureElement = firstDirectChildElement(testcase, tagName);
    if (failureElement != null) {
      failures.add(
          parseFailureElement(className, methodName, testcase, failureElement, defaultType));
    }
  }

  private TestRunSummary parseSummaryXmlFile(final Path xmlFile)
      throws IOException, ParserConfigurationException, SAXException {
    final DocumentBuilderFactory factory = xmlSecurityFactory.createDocumentBuilderFactory();
    final DocumentBuilder builder = factory.newDocumentBuilder();
    final Document doc = builder.parse(xmlFile.toFile());
    final Element root = doc.getDocumentElement();
    if (root == null) {
      return null;
    }
    final SummaryAccumulator accumulator = new SummaryAccumulator();
    if (isTagNamed(root, "testsuite")) {
      accumulateSuiteSummary(root, accumulator);
    } else {
      final List<Element> testsuites = directChildElements(root, "testsuite");
      if (!testsuites.isEmpty()) {
        for (final Element testsuite : testsuites) {
          accumulateSuiteSummary(testsuite, accumulator);
        }
      } else if (isTagNamed(root, "testsuites")) {
        accumulator.addAttributeSummary(root);
      }
    }
    return accumulator.foundSummary() ? accumulator.toSummary() : null;
  }

  private void accumulateSuiteSummary(
      final Element testsuite, final SummaryAccumulator accumulator) {
    final List<Element> testcases = directChildElements(testsuite, "testcase");
    for (final Element testcase : testcases) {
      accumulator.incrementTotal();
      if (hasDirectChildElement(testcase, "failure")) {
        accumulator.incrementFailures();
      } else if (hasDirectChildElement(testcase, "error")) {
        accumulator.incrementErrors();
      } else if (isSkipped(testcase)) {
        accumulator.incrementSkipped();
      }
    }
    final List<Element> nestedSuites = directChildElements(testsuite, "testsuite");
    for (final Element nestedSuite : nestedSuites) {
      accumulateSuiteSummary(nestedSuite, accumulator);
    }
    if (testcases.isEmpty() && nestedSuites.isEmpty()) {
      accumulator.addAttributeSummary(testsuite);
    }
  }

  private List<Element> directChildElements(final Element parent, final String tagName) {
    final List<Element> elements = new ArrayList<>();
    final NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      final Node child = children.item(i);
      if (child instanceof Element element && tagName.equals(element.getTagName())) {
        elements.add(element);
      }
    }
    return elements;
  }

  private Element firstDirectChildElement(final Element parent, final String tagName) {
    final NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      final Node child = children.item(i);
      if (child instanceof Element element && tagName.equals(element.getTagName())) {
        return element;
      }
    }
    return null;
  }

  private boolean hasDirectChildElement(final Element parent, final String tagName) {
    return firstDirectChildElement(parent, tagName) != null;
  }

  private boolean isTagNamed(final Element element, final String tagName) {
    return tagName.equals(element.getTagName());
  }

  private String firstNonBlank(final String primary, final String fallback) {
    if (primary != null && !primary.isBlank()) {
      return primary;
    }
    return fallback;
  }

  private boolean isSkipped(final Element testcase) {
    if (hasDirectChildElement(testcase, SKIPPED_LITERAL)) {
      return true;
    }
    final String status = testcase.getAttribute("status");
    if (status == null || status.isBlank()) {
      return false;
    }
    final String normalized = status.trim().toLowerCase(Locale.ROOT);
    return SKIPPED_LITERAL.equals(normalized) || DISABLED_LITERAL.equals(normalized);
  }

  private TestFailure parseFailureElement(
      final String className,
      final String methodName,
      final Element testcaseElement,
      final Element failureElement,
      final String defaultType) {
    String failureType = failureElement.getAttribute("type");
    if (failureType.isEmpty()) {
      failureType = defaultType;
    }
    String message = failureElement.getAttribute("message");
    final String stackTrace = failureElement.getTextContent();
    // If message is empty, try to extract from stack trace
    if (message.isEmpty()) {
      final int firstNewline = stackTrace.indexOf('\n');
      if (firstNewline > 0) {
        message = stackTrace.substring(0, firstNewline).trim();
      } else {
        message = stackTrace.trim();
      }
    }
    final String reportContent = elementToString(testcaseElement);
    return new TestFailure(className, methodName, failureType, message, stackTrace, reportContent);
  }

  // Note: getFixSuggestion() and formatForExchange() have been moved to
  // FailureAnalysisService in the infrastructure buildtool layer.
  private String elementToString(final Element element) {
    if (element == null) {
      return null;
    }
    try {
      final javax.xml.transform.TransformerFactory tf =
          xmlSecurityFactory.createTransformerFactory();
      final javax.xml.transform.Transformer transformer = tf.newTransformer();
      transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "no");
      final javax.xml.transform.dom.DOMSource domSource =
          new javax.xml.transform.dom.DOMSource(element);
      final java.io.StringWriter writer = new java.io.StringWriter();
      final javax.xml.transform.stream.StreamResult result =
          new javax.xml.transform.stream.StreamResult(writer);
      transformer.transform(domSource, result);
      return writer.toString();
    } catch (TransformerException e) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "[JUnitXmlParser] Failed to serialize XML element: " + e.getMessage()));
      return null;
    }
  }

  private TestRunSummary parseSummaryXmlFileSafely(final Path xmlFile) {
    try {
      return parseSummaryXmlFile(xmlFile);
    } catch (IOException | ParserConfigurationException | SAXException e) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "[JUnitXmlParser] Error parsing summary " + xmlFile + ": " + e.getMessage()));
      return null;
    }
  }

  private static final class SummaryAccumulator {

    private int total;

    private int failures;

    private int errors;

    private int skipped;

    private boolean foundSummary;

    private void incrementTotal() {
      total++;
      foundSummary = true;
    }

    private void incrementFailures() {
      failures++;
    }

    private void incrementErrors() {
      errors++;
    }

    private void incrementSkipped() {
      skipped++;
    }

    private void addAttributeSummary(final Element testsuite) {
      total += parseNonNegativeInt(testsuite, "tests");
      failures += parseNonNegativeInt(testsuite, "failures");
      errors += parseNonNegativeInt(testsuite, "errors");
      skipped += parseNonNegativeInt(testsuite, SKIPPED_LITERAL);
      skipped += parseNonNegativeInt(testsuite, DISABLED_LITERAL);
      foundSummary = true;
    }

    private boolean foundSummary() {
      return foundSummary;
    }

    private TestRunSummary toSummary() {
      return new TestRunSummary(total, failures, errors, skipped);
    }

    private static int parseNonNegativeInt(final Element element, final String name) {
      final String value = element.getAttribute(name);
      if (value == null || value.isBlank()) {
        return 0;
      }
      try {
        return Math.max(0, Integer.parseInt(value.trim()));
      } catch (NumberFormatException e) {
        return 0;
      }
    }
  }
}
