package com.craftsmanbro.fulcraft.plugins.analysis.core.service.analyzer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CommonMethodAnalyzerTest {

  private CommonMethodAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    analyzer = new CommonMethodAnalyzer();
  }

  @Nested
  class AnalyzeMethodHintsTests {

    @Test
    void shouldReturnEmptyHintsForNullSourceCode() {
      var hints = analyzer.analyzeMethodHints(null, "toString");
      assertFalse(hints.hasHints());
    }

    @Test
    void shouldReturnEmptyHintsForBlankSourceCode() {
      var hints = analyzer.analyzeMethodHints("   ", "toString");
      assertFalse(hints.hasHints());
    }

    @Test
    void shouldReturnEmptyHintsForInvalidSourceCode() {
      var hints = analyzer.analyzeMethodHints("not valid java", "toString");
      assertFalse(hints.hasHints());
    }

    @Test
    void shouldReturnEmptyHintsForUnknownTargetMethod() {
      String code =
          """
          class MyClass {
              String name;
              public String toString() {
                  return "MyClass[" + name + "]";
              }
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "doWork");

      assertFalse(hints.hasHints());
    }
  }

  @Nested
  class ExtractToStringFormatTests {

    @Test
    void shouldExtractFormatFromStringFormat() {
      String code =
          """
          class MyClass {
              private String name;
              private int value;

              @Override
              public String toString() {
                  return String.format("MyClass[name=%s, value=%d]", name, value);
              }
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "toString");

      assertNotNull(hints.toStringFormat());
      assertEquals("MyClass[name=%s, value=%d]", hints.toStringFormat());
    }

    @Test
    void shouldExtractPatternFromConcatenation() {
      String code =
          """
          class MyClass {
              private String name;

              @Override
              public String toString() {
                  return "MyClass[" + name + "]";
              }
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "toString");

      assertNotNull(hints.toStringFormat());
      assertTrue(hints.toStringFormat().contains("MyClass["));
    }

    @Test
    void shouldFallbackToClassNamePlaceholderWhenSimpleNameIsUsed() {
      String code =
          """
          class MyClass {
              @Override
              public String toString() {
                  return getClass().getSimpleName();
              }
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "toString");

      assertEquals("<ClassName>", hints.toStringFormat());
    }
  }

  @Nested
  class ExtractEqualsFieldsTests {

    @Test
    void shouldExtractFieldsFromObjectsEquals() {
      String code =
          """
          import java.util.Objects;
          class MyClass {
              private String name;
              private int value;

              @Override
              public boolean equals(Object o) {
                  if (this == o) return true;
                  if (o == null || getClass() != o.getClass()) return false;
                  MyClass myClass = (MyClass) o;
                  return value == myClass.value && Objects.equals(name, myClass.name);
              }
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "equals");

      assertNotNull(hints.equalsFields());
      assertFalse(hints.equalsFields().isEmpty());
      assertTrue(
          hints.equalsFields().contains("name") || hints.equalsFields().contains("value"),
          "Should contain at least one of the compared fields");
    }

    @Test
    void shouldReturnEmptyListWhenNoEqualsMethod() {
      String code =
          """
          class MyClass {
              private String name;
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "equals");

      assertTrue(hints.equalsFields().isEmpty());
    }

    @Test
    void shouldIgnoreObjectsScopeForObjectsEqualsCall() {
      String code =
          """
          import java.util.Objects;
          class MyClass {
              private String name;

              @Override
              public boolean equals(Object o) {
                  if (o == null || getClass() != o.getClass()) {
                      return false;
                  }
                  MyClass that = (MyClass) o;
                  return Objects.equals(name, that.name);
              }
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "equals");

      assertTrue(hints.equalsFields().contains("name"));
      assertFalse(hints.equalsFields().contains("Objects"));
    }

    @Test
    void shouldRemoveEqualsParameterNameExtractedFromMethodCallPath() {
      String code =
          """
          class MyClass {
              private String name;

              @Override
              public boolean equals(Object o) {
                  return this.name.hashCode() == o.toString().hashCode();
              }
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "equals");

      assertTrue(hints.equalsFields().contains("name"));
      assertFalse(hints.equalsFields().contains("o"));
    }
  }

  @Nested
  class ExtractHashCodeFieldsTests {

    @Test
    void shouldExtractFieldsFromObjectsHash() {
      String code =
          """
          import java.util.Objects;
          class MyClass {
              private String name;
              private int value;

              @Override
              public int hashCode() {
                  return Objects.hash(name, value);
              }
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "hashCode");

      assertNotNull(hints.hashCodeFields());
      assertFalse(hints.hashCodeFields().isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenNoHashCodeMethod() {
      String code =
          """
          class MyClass {
              private String name;
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "hashCode");

      assertTrue(hints.hashCodeFields().isEmpty());
    }

    @Test
    void shouldFilterOutCommonLocalVariableNamesFromHashCodeFields() {
      String code =
          """
          class MyClass {
              private String name;

              @Override
              public int hashCode() {
                  int result = 17;
                  int prime = 31;
                  int hash = result;
                  result = prime * result + name.hashCode();
                  return result + hash;
              }
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "hashCode");

      assertTrue(hints.hashCodeFields().contains("name"));
      assertFalse(hints.hashCodeFields().contains("result"));
      assertFalse(hints.hashCodeFields().contains("prime"));
      assertFalse(hints.hashCodeFields().contains("hash"));
    }
  }

  @Nested
  class ExtractConstructorPatternTests {

    @Test
    void shouldExtractConstructorPattern() {
      String code =
          """
          class MyClass {
              private String name;
              private int value;

              public MyClass(String name, int value) {
                  this.name = name;
                  this.value = value;
              }
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "MyClass");

      assertNotNull(hints.constructorPattern());
      assertTrue(hints.constructorPattern().contains("String"));
      assertTrue(hints.constructorPattern().contains("int"));
    }

    @Test
    void shouldReturnNullForDefaultConstructorOnly() {
      String code =
          """
          class MyClass {
              private String name;

              public MyClass() {
              }
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "MyClass");

      // Default constructor has no parameters, so pattern should be null
      assertNull(hints.constructorPattern());
    }

    @Test
    void shouldExtractConstructorPatternForInitLikeTargetName() {
      String code =
          """
          class MyClass {
              private String name;

              public MyClass(String name) {
                  this.name = name;
              }
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "initState");

      assertEquals("Constructor(String name)", hints.constructorPattern());
    }

    @Test
    void shouldPickFirstNonDefaultConstructorPattern() {
      String code =
          """
          class MyClass {
              public MyClass() {
              }

              public MyClass(String name, int count) {
              }
          }
          """;

      var hints = analyzer.analyzeMethodHints(code, "MyClass");

      assertEquals("Constructor(String name, int count)", hints.constructorPattern());
    }
  }

  @Nested
  class FormatHintsForPromptTests {

    @Test
    void shouldReturnEmptyStringForNullHints() {
      String result = analyzer.formatHintsForPrompt(null, "toString");
      assertEquals("", result);
    }

    @Test
    void shouldReturnEmptyStringForHintsWithoutData() {
      var hints = new CommonMethodAnalyzer.MethodHints(null, List.of(), List.of(), null);

      String result = analyzer.formatHintsForPrompt(hints, "toString");
      assertEquals("", result);
    }

    @Test
    void shouldFormatToStringHints() {
      var hints = new CommonMethodAnalyzer.MethodHints("MyClass[%s]", List.of(), List.of(), null);

      String result = analyzer.formatHintsForPrompt(hints, "toString");

      assertTrue(result.contains("METHOD IMPLEMENTATION HINTS"));
      assertTrue(result.contains("MyClass[%s]"));
      assertTrue(result.contains("IMPORTANT"));
    }

    @Test
    void shouldFormatEqualsHints() {
      var hints =
          new CommonMethodAnalyzer.MethodHints(null, List.of("name", "value"), List.of(), null);

      String result = analyzer.formatHintsForPrompt(hints, "equals");

      assertTrue(result.contains("METHOD IMPLEMENTATION HINTS"));
      assertTrue(result.contains("name, value"));
      assertTrue(result.contains("same values"));
    }

    @Test
    void shouldFormatHashCodeHints() {
      var hints =
          new CommonMethodAnalyzer.MethodHints(null, List.of(), List.of("name", "value"), null);

      String result = analyzer.formatHintsForPrompt(hints, "hashCode");

      assertTrue(result.contains("METHOD IMPLEMENTATION HINTS"));
      assertTrue(result.contains("name, value"));
      assertTrue(result.contains("hashCode"));
    }

    @Test
    void shouldAlwaysIncludeConstructorPatternWhenPresent() {
      var hints =
          new CommonMethodAnalyzer.MethodHints(
              null, List.of(), List.of(), "Constructor(String name)");

      String result = analyzer.formatHintsForPrompt(hints, "save");

      assertTrue(result.contains("METHOD IMPLEMENTATION HINTS"));
      assertTrue(result.contains("Constructor pattern: Constructor(String name)"));
    }
  }

  @Nested
  class MethodHintsTests {

    @Test
    void shouldMakeDefensiveCopiesForFieldLists() {
      List<String> equalsFields = new ArrayList<>(List.of("name"));
      List<String> hashCodeFields = new ArrayList<>(List.of("value"));

      var hints = new CommonMethodAnalyzer.MethodHints(null, equalsFields, hashCodeFields, null);
      equalsFields.add("changed");
      hashCodeFields.add("changed");

      assertEquals(List.of("name"), hints.equalsFields());
      assertEquals(List.of("value"), hints.hashCodeFields());
      assertThrows(UnsupportedOperationException.class, () -> hints.equalsFields().add("other"));
      assertThrows(UnsupportedOperationException.class, () -> hints.hashCodeFields().add("other"));
    }
  }

  @Nested
  class FieldExtractionLogicTests {

    @ParameterizedTest(name = "{index}: extracts {1}")
    @MethodSource(
        "com.craftsmanbro.fulcraft.plugins.analysis.core.service.analyzer.CommonMethodAnalyzerTest#fieldExtractionTestCases")
    void shouldExtractFieldFromEqualsBinaryExpressions(String code, String expectedField) {
      var hints = analyzer.analyzeMethodHints(code, "equals");
      assertTrue(hints.equalsFields().contains(expectedField));
    }

    @Test
    void shouldExtractFieldFromBinaryAndCheckForMethodCallExclusion() {
      // getSomething() is not allowed in extractFieldName path
      // We use equals with == to ensure we hit the BinaryExpr path which calls
      // extractFieldName
      String code =
          """
          class Test {
              String field;
              public boolean equals(Object o) {
                  // getSomething() is not allowed, but field should be extracted if used directly
                  return getSomething() == getSomething();
              }
              String getSomething() { return "s"; }
          }
          """;
      var hints = analyzer.analyzeMethodHints(code, "equals");
      // getSomething is not field, should not be in equalsFields
      assertFalse(hints.equalsFields().contains("getSomething"));
    }

    @Test
    void shouldIgnoreUpperCaseFieldsInBinaryExpr() {
      // "Field" -> Uppercase -> Ignore
      // We use simple name to avoid FieldAccessExpr scanner picking it up as field
      // access.
      String code =
          """
          class Test {
              static int Field = 0;
              public boolean equals(Object o) {
                  return Field == Field;
              }
          }
          """;
      var hints = analyzer.analyzeMethodHints(code, "equals");
      assertFalse(hints.equalsFields().contains("Field"));
    }
  }

  static Stream<Arguments> fieldExtractionTestCases() {
    return Stream.of(
        Arguments.of(
            """
                class Test {
                    String simpleField;
                    public boolean equals(Object o) {
                        return simpleField == ((Test)o).simpleField;
                    }
                }
                """,
            "simpleField"),
        Arguments.of(
            """
                class Test {
                    String field;
                    public boolean equals(Object o) {
                        return this.field == ((Test)o).field;
                    }
                }
                """,
            "field"),
        Arguments.of(
            """
                class Test {
                    String field;
                    public boolean equals(Object o) {
                        return this.field.hashCode() == o.hashCode();
                    }
                }
                """,
            "field"),
        Arguments.of(
            """
                class Test {
                    Inner nested;
                    public boolean equals(Object o) {
                        return nested.field.hashCode() == 0;
                    }
                }
                """,
            "field"));
  }
}
