package com.craftsmanbro.fulcraft.plugins.reporting.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReportFormatTest {

  @Test
  void fromStringDefaultsToMarkdown() {
    assertEquals(ReportFormat.MARKDOWN, ReportFormat.fromString(null));
    assertEquals(ReportFormat.MARKDOWN, ReportFormat.fromString("  "));
  }

  @Test
  void fromStringSupportsAliases() {
    assertEquals(ReportFormat.MARKDOWN, ReportFormat.fromString("MD"));
    assertEquals(ReportFormat.HTML, ReportFormat.fromString("htm"));
    assertEquals(ReportFormat.JSON, ReportFormat.fromString("Json"));
    assertEquals(ReportFormat.TEXT, ReportFormat.fromString("plain"));
    assertEquals(ReportFormat.SARIF, ReportFormat.fromString("sarif.json"));
    assertEquals(ReportFormat.YAML, ReportFormat.fromString("yml"));
  }

  @Test
  void fromStringRejectsUnknownFormat() {
    assertThrows(IllegalArgumentException.class, () -> ReportFormat.fromString("docx"));
  }

  @Test
  void fromStringOrDefaultReturnsFallbackOnUnknown() {
    assertEquals(ReportFormat.HTML, ReportFormat.fromStringOrDefault("docx", ReportFormat.HTML));
  }

  @Test
  void formatMetadataProvidesDefaults() {
    assertEquals("summary.json", ReportFormat.JSON.getDefaultFilename());
    assertEquals("report.md", ReportFormat.MARKDOWN.getDefaultFilename());
    assertTrue(ReportFormat.MARKDOWN.isHumanReadable());
    assertTrue(ReportFormat.JSON.isMachineReadable());
    assertFalse(ReportFormat.JSON.isHumanReadable());
  }
}
