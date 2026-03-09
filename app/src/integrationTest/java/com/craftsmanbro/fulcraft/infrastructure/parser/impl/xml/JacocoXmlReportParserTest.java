package com.craftsmanbro.fulcraft.infrastructure.parser.impl.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.CoverageSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JacocoXmlReportParserTest {

  private JacocoXmlReportParser parser;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    parser = new JacocoXmlReportParser();
  }

  @Test
  void parse_withValidReport_extractsMetrics() throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <report name="test-report">
          <counter type="INSTRUCTION" missed="100" covered="400"/>
          <counter type="BRANCH" missed="20" covered="80"/>
          <counter type="LINE" missed="10" covered="90"/>
          <counter type="METHOD" missed="5" covered="45"/>
          <counter type="CLASS" missed="2" covered="18"/>
        </report>
        """;

    CoverageSummary summary = parser.parseString(xml);

    assertEquals(400, summary.getInstructionCovered());
    assertEquals(500, summary.getInstructionTotal());
    assertEquals(0.80, summary.getInstructionCoverageRate(), 0.01);

    assertEquals(80, summary.getBranchCovered());
    assertEquals(100, summary.getBranchTotal());
    assertEquals(0.80, summary.getBranchCoverageRate(), 0.01);

    assertEquals(90, summary.getLineCovered());
    assertEquals(100, summary.getLineTotal());
    assertEquals(0.90, summary.getLineCoverageRate(), 0.01);

    assertEquals(45, summary.getMethodCovered());
    assertEquals(50, summary.getMethodTotal());
    assertEquals(0.90, summary.getMethodCoverageRate(), 0.01);

    assertEquals(18, summary.getClassCovered());
    assertEquals(20, summary.getClassTotal());
    assertEquals(0.90, summary.getClassCoverageRate(), 0.01);
  }

  @Test
  void parse_withZeroTotal_returnsZeroRate() throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <report name="empty-report">
          <counter type="LINE" missed="0" covered="0"/>
          <counter type="BRANCH" missed="0" covered="0"/>
        </report>
        """;

    CoverageSummary summary = parser.parseString(xml);

    assertEquals(0.0, summary.getLineCoverageRate(), 0.01);
    assertEquals(0.0, summary.getBranchCoverageRate(), 0.01);
  }

  @Test
  void parse_withPackageLevelCounters_aggregatesCorrectly() throws IOException {
    // When counters are at package level, not report level
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <report name="test-report">
          <package name="com.example">
            <counter type="LINE" missed="10" covered="40"/>
            <counter type="BRANCH" missed="5" covered="15"/>
          </package>
          <package name="com.other">
            <counter type="LINE" missed="10" covered="40"/>
            <counter type="BRANCH" missed="5" covered="15"/>
          </package>
        </report>
        """;

    CoverageSummary summary = parser.parseString(xml);

    // Should aggregate from packages
    assertEquals(80, summary.getLineCovered());
    assertEquals(100, summary.getLineTotal());
    assertEquals(30, summary.getBranchCovered());
    assertEquals(40, summary.getBranchTotal());
  }

  @Test
  void parse_withGroupLevelCounters_aggregatesCorrectly() throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <report name="test-report">
          <group name="group-a">
            <counter type="LINE" missed="3" covered="7"/>
            <counter type="BRANCH" missed="2" covered="8"/>
          </group>
          <group name="group-b">
            <counter type="LINE" missed="4" covered="6"/>
            <counter type="BRANCH" missed="1" covered="9"/>
          </group>
        </report>
        """;

    CoverageSummary summary = parser.parseString(xml);

    assertEquals(13, summary.getLineCovered());
    assertEquals(20, summary.getLineTotal());
    assertEquals(17, summary.getBranchCovered());
    assertEquals(20, summary.getBranchTotal());
  }

  @Test
  void parse_withMissingFile_throwsIOException() {
    Path nonExistent = tempDir.resolve("nonexistent.xml");
    assertThrows(IOException.class, () -> parser.parse(nonExistent));
  }

  @Test
  void parse_withInvalidXml_throwsIOException() {
    String invalidXml = "not xml content";
    assertThrows(IOException.class, () -> parser.parseString(invalidXml));
  }

  @Test
  void parse_fromFile_extractsMetrics(@TempDir Path tempDir) throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <report name="test-report">
          <counter type="LINE" missed="20" covered="80"/>
        </report>
        """;

    Path reportFile = tempDir.resolve("jacoco.xml");
    Files.writeString(reportFile, xml);

    CoverageSummary summary = parser.parse(reportFile);

    assertEquals(80, summary.getLineCovered());
    assertEquals(100, summary.getLineTotal());
    assertEquals(0.80, summary.getLineCoverageRate(), 0.01);
  }

  @Test
  void toString_formatsSummaryCorrectly() throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <report name="test-report">
          <counter type="LINE" missed="15" covered="85"/>
          <counter type="BRANCH" missed="30" covered="70"/>
        </report>
        """;

    CoverageSummary summary = parser.parseString(xml);
    String str = summary.toString();

    assertTrue(str.contains("line=85.0%"));
    assertTrue(str.contains("branch=70.0%"));
  }

  @Test
  void parseRejectsNullInputs() {
    assertThrows(NullPointerException.class, () -> parser.parse(null));
    assertThrows(NullPointerException.class, () -> parser.parseString(null));
  }

  @Test
  void parse_withTopLevelCounters_ignoresNestedCounters() throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <report name="mixed">
          <counter type="LINE" missed="2" covered="8"/>
          <package name="com.example">
            <counter type="LINE" missed="20" covered="80"/>
          </package>
        </report>
        """;

    CoverageSummary summary = parser.parseString(xml);

    assertEquals(8, summary.getLineCovered());
    assertEquals(10, summary.getLineTotal());
  }

  @Test
  void parse_withInvalidCounterAttributes_defaultsToZero() throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <report name="invalid-attrs">
          <counter type="line" missed="x" covered="7"/>
          <counter type="BRANCH" covered="3"/>
        </report>
        """;

    CoverageSummary summary = parser.parseString(xml);

    assertEquals(7, summary.getLineCovered());
    assertEquals(7, summary.getLineTotal());
    assertEquals(3, summary.getBranchCovered());
    assertEquals(3, summary.getBranchTotal());
  }

  @Test
  void parse_withPackageAndGroupFallback_prefersPackageCounters() throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <report name="fallback-priority">
          <package name="pkg">
            <counter type="LINE" missed="1" covered="9"/>
          </package>
          <group name="grp">
            <counter type="LINE" missed="5" covered="5"/>
          </group>
        </report>
        """;

    CoverageSummary summary = parser.parseString(xml);

    assertEquals(9, summary.getLineCovered());
    assertEquals(10, summary.getLineTotal());
  }
}
