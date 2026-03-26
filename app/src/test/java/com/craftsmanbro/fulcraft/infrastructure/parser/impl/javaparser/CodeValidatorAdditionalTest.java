package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class CodeValidatorAdditionalTest {

  @Test
  void extractAllPrivateFieldNames_includesConflictingNames() {
    String code =
        """
        class A { private int value; }
        class B { public int value; }
        """;

    CodeValidator validator = new CodeValidator();
    Set<String> privateFields = validator.extractAllPrivateFieldNames(code);

    assertTrue(privateFields.contains("value"));
  }

  @Test
  void stripCodeFences_removesLanguageAndFenceMarkers() {
    String fenced = """
        ```java
        class Test {}
        ```
        """;

    assertEquals("class Test {}", CodeValidator.stripCodeFences(fenced));
  }

  @Test
  void countTests_countsTestAndParameterizedTestAnnotations() {
    String code =
        """
        import org.junit.jupiter.api.Test;
        import org.junit.jupiter.params.ParameterizedTest;

        class SampleTest {
          @Test
          void one() {}

          @ParameterizedTest
          void two() {}

          void helper() {}
        }
        """;

    CodeValidator validator = new CodeValidator();

    assertEquals(2, validator.countTests(code));
  }

  @Test
  void isMissingRequiredTestElements_returnsFalseWhenComplete() {
    String code =
        """
        import org.junit.jupiter.api.Assertions;
        import org.junit.jupiter.api.Test;

        class FooTest {
          @Test
          void doWorkTest() {
            Foo sut = new Foo();
            sut.doWork();
            Assertions.assertTrue(true);
          }
        }
        """;

    CodeValidator validator = new CodeValidator();

    assertFalse(validator.isMissingRequiredTestElements(code, "FooTest", "Foo", "doWork"));
  }
}
