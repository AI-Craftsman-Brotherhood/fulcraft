package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.ThrowStmt;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExceptionCollectorTest {

  @Test
  void collectsDeclaredAndThrownExceptionsInOrder() {
    String source =
        """
        package com.example;

        public class Sample {
          public void read() throws java.io.IOException {
            throw new java.lang.IllegalArgumentException("boom");
          }
        }
        """;

    CompilationUnit cu = StaticJavaParser.parse(source);
    ClassOrInterfaceDeclaration sample = cu.getClassByName("Sample").orElseThrow();
    MethodDeclaration read = sample.getMethodsByName("read").get(0);

    List<String> exceptions = ExceptionCollector.collectThrownExceptions(read);

    assertEquals(List.of("java.io.IOException", "java.lang.IllegalArgumentException"), exceptions);
  }

  @Test
  void resolveThrowTypeFallsBackToExpressionTextWhenUnresolved() {
    String source =
        """
        package com.example;

        class Sample {
          void fail(Exception ex) {
            throw ex;
          }
        }
        """;

    CompilationUnit cu = StaticJavaParser.parse(source);
    ThrowStmt throwStmt = cu.findFirst(ThrowStmt.class).orElseThrow();

    assertEquals("ex", ExceptionCollector.resolveThrowType(throwStmt));
  }
}
