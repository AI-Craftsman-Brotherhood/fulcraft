package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CodeValidatorTest {

  @Test
  void extractPrivateFields_excludesConflictingNamesAcrossClasses() {
    String code =
        """
        class A { private int value; private int hidden; }
        class B { int value; }
        """;

    CodeValidator validator = new CodeValidator();
    List<String> privateFields = validator.extractPrivateFields(code);

    assertFalse(privateFields.contains("value"));
    assertTrue(privateFields.contains("hidden"));
  }

  @Test
  void validateCode_acceptsConstructorTargetWhenObjectCreationExists() {
    String code =
        """
        import org.junit.jupiter.api.Assertions;
        import org.junit.jupiter.api.Test;

        class TargetTest {
          @Test
          void createsSut() {
            new Target();
            Assertions.assertTrue(true);
          }

          static class Target {}
        }
        """;

    CodeValidator validator = new CodeValidator();
    String result = validator.validateCode(code, List.of(), "<init>");

    assertNull(result);
  }

  @Test
  void validateCode_rejectsConstructorTargetWhenNoObjectCreationExists() {
    String code =
        """
        import org.junit.jupiter.api.Assertions;
        import org.junit.jupiter.api.Test;

        class TargetTest {
          @Test
          void doesNotCreateSut() {
            Assertions.assertTrue(true);
          }
        }
        """;

    CodeValidator validator = new CodeValidator();
    String result = validator.validateCode(code, List.of(), "<init>");

    assertNotNull(result);
    assertTrue(result.contains("constructor"));
  }

  @Test
  void validateCode_doesNotFlagPrivateFieldNameWhenLocalVariableShadowsIt() {
    String code =
        """
        import org.junit.jupiter.api.Assertions;
        import org.junit.jupiter.api.Test;

        class SampleTest {
          @Test
          void usesLocalValue() {
            int value = 10;
            helper();
            Assertions.assertEquals(10, value);
          }

          void helper() {}
        }
        """;

    CodeValidator validator = new CodeValidator();
    String result = validator.validateCode(code, List.of("value"), "helper");

    assertNull(result);
  }

  @Test
  void validateCode_detectsStaticMisuseWhenTargetMethodIncludesSignature() {
    String code =
        """
        import org.junit.jupiter.api.Assertions;
        import org.junit.jupiter.api.Test;

        class UtilTest {
          @Test
          void callsStaticMethodViaInstance() {
            boolean actual = new StringUtil().isEmpty("");
            Assertions.assertTrue(actual);
          }
        }
        """;

    CodeValidator validator = new CodeValidator();
    String result = validator.validateCode(code, List.of(), "isEmpty(String)", "StringUtil", true);

    assertNotNull(result);
    assertTrue(result.contains("STATIC_MISUSE"));
  }

  @Test
  void validateCode_detectsStaticMisuseWhenInstanceIsStoredInField() {
    String code =
        """
        import org.junit.jupiter.api.Assertions;
        import org.junit.jupiter.api.Test;

        class UtilTest {
          private final StringUtil sut = new StringUtil();

          @Test
          void callsStaticMethodViaField() {
            boolean actual = sut.isEmpty("");
            Assertions.assertTrue(actual);
          }
        }
        """;

    CodeValidator validator = new CodeValidator();
    String result = validator.validateCode(code, List.of(), "isEmpty(String)", "StringUtil", true);

    assertNotNull(result);
    assertTrue(result.contains("STATIC_MISUSE"));
  }

  @Test
  void validateCode_detectsStaticMisuseWhenAssignedFieldUsesThisQualifier() {
    String code =
        """
        import org.junit.jupiter.api.Assertions;
        import org.junit.jupiter.api.Test;

        class UtilTest {
          private StringUtil sut;

          @Test
          void callsStaticMethodViaAssignedField() {
            this.sut = new StringUtil();
            boolean actual = this.sut.isEmpty("");
            Assertions.assertTrue(actual);
          }
        }
        """;

    CodeValidator validator = new CodeValidator();
    String result = validator.validateCode(code, List.of(), "isEmpty(String)", "StringUtil", true);

    assertNotNull(result);
    assertTrue(result.contains("STATIC_MISUSE"));
  }
}
