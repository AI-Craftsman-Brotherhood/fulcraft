package com.craftsmanbro.fulcraft.infrastructure.parser.impl.xml;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.StaticAnalysisSummary;
import com.craftsmanbro.fulcraft.infrastructure.xml.contract.XmlSecurityFactoryPort;
import com.craftsmanbro.fulcraft.infrastructure.xml.impl.DefaultXmlSecurityFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Parses static analysis reports from various tools (SpotBugs, PMD, Checkstyle).
 *
 * <p>Supports multiple report formats and normalizes findings to a common severity scale.
 */
public class StaticAnalysisXmlReportParser {

  private static final String TOOL_SPOTBUGS = "spotbugs";

  private static final String TOOL_PMD = "pmd";

  private static final String TOOL_CHECKSTYLE = "checkstyle";

  private static final String FINDINGS_LITERAL = " findings";

  private final XmlSecurityFactoryPort xmlSecurityFactory;

  public StaticAnalysisXmlReportParser() {
    this(new DefaultXmlSecurityFactory());
  }

  public StaticAnalysisXmlReportParser(final XmlSecurityFactoryPort xmlSecurityFactory) {
    this.xmlSecurityFactory =
        Objects.requireNonNull(
            xmlSecurityFactory,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "xmlSecurityFactory"));
  }

  /**
   * Parse a static analysis report file.
   *
   * @param reportPath Path to the report file
   * @param tool The tool that generated the report (spotbugs, pmd, checkstyle)
   * @return StaticAnalysisSummary with extracted findings
   * @throws IOException if the file cannot be read or parsed
   */
  public StaticAnalysisSummary parse(final Path reportPath, final String tool) throws IOException {
    Objects.requireNonNull(
        reportPath,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "reportPath"));
    Objects.requireNonNull(
        tool,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "tool"));
    if (!Files.exists(reportPath)) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Static analysis report not found: " + reportPath));
    }
    Logger.info(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message",
            "Parsing static analysis report: " + reportPath + " (tool: " + tool + ")"));
    final String toolLower = tool.toLowerCase(Locale.ROOT);
    return switch (toolLower) {
      case TOOL_SPOTBUGS -> parseSpotBugsXml(reportPath);
      case TOOL_PMD -> parsePmdXml(reportPath);
      case TOOL_CHECKSTYLE -> parseCheckstyleXml(reportPath);
      default ->
          throw new IOException(
              com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                  "infra.common.error.message", "Unsupported static analysis tool: " + tool));
    };
  }

  /**
   * Parse multiple report files and merge results, skipping files that fail to parse.
   *
   * @param reportPaths List of report path and tool pairs
   * @return Merged StaticAnalysisSummary
   */
  public StaticAnalysisSummary parseMultiple(final List<ReportSpec> reportPaths) {
    final StaticAnalysisSummary merged = new StaticAnalysisSummary();
    for (final ReportSpec spec : reportPaths) {
      try {
        final StaticAnalysisSummary result = parse(spec.path(), spec.tool());
        merged.merge(result);
      } catch (IOException e) {
        Logger.warn(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.log.message",
                "Failed to parse report " + spec.path() + ": " + e.getMessage()));
        // Continue with other reports
      }
    }
    return merged;
  }

  /** Parse SpotBugs XML report. */
  private StaticAnalysisSummary parseSpotBugsXml(final Path reportPath) throws IOException {
    final StaticAnalysisSummary summary = new StaticAnalysisSummary();
    summary.addTool(TOOL_SPOTBUGS);
    final Document doc = parseXml(reportPath);
    final NodeList bugInstances = doc.getElementsByTagName("BugInstance");
    for (int i = 0; i < bugInstances.getLength(); i++) {
      final Element bug = (Element) bugInstances.item(i);
      final StaticAnalysisSummary.Finding finding = new StaticAnalysisSummary.Finding();
      finding.setTool(TOOL_SPOTBUGS);
      finding.setRuleId(bug.getAttribute("type"));
      finding.setMessage(extractSpotBugsMessage(bug));
      // SpotBugs priority: 1=high, 2=normal, 3=low
      final int priority = parseIntAttr(bug, "priority", 3);
      finding.setSeverity(mapSpotBugsPriority(priority));
      // Extract source location
      final NodeList sources = bug.getElementsByTagName("SourceLine");
      final Element source = selectPrimarySourceLine(sources);
      if (source != null) {
        finding.setClassName(source.getAttribute("classname"));
        finding.setFilePath(source.getAttribute("sourcepath"));
        finding.setLineNumber(parseIntAttr(source, "start", 0));
      }
      summary.addFinding(finding);
    }
    Logger.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message",
            "SpotBugs: found " + summary.getTotalCount() + FINDINGS_LITERAL));
    return summary;
  }

  /** Parse PMD XML report. */
  private StaticAnalysisSummary parsePmdXml(final Path reportPath) throws IOException {
    final StaticAnalysisSummary summary = new StaticAnalysisSummary();
    summary.addTool(TOOL_PMD);
    final Document doc = parseXml(reportPath);
    final NodeList files = doc.getElementsByTagName("file");
    for (int i = 0; i < files.getLength(); i++) {
      final Element file = (Element) files.item(i);
      final String filePath = file.getAttribute("name");
      final NodeList violations = file.getElementsByTagName("violation");
      for (int j = 0; j < violations.getLength(); j++) {
        final Element violation = (Element) violations.item(j);
        final StaticAnalysisSummary.Finding finding = new StaticAnalysisSummary.Finding();
        finding.setTool(TOOL_PMD);
        finding.setFilePath(filePath);
        finding.setRuleId(violation.getAttribute("rule"));
        finding.setMessage(violation.getTextContent().trim());
        finding.setLineNumber(parseIntAttr(violation, "beginline", 0));
        finding.setClassName(violation.getAttribute("class"));
        // PMD priority: 1=high, 5=low
        final int priority = parseIntAttr(violation, "priority", 3);
        finding.setSeverity(mapPmdPriority(priority));
        summary.addFinding(finding);
      }
    }
    Logger.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message",
            "PMD: found " + summary.getTotalCount() + FINDINGS_LITERAL));
    return summary;
  }

  /** Parse Checkstyle XML report. */
  private StaticAnalysisSummary parseCheckstyleXml(final Path reportPath) throws IOException {
    final StaticAnalysisSummary summary = new StaticAnalysisSummary();
    summary.addTool(TOOL_CHECKSTYLE);
    final Document doc = parseXml(reportPath);
    final NodeList files = doc.getElementsByTagName("file");
    for (int i = 0; i < files.getLength(); i++) {
      final Element file = (Element) files.item(i);
      final String filePath = file.getAttribute("name");
      final NodeList errors = file.getElementsByTagName("error");
      for (int j = 0; j < errors.getLength(); j++) {
        final Element error = (Element) errors.item(j);
        final StaticAnalysisSummary.Finding finding = new StaticAnalysisSummary.Finding();
        finding.setTool(TOOL_CHECKSTYLE);
        finding.setFilePath(filePath);
        finding.setRuleId(error.getAttribute("source"));
        finding.setMessage(error.getAttribute("message"));
        finding.setLineNumber(parseIntAttr(error, "line", 0));
        // Checkstyle severity: error, warning, info
        final String severity = error.getAttribute("severity");
        finding.setSeverity(mapCheckstyleSeverity(severity));
        summary.addFinding(finding);
      }
    }
    Logger.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message",
            "Checkstyle: found " + summary.getTotalCount() + FINDINGS_LITERAL));
    return summary;
  }

  /** Parse XML document with security features enabled. */
  private Document parseXml(final Path path) throws IOException {
    try {
      final DocumentBuilderFactory factory = xmlSecurityFactory.createDocumentBuilderFactory();
      final DocumentBuilder builder = factory.newDocumentBuilder();
      final Document doc = builder.parse(path.toFile());
      doc.getDocumentElement().normalize();
      return doc;
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to parse XML: " + e.getMessage()),
          e);
    }
  }

  /** Map SpotBugs priority to normalized severity. */
  private String mapSpotBugsPriority(final int priority) {
    return switch (priority) {
      case 1 -> StaticAnalysisSummary.SEVERITY_CRITICAL;
      case 2 -> StaticAnalysisSummary.SEVERITY_MAJOR;
      default -> StaticAnalysisSummary.SEVERITY_MINOR;
    };
  }

  /** Map PMD priority to normalized severity. */
  private String mapPmdPriority(final int priority) {
    return switch (priority) {
      case 1 -> StaticAnalysisSummary.SEVERITY_BLOCKER;
      case 2 -> StaticAnalysisSummary.SEVERITY_CRITICAL;
      case 3 -> StaticAnalysisSummary.SEVERITY_MAJOR;
      case 4 -> StaticAnalysisSummary.SEVERITY_MINOR;
      default -> StaticAnalysisSummary.SEVERITY_INFO;
    };
  }

  /** Map Checkstyle severity to normalized severity. */
  private String mapCheckstyleSeverity(final String severity) {
    if (severity == null) {
      return StaticAnalysisSummary.SEVERITY_INFO;
    }
    return switch (severity.toLowerCase(Locale.ROOT)) {
      case "error" -> StaticAnalysisSummary.SEVERITY_MAJOR;
      case "warning" -> StaticAnalysisSummary.SEVERITY_MINOR;
      default -> StaticAnalysisSummary.SEVERITY_INFO;
    };
  }

  /** Parse integer attribute with default value. */
  private int parseIntAttr(final Element element, final String attrName, final int defaultValue) {
    final String value = element.getAttribute(attrName);
    if (value.isEmpty()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private String extractSpotBugsMessage(final Element bug) {
    String message = bug.getAttribute("shortMessage");
    if (message == null || message.isBlank()) {
      message = getChildText(bug, "ShortMessage");
    }
    if (message == null || message.isBlank()) {
      message = getChildText(bug, "LongMessage");
    }
    return message == null ? "" : message.trim();
  }

  private String getChildText(final Element parent, final String tagName) {
    final NodeList nodes = parent.getElementsByTagName(tagName);
    if (nodes.getLength() == 0) {
      return null;
    }
    return nodes.item(0).getTextContent();
  }

  private Element selectPrimarySourceLine(final NodeList sources) {
    if (sources == null || sources.getLength() == 0) {
      return null;
    }
    for (int i = 0; i < sources.getLength(); i++) {
      final Element candidate = (Element) sources.item(i);
      final String primary = candidate.getAttribute("primary");
      if ("true".equalsIgnoreCase(primary)) {
        return candidate;
      }
    }
    return (Element) sources.item(0);
  }

  /** Specification for a report file and its tool. */
  public record ReportSpec(Path path, String tool) {

    public ReportSpec {
      Objects.requireNonNull(
          path,
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.argument_null", "path"));
      Objects.requireNonNull(
          tool,
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.argument_null", "tool"));
    }
  }
}
