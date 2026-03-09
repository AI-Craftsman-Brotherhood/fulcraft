package com.craftsmanbro.fulcraft.infrastructure.buildtool.failure.util;

import com.craftsmanbro.fulcraft.i18n.MessageSource;

/**
 * Fix suggestion templates for different mismatch types.
 *
 * <p>This class provides template suggestions for how to fix different types of assertion
 * mismatches. The key principle is to relax assertions to property-based checks rather than
 * recalculating expected values.
 *
 * <h2>Core Philosophy</h2>
 *
 * <p>When a test fails, the fix should NOT be to recalculate the expected value. Instead, the
 * assertion should be relaxed to check properties or use tolerances.
 *
 * @see MismatchType
 * @see com.craftsmanbro.fulcraft.infrastructure.parser.impl.xml.JUnitXmlReportParser
 */
public final class FixSuggestionTemplates {

  private static final String FIX_HEADER_KEY = "fix.header";

  private static final String FIX_CRITICAL_KEY = "fix.critical";

  private static final String FIX_TEMPLATE_REPLACE_INSTRUCTION_KEY =
      "fix.template.replace_instruction";

  private static final String FIX_TEMPLATE_BEFORE_WRONG_KEY = "fix.template.before_wrong";

  private static final String FIX_TEMPLATE_AFTER_CORRECT_KEY = "fix.template.after_correct";

  private static final String RELAX_TO_PROPERTIES = "RELAX_TO_PROPERTIES";

  private FixSuggestionTemplates() {
    // Utility class - prevent instantiation
  }

  /**
   * Get fix suggestion template for a mismatch type.
   *
   * @param type the type of mismatch
   * @return the fix suggestion template
   */
  public static String getTemplate(final MismatchType type) {
    return switch (type) {
      case NUMERIC -> getNumericTemplate();
      case FLOAT_TOLERANCE -> getFloatTemplate();
      case STRING -> getStringTemplate();
      case COLLECTION -> getCollectionTemplate();
      case MAP -> getMapTemplate();
      case ORDERING -> getOrderingTemplate();
      case OBJECT_EQUALS -> getObjectEqualsTemplate();
      case NULL_MISMATCH -> getNullTemplate();
      case EXCEPTION_MESSAGE -> getExceptionTemplate();
      case UNKNOWN -> getUnknownTemplate();
    };
  }

  /**
   * Get template ID for logging/tracking.
   *
   * @param type the type of mismatch
   * @return the template ID
   */
  public static String getTemplateId(final MismatchType type) {
    return "FIX_" + type.name();
  }

