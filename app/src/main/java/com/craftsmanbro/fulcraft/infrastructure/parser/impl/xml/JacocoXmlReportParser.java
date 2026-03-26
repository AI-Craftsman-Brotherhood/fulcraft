package com.craftsmanbro.fulcraft.infrastructure.parser.impl.xml;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.CoverageSummary;
import com.craftsmanbro.fulcraft.infrastructure.xml.contract.XmlSecurityFactoryPort;
import com.craftsmanbro.fulcraft.infrastructure.xml.impl.DefaultXmlSecurityFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Parses JaCoCo XML coverage reports to extract coverage metrics.
 *
 * <p>Supports the standard JaCoCo XML format with counter elements for instruction, branch, line,
 * method, and class coverage.
 */
public class JacocoXmlReportParser {

  private static final String COUNTER_ELEMENT = "counter";

  private static final String TYPE_ATTR = "type";

  private static final String COVERED_ATTR = "covered";

  private static final String MISSED_ATTR = "missed";

  private final XmlSecurityFactoryPort xmlSecurityFactory;

  public JacocoXmlReportParser() {
    this(new DefaultXmlSecurityFactory());
  }

  public JacocoXmlReportParser(final XmlSecurityFactoryPort xmlSecurityFactory) {
    this.xmlSecurityFactory =
        Objects.requireNonNull(
            xmlSecurityFactory,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "xmlSecurityFactory"));
  }

  /**
   * Parse a JaCoCo XML report file.
   *
   * @param reportPath Path to the JaCoCo XML report
   * @return CoverageSummary with extracted metrics
   * @throws IOException if the file cannot be read or parsed
   */
  public CoverageSummary parse(final Path reportPath) throws IOException {
    Objects.requireNonNull(
        reportPath,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "reportPath"));
    if (!Files.exists(reportPath)) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Coverage report not found: " + reportPath));
    }
    Logger.info(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message", "Parsing JaCoCo report: " + reportPath));
    try {
      final DocumentBuilderFactory factory = xmlSecurityFactory.createDocumentBuilderFactory();
      final DocumentBuilder builder = factory.newDocumentBuilder();
      final Document doc = builder.parse(reportPath.toFile());
      doc.getDocumentElement().normalize();
      return parseDocument(doc);
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to parse JaCoCo report: " + e.getMessage()),
          e);
    }
  }

  /**
   * Parse a JaCoCo XML report from string content.
   *
   * @param xmlContent The XML content as a string
   * @return CoverageSummary with extracted metrics
   * @throws IOException if parsing fails
   */
  public CoverageSummary parseString(final String xmlContent) throws IOException {
    Objects.requireNonNull(
        xmlContent,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "xmlContent"));
    try {
      final DocumentBuilderFactory factory = xmlSecurityFactory.createDocumentBuilderFactory();
      final DocumentBuilder builder = factory.newDocumentBuilder();
      final Document doc =
          builder.parse(
              new java.io.ByteArrayInputStream(
                  xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      doc.getDocumentElement().normalize();
      return parseDocument(doc);
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to parse JaCoCo XML: " + e.getMessage()),
          e);
    }
  }

  /** Parse the document and extract coverage metrics. */
  private CoverageSummary parseDocument(final Document doc) {
    CoverageSummary summary = new CoverageSummary();
    // Get root element (should be "report")
    final Element root = doc.getDocumentElement();
    // Get all counter elements at the report level
    final NodeList counters = root.getElementsByTagName(COUNTER_ELEMENT);
    boolean foundReportCounters = false;
    // Only process top-level counters (direct children of report)
    for (int i = 0; i < counters.getLength(); i++) {
      final Element counter = (Element) counters.item(i);
      // Only process if parent is the report element (skip package/class level)
      if (counter.getParentNode().equals(root)) {
        processCounter(counter, summary);
        foundReportCounters = true;
      }
    }
    // If no top-level counters, aggregate from all counters (fallback)
    if (!foundReportCounters) {
      summary = aggregateAllCounters(doc);
    }
    Logger.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message", "Parsed coverage: " + summary));
    return summary;
  }

  /** Process a single counter element. */
  private void processCounter(final Element counter, final CoverageSummary summary) {
    final String type = counter.getAttribute(TYPE_ATTR);
    final int covered = parseIntAttr(counter, COVERED_ATTR);
    final int missed = parseIntAttr(counter, MISSED_ATTR);
    final int total = covered + missed;
    switch (type.toUpperCase(java.util.Locale.ROOT)) {
      case "LINE" -> {
        summary.setLineCovered(covered);
        summary.setLineTotal(total);
      }
      case "BRANCH" -> {
        summary.setBranchCovered(covered);
        summary.setBranchTotal(total);
      }
      case "INSTRUCTION" -> {
        summary.setInstructionCovered(covered);
        summary.setInstructionTotal(total);
      }
      case "METHOD" -> {
        summary.setMethodCovered(covered);
        summary.setMethodTotal(total);
      }
      case "CLASS" -> {
        summary.setClassCovered(covered);
        summary.setClassTotal(total);
      }
      default -> {
        // Ignore unknown counter types
      }
    }
  }

  /**
   * Aggregate coverage from all counter elements in the document. Used when top-level counters are
   * not found.
   */
  private CoverageSummary aggregateAllCounters(final Document doc) {
    final CoverageSummary summary = new CoverageSummary();
    if (!aggregateCountersUnderTag(doc, "package", summary)) {
      aggregateCountersUnderTag(doc, "group", summary);
    }
    return summary;
  }

  private boolean aggregateCountersUnderTag(
      final Document doc, final String tagName, final CoverageSummary summary) {
    boolean found = false;
    final NodeList elements = doc.getElementsByTagName(tagName);
    for (int i = 0; i < elements.getLength(); i++) {
      final Element element = (Element) elements.item(i);
      final NodeList counters = element.getChildNodes();
      for (int j = 0; j < counters.getLength(); j++) {
        if (counters.item(j) instanceof Element counter
            && COUNTER_ELEMENT.equals(counter.getTagName())) {
          aggregateCounter(counter, summary);
          found = true;
        }
      }
    }
    return found;
  }

  /** Aggregate a counter element into the summary (adds to existing values). */
  private void aggregateCounter(final Element counter, final CoverageSummary summary) {
    final String type = counter.getAttribute(TYPE_ATTR);
    final int covered = parseIntAttr(counter, COVERED_ATTR);
    final int missed = parseIntAttr(counter, MISSED_ATTR);
    final int total = covered + missed;
    switch (type.toUpperCase(java.util.Locale.ROOT)) {
      case "LINE" -> {
        summary.addLineCovered(covered);
        summary.addLineTotal(total);
      }
      case "BRANCH" -> {
        summary.addBranchCovered(covered);
        summary.addBranchTotal(total);
      }
      case "INSTRUCTION" -> {
        summary.addInstructionCovered(covered);
        summary.addInstructionTotal(total);
      }
      case "METHOD" -> {
        summary.addMethodCovered(covered);
        summary.addMethodTotal(total);
      }
      case "CLASS" -> {
        summary.addClassCovered(covered);
        summary.addClassTotal(total);
      }
      default -> {
        // Ignore unknown counter types
      }
    }
  }

  /** Parse an integer attribute, returning 0 if not found or invalid. */
  private int parseIntAttr(final Element element, final String attrName) {
    final String value = element.getAttribute(attrName);
    if (value.isEmpty()) {
      return 0;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
