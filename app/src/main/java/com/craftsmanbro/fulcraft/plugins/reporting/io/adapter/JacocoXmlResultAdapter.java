package com.craftsmanbro.fulcraft.plugins.reporting.io.adapter;

import com.craftsmanbro.fulcraft.plugins.reporting.io.JacocoReportLoader;
import com.craftsmanbro.fulcraft.plugins.reporting.io.contract.CoverageReader;
import com.craftsmanbro.fulcraft.plugins.reporting.model.CoverageSummary;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Adapter that exposes JaCoCo XML parsing via the CoverageReader port. */
public class JacocoXmlResultAdapter implements CoverageReader {

  private final JacocoReportLoader loader;

  public JacocoXmlResultAdapter() {
    this(new JacocoReportLoader());
  }

  public JacocoXmlResultAdapter(final JacocoReportLoader loader) {
    this.loader =
        Objects.requireNonNull(
            loader,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "report.common.error.argument_null", "loader"));
  }

  @Override
  public CoverageSummary readCoverage(final Path reportPath) throws IOException {
    return loader.loadCoverage(reportPath);
  }
}
