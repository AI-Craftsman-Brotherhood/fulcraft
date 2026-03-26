package com.craftsmanbro.fulcraft.plugins.reporting.io.contract;

import com.craftsmanbro.fulcraft.plugins.reporting.model.CoverageSummary;
import java.io.IOException;
import java.nio.file.Path;

/** Reads code coverage summaries from reports. */
public interface CoverageReader {

  CoverageSummary readCoverage(Path reportPath) throws IOException;
}