  private static String getNumericTemplate() {
    return MessageSource.getMessage(FIX_HEADER_KEY, RELAX_TO_PROPERTIES, "NUMERIC")
        + "\n\n"
        + MessageSource.getMessage("fix.numeric.desc")
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_REPLACE_INSTRUCTION_KEY)
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_BEFORE_WRONG_KEY)
        + "\n"
        + "```java\n"
        + "assertEquals(42, result);\n"
        + "assertThat(result).isEqualTo(42);\n"
        + "```\n\n"
        + MessageSource.getMessage("fix.template.after_correct_options")
        + "\n"
        + "```java\n"
        + "// Option 1: Just verify non-null\n"
        + "assertNotNull(result);\n\n"
        + "// Option 2: Verify reasonable range\n"
        + "assertTrue(result > 0);\n"
        + "assertTrue(result >= 0 && result <= 1000);\n\n"
        + "// Option 3: Verify type/property\n"
        + "assertThat(result).isNotNull();\n"
        + "assertThat(result).isPositive();\n"
        + "assertThat(result).isGreaterThan(0);\n"
        + "```\n\n"
        + MessageSource.getMessage(
            FIX_CRITICAL_KEY, MessageSource.getMessage("fix.numeric.critical"))
        + "\n";
  }

  private static String getFloatTemplate() {
    return MessageSource.getMessage(FIX_HEADER_KEY, "RELAX_TO_TOLERANCE", "FLOAT")
        + "\n\n"
        + MessageSource.getMessage("fix.float.desc")
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_REPLACE_INSTRUCTION_KEY)
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_BEFORE_WRONG_KEY)
        + "\n"
        + "```java\n"
        + "assertEquals(3.14159, result);\n"
        + "assertThat(result).isEqualTo(3.14159);\n"
        + "```\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_AFTER_CORRECT_KEY)
        + "\n"
        + "```java\n"
        + "// Use tolerance comparison\n"
        + "assertThat(result).isCloseTo(3.14, within(0.01));\n"
        + "assertThat(result).isCloseTo(3.14, withPercentage(1.0));\n\n"
        + "// Or use property assertions\n"
        + "assertThat(result).isPositive();\n"
        + "assertThat(result).isBetween(3.0, 4.0);\n"
        + "```\n\n"
        + MessageSource.getMessage(FIX_CRITICAL_KEY, MessageSource.getMessage("fix.float.critical"))
        + "\n";
  }

  private static String getStringTemplate() {
    return MessageSource.getMessage(FIX_HEADER_KEY, "RELAX_TO_PARTIAL_MATCH", "STRING")
        + "\n\n"
        + MessageSource.getMessage("fix.string.desc")
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_REPLACE_INSTRUCTION_KEY)
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_BEFORE_WRONG_KEY)
        + "\n"
        + "```java\n"
        + "assertEquals(\"Hello World!\", result);\n"
        + "assertThat(result).isEqualTo(\"Hello World!\");\n"
        + "```\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_AFTER_CORRECT_KEY)
        + "\n"
        + "```java\n"
        + "// Check contains key content\n"
        + "assertThat(result).contains(\"Hello\");\n"
        + "assertThat(result).containsIgnoringCase(\"hello\");\n\n"
        + "// Check starts/ends with\n"
        + "assertThat(result).startsWith(\"Hello\");\n"
        + "assertThat(result).endsWith(\"!\");\n\n"
        + "// Check not null/empty\n"
        + "assertThat(result).isNotNull();\n"
        + "assertThat(result).isNotEmpty();\n"
        + "```\n\n"
        + MessageSource.getMessage(
            FIX_CRITICAL_KEY, MessageSource.getMessage("fix.string.critical"))
        + "\n";
  }

  private static String getCollectionTemplate() {
    return MessageSource.getMessage(FIX_HEADER_KEY, RELAX_TO_PROPERTIES, "COLLECTION")
        + "\n\n"
        + MessageSource.getMessage("fix.collection.desc")
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_REPLACE_INSTRUCTION_KEY)
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_BEFORE_WRONG_KEY)
        + "\n"
        + "```java\n"
        + "assertThat(list).containsExactly(item1, item2, item3);\n"
        + "assertEquals(Arrays.asList(1, 2, 3), result);\n"
        + "```\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_AFTER_CORRECT_KEY)
        + "\n"
        + "```java\n"
        + "// Check size\n"
        + "assertThat(list).hasSize(3);\n"
        + "assertThat(list).hasSizeGreaterThan(0);\n\n"
        + "// Check contains (order-independent)\n"
        + "assertThat(list).contains(item1);\n"
        + "assertThat(list).isNotEmpty();\n\n"
        + "// Check element properties\n"
        + "assertThat(list).allMatch(item -> item != null);\n"
        + "```\n\n"
        + MessageSource.getMessage(
            FIX_CRITICAL_KEY, MessageSource.getMessage("fix.collection.critical"))
        + "\n";
  }

  private static String getMapTemplate() {
    return MessageSource.getMessage(FIX_HEADER_KEY, RELAX_TO_PROPERTIES, "MAP")
        + "\n\n"
        + MessageSource.getMessage("fix.map.desc")
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_REPLACE_INSTRUCTION_KEY)
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_AFTER_CORRECT_KEY)
        + "\n"
        + "```java\n"
        + "// Check size\n"
        + "assertThat(map).hasSize(3);\n"
        + "assertThat(map).isNotEmpty();\n\n"
        + "// Check contains key/value\n"
        + "assertThat(map).containsKey(\"key1\");\n"
        + "assertThat(map).containsValue(expectedValue);\n\n"
        + "// Check entry properties\n"
        + "assertThat(map.keySet()).allMatch(Objects::nonNull);\n"
        + "```\n\n"
        + MessageSource.getMessage(FIX_CRITICAL_KEY, MessageSource.getMessage("fix.map.critical"))
        + "\n";
  }

  private static String getOrderingTemplate() {
    return MessageSource.getMessage(FIX_HEADER_KEY, RELAX_TO_PROPERTIES, "ORDERING")
        + "\n\n"
        + MessageSource.getMessage("fix.ordering.desc")
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_REPLACE_INSTRUCTION_KEY)
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_AFTER_CORRECT_KEY)
        + "\n"
        + "```java\n"
        + "// Check that result is sorted\n"
        + "assertThat(list).isSorted();\n"
        + "assertThat(list).isSortedAccordingTo(comparator);\n\n"
        + "// Or just check contains (ignore order)\n"
        + "assertThat(list).containsExactlyInAnyOrder(expected.toArray());\n\n"
        + "// Or check size/content properties\n"
        + "assertThat(list).hasSize(expectedSize);\n"
        + "```\n\n"
        + MessageSource.getMessage(
            FIX_CRITICAL_KEY, MessageSource.getMessage("fix.ordering.critical"))
        + "\n";
  }

  private static String getObjectEqualsTemplate() {
    return MessageSource.getMessage(FIX_HEADER_KEY, RELAX_TO_PROPERTIES, "OBJECT")
        + "\n\n"
        + MessageSource.getMessage("fix.object.desc")
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_REPLACE_INSTRUCTION_KEY)
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_AFTER_CORRECT_KEY)
        + "\n"
        + "```java\n"
        + "// Check not null\n"
        + "assertNotNull(result);\n"
        + "assertThat(result).isNotNull();\n\n"
        + "// Check specific properties\n"
        + "assertThat(result.getId()).isNotNull();\n"
        + "assertThat(result.getName()).isNotEmpty();\n\n"
        + "// Check type\n"
        + "assertThat(result).isInstanceOf(ExpectedType.class);\n"
        + "```\n\n"
        + MessageSource.getMessage(
            FIX_CRITICAL_KEY, MessageSource.getMessage("fix.object.critical"))
        + "\n";
  }

  private static String getNullTemplate() {
    return MessageSource.getMessage(FIX_HEADER_KEY, "FIX_NULL_HANDLING", "NULL")
        + "\n\n"
        + MessageSource.getMessage("fix.null.desc")
        + "\n\n"
        + "Options:\n"
        + "```java\n"
        + "// If null is acceptable\n"
        + "assertThat(result).isNull();\n\n"
        + "// If null is not acceptable, check the code under test\n"
        + "// and add appropriate null handling\n\n"
        + "// If result should not be null\n"
        + "assertThat(result).isNotNull();\n"
        + "```\n";
  }

  private static String getExceptionTemplate() {
    return MessageSource.getMessage(FIX_HEADER_KEY, "RELAX_EXCEPTION_CHECK", "EXCEPTION")
        + "\n\n"
        + MessageSource.getMessage("fix.exception.desc")
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_REPLACE_INSTRUCTION_KEY)
        + "\n\n"
        + MessageSource.getMessage(FIX_TEMPLATE_AFTER_CORRECT_KEY)
        + "\n"
        + "```java\n"
        + "// Just check exception type\n"
        + "assertThrows(ExpectedException.class, () -> methodCall());\n\n"
        + "// Or check exception type and verify message contains key text\n"
        + "var ex = assertThrows(ExpectedException.class, () -> methodCall());\n"
        + "assertThat(ex.getMessage()).contains(\"key text\");\n"
        + "```\n\n"
        + MessageSource.getMessage(
            FIX_CRITICAL_KEY, MessageSource.getMessage("fix.exception.critical"))
        + "\n";
  }

  private static String getUnknownTemplate() {
    return MessageSource.getMessage(FIX_HEADER_KEY, RELAX_TO_PROPERTIES, "GENERAL")
        + "\n\n"
        + MessageSource.getMessage("fix.unknown.desc")
        + "\n\n"
        + "1. Replace exact assertEquals with property assertions\n"
        + "2. Use assertNotNull if value presence is key\n"
        + "3. Use assertTrue with a reasonable condition\n"
        + "4. Use contains/startsWith for strings\n"
        + "5. Use hasSize for collections\n\n"
        + MessageSource.getMessage(
            FIX_CRITICAL_KEY, MessageSource.getMessage("fix.unknown.critical"))
        + "\n";
  }
}
