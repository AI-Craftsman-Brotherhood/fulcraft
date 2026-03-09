package com.craftsmanbro.fulcraft.infrastructure.parser.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.xml.StaticAnalysisXmlReportParser;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.StaticAnalysisSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticAnalysisXmlReportParserTest {

  private StaticAnalysisXmlReportParser parser;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    parser = new StaticAnalysisXmlReportParser();
  }

  @Test
  void parseSpotBugs_extractsFindings() throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <BugCollection>
          <BugInstance type="NP_NULL_ON_SOME_PATH" priority="1">
            <ShortMessage>Possible null pointer</ShortMessage>
            <SourceLine classname="com.example.Foo" sourcepath="Foo.java" start="11"/>
            <SourceLine classname="com.example.Foo" sourcepath="Foo.java" start="10" primary="true"/>
          </BugInstance>
          <BugInstance type="DM_STRING_CTOR" priority="2" shortMessage="Inefficient string constructor">
            <SourceLine classname="com.example.Bar" sourcepath="Bar.java" start="20"/>
          </BugInstance>
          <BugInstance type="URF_UNREAD_FIELD" priority="3" shortMessage="Unread field">
            <SourceLine classname="com.example.Baz" sourcepath="Baz.java" start="30"/>
          </BugInstance>
        </BugCollection>
        """;

    Path reportFile = tempDir.resolve("spotbugs.xml");
    Files.writeString(reportFile, xml);

    StaticAnalysisSummary summary = parser.parse(reportFile, "spotbugs");

    assertEquals(3, summary.getTotalCount());
    assertEquals(1, summary.getCriticalCount()); // priority 1 -> CRITICAL
    assertEquals(1, summary.getMajorCount()); // priority 2 -> MAJOR
    assertEquals(1, summary.getMinorCount()); // priority 3 -> MINOR
    assertTrue(summary.getToolsUsed().contains("spotbugs"));
    assertEquals("Possible null pointer", summary.getFindings().get(0).getMessage());
    assertEquals(10, summary.getFindings().get(0).getLineNumber());
  }

  @Test
  void parsePmd_extractsFindings() throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <pmd>
          <file name="src/main/java/Foo.java">
            <violation rule="UnusedVariable" priority="1" beginline="10" class="Foo">
              Unused variable 'x'
            </violation>
            <violation rule="EmptyCatchBlock" priority="2" beginline="20" class="Foo">
              Empty catch block
            </violation>
            <violation rule="MagicNumber" priority="4" beginline="30" class="Foo">
              Magic number: 42
            </violation>
          </file>
        </pmd>
        """;

    Path reportFile = tempDir.resolve("pmd.xml");
    Files.writeString(reportFile, xml);

    StaticAnalysisSummary summary = parser.parse(reportFile, "pmd");

    assertEquals(3, summary.getTotalCount());
    assertEquals(1, summary.getBlockerCount()); // priority 1 -> BLOCKER
    assertEquals(1, summary.getCriticalCount()); // priority 2 -> CRITICAL
    assertEquals(1, summary.getMinorCount()); // priority 4 -> MINOR
    assertTrue(summary.getToolsUsed().contains("pmd"));
  }

  @Test
  void parseCheckstyle_extractsFindings() throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <checkstyle>
          <file name="src/main/java/Foo.java">
            <error line="10" severity="error" source="com.puppycrawl.tools.checkstyle.checks.whitespace.WhitespaceAfterCheck"
                   message="Missing whitespace"/>
            <error line="20" severity="warning" source="com.puppycrawl.tools.checkstyle.checks.naming.LocalVariableNameCheck"
                   message="Name should match pattern"/>
            <error line="30" severity="info" source="com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocMethodCheck"
                   message="Missing javadoc"/>
          </file>
        </checkstyle>
        """;

    Path reportFile = tempDir.resolve("checkstyle.xml");
    Files.writeString(reportFile, xml);

    StaticAnalysisSummary summary = parser.parse(reportFile, "checkstyle");

    assertEquals(3, summary.getTotalCount());
    assertEquals(1, summary.getMajorCount()); // error -> MAJOR
    assertEquals(1, summary.getMinorCount()); // warning -> MINOR
    assertEquals(1, summary.getInfoCount()); // info -> INFO
    assertTrue(summary.getToolsUsed().contains("checkstyle"));
  }

  @Test
  void parseMultiple_mergesResults() throws IOException {
    String spotbugsXml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <BugCollection>
          <BugInstance type="NP_NULL" priority="1" shortMessage="NPE">
            <SourceLine classname="Foo" sourcepath="Foo.java" start="10"/>
          </BugInstance>
        </BugCollection>
        """;

    String pmdXml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <pmd>
          <file name="Bar.java">
            <violation rule="Unused" priority="2" beginline="10" class="Bar">
              Unused
            </violation>
          </file>
        </pmd>
        """;

    Path spotbugsFile = tempDir.resolve("spotbugs.xml");
    Path pmdFile = tempDir.resolve("pmd.xml");
    Files.writeString(spotbugsFile, spotbugsXml);
    Files.writeString(pmdFile, pmdXml);

    List<StaticAnalysisXmlReportParser.ReportSpec> reports =
        List.of(
            new StaticAnalysisXmlReportParser.ReportSpec(spotbugsFile, "spotbugs"),
            new StaticAnalysisXmlReportParser.ReportSpec(pmdFile, "pmd"));

    StaticAnalysisSummary summary = parser.parseMultiple(reports);

    assertEquals(2, summary.getTotalCount());
    assertEquals(2, summary.getCriticalCount()); // Both are critical level
    assertEquals(2, summary.getToolsUsed().size());
    assertTrue(summary.getToolsUsed().contains("spotbugs"));
    assertTrue(summary.getToolsUsed().contains("pmd"));
  }

  @Test
  void parse_withMissingFile_throwsIOException() {
    Path nonExistent = tempDir.resolve("nonexistent.xml");
    assertThrows(IOException.class, () -> parser.parse(nonExistent, "spotbugs"));
  }

  @Test
  void parse_withUnsupportedTool_throwsIOException() throws IOException {
    Path dummyFile = tempDir.resolve("dummy.xml");
    Files.writeString(dummyFile, "<root/>");
    assertThrows(IOException.class, () -> parser.parse(dummyFile, "unsupported"));
  }

  @Test
  void parseMultiple_continuesOnError() throws IOException {
    String validXml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <BugCollection>
          <BugInstance type="NP" priority="1" shortMessage="NPE">
            <SourceLine classname="Foo" sourcepath="Foo.java" start="10"/>
          </BugInstance>
        </BugCollection>
        """;

    Path validFile = tempDir.resolve("spotbugs.xml");
    Path nonExistentFile = tempDir.resolve("nonexistent.xml");
    Files.writeString(validFile, validXml);

    List<StaticAnalysisXmlReportParser.ReportSpec> reports =
        List.of(
            new StaticAnalysisXmlReportParser.ReportSpec(validFile, "spotbugs"),
            new StaticAnalysisXmlReportParser.ReportSpec(nonExistentFile, "spotbugs"));

    // Should not throw, but skip failed file
    StaticAnalysisSummary summary = parser.parseMultiple(reports);

    assertEquals(1, summary.getTotalCount());
  }

  @Test
  void hasHighSeverity_returnsTrueWhenBlockersExist() throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <pmd>
          <file name="Foo.java">
            <violation rule="Critical" priority="1" beginline="10" class="Foo">Critical issue</violation>
          </file>
        </pmd>
        """;

    Path reportFile = tempDir.resolve("pmd.xml");
    Files.writeString(reportFile, xml);

    StaticAnalysisSummary summary = parser.parse(reportFile, "pmd");

    assertTrue(summary.hasBlockers());
    assertTrue(summary.hasHighSeverity());
  }

  @Test
  void parseToolNameIsCaseInsensitive() throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <pmd>
          <file name="Foo.java">
            <violation rule="Unused" priority="2" beginline="5" class="Foo">Unused</violation>
          </file>
        </pmd>
        """;
    Path reportFile = tempDir.resolve("pmd-case.xml");
    Files.writeString(reportFile, xml);

    StaticAnalysisSummary summary = parser.parse(reportFile, "PMD");

    assertEquals(1, summary.getCriticalCount());
    assertTrue(summary.getToolsUsed().contains("pmd"));
  }

  @Test
  void parseSpotBugsMessageFallsBackToLongMessageAndDefaultsToEmpty() throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <BugCollection>
          <BugInstance type="ONE" priority="2">
            <LongMessage>Long message text</LongMessage>
            <SourceLine classname="com.example.Foo" sourcepath="Foo.java" start="9"/>
          </BugInstance>
          <BugInstance type="TWO" priority="2">
            <SourceLine classname="com.example.Bar" sourcepath="Bar.java" start="11"/>
          </BugInstance>
        </BugCollection>
        """;
    Path reportFile = tempDir.resolve("spotbugs-message-fallback.xml");
    Files.writeString(reportFile, xml);

    StaticAnalysisSummary summary = parser.parse(reportFile, "spotbugs");

    assertEquals("Long message text", summary.getFindings().get(0).getMessage());
    assertEquals("", summary.getFindings().get(1).getMessage());
  }

  @Test
  void parseCheckstyleUnknownSeverityMapsToInfo() throws IOException {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <checkstyle>
          <file name="src/main/java/Foo.java">
            <error line="10" severity="notice" source="check" message="notice message"/>
          </file>
        </checkstyle>
        """;
    Path reportFile = tempDir.resolve("checkstyle-unknown-severity.xml");
    Files.writeString(reportFile, xml);

    StaticAnalysisSummary summary = parser.parse(reportFile, "checkstyle");

    assertEquals(1, summary.getInfoCount());
    assertEquals("INFO", summary.getFindings().get(0).getSeverity());
  }

  @Test
  void reportSpecRejectsNullValues() {
    assertThrows(
        NullPointerException.class,
        () -> new StaticAnalysisXmlReportParser.ReportSpec(null, "pmd"));
    assertThrows(
        NullPointerException.class,
        () -> new StaticAnalysisXmlReportParser.ReportSpec(tempDir.resolve("x.xml"), null));
  }
}
