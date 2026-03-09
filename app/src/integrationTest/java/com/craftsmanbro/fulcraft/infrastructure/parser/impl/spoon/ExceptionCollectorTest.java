package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtThrow;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;

class ExceptionCollectorTest {

  @TempDir Path tempDir;

  @Test
  void collectsDeclaredAndThrownExceptionsInOrder() throws Exception {
    Launcher launcher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/Sample.java",
            """
                        package com.example;

                        public class Sample {
                            public void read() throws java.io.IOException {
                                throw new java.lang.IllegalArgumentException("boom");
                            }
                        }
                        """);
    CtMethod<?> read =
        SpoonTestUtils.getType(launcher, "com.example.Sample").getMethodsByName("read").get(0);

    List<String> exceptions = ExceptionCollector.collectThrownExceptions(read);

    assertEquals(List.of("java.io.IOException", "java.lang.IllegalArgumentException"), exceptions);
  }

  @Test
  void resolveThrowTypeFallsBackToExpressionTextWhenTypeMissing() {
    CtThrow thr = mock(CtThrow.class);
    CtExpression<?> expr = mock(CtExpression.class);
    org.mockito.Mockito.doReturn(expr).when(thr).getThrownExpression();
    when(expr.getType()).thenReturn(null);
    when(expr.toString()).thenReturn("new CustomException()");

    assertEquals("new CustomException()", ExceptionCollector.resolveThrowType(thr));
  }

  @Test
  void resolveThrowTypeReturnsNullWhenNoExpression() {
    CtThrow thr = mock(CtThrow.class);
    when(thr.getThrownExpression()).thenReturn(null);

    assertNull(ExceptionCollector.resolveThrowType(thr));
  }

  @Test
  void collectThrownExceptionsDeduplicatesDeclaredAndThrownTypes() throws Exception {
    Launcher launcher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/Sample.java",
            """
                        package com.example;

                        public class Sample {
                            public void read() throws java.io.IOException {
                                throw new java.io.IOException("boom");
                            }
                        }
                        """);
    CtMethod<?> read =
        SpoonTestUtils.getType(launcher, "com.example.Sample").getMethodsByName("read").get(0);

    List<String> exceptions = ExceptionCollector.collectThrownExceptions(read);

    assertEquals(List.of("java.io.IOException"), exceptions);
  }

  @Test
  void resolveThrowTypeFallsBackWhenTypeResolutionFails() {
    CtThrow thr = mock(CtThrow.class);
    CtExpression<?> expr = mock(CtExpression.class);
    org.mockito.Mockito.doReturn(expr).when(thr).getThrownExpression();
    when(expr.getType()).thenThrow(new RuntimeException("failed"));
    when(expr.toString()).thenReturn("ex");

    assertEquals("ex", ExceptionCollector.resolveThrowType(thr));
  }

  @Test
  void collectThrownExceptionsRejectsNullExecutable() {
    assertThrows(
        NullPointerException.class, () -> ExceptionCollector.collectThrownExceptions(null));
  }

  @Test
  void collectThrownExceptionsUsesSimpleNameAndSkipsBlankNames() {
    CtExecutable<Object> executable = mock(CtExecutable.class);
    CtTypeReference<Throwable> simpleOnly = mock(CtTypeReference.class);
    CtTypeReference<Throwable> blank = mock(CtTypeReference.class);
    when(simpleOnly.getQualifiedName()).thenReturn(" ");
    when(simpleOnly.getSimpleName()).thenReturn("SimpleOnlyException");
    when(blank.getQualifiedName()).thenReturn(" ");
    when(blank.getSimpleName()).thenReturn(" ");
    when(executable.getThrownTypes()).thenReturn(java.util.Set.of(simpleOnly, blank));
    when(executable.getElements(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

    List<String> exceptions = ExceptionCollector.collectThrownExceptions(executable);

    assertEquals(List.of("SimpleOnlyException"), exceptions);
  }
}
