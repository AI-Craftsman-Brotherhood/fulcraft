package com.craftsmanbro.fulcraft.infrastructure.formatter.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.formatter.contract.TestCodeFormattingPort;
import com.craftsmanbro.fulcraft.infrastructure.formatter.model.TestCodeFormattingProfile;
import org.junit.jupiter.api.Test;

class TestCodeFormatterTest {

  private final TestCodeFormatter formatter = new TestCodeFormatter();

  @Test
  void supportsContractReference() {
    TestCodeFormattingPort port = formatter;
    assertEquals("class A { }\n", port.format("class A {}"));
  }

  @Test
  void formatWithNullProfileFallsBackToDeterministicDefaults() {
    String expected = formatter.format("class A {}");

    String actual = formatter.format("class A {}", null);

    assertEquals(expected, actual);
  }

  @Test
  void formatWithProfileCanDisableTrailingNewline() {
    TestCodeFormattingProfile profile =
        new TestCodeFormattingProfile(true, true, true, true, false);

    String actual = formatter.format("class A {}", profile);

    assertFalse(actual.endsWith("\n"));
  }

  @Test
  void formatWithProfileCanDisableMemberSorting() {
    String input =
        """
        class Sample {
            @org.junit.jupiter.api.Test
            void testB() {}

            @org.junit.jupiter.api.Test
            void testA() {}
        }
        """;

    TestCodeFormattingProfile profile =
        new TestCodeFormattingProfile(true, false, true, true, true);

    String actual = formatter.format(input, profile);

    assertTrue(actual.indexOf("testB") < actual.indexOf("testA"));
  }

  @Test
  void testImportSorting() {
    String input =
        """
        package com.example;

        import org.junit.jupiter.api.Test;
        import java.util.List;
        import com.example.Foo;
        import javax.annotation.Nullable;
        import java.util.ArrayList;

        class TestClass {}
        """;

    String expected =
        """
        package com.example;

        import java.util.ArrayList;
        import java.util.List;
        import javax.annotation.Nullable;
        import org.junit.jupiter.api.Test;
        import com.example.Foo;

        class TestClass {
        }
        """;

    String actual = formatter.format(input);
    // Standardize whitespace for comparison (formatter might add extra newlines)
    assertEquals(normalize(expected), normalize(actual));
  }

  @Test
  void testStaticImportSorting() {
    String input =
        """
        package com.example;

        import com.example.Foo;
        import static org.junit.jupiter.api.Assertions.assertEquals;
        import javax.annotation.Nullable;
        import static java.util.Collections.emptyList;
        import java.util.List;
        import org.junit.jupiter.api.Test;

        class TestClass {}
        """;

    String expected =
        """
        package com.example;

        import static java.util.Collections.emptyList;
        import static org.junit.jupiter.api.Assertions.assertEquals;
        import java.util.List;
        import javax.annotation.Nullable;
        import org.junit.jupiter.api.Test;
        import com.example.Foo;

        class TestClass {
        }
        """;

    String actual = formatter.format(input);
    assertEquals(normalize(expected), normalize(actual));
  }

  @Test
  void testImportSortingWithUnknownPackageLast() {
    String input =
        """
        package com.example;

        import net.example.CustomType;
        import java.util.List;
        import com.example.Helper;
        import org.junit.jupiter.api.Test;
        import static java.util.Collections.emptyList;
        import javax.annotation.Nullable;

        class TestClass {}
        """;

    String expected =
        """
        package com.example;

        import static java.util.Collections.emptyList;
        import java.util.List;
        import javax.annotation.Nullable;
        import org.junit.jupiter.api.Test;
        import com.example.Helper;
        import net.example.CustomType;

        class TestClass {
        }
        """;

    String actual = formatter.format(input);
    assertEquals(normalize(expected), normalize(actual));
  }

  @Test
  void testMemberOrdering() {
    String input =
        """
        package com.example;

        import org.junit.jupiter.api.Test;
        import org.junit.jupiter.api.BeforeEach;

        class TestClass {
            @Test
            void testB() {}

            private void helper() {}

            @BeforeEach
            void setup() {}

            @Test
            void testA() {}

            private int field;
        }
        """;

    String expected =
        """
        package com.example;

        import org.junit.jupiter.api.BeforeEach;
        import org.junit.jupiter.api.Test;

        class TestClass {

            private int field;

            @BeforeEach
            void setup() {
            }

            @Test
            void testA() {
            }

            @Test
            void testB() {
            }

            private void helper() {
            }
        }
        """;

    String actual = formatter.format(input);
    assertEquals(normalize(expected), normalize(actual));
  }

  @Test
  void testMemberOrderingWithLifecycleAndConstructor() {
    String input =
        """
        package com.example;

        class TestClass {
            @org.junit.jupiter.api.AfterAll
            static void afterAll() {}

            @org.junit.jupiter.api.Test
            void testB() {}

            private int field;

            TestClass() {}

            @org.junit.jupiter.api.BeforeAll
            static void beforeAll() {}

            @org.junit.jupiter.api.BeforeEach
            void setup() {}

            @org.junit.jupiter.api.AfterEach
            void teardown() {}

            @org.junit.jupiter.api.Test
            void testA() {}

            private void helper() {}

            class Inner {}
        }
        """;

    String expected =
        """
        package com.example;

        class TestClass {

            private int field;

            TestClass() {
            }

            @org.junit.jupiter.api.BeforeAll
            static void beforeAll() {
            }

            @org.junit.jupiter.api.BeforeEach
            void setup() {
            }

            @org.junit.jupiter.api.AfterEach
            void teardown() {
            }

            @org.junit.jupiter.api.AfterAll
            static void afterAll() {
            }

            @org.junit.jupiter.api.Test
            void testA() {
            }

            @org.junit.jupiter.api.Test
            void testB() {
            }

            private void helper() {
            }

            class Inner {
            }
        }
        """;

    String actual = formatter.format(input);
    assertEquals(normalize(expected), normalize(actual));
  }

