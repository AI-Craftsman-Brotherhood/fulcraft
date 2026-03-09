package com.craftsmanbro.fulcraft.plugins.reporting.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportFormat;
import org.junit.jupiter.api.Test;

class ReportWriterPortTest {

  @Test
  void defaultGetFormat_returnsMarkdown() {
    ReportWriterPort writer = new DefaultReportWriter();

    assertEquals(ReportFormat.MARKDOWN, writer.getFormat());
  }

  @Test
  void overriddenGetFormat_returnsCustomValue() {
    ReportWriterPort writer = new JsonReportWriter();

    assertEquals(ReportFormat.JSON, writer.getFormat());
  }

  private static final class DefaultReportWriter implements ReportWriterPort {

    @Override
    public void writeReport(ReportData data, Config config) throws ReportWriteException {
      // no-op test stub
    }
  }

  private static final class JsonReportWriter implements ReportWriterPort {

    @Override
    public ReportFormat getFormat() {
      return ReportFormat.JSON;
    }

    @Override
    public void writeReport(ReportData data, Config config) throws ReportWriteException {
      // no-op test stub
    }
  }
}
