package com.craftsmanbro.fulcraft.infrastructure.coverage.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CoverageReportReferenceTest {

  @Test
  void storesPathAndExplicitConfigurationFlag() {
    Path path = Path.of("build/reports/jacoco/test/jacocoTestReport.xml");

    CoverageReportReference reference = new CoverageReportReference(path, true);

    assertEquals(path, reference.path());
    assertTrue(reference.explicitlyConfigured());
  }

  @Test
  void rejectsNullPath() {
    assertThrows(NullPointerException.class, () -> new CoverageReportReference(null, false));
  }
}
