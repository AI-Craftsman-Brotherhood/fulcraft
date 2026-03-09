package com.craftsmanbro.fulcraft.infrastructure.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.coverage.contract.CoverageLoaderPort;
import com.craftsmanbro.fulcraft.infrastructure.coverage.impl.JacocoCoverageAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link JacocoCoverageAdapter}.
 *
 * <p>Verifies XML parsing, line/branch/method coverage retrieval.
 */
class JacocoCoverageAdapterTest {

  @TempDir Path tempDir;

  // --- Constructor tests ---

  @Test
  void constructor_withNullPath_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new JacocoCoverageAdapter(null));
  }

  @Test
  void constructor_withValidPath_createsInstance() {
    Path reportPath = tempDir.resolve("report.xml");
    CoverageLoaderPort adapter = new JacocoCoverageAdapter(reportPath);
    // Just verify no exception
    assertEquals(-1, adapter.getLineCoverage("any.Class"));
  }

  // --- isAvailable tests ---

  @Test
  void isAvailable_withNonExistentFile_returnsFalse() {
    Path reportPath = tempDir.resolve("nonexistent.xml");
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertFalse(adapter.isAvailable());
  }

  @Test
  void isAvailable_withValidReport_returnsTrue() throws IOException {
    Path reportPath = createValidReport();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertTrue(adapter.isAvailable());
  }

  @Test
  void isAvailable_withInvalidXml_returnsFalse() throws IOException {
    Path reportPath = tempDir.resolve("invalid.xml");
    Files.writeString(reportPath, "not valid xml");
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertFalse(adapter.isAvailable());
  }

  // --- getLineCoverage tests ---

  @Test
  void getLineCoverage_withNullClass_returnsNegative() throws IOException {
    Path reportPath = createValidReport();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertEquals(-1, adapter.getLineCoverage(null));
  }

  @Test
  void getLineCoverage_withUnknownClass_returnsNegative() throws IOException {
    Path reportPath = createValidReport();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertEquals(-1, adapter.getLineCoverage("unknown.Class"));
  }

  @Test
  void getLineCoverage_withKnownClass_returnsPercentage() throws IOException {
    Path reportPath = createReportWithCoverage();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    double coverage = adapter.getLineCoverage("com.example.MyClass");

    // 80 covered, 20 missed = 80%
    assertEquals(80.0, coverage, 0.01);
  }

  @Test
  void getLineCoverage_withDottedNestedClassName_resolvesJacocoDollarNotation() throws IOException {
    Path reportPath = createReportWithNestedClassCoverage();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    double dottedCoverage = adapter.getLineCoverage("com.example.Outer.Inner");
    double dollarCoverage = adapter.getLineCoverage("com.example.Outer$Inner");

    assertEquals(30.0, dottedCoverage, 0.01);
    assertEquals(30.0, dollarCoverage, 0.01);
  }

  // --- getBranchCoverage tests ---

  @Test
  void getBranchCoverage_withNullClass_returnsNegative() throws IOException {
    Path reportPath = createValidReport();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertEquals(-1, adapter.getBranchCoverage(null));
  }

  @Test
  void getBranchCoverage_withUnknownClass_returnsNegative() throws IOException {
    Path reportPath = createValidReport();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertEquals(-1, adapter.getBranchCoverage("unknown.Class"));
  }

  @Test
  void getBranchCoverage_withKnownClass_returnsPercentage() throws IOException {
    Path reportPath = createReportWithCoverage();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    double coverage = adapter.getBranchCoverage("com.example.MyClass");

    // 60 covered, 40 missed = 60%
    assertEquals(60.0, coverage, 0.01);
  }

  // --- getMethodCoverage tests ---

  @Test
  void getMethodCoverage_withNullClass_returnsNegative() throws IOException {
    Path reportPath = createValidReport();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertEquals(-1, adapter.getMethodCoverage(null, "method()"));
  }

  @Test
  void getMethodCoverage_withNullSignature_returnsNegative() throws IOException {
    Path reportPath = createValidReport();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertEquals(-1, adapter.getMethodCoverage("com.example.MyClass", null));
  }

  @Test
  void getMethodCoverage_withBlankSignature_returnsNegative() throws IOException {
    Path reportPath = createValidReport();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertEquals(-1, adapter.getMethodCoverage("com.example.MyClass", "   "));
  }

  @Test
  void getMethodCoverage_withKnownMethod_returnsPercentage() throws IOException {
    Path reportPath = createReportWithMethods();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    double coverage = adapter.getMethodCoverage("com.example.MyClass", "myMethod()");

    // 50 covered, 50 missed = 50%
    assertEquals(50.0, coverage, 0.01);
  }

  @Test
  void getMethodCoverage_withMethodWithParameters_returnsPercentage() throws IOException {
    Path reportPath = createReportWithMethods();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    double coverage = adapter.getMethodCoverage("com.example.MyClass", "paramMethod(String, int)");

    // 70 covered, 30 missed = 70%
    assertEquals(70.0, coverage, 0.01);
  }

  @Test
  void getMethodCoverage_withFqnMethodSyntax_returnsPercentage() throws IOException {
    Path reportPath = createReportWithMethods();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    double coverage =
        adapter.getMethodCoverage(
            "com.example.MyClass", "com.example.MyClass#paramMethod(java.lang.String, int)");

    assertEquals(70.0, coverage, 0.01);
  }

  @Test
  void getMethodCoverage_withDotQualifiedMethodSyntax_returnsPercentage() throws IOException {
    Path reportPath = createReportWithMethods();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    double coverage =
        adapter.getMethodCoverage(
            "com.example.MyClass", "com.example.MyClass.paramMethod(java.lang.String, int)");

    assertEquals(70.0, coverage, 0.01);
  }

  @Test
  void getMethodCoverage_withGenericAndVarargsSignature_returnsPercentage() throws IOException {
    Path reportPath = createReportWithGenericAndArrayParameters();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    double coverage =
        adapter.getMethodCoverage(
            "com.example.MyClass", "complexMethod(java.util.List<java.lang.String>, String...)");

    assertEquals(75.0, coverage, 0.01);
  }

  @Test
  void getMethodCoverage_withAnnotatedAndNamedParameters_returnsPercentage() throws IOException {
    Path reportPath = createReportWithMethods();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    double coverage =
        adapter.getMethodCoverage(
            "com.example.MyClass",
            "paramMethod(final @javax.annotation.Nullable java.lang.String value, int count)");

    assertEquals(70.0, coverage, 0.01);
  }

  @Test
  void getMethodCoverage_withNestedParameterTypeUsingDotNotation_returnsPercentage()
      throws IOException {
    Path reportPath = createReportWithNestedParameterMethods();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    double coverage =
        adapter.getMethodCoverage(
            "com.example.MyClass", "acceptEntry(java.util.Map.Entry<java.lang.String, int>)");

    assertEquals(80.0, coverage, 0.01);
  }

  @Test
  void getMethodCoverage_withMalformedSignature_returnsNegative() throws IOException {
    Path reportPath = createReportWithMethods();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertEquals(-1, adapter.getMethodCoverage("com.example.MyClass", "paramMethod"));
  }

  @Test
  void getMethodCoverage_withoutLineCounter_fallsBackToBranchCoverage() throws IOException {
    Path reportPath = createReportWithBranchOnlyMethod();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    double coverage = adapter.getMethodCoverage("com.example.MyClass", "branchOnly()");

    assertEquals(75.0, coverage, 0.01);
  }

  @Test
  void getMethodCoverage_withDuplicateMethodEntries_usesFirstEntry() throws IOException {
    Path reportPath = createReportWithDuplicateMethodEntries();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    double coverage = adapter.getMethodCoverage("com.example.MyClass", "duplicated()");

    assertEquals(10.0, coverage, 0.01);
  }

  @Test
  void getMethodCoverage_withUnknownMethod_returnsNegative() throws IOException {
    Path reportPath = createReportWithMethods();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertEquals(-1, adapter.getMethodCoverage("com.example.MyClass", "unknownMethod()"));
  }

  @Test
  void getMethodCoverage_withDottedNestedClassName_resolvesJacocoDollarNotation()
      throws IOException {
    Path reportPath = createReportWithNestedClassCoverage();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    double dottedCoverage =
        adapter.getMethodCoverage("com.example.Outer.Inner", "innerMethod(java.lang.String)");
    double dollarCoverage =
        adapter.getMethodCoverage("com.example.Outer$Inner", "innerMethod(java.lang.String)");

    assertEquals(70.0, dottedCoverage, 0.01);
    assertEquals(70.0, dollarCoverage, 0.01);
  }

  // --- Edge cases ---

  @Test
  void getLineCoverage_withZeroCoveredAndMissed_returnsNegative() throws IOException {
    Path reportPath = createReportWithZeroCoverage();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertEquals(-1, adapter.getLineCoverage("com.example.EmptyClass"));
  }

  @Test
  void multipleCallsToIsAvailable_returnsSameResult() throws IOException {
    Path reportPath = createValidReport();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertTrue(adapter.isAvailable());
    assertTrue(adapter.isAvailable());
    assertTrue(adapter.isAvailable());
  }

  @Test
  void isAvailable_afterInitialMissingFileCheck_doesNotReload() throws IOException {
    Path reportPath = tempDir.resolve("late_created.xml");
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertFalse(adapter.isAvailable());

    Files.writeString(reportPath, createMinimalValidXml());

    assertFalse(adapter.isAvailable());
    assertEquals(-1, adapter.getLineCoverage("com.example.MyClass"));
  }

  @Test
  void getLineCoverage_withInvalidCounterValue_treatsInvalidAsZero() throws IOException {
    Path reportPath = createReportWithInvalidCounterValues();
    JacocoCoverageAdapter adapter = new JacocoCoverageAdapter(reportPath);

    assertEquals(0.0, adapter.getLineCoverage("com.example.MyClass"), 0.01);
  }

  // --- Helper methods ---

  private Path createValidReport() throws IOException {
    Path reportPath = tempDir.resolve("valid_report.xml");
    String xml = createMinimalValidXml();
    Files.writeString(reportPath, xml);
    return reportPath;
  }

  private Path createReportWithCoverage() throws IOException {
    Path reportPath = tempDir.resolve("coverage_report.xml");
    String xml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <report name="test">
                  <package name="com/example">
                    <class name="com/example/MyClass">
                      <counter type="LINE" missed="20" covered="80"/>
                      <counter type="BRANCH" missed="40" covered="60"/>
                    </class>
                  </package>
                </report>
                """;
    Files.writeString(reportPath, xml);
    return reportPath;
  }

  private Path createReportWithMethods() throws IOException {
    Path reportPath = tempDir.resolve("method_report.xml");
    String xml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <report name="test">
                  <package name="com/example">
                    <class name="com/example/MyClass">
                      <method name="myMethod" desc="()V">
                        <counter type="LINE" missed="50" covered="50"/>
                        <counter type="BRANCH" missed="0" covered="0"/>
                      </method>
                      <method name="paramMethod" desc="(Ljava/lang/String;I)V">
                        <counter type="LINE" missed="30" covered="70"/>
                        <counter type="BRANCH" missed="0" covered="0"/>
                      </method>
                      <counter type="LINE" missed="20" covered="80"/>
                      <counter type="BRANCH" missed="40" covered="60"/>
                    </class>
                  </package>
                </report>
                """;
    Files.writeString(reportPath, xml);
    return reportPath;
  }

  private Path createReportWithGenericAndArrayParameters() throws IOException {
    Path reportPath = tempDir.resolve("generic_array_method_report.xml");
    String xml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <report name="test">
                  <package name="com/example">
                    <class name="com/example/MyClass">
                      <method name="complexMethod" desc="(Ljava/util/List;[Ljava/lang/String;)V">
                        <counter type="LINE" missed="25" covered="75"/>
                      </method>
                    </class>
                  </package>
                </report>
                """;
    Files.writeString(reportPath, xml);
    return reportPath;
  }

  private Path createReportWithBranchOnlyMethod() throws IOException {
    Path reportPath = tempDir.resolve("branch_only_method_report.xml");
    String xml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <report name="test">
                  <package name="com/example">
                    <class name="com/example/MyClass">
                      <method name="branchOnly" desc="()V">
                        <counter type="BRANCH" missed="1" covered="3"/>
                      </method>
                    </class>
                  </package>
                </report>
                """;
    Files.writeString(reportPath, xml);
    return reportPath;
  }

  private Path createReportWithNestedParameterMethods() throws IOException {
    Path reportPath = tempDir.resolve("nested_parameter_method_report.xml");
    String xml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <report name="test">
                  <package name="com/example">
                    <class name="com/example/MyClass">
                      <method name="acceptEntry" desc="(Ljava/util/Map$Entry;)V">
                        <counter type="LINE" missed="20" covered="80"/>
                      </method>
                    </class>
                  </package>
                </report>
                """;
    Files.writeString(reportPath, xml);
    return reportPath;
  }

  private Path createReportWithDuplicateMethodEntries() throws IOException {
    Path reportPath = tempDir.resolve("duplicate_method_report.xml");
    String xml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <report name="test">
                  <package name="com/example">
                    <class name="com/example/MyClass">
                      <method name="duplicated" desc="()V">
                        <counter type="LINE" missed="90" covered="10"/>
                      </method>
                      <method name="duplicated" desc="()V">
                        <counter type="LINE" missed="10" covered="90"/>
                      </method>
                    </class>
                  </package>
                </report>
                """;
    Files.writeString(reportPath, xml);
    return reportPath;
  }

  private Path createReportWithInvalidCounterValues() throws IOException {
    Path reportPath = tempDir.resolve("invalid_counter_report.xml");
    String xml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <report name="test">
                  <package name="com/example">
                    <class name="com/example/MyClass">
                      <counter type="LINE" missed="10" covered="invalid-number"/>
                    </class>
                  </package>
                </report>
                """;
    Files.writeString(reportPath, xml);
    return reportPath;
  }

  private Path createReportWithNestedClassCoverage() throws IOException {
    Path reportPath = tempDir.resolve("nested_class_coverage_report.xml");
    String xml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <report name="test">
                  <package name="com/example">
                    <class name="com/example/Outer$Inner">
                      <method name="innerMethod" desc="(Ljava/lang/String;)V">
                        <counter type="LINE" missed="30" covered="70"/>
                      </method>
                      <counter type="LINE" missed="70" covered="30"/>
                    </class>
                  </package>
                </report>
                """;
    Files.writeString(reportPath, xml);
    return reportPath;
  }

  private String createMinimalValidXml() {
    return """
            <?xml version="1.0" encoding="UTF-8"?>
            <report name="test">
              <package name="com/example">
                <class name="com/example/MyClass">
                  <counter type="LINE" missed="0" covered="0"/>
                  <counter type="BRANCH" missed="0" covered="0"/>
                </class>
              </package>
            </report>
            """;
  }

  private Path createReportWithZeroCoverage() throws IOException {
    Path reportPath = tempDir.resolve("zero_coverage_report.xml");
    String xml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <report name="test">
                  <package name="com/example">
                    <class name="com/example/EmptyClass">
                      <counter type="LINE" missed="0" covered="0"/>
                      <counter type="BRANCH" missed="0" covered="0"/>
                    </class>
                  </package>
                </report>
                """;
    Files.writeString(reportPath, xml);
    return reportPath;
  }
}
