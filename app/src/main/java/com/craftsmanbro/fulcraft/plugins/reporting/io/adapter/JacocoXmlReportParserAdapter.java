package com.craftsmanbro.fulcraft.plugins.reporting.io.adapter;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.xml.JacocoXmlReportParser;
import com.craftsmanbro.fulcraft.plugins.reporting.model.CoverageSummary;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public class JacocoXmlReportParserAdapter {

  private final JacocoXmlReportParser parser;

  public JacocoXmlReportParserAdapter() {
    this(new JacocoXmlReportParser());
  }

  public JacocoXmlReportParserAdapter(final JacocoXmlReportParser parser) {
    this.parser =
        Objects.requireNonNull(
            parser,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "parser"));
  }

  public CoverageSummary parse(final Path reportPath) throws IOException {
    return toFeatureCoverage(parser.parse(reportPath));
  }

  private static CoverageSummary toFeatureCoverage(
      final com.craftsmanbro.fulcraft.infrastructure.parser.model.CoverageSummary source) {
    final CoverageSummary target = new CoverageSummary();
    if (source == null) {
      return target;
    }
    target.setLineCovered(source.getLineCovered());
    target.setLineTotal(source.getLineTotal());
    target.setBranchCovered(source.getBranchCovered());
    target.setBranchTotal(source.getBranchTotal());
    target.setInstructionCovered(source.getInstructionCovered());
    target.setInstructionTotal(source.getInstructionTotal());
    target.setMethodCovered(source.getMethodCovered());
    target.setMethodTotal(source.getMethodTotal());
    target.setClassCovered(source.getClassCovered());
    target.setClassTotal(source.getClassTotal());
    return target;
  }
}
