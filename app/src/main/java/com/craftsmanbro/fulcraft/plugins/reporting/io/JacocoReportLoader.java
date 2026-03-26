package com.craftsmanbro.fulcraft.plugins.reporting.io;

import com.craftsmanbro.fulcraft.plugins.reporting.io.adapter.JacocoXmlReportParserAdapter;
import com.craftsmanbro.fulcraft.plugins.reporting.model.CoverageSummary;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Loads JaCoCo XML reports using infrastructure parser. This class handles the I/O logic for
 * coverage.
 */
public class JacocoReportLoader {

  private final JacocoXmlReportParserAdapter parser;

  public JacocoReportLoader() {
    this(new JacocoXmlReportParserAdapter());
  }

  public JacocoReportLoader(final JacocoXmlReportParserAdapter parser) {
    this.parser =
        Objects.requireNonNull(
            parser,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "parser"));
  }

  public CoverageSummary loadCoverage(final Path reportPath) throws IOException {
    return parser.parse(reportPath);
  }
}
