package com.craftsmanbro.fulcraft.infrastructure.parser.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Summary of static analysis findings from tools like SpotBugs, PMD, ErrorProne, etc.
 *
 * <p>Findings are categorized by severity level for quality gate evaluation.
 */
public class StaticAnalysisSummary {

  // Severity levels following common conventions
  public static final String SEVERITY_BLOCKER = "BLOCKER";

  public static final String SEVERITY_CRITICAL = "CRITICAL";

  public static final String SEVERITY_MAJOR = "MAJOR";

  public static final String SEVERITY_MINOR = "MINOR";

  public static final String SEVERITY_INFO = "INFO";

  @JsonProperty("blocker_count")
  private int blockerCount;

  @JsonProperty("critical_count")
  private int criticalCount;

  @JsonProperty("major_count")
  private int majorCount;

  @JsonProperty("minor_count")
  private int minorCount;

  @JsonProperty("info_count")
  private int infoCount;

  @JsonProperty("tools_used")
  private List<String> toolsUsed = new ArrayList<>();

  @JsonProperty("findings")
  private List<Finding> findings = new ArrayList<>();

  // === Getters ===
  public int getBlockerCount() {
    return blockerCount;
  }

  public int getCriticalCount() {
    return criticalCount;
  }

  public int getMajorCount() {
    return majorCount;
  }

  public int getMinorCount() {
    return minorCount;
  }

  public int getInfoCount() {
    return infoCount;
  }

  public List<String> getToolsUsed() {
    return Collections.unmodifiableList(toolsUsed);
  }

  public List<Finding> getFindings() {
    return Collections.unmodifiableList(findings);
  }

  // === Setters ===
  public void setBlockerCount(final int blockerCount) {
    this.blockerCount = blockerCount;
  }

  public void setCriticalCount(final int criticalCount) {
    this.criticalCount = criticalCount;
  }

  public void setMajorCount(final int majorCount) {
    this.majorCount = majorCount;
  }

  public void setMinorCount(final int minorCount) {
    this.minorCount = minorCount;
  }

  public void setInfoCount(final int infoCount) {
    this.infoCount = infoCount;
  }

  public void setToolsUsed(final List<String> toolsUsed) {
    this.toolsUsed = toolsUsed != null ? new ArrayList<>(toolsUsed) : new ArrayList<>();
  }

  public void setFindings(final List<Finding> findings) {
    this.findings = findings != null ? new ArrayList<>(findings) : new ArrayList<>();
    if (!hasExplicitSeverityCounts()) {
      recalculateSeverityCountsFromFindings();
    }
  }

  // === Computed Values ===
  /** Total number of all findings. */
  @JsonProperty("total_count")
  public int getTotalCount() {
    return blockerCount + criticalCount + majorCount + minorCount + infoCount;
  }

  /** Count of blocker and critical findings combined. */
  @JsonProperty("high_severity_count")
  public int getHighSeverityCount() {
    return blockerCount + criticalCount;
  }

  /** Returns true if there are any blocker-level findings. */
  public boolean hasBlockers() {
    return blockerCount > 0;
  }

  /** Returns true if there are any critical-level findings. */
  public boolean hasCriticals() {
    return criticalCount > 0;
  }

  /** Returns true if there are any blocker or critical findings. */
  public boolean hasHighSeverity() {
    return hasBlockers() || hasCriticals();
  }

  // === Increment Methods ===
  public void incrementBlockerCount() {
    this.blockerCount++;
  }

  public void incrementCriticalCount() {
    this.criticalCount++;
  }

  public void incrementMajorCount() {
    this.majorCount++;
  }

  public void incrementMinorCount() {
    this.minorCount++;
  }

  public void incrementInfoCount() {
    this.infoCount++;
  }

  /** Increment count by severity name. */
  public void incrementBySeverity(final String severity) {
    if (severity == null) {
      return;
    }
    switch (severity.toUpperCase(java.util.Locale.ROOT)) {
      case SEVERITY_BLOCKER -> incrementBlockerCount();
      case SEVERITY_CRITICAL -> incrementCriticalCount();
      case SEVERITY_MAJOR -> incrementMajorCount();
      case SEVERITY_MINOR -> incrementMinorCount();
      case SEVERITY_INFO -> incrementInfoCount();
      default -> incrementInfoCount();
    }
  }

  public void addTool(final String tool) {
    if (tool != null && !tool.isBlank() && !toolsUsed.contains(tool)) {
      toolsUsed.add(tool);
    }
  }

  public void addFinding(final Finding finding) {
    if (finding != null) {
      findings.add(finding);
      incrementBySeverity(finding.getSeverity());
    }
  }

  private boolean hasExplicitSeverityCounts() {
    return blockerCount != 0
        || criticalCount != 0
        || majorCount != 0
        || minorCount != 0
        || infoCount != 0;
  }

  private void recalculateSeverityCountsFromFindings() {
    blockerCount = 0;
    criticalCount = 0;
    majorCount = 0;
    minorCount = 0;
    infoCount = 0;
    for (final Finding finding : findings) {
      if (finding != null) {
        incrementBySeverity(finding.getSeverity());
      }
    }
  }

  /** Merge another analysis summary into this one. */
  public void merge(final StaticAnalysisSummary other) {
    if (other == null) {
      return;
    }
    this.blockerCount += other.blockerCount;
    this.criticalCount += other.criticalCount;
    this.majorCount += other.majorCount;
    this.minorCount += other.minorCount;
    this.infoCount += other.infoCount;
    for (final String tool : other.toolsUsed) {
      addTool(tool);
    }
    this.findings.addAll(other.findings);
  }

  /** Represents a single static analysis finding. */
  public static class Finding {

    @JsonProperty("tool")
    private String tool;

    @JsonProperty("severity")
    private String severity;

    @JsonProperty("rule_id")
    private String ruleId;

    @JsonProperty("message")
    private String message;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("line_number")
    private int lineNumber;

    @JsonProperty("class_name")
    private String className;

    public Finding() {}

    public Finding(
        final String tool, final String severity, final String ruleId, final String message) {
      this.tool = tool;
      this.severity = severity;
      this.ruleId = ruleId;
      this.message = message;
    }

    // Getters and setters
    public String getTool() {
      return tool;
    }

    public void setTool(final String tool) {
      this.tool = tool;
    }

    public String getSeverity() {
      return severity;
    }

    public void setSeverity(final String severity) {
      this.severity = severity;
    }

    public String getRuleId() {
      return ruleId;
    }

    public void setRuleId(final String ruleId) {
      this.ruleId = ruleId;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(final String message) {
      this.message = message;
    }

    public String getFilePath() {
      return filePath;
    }

    public void setFilePath(final String filePath) {
      this.filePath = filePath;
    }

    public int getLineNumber() {
      return lineNumber;
    }

    public void setLineNumber(final int lineNumber) {
      this.lineNumber = lineNumber;
    }

    public String getClassName() {
      return className;
    }

    public void setClassName(final String className) {
      this.className = className;
    }

    @Override
    public String toString() {
      return String.format("[%s] %s: %s (%s:%d)", severity, ruleId, message, filePath, lineNumber);
    }
  }

  @Override
  public String toString() {
    return String.format(
        "StaticAnalysisSummary[blocker=%d, critical=%d, major=%d, minor=%d, info=%d, tools=%s]",
        blockerCount, criticalCount, majorCount, minorCount, infoCount, toolsUsed);
  }
}
