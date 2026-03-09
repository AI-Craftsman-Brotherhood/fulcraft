package com.craftsmanbro.fulcraft.plugins.reporting.core.service.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.craftsmanbro.fulcraft.plugins.reporting.model.CoverageSummary;
import com.craftsmanbro.fulcraft.plugins.reporting.model.GenerationSummary;
import org.junit.jupiter.api.Test;

class CoverageIntegratorTest {

  @Test
  void updateSummaryWithCoverage_setsLineAndBranchCoverage() {
    CoverageIntegrator integrator = new CoverageIntegrator();
    GenerationSummary summary = new GenerationSummary();
    CoverageSummary coverage = new CoverageSummary();
    coverage.setLineCovered(50);
    coverage.setLineTotal(200);
    coverage.setBranchCovered(3);
    coverage.setBranchTotal(4);

    integrator.updateSummaryWithCoverage(summary, coverage);

    assertEquals(0.25, summary.getLineCoverage(), 1e-9);
    assertEquals(0.75, summary.getBranchCoverage(), 1e-9);
  }

  @Test
  void updateSummaryWithCoverage_ignoresNullCoverageSummary() {
    CoverageIntegrator integrator = new CoverageIntegrator();
    GenerationSummary summary = new GenerationSummary();
    summary.setLineCoverage(0.5);
    summary.setBranchCoverage(0.25);

    integrator.updateSummaryWithCoverage(summary, null);

    assertEquals(0.5, summary.getLineCoverage(), 1e-9);
    assertEquals(0.25, summary.getBranchCoverage(), 1e-9);
  }

  @Test
  void updateSummaryWithCoverage_skipsCoverageWhenTotalsZero() {
    CoverageIntegrator integrator = new CoverageIntegrator();
    GenerationSummary summary = new GenerationSummary();
    CoverageSummary coverage = new CoverageSummary();
    coverage.setLineCovered(10);
    coverage.setLineTotal(0);
    coverage.setBranchCovered(5);
    coverage.setBranchTotal(0);

    integrator.updateSummaryWithCoverage(summary, coverage);

    assertNull(summary.getLineCoverage());
    assertNull(summary.getBranchCoverage());
  }

  @Test
  void updateSummaryWithCoverage_setsLineCoverageWhenBranchMissing() {
    CoverageIntegrator integrator = new CoverageIntegrator();
    GenerationSummary summary = new GenerationSummary();
    CoverageSummary coverage = new CoverageSummary();
    coverage.setLineCovered(1);
    coverage.setLineTotal(2);
    coverage.setBranchCovered(0);
    coverage.setBranchTotal(0);

    integrator.updateSummaryWithCoverage(summary, coverage);

    assertEquals(0.5, summary.getLineCoverage(), 1e-9);
    assertNull(summary.getBranchCoverage());
  }

  @Test
  void updateSummaryWithCoverage_preservesExistingValuesWhenTotalsZero() {
    CoverageIntegrator integrator = new CoverageIntegrator();
    GenerationSummary summary = new GenerationSummary();
    summary.setLineCoverage(0.42);
    summary.setBranchCoverage(0.33);
    CoverageSummary coverage = new CoverageSummary();
    coverage.setLineCovered(10);
    coverage.setLineTotal(0);
    coverage.setBranchCovered(3);
    coverage.setBranchTotal(0);

    integrator.updateSummaryWithCoverage(summary, coverage);

    assertEquals(0.42, summary.getLineCoverage(), 1e-9);
    assertEquals(0.33, summary.getBranchCoverage(), 1e-9);
  }

  @Test
  void updateSummaryWithCoverage_updatesBranchCoverageWhenOnlyBranchDataAvailable() {
    CoverageIntegrator integrator = new CoverageIntegrator();
    GenerationSummary summary = new GenerationSummary();
    summary.setLineCoverage(0.2);
    CoverageSummary coverage = new CoverageSummary();
    coverage.setLineCovered(0);
    coverage.setLineTotal(0);
    coverage.setBranchCovered(1);
    coverage.setBranchTotal(4);

    integrator.updateSummaryWithCoverage(summary, coverage);

    assertEquals(0.2, summary.getLineCoverage(), 1e-9);
    assertEquals(0.25, summary.getBranchCoverage(), 1e-9);
  }

  @Test
  void updateSummaryWithCoverage_rejectsNullSummary() {
    CoverageIntegrator integrator = new CoverageIntegrator();

    assertThrows(
        NullPointerException.class, () -> integrator.updateSummaryWithCoverage(null, null));
  }
}