  @Test
  void testParameterizedTestOrdering() {
    String input =
        """
        package com.example;

        class TestClass {
            @org.junit.jupiter.params.ParameterizedTest
            void testC() {}

            @org.junit.jupiter.api.Test
            void testA() {}

            private void helper() {}
        }
        """;

    String expected =
        """
        package com.example;

        class TestClass {

            @org.junit.jupiter.api.Test
            void testA() {
            }

            @org.junit.jupiter.params.ParameterizedTest
            void testC() {
            }

            private void helper() {
            }
        }
        """;

    String actual = formatter.format(input);
    assertEquals(normalize(expected), normalize(actual));
  }

  @Test
  void testDeterminism() {
    String input =
        """
          package com.example;
          import java.util.List;
          import java.util.ArrayList;
          class A {
            @Test void b() {}
            @Test void a() {}
          }
        """;

    String firstPass = formatter.format(input);
    String secondPass = formatter.format(firstPass);

    assertEquals(firstPass, secondPass);
  }

  @Test
  void testFullyQualifiedAnnotations() {
    String input =
        """
        package com.example;

        class TestClass {
            @org.junit.jupiter.api.Test
            void testB() {}

            @org.junit.jupiter.api.Test
            void testA() {}
        }
        """;

    String expected =
        """
        package com.example;

        class TestClass {

            @org.junit.jupiter.api.Test
            void testA() {
            }

            @org.junit.jupiter.api.Test
            void testB() {
            }
        }
        """;

    String actual = formatter.format(input);
    assertEquals(normalize(expected), normalize(actual));
  }

  @Test
  void testNestedClassOrdering() {
    String input =
        """
        package com.example;

        import org.junit.jupiter.api.Test;

        class TestClass {
            class Nested {
                @Test
                void testB() {}

                @Test
                void testA() {}
            }
        }
        """;

    String expected =
        """
        package com.example;

        import org.junit.jupiter.api.Test;

        class TestClass {
            class Nested {

                @Test
                void testA() {
                }

                @Test
                void testB() {
                }
            }
        }
        """;

    String actual = formatter.format(input);
    assertEquals(normalize(expected), normalize(actual));
  }

  @Test
  void testNestedClassOrderingWithFieldsAndConstructor() {
    String input =
        """
        package com.example;

        class Outer {
            class Inner {
                @org.junit.jupiter.api.Test
                void testB() {}

                Inner() {}

                private String value;

                private void helper() {}

                @org.junit.jupiter.api.Test
                void testA() {}
            }
        }
        """;

    String expected =
        """
        package com.example;

        class Outer {
            class Inner {

                private String value;

                Inner() {
                }

                @org.junit.jupiter.api.Test
                void testA() {
                }

                @org.junit.jupiter.api.Test
                void testB() {
                }

                private void helper() {
                }
            }
        }
        """;

    String actual = formatter.format(input);
    assertEquals(normalize(expected), normalize(actual));
  }

  @Test
  void testJava21SyntaxParsing() {
    String input =
        """
        package com.example;

        record Point(int x, int y) {}
        """;

    String actual = formatter.format(input);
    assertEquals(normalize(input), normalize(actual));
  }

  @Test
  void testEmptyRecordKeepsTightBraces() {
    String input =
        """
        package com.example;

        record Point(int x, int y) {}
        """;

    String actual = formatter.format(input);
    assertTrue(actual.contains("record Point(int x, int y) {}"));
    assertFalse(actual.contains("record Point(int x, int y) { }"));
  }

  @Test
  void testFormatterDoesNotRewriteBracePatternsInsideStringLiterals() {
    String input =
        """
        package com.example;

        class Sample {
            String braces = "{}";
            String recordTemplate = "record Template { }";

            record Empty() {}
        }
        """;

    String actual = formatter.format(input);

    assertTrue(actual.contains("String braces = \"{}\";"));
    assertTrue(actual.contains("String recordTemplate = \"record Template { }\";"));
    assertTrue(actual.contains("record Empty() {}"));
  }

  @Test
  void testFormattedCodeEndsWithNewline() {
    String input = "package com.example; class TestClass {}";

    String actual = formatter.format(input);

    assertTrue(actual.endsWith("\n"));
  }

  @Test
  void testNullAndBlankInputReturnAsIs() {
    assertNull(formatter.format(null));

    String empty = "";
    assertEquals(empty, formatter.format(empty));

    String blank = "   \n\t  ";
    assertEquals(blank, formatter.format(blank));
  }

  @Test
  void testParseFailureReturnsOriginalCode() {
    String invalid = "class Broken { void m( }";

    String actual = formatter.format(invalid);

    assertEquals(invalid, actual);
  }

  private String normalize(String s) {
    return s.trim().replaceAll("\\r\\n", "\n").replaceAll("\\s+", " ");
  }
}
